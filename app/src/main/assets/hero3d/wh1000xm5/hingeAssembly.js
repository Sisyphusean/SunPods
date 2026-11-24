import * as THREE from "three";
import { HEADPHONE_DIMENSIONS, clampUnitRange } from "./dimensions.js";
import { buildLoftedGeometry, evalSuperellipse } from "./geometryUtils.js";
import { createEarcupAssembly } from "./earcup.js";

function createYokeTongueGeometry() {
  const tongueLength = HEADPHONE_DIMENSIONS.yokeTongueLength;
  const widthZ = HEADPHONE_DIMENSIONS.yokeTongueWidthZ;
  const thicknessX = HEADPHONE_DIMENSIONS.yokeTongueThicknessX;

  const heightSegments = 24;
  const radialSegments = 32;

  return buildLoftedGeometry({
    ringCount: heightSegments,
    segmentCount: radialSegments,
    fillVertex(heightIndex, angleIndex, uCoord, verticalProgress, positions, uvs) {
      const localY = THREE.MathUtils.lerp(0.012, -tongueLength, verticalProgress);
      // Smoothly transition from cylindrical swivel joint at top to sculpted capsule tongue below.
      const capsuleWeight = Math.min(1, verticalProgress / 0.32);
      const smoothCapsule = capsuleWeight * capsuleWeight * (3 - 2 * capsuleWeight);
      const tipRoundFactor =
        verticalProgress > 0.88
          ? Math.sqrt(Math.max(0, 1 - Math.pow((verticalProgress - 0.88) / 0.12, 2)))
          : 1;

      const halfThicknessX =
        THREE.MathUtils.lerp(HEADPHONE_DIMENSIONS.sliderRadius, thicknessX / 2, smoothCapsule) *
        tipRoundFactor;
      const halfWidthZ =
        THREE.MathUtils.lerp(HEADPHONE_DIMENSIONS.sliderRadius, widthZ / 2, smoothCapsule) *
        tipRoundFactor;

      const thetaRadians = uCoord * Math.PI * 2;
      const pt = evalSuperellipse(
        Math.cos(thetaRadians),
        Math.sin(thetaRadians),
        halfThicknessX,
        halfWidthZ,
        2 / 2.6,
      );
      positions.push(pt.x, localY, pt.y);
      uvs.push(uCoord, verticalProgress);
    },
  });
}

function createVerticalSonyWordmark(sideSign, materials) {
  const logoGeometry = new THREE.PlaneGeometry(0.052, 0.145);
  logoGeometry.rotateY(sideSign * (Math.PI / 2));
  const logoMaterial =
    sideSign > 0 ? materials.leftWordmarkMaterial : materials.rightWordmarkMaterial;
  const logoMesh = new THREE.Mesh(logoGeometry, logoMaterial);

  logoMesh.position.set(
    sideSign * (HEADPHONE_DIMENSIONS.yokeTongueThicknessX / 2 + 0.0016),
    -HEADPHONE_DIMENSIONS.yokeTongueLength * 0.42,
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
    sideAssembly.quaternion.setFromUnitVectors(
      defaultDownwardAxis,
      nextTipAnchor.downwardTangent,
    );
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
  const sliderArm = new THREE.Mesh(sliderArmGeometry, materials.sliderStemMaterial);
  sliderArm.name = "sliderArm";
  sideAssembly.add(sliderArm);

  const seamRingGeometry = new THREE.CylinderGeometry(
    HEADPHONE_DIMENSIONS.sliderRadius * 0.96,
    HEADPHONE_DIMENSIONS.sliderRadius * 0.96,
    HEADPHONE_DIMENSIONS.sliderSeamThickness,
    32,
  );
  const seamRingMesh = new THREE.Mesh(seamRingGeometry, materials.seamMaterial);
  seamRingMesh.position.y = -HEADPHONE_DIMENSIONS.sliderBaseExposedLength;
  seamRingMesh.name = `${sideLabel}SwivelSeam`;

  const swivelPivot = new THREE.Group();
  swivelPivot.name = "swivelPivot";
  swivelPivot.position.y = -HEADPHONE_DIMENSIONS.sliderBaseExposedLength;

  const tiltPivot = new THREE.Group();
  tiltPivot.name = "tiltPivot";

  const swivelKnuckleGeometry = new THREE.SphereGeometry(
    HEADPHONE_DIMENSIONS.sliderRadius,
    24,
    16,
  );
  const swivelKnuckleMesh = new THREE.Mesh(
    swivelKnuckleGeometry,
    materials.sliderStemMaterial,
  );
  swivelKnuckleMesh.name = "swivelKnuckle";

  const yokeTongueGeometry = createYokeTongueGeometry();
  const yokeTongue = new THREE.Mesh(yokeTongueGeometry, materials.sliderStemMaterial);
  yokeTongue.name = "yokeTongue";

  const sonyWordmarkMesh = createVerticalSonyWordmark(sideSign, materials);

  const { earcupGroup, earcupShell, earpadCushion, acousticScrim } = createEarcupAssembly(
    sideSign,
    materials,
  );
  // Nested snugly inside upper earcup shoulder so yoke tongue flows tangentially into shell with no zig-zag step.
  const sweepPivot = new THREE.Group();
  sweepPivot.name = "sweepPivot";
  sweepPivot.position.set(-sideSign * 0.172, -0.175, 0);
  earcupGroup.position.set(0, -0.325, 0);
  sweepPivot.add(earcupGroup);

  tiltPivot.add(swivelKnuckleMesh, yokeTongue, sonyWordmarkMesh, sweepPivot);
  swivelPivot.add(tiltPivot);
  sliderTrackGroup.add(seamRingMesh, swivelPivot);

  function setExtension(extensionRatio) {
    const clampedExtension = clampUnitRange(extensionRatio);
    const travelOffset = clampedExtension * HEADPHONE_DIMENSIONS.sliderMaxExtension;
    const topAnchorY = HEADPHONE_DIMENSIONS.sliderTopAnchorY;
    const bottomSwivelY = -(HEADPHONE_DIMENSIONS.sliderBaseExposedLength + travelOffset);

    sliderArm.scale.set(1, topAnchorY - bottomSwivelY, 1);
    sliderArm.position.y = (topAnchorY + bottomSwivelY) / 2;
    sliderTrackGroup.position.y = -travelOffset;
  }

  function setSwivel(swivelRatio) {
    const clampedSwivel = clampUnitRange(swivelRatio);
    const neutralFactor = 1 - clampedSwivel;
    // Swivel earpads rearward (-Z) for flat-fold; distribute gentle inward cant smoothly across yoke & U-pocket.
    swivelPivot.rotation.y =
      -sideSign * clampedSwivel * HEADPHONE_DIMENSIONS.earcupFlatFoldAngleRadians;
    tiltPivot.rotation.z =
      -sideSign * HEADPHONE_DIMENSIONS.earcupInwardTiltRadians * neutralFactor;
    tiltPivot.rotation.x = -0.45 * clampedSwivel;
    sweepPivot.rotation.x =
      HEADPHONE_DIMENSIONS.earcupForwardSweepRadians * neutralFactor;
    sweepPivot.rotation.z =
      -sideSign * HEADPHONE_DIMENSIONS.earcupSweepInwardTiltRadians * neutralFactor;
  }

  setExtension(0);
  setSwivel(0);

  return {
    sideAssembly,
    sliderArm,
    swivelPivot,
    tiltPivot,
    sweepPivot,
    earcupShell,
    earpadCushion,
    acousticScrim,
    setExtension,
    setSwivel,
    updateTipAnchor,
  };
}
