import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {ByteSource,MkvAudio,blockFrames,vint} from '../dist/mkv-audio.mjs';
import {decoder} from '../dist/vendor/eac3/decode-eac3.mjs';
import {CompatAudio} from '../dist/compat-audio.mjs';
const fixture=Buffer.from(fs.readFileSync(new URL('./fixtures/dolby.mkv.base64',import.meta.url),'utf8'),'base64');
test('real MKV Dolby 5.1 audio is decoded, including dialogue in the centre channel',async()=>{
 const reader=new MkvAudio(new ByteSource({blob:new Blob([fixture])}));assert.equal(await reader.init(),true);assert.equal(reader.track.codec,'A_EAC3');assert.ok(reader.cues.length>=2);
 const dec=await decoder();let samples=0,energy=Array(6).fill(0),previous=-1;
 try{for await(const packet of reader.packets()){assert.ok(packet.time>=previous);previous=packet.time;for(const bytes of packet.frames){const pcm=dec.decode(bytes);assert.equal(pcm.sampleRate,48000);assert.equal(pcm.channelData.length,6);samples+=pcm.channelData[0].length;pcm.channelData.forEach((ch,i)=>{for(const x of ch)energy[i]+=x*x})}}assert.equal(dec.errors,0)}finally{dec.free()}
 assert.ok(samples>=3*48000);assert.ok(energy[2]>1000,'Centre dialogue must not be lost');for(const i of [0,1,3,4,5])assert.ok(energy[i]<1,'Silent channels should remain silent');
});
test('Drive audio uses authenticated service-worker byte ranges, seeks from cues, and never downloads the whole movie',async()=>{
 const requests=[];const fetcher=async(url,init)=>{assert.equal(url,'https://example.test/IAMTT/drive-media/private');assert.equal(init.cache,'no-store');assert.equal(init.headers.Authorization,undefined);const [,a,b]=/^bytes=(\d+)-(\d+)$/.exec(init.headers.Range);requests.push([Number(a),Number(b)]);return new Response(fixture.subarray(Number(a),Number(b)+1),{status:206,headers:{'Content-Range':`bytes ${a}-${b}/${fixture.length}`}})};
 const reader=new MkvAudio(new ByteSource({url:'https://example.test/IAMTT/drive-media/private',size:fixture.length,fetcher}));assert.equal(await reader.init(),true);
 reader.source.cache=null;requests.length=0;const times=[];for await(const p of reader.packets(2.2))times.push(p.time);
 const cue=reader.cues.filter(c=>c.time<=2.2).at(-1);assert.equal(requests[0][0],cue.pos);assert.ok(cue.pos>reader.firstCluster);assert.ok(times[0]>=1.2);assert.ok(times.at(-1)>2.8);assert.ok(requests.every(([a,b])=>b-a+1<=65536));
});
test('a server ignoring Range is cancelled without buffering the full movie',async()=>{
 let cancelled=false;const source=new ByteSource({url:'/drive-media/x',size:10e9,fetcher:async()=>({status:200,body:{cancel:async()=>{cancelled=true}}})});await assert.rejects(()=>source.read(0,12),/byte-range/);assert.equal(cancelled,true);
});
test('abort prevents more private-media reads',async()=>{const c=new AbortController();c.abort();const source=new ByteSource({blob:new Blob([fixture]),signal:c.signal});await assert.rejects(()=>source.read(0,12),{name:'AbortError'})});
test('Matroska variable integers and each block lacing mode are handled safely',()=>{
 assert.equal(vint(Uint8Array.of(0xff)).value,Infinity);assert.equal(vint(Uint8Array.of(0x40,0x80)).value,128);
 for(const [flags,lace]of [[0,[]],[2,[1,2]],[4,[1]],[6,[1,0x82]]]){const b=Uint8Array.from([0x82,0xff,0xf6,flags,...lace,1,2,3,4]);const out=blockFrames(b);assert.equal(out.track,2);assert.equal(out.relative,-10);assert.deepEqual(out.frames.map(f=>[...f]),flags?[[1,2],[3,4]]:[[1,2,3,4]])}
 assert.throws(()=>blockFrames(Uint8Array.from([0x82,0,0,2,2,255])),/lacing/);
});
class FakeAudioContext{
 constructor(){this.currentTime=0;this.destination={};this.nodes=[]}
 createGain(){return {gain:{value:1},connect(){},disconnect(){}}}
 createMediaElementSource(){return {connect(){}}}
 resume(){return Promise.resolve()}
 createBuffer(channels,length,sampleRate){const data=[];return {duration:length/sampleRate,copyToChannel(ch,i){data[i]=ch},data}}
 createBufferSource(){const n={playbackRate:{value:1},connect(){},disconnect(){},start(at){this.at=at},stop(){this.stopped=true}};this.nodes.push(n);return n}
}
test('compatible sound schedules all six channels, respects mute and clears sound on pause, seek and close',async()=>{
 globalThis.AudioContext=FakeAudioContext;
 const v=new EventTarget();Object.assign(v,{paused:false,seeking:false,currentTime:0,duration:3,volume:0.7,muted:false,playbackRate:1});let statuses=[];
 const audio=new CompatAudio(v,{blob:new Blob([fixture]),onStatus:s=>statuses.push(s)});assert.equal(await audio.init(),true);
 for(let i=0;i<20&&!audio.sources.size;i++)await new Promise(r=>setTimeout(r,5));assert.ok(audio.sources.size>0);assert.equal(audio.hub.native.gain.value,0);assert.equal(audio.gain.gain.value,0.7);assert.equal(audio.hub.context.nodes[0].buffer.data.length,6);
 v.muted=true;v.dispatchEvent(new Event('volumechange'));assert.equal(audio.gain.gain.value,0);
 v.paused=true;v.dispatchEvent(new Event('pause'));assert.equal(audio.sources.size,0);assert.ok(audio.hub.context.nodes.every(n=>n.stopped));
 v.paused=false;v.currentTime=2;v.muted=false;v.playbackRate=1.5;v.dispatchEvent(new Event('seeked'));for(let i=0;i<20&&!audio.sources.size;i++)await new Promise(r=>setTimeout(r,5));assert.ok(audio.sources.size);assert.equal([...audio.sources][0].playbackRate.value,1.5);
 v.dispatchEvent(new Event('seeking'));assert.equal(audio.sources.size,0);audio.dispose();assert.equal(audio.hub.native.gain.value,1);assert.equal(audio.sources.size,0);assert.ok(statuses.includes('Dolby audio active'));delete globalThis.AudioContext;
});
