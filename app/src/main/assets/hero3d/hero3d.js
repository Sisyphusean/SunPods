// SunPods Home hero: the approved Blender earbuds (loop2/xm5_bud_v2.blend) and case
// (case_loop/xm5_case.blend), live in three.js r170, in Sony's three WF-1000XM5 finishes.
//
// Lighting and materials are translations of the Blender scenes the app renders came from
// (render_turntable.py / hero_render.py): same camera, same world, same lights, same shader maths.
//
// API (window.sunpods):
//   setColor('black' | 'silver' | 'pink' | 'blue', instant?) short crossfade between finishes
//   setModel('buds' | 'case')                           earbuds/headphones hero or charging/carry case
//   setLid(0..1, instant?)                              case lid, 0 closed, 1 open
//   setCaseProgress(0..1, instant?)                     continuous pack/unpack choreography (0 hero, 1 closed case)
//   playUnpack()  playPack()                            smooth unpack (1 -> 0) and pack (0 -> 1) across all devices
//   preload('xm5' | 'c710n' | 'wh1000xm5' | ...)        load and warm a device ahead of setDevice (no visible change)
//   setDevice(id, fadeMs?)  fadeMs: a slower fade for the Welcome page's cycle
//   wiggle()  spin()                                     the one-time hint and the TalkBack spin
//   URL params: ?device=xm5|c710n|wh1000xm5&color=...&model=...&lid=...&caseProgress=0..1&centered=0|1
// Android bridge (optional, JavascriptInterface "SunPodsHost"): ready(), settled(), tap(), unpacked(), packed().
import * as THREE from 'three';
import { GLTFLoader } from './lib/addons/loaders/GLTFLoader.js';
import { DRACOLoader } from './lib/addons/loaders/DRACOLoader.js';

const params = new URLSearchParams(location.search);
const DEBUG = params.has('debug');
const rad = (d) => d * Math.PI / 180;
const clamp01 = (x) => Math.min(1, Math.max(0, x));
const smooth = (t) => t * t * (3 - 2 * t);
const merge = (a, b) => {
  if (!b) return a;
  const o = Array.isArray(a) ? a.slice() : { ...a };
  for (const [k, v] of Object.entries(b)) {
    o[k] = v && typeof v === 'object' && !Array.isArray(v) && a[k] ? merge(a[k], v) : v;
  }
  return o;
};
const makeFinishCopy = (FINISHES, KEYS, fallback = 'black') => (name, from) => {
  const src = from || FINISHES[name] || FINISHES[fallback];
  return Object.fromEntries(KEYS.map((k) => [k, src[k].slice()]));
};

// ---------------------------------------------------------------------------------------------
// Finishes. Linear RGB, as in Blender. Black is the approved Blender material set, unchanged.
// Platinum Silver and Smoky Pink are solved from Sony's official product shots of all three
// colours (one shoot, same light): body albedo = (Sony value - our specular) / our irradiance,
// measured on the face, ear tip and case front in this page's own renders (calib_colors.py).
// The same solve gives the approved black face 0.158/0.170/0.166 against its 0.150/0.162/0.178.
// Silver needs a little more than albedo 1 to reach Sony's brightness; it is capped at 1.0.
// Grille and logo metals take Sony's colour-to-black ratios (case logo: silver reads darker gold,
// pink lighter rose than black's); badges follow Sony (grey L, red R).
const FINISHES = {
  black: {
    gloss: [0.008, 0.009, 0.010], satin: [0.150, 0.162, 0.178], hair: [0.120, 0.130, 0.140],
    grille: [0.8388, 0.491, 0.314], logo: [0.6795, 0.4851, 0.305],
    tip: [0.075, 0.077, 0.080], badge: [0.13, 0.135, 0.14], badgeR: [0.13, 0.135, 0.14], badgeL: [0.85, 0.86, 0.87],
    caseShell: [0.0169, 0.0223, 0.0271], caseInner: [0.012, 0.013, 0.015],
    caseLogo: [1.0, 0.62, 0.36], caseLogoEm: [0.6795, 0.4851, 0.305], led: [0.07, 0.07, 0.072],
  },
  silver: {   // Platinum Silver, colour code 0x03
    gloss: [0.881, 0.900, 0.862], satin: [0.979, 1.000, 0.958], hair: [0.979, 1.000, 0.958],
    grille: [0.852, 0.756, 0.373], logo: [0.792, 0.703, 0.347],
    tip: [0.310, 0.328, 0.292], badge: [0.50, 0.50, 0.50], badgeR: [0.55, 0.06, 0.055], badgeL: [0.85, 0.86, 0.87],
    caseShell: [0.971, 1.000, 0.937], caseInner: [0.874, 0.900, 0.843],
    caseLogo: [0.67, 0.45, 0.13], caseLogoEm: [0.455, 0.35, 0.11], led: [0.10, 0.10, 0.10],
  },
  pink: {     // Smoky Pink, colour code 0x06
    gloss: [0.780, 0.645, 0.644], satin: [0.867, 0.717, 0.715], hair: [0.867, 0.717, 0.715],
    grille: [0.972, 0.665, 0.476], logo: [0.904, 0.618, 0.443],
    tip: [0.230, 0.190, 0.190], badge: [0.50, 0.50, 0.50], badgeR: [0.55, 0.06, 0.055], badgeL: [0.85, 0.86, 0.87],
    caseShell: [0.828, 0.685, 0.690], caseInner: [0.745, 0.617, 0.621],
    caseLogo: [1.0, 0.75, 0.42], caseLogoEm: [0.79, 0.59, 0.35], led: [0.10, 0.10, 0.10],
  },
};
const COLOR_KEYS = Object.keys(FINISHES.black);

// ---------------------------------------------------------------------------------------------
// The hero scene, in Blender units and axes (converted below). render_turntable.py, TT_LENS=76.
const HERO = {
  lens: 76, sensor: 36, frameAspect: 1000 / 712,
  cam: [0, -15.5, 1.6], target: [0, 0.2, 0],
  world: 1.6,                                   // world background strength for non-camera rays
  // area lights seen only by diffuse rays (visible_glossy off in xm5_bud_v2.blend)
  diffuseLights: [
    { loc: [-6, -7.5, 6], rot: [58, 0, -38.7], size: 9, power: 650 },   // LGT_Key_Area
    { loc: [7.5, -4.5, 1.5], rot: [80.3, 0, 59], size: 9, power: 260 }, // LGT_Fill_Area
    { loc: [0, 3, 9], rot: [18.4, 0, 180], size: 9, power: 200 },       // LGT_Top_Area
  ],
  // LGT_Rim_App (app_pose.py): visible to glossy and diffuse, aimed at the target
  rim: { loc: [0, 7, 5], size: 4, power: 1100 },
};
// The closed case in the hero frame (hero_render.py REST: yaw -16, tilt 14, fitted scale)
const CASE_REST = [
  [0.582578, 0.162089, -0.040413, 0.159496],
  [-0.167052, 0.565273, -0.140938, 0.6],
  [0.0, 0.146618, 0.588053, -1.215789],
];
const LID_OPEN = rad(-116);   // EMPTY_Lid rotation X when open (hero_render.py)
const BUD_SEAT = -0.45;       // buds sit this much lower under the closed lid (hero_render.py)

// Blender (x, y, z) -> three.js (x, z, -y)
const b2t = (v) => new THREE.Vector3(v[0], v[2], -v[1]);
function blenderMatrixToThree(rows) {
  const m = new THREE.Matrix4().set(
    rows[0][0], rows[0][1], rows[0][2], rows[0][3],
    rows[1][0], rows[1][1], rows[1][2], rows[1][3],
    rows[2][0], rows[2][1], rows[2][2], rows[2][3],
    0, 0, 0, 1);
  const C = new THREE.Matrix4().set(1, 0, 0, 0, 0, 0, 1, 0, 0, -1, 0, 0, 0, 0, 0, 1);
  return C.clone().multiply(m).multiply(C.clone().transpose());
}

// ---------------------------------------------------------------------------------------------
// Renderer, camera
const canvas = document.getElementById('c');
const renderer = new THREE.WebGLRenderer({
  canvas, antialias: true, alpha: true, premultipliedAlpha: true, powerPreference: 'low-power',
});
renderer.setClearColor(0x000000, 0);
renderer.outputColorSpace = THREE.SRGBColorSpace;   // Blender "Standard" view transform
renderer.toneMapping = THREE.NoToneMapping;
const DPR_CAP = 2;

const scene = new THREE.Scene();
const camera = new THREE.PerspectiveCamera(20, 1, 1, 60);
camera.position.copy(b2t(HERO.cam));
camera.lookAt(b2t(HERO.target));

function fitCamera(w, h) {
  // Blender's AUTO sensor fit on the 1000x712 hero frame, kept whole inside any viewport.
  const aspect = w / h;
  const tanH = (HERO.sensor / 2) / HERO.lens;
  const tanV = aspect >= HERO.frameAspect ? tanH / HERO.frameAspect : tanH / aspect;
  camera.fov = 2 * Math.atan(tanV) * 180 / Math.PI;
  camera.aspect = aspect;
  camera.updateProjectionMatrix();
  if (centered && (shift.x || shift.y)) {
    // an off-axis shift of the frustum: the picture moves on screen, nothing else about it changes
    camera.projectionMatrix.elements[8] = shift.x;
    camera.projectionMatrix.elements[9] = shift.y;
    camera.projectionMatrixInverse.copy(camera.projectionMatrix).invert();
  }
}

