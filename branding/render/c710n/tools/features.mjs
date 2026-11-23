// Feature panel for the review packet: views with no clean Sony silhouette (case back, case top,
// earbud inner side, tactile dot), rendered at fixed cameras next to the Sony material that shows them.
//   node features.mjs <iterDir>      -> <iterDir>/feat_*.png, then compose_features.py
import path from 'node:path';
import { openHero, savePng } from './harness.mjs';
const [out] = process.argv.slice(2);
const page = await openHero({ query: 'device=c710n', width: 512, height: 512, log: false });
await page.inject('fit_page.js');
await page.evaluate('window.C7FIT.init()');
const S = [
  { name: 'case_back_black', kind: 'case', pose: [180, 4, 0, 0, 0.02, 0.95], color: 'black' },
  { name: 'case_back_blue', kind: 'case', pose: [180, 4, 0, 0, 0.02, 0.95], color: 'blue' },
  { name: 'case_top_white', kind: 'case', pose: [0, 62, 0, 0, 0, 0.95], color: 'white' },
  { name: 'case_open34_blue', kind: 'case', lid: 1, pose: [-28, 22, 0, 0, 0.02, 0.85], color: 'blue' },
  { name: 'bud_inner_R_black', kind: 'budR', pose: [180, 0, 0, 0, 0, 2.3], color: 'black' },
  { name: 'bud_inner_L_white', kind: 'budL', pose: [180, 0, 0, 0, 0, 2.3], color: 'white' },
  { name: 'bud_top_L_black', kind: 'budL', pose: [0, -62, 0, 0, 0, 2.3], color: 'black' },
  { name: 'bud_34_R_pink', kind: 'budR', pose: [-35, 10, 0, 0, 0, 2.3], color: 'pink' },
];
for (const s of S) {
  const r = await page.evaluate(`C7FIT.shot(${JSON.stringify(s.kind)}, ${s.lid || 0}, ${JSON.stringify(s.pose)}, ${JSON.stringify(s.color)}, 512)`);
  savePng(path.join(out, 'feat_' + s.name + '.png'), r.color);
}
await page.evaluate('C7FIT.done()');
await page.close();
