import * as THREE from "three";
import { HEADPHONE_DIMENSIONS } from "./dimensions.js";
import { buildLoftedGeometry, createStadiumShape, evalSuperellipse } from "./geometryUtils.js";

function smoothStepRange(edgeMin, edgeMax, inputValue) {
  const clampedFraction = Math.min(1, Math.max(0, (inputValue - edgeMin) / (edgeMax - edgeMin)));
  return clampedFraction * clampedFraction * (3 - 2 * clampedFraction);
}

function weldPolarSeamNormals(surfaceGeometry, ringSteps, radialSegments) {
  const normalAttribute = surfaceGeometry.attributes.normal;
  const seamStartNormal = new THREE.Vector3();
  const seamEndNormal = new THREE.Vector3();
  const averagedNormal = new THREE.Vector3();

  // Duplicate vertices at theta = 0 and theta = 2*PI have one-sided triangle normals unless welded.
  const ringStride = radialSegments + 1;
  for (let ringIndex = 0; ringIndex <= ringSteps; ringIndex += 1) {
    const startVertexIndex = ringIndex * ringStride;
    const endVertexIndex = ringIndex * ringStride + radialSegments;

    seamStartNormal.fromBufferAttribute(normalAttribute, startVertexIndex);
    seamEndNormal.fromBufferAttribute(normalAttribute, endVertexIndex);
    averagedNormal.addVectors(seamStartNormal, seamEndNormal);

    if (averagedNormal.lengthSq() > 1e-10) {
      averagedNormal.normalize();
      normalAttribute.setXYZ(startVertexIndex, averagedNormal.x, averagedNormal.y, averagedNormal.z);
      normalAttribute.setXYZ(endVertexIndex, averagedNormal.x, averagedNormal.y, averagedNormal.z);
    }
  }
  normalAttribute.needsUpdate = true;
}

function evaluateEarcupShellPoint(radialProgress, thetaRadians, sideSign) {
  const sinTheta = Math.sin(thetaRadians);
  const cosTheta = Math.cos(thetaRadians);

  const outerRimY = HEADPHONE_DIMENSIONS.earcupHalfHeightY * sinTheta;
  // Slightly fuller lower cheek and sleeker upper temple match the ergonomic XM5 shell profile.
  const outerRimZ =
    HEADPHONE_DIMENSIONS.earcupHalfDepthZ * cosTheta * (1 - 0.055 * sinTheta);

  const plateauCenterY = HEADPHONE_DIMENSIONS.earcupPlateauOffsetY;
  const plateauRimY =
    plateauCenterY + HEADPHONE_DIMENSIONS.earcupPlateauHalfHeightY * sinTheta;
  const plateauRimZ = HEADPHONE_DIMENSIONS.earcupPlateauHalfDepthZ * cosTheta;

  const plateauBoundaryU = 0.58;
  const bevelEndU = 0.64;
  const flankEndU = 0.86;
  const plateauEdgeHeight = HEADPHONE_DIMENSIONS.earcupShellThicknessX - 0.002;
  const bevelDropHeight = plateauEdgeHeight - 0.022;
  const seamBandHeight = 0.072;

  let positionY;
  let positionZ;
  let depthOutward;

  if (radialProgress <= plateauBoundaryU) {
    const localProgress = radialProgress / plateauBoundaryU;
    positionY = THREE.MathUtils.lerp(plateauCenterY, plateauRimY, localProgress);
    positionZ = THREE.MathUtils.lerp(0, plateauRimZ, localProgress);
    // Virtually flat touch-panel plateau with subtle 0.2 mm crown for clean studio reflections.
    depthOutward =
      HEADPHONE_DIMENSIONS.earcupShellThicknessX - 0.002 * (localProgress * localProgress);
  } else if (radialProgress <= bevelEndU) {
    const bevelProgress = (radialProgress - plateauBoundaryU) / (bevelEndU - plateauBoundaryU);
    const radialFraction = bevelProgress * 0.14;
    positionY = THREE.MathUtils.lerp(plateauRimY, outerRimY, radialFraction);
    positionZ = THREE.MathUtils.lerp(plateauRimZ, outerRimZ, radialFraction);
    const smoothBevel = bevelProgress * bevelProgress * (3 - 2 * bevelProgress);
    depthOutward = THREE.MathUtils.lerp(plateauEdgeHeight, bevelDropHeight, smoothBevel);
  } else if (radialProgress <= flankEndU) {
    const flankProgress = (radialProgress - bevelEndU) / (flankEndU - bevelEndU);
    const radialFraction = 0.14 + flankProgress * 0.72;
    positionY = THREE.MathUtils.lerp(plateauRimY, outerRimY, radialFraction);
    positionZ = THREE.MathUtils.lerp(plateauRimZ, outerRimZ, radialFraction);
    depthOutward = THREE.MathUtils.lerp(
      bevelDropHeight,
      seamBandHeight,
      Math.pow(flankProgress, 1.12),
    );
  } else {
    const rimProgress = (radialProgress - flankEndU) / (1 - flankEndU);
    const radialFraction = 0.86 + rimProgress * 0.14;
    positionY = THREE.MathUtils.lerp(plateauRimY, outerRimY, radialFraction);
    positionZ = THREE.MathUtils.lerp(plateauRimZ, outerRimZ, radialFraction);
    depthOutward = seamBandHeight * Math.cos(rimProgress * (Math.PI / 2));
  }

  // Tightly tailored vertical U-pocket in the upper-outer shell cradles the yoke tongue cleanly.
  if (positionY > 0.25) {
    const verticalDistanceBelowStart = Math.max(0, 0.325 - positionY);
    const distanceFromSlotCenter = Math.hypot(verticalDistanceBelowStart, positionZ);
    const innerPocketRadius = 0.038;
    const outerLipRadius = 0.054;

    if (distanceFromSlotCenter < outerLipRadius) {
      const lipBlend = smoothStepRange(innerPocketRadius, outerLipRadius, distanceFromSlotCenter);
      const pocketFloorDepth = Math.min(depthOutward, 0.145);
      depthOutward = THREE.MathUtils.lerp(pocketFloorDepth, depthOutward, lipBlend);
    }
  }

  return new THREE.Vector3(sideSign * depthOutward, positionY, positionZ);
}

