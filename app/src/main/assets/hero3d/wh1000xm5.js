// SunPods hero: the Sony WH-1000XM5 over-ear headphones and their carrying case, modelled in code
// (three.js r170) for the same scene, lights and material language as the WF-1000XM5 in hero3d.js.
//
// Everything is built once when the module loads, from analytic surfaces (no raycasts, no meshes from
// files): each ear cup is a "pillow" (an oval outline whose wall rolls over into a flat face) with a
// lathed leather pad, the headband is a rounded section swept along an elliptical arc, the sliders and
// hangers are extruded bars, and small parts (buttons, mic slots, ports, indicator) are patches laid on
// the cup surface by its own parameterisation. The SONY wordmark comes from c710n_marks.png.
// Units are centimetres-ish: the cup is 10 tall. Shapes and proportions were measured from Sony's
// product photos (branding/render/wh1000xm5, references not committed) and refined against them.
//
// createWH1000XM5(ctx) returns { buds, kase, FINISHES, KEYS, finishCopy, applyFinish, applyPose, ready,
// warm } for hero3d.js ('buds' is the headphones); ctx carries hero3d's THREE, patched() and lin().

// ---------------------------------------------------------------------------------------------
// Finishes. Linear RGB. Black, Platinum Silver, Midnight Blue and Smoky Pink, matched to Sony's
// product shots (one shoot for black, silver and pink; Midnight Blue from Sony's colour name and the
// navy of its retail listings). Every key is a triple so finishes crossfade.
const FINISHES = {
  black: {
    body: [0.026, 0.026, 0.028], face: [0.046, 0.046, 0.048], pad: [0.026, 0.026, 0.028], slider: [0.030, 0.030, 0.032], hinge: [0.016, 0.016, 0.017],
    baffle: [0.016, 0.016, 0.017], print: [0.42, 0.42, 0.42], mic: [0.22, 0.15, 0.07], seam: [0.010, 0.010, 0.010],
    caseShell: [0.030, 0.030, 0.032], caseLining: [0.026, 0.026, 0.028], zip: [0.010, 0.010, 0.011], pull: [0.62, 0.46, 0.22],
    lift: [0, 0, 0],
  },
  silver: {   // Platinum Silver
    body: [0.640, 0.615, 0.540], face: [0.700, 0.675, 0.600], pad: [0.600, 0.575, 0.505], slider: [0.600, 0.580, 0.510], hinge: [0.30, 0.29, 0.26],
    baffle: [0.25, 0.24, 0.22], print: [0.52, 0.38, 0.18], mic: [0.40, 0.32, 0.20], seam: [0.20, 0.19, 0.17],
    caseShell: [0.520, 0.500, 0.440], caseLining: [0.420, 0.405, 0.360], zip: [0.30, 0.29, 0.26], pull: [0.62, 0.48, 0.25],
    lift: [0.03, 0.03, 0.03],
  },
  blue: {     // Midnight Blue
    body: [0.026, 0.034, 0.060], face: [0.042, 0.052, 0.086], pad: [0.024, 0.031, 0.055], slider: [0.022, 0.029, 0.052], hinge: [0.012, 0.014, 0.022],
    baffle: [0.014, 0.017, 0.026], print: [0.50, 0.40, 0.30], mic: [0.20, 0.17, 0.14], seam: [0.008, 0.009, 0.014],
    caseShell: [0.024, 0.030, 0.050], caseLining: [0.020, 0.025, 0.040], zip: [0.008, 0.009, 0.014], pull: [0.55, 0.45, 0.32],
    lift: [0, 0, 0],
  },
  pink: {     // Smoky Pink
    body: [0.530, 0.440, 0.435], face: [0.585, 0.490, 0.484], pad: [0.495, 0.410, 0.405], slider: [0.500, 0.415, 0.410], hinge: [0.25, 0.20, 0.20],
    baffle: [0.20, 0.16, 0.16], print: [0.62, 0.40, 0.30], mic: [0.36, 0.26, 0.22], seam: [0.16, 0.13, 0.13],
    caseShell: [0.430, 0.360, 0.355], caseLining: [0.350, 0.290, 0.285], zip: [0.25, 0.20, 0.20], pull: [0.66, 0.46, 0.34],
    lift: [0.02, 0.02, 0.02],
  },
};
const KEYS = Object.keys(FINISHES.black);

// ---------------------------------------------------------------------------------------------
// Headphones, in cm. Frame: +y up, +z towards the wearer's front, +x towards the wearer's left.
// The right unit (slider, hanger, cup) is built at x < 0; the left unit is its mirror image.
const HP0 = {
  // ear cup, in its own frame: origin at the centre of the housing/pad seam, +z out of the outer face,
  // +y up, +x towards the front. Outline radius every 11.25 deg from +x, counter-clockwise (measured on
  // Sony's side view: an oval 8.5 x 10 whose long axis leans back at the top)
  cup: {
    r: [4.20, 4.10, 4.13, 4.23, 4.47, 4.69, 4.88, 5.00, 5.02, 5.05, 5.12, 5.09, 4.95, 4.81, 4.61, 4.37,
      4.18, 4.07, 4.10, 4.23, 4.42, 4.66, 4.81, 4.98, 5.07, 5.12, 5.11, 5.05, 4.90, 4.76, 4.59, 4.38],
    scale: 1.0,
    H: 2.55,            // housing depth, seam to the flat face
    wall: 0.30,         // straight wall above the seam before the round-over
    n: 2.3,             // round-over superellipse exponent (2 = elliptical, larger = squarer)
    faceK: 0.70,        // flat face size, as a fraction of the outline
    faceC: [0.05, -0.28], // flat face centre (x front, y up)
    dome: 0.06,
  },
  pad: { inset: 0.10, W: 1.95, T: 2.30, rc: 0.85, ri: 0.55, bulge: 0.06 },
  // headband: outer outline an ellipse (half-width a, height b above its equator), section t x w
  band: { a: 7.80, b: 6.80, t: 1.40, w: 1.75, ext0: 0.45, es: 3.2, cushion: 62 },
  slider: { w: 0.62, t: 0.42, len: 2.6, ext: 1.10 },
  hanger: { w: 1.00, t: 0.62, len: 2.70, rr: 0.28 },
  knuckle: { r: 0.36, len: 0.95 },
  pivot: [0.0, 4.30, 1.85],    // where the hanger's knuckle sits, in the cup frame
  roll: 20,                    // cup bottoms swing in (deg) at rest
  yaw: 36,                     // cups turned about the hanger, outer faces towards the front (deg) at rest
  logo: { y: 1.50, len: 1.10, h: 0.30, flip: false },   // SONY on the hanger: centre above the pivot, length, letter height
  // parts on the cup, as (angle around the outline in deg, position up the wall t 0..1 / face 1..2, w, h, kind)
  micL: [[330, 0.42, 0.42, 0.13], [57, 0.55, 0.34, 0.11], [160, 0.38, 0.34, 0.11], [245, 0.30, 0.34, 0.11]],
  micR: [[330, 0.42, 0.42, 0.13], [57, 0.55, 0.34, 0.11], [160, 0.38, 0.34, 0.11], [205, 0.32, 0.34, 0.11]],
  buttons: { led: [193, 0.36, 0.13], power: [205, 0.36, 0.72, 0.30], nc: [222, 0.36, 0.72, 0.30], jack: [252, 0.30, 0.42] },
  usb: [278, 0.30, 0.86, 0.30],
  notch: [90, 0.50, 1.30, 1.70],
};

