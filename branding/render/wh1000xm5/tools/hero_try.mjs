// Hero pose trials (tools only): renders the hero frame for each candidate HERO override and reports
// its alpha box against the WF-1000XM5 pair's (turntable/f000.webp: 176,133,830,583).
//   node hero_try.mjs <outDir> '<[{"name":..,"HERO":{..},"HP":{..}}]>' [colour]
import fs from 'node:fs';
import path from 'node:path';
import { openHero, savePng } from './harness.mjs';
const [outDir, candArg, color = 'black'] = process.argv.slice(2);
const cands = JSON.parse(candArg);
fs.mkdirSync(outDir, { recursive: true });
const REF = [176, 133, 830, 583];
const page = await openHero({ query: 'device=wh1000xm5', width: 1000, height: 712, log: false });
for (const c of cands) {
  const { name, ...ovr } = c;
  await page.evaluate(`window.sunpods.debugRebuildMod('wh1000xm5', ${JSON.stringify(ovr)})`);
  const url = await page.evaluate(`window.sunpods.debugShot({ device: 'wh1000xm5', color: '${color}', model: 'buds', angle: 0, w: 1000, h: 712 })`);
  savePng(path.join(outDir, name + '.png'), url);
  const box = await page.evaluate(`(async () => { const i = new Image(); i.src = ${JSON.stringify(url)}; await i.decode();
    const c = document.createElement('canvas'); c.width = i.width; c.height = i.height; const g = c.getContext('2d'); g.drawImage(i, 0, 0);
    const d = g.getImageData(0, 0, c.width, c.height).data; let x0 = 1e9, y0 = 1e9, x1 = -1, y1 = -1;
    for (let y = 0; y < c.height; y++) for (let x = 0; x < c.width; x++) if (d[(y * c.width + x) * 4 + 3] > 127) { if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y; }
    return [x0, y0, x1 + 1, y1 + 1]; })()`);
  const w = box[2] - box[0], h = box[3] - box[1], rw = REF[2] - REF[0], rh = REF[3] - REF[1];
  console.log(name.padEnd(10), JSON.stringify(box), `w ${w} (${(100 * (w / rw - 1)).toFixed(1)}%) h ${h} (${(100 * (h / rh - 1)).toFixed(1)}%) centre ${((box[0] + box[2]) / 2 - (REF[0] + REF[2]) / 2).toFixed(0)},${((box[1] + box[3]) / 2 - (REF[1] + REF[3]) / 2).toFixed(0)}`);
}
await page.close();
process.exit(0);