// ---------------------------------------------------------------------------------------------
// Centring (the device picker and Welcome; Home keeps the hero framing). Each model's hero pose has
// its own offset, so the shown model's projected silhouette (every visible vertex at the rest pose)
// is measured and the frustum shifted until its box sits at the middle of the canvas, both ways.
let centered = params.get('centered') === '1';
const shift = { x: 0, y: 0 };
let viewW = 0, viewH = 0;
let forcedSize = null;
const ptsCache = new Map();
function activeRoot() {
  if (state.device === 'xm5') return buds.root;
  const s = mods[state.device];
  return s && s.m ? s.m.buds.root : null;
}
function heroPoints(root) {
  const key = state.device;
  if (ptsCache.has(key)) return ptsCache.get(key);
  const a0 = state.angle, cp0 = state.caseProgress, m0 = state.model, leg0 = legacyLidMode;
  state.angle = 0; state.caseProgress = 0; state.model = 'buds'; legacyLidMode = false;
  applyPose();
  root.updateWorldMatrix(true, true);
  const out = [], v = new THREE.Vector3();
  (function walk(o) {
    if (!o.visible) return;
    const pos = o.isMesh && o.geometry && o.geometry.attributes.position;
    if (pos) {
      const step = Math.max(1, Math.ceil(pos.count / 60000));
      for (let i = 0; i < pos.count; i += step) { v.fromBufferAttribute(pos, i).applyMatrix4(o.matrixWorld); out.push(v.x, v.y, v.z); }
    }
    for (const c of o.children) walk(c);
  })(root);
  state.angle = a0; state.caseProgress = cp0; state.model = m0; legacyLidMode = leg0;
  applyPose();
  const pts = new Float32Array(out);
  if (pts.length) ptsCache.set(key, pts);
  return pts;
}
function recenter() {
  shift.x = 0; shift.y = 0;
  if (viewW < 2 || viewH < 2) return;
  fitCamera(viewW, viewH);
  const root = centered && state.model === 'buds' ? activeRoot() : null;
  if (root) {
    const p = heroPoints(root);
    if (p.length) {
      camera.updateMatrixWorld();
      const e = new THREE.Matrix4().multiplyMatrices(camera.projectionMatrix, camera.matrixWorldInverse).elements;
      let x0 = Infinity, x1 = -Infinity, y0 = Infinity, y1 = -Infinity;
      for (let i = 0; i < p.length; i += 3) {
        const x = p[i], y = p[i + 1], z = p[i + 2];
        const w = e[3] * x + e[7] * y + e[11] * z + e[15];
        if (w <= 0) continue;
        const nx = (e[0] * x + e[4] * y + e[8] * z + e[12]) / w, ny = (e[1] * x + e[5] * y + e[9] * z + e[13]) / w;
        if (nx < x0) x0 = nx; if (nx > x1) x1 = nx; if (ny < y0) y0 = ny; if (ny > y1) y1 = ny;
      }
      if (x1 > x0) { shift.x = (x0 + x1) / 2; shift.y = (y0 + y1) / 2; fitCamera(viewW, viewH); }
    }
  }
  requestRender();
}
function resize() {
  // Size from the window, not CSS: Android's WebView starts at zero height, and 100%/100vh can
  // stay at zero after it grows. innerWidth/innerHeight always follow the real view.
  // The app pushes the WebView's own layout size (setSize) on every layout change; it wins over innerWidth,
  // which can lag behind when the shared view is moved to another screen.
  const w = forcedSize ? forcedSize.w : Math.max(1, window.innerWidth || canvas.clientWidth), h = forcedSize ? forcedSize.h : Math.max(1, window.innerHeight || canvas.clientHeight);
  canvas.style.width = w + 'px';
  canvas.style.height = h + 'px';
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, DPR_CAP));
  renderer.setSize(w, h, false);
  viewW = w; viewH = h;
  recenter();
  requestRender();
}

// ---------------------------------------------------------------------------------------------
// Lighting: the Blender world + rim light as an environment map (diffuse and glossy), and the
// three diffuse-only area lights as spherical harmonics (a light probe adds no reflections).
function v3(a) { return { x: a[0], y: a[1], z: a[2] }; }
function sub(a, b) { return { x: a.x - b.x, y: a.y - b.y, z: a.z - b.z }; }
function dot(a, b) { return a.x * b.x + a.y * b.y + a.z * b.z; }
function norm(a) { const l = Math.hypot(a.x, a.y, a.z); return { x: a.x / l, y: a.y / l, z: a.z / l }; }
function blenderEulerDir(r) {   // local -Z rotated by a Blender XYZ euler (degrees): Rz Ry Rx v
  let x = 0, y = 0, z = -1;
  const [ax, ay, az] = r.map(rad);
  let c = Math.cos(ax), s = Math.sin(ax); [y, z] = [c * y - s * z, s * y + c * z];
  c = Math.cos(ay); s = Math.sin(ay); [x, z] = [c * x + s * z, -s * x + c * z];
  c = Math.cos(az); s = Math.sin(az); [x, y] = [c * x - s * y, s * x + c * y];
  return { x, y, z };
}
function makeDisk(loc, dir, size, power) {
  const r = size / 2;
  return { c: v3(loc), n: dir, r, L: power / (4 * Math.PI * r * r) };   // Cycles: L = P / (4A)
}
const TGT = v3(HERO.target);
function diskRadiance(d, disks) {
  let L = 0;
  for (const k of disks) {
    const den = dot(d, k.n);
    if (den >= 0) continue;                       // single sided, emits along n
    const t = dot(sub(k.c, TGT), k.n) / den;
    if (t <= 0) continue;
    const p = { x: TGT.x + d.x * t - k.c.x, y: TGT.y + d.y * t - k.c.y, z: TGT.z + d.z * t - k.c.z };
    if (dot(p, p) <= k.r * k.r) L += k.L;
  }
  return L;
}
const CAMFWD = norm(sub(v3(HERO.target), v3(HERO.cam)));
function worldRadiance(d) {
  // ColorRamp on direction Z, mixed towards 0.4 around the camera's forward direction
  const t = clamp01((d.z + 1) / 2);
  const stops = [[0, 0.18], [0.5, 0.03], [0.62, 0.35], [1, 0.55]];
  let v = stops[stops.length - 1][1];
  for (let i = 0; i < stops.length - 1; i++) {
    const [t0, v0] = stops[i], [t1, v1] = stops[i + 1];
    if (t <= t1) { v = v0 + (v1 - v0) * clamp01((t - t0) / (t1 - t0)); break; }
  }
  const f = clamp01((dot(d, CAMFWD) - 0.78) / (0.95 - 0.78));
  return (v + (0.4 - v) * f) * HERO.world;
}
// three.js equirect texel -> Blender direction
function texelDir(u, v) {
  const phi = (u - 0.5) * 2 * Math.PI, th = (v - 0.5) * Math.PI;
  const x = Math.cos(th) * Math.cos(phi), y = Math.sin(th), z = Math.cos(th) * Math.sin(phi);
  return { x, y: -z, z: y };
}
function buildEnvironment() {
  // the world only: the rim light is evaluated analytically in the materials (see RIM_GLSL)
  const W = 512, H = 256;
  const data = new Uint16Array(W * H * 4);
  for (let j = 0; j < H; j++) {
    for (let i = 0; i < W; i++) {
      const L = worldRadiance(texelDir((i + 0.5) / W, (j + 0.5) / H));
      const h = THREE.DataUtils.toHalfFloat(L), o = (j * W + i) * 4;
      data[o] = data[o + 1] = data[o + 2] = h; data[o + 3] = 15360;   // 1.0
    }
  }
  const tex = new THREE.DataTexture(data, W, H, THREE.RGBAFormat, THREE.HalfFloatType);
  tex.mapping = THREE.EquirectangularReflectionMapping;
  tex.magFilter = tex.minFilter = THREE.LinearFilter;
  tex.generateMipmaps = false;
  tex.needsUpdate = true;
  const pmrem = new THREE.PMREMGenerator(renderer);
  const env = pmrem.fromEquirectangular(tex).texture;
  pmrem.dispose(); tex.dispose();
  return env;
}
// buildDiffuseProbe() output for HERO.diffuseLights (three.js SH basis, white light). Kept as
// numbers so the phone skips the integration; rerun the function if the lights ever change.
const DIFFUSE_SH = [0.640997, 0.540961, 0.447641, -0.010888, -0.211384, 0.308576, -0.019135, -0.10906, 0.006165];
function diffuseProbe() {
  if (DEBUG && params.has('rebuildProbe')) return buildDiffuseProbe();
  const sh = new THREE.SphericalHarmonics3();
  DIFFUSE_SH.forEach((v, k) => sh.coefficients[k].setScalar(v));
  return new THREE.LightProbe(sh, 1);
}
function buildDiffuseProbe() {
  const disks = HERO.diffuseLights.map((l) => makeDisk(l.loc, blenderEulerDir(l.rot), l.size, l.power));
  const sh = new THREE.SphericalHarmonics3();
  const basis = new Array(9).fill(0);
  const W = 256, H = 128, SS = 3, n = new THREE.Vector3();
  const acc = new Array(9).fill(0);
  for (let j = 0; j < H; j++) {
    for (let i = 0; i < W; i++) {
      const th = ((j + 0.5) / H - 0.5) * Math.PI;
      const dOmega = Math.cos(th) * (Math.PI / H) * (2 * Math.PI / W);
      let L = 0;
      for (let a = 0; a < SS; a++) for (let b = 0; b < SS; b++) {
        L += diskRadiance(texelDir((i + (a + 0.5) / SS) / W, (j + (b + 0.5) / SS) / H), disks);
      }
      L /= SS * SS;
      if (!L) continue;
      const d = texelDir((i + 0.5) / W, (j + 0.5) / H);
      n.set(d.x, d.z, -d.y);
      THREE.SphericalHarmonics3.getBasisAt(n, basis);
      for (let k = 0; k < 9; k++) acc[k] += L * basis[k] * dOmega;
    }
  }
  for (let k = 0; k < 9; k++) sh.coefficients[k].setScalar(acc[k]);
  return new THREE.LightProbe(sh, 1);
}

