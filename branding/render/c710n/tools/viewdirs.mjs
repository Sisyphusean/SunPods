// Direction towards the camera, in the earbud's own frame, for review angles (tools only).
import fs from 'node:fs';
import path from 'node:path';
import { openHero, C7DIR } from './harness.mjs';
const ids = process.argv.slice(2);
const meta = JSON.parse(fs.readFileSync(path.join(C7DIR, '_fit', 'refs', 'meta.json'), 'utf8'));
const fits = JSON.parse(fs.readFileSync(path.join(C7DIR, '_fit', 'fits_latest.json'), 'utf8'));
const page = await openHero({ query: 'device=c710n', width: 256, height: 256, log: false });
await page.inject('fit_page.js');
await page.evaluate('window.C7FIT.init()');
for (const id of ids) {
  const m = meta[id];
  const v = await page.evaluate(`(async () => { const d = await window.sunpods.debugC7(false); const T = d.THREE;
    C7FIT.useSubject(${JSON.stringify(m.subject)}, 0); C7FIT.setPose(${JSON.stringify(fits[id].pose)});
    const obj = C7FIT.subject.obj; obj.updateWorldMatrix(true, false); const cam = C7FIT.camera; cam.updateMatrixWorld();
    const tipC = (() => { const o = obj.getObjectByName('C7_Tip'); o.geometry.computeBoundingBox(); return o.geometry.boundingBox.getCenter(new T.Vector3()); })();
    const camLocal = cam.position.clone().applyMatrix4(new T.Matrix4().copy(obj.matrixWorld).invert());
    const dir = camLocal.sub(tipC).normalize();
    return dir.toArray().map((x) => +x.toFixed(3)); })()`);
  console.log(id, m.subject, JSON.stringify(v));
}
await page.close(); process.exit(0);
