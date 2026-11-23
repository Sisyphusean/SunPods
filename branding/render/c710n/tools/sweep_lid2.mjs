// Saves overlays of the open case at given lid angles with a held pitch (lid-angle check).
import fs from 'node:fs';
import path from 'node:path';
import { openHero, C7DIR, savePng } from './harness.mjs';
const [id, pitch, list, out] = process.argv.slice(2);
const b64 = fs.readFileSync(path.join(C7DIR, '_fit', 'refs', id + '_mask.png')).toString('base64');
const page = await openHero({ query: 'device=c710n', width: 512, height: 512, log: false });
await page.inject('fit_page.js');
await page.evaluate('window.C7FIT.init()');
for (const deg of list.split(',').map(Number)) {
  const r = await page.evaluate(`(async () => {
    const d = await window.sunpods.debugC7();
    const lidFrac = ${deg} * Math.PI / 180 / d.C7.LID_OPEN;
    const f = await C7FIT.fit('case', lidFrac, ${JSON.stringify(b64)}, [0, ${pitch}, 0], { lockPitch: ${pitch}, starts: [[0,0,0]], iters: 100 });
    const s = C7FIT.shot('case', lidFrac, f.pose, 'black', 512);
    return { iou: f.iou, mask: s.mask, color: s.color };
  })()`);
  savePng(path.join(out, `lid${deg}_mask.png`), r.mask); savePng(path.join(out, `lid${deg}_color.png`), r.color);
  console.log('lid', deg, r.iou.toFixed(4));
}
await page.close();
