import * as THREE from "three";
import { clampUnitRange } from "./dimensions.js";
import { disposeObjectGeometries } from "./geometryUtils.js";
import { createHeadphoneMaterials } from "./materials.js";
import { createHeadbandAssembly } from "./headband.js";
import { createSideAssembly } from "./hingeAssembly.js";

export function createWh1000Xm6Model(initialColorway = "black", options = {}) {
  const rootGroup = new THREE.Group();
  rootGroup.name = "sonyWh1000Xm6";

  const modelAnchorGroup = new THREE.Group();
  modelAnchorGroup.name = "wh1000xm6Anchor";
  rootGroup.add(modelAnchorGroup);

  const materials = createHeadphoneMaterials(initialColorway, options);
  const { headbandAssembly, leftTipAnchor, rightTipAnchor, setFlex } =
    createHeadbandAssembly(materials);

  const leftSide = createSideAssembly(1, leftTipAnchor, materials);
  const rightSide = createSideAssembly(-1, rightTipAnchor, materials);

  modelAnchorGroup.add(headbandAssembly, leftSide.sideAssembly, rightSide.sideAssembly);

  rootGroup.updateMatrixWorld(true);
  const neutralBoundingBox = new THREE.Box3().setFromObject(modelAnchorGroup);
  modelAnchorGroup.position.y = -neutralBoundingBox.min.y;
  rootGroup.updateMatrixWorld(true);

  function applyLinearColors(finishObj) {
    materials.applyLinearColors(finishObj);
  }

  function setSliderExtension(extensionRatio) {
    leftSide.setExtension(extensionRatio);
    rightSide.setExtension(extensionRatio);
  }

  function setEarcupSwivel(swivelRatio) {
    leftSide.setSwivel(swivelRatio);
    rightSide.setSwivel(swivelRatio);
  }

  function setFold(foldRatio) {
    leftSide.setFold(foldRatio);
    rightSide.setFold(foldRatio);
  }

  function setHeadbandFlex(flexRatio) {
    const clampedFlex = clampUnitRange(flexRatio);
    const updatedAnchors = setFlex(clampedFlex);
    leftSide.updateTipAnchor(updatedAnchors.leftTipAnchor);
    rightSide.updateTipAnchor(updatedAnchors.rightTipAnchor);
  }

  function dispose() {
    disposeObjectGeometries(rootGroup);
    materials.dispose();
  }

  return {
    rootGroup,
    materials,
    applyLinearColors,
    setSliderExtension,
    setEarcupSwivel,
    setFold,
    setHeadbandFlex,
    dispose,
    parts: {
      headbandAssembly,
      leftSide,
      rightSide,
    },
  };
}
