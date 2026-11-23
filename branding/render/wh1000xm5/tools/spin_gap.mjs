// Closest distances between the hero headphones' parts at every 5 degrees of the drag spin (tools only):
// left unit to right unit, and each cup (housing, pad) to the headband. The headphones spin as one
// rigid body, so these must stay positive at every angle (nothing passes through anything).
//   node spin_gap.mjs
import { openHero } from './harness.mjs';
const page = await openHero({ query: 'device=wh1000xm5', width: 712, height: 506, log: false });
const r = await page.evaluate(`(async () => {
  const d = await window.sunpods.debugMod('wh1000xm5', true);
  const T = d.THREE, st = d.state, P = d.M.buds.phones;
  st.model = 'buds';
  const pts = (root, filter) => { const out = []; root.updateMatrixWorld(true); root.traverse((o) => {
    if (!o.isMesh || !o.visible || !filter(o)) return;
    const p = o.geometry.attributes.position, v = new T.Vector3(), step = Math.max(1, Math.floor(p.count / 1500));
    for (let i = 0; i < p.count; i += step) { v.fromBufferAttribute(p, i).applyMatrix4(o.matrixWorld); out.push(v.x, v.y, v.z); }
  }); return out; };
  const gap = (A, B) => { let m = 1e9; for (let i = 0; i < A.length; i += 3) for (let j = 0; j < B.length; j += 3) {
    const dx = A[i] - B[j], dy = A[i + 1] - B[j + 1], dz = A[i + 2] - B[j + 2], q = dx * dx + dy * dy + dz * dz; if (q < m) m = q; } return Math.sqrt(m); };
  const band = (o) => /XM5H_Band|XM5H_Cushion/.test(o.name), cup = (o) => /XM5H_Housing|XM5H_Pad|XM5H_Face/.test(o.name);
  const res = [];
  for (let a = 0; a < 360; a += 5) {
    st.angle = a; d.applyPose();
    const L = pts(P.units.L.holder, () => true), R = pts(P.units.R.holder, () => true), B = pts(P.root, band);
    const cL = pts(P.units.L.cup, cup), cR = pts(P.units.R.cup, cup);
    res.push([a, gap(L, R), gap(cL, B), gap(cR, B)]);
  }
  st.angle = 0; d.applyPose();
  return JSON.stringify(res);
})()`);
const res = JSON.parse(r);
const worst = (k) => res.reduce((w, x) => (x[k] < w[k] ? x : w));
// world units: the hero is scaled to about 0.236 of the model's centimetres
const wl = worst(1), wb = Math.min(worst(2)[2], worst(3)[3]);
console.log('wh1000xm5 closest gap, left unit to right unit:', wl[1].toFixed(4), 'at', wl[0], 'deg; cups to headband:', wb.toFixed(4),
  ';', res.every((x) => x[1] > 0.005 && x[2] > 0.005 && x[3] > 0.005) ? 'no part passes through another at any angle' : 'CONTACT at ' + res.filter((x) => Math.min(x[1], x[2], x[3]) <= 0.005).map((x) => x[0]).join(' '));
await page.close(); process.exit(0);
