// Renders the WF-C710N app stills' raw frames from the live three.js scene (headless Chrome):
//   pair_<c>.png      the hero earbud pair (debugShot, the app's own hero camera), 2000 x 1424
//   budL_<c>.png, budR_<c>.png, case_<c>.png   one subject each, square, transparent
// then compose_stills.py frames them like the XM5 files.   node stills.mjs <outDir> [colours...]
import fs from 'node:fs';
import path from 'node:path';
import { openHero, savePng } from './harness.mjs';

const [outDir, ...cols] = process.argv.slice(2);
const colours = cols.length ? cols : ['black', 'white', 'pink', 'blue'];
fs.mkdirSync(outDir, { recursive: true });
// Sony's own studio angles for the single earbuds (front_R / front_L fitted cameras), and a raised
// three-quarter view of the closed case (the XM5 bud_case framing)
const POSE = {
  budR: [10.26, -0.29, 1.54, 0, 0, 2.0],
  budL: [-10.21, -0.53, -1.60, 0, 0, 2.0],
  case: [-24, 26, 0, 0, 0, 1.0],
};
const page = await openHero({ query: 'device=c710n', width: 1000, height: 712, log: false });
for (const c of colours) {
  const url = await page.evaluate(`window.sunpods.debugShot({ device: 'c710n', model: 'buds', color: '${c}', angle: 0, w: 2000, h: 1424 })`);
  savePng(path.join(outDir, `pair_${c}.png`), url);
}
await page.inject('fit_page.js');
await page.evaluate('window.C7FIT.init()');
for (const c of colours) {
  for (const k of ['budR', 'budL', 'case']) {
    const r = await page.evaluate(`C7FIT.shot('${k}', 0, ${JSON.stringify(POSE[k])}, '${c}', 1400)`);
    savePng(path.join(outDir, `${k}_${c}.png`), r.color);
  }
  console.log('rendered', c);
}
await page.evaluate('C7FIT.done()');
await page.close();
process.exit(0);
