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
    shellHex: 0x1b1c20,
    leatherHex: 0x17181c,
    leatherSheenHex: 0x35373e,
    sliderHex: 0x1f2024,
    metalTrimHex: 0x26282e,
    brandingAccentHex: "#e6cc9e",
    brandingEmissiveHex: 0x9b8054,
    brandingEmissiveIntensity: 0.32,
    rimLabelHex: "#9aa0ab",
    ancBronzeHex: 0x8f6240,
    scrimTintHex: 0x17181c,
    buttonHex: 0x1e2025,
    seamHex: 0x111216,
  },
  silver: {
    shellHex: 0xd9d4ca,
    leatherHex: 0xcfc9be,
    leatherSheenHex: 0xe6e1d7,
    sliderHex: 0xd2cdc3,
    metalTrimHex: 0xa49e93,
    brandingAccentHex: "#686055",
    brandingEmissiveHex: 0x000000,
    brandingEmissiveIntensity: 0.0,
    rimLabelHex: "#524d46",
    ancBronzeHex: 0x8c857a,
    scrimTintHex: 0x45423d,
    buttonHex: 0xcdc7bc,
    seamHex: 0x8e877d,
  },
  blue: {
    shellHex: 0x1d283b,
    leatherHex: 0x172030,
    leatherSheenHex: 0x324159,
    sliderHex: 0x202c40,
    metalTrimHex: 0x34435c,
    brandingAccentHex: "#cddbf0",
    brandingEmissiveHex: 0x6f84a6,
    brandingEmissiveIntensity: 0.24,
    rimLabelHex: "#90a0b8",
    ancBronzeHex: 0x6b5d54,
    scrimTintHex: 0x171e2b,
    buttonHex: 0x222f45,
    seamHex: 0x121b2b,
  },
  pink: {
    shellHex: 0xb69c9a,
    leatherHex: 0xab918f,
    leatherSheenHex: 0xcfb8b6,
    sliderHex: 0xb09795,
    metalTrimHex: 0x8d7977,
    brandingAccentHex: "#a67f58",
    brandingEmissiveHex: 0x614429,
    brandingEmissiveIntensity: 0.12,
    rimLabelHex: "#4d3e3d",
    ancBronzeHex: 0x986f56,
    scrimTintHex: 0x463a39,
    buttonHex: 0xad9492,
    seamHex: 0x766463,
  },
};

export function resolveColorwayKey(colorwayName) {
  return colorwayName === "midnightBlue" ? "blue" : colorwayName;
}

export function createHeadphoneMaterials(initialColorway = "black", options = {}) {
  const resolvedInitial = resolveColorwayKey(initialColorway);
  if (!(resolvedInitial in COLORWAY_PRESETS)) {
    throw new RangeError(`Unknown WH-1000XM5 colorway "${initialColorway}".`);
  }

  const initialPreset = COLORWAY_PRESETS[resolvedInitial];
  const leatherTextures = createLeatheretteGrainTextures();
  const acousticTextures = createAcousticMeshTextures();

  // White-ink glyph canvases are created once and tinted via material.color so crossfades allocate zero canvases.
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
    roughness: 0.28,
    metalness: 0.35,
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
    opacity: 0.82,
    depthWrite: false,
    polygonOffset: true,
    polygonOffsetFactor: -1,
    polygonOffsetUnits: -1,
  }));

  const matteShellMaterial = decorate(new THREE.MeshPhysicalMaterial({
    color: initialPreset.shellHex,
    roughness: 0.68,
    metalness: 0.02,
    clearcoat: 0,
    sheen: 0.06,
    sheenRoughness: 0.9,
  }));

  const sliderStemMaterial = decorate(new THREE.MeshPhysicalMaterial({
    color: initialPreset.sliderHex,
    roughness: 0.56,
    metalness: 0.05,
    clearcoat: 0,
  }));

  const leatheretteMaterial = decorate(new THREE.MeshPhysicalMaterial({
    color: initialPreset.leatherHex,
    roughness: 0.78,
    metalness: 0.01,
    bumpMap: leatherTextures.bumpTexture,
    bumpScale: 0.0028,
    roughnessMap: leatherTextures.roughnessTexture,
    sheen: 0.22,
    sheenRoughness: 0.82,
    sheenColor: new THREE.Color(initialPreset.leatherSheenHex),
  }));

  const satinMetalMaterial = decorate(new THREE.MeshStandardMaterial({
    color: initialPreset.metalTrimHex,
    metalness: 0.65,
    roughness: 0.42,
  }));

  const buttonMaterial = decorate(new THREE.MeshStandardMaterial({
    color: initialPreset.buttonHex,
    metalness: 0.04,
    roughness: 0.56,
  }));

  const recessedCavityMaterial = decorate(new THREE.MeshStandardMaterial({
    color: 0x090a0d,
    metalness: 0.08,
    roughness: 0.88,
  }));

  const seamMaterial = decorate(new THREE.MeshStandardMaterial({
    color: initialPreset.seamHex,
    metalness: 0.04,
    roughness: 0.78,
  }));

  const ancBronzeMeshMaterial = decorate(new THREE.MeshStandardMaterial({
    color: initialPreset.ancBronzeHex,
    metalness: 0.55,
    roughness: 0.48,
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
    sliderStemMaterial,
    leatheretteMaterial,
    satinMetalMaterial,
    buttonMaterial,
    recessedCavityMaterial,
    seamMaterial,
    ancBronzeMeshMaterial,
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
    [sliderStemMaterial.color, "slider"],
    [leatheretteMaterial.color, "leather"],
    [leatheretteMaterial.sheenColor, "leatherSheen"],
    [satinMetalMaterial.color, "metalTrim"],
    [ancBronzeMeshMaterial.color, "ancBronze"],
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
    sliderStemMaterial,
    leatheretteMaterial,
    satinMetalMaterial,
    buttonMaterial,
    recessedCavityMaterial,
    seamMaterial,
    ancBronzeMeshMaterial,
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
