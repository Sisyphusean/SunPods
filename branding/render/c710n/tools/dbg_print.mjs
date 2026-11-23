import fs from 'node:fs';
import path from 'node:path';
import { openHero, savePng, C7DIR } from './harness.mjs';
const [out] = process.argv.slice(2);
const fits = JSON.parse(fs.readFileSync(path.join(C7DIR, '_fit', 'fits_latest.json'), 'utf8'));
const page = await openHero({ query: 'device=c710n', width: 512, height: 512, log: false });
await page.inject('fit_page.js');
await page.evaluate('window.C7FIT.init()');
let n = 0;
for (const side of ['FrontSide', 'BackSide']) {
  await page.evaluate(`(async () => { const d = await window.sunpods.debugC7(false); d.C7.M.print.side = d.THREE.${side}; d.C7.M.print.needsUpdate = true; })()`);
  for (const id of ['spin10', 'spin03']) {
    const r = await page.evaluate(`C7FIT.shot('budR', 0, ${JSON.stringify(fits[id].pose)}, 'blue', 512)`);
    savePng(`${out}_${side}_${id}.png`, r.color); n++;
  }
}
await page.close(); process.exit(0);
