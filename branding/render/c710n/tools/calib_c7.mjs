// Colour calibration renders for calib_c7.py: the right earbud face-on (front_R) and the closed case
// (closed_front) at their fitted cameras, with the body albedo at 0 and at 0.5 (calibration finishes),
// plus the screen positions of the sample regions.
//   node calib_c7.mjs
import fs from 'node:fs';
import path from 'node:path';
import { openHero, C7DIR, savePng } from './harness.mjs';

const OUT = path.join(C7DIR, '_fit', 'calib'); fs.mkdirSync(OUT, { recursive: true });
const fits = JSON.parse(fs.readFileSync(path.join(C7DIR, '_fit', 'fits_latest.json'), 'utf8'));
const page = await openHero({ query: 'device=c710n', width: 512, height: 512, log: false });
await page.inject('fit_page.js');
await page.evaluate('window.C7FIT.init()');
const base = await page.evaluate(`(async () => { const d = await window.sunpods.debugC7(); return d.C7.finishCopy('black'); })()`);
for (const v of [0, 0.5]) {
  const f = JSON.parse(JSON.stringify(base));
  for (const k of ['shell', 'face', 'tip', 'grille', 'caseShell', 'caseInner']) f[k] = [v, v, v];
  await page.evaluate(`window.sunpods.debugFinishC7('cal${v * 10}', ${JSON.stringify(f)})`);
}
const info = {};
for (const [id, kind, lid] of [['front_R', 'budR', 0], ['closed_front', 'case', 0]]) {
  const pose = fits[id].pose;
  for (const v of [0, 5]) {
    const s = await page.evaluate(`C7FIT.shot(${JSON.stringify(kind)}, ${lid}, ${JSON.stringify(pose)}, 'cal${v}', 512)`);
    savePng(path.join(OUT, `${id}_cal${v}.png`), s.color);
  }
  const pts = await page.evaluate(`(async () => {
    const d = await window.sunpods.debugC7(false);
    const C7 = d.C7, BH = C7.BH, B = C7.CASE.B;
    const nz = C7.BUD.nozzle, tp = C7.BUD.tip;
    const dir = nz.dir, n = Math.hypot(...dir);
    const tipFront = nz.from.map((c, i) => (c + dir[i] / n * (nz.len + tp.len * 0.8)) * BH);
    const P = ${JSON.stringify(kind)} === 'case'
      ? { caseFrontL: [-0.95, 0.62, B], caseFrontR: [0.95, 0.62, B], lidFront: [0, 2.05, B - 0.15] }
      : { disc: [0, 0, 0], discEdge: [BH * C7.BUD.Rd, 0, 0], shellTop: [-0.07 * BH, 0.39 * BH, -0.12 * BH], shellLeft: [-0.39 * BH, 0.06 * BH, -0.12 * BH], tip: tipFront };
    const names = Object.keys(P);
    const xy = C7FIT.project(${JSON.stringify(kind)}, ${lid}, ${JSON.stringify(pose)}, names.map((k) => P[k]), 512);
    return Object.fromEntries(names.map((k, i) => [k, xy[i]]));
  })()`);
  info[id] = pts;
}
fs.writeFileSync(path.join(OUT, 'points.json'), JSON.stringify(info, null, 1));
console.log(JSON.stringify(info));
await page.close();
