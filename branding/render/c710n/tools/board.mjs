// Review board renders for one iteration: fits the camera of every review angle (refs.json) to Sony's
// silhouette, then renders the model there in the reference's colour, plus its silhouette.
//   node board.mjs <iterDir> [buds|case|all] [--refit]
// Sony's 360 viewer frames (spinNN) are fitted together as one turntable (shared elevation, roll and
// scale; yaw = yaw0 + the frame's measured turntable angle). Other angles are fitted one by one.
// Writes <iterDir>/<id>_model.png, <id>_mask.png and fits.json (pose and IoU per angle); later runs
// start from the last fits (_fit/fits_latest.json) unless --refit.
import fs from 'node:fs';
import path from 'node:path';
import { openHero, savePng, C7DIR } from './harness.mjs';

const [iterDir, which = 'all', ...flags] = process.argv.slice(2);
const refit = flags.includes('--refit');
fs.mkdirSync(iterDir, { recursive: true });
const meta = JSON.parse(fs.readFileSync(path.join(C7DIR, '_fit', 'refs', 'meta.json'), 'utf8'));
const spinFit = JSON.parse(fs.readFileSync(path.join(C7DIR, '_fit', 'spin_fit.json'), 'utf8'));
const prevFile = path.join(C7DIR, '_fit', 'fits_latest.json');
const prev = !refit && fs.existsSync(prevFile) ? JSON.parse(fs.readFileSync(prevFile, 'utf8')) : {};
const page = await openHero({ query: 'device=c710n', width: 512, height: 512, log: false });
await page.inject('fit_page.js');
await page.evaluate('window.C7FIT.init()');
const fits = {};
const t0 = Date.now();
const mask64 = (id) => fs.readFileSync(path.join(C7DIR, '_fit', 'refs', id + '_mask.png')).toString('base64');
async function render(id, r, pose) {
  const shot = await page.evaluate(`C7FIT.shot(${JSON.stringify(r.subject)}, ${r.lid || 0}, ${JSON.stringify(pose)}, ${JSON.stringify(r.color)}, 512)`);
  savePng(path.join(iterDir, id + '_model.png'), shot.color);
  savePng(path.join(iterDir, id + '_mask.png'), shot.mask);
}
// turntable group
const spinIds = Object.keys(meta).filter((k) => k.startsWith('spin') && (which === 'all' || meta[k].group === which));
if (spinIds.length) {
  const items = spinIds.map((id) => {
    const i = spinFit.files.indexOf(path.basename(meta[id].img));
    return { b64: mask64(id), phi: (process.env.SPIN_SIGN === "-1" ? -1 : 1) * spinFit.angles_deg[i] };
  });
  const init = prev.spin03 ? [prev.spin03.pose[0] - items[0].phi, prev.spin03.pose[1], prev.spin03.pose[2]] : [10, 0, 0];
  const g = await page.evaluate(`C7FIT.fitGroup('budR', ${JSON.stringify(items)}, ${JSON.stringify(init)})`);
  console.log('turntable: yaw0', g.shared[0].toFixed(2), 'elev', g.shared[1].toFixed(2), 'roll', g.shared[2].toFixed(2), 'mean IoU', g.mean.toFixed(4), `${((Date.now() - t0) / 1000).toFixed(0)}s`);
  for (let k = 0; k < spinIds.length; k++) {
    const id = spinIds[k];
    fits[id] = { pose: g.poses[k], iou: g.per[k], subject: 'budR', group: meta[id].group, turntable: g.shared };
    await render(id, meta[id], g.poses[k]);
    console.log(id.padEnd(14), 'IoU', g.per[k].toFixed(4));
  }
}
for (const [id, r] of Object.entries(meta)) {
  if (id.startsWith('spin')) continue;
  if (which !== 'all' && r.group !== which) continue;
  const b64 = mask64(id);
  const lid = r.lid || 0;
  const lock = r.lockPitch !== undefined ? `, lockPitch: ${r.lockPitch}` : '';
  const res = prev[id]
    ? await page.evaluate(`C7FIT.fit(${JSON.stringify(r.subject)}, ${lid}, ${JSON.stringify(b64)}, ${JSON.stringify(prev[id].pose.slice(0, 3))}, { starts: [[0,0,0]], iters: 120${lock} })`)
    : await page.evaluate(`C7FIT.fit(${JSON.stringify(r.subject)}, ${lid}, ${JSON.stringify(b64)}, ${JSON.stringify(r.init)}${lock ? ', {' + lock.slice(1) + '}' : ''})`);
  await render(id, r, res.pose);
  fits[id] = { ...res, subject: r.subject, group: r.group };
  console.log(id.padEnd(14), 'IoU', res.iou.toFixed(4), 'pose', res.pose.map((v) => v.toFixed(2)).join(' '), `${((Date.now() - t0) / 1000).toFixed(0)}s`);
}
await page.evaluate('C7FIT.done()');
const all = { ...(fs.existsSync(prevFile) ? JSON.parse(fs.readFileSync(prevFile, 'utf8')) : {}), ...fits };
fs.writeFileSync(prevFile, JSON.stringify(all, null, 1));
fs.writeFileSync(path.join(iterDir, 'fits.json'), JSON.stringify(fits, null, 1));
await page.close();
