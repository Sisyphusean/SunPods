// Spin check (tools only): the hero pair at every 15 degrees of the drag spin, as the app shows it.
//   node spin_sheet.mjs <out.png> [device] [colour]
import fs from 'node:fs';
import { openHero, savePng } from './harness.mjs';
const [out, device = 'c710n', color = 'black'] = process.argv.slice(2);
const page = await openHero({ query: `device=${device}`, width: 712, height: 506, log: false });
const dir = out.replace(/\.png$/, '');
fs.mkdirSync(dir, { recursive: true });
for (let a = 0; a < 360; a += 15) {
  const url = await page.evaluate(`window.sunpods.debugShot({ device: '${device}', model: 'buds', color: '${color}', angle: ${a}, w: 400, h: 285 })`);
  savePng(`${dir}/a${String(a).padStart(3, '0')}.png`, url);
}
await page.close(); process.exit(0);
