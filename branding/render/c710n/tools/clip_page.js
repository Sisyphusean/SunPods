// Injected into the hero page after fit_page.js (tools only): renders one frame of a WF-C710N setup
// clip from the live three.js scene. The case is held on the fit rig in front of the hero camera
// (its pose is the "camera move"), the lid, the earbuds and two effects are set per frame:
//   glow: the pairing button's press ring (0..1), led: the case indicator, blue (0..1)
window.C7CLIP = (() => {
  let d = null, T = null, ring = null, ringMat = null, ledGlow = null, ledMat = null, kind = null;
  async function init(lid0) {
    d = await window.sunpods.debugC7(false);
    T = d.THREE;
    const C7 = d.C7, C = C7.CASE;
    window.C7FIT.useSubject('case', lid0);
    kind = 'case';
    // the press ring round the pairing button (back, left of USB-C seen from behind)
    ringMat = new T.MeshBasicMaterial({ color: new T.Color(1.0, 0.78, 0.36), transparent: true, opacity: 0, depthWrite: false,
      blending: T.AdditiveBlending, side: T.DoubleSide });
    ring = new T.Mesh(new T.RingGeometry(0.30, 0.36, 64), ringMat);
    ring.position.set(C.button.x, C.button.y, -C.B - 0.03); ring.rotation.y = Math.PI; ring.renderOrder = 20;
    C7.kase.base.add(ring);
    // the indicator's glow on the front (a soft disc over the LED)
    const cv = document.createElement('canvas'); cv.width = cv.height = 128;
    const g = cv.getContext('2d'), gr = g.createRadialGradient(64, 64, 0, 64, 64, 64);
    gr.addColorStop(0, 'rgba(255,255,255,1)'); gr.addColorStop(0.25, 'rgba(255,255,255,0.55)'); gr.addColorStop(1, 'rgba(255,255,255,0)');
    g.fillStyle = gr; g.fillRect(0, 0, 128, 128);
    const tex = new T.CanvasTexture(cv);
    ledMat = new T.MeshBasicMaterial({ map: tex, color: new T.Color(0.15, 0.45, 1.0), transparent: true, opacity: 0, depthWrite: false,
      blending: T.AdditiveBlending });
    ledGlow = new T.Mesh(new T.PlaneGeometry(0.55, 0.55), ledMat);
    ledGlow.position.set(0, C.led.y, C.B + 0.04); ledGlow.renderOrder = 20;
    C7.kase.base.add(ledGlow);
    return true;
  }
  // s: { pose:[yaw,pitch,roll,tx,ty,scale], lid, lift (0..1: earbuds out of the case), glow, led, size }
  function frame(s) {
    const C7 = d.C7, C = C7.CASE;
    C7.kase.lid.rotation.x = -C7.LID_OPEN * s.lid;
    const lift = s.lift || 0;
    for (const an of C7.kase.anchors) {
      const side = an.side === 'R' ? 1 : -1;
      an.node.position.y = an.y - C.budSeat.drop * (1 - Math.min(1, s.lid * 1.6)) + 2.4 * lift;
      an.node.position.x = side * (C.budSeat.pos[0] + 0.55 * lift);
      an.node.position.z = C.budSeat.pos[2] + 0.6 * lift;
      an.node.visible = true;
      if (!an.q0) an.q0 = an.node.quaternion.clone();
      // as they rise the earbuds turn their discs from straight up towards the viewer (in the case frame)
      const qx = new T.Quaternion().setFromAxisAngle(new T.Vector3(1, 0, 0), 0.95 * lift);
      const qz = new T.Quaternion().setFromAxisAngle(new T.Vector3(0, 0, 1), side * 0.30 * lift);
      an.node.quaternion.copy(qz).multiply(qx).multiply(an.q0);
    }
    ringMat.opacity = s.glow || 0;
    ring.scale.setScalar(1 + 0.25 * (s.ringGrow || 0));
    const led = s.led || 0;
    ledMat.opacity = led;
    C7.M.led.emissive.setRGB(0.10 * led, 0.40 * led, 1.0 * led);
    window.C7FIT.setPose(s.pose);
    const size = s.size || 960;
    const r = d.renderer;
    r.setPixelRatio(1); r.setSize(size, size, false);
    r.setClearColor(0x000000, 0);
    r.render(d.scene, window.C7FIT.camera);
    return d.canvas.toDataURL('image/png');
  }
  // where a case-frame point lands (pixels, size x size) for a pose (to aim zooms)
  function project(pose, pt, size = 960, lid = 1) {
    window.C7FIT.setPose(pose);
    const C7 = d.C7;
    C7.kase.lid.rotation.x = -C7.LID_OPEN * lid;
    const cam = window.C7FIT.camera;
    cam.updateMatrixWorld(); cam.updateProjectionMatrix();
    C7.kase.base.updateWorldMatrix(true, false);
    const v = new T.Vector3(pt[0], pt[1], pt[2]).applyMatrix4(C7.kase.base.matrixWorld).project(cam);
    return [(v.x * 0.5 + 0.5) * size, (0.5 - v.y * 0.5) * size];
  }
  function done() { if (ring) ring.parent.remove(ring); if (ledGlow) ledGlow.parent.remove(ledGlow); d.C7.M.led.emissive.setRGB(0, 0, 0); window.C7FIT.done(); }
  return { init, frame, project, done };
})();
