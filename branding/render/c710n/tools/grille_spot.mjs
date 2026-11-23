// Where on the earbud's underside the mic grille faces the review cameras as Sony's does (tools only):
// for face-on (x, y) spots, the angle between the shell normal and each camera direction.
import { openHero } from './harness.mjs';
const page = await openHero({ query: 'device=c710n', width: 256, height: 256, log: false });
const r = await page.evaluate(`(async () => {
  const d = await window.sunpods.debugC7(); const T = d.THREE, C7 = d.C7, BH = C7.BH;
  const shell = C7.buds.budR.getObjectByName('C7_BudShell');
  const g = new T.Mesh(shell.geometry); g.updateMatrixWorld(true);
  const ray = new T.Raycaster();
  const vF = new T.Vector3(-0.22, 0.052, 0.974).normalize(), vS = new T.Vector3(0.516, 0.14, 0.845).normalize();
  const out = [];
  for (let x = -0.10; x <= 0.301; x += 0.05) for (let y = -0.46; y <= -0.239; y += 0.03) {
    ray.set(new T.Vector3(x * BH, y * BH, BH), new T.Vector3(0, 0, -1));
    const h = ray.intersectObject(g, false)[0]; if (!h) continue;
    const n = h.face.normal.clone().normalize();
    const aF = Math.acos(n.dot(vF)) * 180 / Math.PI, aS = Math.acos(n.dot(vS)) * 180 / Math.PI;
    out.push([+x.toFixed(2), +y.toFixed(2), Math.round(aF), Math.round(aS), +(1 / Math.cos(aF * Math.PI / 180)).toFixed(2), +(1 / Math.cos(aS * Math.PI / 180)).toFixed(2)]);
  }
  return out;
})()`);
for (const row of r) console.log(row.join('\t'));
await page.close(); process.exit(0);
