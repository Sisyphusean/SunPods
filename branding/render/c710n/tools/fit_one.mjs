// Multi-start fit of one review angle, printing every start's result (tools only).
//   node fit_one.mjs <id> '[[yaw,pitch,roll],...]'
import fs from 'node:fs';
import path from 'node:path';
import { openHero, C7DIR } from './harness.mjs';
const [id, startsJson] = process.argv.slice(2);
const meta = JSON.parse(fs.readFileSync(path.join(C7DIR, '_fit', 'refs', 'meta.json'), 'utf8'));
const r = meta[id];
const page = await openHero({ query: 'device=c710n', width: 512, height: 512, log: false });
await page.inject('fit_page.js');
await page.evaluate('window.C7FIT.init()');
const b64 = fs.readFileSync(path.join(C7DIR, '_fit', 'refs', id + '_mask.png')).toString('base64');
for (const st of JSON.parse(startsJson)) {
  const res = await page.evaluate(`C7FIT.fit(${JSON.stringify(r.subject)}, ${r.lid || 0}, ${JSON.stringify(b64)}, ${JSON.stringify(st)}, { starts: [[0,0,0]], iters: 160 })`);
  console.log(JSON.stringify(st), 'IoU', res.iou.toFixed(4), 'pose', res.pose.map((v) => v.toFixed(2)).join(' '));
}
await page.evaluate('C7FIT.done()');
await page.close();
process.exit(0);
