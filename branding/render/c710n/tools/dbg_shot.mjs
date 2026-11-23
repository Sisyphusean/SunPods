// Debug renders of the subject at given poses (tools only).
//   node dbg_shot.mjs <outPrefix> '<json [{subject, lid, pose:[yaw,pitch,roll,tx,ty,scale], color, hide:[names]}]>'
import { openHero, savePng } from './harness.mjs';
const [out, json] = process.argv.slice(2);
const page = await openHero({ query: 'device=c710n', width: 512, height: 512, log: false });
await page.inject('fit_page.js');
await page.evaluate('window.C7FIT.init()');
let n = 0;
for (const t of JSON.parse(json)) {
  if (t.hide) await page.evaluate(`(async () => { const d = await window.sunpods.debugC7(false); d.scene.traverse((o) => { if (${JSON.stringify(t.hide)}.includes(o.name)) o.visible = false; }); })()`);
  const r = await page.evaluate(`C7FIT.shot(${JSON.stringify(t.subject)}, ${t.lid || 0}, ${JSON.stringify(t.pose)}, ${JSON.stringify(t.color)}, 512)`);
  savePng(`${out}_${n++}.png`, r.color);
}
await page.close();
process.exit(0);
