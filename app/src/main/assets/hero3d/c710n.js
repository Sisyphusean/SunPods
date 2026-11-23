// SunPods hero: the Sony WF-C710N earbuds and charging case, modelled in code (three.js r170) for
// the same scene, lights and material language as the WF-1000XM5 in hero3d.js.
//
// Everything is built once when the module loads (no per-frame geometry work): lofted and lathed
// BufferGeometry for the shells, conformal patches (raycast onto the shell) for prints, grilles,
// contacts and windows, and a 1024x512 print atlas (c710n_marks.png: SONY wordmark, L and R marks).
// Units are centimetres. Shapes and proportions were measured from Sony's product photos and the
// 360 viewer (branding/render/c710n/reference_analysis.md) and refined against them.
//
// createC710N(ctx) returns { buds, kase, FINISHES, KEYS, finishCopy, applyFinish, applyPose, LID_OPEN }
// for hero3d.js; ctx carries hero3d's THREE, patched() (rim light, specular AA) and lin().


// ---------------------------------------------------------------------------------------------
// Finishes. Linear RGB, solved against Sony's product shots of the four colours (one shoot, same
// light), see branding/render/c710n/tools/calib_c7.py. glass is 0 for the three opaque finishes and
// 1 for Glass Blue (clear shells, visible internals); every key is a triple so finishes crossfade.
const FINISHES = {
  black: {
    shell: [0.084, 0.084, 0.084], face: [0.084, 0.084, 0.084], tip: [0.120, 0.130, 0.150],
    grille: [0.070, 0.070, 0.070], print: [0.360, 0.360, 0.360],
    caseShell: [0.085, 0.085, 0.087], caseInner: [0.042, 0.042, 0.044], casePrint: [0.215, 0.215, 0.215],
    glass: [0, 0, 0], intTint: [0.02, 0.02, 0.02], lift: [0, 0, 0], tipA: [0.985, 0.985, 0.985], seatShade: [0.52, 0.52, 0.52],
    seamK: [0.28, 0.28, 0.28], rimK: [0.15, 0.15, 0.15], innerLift: [0, 0, 0], tipEdge: [0.3, 0.3, 0.3],
  },
  white: {
    shell: [0.935, 0.932, 0.912], face: [0.905, 0.902, 0.885], tip: [0.860, 0.910, 0.970],
    grille: [0.960, 0.975, 0.970], print: [0.260, 0.262, 0.264],
    caseShell: [1.000, 1.000, 0.995], caseInner: [1.000, 1.000, 0.995], casePrint: [0.860, 0.862, 0.856],
    glass: [0, 0, 0], intTint: [0.6, 0.6, 0.6], lift: [0.10, 0.10, 0.10], tipA: [0.34, 0.34, 0.34], seatShade: [0.92, 0.92, 0.92],
    seamK: [0.05, 0.05, 0.05], rimK: [0.85, 0.85, 0.85], innerLift: [0.18, 0.18, 0.18], tipEdge: [0.9, 0.9, 0.9],
  },
  pink: {
    shell: [0.880, 0.630, 0.575], face: [0.880, 0.630, 0.575], tip: [0.880, 0.700, 0.660],
    grille: [0.800, 0.555, 0.518], print: [0.600, 0.375, 0.340],
    caseShell: [0.970, 0.643, 0.592], caseInner: [0.870, 0.575, 0.531], casePrint: [0.620, 0.340, 0.310],
    glass: [0, 0, 0], intTint: [0.5, 0.3, 0.3], lift: [0.05, 0.05, 0.05], tipA: [0.97, 0.97, 0.97], seatShade: [0.80, 0.80, 0.80],
    seamK: [0.20, 0.20, 0.20], rimK: [0.60, 0.60, 0.60], innerLift: [0.05, 0.05, 0.05], tipEdge: [0.4, 0.4, 0.4],
  },
  blue: {   // Glass Blue: tint of the clear shell (deep teal-blue), the smoky disc, internals
    shell: [0.060, 0.160, 0.220], face: [0.020, 0.040, 0.080], tip: [0.130, 0.220, 0.300],
    grille: [0.450, 0.600, 0.720], print: [0.880, 0.910, 0.940],
    caseShell: [0.090, 0.210, 0.280], caseInner: [0.080, 0.205, 0.260], casePrint: [0.160, 0.260, 0.360],
    glass: [1, 1, 1], intTint: [0.055, 0.105, 0.155], lift: [0, 0, 0], tipA: [0.97, 0.97, 0.97], seatShade: [0.85, 0.85, 0.85],
    seamK: [0.28, 0.28, 0.28], rimK: [0.30, 0.30, 0.30], innerLift: [0, 0, 0], tipEdge: [0.4, 0.4, 0.4],
  },
};
const KEYS = Object.keys(FINISHES.black);

// ---------------------------------------------------------------------------------------------
// Earbud shape (right earbud), in units of the body height BH. Frame: origin at the centre of the
// flat touch disc, +z out of the face, +y up (as worn), +x towards the ear tip side.
const BH = 1.90;                      // body height in cm (0.31 of the case width, Sony in-box photo)
const BUD0 = {
  Rd: 0.290,                          // touch disc radius (a flat circular facet at z = 0)
  // Body sections behind the face: [z, centre x, centre y, radius every 22.5 deg from +x, counter-
  // clockwise seen from the face]. Measured on Sony's 360 viewer (visual hull of the nine turntable
  // frames, spin_sections.py), the ear tip cut away and its gap bridged; refined in the review loop
  // (iteration 02: chin underside +0.03, shoulder at the nozzle -0.025, upper back +0.025;
  // iteration 03: chin -0.015, top front corner +0.02; iteration 04: upper rear crown +0.025,
  // neck above the tip -0.015, chin -0.01; iteration 05: crown +0.02 back to -0.32, underside below
  // the tip +0.02, chin away from the tip -0.02; iteration 06: inner-face shoulder towards the tip
  // +0.03, disc-side +0.02, chin -0.02, body-tip join -0.02; iteration 07: shoulder at the tip join
  // -0.02, chin +0.01; iteration 08: shoulder at the tip join +0.02 again, the face rim squarer:
  // the first sections move a quarter of the way out towards the full body at 0.32 deep; iteration 09:
  // shoulder above the tip +0.02 more, top corner of the face rim +0.02; iteration 10: top and back
  // dome +0.02 from 0.32 deep, back away from the tip +0.015, chin +0.015 lower; iteration 11: face
  // rim and top edge +0.015, shoulder above the tip +0.01, chin 0.008 back up; iteration 12: shoulder
  // above the tip +0.015, chin +0.01 out near the face; iteration 13: underside behind the tip 0.01 up;
  // iteration 14: upper rear crown +0.02 from 0.55 deep, outer face lower half 0.01 in, chin 0.008 up).
  // The face-on outline is a circle (0.45) round the disc with a chin towards the tip that carries
  // the mic; the body tapers like a cone from the disc and closes round the inner side.
  sections: [
    [-0.070, 0.009, -0.010, 0.329, 0.338, 0.355, 0.425, 0.435, 0.459, 0.416, 0.407, 0.374, 0.347, 0.318, 0.327, 0.339, 0.342, 0.338, 0.354],
    [-0.120, 0.016, -0.017, 0.345, 0.359, 0.382, 0.448, 0.462, 0.486, 0.440, 0.443, 0.426, 0.404, 0.363, 0.359, 0.386, 0.391, 0.385, 0.386],
    [-0.180, 0.023, -0.025, 0.357, 0.375, 0.398, 0.461, 0.476, 0.495, 0.458, 0.485, 0.467, 0.439, 0.372, 0.396, 0.439, 0.452, 0.422, 0.416],
    [-0.250, 0.033, -0.035, 0.376, 0.397, 0.421, 0.458, 0.468, 0.488, 0.488, 0.501, 0.481, 0.454, 0.401, 0.422, 0.471, 0.492, 0.462, 0.457],
    [-0.320, 0.042, -0.045, 0.405, 0.414, 0.441, 0.509, 0.518, 0.536, 0.549, 0.523, 0.498, 0.464, 0.407, 0.434, 0.484, 0.514, 0.505, 0.509],
    [-0.400, 0.052, -0.056, 0.443, 0.439, 0.451, 0.545, 0.565, 0.585, 0.589, 0.537, 0.512, 0.479, 0.416, 0.441, 0.486, 0.512, 0.529, 0.548],
    [-0.480, 0.062, -0.067, 0.479, 0.415, 0.430, 0.561, 0.601, 0.619, 0.620, 0.579, 0.524, 0.490, 0.413, 0.427, 0.462, 0.513, 0.563, 0.590],
    [-0.550, 0.072, -0.077, 0.417, 0.417, 0.411, 0.522, 0.611, 0.631, 0.644, 0.575, 0.517, 0.478, 0.391, 0.401, 0.432, 0.448, 0.384, 0.371],
    [-0.610, 0.079, -0.085, 0.321, 0.315, 0.355, 0.470, 0.559, 0.617, 0.616, 0.542, 0.484, 0.435, 0.368, 0.368, 0.407, 0.417, 0.345, 0.312],
    [-0.660, 0.086, -0.092, 0.258, 0.242, 0.259, 0.385, 0.489, 0.552, 0.587, 0.501, 0.441, 0.395, 0.334, 0.335, 0.383, 0.383, 0.301, 0.259],
    [-0.700, 0.091, -0.098, 0.214, 0.193, 0.169, 0.256, 0.378, 0.434, 0.543, 0.459, 0.396, 0.358, 0.304, 0.313, 0.358, 0.353, 0.287, 0.239],
    [-0.735, 0.096, -0.103, 0.215, 0.200, 0.145, 0.190, 0.250, 0.290, 0.350, 0.370, 0.335, 0.315, 0.267, 0.280, 0.312, 0.318, 0.283, 0.240],







  ],
  backEnd: { z: -0.762, shrink: 0.45 },   // rounded closure behind the last section
  faceTilt: 0.09,                          // the face leans forward at the top (shear fading by 0.4 deep)
  // ear tip: a silicone mushroom on the nozzle. Distances along the tip axis from nozzle.from; the
  // shape from Sony's side views (side_L black: the tip is 1.38x wider than long, widest at its crisp
  // rear lip, a convex face with a big rounded rim) and the end-on 360 frames (a small round opening,
  // the lighter nozzle mouth seen through the silicone round it)
  nozzle: { from: [0.34, -0.28, -0.50], dir: [0.6589, 0.174, -0.7318], r: 0.085 },
  tip: { seatCap: 0.07, lip: 0.214, len: 0.426, r: 0.280, flare: 1.03, rim: 0.70, sag: 0.05, bore: 0.33, depth: 0.055, thick: 0.030 },
  // features on the face side, as face-on (x, y) positions; the patches are projected onto the shell
  // mic grille: projected half-way between the earbud's axis and the surface normal, so it reads as Sony's
  // near-round oval from the front and the side; whole holes in about eight rows inside a dark groove
  mic: { at: [0.080, -0.370], w: 0.215, h: 0.230, rot: 8, proj: 0.5, pitch: 0.25, hole: 0.044, field: 0.74, ringW: 0.022, ringK: 0.75 },
  airTop: { at: [-0.055, 0.428], w: 0.028, h: 0.018 },
  dotL: { at: [0.000, 0.440], d: 0.045 },       // tactile dot, left earbud only (mirrored x)
  logo: { th0: -13, th1: 33, rad: 0.400, h: 0.080, lShift: 0 },
  seam: { n: [0.10, 0.30, 1.0], z: -0.255, w: 0.0045 },
  // inner side (towards the ear), as (x, y) seen from behind the face; projected along +z onto the shell
  inner: {
    contactA: [0.08, 0.22], contactB: [-0.26, 0.03], contactD: 0.082,
    sensor: [0.17, 0.03], sensorW: 0.250, sensorH: 0.200,
    mark: [-0.17, -0.15], markD: 0.130,
    vent: [-0.30, -0.19], ventD: 0.030,
  },
};

// ---------------------------------------------------------------------------------------------
// Charging case, in cm. Frame: origin at the bottom centre, +y up, +z towards the front (LED), +x right.
const CASE0 = {
  A: 3.05, B: 1.40, endR: 0.72,       // plan: stadium half-width, half-depth, end radius (of B)
  H: 3.02, yP: 1.65,                  // overall height, parting line (base top)
  bottom: { v: 1.50, hx: 1.10, hz: 0.78, dish: 0.04 },        // base round-over: height, reach across, reach in depth
  top: { v: 1.20, hx: 1.45, hz: 0.92, dome: 0.02 }, // lid round-over and top dome
  lidGrow: 0.040,                                   // the lid's plan is a hair larger than the base's
  wall: 0.11, gap: 0.006,
  deck: 0.42,                         // depth of the earbud deck below the base rim
  slot: { w: 2.50, h: 0.16, y: 1.49, depth: 0.075 },   // finger slot on the base front
  led: { w: 0.12, h: 0.060, y: 1.10 },
  usb: { w: 0.90, h: 0.30, y: 0.95, x: -0.12 },
  button: { d: 0.34, y: 0.95, x: 0.72 },
  hinge: { w: 2.20, h: 0.07 },
  logo: { w: 1.44, z: 0.62 },
  lidOpenDeg: 105,
  // earbuds in their wells: position (cm, case frame) and rotation (deg, XYZ) of the right earbud;
  // the left one mirrors it in x
  // the right earbud sits in the right well (seen from the front), touch disc up and tipped back a
  // little, its chin (mic) towards the case end, ear tip down and back; the left earbud mirrors it
  // (pose chosen by tools/seat_search.js: nothing pokes through the walls with the lid closed, the
  // disc faces up within 20 deg, the mic sits high at the outer side, the earbuds nearly touch)
  budSeat: { pos: [1.12, 2.75, 0.15], x: [0, 0, -1], y: [-1, 0, 0], tilt: [-10, -30, -4], drop: 0.15, scale: 0.885 },
  tray: { r: 0.62, depth: 0.95 },
  well: { x: 1.15, z: 0.0 },          // ear-tip well centres (the ear tips go down into them)
};

