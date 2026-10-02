import test from 'node:test';import assert from 'node:assert/strict';
import {parseName,catalog,attachSubtitles,toVtt} from '../dist/media.mjs';
test('release filenames retain title/year and episode numbers',()=>{
 assert.deepEqual(parseName('The.Dark.Knight.2008.1080p.BluRay.x264.mkv'),{type:'movie',title:'The Dark Knight',year:2008});
 assert.deepEqual(parseName('Severance.2022.S02E03.1080p.WEB-DL.mkv'),{type:'show',title:'Severance',year:2022,season:2,episode:3,episodeTitle:''});
 assert.equal(parseName('The Office 1x02.mp4').episode,2);
 assert.equal(parseName('2001.A.Space.Odyssey.1968.1080p.mp4').title,'2001 A Space Odyssey');
});
test('season folders identify episodes and movie folder improves generic filename',()=>{
 const p=parseName('05 - Half Loop.mp4','Severance/Season 02');assert.equal(p.title,'Severance');assert.equal(p.season,2);assert.equal(p.episode,5);assert.equal(p.episodeTitle,'Half Loop');
 assert.equal(parseName('Episode 3.mkv','Show/Season 1').type,'show');
 assert.equal(parseName('movie.mp4','Inception (2010)').title,'Inception');
});
test('catalog groups shows, orders episodes and prefers higher-quality duplicates',()=>{
 const entries=catalog([{id:'b',name:'Show.S01E02.720p.mkv',path:''},{id:'a',name:'Show.S01E01.720p.mkv',path:''},{id:'c',name:'Show.S01E01.1080p.mkv',path:''},{id:'movie',name:'Film (2025).mp4',path:''}]);
 const show=entries.find(t=>t.type==='show');assert.equal(entries.length,2);assert.deepEqual(show.files.map(f=>f.id),['c','b']);
});
test('subtitles match language suffixes and Subs folders, without cross-folder leakage',()=>{
 const v={id:'v',name:'Film (2025).mp4',path:'Movies',source:'drive',sourceFolder:'root'};
 const subs=[{id:'ok',name:'Film (2025).en.srt',path:'Movies/Subs',source:'drive',sourceFolder:'root'},{id:'wrong-root',name:'Film (2025).ms.srt',path:'Movies',source:'drive',sourceFolder:'another'},{id:'wrong-movie',name:'Other.srt',path:'Movies',source:'drive',sourceFolder:'root'}];
 assert.deepEqual(attachSubtitles([v],subs)[0].subtitles.map(s=>s.id),['ok']);
});
test('SRT and ASS captions become WebVTT',()=>{
 assert.match(toVtt('1\r\n00:00:01,000 --> 00:00:03,000\r\nHello\r\n','a.srt'),/^WEBVTT\n\n1\n00:00:01\.000 --> 00:00:03\.000\nHello/);
 const ass='[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\nDialogue: 0,0:00:01.20,0:00:03.40,Default,,0,0,0,,{\\b1}Hello\\NWorld';
 assert.match(toVtt(ass,'a.ass'),/00:00:01\.200 --> 00:00:03\.400\nHello\nWorld/);
});
