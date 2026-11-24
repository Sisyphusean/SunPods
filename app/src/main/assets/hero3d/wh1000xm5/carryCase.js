import * as THREE from "three";
import { clampUnitRange } from "./dimensions.js";
import {
  buildLoftedGeometry,
  disposeObjectGeometries,
  evalSuperellipse,
} from "./geometryUtils.js";
import { resolveColorwayKey } from "./materials.js";
import {
  createFeltColorTexture,
  createFeltScalarTexture,
  createWovenFabricColorTexture,
  createWovenFabricScalarTextures,
} from "./textures.js";

export const CASE_COLORWAY_PRESETS = {
  black: {
    fabricBaseHex: "#222429",
    fabricThreadHex: "#31343c",
    feltBaseHex: "#191b1f",
    feltFiberHex: "#25282e",
    zipperTapeHex: 0x18191d,
    zipperTeethHex: 0x2b2d33,
    sliderMetalHex: 0xb89b6a,
    pullTabHex: 0x1e2024,
  },
  silver: {
    fabricBaseHex: "#ccc5b8",
    fabricThreadHex: "#dcd5c8",
    feltBaseHex: "#b7b0a3",
    feltFiberHex: "#c7c0b3",
    zipperTapeHex: 0xb5aea1,
    zipperTeethHex: 0x9f988b,
    sliderMetalHex: 0xc2b8a3,
    pullTabHex: 0xc2bbb0,
  },
  blue: {
    fabricBaseHex: "#1e293b",
    fabricThreadHex: "#2b3b54",
    feltBaseHex: "#161f2e",
    feltFiberHex: "#223047",
    zipperTapeHex: 0x151e2c,
    zipperTeethHex: 0x26354d,
    sliderMetalHex: 0x627696,
    pullTabHex: 0x1b2638,
  },
  pink: {
    fabricBaseHex: "#aa9391",
    fabricThreadHex: "#bda7a5",
    feltBaseHex: "#96807e",
    feltFiberHex: "#a89290",
    zipperTapeHex: 0x917d7b,
    zipperTeethHex: 0x7d6a68,
    sliderMetalHex: 0xc29c76,
    pullTabHex: 0x9e8886,
  },
};

const CASE_DIMENSIONS = {
  rearSpineZ: -1.15,
  frontApexZ: 1.12,
  rearHalfWidthX: 1.24,
  frontHalfWidthX: 0.94,
  seamHeightY: 0.37,
  maxLidOpenRadians: THREE.MathUtils.degToRad(112),
};

function evaluateCasePerimeterPoint(perimeterParam, target = new THREE.Vector2()) {
  const angleRadians = perimeterParam * Math.PI * 2;
  const sinAngle = Math.sin(angleRadians);
  const cosAngle = Math.cos(angleRadians);

  const rearBlend = THREE.MathUtils.clamp((-cosAngle + 1) * 0.5, 0, 1);
  const halfWidthX = THREE.MathUtils.lerp(
    CASE_DIMENSIONS.frontHalfWidthX,
    CASE_DIMENSIONS.rearHalfWidthX,
    Math.pow(rearBlend, 0.72),
  );

  const longitudinalRadius = cosAngle >= 0 ? CASE_DIMENSIONS.frontApexZ : Math.abs(CASE_DIMENSIONS.rearSpineZ);
  const ptX = evalSuperellipse(sinAngle, cosAngle, halfWidthX, longitudinalRadius, 2 / 2.35).x;
  const longitudinalPower = cosAngle >= 0 ? 2 / 2.15 : 2 / 2.65;
  const ptZ =
    Math.sign(cosAngle) * Math.pow(Math.abs(cosAngle), longitudinalPower) * longitudinalRadius;

  return target.set(ptX, ptZ);
}

function sampleCasePerimeterPoints(segmentCount) {
  const perimeterPoints = [];
  for (let pointIndex = 0; pointIndex < segmentCount; pointIndex += 1) {
    perimeterPoints.push(evaluateCasePerimeterPoint(pointIndex / segmentCount));
  }
  return perimeterPoints;
}

