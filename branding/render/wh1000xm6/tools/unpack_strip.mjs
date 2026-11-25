import fs from 'node:fs';
import path from 'node:path';
import { openHero, savePng } from './harness.mjs';

const [outDir] = process.argv.slice(2);
fs.mkdirSync(outDir, { recursive: true });
const page = await openHero({ query: 'device=wh1000xm6', width: 512, height: 512, log: false });
const steps = [1.0, 0.75, 0.5, 0.25, 0.0];
for (let i = 0; i < steps.length; i++) {
  const p = steps[i];
  const url = await page.evaluate(`window.sunpods.debugShot({ device: 'wh1000xm6', caseProgress: ${p}, color: 'black', angle: 0, w: 512, h: 512 })`);
  savePng(path.join(outDir, `unpack_${i}_${p.toFixed(2)}.png`), url);
}
await page.close();
process.exit(0);
