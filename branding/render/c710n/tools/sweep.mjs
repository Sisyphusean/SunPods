// Shape sweep (tools only): rebuilds the model with each trial override and fits given angles.
//   node sweep.mjs <outDir> <refIds,comma> <trials.json>   trials: [{ "name": "...", "overrides": {...}, "lid": deg?, "pitch": deg? }]
import fs from 'node:fs';
import path from 'node:path';
import { openHero, C7DIR, savePng } from './harness.mjs';
const [out, ids, trialsFile] = process.argv.slice(2);
const trials = JSON.parse(fs.readFileSync(trialsFile, 'utf8'));
const meta = JSON.parse(fs.readFileSync(path.join(C7DIR, '_fit', 'refs', 'meta.json'), 'utf8'));
fs.mkdirSync(out, { recursive: true });
const page = await openHero({ query: 'device=c710n', width: 512, height: 512, log: false });
await page.inject('fit_page.js');
await page.evaluate('window.C7FIT.init()');
for (const t of trials) {
  await page.evaluate(`C7FIT.rebuild(${JSON.stringify(t.overrides || {})})`);
  const row = [];
  for (const id of ids.split(',')) {
    const r = meta[id];
    const b64 = fs.readFileSync(path.join(C7DIR, '_fit', 'refs', id + '_mask.png')).toString('base64');
    const lid = t.lid !== undefined ? `(${t.lid} * Math.PI / 180) / (await window.sunpods.debugC7(false)).C7.LID_OPEN` : (r.lid || 0);
    const opts = t.pitch !== undefined ? `{ lockPitch: ${t.pitch}, starts: [[0,0,0],[6,0,0],[-6,0,0]], iters: 110 }` : `{ starts: [[0,0,0],[8,0,0],[-8,0,0]], iters: 110 }`;
    const res = await page.evaluate(`(async () => {
      const lid = ${r.subject === 'case' ? lid : 0};
      const f = await C7FIT.fit(${JSON.stringify(r.subject)}, lid, ${JSON.stringify(b64)}, ${JSON.stringify(t.pitch !== undefined ? [r.init[0], t.pitch, r.init[2]] : r.init)}, ${opts});
      const s = C7FIT.shot(${JSON.stringify(r.subject)}, lid, f.pose, ${JSON.stringify(r.color)}, 512);
      return { iou: f.iou, pose: f.pose, mask: s.mask, color: s.color };
    })()`);
    savePng(path.join(out, `${t.name}_${id}_mask.png`), res.mask); savePng(path.join(out, `${t.name}_${id}_color.png`), res.color);
    row.push(`${id} ${res.iou.toFixed(4)} (pitch ${res.pose[1].toFixed(1)})`);
  }
  console.log(t.name.padEnd(18), row.join('  '));
}
await page.close();