function createLoftedShellGeometry(
  perimeterPoints,
  ringProfiles,
  closeBottomCap,
  closeTopCap,
  usePlanarFloorUvs = false,
) {
  const perimeterCount = perimeterPoints.length;
  const ringCount = ringProfiles.length;
  const totalWidthX = CASE_DIMENSIONS.rearHalfWidthX * 2;
  const totalLengthZ = CASE_DIMENSIONS.frontApexZ - CASE_DIMENSIONS.rearSpineZ;

  return buildLoftedGeometry({
    ringCount: ringCount - 1,
    segmentCount: perimeterCount,
    fillVertex(ringIndex, pointIndex, uCoord, vCoord, positions, uvs) {
      const profile = ringProfiles[ringIndex];
      const perimeterPoint = perimeterPoints[pointIndex % perimeterCount];
      const scaledX = perimeterPoint.x * profile.scaleX;
      const scaledZ = perimeterPoint.y * profile.scaleZ;
      positions.push(scaledX, profile.heightY, scaledZ);

      if (usePlanarFloorUvs && profile.heightY <= 0.03) {
        uvs.push(scaledX / totalWidthX + 0.5, (scaledZ - CASE_DIMENSIONS.rearSpineZ) / totalLengthZ);
      } else {
        uvs.push(uCoord, vCoord);
      }
    },
    bottomCapCenter: closeBottomCap ? [0, ringProfiles[0].heightY, 0] : null,
    topCapCenter: closeTopCap ? [0, ringProfiles[ringCount - 1].heightY, 0] : null,
  });
}

function createMoldedInteriorTrayGeometry(perimeterPoints) {
  const trayShape = new THREE.Shape();
  const insetFactor = 0.945;

  perimeterPoints.forEach((perimeterPoint, pointIndex) => {
    const insetX = perimeterPoint.x * insetFactor;
    const insetZ = -perimeterPoint.y * insetFactor;
    if (pointIndex === 0) {
      trayShape.moveTo(insetX, insetZ);
    } else {
      trayShape.lineTo(insetX, insetZ);
    }
  });
  trayShape.closePath();

  const trayGeometry = new THREE.ExtrudeGeometry(trayShape, {
    depth: 0.24,
    bevelEnabled: true,
    bevelThickness: 0.025,
    bevelSize: 0.02,
    bevelSegments: 3,
  });
  trayGeometry.rotateX(-Math.PI / 2);
  trayGeometry.computeVertexNormals();
  return trayGeometry;
}

function createCenterBridgeGeometry() {
  const bridgeShape = new THREE.Shape();
  bridgeShape.moveTo(-0.34, -0.58);
  bridgeShape.lineTo(0.34, -0.58);
  bridgeShape.quadraticCurveTo(0.24, 0.05, 0.16, 0.42);
  bridgeShape.quadraticCurveTo(0, 0.52, -0.16, 0.42);
  bridgeShape.quadraticCurveTo(-0.24, 0.05, -0.34, -0.58);
  bridgeShape.closePath();

  const bridgeGeometry = new THREE.ExtrudeGeometry(bridgeShape, {
    depth: 0.075,
    bevelEnabled: true,
    bevelThickness: 0.025,
    bevelSize: 0.022,
    bevelSegments: 3,
  });
  bridgeGeometry.rotateX(-Math.PI / 2);
  bridgeGeometry.computeVertexNormals();
  return bridgeGeometry;
}

