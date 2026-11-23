// Where the seated right earbud's ear tip sits in the case frame (tools only).
import { openHero } from './harness.mjs';
const page = await openHero({ query: 'device=c710n', width: 256, height: 256, log: false });
const r = await page.evaluate(`(async () => {
  const d = await window.sunpods.debugC7();
  const T = d.THREE, k = d.C7.kase;
  d.state.lid = 0; d.applyPose(d.state, true);
  k.root.updateMatrixWorld(true);
  const an = k.anchors.find((a) => a.side === 'R');
  const tip = an.bud.getObjectByName('C7_Tip');
  const box = new T.Box3().setFromObject(tip);
  const inv = new T.Matrix4().copy(k.base.matrixWorld).invert();
  const c = box.getCenter(new T.Vector3()).applyMatrix4(inv);
  const pts = []; const p = tip.geometry.getAttribute('position'); const v = new T.Vector3();
  let mn = [9,9,9], mx = [-9,-9,-9];
  for (let i = 0; i < p.count; i += 3) { v.fromBufferAttribute(p, i).applyMatrix4(tip.matrixWorld).applyMatrix4(inv);
    mn = [Math.min(mn[0], v.x), Math.min(mn[1], v.y), Math.min(mn[2], v.z)]; mx = [Math.max(mx[0], v.x), Math.max(mx[1], v.y), Math.max(mx[2], v.z)]; }
  const bud = new T.Box3().setFromObject(an.bud); const bmin = bud.min.clone().applyMatrix4(inv), bmax = bud.max.clone().applyMatrix4(inv);
  return { centre: c.toArray().map((x) => +x.toFixed(3)), min: mn.map((x) => +x.toFixed(3)), max: mx.map((x) => +x.toFixed(3)), deckY: d.C7.CASE.yP - d.C7.CASE.deck };
})()`);
console.log(JSON.stringify(r));
await page.close(); process.exit(0);