// The rim light (LGT_Rim_App, a 4 unit disk, visible to diffuse and glossy rays) as an analytic
// disk light: Lambert diffuse plus GGX specular at Karis' representative point with his energy
// normalisation, so it keeps its round highlight on the gloss shell and lights rough surfaces at
// grazing angles the way Cycles does (an environment map alone cannot).
const RIM_U = {
  uRimPos: { value: new THREE.Vector3() }, uRimDir: { value: new THREE.Vector3() },
  uRimL: { value: 0 }, uRimR: { value: 0 },
};
function updateRim() {
  camera.updateMatrixWorld();
  const loc = b2t(HERO.rim.loc);
  RIM_U.uRimPos.value.copy(loc).applyMatrix4(camera.matrixWorldInverse);
  RIM_U.uRimDir.value.copy(b2t(HERO.target)).sub(loc).normalize().transformDirection(camera.matrixWorldInverse);
  const r = HERO.rim.size / 2;
  RIM_U.uRimR.value = r;
  RIM_U.uRimL.value = HERO.rim.power / (4 * Math.PI * r * r);   // Cycles: L = P / (4A)
}
// Specular anti-aliasing (Tokuyoshi and Kaplanyan): widen the GGX lobe by the screen-space normal
// variance. Cycles averages many samples per pixel; without this the case lid's fine surface
// undulation and the shell's hairline shimmer as the earbuds turn.
const SPEC_AA = /* glsl */`
{
  vec3 ndx = dFdx(normal), ndy = dFdy(normal);
  float k = min(0.5 * (dot(ndx, ndx) + dot(ndy, ndy)), 0.18);
  material.roughness = min(pow(pow2(pow2(material.roughness)) + k, 0.25), 1.0);
  #ifdef USE_CLEARCOAT
  material.clearcoatRoughness = min(pow(pow2(pow2(material.clearcoatRoughness)) + k, 0.25), 1.0);
  #endif
}
`;
// Baked ambient occlusion (vertex colours from Cycles, see export_glb.py), applied the way three.js
// applies an aoMap, and to the rim light as a stand-in for its shadow.
const AO_GLSL = /* glsl */`
{
  float ao = vAO;
  reflectedLight.indirectDiffuse *= ao;
  reflectedLight.directDiffuse *= ao;
  reflectedLight.directSpecular *= ao;
  reflectedLight.indirectSpecular *= computeSpecularOcclusion(saturate(dot(geometryNormal, geometryViewDir)), ao, material.roughness);
  #ifdef USE_CLEARCOAT
  clearcoatSpecularIndirect *= ao;
  clearcoatSpecularDirect *= ao;
  #endif
  #ifdef USE_SHEEN
  sheenSpecularIndirect *= ao;
  #endif
}
`;
const RIM_PARS = /* glsl */`
uniform vec3 uRimPos, uRimDir;
uniform float uRimL, uRimR;
`;
const RIM_GLSL = /* glsl */`
{
  vec3 Lv = uRimPos - geometryPosition;
  float d2 = dot(Lv, Lv), d = sqrt(d2);
  vec3 Lc = Lv / d;
  float E = PI * uRimL * uRimR * uRimR / (uRimR * uRimR + d2) * saturate(dot(-Lc, uRimDir));
  reflectedLight.directDiffuse += E * saturate(dot(geometryNormal, Lc)) * BRDF_Lambert(material.diffuseColor);
  vec3 Rv = reflect(-geometryViewDir, geometryNormal);
  vec3 c2r = dot(Lv, Rv) * Rv - Lv;
  vec3 Lp = normalize(Lv + c2r * saturate(uRimR / max(length(c2r), 1e-4)));
  float a = pow2(material.roughness);
  float k = pow2(a / saturate(a + uRimR / (2.0 * d)));
  reflectedLight.directSpecular += E * k * saturate(dot(geometryNormal, Lp)) * BRDF_GGX(Lp, geometryViewDir, geometryNormal, material);
  #ifdef USE_CLEARCOAT
  vec3 Rc = reflect(-geometryViewDir, geometryClearcoatNormal);
  vec3 c2c = dot(Lv, Rc) * Rc - Lv;
  vec3 Lq = normalize(Lv + c2c * saturate(uRimR / max(length(c2c), 1e-4)));
  float ac = pow2(material.clearcoatRoughness);
  float kc = pow2(ac / saturate(ac + uRimR / (2.0 * d)));
  clearcoatSpecularDirect += E * kc * saturate(dot(geometryClearcoatNormal, Lq)) * BRDF_GGX_Clearcoat(Lq, geometryViewDir, geometryClearcoatNormal, material);
  #endif
}
`;

// ---------------------------------------------------------------------------------------------
// Materials: MeshPhysicalMaterial, with the Blender node maths in GLSL where the source used
// procedural shading (shell face mask and hairline, grille holes, ear tip mesh).
const lin = (a) => new THREE.Color().setRGB(a[0], a[1], a[2]);
const SHEEN_K = 0.5;   // Principled v1 sheen -> three.js sheen colour

const GLSL_PARS = /* glsl */`
varying vec3 vObjPos;
varying vec3 vObjN;
vec3 bl(vec3 v) { return vec3(v.x, -v.z, v.y); }   // glTF local (Y up) -> Blender local (Z up)
float aaStep(float edge, float x) {
  float w = max(fwidth(x), 1e-5) * 0.75;
  return smoothstep(edge - w, edge + w, x);
}
vec3 bumpNormal(vec3 pos, vec3 N, float h, float dist) {   // Cycles Bump node
  vec3 dpdx = dFdx(pos), dpdy = dFdy(pos);
  vec3 Rx = cross(dpdy, N), Ry = cross(N, dpdx);
  float det = dot(dpdx, Rx);
  vec3 g = sign(det) * (dFdx(h) * Rx + dFdy(h) * Ry);
  return normalize(abs(det) * N - dist * g);
}
`;
const ACES_PARS = /* glsl */`
vec3 whRRTAndODTFit(vec3 v) {
  vec3 a = v * (v + 0.0245786) - 0.000090537;
  vec3 b = v * (0.983729 * v + 0.4329510) + 0.238081;
  return a / b;
}
vec3 whACESFilmic(vec3 color) {
  const mat3 ACESInputMat = mat3(
    vec3(0.59719, 0.07600, 0.02840),
    vec3(0.35458, 0.90834, 0.13383),
    vec3(0.04823, 0.01566, 0.83777)
  );
  const mat3 ACESOutputMat = mat3(
    vec3(1.60475, -0.10208, -0.00327),
    vec3(-0.53108, 1.10813, -0.07276),
    vec3(-0.07367, -0.00605, 1.07602)
  );
  color *= 1.05 / 0.6;
  color = ACESInputMat * color;
  color = whRRTAndODTFit(color);
  color = ACESOutputMat * color;
  return clamp(color, 0.0, 1.0);
}
`;
function patchVertex(shader) {
  shader.vertexShader = shader.vertexShader
    .replace('#include <common>', '#include <common>\nvarying vec3 vObjPos;\nvarying vec3 vObjN;')
    .replace('#include <begin_vertex>', '#include <begin_vertex>\nvObjPos = position;\nvObjN = normal;');
}
function patched(mat, key, uniforms = {}, frag = { after: {} }, opts = {}) {
  const useVertex = opts.vertex !== false;
  const useRim = opts.rim !== false;
  const useAces = !!opts.aces;
  const isBasic = !!opts.basic;
  mat.userData.uniforms = uniforms;
  mat.customProgramCacheKey = () => key;
  mat.onBeforeCompile = (shader) => {
    Object.assign(shader.uniforms, uniforms);
    if (useRim) Object.assign(shader.uniforms, RIM_U);
    if (useVertex) patchVertex(shader);
    const ao = useVertex && !!mat.userData.ao;
    if (ao) {
      shader.vertexShader = shader.vertexShader
        .replace('#include <common>', '#include <common>\nattribute float aOcc;\nvarying float vAO;')
        .replace('#include <begin_vertex>', '#include <begin_vertex>\nvAO = 1.0 - aOcc;');
    }
    const extraPars = (useVertex ? GLSL_PARS : '') + (useRim ? RIM_PARS : '') + (useAces ? ACES_PARS : '') + (ao ? 'varying float vAO;\n' : '') + (frag.pars || '');
    let fs = shader.fragmentShader.replace('#include <clipping_planes_pars_fragment>', '#include <clipping_planes_pars_fragment>\n' + extraPars);
    const after = { ...(frag.after || {}) };
    if (ao) after.aomap_fragment = (after.aomap_fragment || '') + AO_GLSL;
    if (!isBasic) after.lights_physical_fragment = (after.lights_physical_fragment || '') + SPEC_AA;
    if (useRim) after.lights_fragment_maps = (after.lights_fragment_maps || '') + RIM_GLSL;
    for (const [chunk, code] of Object.entries(after)) {
      fs = fs.replace(`#include <${chunk}>`, `#include <${chunk}>\n${code}`);
    }
    if (useAces && mat.toneMapped !== false) {
      fs = fs.replace('#include <tonemapping_fragment>', 'gl_FragColor.rgb = whACESFilmic(gl_FragColor.rgb);');
    }
    shader.fragmentShader = fs;
  };
  return mat;
}

