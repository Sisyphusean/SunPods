import * as THREE from "three";
import {
  createAcousticMeshTextures,
  createHeadbandBadgeTexture,
  createLeatheretteGrainTextures,
  createRimLabelTexture,
  createSonyWordmarkTexture,
} from "./textures.js";

export const COLORWAY_PRESETS = {
  black: {
    shellHex: 0x222429,
    touchFaceHex: 0x2a2c32,
    leatherHex: 0x1c1e23,
    leatherSheenHex: 0x3b3e47,
    sliderHex: 0x26282e,
    hingeCutHex: 0x626772,
    hingeCutMetalness: 0.80,
    hingeCutRoughness: 0.26,
    metalTrimHex: 0x323640,
    brandingAccentHex: "#dfc28b",
    brandingEmissiveHex: 0x8f7344,
    brandingEmissiveIntensity: 0.30,
    rimLabelHex: "#a0a6b2",
    micGrilleHex: 0x32343b,
    scrimTintHex: 0x1a1c20,
    buttonHex: 0x26282e,
    seamHex: 0x111216,
  },
  silver: {
    shellHex: 0xc9c8c4,
    touchFaceHex: 0xd1d0cc,
    leatherHex: 0xc1c0bc,
    leatherSheenHex: 0xdeddd9,
    sliderHex: 0xc4c3bf,
    hingeCutHex: 0xb6b5b0,
    hingeCutMetalness: 0.86,
    hingeCutRoughness: 0.22,
    metalTrimHex: 0xa2a19c,
    brandingAccentHex: "#77726a",
    brandingEmissiveHex: 0x000000,
    brandingEmissiveIntensity: 0.0,
    rimLabelHex: "#58544e",
    micGrilleHex: 0x8e8c87,
    scrimTintHex: 0x454440,
    buttonHex: 0xc2c1bd,
    seamHex: 0x8e8d88,
  },
  blue: {
    shellHex: 0x223149,
    touchFaceHex: 0x2a3b56,
    leatherHex: 0x1d293d,
    leatherSheenHex: 0x3b4d6b,
    sliderHex: 0x263750,
    hingeCutHex: 0x647a9c,
    hingeCutMetalness: 0.78,
    hingeCutRoughness: 0.28,
    metalTrimHex: 0x3a4d6b,
    brandingAccentHex: "#d6e2f2",
    brandingEmissiveHex: 0x7087ab,
    brandingEmissiveIntensity: 0.24,
    rimLabelHex: "#98a8c0",
    micGrilleHex: 0x334561,
    scrimTintHex: 0x1a2333,
    buttonHex: 0x273852,
    seamHex: 0x131c2b,
  },
  pink: {
    shellHex: 0xbca29e,
    touchFaceHex: 0xc4aba7,
    leatherHex: 0xb29894,
    leatherSheenHex: 0xd6beb9,
    sliderHex: 0xb69c98,
    hingeCutHex: 0xb89074,
    hingeCutMetalness: 0.82,
    hingeCutRoughness: 0.26,
    metalTrimHex: 0x987b75,
    brandingAccentHex: "#8c694b",
    brandingEmissiveHex: 0x4e3722,
    brandingEmissiveIntensity: 0.12,
    rimLabelHex: "#4d3e3d",
    micGrilleHex: 0x8c706b,
    scrimTintHex: 0x4a3d3b,
    buttonHex: 0xb69c98,
    seamHex: 0x7b6664,
  },
};

export function resolveColorwayKey(colorwayName) {
  return colorwayName === "midnightBlue" ? "blue" : colorwayName;
}