function evaluateShellSurfaceFrame(radialProgress, thetaRadians, sideSign) {
  const centerPoint = evaluateEarcupShellPoint(radialProgress, thetaRadians, sideSign);
  const deltaU = 0.004;
  const deltaTheta = 0.01;

  const uForward = evaluateEarcupShellPoint(
    Math.min(1, radialProgress + deltaU),
    thetaRadians,
    sideSign,
  );
  const thetaForward = evaluateEarcupShellPoint(
    radialProgress,
    thetaRadians + deltaTheta,
    sideSign,
  );

  const radialTangent = uForward.sub(centerPoint).normalize();
  const perimeterTangent = thetaForward.sub(centerPoint).normalize();

  const outwardNormal =
    sideSign > 0
      ? new THREE.Vector3().crossVectors(perimeterTangent, radialTangent).normalize()
      : new THREE.Vector3().crossVectors(radialTangent, perimeterTangent).normalize();

  return {
    position: centerPoint,
    normal: outwardNormal,
    perimeterTangent,
  };
}

function buildSculptedEarcupShellGeometry(sideSign) {
  const ringSteps = 56;
  const radialSegments = 160;
  const shellGeometry = buildLoftedGeometry({
    ringCount: ringSteps,
    segmentCount: radialSegments,
    flipWinding: sideSign <= 0,
    fillVertex(_ringIndex, _angleIndex, uCoord, radialProgress, positions, uvs) {
      const surfacePoint = evaluateEarcupShellPoint(radialProgress, uCoord * Math.PI * 2, sideSign);
      positions.push(surfacePoint.x, surfacePoint.y, surfacePoint.z);
      uvs.push(uCoord, radialProgress);
    },
  });
  weldPolarSeamNormals(shellGeometry, ringSteps, radialSegments);
  return shellGeometry;
}

function buildTailoredEarpadGeometry(sideSign) {
  const padProfileSpline = new THREE.SplineCurve([
    new THREE.Vector2(1.0, 0.0),
    new THREE.Vector2(1.01, 0.042),
    new THREE.Vector2(0.955, 0.145),
    new THREE.Vector2(0.76, 0.184),
    new THREE.Vector2(0.42, HEADPHONE_DIMENSIONS.earpadDepthX),
    new THREE.Vector2(0.16, 0.172),
    new THREE.Vector2(0.015, 0.092),
    new THREE.Vector2(0.03, 0.005),
  ]);

  const padSteps = 32;
  const radialSegments = 120;
  const earpadGeometry = buildLoftedGeometry({
    ringCount: padSteps,
    segmentCount: radialSegments,
    flipWinding: sideSign <= 0,
    fillVertex(_stepIndex, _angleIndex, uCoord, curveProgress, positions, uvs) {
      const samplePoint = padProfileSpline.getPointAt(curveProgress);
      const outerBlend = samplePoint.x;
      const padDepth = samplePoint.y;
      const thetaRadians = uCoord * Math.PI * 2;
      const sinTheta = Math.sin(thetaRadians);
      const cosTheta = Math.cos(thetaRadians);

      const outerY = (HEADPHONE_DIMENSIONS.earcupHalfHeightY + 0.002) * sinTheta;
      const outerZ =
        (HEADPHONE_DIMENSIONS.earcupHalfDepthZ + 0.002) * cosTheta * (1 - 0.055 * sinTheta);
      const innerPt = evalSuperellipse(
        sinTheta,
        cosTheta,
        HEADPHONE_DIMENSIONS.earpadInnerHalfHeightY,
        HEADPHONE_DIMENSIONS.earpadInnerHalfDepthZ,
      );

      positions.push(
        -sideSign * padDepth,
        THREE.MathUtils.lerp(innerPt.x, outerY, outerBlend),
        THREE.MathUtils.lerp(innerPt.y, outerZ, outerBlend),
      );
      uvs.push(uCoord * 2, curveProgress);
    },
  });
  weldPolarSeamNormals(earpadGeometry, padSteps, radialSegments);
  return earpadGeometry;
}