function makeShell(maskTex) {
  const mat = new THREE.MeshPhysicalMaterial({
    color: 0xffffff, roughness: 0.06, metalness: 0, clearcoat: 0.2, clearcoatRoughness: 0.04,
    sheen: 1, sheenColor: new THREE.Color(0, 0, 0), sheenRoughness: 0.8, ior: 1.5,
  });
  const u = {
    uMask: { value: maskTex },
    uGloss: { value: new THREE.Color() }, uSatin: { value: new THREE.Color() }, uHair: { value: new THREE.Color() },
  };
  return patched(mat, 'sunpods-shell', u, {
    pars: /* glsl */`
uniform sampler2D uMask;
uniform vec3 uGloss, uSatin, uHair;
float sFace, sHair, sH, sRough, sF0, sCC, sSheen;
`,
    after: {
      color_fragment: /* glsl */`
{
  // MAT_Bud_Shell: face mask projected in object space, gated by the normal; hairline at its rim
  vec3 bp = bl(vObjPos);
  vec3 bn = normalize(bl(vObjN));
  vec2 muv = vec2((dot(bp, vec3(0.9781, -0.2079, 0.0)) + 0.9898) / 2.2888,
                  (dot(bp, vec3(-0.0504, -0.2372, 0.9701)) + 1.1368) / 2.2888);
  float R = texture2D(uMask, muv).r;
  float na = dot(bn, vec3(-0.2017, -0.9489, -0.2425));
  float nb = dot(bn, vec3(-0.1169, -0.9739, 0.1948));
  float m4 = aaStep(0.12, na);
  float m5 = aaStep(0.33, nb);
  float m6 = 1.0 - aaStep(-0.5, bp.z);
  sFace = R * max(m5, m6);
  float ridge = max(1.0 - abs(nb - 0.33) / 0.012, 0.0) * R;
  sH = max(m4 * m5, ridge);
  sHair = 0.5 * sH;
  diffuseColor.rgb = mix(mix(uGloss, uSatin, sFace), uHair, sHair);
  sRough = mix(mix(0.06, 0.5, sFace), 0.25, sHair);
  sF0 = mix(mix(0.04, 0.048, sFace), 0.04, sHair);
  sCC = mix(mix(0.2, 0.0, sFace), 0.0, sHair);
  sSheen = mix(mix(0.0, 0.4, sFace), 0.0, sHair);
}`,
      roughnessmap_fragment: 'roughnessFactor = sRough;',
      metalnessmap_fragment: 'metalnessFactor = 0.0;',
      normal_fragment_maps: 'normal = bumpNormal(-vViewPosition, normal, sH, 0.003);',
      clearcoat_normal_fragment_maps: 'clearcoatNormal = normal;',
      lights_physical_fragment: /* glsl */`
material.specularColor = vec3(sF0);
material.specularF90 = 1.0;
#ifdef USE_CLEARCOAT
material.clearcoat = sCC;
#endif
#ifdef USE_SHEEN
material.sheenColor = vec3(sSheen * ${SHEEN_K.toFixed(3)});
#endif`,
    },
  });
}

function makeGrille() {
  const mat = new THREE.MeshPhysicalMaterial({ color: 0xffffff, roughness: 0.32, metalness: 0.7 });
  const u = { uCopper: { value: new THREE.Color() } };
  return patched(mat, 'sunpods-grille', u, {
    pars: 'uniform vec3 uCopper;\nfloat gDark;\n',
    after: {
      color_fragment: /* glsl */`
{
  // MAT_Bud_Grille: rings of holes every 0.031, hole radius 0.0111, flush copper disc
  vec3 bp = bl(vObjPos);
  float r = length(bp.xy);
  float k = floor(r / 0.031 + 0.5);
  float n = max(floor(k * 6.2832 + 0.5), 1.0);
  float j = floor(atan(bp.y, bp.x) * n / 6.2832 + 0.5);
  float th = j * 6.2832 / n;
  float d = length(bp.xy - k * 0.031 * vec2(cos(th), sin(th)));
  float dc = r + (r < 0.0155 ? 0.0 : 9.0);
  float dm = min(d, dc);
  float w = max(fwidth(r), 1e-5) * 0.8;
  float hole = (1.0 - smoothstep(0.0111 - w, 0.0111 + w, dm)) * (1.0 - aaStep(0.2064, r));
  float gap = aaStep(0.2316, r);
  gDark = max(hole, gap);
  diffuseColor.rgb = mix(uCopper, vec3(0.006, 0.005, 0.004), gDark);
}`,
      roughnessmap_fragment: 'roughnessFactor = mix(0.32, 0.9, gDark);',
      metalnessmap_fragment: 'metalnessFactor = mix(0.7, 0.0, gDark);',
    },
  });
}

function makeTipMesh() {
  const mat = new THREE.MeshPhysicalMaterial({ color: 0xffffff, roughness: 0.35, metalness: 1 });
  return patched(mat, 'sunpods-tipmesh', {}, {
    pars: 'float tDark;\n',
    after: {
      color_fragment: /* glsl */`
{
  // MAT_Bud_TipMesh: square mesh, 0.03 pitch, in the ear tip bore
  vec2 g = bl(vObjPos).xy / 0.03;
  vec2 f = abs(fract(g) - 0.5);
  float w = max(fwidth(g.x), 1e-5) * 0.75;
  tDark = (1.0 - smoothstep(0.3 - w, 0.3 + w, f.x)) * (1.0 - smoothstep(0.3 - w, 0.3 + w, f.y));
  diffuseColor.rgb = mix(vec3(0.32, 0.33, 0.34), vec3(0.005), tDark);
}`,
      roughnessmap_fragment: 'roughnessFactor = mix(0.35, 0.5, tDark);',
      metalnessmap_fragment: 'metalnessFactor = 1.0 - tDark;',
    },
  });
}

// Blender "Specular" s -> three.js specularIntensity (F0 = 0.08 s; three's base F0 is 0.04)
const spec = (s) => 2 * s;
// Principled v1 clearcoat is weighted 0.25 in Cycles
const coat = (c) => 0.25 * c;

function buildMaterials(maskTex) {
  const P = (o) => new THREE.MeshPhysicalMaterial(o);
  const M = {
    MAT_Bud_Shell: makeShell(maskTex),
    MAT_Bud_Grille: makeGrille(),
    MAT_Bud_TipMesh: makeTipMesh(),
    MAT_Bud_Tip: P({ roughness: 0.95, specularIntensity: spec(0.2) }),
    MAT_Bud_Badge: P({ roughness: 0.35 }),
    MAT_Bud_BadgeR: P({ roughness: 0.35 }),   // the right bud's badge disc (red on Sony's silver and pink)
    MAT_Bud_BadgeL: P({ roughness: 0.4 }),
    MAT_Bud_Contact: P({ color: lin([0.305, 0.2159, 0.0802]), metalness: 1, roughness: 0.25 }),
    MAT_Bud_Dimple: P({ color: lin([0.006, 0.006, 0.007]), roughness: 0.35 }),
    MAT_Bud_Logo: P({ metalness: 0.3, roughness: 0.35, emissiveIntensity: 1 }),
    MAT_Bud_Nozzle: P({ color: lin([0.008, 0.009, 0.010]), roughness: 0.06, clearcoat: coat(0.8), clearcoatRoughness: 0.03 }),
    MAT_Bud_Seam: P({ color: lin([0.004, 0.004, 0.004]), roughness: 0.3 }),
    MAT_Bud_Sensor: P({ color: lin([0.03, 0.03, 0.035]), roughness: 0.03, clearcoat: coat(1), clearcoatRoughness: 0.03 }),
    MAT_Bud_Socket: P({ color: lin([0.004, 0.004, 0.004]), roughness: 0.5 }),
    MAT_Case_Shell: P({ roughness: 0.66, specularIntensity: spec(0.38), sheen: 1, sheenRoughness: 0.8,
      sheenColor: new THREE.Color(0.2 * SHEEN_K, 0.2 * SHEEN_K, 0.2 * SHEEN_K), clearcoat: coat(0.12), clearcoatRoughness: 0.45 }),
    MAT_Case_Inner: P({ roughness: 0.5, specularIntensity: spec(0.35) }),
    MAT_Case_LED: P({ roughness: 0.3 }),
    MAT_Case_Logo: P({ metalness: 0.9, roughness: 0.25, emissiveIntensity: 1 }),
    MAT_Case_Port: P({ color: lin([0.006, 0.006, 0.007]), roughness: 0.35 }),
    MAT_Case_PortTongue: P({ color: lin([0.02, 0.02, 0.02]), roughness: 0.3 }),
  };
  for (const k of ['MAT_Bud_Shell', 'MAT_Bud_Tip', 'MAT_Bud_Nozzle']) M[k].userData.ao = true;
  for (const [k, m] of Object.entries(M)) {
    m.name = k;
    // plain materials still get the rim light; they share programs (three adds its own defines)
    if (!m.userData.uniforms) patched(m, m.userData.ao ? 'sunpods-plain-ao' : 'sunpods-plain');
  }
  return M;
}

let MATS = null;
let finish = Object.fromEntries(COLOR_KEYS.map((k) => [k, FINISHES.black[k].slice()]));
function applyFinish(f) {
  const set = (c, a) => c.setRGB(a[0], a[1], a[2]);
  const su = MATS.MAT_Bud_Shell.userData.uniforms;
  set(su.uGloss.value, f.gloss); set(su.uSatin.value, f.satin); set(su.uHair.value, f.hair);
  set(MATS.MAT_Bud_Grille.userData.uniforms.uCopper.value, f.grille);
  set(MATS.MAT_Bud_Logo.color, f.logo); set(MATS.MAT_Bud_Logo.emissive, f.logo);
  set(MATS.MAT_Bud_Tip.color, f.tip);
  set(MATS.MAT_Bud_Badge.color, f.badge);
  set(MATS.MAT_Bud_BadgeR.color, f.badgeR);
  set(MATS.MAT_Bud_BadgeL.color, f.badgeL);
  set(MATS.MAT_Case_Shell.color, f.caseShell);
  set(MATS.MAT_Case_Inner.color, f.caseInner);
  set(MATS.MAT_Case_Logo.color, f.caseLogo); set(MATS.MAT_Case_Logo.emissive, f.caseLogoEm);
  set(MATS.MAT_Case_LED.color, f.led);
}

// ---------------------------------------------------------------------------------------------
// Models
// Devices besides the WF-1000XM5, each modelled in code in its own module and lit by this same scene
// and shaders. A module's create(ctx) returns { buds: { root }, kase: { root }, FINISHES, KEYS,
// finishCopy, applyFinish, applyPose(state, active), ready, warm }; 'buds' is the hero model (earbuds,
// or headphones) and 'kase' the case. Adding a model is a module plus one line here.
const DEVICES = {
  c710n: { url: './c710n.js', create: 'createC710N', colors: ['black', 'white', 'pink', 'blue'] },
  wh1000xm5: { url: './wh1000xm5.js', create: 'createWH1000XM5', colors: ['black', 'silver', 'blue', 'pink'] },
};
const rawCaseProgress = params.get('caseProgress');
const hasCaseProgressParam = rawCaseProgress !== null && rawCaseProgress !== '';
const initDevice = DEVICES[params.get('device')] ? params.get('device') : 'xm5';
const initLid = clamp01(parseFloat(params.get('lid') || '0') || 0);
const initModelParam = params.get('model') === 'case' ? 'case' : 'buds';
const initCaseProgress = hasCaseProgressParam
  ? clamp01(parseFloat(rawCaseProgress) || 0)
  : (initModelParam === 'case' ? (initLid > 0 ? 0 : 1) : 0);
