import * as THREE from "three";

export { clampUnitRange } from "./geometryUtils.js";

export const HEADPHONE_DIMENSIONS = {
  // 1 Three.js unit equals 10 cm in physical scale.
  // Gentle horseshoe taper curls cuffs subtly inward so sliders enter cleanly without a front kink.
  headbandArchControlPoints: [
    new THREE.Vector3(-0.662, 1.195, 0),
    new THREE.Vector3(-0.670, 1.372, 0),
    new THREE.Vector3(-0.552, 1.576, 0),
    new THREE.Vector3(-0.324, 1.768, 0),
    new THREE.Vector3(0, 1.85, 0),
    new THREE.Vector3(0.324, 1.768, 0),
    new THREE.Vector3(0.552, 1.576, 0),
    new THREE.Vector3(0.670, 1.372, 0),
    new THREE.Vector3(0.662, 1.195, 0),
  ],
  headbandCrownHalfWidthZ: 0.094,
  headbandTipHalfWidthZ: 0.09,
  headbandCrownHalfThicknessR: 0.062,
  headbandTipHalfThicknessR: 0.058,

  sliderRadius: 0.041,
  sliderTopAnchorY: 0.06,
  sliderBaseExposedLength: 0.068,
  sliderMaxExtension: 0.24,
  sliderSeamThickness: 0.003,

  yokeTongueLength: 0.305,
  yokeTongueWidthZ: 0.088,
  yokeTongueThicknessX: 0.056,

  earcupHalfHeightY: 0.5,
  earcupHalfDepthZ: 0.41,
  earcupShellThicknessX: 0.248,
  earcupPlateauOffsetY: -0.055,
  earcupPlateauHalfHeightY: 0.315,
  earcupPlateauHalfDepthZ: 0.26,
  earcupInwardTiltRadians: 0.10,
  earcupSweepInwardTiltRadians: 0.10,
  earcupForwardSweepRadians: 0.2,
  earcupFlatFoldAngleRadians: Math.PI / 2,

  earpadDepthX: 0.185,
  earpadInnerHalfHeightY: 0.31,
  earpadInnerHalfDepthZ: 0.205,
};
