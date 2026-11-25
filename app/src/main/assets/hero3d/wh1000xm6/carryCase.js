import * as THREE from "three";
import { clampUnitRange } from "./dimensions.js";
import { buildLoftedGeometry, disposeObjectGeometries, evalSuperellipse } from "./geometryUtils.js";
import { resolveColorwayKey } from "./materials.js";
import {
  createFeltColorTexture,
  createFeltScalarTexture,
  createWovenFabricColorTexture,
  createWovenFabricScalarTextures,
} from "./textures.js";

export const CASE_COLORWAY_PRESETS = {
  black: {
    fabricBaseHex: "#24262c",
    fabricThreadHex: "#31343d",
    feltBaseHex: "#1b1d22",
    feltFiberHex: "#272a31",
    seamHex: 0x131418,
    claspStrapHex: 0x1e2026,
    claspTrimHex: 0x626772,
  },
  silver: {
    fabricBaseHex: "#c9c8c4",
    fabricThreadHex: "#d6d5d1",
    feltBaseHex: "#b5b4b0",
    feltFiberHex: "#c4c3bf",
    seamHex: 0xa2a19c,
    claspStrapHex: "#c2c1bd",
    claspTrimHex: 0x9b9a95,
  },
  blue: {
    fabricBaseHex: "#212e42",
    fabricThreadHex: "#2e405b",
    feltBaseHex: "#192333",
    feltFiberHex: "#25334a",
    seamHex: 0x131b28,
    claspStrapHex: "#1d293d",
    claspTrimHex: 0x647a9c,
  },
  pink: {
    fabricBaseHex: "#b79e9b",
    fabricThreadHex: "#c8b1ae",
    feltBaseHex: "#a08987",
    feltFiberHex: "#b29b99",
    seamHex: 0x8f7977,
    claspStrapHex: 0xb29a97,
    claspTrimHex: 0x9e7d69,
  },
};

// Compact pebble-oval clamshell proportions for folded WH-1000XM6 (no zipper).
const CASE_DIMENSIONS = {
  rearSpineZ: -0.96,
  frontApexZ: 0.94,
  rearHalfWidthX: 1.04,
  frontHalfWidthX: 0.96,
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
    Math.pow(rearBlend, 0.75),
  );

  const longitudinalRadius = cosAngle >= 0 ? CASE_DIMENSIONS.frontApexZ : Math.abs(CASE_DIMENSIONS.rearSpineZ);
  const ptX = evalSuperellipse(sinAngle, cosAngle, halfWidthX, longitudinalRadius, 2 / 2.25).x;
  const ptZ = evalSuperellipse(sinAngle, cosAngle, halfWidthX, longitudinalRadius, 2 / 2.25).y;
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

function createPerimeterSeamLipGeometry(perimeterPoints, seamHeightY, seamScale) {
  const seamPoints = perimeterPoints.map(
    (p) => new THREE.Vector3(p.x * seamScale, seamHeightY, p.y * seamScale),
  );
  const seamCurve = new THREE.CatmullRomCurve3(seamPoints, true);
  return new THREE.TubeGeometry(seamCurve, 128, 0.0085, 8, true);
}

function createMoldedInteriorTrayGeometry(perimeterPoints) {
  const trayShape = new THREE.Shape();
  const insetFactor = 0.94;

  perimeterPoints.forEach((p, idx) => {
    const ix = p.x * insetFactor;
    const iz = -p.y * insetFactor;
    if (idx === 0) trayShape.moveTo(ix, iz);
    else trayShape.lineTo(ix, iz);
  });
  trayShape.closePath();

  const trayGeometry = new THREE.ExtrudeGeometry(trayShape, {
    depth: 0.22,
    bevelEnabled: true,
    bevelThickness: 0.024,
    bevelSize: 0.02,
    bevelSegments: 3,
  });
  trayGeometry.rotateX(-Math.PI / 2);
  trayGeometry.computeVertexNormals();
  return trayGeometry;
}

function createRearHingeBridgeGeometry() {
  const hingeShape = new THREE.Shape();
  hingeShape.moveTo(-0.46, -0.055);
  hingeShape.lineTo(0.46, -0.055);
  hingeShape.lineTo(0.42, 0.055);
  hingeShape.lineTo(-0.42, 0.055);
  hingeShape.closePath();

  const hingeGeometry = new THREE.ExtrudeGeometry(hingeShape, {
    depth: 0.020,
    bevelEnabled: true,
    bevelThickness: 0.007,
    bevelSize: 0.007,
    bevelSegments: 2,
  });
  hingeGeometry.rotateX(-Math.PI / 2);
  hingeGeometry.computeVertexNormals();
  return hingeGeometry;
}