let legacyLidMode = !hasCaseProgressParam && initModelParam === 'case' && initLid > 0;
let deviceFading = false;
let modelFading = false;

const state = {
  color: FINISHES[params.get('color')] ? params.get('color') : 'black',
  model: hasCaseProgressParam ? (initCaseProgress > 0.0005 ? 'case' : 'buds') : initModelParam,
  angle: 0,
  lid: hasCaseProgressParam ? 0 : initLid,
  caseProgress: initCaseProgress,
  ready: false,
  device: initDevice,
};
// a device's colour from the URL is checked against its finishes
if (state.device !== 'xm5') state.color = DEVICES[state.device].colors.includes(params.get('color')) ? params.get('color') : 'black';
const CASE_REST_MAT = blenderMatrixToThree(CASE_REST);
const buds = { root: new THREE.Group(), pivotL: null, pivotR: null, pivotParent: null, restL: null, restR: null, sourceInv: {}, loaded: false };
const kase = { root: new THREE.Group(), spin: new THREE.Group(), lid: null, anchors: [], loaded: false };
buds.root.name = 'buds'; kase.root.name = 'case';
kase.root.matrixAutoUpdate = false;
kase.root.matrix.copy(CASE_REST_MAT);
kase.root.add(kase.spin);
scene.add(buds.root, kase.root);

const _vUp = new THREE.Vector3(0, 1, 0);
const _p0 = new THREE.Vector3();
const _p1 = new THREE.Vector3();
const _s0 = new THREE.Vector3();
const _s1 = new THREE.Vector3();
const _q0 = new THREE.Quaternion();
const _q1 = new THREE.Quaternion();
const _m0 = new THREE.Matrix4();
const _mParentInv = new THREE.Matrix4();
const _mDrop = new THREE.Matrix4();
const _mDropScale = new THREE.Matrix4();

const draco = new DRACOLoader().setDecoderPath('./lib/draco/').setDecoderConfig({ type: 'wasm' }).setWorkerLimit(1);
const loader = new GLTFLoader().setDRACOLoader(draco);
const loadGltf = (url) => new Promise((res, rej) => loader.load(url, res, undefined, rej));

function useOurMaterials(root) {
  root.traverse((o) => {
    if (!o.isMesh) return;
    let name = o.material && o.material.name;
    if (name === 'MAT_Bud_Badge' && o.name.endsWith('_R')) name = 'MAT_Bud_BadgeR';
    const m = MATS[name];
    if (m) { o.material.dispose(); o.material = m; }
    const col = o.geometry.getAttribute('color');
    if (col) {
      // baked occlusion arrives as COLOR_0; kept as 1 - AO so a mesh without it reads as open
      const occ = new Float32Array(col.count);
      for (let i = 0; i < col.count; i++) occ[i] = 1 - col.getX(i);
      o.geometry.setAttribute('aOcc', new THREE.BufferAttribute(occ, 1));
      o.geometry.deleteAttribute('color');
    }
  });
}
function wrapPivot(node) {
  // spin about the bud's own vertical axis (world up through its origin), like the turntable
  const pivot = new THREE.Group();
  pivot.position.copy(node.position);
  node.parent.add(pivot);
  node.position.set(0, 0, 0);
  pivot.add(node);
  return pivot;
}
let budsPromise = null, casePromise = null;
function loadBuds() {
  budsPromise = budsPromise || loadGltf('./buds.glb').then((g) => {
    useOurMaterials(g.scene);
    buds.root.add(g.scene);
    buds.source = { L: g.scene.getObjectByName('EMPTY_BudL'), R: g.scene.getObjectByName('EMPTY_BudR') };
    buds.restL = { pos: buds.source.L.position.clone() };
    buds.restR = { pos: buds.source.R.position.clone() };
    buds.source.L.updateMatrix();
    buds.source.R.updateMatrix();
    buds.sourceInv = {
      L: buds.source.L.matrix.clone().setPosition(0, 0, 0).invert(),
      R: buds.source.R.matrix.clone().setPosition(0, 0, 0).invert(),
    };
    buds.pivotL = wrapPivot(buds.source.L);
    buds.pivotR = wrapPivot(buds.source.R);
    buds.pivotParent = buds.pivotL.parent;
    buds.loaded = true;
  });
  return budsPromise;
}
function loadCase() {
  casePromise = casePromise || Promise.all([loadGltf(DEBUG && params.get('caseUrl') ? params.get('caseUrl') : './case.glb'), loadBuds()]).then(([g]) => {
    useOurMaterials(g.scene);
    kase.spin.add(g.scene);
    kase.lid = g.scene.getObjectByName('EMPTY_Lid');
    for (const side of ['L', 'R']) {
      // the earbuds sit in the case in the app-hero pose with the approved shell (shared materials)
      const anchor = g.scene.getObjectByName('EMPTY_Bud' + side);
      for (const child of buds.source[side].children) anchor.add(child.clone());
      kase.anchors.push({ side, node: anchor, y: anchor.position.y });
    }
    kase.loaded = true;
  });
  return casePromise;
}

// Studio 3-point directional lighting used exclusively when state.device === 'wh1000xm5'
// (xm5 and c710n keep intensities at 0 and remain 100% visually unchanged).
const whStudioLights = {
  key: new THREE.DirectionalLight(0xfff6eb, 0),
  fill: new THREE.DirectionalLight(0xd4e2ff, 0),
  rim: new THREE.DirectionalLight(0xeef3ff, 0),
  rim2: new THREE.DirectionalLight(0xeef3ff, 0),
};
whStudioLights.key.position.set(2.4, 5.4, 4.2);
whStudioLights.fill.position.set(-3.8, 2.5, 2.8);
whStudioLights.rim.position.set(-1.6, 3.2, -4.0);
whStudioLights.rim2.position.set(3.2, 2.2, -3.5);
let sceneProbe = null;
function syncDeviceLighting(dev) {
  const isWh = dev === 'wh1000xm5';
  whStudioLights.key.intensity = isWh ? 1.95 : 0;
  whStudioLights.fill.intensity = isWh ? 1.05 : 0;
  whStudioLights.rim.intensity = isWh ? 1.55 : 0;
  whStudioLights.rim2.intensity = isWh ? 0.95 : 0;
  if (sceneProbe) sceneProbe.intensity = isWh ? 0.22 : 1.0;
  scene.environmentIntensity = isWh ? 0.58 : 1.0;
}

const modPoseState = {
  color: 'black', model: 'buds', angle: 0, lid: 0, caseProgress: 0, ready: false, device: 'xm5',
};

function applyPose() {
  const a = rad(state.angle);
  let effModel = state.model;
  let effLid = state.lid;

  if (canvas.style.opacity !== '1' && !deviceFading && !modelFading) {
    canvas.style.transitionDuration = '';
    canvas.style.opacity = '1';
  }

  if (state.device === 'xm5') {
    if (legacyLidMode) {
      kase.root.matrix.copy(CASE_REST_MAT);
      if (buds.pivotL && buds.restL) {
        buds.pivotL.position.copy(buds.restL.pos); buds.pivotL.quaternion.setFromAxisAngle(_vUp, a); buds.pivotL.scale.set(1, 1, 1);
        buds.pivotR.position.copy(buds.restR.pos); buds.pivotR.quaternion.setFromAxisAngle(_vUp, -a); buds.pivotR.scale.set(1, 1, 1);
      }
      kase.spin.rotation.y = a;
      if (kase.lid) {
        kase.lid.rotation.x = LID_OPEN * effLid;
        for (const an of kase.anchors) {
          an.node.position.y = an.y + BUD_SEAT * (1 - effLid);
          an.node.visible = effLid > 0.01;
        }
      }
      buds.root.visible = effModel === 'buds';
      kase.root.visible = effModel === 'case';
    } else {
      const p = clamp01(state.caseProgress);
      if (p <= 0.0005) {
        state.model = effModel = 'buds';
        state.lid = effLid = 0;
        kase.root.visible = false;
        kase.root.matrix.copy(CASE_REST_MAT);
        if (buds.pivotL && buds.restL) {
          buds.pivotL.position.copy(buds.restL.pos); buds.pivotL.quaternion.setFromAxisAngle(_vUp, a); buds.pivotL.scale.set(1, 1, 1);
          buds.pivotR.position.copy(buds.restR.pos); buds.pivotR.quaternion.setFromAxisAngle(_vUp, -a); buds.pivotR.scale.set(1, 1, 1);
        }
        buds.root.visible = true;
      } else if (p >= 0.72 || !kase.loaded || !buds.loaded) {
        const lidT = smooth(clamp01((1 - p) / 0.28));
        state.model = effModel = 'case';
        state.lid = effLid = lidT;
        kase.root.matrix.copy(CASE_REST_MAT);
        kase.spin.rotation.y = a;
        if (kase.lid) {
          kase.lid.rotation.x = LID_OPEN * lidT;
          for (const an of kase.anchors) {
            an.node.position.y = an.y + BUD_SEAT * (1 - lidT);
            an.node.visible = lidT > 0.01;
          }
        }
        buds.root.visible = false;
        kase.root.visible = true;
      } else {
        // Continuous 3D unpack: open case -> earbuds lift on an arc to hero pose -> case glides below viewport.
        const liftT = smooth(clamp01((0.72 - p) / 0.58));
        const dropT = smooth(clamp01((0.58 - p) / 0.44));
        state.model = effModel = 'case';
        state.lid = effLid = 1;

        // First compute exact seated world poses at the resting (pre-drop) case so unpack starts flush from the wells.
        kase.root.matrix.copy(CASE_REST_MAT);
        kase.spin.rotation.y = a;
        if (kase.lid) kase.lid.rotation.x = LID_OPEN;
        for (const an of kase.anchors) {
          an.node.position.y = an.y;
          an.node.visible = false;
        }
        kase.root.updateMatrixWorld(true);
        buds.pivotParent.updateWorldMatrix(true, false);
        _mParentInv.copy(buds.pivotParent.matrixWorld).invert();

        const arcY = Math.sin(Math.PI * liftT) * 1.15;
        const arcZ = Math.sin(Math.PI * liftT) * 0.45;
        for (const an of kase.anchors) {
          const side = an.side;
          const pivot = side === 'L' ? buds.pivotL : buds.pivotR;
          const rest = side === 'L' ? buds.restL : buds.restR;
          const spinAng = side === 'L' ? a : -a;
          _m0.copy(an.node.matrixWorld).multiply(buds.sourceInv[side]).premultiply(_mParentInv);
          _m0.decompose(_p0, _q0, _s0);
          _p1.copy(rest.pos);
          _q1.setFromAxisAngle(_vUp, spinAng);
          _s1.set(1, 1, 1);
          pivot.position.lerpVectors(_p0, _p1, liftT);
          pivot.position.y += arcY;
          pivot.position.z += arcZ;
          pivot.quaternion.slerpQuaternions(_q0, _q1, liftT);
          pivot.scale.lerpVectors(_s0, _s1, liftT);
        }

        // Now glide and gently shrink the case downward below the frame.
        const sDrop = 1 - 0.35 * dropT;
        _mDropScale.makeScale(sDrop, sDrop, sDrop);
        _mDrop.makeTranslation(0, -5.2 * dropT, 0).multiply(CASE_REST_MAT).multiply(_mDropScale);
        kase.root.matrix.copy(_mDrop);
        kase.root.visible = dropT < 0.995;
        buds.root.visible = true;
      }
    }
  } else {
    buds.root.visible = false;
    kase.root.visible = false;
    if (!legacyLidMode) {
      const p = clamp01(state.caseProgress);
      state.model = effModel = p > 0.0005 ? 'case' : 'buds';
      state.lid = effLid = 0;
    }
  }

  for (const id in mods) {
    const slot = mods[id];
    if (!slot || !slot.m) continue;
    const m = slot.m;
    const useNativeCase = !!(m.handlesCaseProgress && !legacyLidMode);
    modPoseState.color = state.color;
    modPoseState.angle = state.angle;
    modPoseState.ready = state.ready;
    modPoseState.device = state.device;
    modPoseState.model = useNativeCase ? state.model : effModel;
    modPoseState.lid = useNativeCase ? state.lid : effLid;
    modPoseState.caseProgress = useNativeCase ? state.caseProgress : undefined;
    m.applyPose(modPoseState, state.device === id);
  }
}

