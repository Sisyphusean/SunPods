// The hero pair at given spin angles (tools only):  node hero_at.mjs <outDir> <colour> <angle...>
import fs from 'node:fs';
import { openHero, savePng } from './harness.mjs';
const [out, color, ...angles] = process.argv.slice(2);
fs.mkdirSync(out, { recursive: true });
const page = await openHero({ query: 'device=c710n', width: 712, height: 506, log: false });
for (const a of angles) savePng(`${out}/a${a}.png`, await page.evaluate(`window.sunpods.debugShot({ device: 'c710n', model: 'buds', color: '${color}', angle: ${a}, w: 1400, h: 997 })`));
await page.close(); process.exit(0);
