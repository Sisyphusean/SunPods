import fs from 'node:fs';
import path from 'node:path';
import { openHero, savePng, XMDIR, loadMeta } from './harness.mjs';

const [outDir, ...idsArg] = process.argv.slice(2);
const ids = idsArg.length ? idsArg : ['black_side', 'black_primary', 'black_front', 'black_rear', 'black_folded', 'black_case', 'silver_primary'];
fs.mkdirSync(outDir, { recursive: true });
const meta = loadMeta();
const fits = JSON.parse(fs.readFileSync(path.join(XMDIR, '_fit', 'fits_latest.json'), 'utf8'));
const page = await openHero({ query: 'device=wh1000xm6', width: 512, height: 512, log: false });
await page.inject('fit_page.js');
await page.evaluate('window.XMFIT.init()');
if (process.env.XM_OVR) await page.evaluate(`XMFIT.rebuild(${process.env.XM_OVR})`);
const PARTS = {
  phones: [
    ['touch face', 'leftSide', 'earcupTouchFace'],
    ['hinge cut', 'leftSide', 'scarfHingeCollar'],
    ['SONY stem', 'leftSide', 'sonyWordmark'],
    ['power btn', 'leftSide', 'powerButton'],
    ['nc/amb btn', 'leftSide', 'ncAmbButton'],
    ['pad', 'rightSide', 'earpadCushion'],
  ],
  case: [
    ['clasp strap', null, 'carryCaseMagneticClasp'],
    ['clasp bar', null, 'carryCaseMagneticClaspBar'],
    ['rim seam', null, 'carryCaseUpperSeamLip'],
  ],
};
const where = {};
for (const id of ids) {
  if (!fits[id]) continue;
  const m = meta[id], pose = fits[id].pose, hp = m.hp || null;
  const r = await page.evaluate(`XMFIT.shot(${JSON.stringify(m.subject)}, ${m.lid || 0}, ${JSON.stringify(pose)}, ${JSON.stringify(m.color)}, 1536, ${JSON.stringify(hp)})`);
  savePng(path.join(outDir, `${id}_model.png`), r.color);
  const parts = PARTS[m.subject];
  const pts = await page.evaluate(`(async () => { const d = await window.sunpods.debugMod('wh1000xm6', false); const T = d.THREE;
    XMFIT.useSubject(${JSON.stringify(m.subject)}, ${m.lid || 0}, ${JSON.stringify(hp)});
    const subj = XMFIT.subject.obj; subj.updateMatrixWorld(true);
    const inv = new T.Matrix4().copy(subj.matrixWorld).invert();
    return ${JSON.stringify(parts)}.map(([label, sideKey, name]) => {
      const root = sideKey ? d.M.headphoneModel.parts[sideKey].sideAssembly : subj;
      let o = null; root.traverse((x) => { if (!o && x.name === name) o = x; });
      const c = o ? new T.Box3().setFromObject(o).getCenter(new T.Vector3()).applyMatrix4(inv) : new T.Vector3();
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
