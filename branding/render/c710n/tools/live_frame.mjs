// Live hero framing of both devices (debugShot, the app's camera): alpha boxes (tools only).
import { openHero, savePng } from './harness.mjs';
const out = process.argv[2];
const page = await openHero({ query: '', width: 1000, height: 712, log: false });
for (const dev of ['xm5', 'c710n']) for (const [model, lid] of [['buds', 0], ['case', 0], ['case', 1]]) {
  const url = await page.evaluate(`window.sunpods.debugShot({ device: '${dev}', model: '${model}', lid: ${lid}, color: 'black', angle: 0, w: 1000, h: 712 })`);
  savePng(`${out}_${dev}_${model}${lid}.png`, url);
}
await page.close(); process.exit(0);
