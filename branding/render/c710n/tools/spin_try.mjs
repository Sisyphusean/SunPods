// Tries spin-axis separations (tools only): closest gap over a full spin for each candidate offset.
import { openHero } from './harness.mjs';
const page = await openHero({ query: 'device=c710n', width: 712, height: 506, log: false });
const r = await page.evaluate(`(async () => {
  const d = await window.sunpods.debugC7(true); const T = d.THREE, st = d.state;
  const pR = d.C7.buds.pivotR, pL = d.C7.buds.pivotL, r0 = pR.position.clone(), l0 = pL.position.clone();
  const pts = (root) => { const out = []; root.updateMatrixWorld(true); root.traverse((o) => {
    if (!o.isMesh || !o.visible) return; const p = o.geometry.attributes.position, v = new T.Vector3(), step = Math.max(1, Math.floor(p.count / 900));
    for (let i = 0; i < p.count; i += step) { v.fromBufferAttribute(p, i).applyMatrix4(o.matrixWorld); out.push(v.x, v.y, v.z); } }); return out; };
  const gap = () => { let w = 1e9; for (let a = 0; a < 360; a += 5) { st.angle = a; d.applyPose(); const A = pts(pR), B = pts(pL);
    for (let i = 0; i < A.length; i += 3) for (let j = 0; j < B.length; j += 3) { const q = (A[i]-B[j])**2 + (A[i+1]-B[j+1])**2 + (A[i+2]-B[j+2])**2; if (q < w) w = q; } } return Math.sqrt(w); };
  const out = [];
  for (const [dz, dx] of [[0,0],[0.4,0],[0.7,0],[1.0,0],[0,0.25],[0.5,0.15]]) {
    pR.position.set(r0.x - dx, r0.y, r0.z - dz); pL.position.set(l0.x + dx, l0.y, l0.z + dz);
    out.push({ dz, dx, gap: +gap().toFixed(3) });
  }
  pR.position.copy(r0); pL.position.copy(l0); st.angle = 0; d.applyPose();
  return JSON.stringify(out);
})()`);
console.log(r);
await page.close(); process.exit(0);