function orientObjectOnShellFrame(targetObject, surfaceFrame, surfaceInset = 0) {
  targetObject.position
    .copy(surfaceFrame.position)
    .addScaledVector(surfaceFrame.normal, surfaceInset);

  const zAxis = surfaceFrame.normal.clone().normalize();
  const xAxis = surfaceFrame.perimeterTangent
    .clone()
    .addScaledVector(zAxis, -surfaceFrame.perimeterTangent.dot(zAxis))
    .normalize();
  const yAxis = new THREE.Vector3().crossVectors(zAxis, xAxis).normalize();

  const basisMatrix = new THREE.Matrix4().makeBasis(xAxis, yAxis, zAxis);
  targetObject.quaternion.setFromRotationMatrix(basisMatrix);
}

function createSlimAncMicSlot(
  slotName,
  radialProgress,
  thetaRadians,
  sideSign,
  materials,
  useBronzeMesh = true,
) {
  const slotGroup = new THREE.Group();
  slotGroup.name = slotName;

  const surfaceFrame = evaluateShellSurfaceFrame(radialProgress, thetaRadians, sideSign);
  orientObjectOnShellFrame(slotGroup, surfaceFrame, 0.0004);

  const slotLength = 0.066;
  const slotWidth = 0.01;
  const bezelShape = createStadiumShape(slotLength + 0.006, slotWidth + 0.005);
  const bezelGeometry = new THREE.ExtrudeGeometry(bezelShape, {
    depth: 0.0024,
    bevelEnabled: false,
    curveSegments: 10,
  });
  bezelGeometry.translate(0, 0, -0.0014);
  const bezelMesh = new THREE.Mesh(bezelGeometry, materials.satinMetalMaterial);
  bezelMesh.name = `${slotName}Collar`;

  const slotOpeningGeometry = new THREE.ShapeGeometry(
    createStadiumShape(slotLength, slotWidth),
    10,
  );
  slotOpeningGeometry.translate(0, 0, 0.0011);
  const slotOpeningMesh = new THREE.Mesh(slotOpeningGeometry, materials.recessedCavityMaterial);
  slotOpeningMesh.name = `${slotName}Recess`;

  slotGroup.add(bezelMesh, slotOpeningMesh);

  if (useBronzeMesh) {
    // Recessed warm copper-bronze acoustic mesh inside the perimeter mic slot.
    const bronzeMeshGeometry = new THREE.ShapeGeometry(
      createStadiumShape(slotLength * 0.88, slotWidth * 0.76),
      10,
    );
    bronzeMeshGeometry.translate(0, 0, 0.0015);
    const bronzeAcousticMesh = new THREE.Mesh(bronzeMeshGeometry, materials.ancBronzeMeshMaterial);
    bronzeAcousticMesh.name = `${slotName}BronzeMesh`;
    slotGroup.add(bronzeAcousticMesh);
  }

  return slotGroup;
}

function computeLocalRimScales(radialProgress, centerTheta, sideSign) {
  const sampleDeltaTheta = 0.005;
  const pointThetaAhead = evaluateEarcupShellPoint(
    radialProgress,
    centerTheta + sampleDeltaTheta,
    sideSign,
  );
  const pointThetaBehind = evaluateEarcupShellPoint(
    radialProgress,
    centerTheta - sampleDeltaTheta,
    sideSign,
  );
  const arcScalePerRadian = pointThetaAhead.distanceTo(pointThetaBehind) / (2 * sampleDeltaTheta);

  const sampleDeltaU = 0.004;
  const clampedUpperU = Math.min(1, radialProgress + sampleDeltaU);
  const clampedLowerU = Math.max(0, radialProgress - sampleDeltaU);
  const pointOuterU = evaluateEarcupShellPoint(clampedUpperU, centerTheta, sideSign);
  const pointInnerU = evaluateEarcupShellPoint(clampedLowerU, centerTheta, sideSign);
  const radialScalePerUnitU =
    pointOuterU.distanceTo(pointInnerU) / Math.max(1e-5, clampedUpperU - clampedLowerU);

  return { arcScalePerRadian, radialScalePerUnitU };
}

