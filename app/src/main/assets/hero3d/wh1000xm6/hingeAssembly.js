import * as THREE from "three";
import { HEADPHONE_DIMENSIONS, clampUnitRange } from "./dimensions.js";
import { buildLoftedGeometry, evalSuperellipse } from "./geometryUtils.js";
import { createEarcupAssembly } from "./earcup.js";

function createUpperStemGeometry(sideSign) {
  const heightSegments = 16;
  const radialSegments = 28;
  const baseRadiusX = HEADPHONE_DIMENSIONS.sliderRadius * HEADPHONE_DIMENSIONS.sliderScaleX;
  const baseRadiusZ = HEADPHONE_DIMENSIONS.sliderRadius * HEADPHONE_DIMENSIONS.sliderScaleZ;
  const cutSlope = Math.tan(HEADPHONE_DIMENSIONS.hingeCutTiltRadians);

  return buildLoftedGeometry({
    ringCount: heightSegments,
    segmentCount: radialSegments,
    fillVertex(_r, _s, uCoord, vCoord, positions, uvs) {
      const thetaRadians = uCoord * Math.PI * 2;
      const pt = evalSuperellipse(Math.cos(thetaRadians), Math.sin(thetaRadians), baseRadiusX, baseRadiusZ, 2 / 2.6);
      // Slanted diagonal scarf cut at bottom of upper stem.
      const cutY = -0.072 - sideSign * pt.x * cutSlope * 0.85;
      const localY = THREE.MathUtils.lerp(0.012, cutY, vCoord);
      positions.push(pt.x, localY, pt.y);
      uvs.push(uCoord, vCoord);
    },
  });
}

function createScarfCollarGeometry(sideSign) {
  const rX = HEADPHONE_DIMENSIONS.sliderRadius * HEADPHONE_DIMENSIONS.sliderScaleX * 1.04;
  const rZ = HEADPHONE_DIMENSIONS.sliderRadius * HEADPHONE_DIMENSIONS.sliderScaleZ * 1.04;
  const cutSlope = Math.tan(HEADPHONE_DIMENSIONS.hingeCutTiltRadians);

  return buildLoftedGeometry({
    ringCount: 4,
    segmentCount: 32,
    fillVertex(_r, _s, uCoord, vCoord, positions, uvs) {
      const theta = uCoord * Math.PI * 2;
      const pt = evalSuperellipse(Math.cos(theta), Math.sin(theta), rX, rZ, 2 / 2.6);
      const bandOffset = (vCoord - 0.5) * 0.014;
      const localY = -sideSign * pt.x * cutSlope * 0.85 + bandOffset;
      positions.push(pt.x, localY, pt.y);
      uvs.push(uCoord, vCoord);
    },
  });
}

function createLowerHingeStemGeometry(sideSign) {
  const tongueLength = HEADPHONE_DIMENSIONS.yokeTongueLength;
  const widthZ = HEADPHONE_DIMENSIONS.yokeTongueWidthZ;
  const thicknessX = HEADPHONE_DIMENSIONS.yokeTongueThicknessX;
  const rX = HEADPHONE_DIMENSIONS.sliderRadius * HEADPHONE_DIMENSIONS.sliderScaleX;
  const rZ = HEADPHONE_DIMENSIONS.sliderRadius * HEADPHONE_DIMENSIONS.sliderScaleZ;
  const cutSlope = Math.tan(HEADPHONE_DIMENSIONS.hingeCutTiltRadians);

  return buildLoftedGeometry({
    ringCount: 24,
    segmentCount: 32,
    fillVertex(_hIdx, _aIdx, uCoord, vCoord, positions, uvs) {
      const theta = uCoord * Math.PI * 2;
      const capsuleWeight = Math.min(1, vCoord / 0.30);
      const smoothCapsule = capsuleWeight * capsuleWeight * (3 - 2 * capsuleWeight);
      const tipRoundFactor =
        vCoord > 0.88 ? Math.sqrt(Math.max(0, 1 - Math.pow((vCoord - 0.88) / 0.12, 2))) : 1;

      const halfX = THREE.MathUtils.lerp(rX, thicknessX / 2, smoothCapsule) * tipRoundFactor;
      const halfZ = THREE.MathUtils.lerp(rZ, widthZ / 2, smoothCapsule) * tipRoundFactor;
      const pt = evalSuperellipse(Math.cos(theta), Math.sin(theta), halfX, halfZ, 2 / 2.6);
      const topScarfY = 0.010 - sideSign * pt.x * cutSlope * 0.85;
      const bottomTipY = -tongueLength;
      const localY = THREE.MathUtils.lerp(topScarfY, bottomTipY, vCoord);
      positions.push(pt.x, localY, pt.y);
      uvs.push(uCoord, vCoord);
    },
  });
}

