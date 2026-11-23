// SunPods Home hero: the approved Blender earbuds (loop2/xm5_bud_v2.blend) and case
// (case_loop/xm5_case.blend), live in three.js r170, in Sony's three WF-1000XM5 finishes.
//
// Lighting and materials are translations of the Blender scenes the app renders came from
// (render_turntable.py / hero_render.py): same camera, same world, same lights, same shader maths.
//
// API (window.sunpods):
//   setColor('black' | 'silver' | 'pink', instant?)   short crossfade between finishes
//   setModel('buds' | 'case')                           earbuds hero or charging case
//   setLid(0..1, instant?)                              case lid, 0 closed, 1 open (Sony's -116 deg)
//   wiggle()  spin()                                     the one-time hint and the TalkBack spin
//   setDevice('xm5' | 'c710n')                          which earbuds: WF-1000XM5 (default) or WF-C710N
//     On the WF-C710N (c710n.js, built in code) setColor takes 'black' | 'white' | 'pink' | 'blue'
//     (Glass Blue); setModel, setLid, wiggle, spin and snapshot work the same. URL: ?device=c710n
// Android bridge (optional, JavascriptInterface "SunPodsHost"): ready(), settled(), tap().
import * as THREE from 'three';
import { GLTFLoader } from './lib/addons/loaders/GLTFLoader.js';
import { DRACOLoader } from './lib/addons/loaders/DRACOLoader.js';

const params = new URLSearchParams(location.search);
const DEBUG = params.has('debug');
const rad = (d) => d * Math.PI / 180;
const clamp01 = (x) => Math.min(1, Math.max(0, x));
const smooth = (t) => t * t * (3 - 2 * t);

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
}
function resize() {
  // Size from the window, not CSS: Android's WebView starts at zero height, and 100%/100vh can
  // stay at zero after it grows. innerWidth/innerHeight always follow the real view.
  const w = Math.max(1, window.innerWidth || canvas.clientWidth), h = Math.max(1, window.innerHeight || canvas.clientHeight);
  canvas.style.width = w + 'px';
  canvas.style.height = h + 'px';
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, DPR_CAP));
  renderer.setSize(w, h, false);
  fitCamera(w, h);
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
function patchVertex(shader) {
  shader.vertexShader = shader.vertexShader
    .replace('#include <common>', '#include <common>\nvarying vec3 vObjPos;\nvarying vec3 vObjN;')
    .replace('#include <begin_vertex>', '#include <begin_vertex>\nvObjPos = position;\nvObjN = normal;');
}
function patched(mat, key, uniforms = {}, frag = { after: {} }) {
  mat.userData.uniforms = uniforms;
  mat.customProgramCacheKey = () => key;
  mat.onBeforeCompile = (shader) => {
    Object.assign(shader.uniforms, uniforms, RIM_U);
    patchVertex(shader);
    const ao = !!mat.userData.ao;
    if (ao) {
      shader.vertexShader = shader.vertexShader
        .replace('#include <common>', '#include <common>\nattribute float aOcc;\nvarying float vAO;')
        .replace('#include <begin_vertex>', '#include <begin_vertex>\nvAO = 1.0 - aOcc;');
    }
    let fs = shader.fragmentShader.replace('#include <clipping_planes_pars_fragment>',
      '#include <clipping_planes_pars_fragment>\n' + GLSL_PARS + RIM_PARS + (ao ? 'varying float vAO;\n' : '') + (frag.pars || ''));
    const after = { ...frag.after };
    if (ao) after.aomap_fragment = (after.aomap_fragment || '') + AO_GLSL;
    after.lights_physical_fragment = (after.lights_physical_fragment || '') + SPEC_AA;
    after.lights_fragment_maps = (after.lights_fragment_maps || '') + RIM_GLSL;
    for (const [chunk, code] of Object.entries(after)) {
      fs = fs.replace(`#include <${chunk}>`, `#include <${chunk}>\n${code}`);
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
const state = {
  color: FINISHES[params.get('color')] ? params.get('color') : 'black',
  model: params.get('model') === 'case' ? 'case' : 'buds',
  angle: 0,
  lid: clamp01(parseFloat(params.get('lid') || '0') || 0),
  ready: false,
  device: params.get('device') === 'c710n' ? 'c710n' : 'xm5',
};
// WF-C710N finishes (c710n.js holds their values); the colour from the URL is checked against them
const C7_COLORS = ['black', 'white', 'pink', 'blue'];
if (state.device === 'c710n') state.color = C7_COLORS.includes(params.get('color')) ? params.get('color') : 'black';
const buds = { root: new THREE.Group(), pivotL: null, pivotR: null, loaded: false };
const kase = { root: new THREE.Group(), spin: new THREE.Group(), lid: null, anchors: [], loaded: false };
buds.root.name = 'buds'; kase.root.name = 'case';
kase.root.matrixAutoUpdate = false;
kase.root.matrix.copy(blenderMatrixToThree(CASE_REST));
kase.root.add(kase.spin);
scene.add(buds.root, kase.root);

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
    buds.pivotL = wrapPivot(buds.source.L);
    buds.pivotR = wrapPivot(buds.source.R);
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
      kase.anchors.push({ node: anchor, y: anchor.position.y });
    }
    kase.loaded = true;
  });
  return casePromise;
}