function sampleInsetStadiumLoopOffsets(
  lengthTotal,
  widthTotal,
  chamferInset,
  capSegments = 10,
  straightSegments = 6,
) {
  const ringLength = Math.max(0.002, lengthTotal - 2 * chamferInset);
  const ringWidth = Math.max(0.0004, widthTotal - 2 * chamferInset);
  const halfStraight = Math.max(0.0005, (ringLength - ringWidth) / 2);
  const endRadius = ringWidth / 2;
  const loopOffsets = [];

  for (let capIndex = 0; capIndex <= capSegments; capIndex += 1) {
    const capAngle = -Math.PI / 2 + (capIndex / capSegments) * Math.PI;
    loopOffsets.push({
      alongLength: halfStraight + endRadius * Math.cos(capAngle),
      acrossWidth: endRadius * Math.sin(capAngle),
    });
  }

  for (let edgeIndex = 1; edgeIndex < straightSegments; edgeIndex += 1) {
    const edgeFraction = edgeIndex / straightSegments;
    loopOffsets.push({
      alongLength: THREE.MathUtils.lerp(halfStraight, -halfStraight, edgeFraction),
      acrossWidth: endRadius,
    });
  }

  for (let capIndex = 0; capIndex <= capSegments; capIndex += 1) {
    const capAngle = Math.PI / 2 + (capIndex / capSegments) * Math.PI;
    loopOffsets.push({
      alongLength: -halfStraight + endRadius * Math.cos(capAngle),
      acrossWidth: endRadius * Math.sin(capAngle),
    });
  }

  for (let edgeIndex = 1; edgeIndex < straightSegments; edgeIndex += 1) {
    const edgeFraction = edgeIndex / straightSegments;
    loopOffsets.push({
      alongLength: THREE.MathUtils.lerp(-halfStraight, halfStraight, edgeFraction),
      acrossWidth: -endRadius,
    });
  }

  return loopOffsets;
}

function buildCurvedRimStadiumGeometry(
  radialProgress,
  centerTheta,
  sideSign,
  lengthTotal,
  widthTotal,
  profileRingSpecs,
  inverseFrameMatrix,
) {
  const { arcScalePerRadian, radialScalePerUnitU } = computeLocalRimScales(
    radialProgress,
    centerTheta,
    sideSign,
  );
  const radialDirectionSign = sideSign > 0 ? 1 : -1;

  const capSegments = 10;
  const straightSegments = 6;
  const ringVertexCount = (capSegments + 1) * 2 + (straightSegments - 1) * 2;
  const ringCount = profileRingSpecs.length;

  const positions = [];
  const indices = [];

  for (let ringIndex = 0; ringIndex < ringCount; ringIndex += 1) {
    const ringSpec = profileRingSpecs[ringIndex];
    const loopOffsets = sampleInsetStadiumLoopOffsets(
      lengthTotal,
      widthTotal,
      ringSpec.chamferInset,
      capSegments,
      straightSegments,
    );

    for (let vertexIndex = 0; vertexIndex < ringVertexCount; vertexIndex += 1) {
      const offsetSample = loopOffsets[vertexIndex];
      const thetaSample = centerTheta + offsetSample.alongLength / arcScalePerRadian;
      const radialSample = THREE.MathUtils.clamp(
        radialProgress + (offsetSample.acrossWidth / radialScalePerUnitU) * radialDirectionSign,
        0,
        1,
      );
      const sampledFrame = evaluateShellSurfaceFrame(radialSample, thetaSample, sideSign);
      const earcupPoint = sampledFrame.position
        .clone()
        .addScaledVector(sampledFrame.normal, ringSpec.heightOffset);
      const localPoint = earcupPoint.applyMatrix4(inverseFrameMatrix);

      positions.push(localPoint.x, localPoint.y, localPoint.z);
    }
  }

  for (let ringIndex = 0; ringIndex < ringCount - 1; ringIndex += 1) {
    const currentRingStart = ringIndex * ringVertexCount;
    const nextRingStart = (ringIndex + 1) * ringVertexCount;

    for (let segmentIndex = 0; segmentIndex < ringVertexCount; segmentIndex += 1) {
      const nextSegmentIndex = (segmentIndex + 1) % ringVertexCount;
      const vertexA = currentRingStart + segmentIndex;
      const vertexB = nextRingStart + segmentIndex;
      const vertexC = nextRingStart + nextSegmentIndex;
      const vertexD = currentRingStart + nextSegmentIndex;

      indices.push(vertexA, vertexB, vertexD);
      indices.push(vertexB, vertexC, vertexD);
    }
  }

  const stadiumGeometry = new THREE.BufferGeometry();
  stadiumGeometry.setAttribute("position", new THREE.Float32BufferAttribute(positions, 3));
  stadiumGeometry.setIndex(indices);
  stadiumGeometry.computeVertexNormals();
  return stadiumGeometry;
}