function merge(a, b) {
  if (!b) return a;
  const o = Array.isArray(a) ? a.slice() : { ...a };
  for (const [k, v] of Object.entries(b)) o[k] = v && typeof v === 'object' && !Array.isArray(v) && a[k] ? merge(a[k], v) : v;
  return o;
}

// ---------------------------------------------------------------------------------------------
export function createC710N(ctx) {
  const { THREE, patched, lin } = ctx;
  // tools only (hero3d ?debug): try shape values without editing this file
  const BUD = ctx.DEBUG && ctx.overrides ? merge(BUD0, ctx.overrides.BUD) : BUD0;
  const CASE = ctx.DEBUG && ctx.overrides ? merge(CASE0, ctx.overrides.CASE) : CASE0;
  const V3 = (a) => new THREE.Vector3(a[0], a[1], a[2]);
  const rad = (d) => d * Math.PI / 180;

  // Laying decals, the grille, the SONY strip and the tip seat onto the shells casts thousands of
  // rays. Testing every shell triangle for each ray took over 20 s, so each target is split once into
  // spatial chunks, and a ray is only tested against the chunks whose bounds it crosses. three's own
  // Mesh.raycast still does every hit test, so points and normals come out exactly as before.
  let chunkCache = new WeakMap();   // cleared once the model is built
  function chunksOf(target) {
    if (!chunkCache) return null;
    const geo = target.geometry, pos = geo && geo.attributes.position;
    if (!pos || Array.isArray(target.material) || geo.morphAttributes.position) return null;
    const cached = chunkCache.get(target);
    if (cached && cached.geo === geo && cached.version === pos.version) return cached.meshes;
    const idx = geo.index, nTri = Math.floor((idx ? idx.count : pos.count) / 3);
    const vi = (t, k) => (idx ? idx.getX(t * 3 + k) : t * 3 + k);
    const bb = new THREE.Box3().setFromBufferAttribute(pos), size = bb.getSize(new THREE.Vector3());
    const G = 8, cell = (v, lo, s) => Math.min(G - 1, Math.max(0, Math.floor(((v - lo) / (s || 1)) * G)));
    const bins = new Map();
    for (let t = 0; t < nTri; t++) {
      let cx = 0, cy = 0, cz = 0;
      for (let k = 0; k < 3; k++) { const v = vi(t, k); cx += pos.getX(v); cy += pos.getY(v); cz += pos.getZ(v); }
      const key = cell(cx / 3, bb.min.x, size.x) + G * (cell(cy / 3, bb.min.y, size.y) + G * cell(cz / 3, bb.min.z, size.z));
      let b = bins.get(key); if (!b) bins.set(key, b = []); b.push(t);
    }
    const meshes = [];
    for (const tris of bins.values()) {
      const arr = new Float32Array(tris.length * 9);
      let o = 0;
      for (const t of tris) for (let k = 0; k < 3; k++) { const v = vi(t, k); arr[o++] = pos.getX(v); arr[o++] = pos.getY(v); arr[o++] = pos.getZ(v); }
      const g = new THREE.BufferGeometry(); g.setAttribute('position', new THREE.BufferAttribute(arr, 3));
      g.computeBoundingSphere(); g.computeBoundingBox();
      const m = new THREE.Mesh(g, target.material); m.matrixAutoUpdate = false;
      meshes.push(m);
    }
    chunkCache.set(target, { geo, version: pos.version, meshes });
    return meshes;
  }
  const hitInv = new THREE.Matrix4(), hitRay = new THREE.Ray();
  function firstHit(rc, target) {
    const meshes = chunksOf(target);
    if (!meshes) return rc.intersectObject(target, false)[0];
    hitInv.copy(target.matrixWorld).invert(); hitRay.copy(rc.ray).applyMatrix4(hitInv);
    const near = [];
    for (const m of meshes) if (hitRay.intersectsBox(m.geometry.boundingBox)) { m.matrixWorld.copy(target.matrixWorld); near.push(m); }
    return near.length ? rc.intersectObjects(near, false)[0] : undefined;
  }

  // ---------------------------------------------------------------------------------------------
  // Print atlas
  let atlasReady;
  const atlas = new THREE.TextureLoader().load('./c710n_marks.png', () => atlasReady && atlasReady());
  const ready = new Promise((r) => { atlasReady = r; });
  atlas.colorSpace = THREE.NoColorSpace;
  atlas.anisotropy = 4;
  atlas.generateMipmaps = true;
  atlas.minFilter = THREE.LinearMipmapLinearFilter;

  // ---------------------------------------------------------------------------------------------
  // Materials
  const M = {};
  const P = (o) => new THREE.MeshPhysicalMaterial(o);
  // matte moulded plastic, the XM5 case's shader (Blender Principled: specular 0.38, sheen 0.2,
  // clearcoat 0.12 at 0.45) with this model's roughness
  const plastic = (rough) => P({ roughness: rough, metalness: 0, specularIntensity: 0.76, sheen: 1, sheenRoughness: 0.8,
    sheenColor: new THREE.Color(0.1, 0.1, 0.1), clearcoat: 0.03, clearcoatRoughness: 0.45 });

  // Glass Blue: clear tinted plastic. Its colour is an absorption tint, not lit diffuse paint, so it
  // stays a saturated teal-blue in any light. Far walls (seen through the near ones) are alpha blended
  // and nearly opaque: they stand in for the backdrop seen through the plastic, so the page behind the
  // canvas hardly leaks through. Near walls draw in two passes: the transmit pass multiplies whatever is
  // behind by the plastic's transmission (clear in the middle, navy towards the silhouette, where the
  // path through the plastic is long), so the internals keep their contrast instead of being washed
  // over; the scatter pass then adds the plastic's faint own colour and its reflections.
  const GLASS_FRAG = /* glsl */`
{
  float nv = saturate(dot(normalize(normal), normalize(vViewPosition)));
  float edge = pow(1.0 - nv, uGlassPow);
  float a = mix(uGlassA.x, uGlassA.y, edge);
  vec3 tint = uGlassTint * mix(1.0, 0.15, edge) + uGlassEdgeHi * vec3(0.30, 0.42, 0.48) * pow(edge, 7.0);
  vec3 sp = reflectedLight.directSpecular + reflectedLight.indirectSpecular;
  #ifdef USE_CLEARCOAT
  sp += (clearcoatSpecularDirect + clearcoatSpecularIndirect) * material.clearcoat;
  #endif
  float a2 = saturate(a + dot(sp, vec3(0.333)));
  gl_FragColor = vec4((tint * a + sp) / max(a2, 1e-3), a2);
}`;
  // Glass Blue reflects a small studio: the hero's soft world plus two softboxes, so the clear
  // plastic shows crisp highlights (three.js turns this equirect map into a PMREM itself, once)
  const studioEnv = (() => {
    const W = 256, H = 128, data = new Uint16Array(W * H * 4);
    const box = (az, el, a0, a1, e0, e1) => (Math.abs(az - a0) < a1 && Math.abs(el - e0) < e1 ? 1 : 0);
    for (let j = 0; j < H; j++) {
      for (let i = 0; i < W; i++) {
        const az = ((i + 0.5) / W - 0.5) * 360, el = (0.5 - (j + 0.5) / H) * 180;
        let L = 0.30 + 0.45 * Math.max(0, Math.sin(el * Math.PI / 180));
        L += 3.5 * box(az, el, -120, 28, 48, 10);      // a broad softbox up and to the left
        L += 2.5 * box(az, el, 135, 5, 12, 26);       // a tall strip on the right
        L += 1.2 * box(az, el, 20, 30, -8, 4);        // a low bounce card behind
        const h = THREE.DataUtils.toHalfFloat(L), o = (j * W + i) * 4;
        data[o] = data[o + 1] = data[o + 2] = h; data[o + 3] = 15360;
      }
    }
    const t = new THREE.DataTexture(data, W, H, THREE.RGBAFormat, THREE.HalfFloatType);
    t.mapping = THREE.EquirectangularReflectionMapping;
    t.magFilter = t.minFilter = THREE.LinearFilter; t.generateMipmaps = false; t.needsUpdate = true;
    return t;
  })();
  const GLASS_S_FRAG = /* glsl */`
{
  float nv = saturate(dot(normalize(normal), normalize(vViewPosition)));
  float edge = pow(1.0 - nv, uGlassPow);
  vec3 sp = reflectedLight.directSpecular + reflectedLight.indirectSpecular;
  #ifdef USE_CLEARCOAT
  sp += (clearcoatSpecularDirect + clearcoatSpecularIndirect) * material.clearcoat;
  #endif
  vec3 cyan = uGlassEdgeHi * vec3(0.14, 0.46, 0.58) * pow(edge, 6.0);   // light piped along thick edges
  // added to the display-encoded framebuffer: scaled by the sRGB curve's slope at mid tones instead
  // of encoded (encoding a small linear value would add far too much over dark internals)
  gl_FragColor = vec4((uGlassTint * mix(uGlassS.x, uGlassS.y, edge) + cyan + sp) * 1.4, 0.0);
}`;
  // (both near-wall passes write after the last chunk: their values are used as is, not colour-managed)
  const GLASS_T_FRAG = /* glsl */`
{
  float nv = saturate(dot(normalize(normal), normalize(vViewPosition)));
  float edge = pow(1.0 - nv, uGlassPow);
  gl_FragColor = vec4(mix(uGlassTc, uGlassTe, edge) * GLASS_T_EXTRA, mix(uGlassA.x, uGlassA.y, edge));
}`;
  const GLASS_PARS = 'uniform vec2 uGlassA, uGlassS;\nuniform float uGlassPow;\nuniform vec3 uGlassTint, uGlassTc, uGlassTe;\nuniform float uGlassEdgeHi;\n';
  const blendTransmit = (m) => Object.assign(m, { transparent: true, depthWrite: false, blending: THREE.CustomBlending,
    blendEquation: THREE.AddEquation, blendSrc: THREE.ZeroFactor, blendDst: THREE.SrcColorFactor,
    blendEquationAlpha: THREE.AddEquation, blendSrcAlpha: THREE.OneFactor, blendDstAlpha: THREE.OneMinusSrcAlphaFactor });
  const blendScatter = (m) => Object.assign(m, { transparent: true, depthWrite: false, blending: THREE.CustomBlending,
    blendEquation: THREE.AddEquation, blendSrc: THREE.OneFactor, blendDst: THREE.OneFactor,
    blendEquationAlpha: THREE.AddEquation, blendSrcAlpha: THREE.ZeroFactor, blendDstAlpha: THREE.OneFactor });
  // pass: 'back' (alpha blended far wall), 'scatter' or 'transmit' (near wall)
  function glassMat(key, base, uni, pass = 'back', extra = {}) {
    const m = P({ envMap: studioEnv, ...base });
    m.transparent = true; m.depthWrite = false;
    if (pass === 'scatter') blendScatter(m);
    if (pass === 'transmit') blendTransmit(m);
    const frag = pass === 'back' ? { opaque_fragment: GLASS_FRAG } : pass === 'scatter' ? { dithering_fragment: GLASS_S_FRAG }
      : { dithering_fragment: GLASS_T_FRAG.replace('GLASS_T_EXTRA', extra.tExtra || '1.0') };
    return patched(m, key + '-' + pass, uni, { pars: GLASS_PARS + (extra.pars || ''), after: { ...(extra.after || {}), ...frag } });
  }
  const glassU = (a0, a1, pw, hi = 0, o = {}) => ({ uGlassA: { value: new THREE.Vector2(a0, a1) }, uGlassPow: { value: pw },
    uGlassTint: { value: new THREE.Color() }, uGlassEdgeHi: { value: hi },
    uGlassS: { value: new THREE.Vector2(...(o.S || [0.05, 0.45])) },
    uGlassTc: { value: new THREE.Color(...(o.Tc || [1, 1, 1])) }, uGlassTe: { value: new THREE.Color(...(o.Te || [0, 0, 0])) } });

  // earbud shell with the parting-line groove (a plane through the body) as a shading detail
  const seamN = V3(BUD.seam.n).normalize();
  const SEAM_PARS = /* glsl */`
varying vec3 vObjPos;
uniform vec3 uSeamN; uniform float uSeamD, uSeamW, uSeamK;
float seamMask() {
  float d = dot(vObjPos, uSeamN) - uSeamD;
  float w = max(fwidth(d), 1e-5);
  return 1.0 - smoothstep(uSeamW - w, uSeamW + w, abs(d));
}`;
  const seamU = () => ({ uSeamN: { value: seamN.clone() }, uSeamD: { value: BUD.seam.z * BH * seamN.z }, uSeamW: { value: BUD.seam.w * BH }, uSeamK: { value: 0.28 } });
  const SHELL_GLASS = { Tc: [0.72, 0.87, 0.92], Te: [0.05, 0.13, 0.22], S: [0.03, 0.45] };
  function budShellMat(glass, back = false) {
    // Glass Blue: the far wall nearly opaque (a backdrop seen through the plastic), the near wall in
    // the scatter pass (its transmit twin is M.shellGlassT)
    const u = { ...seamU(), ...(glass ? (back ? glassU(0.90, 0.98, 1.4, 0) : glassU(0.50, 0.95, 1.6, 1, SHELL_GLASS)) : {}) };
    const base = glass
      ? { roughness: 0.04, metalness: 0, specularIntensity: 0.7, clearcoat: 1.0, clearcoatRoughness: 0.02, side: THREE.FrontSide }
      : { roughness: 0.6, metalness: 0, specularIntensity: 0.76, sheen: 1, sheenRoughness: 0.8,
          sheenColor: new THREE.Color(0.1, 0.1, 0.1), clearcoat: 0.03, clearcoatRoughness: 0.45 };
    const seamAfter = {
      color_fragment: 'float sm = seamMask(); diffuseColor.rgb *= 1.0 - uSeamK * sm;',
      roughnessmap_fragment: 'roughnessFactor = mix(roughnessFactor, 0.7, sm);',
    };
    if (glass) {
      return glassMat('c7-shell-glass', base, u, back ? 'back' : 'scatter',
        { pars: SEAM_PARS.replace('varying vec3 vObjPos;\n', ''), after: seamAfter });
    }
    return patched(P(base), 'c7-shell', u, { pars: SEAM_PARS.replace('varying vec3 vObjPos;\n', ''), after: seamAfter });
  }
  M.shell = budShellMat(false);
  M.shellGlass = budShellMat(true);
  // its transmit twin shares the uniforms; the parting line shows as a darker line in the plastic
  M.shellGlassT = glassMat('c7-shell-glass', { roughness: 0.04, metalness: 0, side: THREE.FrontSide }, M.shellGlass.userData.uniforms, 'transmit',
    { pars: SEAM_PARS.replace('varying vec3 vObjPos;\n', ''), after: { color_fragment: 'float sm = seamMask();' }, tExtra: '(1.0 - 0.45 * sm)' });
  M.shellGlassBack = budShellMat(true, true); M.shellGlassBack.side = THREE.BackSide;
  // inside the case the seated earbuds sit in the case's shade (the XM5 case bakes this as AO)
  M.shellCase = budShellMat(false); M.shellCase.roughness = 0.85;
  M.face = plastic(0.55);
  // the Glass Blue disc: smoky, glossy, a metallic sheen
  M.faceSmoke = P({ envMap: studioEnv, roughness: 0.06, metalness: 0.2, clearcoat: 1, clearcoatRoughness: 0.02,
    transparent: true, opacity: 0.84 });
  M.faceCase = plastic(0.55);

  // custom shaders that need the mesh uv without a texture map
  function withUv(mat) {
    const prev = mat.onBeforeCompile;
    mat.onBeforeCompile = (sh, r) => {
      prev(sh, r);
      sh.vertexShader = sh.vertexShader
        .replace('#include <common>', '#include <common>\nvarying vec2 vC7Uv;')
        .replace('#include <begin_vertex>', '#include <begin_vertex>\nvC7Uv = uv;');
    };
    return mat;
  }
  // mic grille: the shell itself, perforated: an oval of small sparse holes (about six rows, spaced
  // about twice their diameter, a margin inside the outline) in a thin engraved outline
  const GRILLE_GLSL = /* glsl */`
{
  vec2 p = (vC7Uv - 0.5) * 2.0;               // -1..1 over the oval
  float r = length(p);
  if (r > 1.0) discard;                       // an oval on its patch
  vec2 q = p * vec2(${(BUD.mic.w / BUD.mic.h).toFixed(4)}, 1.0);   // round holes on the oval
  const float pitch = ${BUD.mic.pitch.toFixed(4)};   // hole spacing, in half-heights of the oval
  float rowH = pitch * 0.8660254;
  float j0 = floor(q.y / rowH + 0.5);
  float d = 9.0;
  vec2 cBest = vec2(0.0);
  for (int k = -1; k <= 1; k++) {
    float j = j0 + float(k);
    float sh = mod(j, 2.0) * 0.5;
    float i = floor(q.x / pitch - sh + 0.5);
    vec2 c = vec2((i + sh) * pitch, j * rowH);
    float dc = length(q - c);
    if (dc < d) { d = dc; cBest = c; }
  }
  float w = max(fwidth(d), 1e-5), rw = max(fwidth(r), 1e-5);
  // whole holes only: a hole is kept when its centre lies inside the perforated circle
  float inside = step(length(cBest / vec2(${(BUD.mic.w / BUD.mic.h).toFixed(4)}, 1.0)), ${BUD.mic.field.toFixed(4)});
  gHole = (1.0 - smoothstep(${BUD.mic.hole.toFixed(4)} - w, ${BUD.mic.hole.toFixed(4)} + w, d)) * inside;
  gRing = 1.0 - smoothstep(${BUD.mic.ringW.toFixed(4)} - rw, ${BUD.mic.ringW.toFixed(4)} + rw, abs(r - 0.945));
  gEdge = 1.0 - smoothstep(0.014 - rw, 0.014 + rw, abs(r - 0.978));   // the groove's lit edge
  diffuseColor.rgb *= (1.0 - 0.85 * gHole) * (1.0 - ${BUD.mic.ringK.toFixed(4)} * gRing) * (1.0 + 0.22 * gEdge);
}`;
  M.grille = withUv(patched(P({ roughness: 0.6, metalness: 0, specularIntensity: 0.76, sheen: 1, sheenRoughness: 0.8,
    sheenColor: new THREE.Color(0.1, 0.1, 0.1) }), 'c7-grille-' + JSON.stringify([BUD.mic.w, BUD.mic.h, BUD.mic.pitch, BUD.mic.hole, BUD.mic.field, BUD.mic.ringW, BUD.mic.ringK]), {}, {
    pars: 'varying vec2 vC7Uv;\nfloat gHole, gRing, gEdge;\n',
    after: {
      color_fragment: GRILLE_GLSL,
      roughnessmap_fragment: 'roughnessFactor = mix(roughnessFactor, 0.9, max(gHole, gRing));',
    },
  }));
  // Glass Blue shows a silver mesh behind the clear cover
  M.grilleGlass = withUv(patched(P({ roughness: 0.4, metalness: 0.3 }), 'c7-grille-metal-' + JSON.stringify([BUD.mic.w, BUD.mic.h, BUD.mic.pitch, BUD.mic.hole, BUD.mic.field, BUD.mic.ringW, BUD.mic.ringK]), {}, {
    pars: 'varying vec2 vC7Uv;\nfloat gHole, gRing, gEdge;\n',
    after: { color_fragment: GRILLE_GLSL, roughnessmap_fragment: 'roughnessFactor = mix(roughnessFactor, 0.9, max(gHole, gRing));',
      metalnessmap_fragment: 'metalnessFactor = mix(metalnessFactor, 0.0, max(gHole, gRing));' },
  }));
  // prints: tone-on-tone ink from the atlas (alpha masked), slightly glossier than the shell
  const printMat = (rect) => {
    const m = P({ roughness: 0.35, metalness: 0, transparent: true, depthWrite: false, alphaMap: atlas,
      polygonOffset: true, polygonOffsetFactor: -2, polygonOffsetUnits: -2 });
    m.userData.rect = rect;
    return m;
  };
  M.print = printMat();
  M.markR = P({ color: lin([0.85, 0.11, 0.04]), roughness: 0.4, transparent: true, depthWrite: false, alphaMap: atlas,
    polygonOffset: true, polygonOffsetFactor: -2, polygonOffsetUnits: -2 });
  M.markL = P({ color: lin([0.55, 0.56, 0.57]), roughness: 0.4, transparent: true, depthWrite: false, alphaMap: atlas,
    polygonOffset: true, polygonOffsetFactor: -2, polygonOffsetUnits: -2 });
  // silicone tips: lighter than the shells and a little translucent
  const tipAxis = (() => {
    const d = V3(BUD.nozzle.dir).normalize(), o = V3(BUD.nozzle.from).multiplyScalar(BH).addScaledVector(d, BUD.tip.lip * BH);
    const ex = new THREE.Vector3(1, 0, 0).projectOnPlane(d).normalize(), ey = new THREE.Vector3().crossVectors(d, ex);
    return { o, d, ex, ey, R: BUD.tip.r * BH };
  })();
  M.tip = P({ roughness: 0.5, metalness: 0, specularIntensity: 0.7, sheen: 1, sheenRoughness: 0.4,
    sheenColor: new THREE.Color(0.12, 0.12, 0.12), side: THREE.DoubleSide });
  // Opaque on purpose: a see-through double-sided tip drew its inside over its outside in patches (light
  // bands on pink, a hollow look on white). The frosted look comes from the soft darkened edge (tipEdge).
  // the ear-tip size marks: small embossed dots in short rows near the rear lip, in the silicone colour
  patched(M.tip, 'c7-tip', { uTipO: { value: tipAxis.o }, uTipD: { value: tipAxis.d }, uTipX: { value: tipAxis.ex }, uTipY: { value: tipAxis.ey }, uEdgeA: { value: 0.3 } }, {
    pars: 'uniform vec3 uTipO, uTipD, uTipX, uTipY;\nuniform float uEdgeA;\n',
    after: { opaque_fragment: `
{
  float nv = abs(dot(normalize(normal), normalize(vViewPosition)));
  float e = pow(1.0 - nv, 2.5) * uEdgeA;
  gl_FragColor.a = mix(gl_FragColor.a, 1.0, e);
  gl_FragColor.rgb *= 1.0 - 0.35 * e;
}`, color_fragment: `
{
  vec3 q0 = vObjPos - uTipO;
  float s0 = dot(q0, uTipD), r0 = length(q0 - uTipD * s0);
  if (s0 < ${(0.5 * 0.30 * BH).toFixed(4)} && r0 < ${(0.27 * BH).toFixed(4)}) diffuseColor.rgb *= 0.55;
}
if (gl_FrontFacing) {
  vec3 q = vObjPos - uTipO;
  float s = dot(q, uTipD);
  float a = atan(dot(q, uTipY), dot(q, uTipX));
  float arc = a * ${tipAxis.R.toFixed(4)};                       // length round the skirt
  const float P = ${(0.032 * BH).toFixed(4)}, RD = ${(0.0085 * BH).toFixed(4)};
  float grp = ${(Math.PI / 3 * tipAxis.R).toFixed(4)};            // a group every 60 deg
  float ga = mod(arc + grp * 0.5, grp) - grp * 0.5;               // position within the group
  float col = clamp(floor(ga / P + 0.5), -1.0, 1.0), row = clamp(floor((s - ${(0.012 * BH).toFixed(4)}) / P + 0.5), 0.0, 2.0);
  vec2 dc = vec2(ga - col * P, s - (${(0.012 * BH).toFixed(4)} + row * P));
  float d = length(dc) / RD;
  float w = fwidth(d) + 1e-4;
  float rim = (1.0 - smoothstep(0.75 - w, 0.75 + w, abs(d - 0.8) + 0.75)) ;
  float dotIn = 1.0 - smoothstep(0.7 - w, 0.7 + w, d);
  float ring = smoothstep(0.65 - w, 0.65 + w, d) * (1.0 - smoothstep(1.0 - w, 1.0 + w, d));
  float lit = clamp(dc.y / RD, -1.0, 1.0);
  diffuseColor.rgb *= 1.0 + 0.04 * dotIn - 0.07 * ring * (0.5 - 0.5 * lit);
}` },
  });
  M.nozzle = P({ color: lin([0.03, 0.03, 0.033]), roughness: 0.5 });
  M.nozzleIn = P({ color: lin([0.05, 0.05, 0.05]), roughness: 0.6 });
  // the opening's parts: drawn after the silicone (which writes depth), so they show only through the
  // opening, never through the translucent cap
  M.nozzleMouth = P({ color: lin([0.2, 0.2, 0.2]), roughness: 0.6, transparent: true });   // the nozzle's end
  M.bore = P({ color: lin([0.02, 0.02, 0.022]), roughness: 0.8, transparent: true });       // its thin dark rim
  M.tipTube = P({ color: lin([0.1, 0.1, 0.1]), roughness: 0.6, side: THREE.BackSide, transparent: true });   // the opening's wall
  M.contact = P({ color: lin([0.80, 0.56, 0.24]), metalness: 1, roughness: 0.22 });
  M.contactRing = P({ color: lin([0.02, 0.02, 0.022]), roughness: 0.5 });
  M.sensor = shapeMat({ color: lin([0.006, 0.006, 0.007]), roughness: 0.18 },
    'sensor', 'rboxR', BUD.inner.sensorW * BH, BUD.inner.sensorH * BH);
  M.hole = P({ color: lin([0.004, 0.004, 0.004]), roughness: 0.8 });
  // Glass Blue internals (seen through the clear shells): dark tinted mouldings and a few metals
  M.int = P({ color: lin([0.015, 0.05, 0.09]), roughness: 0.6 });
  M.intLight = P({ color: lin([0.22, 0.40, 0.52]), roughness: 0.3 });
  M.intMetal = P({ color: lin([0.34, 0.36, 0.38]), metalness: 1, roughness: 0.35 });
  M.intCore = P({ color: lin([0.050, 0.068, 0.085]), roughness: 0.6 });   // the dark acoustic core
  M.intMetalDark = P({ color: lin([0.06, 0.07, 0.08]), metalness: 0, roughness: 0.5 });   // (no silver ring through the shell)
  M.intBoard = P({ color: lin([0.045, 0.060, 0.075]), roughness: 0.45 });
  M.intLayer = P({ color: lin([0.14, 0.18, 0.22]), roughness: 0.4 });
  M.intGrey = P({ color: lin([0.10, 0.11, 0.12]), roughness: 0.45 });
  M.magnetBlock = P({ color: lin([0.015, 0.016, 0.018]), roughness: 0.4 });
  M.intPale = P({ color: lin([0.45, 0.66, 0.78]), roughness: 0.3 });
  M.discRim = P({ color: lin([0.65, 0.80, 0.88]), roughness: 0.1, transparent: true, opacity: 0.55, depthWrite: false });
  M.intDriver = P({ color: lin([0.07, 0.12, 0.16]), roughness: 0.4 });

  // case
  M.caseShell = plastic(0.68);
  M.caseShell.clearcoat = 0.04; M.caseShell.clearcoatRoughness = 0.4;
  // the finger slot reads as a dark groove: shade its recess (object space, base front only)
  {
    const sl = CASE.slot;
    patched(M.caseShell, 'c7-case-shell', {}, {
      after: { color_fragment: `
{
  float dx = max(abs(vObjPos.x) - ${(sl.w / 2 - sl.h / 2).toFixed(4)}, 0.0), dy = vObjPos.y - ${sl.y.toFixed(4)};
  float d = length(vec2(dx, dy)) / ${(sl.h / 2).toFixed(4)};
  float inSlot = (1.0 - smoothstep(0.80, 1.0, d)) * step(${(CASE.B * 0.5).toFixed(4)}, vObjPos.z);
  diffuseColor.rgb *= 1.0 - 0.45 * inSlot * smoothstep(-1.0, 0.3, -dy / ${(sl.h / 2).toFixed(4)});
}` },
    });
  }
  M.caseRim = P({ roughness: 0.7, metalness: 0, specularIntensity: 0.5 });
  M.boss = P({ roughness: 0.75, metalness: 0, specularIntensity: 0.5 });   // the magnet tab, a shade under the lid's inside
  M.caseBaseRim = plastic(0.6);
  M.caseShellGlass = glassMat('c7-case-glass', { roughness: 0.04, metalness: 0, clearcoat: 1, clearcoatRoughness: 0.02 },
    glassU(0.45, 0.95, 1.2, 1, { Tc: [0.48, 0.68, 0.82], Te: [0.025, 0.07, 0.16], S: [0.15, 0.40] }), 'scatter');
  M.caseShellGlassT = glassMat('c7-case-glass', { roughness: 0.04, metalness: 0 }, M.caseShellGlass.userData.uniforms, 'transmit');
  M.lidShellGlass = glassMat('c7-case-glass', { roughness: 0.04, metalness: 0, clearcoat: 1, clearcoatRoughness: 0.02 },
    glassU(0.45, 0.95, 1.2, 1, { Tc: [0.74, 0.88, 0.96], Te: [0.05, 0.13, 0.24], S: [0.35, 0.50] }), 'scatter');
  M.lidShellGlassT = glassMat('c7-case-glass', { roughness: 0.04, metalness: 0 }, M.lidShellGlass.userData.uniforms, 'transmit');
  M.caseShellGlassBack = glassMat('c7-case-glass', { roughness: 0.04, metalness: 0, clearcoat: 1, clearcoatRoughness: 0.02, side: THREE.BackSide }, glassU(0.88, 0.98, 1.4, 0));
  // Glass Blue rims (the cut faces of the thick walls at the parting line) read as dark navy
  M.caseRimGlass = P({ envMap: studioEnv, color: lin([0.008, 0.035, 0.065]), roughness: 0.25, clearcoat: 1, clearcoatRoughness: 0.05 });
  M.caseInner = P({ roughness: 0.75, metalness: 0, specularIntensity: 0.5, side: THREE.DoubleSide });
  M.caseInnerGlass = glassMat('c7-caseinner-glass', { roughness: 0.06, metalness: 0, side: THREE.DoubleSide },
    glassU(0.35, 0.85, 1.6, 0, { Tc: [0.80, 0.94, 0.98], Te: [0.30, 0.50, 0.62], S: [0.03, 0.30] }), 'scatter');
  M.caseInnerGlassT = glassMat('c7-caseinner-glass', { roughness: 0.06, metalness: 0, side: THREE.DoubleSide }, M.caseInnerGlass.userData.uniforms, 'transmit');
  M.casePrint = P({ roughness: 0.2, metalness: 0, transparent: true, depthWrite: false, alphaMap: atlas, side: THREE.DoubleSide,
    polygonOffset: true, polygonOffsetFactor: -2, polygonOffsetUnits: -2 });
  // small parts drawn on conformal patches: the outline is cut in the shader (pill, rounded box, disc)
  function shapeMat(base, key, shape, w, h, extra = '') {
    const m = P(base);
    return withUv(patched(m, 'c7-shape-' + key, { uSz: { value: new THREE.Vector2(w, h) } }, {
      pars: 'varying vec2 vC7Uv;\nuniform vec2 uSz;\nfloat shRing;\n',
      after: {
        color_fragment: `
{
  vec2 p = (vC7Uv - 0.5) * uSz;
  float d;
  ${shape === 'pill' ? 'd = length(max(abs(p) - vec2(0.5 * (uSz.x - uSz.y), 0.0), 0.0)) - 0.5 * uSz.y;' : ''}
  ${shape === 'rbox' ? 'vec2 q = abs(p) - 0.5 * uSz + 0.3 * uSz.y; d = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - 0.3 * uSz.y;' : ''}
  ${shape === 'rboxR' ? 'vec2 q = abs(p) - 0.5 * uSz + 0.45 * uSz.y; d = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - 0.45 * uSz.y;' : ''}
  ${shape === 'disc' ? 'd = length(p) - 0.5 * uSz.x;' : ''}
  if (d > 0.0) discard;
  shRing = 0.0;
  ${extra}
}`,
      },
    }));
  }
  M.led = shapeMat({ color: lin([0.012, 0.012, 0.013]), roughness: 0.08, clearcoat: 0.5, clearcoatRoughness: 0.04, emissive: new THREE.Color(0, 0, 0) },
    'led', 'pill', CASE.led.w, CASE.led.h);
  M.port = shapeMat({ color: lin([0.006, 0.006, 0.007]), roughness: 0.4 }, 'usb', 'pill', CASE.usb.w, CASE.usb.h);
  // the port's metal shell: a thin bright ring just inside the opening
  M.portShell = shapeMat({ color: lin([0.55, 0.56, 0.58]), metalness: 1, roughness: 0.3 }, 'usbshell', 'pill', CASE.usb.w - 0.05, CASE.usb.h - 0.05, 'if (d < -0.03) discard;');
  M.portTongue = shapeMat({ color: lin([0.03, 0.03, 0.032]), roughness: 0.35 }, 'tongue', 'rbox', CASE.usb.w * 0.66, CASE.usb.h * 0.30);
  // pairing button: a disc in the case colour inside a fine dark groove
  M.button = shapeMat({ roughness: 0.5, emissive: new THREE.Color(0, 0, 0) }, 'button', 'disc', CASE.button.d + 0.07, CASE.button.d + 0.07,
    'float r = length(p); shRing = 1.0 - smoothstep(0.012, 0.02, abs(r - 0.5 * uSz.x + 0.022)); diffuseColor.rgb *= 1.0 - 0.9 * shRing;');
  M.caseContact = M.contact;
  M.magnet = P({ color: lin([0.45, 0.46, 0.48]), metalness: 1, roughness: 0.35 });

  for (const [k, m] of Object.entries(M)) {
    m.name = 'C7_' + k;
    if (!m.userData.uniforms) patched(m, 'sunpods-plain');   // shares the XM5's plain programs (rim light)
  }

  // ---------------------------------------------------------------------------------------------
  // Earbud geometry
  // Catmull-Rom through four values
  const cr = (p0, p1, p2, p3, t) => 0.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t * t + (-p0 + 3 * p1 - 3 * p2 + p3) * t * t * t);
  // stations: the disc edge, the measured sections (lightly smoothed round each ring), a rounded closure
  const STATIONS = (() => {
    const st = [[0, 0, 0, ...new Array(16).fill(BUD.Rd)]];
    for (const s of BUD.sections) {
      const r = s.slice(3), sm = r.map((v, i) => 0.25 * r[(i + 15) % 16] + 0.5 * v + 0.25 * r[(i + 1) % 16]);
      st.push([s[0], s[1], s[2], ...sm]);
    }
    const last = st[st.length - 1], be = BUD.backEnd;
    const zl = last[0];
    st.push([zl + (be.z - zl) * 0.55, last[1], last[2], ...last.slice(3).map((v) => v * (0.5 + 0.5 * be.shrink + 0.15))]);
    st.push([be.z, last[1], last[2], ...last.slice(3).map((v) => v * be.shrink)]);
    return st;
  })();
  function stationAt(u) {   // u in [0, n-1], Catmull-Rom between stations (ends clamped)
    const n = STATIONS.length, i = Math.min(n - 2, Math.floor(u)), t = u - i;
    const S = (k) => STATIONS[Math.max(0, Math.min(n - 1, k))];
    const a = S(i - 1), b = S(i), c = S(i + 1), d = S(i + 2);
    return b.map((_, k) => (k === 0 && (i === 0 || i >= n - 2)) ? b[k] + (c[k] - b[k]) * t : cr(a[k], b[k], c[k], d[k], t));
  }
  function ringRadius(st, thDeg) {   // periodic Catmull-Rom over the 16 radii
    const x = (((thDeg % 360) + 360) % 360) / 22.5, i = Math.floor(x), t = x - i;
    const R = (k) => st[3 + ((k % 16) + 16) % 16];
    return cr(R(i - 1), R(i), R(i + 1), R(i + 2), t);
  }
  function shellGeometry() {
    const NU = 160, PER = 7;
    const nSt = STATIONS.length, NV = (nSt - 1) * PER;
    const pos = [], uv = [], idx = [];
    let last = null;
    for (let j = 0; j <= NV; j++) {
      // rows ease in at the disc edge so the crease is crisp
      const u = Math.min(nSt - 1, j / PER);
      const st = stationAt(u);
      last = st;
      for (let i = 0; i < NU; i++) {
        const th = 360 * i / NU, a = rad(th), r = ringRadius(st, th);
        const y = st[2] + r * Math.sin(a), w = Math.min(1, Math.max(0, 1 + st[0] / 0.4));
        pos.push((st[1] + r * Math.cos(a)) * BH, y * BH, (st[0] + BUD.faceTilt * y * w) * BH);
        uv.push(i / NU, j / NV);
      }
    }
    // inner pole
    const pole = pos.length / 3;
    pos.push(last[1] * BH, last[2] * BH, (last[0] - 0.004) * BH); uv.push(0.5, 1);
    for (let j = 0; j < NV; j++) {
      for (let i = 0; i < NU; i++) {
        const i1 = (i + 1) % NU;
        const a = j * NU + i, b = j * NU + i1, c = (j + 1) * NU + i, d = (j + 1) * NU + i1;
        if (j === NV - 1) { idx.push(a, pole, b); continue; }
        idx.push(a, c, b, b, c, d);
      }
    }
    const g = new THREE.BufferGeometry();
    g.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3));
    g.setAttribute('uv', new THREE.Float32BufferAttribute(uv, 2));
    g.setIndex(idx);
    g.computeVertexNormals();
    return g;
  }
  function faceGeometry() {
    const g = new THREE.CircleGeometry(BUD.Rd * BH, 160);
    const p = g.getAttribute('position');
    for (let i = 0; i < p.count; i++) p.setZ(i, BUD.faceTilt * p.getY(i));
    g.computeVertexNormals();
    // circle lies in xy facing +z already
    const u = g.getAttribute('uv');
    for (let i = 0; i < u.count; i++) u.setXY(i, u.getX(i), u.getY(i));
    return g;
  }

  // Lathe around an arbitrary axis: profile [[r, s], ...] (s along the axis from `from`)
  function latheAlong(profile, from, dir, segs = 64) {
    const g = new THREE.LatheGeometry(profile.map((p) => new THREE.Vector2(p[0], p[1])), segs);
    const q = new THREE.Quaternion().setFromUnitVectors(new THREE.Vector3(0, 1, 0), dir.clone().normalize());
    g.applyQuaternion(q);
    g.translate(from.x, from.y, from.z);
    return g;
  }
  function tipGeometries(body) {
    const nz = BUD.nozzle, tp = BUD.tip;
    const dir = V3(nz.dir).normalize();
    const from = V3(nz.from).multiplyScalar(BH);
    const base = from.clone().add(dir.clone().multiplyScalar(tp.lip * BH));   // the skirt's rear lip
    const L = tp.len * BH, R = tp.r * BH, th = tp.thick * BH;
    const ro = tp.bore * R, dep = tp.depth * BH;                         // the opening: a short tube
    const rr = tp.rim * R, sag = tp.sag * R, Rl = R * tp.flare, sRim = L - sag - rr;
    // the silicone as one lathe profile: the convex face from the opening, the big rounded rim, the
    // skirt back to a crisp lip a touch wider than the cap, the back closed round the stem, and the
    // inside of the cap up to the bottom of the opening's tube
    const prof = [];
    for (let k = 0; k <= 10; k++) { const t = k / 10; prof.push([ro + (R - rr - ro) * t, L - sag * t * t]); }
    for (let k = 1; k <= 16; k++) { const a = (k / 16) * Math.PI / 2; prof.push([R - rr + rr * Math.sin(a), L - sag - rr * (1 - Math.cos(a))]); }
    for (let k = 1; k <= 8; k++) { const t = k / 8; prof.push([R + (Rl - R) * t + 0.03 * R * Math.sin(Math.PI * t), sRim * (1 - t) + (k === 8 ? 0 : 0)]); }
    prof.splice(prof.length - 1, 0, [Rl, 0.004 * BH]);                 // a crisp rear edge
    prof.push([Rl - 0.004 * BH, 0]);
    prof.push([Rl - th, 0]);
    prof.push([Rl - th * 1.2, 0.22 * sRim]);                         // the inside of the skirt
    prof.push([nz.r * BH + 0.006 * BH, 0.42 * sRim]);                 // closing onto the stem
    const tip = latheAlong(prof.map((q) => [Math.max(0.0001, q[0]), q[1]]), base, dir, 96);
    // the opening's wall (the silicone inside it, in shade) and, at its bottom, the nozzle's end:
    // a light disc with a thin rim
    const tube = latheAlong([[ro, L - dep], [ro, L + 0.0005 * BH]], base, dir, 48);
    const mouth = latheAlong([[ro * 0.97, L - dep], [0.0001, L - dep]], base, dir, 48);
    const mouthRim = latheAlong([[ro, L - dep + 0.0008 * BH], [ro * 0.84, L - dep + 0.0008 * BH]], base, dir, 48);
    // seat the skirt flush on the body: per angle round the axis, how far behind the lip plane the
    // body is (its outer edge), and stretch the skirt's rear part back to it
    if (body) {
      const ex = new THREE.Vector3(1, 0, 0).projectOnPlane(dir).normalize(), ey = new THREE.Vector3().crossVectors(dir, ex);
      const NA = 96, back = new Float32Array(NA), rc = new THREE.Raycaster(), CAP = (BUD.tip.seatCap ?? 0.03) * BH;   // reach at most 0.03 back
      body.updateWorldMatrix(true, false);
      for (let k = 0; k < NA; k++) {
        const a = (k / NA) * Math.PI * 2;
        const o = base.clone().addScaledVector(ex, Math.cos(a) * Rl).addScaledVector(ey, Math.sin(a) * Rl).addScaledVector(dir, 0.02 * BH);
        rc.set(o, dir.clone().negate()); rc.far = 0.2 * BH;
        const h = firstHit(rc, body);
        back[k] = Math.min(CAP, Math.max(0, h ? h.distance - 0.02 * BH - 0.012 * BH : CAP));   // the lip stands just clear of the body
      }
      // smooth round the circle so the seated edge stays a clean line
      const sm = new Float32Array(NA);
      for (let k = 0; k < NA; k++) { let t = 0; for (let j = -4; j <= 4; j++) t += back[(k + j + NA) % NA]; sm[k] = t / 9; }
      back.set(sm);
      const pos = tip.getAttribute('position'), v = new THREE.Vector3();
      for (let i = 0; i < pos.count; i++) {
        v.fromBufferAttribute(pos, i).sub(base);
        const sv = v.dot(dir);
        if (sv > sRim) continue;
        const a = Math.atan2(v.dot(ey), v.dot(ex)), u = ((a / (Math.PI * 2)) % 1 + 1) % 1 * NA;
        const k0 = Math.floor(u) % NA, k1 = (k0 + 1) % NA, f = u - Math.floor(u);
        const b = back[k0] * (1 - f) + back[k1] * f;
        const w = Math.min(1, Math.max(0, 1 - sv / (0.45 * sRim)));
        pos.setXYZ(i, pos.getX(i) - dir.x * b * w, pos.getY(i) - dir.y * b * w, pos.getZ(i) - dir.z * b * w);
      }
      pos.needsUpdate = true; tip.computeVertexNormals();
    }
    // the nozzle stem (hidden under the seated skirt)
    const sLip = tp.lip * BH;
    const nozzle = latheAlong([[0.0001, 0], [nz.r * BH, 0], [nz.r * BH, sLip - 0.004 * BH], [0.0001, sLip - 0.004 * BH]], from, dir, 48);
    // the stem's part inside the skirt's hollow, in the silicone's own (shaded) colour, so nothing
    // dark reads through the silicone
    const nozzleIn = latheAlong([[nz.r * BH, sLip - 0.004 * BH], [nz.r * BH, sLip + 0.42 * sRim]], from, dir, 48);
    return { nozzle, nozzleIn, tip, tube, mouth, mouthRim, frame: { base, dir, sRim, L } };
  }

  // Conformal patches: a grid in a local frame, projected onto a target mesh along -n
  const ray = new THREE.Raycaster();
  function conformal(target, center, n, up, w, h, opts = {}) {
    const N = opts.n || 18, off = opts.off ?? 0.004, flipU = !!opts.flipU;
    n = n.clone().normalize();
    const xA = new THREE.Vector3().crossVectors(up, n).normalize();
    const yA = new THREE.Vector3().crossVectors(n, xA).normalize();
    const pos = [], uv = [], idx = [];
    const rect = opts.rect || [0, 0, 1, 1];        // atlas u0 v0 u1 v1 (v from the top)
    target.updateWorldMatrix(true, false);
    const inv = new THREE.Matrix4().copy(target.matrixWorld).invert();
    for (let j = 0; j <= N; j++) {
      for (let i = 0; i <= N; i++) {
        const u = i / N, v = j / N;
        let lx = (u - 0.5) * w, ly = (v - 0.5) * h;
        const p = center.clone().addScaledVector(xA, lx).addScaledVector(yA, ly);
        const o = p.clone().addScaledVector(n, 0.6 * BH);
        ray.set(o, n.clone().negate());
        const hit = firstHit(ray, target);
        let q = hit ? hit.point.clone() : p.clone();
        let nn = n;
        if (hit && hit.face) nn = hit.face.normal.clone().transformDirection(target.matrixWorld);
        q.addScaledVector(nn, off);
        q.applyMatrix4(inv);
        pos.push(q.x, q.y, q.z);
        const uu = flipU ? 1 - u : u;
        uv.push(rect[0] + (rect[2] - rect[0]) * uu, 1 - (rect[1] + (rect[3] - rect[1]) * (1 - v)));
      }
    }
    for (let j = 0; j < N; j++) for (let i = 0; i < N; i++) {
      const a = j * (N + 1) + i, b = a + 1, c = a + N + 1, d = c + 1;
      idx.push(a, b, c, b, d, c);
    }
    const g = new THREE.BufferGeometry();
    g.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3));
    g.setAttribute('uv', new THREE.Float32BufferAttribute(uv, 2));
    g.setIndex(idx);
    g.computeVertexNormals();
    return g;
  }
  function surfacePoint(target, xy, dirZ) {
    // point on the shell seen along z (face side: dirZ = -1 looks from +z; inner side: +1 from -z)
    const o = new THREE.Vector3(xy[0] * BH, xy[1] * BH, dirZ < 0 ? 1.0 * BH : -1.6 * BH);
    ray.set(o, new THREE.Vector3(0, 0, dirZ < 0 ? -1 : 1));
    const hit = firstHit(ray, target);
    if (!hit) return { p: o, n: new THREE.Vector3(0, 0, -dirZ) };
    return { p: hit.point.clone(), n: hit.face.normal.clone() };
  }
  const ATL = {   // atlas rects (u0, v0 from top, u1, v1)
    sony: [0.04, 0.03, 0.96, 0.47], markL: [0, 0.5, 0.25, 1], markR: [0.25, 0.5, 0.5, 1],
  };
  // the SONY wordmark follows the rim: a strip of quads along an arc on the shoulder
  function logoStrip(target, side) {
    // the centre line runs round the disc at a fixed face-on radius; the letter height is measured
    // on the surface itself (the shoulder is steep there, so a face-on band would stretch the letters)
    const lg = BUD.logo, N = 48, hh = lg.h * BH / 2;
    const pos = [], uv = [], idx = [];
    const rect = ATL.sony;
    const cen = [];
    // a straight line seen face-on (Sony's wordmark runs straight up the side, not round the disc):
    // the chord between the arc's ends, its middle at the logo radius
    const sh = side === 'L' ? (lg.lShift || 0) : 0;
    const a0 = rad(lg.th0 + sh), a1 = rad(lg.th1 + sh), re = lg.rad * BH / Math.cos((a1 - a0) / 2);
    for (let i = 0; i <= N; i++) {
      const t = i / N;
      const x = (Math.cos(a0) * (1 - t) + Math.cos(a1) * t) * re, y = (Math.sin(a0) * (1 - t) + Math.sin(a1) * t) * re;
      ray.set(new THREE.Vector3(x, y, BH), new THREE.Vector3(0, 0, -1));
      const hit = firstHit(ray, target);
      cen.push(hit ? { p: hit.point.clone(), n: hit.face.normal.clone() } : null);
    }
    const project = (q, n) => {
      ray.set(q.clone().addScaledVector(n, 0.3 * BH), n.clone().negate());
      const hit = firstHit(ray, target);
      return hit ? hit.point.clone().addScaledVector(hit.face.normal, 0.003) : q;
    };
    for (let i = 0; i <= N; i++) {
      const c = cen[i], a = cen[Math.max(0, i - 1)], b = cen[Math.min(N, i + 1)];
      const tan = b.p.clone().sub(a.p).normalize();
      let m = new THREE.Vector3().crossVectors(c.n, tan).normalize();
      if (m.z < 0) m.negate();                     // m points towards the disc
      for (let j = 0; j <= 1; j++) {
        const q = project(c.p.clone().addScaledVector(m, j === 0 ? hh : -hh), c.n);
        pos.push(q.x, q.y, q.z);
        // reading along the arc (bottom to top on the right earbud), letter tops towards the disc
        const t = i / N, u = side === 'L' ? 1 - t : t;
        const v = j === 0 ? 0 : 1;                 // j=0 towards the disc = letter top
        uv.push(rect[0] + (rect[2] - rect[0]) * u, 1 - (rect[1] + (rect[3] - rect[1]) * v));
      }
    }
    for (let i = 0; i < N; i++) { const a = i * 2; idx.push(a, a + 1, a + 2, a + 1, a + 3, a + 2); }
    const g = new THREE.BufferGeometry();
    g.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3));
    g.setAttribute('uv', new THREE.Float32BufferAttribute(uv, 2));
    g.setIndex(idx); g.computeVertexNormals();
    return g;
  }

  // Glass Blue internals of an earbud: a dark acoustic core, battery, board, the driver ring
  function budInternals() {
    // separate dark parts: the acoustic housing round the nozzle, the coin battery, the board under
    // the disc, a magnet pair, flex loops, and the driver behind the inner side (dim)
    const grp = new THREE.Group(); grp.name = 'C7_BudInternals';
    const add = (geo, mat, pos, rot) => {
      const m = new THREE.Mesh(geo, mat); m.position.set(pos[0] * BH, pos[1] * BH, pos[2] * BH);
      if (rot) m.rotation.set(rot[0], rot[1], rot[2]);
      grp.add(m); return m;
    };
    const housing = add(new THREE.SphereGeometry(0.22 * BH, 24, 16), M.int, [0.16, -0.16, -0.50]); housing.scale.set(1.1, 0.9, 0.75);
    add(new THREE.CylinderGeometry(0.17 * BH, 0.19 * BH, 0.22 * BH, 28), M.intCore, [-0.06, 0.05, -0.46], [Math.PI / 2, 0, 0]);
    add(new THREE.CylinderGeometry(0.16 * BH, 0.16 * BH, 0.09 * BH, 36), M.intMetalDark, [-0.04, 0.06, -0.20], [Math.PI / 2, 0, 0]);
    add(new THREE.CylinderGeometry(0.24 * BH, 0.24 * BH, 0.02 * BH, 40), M.int, [0, 0, -0.07], [Math.PI / 2, 0, 0]);
    for (const x of [-0.22, 0.22]) add(new THREE.BoxGeometry(0.05 * BH, 0.09 * BH, 0.05 * BH), M.intMetal, [x, 0.02, -0.33]);
    add(new THREE.BoxGeometry(0.34 * BH, 0.20 * BH, 0.02 * BH), M.intBoard, [0.02, -0.06, -0.36], [0.2, 0, 0.3]);
    add(new THREE.CylinderGeometry(0.12 * BH, 0.12 * BH, 0.03 * BH, 32), M.intDriver, [-0.03, 0.07, -0.66], [Math.PI / 2, 0, 0]);
    // the ribbed frame that carries the board, round the body behind the disc (dark, fine)
    for (let k = 0; k < 4; k++) {   // the stacked layers of the inner frame and board, faint
      add(new THREE.BoxGeometry(0.46 * BH, 0.006 * BH, 0.20 * BH), M.intLayer, [0.0, -0.15 + k * 0.10, -0.30]);
    }
    add(new THREE.CylinderGeometry(0.06 * BH, 0.06 * BH, 0.05 * BH, 24), M.intPale, [0.12, 0.10, -0.30], [Math.PI / 2, 0, 0]);
    return grp;
  }

  function buildBud(side) {
    const g = new THREE.Group(); g.name = 'C7_Bud' + side;
    const mirror = side === 'L';
    const shellG = shellGeometry();
    const shell = new THREE.Mesh(shellG, M.shell); shell.name = 'C7_BudShell';
    const shellBack = new THREE.Mesh(shellG, M.shellGlassBack); shellBack.visible = false; shellBack.renderOrder = 2;
    const face = new THREE.Mesh(faceGeometry(), M.face); face.name = 'C7_BudFace';
    g.add(shell, shellBack, face);
    // patches are projected onto the unmirrored shell; the group mirrors afterwards
    const target = shell;
    const up = new THREE.Vector3(0, 1, 0);
    // mic grille
    const mic = surfacePoint(target, BUD.mic.at, -1);
    // drawn face-on (as seen along the earbud's axis) and laid onto the shell, so that from the front
    // it reads as the level oval Sony's photos show (mic.faceOn); otherwise in the surface's own plane
    const zF = new THREE.Vector3(0, 0, 1);
    // proj: 0 projects along the earbud's axis (face-on), 1 along the surface normal
    const micN = zF.clone().lerp(mic.n.clone().normalize(), BUD.mic.proj).normalize();
    const grilleGeo = conformal(target, mic.p, micN, up.clone().applyAxisAngle(micN, rad(BUD.mic.rot || 0)), BUD.mic.w * BH, BUD.mic.h * BH, { n: 28, off: 0.0035 });
    const grille = new THREE.Mesh(grilleGeo, M.grille);
    grille.name = 'C7_Grille';
    // air hole on top, and the tactile dot (left only)
    const air = surfacePoint(target, BUD.airTop.at, -1);
    const airN = air.n.clone();
    const airHole = new THREE.Mesh(conformal(target, air.p, airN, new THREE.Vector3(0, 0, 1),
      BUD.airTop.w * BH, BUD.airTop.h * BH, { n: 6, off: 0.002 }), M.hole);
    g.add(grille, airHole);
    if (side === 'L') {
      const dp = surfacePoint(target, BUD.dotL.at, -1);
      const dot = new THREE.Mesh(new THREE.SphereGeometry(BUD.dotL.d * BH / 2, 16, 10), M.shell);
      dot.position.copy(dp.p); dot.scale.set(1, 1, 0.6);   // a low bump (it stood out as a spike at 2.0)
      dot.lookAt(dp.p.clone().add(dp.n)); dot.name = 'C7_TactileDot';
      g.add(dot);
    }
    // SONY wordmark on the rim
    const logo = new THREE.Mesh(logoStrip(target, side), M.print); logo.name = 'C7_Logo';
    logo.material = M.print; logo.userData.rect = ATL.sony;
    g.add(logo);
    // inner side: charging contacts, IR sensor window, L/R mark, vent
    const inr = BUD.inner;
    for (const c of [inr.contactA, inr.contactB]) {
      const sp = surfacePoint(target, c, 1);
      const m = new THREE.Mesh(new THREE.CylinderGeometry(inr.contactD * BH / 2, inr.contactD * BH / 2, 0.012 * BH, 24), M.contact);
      m.position.copy(sp.p); m.quaternion.setFromUnitVectors(new THREE.Vector3(0, 1, 0), sp.n);
      m.name = 'C7_Contact'; g.add(m);
      const ring = new THREE.Mesh(new THREE.CylinderGeometry(inr.contactD * BH * 0.78, inr.contactD * BH * 0.78, 0.008 * BH, 28), M.contactRing);
      ring.position.copy(sp.p); ring.quaternion.copy(m.quaternion); g.add(ring);
    }
    const sens = surfacePoint(target, inr.sensor, 1);
    g.add(Object.assign(new THREE.Mesh(conformal(target, sens.p, sens.n, up, inr.sensorW * BH, inr.sensorH * BH, { n: 16, off: 0.006 }), M.sensor), { name: 'C7_Sensor' }));
    const mk = surfacePoint(target, inr.mark, 1);
    g.add(Object.assign(new THREE.Mesh(conformal(target, mk.p, mk.n, up, inr.markD * BH, inr.markD * BH,
      { n: 8, off: 0.003, rect: side === 'L' ? ATL.markL : ATL.markR, flipU: mirror }), side === 'L' ? M.markL : M.markR), { name: 'C7_Mark' }));
    const vt = surfacePoint(target, inr.vent, 1);
    g.add(Object.assign(new THREE.Mesh(conformal(target, vt.p, vt.n, up, inr.ventD * BH, inr.ventD * BH, { n: 6, off: 0.002 }), M.hole), { name: 'C7_Vent' }));
    // nozzle and ear tip
    const tg = tipGeometries(target);
    g.add(Object.assign(new THREE.Mesh(tg.nozzle, M.nozzle), { name: 'C7_Nozzle' }));
    g.add(Object.assign(new THREE.Mesh(tg.nozzleIn, M.nozzleIn), { name: 'C7_NozzleIn' }));
    const tipMesh = Object.assign(new THREE.Mesh(tg.tip, M.tip), { name: 'C7_Tip' });
    g.add(tipMesh);
    for (const [geo, mat, name] of [[tg.tube, M.tipTube, 'C7_TipTube'], [tg.mouth, M.nozzleMouth, 'C7_NozzleMouth'], [tg.mouthRim, M.bore, 'C7_NozzleRim']]) {
      g.add(Object.assign(new THREE.Mesh(geo, mat), { name, renderOrder: 7 }));
    }
    // Glass Blue internals
    const internals = budInternals(); internals.visible = false;
    g.add(internals);
    // Glass Blue: the pale blue round part between the contacts, just under the inner surface
    const pb = surfacePoint(target, [-0.08, 0.14], 1);
    const paleDisc = new THREE.Mesh(new THREE.CircleGeometry(0.10 * BH, 40), M.intPale);
    paleDisc.position.copy(pb.p).addScaledVector(pb.n, -0.006 * BH); paleDisc.lookAt(pb.p.clone().add(pb.n));
    internals.add(paleDisc);
    // Glass Blue: the mic's round metal mesh, inside the clear shell under the grille, turned
    // towards the outer side
    {
      const nIn = mic.n.clone().normalize();
      const facing = nIn.clone().add(new THREE.Vector3(0.6, 0, 0)).normalize();
      const meshDisc = new THREE.Mesh(new THREE.CircleGeometry(0.115 * BH, 48), M.grilleGlass);
      meshDisc.position.copy(mic.p).addScaledVector(nIn, -0.045 * BH);
      meshDisc.lookAt(meshDisc.position.clone().add(facing));
      meshDisc.name = 'C7_MicMesh';
      internals.add(meshDisc);
    }
    // Glass Blue: a bright clear ring round the touch disc
    const discRim = new THREE.Mesh(new THREE.RingGeometry(BUD.Rd * BH * 0.955, BUD.Rd * BH * 1.004, 96), M.discRim);
    discRim.position.z = 0.002; discRim.rotation.x = Math.atan(BUD.faceTilt); discRim.visible = false;
    g.add(discRim);
    g.userData = { shell, shellBack, face, grille, internals, logo, tips: [tipMesh], mark: g.getObjectByName('C7_Mark'), discRim };
    if (mirror) g.scale.x = -1;
    return g;
  }

  // ---------------------------------------------------------------------------------------------
  // Case geometry
  // Plan outline: a stadium (rounded oblong whose end radius is CASE.endR of the half-depth), walked
  // by a perimeter parameter u in [0, 1) that starts at +x and runs towards +z (the front). The
  // segment proportions come from the full-size outline, so every ring has matching vertices.
  const PLAN = (() => {
    const r0 = CASE.B * CASE.endR, sx = CASE.A - r0, sz = CASE.B - r0;
    const segs = [sz, Math.PI * r0 / 2, 2 * sx, Math.PI * r0 / 2, 2 * sz, Math.PI * r0 / 2, 2 * sx, Math.PI * r0 / 2, sz];
    const tot = segs.reduce((a, b) => a + b, 0), cum = [0];
    for (const l of segs) cum.push(cum[cum.length - 1] + l / tot);
    return { cum };
  })();
  function planPt(A, B, u) {
    const r = Math.max(1e-5, Math.min(B, CASE.B * CASE.endR * Math.min(1, B / CASE.B))), sx = Math.max(0, A - r), sz = Math.max(0, B - r);
    u = ((u % 1) + 1) % 1;
    const c = PLAN.cum;
    let k = 0; while (k < 8 && u >= c[k + 1]) k++;
    const t = (u - c[k]) / Math.max(1e-9, c[k + 1] - c[k]);
    const arc = (cx, cz, a0) => { const a = a0 + t * Math.PI / 2; return [cx + r * Math.cos(a), cz + r * Math.sin(a)]; };
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
  const NCASE = 192;
  // grid of rings: [{ dx, dz, y }] (or { d } for both); ring points = superellipse(A - dx, B - dz)
  function ringsGeometry(rings, opts = {}) {
    const N = NCASE;
    const pos = [], uv = [], idx = [];
    rings.forEach((rg, j) => {
      const A = CASE.A - (rg.dx ?? rg.d), B = CASE.B - (rg.dz ?? rg.d);
      for (let i = 0; i < N; i++) {
        const [x, z] = planPt(Math.max(A, 1e-4), Math.max(B, 1e-4), i / N);
        pos.push(x, rg.y, z);
        uv.push(i / N, j / (rings.length - 1));
      }
    });
    for (let j = 0; j < rings.length - 1; j++) {
      for (let i = 0; i < N; i++) {
        const i1 = (i + 1) % N, a = j * N + i, b = j * N + i1, c = (j + 1) * N + i, d = (j + 1) * N + i1;
        if (opts.flip) idx.push(a, b, c, b, d, c); else idx.push(a, c, b, b, c, d);
      }
    }
    if (opts.capEnd) {   // close the last ring at its centre
      const cIdx = pos.length / 3; const last = rings[rings.length - 1];
      pos.push(0, last.y + (opts.capRise || 0), 0); uv.push(0.5, 1);
      const j = rings.length - 1;
      for (let i = 0; i < N; i++) { const a = j * N + i, b = j * N + (i + 1) % N; if (opts.flip) idx.push(a, b, cIdx); else idx.push(a, cIdx, b); }
    }
    const g = new THREE.BufferGeometry();
    g.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3));
    g.setAttribute('uv', new THREE.Float32BufferAttribute(uv, 2));
    g.setIndex(idx);
    if (opts.dent) opts.dent(g);
    g.computeVertexNormals();
    return g;
  }
  // The case is a soft pebble: the base walls curve in all the way from the parting line to a small
  // flat bottom, and the lid rolls over from a short wall into a gently domed top (elliptical
  // round-overs with separate reach across the width and the depth, measured on the front views).
  function baseOuterRings(t = 0) {
    const C = CASE, b = C.bottom, rings = [];
    const yTop = C.yP - C.gap / 2, v = Math.min(b.v, yTop);
    rings.push({ dx: t, dz: t, y: yTop });
    for (let k = 1; k <= 4; k++) { const y = yTop - (yTop - v) * (k / 4); if (y < yTop - 1e-4) rings.push({ dx: t, dz: t, y }); }
    for (let k = 1; k <= 28; k++) {
      const a = (k / 28) * Math.PI / 2;
      rings.push({ dx: t + b.hx * (1 - Math.cos(a)), dz: t + b.hz * (1 - Math.cos(a)), y: t + (v - t) * (1 - Math.sin(a)) });
    }
    // flat bottom, scaled in towards the centre
    const A0 = C.A - t - b.hx, B0 = C.B - t - b.hz;
    // the bottom is a touch dished: its centre sits a little higher than its rim
    for (let k = 1; k <= 6; k++) { const s = 1 - k / 6 * 0.98; rings.push({ dx: C.A - A0 * s, dz: C.B - B0 * s, y: t + (b.dish || 0) * (1 - s * s) }); }
    return rings;
  }
  function lidOuterRings(t0 = 0) {
    const C = CASE, tp = C.top, rings = [], t = t0 - C.lidGrow;
    const y0 = C.yP + C.gap / 2, yTop = C.H - t0, yS = yTop - tp.v;
    rings.push({ dx: t, dz: t, y: y0 });
    if (yS > y0) for (let k = 1; k <= 3; k++) rings.push({ dx: t, dz: t, y: y0 + (yS - y0) * (k / 3) });
    const vv = yTop - Math.max(yS, y0);
    for (let k = 1; k <= 28; k++) {
      const a = (k / 28) * Math.PI / 2;
      rings.push({ dx: t + tp.hx * (1 - Math.cos(a)), dz: t + tp.hz * (1 - Math.cos(a)), y: yTop - vv * (1 - Math.sin(a)) });
    }
    // gently domed top, scaled in towards the centre
    const A0 = C.A - t - tp.hx, B0 = C.B - t - tp.hz;
    for (let k = 1; k <= 8; k++) {
      const u = k / 8, s = 1 - u * 0.98;
      rings.push({ dx: C.A - A0 * s, dz: C.B - B0 * s, y: yTop + tp.dome * (1 - s * s) });
    }
    return rings;
  }
  // finger slot on the base front: an indentation following a rounded slot outline
  function slotDent(g) {
    const sl = CASE.slot, p = g.getAttribute('position');
    for (let i = 0; i < p.count; i++) {
      const x = p.getX(i), y = p.getY(i), z = p.getZ(i);
      if (z < CASE.B * 0.5) continue;
      const dx = Math.max(0, Math.abs(x) - (sl.w / 2 - sl.h / 2)), dy = y - sl.y;
      const d = Math.hypot(dx, dy) / (sl.h / 2);        // 1 at the slot edge
      if (d >= 1.12) continue;
      const f = d < 0.88 ? 1 : 0.5 + 0.5 * Math.cos(Math.PI * (d - 0.88) / 0.24);
      p.setZ(i, z - sl.depth * f);
    }
  }
  function lidInnerRings() {
    return lidOuterRings(CASE.wall);
  }
  function rimGeometry(y, dOut, dIn) {
    return ringsGeometry([{ d: dOut, k: 1, y }, { d: dIn, k: 1, y }]);
  }
  // deck with the two earbud wells (a Shape with holes), and the wells as tapered tubes
  function deckAndWells(wellCentres) {
    const C = CASE, t = C.wall, y = C.yP - C.deck;
    const shape = new THREE.Shape();
    const N = 96;
    for (let i = 0; i <= N; i++) {
      const [x, z] = planPt(C.A - t, C.B - t, i / N);
      if (i === 0) shape.moveTo(x, z); else shape.lineTo(x, z);
    }
    for (const w of wellCentres) {
      const hole = new THREE.Path();
      for (let i = 0; i <= 48; i++) {
        const a = -(i / 48) * Math.PI * 2;
        const x = w.x + Math.cos(a) * C.tray.r * 1.12, z = w.z + Math.sin(a) * C.tray.r * 0.86;
        if (i === 0) hole.moveTo(x, z); else hole.lineTo(x, z);
      }
      shape.holes.push(hole);
    }
    const deck = new THREE.ShapeGeometry(shape, 1);
    deck.rotateX(Math.PI / 2);   // shape xy -> xz, facing +y
    deck.translate(0, y, 0);
    // flip winding to face up
    const ix = deck.index.array; for (let i = 0; i < ix.length; i += 3) { const a = ix[i]; ix[i] = ix[i + 2]; ix[i + 2] = a; }
    deck.computeVertexNormals();
    const wells = wellCentres.map((w) => {
      const pts = [];
      for (let k = 0; k <= 10; k++) {
        const s = k / 10;
        pts.push(new THREE.Vector2(C.tray.r * (1 - 0.55 * s * s), -C.tray.depth * s));
      }
      pts.push(new THREE.Vector2(0.0001, -C.tray.depth));
      const g = new THREE.LatheGeometry(pts, 48);
      g.scale(1.12, 1, 0.86);
      g.translate(w.x, y, w.z);
      return g;
    });
    return { deck, wells };
  }
  function caseInternals() {
    // Glass Blue: a slim battery low in the base, thin boards, the four magnet holders along the
    // front, the LED light pipe and the hinge pin
    const grp = new THREE.Group(); grp.name = 'C7_CaseInternals';
    const B = CASE.B;
    const batt = new THREE.Mesh(new THREE.BoxGeometry(2.9, 0.34, 1.05), M.intMetal); batt.position.set(0, 0.42, -0.10);
    const board = new THREE.Mesh(new THREE.BoxGeometry(4.2, 0.03, 1.9), M.intBoard); board.position.set(0, 0.70, 0);
    const pcbFront = new THREE.Mesh(new THREE.BoxGeometry(3.4, 0.34, 0.03), M.intBoard); pcbFront.position.set(0, 0.92, B - 0.24);
    grp.add(batt, board, pcbFront);
    for (const x of [-1.6, -1.45, 1.45, 1.6]) {
      const pad = new THREE.Mesh(new THREE.BoxGeometry(0.08, 0.02, 0.16), M.contact); pad.position.set(x, 0.72, 0.2); grp.add(pad);
    }
    for (const x of [-2.15, -0.75, 0.75, 2.15]) {
      const holder = new THREE.Mesh(new THREE.BoxGeometry(0.30, 0.22, 0.08), M.int);
      holder.position.set(x, 1.15, B - 0.26); grp.add(holder);
      const mag = new THREE.Mesh(new THREE.BoxGeometry(0.16, 0.10, 0.05), M.magnet);
      mag.position.set(x, 1.15, B - 0.22); grp.add(mag);
    }
    const ledPipe = new THREE.Mesh(new THREE.BoxGeometry(0.14, 0.34, 0.12), M.intLight); ledPipe.position.set(0, 0.95, B - 0.18);
    const hinge = new THREE.Mesh(new THREE.CylinderGeometry(0.05, 0.05, CASE.hinge.w, 16), M.intMetal);
    hinge.rotation.z = Math.PI / 2; hinge.position.set(0, CASE.yP, -B + 0.14);
    grp.add(ledPipe, hinge);
    return grp;
  }

  function buildCase() {
    const C = CASE;
    const root = new THREE.Group(); root.name = 'C7_Case';
    const spin = new THREE.Group(); root.add(spin);
    const base = new THREE.Group(); base.name = 'C7_CaseBase'; spin.add(base);
    const baseOuterG = ringsGeometry(baseOuterRings(), { capEnd: true, flip: true, dent: slotDent });
    const baseOuter = new THREE.Mesh(baseOuterG, M.caseShell); baseOuter.name = 'C7_CaseBaseShell';
    const baseOuterBack = new THREE.Mesh(baseOuterG, M.caseShellGlassBack); baseOuterBack.visible = false; baseOuterBack.renderOrder = 1;
    const yRim = C.yP - C.gap / 2;
    const rim = new THREE.Mesh(rimGeometry(yRim, 0, C.wall), M.caseBaseRim);
    const innerWall = new THREE.Mesh(ringsGeometry([{ d: C.wall, k: 1, y: yRim }, { d: C.wall, k: 1, y: C.yP - C.deck }]), M.caseInner);
    // earbud wells
    const seat = C.budSeat;
    const wellCentres = [{ x: C.well.x, z: C.well.z }, { x: -C.well.x, z: C.well.z }];
    const dw = deckAndWells(wellCentres);
    const deck = new THREE.Mesh(dw.deck, M.caseInner); deck.name = 'C7_CaseDeck';
    base.add(baseOuter, baseOuterBack, rim, innerWall, deck);
    for (const wg of dw.wells) {
      const w = new THREE.Mesh(wg, M.caseInner); w.material = M.caseInner; base.add(w);
      // two charging pins at the bottom of each well
    }
    for (const w of wellCentres) {
      for (const dx of [-0.12, 0.12]) {
        const pin = new THREE.Mesh(new THREE.CylinderGeometry(0.035, 0.035, 0.05, 12), M.caseContact);
        pin.position.set(w.x + dx, C.yP - C.deck - C.tray.depth * 0.72, w.z); base.add(pin);
      }
    }
    // front LED, back USB-C port, pairing button and hinge gap: patches laid onto the curved shell
    const onShell = (x, y, front, w, h, mat, off = 0.003, name) => {
      const n = new THREE.Vector3(0, 0, front ? 1 : -1);
      const p = new THREE.Vector3(x, y, front ? C.B : -C.B);
      const m = new THREE.Mesh(conformal(baseOuter, p, n, new THREE.Vector3(0, 1, 0), w, h, { n: 10, off, flipU: !front }), mat);
      if (name) m.name = name;
      base.add(m);
      return m;
    };
    const led = onShell(0, C.led.y, true, C.led.w, C.led.h, M.led, 0.003, 'C7_LED');
    onShell(C.usb.x, C.usb.y, false, C.usb.w, C.usb.h, M.port, 0.003, 'C7_USB');
    onShell(C.usb.x, C.usb.y, false, C.usb.w * 0.66, C.usb.h * 0.30, M.portTongue, 0.005, 'C7_USBTongue');
    onShell(C.usb.x, C.usb.y, false, C.usb.w, C.usb.h, M.portShell, 0.004, 'C7_USBShell');
    const button = onShell(C.button.x, C.button.y, false, C.button.d + 0.07, C.button.d + 0.07, M.button, 0.004, 'C7_Button');
    const internals = caseInternals(); internals.visible = false; base.add(internals);

    // lid, hinged at the back on the parting line
    const lid = new THREE.Group(); lid.name = 'C7_Lid';
    const hingeZ = -C.B + 0.14;
    lid.position.set(0, C.yP, hingeZ);
    spin.add(lid);
    const lidBody = new THREE.Group(); lidBody.position.set(0, -C.yP, -hingeZ); lid.add(lidBody);
    const lidOuterG = ringsGeometry(lidOuterRings(), { capEnd: true, capRise: CASE.top.dome * 0.02 });
    const lidOuter = new THREE.Mesh(lidOuterG, M.caseShell); lidOuter.name = 'C7_LidShell';
    const lidOuterBack = new THREE.Mesh(lidOuterG, M.caseShellGlassBack); lidOuterBack.visible = false; lidOuterBack.renderOrder = 1;
    const lidInner = new THREE.Mesh(ringsGeometry(lidInnerRings(), { capEnd: true, flip: true }), M.caseInner);
    const lidRim = new THREE.Mesh(rimGeometry(C.yP + C.gap / 2, C.wall, -C.lidGrow), M.caseRim);
    // magnet boss inside the lid front: a half disc on the inner front wall
    // (a half cylinder whose flat side lies against the wall, rounded edge pointing down to the rim)
    // (a D-shaped tab, about 12% of the case width, standing in from the front wall at the rim)
    const boss = new THREE.Mesh(new THREE.CylinderGeometry(0.37, 0.37, 0.16, 40, 1, false, Math.PI / 2, Math.PI), M.caseInner);
    boss.position.set(0, C.yP + C.gap / 2 + 0.10, C.B - C.wall + 0.02);
    boss.name = 'C7_MagnetTab';
    boss.scale.set(1, 1, 1.5);   // a U-shaped tab, deeper than it is wide
    const bossGlass = new THREE.Group();
    {
      const blk = new THREE.Mesh(new THREE.BoxGeometry(0.46, 0.13, 0.30), M.magnetBlock); bossGlass.add(blk);
      for (const x of [-0.16, -0.055, 0.055, 0.16]) {
        const rib = new THREE.Mesh(new THREE.BoxGeometry(0.035, 0.15, 0.32), M.intGrey); rib.position.x = x; bossGlass.add(rib);
      }
      bossGlass.position.set(0, boss.position.y - 0.02, boss.position.z - 0.18);
      bossGlass.scale.set(0.8, 0.8, 0.8);
    }
    // SONY on the lid top, near the back edge
    const logoW = C.logo.w, logoH = logoW * (ATL.sony[3] - ATL.sony[1]) * 512 / ((ATL.sony[2] - ATL.sony[0]) * 1024);
    const lidLogo = new THREE.Mesh(conformal(lidOuter, new THREE.Vector3(0, C.H, C.logo.z), new THREE.Vector3(0, 1, 0),
      new THREE.Vector3(0, 0, -1), logoW, logoH, { n: 12, off: 0.004, rect: ATL.sony }), M.casePrint);
    lidLogo.name = 'C7_CaseLogo';
    lidLogo.renderOrder = 8;   // over the Glass Blue lid, and readable from inside it
    lidBody.add(lidOuter, lidOuterBack, lidInner, lidRim, boss, lidLogo);
    const lidInternals = new THREE.Group(); lidInternals.visible = false;
    const lidMag = new THREE.Mesh(new THREE.BoxGeometry(0.5, 0.12, 0.10), M.magnet); lidMag.position.set(0, C.yP + 0.28, C.B - 0.22);
    const hingeBlock = new THREE.Mesh(new THREE.BoxGeometry(1.15, 0.34, 0.28), M.intGrey); hingeBlock.position.set(0, C.yP + 0.02, -C.B + 0.22);
    const hingePin = new THREE.Mesh(new THREE.CylinderGeometry(0.045, 0.045, 2.3, 16), M.intMetal);
    hingePin.rotation.z = Math.PI / 2; hingePin.position.set(0, C.yP - 0.02, -C.B + 0.16);
    lidInternals.add(lidMag, hingeBlock, bossGlass, hingePin);
    for (let k = 0; k < 5; k++) {
      const rib = new THREE.Mesh(new THREE.BoxGeometry(0.04, 0.10, 0.22), M.intGrey);
      rib.position.set(-0.16 + k * 0.08, C.yP + 0.12, C.B - C.wall - 0.14); lidInternals.add(rib);
    }
    for (let k = 0; k < 7; k++) {
      const tooth = new THREE.Mesh(new THREE.BoxGeometry(0.07, 0.12, 0.22), M.intMetal);
      tooth.position.set(-0.45 + k * 0.15, C.yP - 0.02, -C.B + 0.24); lidInternals.add(tooth);
    }
    lidBody.add(lidInternals);

    // earbuds in their wells
    const anchors = [];
    const bx = V3(seat.x).normalize(), by = V3(seat.y).normalize(), bz = new THREE.Vector3().crossVectors(bx, by);
    // tilt = [tip back (x), spin round the vertical (y), roll outwards (z)], applied spin, tip, roll
    const seatM = new THREE.Matrix4().makeRotationFromEuler(new THREE.Euler(rad(seat.tilt[0]), rad(seat.tilt[1]), rad(seat.tilt[2]), 'ZXY'))
      .multiply(new THREE.Matrix4().makeBasis(bx, by, bz));
    const seatEuler = new THREE.Euler().setFromRotationMatrix(seatM);
    for (const side of ['R', 'L']) {
      const s = side === 'R' ? 1 : -1;
      const anchor = new THREE.Group(); anchor.name = 'C7_Seat' + side;
      anchor.position.set(seat.pos[0] * s, seat.pos[1], seat.pos[2]);
      anchor.rotation.set(seatEuler.x, seatEuler.y * s, seatEuler.z * s);
      base.add(anchor);
      anchors.push({ node: anchor, y: anchor.position.y, side });
    }
    return { root, spin, base, lid, anchors, parts: { baseOuter, baseOuterBack, lidOuter, lidOuterBack, lidInner, rim, lidRim, innerWall, deck, internals, lidInternals, button, led, boss } };
  }

  // ---------------------------------------------------------------------------------------------
  // Assembly: hero earbuds (like the XM5 hero: right earbud upper left, left earbud lower right)
  const budsRoot = new THREE.Group(); budsRoot.name = 'c710n_buds';
  const HERO_DY = -0.10, CASE_DY = 0.21;   // vertical trims of the hero pair and the case (world units)
  // sized and placed so the pair fills the hero frame like the XM5 pair (alpha box measured in the
  // app's 1000 x 712 hero view)
  const HERO_S = 1.05;
  const pivotR = new THREE.Group(), pivotL = new THREE.Group();
  // the right earbud a little further back and the left a little nearer, so their spins never meet
  pivotR.position.set(-1.39, 0.56, -0.35); pivotL.position.set(1.39, -0.56, 0.5);
  budsRoot.position.y = HERO_DY;
  const budR = buildBud('R'), budL = buildBud('L');
  budR.rotation.set(rad(4), rad(18), rad(-8)); budL.rotation.set(rad(4), rad(-18), rad(8));
  budR.scale.multiplyScalar(HERO_S); budL.scale.multiplyScalar(HERO_S);
  pivotR.add(budR); pivotL.add(budL);
  // Spin each earbud about its own middle (like the XM5), not about its local origin: the ear tip
  // sticks out to one side, so turning about the origin swung the two earbuds through each other.
  // Only x and z move (the spin is about y), and the pose at rest stays exactly where it was.
  for (const [pivot, bud] of [[pivotR, budR], [pivotL, budL]]) {
    pivot.updateMatrixWorld(true);
    const c = new THREE.Box3().setFromObject(bud).getCenter(new THREE.Vector3()).sub(pivot.position);
    c.y = 0;
    bud.position.sub(c); pivot.position.add(c);
  }
  budsRoot.add(pivotR, pivotL);

  const kase = buildCase();
  // the case rests like the XM5 case in the hero frame (yaw -16, tilt 14), scaled to the same size
  const CASE_S = 1.0;   // relative to the XM5 case's rest scale (its 6.48 cm case and this one fill the hero alike)
  kase.root.matrixAutoUpdate = false;
  kase.root.matrix.makeTranslation(0, CASE_DY, 0).multiply(ctx.caseRest).multiply(new THREE.Matrix4().makeScale(CASE_S * 6.48 / (2 * CASE.A), CASE_S * 6.48 / (2 * CASE.A), CASE_S * 6.48 / (2 * CASE.A)));
  // earbuds in the case share geometry and materials with the hero pair
  for (const an of kase.anchors) {
    const b = buildBud(an.side);
    b.userData.inCase = true;
    b.scale.multiplyScalar(CASE.budSeat.scale);
    b.userData.face.material = M.faceCase;
    an.node.add(b);
    an.bud = b;
  }
  const allBuds = [budR, budL, ...kase.anchors.map((a) => a.bud)];
  for (const an of kase.anchors) {
    an.bud.userData.shell.material = M.shellCase;
    // the inner side sits down in the well: its marks and contacts are never seen in the case
    an.bud.traverse((o) => { if (/C7_Mark|C7_Contact|C7_Sensor/.test(o.name)) o.visible = false; });
  }

  // the glass materials on throwaway meshes: hero3d compiles them on their own (never drawn) once the
  // first frame is up, so picking Glass Blue later doesn't stall on building its programs
  const warm = new THREE.Group(); warm.name = 'C7_Warm'; warm.scale.setScalar(1e-6);
  for (const m of [M.shellGlass, M.shellGlassT, M.shellGlassBack, M.grilleGlass, M.caseShellGlass, M.caseShellGlassT, M.caseShellGlassBack,
    M.caseInnerGlass, M.caseInnerGlassT, M.faceSmoke, M.caseRimGlass, M.lidShellGlass, M.lidShellGlassT]) {
    warm.add(new THREE.Mesh(new THREE.PlaneGeometry(1, 1), m));
  }

  // ---------------------------------------------------------------------------------------------
  // Finishes
  let glassOn = null;
  function twin(mesh, mat, on) {   // the transmit pass of a near glass wall: same geometry, drawn just before it
    let t = mesh.userData.twin;
    if (!t && on) { t = new THREE.Mesh(mesh.geometry, mat); t.name = mesh.name + '_T'; mesh.add(t); mesh.userData.twin = t; }
    if (t) { t.visible = on; t.renderOrder = mesh.renderOrder - 0.5; }
  }
  function setGlass(on) {
    if (glassOn === on) return;
    glassOn = on;
    for (const b of allBuds) {
      const u = b.userData;
      u.shell.material = on ? M.shellGlass : (u.inCase ? M.shellCase : M.shell);
      u.shell.renderOrder = on ? 3 : 0;
      u.shellBack.visible = on;
      twin(u.shell, M.shellGlassT, on);
      u.face.material = on ? M.faceSmoke : (u.inCase ? M.faceCase : M.face);
      u.face.renderOrder = 0;
      u.grille.visible = !on;   // Glass Blue: the metal mesh sits inside, under the clear shell
      u.grille.material = M.grille;
      u.grille.renderOrder = on ? 4 : 0;
      u.internals.visible = on;
      u.logo.renderOrder = on ? 5 : 0;
      u.discRim.visible = on;
      u.mark.renderOrder = on ? 5 : 0;
      for (const t of u.tips) t.renderOrder = on ? 4 : 0;
    }
    const p = kase.parts;
    p.baseOuter.material = on ? M.caseShellGlass : M.caseShell; p.baseOuter.renderOrder = on ? 6 : 0;
    p.lidOuter.material = on ? M.lidShellGlass : M.caseShell; p.lidOuter.renderOrder = on ? 6 : 0;
    p.baseOuterBack.visible = on; p.lidOuterBack.visible = on;
    twin(p.baseOuter, M.caseShellGlassT, on); twin(p.lidOuter, M.lidShellGlassT, on);
    for (const k of ['lidInner', 'innerWall', 'deck', 'boss']) {
      p[k].material = on ? (k === 'boss' ? M.caseShellGlass : M.caseInnerGlass) : (k === 'boss' ? M.boss : M.caseInner); p[k].renderOrder = on ? 5 : 0;
      twin(p[k], k === 'boss' ? M.caseShellGlassT : M.caseInnerGlassT, on);
    }
    p.rim.material = on ? M.caseRimGlass : M.caseBaseRim; p.lidRim.material = on ? M.caseRimGlass : M.caseRim;
    for (const k of ['rim', 'lidRim']) p[k].renderOrder = 0;
    p.internals.visible = on; p.lidInternals.visible = on;
  }
  function applyFinish(f) {
    const set = (c, a) => c.setRGB(a[0], a[1], a[2]);
    set(M.shell.color, f.shell); set(M.face.color, f.face); set(M.faceSmoke.color, f.face);
    set(M.shellGlassBack.userData.uniforms.uGlassTint.value, f.shell);
    set(M.shellGlass.userData.uniforms.uGlassTint.value, f.shell.map((v) => v * 0.6));
    set(M.caseShellGlassBack.userData.uniforms.uGlassTint.value, f.caseShell);
    set(M.caseShellGlass.userData.uniforms.uGlassTint.value, f.caseShell.map((v) => v * 0.6));
    set(M.lidShellGlass.userData.uniforms.uGlassTint.value, f.caseShell.map((v) => v * 0.8));
    set(M.caseInnerGlass.userData.uniforms.uGlassTint.value, f.caseInner.map((v) => v * 0.6));
    // the seated earbuds, in the case's shade
    const sh = f.seatShade[0];
    M.shellCase.color.setRGB(f.shell[0] * sh, f.shell[1] * sh, f.shell[2] * sh);
    // seated Glass Blue discs read lighter, towards the clear shell tint
    const fc = f.glass[0] > 0.5 ? f.face.map((v) => v * 0.8) : f.shell.map((v) => v * sh * 0.92);
    M.faceCase.color.setRGB(fc[0], fc[1], fc[2]);
    set(M.hole.color, f.shell.map((v) => v * 0.45));
    set(M.grille.color, f.shell); set(M.grilleGlass.color, f.grille);   // body-coloured (Glass Blue: the metal mesh)
    set(M.tip.color, f.tip); M.tip.opacity = f.tipA[0];
    // light silicone scatters light through itself: the pale tips are lifted like the pale shells
    M.tip.emissive.setRGB(...f.tip.map((v) => v * 2.5 * f.lift[0]));
    M.tip.userData.uniforms.uEdgeA.value = f.tipEdge[0];
    set(M.print.color, f.print);
    M.print.emissive.setRGB(f.print[0] * 0.55 * f.glass[0], f.print[1] * 0.55 * f.glass[0], f.print[2] * 0.55 * f.glass[0]);
    set(M.caseShell.color, f.caseShell); set(M.caseInner.color, f.caseInner); M.caseBaseRim.color.setRGB(...f.caseShell.map((v) => v * (0.5 + 0.5 * f.rimK[0])));
    M.caseRim.color.setRGB(f.caseInner[0] * f.rimK[0], f.caseInner[1] * f.rimK[0], f.caseInner[2] * f.rimK[0]);
    M.boss.color.setRGB(f.caseInner[0] * 0.86, f.caseInner[1] * 0.86, f.caseInner[2] * 0.86);
    for (const m of [M.shell, M.shellCase]) m.userData.uniforms.uSeamK.value = f.seamK[0];
    // the stem: a dark version of the body (navy on Glass Blue); the mouth lighter, seen through the tip
    const lum = Math.min(1, (f.shell[0] + f.shell[1] + f.shell[2]) / 3);
    set(M.nozzle.color, f.glass[0] > 0.5 ? [0.06, 0.10, 0.14] : f.shell.map((v) => v * (0.35 + 0.5 * lum)));
    set(M.nozzleMouth.color, f.shell.map((v) => v * 0.30 + 0.13));
    set(M.tipTube.color, f.tip.map((v) => v * (0.45 + 0.4 * lum)));
    set(M.nozzleIn.color, f.tip.map((v) => v * 0.5));
    set(M.casePrint.color, f.casePrint); M.casePrint.opacity = f.glass[0] > 0.5 ? 0.85 : 1;
    set(M.button.color, f.caseShell);
    set(M.int.color, f.intTint);
    for (const [m, k] of [[M.shell, 'shell'], [M.face, 'face'], [M.caseShell, 'caseShell'], [M.button, 'caseShell'], [M.caseInner, 'caseInner'], [M.shellCase, 'shell'], [M.faceCase, 'shell'], [M.grille, 'shell']]) {
      m.emissive.setRGB(f[k][0] * f.lift[0], f[k][1] * f.lift[1], f[k][2] * f.lift[2]);
    }
    M.caseInner.emissive.setRGB(f.caseInner[0] * f.innerLift[0], f.caseInner[1] * f.innerLift[0], f.caseInner[2] * f.innerLift[0]);
    setGlass(f.glass[0] > 0.5);
  }
  function finishCopy(name, from) {
    const src = from || FINISHES[name] || FINISHES.black;
    return Object.fromEntries(KEYS.map((k) => [k, src[k].slice()]));
  }

  // ---------------------------------------------------------------------------------------------
  // Pose (called by hero3d.js every frame it draws)
  const LID_OPEN = rad(CASE.lidOpenDeg);
  const pair = { R: 0, L: 0 };
  function applyPose(st, active) {
    const a = rad(st.angle);
    pivotR.rotation.y = a; pivotL.rotation.y = -a;            // mirrored spin, like the XM5
    kase.spin.rotation.y = a;
    kase.lid.rotation.x = -LID_OPEN * st.lid;
    // the earbuds stand proud of the rim when the lid is open and settle under it as it closes
    // (the XM5 case does the same, BUD_SEAT in hero3d.js)
    for (const an of kase.anchors) {
      an.node.position.y = an.y - CASE.budSeat.drop * (1 - Math.min(1, st.lid * 1.6)) + (pair[an.side] || 0);
      an.node.visible = true;
    }
    budsRoot.visible = active && st.model === 'buds';
    kase.root.visible = active && st.model === 'case';
  }

  chunkCache = null;
  return {
    buds: { root: budsRoot, pivotL, pivotR, budL, budR },
    kase,
    FINISHES, KEYS, finishCopy, applyFinish, applyPose, LID_OPEN, ready, warm,
    M, BUD, CASE, BH, pair,
  };
}
