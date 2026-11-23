import { openHero } from './harness.mjs';
const page = await openHero({ query: 'device=c710n', width: 256, height: 256, log: false });
await page.inject('seat_search.js');
const t0 = Date.now();
const r = await page.evaluate(`window.C7SEAT(${process.argv[2] || '{ "mode": "tipdown" }'})`);
console.log(JSON.stringify(r, null, 0).replace(/\},\{/g, '},\n{'), ((Date.now() - t0) / 1000).toFixed(0) + 's');
await page.close(); process.exit(0);