function createRearHingeBridgeGeometry() {
  const hingeShape = new THREE.Shape();
  hingeShape.moveTo(-0.5, -0.06);
  hingeShape.lineTo(0.5, -0.06);
  hingeShape.lineTo(0.46, 0.06);
  hingeShape.lineTo(-0.46, 0.06);
  hingeShape.closePath();

  const hingeGeometry = new THREE.ExtrudeGeometry(hingeShape, {
    depth: 0.022,
    bevelEnabled: true,
    bevelThickness: 0.008,
    bevelSize: 0.008,
    bevelSegments: 2,
  });
  hingeGeometry.rotateX(-Math.PI / 2);
  hingeGeometry.computeVertexNormals();
  return hingeGeometry;
}

function createEarcupWellBossGeometry() {
  const bossShape = new THREE.Shape();
  const halfWidth = 0.14;
  const halfLength = 0.2;
  const cornerRadius = 0.095;

  bossShape.moveTo(-halfWidth + cornerRadius, -halfLength);
  bossShape.lineTo(halfWidth - cornerRadius, -halfLength);
  bossShape.quadraticCurveTo(halfWidth, -halfLength, halfWidth, -halfLength + cornerRadius);
  bossShape.lineTo(halfWidth, halfLength - cornerRadius);
  bossShape.quadraticCurveTo(halfWidth, halfLength, halfWidth - cornerRadius, halfLength);
  bossShape.lineTo(-halfWidth + cornerRadius, halfLength);
  bossShape.quadraticCurveTo(-halfWidth, halfLength, -halfWidth, halfLength - cornerRadius);
  bossShape.lineTo(-halfWidth, -halfLength + cornerRadius);
  bossShape.quadraticCurveTo(-halfWidth, -halfLength, -halfWidth + cornerRadius, -halfLength);

  const bossGeometry = new THREE.ExtrudeGeometry(bossShape, {
    depth: 0.055,
    bevelEnabled: true,
    bevelThickness: 0.018,
    bevelSize: 0.016,
    bevelSegments: 3,
  });
  bossGeometry.rotateX(-Math.PI / 2);
  bossGeometry.computeVertexNormals();
  return bossGeometry;
}

function createZipperTrackGeometry(perimeterPoints, trackHeightY, trackScale) {
  const trackPoints = perimeterPoints.map(
    (perimeterPoint) =>
      new THREE.Vector3(
        perimeterPoint.x * trackScale,
        trackHeightY,
        perimeterPoint.y * trackScale,
      ),
  );
  const trackCurve = new THREE.CatmullRomCurve3(trackPoints, true);
  return new THREE.TubeGeometry(trackCurve, 128, 0.014, 10, true);
}

function createLidPadInsertGeometry() {
  const padShape = new THREE.Shape();
  padShape.moveTo(-0.46, -0.34);
  padShape.lineTo(0.46, -0.34);
  padShape.lineTo(0.32, 0.36);
  padShape.lineTo(-0.32, 0.36);
  padShape.closePath();

  const padGeometry = new THREE.ExtrudeGeometry(padShape, {
    depth: 0.025,
    bevelEnabled: true,
    bevelThickness: 0.012,
    bevelSize: 0.015,
    bevelSegments: 3,
  });
  padGeometry.rotateX(Math.PI / 2);
  padGeometry.computeVertexNormals();
  return padGeometry;
}

function createHangingLoopGeometry() {
  // Flat tapered woven strap loop with rounded nose and central vertical slit matching Sony WH-1000XM5 case.
  const outerShape = new THREE.Shape();
  outerShape.moveTo(0.092, -0.05);
  outerShape.lineTo(0.072, 0.44);
  outerShape.absarc(0, 0.44, 0.072, 0, Math.PI, false);
  outerShape.lineTo(-0.092, -0.05);
  outerShape.closePath();

  const slitHole = new THREE.Path();
  const slitHalfW = 0.024;
  const slitBottomY = 0.045;
  const slitTopY = 0.415;
  slitHole.moveTo(-slitHalfW, slitBottomY);
  slitHole.lineTo(-slitHalfW, slitTopY);
  slitHole.absarc(0, slitTopY, slitHalfW, Math.PI, 0, true);
  slitHole.lineTo(slitHalfW, slitBottomY);
  slitHole.absarc(0, slitBottomY, slitHalfW, 0, -Math.PI, true);
  outerShape.holes.push(slitHole);

  const strapGeometry = new THREE.ExtrudeGeometry(outerShape, {
    depth: 0.016,
    bevelEnabled: true,
    bevelThickness: 0.004,
    bevelSize: 0.003,
    bevelSegments: 2,
    curveSegments: 16,
  });
  // Rotate +Y -> +Z so the strap protrudes forward from the front apex seam, centered vertically.
  strapGeometry.rotateX(Math.PI / 2);
  strapGeometry.translate(0, 0.008, 0);
  strapGeometry.computeVertexNormals();
  return strapGeometry;
}

