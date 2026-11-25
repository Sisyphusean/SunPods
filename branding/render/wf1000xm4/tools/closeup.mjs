// Close-up review renders (tools only): the model at each review angle's fitted camera at 3x the
// board size (1536 px), plus where its features land (in 512 px board space) for closeup.py:
// the earbud's middle, the ear tip, the copper microphone, the slit microphone and the SONY print.
//   node closeup.mjs <outDir> [ids...]      (X4_OVR='{"BUD":{...}}' tries shape values)
import fs from 'node:fs';
import path from 'node:path';
import { openHero, savePng, X4DIR } from './harness.mjs';

const [outDir, ...idsArg] = process.argv.slice(2);
const meta = JSON.parse(fs.readFileSync(path.join(X4DIR, '_fit', 'refs', 'meta.json'), 'utf8'));
const fits = JSON.parse(fs.readFileSync(path.join(X4DIR, '_fit', 'fits_latest.json'), 'utf8'));
const ids = idsArg.length ? idsArg : Object.keys(meta);
fs.mkdirSync(outDir, { recursive: true });
const page = await openHero({ query: 'device=wf1000xm4', width: 512, height: 512, log: false });
await page.inject('fit_page.js');
await page.evaluate('window.X4FIT.init()');
if (process.env.X4_OVR) await page.evaluate(`X4FIT.rebuild(${process.env.X4_OVR})`);
const where = {};
for (const id of ids) {
  const m = meta[id], pose = fits[id].pose;
  const r = await page.evaluate(`X4FIT.shot(${JSON.stringify(m.subject)}, 0, ${JSON.stringify(pose)}, ${JSON.stringify(m.color)}, 1536)`);
  savePng(path.join(outDir, `${id}_model.png`), r.color);
  // feature centres in the earbud's own frame (their meshes carry no transform of their own)
  const pts = await page.evaluate(`(async () => { const d = await window.sunpods.debugMod('wf1000xm4', false); const b = d.M.buds.${m.subject};
    const c = (n) => { const o = b.getObjectByName(n); o.geometry.computeBoundingBox(); return o.geometry.boundingBox.getCenter(new d.THREE.Vector3()).toArray(); };
    return [c('X4_BudShell'), c('X4_Tip'), c('X4_BossFace'), c('X4_Slit'), c('X4_Logo')]; })()`);
  where[id] = await page.evaluate(`X4FIT.project(${JSON.stringify(m.subject)}, 0, ${JSON.stringify(pose)}, ${JSON.stringify(pts)}, 512)`);
  console.log(id, JSON.stringify(where[id].map((p) => p.map((v) => Math.round(v)))));
}
fs.writeFileSync(path.join(outDir, 'where.json'), JSON.stringify(where, null, 1));
await page.evaluate('X4FIT.done()');
await page.close();
process.exit(0);
