// Earbud bounding-box centres (full and shell only) in the fit frame, plus the hero camera (tools only).
import { openHero } from './harness.mjs';
const page = await openHero({ query: 'device=c710n', width: 256, height: 256, log: false });
const r = await page.evaluate(`(async () => {
  const d = await window.sunpods.debugC7(); const T = d.THREE;
  const out = { cam: d.camera.quaternion.toArray() };
  for (const k of ['budR', 'budL']) {
    const b = d.C7.buds[k]; const g = new T.Group(); const sx = Math.sign(b.scale.x);
    const parent = b.parent, pos = b.position.clone(), q = b.quaternion.clone(), sc = b.scale.clone();
    g.add(b); b.position.set(0, 0, 0); b.quaternion.identity(); b.scale.set(sx, 1, 1); g.updateMatrixWorld(true);
    out[k] = { full: new T.Box3().setFromObject(b, true).getCenter(new T.Vector3()).toArray(), shell: new T.Box3().setFromObject(b.getObjectByName('C7_BudShell'), true).getCenter(new T.Vector3()).toArray() };
    parent.add(b); b.position.copy(pos); b.quaternion.copy(q); b.scale.copy(sc);
  }
  return out;
})()`);
console.log(JSON.stringify(r));
await page.close(); process.exit(0);
