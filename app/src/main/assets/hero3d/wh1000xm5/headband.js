import * as THREE from "three";
import { HEADPHONE_DIMENSIONS, clampUnitRange } from "./dimensions.js";
import { buildLoftedGeometry, createStadiumShape, evalSuperellipse } from "./geometryUtils.js";

function createHeadbandCrossSectionProfile(segmentSteps, radialSteps) {
  const vertexCount = (segmentSteps + 1) * (radialSteps + 1);
  const radialOffsets = new Float32Array(vertexCount);
  const depthCoordinates = new Float32Array(vertexCount);

  let vertexIndex = 0;
  for (let pathStepIndex = 0; pathStepIndex <= segmentSteps; pathStepIndex += 1) {
    const curveParam = pathStepIndex / segmentSteps;
    const crownWeight = Math.sin(curveParam * Math.PI);
    const smoothWeight = Math.pow(crownWeight, 0.75);
    const depthHalfSize = THREE.MathUtils.lerp(
      HEADPHONE_DIMENSIONS.headbandTipHalfWidthZ,
      HEADPHONE_DIMENSIONS.headbandCrownHalfWidthZ,
      smoothWeight,
    );
    const radialHalfSize = THREE.MathUtils.lerp(
      HEADPHONE_DIMENSIONS.headbandTipHalfThicknessR,
      HEADPHONE_DIMENSIONS.headbandCrownHalfThicknessR,
      smoothWeight,
    );

    for (let ringStepIndex = 0; ringStepIndex <= radialSteps; ringStepIndex += 1) {
      const angleRadians = (ringStepIndex / radialSteps) * Math.PI * 2;
      const pt = evalSuperellipse(Math.cos(angleRadians), Math.sin(angleRadians), radialHalfSize, depthHalfSize);
      // Blend plush underside crown padding directly into the single unified headband surface.
      const innerSideWeight = Math.max(0, -Math.cos(angleRadians));
      const unifiedCushionBulge = 0.016 * smoothWeight * Math.pow(innerSideWeight, 1.2);

      radialOffsets[vertexIndex] = pt.x - unifiedCushionBulge;
      depthCoordinates[vertexIndex] = pt.y;
      vertexIndex += 1;
    }
  }

  return { radialOffsets, depthCoordinates };
}

function updateHeadbandBandPositions(
  archCurve,
  segmentSteps,
  radialSteps,
  bandGeometry,
  crossSectionProfile,
  scratchVectors,
) {
  const positionAttribute = bandGeometry.getAttribute("position");
  const { radialOffsets, depthCoordinates } = crossSectionProfile;
  const {
    centerPointScratch,
    tangentScratch,
    outwardRadialScratch,
    vertexPositionScratch,
    depthAxisVector,
  } = scratchVectors;

  let vertexIndex = 0;
  for (let pathStepIndex = 0; pathStepIndex <= segmentSteps; pathStepIndex += 1) {
    const curveParam = pathStepIndex / segmentSteps;
    archCurve.getPointAt(curveParam, centerPointScratch);
    archCurve.getTangentAt(curveParam, tangentScratch).normalize();
    outwardRadialScratch.set(-tangentScratch.y, tangentScratch.x, 0).normalize();

    for (let ringStepIndex = 0; ringStepIndex <= radialSteps; ringStepIndex += 1) {
      vertexPositionScratch
        .copy(centerPointScratch)
        .addScaledVector(outwardRadialScratch, radialOffsets[vertexIndex])
        .addScaledVector(depthAxisVector, depthCoordinates[vertexIndex]);

      positionAttribute.setXYZ(
        vertexIndex,
        vertexPositionScratch.x,
        vertexPositionScratch.y,
        vertexPositionScratch.z,
      );
      vertexIndex += 1;
    }
  }

  positionAttribute.needsUpdate = true;
  bandGeometry.computeVertexNormals();
  bandGeometry.computeBoundingSphere();
}