// ---------------------------------------------------------------------------------------------
// Render on demand: the loop runs only while something moves.
let raf = 0;
const anims = new Set();
const postRenderHooks = [];
function requestRender() { if (!raf && !document.hidden) raf = requestAnimationFrame(frame); }
function frame(now) {
  raf = 0;
  for (const a of [...anims]) if (a(now) === false) anims.delete(a);
  applyPose();
  renderer.render(scene, camera);
  if (postRenderHooks.length) {
    const hooks = postRenderHooks.splice(0, postRenderHooks.length);
    for (const fn of hooks) fn();
  }
  if (DEBUG) api.frames = (api.frames || 0) + 1;
  if (anims.size) requestRender();
}
document.addEventListener('visibilitychange', () => { if (!document.hidden) requestRender(); });

// colour crossfade
let colorAnim = null;
function setColor(name, instant) {
  if (deviceTarget !== 'xm5') { setColorMod(deviceTarget, name, instant); return; }
  if (!FINISHES[name]) return;
  state.color = name;
  if (!MATS) return;
  if (colorAnim) anims.delete(colorAnim);
  const from = Object.fromEntries(COLOR_KEYS.map((k) => [k, finish[k].slice()]));
  const to = FINISHES[name];
  if (instant) {
    finish = Object.fromEntries(COLOR_KEYS.map((k) => [k, to[k].slice()]));
    applyFinish(finish); requestRender(); return;
  }
  const t0 = performance.now(), dur = 380;
  colorAnim = (now) => {
    const t = smooth(clamp01((now - t0) / dur));
    for (const k of COLOR_KEYS) for (let i = 0; i < 3; i++) finish[k][i] = from[k][i] + (to[k][i] - from[k][i]) * t;
    applyFinish(finish);
    return t < 1;
  };
  anims.add(colorAnim); requestRender();
}

// lid (legacy API preserved for tools and direct lid calls)
let lidAnim = null;
function setLid(v, instant) {
  const target = clamp01(Number(v) || 0);
  legacyLidMode = true;
  cancelCaseAnim();
  if (lidAnim) anims.delete(lidAnim);
  if (instant) { state.lid = target; requestRender(); return; }
  const from = state.lid, t0 = performance.now(), dur = 300 + 500 * Math.abs(target - from);
  lidAnim = (now) => {
    const t = smooth(clamp01((now - t0) / dur));
    state.lid = from + (target - from) * t;
    return t < 1;
  };
  anims.add(lidAnim); requestRender();
}

// continuous pack/unpack choreography (caseProgress: 0 standalone hero, 1 closed case)
let caseAnim = null;
let pendingCaseAction = null;
function isDeviceLoaded(id, target = state.caseProgress) {
  if (id === 'xm5') return (target > 0.0005 || state.caseProgress > 0.0005) ? kase.loaded && buds.loaded : buds.loaded;
  return !!(mods[id] && mods[id].m);
}
function cancelCaseAnim() {
  if (caseAnim) { anims.delete(caseAnim); caseAnim = null; }
  pendingCaseAction = null;
}
function finishCaseTransition(target, kind) {
  state.caseProgress = target;
  state.angle = 0;
  state.model = target > 0.0005 ? 'case' : 'buds';
  if (target <= 0.0005) state.lid = 0;
  postRenderHooks.push(() => {
    if (kind === 'unpack') host('unpacked');
    else if (kind === 'pack') host('packed');
  });
}
function animateCaseTo(target, kind) {
  legacyLidMode = false;
  cancelCaseAnim();
  const dev = deviceTarget;
  if (state.device !== dev || !isDeviceLoaded(dev, target)) {
    pendingCaseAction = { kind, target };
    if (dev === 'xm5') {
      const loads = [loadBuds()];
      if (target > 0.0005 || state.caseProgress > 0.0005) loads.push(loadCase());
      Promise.all(loads).then(() => {
        if (pendingCaseAction && pendingCaseAction.kind === kind && deviceTarget === dev && state.device === dev && isDeviceLoaded(dev, target)) {
          const p = pendingCaseAction; pendingCaseAction = null;
          animateCaseTo(p.target, p.kind);
        }
      });
    }
    return;
  }
  const startP = state.caseProgress;
  const dist = Math.abs(target - startP);
  if (dist <= 0.0005) {
    finishCaseTransition(target, kind);
    requestRender();
    return;
  }
  stopMotion();
  const startAngle = state.angle;
  const homeAngle = Math.round(startAngle / 360) * 360;
  const dur = Math.max(200, (target < startP ? 1900 : 1600) * dist);
  const t0 = performance.now();
  caseAnim = (now) => {
    const u = clamp01((now - t0) / dur);
    const e = u < 0.5 ? 2 * u * u : 1 - Math.pow(-2 * u + 2, 2) / 2;
    state.caseProgress = startP + (target - startP) * e;
    if (Math.abs(homeAngle - startAngle) > 0.01) {
      state.angle = startAngle + (homeAngle - startAngle) * smooth(clamp01(u * 1.4));
    }
    if (u < 1) return true;
    finishCaseTransition(target, kind);
    caseAnim = null;
    return false;
  };
  anims.add(caseAnim);
  requestRender();
}
function setCaseProgress(v, instant) {
  const target = clamp01(Number(v) || 0);
  legacyLidMode = false;
  cancelCaseAnim();
  if (instant) {
    state.caseProgress = target;
    state.model = target > 0.0005 ? 'case' : 'buds';
    state.lid = 0;
    if (deviceTarget === 'xm5' && target > 0.0005 && (!kase.loaded || !buds.loaded)) {
      Promise.all([loadCase(), loadBuds()]).then(() => requestRender());
    }
    if (centered) recenter();
    requestRender();
    return;
  }
  animateCaseTo(target, null);
}
function playUnpack() { animateCaseTo(0, 'unpack'); }
function playPack() { animateCaseTo(1, 'pack'); }

// model switch: a short fade so the swap never pops
let modelToken = 0;
function setModel(m) {
  const want = m === 'case' ? 'case' : 'buds';
  const token = ++modelToken;
  cancelCaseAnim();
  const ready = deviceTarget !== 'xm5' ? loadMod(deviceTarget) : want === 'case' ? loadCase() : loadBuds();
  ready.then(() => {
    if (token !== modelToken) return;
    if (state.model === want) {
      modelFading = false;
      canvas.style.transitionDuration = '';
      canvas.style.opacity = '1';
      if (want === 'buds') { legacyLidMode = false; state.caseProgress = 0; }
      else if (!legacyLidMode) { state.caseProgress = state.lid > 0 ? 0 : 1; }
      requestRender(); return;
    }
    modelFading = true;
    canvas.style.transitionDuration = '';
    canvas.style.opacity = '0';
    setTimeout(() => {
      modelFading = false;
      if (token !== modelToken) return;
      state.model = want; state.angle = 0; stopMotion();
      if (want === 'buds') { legacyLidMode = false; state.caseProgress = 0; state.lid = 0; }
      else if (state.lid > 0) { legacyLidMode = true; }
      else { legacyLidMode = false; state.caseProgress = 1; }
      requestRender();
      requestAnimationFrame(() => { canvas.style.opacity = '1'; });
    }, 170);
  });
}

