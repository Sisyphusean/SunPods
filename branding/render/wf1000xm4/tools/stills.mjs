// Renders the WF-1000XM4 app stills' raw frames from the live three.js scene (headless Chrome):
//   pair_<c>.png      the hero earbud pair (debugShot, the app's own hero camera), 2000 x 1424
//   budL_<c>.png, budR_<c>.png, case_<c>.png   one subject each, square, transparent
// then compose_stills.py frames them like the XM5 files.   node stills.mjs <outDir> [colours...]
import fs from 'node:fs';
import path from 'node:path';
import { openHero, savePng } from './harness.mjs';

const [outDir, ...cols] = process.argv.slice(2);
const colours = cols.length ? cols : ['black', 'silver'];
fs.mkdirSync(outDir, { recursive: true });
// the single earbuds at Sony's own face-on studio angle (the fitted camera of sony.jp's face view,
// mirrored for the left earbud), and a raised three-quarter view of the closed case
const POSE = {
  budR: [-8.7, 2.5, 8.3, 0, 0, 2.0],
  budL: [8.7, 2.5, -8.3, 0, 0, 2.0],
  case: [-24, 26, 0, 0, 0, 1.0],
};
const page = await openHero({ query: 'device=wf1000xm4', width: 1000, height: 712, log: false });
for (const c of colours) {
  const url = await page.evaluate(`window.sunpods.debugShot({ device: 'wf1000xm4', model: 'buds', color: '${c}', angle: 0, w: 2000, h: 1424 })`);
  savePng(path.join(outDir, `pair_${c}.png`), url);
}
await page.inject('fit_page.js');
await page.evaluate('window.X4FIT.init()');
for (const c of colours) {
  for (const k of ['budR', 'budL', 'case']) {
    const r = await page.evaluate(`X4FIT.shot('${k}', 0, ${JSON.stringify(POSE[k])}, '${c}', 1400)`);
    savePng(path.join(outDir, `${k}_${c}.png`), r.color);
  }
  console.log('rendered', c);
}
await page.evaluate('X4FIT.done()');
await page.close();
process.exit(0);
