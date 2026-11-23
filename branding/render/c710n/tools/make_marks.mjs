// Draws the WF-C710N print atlas (app/src/main/assets/hero3d/c710n_marks.png) in the hero page:
// the SONY wordmark traced from the approved XM5 case logo mesh (case.glb, TXT_CaseSONY), and the
// L and R marks. One 1024x512 RGBA atlas, white on transparent, used as alpha masks by c710n.js:
//   row 0 (y 0..255):   SONY wordmark, centred in 1024x256
//   row 1 (y 256..511): L mark (x 0..255), R mark (x 256..511), R letter only (x 512..767), L letter only (x 768..1023)
//   node make_marks.mjs
import path from 'node:path';
import { openHero, savePng, HERO } from './harness.mjs';

const page = await openHero({ query: 'model=case' });
const url = await page.evaluate(`(async () => {
  const d = await window.sunpods.debugC7(false);
  let mesh = null;
  d.scene.traverse((o) => { if (o.isMesh && o.name === 'SM_Case_SONY') mesh = o; });
  if (!mesh) throw new Error('no SM_Case_SONY');
  const g = mesh.geometry, pos = g.getAttribute('position'), idx = g.index;
  // the glyphs lie in the mesh's x/z plane (y is the thin depth); find the bounds
  let x0 = 1e9, x1 = -1e9, z0 = 1e9, z1 = -1e9;
  for (let i = 0; i < pos.count; i++) { const x = pos.getX(i), z = pos.getZ(i); x0 = Math.min(x0, x); x1 = Math.max(x1, x); z0 = Math.min(z0, z); z1 = Math.max(z1, z); }
  const cv = document.createElement('canvas'); cv.width = 1024; cv.height = 512;
  const c = cv.getContext('2d');
  c.clearRect(0, 0, 1024, 512);
  // wordmark: the WF-C710N print is bolder and tighter than the XM5's: letters are found as
  // x-clusters of triangles, the gaps between them closed up by TIGHT, and the strokes thickened
  const TIGHT = 0.7, BOLD = 7;
  const n = idx ? idx.count : pos.count;
  const tris = [];
  for (let t = 0; t < n; t += 3) {
    const a = idx ? idx.getX(t) : t, b = idx ? idx.getX(t + 1) : t + 1, e = idx ? idx.getX(t + 2) : t + 2;
    tris.push([[pos.getX(a), pos.getZ(a)], [pos.getX(b), pos.getZ(b)], [pos.getX(e), pos.getZ(e)]]);
  }
  // letters: sort triangle x-ranges and merge overlapping ones
  const ranges = tris.map((tr) => [Math.min(...tr.map((p) => p[0])), Math.max(...tr.map((p) => p[0]))]);
  const order = ranges.map((r, i) => i).sort((p, q) => ranges[p][0] - ranges[q][0]);
  const letters = [];
  for (const i of order) {
    const r = ranges[i], L = letters[letters.length - 1];
    if (L && r[0] <= L.x1 + 1e-4) { L.x1 = Math.max(L.x1, r[1]); L.tris.push(i); } else letters.push({ x0: r[0], x1: r[1], tris: [i] });
  }
  let shift = 0;
  letters.forEach((L, k) => { if (k > 0) shift += (L.x0 - letters[k - 1].x1) * (1 - TIGHT); L.shift = shift; });
  const nx0 = letters[0].x0, nx1 = letters[letters.length - 1].x1 - shift;
  const s = (920 - 2 * BOLD) / (nx1 - nx0), ox = 512 - (nx0 + nx1) / 2 * s, oy = 128 - (z0 + z1) / 2 * s;
  c.fillStyle = '#fff'; c.strokeStyle = '#fff'; c.lineJoin = 'round';
  for (const L of letters) for (const i of L.tris) {
    const tr = tris[i];
    c.beginPath();
    tr.forEach((p, k) => { const X = ox + (p[0] - L.shift) * s, Y = oy + p[1] * s; if (k) c.lineTo(X, Y); else c.moveTo(X, Y); });
    c.closePath(); c.fill(); c.lineWidth = BOLD; c.stroke();
  }
  console.log('letters', letters.length);
  // L and R marks: a filled disc with the letter knocked out (alpha), plus letters alone
  const mark = (cx, letter, knock) => {
    c.save();
    c.translate(cx, 384);
    if (knock) { c.beginPath(); c.arc(0, 0, 118, 0, Math.PI * 2); c.fill(); c.globalCompositeOperation = 'destination-out'; }
    c.font = 'bold 170px Arial, Helvetica, sans-serif'; c.textAlign = 'center'; c.textBaseline = 'middle';
    c.fillText(letter, 0, 8);
    c.restore();
  };
  mark(128, 'L', true); mark(384, 'R', true); mark(640, 'R', false); mark(896, 'L', false);
  return cv.toDataURL('image/png');
})()`);
savePng(path.join(HERO, 'c710n_marks.png'), url);
console.log('wrote', path.join(HERO, 'c710n_marks.png'));
await page.close();
