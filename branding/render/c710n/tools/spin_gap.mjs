// Closest distance between the two hero earbuds at every 5 degrees of the drag spin (tools only).
//   node spin_gap.mjs [device]
import { openHero } from './harness.mjs';
const device = process.argv[2] || 'c710n';
const page = await openHero({ query: `device=${device}`, width: 712, height: 506, log: false });
const r = await page.evaluate(`(async () => {
  const d = await window.sunpods.debugC7(true);
  const T = d.THREE, st = d.state;
  const roots = device => device === 'c710n' ? [d.C7.buds.pivotR, d.C7.buds.pivotL] : null;
  const [pR, pL] = roots('${device}');
  st.model = 'buds';
  const pts = (root) => { const out = []; root.updateMatrixWorld(true); root.traverse((o) => {
    if (!o.isMesh || !o.visible || !o.geometry.attributes.position) return;
    const p = o.geometry.attributes.position, v = new T.Vector3(), step = Math.max(1, Math.floor(p.count / 1500));
    for (let i = 0; i < p.count; i += step) { v.fromBufferAttribute(p, i).applyMatrix4(o.matrixWorld); out.push(v.x, v.y, v.z); }
  }); return out; };
  const res = [];
  for (let a = 0; a < 360; a += 5) {
    st.angle = a; d.applyPose();
    const A = pts(pR), B = pts(pL); let m = 1e9;
    for (let i = 0; i < A.length; i += 3) for (let j = 0; j < B.length; j += 3) {
      const dx = A[i] - B[j], dy = A[i + 1] - B[j + 1], dz = A[i + 2] - B[j + 2], q = dx * dx + dy * dy + dz * dz; if (q < m) m = q;
    }
    res.push([a, Math.sqrt(m)]);
  }
  st.angle = 0; d.applyPose();
  return JSON.stringify(res);
})()`);
const res = JSON.parse(r);
const worst = res.reduce((w, x) => (x[1] < w[1] ? x : w));
console.log(device, 'closest gap', worst[1].toFixed(3), 'at', worst[0], 'deg;', res.filter((x) => x[1] < 0.05).map((x) => x[0]).join(' ') || 'no angle closer than 0.05');
await page.close(); process.exit(0);
