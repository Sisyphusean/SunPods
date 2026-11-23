// XM5 regression shots (tools only): the XM5 path rendered through debugShot, to compare the current
// hero3d.js with git HEAD's.   node xm5_regress.mjs <outPrefix>   (HERO3D_DIR picks the page copy)
import { openHero, savePng } from './harness.mjs';
const out = process.argv[2];
const page = await openHero({ query: '', width: 1000, height: 712, log: false });
const shots = [];
for (const color of ['black', 'silver', 'pink']) for (const [model, lid, angle] of [['buds', 0, 0], ['buds', 0, 45], ['case', 0, 0], ['case', 1, -20]]) shots.push({ color, model, lid, angle });
for (const s of shots) {
  const url = await page.evaluate(`window.sunpods.debugShot(${JSON.stringify({ ...s, w: 1000, h: 712 })})`);
  savePng(`${out}_${s.color}_${s.model}${s.lid}_${s.angle}.png`, url);
}
console.log('shots', shots.length);
await page.close(); process.exit(0);
