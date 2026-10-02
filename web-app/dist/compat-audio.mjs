import {ByteSource,MkvAudio} from './mkv-audio.mjs';
const hubs=new WeakMap();
function audioHub(video){
 let hub=hubs.get(video);if(hub)return hub;
 const Audio=globalThis.AudioContext||globalThis.webkitAudioContext;if(!Audio)throw new Error('This browser cannot start the audio player');
 const context=new Audio({latencyHint:'playback'}),native=context.createGain();
 context.createMediaElementSource(video).connect(native);native.connect(context.destination);
 hub={context,native};hubs.set(video,hub);return hub;
}
export function prepareAudio(video,create=true){const hub=create?audioHub(video):hubs.get(video);hub?.context.resume().catch(()=>{});return hub}
export class CompatAudio{
 constructor(video,options){
  this.video=video;this.options=options;this.hub=prepareAudio(video);this.life=new AbortController();this.sources=new Set();this.events=[];this.generation=0;this.active=false;this.disposed=false;
  this.gain=this.hub.context.createGain();this.gain.connect(this.hub.context.destination);
  const listen=(name,fn)=>{video.addEventListener(name,fn);this.events.push([name,fn])};
  for(const e of ['pause','waiting','seeking','ended','emptied'])listen(e,()=>this.halt());
  for(const e of ['playing','seeked','ratechange'])listen(e,()=>{if(this.active&&!video.paused&&!video.seeking)this.restart()});
  listen('volumechange',()=>this.volume());this.volume();
 }
 volume(){this.gain.gain.value=this.video.muted?0:this.video.volume}
 async init(){
  try{this.mkv=new MkvAudio(new ByteSource({...this.options,signal:this.life.signal}));if(!await this.mkv.init()||this.disposed)return false;
   // The shipped decoder is local, so private media never goes to a conversion service.
   const module=await import('./vendor/eac3/decode-eac3.mjs');if(this.disposed)return false;this.decoderFactory=module.decoder;
   const probe=await this.decoderFactory();probe.free();if(this.disposed)return false;
   this.active=true;this.hub.native.gain.value=0;this.options.onStatus?.('Dolby audio ready');
   if(!this.video.paused&&!this.video.seeking)this.restart();return true;
  }catch(e){if(!this.disposed)this.options.onStatus?.('Audio could not start: '+e.message,true);return false}
 }
 halt(){++this.generation;this.run?.abort();for(const source of this.sources){try{source.stop()}catch{}source.disconnect()}this.sources.clear()}
 restart(){
  this.halt();if(this.disposed||!this.active||this.video.paused||this.video.seeking)return;
  const generation=this.generation;this.run=new AbortController();const signal=AbortSignal.any([this.life.signal,this.run.signal]);
  this.pump(generation,signal).catch(e=>{if(!signal.aborted&&!this.disposed){this.halt();this.options.onStatus?.('Audio interrupted: '+e.message+'. Tap Restore sound to retry.',true)}});
 }
 async pump(generation,signal){
  const v=this.video,ctx=this.hub.context;await ctx.resume();signal.throwIfAborted();
  const reader=new MkvAudio(new ByteSource({...this.options,signal}));Object.assign(reader,{scale:this.mkv.scale,track:this.mkv.track,cues:this.mkv.cues,firstCluster:this.mkv.firstCluster,segment:this.mkv.segment});
  const dec=await this.decoderFactory();let heard=false,nextAt=0,nextTime=0;
  try{for await(const packet of reader.packets(Math.max(0,v.currentTime-0.1))){
    signal.throwIfAborted();if(generation!==this.generation)return;
    // Backpressure caps decoded audio at three seconds, even while a movie is paused.
    while(packet.time>v.currentTime+3){await delay(50,signal);if(generation!==this.generation)return}
    let time=packet.time;
    for(const bytes of packet.frames){const pcm=dec.decode(bytes);if(!pcm.channelData.length){if(dec.errors>8)throw new Error('Dolby stream could not be decoded');continue}
     const frames=pcm.channelData[0].length,duration=frames/pcm.sampleRate,rate=v.playbackRate;
     const trim=Math.max(0,Math.ceil((v.currentTime+0.04*rate-time)*pcm.sampleRate));
     if(trim<frames){const buffer=ctx.createBuffer(pcm.channelData.length,frames-trim,pcm.sampleRate);pcm.channelData.forEach((ch,i)=>buffer.copyToChannel(ch.subarray(trim),i));
      const source=ctx.createBufferSource();source.buffer=buffer;source.playbackRate.value=rate;source.connect(this.gain);
      const target=time+trim/pcm.sampleRate;let at=ctx.currentTime+(target-v.currentTime)/rate;
      if(Math.abs(time-nextTime)<0.003&&nextAt>=ctx.currentTime&&Math.abs(nextAt-at)<0.05)at=nextAt;
      source.start(Math.max(ctx.currentTime,at));nextAt=at+buffer.duration/rate;nextTime=time+duration;
      this.sources.add(source);source.onended=()=>{this.sources.delete(source);source.disconnect()};
      if(!heard){heard=true;this.options.onStatus?.('Dolby audio active')}
     }time+=duration;
    }
   }
   if(!heard&&!signal.aborted&&v.currentTime<v.duration-0.1)throw new Error('No playable Dolby audio packets were found');
  }finally{dec.free()}
 }
 async restore(){await this.hub.context.resume();if(!this.active)await this.init();else this.restart()}
 dispose(){if(this.disposed)return;this.disposed=true;this.life.abort();this.halt();for(const [name,fn]of this.events)this.video.removeEventListener(name,fn);this.gain.disconnect();this.hub.native.gain.value=1}
}
function delay(ms,signal){return new Promise((resolve,reject)=>{signal.throwIfAborted();const cancel=()=>{clearTimeout(timer);reject(signal.reason)};const timer=setTimeout(()=>{signal.removeEventListener('abort',cancel);resolve()},ms);signal.addEventListener('abort',cancel,{once:true})})}