function createRecessedSlimButton(
  buttonName,
  radialProgress,
  centerTheta,
  sideSign,
  buttonLength,
  buttonWidth,
  materials,
) {
  const buttonGroup = new THREE.Group();
  buttonGroup.name = buttonName;

  const centerFrame = evaluateShellSurfaceFrame(radialProgress, centerTheta, sideSign);
  orientObjectOnShellFrame(buttonGroup, centerFrame, 0);
  buttonGroup.updateMatrix();
  const inverseFrameMatrix = buttonGroup.matrix.clone().invert();

  // Tight dark recessed slot well hugs the smooth flank without a boxy outer border.
  const wellLength = buttonLength + 0.0028;
  const wellWidth = buttonWidth + 0.0026;
  const wellProfileRings = [
    { chamferInset: wellWidth * 0.48, heightOffset: 0.0005 },
    { chamferInset: 0.001, heightOffset: 0.0005 },
    { chamferInset: 0.0004, heightOffset: 0.0009 },
    { chamferInset: 0.0, heightOffset: -0.001 },
  ];
  const slotWellGeometry = buildCurvedRimStadiumGeometry(
    radialProgress,
    centerTheta,
    sideSign,
    wellLength,
    wellWidth,
    wellProfileRings,
    inverseFrameMatrix,
  );
  const slotWellMesh = new THREE.Mesh(slotWellGeometry, materials.recessedCavityMaterial);
  slotWellMesh.name = `${buttonName}Well`;

  // Flat-crowned, uniformly chamfered arc-curved pill cap rises cleanly above the recessed well.
  const capProfileRings = [
    { chamferInset: buttonWidth * 0.48, heightOffset: 0.0055 },
    { chamferInset: buttonWidth * 0.24, heightOffset: 0.0054 },
    { chamferInset: buttonWidth * 0.11, heightOffset: 0.0048 },
    { chamferInset: 0.0, heightOffset: 0.003 },
    { chamferInset: 0.0, heightOffset: 0.0003 },
  ];
  const capGeometry = buildCurvedRimStadiumGeometry(
    radialProgress,
    centerTheta,
    sideSign,
    buttonLength,
    buttonWidth,
    capProfileRings,
    inverseFrameMatrix,
  );
  const capMesh = new THREE.Mesh(capGeometry, materials.buttonMaterial);
  capMesh.name = `${buttonName}Cap`;

  buttonGroup.add(slotWellMesh, capMesh);
  return buttonGroup;
}

function buildCurvedSilkscreenStripGeometry(
  radialProgress,
  centerTheta,
  sideSign,
  stripLength,
  stripWidth,
  inverseFrameMatrix,
  surfaceOffset = 0.0018,
) {
  const { arcScalePerRadian, radialScalePerUnitU } = computeLocalRimScales(
    radialProgress,
    centerTheta,
    sideSign,
  );
  const radialDirectionSign = sideSign > 0 ? 1 : -1;

  const lengthSegments = 20;
  const widthSegments = 8;
  const positions = [];
  const normals = [];
  const uvs = [];
  const indices = [];

  for (let rowIndex = 0; rowIndex <= widthSegments; rowIndex += 1) {
    const vFraction = rowIndex / widthSegments;
    const acrossOffset = (vFraction - 0.5) * stripWidth;
    const radialSample = THREE.MathUtils.clamp(
      radialProgress + (acrossOffset / radialScalePerUnitU) * radialDirectionSign,
      0,
      1,
    );

    for (let colIndex = 0; colIndex <= lengthSegments; colIndex += 1) {
      const uFraction = colIndex / lengthSegments;
      const alongOffset = (0.5 - uFraction) * stripLength;
      const thetaSample = centerTheta + alongOffset / arcScalePerRadian;

      const sampledFrame = evaluateShellSurfaceFrame(radialSample, thetaSample, sideSign);
      const earcupPoint = sampledFrame.position
        .clone()
        .addScaledVector(sampledFrame.normal, surfaceOffset);
      const localPoint = earcupPoint.applyMatrix4(inverseFrameMatrix);
      const localNormal = sampledFrame.normal
        .clone()
        .transformDirection(inverseFrameMatrix)
        .normalize();

      positions.push(localPoint.x, localPoint.y, localPoint.z);
      normals.push(localNormal.x, localNormal.y, localNormal.z);
      uvs.push(uFraction, 1 - vFraction);
    }
  }

  const rowStride = lengthSegments + 1;
  for (let rowIndex = 0; rowIndex < widthSegments; rowIndex += 1) {
    for (let colIndex = 0; colIndex < lengthSegments; colIndex += 1) {
      const vertexA = rowIndex * rowStride + colIndex;
      const vertexB = (rowIndex + 1) * rowStride + colIndex;
      const vertexC = rowIndex * rowStride + colIndex + 1;
      const vertexD = (rowIndex + 1) * rowStride + colIndex + 1;

      indices.push(vertexA, vertexB, vertexC);
      indices.push(vertexB, vertexD, vertexC);
    }
  }

  const stripGeometry = new THREE.BufferGeometry();
  stripGeometry.setAttribute("position", new THREE.Float32BufferAttribute(positions, 3));
  stripGeometry.setAttribute("normal", new THREE.Float32BufferAttribute(normals, 3));
  stripGeometry.setAttribute("uv", new THREE.Float32BufferAttribute(uvs, 2));
  stripGeometry.setIndex(indices);
  return stripGeometry;
}

