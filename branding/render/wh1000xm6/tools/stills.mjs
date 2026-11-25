import fs from 'node:fs';
import path from 'node:path';
import { openHero, savePng } from './harness.mjs';

const [outDir, ...cols] = process.argv.slice(2);
const colours = cols.length ? cols : ['black', 'silver', 'blue', 'pink'];
fs.mkdirSync(outDir, { recursive: true });
const POSE = {
  side: ['phones', 0, [-40, 10, 0, 0, 0, 1.0]],
  front: ['phones', 0, [0, 6, 0, 0, 0, 1.0]],
  case: ['case', 0, [-30, 38, 0, 0, 0, 1.0]],
};
const page = await openHero({ query: 'device=wh1000xm6', width: 1000, height: 712, log: false });
if (process.env.XM_OVR) await page.evaluate(`window.sunpods.debugRebuildMod('wh1000xm6', ${process.env.XM_OVR})`);
for (const c of colours) {
  const url = await page.evaluate(`window.sunpods.debugShot({ device: 'wh1000xm6', model: 'buds', color: '${c}', angle: 0, w: 2000, h: 1424 })`);
  savePng(path.join(outDir, `pair_${c}.png`), url);
}
await page.inject('fit_page.js');
await page.evaluate('window.XMFIT.init()');
for (const c of colours) {
  for (const [k, [subject, lid, pose]] of Object.entries(POSE)) {
    const sz = await page.evaluate(`XMFIT.useSubject('${subject}', ${lid}).size`);
    const p = pose.slice(); p[5] = 0.8 * 7.37 / Math.hypot(...sz);
    const r = await page.evaluate(`XMFIT.shot('${subject}', ${lid}, ${JSON.stringify(p)}, '${c}', 1400)`);
    savePng(path.join(outDir, `${k}_${c}.png`), r.color);
  }
  console.log('rendered', c);
}
await page.evaluate('XMFIT.done()');
await page.close();
process.exit(0);