export function createCarryCaseModel(initialColorway = "black", options = {}) {
  let activeColorway = resolveColorwayKey(initialColorway);
  if (!(activeColorway in CASE_COLORWAY_PRESETS)) {
    throw new RangeError(`Unknown WH-1000XM5 case colorway "${initialColorway}".`);
  }

  const decorate = typeof options.decorateMaterial === "function" ? options.decorateMaterial : (m) => m;
  const initialPreset = CASE_COLORWAY_PRESETS[activeColorway];

  const wovenScalars = createWovenFabricScalarTextures();
  const feltBumpTexture = createFeltScalarTexture();

  const wovenColorCache = new Map();
  const feltColorCache = new Map();
  function getFinishTextures(key) {
    const preset = CASE_COLORWAY_PRESETS[key];
    let wovenColor = wovenColorCache.get(key);
    if (!wovenColor) {
      wovenColor = createWovenFabricColorTexture(preset.fabricBaseHex, preset.fabricThreadHex);
      wovenColorCache.set(key, wovenColor);
    }
    let feltColor = feltColorCache.get(key);
    if (!feltColor) {
      feltColor = createFeltColorTexture(preset.feltBaseHex, preset.feltFiberHex);
      feltColorCache.set(key, feltColor);
    }
    return { wovenColor, feltColor };
  }

  const initialTextures = getFinishTextures(activeColorway);

  const wovenFabricMaterial = decorate(new THREE.MeshPhysicalMaterial({
    map: initialTextures.wovenColor,
    bumpMap: wovenScalars.bumpTexture,
    bumpScale: 0.0045,
    roughnessMap: wovenScalars.roughnessTexture,
    roughness: 0.88,
    metalness: 0.02,
    sheen: 0.18,
    sheenRoughness: 0.86,
    transparent: true,
    opacity: 1,
    depthWrite: true,
  }));

  const interiorFeltMaterial = decorate(new THREE.MeshPhysicalMaterial({
    map: initialTextures.feltColor,
    bumpMap: feltBumpTexture,
    bumpScale: 0.0032,
    roughness: 0.94,
    metalness: 0.0,
    sheen: 0.24,
    sheenRoughness: 0.92,
    transparent: true,
    opacity: 1,
    depthWrite: true,
  }));

  const zipperTapeMaterial = decorate(new THREE.MeshStandardMaterial({
    color: initialPreset.zipperTapeHex,
    roughness: 0.86,
    metalness: 0.04,
    transparent: true,
    opacity: 1,
    depthWrite: true,
  }));

  const zipperTeethMaterial = decorate(new THREE.MeshStandardMaterial({
    color: initialPreset.zipperTeethHex,
    roughness: 0.58,
    metalness: 0.28,
    transparent: true,
    opacity: 1,
    depthWrite: true,
  }));

  const sliderMetalMaterial = decorate(new THREE.MeshStandardMaterial({
    color: initialPreset.sliderMetalHex,
    roughness: 0.36,
    metalness: 0.78,
    transparent: true,
    opacity: 1,
    depthWrite: true,
  }));

  const pullTabMaterial = decorate(new THREE.MeshStandardMaterial({
    color: initialPreset.pullTabHex,
    roughness: 0.85,
    metalness: 0.04,
    transparent: true,
    opacity: 1,
    depthWrite: true,
  }));

  const caseMaterials = [
    wovenFabricMaterial,
    interiorFeltMaterial,
    zipperTapeMaterial,
    zipperTeethMaterial,
    sliderMetalMaterial,
    pullTabMaterial,
  ];

  const rootGroup = new THREE.Group();
  rootGroup.name = "sonyWh1000Xm5CarryCase";

  const perimeterPoints = sampleCasePerimeterPoints(72);

  const lowerOuterProfiles = [
    { scaleX: 0.001, scaleZ: 0.001, heightY: 0.0 },
    { scaleX: 0.28, scaleZ: 0.28, heightY: 0.0 },
    { scaleX: 0.55, scaleZ: 0.55, heightY: 0.0 },
    { scaleX: 0.78, scaleZ: 0.78, heightY: 0.0 },
    { scaleX: 0.86, scaleZ: 0.86, heightY: 0.005 },
    { scaleX: 0.93, scaleZ: 0.93, heightY: 0.028 },
    { scaleX: 0.985, scaleZ: 0.985, heightY: 0.08 },
    { scaleX: 1.0, scaleZ: 1.0, heightY: 0.18 },
    { scaleX: 0.978, scaleZ: 0.978, heightY: 0.25 },
    { scaleX: 1.0, scaleZ: 1.0, heightY: 0.34 },
    { scaleX: 1.0, scaleZ: 1.0, heightY: CASE_DIMENSIONS.seamHeightY },
    { scaleX: 0.982, scaleZ: 0.982, heightY: CASE_DIMENSIONS.seamHeightY + 0.004 },
    { scaleX: 0.958, scaleZ: 0.958, heightY: CASE_DIMENSIONS.seamHeightY - 0.004 },
  ];
  const lowerShellMesh = new THREE.Mesh(
    createLoftedShellGeometry(perimeterPoints, lowerOuterProfiles, false, false, true),
    wovenFabricMaterial,
  );
  lowerShellMesh.name = "carryCaseLowerShell";

  const lowerInnerProfiles = [
    { scaleX: 0.958, scaleZ: 0.958, heightY: CASE_DIMENSIONS.seamHeightY - 0.004 },
    { scaleX: 0.948, scaleZ: 0.948, heightY: 0.24 },
    { scaleX: 0.93, scaleZ: 0.93, heightY: 0.07 },
    { scaleX: 0.85, scaleZ: 0.85, heightY: 0.04 },
  ];
  const lowerInnerWallMesh = new THREE.Mesh(
    createLoftedShellGeometry(perimeterPoints, lowerInnerProfiles, false, false),
    interiorFeltMaterial,
  );
  lowerInnerWallMesh.name = "carryCaseLowerInnerWall";

  const lowerRimTapeMesh = new THREE.Mesh(
    createZipperTrackGeometry(perimeterPoints, CASE_DIMENSIONS.seamHeightY - 0.008, 1.002),
    zipperTapeMaterial,
  );
  lowerRimTapeMesh.name = "carryCaseLowerZipperTape";

  const zipperTeethRailMesh = new THREE.Mesh(
    createZipperTrackGeometry(perimeterPoints, CASE_DIMENSIONS.seamHeightY, 1.007),
    zipperTeethMaterial,
  );
  zipperTeethRailMesh.name = "carryCaseZipperTeeth";

  const interiorTrayGroup = new THREE.Group();
  interiorTrayGroup.name = "carryCaseInteriorTray";

  const baseTrayMesh = new THREE.Mesh(
    createMoldedInteriorTrayGeometry(perimeterPoints),
    interiorFeltMaterial,
  );
  baseTrayMesh.position.y = 0.05;
  baseTrayMesh.scale.set(1, 0.2, 1);
  baseTrayMesh.name = "carryCaseFeltTray";

  const bossGeo = createEarcupWellBossGeometry();
  const leftEarcupBossMesh = new THREE.Mesh(bossGeo, interiorFeltMaterial);
  leftEarcupBossMesh.position.set(-0.62, 0.075, -0.3);
  leftEarcupBossMesh.rotation.y = 0.1;
  leftEarcupBossMesh.name = "carryCaseLeftEarcupWell";

  const rightEarcupBossMesh = new THREE.Mesh(bossGeo, interiorFeltMaterial);
  rightEarcupBossMesh.position.set(0.62, 0.075, -0.3);
  rightEarcupBossMesh.rotation.y = -0.1;
  rightEarcupBossMesh.name = "carryCaseRightEarcupWell";

  const centerBridgeMesh = new THREE.Mesh(createCenterBridgeGeometry(), interiorFeltMaterial);
  centerBridgeMesh.position.set(0, 0.07, 0.06);
  centerBridgeMesh.scale.set(1, 0.5, 0.92);
  centerBridgeMesh.name = "carryCaseCenterBridge";

  const cableCompartmentLidMesh = new THREE.Mesh(createCenterBridgeGeometry(), interiorFeltMaterial);
  cableCompartmentLidMesh.position.set(0, 0.06, 0.2);
  cableCompartmentLidMesh.scale.set(0.92, 0.85, 0.9);
  cableCompartmentLidMesh.name = "carryCaseCableCompartmentLid";

  const cableDividerLineMesh = new THREE.Mesh(
    new THREE.BoxGeometry(0.68, 0.008, 0.012),
    zipperTapeMaterial,
  );
  cableDividerLineMesh.position.set(0, 0.122, 0.165);
  cableDividerLineMesh.name = "carryCaseCableCompartmentSeam";

  const cableFingerRecessMesh = new THREE.Mesh(
    new THREE.BoxGeometry(0.18, 0.01, 0.032),
    zipperTapeMaterial,
  );
  cableFingerRecessMesh.position.set(0, 0.124, 0.19);
  cableFingerRecessMesh.name = "carryCaseCableFingerRecess";

  interiorTrayGroup.add(
    baseTrayMesh,
    leftEarcupBossMesh,
    rightEarcupBossMesh,
    centerBridgeMesh,
    cableCompartmentLidMesh,
    cableDividerLineMesh,
    cableFingerRecessMesh,
  );

  const spineHingeMesh = new THREE.Mesh(createRearHingeBridgeGeometry(), wovenFabricMaterial);
  spineHingeMesh.position.set(0, CASE_DIMENSIONS.seamHeightY - 0.011, CASE_DIMENSIONS.rearSpineZ + 0.04);
  spineHingeMesh.name = "carryCaseRearSpineHinge";

  const hangingLoopMesh = new THREE.Mesh(createHangingLoopGeometry(), wovenFabricMaterial);
  hangingLoopMesh.position.set(0, CASE_DIMENSIONS.seamHeightY - 0.008, CASE_DIMENSIONS.frontApexZ - 0.008);
  hangingLoopMesh.rotation.x = -0.14;
  hangingLoopMesh.name = "carryCaseHangingLoop";

  const lidPivot = new THREE.Group();
  lidPivot.name = "carryCaseLidPivot";
  lidPivot.position.set(0, CASE_DIMENSIONS.seamHeightY, CASE_DIMENSIONS.rearSpineZ + 0.025);

  const lidLocalOffsetGroup = new THREE.Group();
  lidLocalOffsetGroup.position.set(0, -CASE_DIMENSIONS.seamHeightY, -(CASE_DIMENSIONS.rearSpineZ + 0.025));
  lidPivot.add(lidLocalOffsetGroup);

  const upperShellProfiles = [
    { scaleX: 0.958, scaleZ: 0.958, heightY: CASE_DIMENSIONS.seamHeightY + 0.004 },
    { scaleX: 0.982, scaleZ: 0.982, heightY: CASE_DIMENSIONS.seamHeightY - 0.004 },
    { scaleX: 1.0, scaleZ: 1.0, heightY: CASE_DIMENSIONS.seamHeightY },
    { scaleX: 1.0, scaleZ: 1.0, heightY: 0.42 },
    { scaleX: 0.985, scaleZ: 0.985, heightY: 0.54 },
    { scaleX: 0.91, scaleZ: 0.91, heightY: 0.64 },
    { scaleX: 0.74, scaleZ: 0.74, heightY: 0.678 },
    { scaleX: 0.48, scaleZ: 0.48, heightY: 0.692 },
    { scaleX: 0.22, scaleZ: 0.22, heightY: 0.698 },
    { scaleX: 0.001, scaleZ: 0.001, heightY: 0.7 },
  ];
  const upperShellMesh = new THREE.Mesh(
    createLoftedShellGeometry(perimeterPoints, upperShellProfiles, false, false),
    wovenFabricMaterial,
  );
  upperShellMesh.name = "carryCaseUpperShell";

  const upperRimTapeMesh = new THREE.Mesh(
    createZipperTrackGeometry(perimeterPoints, CASE_DIMENSIONS.seamHeightY + 0.008, 1.002),
    zipperTapeMaterial,
  );
  upperRimTapeMesh.name = "carryCaseUpperZipperTape";

  const lidInnerLinerProfiles = [
    { scaleX: 0.958, scaleZ: 0.958, heightY: CASE_DIMENSIONS.seamHeightY + 0.004 },
    { scaleX: 0.94, scaleZ: 0.94, heightY: 0.5 },
    { scaleX: 0.88, scaleZ: 0.88, heightY: 0.6 },
    { scaleX: 0.72, scaleZ: 0.72, heightY: 0.642 },
    { scaleX: 0.45, scaleZ: 0.45, heightY: 0.658 },
    { scaleX: 0.18, scaleZ: 0.18, heightY: 0.663 },
    { scaleX: 0.001, scaleZ: 0.001, heightY: 0.665 },
  ];
  const lidInnerLinerGeometry = createLoftedShellGeometry(
    perimeterPoints,
    lidInnerLinerProfiles,
    false,
    false,
  );
  const linerIndexAttr = lidInnerLinerGeometry.getIndex();
  for (let indexOffset = 0; indexOffset < linerIndexAttr.count; indexOffset += 3) {
    const tempIndex = linerIndexAttr.getX(indexOffset + 1);
    linerIndexAttr.setX(indexOffset + 1, linerIndexAttr.getX(indexOffset + 2));
    linerIndexAttr.setX(indexOffset + 2, tempIndex);
  }
  lidInnerLinerGeometry.computeVertexNormals();

  const lidInnerLinerMesh = new THREE.Mesh(lidInnerLinerGeometry, interiorFeltMaterial);
  lidInnerLinerMesh.name = "carryCaseLidFeltLiner";

  const lidPadInsertMesh = new THREE.Mesh(createLidPadInsertGeometry(), interiorFeltMaterial);
  lidPadInsertMesh.position.set(0, 0.655, -0.18);
  lidPadInsertMesh.name = "carryCaseLidPadInsert";

  lidLocalOffsetGroup.add(upperShellMesh, upperRimTapeMesh, lidInnerLinerMesh, lidPadInsertMesh);

  const zipperPullGroup = new THREE.Group();
  zipperPullGroup.name = "carryCaseZipperPull";

  const sliderBodyMesh = new THREE.Mesh(new THREE.BoxGeometry(0.056, 0.036, 0.088), sliderMetalMaterial);
  sliderBodyMesh.name = "carryCaseZipperSlider";

  const pullTabMesh = new THREE.Mesh(new THREE.BoxGeometry(0.022, 0.044, 0.125), pullTabMaterial);
  pullTabMesh.position.set(-0.035, -0.008, 0.075);
  pullTabMesh.rotation.y = -0.25;
  pullTabMesh.name = "carryCaseZipperPullTab";

  zipperPullGroup.add(sliderBodyMesh, pullTabMesh);

  rootGroup.add(
    lowerShellMesh,
    lowerInnerWallMesh,
    lowerRimTapeMesh,
    zipperTeethRailMesh,
    interiorTrayGroup,
    spineHingeMesh,
    hangingLoopMesh,
    lidPivot,
    zipperPullGroup,
  );

  const curPtScratch = new THREE.Vector2();
  const aheadPtScratch = new THREE.Vector2();

  function updateZipperPullAlongSeam(openRatio) {
    const seamParam = THREE.MathUtils.lerp(0.10, 0.36, openRatio) % 1;
    const currentPoint = evaluateCasePerimeterPoint(seamParam, curPtScratch);
    const aheadPoint = evaluateCasePerimeterPoint((seamParam + 0.01) % 1, aheadPtScratch);
    const tangentAngle = Math.atan2(
      aheadPoint.x - currentPoint.x,
      aheadPoint.y - currentPoint.y,
    );

    zipperPullGroup.position.set(
      currentPoint.x * 1.012,
      CASE_DIMENSIONS.seamHeightY,
      currentPoint.y * 1.012,
    );
    zipperPullGroup.rotation.set(0, tangentAngle, 0);
  }

  let currentOpenAmount = -1;
  function setOpenAmount(openRatio) {
    const clampedOpenRatio = clampUnitRange(openRatio);
    if (Math.abs(clampedOpenRatio - currentOpenAmount) < 1e-6) {
      return;
    }
    currentOpenAmount = clampedOpenRatio;
    lidPivot.rotation.x = -clampedOpenRatio * CASE_DIMENSIONS.maxLidOpenRadians;
    updateZipperPullAlongSeam(clampedOpenRatio);
  }

  function setColorTarget(colorwayName) {
    const key = resolveColorwayKey(colorwayName);
    if (!(key in CASE_COLORWAY_PRESETS) || key === activeColorway) {
      return;
    }
    activeColorway = key;
    const { wovenColor, feltColor } = getFinishTextures(key);
    wovenFabricMaterial.map = wovenColor;
    interiorFeltMaterial.map = feltColor;
  }

  function applyLinearColors(f) {
    const set = (c, a) => c.setRGB(a[0], a[1], a[2]);
    set(zipperTapeMaterial.color, f.zipperTape);
    set(zipperTeethMaterial.color, f.zipperTeeth);
    set(sliderMetalMaterial.color, f.sliderMetal);
    set(pullTabMaterial.color, f.pullTab);
  }

  let currentOpacity = 1;
  function setOpacity(alpha) {
    const clamped = clampUnitRange(alpha);
    if (Math.abs(clamped - currentOpacity) < 1e-4) return;
    currentOpacity = clamped;
    const isSolid = clamped >= 0.999;
    for (const mat of caseMaterials) {
      mat.depthWrite = isSolid;
      mat.opacity = clamped;
    }
  }

  function getSeatedHeadphonePose() {
    return {
      position: new THREE.Vector3(0, 0.455, -0.92),
      rotation: new THREE.Euler(-Math.PI / 2, 0, Math.PI, "XYZ"),
    };
  }

  function dispose() {
    disposeObjectGeometries(rootGroup);
    wovenScalars.bumpTexture.dispose();
    wovenScalars.roughnessTexture.dispose();
    feltBumpTexture.dispose();
    for (const tex of wovenColorCache.values()) tex.dispose();
    for (const tex of feltColorCache.values()) tex.dispose();
    wovenColorCache.clear();
    feltColorCache.clear();
    for (const mat of caseMaterials) {
      mat.dispose();
    }
  }

  setOpenAmount(0);

  return {
    rootGroup,
    setOpenAmount,
    setColorTarget,
    applyLinearColors,
    setOpacity,
    getSeatedHeadphonePose,
    dispose,
  };
}
