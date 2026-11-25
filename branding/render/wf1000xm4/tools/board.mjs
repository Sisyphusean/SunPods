// Review board renders for one iteration (tools only): fits the camera of every review angle
// (_fit/refs/meta.json, from prep_refs.py) to Sony's silhouette, then renders the model there in the
// reference's colour, plus its silhouette.
//   node board.mjs <iterDir> [--refit] [--lockpitch=deg] [--nosave] [ids...]      (X4_OVR='{"BUD":{...}}' tries shape values)
// Writes <iterDir>/<id>_model.png, <id>_mask.png and fits.json (pose and IoU per angle); later runs
// start from the last fits (_fit/fits_latest.json) unless --refit.
import fs from 'node:fs';
import path from 'node:path';
import { openHero, savePng, X4DIR } from './harness.mjs';

const [iterDir, ...rest] = process.argv.slice(2);
const refit = rest.includes('--refit');
const only = rest.filter((a) => !a.startsWith('--'));
const lockArg = rest.find((a) => a.startsWith('--lockpitch='));
const lockAll = lockArg ? `, lockPitch: ${parseFloat(lockArg.split('=')[1])}` : '';   // hold the pitch (deg) while fitting
const save = !rest.includes('--nosave');
fs.mkdirSync(iterDir, { recursive: true });
const meta = JSON.parse(fs.readFileSync(path.join(X4DIR, '_fit', 'refs', 'meta.json'), 'utf8'));
const prevFile = path.join(X4DIR, '_fit', 'fits_latest.json');
const prev = !refit && fs.existsSync(prevFile) ? JSON.parse(fs.readFileSync(prevFile, 'utf8')) : {};
const page = await openHero({ query: 'device=wf1000xm4', width: 512, height: 512, log: false });
await page.inject('fit_page.js');
await page.evaluate('window.X4FIT.init()');
if (process.env.X4_OVR) await page.evaluate(`X4FIT.rebuild(${process.env.X4_OVR})`);
const fits = {};
const t0 = Date.now();
for (const [id, r] of Object.entries(meta)) {
  if (only.length && !only.includes(id)) continue;
  const lock = lockArg ? lockAll : r.lockPitch !== undefined ? `, lockPitch: ${r.lockPitch}` : '';
  const b64 = fs.readFileSync(path.join(X4DIR, '_fit', 'refs', id + '_mask.png')).toString('base64');
  const res = prev[id]
    ? await page.evaluate(`X4FIT.fit(${JSON.stringify(r.subject)}, 0, ${JSON.stringify(b64)}, ${JSON.stringify(prev[id].pose.slice(0, 3))}, { starts: [[0,0,0]], iters: 120${lock} })`)
    : await page.evaluate(`X4FIT.fit(${JSON.stringify(r.subject)}, 0, ${JSON.stringify(b64)}, ${JSON.stringify(r.init)}, { starts: [[0,0,0],[25,0,0],[-25,0,0],[0,20,0],[0,-20,0],[0,0,20],[0,0,-20]]${lock} })`);
  const shot = await page.evaluate(`X4FIT.shot(${JSON.stringify(r.subject)}, 0, ${JSON.stringify(res.pose)}, ${JSON.stringify(r.color)}, 512)`);
  savePng(path.join(iterDir, id + '_model.png'), shot.color);
  savePng(path.join(iterDir, id + '_mask.png'), shot.mask);
  fits[id] = { ...res, subject: r.subject };
  console.log(id.padEnd(14), 'IoU', res.iou.toFixed(4), 'pose', res.pose.map((v) => v.toFixed(2)).join(' '), `${((Date.now() - t0) / 1000).toFixed(0)}s`);
}
await page.evaluate('X4FIT.done()');
const all = { ...(fs.existsSync(prevFile) ? JSON.parse(fs.readFileSync(prevFile, 'utf8')) : {}), ...fits };
if (save) fs.writeFileSync(prevFile, JSON.stringify(all, null, 1));
fs.writeFileSync(path.join(iterDir, 'fits.json'), JSON.stringify(fits, null, 1));
await page.close();
process.exit(0);
