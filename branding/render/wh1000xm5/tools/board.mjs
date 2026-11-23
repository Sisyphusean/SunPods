// Review board renders for one iteration (tools only): fits the camera of every review angle
// (refs.json) to Sony's silhouette, then renders the model there in the reference's colour, plus its
// silhouette.   node board.mjs <iterDir> [phones|case|all] [--refit]
// Writes <iterDir>/<id>_model.png, <id>_mask.png and fits.json (pose and IoU per angle); later runs
// start from the last fits (_fit/fits_latest.json) unless --refit. XM_OVR='{...}' tries shape values.
import fs from 'node:fs';
import path from 'node:path';
import { openHero, savePng, XMDIR } from './harness.mjs';

const [iterDir, which = 'all', ...flags] = process.argv.slice(2);
const refit = flags.includes('--refit');
fs.mkdirSync(iterDir, { recursive: true });
const meta = JSON.parse(fs.readFileSync(path.join(XMDIR, '_fit', 'refs', 'meta.json'), 'utf8'));
const prevFile = path.join(XMDIR, '_fit', 'fits_latest.json');
const prev = !refit && fs.existsSync(prevFile) ? JSON.parse(fs.readFileSync(prevFile, 'utf8')) : {};
const page = await openHero({ query: 'device=wh1000xm5', width: 512, height: 512, log: false });
await page.inject('fit_page.js');
await page.evaluate('window.XMFIT.init()');
if (process.env.XM_OVR) await page.evaluate(`XMFIT.rebuild(${process.env.XM_OVR})`);
const fits = {};
const t0 = Date.now();
const mask64 = (id) => fs.readFileSync(path.join(XMDIR, '_fit', 'refs', id + '_mask.png')).toString('base64');
for (const [id, r] of Object.entries(meta)) {
  if (which !== 'all' && r.group !== which && !which.split(',').includes(id)) continue;
  const b64 = mask64(id), lid = r.lid || 0, hp = r.hp || null;
  const o = prev[id] ? `{ starts: [[0,0,0]], iters: 120, pose: ${JSON.stringify(hp)} }` : `{ pose: ${JSON.stringify(hp)} }`;
  const res = await page.evaluate(`XMFIT.fit(${JSON.stringify(r.subject)}, ${lid}, ${JSON.stringify(b64)}, ${JSON.stringify(prev[id] ? prev[id].pose.slice(0, 3) : r.init)}, ${o})`);
  const shot = await page.evaluate(`XMFIT.shot(${JSON.stringify(r.subject)}, ${lid}, ${JSON.stringify(res.pose)}, ${JSON.stringify(r.color)}, 512, ${JSON.stringify(hp)})`);
  savePng(path.join(iterDir, id + '_model.png'), shot.color);
  savePng(path.join(iterDir, id + '_mask.png'), shot.mask);
  fits[id] = { ...res, subject: r.subject, group: r.group };
  console.log(id.padEnd(12), 'IoU', res.iou.toFixed(4), 'pose', res.pose.map((v) => v.toFixed(2)).join(' '), `${((Date.now() - t0) / 1000).toFixed(0)}s`);
}
await page.evaluate('XMFIT.done()');
const all = { ...(fs.existsSync(prevFile) ? JSON.parse(fs.readFileSync(prevFile, 'utf8')) : {}), ...fits };
fs.writeFileSync(prevFile, JSON.stringify(all, null, 1));
fs.writeFileSync(path.join(iterDir, 'fits.json'), JSON.stringify(fits, null, 1));
await page.close();
process.exit(0);
