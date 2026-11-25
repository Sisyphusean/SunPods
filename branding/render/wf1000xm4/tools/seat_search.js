// Injected into the hero page (tools only): searches the earbud seat pose in the WF-1000XM4 case so
// that nothing pokes through the case with the lid closed (the module's own caseInside test), the
// touch disc faces up (within a limit), the copper microphone sits towards the case end and the
// earbuds sit as high as they can; the left earbud mirrors the right one, so they must not cross x = 0.
//   window.X4SEAT({ test: { tilt, pos, scale } }) counts points outside for one pose instead.
window.X4SEAT = async (opts = {}) => {
  const d = await window.sunpods.debugMod('wf1000xm4');
  const T = d.THREE, X4 = d.M, C = X4.CASE;
  const bud = X4.buds.budR;
  const pts = [];
  bud.traverse((o) => {
    if (!o.isMesh || !/X4_BudShell|X4_Tip$|X4_Boss$/.test(o.name)) return;
    const p = o.geometry.getAttribute('position'), step = o.name === 'X4_BudShell' ? 37 : 9;
    for (let i = 0; i < p.count; i += step) pts.push(new T.Vector3(p.getX(i), p.getY(i), p.getZ(i)));
  });
  const seat = C.budSeat;
  const basis = new T.Matrix4().makeBasis(new T.Vector3(...seat.x).normalize(), new T.Vector3(...seat.y).normalize(),
    new T.Vector3().crossVectors(new T.Vector3(...seat.x).normalize(), new T.Vector3(...seat.y).normalize()));
  const rot = (t) => new T.Matrix4().makeRotationFromEuler(new T.Euler(t[0] * Math.PI / 180, t[1] * Math.PI / 180, t[2] * Math.PI / 180, 'ZXY')).multiply(basis);
  const boss = new T.Vector3(...X4.BUD.boss.at).multiplyScalar(X4.BH);
  const w = new T.Vector3();
  function check(R, ps, sc, margin) {
    const M = new T.Matrix4().makeTranslation(ps[0], ps[1], ps[2]).multiply(R).multiply(new T.Matrix4().makeScale(sc, sc, sc));
    let bad = 0, minX = 1e9;
    for (const p of pts) { w.copy(p).applyMatrix4(M); if (!X4.caseInside(w.x, w.y, w.z, margin)) { bad++; if (!opts.test) break; } if (w.x < minX) minX = w.x; }
    return { bad, gap: 2 * minX, M };
  }
  if (opts.test) {
    const t = opts.test.tilt || seat.tilt, ps = opts.test.pos || [seat.pos[0], seat.pos[1] - seat.drop, seat.pos[2]];
    const r = check(rot(t), ps, opts.test.scale || seat.scale, opts.margin ?? 0.02);
    return { bad: r.bad, of: pts.length, gap: +r.gap.toFixed(3) };
  }
  const out = [];
  const sc = opts.scale || seat.scale;
  for (let tx = -70; tx <= 10; tx += 5) for (let ty = -30; ty <= 30; ty += 10) for (let tz = -30; tz <= 30; tz += 10) {
    const R = rot([tx, ty, tz]);
    const n = new T.Vector3(0, 0, 1).transformDirection(R);
    if (n.y < Math.cos((opts.maxTilt || 70) * Math.PI / 180)) continue;
    for (const px of (opts.xs || [1.05, 1.15, 1.25, 1.35, 1.45])) for (const py of (opts.ys || [1.3, 1.5, 1.7, 1.9, 2.1, 2.3])) for (const pz of (opts.zs || [-0.35, -0.2, -0.05, 0.1, 0.25])) {
      const r = check(R, [px, py, pz], sc, 0.02);
      if (r.bad || r.gap < (opts.minGap || 0.08)) continue;
      const b = boss.clone().applyMatrix4(r.M);
      const face = new T.Vector3(0, 0, 0).applyMatrix4(r.M);
      // high discs facing up, the microphone out towards the end and to the front, earbuds close together
      // (opts.nT: a wanted disc direction, Sony's photo shows the discs turned up and back towards the hinge)
      const nT = opts.nT ? new T.Vector3(...opts.nT).normalize() : null;
      const score = (nT ? -3 * n.distanceTo(nT) + 0.5 * face.y : face.y + 0.6 * n.y) + 0.4 * (b.x - px) + 0.2 * b.z - 1.5 * Math.max(0, r.gap - 0.25);
      out.push({ score: +score.toFixed(3), tilt: [tx, ty, tz], pos: [px, +(py + seat.drop).toFixed(2), pz], faceY: +face.y.toFixed(2), n: n.toArray().map((v) => +v.toFixed(2)), gap: +r.gap.toFixed(2) });
    }
  }
  out.sort((a, b) => b.score - a.score);
  return { n: out.length, best: out.slice(0, 12) };
};
