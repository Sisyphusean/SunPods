// Lid-angle check: fits the open-case reference with the camera pitch held, for several lid angles.
//   node sweep_lid.mjs <refId> <pitch> <deg,deg,...>
import fs from 'node:fs';
import path from 'node:path';
import { openHero, C7DIR } from './harness.mjs';
const [id, pitch, list] = process.argv.slice(2);
const b64 = fs.readFileSync(path.join(C7DIR, '_fit', 'refs', id + '_mask.png')).toString('base64');
const page = await openHero({ query: 'device=c710n', width: 512, height: 512, log: false });
await page.inject('fit_page.js');
await page.evaluate('window.C7FIT.init()');
for (const deg of list.split(',').map(Number)) {
  const r = await page.evaluate(`(async () => {
    const d = await window.sunpods.debugC7();
    const open = ${deg} * Math.PI / 180;
    d.C7.LID_OPEN_TEST = open;
    const lidFrac = open / d.C7.LID_OPEN;
    return C7FIT.fit('case', lidFrac, ${JSON.stringify(b64)}, [0, ${pitch}, 0], { lockPitch: ${pitch}, starts: [[0,0,0],[8,0,0],[-8,0,0]], iters: 120 });
  })()`);
  console.log('lid', deg, 'IoU', r.iou.toFixed(4), 'pose', r.pose.map((v) => v.toFixed(2)).join(' '));
}
await page.close();