export function createHeadphoneMaterials(initialColorway = "black", options = {}) {
  const resolvedInitial = resolveColorwayKey(initialColorway);
  if (!(resolvedInitial in COLORWAY_PRESETS)) {
    throw new RangeError(`Unknown WH-1000XM6 colorway "${initialColorway}".`);
  }

  const initialPreset = COLORWAY_PRESETS[resolvedInitial];
  const leatherTextures = createLeatheretteGrainTextures();
  const acousticTextures = createAcousticMeshTextures();

  const leftWordmarkTexture = createSonyWordmarkTexture("#ffffff", -1);
  const rightWordmarkTexture = createSonyWordmarkTexture("#ffffff", 1);
  const powerLabelTexture = createRimLabelTexture("power", "#ffffff");
  const ncAmbLabelTexture = createRimLabelTexture("ncamb", "#ffffff");
  const leftBadgeTexture = createHeadbandBadgeTexture("L", "#9ca3af");
  const rightBadgeTexture = createHeadbandBadgeTexture("R", "#c9282d");

  const decorate = typeof options.decorateMaterial === "function" ? options.decorateMaterial : (m) => m;

  const makeWordmarkMaterial = (map) => decorate(new THREE.MeshStandardMaterial({
    color: new THREE.Color(initialPreset.brandingAccentHex),
    map,
    transparent: true,
    alphaTest: 0.08,
    roughness: 0.26,
    metalness: 0.38,
    emissive: new THREE.Color(initialPreset.brandingEmissiveHex),
    emissiveIntensity: initialPreset.brandingEmissiveIntensity,
    depthWrite: false,
  }));

  const makeBadgeMaterial = (map) => decorate(new THREE.MeshStandardMaterial({
    map,
    transparent: true,
    alphaTest: 0.2,
    roughness: 0.55,
    metalness: 0.05,
    polygonOffset: true,
    polygonOffsetFactor: -1,
    polygonOffsetUnits: -1,
  }));

  const makeRimLabelMaterial = (map) => decorate(new THREE.MeshBasicMaterial({
    color: new THREE.Color(initialPreset.rimLabelHex),
    map,
    transparent: true,
    opacity: 0.84,
    depthWrite: false,
    polygonOffset: true,
    polygonOffsetFactor: -1,
    polygonOffsetUnits: -1,
  }));

  const matteShellMaterial = decorate(new THREE.MeshPhysicalMaterial({
    color: initialPreset.shellHex,
    roughness: 0.70,
    metalness: 0.02,
    clearcoat: 0,
    sheen: 0.05,
    sheenRoughness: 0.90,
  }));

  const touchFaceMaterial = decorate(new THREE.MeshPhysicalMaterial({
    color: initialPreset.touchFaceHex,
    roughness: 0.66,
    metalness: 0.02,
    clearcoat: 0,
    sheen: 0.07,
    sheenRoughness: 0.86,
  }));

  const sliderStemMaterial = decorate(new THREE.MeshPhysicalMaterial({
    color: initialPreset.sliderHex,
    roughness: 0.54,
    metalness: 0.08,
    clearcoat: 0,
  }));

  const scarfCollarMaterial = decorate(new THREE.MeshStandardMaterial({
    color: initialPreset.hingeCutHex,
    metalness: initialPreset.hingeCutMetalness,
    roughness: initialPreset.hingeCutRoughness,
  }));

  const leatheretteMaterial = decorate(new THREE.MeshPhysicalMaterial({
    color: initialPreset.leatherHex,
    roughness: 0.78,
    metalness: 0.01,
    bumpMap: leatherTextures.bumpTexture,
    bumpScale: 0.0026,
    roughnessMap: leatherTextures.roughnessTexture,
    sheen: 0.20,
    sheenRoughness: 0.84,
    sheenColor: new THREE.Color(initialPreset.leatherSheenHex),
  }));

  const satinMetalMaterial = decorate(new THREE.MeshStandardMaterial({
    color: initialPreset.metalTrimHex,
    metalness: 0.68,
    roughness: 0.36,
  }));

  const buttonMaterial = decorate(new THREE.MeshStandardMaterial({
    color: initialPreset.buttonHex,
    metalness: 0.06,
    roughness: 0.52,
  }));

  const recessedCavityMaterial = decorate(new THREE.MeshStandardMaterial({
    color: 0x090a0d,
    metalness: 0.06,
    roughness: 0.90,
  }));

  const seamMaterial = decorate(new THREE.MeshStandardMaterial({
    color: initialPreset.seamHex,
    metalness: 0.04,
    roughness: 0.80,
  }));

  const micGrilleMaterial = decorate(new THREE.MeshStandardMaterial({
    color: initialPreset.micGrilleHex,
    metalness: 0.48,
    roughness: 0.52,
  }));

  const usbMetalLinerMaterial = decorate(new THREE.MeshStandardMaterial({
    color: 0xa8adb5,
    metalness: 0.85,
    roughness: 0.25,
  }));

  const acousticScrimMaterial = decorate(new THREE.MeshStandardMaterial({
    color: initialPreset.scrimTintHex,
    map: acousticTextures.colorTexture,
    bumpMap: acousticTextures.bumpTexture,
    bumpScale: 0.003,
    metalness: 0.0,
    roughness: 0.94,
  }));

  const leftWordmarkMaterial = makeWordmarkMaterial(leftWordmarkTexture);
  const rightWordmarkMaterial = makeWordmarkMaterial(rightWordmarkTexture);
  const leftBadgeMaterial = makeBadgeMaterial(leftBadgeTexture);
  const rightBadgeMaterial = makeBadgeMaterial(rightBadgeTexture);
  const powerLabelMaterial = makeRimLabelMaterial(powerLabelTexture);
  const ncAmbLabelMaterial = makeRimLabelMaterial(ncAmbLabelTexture);

  const statusLedMaterial = decorate(new THREE.MeshStandardMaterial({
    color: 0x3b82f6,
    emissive: 0x2563eb,
    emissiveIntensity: 1.5,
    roughness: 0.25,
    metalness: 0.1,
  }));

  const chargingLedMaterial = decorate(new THREE.MeshStandardMaterial({
    color: 0x7c3f14,
    emissive: 0xea580c,
    emissiveIntensity: 0.75,
    roughness: 0.3,
    metalness: 0.1,
  }));

  const ownedMaterials = [
    matteShellMaterial,
    touchFaceMaterial,
    sliderStemMaterial,
    scarfCollarMaterial,
    leatheretteMaterial,
    satinMetalMaterial,
    buttonMaterial,
    recessedCavityMaterial,
    seamMaterial,
    micGrilleMaterial,
    usbMetalLinerMaterial,
    acousticScrimMaterial,
    leftWordmarkMaterial,
    rightWordmarkMaterial,
    leftBadgeMaterial,
    rightBadgeMaterial,
    statusLedMaterial,
    chargingLedMaterial,
    powerLabelMaterial,
    ncAmbLabelMaterial,
  ];

  const ownedTextures = [
    leatherTextures.bumpTexture,
    leatherTextures.roughnessTexture,
    acousticTextures.colorTexture,
    acousticTextures.bumpTexture,
    leftBadgeTexture,
    rightBadgeTexture,
    leftWordmarkTexture,
    rightWordmarkTexture,
    powerLabelTexture,
    ncAmbLabelTexture,
  ];

  const colorTargets = [
    [matteShellMaterial.color, "shell"],
    [touchFaceMaterial.color, "touchFace"],
    [sliderStemMaterial.color, "slider"],
    [scarfCollarMaterial.color, "hingeCut"],
    [leatheretteMaterial.color, "leather"],
    [leatheretteMaterial.sheenColor, "leatherSheen"],
    [satinMetalMaterial.color, "metalTrim"],
    [micGrilleMaterial.color, "micGrille"],
    [acousticScrimMaterial.color, "scrimTint"],
    [buttonMaterial.color, "button"],
    [seamMaterial.color, "seam"],
    [leftWordmarkMaterial.color, "brandingAccent"],
    [leftWordmarkMaterial.emissive, "brandingEmissive"],
    [rightWordmarkMaterial.color, "brandingAccent"],
    [rightWordmarkMaterial.emissive, "brandingEmissive"],
    [powerLabelMaterial.color, "rimLabel"],
    [ncAmbLabelMaterial.color, "rimLabel"],
  ];

  function applyLinearColors(f) {
    for (const [targetColor, key] of colorTargets) {
      const rgb = f[key];
      targetColor.setRGB(rgb[0], rgb[1], rgb[2]);
    }
    const emissiveInt = f.brandingEmissiveIntensity[0];
    leftWordmarkMaterial.emissiveIntensity = emissiveInt;
    rightWordmarkMaterial.emissiveIntensity = emissiveInt;
  }

  function dispose() {
    for (const activeMaterial of ownedMaterials) {
      activeMaterial.dispose();
    }
    for (const activeTexture of ownedTextures) {
      activeTexture.dispose();
    }
  }

  return {
    matteShellMaterial,
    touchFaceMaterial,
    sliderStemMaterial,
    scarfCollarMaterial,
    leatheretteMaterial,
    satinMetalMaterial,
    buttonMaterial,
    recessedCavityMaterial,
    seamMaterial,
    micGrilleMaterial,
    usbMetalLinerMaterial,
    acousticScrimMaterial,
    leftWordmarkMaterial,
    rightWordmarkMaterial,
    leftBadgeMaterial,
    rightBadgeMaterial,
    statusLedMaterial,
    chargingLedMaterial,
    powerLabelMaterial,
    ncAmbLabelMaterial,
    applyLinearColors,
    dispose,
  };
}
