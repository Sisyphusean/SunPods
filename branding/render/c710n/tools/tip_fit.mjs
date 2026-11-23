// Fits the ear tip's axis and seat to Sony's silhouettes with the earbud body held where it was fitted
// (cameras from _fit/fits_latest.json, shell-centred): Nelder-Mead over the axis (two angles) and the
// nozzle's base (three offsets), mean outline IoU over every earbud review angle (tools only).
import fs from 'node:fs';
import path from 'node:path';
import { openHero, C7DIR } from './harness.mjs';
const meta = JSON.parse(fs.readFileSync(path.join(C7DIR, '_fit', 'refs', 'meta.json'), 'utf8'));
const fits = JSON.parse(fs.readFileSync(path.join(C7DIR, '_fit', 'fits_latest.json'), 'utf8'));
const ids = Object.keys(meta).filter((k) => meta[k].group === 'buds' && fits[k]);
const page = await openHero({ query: 'device=c710n', width: 256, height: 256, log: false });
await page.inject('fit_page.js');
await page.evaluate('window.C7FIT.init()');
const views = ids.map((k) => ({ k, subj: meta[k].subject, pose: fits[k].pose, b64: fs.readFileSync(path.join(C7DIR, '_fit', 'refs', k + '_mask.png')).toString('base64') }));
const w0 = JSON.parse(process.argv[2] || '{}');   // weights per id (studio views count more)
const res = await page.evaluate(`(async () => {
  const views = ${JSON.stringify(views)}, W = ${JSON.stringify(w0)};
  for (const v of views) v.ref = await C7FIT.decodeMask(v.b64, 192);
  const d0 = ${JSON.stringify([0.827, 0.174, -0.533])}, f0 = [0.34, -0.28, -0.50];
  const n = Math.hypot(...d0); const D = d0.map((x) => x / n);
  const e1 = [D[2], 0, -D[0]]; const n1 = Math.hypot(...e1); for (let i = 0; i < 3; i++) e1[i] /= n1;
  const e2 = [D[1] * e1[2] - D[2] * e1[1], D[2] * e1[0] - D[0] * e1[2], D[0] * e1[1] - D[1] * e1[0]];
  const params = (x) => ({ dir: D.map((v, i) => v + x[0] * e1[i] + x[1] * e2[i]), from: f0.map((v, i) => v + x[2 + i]) });
  async function score(x) {
    const p = params(x);
    await C7FIT.rebuild({ BUD: { nozzle: { dir: p.dir, from: p.from } } });
    let s = 0, wsum = 0; const per = {};
    for (const v of views) { const w = W[v.k] || 1; const iou = C7FIT.scoreAt(v.subj, 0, v.pose, v.ref); per[v.k] = +iou.toFixed(4); s += w * iou; wsum += w; }
    return { mean: s / wsum, per };
  }
  // Nelder-Mead (async)
  const N = 5; let simplex = [[0, 0, 0, 0, 0]]; const st = [0.25, 0.15, 0.03, 0.03, 0.03];
  for (let i = 0; i < N; i++) { const p = [0, 0, 0, 0, 0]; p[i] = st[i]; simplex.push(p); }
  let vals = []; for (const p of simplex) vals.push(-(await score(p)).mean);
  const start = -vals[0];
  for (let it = 0; it < 70; it++) {
    const ord = vals.map((v, i) => [v, i]).sort((a, b) => a[0] - b[0]).map((x) => x[1]);
    simplex = ord.map((i) => simplex[i]); vals = ord.map((i) => vals[i]);
    const c = Array(N).fill(0); for (let i = 0; i < N; i++) for (let j = 0; j < N; j++) c[j] += simplex[i][j] / N;
    const xr = c.map((v, j) => v + (v - simplex[N][j])); const fr = -(await score(xr)).mean;
    if (fr < vals[0]) { const xe = c.map((v, j) => v + 2 * (v - simplex[N][j])); const fe = -(await score(xe)).mean; if (fe < fr) { simplex[N] = xe; vals[N] = fe; } else { simplex[N] = xr; vals[N] = fr; } }
    else if (fr < vals[N - 1]) { simplex[N] = xr; vals[N] = fr; }
    else { const xc = c.map((v, j) => v + 0.5 * (simplex[N][j] - v)); const fc = -(await score(xc)).mean;
      if (fc < vals[N]) { simplex[N] = xc; vals[N] = fc; } else { for (let i = 1; i <= N; i++) { simplex[i] = simplex[i].map((v, j) => simplex[0][j] + 0.5 * (v - simplex[0][j])); vals[i] = -(await score(simplex[i])).mean; } } }
  }
  const best = simplex[vals.indexOf(Math.min(...vals))];
  const fin = await score(best);
  return { start, best: params(best), mean: fin.mean, per: fin.per };
})()`);
console.log(JSON.stringify(res, null, 1));
await page.close(); process.exit(0);