function createCurvedSilkscreenLabel(
  labelName,
  radialProgress,
  centerTheta,
  sideSign,
  stripLength,
  stripWidth,
  labelMaterial,
) {
  const centerFrame = evaluateShellSurfaceFrame(radialProgress, centerTheta, sideSign);
  const frameAnchor = new THREE.Object3D();
  orientObjectOnShellFrame(frameAnchor, centerFrame, 0);
  frameAnchor.updateMatrix();
  const inverseFrameMatrix = frameAnchor.matrix.clone().invert();

  const labelMesh = new THREE.Mesh(
    buildCurvedSilkscreenStripGeometry(
      radialProgress,
      centerTheta,
      sideSign,
      stripLength,
      stripWidth,
      inverseFrameMatrix,
      0.0018,
    ),
    labelMaterial,
  );
  labelMesh.name = labelName;
  labelMesh.position.copy(frameAnchor.position);
  labelMesh.quaternion.copy(frameAnchor.quaternion);
  return labelMesh;
}

function createAudioJack35mm(jackFrame, materials) {
  const audioJackGroup = new THREE.Group();
  audioJackGroup.name = "audioJack35mm";
  orientObjectOnShellFrame(audioJackGroup, jackFrame, 0);

  // Compact raised matte rim boss buried into the smooth flank so zero overhang occurs.
  const rimBossProfile = [
    new THREE.Vector2(0.0215, 0.0018),
    new THREE.Vector2(0.023, 0.0048),
    new THREE.Vector2(0.0242, 0.005),
    new THREE.Vector2(0.0258, 0.0025),
    new THREE.Vector2(0.027, -0.006),
  ];
  const rimBossGeometry = new THREE.LatheGeometry(rimBossProfile, 32);
  rimBossGeometry.rotateX(Math.PI / 2);
  const rimBossMesh = new THREE.Mesh(rimBossGeometry, materials.matteShellMaterial);
  rimBossMesh.name = "audioJackRimBoss";

  // Chamfered satin-metallic bushing ring (0.023 outer radius, 0.0165 bore radius).
  const bushingProfile = [
    new THREE.Vector2(0.0165, -0.002),
    new THREE.Vector2(0.0165, 0.0032),
    new THREE.Vector2(0.018, 0.005),
    new THREE.Vector2(0.0214, 0.005),
    new THREE.Vector2(0.023, 0.0032),
    new THREE.Vector2(0.023, 0.002),
  ];
  const bushingGeometry = new THREE.LatheGeometry(bushingProfile, 32);
  bushingGeometry.rotateX(Math.PI / 2);
  const bushingMesh = new THREE.Mesh(bushingGeometry, materials.satinMetalMaterial);
  bushingMesh.name = "audioJackBushingRing";

  // Shallow dark recessed socket cavity stays cleanly inside the shell flank.
  const socketBoreProfile = [
    new THREE.Vector2(0.0165, 0.0018),
    new THREE.Vector2(0.0165, -0.004),
    new THREE.Vector2(0.0, -0.004),
  ];
  const socketBoreGeometry = new THREE.LatheGeometry(socketBoreProfile, 28);
  socketBoreGeometry.rotateX(Math.PI / 2);
  const socketBoreMesh = new THREE.Mesh(socketBoreGeometry, materials.recessedCavityMaterial);
  socketBoreMesh.name = "audioJackSocketBore";

  // Subtle metallic contact sleeve ring inside the socket cavity.
  const contactSleeveProfile = [
    new THREE.Vector2(0.0164, 0.0002),
    new THREE.Vector2(0.0156, -0.0004),
    new THREE.Vector2(0.0156, -0.0024),
    new THREE.Vector2(0.0164, -0.003),
  ];
  const contactSleeveGeometry = new THREE.LatheGeometry(contactSleeveProfile, 28);
  contactSleeveGeometry.rotateX(Math.PI / 2);
  const contactSleeveMesh = new THREE.Mesh(contactSleeveGeometry, materials.satinMetalMaterial);
  contactSleeveMesh.name = "audioJackContactSleeve";

  audioJackGroup.add(rimBossMesh, bushingMesh, socketBoreMesh, contactSleeveMesh);
  return audioJackGroup;
}

function createStatusLed(ledFrame, materials) {
  const statusLedGroup = new THREE.Group();
  statusLedGroup.name = "statusLed";
  orientObjectOnShellFrame(statusLedGroup, ledFrame, 0);

  const pinholeProfile = [
    new THREE.Vector2(0.0028, -0.0015),
    new THREE.Vector2(0.0028, 0.0008),
    new THREE.Vector2(0.004, 0.0014),
    new THREE.Vector2(0.0052, 0.0014),
    new THREE.Vector2(0.0064, 0.0002),
    new THREE.Vector2(0.0072, -0.0016),
  ];
  const pinholeRingGeometry = new THREE.LatheGeometry(pinholeProfile, 24);
  pinholeRingGeometry.rotateX(Math.PI / 2);
  const pinholeRingMesh = new THREE.Mesh(pinholeRingGeometry, materials.recessedCavityMaterial);
  pinholeRingMesh.name = "statusLedPinhole";

  const ledCoreGeometry = new THREE.CylinderGeometry(0.0027, 0.0027, 0.0016, 16);
  ledCoreGeometry.rotateX(Math.PI / 2);
  const ledCoreMesh = new THREE.Mesh(ledCoreGeometry, materials.statusLedMaterial);
  ledCoreMesh.position.z = 0.0002;
  ledCoreMesh.name = "statusLedCore";

  statusLedGroup.add(pinholeRingMesh, ledCoreMesh);
  return statusLedGroup;
}

