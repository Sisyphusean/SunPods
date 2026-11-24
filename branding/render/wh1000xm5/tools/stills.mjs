// Renders the WH-1000XM5 app stills' raw frames from the live three.js scene (headless Chrome):
//   pair_<c>.png     the hero view (debugShot, the app's own hero camera), 2000 x 1424
//   side_<c>.png, front_<c>.png, case_<c>.png   one subject each, square, transparent
// then compose_stills.py frames them like the XM5 files.   node stills.mjs <outDir> [colours...]
import fs from 'node:fs';
import path from 'node:path';
import { openHero, savePng } from './harness.mjs';

const [outDir, ...cols] = process.argv.slice(2);
const colours = cols.length ? cols : ['black', 'silver', 'blue', 'pink'];
fs.mkdirSync(outDir, { recursive: true });
// Sony's own studio angles (upright 3/4 side view, straight front view, and raised three-quarter
// view of the closed case). [subject, lid, rig pose]; the scale is set from the subject's size.
const POSE = {
  side: ['phones', 0, [-42, 11, 0, 0, 0, 1.0]],   // upright three-quarter hero angle, band on top
  front: ['phones', 0, [0, 6, 0, 0, 0, 1.0]],      // upright straight front view, band on top
  case: ['case', 0, [-32, 38, 0, 0, 0, 1.0]],      // raised 3/4 view of closed carry case
};
const page = await openHero({ query: 'device=wh1000xm5', width: 1000, height: 712, log: false });
if (process.env.XM_OVR) await page.evaluate(`window.sunpods.debugRebuildMod('wh1000xm5', ${process.env.XM_OVR})`);
for (const c of colours) {
  const url = await page.evaluate(`window.sunpods.debugShot({ device: 'wh1000xm5', model: 'buds', color: '${c}', angle: 0, w: 2000, h: 1424 })`);
  savePng(path.join(outDir, `pair_${c}.png`), url);
}
await page.inject('fit_page.js');
await page.evaluate('window.XMFIT.init()');
for (const c of colours) {
  for (const [k, [subject, lid, pose]] of Object.entries(POSE)) {
    const sz = await page.evaluate(`XMFIT.useSubject('${subject}', ${lid}).size`);
    const p = pose.slice(); p[5] = 0.8 * 7.37 / Math.hypot(...sz);   // about 80% of the square
    const r = await page.evaluate(`XMFIT.shot('${subject}', ${lid}, ${JSON.stringify(p)}, '${c}', 1400)`);
    savePng(path.join(outDir, `${k}_${c}.png`), r.color);
  }
  console.log('rendered', c);
}
await page.evaluate('XMFIT.done()');
await page.close();
process.exit(0);
