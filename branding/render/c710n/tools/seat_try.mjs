// Earbud seat check: renders the open case (open_white fitted camera) for trial seat poses and
// writes a strip beside Sony's photo.   node seat_try.mjs <outDir> <trials.json>
// trials: [{ "name": "...", "seat": { "pos": [...], "tilt": [...] } }]
import fs from 'node:fs';
import path from 'node:path';
import { openHero, C7DIR, savePng } from './harness.mjs';
const [out, trialsFile] = process.argv.slice(2);
const trials = JSON.parse(fs.readFileSync(trialsFile, 'utf8'));
const fits = JSON.parse(fs.readFileSync(path.join(C7DIR, '_fit', 'fits_latest.json'), 'utf8'));
fs.mkdirSync(out, { recursive: true });
const page = await openHero({ query: 'device=c710n', width: 512, height: 512, log: false });
await page.inject('fit_page.js');
await page.evaluate('window.C7FIT.init()');
for (const t of trials) {
  await page.evaluate(`C7FIT.rebuild(${JSON.stringify({ CASE: { budSeat: t.seat, ...(t.well ? { well: t.well } : {}) } })})`);
  for (const id of ['open_white', 'open_front']) {
    const r = await page.evaluate(`C7FIT.shot('case', 1, ${JSON.stringify(fits[id].pose)}, ${JSON.stringify(id === 'open_white' ? 'white' : 'black')}, 512)`);
    savePng(path.join(out, `${t.name}_${id}.png`), r.color);
  }
  console.log('rendered', t.name);
}
await page.close();