function createSideControlsAndPorts(sideSign, materials) {
  const hardwareElements = [];
  const flankHardwareU = 0.815;
  const silkscreenLabelU = 0.905;

  if (sideSign > 0) {
    // Exact left rim order along the smooth flank seam: 3.5mm jack -> Power -> pinhole LED -> NC/AMB.
    const jackFrame = evaluateShellSurfaceFrame(flankHardwareU, -Math.PI * 0.46, sideSign);
    hardwareElements.push(createAudioJack35mm(jackFrame, materials));

    const powerTheta = -Math.PI * 0.58;
    hardwareElements.push(
      createRecessedSlimButton(
        "powerButton",
        flankHardwareU,
        powerTheta,
        sideSign,
        0.054,
        0.014,
        materials,
      ),
    );
    hardwareElements.push(
      createCurvedSilkscreenLabel(
        "powerSilkscreenLabel",
        silkscreenLabelU,
        powerTheta,
        sideSign,
        0.052,
        0.017,
        materials.powerLabelMaterial,
      ),
    );

    const ledFrame = evaluateShellSurfaceFrame(flankHardwareU, -Math.PI * 0.65, sideSign);
    hardwareElements.push(createStatusLed(ledFrame, materials));

    const ncAmbTheta = -Math.PI * 0.74;
    hardwareElements.push(
      createRecessedSlimButton(
        "ncAmbButton",
        flankHardwareU,
        ncAmbTheta,
        sideSign,
        0.088,
        0.014,
        materials,
      ),
    );
    hardwareElements.push(
      createCurvedSilkscreenLabel(
        "ncAmbSilkscreenLabel",
        silkscreenLabelU,
        ncAmbTheta,
        sideSign,
        0.064,
        0.02,
        materials.ncAmbLabelMaterial,
      ),
    );
  } else {
    // Right cup: bright metallic-lined USB Type-C port + pinpoint charging LED along the seam.
    const usbFrame = evaluateShellSurfaceFrame(flankHardwareU, -Math.PI * 0.56, sideSign);
    const usbCPortGroup = new THREE.Group();
    usbCPortGroup.name = "usbCPort";

    const usbCollarGeometry = new THREE.ExtrudeGeometry(createStadiumShape(0.052, 0.02), {
      depth: 0.005,
      bevelEnabled: true,
      bevelThickness: 0.0015,
      bevelSize: 0.0015,
      bevelSegments: 2,
      curveSegments: 10,
    });
    const usbCollarMesh = new THREE.Mesh(usbCollarGeometry, materials.usbMetalLinerMaterial);

    const usbCavityGeometry = new THREE.ShapeGeometry(createStadiumShape(0.042, 0.012), 10);
    usbCavityGeometry.translate(0, 0, 0.0067);
    const usbCavityMesh = new THREE.Mesh(usbCavityGeometry, materials.recessedCavityMaterial);

    const usbTongueGeometry = new THREE.BoxGeometry(0.028, 0.0035, 0.005);
    const usbTongueMesh = new THREE.Mesh(usbTongueGeometry, materials.usbMetalLinerMaterial);
    usbTongueMesh.position.z = 0.0052;

    usbCPortGroup.add(usbCollarMesh, usbCavityMesh, usbTongueMesh);
    orientObjectOnShellFrame(usbCPortGroup, usbFrame, -0.001);
    hardwareElements.push(usbCPortGroup);

    const chargeLedFrame = evaluateShellSurfaceFrame(flankHardwareU, -Math.PI * 0.64, sideSign);
    const chargingLedGeometry = new THREE.CylinderGeometry(0.0044, 0.0044, 0.005, 16);
    chargingLedGeometry.rotateX(Math.PI / 2);
    const chargingLedMesh = new THREE.Mesh(chargingLedGeometry, materials.chargingLedMaterial);
    chargingLedMesh.name = "chargingLed";
    orientObjectOnShellFrame(chargingLedMesh, chargeLedFrame, 0.0);
    hardwareElements.push(chargingLedMesh);
  }

  return hardwareElements;
}