// Carrying case, in cm. Frame (lying on its back): +y up (the lid on top), +z towards the narrow end
// with the loop, -z the wide end where the lid hinges; +x across.
const CASE0 = {
  W: 21.2, L: 23.8,          // outer width and length (plan)
  // plan outline: half-width (fraction of W/2) at fractions of the length from the wide end, measured
  // on Sony's photo of the open case (the back shell's outline)
  hw: [[0.0, 0.0], [0.006, 0.36], [0.025, 0.62], [0.07, 0.80], [0.12, 0.89], [0.17, 0.946], [0.215, 0.98], [0.26, 1.0], [0.31, 1.0],
    [0.405, 0.99], [0.50, 0.967], [0.595, 0.93], [0.69, 0.88], [0.786, 0.81], [0.88, 0.677], [0.928, 0.57], [0.965, 0.44], [0.988, 0.27], [1.0, 0.0]],
  baseH: 4.0, lidH: 2.3, wall: 0.28,
  n: 2.2, faceK: 0.70, dome: 0.10,
  lidOpenDeg: 112,
  loop: { len: 4.6, w: 1.7, slotL: 2.4, slotW: 0.40, t: 0.22 },
  phones: { pos: [0, 0.04, -0.4], scale: 1.0, ext: 0.35, squeeze: 14 },   // folded headphones on the base's floor: offset of their middle
};

// The hero: the headphones turned and tilted so they fill the frame like the WF-1000XM5 pair
const HERO0 = { rot: [-55, 10, -92], scale: 0.236, pos: [0.05, 0.10, 0], ext: 1.10 };
const CASEREST0 = { yaw: -22, tilt: 46, scale: 0.168, pos: [0.0, -0.02, 0] };

function merge(a, b) {
  if (!b) return a;
  const o = Array.isArray(a) ? a.slice() : { ...a };
  for (const [k, v] of Object.entries(b)) o[k] = v && typeof v === 'object' && !Array.isArray(v) && a[k] ? merge(a[k], v) : v;
  return o;
}
const cr = (p0, p1, p2, p3, t) => 0.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t * t + (-p0 + 3 * p1 - 3 * p2 + p3) * t * t * t);
function periodic(samples) {   // periodic Catmull-Rom over evenly spaced samples, argument in degrees
  const n = samples.length;
  const R = (k) => samples[((k % n) + n) % n];
  return (deg) => {
    const x = (((deg % 360) + 360) % 360) / (360 / n), i = Math.floor(x), t = x - i;
    return cr(R(i - 1), R(i), R(i + 1), R(i + 2), t);
  };
}

