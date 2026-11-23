// Fits the mic grille's placement so its outline reads as Sony's does in the studio views (tools
// only): for each trial (spot, aspect, turn), the oval's outline is projected into front_R_white and
// side_L and its aspect and tilt measured (PCA), against targets read off Sony's photos.
import fs from 'node:fs';
import path from 'node:path';
import { openHero, C7DIR } from './harness.mjs';
const fits = JSON.parse(fs.readFileSync(path.join(C7DIR, '_fit', 'fits_latest.json'), 'utf8'));
const page = await openHero({ query: 'device=c710n', width: 256, height: 256, log: false });
await page.inject('fit_page.js');
await page.evaluate('window.C7FIT.init()');
const views = { front_R_white: ['budR', 1.40, 0], side_L: ['budL', 1.25, 30], front_L: ['budL', 1.35, 0] };
const res = await page.evaluate(`(async () => {
  const d = await window.sunpods.debugC7(false); const T = d.THREE, C7 = d.C7, BH = C7.BH;
  const shell = new T.Mesh(C7.buds.budR.getObjectByName('C7_BudShell').geometry); shell.updateMatrixWorld(true);
  const ray = new T.Raycaster();
  const poses = ${JSON.stringify(Object.fromEntries(Object.keys(views).map((k) => [k, fits[k].pose])))};
  const views = ${JSON.stringify(views)};
  const outline = (x, y, w, h, rot) => {
    ray.set(new T.Vector3(x * BH, y * BH, BH), new T.Vector3(0, 0, -1));
    const hit = ray.intersectObject(shell, false)[0]; if (!hit) return null;
    const n = hit.face.normal.clone().normalize();
    const up = new T.Vector3(0, 1, 0).applyAxisAngle(n, rot * Math.PI / 180);
    const xA = new T.Vector3().crossVectors(up, n).normalize(), yA = new T.Vector3().crossVectors(n, xA).normalize();
    const pts = [];
    for (let k = 0; k < 24; k++) {
      const a = k / 24 * Math.PI * 2;
      const q = hit.point.clone().addScaledVector(xA, Math.cos(a) * w * BH / 2).addScaledVector(yA, Math.sin(a) * h * BH / 2);
      ray.set(q.clone().addScaledVector(n, 0.2 * BH), n.clone().negate());
      const h2 = ray.intersectObject(shell, false)[0];
      pts.push((h2 ? h2.point : q).toArray());
    }
    return pts;
  };
  const measure = (k, pts) => {
    const [subj] = views[k];
    const sc = C7FIT.project(subj, 0, poses[k], pts, 512);
    let mx = 0, my = 0; for (const p of sc) { mx += p[0]; my += p[1]; } mx /= sc.length; my /= sc.length;
    let sxx = 0, syy = 0, sxy = 0; for (const p of sc) { const dx = p[0] - mx, dy = p[1] - my; sxx += dx * dx; syy += dy * dy; sxy += dx * dy; }
    const tr = sxx + syy, det = sxx * syy - sxy * sxy, l1 = tr / 2 + Math.sqrt(tr * tr / 4 - det), l2 = tr / 2 - Math.sqrt(tr * tr / 4 - det);
    const ang = 0.5 * Math.atan2(2 * sxy, sxx - syy) * 180 / Math.PI;   // major axis, image coords (y down)
    return { ratio: Math.sqrt(l1 / Math.max(l2, 1e-9)), tilt: -ang, cx: mx, cy: my, size: Math.sqrt(l1) };
  };
  const out = [];
  for (const x of [-0.12, -0.06, 0.0, 0.06, 0.12, 0.18]) for (const y of [-0.44, -0.40, -0.36, -0.32]) for (const asp of [0.9, 1.12, 1.3]) for (let rot = -60; rot <= 60; rot += 30) {
    const h = Math.sqrt(0.055 / asp), w = 0.055 / h;
    const pts = outline(x, y, w, h, rot); if (!pts) continue;
    const m = {}; let err = 0;
    for (const k of Object.keys(views)) {
      const [, r0, t0] = views[k]; m[k] = measure(k, pts);
      let dt = ((m[k].tilt - t0) % 180 + 270) % 180 - 90;
      err += Math.pow((m[k].ratio - r0) / 0.15, 2) + Math.pow(dt / 15, 2) * Math.min(1, (m[k].ratio - 1) / 0.2);
    }
    out.push({ err: +err.toFixed(2), x, y, w: +w.toFixed(3), h: +h.toFixed(3), rot, m: Object.fromEntries(Object.entries(m).map(([k, v]) => [k, [+v.ratio.toFixed(2), Math.round(v.tilt), Math.round(v.cx), Math.round(v.cy)]])) });
  }
  out.sort((a, b) => a.err - b.err);
  const cur = (() => { const pts = outline(0.12, -0.36, 0.21, 0.26, 0); return Object.fromEntries(Object.keys(views).map((k) => { const v = measure(k, pts); return [k, [+v.ratio.toFixed(2), Math.round(v.tilt), Math.round(v.cx), Math.round(v.cy)]]; })); })();
  return { cur, best: out.slice(0, 8) };
})()`);
console.log('current (0.12,-0.36, 0.21x0.26, rot 0):', JSON.stringify(res.cur));
for (const b of res.best) console.log(JSON.stringify(b));
await page.close(); process.exit(0);
