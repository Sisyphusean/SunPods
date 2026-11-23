// Converts earbud poses fitted with full-bounding-box centring (old ear tip) to shell centring (tools only).
//   node convert_poses.mjs <fits.json in> <out fits_latest.json> '<old centres json>'
import fs from 'node:fs';
import { openHero } from './harness.mjs';
const [inFile, outFile, centresJson] = process.argv.slice(2);
const fin = JSON.parse(fs.readFileSync(inFile, 'utf8'));
const C = JSON.parse(centresJson);
const page = await openHero({ query: 'device=c710n', width: 128, height: 128, log: false });
const conv = await page.evaluate(`(async () => {
  const d = await window.sunpods.debugC7(false); const T = d.THREE;
  const fin = ${JSON.stringify(fin)}, C = ${JSON.stringify(C)};
  const vh = 2 * 30 * Math.tan(7 * Math.PI / 180), out = {};
  for (const [k, f] of Object.entries(fin)) {
    if (f.group !== 'buds') continue;
    const [yaw, pitch, roll, tx, ty, s] = f.pose;
    const c = C[f.subject];
    const dv = new T.Vector3(...c.full).sub(new T.Vector3(...c.shell)).multiplyScalar(s);
    const qo = new T.Quaternion().setFromEuler(new T.Euler(pitch * Math.PI / 180, yaw * Math.PI / 180, roll * Math.PI / 180, 'ZXY'));
    dv.applyQuaternion(qo);
    out[k] = { ...f, pose: [yaw, pitch, roll, tx - dv.x / vh, ty - dv.y / vh, s] };
  }
  return out;
})()`);
const latest = JSON.parse(fs.readFileSync(outFile, 'utf8'));
Object.assign(latest, conv);
fs.writeFileSync(outFile, JSON.stringify(latest, null, 1));
console.log('converted', Object.keys(conv).length);
await page.close(); process.exit(0);
