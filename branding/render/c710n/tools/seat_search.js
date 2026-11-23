// Injected into the hero page: searches the earbud seat pose in the case so that nothing pokes
// through the case walls with the lid closed, the touch disc faces up (within a limit), and the mic
// sits as high and as far out as it can. Prints the best candidates. (Tools only.)
window.C7SEAT = async (opts = {}) => {
  const d = await window.sunpods.debugC7();
  const T = d.THREE, C7 = d.C7, C = C7.CASE, BH = C7.BH;
  const bud = C7.buds.budR;
  // sample points: shell and ear tip vertices in the earbud's own frame
  const pts = [];
  bud.traverse((o) => {
    if (!o.isMesh || !/Shell|Tip|Nozzle/.test(o.name)) return;
    const p = o.geometry.getAttribute('position');
    for (let i = 0; i < p.count; i += 7) pts.push(new T.Vector3(p.getX(i), p.getY(i), p.getZ(i)));
  });
  const mic = new T.Vector3(C7.BUD.mic.at[0] * BH, C7.BUD.mic.at[1] * BH, -0.12 * BH);
  // inside test for the closed case (inner surface: walls inset by the wall thickness)
  const ell = (y, v, hx) => (y >= v ? 0 : hx * (1 - Math.sqrt(Math.max(0, 1 - Math.pow(1 - y / v, 2)))));
  function inside(x, y, z, margin) {
    const t = C.wall + margin;
    if (y < t) return false;
    let dx, dz;
    if (y < C.yP) { dx = ell(y, C.bottom.v, C.bottom.hx); dz = ell(y, C.bottom.v, C.bottom.hz); }
    else {
      const dd = C.H - C.wall - y; if (dd < margin) return false;
      const k = dd >= C.top.v ? 0 : 1 - Math.sqrt(Math.max(0, 1 - Math.pow(1 - dd / C.top.v, 2)));
      dx = C.top.hx * k; dz = C.top.hz * k;
    }
    const A = C.A - t - dx, B = C.B - t - dz;
    if (A <= 0 || B <= 0) return false;
    const r = Math.min(B, C.B * C.endR * Math.min(1, B / C.B));
    const qx = Math.abs(x) - (A - r), qz = Math.abs(z) - (B - r);
    return Math.hypot(Math.max(qx, 0), Math.max(qz, 0)) + Math.min(Math.max(qx, qz), 0) - r <= 0;
  }
  const seat = C.budSeat;
  const bx = new T.Vector3(...seat.x).normalize(), by = new T.Vector3(...seat.y).normalize(), bz = new T.Vector3().crossVectors(bx, by);
  const basis = new T.Matrix4().makeBasis(bx, by, bz);
  const res = [];
  const sc = opts.scale || seat.scale || 1;
  if (opts.test) {   // count points outside the closed case for the current seat (or a given one)
    const t = opts.test.tilt || seat.tilt, ps = opts.test.pos || [seat.pos[0], seat.pos[1] - seat.drop, seat.pos[2]];
    const R = new T.Matrix4().makeRotationFromEuler(new T.Euler(t[0] * Math.PI / 180, t[1] * Math.PI / 180, t[2] * Math.PI / 180, 'ZXY')).multiply(basis);
    const M = new T.Matrix4().makeTranslation(ps[0], ps[1], ps[2]).multiply(R).multiply(new T.Matrix4().makeScale(sc, sc, sc));
    let bad = 0, minX = 1e9;
    for (const p of pts) { const w = p.clone().applyMatrix4(M); if (!inside(w.x, w.y, w.z, 0.02)) bad++; minX = Math.min(minX, w.x); }
    return { bad, of: pts.length, gap: 2 * minX };
  }
  if (opts.mode === 'tipdown') {   // ear tip down into a well, disc up and a little to the front, body above the deck
    const tipPts = [];
    bud.getObjectByName('C7_Tip').geometry.getAttribute('position');
    { const g = bud.getObjectByName('C7_Tip').geometry.getAttribute('position'); for (let i = 0; i < g.count; i += 11) tipPts.push(new T.Vector3(g.getX(i), g.getY(i), g.getZ(i))); }
    const tipC = tipPts.reduce((a, v) => a.add(v), new T.Vector3()).multiplyScalar(1 / tipPts.length);
    const shellPts = []; bud.getObjectByName('C7_BudShell').geometry.getAttribute('position');
    { const g = bud.getObjectByName('C7_BudShell').geometry.getAttribute('position'); for (let i = 0; i < g.count; i += 13) shellPts.push(new T.Vector3(g.getX(i), g.getY(i), g.getZ(i))); }
    const deckY = C.yP - C.deck;
    const out = [];
    for (let tx = -60; tx <= 60; tx += 6) for (let ty = -180; ty < 180; ty += 15) for (let tz = -60; tz <= 60; tz += 6) {
      const R = new T.Matrix4().makeRotationFromEuler(new T.Euler(tx * Math.PI / 180, ty * Math.PI / 180, tz * Math.PI / 180, 'ZXY')).multiply(basis);
      const n = new T.Vector3(0, 0, 1).applyMatrix4(R);
      if (n.y < Math.cos((opts.maxTilt || 40) * Math.PI / 180)) continue;
      for (const px of (opts.xs || [0.75, 0.85, 0.95, 1.05])) for (const py of (opts.ys || [2.35, 2.5, 2.65])) for (const pz of [-0.15, 0, 0.15]) {
        const M = new T.Matrix4().makeTranslation(px, py - seat.drop, pz).multiply(R).multiply(new T.Matrix4().makeScale(sc, sc, sc));
        let bad = 0, minX = 1e9;
        for (const p of pts) { const w = p.clone().applyMatrix4(M); if (!inside(w.x, w.y, w.z, 0.02)) { bad++; break; } if (w.x < minX) minX = w.x; }
        if (bad) continue;
        let low = 0;
        for (const p of shellPts) { const w = p.clone().applyMatrix4(M); if (w.y < deckY + 0.02) low++; }
        if (low > shellPts.length * (opts.lowMax || 0.3)) continue;
        const tc = tipC.clone().applyMatrix4(M);
        const gap = 2 * minX;
        if (gap < (opts.minGap || 0.2)) continue;
        const nT = opts.nT ? new T.Vector3(...opts.nT).normalize() : null;
        const score = nT ? -3.0 * n.distanceTo(nT) - 0.5 * tc.y - 2.0 * Math.abs(gap - 0.30)
          : -tc.y + 0.3 * n.y + 0.3 * n.z - 2.0 * Math.abs(gap - 0.30);
        out.push({ score, tilt: [tx, ty, tz], pos: [px, py, pz], tip: tc.toArray().map((v) => +v.toFixed(2)), gap: +gap.toFixed(2), n: n.toArray().map((v) => +v.toFixed(2)), low });
      }
    }
    out.sort((a, b) => b.score - a.score);
    return { n: out.length, best: out.slice(0, 10) };
  }
  for (let tx = -30; tx <= 40; tx += 5) for (let ty = -180; ty < 180; ty += 15) for (let tz = -40; tz <= 40; tz += 5) {
    const R = new T.Matrix4().makeRotationFromEuler(new T.Euler(tx * Math.PI / 180, ty * Math.PI / 180, tz * Math.PI / 180, 'ZXY')).multiply(basis);
    const n = new T.Vector3(0, 0, 1).applyMatrix4(R);
    if (n.y < Math.cos((opts.maxTilt || 40) * Math.PI / 180)) continue;
    for (const px of (opts.xs || [0.85, 0.95, 1.05, 1.15])) for (const py of (opts.ys || [2.35, 2.45, 2.55])) for (const pz of [-0.15, -0.05, 0.05, 0.15]) {
      const drop = seat.drop;
      let bad = 0, minX = 1e9;
      const M = new T.Matrix4().makeTranslation(px, py - drop, pz).multiply(R).multiply(new T.Matrix4().makeScale(sc, sc, sc));
      for (const p of pts) {
        const w = p.clone().applyMatrix4(M);
        if (!inside(w.x, w.y, w.z, 0.02)) { bad++; if (bad > 0) break; }
        if (w.x < minX) minX = w.x;
      }
      if (bad) continue;
      const m = mic.clone().applyMatrix4(new T.Matrix4().makeTranslation(px, py, pz).multiply(R).multiply(new T.Matrix4().makeScale(sc, sc, sc)));
      const gap = 2 * minX;                         // the left earbud mirrors this one
      const score = m.y + 0.6 * (m.x - px) + 0.3 * m.z - 2.0 * Math.max(0, gap - 0.25) - 3.0 * Math.max(0, -gap) + 0.5 * n.y;
      res.push({ score, tilt: [tx, ty, tz], pos: [px, py, pz], micY: m.y, micOut: m.x - px, gap, ny: n.y });
    }
  }
  res.sort((a, b) => b.score - a.score);
  return { n: res.length, best: res.slice(0, 12) };
};