function createMagneticClaspStrapGeometry() {
  // Vertical woven/matte closure tab folding down from lid over horizontal clamshell seam.
  const strapShape = new THREE.Shape();
  const halfW = 0.085;
  const r = 0.036;
  const topY = 0.16;
  const botY = -0.15;

  strapShape.moveTo(-halfW, topY);
  strapShape.lineTo(halfW, topY);
  strapShape.lineTo(halfW, botY + r);
  strapShape.absarc(halfW - r, botY + r, r, 0, -Math.PI / 2, true);
  strapShape.lineTo(-halfW + r, botY);
  strapShape.absarc(-halfW + r, botY + r, r, -Math.PI / 2, -Math.PI, true);
  strapShape.closePath();

  const geo = new THREE.ExtrudeGeometry(strapShape, {
    depth: 0.018,
    bevelEnabled: true,
    bevelThickness: 0.005,
    bevelSize: 0.004,
    bevelSegments: 2,
    curveSegments: 12,
  });
  geo.computeVertexNormals();
  return geo;
}

function createClaspBarGeometry() {
  const barShape = new THREE.Shape();
  const halfLen = 0.038;
  const rad = 0.008;
  barShape.moveTo(-halfLen, -rad);
  barShape.lineTo(halfLen, -rad);
  barShape.absarc(halfLen, 0, rad, -Math.PI / 2, Math.PI / 2, false);
  barShape.lineTo(-halfLen, rad);
  barShape.absarc(-halfLen, 0, rad, Math.PI / 2, (Math.PI * 3) / 2, false);

  const geo = new THREE.ExtrudeGeometry(barShape, {
    depth: 0.008,
    bevelEnabled: true,
    bevelThickness: 0.002,
    bevelSize: 0.002,
    bevelSegments: 2,
    curveSegments: 10,
  });
  geo.computeVertexNormals();
  return geo;
}

