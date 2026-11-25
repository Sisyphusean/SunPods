// The hero frame's alpha box against the WF-1000XM5's (turntable/f000.webp: 175 132 832 585), for
// trial hero poses (tools only):  node hero_box.mjs <out.png> ['{"HERO":{...}}']
import { openHero, savePng } from './harness.mjs';
const [out, ovr] = process.argv.slice(2);
const page = await openHero({ query: 'device=wf1000xm4', width: 1000, height: 712, log: false });
if (ovr) await page.evaluate(`window.sunpods.debugRebuildMod('wf1000xm4', ${ovr})`);
const url = await page.evaluate(`window.sunpods.debugShot({ device: 'wf1000xm4', model: 'buds', color: 'black', angle: 0, w: 1000, h: 712 })`);
if (out) savePng(out, url);
const box = await page.evaluate(`(async () => { const i = new Image(); i.src = ${JSON.stringify(url)}; await i.decode();
  const c = document.createElement('canvas'); c.width = i.width; c.height = i.height; const g = c.getContext('2d'); g.drawImage(i, 0, 0);
  const d = g.getImageData(0, 0, c.width, c.height).data; let x0 = 1e9, y0 = 1e9, x1 = -1, y1 = -1;
  for (let y = 0; y < c.height; y++) for (let x = 0; x < c.width; x++) if (d[(y * c.width + x) * 4 + 3] > 0) { if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y; }
  return [x0, y0, x1 + 1, y1 + 1]; })()`);
const ref = [175, 132, 832, 585];
const w = box[2] - box[0], h = box[3] - box[1], rw = ref[2] - ref[0], rh = ref[3] - ref[1];
console.log('box', box.join(' '), 'size', w, h, 'vs XM5', rw, rh, `(${(100 * (w / rw - 1)).toFixed(1)}%, ${(100 * (h / rh - 1)).toFixed(1)}%)`,
  'edges off', box.map((v, i) => v - ref[i]).join(' '));
await page.close(); process.exit(0);
