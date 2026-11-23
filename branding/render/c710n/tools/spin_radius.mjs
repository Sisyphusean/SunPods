// Spin reach of each hero earbud (farthest point from its spin axis, in the floor plane) and the
// distance between the two axes (tools only).   node spin_radius.mjs
import { openHero } from './harness.mjs';
const page = await openHero({ query: 'device=c710n', width: 712, height: 506, log: false });
const r = await page.evaluate(`(async () => {
  const d = await window.sunpods.debugC7(true); const T = d.THREE;
  d.state.angle = 0; d.state.model = 'buds'; d.applyPose();
  const reach = (pivot) => { pivot.updateMatrixWorld(true); const axis = new T.Vector3().setFromMatrixPosition(pivot.matrixWorld); let m = 0;
    pivot.traverse((o) => { if (!o.isMesh || !o.visible) return; const p = o.geometry.attributes.position, v = new T.Vector3();
      for (let i = 0; i < p.count; i += 3) { v.fromBufferAttribute(p, i).applyMatrix4(o.matrixWorld); m = Math.max(m, Math.hypot(v.x - axis.x, v.z - axis.z)); } });
    return [m, axis.x, axis.y, axis.z]; };
  const R = reach(d.C7.buds.pivotR), L = reach(d.C7.buds.pivotL);
  return JSON.stringify({ R, L, axisGap: Math.hypot(R[1] - L[1], R[3] - L[3]), cam: d.camera.position.toArray() });
})()`);
console.log(r);
await page.close(); process.exit(0);
