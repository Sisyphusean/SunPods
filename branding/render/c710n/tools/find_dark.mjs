// Which case part draws the dark patch between the seated earbuds (tools only).
import fs from 'node:fs';
import path from 'node:path';
import { openHero, savePng, C7DIR } from './harness.mjs';
const fits = JSON.parse(fs.readFileSync(path.join(C7DIR, '_fit', 'fits_latest.json'), 'utf8'));
const page = await openHero({ query: 'device=c710n', width: 512, height: 512, log: false });
await page.inject('fit_page.js');
await page.evaluate('window.C7FIT.init()');
const names = await page.evaluate(`(async () => { const d = await window.sunpods.debugC7(false); const out = [];
  d.C7.kase.spin.traverse((o) => { if (o.isMesh) out.push((o.name || '?') + '|' + o.uuid); }); return out; })()`);
console.log(names.length, 'meshes');
const pose = fits.open_front.pose;
const base = await page.evaluate(`C7FIT.shot('case', 1, ${JSON.stringify(pose)}, 'black', 512)`);
savePng(process.argv[2] + '_base.png', base.color);
for (const nm of names) {
  const uuid = nm.split('|')[1];
  await page.evaluate(`(async () => { const d = await window.sunpods.debugC7(false); d.C7.kase.spin.traverse((o) => { if (o.uuid === '${uuid}') { o.userData.wasVis = o.visible; o.visible = false; } }); })()`);
  const r = await page.evaluate(`C7FIT.shot('case', 1, ${JSON.stringify(pose)}, 'black', 512)`);
  const f = process.argv[2] + '_' + nm.split('|')[0].replace(/[^A-Za-z0-9_]/g, '') + '_' + uuid.slice(0, 4) + '.png';
  savePng(f, r.color);
  await page.evaluate(`(async () => { const d = await window.sunpods.debugC7(false); d.C7.kase.spin.traverse((o) => { if (o.uuid === '${uuid}') o.visible = o.userData.wasVis; }); })()`);
}
await page.close(); process.exit(0);
