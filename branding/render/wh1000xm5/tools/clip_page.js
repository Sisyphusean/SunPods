// Injected into the hero page after fit_page.js (tools only): renders one frame of a WH-1000XM5 setup
// clip from the live three.js scene, on the fit rig in front of the hero camera.
//   open: the case (on the rig; its pose is the camera move) opens, and a second, free copy of the
//         headphones lifts out of it, unfolds and turns into the hero pose (the copy in the case hides
//         the moment the free one takes over, at the same place, so there is no jump).
//   pair: the headphones (on the rig) turn to show the left cup's power button, held (press ring),
//         then the indicator beside it flashes blue.
window.XMCLIP = (() => {
  let d = null, T = null, kind = null, fly = null, flyC = null, ring = null, ringMat = null, ledGlow = null, ledMat = null;
  const ease = (t) => (t <= 0 ? 0 : t >= 1 ? 1 : t * t * (3 - 2 * t));
  // centre and surface normal of a small patch (its middle vertex), in its mesh's frame
  const patchAt = (mesh) => {
    const g = mesh.geometry, k = (g.attributes.position.count - 1) / 2;
    return { p: new T.Vector3().fromBufferAttribute(g.attributes.position, k), n: new T.Vector3().fromBufferAttribute(g.attributes.normal, k).normalize() };
  };
  async function init(which) {
    d = await window.sunpods.debugMod('wh1000xm5', false);
    T = d.THREE;
    kind = which;
    const M = d.M;
    if (which === 'open') {
      window.XMFIT.useSubject('case', 0);
      fly = M.buildPhones('Clip');
      fly.root.matrixAutoUpdate = false;
      d.scene.add(fly.root);
      fly.pose({});
      fly.root.updateMatrixWorld(true);
      flyC = new T.Box3().setFromObject(fly.root).getCenter(new T.Vector3());   // the unfolded headphones' middle (root at identity)
      fly.root.visible = false;
    } else {
      window.XMFIT.useSubject('phones', 0);
      const cup = M.buds.phones.units.L.cup;
      const pw = patchAt(cup.getObjectByName('XM5H_Power')), ld = patchAt(cup.getObjectByName('XM5H_LED'));
      ringMat = new T.MeshBasicMaterial({ color: new T.Color(1.0, 0.78, 0.36), transparent: true, opacity: 0, depthWrite: false,
        blending: T.AdditiveBlending, side: T.DoubleSide });
      ring = new T.Mesh(new T.RingGeometry(0.50, 0.60, 64), ringMat);
      ring.position.copy(pw.p).addScaledVector(pw.n, 0.03); ring.lookAt(ring.position.clone().add(pw.n));
      ring.renderOrder = 20;
      cup.add(ring);
      const cv = document.createElement('canvas'); cv.width = cv.height = 128;
      const g = cv.getContext('2d'), gr = g.createRadialGradient(64, 64, 0, 64, 64, 64);
      // a blue light, not just brighter: normal blending, so it reads blue on the pale finishes too
      gr.addColorStop(0, 'rgba(225,240,255,1)'); gr.addColorStop(0.12, 'rgba(70,150,255,0.95)'); gr.addColorStop(0.4, 'rgba(30,110,255,0.35)'); gr.addColorStop(1, 'rgba(20,90,255,0)');
      g.fillStyle = gr; g.fillRect(0, 0, 128, 128);
      const tex = new T.CanvasTexture(cv); tex.colorSpace = T.SRGBColorSpace;
      ledMat = new T.MeshBasicMaterial({ map: tex, transparent: true, opacity: 0, depthWrite: false });
      ledGlow = new T.Mesh(new T.PlaneGeometry(1.1, 1.1), ledMat);
      ledGlow.position.copy(ld.p).addScaledVector(ld.n, 0.04); ledGlow.lookAt(ledGlow.position.clone().add(ld.n)); ledGlow.renderOrder = 20;
      cup.add(ledGlow);
    }
    return true;
  }
  // the yaw and pitch (fit rig convention) that turn a part's surface normal towards the camera
  function facing(meshName) {
    const M = d.M, mesh = M.buds.phones.units.L.cup.getObjectByName(meshName), root = M.buds.phones.root;
    root.updateMatrixWorld(true);
    const n = patchAt(mesh).n;
    n.transformDirection(new T.Matrix4().copy(root.matrixWorld).invert().multiply(mesh.matrixWorld));
    const yaw = Math.atan2(-n.x, n.z);
    const n1 = n.clone().applyAxisAngle(new T.Vector3(0, 1, 0), yaw);
    const pitch = Math.atan2(n1.y, n1.z);
    return [yaw * 180 / Math.PI, pitch * 180 / Math.PI];
  }
  // s: { pose:[yaw,pitch,roll,tx,ty,scale], lid, lift, flyY, flyScale, arc, glow, ringGrow, led, size }
  function frame(s) {
    const M = d.M;
    const size = s.size || 960;
    if (kind === 'open') {
      M.kase.lid.rotation.x = -M.LID_OPEN * s.lid;
      window.XMFIT.setPose(s.pose);
      const lift = s.lift || 0;
      const inCase = M.kase.phones.root;
      inCase.visible = lift <= 0;
      fly.root.visible = lift > 0;
      if (lift > 0) {
        d.scene.updateMatrixWorld(true);
        const pa = new T.Vector3(), qa = new T.Quaternion(), sa = new T.Vector3();
        inCase.matrixWorld.decompose(pa, qa, sa);
        // the end: the hero pose in front of the clip camera, its middle on the frame's middle
        const cam = window.XMFIT.camera, H = M.HERO;
        const qb = cam.quaternion.clone().multiply(new T.Quaternion().setFromEuler(new T.Euler(H.rot[1] * Math.PI / 180, H.rot[0] * Math.PI / 180, H.rot[2] * Math.PI / 180, 'ZXY')));
        const fwd = new T.Vector3(0, 0, -1).applyQuaternion(cam.quaternion), up = new T.Vector3(0, 1, 0).applyQuaternion(cam.quaternion);
        const target = cam.position.clone().addScaledVector(fwd, 30).addScaledVector(up, s.flyY || 0);
        const sb = s.flyScale || 0.3;
        const t = ease(lift);
        const sc = sa.x + (sb - sa.x) * t;
        const q = qa.clone().slerp(qb, ease(Math.min(1, lift * 1.2)));
        // travel: the unfolded middle goes from where it sits in the case to the target, on an arc up
        const ca = flyC.clone().multiplyScalar(sa.x).applyQuaternion(qa).add(pa);
        const c = ca.clone().lerp(target, t).addScaledVector(up, Math.sin(Math.PI * t) * (s.arc || 0));
        const p = c.clone().sub(flyC.clone().multiplyScalar(sc).applyQuaternion(q));
        fly.root.matrix.compose(p, q, new T.Vector3(sc, sc, sc));
        const u = ease((lift - 0.1) / 0.65);
        fly.pose({ fold: 1 - u, ext: M.CASE.phones.ext + (H.ext - M.CASE.phones.ext) * u, squeeze: M.CASE.phones.squeeze * (1 - u) });
        fly.root.updateMatrixWorld(true);
      }
    } else {
      window.XMFIT.setPose(s.pose);
      ringMat.opacity = s.glow || 0;
      ring.scale.setScalar(1 + 0.25 * (s.ringGrow || 0));
      const led = s.led || 0;
      ledMat.opacity = led;
      M.SH.led.emissive.setRGB(0.10 * led, 0.40 * led, 1.0 * led);
    }
    const r = d.renderer;
    r.setPixelRatio(1); r.setSize(size, size, false);
    r.setClearColor(0x000000, 0);
    r.render(d.scene, window.XMFIT.camera);
    return d.canvas.toDataURL('image/png');
  }
  // where a part of the left cup lands (pixels, size x size) for a rig pose (to aim zooms)
  function project(pose, meshName, size = 960) {
    window.XMFIT.setPose(pose);
    const cam = window.XMFIT.camera;
    cam.updateMatrixWorld(); cam.updateProjectionMatrix();
    d.scene.updateMatrixWorld(true);
    const mesh = d.M.buds.phones.units.L.cup.getObjectByName(meshName);
    const v = patchAt(mesh).p.applyMatrix4(mesh.matrixWorld).project(cam);
    return [(v.x * 0.5 + 0.5) * size, (0.5 - v.y * 0.5) * size];
  }
  function done() {
    if (fly) { d.scene.remove(fly.root); fly = null; d.M.kase.phones.root.visible = true; }
    if (ring) { ring.parent.remove(ring); ring = null; }
    if (ledGlow) { ledGlow.parent.remove(ledGlow); ledGlow = null; }
    d.M.SH.led.emissive.setRGB(0, 0, 0);
    window.XMFIT.done();
  }
  return { init, frame, project, facing, done };
})();
