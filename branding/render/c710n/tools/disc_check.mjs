import fs from 'node:fs';
import path from 'node:path';
import { openHero, C7DIR } from './harness.mjs';
const [a, b] = process.argv.slice(2);
const fa = JSON.parse(fs.readFileSync(a, 'utf8')), fb = JSON.parse(fs.readFileSync(b, 'utf8'));
const page = await openHero({ query: 'device=c710n', width: 256, height: 256, log: false });
await page.inject('fit_page.js');
await page.evaluate('window.C7FIT.init()');
for (const id of ['front_R_white', 'front_R', 'front_L']) {
  const subj = id === 'front_L' ? 'budL' : 'budR';
  const pts = [[0, 0, 0], [0.144 * 1.9 * 0, -0.4 * 1.9, 0]];
  const pa = await page.evaluate(`C7FIT.project('${subj}', 0, ${JSON.stringify(fa[id].pose)}, ${JSON.stringify(pts)}, 512)`);
  const pb = await page.evaluate(`C7FIT.project('${subj}', 0, ${JSON.stringify(fb[id].pose)}, ${JSON.stringify(pts)}, 512)`);
  console.log(id, 'disc centre with iter16 camera', pa[0].map(Math.round), ' with re-fitted camera', pb[0].map(Math.round));
}
await page.close(); process.exit(0);
