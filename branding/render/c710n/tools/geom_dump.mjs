// Dumps every C710N mesh's vertex positions (traversal order) and the model build time, to compare
// two versions of c710n.js:   node geom_dump.mjs <out.json>   /   node geom_dump.mjs --compare a.json b.json
import fs from 'node:fs';
if (process.argv[2] === '--compare') {
  const a = JSON.parse(fs.readFileSync(process.argv[3], 'utf8')), b = JSON.parse(fs.readFileSync(process.argv[4], 'utf8'));
  const ka = Object.keys(a.meshes), kb = Object.keys(b.meshes);
  console.log('meshes', ka.length, kb.length, 'same names/order:', JSON.stringify(ka) === JSON.stringify(kb));
  let worst = 0, worstK = '', diffMeshes = 0;
  for (const k of ka) {
    const x = Buffer.from(a.meshes[k], 'base64'), y = b.meshes[k] && Buffer.from(b.meshes[k], 'base64');
    if (!y || x.length !== y.length) { console.log('size differs', k); diffMeshes++; continue; }
    const fx = new Float32Array(x.buffer, x.byteOffset, x.length / 4), fy = new Float32Array(y.buffer, y.byteOffset, y.length / 4);
    let m = 0; for (let i = 0; i < fx.length; i++) m = Math.max(m, Math.abs(fx[i] - fy[i]));
    if (m > 0) diffMeshes++;
    if (m > worst) { worst = m; worstK = k; }
  }
  console.log('meshes that differ:', diffMeshes, ' largest difference:', worst, worstK, ' (body height is about 1)');
  console.log('build ms:', a.buildMs, '->', b.buildMs);
  process.exit(0);
}
const { openHero } = await import('./harness.mjs');
const page = await openHero({ query: 'device=c710n', width: 712, height: 506, log: true });
const res = await page.evaluate(`(async () => {
  const d = await window.sunpods.debugC7(true);
  const out = {};
  let i = 0;
  for (const root of [d.C7.buds.root, d.C7.kase.root]) root.traverse((o) => {
    if (!o.isMesh) return;
    const p = o.geometry.attributes.position.array;
    let s = ''; const u8 = new Uint8Array(p.buffer, p.byteOffset, p.byteLength);
    for (let j = 0; j < u8.length; j += 0x8000) s += String.fromCharCode.apply(null, u8.subarray(j, j + 0x8000));
    out[(i++) + '_' + (o.name || o.parent.name || 'mesh')] = btoa(s);
  });
  return JSON.stringify(out);
})()`);
const marks = await page.evaluate(`performance.getEntriesByType ? 0 : 0`);
fs.writeFileSync(process.argv[2], JSON.stringify({ meshes: JSON.parse(res), buildMs: Number(process.env.BUILD_MS || 0) }));
console.log('wrote', process.argv[2], Object.keys(JSON.parse(res)).length, 'meshes');
await page.close(); process.exit(0);
