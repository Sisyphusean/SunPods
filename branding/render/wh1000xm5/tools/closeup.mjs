// Close-up review renders (tools only): the model at each review angle's fitted camera at 3x the board
// size (1536 px), plus where its parts land (in 512-px board space), for closeup.py.
//   node closeup.mjs <outDir> [ids...]      XM_OVR='{...}' tries shape values
import fs from 'node:fs';
import path from 'node:path';
import { openHero, savePng, XMDIR } from './harness.mjs';

const [outDir, ...idsArg] = process.argv.slice(2);
const ids = idsArg.length ? idsArg : ['side_L', 'tq_L', 'front', 'tq_silver', 'case_black'];
fs.mkdirSync(outDir, { recursive: true });
const meta = JSON.parse(fs.readFileSync(path.join(XMDIR, '_fit', 'refs', 'meta.json'), 'utf8'));
const fits = JSON.parse(fs.readFileSync(path.join(XMDIR, '_fit', 'fits_latest.json'), 'utf8'));
const page = await openHero({ query: 'device=wh1000xm5', width: 512, height: 512, log: false });
await page.inject('fit_page.js');
await page.evaluate('window.XMFIT.init()');
if (process.env.XM_OVR) await page.evaluate(`XMFIT.rebuild(${process.env.XM_OVR})`);
// parts, by mesh name and side: their bounding-box centres in the subject's frame
const PARTS = {
  phones: [['cup', 'L', 'XM5H_Face'], ['hanger', 'L', 'XM5H_Logo'], ['joint', 'L', 'XM5H_Knuckle'], ['band end', 'L', 'XM5H_Slider'], ['pad', 'R', 'XM5H_Pad'], ['buttons', 'L', 'XM5H_Power']],
  case: [['case loop', null, 'XM5H_CaseLoop'], ['cups in case', null, 'XM5H_Face'], ['case rim', null, 'XM5H_CasePull']],
};
const where = {};
for (const id of ids) {
  const m = meta[id], pose = fits[id].pose, hp = m.hp || null;
  const r = await page.evaluate(`XMFIT.shot(${JSON.stringify(m.subject)}, ${m.lid || 0}, ${JSON.stringify(pose)}, ${JSON.stringify(m.color)}, 1536, ${JSON.stringify(hp)})`);
  savePng(path.join(outDir, `${id}_model.png`), r.color);
  const parts = PARTS[m.subject];
  const pts = await page.evaluate(`(async () => { const d = await window.sunpods.debugMod('wh1000xm5', false); const T = d.THREE;
    XMFIT.useSubject(${JSON.stringify(m.subject)}, ${m.lid || 0}, ${JSON.stringify(hp)});
    const subj = XMFIT.subject.obj; subj.updateMatrixWorld(true);
    const inv = new T.Matrix4().copy(subj.matrixWorld).invert();
    return ${JSON.stringify(parts)}.map(([label, side, name]) => {
      const root = side ? d.M.buds.phones.units[side].cup.parent.parent.parent.parent.parent : subj;
      let o = null; root.traverse((x) => { if (!o && x.name === name) o = x; });
      const c = new T.Box3().setFromObject(o).getCenter(new T.Vector3()).applyMatrix4(inv);
      return c.toArray();
    }); })()`);
  const px = await page.evaluate(`XMFIT.project(${JSON.stringify(m.subject)}, ${m.lid || 0}, ${JSON.stringify(pose)}, ${JSON.stringify(pts)}, 512, ${JSON.stringify(hp)})`);
  where[id] = parts.map((p, k) => [p[0], px[k]]);
  console.log(id, JSON.stringify(where[id].map(([l, p]) => [l, p.map(Math.round)])));
}
fs.writeFileSync(path.join(outDir, 'where.json'), JSON.stringify(where, null, 1));
await page.evaluate('XMFIT.done()');
await page.close();
process.exit(0);