export function createEarcupAssembly(sideSign, materials) {
  const sideLabel = sideSign > 0 ? "left" : "right";
  const earcupGroup = new THREE.Group();
  earcupGroup.name = `${sideLabel}EarcupAssembly`;

  const shellGeometry = buildSculptedEarcupShellGeometry(sideSign);
  const earcupShell = new THREE.Mesh(shellGeometry, materials.matteShellMaterial);
  earcupShell.name = "earcupShell";

  const earpadGeometry = buildTailoredEarpadGeometry(sideSign);
  const earpadCushion = new THREE.Mesh(earpadGeometry, materials.leatheretteMaterial);
  earpadCushion.name = "earpadCushion";

  // Subtle hairline parting seam separating earcup shell from tailored leatherette pad.
  const seamPoints = [];
  const seamSegments = 96;
  for (let segmentIndex = 0; segmentIndex < seamSegments; segmentIndex += 1) {
    const thetaRadians = (segmentIndex / seamSegments) * Math.PI * 2;
    const sinTheta = Math.sin(thetaRadians);
    const cosTheta = Math.cos(thetaRadians);
    seamPoints.push(
      new THREE.Vector3(
        -sideSign * 0.001,
        HEADPHONE_DIMENSIONS.earcupHalfHeightY * sinTheta,
        HEADPHONE_DIMENSIONS.earcupHalfDepthZ * cosTheta * (1 - 0.055 * sinTheta),
      ),
    );
  }
  const seamCurve = new THREE.CatmullRomCurve3(seamPoints, true);
  const seamGeometry = new THREE.TubeGeometry(seamCurve, 96, 0.0012, 8, true);
  const seamMesh = new THREE.Mesh(seamGeometry, materials.seamMaterial);
  seamMesh.name = `${sideLabel}PadSeamHairline`;

  // Ultra-fine circumferential mid-housing parting hairline passing through ports and buttons.
  const midHousingSeamPoints = [];
  for (let segmentIndex = 0; segmentIndex < seamSegments; segmentIndex += 1) {
    const thetaRadians = (segmentIndex / seamSegments) * Math.PI * 2;
    const midPoint = evaluateEarcupShellPoint(0.815, thetaRadians, sideSign);
    midPoint.x -= sideSign * 0.0005;
    midHousingSeamPoints.push(midPoint);
  }
  const midHousingCurve = new THREE.CatmullRomCurve3(midHousingSeamPoints, true);
  const midHousingSeamGeometry = new THREE.TubeGeometry(midHousingCurve, 96, 0.0009, 8, true);
  const midHousingSeamMesh = new THREE.Mesh(
    midHousingSeamGeometry,
    materials.satinMetalMaterial,
  );
  midHousingSeamMesh.name = `${sideLabel}MidHousingSeam`;

  // Dark inner backing plate keeps the cavity watertight behind the woven acoustic scrim.
  const innerBaffleGeometry = new THREE.CircleGeometry(0.42, 48);
  innerBaffleGeometry.rotateY(-sideSign * (Math.PI / 2));
  innerBaffleGeometry.scale(1, 0.96, 0.72);
  const innerBaffleMesh = new THREE.Mesh(innerBaffleGeometry, materials.recessedCavityMaterial);
  innerBaffleMesh.position.x = -sideSign * 0.008;
  innerBaffleMesh.name = `${sideLabel}InnerBaffle`;

  // Clean recessed acoustic woven fabric scrim (real XM5 has no L/R letters inside the cups).
  const scrimGeometry = new THREE.CircleGeometry(0.36, 48);
  scrimGeometry.rotateY(-sideSign * (Math.PI / 2));
  scrimGeometry.scale(1, 0.94, 0.68);
  const acousticScrim = new THREE.Mesh(scrimGeometry, materials.acousticScrimMaterial);
  acousticScrim.position.x = -sideSign * 0.018;
  acousticScrim.name = "acousticScrim";

  const upperRearSlot = createSlimAncMicSlot(
    "ancMicSlotUpperRear",
    0.86,
    Math.PI * 0.66,
    sideSign,
    materials,
    false,
  );
  const frontLowerSlot = createSlimAncMicSlot(
    "ancMicSlotFrontLower",
    0.86,
    -Math.PI * 0.08,
    sideSign,
    materials,
    true,
  );
  const frontBottomSlot = createSlimAncMicSlot(
    "ancMicSlotFrontBottom",
    0.86,
    -Math.PI * 0.24,
    sideSign,
    materials,
    true,
  );
  const rearLowerSlot = createSlimAncMicSlot(
    "ancMicSlotRearLower",
    0.86,
    -Math.PI * 0.85,
    sideSign,
    materials,
    true,
  );

  const sideControlMeshes = createSideControlsAndPorts(sideSign, materials);

  earcupGroup.add(
    earcupShell,
    earpadCushion,
    seamMesh,
    midHousingSeamMesh,
    innerBaffleMesh,
    acousticScrim,
    upperRearSlot,
    frontLowerSlot,
    frontBottomSlot,
    rearLowerSlot,
    ...sideControlMeshes,
  );

  return {
    earcupGroup,
    earcupShell,
    earpadCushion,
    acousticScrim,
  };
}