// ---------------------------------------------------------------------------------------------
export function createWH1000XM5(ctx) {
  const { THREE, patched, lin } = ctx;
  const ov = ctx.DEBUG && ctx.overrides ? ctx.overrides : null;   // tools only: trial values
  const HP = ov ? merge(HP0, ov.HP) : HP0;
  const CASE = ov ? merge(CASE0, ov.CASE) : CASE0;
  const HERO = ov ? merge(HERO0, ov.HERO) : HERO0;
  const CASEREST = ov ? merge(CASEREST0, ov.CASEREST) : CASEREST0;
  const rad = (d) => d * Math.PI / 180;
  const V3 = (x, y, z) => new THREE.Vector3(x, y, z);

  // ---------------------------------------------------------------------------------------------
  // Print atlas (the WF-C710N's: SONY wordmark traced from the approved XM5 case logo)
  let atlasReady;
  const ready = new Promise((r) => { atlasReady = r; });
  const atlas = new THREE.TextureLoader().load('./c710n_marks.png', () => atlasReady(), undefined, () => atlasReady());
  atlas.colorSpace = THREE.NoColorSpace;
  atlas.anisotropy = 4;
  const ATL_SONY = [0.04, 0.03, 0.96, 0.47];

  // ---------------------------------------------------------------------------------------------
  // Materials. Few programs: every opaque surface shares one (the plain patched program), the small
  // parts share one shape program, the print has one, the case fabric one.
  const M = {};
  const P = (o) => new THREE.MeshPhysicalMaterial(o);
  // Sony's soft matte finish: the XM5 case's plastic shader (specular 0.38, sheen 0.2, a whisper of
  // clearcoat) with this model's roughness; leather and fabric use the same features (one program)
  const matte = (rough, sheen = 0.1) => P({ roughness: rough, metalness: 0, specularIntensity: 0.76, sheen: 1, sheenRoughness: 0.8,
    sheenColor: new THREE.Color(sheen, sheen, sheen), clearcoat: 0.03, clearcoatRoughness: 0.45 });
  M.body = matte(0.62);
  M.face = matte(0.72, 0.18);               // the flat outer face (touch panel on the right) is a finer finish
  M.band = matte(0.60);
  M.slider = matte(0.45);
  M.hanger = matte(0.55);
  M.hinge = matte(0.50);
  M.pad = matte(0.52, 0.16);          // protein-leather pads and headband cushion: a little more sheen
  M.cushion = matte(0.55, 0.16);
  M.baffle = matte(0.90, 0.05);       // the fabric inside the pad
  M.seam = matte(0.70, 0.0);          // the dark joint between housing and pad
  M.red = matte(0.40, 0.0); M.red.color.copy(lin([0.55, 0.035, 0.03]));
  M.print = P({ roughness: 0.35, metalness: 0.6, transparent: true, depthWrite: false, alphaMap: atlas,
    polygonOffset: true, polygonOffsetFactor: -2, polygonOffsetUnits: -2 });

  // small parts drawn on patches: the outline is cut in the shader. kind 0 pill hole (mic slot, port),
  // 1 disc hole (jack), 2 button (pill in the body colour inside a fine groove; tactile dot if uDot),
  // 3 indicator (dark glossy disc)
  function shapeMat(kind, w, h, base = {}) {
    const m = P({ roughness: 0.5, metalness: 0, specularIntensity: 0.76, sheen: 1, sheenRoughness: 0.8, sheenColor: new THREE.Color(0.1, 0.1, 0.1),
      clearcoat: 0.03, clearcoatRoughness: 0.45, ...base });
    const u = { uSz: { value: new THREE.Vector2(w, h) }, uKind: { value: kind }, uDot: { value: 0 }, uInk: { value: new THREE.Color(0.01, 0.01, 0.01) } };
    m.onBeforeCompile = null;
    patched(m, 'xm5h-shape', u, {
      pars: 'varying vec2 vXmUv;\nuniform vec2 uSz;\nuniform float uKind, uDot;\nuniform vec3 uInk;\nfloat shK;\n',
      after: {
        color_fragment: /* glsl */`
{
  vec2 p = (vXmUv - 0.5) * uSz;
  float d;
  if (uKind < 0.5 || (uKind > 1.5 && uKind < 2.5)) d = length(max(abs(p) - vec2(0.5 * (uSz.x - uSz.y), 0.0), 0.0)) - 0.5 * uSz.y;
  else d = length(p) - 0.5 * uSz.x;
  if (d > 0.0) discard;
  float w = max(fwidth(d), 1e-4);
  if (uKind < 1.5) {                                   // a hole: dark, a lit lip on its lower edge
    float lip = 1.0 - smoothstep(0.0, 0.25 * uSz.y, -d);
    diffuseColor.rgb = mix(uInk, diffuseColor.rgb * 0.55, lip * smoothstep(-0.2, 0.6, -p.y / uSz.y));
    shK = 1.0;
  } else if (uKind < 2.5) {                            // button: groove ring, tactile dot
    float g = 1.0 - smoothstep(0.010 - w, 0.022 + w, abs(d + 0.022));
    float dot = uDot * (1.0 - smoothstep(0.045 - w, 0.06 + w, length(p - vec2(0.0, 0.0))));
    diffuseColor.rgb *= (1.0 - 0.85 * g) * (1.0 + 0.6 * dot);
    shK = 0.0;
  } else {                                             // indicator window
    diffuseColor.rgb = uInk;
    shK = 0.5;
  }
}`,
        roughnessmap_fragment: 'roughnessFactor = shK > 0.9 ? 0.85 : (shK > 0.4 ? 0.12 : roughnessFactor);',
      },
    });
    const prev = m.onBeforeCompile;
    m.onBeforeCompile = (sh, r) => {
      prev(sh, r);
      sh.vertexShader = sh.vertexShader
        .replace('#include <common>', '#include <common>\nvarying vec2 vXmUv;')
        .replace('#include <begin_vertex>', '#include <begin_vertex>\nvXmUv = uv;');
    };
    return m;
  }

  // case fabric: a fine woven grain in object space, fading out before it could shimmer
  function fabric(rough) {
    const m = matte(rough, 0.18);
    return patched(m, 'xm5h-fabric', { uWeave: { value: 1 / 0.16 } }, {
      pars: 'uniform float uWeave;\n',
      after: {
        color_fragment: /* glsl */`
{
  vec3 q = vObjPos * uWeave;
  float fw = max(max(fwidth(q.x), fwidth(q.y)), fwidth(q.z));
  float k = (1.0 - smoothstep(0.25, 0.6, fw));
  float a = sin(q.x * 6.2832 + 1.7 * sin(q.y * 3.1)) * sin(q.z * 6.2832 + 1.3 * sin(q.y * 2.7));
  float b = sin((q.x + q.z) * 9.42 + q.y * 4.0);
  diffuseColor.rgb *= 1.0 + k * (0.05 * a + 0.025 * b);
}`,
      },
    });
  }
  M.caseShell = fabric(0.86);
  M.caseLining = fabric(0.92);
  M.zip = matte(0.55, 0.05);
  M.pull = P({ roughness: 0.3, metalness: 0.9 });
  M.loop = fabric(0.8);
  for (const [k, m] of Object.entries(M)) {
    m.name = 'XM5H_' + k;
    if (!m.userData.uniforms) patched(m, 'sunpods-plain');   // shares the XM5's plain program (rim light)
  }

  // ---------------------------------------------------------------------------------------------
  // Geometry helpers
  function gridGeometry(rows, cols, fn, { wrap = true, pole = null, flip = false, wind = 'abc' } = {}) {
    // rows x cols grid of points fn(j, i) -> [x, y, z]; wrap closes the columns; pole closes the last row
    const pos = [], uv = [], idx = [];
    for (let j = 0; j < rows; j++) for (let i = 0; i < cols; i++) { const p = fn(j, i); pos.push(p[0], p[1], p[2]); uv.push(i / (wrap ? cols : cols - 1), j / (rows - 1)); }
    const nI = wrap ? cols : cols - 1;
    const tri = (a, b, c) => { if ((wind === 'abc') !== flip) idx.push(a, b, c); else idx.push(a, c, b); };
    for (let j = 0; j < rows - 1; j++) for (let i = 0; i < nI; i++) {
      const i1 = (i + 1) % cols, a = j * cols + i, b = j * cols + i1, c = (j + 1) * cols + i, d = (j + 1) * cols + i1;
      tri(a, b, c); tri(b, d, c);
    }
    if (pole) {
      const pi = pos.length / 3; pos.push(pole[0], pole[1], pole[2]); uv.push(0.5, 1);
      const j = rows - 1;
      for (let i = 0; i < nI; i++) tri(j * cols + i, j * cols + (i + 1) % cols, pi);
    }
    const g = new THREE.BufferGeometry();
    g.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3));
    g.setAttribute('uv', new THREE.Float32BufferAttribute(uv, 2));
    g.setIndex(idx);
    g.computeVertexNormals();
    return g;
  }

  // A "pillow": an outline (radius by angle) whose wall rises straight, then rolls over (a superellipse
  // quarter) into a flat, faintly domed face. t in [0, 1] runs from the rim up the wall to the face's
  // edge, t in [1, 2] across the face to its centre.
  function pillow(o) {
    const e = 2 / o.n, tw = o.tw ?? 0.12;
    const pw = (v, p) => Math.pow(Math.max(0, v), p);
    function point(phi, t) {
      const a = rad(phi), c = Math.cos(a), s = Math.sin(a);
      const r = o.outline(phi), rf = o.faceK * r;
      const Fx = o.faceC[0] + rf * c, Fy = o.faceC[1] + rf * s;
      if (t >= 1) { const u = Math.max(0, 2 - t); return [o.faceC[0] + rf * u * c, o.faceC[1] + rf * u * s, o.H + o.dome * (1 - u * u)]; }
      if (t <= tw) { const k = t / tw; return [r * c, r * s, o.wall * k]; }
      const a2 = (t - tw) / (1 - tw) * Math.PI / 2;
      const kx = pw(Math.cos(a2), e), kz = pw(Math.sin(a2), e);
      return [Fx + (r * c - Fx) * kx, Fy + (r * s - Fy) * kx, o.wall + (o.H - o.wall) * kz];
    }
    const NU = o.NU || 144;
    const rowsT = [0, tw * 0.5, tw];
    const NC = o.NC || 22;
    for (let k = 1; k <= NC; k++) rowsT.push(tw + (1 - tw) * k / NC);
    const NF = o.NF || 8;
    for (let k = 1; k < NF; k++) rowsT.push(1 + k / NF);
    function geometry(flip = false, part = 'all') {
      const rows = part === 'wall' ? rowsT.filter((t) => t <= 1) : part === 'face' ? [1, ...rowsT.filter((t) => t > 1)] : rowsT;
      return gridGeometry(rows.length, NU, (j, i) => point(360 * i / NU, rows[j]), { pole: part === 'wall' ? null : [o.faceC[0], o.faceC[1], o.H + o.dome], flip });
    }
    // a small patch laid on the surface round (phi0, t0): w along the rim direction, h along t
    function patch(phi0, t0, w, h, off = 0.012, N = 8) {
      const p0 = point(phi0, t0);
      const dp = 0.5, dt = 0.01;
      const pa = point(phi0 + dp, t0), pb = point(phi0, Math.min(2, t0 + dt));
      const sPhi = Math.hypot(pa[0] - p0[0], pa[1] - p0[1], pa[2] - p0[2]) / dp;   // cm per degree
      const sT = Math.hypot(pb[0] - p0[0], pb[1] - p0[1], pb[2] - p0[2]) / dt;     // cm per unit t
      const pos = [], uv = [], idx = [];
      const nrm = (phi, t) => {
        const q = point(phi, t), qa = point(phi + 0.3, t), qb = point(phi, t + 0.004);
        const A = V3(qa[0] - q[0], qa[1] - q[1], qa[2] - q[2]), B = V3(qb[0] - q[0], qb[1] - q[1], qb[2] - q[2]);
        return [q, A.cross(B).normalize()];
      };
      for (let j = 0; j <= N; j++) for (let i = 0; i <= N; i++) {
        const u = (i / N - 0.5) * w, v = (j / N - 0.5) * h;
        const [q, n] = nrm(phi0 + u / sPhi, t0 + v / sT);
        pos.push(q[0] + n.x * off, q[1] + n.y * off, q[2] + n.z * off); uv.push(i / N, j / N);
      }
      for (let j = 0; j < N; j++) for (let i = 0; i < N; i++) {
        const a = j * (N + 1) + i, b = a + 1, c = a + N + 1, d = c + 1;
        idx.push(a, b, c, b, d, c);
      }
      const g = new THREE.BufferGeometry();
      g.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3));
      g.setAttribute('uv', new THREE.Float32BufferAttribute(uv, 2));
      g.setIndex(idx); g.computeVertexNormals();
      return g;
    }
    return { point, geometry, patch, o };
  }

  // rounded-rectangle bar, section t (x) by w (z), from y = 0 down to y = -len
  function bar(w, t, len, rr, y0 = 0) {
    const s = new THREE.Shape(), hx = t / 2, hz = w / 2, r = Math.min(rr, hx * 0.95, hz * 0.95);
    s.moveTo(-hx + r, -hz); s.lineTo(hx - r, -hz); s.absarc(hx - r, -hz + r, r, -Math.PI / 2, 0);
    s.lineTo(hx, hz - r); s.absarc(hx - r, hz - r, r, 0, Math.PI / 2);
    s.lineTo(-hx + r, hz); s.absarc(-hx + r, hz - r, r, Math.PI / 2, Math.PI);
    s.lineTo(-hx, -hz + r); s.absarc(-hx + r, -hz + r, r, Math.PI, 1.5 * Math.PI);
    const g = new THREE.ExtrudeGeometry(s, { depth: len, bevelEnabled: false, curveSegments: 5 });
    g.rotateX(Math.PI / 2);   // extrusion +z -> -y; the shape's y -> z
    g.translate(0, y0, 0);
    return g;
  }

  // ---------------------------------------------------------------------------------------------
  // Headphone geometry (built once, shared by the hero and the headphones in the case)
  const cupR0 = periodic(HP.cup.r);
  const cupR = (phi) => cupR0(phi) * HP.cup.scale;
  const housing = pillow({ outline: cupR, faceC: HP.cup.faceC, faceK: HP.cup.faceK, H: HP.cup.H, wall: HP.cup.wall, n: HP.cup.n, dome: HP.cup.dome, NU: 144 });

  function padGeometry() {
    const pd = HP.pad, W = pd.W, T = pd.T, rc = pd.rc, ri = pd.ri;
    const prof = [[0.30, 0.0], [0.08, -0.05], [0.0, -0.16]];
    for (let k = 1; k <= 6; k++) { const z = -0.16 - (T - 0.16 - rc) * k / 6; prof.push([-pd.bulge * Math.sin(Math.PI * k / 6), z]); }
    for (let k = 1; k <= 10; k++) { const a = (k / 10) * Math.PI / 2; prof.push([rc - rc * Math.cos(a), -T + rc - rc * Math.sin(a)]); }
    for (let k = 1; k <= 6; k++) { const d = rc + (W - ri - rc) * k / 6; prof.push([d, -T - 0.07 * Math.sin(Math.PI * k / 6)]); }
    for (let k = 1; k <= 8; k++) { const a = (k / 8) * Math.PI / 2; prof.push([W - ri + ri * Math.sin(a), -T + ri - ri * Math.cos(a)]); }
    for (let k = 1; k <= 4; k++) prof.push([W + 0.04 * k / 4, -T + ri + (T - ri - 0.40) * k / 4]);
    prof.push([W + 0.10, -0.30]);
    const NU = 144;
    return gridGeometry(prof.length, NU, (j, i) => {
      const phi = 360 * i / NU, a = rad(phi), r = cupR(phi) - pd.inset - prof[j][0];
      return [r * Math.cos(a), r * Math.sin(a), prof[j][1]];
    }, { wind: 'acb' });
  }
  function baffleGeometry() {
    const NU = 96, pos = [0, 0, -0.30], idx = [];
    for (let i = 0; i < NU; i++) {
      const phi = 360 * i / NU, a = rad(phi), r = cupR(phi) - HP.pad.inset - HP.pad.W - 0.06;
      pos.push(r * Math.cos(a), r * Math.sin(a), -0.30);
    }
    for (let i = 0; i < NU; i++) idx.push(0, 1 + (i + 1) % NU, 1 + i);
    const g = new THREE.BufferGeometry();
    g.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3));
    g.setAttribute('uv', new THREE.Float32BufferAttribute(new Array(pos.length / 3 * 2).fill(0.5), 2));
    g.setIndex(idx); g.computeVertexNormals();
    return g;
  }
  function seamGeometry() {   // the dark ring between housing and pad
    const NU = 144;
    const rows = [[-0.02, 0.0], [0.02, -0.08], [0.32, -0.08]];
    return gridGeometry(rows.length, NU, (j, i) => {
      const phi = 360 * i / NU, a = rad(phi), r = cupR(phi) - rows[j][0];
      return [r * Math.cos(a), r * Math.sin(a), rows[j][1]];
    }, { wind: 'acb' });
  }

  // headband: a rounded section swept along the elliptical arc, split into the outer shell and the
  // cushion underneath (the same vertices along the split, so no gap)
  const BAND = HP.band;
  const bandA = BAND.a - BAND.t / 2, bandB = BAND.b - BAND.t / 2, bandYc = -BAND.b;
  const bandEnd = V3(-bandA, bandYc - BAND.ext0, 0);   // left end of the centreline
  function bandGeometries() {
    const pts = [];
    const nExt = 8, nArc = 140;
    for (let k = 0; k < nExt; k++) pts.push(V3(-bandA, bandYc - BAND.ext0 * (1 - k / nExt), 0));
    for (let k = 0; k <= nArc; k++) { const ps = Math.PI * k / nArc; pts.push(V3(-bandA * Math.cos(ps), bandYc + bandB * Math.sin(ps), 0)); }
    for (let k = 1; k <= nExt; k++) pts.push(V3(bandA, bandYc - BAND.ext0 * k / nExt, 0));
    const n = pts.length;
    const len = [0];
    for (let k = 1; k < n; k++) len.push(len[k - 1] + pts[k].distanceTo(pts[k - 1]));
    const L = len[n - 1], CAP = 0.30;
    const tan = pts.map((p, k) => pts[Math.min(n - 1, k + 1)].clone().sub(pts[Math.max(0, k - 1)]).normalize());
    const Z = V3(0, 0, 1);
    const nor = tan.map((t) => new THREE.Vector3().crossVectors(Z, t).normalize());
    const sp = (v, p) => Math.sign(v) * Math.pow(Math.abs(v), p);
    const ep = 2 / BAND.es;
    // section scale at the rounded ends
    const capS = (s) => { const d = Math.min(s, L - s); return d >= CAP ? 1 : Math.max(0.02, Math.sqrt(1 - Math.pow(1 - d / CAP, 2))); };
    // the ends: pull the last rows in along the tangent so the cap rounds over
    function sweep(a0, a1, NS) {
      return gridGeometry(n, NS + 1, (j, i) => {
        const al = a0 + (a1 - a0) * i / NS;
        const k = capS(len[j]);
        const nn = (BAND.t / 2) * sp(Math.cos(al), ep) * k, zz = (BAND.w / 2) * sp(Math.sin(al), ep) * k;
        const p = pts[j];
        return [p.x + nor[j].x * nn, p.y + nor[j].y * nn, zz];
      }, { wrap: false });
    }
    const b = rad(BAND.cushion);
    return { shell: sweep(-(Math.PI - b), Math.PI - b, 56), cushion: sweep(Math.PI - b, Math.PI + b, 20) };
  }

  const HG = HP.hanger, SL = HP.slider, KN = HP.knuckle;
  const G = {
    housing: housing.geometry(false, 'wall'),
    face: housing.geometry(false, 'face'),
    pad: padGeometry(),
    baffle: baffleGeometry(),
    seam: seamGeometry(),
    ...(() => { const b = bandGeometries(); return { bandShell: b.shell, bandCushion: b.cushion }; })(),
    slider: bar(SL.w, SL.t, SL.len, 0.2, SL.len),           // from y = +len (inside the band end) down to 0
    hanger: bar(HG.w, HG.t, HG.len + 0.15, HG.rr, 0.15),
    knuckle: (() => { const g = new THREE.CylinderGeometry(HG.w / 2, HG.w / 2, HG.t, 32); g.rotateZ(Math.PI / 2); return g; })(),
  };
  // SONY on the hanger's outer face (x = -t/2), reading up, letter tops towards the front on the left
  // unit and towards the back on the right one (as Sony prints it; the left unit is the right one
  // mirrored, so its uv flips)
  function logoGeometry(side) {
    const lg = HP.logo, g = new THREE.PlaneGeometry(lg.h, lg.len, 1, 1);
    g.rotateY(-Math.PI / 2);                                  // faces -x; its width along z, length along y
    g.translate(-HG.t / 2 - 0.004, -HG.len + lg.y, 0);
    const p = g.getAttribute('position'), uv = g.getAttribute('uv');
    const R = ATL_SONY;
    for (let i = 0; i < p.count; i++) {
      const along = (p.getY(i) - (-HG.len + lg.y - lg.len / 2)) / lg.len;      // 0 at the bottom
      let down = (p.getZ(i) + lg.h / 2) / lg.h;                                 // 0 at the back (-z)
      if ((side === 'L') !== !!lg.flip) down = 1 - down;
      uv.setXY(i, R[0] + (R[2] - R[0]) * along, 1 - (R[1] + (R[3] - R[1]) * down));
    }
    return g;
  }
  G.logoL = logoGeometry('L'); G.logoR = logoGeometry('R');
  G.redDot = (() => { const g = new THREE.CircleGeometry(0.11, 24); g.rotateY(Math.PI / 2); g.translate(HG.t / 2 + 0.004, -HG.len + 1.25, 0.0); return g; })();

  // small parts on the cups: (phi, t, w, h) on the housing surface
  const SH = {};
  const shapeOf = (key, kind, w, h, base) => SH[key] || (SH[key] = shapeMat(kind, w, h, base));
  function cupParts(side) {
    const out = [];
    const add = (geo, mat, name) => { const m = new THREE.Mesh(geo, mat); m.name = name; out.push(m); return m; };
    // the notch in the top of the housing that the hanger's foot sits in
    add(housing.patch(HP.notch[0], HP.notch[1], HP.notch[2], HP.notch[3], 0.008, 10), shapeOf('notch', 0, HP.notch[2], HP.notch[3]), 'XM5H_Notch');
    for (const [phi, t, w, h] of side === 'L' ? HP.micL : HP.micR) add(housing.patch(phi, t, w, h, 0.010, 6), shapeOf('mic' + w + h, 0, w, h), 'XM5H_Mic');
    if (side === 'L') {
      const B = HP.buttons;
      const led = add(housing.patch(B.led[0], B.led[1], B.led[2], B.led[2], 0.012, 6), shapeOf('led', 3, B.led[2], B.led[2], { roughness: 0.1, emissive: new THREE.Color(0, 0, 0) }), 'XM5H_LED');
      const power = add(housing.patch(B.power[0], B.power[1], B.power[2], B.power[3], 0.014, 8), shapeOf('power', 2, B.power[2], B.power[3]), 'XM5H_Power');
      power.material.userData.uniforms.uDot.value = 1;
      add(housing.patch(B.nc[0], B.nc[1], B.nc[2], B.nc[3], 0.014, 8), shapeOf('nc', 2, B.nc[2], B.nc[3]), 'XM5H_NC');
      add(housing.patch(B.jack[0], B.jack[1], B.jack[2], B.jack[2], 0.010, 8), shapeOf('jack', 1, B.jack[2], B.jack[2]), 'XM5H_Jack');
      void led;
    } else {
      const U = HP.usb;
      add(housing.patch(U[0], U[1], U[2], U[3], 0.010, 8), shapeOf('usb', 0, U[2], U[3]), 'XM5H_USB');
    }
    return out;
  }
  const partsL = cupParts('L'), partsR = cupParts('R');

  // ---------------------------------------------------------------------------------------------
  // Headphone assembly. Per unit: the slider (fixed to the band), the swivel (hanger and cup turn
  // together about the slider to fold flat), the pivot at the hanger's foot (the cup swings in).
  const cupBasis = new THREE.Matrix4().makeBasis(V3(0, 0, 1), V3(0, 1, 0), V3(-1, 0, 0));   // cup frame -> right unit
  const cupQ = new THREE.Quaternion().setFromRotationMatrix(cupBasis);
  const pivotInUnit = V3(...HP.pivot).applyMatrix4(cupBasis);
  function buildPhones(tag) {
    const root = new THREE.Group(); root.name = 'XM5H_Phones' + tag;
    const mesh = (geo, mat, name) => Object.assign(new THREE.Mesh(geo, mat), { name });
    root.add(mesh(G.bandShell, M.band, 'XM5H_Band'), mesh(G.bandCushion, M.cushion, 'XM5H_Cushion'));
    const units = {};
    for (const side of ['L', 'R']) {
      const holder = new THREE.Group(); holder.name = 'XM5H_Side' + side;
      if (side === 'L') holder.scale.x = -1;
      root.add(holder);
      const unit = new THREE.Group(); unit.position.copy(bandEnd); holder.add(unit);
      const ext = new THREE.Group(); unit.add(ext);                  // moves down as the slider extends
      ext.add(mesh(G.slider, M.slider, 'XM5H_Slider'));
      const swivel = new THREE.Group(); ext.add(swivel);
      swivel.add(mesh(G.hanger, M.hanger, 'XM5H_Hanger'), mesh(side === 'L' ? G.logoL : G.logoR, M.print, 'XM5H_Logo'));
      if (side === 'R') swivel.add(mesh(G.redDot, M.red, 'XM5H_RedDot'));
      const pivot = new THREE.Group(); pivot.position.y = -HG.len; swivel.add(pivot);
      pivot.add(mesh(G.knuckle, M.hanger, 'XM5H_Knuckle'));
      const cup = new THREE.Group(); cup.name = 'XM5H_Cup' + side;
      cup.quaternion.copy(cupQ); cup.position.copy(pivotInUnit).negate();
      pivot.add(cup);
      cup.add(mesh(G.housing, M.body, 'XM5H_Housing'), mesh(G.face, M.face, 'XM5H_Face'), mesh(G.pad, M.pad, 'XM5H_Pad'), mesh(G.baffle, M.baffle, 'XM5H_Baffle'), mesh(G.seam, M.seam, 'XM5H_Seam'));
      for (const p of side === 'L' ? partsL : partsR) cup.add(mesh(p.geometry, p.material, p.name));
      units[side] = { holder, unit, ext, swivel, pivot, cup };
    }
    // pose: fold 0 (worn) .. 1 (flat, outer faces to the front), slider extension, cup swing and turn
    // (squeeze: the band's arms flexed in, as when stored in the case)
    function pose({ fold = 0, ext = HERO.ext, roll = HP.roll, yaw = HP.yaw, squeeze = 0 } = {}) {
      for (const s of ['L', 'R']) {
        const u = units[s];
        u.unit.rotation.z = rad(squeeze);
        u.ext.position.y = -ext;
        u.swivel.rotation.y = rad(yaw * (1 - fold) + 90 * fold);
        u.pivot.rotation.z = rad(roll * (1 - fold));
      }
    }
    pose();
    return { root, units, pose };
  }

  // ---------------------------------------------------------------------------------------------
  // Case geometry
  // plan outline: radius by angle about the outline's middle, from the half-width table
  const caseOutline = (() => {
    const T = CASE.hw, hwAt = (f) => {
      if (f <= 0 || f >= 1) return 0;
      let k = 0; while (k < T.length - 2 && f > T[k + 1][0]) k++;
      const [f0, h0] = T[k], [f1, h1] = T[k + 1], t = (f - f0) / (f1 - f0);
      return h0 + (h1 - h0) * t;
    };
    // pillow frame: x across, y along the length (+y the narrow end); centre at 0.42 L from the wide end
    const yC = 0.42 * CASE.L;
    const inside = (x, y) => { const f = (y + yC) / CASE.L; return Math.abs(x) <= hwAt(f) * CASE.W / 2; };
    const N = 96, r = [];
    for (let k = 0; k < N; k++) {
      const a = 2 * Math.PI * k / N, c = Math.cos(a), s = Math.sin(a);
      let lo = 0, hi = CASE.L;
      for (let it = 0; it < 40; it++) { const m = (lo + hi) / 2; if (inside(c * m, s * m)) lo = m; else hi = m; }
      r.push(lo);
    }
    // smooth lightly so the table's corners don't show
    const sm = r.map((v, i) => 0.25 * r[(i + N - 1) % N] + 0.5 * v + 0.25 * r[(i + 1) % N]);
    return { fn: periodic(sm), yC };
  })();
  const caseR = caseOutline.fn;
  const inset = (fn, d) => (phi) => fn(phi) - d;
  const casePillow = (H, d, faceK) => pillow({ outline: inset(caseR, d), faceC: [0, 0], faceK, H, wall: 0.35, n: CASE.n, dome: CASE.dome, NU: 160, NC: 18, NF: 8 });
  const baseOut = casePillow(CASE.baseH, 0, CASE.faceK), baseIn = casePillow(CASE.baseH - CASE.wall, CASE.wall, CASE.faceK * 0.97);
  const lidOut = casePillow(CASE.lidH, 0, CASE.faceK), lidIn = casePillow(CASE.lidH - CASE.wall, CASE.wall, CASE.faceK * 0.96);
  // pillow (x, y, z) -> case: the base grows down (y = -z, z = y), the lid up (x mirrored so it stays a rotation)
  const toBase = new THREE.Matrix4().makeBasis(V3(1, 0, 0), V3(0, 0, 1), V3(0, -1, 0));
  const toLid = new THREE.Matrix4().makeBasis(V3(-1, 0, 0), V3(0, 0, 1), V3(0, 1, 0));
  function ringBand(r0, r1, y0, y1, flip) {   // a strip round the outline (zip tape and the rim)
    const NU = 160, rows = [[r0, y0], [r1, y1]];
    return gridGeometry(2, NU, (j, i) => {
      const phi = 360 * i / NU, a = rad(phi), r = caseR(phi) + rows[j][0];
      return [r * Math.cos(a), rows[j][1], r * Math.sin(a)];
    }, { flip });
  }
  function buildCase() {
    const root = new THREE.Group(); root.name = 'XM5H_Case';
    root.matrixAutoUpdate = false;
    const spin = new THREE.Group(); root.add(spin);
    const base = new THREE.Group(); base.name = 'XM5H_CaseBase'; spin.add(base);
    const mk = (geo, mat, name, m4) => { if (m4) geo.applyMatrix4(m4); return Object.assign(new THREE.Mesh(geo, mat), { name }); };
    const baseShell = mk(baseOut.geometry(), M.caseShell, 'XM5H_CaseBaseShell', toBase);
    const baseLining = mk(baseIn.geometry(true), M.caseLining, 'XM5H_CaseBaseLining', toBase);
    const baseRim = mk(ringBand(0.0, -CASE.wall, -0.001, -0.001, true), M.zip, 'XM5H_CaseBaseRim');
    const baseZip = mk(ringBand(0.035, 0.035, -0.005, -0.42, false), M.zip, 'XM5H_CaseBaseZip');
    base.add(baseShell, baseLining, baseRim, baseZip);
    // the loop at the narrow end: a flat ribbon tab with a slot
    {
      const lp = CASE.loop, s = new THREE.Shape(), zN = caseR(90) - 0.3;
      s.moveTo(-lp.w / 2, 0); s.lineTo(-lp.w / 2 * 0.8, lp.len * 0.55);
      s.quadraticCurveTo(0, lp.len * 1.12, lp.w / 2 * 0.8, lp.len * 0.55); s.lineTo(lp.w / 2, 0); s.lineTo(-lp.w / 2, 0);
      const h = new THREE.Path(); const sy = lp.len * 0.5, sl = lp.slotL, sw = lp.slotW;
      h.moveTo(0, sy - sl / 2); h.absarc(0, sy - sl / 2 + sw / 2, sw / 2, -Math.PI / 2, Math.PI / 2, true);
      h.absarc(0, sy + sl / 2 - sw / 2, sw / 2, -Math.PI / 2, Math.PI / 2, false); s.holes.push(h);
      const g = new THREE.ExtrudeGeometry(s, { depth: lp.t, bevelEnabled: true, bevelThickness: 0.05, bevelSize: 0.05, bevelSegments: 2, curveSegments: 10 });
      g.rotateX(Math.PI / 2); g.translate(0, -0.15 + lp.t / 2, zN);
      base.add(Object.assign(new THREE.Mesh(g, M.loop), { name: 'XM5H_CaseLoop' }));
    }
    // zipper pull near the hinge, on the right side
    {
      const phi = -38, a = rad(phi), r = caseR(phi) + 0.12;
      const g = new THREE.BoxGeometry(0.16, 0.55, 1.3); g.translate(0, -0.1, -0.65);
      const pull = new THREE.Mesh(g, M.pull); pull.name = 'XM5H_CasePull';
      pull.position.set(r * Math.cos(a), -0.05, r * Math.sin(a)); pull.rotation.y = rad(-20);
      base.add(pull);
    }
    // the lid, hinged at the wide end
    const lid = new THREE.Group(); lid.name = 'XM5H_CaseLid';
    const zH = -caseR(270) + 0.25;
    lid.position.set(0, 0.0, zH); spin.add(lid);
    const lidBody = new THREE.Group(); lidBody.position.set(0, 0, -zH); lid.add(lidBody);
    const lidShell = mk(lidOut.geometry(), M.caseShell, 'XM5H_CaseLidShell', toLid);
    const lidLining = mk(lidIn.geometry(true), M.caseLining, 'XM5H_CaseLidLining', toLid);
    const lidRim = mk(ringBand(0.0, -CASE.wall, 0.001, 0.001, false), M.zip, 'XM5H_CaseLidRim');
    const lidZip = mk(ringBand(0.035, 0.035, 0.005, 0.42, true), M.zip, 'XM5H_CaseLidZip');
    lidBody.add(lidShell, lidLining, lidRim, lidZip);
    // the fabric hinge strip
    const hinge = new THREE.Mesh(new THREE.CylinderGeometry(0.32, 0.32, 7.0, 16), M.zip);
    hinge.rotation.z = Math.PI / 2; hinge.position.set(0, 0, zH - 0.12); spin.add(hinge);
    // the folded headphones, pads down on the base's floor, the band towards the loop
    const anchor = new THREE.Group(); anchor.name = 'XM5H_CasePhones';
    base.add(anchor);
    const phones = buildPhones('Case');
    phones.pose({ fold: 1, ext: CASE.phones.ext, squeeze: CASE.phones.squeeze });
    // headphone frame -> case: band top (+y) towards the loop (+z), outer faces (+z) up (+y)
    const inner = new THREE.Group();
    inner.quaternion.setFromRotationMatrix(new THREE.Matrix4().makeBasis(V3(-1, 0, 0), V3(0, 0, 1), V3(0, 1, 0)));
    inner.add(phones.root);
    anchor.add(inner);
    inner.updateMatrixWorld(true);
    const bb = new THREE.Box3().setFromObject(inner);
    const c = bb.getCenter(new THREE.Vector3());
    phones.root.position.copy(c.clone().negate().applyQuaternion(inner.quaternion.clone().invert()));
    // resting on the floor of the base (its lining), centred across, offset along the length
    const sz = bb.getSize(new THREE.Vector3());
    anchor.position.set(CASE.phones.pos[0], -(CASE.baseH - CASE.wall) + sz.y / 2 * CASE.phones.scale + CASE.phones.pos[1], CASE.phones.pos[2]);
    anchor.scale.setScalar(CASE.phones.scale);
    return { root, spin, base, lid, phones, anchors: [{ node: anchor, y: anchor.position.y }],
      parts: { baseShell, baseLining, lidShell, lidLining, baseZip, lidZip } };
  }

  // ---------------------------------------------------------------------------------------------
  // Assembly: the hero headphones, turned and tilted to fill the hero frame like the earbud pair, spun
  // as one rigid body about the vertical through their middle (nothing can pass through anything)
  const budsRoot = new THREE.Group(); budsRoot.name = 'wh1000xm5_phones';
  budsRoot.position.set(...HERO.pos);
  const spinG = new THREE.Group(); budsRoot.add(spinG);
  const orient = new THREE.Group();
  // [yaw, pitch, roll] as the fit tools use them: yaw about the headphones' own vertical first, then
  // pitch towards the camera, then roll in the picture
  orient.rotation.set(rad(HERO.rot[1]), rad(HERO.rot[0]), rad(HERO.rot[2]), 'ZXY');
  orient.scale.setScalar(HERO.scale);
  spinG.add(orient);
  const phones = buildPhones('Hero');
  phones.pose({ fold: 0, ext: HERO.ext });
  orient.add(phones.root);
  phones.root.updateMatrixWorld(true);
  {
    const bb = new THREE.Box3().setFromObject(phones.root);
    phones.root.position.copy(bb.getCenter(new THREE.Vector3()).negate());
  }

  const kase = buildCase();
  const caseM = new THREE.Matrix4().makeTranslation(...CASEREST.pos)
    .multiply(new THREE.Matrix4().makeRotationY(rad(CASEREST.yaw)))
    .multiply(new THREE.Matrix4().makeRotationX(rad(CASEREST.tilt)))
    .multiply(new THREE.Matrix4().makeScale(CASEREST.scale, CASEREST.scale, CASEREST.scale));
  kase.root.matrix.copy(caseM);

  const warm = new THREE.Group(); warm.name = 'XM5H_Warm'; warm.scale.setScalar(1e-6);

  // ---------------------------------------------------------------------------------------------
  // Finishes
  function applyFinish(f) {
    const set = (c, a, k = 1) => c.setRGB(a[0] * k, a[1] * k, a[2] * k);
    set(M.body.color, f.body); set(M.band.color, f.body); set(M.hanger.color, f.body); set(M.face.color, f.face);
    set(M.slider.color, f.slider); set(M.hinge.color, f.hinge);
    set(M.pad.color, f.pad); set(M.cushion.color, f.pad);
    set(M.baffle.color, f.baffle); set(M.seam.color, f.seam);
    set(M.print.color, f.print); set(M.print.emissive, f.print, 0.25);
    for (const [k, m] of Object.entries(SH)) {
      if (k === 'led') continue;
      set(m.color, f.body);
      set(m.userData.uniforms.uInk.value, k.startsWith('mic') ? f.mic : f.seam, k.startsWith('mic') ? 0.35 : k === 'notch' ? 0.25 : 0.6);
    }
    set(SH.led.userData.uniforms.uInk.value, f.hinge, 0.6);
    set(M.caseShell.color, f.caseShell); set(M.caseLining.color, f.caseLining); set(M.loop.color, f.caseShell);
    set(M.zip.color, f.zip); set(M.pull.color, f.pull);
    for (const m of [M.body, M.face, M.band, M.hanger, M.slider, M.pad, M.cushion, M.caseShell, M.caseLining, M.loop]) set(m.emissive, m.color.toArray(), f.lift[0]);
    for (const m of Object.values(SH)) if (m !== SH.led) set(m.emissive, m.color.toArray(), f.lift[0]);
  }
  function finishCopy(name, from) {
    const src = from || FINISHES[name] || FINISHES.black;
    return Object.fromEntries(KEYS.map((k) => [k, src[k].slice()]));
  }

  // ---------------------------------------------------------------------------------------------
  // Pose (called by hero3d.js every frame it draws)
  const LID_OPEN = rad(CASE.lidOpenDeg);
  function applyPose(st, active) {
    const a = rad(st.angle);
    spinG.rotation.y = a;
    kase.spin.rotation.y = a;
    kase.lid.rotation.x = -LID_OPEN * st.lid;
    budsRoot.visible = active && st.model === 'buds';
    kase.root.visible = active && st.model === 'case';
  }

  return {
    buds: { root: budsRoot, spin: spinG, orient, phones },
    kase,
    FINISHES, KEYS, finishCopy, applyFinish, applyPose, ready, warm, LID_OPEN,
    M, SH, HP, CASE, HERO, CASEREST, G, buildPhones, housing,
  };
}
