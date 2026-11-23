// Renders the WF-C710N setup clips' frames from the live three.js scene (headless Chrome), 30 fps,
// 960 px square, transparent; anim/pack.py then packs them like the XM5 clips (480 px WebP, q 82).
//   node clips.mjs <outDir> <open|pair|both> [colours...]
// open (120 frames, 4.0 s): the closed case turns in, the lid opens, both earbuds rise out of it.
// pair (210 frames, 7.0 s): the C710N help guide's pairing steps: earbuds in the case with the lid
//   open, the case turns to its back, the pairing button is held (press ring), then it turns back
//   to the front where the indicator flashes blue twice, repeatedly.
import fs from 'node:fs';
import path from 'node:path';
import { openHero, savePng } from './harness.mjs';

const [outDir, which = 'both', ...cols] = process.argv.slice(2);
const colours = cols.length ? cols : ['black', 'white', 'pink', 'blue'];
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

// poses: [yaw, pitch, roll, tx, ty, scale]
function openState(f) {
  const pose = track([[0, [-28, 22, 0, 0, -0.02, 0.62]], [18, [-24, 22, 0, 0, -0.02, 0.64]], [60, [-18, 20, 0, 0, -0.06, 0.70]],
    [100, [-14, 18, 0, 0, -0.10, 0.72]], [119, [-13, 18, 0, 0, -0.10, 0.72]]], f);
  const lid = track([[0, 0], [16, 0], [52, 1], [119, 1]], f);
  const lift = track([[0, 0], [54, 0], [96, 1], [119, 1]], f);
  return { pose, lid, lift };
}
// the indicator during pairing: two short blue flashes, a pause, repeat (on 5, off 5, on 5, off 16 frames)
function blink(f, start) {
  if (f < start) return 0;
  const k = (f - start) % 31;
  return (k < 5 || (k >= 10 && k < 15)) ? 1 : 0;
}
function pairState(f, aim) {
  const front = [-22, 18, 0, 0, -0.04, 0.66];
  const back = [180 + 18, 12, 0, 0, 0, 0.66];
  const pose = track([[0, front], [22, front], [52, back], [66, aim.button], [140, aim.button], [150, aim.button],
    [178, aim.led], [209, aim.ledWide]], f);
  // press: the ring fades in and pulses while the button is held (frames 70 to 146)
  let glow = 0, grow = 0;
  if (f >= 70 && f <= 146) {
    const t = (f - 70) / 76;
    glow = Math.min(1, (f - 70) / 8) * (0.65 + 0.35 * Math.sin(t * Math.PI * 6)) * (f > 140 ? (146 - f) / 6 : 1);
    grow = t;
  }
  return { pose, lid: 1, lift: 0, glow, ringGrow: grow, led: blink(f, 176) };
}

const page = await openHero({ query: 'device=c710n', width: SIZE, height: SIZE, log: false });
await page.inject('fit_page.js');
await page.evaluate('window.C7FIT.init()');
await page.inject('clip_page.js');
for (const clip of which === 'both' ? ['open', 'pair'] : [which]) {
  for (const c of colours) {
    await page.evaluate(`window.C7FIT.done()`);
    await page.evaluate(`(async () => { const d = await window.sunpods.debugC7(false); d.setColor('${c}', true); })()`);
    // the colour change rebuilds the hero asynchronously; let it settle so its pair can't show up in the clip
    await new Promise((r) => setTimeout(r, 2000));
    await page.evaluate(`window.C7CLIP.init(${clip === 'open' ? 0 : 1})`);
    let aim = null;
    if (clip === 'pair') {
      // zoom targets: the pairing button (back) and the indicator (front), centred
      const C = await page.evaluate(`(async () => { const d = await window.sunpods.debugC7(false); return d.C7.CASE; })()`);
      const centre = async (pose, pt) => {
        let p = pose.slice();
        for (let i = 0; i < 3; i++) {
          const [x, y] = await page.evaluate(`window.C7CLIP.project(${JSON.stringify(p)}, ${JSON.stringify(pt)}, ${SIZE}, 1)`);
          p[3] -= (x - SIZE / 2) / SIZE; p[4] += (y - SIZE / 2) / SIZE;
        }
        return p;
      };
      aim = {
        button: await centre([180 + 10, 8, 0, 0, 0, 1.9], [C.button.x - 0.35, C.button.y, -C.B]),
        led: await centre([-12, 14, 0, 0, 0, 0.95], [0, C.led.y + 0.75, C.B]),
        ledWide: await centre([-16, 16, 0, 0, 0, 0.82], [0, C.led.y + 0.95, C.B]),
      };
    }
    const n = clip === 'open' ? 120 : 210;
    const dir = path.join(outDir, `${clip}_${c}`);
    fs.mkdirSync(dir, { recursive: true });
    for (let f = 0; f < n; f++) {
      const s = clip === 'open' ? openState(f) : pairState(f, aim);
      const url = await page.evaluate(`window.C7CLIP.frame(${JSON.stringify({ ...s, size: SIZE })})`);
      savePng(path.join(dir, `f${String(f).padStart(4, '0')}.png`), url);
    }
    await page.evaluate(`window.C7CLIP.done()`);
    console.log('clip', clip, c, n, 'frames');
  }
}
await page.close();
process.exit(0);
