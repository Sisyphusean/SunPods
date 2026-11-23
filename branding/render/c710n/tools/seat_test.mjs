// Collision check of seat poses (closed lid): node seat_test.mjs '<json list of {tilt,pos}>'  (tools only)
import { openHero } from './harness.mjs';
const trials = JSON.parse(process.argv[2]);
const page = await openHero({ query: 'device=c710n', width: 256, height: 256, log: false });
await page.inject('seat_search.js');
for (const t of trials) {
  const r = await page.evaluate(`window.C7SEAT({ test: ${JSON.stringify(t)} })`);
  console.log(JSON.stringify(t), JSON.stringify(r));
}
await page.close();
process.exit(0);
