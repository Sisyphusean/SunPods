import * as THREE from "three";

function deterministicNoise(seedX, seedY) {
  const rawSine = Math.sin(seedX * 127.1 + seedY * 311.7) * 43758.5453123;
  return rawSine - Math.floor(rawSine);
}

function createConfiguredCanvasTexture(
  widthPixels,
  heightPixels,
  drawCallback,
  { colorSpace = THREE.NoColorSpace, repeat = null, maxAnisotropy = 0 } = {},
) {
  const canvas = document.createElement("canvas");
  canvas.width = widthPixels;
  canvas.height = heightPixels;
  drawCallback(canvas.getContext("2d"), widthPixels, heightPixels);

  const texture = new THREE.CanvasTexture(canvas);
  texture.colorSpace = colorSpace;
  if (repeat) {
    texture.wrapS = THREE.RepeatWrapping;
    texture.wrapT = THREE.RepeatWrapping;
    texture.repeat.set(repeat[0], repeat[1]);
  }
  if (maxAnisotropy > 0) {
    texture.anisotropy = maxAnisotropy;
  }
  return texture;
}

function forEachWeaveCell(textureSize, threadPitch, callback) {
  for (let gridY = 0; gridY < textureSize; gridY += threadPitch) {
    for (let gridX = 0; gridX < textureSize; gridX += threadPitch) {
      const checkerOdd = (gridX / threadPitch + gridY / threadPitch) % 2 === 0;
      callback(gridX, gridY, threadPitch, checkerOdd);
    }
  }
}

function forEachFeltFleck(textureSize, fleckCount, callback) {
  let seedValue = 1337;
  for (let fleckIndex = 0; fleckIndex < fleckCount; fleckIndex += 1) {
    seedValue = (seedValue * 16807) % 2147483647;
    const coordX = seedValue % textureSize;
    seedValue = (seedValue * 16807) % 2147483647;
    const coordY = seedValue % textureSize;
    seedValue = (seedValue * 16807) % 2147483647;
    const fleckWidth = 1 + (seedValue % 3);
    callback(fleckIndex, coordX, coordY, fleckWidth);
  }
}

export function createLeatheretteGrainTextures(maxAnisotropy = 4) {
  const textureSize = 256;
  const bumpCanvas = document.createElement("canvas");
  const roughnessCanvas = document.createElement("canvas");
  bumpCanvas.width = roughnessCanvas.width = textureSize;
  bumpCanvas.height = roughnessCanvas.height = textureSize;

  const bumpContext = bumpCanvas.getContext("2d");
  const roughnessContext = roughnessCanvas.getContext("2d");
  const bumpImageData = bumpContext.createImageData(textureSize, textureSize);
  const roughnessImageData = roughnessContext.createImageData(textureSize, textureSize);

  // Subtle soft-fit synthetic leather grain avoids shiny plastic appearance.
  for (let pixelY = 0; pixelY < textureSize; pixelY += 1) {
    for (let pixelX = 0; pixelX < textureSize; pixelX += 1) {
      const cellX = Math.floor(pixelX / 4);
      const cellY = Math.floor(pixelY / 4);
      const localX = (pixelX % 4) / 4 - 0.5;
      const localY = (pixelY % 4) / 4 - 0.5;
      const cellJitterX = (deterministicNoise(cellX, cellY) - 0.5) * 0.4;
      const cellJitterY = (deterministicNoise(cellX + 19, cellY + 37) - 0.5) * 0.4;
      const pebbleDome = Math.max(0, 1 - Math.hypot(localX - cellJitterX, localY - cellJitterY) * 1.9);
      const heightValue = Math.min(
        1,
        Math.max(0, 0.42 + pebbleDome * 0.45 + deterministicNoise(pixelX, pixelY) * 0.1),
      );

      const bumpByte = Math.round(heightValue * 255);
      const roughnessByte = Math.round((0.68 + (1 - heightValue) * 0.2) * 255);
      const byteIndex = (pixelY * textureSize + pixelX) * 4;

      bumpImageData.data[byteIndex] = bumpByte;
      bumpImageData.data[byteIndex + 1] = bumpByte;
      bumpImageData.data[byteIndex + 2] = bumpByte;
      bumpImageData.data[byteIndex + 3] = 255;

      roughnessImageData.data[byteIndex] = roughnessByte;
      roughnessImageData.data[byteIndex + 1] = roughnessByte;
      roughnessImageData.data[byteIndex + 2] = roughnessByte;
      roughnessImageData.data[byteIndex + 3] = 255;
    }
  }

  bumpContext.putImageData(bumpImageData, 0, 0);
  roughnessContext.putImageData(roughnessImageData, 0, 0);

  const bumpTexture = new THREE.CanvasTexture(bumpCanvas);
  const roughnessTexture = new THREE.CanvasTexture(roughnessCanvas);
  for (const scalarTexture of [bumpTexture, roughnessTexture]) {
    scalarTexture.colorSpace = THREE.NoColorSpace;
    scalarTexture.wrapS = THREE.RepeatWrapping;
    scalarTexture.wrapT = THREE.RepeatWrapping;
    scalarTexture.repeat.set(8, 8);
    scalarTexture.anisotropy = maxAnisotropy;
  }
  return { bumpTexture, roughnessTexture };
}