function buildHeadbandBandGeometry(
  archCurve,
  segmentSteps,
  radialSteps,
  crossSectionProfile,
  scratchVectors,
) {
  const vertexCount = (segmentSteps + 1) * (radialSteps + 1);
  const placeholderPositions = new Float32Array(vertexCount * 3);
  const placeholderUvs = new Float32Array(vertexCount * 2);
  let vertexIndex = 0;

  const bandGeometry = buildLoftedGeometry({
    ringCount: segmentSteps,
    segmentCount: radialSteps,
    flipWinding: false,
    fillVertex(_r, _s, uCoord, vCoord) {
      placeholderUvs[vertexIndex * 2] = uCoord * 4;
      placeholderUvs[vertexIndex * 2 + 1] = vCoord * 8;
      vertexIndex += 1;
    },
  });
  bandGeometry.setAttribute("position", new THREE.BufferAttribute(placeholderPositions, 3));
  bandGeometry.setAttribute("uv", new THREE.BufferAttribute(placeholderUvs, 2));

  updateHeadbandBandPositions(
    archCurve,
    segmentSteps,
    radialSteps,
    bandGeometry,
    crossSectionProfile,
    scratchVectors,
  );
  return bandGeometry;
}

function updateFlexedControlPoints(flexedControlPoints, flexRatio) {
  const baseControlPoints = HEADPHONE_DIMENSIONS.headbandArchControlPoints;
  const crownPointIndex = Math.floor(baseControlPoints.length / 2);
  const crownY = baseControlPoints[crownPointIndex].y;
  const maxTipX = Math.abs(baseControlPoints[0].x);
  const verticalScale = 1 - 0.55 * flexRatio;

  for (let pointIndex = 0; pointIndex < baseControlPoints.length; pointIndex += 1) {
    const basePoint = baseControlPoints[pointIndex];
    const normalizedFromCrown = maxTipX > 0 ? Math.abs(basePoint.x) / maxTipX : 0;
    const outwardExpansion =
      0.42 * flexRatio * (0.35 + 0.65 * Math.pow(normalizedFromCrown, 0.8));

    flexedControlPoints[pointIndex].set(
      basePoint.x * (1 + outwardExpansion),
      crownY + (basePoint.y - crownY) * verticalScale,
      basePoint.z,
    );
  }
}

