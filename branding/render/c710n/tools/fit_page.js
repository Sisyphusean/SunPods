// Injected into the hero page (?debug) by board.mjs: silhouette-fitted views of the WF-C710N model.
// The camera stays the hero camera (same orientation, so the scene's environment and rim light act as
// in the app); the subject (one earbud or the case) is moved in front of it on a rig, turned by
// yaw (about its own up axis), pitch (towards the camera) and roll, and fitted to Sony's silhouette.
window.C7FIT = (() => {
  let d = null, T = null, rig = null, cam = null, maskMat = null, rtS = null, rtL = null;
  let subject = null, restore = null;
  const D = 30, FOV = 14;
  const viewH = () => 2 * D * Math.tan(FOV * Math.PI / 360);

  async function init() {
    d = await window.sunpods.debugC7();
    T = d.THREE;
    d.stopMotion(); d.anims.clear();
    d.setDeviceNow('c710n');
    rig = new T.Group(); rig.name = 'C7FIT_rig'; d.scene.add(rig);
    cam = new T.PerspectiveCamera(FOV, 1, 1, 200);
    maskMat = new T.MeshBasicMaterial({ color: 0xffffff, side: T.DoubleSide });
    rtS = new T.WebGLRenderTarget(192, 192, { samples: 4 });
    rtL = new T.WebGLRenderTarget(512, 512, { samples: 4 });
    return true;
  }
  function hideAll() {
    const C7 = d.C7;
    C7.buds.root.visible = false; C7.kase.root.visible = false;
    d.scene.traverse((o) => { if (o.name === 'buds' || o.name === 'case') o.visible = false; });
  }
  function useSubject(kind, lid = 0) {
    if (restore) restore();
    const C7 = d.C7;
    hideAll();
    let obj;
    if (kind === 'case') {
      obj = C7.kase.spin;
      C7.kase.lid.rotation.x = -C7.LID_OPEN * lid;
      C7.kase.spin.rotation.y = 0;
      for (const an of C7.kase.anchors) { an.node.position.y = an.y - C7.CASE.budSeat.drop * (1 - Math.min(1, lid * 1.6)); an.node.visible = true; }
    } else obj = kind === 'budR' ? C7.buds.budR : C7.buds.budL;
    const parent = obj.parent, pos = obj.position.clone(), quat = obj.quaternion.clone(), scl = obj.scale.clone();
    const holder = new T.Group();
    rig.add(holder); holder.add(obj);
    obj.position.set(0, 0, 0); obj.quaternion.identity();
    obj.scale.set(Math.sign(scl.x), 1, 1);
    // centre the subject's bounding box on the rig origin
    rig.position.set(0, 0, 0); rig.quaternion.identity(); rig.scale.setScalar(1); rig.updateMatrixWorld(true);
    // (earbuds centre on their shell only, so ear-tip changes don't move the body; C7FIT_CENTRE = 'full' for the old way)
    const centreOn = kind !== 'case' && window.C7FIT_CENTRE !== 'full' ? obj.getObjectByName('C7_BudShell') : obj;
    const box = new T.Box3().setFromObject(centreOn, true);
    const c = box.getCenter(new T.Vector3()), size = box.getSize(new T.Vector3());
    holder.position.copy(c).negate();
    subject = { kind, obj, holder, diag: size.length(), size };
    restore = () => { parent.add(obj); obj.position.copy(pos); obj.quaternion.copy(quat); obj.scale.copy(scl); rig.remove(holder); restore = null; };
    return { size: size.toArray() };
  }
  function setPose(p) {
    const [yaw, pitch, roll, tx, ty, s] = p;
    const hc = d.camera;
    hc.updateMatrixWorld();
    cam.position.copy(hc.position); cam.quaternion.copy(hc.quaternion); cam.updateMatrixWorld();
    const fwd = new T.Vector3(0, 0, -1).applyQuaternion(hc.quaternion);
    const right = new T.Vector3(1, 0, 0).applyQuaternion(hc.quaternion);
    const up = new T.Vector3(0, 1, 0).applyQuaternion(hc.quaternion);
    const vh = viewH();
    rig.position.copy(hc.position).addScaledVector(fwd, D).addScaledVector(right, tx * vh).addScaledVector(up, ty * vh);
    const qo = new T.Quaternion().setFromEuler(new T.Euler(pitch * Math.PI / 180, yaw * Math.PI / 180, roll * Math.PI / 180, 'ZXY'));
    rig.quaternion.copy(hc.quaternion).multiply(qo);
    rig.scale.setScalar(s);
    rig.updateMatrixWorld(true);
  }
  function renderTo(rt, mask) {
    const r = d.renderer;
    const prevOverride = d.scene.overrideMaterial, prevEnv = null;
    const prevClear = r.getClearAlpha();
    d.scene.overrideMaterial = mask ? maskMat : null;
    r.setRenderTarget(rt); r.setClearColor(0x000000, 0); r.clear();
    r.render(d.scene, cam);
    r.setRenderTarget(null);
    d.scene.overrideMaterial = prevOverride;
    void prevEnv; void prevClear;
    const px = new Uint8Array(rt.width * rt.height * 4);
    r.readRenderTargetPixels(rt, 0, 0, rt.width, rt.height, px);
    return px;
  }
  function maskOf(px, w, h) {   // flip to top-down rows, alpha > 127
    const m = new Uint8Array(w * h);
    for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) m[(h - 1 - y) * w + x] = px[(y * w + x) * 4 + 3] > 127 ? 1 : 0;
    return m;
  }
  function iou(a, b) {
    let i = 0, u = 0;
    for (let k = 0; k < a.length; k++) { const x = a[k], y = b[k]; i += x & y; u += x | y; }
    return u ? i / u : 0;
  }
  function bbox(m, w) {
    let x0 = 1e9, y0 = 1e9, x1 = -1, y1 = -1;
    for (let k = 0; k < m.length; k++) if (m[k]) { const x = k % w, y = (k / w) | 0; if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y; }
    return x1 < 0 ? null : [x0, y0, x1 + 1, y1 + 1];
  }
  async function decodeMask(b64, size) {
    const img = new Image(); img.src = 'data:image/png;base64,' + b64; await img.decode();
    const cv = document.createElement('canvas'); cv.width = cv.height = size;
    const c = cv.getContext('2d'); c.drawImage(img, 0, 0, size, size);
    const px = c.getImageData(0, 0, size, size).data, m = new Uint8Array(size * size);
    for (let k = 0; k < m.length; k++) m[k] = px[k * 4] > 127 ? 1 : 0;
    return m;
  }
  function score(p, ref) { setPose(p); return iou(maskOf(renderTo(rtS, true), 192, 192), ref); }
  // align scale and position so the model's box matches the reference box
  function alignBox(p, ref) {
    const rb = bbox(ref, 192);
    for (let it = 0; it < 3; it++) {
      setPose(p);
      const mb = bbox(maskOf(renderTo(rtS, true), 192, 192), 192);
      if (!mb) { p[5] *= 0.7; continue; }
      const k = Math.sqrt(((rb[2] - rb[0]) * (rb[3] - rb[1])) / ((mb[2] - mb[0]) * (mb[3] - mb[1])));
      p[5] *= k;
      const dx = ((rb[0] + rb[2]) - (mb[0] + mb[2])) / 2 / 192, dy = ((rb[1] + rb[3]) - (mb[1] + mb[3])) / 2 / 192;
      p[3] += dx; p[4] -= dy;
    }
    return p;
  }
  function nelderMead(f, x0, steps, iters) {
    const n = x0.length;
    let pts = [x0.slice()];
    for (let i = 0; i < n; i++) { const x = x0.slice(); x[i] += steps[i]; pts.push(x); }
    let vals = pts.map(f);
    for (let it = 0; it < iters; it++) {
      const ord = vals.map((v, i) => i).sort((a, b) => vals[a] - vals[b]);
      pts = ord.map((i) => pts[i]); vals = ord.map((i) => vals[i]);
      const cen = new Array(n).fill(0);
      for (let i = 0; i < n; i++) for (let j = 0; j < n; j++) cen[j] += pts[i][j] / n;
      const xr = cen.map((c, j) => c + (c - pts[n][j])); const fr = f(xr);
      if (fr < vals[0]) {
        const xe = cen.map((c, j) => c + 2 * (c - pts[n][j])); const fe = f(xe);
        if (fe < fr) { pts[n] = xe; vals[n] = fe; } else { pts[n] = xr; vals[n] = fr; }
      } else if (fr < vals[n - 1]) { pts[n] = xr; vals[n] = fr; }
      else {
        const xc = cen.map((c, j) => c + 0.5 * (pts[n][j] - c)); const fc = f(xc);
        if (fc < vals[n]) { pts[n] = xc; vals[n] = fc; }
        else { for (let i = 1; i <= n; i++) { pts[i] = pts[i].map((v, j) => pts[0][j] + 0.5 * (v - pts[0][j])); vals[i] = f(pts[i]); } }
      }
    }
    const b = vals.indexOf(Math.min(...vals));
    return { x: pts[b], v: vals[b] };
  }
  async function fit(kind, lid, refB64, init, opts = {}) {
    useSubject(kind, lid);
    const ref0 = await decodeMask(refB64, 192);
    const ref = ref0;
    // a locked pitch (degrees) is held fixed while the rest is fitted
    const lockP = opts.lockPitch;
    const score0 = score;
    const scoreL = lockP === undefined ? score0 : (x, r) => { x[1] = lockP; return score0(x, r); };
    const s0 = 0.82 * viewH() / subject.diag;
    let best = null;
    const starts = opts.starts || [[0, 0, 0], [20, 0, 0], [-20, 0, 0], [0, 15, 0], [0, -15, 0]];
    for (const st of starts) {
      const p = alignBox([init[0] + st[0], init[1] + st[1], init[2] + st[2], 0, 0, s0], ref);
      if (lockP !== undefined) p[1] = lockP;
      const r = nelderMead((x) => -scoreL(x, ref), p, [10, 8, 6, 0.03, 0.03, s0 * 0.05], opts.iters || 160);
      if (!best || r.v < best.v) best = r;
    }
    // polish
    const r2 = nelderMead((x) => -scoreL(x, ref), best.x, [3, 3, 2, 0.01, 0.01, s0 * 0.015], 160);
    if (r2.v < best.v) best = r2;
    if (lockP !== undefined) best.x[1] = lockP;
    return { pose: best.x, iou: -best.v };
  }
  function scoreAt(kind, lid, pose, ref) { useSubject(kind, lid); return score(pose, ref); }
  function centroid(m) {
    let sx = 0, sy = 0, n = 0;
    for (let k = 0; k < m.length; k++) if (m[k]) { sx += k % 192; sy += (k / 192) | 0; n++; }
    return n ? [sx / n, sy / n] : [96, 96];
  }
  // Sony's 360 viewer: one turntable. Shared elevation (pitch), roll and scale; each frame's yaw is
  // yaw0 plus that frame's turntable angle (measured once from the silhouettes, spin_hull.py);
  // each frame's position follows its silhouette's centroid.
  async function fitGroup(kind, items, init) {
    useSubject(kind, 0);
    const refs = [];
    for (const it of items) refs.push(await decodeMask(it.b64, 192));
    const rc = refs.map(centroid);
    const s0 = 0.82 * viewH() / subject.diag;
    const tr = items.map(() => [0, 0]);
    const evalShared = (x) => {
      let sum = 0; const per = [];
      items.forEach((it, k) => {
        const p = [x[0] + it.phi, x[1], x[2], tr[k][0], tr[k][1], x[3]];
        for (let a = 0; a < 2; a++) {
          setPose(p);
          const c = centroid(maskOf(renderTo(rtS, true), 192, 192));
          p[3] += (rc[k][0] - c[0]) / 192; p[4] -= (rc[k][1] - c[1]) / 192;
        }
        tr[k] = [p[3], p[4]];
        setPose(p);
        const v = iou(maskOf(renderTo(rtS, true), 192, 192), refs[k]);
        sum += v; per.push(v);
      });
      return { mean: sum / items.length, per };
    };
    // scale start from the first frame's box
    const p0 = alignBox([init[0] + items[0].phi, init[1], init[2], 0, 0, s0], refs[0]);
    let best = null;
    for (const dy of [0, 12, -12]) {
      const r = nelderMead((x) => -evalShared(x).mean, [init[0] + dy, init[1], init[2], p0[5]], [8, 6, 4, p0[5] * 0.05], 70);
      if (!best || r.v < best.v) best = r;
    }
    const r2 = nelderMead((x) => -evalShared(x).mean, best.x, [2, 2, 2, best.x[3] * 0.015], 60);
    if (r2.v < best.v) best = r2;
    const fin = evalShared(best.x);
    return { shared: best.x, poses: items.map((it, k) => [best.x[0] + it.phi, best.x[1], best.x[2], tr[k][0], tr[k][1], best.x[3]]), per: fin.per, mean: fin.mean };
  }
  function shot(kind, lid, pose, color, size = 512) {
    useSubject(kind, lid);
    if (color) d.setColor(color, true);
    const rt = size === 512 ? rtL : new T.WebGLRenderTarget(size, size, { samples: 4 });
    setPose(pose);
    // colour: through the page canvas, exactly the app's output path
    const r = d.renderer;
    r.setPixelRatio(1); r.setSize(size, size, false);
    r.setClearColor(0x000000, 0);
    r.render(d.scene, cam);
    const colorUrl = d.canvas.toDataURL('image/png');
    d.resize();
    const msk = renderTo(rt, true);
    const cv = document.createElement('canvas'); cv.width = cv.height = size;
    const c = cv.getContext('2d'), id = c.createImageData(size, size);
    for (let y = 0; y < size; y++) for (let x = 0; x < size; x++) {
      const s = ((size - 1 - y) * size + x) * 4, t = (y * size + x) * 4, v = msk[s + 3] > 127 ? 255 : 0;
      id.data[t] = id.data[t + 1] = id.data[t + 2] = v; id.data[t + 3] = 255;
    }
    c.putImageData(id, 0, 0);
    return { color: colorUrl, mask: cv.toDataURL('image/png') };
  }
  function done() { if (restore) restore(); rig.position.set(0, 0, 0); }
  // screen positions (pixels in a size x size shot) of points given in the subject's own frame
  function project(kind, lid, pose, pts, size = 512) {
    useSubject(kind, lid); setPose(pose);
    cam.updateMatrixWorld(); cam.updateProjectionMatrix();
    subject.obj.updateWorldMatrix(true, false);
    return pts.map((q) => {
      const v = new T.Vector3(q[0], q[1], q[2]).applyMatrix4(subject.obj.matrixWorld).project(cam);
      return [(v.x * 0.5 + 0.5) * size, (0.5 - v.y * 0.5) * size];
    });
  }
  async function rebuild(overrides) {
    if (restore) restore();
    await window.sunpods.debugRebuildC7(overrides);
    d = await window.sunpods.debugC7();
    return true;
  }
  return { init, rebuild, project, fit, fitGroup, shot, scoreAt, useSubject, setPose, done, decodeMask, get subject() { return subject; }, get camera() { return cam; } };
})();
