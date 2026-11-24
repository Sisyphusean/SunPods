import { clampUnitRange } from './wh1000xm5/dimensions.js';
import { CASE_COLORWAY_PRESETS } from './wh1000xm5/carryCase.js';
import { COLORWAY_PRESETS, resolveColorwayKey } from './wh1000xm5/materials.js';
import { createCarryCaseModel } from './wh1000xm5/carryCase.js';
import { createWh1000Xm5Model } from './wh1000xm5/model.js';

function buildFinishes(THREE) {
  const tmp = new THREE.Color();
  const toLin = (hexOrCss) => {
    tmp.set(hexOrCss);
    return [tmp.r, tmp.g, tmp.b];
  };
  const out = {};
  for (const key of ['black', 'silver', 'blue', 'pink']) {
    const p = COLORWAY_PRESETS[key];
    const cp = CASE_COLORWAY_PRESETS[key];
    out[key] = {
      shell: toLin(p.shellHex),
      seam: toLin(p.seamHex),
      leather: toLin(p.leatherHex),
      leatherSheen: toLin(p.leatherSheenHex),
      slider: toLin(p.sliderHex),
      metalTrim: toLin(p.metalTrimHex),
      button: toLin(p.buttonHex),
      ancBronze: toLin(p.ancBronzeHex),
      scrimTint: toLin(p.scrimTintHex),
      brandingAccent: toLin(p.brandingAccentHex),
      brandingEmissive: toLin(p.brandingEmissiveHex),
      brandingEmissiveIntensity: [p.brandingEmissiveIntensity, p.brandingEmissiveIntensity, p.brandingEmissiveIntensity],
      rimLabel: toLin(p.rimLabelHex),
      zipperTape: toLin(cp.zipperTapeHex),
      zipperTeeth: toLin(cp.zipperTeethHex),
      sliderMetal: toLin(cp.sliderMetalHex),
      pullTab: toLin(cp.pullTabHex),
    };
  }
  return out;
}

const HERO0 = {
  rot: [-42, 11, 0],     // [yaw, pitch, roll] deg: 3/4 Sony studio angle with balanced headroom
  scale: 2.02,
  pos: [0.0, -0.06, 0.0],
  ext: 0.04,
};
const CASEREST0 = {
  yaw: -32,
  tilt: 46,
  roll: 0,
  scale: 1.72,
  pos: [0.0, -0.05, 0.0],
};
const CASEOPEN0 = {
  yaw: -22,
  tilt: 26,
  roll: 0,
  scale: 1.12,
  pos: [0.0, -1.48, 0.0],
};