function createHeadbandEndCap(tipPosition, downwardTangent, sideSign, materials) {
  const sideLabel = sideSign > 0 ? "left" : "right";
  const endCapGroup = new THREE.Group();
  endCapGroup.name = `${sideLabel}HeadbandEndCap`;
  endCapGroup.position.copy(tipPosition);
  endCapGroup.quaternion.setFromUnitVectors(new THREE.Vector3(0, -1, 0), downwardTangent);

  const domeGeometry = new THREE.SphereGeometry(1, 32, 24);
  domeGeometry.scale(
    HEADPHONE_DIMENSIONS.headbandTipHalfThicknessR * 0.99,
    0.044,
    HEADPHONE_DIMENSIONS.headbandTipHalfWidthZ * 0.99,
  );
  const domeMesh = new THREE.Mesh(domeGeometry, materials.leatheretteMaterial);
  domeMesh.name = `${sideLabel}TipDome`;

  const exitSocketGeometry = new THREE.CylinderGeometry(
    HEADPHONE_DIMENSIONS.sliderRadius * 1.24,
    HEADPHONE_DIMENSIONS.sliderRadius * 1.16,
    0.016,
    28,
  );
  const exitSocketMesh = new THREE.Mesh(exitSocketGeometry, materials.seamMaterial);
  exitSocketMesh.position.y = -0.036;
  exitSocketMesh.name = `${sideLabel}SliderSocket`;

  const apertureLipGeometry = new THREE.TorusGeometry(
    HEADPHONE_DIMENSIONS.sliderRadius * 1.18,
    0.0032,
    10,
    28,
  );
  apertureLipGeometry.rotateX(Math.PI / 2);
  const apertureLipMesh = new THREE.Mesh(apertureLipGeometry, materials.satinMetalMaterial);
  apertureLipMesh.position.y = -0.043;
  apertureLipMesh.name = `${sideLabel}SliderApertureLip`;

  const cuffPlateGeometry = new THREE.ExtrudeGeometry(createStadiumShape(0.115, 0.074, true), {
    depth: 0.008,
    bevelEnabled: true,
    bevelThickness: 0.0025,
    bevelSize: 0.0025,
    bevelSegments: 2,
    curveSegments: 14,
  });
  cuffPlateGeometry.rotateY(-sideSign * (Math.PI / 2));
  const innerCuffInsertMesh = new THREE.Mesh(cuffPlateGeometry, materials.matteShellMaterial);
  innerCuffInsertMesh.position.set(
    -sideSign * (HEADPHONE_DIMENSIONS.headbandTipHalfThicknessR - 0.0055),
    0.048,
    0,
  );
  innerCuffInsertMesh.name = `${sideLabel}InnerCuffInsert`;

  const cuffLipGeometry = new THREE.ExtrudeGeometry(createStadiumShape(0.121, 0.08, true), {
    depth: 0.005,
    bevelEnabled: false,
    curveSegments: 14,
  });
  cuffLipGeometry.rotateY(-sideSign * (Math.PI / 2));
  const cuffLipMesh = new THREE.Mesh(cuffLipGeometry, materials.seamMaterial);
  cuffLipMesh.position.set(
    -sideSign * (HEADPHONE_DIMENSIONS.headbandTipHalfThicknessR - 0.004),
    0.048,
    0,
  );
  cuffLipMesh.name = `${sideLabel}InnerCuffLip`;

  const badgeGeometry = new THREE.CircleGeometry(0.0195, 32);
  badgeGeometry.rotateY(-sideSign * (Math.PI / 2));
  const badgeMaterial =
    sideSign > 0 ? materials.leftBadgeMaterial : materials.rightBadgeMaterial;
  const channelBadgeMesh = new THREE.Mesh(badgeGeometry, badgeMaterial);
  channelBadgeMesh.position.set(
    -sideSign * (HEADPHONE_DIMENSIONS.headbandTipHalfThicknessR + 0.0072),
    0.044,
    0,
  );
  channelBadgeMesh.name = `${sideLabel}ChannelBadge`;

  endCapGroup.add(
    domeMesh,
    exitSocketMesh,
    apertureLipMesh,
    cuffLipMesh,
    innerCuffInsertMesh,
    channelBadgeMesh,
  );

  const cuffSeamPoints = [];
  const cuffSegments = 48;
  for (let segmentIndex = 0; segmentIndex < cuffSegments; segmentIndex += 1) {
    const angleRadians = (segmentIndex / cuffSegments) * Math.PI * 2;
    const pt = evalSuperellipse(
      Math.cos(angleRadians),
      Math.sin(angleRadians),
      HEADPHONE_DIMENSIONS.headbandTipHalfThicknessR * 1.005,
      HEADPHONE_DIMENSIONS.headbandTipHalfWidthZ * 1.005,
    );
    cuffSeamPoints.push(new THREE.Vector3(pt.x, 0.11, pt.y));
  }
  const cuffSeamCurve = new THREE.CatmullRomCurve3(cuffSeamPoints, true);
  const cuffSeamGeometry = new THREE.TubeGeometry(cuffSeamCurve, 48, 0.0018, 8, true);
  const cuffSeamMesh = new THREE.Mesh(cuffSeamGeometry, materials.seamMaterial);
  cuffSeamMesh.name = `${sideLabel}CuffSeam`;
  endCapGroup.add(cuffSeamMesh);

  if (sideSign > 0) {
    const tactileDotGeometry = new THREE.SphereGeometry(0.0048, 16, 12);
    const tactileDotMesh = new THREE.Mesh(tactileDotGeometry, materials.matteShellMaterial);
    tactileDotMesh.position.set(
      -HEADPHONE_DIMENSIONS.headbandTipHalfThicknessR - 0.0058,
      0.012,
      0,
    );
    tactileDotMesh.name = "tactileDot";
    endCapGroup.add(tactileDotMesh);
  }

  return endCapGroup;
}