export function createAcousticMeshTextures(maxAnisotropy = 4) {
  const textureSize = 256;
  const colorCanvas = document.createElement("canvas");
  const bumpCanvas = document.createElement("canvas");
  colorCanvas.width = bumpCanvas.width = textureSize;
  colorCanvas.height = bumpCanvas.height = textureSize;

  const colorContext = colorCanvas.getContext("2d");
  const bumpContext = bumpCanvas.getContext("2d");
  const colorImageData = colorContext.createImageData(textureSize, textureSize);
  const bumpImageData = bumpContext.createImageData(textureSize, textureSize);

  for (let pixelY = 0; pixelY < textureSize; pixelY += 1) {
    for (let pixelX = 0; pixelX < textureSize; pixelX += 1) {
      const weaveHeight =
        0.5 + 0.25 * Math.sin((pixelX / 5) * Math.PI * 2) + 0.25 * Math.sin((pixelY / 5) * Math.PI * 2);
      const threadShade = Math.round(180 + weaveHeight * 75);
      const bumpShade = Math.round(weaveHeight * 255);
      const byteIndex = (pixelY * textureSize + pixelX) * 4;

      colorImageData.data[byteIndex] = threadShade;
      colorImageData.data[byteIndex + 1] = threadShade;
      colorImageData.data[byteIndex + 2] = Math.min(255, threadShade + 4);
      colorImageData.data[byteIndex + 3] = 255;

      bumpImageData.data[byteIndex] = bumpShade;
      bumpImageData.data[byteIndex + 1] = bumpShade;
      bumpImageData.data[byteIndex + 2] = bumpShade;
      bumpImageData.data[byteIndex + 3] = 255;
    }
  }

  colorContext.putImageData(colorImageData, 0, 0);
  bumpContext.putImageData(bumpImageData, 0, 0);

  const colorTexture = new THREE.CanvasTexture(colorCanvas);
  const bumpTexture = new THREE.CanvasTexture(bumpCanvas);
  colorTexture.colorSpace = THREE.SRGBColorSpace;
  bumpTexture.colorSpace = THREE.NoColorSpace;

  for (const fabricTexture of [colorTexture, bumpTexture]) {
    fabricTexture.wrapS = THREE.RepeatWrapping;
    fabricTexture.wrapT = THREE.RepeatWrapping;
    fabricTexture.repeat.set(12, 16);
    fabricTexture.anisotropy = maxAnisotropy;
  }
  return { colorTexture, bumpTexture };
}

export function createWovenFabricScalarTextures() {
  const bumpTexture = createConfiguredCanvasTexture(
    256,
    256,
    (ctx, size) => {
      ctx.fillStyle = "#7a7a7a";
      ctx.fillRect(0, 0, size, size);
      forEachWeaveCell(size, 8, (gx, gy, pitch, odd) => {
        ctx.fillStyle = odd ? "#b6b6b6" : "#969696";
        ctx.fillRect(gx + 1, gy + 1, pitch - 2, pitch - 2);
      });
    },
    { colorSpace: THREE.NoColorSpace, repeat: [7, 7] },
  );

  const roughnessTexture = createConfiguredCanvasTexture(
    256,
    256,
    (ctx, size) => {
      ctx.fillStyle = "#e2e2e2";
      ctx.fillRect(0, 0, size, size);
      forEachWeaveCell(size, 8, (gx, gy, pitch, odd) => {
        ctx.fillStyle = odd ? "#d8d8d8" : "#ececec";
        ctx.fillRect(gx + 1, gy + 1, pitch - 2, pitch - 2);
      });
    },
    { colorSpace: THREE.NoColorSpace, repeat: [7, 7] },
  );

  return { bumpTexture, roughnessTexture };
}

export function createWovenFabricColorTexture(baseColorHex, threadColorHex) {
  return createConfiguredCanvasTexture(
    256,
    256,
    (ctx, size) => {
      ctx.fillStyle = baseColorHex;
      ctx.fillRect(0, 0, size, size);
      forEachWeaveCell(size, 8, (gx, gy, pitch, odd) => {
        ctx.fillStyle = threadColorHex;
        ctx.globalAlpha = odd ? 0.34 : 0.16;
        ctx.fillRect(gx + 1, gy + 1, odd ? pitch - 2 : 3, odd ? 3 : pitch - 2);
      });
      ctx.globalAlpha = 1;
    },
    { colorSpace: THREE.SRGBColorSpace, repeat: [7, 7] },
  );
}

