import fs from 'node:fs';
import path from 'node:path';
import {execFileSync} from 'node:child_process';
const root=path.resolve(import.meta.dirname,'..');
for(const file of ['app.js','sw.js','media.mjs'])execFileSync(process.execPath,['--check',path.join(root,'dist',file)]);
const html=fs.readFileSync(path.join(root,'dist/index.html'),'utf8');
for(const m of html.matchAll(/(?:src|href)="([^"#?]+)"/g)){
 if(/^(?:https?:|data:)/.test(m[1]))continue;
 if(m[1].startsWith('/'))throw new Error('Root asset path breaks GitHub Pages: '+m[1]);
 if(!fs.existsSync(path.resolve(root,'dist',m[1])))throw new Error('Missing asset '+m[1]);
}
const manifest=JSON.parse(fs.readFileSync(path.join(root,'dist/manifest.webmanifest')));
for(const icon of manifest.icons)if(!fs.existsSync(path.join(root,'dist',icon.src)))throw new Error('Missing icon '+icon.src);
for(const key of ['id','start_url','scope'])if(manifest[key]!=='./')throw new Error('Manifest must use a relative '+key);
const ids=[...html.matchAll(/\bid="([^"]+)"/g)].map(m=>m[1]);if(new Set(ids).size!==ids.length)throw new Error('Duplicate HTML IDs');
const app=fs.readFileSync(path.join(root,'dist/app.js'),'utf8');for(const m of app.matchAll(/\$\('([^']+)'\)/g)){if(!ids.includes(m[1])&&!['scan-progress','detail-actions','episodes'].includes(m[1]))throw new Error('Unknown DOM element '+m[1])}
console.log('Syntax, document IDs, manifest and local assets checked.');