function applyPose() {
  const a = rad(state.angle);
  if (buds.pivotL) { buds.pivotL.rotation.y = a; buds.pivotR.rotation.y = -a; }   // mirrored spin
  kase.spin.rotation.y = a;
  if (kase.lid) {
    const t = state.lid;
    kase.lid.rotation.x = LID_OPEN * t;
    for (const an of kase.anchors) {
      an.node.position.y = an.y + BUD_SEAT * (1 - t);
      an.node.visible = t > 0.01;
    }
  }
  buds.root.visible = state.model === 'buds' && state.device === 'xm5';
  kase.root.visible = state.model === 'case' && state.device === 'xm5';
  if (C7) C7.applyPose(state, state.device === 'c710n');
}

// ---------------------------------------------------------------------------------------------
// Render on demand: the loop runs only while something moves.
let raf = 0;
const anims = new Set();
function requestRender() { if (!raf && !document.hidden) raf = requestAnimationFrame(frame); }
function frame(now) {
  raf = 0;
  for (const a of [...anims]) if (a(now) === false) anims.delete(a);
  applyPose();
  renderer.render(scene, camera);
  if (DEBUG) api.frames = (api.frames || 0) + 1;
  if (anims.size) requestRender();
}
document.addEventListener('visibilitychange', () => { if (!document.hidden) requestRender(); });