export function createWH1000XM5(ctx) {
  const { THREE, smooth, merge, makeFinishCopy, patched } = ctx;
  const ov = ctx.DEBUG && ctx.overrides ? ctx.overrides : null;
  const HERO = ov ? merge(HERO0, ov.HERO) : HERO0;
  const CASEREST = ov ? merge(CASEREST0, ov.CASEREST) : CASEREST0;
  const CASEOPEN = ov ? merge(CASEOPEN0, ov.CASEOPEN) : CASEOPEN0;

  const rad = (d) => d * Math.PI / 180;
  const lerp = (a, b, t) => a + (b - a) * t;

  const FINISHES = buildFinishes(THREE);
  const KEYS = Object.keys(FINISHES.black);
  const ready = Promise.resolve();

  const decorateStudioMaterial = (mat) => patched(mat, mat.isMeshBasicMaterial ? 'wh5-basic' : 'wh5-pbr', {}, {}, {
    vertex: false,
    rim: false,
    aces: true,
    basic: !!mat.isMeshBasicMaterial,
  });

  const headphoneModel = createWh1000Xm5Model('black', { decorateMaterial: decorateStudioMaterial });
  const carryCaseModel = createCarryCaseModel('black', { decorateMaterial: decorateStudioMaterial });

  // Measure canonical local centers for neutral hero pose and flat-folded stowed pose.
  headphoneModel.setSliderExtension(HERO.ext);
  headphoneModel.setEarcupSwivel(0);
  headphoneModel.rootGroup.updateMatrixWorld(true);
  const centerNeutral = new THREE.Box3().setFromObject(headphoneModel.rootGroup).getCenter(new THREE.Vector3());

  headphoneModel.setSliderExtension(0);
  headphoneModel.setEarcupSwivel(1);
  headphoneModel.rootGroup.updateMatrixWorld(true);
  const centerFolded = new THREE.Box3().setFromObject(headphoneModel.rootGroup).getCenter(new THREE.Vector3());

  headphoneModel.setSliderExtension(HERO.ext);
  headphoneModel.setEarcupSwivel(0);

  const budsRoot = new THREE.Group();
  budsRoot.name = 'wh1000xm5_phones';
  const hpHolder = new THREE.Group();
  hpHolder.name = 'wh1000xm5_hpHolder';
  hpHolder.add(headphoneModel.rootGroup);
  budsRoot.add(hpHolder);

  const kaseRoot = new THREE.Group();
  kaseRoot.name = 'wh1000xm5_case';
  const kaseSpin = new THREE.Group();
  kaseSpin.name = 'wh1000xm5_caseSpin';
  const kaseTilt = new THREE.Group();
  kaseTilt.name = 'wh1000xm5_caseTilt';
  kaseTilt.add(carryCaseModel.rootGroup);
  kaseSpin.add(kaseTilt);
  kaseRoot.add(kaseSpin);

  const seatedPose = carryCaseModel.getSeatedHeadphonePose();
  const qHeroBase = new THREE.Quaternion().setFromEuler(
    new THREE.Euler(rad(HERO.rot[1]), rad(HERO.rot[0]), rad(HERO.rot[2]), 'ZXY'),
  );
  const qCaseRest = new THREE.Quaternion().setFromEuler(
    new THREE.Euler(rad(CASEREST.tilt), rad(CASEREST.yaw), rad(CASEREST.roll), 'YXZ'),
  );
  const qCaseOpen = new THREE.Quaternion().setFromEuler(
    new THREE.Euler(rad(CASEOPEN.tilt), rad(CASEOPEN.yaw), rad(CASEOPEN.roll), 'YXZ'),
  );

  const vAxisY = new THREE.Vector3(0, 1, 0);
  const vHeroCenter = new THREE.Vector3(HERO.pos[0], HERO.pos[1], HERO.pos[2]);
  const vOffset = new THREE.Vector3();
  const vCurCenter = new THREE.Vector3();
  const vCenterSeated = new THREE.Vector3();
  const vCenterCur = new THREE.Vector3();
  const vPosSeated = new THREE.Vector3();
  const vScaleSeated = new THREE.Vector3();
  const qSpinY = new THREE.Quaternion();
  const qCur = new THREE.Quaternion();
  const qHeroWorld = new THREE.Quaternion();
  const qSeatedWorld = new THREE.Quaternion();
  const qSeatedLocal = new THREE.Quaternion().setFromEuler(seatedPose.rotation);
  const mSeatedLocal = new THREE.Matrix4().compose(seatedPose.position, qSeatedLocal, new THREE.Vector3(1, 1, 1));
  const mCaseSpin = new THREE.Matrix4();
  const mCaseTilt = new THREE.Matrix4();
  const mSeatedWorld = new THREE.Matrix4();

  let toolOverride = false;

  function setCaseStageTransform(posVec, tiltQuat, scale, spinRad) {
    kaseSpin.position.copy(posVec);
    kaseSpin.rotation.y = spinRad;
    kaseTilt.quaternion.copy(tiltQuat);
    kaseTilt.scale.setScalar(scale);
  }

  function heroWorldQuat(spinRad) {
    qSpinY.setFromAxisAngle(vAxisY, spinRad);
    return qHeroWorld.multiplyQuaternions(qSpinY, qHeroBase);
  }

  function computeSeatedTransform(casePosX, casePosY, casePosZ, tiltQuat, scale, spinRad) {
    mCaseSpin.makeRotationY(spinRad);
    mCaseTilt.compose(vOffset.set(0, 0, 0), tiltQuat, vScaleSeated.set(scale, scale, scale));
    mSeatedWorld.makeTranslation(casePosX, casePosY, casePosZ)
      .multiply(mCaseSpin)
      .multiply(mCaseTilt)
      .multiply(mSeatedLocal);
    mSeatedWorld.decompose(vPosSeated, qSeatedWorld, vScaleSeated);
  }

  // Shared unpack articulation kinematics (reused by render tool clip_page.js).
  function applyUnpackKinematics(tLift, curCenterOut) {
    const unfoldT = smooth(clampUnitRange((tLift - 0.05) / 0.65));
    const extT = smooth(clampUnitRange((tLift - 0.35) / 0.65));
    headphoneModel.setSliderExtension(lerp(0, HERO.ext, extT));
    headphoneModel.setEarcupSwivel(1 - unfoldT);
    curCenterOut.lerpVectors(centerFolded, centerNeutral, unfoldT);
    return unfoldT;
  }

  function applyPose(st, active) {
    if (toolOverride) return;

    budsRoot.visible = false;
    kaseRoot.visible = false;
    if (!active) return;

    const a = rad(st.angle);
    const hasProgress = st.caseProgress !== undefined && st.caseProgress !== null;
    const p = hasProgress ? clampUnitRange(st.caseProgress) : (st.model === 'case' ? 1 : 0);
    const legacyCaseWithLid = !hasProgress && st.model === 'case';

    if (legacyCaseWithLid) {
      const lid = clampUnitRange(st.lid);
      setCaseStageTransform(
        vCurCenter.set(CASEREST.pos[0], CASEREST.pos[1], CASEREST.pos[2]),
        qCaseRest,
        CASEREST.scale,
        a,
      );
      carryCaseModel.setOpenAmount(lid);
      carryCaseModel.setOpacity(1);
      kaseRoot.visible = true;

      if (lid > 0.015) {
        headphoneModel.setSliderExtension(0);
        headphoneModel.setEarcupSwivel(1);
        computeSeatedTransform(CASEREST.pos[0], CASEREST.pos[1], CASEREST.pos[2], qCaseRest, CASEREST.scale, a);
        hpHolder.position.copy(vPosSeated);
        hpHolder.quaternion.copy(qSeatedWorld);
        hpHolder.scale.copy(vScaleSeated);
        budsRoot.visible = true;
      }
      return;
    }

    // Pure upright hero headphones (caseProgress === 0)
    if (p <= 0.0005) {
      kaseRoot.visible = false;
      carryCaseModel.setOpenAmount(0);
      carryCaseModel.setOpacity(1);

      headphoneModel.setSliderExtension(HERO.ext);
      headphoneModel.setEarcupSwivel(0);

      heroWorldQuat(a);
      vOffset.copy(centerNeutral).multiplyScalar(HERO.scale).applyQuaternion(qHeroWorld);
      hpHolder.position.copy(vHeroCenter).sub(vOffset);
      hpHolder.quaternion.copy(qHeroWorld);
      hpHolder.scale.setScalar(HERO.scale);
      budsRoot.visible = true;
      return;
    }

    // Continuous 3D coming-out-of-the-case choreography (p in (0, 1])
    const tOpen = smooth(clampUnitRange((1 - p) / 0.28));
    const tExit = smooth(clampUnitRange((0.30 - p) / 0.28));
    const tLift = smooth(clampUnitRange((0.74 - p) / 0.58));

    const caseX = lerp(CASEREST.pos[0], CASEOPEN.pos[0], tOpen);
    const caseY = lerp(CASEREST.pos[1], CASEOPEN.pos[1], tOpen) - 1.35 * tExit;
    const caseZ = lerp(CASEREST.pos[2], CASEOPEN.pos[2], tOpen) - 1.15 * tExit;
    const caseScale = lerp(CASEREST.scale, CASEOPEN.scale, tOpen) * (1 - 0.12 * tExit);
    const qCase = qCur.slerpQuaternions(qCaseRest, qCaseOpen, tOpen);

    setCaseStageTransform(vCurCenter.set(caseX, caseY, caseZ), qCase, caseScale, a);
    carryCaseModel.setOpenAmount(tOpen);
    carryCaseModel.setOpacity(1 - tExit);
    kaseRoot.visible = tExit < 0.995;

    if (tOpen <= 0.015 && p > 0.98) {
      return;
    }

    computeSeatedTransform(caseX, caseY, caseZ, qCase, caseScale, a);
    vOffset.copy(centerFolded).multiplyScalar(vScaleSeated.x).applyQuaternion(qSeatedWorld);
    vCenterSeated.copy(vPosSeated).add(vOffset);

    heroWorldQuat(a);
    applyUnpackKinematics(tLift, vCenterCur);

    // Gentle forward + upward arc keeps crown well below top canvas edge while clearing tray cleanly.
    const arcSin = Math.sin(Math.PI * tLift);
    vCurCenter.lerpVectors(vCenterSeated, vHeroCenter, tLift);
    vCurCenter.y += 0.65 * arcSin;
    vCurCenter.z += 0.95 * arcSin;

    qCur.slerpQuaternions(qSeatedWorld, qHeroWorld, tLift);
    const curScale = lerp(vScaleSeated.x, HERO.scale, tLift);
    vOffset.copy(vCenterCur).multiplyScalar(curScale).applyQuaternion(qCur);

    hpHolder.position.copy(vCurCenter).sub(vOffset);
    hpHolder.quaternion.copy(qCur);
    hpHolder.scale.setScalar(curScale);
    budsRoot.visible = true;
  }

  function setColorTarget(name) {
    const key = resolveColorwayKey(name);
    if (key in CASE_COLORWAY_PRESETS) {
      carryCaseModel.setColorTarget(key);
    }
  }

  function applyFinish(f) {
    headphoneModel.applyLinearColors(f);
    carryCaseModel.applyLinearColors(f);
  }

  const finishCopy = makeFinishCopy(FINISHES, KEYS);

  return {
    buds: { root: budsRoot },
    kase: { root: kaseRoot },
    headphoneModel,
    carryCaseModel,
    FINISHES,
    KEYS,
    HERO,
    centerNeutral,
    centerFolded,
    applyUnpackKinematics,
    handlesCaseProgress: true,
    precompileClosedBoth: true,
    finishCopy,
    applyFinish,
    setColorTarget,
    applyPose,
    ready,
    setToolOverride: (on) => { toolOverride = !!on; },
    dispose: () => {
      headphoneModel.dispose();
      carryCaseModel.dispose();
    },
  };
}