// ---------------------------------------------------------------------------------------------
// The other devices (DEVICES). The XM5 above is unchanged; setDevice() swaps which model is shown,
// with the same short fade as a model switch. Each device keeps its own colour.
const mods = {};   // id -> { m: the built model, promise, finish, colorAnim }
let deviceTarget = state.device;   // the device asked for last; state.device follows once it has loaded
const colorFor = Object.fromEntries(['xm5', ...Object.keys(DEVICES)].map((d) => [d, 'black']));
colorFor[state.device] = state.color;
// compile() builds a program for every material under what it's given, shown or not. A device's start
// compiles only what is drawn (stand-in meshes sharing the geometry and materials, lit by the real
// scene), so its first frame doesn't wait for the case or the other finishes; the rest follows idle.
function compileShown(root, forceVisible = false) {
  const ws = new THREE.Scene();
  const walk = (o) => {
    if (!forceVisible && o !== root && !o.visible) return;
    if (o.isMesh && o.material) ws.add(new THREE.Mesh(o.geometry, o.material));
    for (const c of o.children) walk(c);
  };
  walk(root);
  return renderer.compileAsync(ws, camera, scene);
}
// The other finishes' programs, off screen, for switching finishes by hand (tools and demo); real
// earbuds keep their own colour, so a normal start never needs them.
function modWarm(id) {
  const m = mods[id] && mods[id].m;
  if (!m || !m.warm || !m.warm.children || !m.warm.children.length) return;
  const ws = new THREE.Scene(); ws.add(m.warm);
  renderer.compileAsync(ws, camera, scene).then(() => mark('other finishes ready')).catch(() => {});
}
function buildMod(id, mod, overrides) {
  const slot = mods[id];
  if (slot.m) scene.remove(slot.m.buds.root, slot.m.kase.root);
  const m = slot.m = mod[DEVICES[id].create]({
    THREE, patched, lin, smooth, merge, makeFinishCopy, DEBUG, overrides, caseRest: blenderMatrixToThree(CASE_REST),
  });
  scene.add(m.buds.root, m.kase.root);
  slot.finish = m.finishCopy(colorFor[id]);
  if (typeof m.setColorTarget === 'function') m.setColorTarget(colorFor[id]);
  m.applyFinish(slot.finish);
  return m.ready;
}
function loadMod(id) {
  const slot = mods[id] || (mods[id] = { m: null, promise: null, finish: null, colorAnim: null });
  slot.promise = slot.promise || import(DEVICES[id].url).then((mod) => buildMod(id, mod));
  return slot.promise;
}
function setColorMod(id, name, instant) {
  const slot = mods[id];
  if (!DEVICES[id].colors.includes(name) && !(DEBUG && slot && slot.m && slot.m.FINISHES[name])) return;
  colorFor[id] = name;
  state.color = name;
  if (!slot || !slot.m) { loadMod(id).then(() => { if (colorFor[id] === name) setColorMod(id, name, true); }); return; }
  const m = slot.m;
  if (slot.colorAnim) anims.delete(slot.colorAnim);
  // Swap canvas textures once per finish change rather than regenerating textures per frame.
  if (typeof m.setColorTarget === 'function') m.setColorTarget(name);
  const from = m.finishCopy(null, slot.finish), to = m.FINISHES[name];
  if (instant || state.device !== id) {
    slot.finish = m.finishCopy(name);
    m.applyFinish(slot.finish); requestRender(); return;
  }
  const t0 = performance.now(), dur = 380;
  slot.colorAnim = (now) => {
    const t = smooth(clamp01((now - t0) / dur));
    for (const k of m.KEYS) for (let i = 0; i < 3; i++) slot.finish[k][i] = from[k][i] + (to[k][i] - from[k][i]) * t;
    m.applyFinish(slot.finish);
    return t < 1;
  };
  anims.add(slot.colorAnim); requestRender();
}
let deviceToken = 0;
// Warm a device that is likely to be shown next (the Welcome page's cycle): load its module and compile
// the shaders of what is drawn, off screen, so the swap itself is only the fade. No-op once done.
const warmed = new Set();
function preload(d) {
  const id = DEVICES[d] ? d : 'xm5';
  if (warmed.has(id)) return;
  warmed.add(id);
  if (id === 'xm5') {
    loadBuds().then(() => compileShown(buds.root)).catch(() => {});
  } else {
    loadMod(id).then(() => compileShown(mods[id].m.buds.root)).catch(() => {});
  }
}
function flushPendingCaseAction(want) {
  if (pendingCaseAction && state.device === want && isDeviceLoaded(want, pendingCaseAction.target)) {
    const p = pendingCaseAction;
    pendingCaseAction = null;
    animateCaseTo(p.target, p.kind);
  }
}
async function snapTo(d, c, progress, isCentered) {
  const want = DEVICES[d] ? d : 'xm5';
  const colorName = want === 'xm5'
    ? (FINISHES[c] ? c : 'black')
    : (DEVICES[want].colors.includes(c) ? c : 'black');
  const p = clamp01(Number(progress) || 0);
  const token = ++deviceToken;
  ++modelToken;
  stopMotion(); anims.clear(); cancelCaseAnim();
  if (lidAnim) { anims.delete(lidAnim); lidAnim = null; }
  deviceFading = false;
  modelFading = false;
  legacyLidMode = false;
  canvas.style.transitionDuration = '0ms';
  canvas.style.opacity = '1';

  colorFor[deviceTarget] = state.color;
  deviceTarget = want;
  colorFor[want] = colorName;

  if (want !== 'xm5') {
    await loadMod(want);
  } else if (p > 0.0005) {
    await Promise.all([loadCase(), loadBuds()]);
  } else {
    await loadBuds();
  }
  if (token !== deviceToken) return false;

  state.device = want;
  state.angle = 0;
  state.caseProgress = p;
  state.model = p > 0.0005 ? 'case' : 'buds';
  state.lid = 0;
  centered = !!isCentered;
  syncDeviceLighting(want);
  setColor(colorName, true);
  applyPose();
  if (centered) recenter(); else { shift.x = shift.y = 0; fitCamera(viewW || 1, viewH || 1); }

  if (want !== 'xm5' && mods[want] && mods[want].m) {
    const M = mods[want].m;
    await compileShown(p > 0.0005 ? M.kase.root : M.buds.root, true);
    if (p > 0.0005) await compileShown(M.buds.root, true);
  } else {
    await compileShown(p > 0.0005 ? kase.root : buds.root, true);
    if (p > 0.0005) await compileShown(buds.root, true);
  }
  if (token !== deviceToken) return false;

  applyPose();
  renderer.render(scene, camera);
  requestAnimationFrame(() => {
    if (token === deviceToken) host('snapped');
  });
  return true;
}

function setDevice(d, fadeMs) {
  const want = DEVICES[d] ? d : 'xm5';
  if (want !== deviceTarget) {
    colorFor[deviceTarget] = state.color;
    deviceTarget = want;
    state.color = colorFor[want];
  }
  const token = ++deviceToken;
  const fade = fadeMs > 0 ? Math.min(1000, fadeMs) : 0;   // a slower, softer fade for the Welcome page's cycle
  const ready = want !== 'xm5' ? loadMod(want) : (state.model === 'case' || (!legacyLidMode && state.caseProgress > 0.0005)) ? loadCase() : loadBuds();
  ready.then(() => {
    if (token !== deviceToken) return;
    if (state.device === want) {
      deviceFading = false;
      canvas.style.transitionDuration = '';
      canvas.style.opacity = '1';
      syncDeviceLighting(want);
      requestRender();
      flushPendingCaseAction(want);
      return;
    }
    deviceFading = true;
    canvas.style.transitionDuration = fade ? fade + 'ms' : '';
    canvas.style.opacity = '0';
    setTimeout(() => {
      deviceFading = false;
      if (token !== deviceToken) return;
      state.device = want; state.angle = 0; stopMotion();
      syncDeviceLighting(want);
      if (centered) { applyPose(); recenter(); } else { applyPose(); }
      requestRender();
      requestAnimationFrame(() => {
        canvas.style.opacity = '1';
        flushPendingCaseAction(want);
      });
    }, fade ? fade + 20 : 170);
  });
}

// ---------------------------------------------------------------------------------------------
// Spin interaction, matching SolarHero: 360 degrees per 320 dp, fling with exponential decay
// (Compose exponentialDecay, friction multiplier 2.2), then after 2.5 s idle a spring
// (damping 0.82, stiffness 60) back to the nearest whole turn, then a haptic tick.
const DEG_PER_PX = 360 / 320;
const FRICTION = 4.2 * 2.2;
let motion = null, settleTimer = 0;
function host(fn) {
  try { const h = window.SunPodsHost; if (h && typeof h[fn] === 'function') h[fn](); } catch (e) { /* no host */ }
}
function stopMotion() {
  if (motion) anims.delete(motion);
  motion = null; clearTimeout(settleTimer);
}
function fling(v0) {
  stopMotion();
  v0 = Math.max(-1440, Math.min(1440, v0));
  const a0 = state.angle, t0 = performance.now();
  if (Math.abs(v0) > 0.1) {
    motion = (now) => {
      const t = (now - t0) / 1000;
      const v = v0 * Math.exp(-FRICTION * t);
      state.angle = a0 + (v0 / FRICTION) * (1 - Math.exp(-FRICTION * t));
      if (Math.abs(v) > 0.1) return true;
      motion = null; scheduleHome(); return false;
    };
    anims.add(motion); requestRender();
  } else scheduleHome();
}
function scheduleHome() {
  clearTimeout(settleTimer);
  settleTimer = setTimeout(() => springHome(), 2500);
}
function springHome(stiffness = 60, damping = 0.82, onDone) {
  stopMotion();
  const home = Math.round(state.angle / 360) * 360;
  const x0 = state.angle - home, t0 = performance.now();
  const w0 = Math.sqrt(stiffness), wd = w0 * Math.sqrt(1 - damping * damping);
  const B = (damping * w0 * x0) / wd;
  motion = (now) => {
    const t = (now - t0) / 1000;
    const e = Math.exp(-damping * w0 * t);
    const x = e * (x0 * Math.cos(wd * t) + B * Math.sin(wd * t));
    state.angle = home + x;
    if (t > 0.2 && Math.abs(x) < 0.05 && e * Math.abs(x0) * w0 < 0.5) {
      state.angle = 0; motion = null;
      if (onDone) onDone(); else host('settled');
      return false;
    }
    return true;
  };
  anims.add(motion); requestRender();
}
function animateTo(target, dur, then) {
  stopMotion();
  const from = state.angle, t0 = performance.now();
  motion = (now) => {
    const t = clamp01((now - t0) / dur);
    state.angle = from + (target - from) * (t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2);
    if (t < 1) return true;
    motion = null; if (then) then(); return false;
  };
  anims.add(motion); requestRender();
}
function wiggle() { animateTo(-40, 700, () => springHome(90, 0.6, () => {})); }
function spin() { animateTo(state.angle + 360, 1600, () => { state.angle = 0; requestRender(); }); }