export function createHeadbandAssembly(materials) {
  const headbandAssembly = new THREE.Group();
  headbandAssembly.name = "headbandAssembly";

  const segmentSteps = 96;
  const radialSteps = 32;
  const flexedControlPoints = HEADPHONE_DIMENSIONS.headbandArchControlPoints.map((controlPoint) =>
    controlPoint.clone(),
  );
  const archCurve = new THREE.CatmullRomCurve3(
    flexedControlPoints,
    false,
    "centripetal",
    0.5,
  );

  const crossSectionProfile = createHeadbandCrossSectionProfile(segmentSteps, radialSteps);
  const scratchVectors = {
    centerPointScratch: new THREE.Vector3(),
    tangentScratch: new THREE.Vector3(),
    outwardRadialScratch: new THREE.Vector3(),
    vertexPositionScratch: new THREE.Vector3(),
    depthAxisVector: new THREE.Vector3(0, 0, 1),
  };
  const defaultDownwardAxis = new THREE.Vector3(0, -1, 0);

  const headbandBandGeometry = buildHeadbandBandGeometry(
    archCurve,
    segmentSteps,
    radialSteps,
    crossSectionProfile,
    scratchVectors,
  );
  const headbandBandMesh = new THREE.Mesh(headbandBandGeometry, materials.leatheretteMaterial);
  headbandBandMesh.name = "headbandLeatherOuter";

  const rightTipPosition = archCurve.getPointAt(0);
  const rightDownwardTangent = archCurve.getTangentAt(0).negate().normalize();

  const leftTipPosition = archCurve.getPointAt(1);
  const leftDownwardTangent = archCurve.getTangentAt(1).normalize();

  const rightEndCap = createHeadbandEndCap(
    rightTipPosition,
    rightDownwardTangent,
    -1,
    materials,
  );
  const leftEndCap = createHeadbandEndCap(
    leftTipPosition,
    leftDownwardTangent,
    1,
    materials,
  );

  headbandAssembly.add(headbandBandMesh, rightEndCap, leftEndCap);

  const leftTipAnchor = {
    position: leftTipPosition,
    downwardTangent: leftDownwardTangent,
  };
  const rightTipAnchor = {
    position: rightTipPosition,
    downwardTangent: rightDownwardTangent,
  };
  const tipAnchors = {
    leftTipAnchor,
    rightTipAnchor,
  };

  let currentFlex = 0;
  function setFlex(flexRatio) {
    const clampedFlex = clampUnitRange(flexRatio);
    if (Math.abs(clampedFlex - currentFlex) < 1e-6) {
      return tipAnchors;
    }
    currentFlex = clampedFlex;
    updateFlexedControlPoints(flexedControlPoints, clampedFlex);
    archCurve.updateArcLengths();

    updateHeadbandBandPositions(
      archCurve,
      segmentSteps,
      radialSteps,
      headbandBandGeometry,
      crossSectionProfile,
      scratchVectors,
    );

    archCurve.getPointAt(0, rightTipPosition);
    archCurve.getTangentAt(0, rightDownwardTangent).negate().normalize();
    rightEndCap.position.copy(rightTipPosition);
    rightEndCap.quaternion.setFromUnitVectors(defaultDownwardAxis, rightDownwardTangent);

    archCurve.getPointAt(1, leftTipPosition);
    archCurve.getTangentAt(1, leftDownwardTangent).normalize();
    leftEndCap.position.copy(leftTipPosition);
    leftEndCap.quaternion.setFromUnitVectors(defaultDownwardAxis, leftDownwardTangent);

    return tipAnchors;
  }

  return {
    headbandAssembly,
    leftTipAnchor,
    rightTipAnchor,
    setFlex,
  };
}
