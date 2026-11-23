// Renders one review angle for several model overrides (tools only).
//   node dbg_var.mjs <id> <outPrefix> '<json [overrides...]>'
import fs from 'node:fs';
import path from 'node:path';
import { openHero, savePng, C7DIR } from './harness.mjs';
const [id, out, json] = process.argv.slice(2);
const meta = JSON.parse(fs.readFileSync(path.join(C7DIR, '_fit', 'refs', 'meta.json'), 'utf8'))[id];
const fit = JSON.parse(fs.readFileSync(path.join(C7DIR, '_fit', 'fits_latest.json'), 'utf8'))[id];
const page = await openHero({ query: 'device=c710n', width: 512, height: 512, log: false });
await page.inject('fit_page.js');
await page.evaluate('window.C7FIT.init()');
let n = 0;
for (const o of JSON.parse(json)) {
  await page.evaluate(`C7FIT.rebuild(${JSON.stringify(o)})`);
  const r = await page.evaluate(`C7FIT.shot(${JSON.stringify(meta.subject)}, ${meta.lid || 0}, ${JSON.stringify(fit.pose)}, ${JSON.stringify(meta.color)}, 512)`);
  savePng(`${out}_${n++}.png`, r.color);
}
await page.close();
process.exit(0);
