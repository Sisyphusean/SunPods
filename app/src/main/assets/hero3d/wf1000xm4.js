// SunPods hero: Sony WF-1000XM4 earbuds and charging case modelled in code (Three.js r170+).
//
// Craft & fidelity highlights:
// - Double-walled solid charging case with concentric dome top, planar base at y = 0,
//   front finger-opening recess with recessed pill LED, stepped rear hinge knuckle block,
//   3D recessed USB-C bushing and tongue, molded lid interior pockets, sculpted center bridge,
//   and dual deep wells with 3 gold charging pogo pins each.
// - Matte recycled-resin micro-grain PBR maps (clearcoat: 0, roughness >= 0.72), calibrated
//   Graphite Black (brushed copper) and Platinum Silver (champagne gold), flush copper/gold
//   mic cylinder + metallic-rimmed upper acoustic slit + 3 inner contacts + IR sensor + L dot.
// - Non-overlapping L/R well seating + continuous 3D pack/unpack (handlesCaseProgress: true).
//
// Units: centimetres.

const FINISHES = {
  black: {
    shell: [0.011, 0.011, 0.013],
    face: [0.015, 0.015, 0.017],
    tip: [0.013, 0.013, 0.015],
    metal: [0.77, 0.50, 0.34],
    slitRim: [0.16, 0.12, 0.09],
    metalIn: [0.008, 0.008, 0.009],
    print: [0.75, 0.53, 0.33],
    caseShell: [0.012, 0.012, 0.014],
    caseInner: [0.008, 0.008, 0.009],
    casePrint: [0.34, 0.32, 0.30],
    lift: [0, 0, 0],
    seatShade: [0.78, 0.78, 0.78],
    sparkle: [0, 0, 0],
  },
  silver: {
    shell: [0.50, 0.48, 0.43],
    face: [0.56, 0.54, 0.48],
    tip: [0.36, 0.35, 0.32],
    metal: [0.75, 0.63, 0.36],
    slitRim: [0.38, 0.35, 0.28],
    metalIn: [0.018, 0.018, 0.016],
    print: [0.64, 0.52, 0.28],
    caseShell: [0.52, 0.50, 0.45],
    caseInner: [0.42, 0.40, 0.36],
    casePrint: [0.58, 0.48, 0.28],
    lift: [0, 0, 0],
    seatShade: [0.88, 0.88, 0.88],
    sparkle: [1, 1, 1],
  },
};
const KEYS = Object.keys(FINISHES.black);

const BH = 2.30;
const BUD0 = {
  Rd: 0.348,
  faceTilt: -0.14,
  disc: { sag: 0.012, proud: 0.004, groove: 0.007 },
  dome: { c: [0.04, -0.03, -0.22], a: [0.42, 0.45, 0.34], m: 2.6 },
  chin: { c: [0.08, -0.31, -0.34], a: [0.35, 0.27, 0.28], m: 2.4 },
  back: { c: [-0.02, 0.11, -0.39], a: [0.36, 0.34, 0.19], m: 2.4 },
  nozzle: { from: [0.24, -0.25, -0.47], dir: [0.24, 0.02, -1.0], len: 0.28, r0: 0.23, r1: 0.15 },
  blend: { chin: 0.10, back: 0.14, neck: 0.12, face: 0.03 },
  core: [0.18, -0.20, -0.38],
  tip: { at: 0.22, len: 0.31, r: 0.228, rBack: 0.042, rFront: 0.095, hole: 0.072, holeDepth: 0.06 },
  boss: { at: [0.30, -0.44, -0.04], dir: [0.06, -0.14, 1.0], r: 0.118, len: 0.175, ring: [0.60, 0.38], collar: 0.006 },
  slit: { th: 120, rad: 0.374, len: 0.115, w: 0.024, rim: 0.0035 },
  logo: { y0: 0.03, y1: 0.30, z: -0.16, h: 0.075 },
  inner: {
    contacts: [[0.02, -0.34], [0.09, -0.39], [0.16, -0.33]],
    contactD: 0.044,
    sensor: [-0.11, 0.09],
    sensorW: 0.145,
    sensorH: 0.105,
    mark: [-0.12, -0.18],
    markD: 0.11,
    dotL: [0.05, -0.30],
    dotD: 0.038,
  },
};

const CASE0 = {
  A: 3.00,
  B: 1.46,
  endR: 0.94,
  H: 3.56,
  yP: 2.06,
  taper: 0.065,
  bottom: { v: 0.64, hx: 0.56, hz: 0.48, dish: 0.02 },
  top: { v: 0.78, hx: 0.72, hz: 0.62, dome: 0.10 },
  lidGrow: 0.024,
  wall: 0.10,
  gap: 0.008,
  deck: 0.56,
  led: { w: 0.34, h: 0.042, y: 2.00 },
  notch: { w: 1.24, h: 0.10, y: 2.00 },
  hinge: { w: 0.92, h: 0.24, depth: 0.12 },
  usb: { w: 0.90, h: 0.31, y: 1.26 },
  logo: { w: 1.32, z: -0.38 },
  lidOpenDeg: 100,
  // Right-handed basis: bz = bx x by points UP (+y) so touch disc (+z) faces up-back toward lid pocket
  // and foam tip (-z) plunges straight DOWN (-y) into separate well; copper mic sits on outer-top shoulder.
  budSeat: {
    pos: [1.05, 2.41, 0],
    x: [0.86, 0.24, -0.45],
    y: [-0.42, -0.14, -0.90],
    tilt: [-25, 10, -20],
    drop: 0.16,
    scale: 0.88,
  },
  well: { rx: 0.80, rz: 0.66, depth: 0.90 },
};

const HERO0 = {
  posR: [-1.78, 0.50, -0.40],
  posL: [1.78, -0.50, 0.45],
  rot: [6, -24, -4],
  s: 0.9,
  dy: 0.13,
  caseDy: 0.21,
  caseS: 1.0,
};

function mergeDefault(a, b) {
  if (!b) return a;
  const o = Array.isArray(a) ? a.slice() : { ...a };
  for (const [k, v] of Object.entries(b)) {
    o[k] = v && typeof v === 'object' && !Array.isArray(v) && a[k] ? mergeDefault(a[k], v) : v;
  }
  return o;
}

function hash2d(x, y) {
  const s = Math.sin(x * 127.1 + y * 311.7) * 43758.5453123;
  return s - Math.floor(s);
}

function makeResinGrainTextures(THREE, maxAniso = 4) {
  const size = 256;
  if (typeof document === 'undefined') {
    const data = new Uint8Array(size * size * 4);
    for (let i = 0; i < size * size; i++) {
      const v = Math.round((0.50 + (hash2d(i % size, Math.floor(i / size)) - 0.5) * 0.16) * 255);
      data[i * 4] = data[i * 4 + 1] = data[i * 4 + 2] = v;
      data[i * 4 + 3] = 255;
    }
    const t = new THREE.DataTexture(data, size, size, THREE.RGBAFormat);
    t.colorSpace = THREE.NoColorSpace;
    t.wrapS = t.wrapT = THREE.RepeatWrapping;
    t.repeat.set(6, 6);
    t.needsUpdate = true;
    return { rough: t, bump: t };
  }

  const rCanvas = document.createElement('canvas');
  const bCanvas = document.createElement('canvas');
  rCanvas.width = bCanvas.width = size;
  rCanvas.height = bCanvas.height = size;
  const rCtx = rCanvas.getContext('2d');
  const bCtx = bCanvas.getContext('2d');
  const rImg = rCtx.createImageData(size, size);
  const bImg = bCtx.createImageData(size, size);

  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      const cell = hash2d(Math.floor(x / 2), Math.floor(y / 2));
      const fine = hash2d(x, y);
      const val = 0.5 * cell + 0.5 * fine;
      const bByte = Math.round((0.40 + val * 0.28) * 255);
      const rByte = Math.round((0.50 + (1 - val) * 0.18) * 255);
      const idx = (y * size + x) * 4;
      bImg.data[idx] = bImg.data[idx + 1] = bImg.data[idx + 2] = bByte;
      bImg.data[idx + 3] = 255;
      rImg.data[idx] = rImg.data[idx + 1] = rImg.data[idx + 2] = rByte;
      rImg.data[idx + 3] = 255;
    }
  }
  bCtx.putImageData(bImg, 0, 0);
  rCtx.putImageData(rImg, 0, 0);

  const rough = new THREE.CanvasTexture(rCanvas);
  const bump = new THREE.CanvasTexture(bCanvas);
  for (const tex of [rough, bump]) {
    tex.colorSpace = THREE.NoColorSpace;
    tex.wrapS = tex.wrapT = THREE.RepeatWrapping;
    tex.repeat.set(6, 6);
    tex.anisotropy = maxAniso;
  }
  return { rough, bump };
}