function createVerticalSonyWordmark(sideSign, materials) {
  const logoGeometry = new THREE.PlaneGeometry(0.044, 0.126);
  logoGeometry.rotateY(sideSign * (Math.PI / 2));
  const logoMaterial =
    sideSign > 0 ? materials.leftWordmarkMaterial : materials.rightWordmarkMaterial;
  const logoMesh = new THREE.Mesh(logoGeometry, logoMaterial);

  logoMesh.position.set(
    sideSign * (HEADPHONE_DIMENSIONS.yokeTongueThicknessX / 2 + 0.0024),
    -HEADPHONE_DIMENSIONS.yokeTongueLength * 0.31,
    0,
  );
  logoMesh.name = "sonyWordmark";
  return logoMesh;
}

export function createSideAssembly(sideSign, tipAnchor, materials) {
  const sideLabel = sideSign > 0 ? "left" : "right";
  const sideAssembly = new THREE.Group();
  sideAssembly.name = `${sideLabel}SideAssembly`;
  const defaultDownwardAxis = new THREE.Vector3(0, -1, 0);

  function updateTipAnchor(nextTipAnchor) {
    sideAssembly.position.copy(nextTipAnchor.position);
    sideAssembly.quaternion.setFromUnitVectors(defaultDownwardAxis, nextTipAnchor.downwardTangent);
  }

  updateTipAnchor(tipAnchor);

  const sliderTrackGroup = new THREE.Group();
  sliderTrackGroup.name = `${sideLabel}SliderTrack`;
  sideAssembly.add(sliderTrackGroup);

  const sliderArmGeometry = new THREE.CylinderGeometry(
    HEADPHONE_DIMENSIONS.sliderRadius,
    HEADPHONE_DIMENSIONS.sliderRadius,
    1,
    32,
  );
  sliderArmGeometry.scale(HEADPHONE_DIMENSIONS.sliderScaleX, 1, HEADPHONE_DIMENSIONS.sliderScaleZ);
  const sliderArm = new THREE.Mesh(sliderArmGeometry, materials.sliderStemMaterial);
  sliderArm.name = "sliderArm";
  sideAssembly.add(sliderArm);

  const upperStemMesh = new THREE.Mesh(createUpperStemGeometry(sideSign), materials.sliderStemMaterial);
  upperStemMesh.name = `${sideLabel}UpperStem`;
  sliderTrackGroup.add(upperStemMesh);

  const foldPivot = new THREE.Group();
  foldPivot.name = "foldPivot";
  foldPivot.position.y = -HEADPHONE_DIMENSIONS.sliderBaseExposedLength;

  const swivelPivot = new THREE.Group();
  swivelPivot.name = "swivelPivot";

  const tiltPivot = new THREE.Group();
  tiltPivot.name = "tiltPivot";

  // Exposed diagonal metallic scarf collar sits right inside the folding break.
  const scarfCollarMesh = new THREE.Mesh(createScarfCollarGeometry(sideSign), materials.scarfCollarMaterial);
  scarfCollarMesh.name = "scarfHingeCollar";

  const swivelKnuckleGeometry = new THREE.SphereGeometry(HEADPHONE_DIMENSIONS.sliderRadius, 24, 16);
  swivelKnuckleGeometry.scale(HEADPHONE_DIMENSIONS.sliderScaleX, 0.9, HEADPHONE_DIMENSIONS.sliderScaleZ);
  const swivelKnuckleMesh = new THREE.Mesh(swivelKnuckleGeometry, materials.scarfCollarMaterial);
  swivelKnuckleMesh.name = "swivelKnuckle";

  const yokeTongue = new THREE.Mesh(createLowerHingeStemGeometry(sideSign), materials.sliderStemMaterial);
  yokeTongue.name = "yokeTongue";

  const sonyWordmarkMesh = createVerticalSonyWordmark(sideSign, materials);

  const { earcupGroup, touchFaceMesh, earcupShell, earpadCushion, acousticScrim } = createEarcupAssembly(
    sideSign,
    materials,
  );
  const sweepPivot = new THREE.Group();
  sweepPivot.name = "sweepPivot";
  sweepPivot.position.set(-sideSign * 0.165, -0.158, 0);
  earcupGroup.position.set(0, -0.356, 0);
  sweepPivot.add(earcupGroup);

  tiltPivot.add(scarfCollarMesh, swivelKnuckleMesh, yokeTongue, sonyWordmarkMesh, sweepPivot);
  swivelPivot.add(tiltPivot);
  foldPivot.add(swivelPivot);
  sliderTrackGroup.add(foldPivot);

  let currentExtension = null;

  function setExtension(extensionRatio) {
    const clampedExtension = clampUnitRange(extensionRatio);
    if (clampedExtension === currentExtension) return;
    currentExtension = clampedExtension;
    const travelOffset = clampedExtension * HEADPHONE_DIMENSIONS.sliderMaxExtension;
    const topAnchorY = HEADPHONE_DIMENSIONS.sliderTopAnchorY;
    const bottomSwivelY = -(HEADPHONE_DIMENSIONS.sliderBaseExposedLength + travelOffset);

    sliderArm.scale.set(1, topAnchorY - bottomSwivelY, 1);
    sliderArm.position.y = (topAnchorY + bottomSwivelY) / 2;
    sliderTrackGroup.position.y = -travelOffset;
  }

  let currentSwivel = 0;
  let currentFold = 0;

  function applyArticulation() {
    const neutralFactor = 1 - Math.max(currentSwivel, currentFold);
    // Fold inward inside the headband arch first, then swivel cups flat for compact case storage.
    foldPivot.rotation.z =
      -sideSign * currentFold * HEADPHONE_DIMENSIONS.earcupFoldInwardAngleRadians;
    foldPivot.rotation.x = sideSign * 0.42 * currentFold;
    swivelPivot.rotation.y =
      -sideSign * currentSwivel * HEADPHONE_DIMENSIONS.earcupFlatFoldAngleRadians;
    tiltPivot.rotation.z =
      -sideSign * HEADPHONE_DIMENSIONS.earcupInwardTiltRadians * neutralFactor;
    tiltPivot.rotation.x = -0.14 * currentSwivel * (1 - currentFold * 0.85);
    sweepPivot.rotation.x =
      HEADPHONE_DIMENSIONS.earcupForwardSweepRadians * neutralFactor;
    sweepPivot.rotation.z =
      -sideSign * HEADPHONE_DIMENSIONS.earcupSweepInwardTiltRadians * neutralFactor;
  }

  function setSwivel(swivelRatio) {
    const nextSwivel = clampUnitRange(swivelRatio);
    if (nextSwivel === currentSwivel) return;
    currentSwivel = nextSwivel;
    applyArticulation();
  }

  function setFold(foldRatio) {
    const nextFold = clampUnitRange(foldRatio);
    if (nextFold === currentFold) return;
    currentFold = nextFold;
    applyArticulation();
  }

  setExtension(0);
  applyArticulation();

  return {
    sideAssembly,
    sliderArm,
    swivelPivot,
    foldPivot,
    tiltPivot,
    sweepPivot,
    touchFaceMesh,
    earcupShell,
    earpadCushion,
    acousticScrim,
    setExtension,
    setSwivel,
    setFold,
    updateTipAnchor,
  };
}