export function createCarryCaseModel(initialColorway = "black", options = {}) {
  let activeColorway = resolveColorwayKey(initialColorway);
  if (!(activeColorway in CASE_COLORWAY_PRESETS)) {
    throw new RangeError(`Unknown WH-1000XM6 case colorway "${initialColorway}".`);
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
    bumpScale: 0.0042,
    roughnessMap: wovenScalars.roughnessTexture,
    roughness: 0.89,
    metalness: 0.02,
    sheen: 0.16,
    sheenRoughness: 0.88,
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

  const seamLipMaterial = decorate(new THREE.MeshStandardMaterial({
    color: initialPreset.seamHex,
    roughness: 0.84,
    metalness: 0.04,
    transparent: true,
    opacity: 1,
    depthWrite: true,
  }));

  const claspStrapMaterial = decorate(new THREE.MeshPhysicalMaterial({
    color: initialPreset.claspStrapHex,
    roughness: 0.72,
    metalness: 0.03,
    clearcoat: 0,
    transparent: true,
    opacity: 1,
    depthWrite: true,
  }));

  const claspTrimMaterial = decorate(new THREE.MeshStandardMaterial({
    color: initialPreset.claspTrimHex,
    roughness: 0.36,
    metalness: 0.65,
    transparent: true,
    opacity: 1,
    depthWrite: true,
  }));

  const caseMaterials = [
    wovenFabricMaterial,
    interiorFeltMaterial,
    seamLipMaterial,
    claspStrapMaterial,
    claspTrimMaterial,
  ];

  const rootGroup = new THREE.Group();
  rootGroup.name = "sonyWh1000Xm6CarryCase";

  const perimeterPoints = sampleCasePerimeterPoints(72);

  const lowerOuterProfiles = [
    { scaleX: 0.001, scaleZ: 0.001, heightY: 0.0 },
    { scaleX: 0.28, scaleZ: 0.28, heightY: 0.0 },
    { scaleX: 0.56, scaleZ: 0.56, heightY: 0.0 },
    { scaleX: 0.78, scaleZ: 0.78, heightY: 0.004 },
    { scaleX: 0.88, scaleZ: 0.88, heightY: 0.020 },
    { scaleX: 0.95, scaleZ: 0.95, heightY: 0.055 },
    { scaleX: 0.992, scaleZ: 0.992, heightY: 0.12 },
    { scaleX: 1.0, scaleZ: 1.0, heightY: 0.22 },
    { scaleX: 1.0, scaleZ: 1.0, heightY: CASE_DIMENSIONS.seamHeightY },
    { scaleX: 0.978, scaleZ: 0.978, heightY: CASE_DIMENSIONS.seamHeightY + 0.004 },
    { scaleX: 0.955, scaleZ: 0.955, heightY: CASE_DIMENSIONS.seamHeightY - 0.004 },
  ];
  const lowerShellMesh = new THREE.Mesh(
    createLoftedShellGeometry(perimeterPoints, lowerOuterProfiles, false, false, true),
    wovenFabricMaterial,
  );
  lowerShellMesh.name = "carryCaseLowerShell";

  const lowerInnerProfiles = [
    { scaleX: 0.955, scaleZ: 0.955, heightY: CASE_DIMENSIONS.seamHeightY - 0.004 },
    { scaleX: 0.945, scaleZ: 0.945, heightY: 0.22 },
    { scaleX: 0.92, scaleZ: 0.92, heightY: 0.07 },
    { scaleX: 0.84, scaleZ: 0.84, heightY: 0.04 },
  ];
  const lowerInnerWallMesh = new THREE.Mesh(
    createLoftedShellGeometry(perimeterPoints, lowerInnerProfiles, false, false),
    interiorFeltMaterial,
  );
  lowerInnerWallMesh.name = "carryCaseLowerInnerWall";

  const lowerSeamLipMesh = new THREE.Mesh(
    createPerimeterSeamLipGeometry(perimeterPoints, CASE_DIMENSIONS.seamHeightY - 0.003, 1.001),
    seamLipMaterial,
  );
  lowerSeamLipMesh.name = "carryCaseLowerSeamLip";

  const interiorTrayGroup = new THREE.Group();
  interiorTrayGroup.name = "carryCaseInteriorTray";

  const baseTrayMesh = new THREE.Mesh(
    createMoldedInteriorTrayGeometry(perimeterPoints),
    interiorFeltMaterial,
  );
  baseTrayMesh.position.y = 0.05;
  baseTrayMesh.scale.set(1, 0.22, 1);
  baseTrayMesh.name = "carryCaseFeltTray";

  const pocketDividerMesh = new THREE.Mesh(new THREE.BoxGeometry(0.56, 0.012, 0.014), seamLipMaterial);
  pocketDividerMesh.position.set(0, 0.118, 0.30);
  pocketDividerMesh.name = "carryCaseCablePocketSeam";

  interiorTrayGroup.add(baseTrayMesh, pocketDividerMesh);

  const spineHingeMesh = new THREE.Mesh(createRearHingeBridgeGeometry(), wovenFabricMaterial);
  spineHingeMesh.position.set(0, CASE_DIMENSIONS.seamHeightY - 0.008, CASE_DIMENSIONS.rearSpineZ + 0.03);
  spineHingeMesh.name = "carryCaseRearSpineHinge";

  const lidPivot = new THREE.Group();
  lidPivot.name = "carryCaseLidPivot";
  lidPivot.position.set(0, CASE_DIMENSIONS.seamHeightY, CASE_DIMENSIONS.rearSpineZ + 0.025);

  const lidLocalOffsetGroup = new THREE.Group();
  lidLocalOffsetGroup.position.set(0, -CASE_DIMENSIONS.seamHeightY, -(CASE_DIMENSIONS.rearSpineZ + 0.025));
  lidPivot.add(lidLocalOffsetGroup);

  const upperShellProfiles = [
    { scaleX: 0.955, scaleZ: 0.955, heightY: CASE_DIMENSIONS.seamHeightY + 0.004 },
    { scaleX: 0.978, scaleZ: 0.978, heightY: CASE_DIMENSIONS.seamHeightY - 0.004 },
    { scaleX: 1.0, scaleZ: 1.0, heightY: CASE_DIMENSIONS.seamHeightY },
    { scaleX: 1.0, scaleZ: 1.0, heightY: 0.45 },
    { scaleX: 0.97, scaleZ: 0.97, heightY: 0.58 },
    { scaleX: 0.89, scaleZ: 0.89, heightY: 0.67 },
    { scaleX: 0.72, scaleZ: 0.72, heightY: 0.715 },
    { scaleX: 0.46, scaleZ: 0.46, heightY: 0.732 },
    { scaleX: 0.20, scaleZ: 0.20, heightY: 0.738 },
    { scaleX: 0.001, scaleZ: 0.001, heightY: 0.74 },
  ];
  const upperShellMesh = new THREE.Mesh(
    createLoftedShellGeometry(perimeterPoints, upperShellProfiles, false, false),
    wovenFabricMaterial,
  );
  upperShellMesh.name = "carryCaseUpperShell";

  const upperSeamLipMesh = new THREE.Mesh(
    createPerimeterSeamLipGeometry(perimeterPoints, CASE_DIMENSIONS.seamHeightY + 0.003, 1.001),
    seamLipMaterial,
  );
  upperSeamLipMesh.name = "carryCaseUpperSeamLip";

  const lidInnerLinerProfiles = [
    { scaleX: 0.955, scaleZ: 0.955, heightY: CASE_DIMENSIONS.seamHeightY + 0.004 },
    { scaleX: 0.935, scaleZ: 0.935, heightY: 0.53 },
    { scaleX: 0.86, scaleZ: 0.86, heightY: 0.64 },
    { scaleX: 0.68, scaleZ: 0.68, heightY: 0.682 },
    { scaleX: 0.42, scaleZ: 0.42, heightY: 0.698 },
    { scaleX: 0.16, scaleZ: 0.16, heightY: 0.703 },
    { scaleX: 0.001, scaleZ: 0.001, heightY: 0.705 },
  ];
  const lidInnerLinerGeometry = createLoftedShellGeometry(perimeterPoints, lidInnerLinerProfiles, false, false);
  const linerIndexAttr = lidInnerLinerGeometry.getIndex();
  for (let idx = 0; idx < linerIndexAttr.count; idx += 3) {
    const tmp = linerIndexAttr.getX(idx + 1);
    linerIndexAttr.setX(idx + 1, linerIndexAttr.getX(idx + 2));
    linerIndexAttr.setX(idx + 2, tmp);
  }
  lidInnerLinerGeometry.computeVertexNormals();
  const lidInnerLinerMesh = new THREE.Mesh(lidInnerLinerGeometry, interiorFeltMaterial);
  lidInnerLinerMesh.name = "carryCaseLidFeltLiner";

  // Front magnetic closure strap flap attached to lid apex.
  const claspPivot = new THREE.Group();
  claspPivot.name = "carryCaseMagneticClasp";
  const frontApexPt = evaluateCasePerimeterPoint(0.0);
  claspPivot.position.set(frontApexPt.x, CASE_DIMENSIONS.seamHeightY + 0.08, CASE_DIMENSIONS.frontApexZ - 0.008);

  const strapMesh = new THREE.Mesh(createMagneticClaspStrapGeometry(), claspStrapMaterial);
  strapMesh.position.set(0, -0.09, 0.006);
  strapMesh.name = "carryCaseMagneticStrap";

  const barMesh = new THREE.Mesh(createClaspBarGeometry(), claspTrimMaterial);
  barMesh.position.set(0, -0.20, 0.025);
  barMesh.name = "carryCaseMagneticClaspBar";

  claspPivot.add(strapMesh, barMesh);

  lidLocalOffsetGroup.add(upperShellMesh, upperSeamLipMesh, lidInnerLinerMesh, claspPivot);

  rootGroup.add(
    lowerShellMesh,
    lowerInnerWallMesh,
    lowerSeamLipMesh,
    interiorTrayGroup,
    spineHingeMesh,
    lidPivot,
  );

  let currentOpenAmount = -1;
  function setOpenAmount(openRatio) {
    const clampedOpenRatio = clampUnitRange(openRatio);
    if (Math.abs(clampedOpenRatio - currentOpenAmount) < 1e-6) return;
    currentOpenAmount = clampedOpenRatio;
    // Unlatch magnetic strap slightly ahead of lid opening.
    const unlatchT = clampUnitRange(clampedOpenRatio * 3.0);
    claspPivot.rotation.x = -unlatchT * THREE.MathUtils.degToRad(34);
    lidPivot.rotation.x = -clampedOpenRatio * CASE_DIMENSIONS.maxLidOpenRadians;
  }

  function setColorTarget(colorwayName) {
    const key = resolveColorwayKey(colorwayName);
    if (!(key in CASE_COLORWAY_PRESETS) || key === activeColorway) return;
    activeColorway = key;
    const { wovenColor, feltColor } = getFinishTextures(key);
    wovenFabricMaterial.map = wovenColor;
    interiorFeltMaterial.map = feltColor;
  }

  function applyLinearColors(f) {
    const set = (c, a) => c.setRGB(a[0], a[1], a[2]);
    set(seamLipMaterial.color, f.caseSeam);
    set(claspStrapMaterial.color, f.claspStrap);
    set(claspTrimMaterial.color, f.claspTrim);
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
    // Compact folded+swiveled XM6 rests flush inside the oval pocket with crown hugging the rear wall.
    return {
      position: new THREE.Vector3(0, 0.25, 1.24),
      rotation: new THREE.Euler(-Math.PI / 2, 0, 0, "XYZ"),
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
