// Hero-frame shots of the WH-1000XM5 (tools only) and their alpha boxes, for placing the hero:
//   node hero_shot.mjs <outDir> [specs json]   specs: [{ name, color, model, angle, lid }]
//   XM_OVR='{"HERO":{...},"HP":{...}}' tries values without editing wh1000xm5.js
import fs from 'node:fs';
import path from 'node:path';
import { openHero, savePng } from './harness.mjs';

const [outDir, specArg] = process.argv.slice(2);
const specs = specArg ? JSON.parse(specArg) : [{ name: 'hero_black', color: 'black', model: 'buds', angle: 0, lid: 0 }];
fs.mkdirSync(outDir, { recursive: true });
const page = await openHero({ query: 'device=wh1000xm5', width: 1000, height: 712, log: true });
if (process.env.XM_OVR) await page.evaluate(`window.sunpods.debugRebuildMod('wh1000xm5', ${process.env.XM_OVR})`);
for (const s of specs) {
  const url = await page.evaluate(`window.sunpods.debugShot(${JSON.stringify({ device: 'wh1000xm5', w: 1000, h: 712, ...s })})`);
  savePng(path.join(outDir, `${s.name}.png`), url);
  const box = await page.evaluate(`(async () => { const i = new Image(); i.src = ${JSON.stringify(url)}; await i.decode();
    const c = document.createElement('canvas'); c.width = i.width; c.height = i.height; const g = c.getContext('2d'); g.drawImage(i, 0, 0);
    const d = g.getImageData(0, 0, c.width, c.height).data; let x0 = 1e9, y0 = 1e9, x1 = -1, y1 = -1;
    for (let y = 0; y < c.height; y++) for (let x = 0; x < c.width; x++) if (d[(y * c.width + x) * 4 + 3] > 127) { if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y; }
    return [x0, y0, x1 + 1, y1 + 1]; })()`);
  console.log(s.name, 'alpha box', JSON.stringify(box), 'size', box[2] - box[0], 'x', box[3] - box[1]);
}
await page.close();
process.exit(0);
