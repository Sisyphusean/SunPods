// Close-up review renders (tools only): the model at each review angle's fitted camera at 3x the
// board size (1536 px), plus where the ear tip and the mic grille land (in 512-px board space), for
// closeup.py.   node closeup.mjs <outDir> [ids...]
import fs from 'node:fs';
import path from 'node:path';
import { openHero, savePng, C7DIR } from './harness.mjs';

const [outDir, ...idsArg] = process.argv.slice(2);
const ids = idsArg.length ? idsArg : ['side_L', 'side_L_pink', 'front_R', 'front_L', 'front_R_white', 'spin07', 'spin09', 'spin10'];
fs.mkdirSync(outDir, { recursive: true });
const meta = JSON.parse(fs.readFileSync(path.join(C7DIR, '_fit', 'refs', 'meta.json'), 'utf8'));
const fits = JSON.parse(fs.readFileSync(path.join(C7DIR, '_fit', 'fits_latest.json'), 'utf8'));
const page = await openHero({ query: 'device=c710n', width: 512, height: 512, log: false });
await page.inject('fit_page.js');
await page.evaluate('window.C7FIT.init()');
// optional trial values (tools only): C7_OVR='{"BUD":{...}}'
if (process.env.C7_OVR) await page.evaluate(`C7FIT.rebuild(${process.env.C7_OVR})`);
const where = {};
for (const id of ids) {
  const m = meta[id], pose = fits[id].pose;
  const r = await page.evaluate(`C7FIT.shot(${JSON.stringify(m.subject)}, 0, ${JSON.stringify(pose)}, ${JSON.stringify(m.color)}, 1536)`);
  savePng(path.join(outDir, `${id}_model.png`), r.color);
  // feature centres in the earbud's own frame (their meshes carry no transform of their own)
  const pts = await page.evaluate(`(async () => { const d = await window.sunpods.debugC7(false); const b = d.C7.buds.${m.subject};
    const c = (n) => { const o = b.getObjectByName(n); o.geometry.computeBoundingBox(); return o.geometry.boundingBox.getCenter(new d.THREE.Vector3()).toArray(); };
    return [c('C7_Tip'), c('C7_Grille')]; })()`);
  where[id] = await page.evaluate(`C7FIT.project(${JSON.stringify(m.subject)}, 0, ${JSON.stringify(pose)}, ${JSON.stringify(pts)}, 512)`);
  console.log(id, JSON.stringify(where[id].map((p) => p.map((v) => Math.round(v)))));
}
fs.writeFileSync(path.join(outDir, 'where.json'), JSON.stringify(where, null, 1));
await page.evaluate('C7FIT.done()');
await page.close();
process.exit(0);