export function createFeltScalarTexture() {
  return createConfiguredCanvasTexture(
    256,
    256,
    (ctx, size) => {
      ctx.fillStyle = "#808080";
      ctx.fillRect(0, 0, size, size);
      forEachFeltFleck(size, 2200, (idx, cx, cy, fw) => {
        ctx.fillStyle = idx % 2 === 0 ? "#9d9d9d" : "#676767";
        ctx.fillRect(cx, cy, fw, 1);
      });
    },
    { colorSpace: THREE.NoColorSpace, repeat: [5, 5] },
  );
}

export function createFeltColorTexture(baseColorHex, fiberColorHex) {
  return createConfiguredCanvasTexture(
    256,
    256,
    (ctx, size) => {
      ctx.fillStyle = baseColorHex;
      ctx.fillRect(0, 0, size, size);
      forEachFeltFleck(size, 2200, (_idx, cx, cy, fw) => {
        ctx.fillStyle = fiberColorHex;
        ctx.globalAlpha = 0.28;
        ctx.fillRect(cx, cy, fw, 1);
      });
      ctx.globalAlpha = 1;
    },
    { colorSpace: THREE.SRGBColorSpace, repeat: [5, 5] },
  );
}

export function createSonyWordmarkTexture(accentHexColor = "#ffffff", rotationDirection = -1) {
  return createConfiguredCanvasTexture(
    128,
    256,
    (ctx, w, h) => {
      ctx.clearRect(0, 0, w, h);
      ctx.save();
      ctx.translate(w / 2, h / 2);
      // Rotate -90 deg on the left yoke so SONY reads upward with glyph tops facing forward (+Z).
      ctx.rotate(rotationDirection * (Math.PI / 2));
      ctx.fillStyle = accentHexColor;
      ctx.font = '700 60px "Times New Roman", Georgia, serif';
      ctx.textAlign = "center";
      ctx.textBaseline = "middle";
      ctx.fillText("S O N Y", 0, 0);
      ctx.restore();
    },
    { colorSpace: THREE.SRGBColorSpace },
  );
}

export function createHeadbandBadgeTexture(channelLetter = "R", badgeColor = "#c9282d") {
  return createConfiguredCanvasTexture(
    128,
    128,
    (ctx, size) => {
      ctx.clearRect(0, 0, size, size);
      ctx.beginPath();
      ctx.arc(size / 2, size / 2, size * 0.45, 0, Math.PI * 2);
      ctx.fillStyle = badgeColor;
      ctx.fill();

      if (channelLetter === "L") {
        ctx.lineWidth = 3;
        ctx.strokeStyle = "#6b7280";
        ctx.stroke();
      }

      ctx.fillStyle = channelLetter === "L" ? "#18191d" : "#f8fafc";
      ctx.font = '800 68px -apple-system, BlinkMacSystemFont, "Inter", sans-serif';
      ctx.textAlign = "center";
      ctx.textBaseline = "middle";
      ctx.fillText(channelLetter, size / 2, size / 2 + 3);
    },
    { colorSpace: THREE.SRGBColorSpace },
  );
}

export function createRimLabelTexture(labelType = "ncamb", labelHexColor = "#ffffff") {
  return createConfiguredCanvasTexture(
    256,
    80,
    (ctx, w, h) => {
      ctx.clearRect(0, 0, w, h);
      ctx.strokeStyle = labelHexColor;
      ctx.fillStyle = labelHexColor;

      if (labelType === "power") {
        const powerCenterX = w * 0.38;
        const centerY = h / 2;
        ctx.lineWidth = 4.2;
        ctx.lineCap = "round";
        ctx.lineJoin = "round";
        ctx.beginPath();
        ctx.arc(powerCenterX, centerY + 2, 16, -Math.PI * 0.28, Math.PI * 1.28, false);
        ctx.stroke();
        ctx.beginPath();
        ctx.moveTo(powerCenterX, centerY - 17);
        ctx.lineTo(powerCenterX, centerY + 1);
        ctx.stroke();

        const bluetoothCenterX = w * 0.64;
        ctx.lineWidth = 3.2;
        ctx.beginPath();
        ctx.moveTo(bluetoothCenterX, centerY - 15);
        ctx.lineTo(bluetoothCenterX, centerY + 15);
        ctx.lineTo(bluetoothCenterX + 10, centerY + 6.5);
        ctx.lineTo(bluetoothCenterX - 7.5, centerY - 6.5);
        ctx.moveTo(bluetoothCenterX - 7.5, centerY + 6.5);
        ctx.lineTo(bluetoothCenterX + 10, centerY - 6.5);
        ctx.lineTo(bluetoothCenterX, centerY - 15);
        ctx.stroke();
      } else {
        ctx.font = '600 38px -apple-system, BlinkMacSystemFont, "Inter", "Helvetica Neue", sans-serif';
        ctx.textAlign = "center";
        ctx.textBaseline = "middle";
        ctx.fillText("NC/AMB", w / 2, h / 2 + 1);
      }
    },
    { colorSpace: THREE.SRGBColorSpace },
  );
}
