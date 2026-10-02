import fs from 'node:fs';
import path from 'node:path';
import {execFileSync} from 'node:child_process';
const root=path.resolve(import.meta.dirname,'..');
for(const file of ['app.js','sw.js','media.mjs'])execFileSync(process.execPath,['--check',path.join(root,'dist',file)]);
const html=fs.readFileSync(path.join(root,'dist/index.html'),'utf8');
for(const m of html.matchAll(/(?:src|href)="(\/[^"#?]+)"/g)){if(!fs.existsSync(path.join(root,'dist',m[1])))throw new Error('Missing asset '+m[1])}
const manifest=JSON.parse(fs.readFileSync(path.join(root,'dist/manifest.webmanifest')));
for(const icon of manifest.icons)if(!fs.existsSync(path.join(root,'dist',icon.src)))throw new Error('Missing icon '+icon.src);
const ids=[...html.matchAll(/\bid="([^"]+)"/g)].map(m=>m[1]);if(new Set(ids).size!==ids.length)throw new Error('Duplicate HTML IDs');
const app=fs.readFileSync(path.join(root,'dist/app.js'),'utf8');for(const m of app.matchAll(/\$\('([^']+)'\)/g)){if(!ids.includes(m[1])&&!['scan-progress','detail-actions','episodes'].includes(m[1]))throw new Error('Unknown DOM element '+m[1])}
console.log('Syntax, document IDs, manifest and local assets checked.');
