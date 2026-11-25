// Seat pose search (tools only): node seat_find.mjs ['<json opts>']   (see seat_search.js)
import { openHero } from './harness.mjs';
const opts = process.argv[2] || '{}';
const page = await openHero({ query: 'device=wf1000xm4', width: 256, height: 256, log: false });
if (process.env.X4_OVR) await page.evaluate(`window.sunpods.debugRebuildMod('wf1000xm4', ${process.env.X4_OVR})`);
await page.inject('seat_search.js');
const t0 = Date.now();
const r = await page.evaluate(`window.X4SEAT(${opts})`);
console.log('candidates', r.n, `${Date.now() - t0} ms`);
for (const b of r.best || [r]) console.log(JSON.stringify(b));
await page.close();
process.exit(0);