let ptr = null;
canvas.addEventListener('pointerdown', (e) => {
  if (!e.isPrimary || ptr) return;
  ptr = { id: e.pointerId, x0: e.clientX, y0: e.clientY, x: e.clientX, t0: e.timeStamp, drag: false, moved: false, samples: [] };
});
canvas.addEventListener('pointermove', (e) => {
  if (!ptr || e.pointerId !== ptr.id) return;
  const dxTot = e.clientX - ptr.x0, dyTot = e.clientY - ptr.y0;
  if (!ptr.drag) {
    if (Math.hypot(dxTot, dyTot) > 8) ptr.moved = true;
    if (Math.abs(dxTot) > 8 && Math.abs(dxTot) > Math.abs(dyTot)) {
      ptr.drag = true; ptr.x = e.clientX;
      stopMotion();
      try { canvas.setPointerCapture(e.pointerId); } catch (err) { /* ignore */ }
    } else return;
  }
  const dx = e.clientX - ptr.x; ptr.x = e.clientX;
  state.angle += dx * DEG_PER_PX;
  ptr.samples.push([e.timeStamp, e.clientX]);
  while (ptr.samples.length > 2 && e.timeStamp - ptr.samples[0][0] > 100) ptr.samples.shift();
  requestRender();
});
function endPointer(e, cancelled) {
  if (!ptr || e.pointerId !== ptr.id) return;
  const p = ptr; ptr = null;
  if (p.drag) {
    let v = 0;
    const s = p.samples;
    if (!cancelled && s.length >= 2) {
      const dt = (s[s.length - 1][0] - s[0][0]) / 1000;
      if (dt > 0) v = ((s[s.length - 1][1] - s[0][1]) / dt) * DEG_PER_PX;
    }
    fling(v);
  } else if (!cancelled && !p.moved && e.timeStamp - p.t0 < 500) {
    host('tap');
  }
}
canvas.addEventListener('pointerup', (e) => endPointer(e, false));
canvas.addEventListener('pointercancel', (e) => endPointer(e, true));
canvas.addEventListener('contextmenu', (e) => e.preventDefault());
canvas.addEventListener('webglcontextlost', (e) => e.preventDefault());
canvas.addEventListener('webglcontextrestored', () => requestRender());

// ---------------------------------------------------------------------------------------------
// Start
const api = {
  isReady: false,
  setColor: (c, instant) => setColor(String(c), !!instant),
  setModel: (m) => setModel(String(m)),
  setLid: (v, instant) => setLid(v, !!instant),
  setCaseProgress: (v, instant) => setCaseProgress(v, !!instant),
  playUnpack: () => playUnpack(),
  playPack: () => playPack(),
  snapTo: (d, c, p, isCentered) => snapTo(String(d), String(c), p, !!isCentered),
  setDevice: (d, fadeMs) => setDevice(String(d), Number(fadeMs) || 0),
  preload: (d) => preload(String(d)),
  setSize: (w, h) => { w = Number(w); h = Number(h); if (w >= 1 && h >= 1 && (!forcedSize || forcedSize.w !== w || forcedSize.h !== h)) { forcedSize = { w, h }; resize(); } },
  setCentered: (on) => { on = !!on; if (on !== centered) { centered = on; applyPose(); recenter(); } },
  wiggle, spin,
  get state() { return { ...state }; },
};
window.sunpods = api;

const T0 = performance.now();
const mark = (what) => { if (DEBUG) console.log('hero3d', what, Math.round(performance.now() - T0), 'ms'); };
async function start() {
  const maskTex = await new THREE.TextureLoader().loadAsync('./face_mask.png');
  maskTex.colorSpace = THREE.NoColorSpace;          // Non-Color in Blender
  maskTex.wrapS = maskTex.wrapT = THREE.RepeatWrapping;
  maskTex.anisotropy = 4;
  updateRim();
  MATS = buildMaterials(maskTex);
  if (state.device !== 'xm5') applyFinish(finish);   // the XM5 materials keep their black finish ready
  setColor(state.color, true);
  const first = state.device !== 'xm5' ? loadMod(state.device)
    : (state.model === 'case' || (!legacyLidMode && state.caseProgress > 0.0005)) ? loadCase() : loadBuds();   // decodes in the worker meanwhile
  scene.environment = buildEnvironment();
  mark('environment');
  sceneProbe = diffuseProbe(); scene.add(sceneProbe); if (DEBUG) api._probe = sceneProbe;
  scene.add(whStudioLights.key, whStudioLights.fill, whStudioLights.rim, whStudioLights.rim2);
  syncDeviceLighting(state.device);
  mark('light probe');
  await first;
  mark('model');
  new ResizeObserver(resize).observe(canvas);
  window.addEventListener('resize', resize);
  resize();
  applyPose();
  const M = state.device !== 'xm5' && mods[state.device] ? mods[state.device].m : null;
  if (M) {
    await compileShown(state.model === 'case' ? M.kase.root : M.buds.root);
    if (M.precompileClosedBoth && state.model === 'case') await compileShown(M.buds.root);
  } else {
    await renderer.compileAsync(scene, camera);
  }
  mark('shaders');
  renderer.render(scene, camera);
  mark('first frame, ' + (renderer.info.programs || []).length + ' programs' + (DEBUG ? ': ' + (renderer.info.programs || []).map((p) => p.name).join(' ') : ''));
  state.ready = api.isReady = true;
  host('ready');
  window.dispatchEvent(new Event('sunpods-ready'));
  // the other model loads once the first frame is up, then the decoder worker is released
  // (another device's start loads the XM5 files only if setDevice('xm5') asks for them)
  if (M) {
    const id = state.device;
    setTimeout(() => {
      if (!M.precompileClosedBoth || state.model !== 'case') {
        compileShown(state.model === 'case' ? M.buds.root : M.kase.root).then(() => mark('other model ready')).catch(() => {});
      }
      if (DEBUG) modWarm(id);
    }, 600);
    return;
  }
  setTimeout(() => {
    (state.model === 'case' ? loadBuds() : loadCase()).then(() => {
      // Compile the non-active root offscreen without ever toggling visibility on the live scene.
      return compileShown(state.model === 'case' ? buds.root : kase.root, true);
    }).then(() => {
      draco.dispose();
      mark('other model ready');
    }).catch(() => {});
  }, 600);
}
start().catch((e) => { console.error('hero3d', e); });

// The frame on screen as a PNG (transparent background), so the app can show it instantly on its
// next launch while this page loads, then fade across to the live view without a pop.
api.snapshot = () => {
  try { renderer.render(scene, camera); return canvas.toDataURL('image/png'); } catch (e) { return null; }
};

// ---------------------------------------------------------------------------------------------
// Preview captures for branding/render/hero3d_preview (only with ?debug).
if (DEBUG) {
  api.debugShot = async ({ color = 'black', model = 'buds', angle = 0, lid = 0, caseProgress, w = 1000, h = 712, device = state.device } = {}) => {
    const dev = DEVICES[device] ? device : 'xm5';
    const wantCase = model === 'case' || (typeof caseProgress === 'number' && caseProgress > 0.0005);
    await (dev !== 'xm5' ? loadMod(dev) : wantCase ? loadCase() : loadBuds());
    stopMotion(); anims.clear(); cancelCaseAnim();
    deviceToken++; deviceTarget = state.device = dev;
    syncDeviceLighting(dev);
    setColor(color, true);
    if (typeof caseProgress === 'number') {
      legacyLidMode = false;
      state.caseProgress = clamp01(caseProgress);
      state.model = state.caseProgress > 0.0005 ? 'case' : 'buds';
      state.lid = 0;
    } else {
      legacyLidMode = model === 'case' && lid > 0;
      state.model = model; state.lid = lid;
      state.caseProgress = model === 'case' ? (lid > 0 ? 0 : 1) : 0;
    }
    state.angle = angle;
    renderer.setPixelRatio(1);
    renderer.setSize(w, h, false);
    fitCamera(w, h);
    applyPose();
    renderer.render(scene, camera);
    const url = canvas.toDataURL('image/png');
    resize();
    return url;
  };
  api.debugFinish = (name, f) => { FINISHES[name] = f; };
  // Model tools (branding/render/<device>/tools): scene access for fitted-camera boards and clips.
  // debugMod(id) for any device; debugC7 and friends are the WF-C710N tools' names for the same.
  api.debugMod = async (id, load = true) => {
    if (id === 'xm5') {
      if (load) await Promise.all([loadCase(), loadBuds()]);
      return { THREE, scene, camera, renderer, canvas, kase, buds, LID_OPEN, MATS, state, applyPose, fitCamera, resize, stopMotion, anims, setColor, syncDeviceLighting,
        setDeviceNow: (d) => { deviceToken++; deviceTarget = state.device = d; syncDeviceLighting(d); } };
    }
    if (load) await loadMod(id);
    return { THREE, scene, camera, renderer, canvas, M: mods[id] && mods[id].m, C7: mods[id] && mods[id].m, state, applyPose, fitCamera, resize, stopMotion, anims, setColor, syncDeviceLighting,
      setDeviceNow: (d) => { deviceToken++; deviceTarget = state.device = d; syncDeviceLighting(d); } };
  };
  api.debugFinishMod = (id, name, f) => { if (mods[id] && mods[id].m) mods[id].m.FINISHES[name] = f; };
  // rebuild a device with trial shape values (tools only)
  api.debugRebuildMod = async (id, overrides) => {
    await loadMod(id);
    await buildMod(id, await import(DEVICES[id].url), overrides);
    return true;
  };
  api.debugC7 = (load = true) => api.debugMod('c710n', load);
  api.debugFinishC7 = (name, f) => api.debugFinishMod('c710n', name, f);
  api.debugRebuildC7 = (overrides) => api.debugRebuildMod('c710n', overrides);
}
