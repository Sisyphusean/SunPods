// Quick look at the WF-1000XM4 model (tools only): the hero frame, the case closed and open, and one
// earbud from six sides, into one contact sheet's raw frames.
//   node look.mjs <outDir> [colour]     (X4_OVR='{"BUD":{...}}' tries shape values)
import fs from 'node:fs';
import path from 'node:path';
import { openHero, savePng } from './harness.mjs';

const [outDir, color = 'black'] = process.argv.slice(2);
fs.mkdirSync(outDir, { recursive: true });
const page = await openHero({ query: `device=wf1000xm4&color=${color}`, width: 1000, height: 712, log: true });
if (process.env.X4_OVR) await page.evaluate(`window.sunpods.debugRebuildMod('wf1000xm4', ${process.env.X4_OVR})`);
for (const [model, lid, angle] of [['buds', 0, 0], ['case', 0, 0], ['case', 1, -20]]) {
  const url = await page.evaluate(`window.sunpods.debugShot(${JSON.stringify({ device: 'wf1000xm4', color, model, lid, angle, w: 1000, h: 712 })})`);
  savePng(path.join(outDir, `hero_${model}${lid ? '_open' : ''}.png`), url);
}
await page.inject('fit_page.js');
await page.evaluate('window.X4FIT.init()');
const VIEWS = { face: [0, 0, 0], side_front: [-90, 0, 0], back: [180, 0, 0], side_back: [90, 0, 0], top: [0, 90, 0], three_q: [-35, 10, 0] };
for (const [k, p] of Object.entries(VIEWS)) {
  const r = await page.evaluate(`X4FIT.shot('budR', 0, ${JSON.stringify([...p, 0, 0, 0.5])}, '${color}', 512)`);
  savePng(path.join(outDir, `budR_${k}.png`), r.color);
}
for (const [k, lid, p] of [['case_top_open', 1, [0, 90, 0]], ['case_front_open', 1, [0, 15, 0]], ['case_end_open', 1, [90, 10, 0]], ['case_back', 0, [180, 10, 0]]]) {
  const r = await page.evaluate(`X4FIT.shot('case', ${lid}, ${JSON.stringify([...p, 0, 0, 0.62])}, '${color}', 512)`);
  savePng(path.join(outDir, `${k}.png`), r.color);
}
await page.evaluate('X4FIT.done()');
await page.close();
process.exit(0);
