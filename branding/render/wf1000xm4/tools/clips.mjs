// Renders the WF-1000XM4 setup clips' frames from the live three.js scene (headless Chrome), 30 fps,
// 960 px square, transparent; anim/pack.py then packs them like the XM5 clips (480 px WebP).
//   node clips.mjs <outDir> <open|pair|both> [colours...]   (X4_FRAMES=0,60,120 renders only those)
// open (120 frames, 4.0 s): the closed case turns in, the lid opens, both earbuds rise out of it.
// pair (210 frames, 7.0 s): Sony's pairing for the WF-1000XM4 (help guide: "Pairing and connecting",
//   "About the touch sensor"): the case is open with the earbuds in (its indicator lights green, as it
//   does when the lid opens), the case turns, both units come out of the case (the earbuds switch on),
//   then both touch sensors are held for about 5 seconds (fingertip rings, a progress arc) until the
//   earbuds enter pairing mode (a pulse on both: the beep and "Bluetooth pairing"). There is no
//   pairing button on this case.
import fs from 'node:fs';
import path from 'node:path';
import { openHero, savePng } from './harness.mjs';

const [outDir, which = 'both', ...cols] = process.argv.slice(2);
const colours = cols.length ? cols : ['black'];
const only = process.env.X4_FRAMES ? process.env.X4_FRAMES.split(',').map(Number) : null;
const SIZE = 960;
const ease = (t) => (t <= 0 ? 0 : t >= 1 ? 1 : t * t * (3 - 2 * t));
const lerp = (a, b, t) => a + (b - a) * t;
const lerpA = (a, b, t) => a.map((v, i) => lerp(v, b[i], t));
// piecewise timeline: keys [[frame, value], ...] eased between keys
function track(keys, f) {
  if (f <= keys[0][0]) return keys[0][1];
  for (let i = 0; i < keys.length - 1; i++) {
    const [f0, v0] = keys[i], [f1, v1] = keys[i + 1];
    if (f <= f1) { const t = ease((f - f0) / (f1 - f0)); return Array.isArray(v0) ? lerpA(v0, v1, t) : lerp(v0, v1, t); }
  }
  return keys[keys.length - 1][1];
}
const lin = (keys, f) => {   // linear (for the progress arc)
  if (f <= keys[0][0]) return keys[0][1];
  for (let i = 0; i < keys.length - 1; i++) { const [f0, v0] = keys[i], [f1, v1] = keys[i + 1]; if (f <= f1) return lerp(v0, v1, (f - f0) / (f1 - f0)); }
  return keys[keys.length - 1][1];
};

// poses: [yaw, pitch, roll, tx, ty, scale]
function openState(f) {
  const closed = [-28, 22, 0, 0, -0.02, 0.60];
  const pose = track([[0, closed], [16, [-24, 22, 0, 0, -0.02, 0.62]], [54, [-18, 20, 0, 0, -0.06, 0.65]],
    [98, [-14, 18, 0, 0, -0.09, 0.65]], [116, closed], [119, closed]], f);
  const lid = track([[0, 0], [14, 0], [46, 1], [100, 1], [116, 0], [119, 0]], f);
  const lift = track([[0, 0], [48, 0], [82, 1], [98, 1], [112, 0], [119, 0]], f);
  return { pose, lid, lift };
}
function pairState(f) {
  const front = [-20, 20, 0, 0, -0.04, 0.62], turned = [16, 16, 0, 0, -0.04, 0.62];
  const up = [0, 12, 0, 0, -0.08, 0.68], close = [0, 10, 0, 0, -0.14, 0.74];
  const pose = track([[0, front], [14, front], [40, turned], [52, turned], [84, up], [104, close], [190, close], [206, front], [209, front]], f);
  const lift = track([[0, 0], [46, 0], [74, 1], [194, 1], [206, 0], [209, 0]], f);
  const out = track([[0, 0], [60, 0], [94, 1], [192, 1], [205, 0], [209, 0]], f);
  // the hold: fingertips on both sensors from frame 104, the arc fills to 5 s (compressed to 2.5 s)
  const hold = track([[0, 0], [102, 0], [110, 1], [182, 1], [190, 0], [209, 0]], f);
  const prog = lin([[0, 0], [108, 0], [180, 1], [190, 1], [191, 0], [209, 0]], f);
  // pairing mode: two pulses on both earbuds before easing cleanly back into the case for seamless loop
  const k = f - 178, done = (k < 0 || f >= 198) ? 0 : Math.max(0, 1 - (k % 10) / 10) * (k < 20 ? 1 : 0.6);
  const led = f < 44 ? Math.min(1, f / 4) * (f > 34 ? (44 - f) / 10 : 1) : 0;   // green: the case's charge, lit as the lid opened
  return { pose, lid: 1, lift, out, hold, prog, done, doneGrow: (k < 0 || f >= 198) ? 0 : (k % 10) / 10, led };
}

const page = await openHero({ query: 'device=wf1000xm4', width: SIZE, height: SIZE, log: false });
await page.inject('fit_page.js');
await page.evaluate('window.X4FIT.init()');
await page.inject('clip_page.js');
for (const clip of which === 'both' ? ['open', 'pair'] : [which]) {
  for (const c of colours) {
    await page.evaluate(`window.X4FIT.done()`);
    await page.evaluate(`(async () => { const d = await window.sunpods.debugMod('wf1000xm4', false); d.setColor('${c}', true); })()`);
    await new Promise((r) => setTimeout(r, 1500));
    await page.evaluate(`window.X4CLIP.init(${clip === 'open' ? 0 : 1})`);
    const n = clip === 'open' ? 120 : 210;
    const dir = path.join(outDir, `${clip}_${c}`);
    fs.mkdirSync(dir, { recursive: true });
    for (let f = 0; f < n; f++) {
      if (only && !only.includes(f)) continue;
      const s = clip === 'open' ? openState(f) : pairState(f);
      const url = await page.evaluate(`window.X4CLIP.frame(${JSON.stringify({ ...s, size: SIZE })})`);
      savePng(path.join(dir, `f${String(f).padStart(4, '0')}.png`), url);
    }
    await page.evaluate(`window.X4CLIP.done()`);
    console.log('clip', clip, c, n, 'frames');
  }
}
await page.close();
process.exit(0);
