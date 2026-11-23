// Review board renders at fixed cameras (tools only): every review angle of a group rendered at the
// camera in _fit/fits_latest.json without re-fitting (so the earbud body stays where it was fitted).
//   node board_fixed.mjs <iterDir> [buds|case]
import fs from 'node:fs';
import path from 'node:path';
import { openHero, savePng, C7DIR } from './harness.mjs';
const [iterDir, group = 'buds'] = process.argv.slice(2);
fs.mkdirSync(iterDir, { recursive: true });
const meta = JSON.parse(fs.readFileSync(path.join(C7DIR, '_fit', 'refs', 'meta.json'), 'utf8'));
const fitsAll = JSON.parse(fs.readFileSync(path.join(C7DIR, '_fit', 'fits_latest.json'), 'utf8'));
const page = await openHero({ query: 'device=c710n', width: 512, height: 512, log: false });
await page.inject('fit_page.js');
await page.evaluate('window.C7FIT.init()');
const fits = {};
for (const [id, r] of Object.entries(meta)) {
  if (r.group !== group || !fitsAll[id]) continue;
  const pose = fitsAll[id].pose;
  const shot = await page.evaluate(`C7FIT.shot(${JSON.stringify(r.subject)}, ${r.lid || 0}, ${JSON.stringify(pose)}, ${JSON.stringify(r.color)}, 512)`);
  savePng(path.join(iterDir, id + '_model.png'), shot.color);
  savePng(path.join(iterDir, id + '_mask.png'), shot.mask);
  fits[id] = { ...fitsAll[id], iou: 0 };
}
fs.writeFileSync(path.join(iterDir, 'fits.json'), JSON.stringify(fits, null, 1));
await page.evaluate('C7FIT.done()');
await page.close();
process.exit(0);
