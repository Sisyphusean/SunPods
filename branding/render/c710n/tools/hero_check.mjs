// Renders the live hero (buds and closed case) for the WF-C710N and the XM5 at 1000x712 through
// debugShot, for framing checks against the XM5 (bounding boxes printed by the caller).
//   node hero_check.mjs <outDir> [color]
import path from 'node:path';
import { openHero, savePng } from './harness.mjs';
const [out, color = 'black'] = process.argv.slice(2);
const page = await openHero({ query: 'device=c710n', width: 1000, height: 712, log: false });
for (const [dev, model, col, lid, angle] of [['c710n', 'buds', color, 0, 0], ['xm5', 'buds', 'black', 0, 0], ['c710n', 'case', color, 0, 0], ['xm5', 'case', 'black', 0, 0], ['c710n', 'case', color, 1, -20], ['xm5', 'case', 'black', 1, -20]]) {
  const url = await page.evaluate(`window.sunpods.debugShot(${JSON.stringify({ device: dev, color: col, model, lid, angle, w: 1000, h: 712 })})`);
  savePng(path.join(out, `${dev}_${model}${lid ? '_open' : ''}.png`), url);
}
await page.close();
