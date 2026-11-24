// Injected into the hero page after fit_page.js (tools only): renders one frame of a WH-1000XM5 setup
// clip from the live three.js scene, on the fit rig in front of the hero camera.
window.XMCLIP = (() => {
  let d = null, T = null, kind = null, hpRoot = null, inCaseAnchor = null;
  let ring = null, ringMat = null, pwrCap = null, pwrCapZ0 = 0;
  let ledGlow = null, ledMat = null, ledPrevEmissive = null, ledPrevIntensity = 0;
  const ease = (t) => (t <= 0 ? 0 : t >= 1 ? 1 : t * t * (3 - 2 * t));

  function leftCup() {
    return d.M.headphoneModel.parts.leftSide.earcupShell.parent;
  }

  async function init(which) {
    d = await window.sunpods.debugMod('wh1000xm5', false);
    T = d.THREE;
    kind = which;
    const M = d.M;
    hpRoot = M.headphoneModel.rootGroup;

    if (which === 'open') {
      window.XMFIT.useSubject('case', 0);
      inCaseAnchor = window.XMFIT.subject.inCaseAnchor;
      hpRoot.visible = false;
    } else {
      window.XMFIT.useSubject('phones', 0);
      const cup = leftCup();
      const pwGroup = cup.getObjectByName('powerButton');
      pwrCap = cup.getObjectByName('powerButtonCap');
      pwrCapZ0 = pwrCap ? pwrCap.position.z : 0;
      const ldGroup = cup.getObjectByName('statusLed');

      ringMat = new T.MeshBasicMaterial({
        color: new T.Color(1.0, 0.78, 0.36),
        transparent: true,
        opacity: 0,
        depthWrite: false,
        blending: T.AdditiveBlending,
        side: T.DoubleSide,
      });
      ring = new T.Mesh(new T.RingGeometry(0.052, 0.062, 64), ringMat);
      ring.position.set(0, 0, 0.008);
      ring.renderOrder = 20;
      pwGroup.add(ring);

      const cv = document.createElement('canvas'); cv.width = cv.height = 128;
      const g = cv.getContext('2d'), gr = g.createRadialGradient(64, 64, 0, 64, 64, 64);
      gr.addColorStop(0, 'rgba(225,240,255,1)');
      gr.addColorStop(0.14, 'rgba(70,150,255,0.95)');
      gr.addColorStop(0.42, 'rgba(30,110,255,0.35)');
      gr.addColorStop(1, 'rgba(20,90,255,0)');
      g.fillStyle = gr; g.fillRect(0, 0, 128, 128);
      const tex = new T.CanvasTexture(cv); tex.colorSpace = T.SRGBColorSpace;
      ledMat = new T.MeshBasicMaterial({ map: tex, transparent: true, opacity: 0, depthWrite: false });
      ledGlow = new T.Mesh(new T.PlaneGeometry(0.11, 0.11), ledMat);
      ledGlow.position.set(0, 0, 0.006);
      ledGlow.renderOrder = 20;
      ldGroup.add(ledGlow);

      const lm = M.headphoneModel.materials.statusLedMaterial;
      ledPrevEmissive = lm.emissive.clone();
      ledPrevIntensity = lm.emissiveIntensity;
    }
    return true;
  }

  function facing(name) {
    const obj = leftCup().getObjectByName(name);
    hpRoot.updateMatrixWorld(true);
    const n = new T.Vector3(0, 0, 1);
    n.transformDirection(new T.Matrix4().copy(hpRoot.matrixWorld).invert().multiply(obj.matrixWorld));
    const yaw = Math.atan2(-n.x, n.z);
    const n1 = n.clone().applyAxisAngle(new T.Vector3(0, 1, 0), yaw);
    const pitch = Math.atan2(n1.y, n1.z);
    return [yaw * 180 / Math.PI, pitch * 180 / Math.PI];
  }

  function frame(s) {
    const M = d.M;
    const size = s.size || 960;
    if (kind === 'open') {
      M.carryCaseModel.setOpenAmount(s.lid);
      window.XMFIT.setPose(s.pose);
      const lift = s.lift || 0;
      if (lift <= 0) {
        if (hpRoot.parent !== inCaseAnchor) {
          inCaseAnchor.add(hpRoot);
          hpRoot.position.set(0, 0, 0);
          hpRoot.quaternion.identity();
          hpRoot.scale.set(1, 1, 1);
        }
        M.headphoneModel.setSliderExtension(0);
        M.headphoneModel.setEarcupSwivel(1);
        hpRoot.visible = s.lid > 0.015;
      } else {
        d.scene.updateMatrixWorld(true);
        const pa = new T.Vector3(), qa = new T.Quaternion(), sa = new T.Vector3();
        inCaseAnchor.matrixWorld.decompose(pa, qa, sa);
        if (hpRoot.parent !== d.scene) d.scene.add(hpRoot);
        hpRoot.visible = true;

        const cam = window.XMFIT.camera, H = M.HERO;
        const qb = cam.quaternion.clone().multiply(
          new T.Quaternion().setFromEuler(
            new T.Euler(H.rot[1] * Math.PI / 180, H.rot[0] * Math.PI / 180, H.rot[2] * Math.PI / 180, 'YXZ'),
          ),
        );
        const fwd = new T.Vector3(0, 0, -1).applyQuaternion(cam.quaternion);
        const up = new T.Vector3(0, 1, 0).applyQuaternion(cam.quaternion);
        const target = cam.position.clone().addScaledVector(fwd, 30).addScaledVector(up, s.flyY || 0);
        const sb = s.flyScale || 2.15;
        const t = ease(lift);

        const curCenter = new T.Vector3();
        M.applyUnpackKinematics(t, curCenter);

        const sc = sa.x + (sb - sa.x) * t;
        const q = qa.clone().slerp(qb, ease(Math.min(1, lift * 1.15)));
        const ca = M.centerFolded.clone().multiplyScalar(sa.x).applyQuaternion(qa).add(pa);
        const arcSin = Math.sin(Math.PI * t);
        const c = ca.clone().lerp(target, t)
          .addScaledVector(up, arcSin * (s.arc || 0))
          .addScaledVector(fwd, -arcSin * (s.arcZ || 0));
        const p = c.clone().sub(curCenter.multiplyScalar(sc).applyQuaternion(q));

        hpRoot.position.copy(p);
        hpRoot.quaternion.copy(q);
        hpRoot.scale.setScalar(sc);
        hpRoot.updateMatrixWorld(true);
      }
    } else {
      window.XMFIT.setPose(s.pose);
      const glow = s.glow || 0;
      ringMat.opacity = glow;
      ring.scale.setScalar(1 + 0.25 * (s.ringGrow || 0));
      if (pwrCap) pwrCap.position.z = pwrCapZ0 - 0.0025 * Math.min(1, glow * 1.4);

      const led = s.led || 0;
      ledMat.opacity = led;
      const lm = M.headphoneModel.materials.statusLedMaterial;
      lm.emissive.setRGB(0.08 * led, 0.42 * led, 1.0 * led);
      lm.emissiveIntensity = led > 0 ? 2.8 * led : ledPrevIntensity;
    }
    const r = d.renderer;
    r.setPixelRatio(1); r.setSize(size, size, false);
    r.setClearColor(0x000000, 0);
    r.render(d.scene, window.XMFIT.camera);
    return d.canvas.toDataURL('image/png');
  }

  function project(pose, name, size = 960) {
    window.XMFIT.setPose(pose);
    d.scene.updateMatrixWorld(true);
    const obj = leftCup().getObjectByName(name);
    return window.XMFIT.projectWorld(new T.Vector3().setFromMatrixPosition(obj.matrixWorld), size);
  }

  function done() {
    if (pwrCap) { pwrCap.position.z = pwrCapZ0; pwrCap = null; }
    if (ring) { ring.parent.remove(ring); ring = null; }
    if (ledGlow) { ledGlow.parent.remove(ledGlow); ledGlow = null; }
    if (d && d.M && d.M.headphoneModel && ledPrevEmissive) {
      const lm = d.M.headphoneModel.materials.statusLedMaterial;
      lm.emissive.copy(ledPrevEmissive);
      lm.emissiveIntensity = ledPrevIntensity;
      ledPrevEmissive = null;
    }
    window.XMFIT.done();
  }
  return { init, frame, project, facing, done };
})();
