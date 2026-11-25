// Renders for the inner-side and case close-up boards (tools only), at poses set by eye to Sony's
// pictures (help guide drawings, sony.jp photos):  node extra.mjs <outDir>   then extra_board.py
import fs from 'node:fs';
import path from 'node:path';
import { openHero, savePng } from './harness.mjs';
const [outDir] = process.argv.slice(2);
fs.mkdirSync(outDir, { recursive: true });
const page = await openHero({ query: 'device=wf1000xm4', width: 512, height: 512, log: false });
await page.inject('fit_page.js');
await page.evaluate('window.X4FIT.init()');
const SHOTS = {
  inner_L: ['budL', 0, [150, -35, 0, 0, 0, 2.0], 'black'],
  inner_R: ['budR', 0, [-150, -35, 0, 0, 0, 2.0], 'black'],
  inner_shell: ['budR', 0, [180, 0, 0, 0, 0, 2.0], 'black'],
  case_open: ['case', 1, [0, 22, 0, 0, 0, 0.9], 'black'],
  case_closed: ['case', 0, [0, 6, 0, 0, 0, 0.9], 'black'],
  case_silver: ['case', 0, [-8, 18, 0, 0, 0, 0.9], 'silver'],
};
for (const [k, [subj, lid, pose, c]] of Object.entries(SHOTS)) {
  const r = await page.evaluate(`X4FIT.shot('${subj}', ${lid}, ${JSON.stringify(pose)}, '${c}', 1024)`);
  savePng(path.join(outDir, `${k}.png`), r.color);
}
await page.evaluate('X4FIT.done()');
await page.close(); process.exit(0);
