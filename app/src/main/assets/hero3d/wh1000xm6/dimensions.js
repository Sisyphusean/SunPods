import * as THREE from "three";

export { clampUnitRange } from "./geometryUtils.js";

export const HEADPHONE_DIMENSIONS = {
  // 1 Three.js unit equals 10 cm in physical scale.
  // Taller, cleaner WH-1000XM6 horseshoe headband with wide crown tapering into sleek cylindrical stems.
  headbandArchControlPoints: [
    new THREE.Vector3(-0.640, 1.245, 0),
    new THREE.Vector3(-0.652, 1.435, 0),
    new THREE.Vector3(-0.535, 1.655, 0),
    new THREE.Vector3(-0.312, 1.842, 0),
    new THREE.Vector3(0, 1.918, 0),
    new THREE.Vector3(0.312, 1.842, 0),
    new THREE.Vector3(0.535, 1.655, 0),
    new THREE.Vector3(0.652, 1.435, 0),
    new THREE.Vector3(0.640, 1.245, 0),
  ],
  headbandCrownHalfWidthZ: 0.138,
  headbandTipHalfWidthZ: 0.086,
  headbandCrownHalfThicknessR: 0.044,
  headbandTipHalfThicknessR: 0.039,

  // Tubular slider & exposed diagonal scarf-joint hinge stem.
  sliderRadius: 0.032,
  sliderScaleX: 0.96,
  sliderScaleZ: 1.04,
  sliderTopAnchorY: 0.05,
  sliderBaseExposedLength: 0.092,
  sliderMaxExtension: 0.24,

  hingeCutTiltRadians: THREE.MathUtils.degToRad(36),
  yokeTongueLength: 0.275,
  yokeTongueWidthZ: 0.064,
  yokeTongueThicknessX: 0.056,
  earcupFoldInwardAngleRadians: THREE.MathUtils.degToRad(78),

  // Smooth matte XM6 earcup with flush circular-oval touch face (zero stepped bevel lip).
  earcupHalfHeightY: 0.495,
  earcupHalfDepthZ: 0.395,
  earcupShellThicknessX: 0.240,
  earcupPlateauOffsetY: -0.028,
  earcupPlateauHalfHeightY: 0.352,
  earcupPlateauHalfDepthZ: 0.285,
  earcupInwardTiltRadians: 0.13,
  earcupSweepInwardTiltRadians: 0.10,
  earcupForwardSweepRadians: 0.14,
  earcupFlatFoldAngleRadians: Math.PI * 0.48,

  earpadDepthX: 0.188,
  earpadInnerHalfHeightY: 0.305,
  earpadInnerHalfDepthZ: 0.202,
};
