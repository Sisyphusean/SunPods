import * as THREE from "three";

export function clampUnitRange(rawValue) {
  if (!Number.isFinite(rawValue)) {
    return 0;
  }
  return Math.min(1, Math.max(0, rawValue));
}

export function evalSuperellipse(cosVal, sinVal, halfA, halfB, exponentPower = 2 / 2.4) {
  return {
    x: Math.sign(cosVal) * Math.pow(Math.abs(cosVal), exponentPower) * halfA,
    y: Math.sign(sinVal) * Math.pow(Math.abs(sinVal), exponentPower) * halfB,
  };
}

export function createStadiumShape(lengthTotal, widthTotal, vertical = false) {
  const halfStraight = Math.max(0.001, (lengthTotal - widthTotal) / 2);
  const endRadius = widthTotal / 2;
  const shape = new THREE.Shape();

  if (vertical) {
    shape.moveTo(-endRadius, -halfStraight);
    shape.lineTo(-endRadius, halfStraight);
    shape.absarc(0, halfStraight, endRadius, Math.PI, 0, true);
    shape.lineTo(endRadius, -halfStraight);
    shape.absarc(0, -halfStraight, endRadius, 0, -Math.PI, true);
  } else {
    shape.moveTo(-halfStraight, -endRadius);
    shape.lineTo(halfStraight, -endRadius);
    shape.absarc(halfStraight, 0, endRadius, -Math.PI / 2, Math.PI / 2, false);
    shape.lineTo(-halfStraight, endRadius);
    shape.absarc(-halfStraight, 0, endRadius, Math.PI / 2, (Math.PI * 3) / 2, false);
  }
  return shape;
}

export function buildLoftedGeometry({
  ringCount,
  segmentCount,
  fillVertex,
  flipWinding = false,
  bottomCapCenter = null,
  topCapCenter = null,
}) {
  const ringStride = segmentCount + 1;
  const ringVertices = (ringCount + 1) * ringStride;
  const positions = [];
  const uvs = [];
  const indices = [];

  for (let ringIndex = 0; ringIndex <= ringCount; ringIndex += 1) {
    const vCoord = ringCount > 0 ? ringIndex / ringCount : 0;
    for (let segIndex = 0; segIndex <= segmentCount; segIndex += 1) {
      const uCoord = segIndex / segmentCount;
      fillVertex(ringIndex, segIndex, uCoord, vCoord, positions, uvs);
    }
  }

  for (let ringIndex = 0; ringIndex < ringCount; ringIndex += 1) {
    for (let segIndex = 0; segIndex < segmentCount; segIndex += 1) {
      const vertexA = ringIndex * ringStride + segIndex;
      const vertexB = (ringIndex + 1) * ringStride + segIndex;
      const vertexC = (ringIndex + 1) * ringStride + segIndex + 1;
      const vertexD = ringIndex * ringStride + segIndex + 1;

      if (flipWinding) {
        indices.push(vertexA, vertexB, vertexD);
        indices.push(vertexB, vertexC, vertexD);
      } else {
        indices.push(vertexA, vertexD, vertexB);
        indices.push(vertexB, vertexD, vertexC);
      }
    }
  }

  if (bottomCapCenter) {
    const bottomCenterIdx = ringVertices;
    positions.push(bottomCapCenter[0], bottomCapCenter[1], bottomCapCenter[2]);
    uvs.push(0.5, 0.5);
    for (let segIndex = 0; segIndex < segmentCount; segIndex += 1) {
      indices.push(bottomCenterIdx, segIndex, segIndex + 1);
    }
  }

  if (topCapCenter) {
    const topRingOffset = ringCount * ringStride;
    const topCenterIdx = positions.length / 3;
    positions.push(topCapCenter[0], topCapCenter[1], topCapCenter[2]);
    uvs.push(0.5, 0.5);
    for (let segIndex = 0; segIndex < segmentCount; segIndex += 1) {
      indices.push(topCenterIdx, topRingOffset + segIndex + 1, topRingOffset + segIndex);
    }
  }

  const geometry = new THREE.BufferGeometry();
  geometry.setIndex(indices);
  if (positions.length > 0) {
    geometry.setAttribute("position", new THREE.Float32BufferAttribute(positions, 3));
    geometry.setAttribute("uv", new THREE.Float32BufferAttribute(uvs, 2));
    geometry.computeVertexNormals();
  }
  return geometry;
}

export function disposeObjectGeometries(rootObject) {
  const uniqueGeometries = new Set();
  rootObject.traverse((descendant) => {
    if (descendant.geometry) {
      uniqueGeometries.add(descendant.geometry);
    }
  });
  for (const geometry of uniqueGeometries) {
    geometry.dispose();
  }
}