// colour crossfade
let colorAnim = null;
function setColor(name, instant) {
  if (deviceTarget === 'c710n') { setColorC7(name, instant); return; }
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

// lid
let lidAnim = null;
function setLid(v, instant) {
  const target = clamp01(Number(v) || 0);
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

// model switch: a short fade so the swap never pops
let modelToken = 0;
function setModel(m) {
  const want = m === 'case' ? 'case' : 'buds';
  const token = ++modelToken;
  const ready = deviceTarget === 'c710n' ? loadC7() : want === 'case' ? loadCase() : loadBuds();
  ready.then(() => {
    if (token !== modelToken) return;
    c7Release();
    if (state.model === want) { requestRender(); return; }
    canvas.style.opacity = '0';
    setTimeout(() => {
      if (token !== modelToken) return;
      state.model = want; state.angle = 0; stopMotion();
      requestRender();
      requestAnimationFrame(() => { canvas.style.opacity = '1'; });
    }, 170);
  });
}

// ---------------------------------------------------------------------------------------------
// WF-C710N: a second device, modelled in code (c710n.js) and lit by this same scene and shaders.
// The XM5 above is unchanged; setDevice() swaps which earbuds and case are shown, with the same
// short fade as a model switch. Each device keeps its own colour.
let C7 = null, c7Promise = null;
let deviceTarget = state.device;   // the device asked for last; state.device follows once it has loaded
const colorFor = { xm5: 'black', c710n: 'black' };
colorFor[state.device] = state.color;
let finishC7 = null;
// A WF-C710N start keeps what its first frame doesn't draw out of the scene until that frame is up:
// the model not shown, and the hidden parts of the other finish (Glass Blue's inner parts, or the
// opaque ones). compile() would otherwise build programs for all of them first.
let c7Held = [];
function c7Hold() {
  c7Held = [];
  if (state.device !== 'c710n' || !C7) return;
  const shown = state.model === 'case' ? C7.kase.root : C7.buds.root;
  c7Held.push([state.model === 'case' ? C7.buds.root : C7.kase.root, scene]);
  const off = new Set();
  shown.traverse((o) => {
    if (o === shown || o.visible) return;
    for (let p = o.parent; p; p = p.parent) if (off.has(p)) return;
    off.add(o);
  });
  for (const o of off) c7Held.push([o, o.parent]);
  for (const [o, parent] of c7Held) parent.remove(o);
}
// The glass programs, built off screen (their meshes are never drawn) while the page is idle.
function c7Warm() {
  if (!C7 || !C7.warm || C7.warm.parent) return;
  const ws = new THREE.Scene(); ws.add(C7.warm);
  renderer.compileAsync(ws, camera, scene).then(() => mark('glass programs ready')).catch(() => {});
}
function c7Release() {
  if (!c7Held.length) return false;
  for (const [o, parent] of c7Held) parent.add(o);
  c7Held = [];
  applyPose(); requestRender();
  return true;
}
function loadC7() {
  c7Promise = c7Promise || import('./c710n.js').then((mod) => {
    C7 = mod.createC710N({ THREE, patched, lin, spec, coat, SHEEN_K, DEBUG, caseRest: blenderMatrixToThree(CASE_REST) });
    scene.add(C7.buds.root, C7.kase.root);
    finishC7 = C7.finishCopy(colorFor.c710n);
    C7.applyFinish(finishC7);
    return C7.ready;
  });
  return c7Promise;
}
let colorAnimC7 = null;
function setColorC7(name, instant) {
  if (!C7_COLORS.includes(name) && !(DEBUG && C7 && C7.FINISHES[name])) return;
  c7Release();
  colorFor.c710n = name;
  state.color = name;
  if (!C7) { loadC7().then(() => { if (colorFor.c710n === name) setColorC7(name, true); }); return; }
  if (colorAnimC7) anims.delete(colorAnimC7);
  const from = C7.finishCopy(null, finishC7), to = C7.FINISHES[name];
  if (instant || state.device !== 'c710n') {
    finishC7 = C7.finishCopy(name);
    C7.applyFinish(finishC7); requestRender(); return;
  }
  const t0 = performance.now(), dur = 380;
  colorAnimC7 = (now) => {
    const t = smooth(clamp01((now - t0) / dur));
    for (const k of C7.KEYS) for (let i = 0; i < 3; i++) finishC7[k][i] = from[k][i] + (to[k][i] - from[k][i]) * t;
    C7.applyFinish(finishC7);
    return t < 1;
  };
  anims.add(colorAnimC7); requestRender();
}
let deviceToken = 0;
function setDevice(d) {
  const want = d === 'c710n' ? 'c710n' : 'xm5';
  if (want !== deviceTarget) {
    colorFor[deviceTarget] = state.color;
    deviceTarget = want;
    state.color = colorFor[want];
  }
  const token = ++deviceToken;
  const ready = want === 'c710n' ? loadC7() : state.model === 'case' ? loadCase() : loadBuds();
  ready.then(() => {
    if (token !== deviceToken) return;
    c7Release();
    if (state.device === want) { requestRender(); return; }
    canvas.style.opacity = '0';
    setTimeout(() => {
      if (token !== deviceToken) return;
      state.device = want; state.angle = 0; stopMotion();
      requestRender();
      requestAnimationFrame(() => { canvas.style.opacity = '1'; });
      if (want === 'c710n') setTimeout(c7Warm, 600);
    }, 170);
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
  setDevice: (d) => setDevice(String(d)),
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
  if (state.device === 'c710n') applyFinish(finish);   // the XM5 materials keep their black finish ready
  setColor(state.color, true);
  const first = state.device === 'c710n' ? loadC7()
    : state.model === 'case' ? loadCase() : loadBuds();   // decodes in the worker meanwhile
  scene.environment = buildEnvironment();
  mark('environment');
  const probe = diffuseProbe(); scene.add(probe); if (DEBUG) api._probe = probe;
  mark('light probe');
  await first;
  mark('model');
  new ResizeObserver(resize).observe(canvas);
  window.addEventListener('resize', resize);
  resize();
  applyPose();
  // compile() builds a program for every material in the scene, shown or not; a WF-C710N start
  // holds the model it isn't showing back until the first frame is up, as the XM5 loads it later
  c7Hold();
  await renderer.compileAsync(scene, camera);
  mark('shaders');
  renderer.render(scene, camera);
  mark('first frame, ' + (renderer.info.programs || []).length + ' programs' + (DEBUG ? ': ' + (renderer.info.programs || []).map((p) => p.name).join(' ') : ''));
  state.ready = api.isReady = true;
  host('ready');
  window.dispatchEvent(new Event('sunpods-ready'));
  // the other model loads once the first frame is up, then the decoder worker is released
  // (a WF-C710N start loads the XM5 files only if setDevice('xm5') asks for them)
  if (state.device === 'c710n') {
    setTimeout(() => {
      if (c7Release()) renderer.compileAsync(scene, camera).then(() => mark('other model ready')).catch(() => {});
      c7Warm();
    }, 600);
    return;
  }
  setTimeout(() => {
    (state.model === 'case' ? loadBuds() : loadCase()).then(() => {
      kase.root.visible = buds.root.visible = true;
      return renderer.compileAsync(scene, camera);   // warm the other model's programs while idle
    }).then(() => {
      applyPose();
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
  api.debugShot = async ({ color = 'black', model = 'buds', angle = 0, lid = 0, w = 1000, h = 712, device = state.device } = {}) => {
    await (device === 'c710n' ? loadC7() : model === 'case' ? loadCase() : loadBuds());
    c7Release();
    stopMotion(); anims.clear();
    deviceToken++; deviceTarget = state.device = device === 'c710n' ? 'c710n' : 'xm5';
    setColor(color, true);
    state.model = model; state.angle = angle; state.lid = lid;
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
  // WF-C710N tools (branding/render/c710n/tools): scene access for fitted-camera boards and clips
  api.debugC7 = async (load = true) => {
    if (load) await loadC7();
    c7Release();
    return { THREE, scene, camera, renderer, canvas, C7, state, applyPose, fitCamera, resize, stopMotion, anims, setColor,
      setDeviceNow: (d) => { deviceToken++; deviceTarget = state.device = d; } };
  };
  api.debugFinishC7 = (name, f) => { if (C7) C7.FINISHES[name] = f; };
  // rebuild the WF-C710N with trial shape values (tools only)
  api.debugRebuildC7 = async (overrides) => {
    await loadC7();
    const mod = await import('./c710n.js');
    scene.remove(C7.buds.root, C7.kase.root);
    C7 = mod.createC710N({ THREE, patched, lin, spec, coat, SHEEN_K, DEBUG, overrides, caseRest: blenderMatrixToThree(CASE_REST) });
    scene.add(C7.buds.root, C7.kase.root);
    finishC7 = C7.finishCopy(colorFor.c710n);
    C7.applyFinish(finishC7);
    await C7.ready;
    return true;
  };
}
