// Bounded, seekable Matroska audio extraction. Video bytes are never decoded here.
const ID={segment:0x18538067,seekHead:0x114d9b74,info:0x1549a966,tracks:0x1654ae6b,cluster:0x1f43b675,cues:0x1c53bb6b};
const MAX_ELEMENT=16*1024*1024;
export function vint(bytes,offset=0,keepMarker=false){
 const first=bytes[offset];if(!first)throw new Error('Invalid Matroska integer');
 let length=1,mask=128;while(!(first&mask)){length++;mask>>=1}
 if(offset+length>bytes.length)throw new Error('Truncated Matroska integer');
 let value=keepMarker?first:first&(mask-1),unknown=!keepMarker&&value===mask-1;
 for(let i=1;i<length;i++){value=value*256+bytes[offset+i];unknown=unknown&&bytes[offset+i]===255}
 if(!unknown&&!Number.isSafeInteger(value))throw new Error('Matroska integer exceeds safe range');
 return {length,value:unknown?Infinity:value};
}
function uint(b){let n=0;for(const x of b)n=n*256+x;return n}
function elements(b){const out=[];let p=0;while(p<b.length){const id=vint(b,p,true);p+=id.length;const size=vint(b,p);p+=size.length;if(!Number.isFinite(size.value)||p+size.value>b.length)throw new Error('Truncated Matroska metadata');out.push({id:id.value,data:b.subarray(p,p+size.value)});p+=size.value}return out}
function text(b){return new TextDecoder().decode(b)}
export class ByteSource{
 constructor({url,blob,size,fetcher=fetch,signal}){this.url=url;this.blob=blob;this.size=blob?.size||Number(size);this.fetcher=fetcher;this.signal=signal;this.cache=null;if(!Number.isSafeInteger(this.size)||this.size<=0)throw new Error('Missing video file size')}
 async read(offset,length,small=false){
  this.signal?.throwIfAborted();if(offset<0||length<0||offset+length>this.size)throw new Error('Truncated Matroska file');
  if(this.cache&&offset>=this.cache.start&&offset+length<=this.cache.start+this.cache.bytes.length)return this.cache.bytes.subarray(offset-this.cache.start,offset-this.cache.start+length);
  const end=Math.min(this.size,offset+Math.max(length,small?32:65536));let bytes;
  if(this.blob)bytes=new Uint8Array(await this.blob.slice(offset,end).arrayBuffer());
  else{const r=await this.fetcher(this.url,{headers:{Range:`bytes=${offset}-${end-1}`},cache:'no-store',signal:this.signal});if(r.status!==206) {await r.body?.cancel();throw new Error(r.status===401?'Reconnect Google Drive to restore sound.':'Audio playback requires byte-range streaming ('+r.status+').')}
   const range=r.headers.get('Content-Range');if(range&&!range.startsWith('bytes '+offset+'-')){await r.body?.cancel();throw new Error('Incorrect audio stream range')}
   bytes=new Uint8Array(await r.arrayBuffer());if(bytes.length!==end-offset)throw new Error('Incomplete audio stream range');
  }
  this.signal?.throwIfAborted();this.cache={start:offset,bytes};return bytes.subarray(0,length);
 }
 async header(offset,small=false){const b=await this.read(offset,Math.min(12,this.size-offset),small);const id=vint(b,0,true),size=vint(b,id.length);const start=offset+id.length+size.length,end=Number.isFinite(size.value)?start+size.value:this.size;if(end>this.size||end<start)throw new Error('Invalid Matroska element size');return {id:id.value,start,end,size:size.value}}
 async data(e){if(e.end-e.start>MAX_ELEMENT)throw new Error('Matroska metadata is too large');return this.read(e.start,e.end-e.start)}
}
export function blockFrames(b){
 const track=vint(b),p=track.length;if(p+3>b.length)throw new Error('Truncated Matroska block');
 const relative=new DataView(b.buffer,b.byteOffset+p,2).getInt16(0),lace=(b[p+2]>>1)&3;let pos=p+3,sizes=[];
 if(!lace)return {track:track.value,relative,frames:[b.subarray(pos)]};
 const count=b[pos++]+1;
 if(lace===1){for(let i=0;i<count-1;i++){let n=0,v;do{if(pos>=b.length)throw new Error('Invalid Xiph lacing');v=b[pos++];n+=v}while(v===255);sizes.push(n)}}
 if(lace===2){const n=(b.length-pos)/count;if(!Number.isInteger(n))throw new Error('Invalid fixed lacing');sizes=Array(count-1).fill(n)}
 if(lace===3&&count>1){const first=vint(b,pos);pos+=first.length;sizes.push(first.value);for(let i=1;i<count-1;i++){const v=vint(b,pos);pos+=v.length;sizes.push(sizes[i-1]+v.value-(2**(7*v.length-1)-1))}}
 sizes.push(b.length-pos-sizes.reduce((a,n)=>a+n,0));const frames=[];for(const n of sizes){if(!Number.isSafeInteger(n)||n<0||pos+n>b.length)throw new Error('Invalid Matroska lacing');frames.push(b.subarray(pos,pos+n));pos+=n}return {track:track.value,relative,frames};
}
export class MkvAudio{
 constructor(source){this.source=source;this.scale=0.001;this.cues=[];this.seeks=new Map();this.track=null}
 async init(){
  const s=this.source;let p=0,segment;while(p<s.size){const e=await s.header(p);if(e.id===ID.segment){segment=e;break}p=e.end}
  if(!segment)throw new Error('This file is not Matroska');this.segment=segment.start;
  p=segment.start;for(let count=0;p<segment.end&&count<128;count++){
   const e=await s.header(p);if(e.id===ID.seekHead)this.readSeeks(await s.data(e));if(e.id===ID.info)this.readInfo(await s.data(e));if(e.id===ID.tracks)this.readTracks(await s.data(e));
   if(e.id===ID.cluster){this.firstCluster=p;break}p=e.end;
  }
  if(!this.track&&this.seeks.has(ID.tracks)){const e=await s.header(this.segment+this.seeks.get(ID.tracks));this.readTracks(await s.data(e))}
  if(!this.track)return false;
  if(this.seeks.has(ID.cues)){const e=await s.header(this.segment+this.seeks.get(ID.cues));if(e.id===ID.cues)this.readCues(await s.data(e))}
  if(this.firstCluster===undefined)throw new Error('No video clusters found');return true;
 }
 readSeeks(b){for(const seek of elements(b)){if(seek.id!==0x4dbb)continue;let id,pos;for(const e of elements(seek.data)){if(e.id===0x53ab)id=uint(e.data);if(e.id===0x53ac)pos=uint(e.data)}if(id!==undefined&&pos!==undefined)this.seeks.set(id,pos)}}
 readInfo(b){for(const e of elements(b))if(e.id===0x2ad7b1)this.scale=uint(e.data)/1e9}
 readTracks(b){const tracks=[];for(const entry of elements(b)){if(entry.id!==0xae)continue;let t={default:true};for(const e of elements(entry.data)){if(e.id===0xd7)t.number=uint(e.data);if(e.id===0x83)t.type=uint(e.data);if(e.id===0x86)t.codec=text(e.data);if(e.id===0x88)t.default=Boolean(uint(e.data))}if(t.type===2)tracks.push(t)}
  // Keep the file's default audio track; never replace an AAC track with a foreign-language Dolby track.
  const t=tracks.find(t=>t.default)||tracks[0];if(t&&['A_EAC3','A_AC3'].includes(t.codec))this.track=t;
 }
 readCues(b){for(const point of elements(b)){if(point.id!==0xbb)continue;let time,positions=[];for(const e of elements(point.data)){if(e.id===0xb3)time=uint(e.data)*this.scale;if(e.id===0xb7){let track,pos;for(const x of elements(e.data)){if(x.id===0xf7)track=uint(x.data);if(x.id===0xf1)pos=uint(x.data)}if(pos!==undefined)positions.push({track,pos})}}
   const chosen=positions.find(p=>p.track===this.track.number)||positions[0];if(chosen&&time!==undefined)this.cues.push({time,pos:this.segment+chosen.pos});}this.cues.sort((a,b)=>a.time-b.time);
 }
 async *packets(time=0){
  let p=this.firstCluster;for(const cue of this.cues){if(cue.time>time)break;p=cue.pos}
  // Files without a cue index still seek by skipping entire clusters, not reading old video payloads.
  if(!this.cues.length&&time>1){let q=p;while(q<this.source.size){const cluster=await this.source.header(q,true);if(cluster.id!==ID.cluster){q=cluster.end;continue}if(!Number.isFinite(cluster.size))break;let r=cluster.start,stamp=0;for(let i=0;i<8&&r<cluster.end;i++){const e=await this.source.header(r,true);if(e.id===0xe7){stamp=uint(await this.source.data(e))*this.scale;break}r=e.end}if(stamp>time)break;p=q;q=cluster.end}}
  const s=this.source;while(p<s.size){const cluster=await s.header(p,true);if(cluster.id!==ID.cluster){if(cluster.end<=p)break;p=cluster.end;continue}let q=cluster.start,stamp=0;
   let next=cluster.end;
   while(q<cluster.end){const e=await s.header(q);if(e.id===ID.cluster){next=q;break}if(e.id===0xe7)stamp=uint(await s.data(e));
    if(e.id===0xa3||e.id===0xa0){let block=e;if(e.id===0xa0){let r=e.start;block=null;while(r<e.end){const child=await s.header(r);if(child.id===0xa1){block=child;break}r=child.end}}
     if(block){const header=await s.read(block.start,Math.min(12,block.end-block.start));if(vint(header).value===this.track.number){const decoded=blockFrames(await s.data(block));const timestamp=(stamp+decoded.relative)*this.scale;if(timestamp>=time-1)yield {time:timestamp,frames:decoded.frames}}}
    }
    if(e.end<=q)throw new Error('Invalid Matroska cluster');q=e.end;
   }if(next<=p)break;p=next;
  }
 }
}