export function createWF1000XM4(ctx) {
  const { THREE, patched, lin, smooth, makeFinishCopy } = ctx;
  const merge = ctx.merge || mergeDefault;
  const clamp01 = (x) => Math.min(1, Math.max(0, x));
  const BUD = ctx.DEBUG && ctx.overrides ? merge(BUD0, ctx.overrides.BUD) : BUD0;
  const CASE = ctx.DEBUG && ctx.overrides ? merge(CASE0, ctx.overrides.CASE) : CASE0;
  const HERO = merge(HERO0, ctx.DEBUG && ctx.overrides ? ctx.overrides.HERO : null);
  const V3 = (a) => new THREE.Vector3(a[0], a[1], a[2]);
  const rad = (d) => (d * Math.PI) / 180;

  // Spatial grid accelerator for fast raycast projections during setup.
  let chunkCache = new WeakMap();
  function chunksOf(target) {
    if (!chunkCache) return null;
    const geo = target.geometry;
    const pos = geo && geo.attributes.position;
    if (!pos || Array.isArray(target.material)) return null;
    const cached = chunkCache.get(target);
    if (cached && cached.geo === geo && cached.version === pos.version) return cached.meshes;
    const idx = geo.index;
    const nTri = Math.floor((idx ? idx.count : pos.count) / 3);
    const vi = (t, k) => (idx ? idx.getX(t * 3 + k) : t * 3 + k);
    const bb = new THREE.Box3().setFromBufferAttribute(pos);
    const size = bb.getSize(new THREE.Vector3());
    const G = 8;
    const cell = (v, lo, s) => Math.min(G - 1, Math.max(0, Math.floor(((v - lo) / (s || 1)) * G)));
    const bins = new Map();
    for (let t = 0; t < nTri; t++) {
      let cx = 0;
      let cy = 0;
      let cz = 0;
      for (let k = 0; k < 3; k++) {
        const v = vi(t, k);
        cx += pos.getX(v);
        cy += pos.getY(v);
        cz += pos.getZ(v);
      }
      const key = cell(cx / 3, bb.min.x, size.x) + G * (cell(cy / 3, bb.min.y, size.y) + G * cell(cz / 3, bb.min.z, size.z));
      let b = bins.get(key);
      if (!b) bins.set(key, (b = []));
      b.push(t);
    }
    const meshes = [];
    for (const tris of bins.values()) {
      const arr = new Float32Array(tris.length * 9);
      let o = 0;
      for (const t of tris) {
        for (let k = 0; k < 3; k++) {
          const v = vi(t, k);
          arr[o++] = pos.getX(v);
          arr[o++] = pos.getY(v);
          arr[o++] = pos.getZ(v);
        }
      }
      const g = new THREE.BufferGeometry();
      g.setAttribute('position', new THREE.BufferAttribute(arr, 3));
      g.computeBoundingSphere();
      g.computeBoundingBox();
      const m = new THREE.Mesh(g, target.material);
      m.matrixAutoUpdate = false;
      meshes.push(m);
    }
    chunkCache.set(target, { geo, version: pos.version, meshes });
    return meshes;
  }
  const hitInv = new THREE.Matrix4();
  const hitRay = new THREE.Ray();
  function firstHit(rc, target) {
    const meshes = chunksOf(target);
    if (!meshes) return rc.intersectObject(target, false)[0];
    hitInv.copy(target.matrixWorld).invert();
    hitRay.copy(rc.ray).applyMatrix4(hitInv);
    const near = [];
    for (const m of meshes) {
      if (hitRay.intersectsBox(m.geometry.boundingBox)) {
        m.matrixWorld.copy(target.matrixWorld);
        near.push(m);
      }
    }
    return near.length ? rc.intersectObjects(near, false)[0] : undefined;
  }

  // Print atlas (SONY wordmark + L/R marks).
  let atlasReady;
  const atlas = typeof document !== 'undefined'
    ? new THREE.TextureLoader().load('./c710n_marks.png', () => atlasReady && atlasReady())
    : new THREE.DataTexture(new Uint8Array([0, 0, 0, 255]), 1, 1);
  const ready = typeof document !== 'undefined'
    ? new Promise((r) => { atlasReady = r; })
    : Promise.resolve();
  atlas.colorSpace = THREE.NoColorSpace;
  atlas.anisotropy = 4;
  atlas.generateMipmaps = true;
  atlas.minFilter = THREE.LinearMipmapLinearFilter;
  const ATL = { sony: [0.04, 0.03, 0.96, 0.47], markL: [0, 0.5, 0.25, 1], markR: [0.25, 0.5, 0.5, 1] };

  // Materials: satin-matte recycled resin PBR (clearcoat = 0 avoids flat case-lid studio glare).
  const grain = makeResinGrainTextures(THREE, 4);
  const M = {};
  const P = (o) => new THREE.MeshPhysicalMaterial(o);
  const resinMat = (roughness, sheen = 0.02) => P({
    roughness,
    metalness: 0,
    roughnessMap: grain.rough,
    bumpMap: grain.bump,
    bumpScale: 0.0014,
    specularIntensity: 0.42,
    sheen,
    sheenRoughness: 0.85,
    sheenColor: new THREE.Color(0.03, 0.03, 0.03),
    clearcoat: 0,
  });

  M.shell = resinMat(0.56, 0.02);
  M.shellCase = resinMat(0.64, 0.02);
  M.face = patched(resinMat(0.48, 0.03), 'xm4-face', { uSparkle: { value: 0 } }, {
    pars: 'uniform float uSparkle;\nfloat xm4Hash(vec3 p) { p = fract(p * 0.3183099 + 0.1); p *= 17.0; return fract(p.x * p.y * p.z * (p.x + p.y + p.z)); }\n',
    after: {
      color_fragment: `
{
  float g = xm4Hash(floor(vObjPos * 280.0));
  float fw = clamp(1.0 - length(fwidth(vObjPos * 280.0)) * 0.6, 0.0, 1.0);
  diffuseColor.rgb *= 1.0 + uSparkle * fw * (g - 0.5) * 0.11;
}`,
    },
  });
  M.faceCase = resinMat(0.56, 0.02);
  M.discEdge = P({ roughness: 0.42, metalness: 0, specularIntensity: 0.55, clearcoat: 0 });
  M.groove = P({ roughness: 0.90, metalness: 0, clearcoat: 0 });

  M.metal = P({ metalness: 1, roughness: 0.28, clearcoat: 0 });
  M.slitRim = P({ metalness: 0.65, roughness: 0.40, clearcoat: 0 });
  M.metalFace = P({ metalness: 1, roughness: 0.22, clearcoat: 0 });
  M.metalIn = P({ roughness: 0.65, metalness: 0, clearcoat: 0 });
  M.collar = P({ roughness: 0.78, metalness: 0, clearcoat: 0 });
  M.slitCore = P({ color: lin([0.006, 0.006, 0.007]), roughness: 0.85, metalness: 0, clearcoat: 0 });

  function withUv(mat) {
    const prev = mat.onBeforeCompile;
    mat.onBeforeCompile = (sh, r) => {
      prev(sh, r);
      sh.vertexShader = sh.vertexShader
        .replace('#include <common>', '#include <common>\nvarying vec2 vX4Uv;')
        .replace('#include <begin_vertex>', '#include <begin_vertex>\nvX4Uv = uv;');
    };
    return mat;
  }

  function shapeMat(base, key, shape, w, h) {
    return withUv(patched(P(base), 'xm4-shape-' + key, { uSz: { value: new THREE.Vector2(w, h) } }, {
      pars: 'varying vec2 vX4Uv;\nuniform vec2 uSz;\n',
      after: {
        color_fragment: `
{
  vec2 p = (vX4Uv - 0.5) * uSz;
  float d;
  ${shape === 'pill' ? 'd = length(max(abs(p) - vec2(0.5 * (uSz.x - uSz.y), 0.0), 0.0)) - 0.5 * uSz.y;' : ''}
  ${shape === 'rbox' ? 'vec2 q = abs(p) - 0.5 * uSz + 0.3 * uSz.y; d = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - 0.3 * uSz.y;' : ''}
  if (d > 0.0) discard;
}`,
      },
    }));
  }

  M.print = P({
    roughness: 0.30,
    metalness: 0.45,
    transparent: true,
    alphaTest: 0.08,
    depthWrite: false,
    alphaMap: atlas,
    polygonOffset: true,
    polygonOffsetFactor: -2,
    polygonOffsetUnits: -2,
  });
  M.markR = P({
    color: lin([0.85, 0.11, 0.04]),
    roughness: 0.42,
    transparent: true,
    alphaTest: 0.08,
    depthWrite: false,
    alphaMap: atlas,
    polygonOffset: true,
    polygonOffsetFactor: -2,
    polygonOffsetUnits: -2,
  });
  M.markL = P({
    color: lin([0.55, 0.56, 0.57]),
    roughness: 0.42,
    transparent: true,
    alphaTest: 0.08,
    depthWrite: false,
    alphaMap: atlas,
    polygonOffset: true,
    polygonOffsetFactor: -2,
    polygonOffsetUnits: -2,
  });

  const tipAxis = (() => {
    const d = V3(BUD.nozzle.dir).normalize();
    const o = V3(BUD.nozzle.from).multiplyScalar(BH);
    return { o, d };
  })();

  M.tip = patched(P({
    roughness: 0.90,
    metalness: 0,
    specularIntensity: 0.35,
    sheen: 0.22,
    sheenRoughness: 0.72,
    sheenColor: new THREE.Color(0.04, 0.04, 0.04),
    clearcoat: 0,
  }), 'xm4-foam', { uTipO: { value: tipAxis.o }, uTipD: { value: tipAxis.d } }, {
    pars: `uniform vec3 uTipO, uTipD;
float xm4N(vec3 p) {
  vec3 i = floor(p), f = fract(p); f = f * f * (3.0 - 2.0 * f);
  float n = dot(i, vec3(1.0, 57.0, 113.0));
  vec4 h = fract(sin(vec4(n, n + 1.0, n + 57.0, n + 58.0)) * 43758.5453);
  vec4 g = fract(sin(vec4(n + 113.0, n + 114.0, n + 170.0, n + 171.0)) * 43758.5453);
  vec4 a = mix(h, g, f.z);
  return mix(mix(a.x, a.y, f.x), mix(a.z, a.w, f.x), f.y);
}
`,
    after: {
      color_fragment: `
{
  vec3 q = vObjPos * 95.0;
  float fw = clamp(1.0 - length(fwidth(q)) * 0.45, 0.0, 1.0);
  float n = xm4N(q) * 0.65 + xm4N(q * 2.3) * 0.35;
  diffuseColor.rgb *= 1.0 + fw * (n - 0.5) * 0.38;
  float s = dot(vObjPos - uTipO, uTipD);
  diffuseColor.rgb *= 1.0 - 0.35 * smoothstep(${((BUD.tip.at + BUD.tip.len - 0.07) * BH).toFixed(4)}, ${((BUD.tip.at + BUD.tip.len - 0.02) * BH).toFixed(4)}, s) * step(length(vObjPos - uTipO - uTipD * s), ${(BUD.tip.hole * 1.3 * BH).toFixed(4)});
}`,
    },
  });
  M.tipHole = P({ color: lin([0.004, 0.004, 0.004]), roughness: 0.92, side: THREE.DoubleSide });
  M.nozzle = P({ color: lin([0.018, 0.018, 0.020]), roughness: 0.55 });
  M.contact = P({ color: lin([0.82, 0.58, 0.25]), metalness: 1, roughness: 0.20 });
  M.sensor = shapeMat(
    { color: lin([0.005, 0.005, 0.007]), roughness: 0.14, clearcoat: 0.65, clearcoatRoughness: 0.05 },
    'sensor',
    'rbox',
    BUD.inner.sensorW * BH,
    BUD.inner.sensorH * BH,
  );

  // Case materials (opaque by default; higher true roughness avoids ceiling-light blowout on the flat lid).
  const caseMat = (roughness) => P({
    roughness,
    metalness: 0,
    bumpMap: grain.bump,
    bumpScale: 0.0014,
    specularIntensity: 0.18,
    sheen: 0.02,
    sheenRoughness: 0.85,
    sheenColor: new THREE.Color(0.03, 0.03, 0.03),
    clearcoat: 0,
  });
  M.caseShell = caseMat(0.84);
  M.caseRim = P({ roughness: 0.78, metalness: 0, clearcoat: 0 });
  M.caseBaseRim = caseMat(0.84);
  M.caseInner = caseMat(0.88);
  M.casePocket = caseMat(0.90);
  M.caseRecess = P({ color: lin([0.012, 0.012, 0.014]), roughness: 0.88, metalness: 0, clearcoat: 0 });
  M.casePrint = P({
    roughness: 0.28,
    metalness: 0.35,
    transparent: true,
    alphaTest: 0.08,
    depthWrite: false,
    alphaMap: atlas,
    polygonOffset: true,
    polygonOffsetFactor: -2,
    polygonOffsetUnits: -2,
  });
  M.led = P({ color: lin([0.012, 0.012, 0.014]), roughness: 0.12, clearcoat: 0.5, clearcoatRoughness: 0.04, emissive: new THREE.Color(0, 0, 0) });
  M.portCavity = P({ color: lin([0.005, 0.005, 0.006]), roughness: 0.85, metalness: 0 });
  M.portShell = P({ color: lin([0.52, 0.53, 0.55]), metalness: 0.92, roughness: 0.30 });
  M.portTongue = P({ color: lin([0.025, 0.025, 0.028]), roughness: 0.45 });
  M.pin = P({ color: lin([0.82, 0.58, 0.25]), metalness: 1, roughness: 0.22 });

  for (const [k, m] of Object.entries(M)) {
    m.name = 'X4_' + k;
    if (!m.userData.uniforms) patched(m, 'sunpods-plain');
  }

  // ---------------------------------------------------------------------------------------------
  // Earbud SDF & mesh construction
  const smin = (a, b, k) => {
    const h = Math.max(k - Math.abs(a - b), 0) / k;
    return Math.min(a, b) - h * h * k * 0.25;
  };
  const smax = (a, b, k) => -smin(-a, -b, k);
  const powm = (v, m) => (m === 2 ? v * v : m === 3 ? v * v * v : Math.pow(v, m));
  const rootm = (v, m) => (m === 2 ? Math.sqrt(v) : m === 3 ? Math.cbrt(v) : Math.pow(v, 1 / m));
  function superq(x, y, z, s) {
    const qx = (x - s.c[0]) / s.a[0];
    const qy = (y - s.c[1]) / s.a[1];
    const qz = Math.abs(z - s.c[2]) / s.a[2];
    const v = rootm(powm(Math.hypot(qx, qy), s.m) + powm(qz, s.m), s.m);
    return (v - 1) * Math.min(s.a[0], s.a[1], s.a[2]);
  }
  const nzD = V3(BUD.nozzle.dir).normalize();
  const nzA = V3(BUD.nozzle.from);
  const nzB = nzA.clone().addScaledVector(nzD, BUD.nozzle.len);
  function roundCone(x, y, z, a, b, r1, r2) {
    const bax = b.x - a.x;
    const bay = b.y - a.y;
    const baz = b.z - a.z;
    const l2 = bax * bax + bay * bay + baz * baz;
    const rr = r1 - r2;
    const a2 = l2 - rr * rr;
    const il2 = 1 / l2;
    const pax = x - a.x;
    const pay = y - a.y;
    const paz = z - a.z;
    const yy = pax * bax + pay * bay + paz * baz;
    const zz = yy - l2;
    const cx = pax * l2 - bax * yy;
    const cy = pay * l2 - bay * yy;
    const cz = paz * l2 - baz * yy;
    const x2 = cx * cx + cy * cy + cz * cz;
    const y2 = yy * yy * l2;
    const z2 = zz * zz * l2;
    const k = Math.sign(rr) * rr * rr * x2;
    if (Math.sign(zz) * a2 * z2 > k) return Math.sqrt(x2 + z2) * il2 - r2;
    if (Math.sign(yy) * a2 * y2 < k) return Math.sqrt(x2 + y2) * il2 - r1;
    return (Math.sqrt(x2 * a2 * il2) + yy * rr) * il2 - r1;
  }
  const faceK = 1 / Math.sqrt(1 + BUD.faceTilt * BUD.faceTilt);
  function bodyF(x, y, z) {
    let u = smin(superq(x, y, z, BUD.dome), superq(x, y, z, BUD.chin), BUD.blend.chin);
    u = smin(u, superq(x, y, z, BUD.back), BUD.blend.back);
    u = smin(u, roundCone(x, y, z, nzA, nzB, BUD.nozzle.r0, BUD.nozzle.r1), BUD.blend.neck);
    return smax(u, (z - BUD.faceTilt * y) * faceK, BUD.blend.face);
  }

  function shellGeometry() {
    const NU = 144;
    const NV = 150;
    const c0 = V3(BUD.core);
    const ax = nzD.clone();
    const e1 = new THREE.Vector3(0, 1, 0).projectOnPlane(ax).normalize();
    const e2 = new THREE.Vector3().crossVectors(ax, e1);
    const pos = [];
    const nor = [];
    const uv = [];
    const idx = [];
    const surf = (dx, dy, dz) => {
      let t = 0.02;
      const step = 0.03;
      let prev = t;
      while (t < 2.0 && bodyF(c0.x + dx * t, c0.y + dy * t, c0.z + dz * t) < 0) {
        prev = t;
        t += step;
      }
      let lo = prev;
      let hi = t;
      for (let k = 0; k < 16; k++) {
        const m = 0.5 * (lo + hi);
        if (bodyF(c0.x + dx * m, c0.y + dy * m, c0.z + dz * m) < 0) lo = m;
        else hi = m;
      }
      return 0.5 * (lo + hi);
    };
    const push = (dx, dy, dz, uCoord, vCoord) => {
      const t = surf(dx, dy, dz);
      const x = c0.x + dx * t;
      const y = c0.y + dy * t;
      const z = c0.z + dz * t;
      const e = 0.0015;
      const gx = bodyF(x + e, y, z) - bodyF(x - e, y, z);
      const gy = bodyF(x, y + e, z) - bodyF(x, y - e, z);
      const gz = bodyF(x, y, z + e) - bodyF(x, y, z - e);
      const gl = Math.hypot(gx, gy, gz) || 1;
      pos.push(x * BH, y * BH, z * BH);
      nor.push(gx / gl, gy / gl, gz / gl);
      uv.push(uCoord, vCoord);
    };
    push(-ax.x, -ax.y, -ax.z, 0.5, 0);
    for (let j = 1; j < NV; j++) {
      const v = (Math.PI * j) / NV;
      const cv = Math.cos(v);
      const sv = Math.sin(v);
      for (let i = 0; i < NU; i++) {
        const u = (2 * Math.PI * i) / NU;
        const cu = Math.cos(u);
        const su = Math.sin(u);
        push(
          -ax.x * cv + (e1.x * cu + e2.x * su) * sv,
          -ax.y * cv + (e1.y * cu + e2.y * su) * sv,
          -ax.z * cv + (e1.z * cu + e2.z * su) * sv,
          i / NU,
          j / NV,
        );
      }
    }
    push(ax.x, ax.y, ax.z, 0.5, 1);
    const back = pos.length / 3 - 1;
    const row = (j) => 1 + (j - 1) * NU;
    for (let i = 0; i < NU; i++) {
      const i1 = (i + 1) % NU;
      idx.push(0, row(1) + i1, row(1) + i);
    }
    for (let j = 1; j < NV - 1; j++) {
      for (let i = 0; i < NU; i++) {
        const i1 = (i + 1) % NU;
        const a = row(j) + i;
        const b = row(j) + i1;
        const c = row(j + 1) + i;
        const d = row(j + 1) + i1;
        idx.push(a, b, c, b, d, c);
      }
    }
    for (let i = 0; i < NU; i++) {
      const i1 = (i + 1) % NU;
      idx.push(back, row(NV - 1) + i, row(NV - 1) + i1);
    }
    const g = new THREE.BufferGeometry();
    g.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3));
    g.setAttribute('normal', new THREE.Float32BufferAttribute(nor, 3));
    g.setAttribute('uv', new THREE.Float32BufferAttribute(uv, 2));
    g.setIndex(idx);
    const p0 = new THREE.Vector3();
    const p1 = new THREE.Vector3();
    const p2 = new THREE.Vector3();
    const n0 = new THREE.Vector3();
    const tri = Math.floor(idx.length / 6) * 3;
    p0.fromBufferAttribute(g.attributes.position, idx[tri]);
    p1.fromBufferAttribute(g.attributes.position, idx[tri + 1]);
    p2.fromBufferAttribute(g.attributes.position, idx[tri + 2]);
    n0.fromBufferAttribute(g.attributes.normal, idx[tri]);
    if (p1.sub(p0).cross(p2.sub(p0)).dot(n0) < 0) {
      const ix = g.index.array;
      for (let k = 0; k < ix.length; k += 3) {
        const tmp = ix[k];
        ix[k] = ix[k + 2];
        ix[k + 2] = tmp;
      }
    }
    return g;
  }

  const faceZ = (x, y) => BUD.faceTilt * y;
  function discGeometry(r0, r1, segs, lift) {
    const g = r0 > 0 ? new THREE.RingGeometry(r0 * BH, r1 * BH, segs, 1) : new THREE.CircleGeometry(r1 * BH, segs);
    const p = g.getAttribute('position');
    const uvAttr = g.getAttribute('uv');
    for (let i = 0; i < p.count; i++) {
      const x = p.getX(i) / BH;
      const y = p.getY(i) / BH;
      const rr = Math.min(1, (x * x + y * y) / (BUD.Rd * BUD.Rd));
      p.setZ(i, (faceZ(x, y) + lift(rr)) * BH);
      if (uvAttr) uvAttr.setXY(i, x / (2 * BUD.Rd) + 0.5, y / (2 * BUD.Rd) + 0.5);
    }
    g.computeVertexNormals();
    return g;
  }

  const D = BUD.disc;
  const discLift = (rr) => D.proud + D.sag * (1 - rr);
  function faceGeometry() {
    return mergeGeos([
      discGeometry(0, BUD.Rd * 0.55, 96, discLift),
      discGeometry(BUD.Rd * 0.55, BUD.Rd * 0.985, 128, discLift),
    ]);
  }
  function mergeGeos(list) {
    const pos = [];
    const nor = [];
    const uv = [];
    const idx = [];
    let base = 0;
    for (const g of list) {
      const gp = g.getAttribute('position');
      const gn = g.getAttribute('normal');
      const gu = g.getAttribute('uv');
      for (let i = 0; i < gp.count; i++) {
        pos.push(gp.getX(i), gp.getY(i), gp.getZ(i));
        nor.push(gn.getX(i), gn.getY(i), gn.getZ(i));
        uv.push(gu ? gu.getX(i) : 0, gu ? gu.getY(i) : 0);
      }
      const gi = g.index ? g.index.array : [...Array(gp.count).keys()];
      for (const k of gi) idx.push(k + base);
      base += gp.count;
    }
    const g = new THREE.BufferGeometry();
    g.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3));
    g.setAttribute('normal', new THREE.Float32BufferAttribute(nor, 3));
    g.setAttribute('uv', new THREE.Float32BufferAttribute(uv, 2));
    g.setIndex(idx);
    return g;
  }

  function latheAlong(profile, from, dir, segs = 64) {
    const g = new THREE.LatheGeometry(profile.map((p) => new THREE.Vector2(p[0], p[1])), segs);
    const q = new THREE.Quaternion().setFromUnitVectors(new THREE.Vector3(0, 1, 0), dir.clone().normalize());
    g.applyQuaternion(q);
    g.translate(from.x, from.y, from.z);
    return g;
  }

  function tipGeometries() {
    const tp = BUD.tip;
    const R = tp.r * BH;
    const L = tp.len * BH;
    const rb = tp.rBack * BH;
    const rf = tp.rFront * BH;
    const ro = tp.hole * BH;
    const hd = tp.holeDepth * BH;
    const base = tipAxis.o.clone().addScaledVector(tipAxis.d, tp.at * BH);
    const prof = [];
    const rStem = BUD.nozzle.r1 * BH * 0.92;
    prof.push([rStem, rb * 0.4]);
    for (let k = 0; k <= 8; k++) {
      const a = (k / 8) * (Math.PI / 2);
      prof.push([R - rb + rb * Math.sin(a), rb - rb * Math.cos(a)]);
    }
    for (let k = 1; k <= 6; k++) {
      const t = k / 7;
      const rAt = R * (1 - 0.20 * t);
      prof.push([rAt + 0.008 * R * Math.sin(Math.PI * t), rb + (L - rb - rf) * t]);
    }
    const rTip = R * 0.80;
    for (let k = 0; k <= 10; k++) {
      const a = (k / 10) * (Math.PI / 2);
      prof.push([rTip - rf + rf * Math.cos(a), L - rf + rf * Math.sin(a)]);
    }
    prof.push([ro + 0.006 * BH, L - 0.002 * BH]);
    prof.push([ro, L - 0.010 * BH]);
    prof.push([ro, L - hd]);
    const tip = latheAlong(prof, base, tipAxis.d, 96);
    const hole = latheAlong([[ro * 1.001, L - hd], [0.0001, L - hd]], base, tipAxis.d, 40);
    const stem = latheAlong([[0.0001, -0.05 * BH], [rStem, -0.05 * BH], [rStem, L - hd - 0.002 * BH], [0.0001, L - hd - 0.002 * BH]], base, tipAxis.d, 40);
    return { tip, hole, stem };
  }

  function bossGeometries() {
    const b = BUD.boss;
    const dir = V3(b.dir).normalize();
    const end = V3(b.at).multiplyScalar(BH);
    const r = b.r * BH;
    const L = b.len * BH;
    const r1 = b.ring[0] * r;
    const r2 = b.ring[1] * r;
    const bev = 0.07 * r;
    const from = end.clone().addScaledVector(dir, -L);
    const collar = latheAlong([[r + b.collar * BH, 0.02 * BH], [r + b.collar * BH, L * 0.45], [r, L * 0.48]], from, dir, 48);
    const side = latheAlong([[r, 0], [r, L - bev], [r - bev * 0.55, L - bev * 0.15], [r - bev, L]], from, dir, 64);
    const face = latheAlong([[r - bev, L], [r1 + 0.015 * r, L], [r1, L - 0.025 * r]], from, dir, 64);
    const black = latheAlong([[r1, L - 0.025 * r], [r1 * 0.96, L - 0.11 * r], [r2 * 1.04, L - 0.11 * r], [r2, L - 0.04 * r]], from, dir, 64);
    const centre = latheAlong([[r2, L - 0.04 * r], [r2 * 0.92, L - 0.01 * r], [r2 * 0.80, L], [0.0001, L]], from, dir, 48);
    return { collar, side, face, black, centre, dir, end };
  }

  // Stadium ring + core for the upper metallic vent slit on the curved rim.
  function slitGeometries(target) {
    const sl = BUD.slit;
    const center = new THREE.Vector3(
      Math.cos(rad(sl.th)) * sl.rad * BH,
      Math.sin(rad(sl.th)) * sl.rad * BH,
      0.5 * BH,
    );
    const n = new THREE.Vector3(0, 0, 1);
    const rc = new THREE.Raycaster(center, new THREE.Vector3(0, 0, -1));
    const hit = firstHit(rc, target);
    const pHit = hit ? hit.point.clone() : new THREE.Vector3(center.x, center.y, faceZ(center.x / BH, center.y / BH) * BH);
    const nHit = hit && hit.face ? hit.face.normal.clone().normalize() : n;
    const radDir = new THREE.Vector3(pHit.x, pHit.y, 0).projectOnPlane(nHit).normalize();
    const tangDir = new THREE.Vector3().crossVectors(radDir, nHit).normalize();

    const projectLocal = (pt) => {
      const rr = new THREE.Raycaster(pt.clone().addScaledVector(nHit, 0.4 * BH), nHit.clone().negate());
      const hh = firstHit(rr, target);
      return hh ? hh.point.clone().addScaledVector(hh.face.normal.clone().normalize(), 0.0025) : pt;
    };

    const makeOvalRing = (rxOut, ryOut, rxIn, ryIn, hOut, hIn) => {
      const segs = 48;
      const pos = [];
      const idx = [];
      const rings = [
        [rxOut, ryOut, hOut],
        [rxOut * 0.96, ryOut * 0.96, hOut + 0.0015 * BH],
        [rxIn * 1.04, ryIn * 1.04, hOut + 0.0015 * BH],
        [rxIn, ryIn, hIn],
      ];
      for (const [rx, ry, h] of rings) {
        for (let i = 0; i < segs; i++) {
          const t = (i / segs) * Math.PI * 2;
          const u = Math.cos(t);
          const v = Math.sin(t);
          const raw = pHit.clone().addScaledVector(tangDir, u * rx).addScaledVector(radDir, v * ry);
          const qp = projectLocal(raw).addScaledVector(nHit, h);
          pos.push(qp.x, qp.y, qp.z);
        }
      }
      for (let r = 0; r < rings.length - 1; r++) {
        for (let i = 0; i < segs; i++) {
          const i1 = (i + 1) % segs;
          const a = r * segs + i;
          const b = r * segs + i1;
          const c = (r + 1) * segs + i;
          const d = (r + 1) * segs + i1;
          idx.push(a, b, c, b, d, c);
        }
      }
      const g = new THREE.BufferGeometry();
      g.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3));
      g.setIndex(idx);
      g.computeVertexNormals();
      return g;
    };

    const rx = (sl.len * 0.5) * BH;
    const ry = (sl.w * 0.5) * BH;
    const rw = sl.rim * BH;
    const rim = makeOvalRing(rx + rw, ry + rw, rx, ry, 0.0005, -0.002);

    const segs = 40;
    const cPos = [pHit.x, pHit.y, pHit.z];
    const cIdx = [];
    for (let i = 0; i < segs; i++) {
      const t = (i / segs) * Math.PI * 2;
      const raw = pHit.clone().addScaledVector(tangDir, Math.cos(t) * rx * 1.03).addScaledVector(radDir, Math.sin(t) * ry * 1.03);
      const qp = projectLocal(raw).addScaledVector(nHit, -0.001);
      cPos.push(qp.x, qp.y, qp.z);
    }
    for (let i = 0; i < segs; i++) cIdx.push(0, 1 + i, 1 + ((i + 1) % segs));
    const core = new THREE.BufferGeometry();
    core.setAttribute('position', new THREE.Float32BufferAttribute(cPos, 3));
    core.setIndex(cIdx);
    core.computeVertexNormals();

    return { rim, core };
  }

  const ray = new THREE.Raycaster();
  function conformal(target, center, n, up, w, h, opts = {}) {
    const N = opts.n || 18;
    const off = opts.off ?? 0.004;
    const flipU = !!opts.flipU;
    n = n.clone().normalize();
    const xA = new THREE.Vector3().crossVectors(up, n).normalize();
    const yA = new THREE.Vector3().crossVectors(n, xA).normalize();
    const pos = [];
    const uv = [];
    const idx = [];
    const miss = [];
    const rect = opts.rect || [0, 0, 1, 1];
    target.updateWorldMatrix(true, false);
    const inv = new THREE.Matrix4().copy(target.matrixWorld).invert();
    for (let j = 0; j <= N; j++) {
      for (let i = 0; i <= N; i++) {
        const u = i / N;
        const v = j / N;
        const p = center.clone().addScaledVector(xA, (u - 0.5) * w).addScaledVector(yA, (v - 0.5) * h);
        ray.set(p.clone().addScaledVector(n, 0.6 * BH), n.clone().negate());
        const hit = firstHit(ray, target);
        miss.push(!hit);
        const q = hit ? hit.point.clone() : p.clone();
        q.addScaledVector(hit && hit.face ? hit.face.normal.clone().transformDirection(target.matrixWorld) : n, off);
        q.applyMatrix4(inv);
        pos.push(q.x, q.y, q.z);
        const uu = flipU ? 1 - u : u;
        uv.push(rect[0] + (rect[2] - rect[0]) * uu, 1 - (rect[1] + (rect[3] - rect[1]) * (1 - v)));
      }
    }
    for (let j = 0; j < N; j++) {
      for (let i = 0; i < N; i++) {
        const a = j * (N + 1) + i;
        const b = a + 1;
        const c = a + N + 1;
        const d = c + 1;
        if (miss[a] || miss[b] || miss[c] || miss[d]) continue;
        idx.push(a, b, c, b, d, c);
      }
    }
    const g = new THREE.BufferGeometry();
    g.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3));
    g.setAttribute('uv', new THREE.Float32BufferAttribute(uv, 2));
    g.setIndex(idx);
    g.computeVertexNormals();
    return g;
  }

  function surfacePoint(target, xy, dirZ) {
    const o = new THREE.Vector3(xy[0] * BH, xy[1] * BH, dirZ < 0 ? 1.0 * BH : -1.6 * BH);
    ray.set(o, new THREE.Vector3(0, 0, dirZ < 0 ? -1 : 1));
    const hit = firstHit(ray, target);
    if (!hit) return { p: o, n: new THREE.Vector3(0, 0, -dirZ) };
    return { p: hit.point.clone(), n: hit.face.normal.clone() };
  }

  function stripOn(target, line, along, top, h, rect, flipU) {
    const N = 48;
    const hh = (h * BH) / 2;
    const pos = [];
    const uv = [];
    const idx = [];
    const cen = [];
    for (let i = 0; i <= N; i++) {
      const o = line(i / N);
      ray.set(o.clone().addScaledVector(along, -1.2 * BH), along);
      const hit = firstHit(ray, target);
      cen.push(hit ? { p: hit.point.clone(), n: hit.face.normal.clone() } : { p: o, n: along.clone().negate() });
    }
    const project = (q, n) => {
      ray.set(q.clone().addScaledVector(n, 0.3 * BH), n.clone().negate());
      const hit = firstHit(ray, target);
      return hit ? hit.point.clone().addScaledVector(hit.face.normal, 0.003) : q;
    };
    for (let i = 0; i <= N; i++) {
      const c = cen[i];
      const a = cen[Math.max(0, i - 1)];
      const b = cen[Math.min(N, i + 1)];
      const tan = b.p.clone().sub(a.p).normalize();
      const tp = top(c.p);
      const m = tp.clone().addScaledVector(c.n, -c.n.dot(tp)).addScaledVector(tan, -tan.dot(tp)).normalize();
      for (let j = 0; j <= 1; j++) {
        const q = project(c.p.clone().addScaledVector(m, j === 0 ? hh : -hh), c.n);
        pos.push(q.x, q.y, q.z);
        const t = i / N;
        const u = flipU ? 1 - t : t;
        const v = j === 0 ? 0 : 1;
        uv.push(rect[0] + (rect[2] - rect[0]) * u, 1 - (rect[1] + (rect[3] - rect[1]) * v));
      }
    }
    const p0 = new THREE.Vector3(pos[0], pos[1], pos[2]);
    const p1 = new THREE.Vector3(pos[3], pos[4], pos[5]);
    const p2 = new THREE.Vector3(pos[6], pos[7], pos[8]);
    const out = p1.sub(p0).cross(p2.sub(p0)).dot(cen[0].n) > 0;
    for (let i = 0; i < N; i++) {
      const a = i * 2;
      if (out) idx.push(a, a + 1, a + 2, a + 1, a + 3, a + 2);
      else idx.push(a, a + 2, a + 1, a + 1, a + 2, a + 3);
    }
    const g = new THREE.BufferGeometry();
    g.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3));
    g.setAttribute('uv', new THREE.Float32BufferAttribute(uv, 2));
    g.setIndex(idx);
    g.computeVertexNormals();
    return g;
  }

  const GEO = (() => {
    const shellG = shellGeometry();
    const target = new THREE.Mesh(shellG);
    target.updateMatrixWorld(true);
    const up = new THREE.Vector3(0, 1, 0);
    const out = { shell: shellG, face: faceGeometry() };
    out.edge = discGeometry(BUD.Rd * 0.985, BUD.Rd, 128, (rr) => D.proud * 0.4 + D.sag * (1 - rr));
    out.groove = discGeometry(BUD.Rd, BUD.Rd + D.groove, 128, () => 0.001);
    Object.assign(out, tipGeometries());
    out.boss = bossGeometries();
    out.slit = slitGeometries(target);

    const lg = BUD.logo;
    const logoLine = (t) => new THREE.Vector3(0, (lg.y0 + (lg.y1 - lg.y0) * t) * BH, lg.z * BH);
    out.logoR = stripOn(target, logoLine, new THREE.Vector3(-1, 0, 0), () => new THREE.Vector3(0, 0, 1), lg.h, ATL.sony, false);
    out.logoL = stripOn(target, logoLine, new THREE.Vector3(-1, 0, 0), () => new THREE.Vector3(0, 0, 1), lg.h, ATL.sony, true);

    const inr = BUD.inner;
    out.contacts = inr.contacts.map((c) => surfacePoint(target, c, 1));
    const sens = surfacePoint(target, inr.sensor, 1);
    out.sensor = conformal(target, sens.p, sens.n, up, inr.sensorW * BH, inr.sensorH * BH, { n: 12, off: 0.004 });
    const mk = surfacePoint(target, inr.mark, 1);
    out.markR = conformal(target, mk.p, mk.n, up, inr.markD * BH, inr.markD * BH, { n: 8, off: 0.003, rect: ATL.markR });
    out.markL = conformal(target, mk.p, mk.n, up, inr.markD * BH, inr.markD * BH, { n: 8, off: 0.003, rect: ATL.markL, flipU: true });
    out.dotL = surfacePoint(target, inr.dotL, 1);
    return out;
  })();

  function buildBud(side, inCase = false) {
    const g = new THREE.Group();
    g.name = 'X4_Bud' + side;
    const mirror = side === 'L';
    const shell = new THREE.Mesh(GEO.shell, M.shell); shell.name = 'X4_BudShell';
    const face = new THREE.Mesh(GEO.face, M.face); face.name = 'X4_BudFace';
    const edge = new THREE.Mesh(GEO.edge, M.discEdge); edge.name = 'X4_DiscEdge';
    const groove = new THREE.Mesh(GEO.groove, M.groove); groove.name = 'X4_DiscGroove';
    g.add(shell, face, edge, groove);

    const b = GEO.boss;
    for (const [geo, mat, name] of [
      [b.collar, M.collar, 'X4_BossCollar'],
      [b.side, M.metal, 'X4_Boss'],
      [b.face, M.metalFace, 'X4_BossFace'],
      [b.black, M.metalIn, 'X4_BossRing'],
      [b.centre, M.metalFace, 'X4_BossCentre'],
    ]) {
      g.add(Object.assign(new THREE.Mesh(geo, mat), { name }));
    }

    g.add(Object.assign(new THREE.Mesh(GEO.slit.rim, M.slitRim), { name: 'X4_SlitRim' }));
    g.add(Object.assign(new THREE.Mesh(GEO.slit.core, M.slitCore), { name: 'X4_Slit' }));
    const logo = Object.assign(new THREE.Mesh(mirror ? GEO.logoL : GEO.logoR, M.print), { name: 'X4_Logo' });
    g.add(logo);

    if (!inCase) {
      for (const c of GEO.contacts) {
        const m = new THREE.Mesh(
          new THREE.CylinderGeometry((BUD.inner.contactD * BH) / 2, (BUD.inner.contactD * BH) / 2, 0.008 * BH, 18),
          M.contact,
        );
        m.position.copy(c.p);
        m.quaternion.setFromUnitVectors(new THREE.Vector3(0, 1, 0), c.n);
        m.name = 'X4_Contact';
        g.add(m);
      }
      g.add(Object.assign(new THREE.Mesh(GEO.sensor, M.sensor), { name: 'X4_Sensor' }));
      g.add(Object.assign(new THREE.Mesh(mirror ? GEO.markL : GEO.markR, mirror ? M.markL : M.markR), { name: 'X4_Mark' }));
      if (mirror) {
        const dp = GEO.dotL;
        const dot = new THREE.Mesh(new THREE.SphereGeometry((BUD.inner.dotD * BH) / 2, 14, 8), M.shell);
        dot.position.copy(dp.p);
        dot.scale.set(1, 1, 0.45);
        dot.lookAt(dp.p.clone().add(dp.n));
        dot.name = 'X4_TactileDot';
        g.add(dot);
      }
    }
    const tip = Object.assign(new THREE.Mesh(GEO.tip, M.tip), { name: 'X4_Tip' });
    g.add(tip);
    g.add(Object.assign(new THREE.Mesh(GEO.hole, M.tipHole), { name: 'X4_TipHole' }));
    g.add(Object.assign(new THREE.Mesh(GEO.stem, M.nozzle), { name: 'X4_Stem' }));
    g.userData = { shell, face, logo, tip };
    if (mirror) g.scale.x = -1;
    return g;
  }

  // ---------------------------------------------------------------------------------------------
  // Double-walled solid charging case
  const PLAN = (() => {
    const r0 = CASE.B * CASE.endR;
    const sx = CASE.A - r0;
    const sz = CASE.B - r0;
    const segs = [sz, (Math.PI * r0) / 2, 2 * sx, (Math.PI * r0) / 2, 2 * sz, (Math.PI * r0) / 2, 2 * sx, (Math.PI * r0) / 2, sz];
    const tot = segs.reduce((a, b) => a + b, 0);
    const cum = [0];
    for (const l of segs) cum.push(cum[cum.length - 1] + l / tot);
    return { cum };
  })();
  const planR = (B) => Math.max(1e-5, Math.min(B, CASE.B * CASE.endR * Math.min(1, B / CASE.B)));

  function planPt(A, B, u) {
    const r = planR(B);
    const sx = Math.max(0, A - r);
    const sz = Math.max(0, B - r);
    u = ((u % 1) + 1) % 1;
    const c = PLAN.cum;
    let k = 0;
    while (k < 8 && u >= c[k + 1]) k++;
    const t = (u - c[k]) / Math.max(1e-9, c[k + 1] - c[k]);
    const arc = (cx, cz, a0) => {
      const a = a0 + (t * Math.PI) / 2;
      return [cx + r * Math.cos(a), cz + r * Math.sin(a)];
    };
    switch (k) {
      case 0: return [A, sz * t];
      case 1: return arc(sx, sz, 0);
      case 2: return [sx - 2 * sx * t, B];
      case 3: return arc(-sx, sz, Math.PI / 2);
      case 4: return [-A, sz - 2 * sz * t];
      case 5: return arc(-sx, -sz, Math.PI);
      case 6: return [-sx + 2 * sx * t, -B];
      case 7: return arc(sx, -sz, 1.5 * Math.PI);
      default: return [A, -sz + sz * t];
    }
  }

  function outerInset(y) {
    const C = CASE;
    const b = C.bottom;
    const tp = C.top;
    if (y <= C.yP) {
      const taper = C.taper * Math.min(1, Math.max(0, (C.yP - y) / Math.max(1e-6, C.yP - b.v)));
      if (y >= b.v) return [taper, taper];
      const k = 1 - Math.sqrt(Math.max(0, 1 - Math.pow(1 - y / b.v, 2)));
      return [C.taper + b.hx * k, C.taper + b.hz * k];
    }
    const yS = C.H - tp.v;
    if (y <= yS) return [-C.lidGrow, -C.lidGrow];
    const k = 1 - Math.sqrt(Math.max(0, 1 - Math.pow((y - yS) / tp.v, 2)));
    return [-C.lidGrow + tp.hx * k, -C.lidGrow + tp.hz * k];
  }

  const NCASE = 192;
  function ringsGeometry(rings, opts = {}) {
    const N = NCASE;
    const pos = [];
    const uv = [];
    const idx = [];
    rings.forEach((rg, j) => {
      const A = CASE.A - (rg.dx ?? rg.d);
      const B = CASE.B - (rg.dz ?? rg.d);
      for (let i = 0; i < N; i++) {
        const [x, z] = planPt(Math.max(A, 1e-4), Math.max(B, 1e-4), i / N);
        pos.push(x, rg.y, z);
        uv.push((i / N) * 2, (j / Math.max(1, rings.length - 1)) * 2);
      }
    });
    for (let j = 0; j < rings.length - 1; j++) {
      for (let i = 0; i < N; i++) {
        const i1 = (i + 1) % N;
        const a = j * N + i;
        const b = j * N + i1;
        const c = (j + 1) * N + i;
        const d = (j + 1) * N + i1;
        if (opts.flip) idx.push(a, b, c, b, d, c);
        else idx.push(a, c, b, b, c, d);
      }
    }
    if (opts.capEnd) {
      const cIdx = pos.length / 3;
      const last = rings[rings.length - 1];
      pos.push(0, last.y + (opts.capRise || 0), 0);
      uv.push(0.5, 0.5);
      const j = rings.length - 1;
      for (let i = 0; i < N; i++) {
        const a = j * N + i;
        const b = j * N + ((i + 1) % N);
        if (opts.flip) idx.push(a, b, cIdx);
        else idx.push(a, cIdx, b);
      }
    }
    const g = new THREE.BufferGeometry();
    g.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3));
    g.setAttribute('uv', new THREE.Float32BufferAttribute(uv, 2));
    g.setIndex(idx);
    g.computeVertexNormals();
    // Weld polar seam vertices at i = 0 across rings for smooth wrap shading.
    const nor = g.attributes.normal;
    if (nor && rings.length > 1) {
      for (let j = 0; j < rings.length; j++) {
        const idx0 = j * N;
        const idx1 = j * N + N - 1;
        const nx = nor.getX(idx0) + nor.getX(idx1);
        const ny = nor.getY(idx0) + nor.getY(idx1);
        const nz = nor.getZ(idx0) + nor.getZ(idx1);
        const len = Math.hypot(nx, ny, nz) || 1;
        nor.setXYZ(idx0, nx / len, ny / len, nz / len);
      }
      nor.needsUpdate = true;
    }
    return g;
  }

  function baseOuterRings(t = 0) {
    const C = CASE;
    const b = C.bottom;
    const rings = [];
    const yTop = C.yP - C.gap / 2;
    for (let k = 0; k <= 6; k++) {
      const y = yTop - (yTop - b.v) * (k / 6);
      const [dx, dz] = outerInset(y);
      rings.push({ dx: t + dx, dz: t + dz, y });
    }
    for (let k = 1; k <= 28; k++) {
      const a = (k / 28) * (Math.PI / 2);
      const y = t + (b.v - t) * (1 - Math.sin(a));
      rings.push({ dx: t + C.taper + b.hx * (1 - Math.cos(a)), dz: t + C.taper + b.hz * (1 - Math.cos(a)), y });
    }
    const A0 = C.A - t - C.taper - b.hx;
    const B0 = C.B - t - C.taper - b.hz;
    for (const s of [0.85, 0.65, 0.42, 0.20, 0.002]) {
      rings.push({ dx: C.A - A0 * s, dz: C.B - B0 * s, y: t + (b.dish || 0) * (1 - s * s) });
    }
    return rings;
  }

  function lidOuterRings(t0 = 0) {
    const C = CASE;
    const tp = C.top;
    const rings = [];
    const t = t0 - C.lidGrow;
    const y0 = C.yP + C.gap / 2;
    const yTop = C.H - t0;
    const yS = yTop - tp.v;
    rings.push({ dx: t, dz: t, y: y0 });
    if (yS > y0) {
      for (let k = 1; k <= 3; k++) rings.push({ dx: t, dz: t, y: y0 + (yS - y0) * (k / 3) });
    }
    const vv = yTop - Math.max(yS, y0);
    for (let k = 1; k <= 30; k++) {
      const a = (k / 30) * (Math.PI / 2);
      rings.push({
        dx: t + tp.hx * (1 - Math.cos(a)),
        dz: t + tp.hz * (1 - Math.cos(a)),
        y: yTop - vv * (1 - Math.sin(a)),
      });
    }
    const A0 = C.A - t - tp.hx;
    const B0 = C.B - t - tp.hz;
    for (const s of [0.90, 0.72, 0.48, 0.22, 0.002]) {
      rings.push({ dx: C.A - A0 * s, dz: C.B - B0 * s, y: yTop + tp.dome * (1 - s * s) });
    }
    return rings;
  }

  function rimGeometry(y, dOut, dIn) {
    return ringsGeometry([{ d: dOut, y }, { d: dIn, y }]);
  }

  function caseInside(x, y, z, margin = 0) {
    const C = CASE;
    const t = C.wall + margin;
    if (y < t || y > C.H - t) return false;
    const [dx, dz] = y > C.yP ? outerInset(Math.min(C.H, y + t)) : outerInset(Math.max(0, y - t));
    const A = C.A - t - dx;
    const B = C.B - t - dz;
    if (A <= 0 || B <= 0) return false;
    const r = planR(B);
    const qx = Math.abs(x) - (A - r);
    const qz = Math.abs(z) - (B - r);
    return Math.hypot(Math.max(qx, 0), Math.max(qz, 0)) + Math.min(Math.max(qx, qz), 0) - r <= 0;
  }

  const seat = CASE.budSeat;
  const seatM = (() => {
    const bx = V3(seat.x).normalize();
    const by = V3(seat.y).normalize();
    const bz = new THREE.Vector3().crossVectors(bx, by).normalize();
    const byOrtho = new THREE.Vector3().crossVectors(bz, bx).normalize();
    return new THREE.Matrix4()
      .makeRotationFromEuler(new THREE.Euler(rad(seat.tilt[0]), rad(seat.tilt[1]), rad(seat.tilt[2]), 'ZXY'))
      .multiply(new THREE.Matrix4().makeBasis(bx, byOrtho, bz));
  })();

  const wellCentres = [
    { x: seat.pos[0], z: seat.pos[2] },
    { x: -seat.pos[0], z: seat.pos[2] },
  ];

  // Sculpted interior deck: raised center bridge + two deep contoured L/R wells.
  function deckAndWells() {
    const C = CASE;
    const t = C.wall;
    const yDeck = C.yP - C.deck;
    const [dx, dz] = outerInset(yDeck);
    const shape = new THREE.Shape();
    const N = 96;
    for (let i = 0; i <= N; i++) {
      const [x, z] = planPt(C.A - t - dx, C.B - t - dz, i / N);
      if (i === 0) shape.moveTo(x, z);
      else shape.lineTo(x, z);
    }
    const rx = C.well.rx;
    const rz = C.well.rz;
    for (const w of wellCentres) {
      const hole = new THREE.Path();
      for (let i = 0; i <= 48; i++) {
        const a = -(i / 48) * Math.PI * 2;
        const x = w.x + Math.cos(a) * rx;
        const z = w.z + Math.sin(a) * rz;
        if (i === 0) hole.moveTo(x, z);
        else hole.lineTo(x, z);
      }
      shape.holes.push(hole);
    }
    const deck = new THREE.ShapeGeometry(shape, 1);
    deck.rotateX(Math.PI / 2);
    deck.translate(0, yDeck, 0);
    const ix = deck.index.array;
    for (let i = 0; i < ix.length; i += 3) {
      const tmp = ix[i];
      ix[i] = ix[i + 2];
      ix[i + 2] = tmp;
    }
    // Keep deck rim planar with well rims so zero seam tears appear; center bridge is a separate saddle mesh.
    deck.computeVertexNormals();

    const wells = wellCentres.map((w) => {
      const pts = [];
      for (let k = 0; k <= 12; k++) {
        const s = k / 12;
        pts.push(new THREE.Vector2(Math.max(0.01, 1 - 0.60 * Math.pow(s, 1.15)), -C.well.depth * s));
      }
      pts.push(new THREE.Vector2(0.0001, -C.well.depth));
      const g = new THREE.LatheGeometry(pts, 48);
      g.scale(rx, 1, rz);
      g.translate(w.x, yDeck, w.z);
      return g;
    });
    return { deck, wells, yDeck };
  }

  // Molded interior liner for lid with twin oval/circular pockets matching Help Guide diagram.
  function lidPocketGeometry(sideX) {
    const pts = [];
    const depth = 0.72;
    for (let k = 0; k <= 10; k++) {
      const s = k / 10;
      pts.push(new THREE.Vector2(Math.max(0.01, 1 - 0.55 * s * s), depth * s));
    }
    pts.push(new THREE.Vector2(0.0001, depth));
    const g = new THREE.LatheGeometry(pts, 40);
    g.scale(0.95, 1, 0.85);
    g.translate(sideX, CASE.yP + 0.14, 0.02);
    return g;
  }

  function buildCase() {
    const C = CASE;
    const root = new THREE.Group();
    root.name = 'X4_Case';
    const spin = new THREE.Group();
    root.add(spin);
    const base = new THREE.Group();
    base.name = 'X4_CaseBase';
    spin.add(base);

    const baseOuter = new THREE.Mesh(ringsGeometry(baseOuterRings(), { capEnd: true, flip: true }), M.caseShell);
    baseOuter.name = 'X4_CaseBaseShell';
    const yRim = C.yP - C.gap / 2;
    const [rdx] = outerInset(yRim);
    const [ddx] = outerInset(yRim - C.deck);
    const rim = new THREE.Mesh(rimGeometry(yRim, rdx, rdx + C.wall), M.caseBaseRim);
    const innerWall = new THREE.Mesh(
      ringsGeometry([{ d: rdx + C.wall, y: yRim }, { d: ddx + C.wall, y: yRim - C.deck }]),
      M.caseInner,
    );
    const dw = deckAndWells();
    const deck = new THREE.Mesh(dw.deck, M.caseInner);
    deck.name = 'X4_CaseDeck';
    const bridgeGeo = new THREE.CapsuleGeometry(0.16, 1.05, 8, 18);
    bridgeGeo.rotateX(Math.PI / 2);
    const bridgeMesh = new THREE.Mesh(bridgeGeo, M.caseInner);
    bridgeMesh.scale.set(1.25, 0.55, 1.0);
    bridgeMesh.position.set(0, dw.yDeck + 0.02, 0);
    bridgeMesh.name = 'X4_CaseBridge';
    base.add(baseOuter, rim, innerWall, deck, bridgeMesh);

    for (const wg of dw.wells) {
      const wm = new THREE.Mesh(wg, M.caseInner);
      wm.name = 'X4_CaseWell';
      base.add(wm);
    }

    // 3 gold charging pogo pins inside each well (matching Help Guide diagram).
    for (const w of wellCentres) {
      for (const [dx, dz] of [[-0.12, -0.08], [0.0, 0.09], [0.12, -0.08]]) {
        const pin = new THREE.Mesh(new THREE.CylinderGeometry(0.026, 0.026, 0.06, 12), M.pin);
        pin.position.set(w.x + dx * Math.sign(w.x || 1), dw.yDeck - C.well.depth * 0.78, w.z + dz);
        pin.name = 'X4_WellPin';
        base.add(pin);
      }
    }

    // Front slim finger-opening recess + recessed pill LED bar.
    const notchG = new THREE.BoxGeometry(C.notch.w, C.notch.h, 0.08);
    const notchMesh = new THREE.Mesh(notchG, M.caseRecess);
    notchMesh.position.set(0, C.notch.y, C.B - 0.028);
    notchMesh.name = 'X4_FrontNotch';
    base.add(notchMesh);

    const ledMesh = new THREE.Mesh(new THREE.CapsuleGeometry(C.led.h / 2, C.led.w - C.led.h, 6, 12), M.led);
    ledMesh.rotation.z = Math.PI / 2;
    ledMesh.scale.set(1, 1, 0.35);
    ledMesh.position.set(0, C.led.y, C.B + 0.002);
    ledMesh.name = 'X4_LED';
    base.add(ledMesh);

    // Rear stepped hinge knuckle block & recessed 3D USB-C port.
    const hingeBlock = new THREE.Mesh(new THREE.BoxGeometry(C.hinge.w, C.hinge.h, C.hinge.depth), M.caseRim);
    hingeBlock.position.set(0, C.yP, -C.B + C.hinge.depth * 0.42);
    hingeBlock.name = 'X4_RearHinge';
    base.add(hingeBlock);

    const usbGroup = new THREE.Group();
    usbGroup.name = 'X4_USBGroup';
    const [udx, udz] = outerInset(C.usb.y);
    const zBack = -(C.B - udz);
    usbGroup.position.set(0, C.usb.y, zBack);

    const usbSleeve = new THREE.Mesh(new THREE.CapsuleGeometry(C.usb.h * 0.48, C.usb.w - C.usb.h, 8, 16), M.portShell);
    usbSleeve.rotation.z = Math.PI / 2;
    usbSleeve.scale.set(1, 1, 0.14);
    usbSleeve.position.set(0, 0, -0.008);
    usbSleeve.name = 'X4_USBShell';

    const usbBore = new THREE.Mesh(new THREE.CapsuleGeometry(C.usb.h * 0.36, C.usb.w - C.usb.h, 8, 16), M.portCavity);
    usbBore.rotation.z = Math.PI / 2;
    usbBore.scale.set(1, 1, 0.10);
    usbBore.position.set(0, 0, -0.014);
    usbBore.name = 'X4_USB';

    const usbTongue = new THREE.Mesh(new THREE.BoxGeometry(C.usb.w * 0.62, C.usb.h * 0.22, 0.025), M.portTongue);
    usbTongue.position.set(0, 0, -0.016);
    usbTongue.name = 'X4_USBTongue';

    usbGroup.add(usbSleeve, usbBore, usbTongue);
    base.add(usbGroup);

    // Hinged upper lid with solid inner wall, molded twin dome pockets, and top SONY logo.
    const lid = new THREE.Group();
    lid.name = 'X4_Lid';
    const hingeZ = -C.B + 0.14;
    lid.position.set(0, C.yP, hingeZ);
    spin.add(lid);

    const lidBody = new THREE.Group();
    lidBody.position.set(0, -C.yP, -hingeZ);
    lid.add(lidBody);

    const lidOuter = new THREE.Mesh(ringsGeometry(lidOuterRings(), { capEnd: true, capRise: CASE.top.dome * 0.02 }), M.caseShell);
    lidOuter.name = 'X4_LidShell';
    const lidInner = new THREE.Mesh(ringsGeometry(lidOuterRings(C.wall), { capEnd: true, flip: true }), M.caseInner);
    lidInner.name = 'X4_LidInner';
    const lidRim = new THREE.Mesh(rimGeometry(C.yP + C.gap / 2, C.wall - C.lidGrow, -C.lidGrow), M.caseRim);

    // Twin molded pockets + lid interior plate closing bottom rim cleanly.
    const lidPlateShape = new THREE.Shape();
    for (let i = 0; i <= 96; i++) {
      const [px, pz] = planPt(C.A - C.wall + C.lidGrow, C.B - C.wall + C.lidGrow, i / 96);
      if (i === 0) lidPlateShape.moveTo(px, pz);
      else lidPlateShape.lineTo(px, pz);
    }
    for (const sx of [wellCentres[0].x, wellCentres[1].x]) {
      const h = new THREE.Path();
      for (let i = 0; i <= 40; i++) {
        const a = -(i / 40) * Math.PI * 2;
        const px = sx + Math.cos(a) * 0.95;
        const pz = 0.02 + Math.sin(a) * 0.85;
        if (i === 0) h.moveTo(px, pz);
        else h.lineTo(px, pz);
      }
      lidPlateShape.holes.push(h);
    }
    const lidPlateG = new THREE.ShapeGeometry(lidPlateShape, 1);
    lidPlateG.rotateX(Math.PI / 2);
    lidPlateG.translate(0, C.yP + 0.14, 0);
    const lix = lidPlateG.index.array;
    for (let i = 0; i < lix.length; i += 3) {
      const tmp = lix[i];
      lix[i] = lix[i + 2];
      lix[i + 2] = tmp;
    }
    lidPlateG.computeVertexNormals();
    const lidPlate = new THREE.Mesh(lidPlateG, M.casePocket);
    lidPlate.name = 'X4_LidPlate';

    const pocketL = new THREE.Mesh(lidPocketGeometry(wellCentres[1].x), M.casePocket);
    const pocketR = new THREE.Mesh(lidPocketGeometry(wellCentres[0].x), M.casePocket);
    pocketL.name = 'X4_LidPocketL';
    pocketR.name = 'X4_LidPocketR';

    const logoW = C.logo.w;
    const logoH = (logoW * (ATL.sony[3] - ATL.sony[1]) * 512) / ((ATL.sony[2] - ATL.sony[0]) * 1024);
    const lidLogo = new THREE.Mesh(
      conformal(
        lidOuter,
        new THREE.Vector3(0, C.H, C.logo.z),
        new THREE.Vector3(0, 1, 0),
        new THREE.Vector3(0, 0, -1),
        logoW,
        logoH,
        { n: 12, off: 0.004, rect: ATL.sony },
      ),
      M.casePrint,
    );
    lidLogo.name = 'X4_CaseLogo';
    lidBody.add(lidOuter, lidInner, lidRim, lidPlate, pocketL, pocketR, lidLogo);

    // Earbuds seated cleanly in separate L/R wells with exact X-plane mirror symmetry.
    const anchors = [];
    const seatQuatR = new THREE.Quaternion().setFromRotationMatrix(seatM);
    const mirrorM = new THREE.Matrix4().makeScale(-1, 1, 1);
    const seatQuatL = new THREE.Quaternion().setFromRotationMatrix(
      new THREE.Matrix4().multiplyMatrices(mirrorM, seatM).multiply(mirrorM),
    );
    for (const side of ['R', 'L']) {
      const s = side === 'R' ? 1 : -1;
      const anchor = new THREE.Group();
      anchor.name = 'X4_Seat' + side;
      anchor.position.set(seat.pos[0] * s, seat.pos[1], seat.pos[2]);
      anchor.quaternion.copy(side === 'R' ? seatQuatR : seatQuatL);
      base.add(anchor);
      anchors.push({ node: anchor, y: anchor.position.y, side });
    }
    return { root, spin, base, lid, anchors, parts: { baseOuter, lidOuter, lidInner, rim, lidRim, innerWall, deck, led: ledMesh } };
  }

  // ---------------------------------------------------------------------------------------------
  // Hero assembly
  const budsRoot = new THREE.Group();
  budsRoot.name = 'wf1000xm4_buds';
  const pivotR = new THREE.Group();
  const pivotL = new THREE.Group();
  pivotR.position.set(...HERO.posR);
  pivotL.position.set(...HERO.posL);
  budsRoot.position.y = HERO.dy;
  const budR = buildBud('R');
  const budL = buildBud('L');
  budR.rotation.set(rad(HERO.rot[0]), rad(HERO.rot[1]), rad(HERO.rot[2]));
  budL.rotation.set(rad(HERO.rot[0]), rad(-HERO.rot[1]), rad(-HERO.rot[2]));
  budR.scale.multiplyScalar(HERO.s);
  budL.scale.multiplyScalar(HERO.s);
  pivotR.add(budR);
  pivotL.add(budL);
  for (const [pivot, bud] of [[pivotR, budR], [pivotL, budL]]) {
    pivot.updateMatrixWorld(true);
    const c = new THREE.Box3().setFromObject(bud).getCenter(new THREE.Vector3()).sub(pivot.position);
    c.y = 0;
    bud.position.sub(c);
    pivot.position.add(c);
  }
  const pivotRestR = pivotR.position.clone();
  const pivotRestL = pivotL.position.clone();
  budsRoot.add(pivotR, pivotL);

  const budInvR = new THREE.Matrix4().compose(budR.position, budR.quaternion, budR.scale).invert();
  const budInvL = new THREE.Matrix4().compose(budL.position, budL.quaternion, budL.scale).invert();

  const kase = buildCase();
  kase.root.matrixAutoUpdate = false;
  const caseS = (HERO.caseS * 6.48) / (2 * CASE.A);
  const CASE_REST_MAT = new THREE.Matrix4()
    .makeTranslation(0, HERO.caseDy, 0)
    .multiply(ctx.caseRest)
    .multiply(new THREE.Matrix4().makeScale(caseS, caseS, caseS));
  kase.root.matrix.copy(CASE_REST_MAT);

  for (const an of kase.anchors) {
    const b = buildBud(an.side, true);
    b.userData.inCase = true;
    b.scale.multiplyScalar(CASE.budSeat.scale);
    b.userData.face.material = M.faceCase;
    b.userData.shell.material = M.shellCase;
    an.node.add(b);
    an.bud = b;
  }

  // ---------------------------------------------------------------------------------------------
  // Finishes
  function applyFinish(f) {
    const set = (c, a) => c.setRGB(a[0], a[1], a[2]);
    set(M.shell.color, f.shell);
    set(M.face.color, f.face);
    M.face.userData.uniforms.uSparkle.value = f.sparkle[0];
    const sh = f.seatShade[0];
    M.shellCase.color.setRGB(f.shell[0] * sh, f.shell[1] * sh, f.shell[2] * sh);
    M.faceCase.color.setRGB(f.face[0] * sh, f.face[1] * sh, f.face[2] * sh);
    M.discEdge.color.setRGB(Math.min(1, f.face[0] * 1.18 + 0.015), Math.min(1, f.face[1] * 1.18 + 0.015), Math.min(1, f.face[2] * 1.18 + 0.015));
    M.groove.color.setRGB(f.shell[0] * 0.35, f.shell[1] * 0.35, f.shell[2] * 0.35);
    M.collar.color.setRGB(f.shell[0] * 0.72, f.shell[1] * 0.72, f.shell[2] * 0.72);
    for (const m of [M.metal, M.metalFace, M.contact]) set(m.color, f.metal);
    set(M.slitRim.color, f.slitRim);
    set(M.metalIn.color, f.metalIn);
    set(M.print.color, f.print);
    set(M.tip.color, f.tip);
    M.caseShell.color.setRGB(f.caseShell[0], f.caseShell[1], f.caseShell[2]);
    M.caseInner.color.setRGB(f.caseInner[0], f.caseInner[1], f.caseInner[2]);
    M.casePocket.color.setRGB(f.caseInner[0] * 0.86, f.caseInner[1] * 0.86, f.caseInner[2] * 0.86);
    M.caseRecess.color.setRGB(f.caseShell[0] * 0.60, f.caseShell[1] * 0.60, f.caseShell[2] * 0.60);
    M.caseBaseRim.color.setRGB(f.caseShell[0] * 0.84, f.caseShell[1] * 0.84, f.caseShell[2] * 0.84);
    M.caseRim.color.setRGB(f.caseInner[0] * 0.75, f.caseInner[1] * 0.75, f.caseInner[2] * 0.75);
    set(M.casePrint.color, f.casePrint);
    for (const [m, k] of [
      [M.shell, 'shell'],
      [M.face, 'face'],
      [M.caseShell, 'caseShell'],
      [M.caseInner, 'caseInner'],
      [M.shellCase, 'shell'],
      [M.faceCase, 'face'],
      [M.tip, 'tip'],
    ]) {
      m.emissive.setRGB(f[k][0] * f.lift[0], f[k][1] * f.lift[1], f[k][2] * f.lift[2]);
    }
  }
  const finishCopy = makeFinishCopy ? makeFinishCopy(FINISHES, KEYS) : (name, from) => {
    const src = from || FINISHES[name] || FINISHES.black;
    return Object.fromEntries(KEYS.map((k) => [k, src[k].slice()]));
  };

  // ---------------------------------------------------------------------------------------------
  // Pose & continuous 3D unpack/pack choreography
  const LID_OPEN = rad(CASE.lidOpenDeg);
  const pair = { R: 0, L: 0 };
  const _vUp = new THREE.Vector3(0, 1, 0);
  const _p0 = new THREE.Vector3();
  const _p1 = new THREE.Vector3();
  const _s0 = new THREE.Vector3();
  const _s1 = new THREE.Vector3();
  const _q0 = new THREE.Quaternion();
  const _q1 = new THREE.Quaternion();
  const _m0 = new THREE.Matrix4();
  const _mRootInv = new THREE.Matrix4();
  const _mDrop = new THREE.Matrix4();
  const _mDropScale = new THREE.Matrix4();

  function applyPose(st, active) {
    if (!active) {
      budsRoot.visible = false;
      kase.root.visible = false;
      return;
    }
    const a = rad(st.angle);
    const hasProgress = st.caseProgress !== undefined && st.caseProgress !== null;
    if (!hasProgress) {
      kase.root.matrix.copy(CASE_REST_MAT);
      pivotR.position.copy(pivotRestR); pivotR.rotation.set(0, a, 0); pivotR.scale.set(1, 1, 1);
      pivotL.position.copy(pivotRestL); pivotL.rotation.set(0, -a, 0); pivotL.scale.set(1, 1, 1);
      kase.spin.rotation.y = a;
      kase.lid.rotation.x = -LID_OPEN * st.lid;
      for (const an of kase.anchors) {
        an.node.position.y = an.y - CASE.budSeat.drop * (1 - Math.min(1, st.lid * 1.6)) + (pair[an.side] || 0);
        an.node.visible = true;
      }
      budsRoot.visible = st.model === 'buds';
      kase.root.visible = st.model === 'case';
      return;
    }

    const p = clamp01(st.caseProgress);
    if (p <= 0.0005) {
      kase.root.visible = false;
      kase.root.matrix.copy(CASE_REST_MAT);
      pivotR.position.copy(pivotRestR); pivotR.rotation.set(0, a, 0); pivotR.scale.set(1, 1, 1);
      pivotL.position.copy(pivotRestL); pivotL.rotation.set(0, -a, 0); pivotL.scale.set(1, 1, 1);
      budsRoot.visible = true;
      return;
    }

    if (p >= 0.72) {
      const lidT = smooth(clamp01((1 - p) / 0.28));
      kase.root.matrix.copy(CASE_REST_MAT);
      kase.spin.rotation.y = a;
      kase.lid.rotation.x = -LID_OPEN * lidT;
      for (const an of kase.anchors) {
        an.node.position.y = an.y - CASE.budSeat.drop * (1 - Math.min(1, lidT * 1.6)) + (pair[an.side] || 0);
        an.node.visible = true;
      }
      kase.root.visible = true;
      budsRoot.visible = false;
      return;
    }

    const liftT = smooth(clamp01((0.72 - p) / 0.58));
    const dropT = smooth(clamp01((0.58 - p) / 0.44));

    kase.root.matrix.copy(CASE_REST_MAT);
    kase.spin.rotation.y = a;
    kase.lid.rotation.x = -LID_OPEN;
    for (const an of kase.anchors) {
      an.node.position.y = an.y + (pair[an.side] || 0);
      an.node.visible = false;
    }
    kase.root.updateMatrixWorld(true);
    budsRoot.updateWorldMatrix(true, false);
    _mRootInv.copy(budsRoot.matrixWorld).invert();

    const arcSin = Math.sin(Math.PI * liftT);
    for (const an of kase.anchors) {
      const isR = an.side === 'R';
      const pivot = isR ? pivotR : pivotL;
      const rest = isR ? pivotRestR : pivotRestL;
      const inv = isR ? budInvR : budInvL;
      const spinAng = isR ? a : -a;
      _m0.copy(an.bud.matrixWorld).multiply(inv).premultiply(_mRootInv);
      _m0.decompose(_p0, _q0, _s0);
      _p1.copy(rest);
      _q1.setFromAxisAngle(_vUp, spinAng);
      _s1.set(1, 1, 1);
      pivot.position.lerpVectors(_p0, _p1, liftT);
      pivot.position.y += arcSin * (isR ? 1.45 : 0.70);
      pivot.position.z += arcSin * (isR ? -0.40 : 0.90);
      pivot.quaternion.slerpQuaternions(_q0, _q1, liftT);
      pivot.scale.lerpVectors(_s0, _s1, liftT);
    }

    const sDrop = 1 - 0.35 * dropT;
    _mDropScale.makeScale(sDrop, sDrop, sDrop);
    _mDrop.makeTranslation(0, -5.2 * dropT, 0).multiply(CASE_REST_MAT).multiply(_mDropScale);
    kase.root.matrix.copy(_mDrop);
    kase.root.visible = dropT < 0.995;
    budsRoot.visible = true;
  }

  chunkCache = null;
  return {
    buds: { root: budsRoot, pivotL, pivotR, budL, budR },
    kase,
    handlesCaseProgress: true,
    precompileClosedBoth: true,
    FINISHES,
    KEYS,
    finishCopy,
    applyFinish,
    applyPose,
    LID_OPEN,
    ready,
    M,
    BUD,
    CASE,
    BH,
    pair,
    caseInside,
  };
}
