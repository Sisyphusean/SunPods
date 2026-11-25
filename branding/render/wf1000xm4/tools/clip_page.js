// Injected into the hero page after fit_page.js (tools only): renders one frame of a WF-1000XM4 setup
// clip from the live three.js scene. The case is held on the fit rig in front of the hero camera
// (its pose is the "camera move"); the lid, the earbuds and the effects are set per frame:
//   lift (0..1): the earbuds rise out of the case; out (0..1): they move apart and turn their touch
//   sensors to the viewer (Sony's pairing: both units out, both sensors held about 5 s);
//   hold (0..1): the fingertip rings on both sensors, prog (0..1): the hold's progress arc,
//   done (0..1): the pairing-mode pulse; led (0..1): the case indicator (green, as when the lid opens)
window.X4CLIP = (() => {
  let d = null, T = null, X4 = null, rings = [], ledGlow = null, ledMat = null;
  const lerp = (a, b, t) => a + (b - a) * t;
  async function init(lid0) {
    d = await window.sunpods.debugMod('wf1000xm4', false);
    T = d.THREE; X4 = d.M;
    const C = X4.CASE;
    window.X4FIT.useSubject('case', lid0);
    // the earbuds' seat (lid open) and their pairing pose (case frame)
    for (const an of X4.kase.anchors) { an.q0 = an.node.quaternion.clone(); an.p0 = an.node.position.clone(); }
    // fingertip rings on both touch sensors: a soft ring, a progress arc, and the pulse
    const disc = X4.BUD.Rd * X4.BH;
    rings = X4.kase.anchors.map((an) => {
      const mk = (geo, color, order) => {
        // plain (not additive) so the rings read on Platinum Silver as well as on Black
        const m = new T.Mesh(geo, new T.MeshBasicMaterial({ color, transparent: true, opacity: 0, depthWrite: false, depthTest: false, side: T.DoubleSide }));
        m.renderOrder = order; m.position.set(0, 0, 0.03); m.rotation.x = Math.atan(X4.BUD.faceTilt); an.bud.add(m); return m;
      };
      return {
        touch: mk(new T.RingGeometry(disc * 0.50, disc * 0.62, 64), new T.Color(0.98, 0.66, 0.22), 20),
        arc: mk(new T.RingGeometry(disc * 0.78, disc * 0.86, 64, 1, Math.PI / 2, 0.001), new T.Color(1.0, 0.74, 0.30), 21),
        pulse: mk(new T.RingGeometry(disc * 0.92, disc * 1.0, 64), new T.Color(0.20, 0.55, 1.0), 22),
      };
    });
    // the case indicator's glow on the front (a soft disc over the LED)
    const cv = document.createElement('canvas'); cv.width = cv.height = 128;
    const g = cv.getContext('2d'), gr = g.createRadialGradient(64, 64, 0, 64, 64, 64);
    gr.addColorStop(0, 'rgba(255,255,255,1)'); gr.addColorStop(0.25, 'rgba(255,255,255,0.55)'); gr.addColorStop(1, 'rgba(255,255,255,0)');
    g.fillStyle = gr; g.fillRect(0, 0, 128, 128);
    ledMat = new T.MeshBasicMaterial({ map: new T.CanvasTexture(cv), color: new T.Color(0.25, 1.0, 0.35), transparent: true, opacity: 0, depthWrite: false,
      blending: T.AdditiveBlending });
    ledGlow = new T.Mesh(new T.PlaneGeometry(0.62, 0.36), ledMat);
    ledGlow.position.set(0, C.led.y, C.B + 0.03); ledGlow.renderOrder = 20;
    X4.kase.base.add(ledGlow);
    return true;
  }
  // s: { pose:[yaw,pitch,roll,tx,ty,scale], lid, lift, out, hold, prog, done, led, size }
  function frame(s) {
    const C = X4.CASE;
    X4.kase.lid.rotation.x = -X4.LID_OPEN * s.lid;
    const lift = s.lift || 0, out = s.out || 0;
    for (const an of X4.kase.anchors) {
      const side = an.side === 'R' ? 1 : -1;
      // seated (dropping under the closed lid) -> straight up out of the well -> apart, sensors to the viewer
      const y = an.y - C.budSeat.drop * (1 - Math.min(1, s.lid * 1.6)) + 1.9 * lift + 0.35 * out;
      an.node.position.set(lerp(an.p0.x, side * 1.75, out), y, lerp(an.p0.z, 0.9, out));
      const qOut = new T.Quaternion().setFromEuler(new T.Euler(0.12, -side * 0.32, 0, 'YXZ'));
      const qSeat = an.q0.clone();
      if (side < 0) { qOut.y *= -1; qOut.z *= -1; }   // mirrored for the left earbud
      an.node.quaternion.copy(qSeat).slerp(qOut, out);
      an.node.visible = true;
    }
    const hold = s.hold || 0, prog = s.prog || 0, done = s.done || 0;
    for (const r of rings) {
      r.touch.material.opacity = hold * 0.9;
      r.touch.scale.setScalar(1 - 0.08 * hold);
      const disc = X4.BUD.Rd * X4.BH;
      r.arc.geometry.dispose();
      r.arc.geometry = new T.RingGeometry(disc * 0.78, disc * 0.86, 64, 1, Math.PI / 2, -Math.max(0.001, prog) * Math.PI * 2);
      r.arc.material.opacity = hold > 0 ? Math.min(1, hold * 1.2) : 0;
      r.pulse.material.opacity = done;
      r.pulse.scale.setScalar(1 + 0.35 * (s.doneGrow || 0));
    }
    ledMat.opacity = s.led || 0;
    X4.M.led.emissive.setRGB(0.10 * (s.led || 0), 0.85 * (s.led || 0), 0.20 * (s.led || 0));
    window.X4FIT.setPose(s.pose);
    const size = s.size || 960;
    const r = d.renderer;
    r.setPixelRatio(1); r.setSize(size, size, false);
    r.setClearColor(0x000000, 0);
    r.render(d.scene, window.X4FIT.camera);
    return d.canvas.toDataURL('image/png');
  }
  // where a case-frame point lands (pixels, size x size) for a pose (to aim the camera)
  function project(pose, pt, size = 960, lid = 1) {
    window.X4FIT.setPose(pose);
    X4.kase.lid.rotation.x = -X4.LID_OPEN * lid;
    const cam = window.X4FIT.camera;
    cam.updateMatrixWorld(); cam.updateProjectionMatrix();
    X4.kase.base.updateWorldMatrix(true, false);
    const v = new T.Vector3(pt[0], pt[1], pt[2]).applyMatrix4(X4.kase.base.matrixWorld).project(cam);
    return [(v.x * 0.5 + 0.5) * size, (0.5 - v.y * 0.5) * size];
  }
  function done() {
    for (const r of rings) for (const m of [r.touch, r.arc, r.pulse]) { m.parent.remove(m); m.geometry.dispose(); m.material.dispose(); }
    rings = [];
    if (ledGlow) ledGlow.parent.remove(ledGlow);
    X4.M.led.emissive.setRGB(0, 0, 0);
    for (const an of X4.kase.anchors) if (an.q0) { an.node.quaternion.copy(an.q0); an.node.position.copy(an.p0); }
    window.X4FIT.done();
  }
  return { init, frame, project, done };
})();
