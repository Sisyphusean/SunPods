// Renders the WH-1000XM5 setup clips' frames from the live three.js scene (headless Chrome), 30 fps,
// 960 px square, transparent; anim/pack.py then packs them like the XM5 clips (480 px WebP).
//   node clips.mjs <outDir> <open|pair|both> [colours...]
// open (120 frames, 4.0 s): the closed carrying case turns in, the lid opens, the headphones lift out,
//   unfold and settle into the hero pose while the case drops away.
// pair (210 frames, 7.0 s): the help guide's pairing steps: the headphones turn to show the power
//   button on the left cup, it is held (press ring), then the indicator beside it flashes blue twice.
import fs from 'node:fs';
import path from 'node:path';
import { openHero, savePng } from './harness.mjs';

const [outDir, which = 'both', ...cols] = process.argv.slice(2);
const colours = cols.length ? cols : ['black', 'silver', 'blue', 'pink'];
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

// poses: [yaw, pitch, roll, tx, ty, scale] of the subject on the fit rig (1 unit = 10 cm)
function openState(f) {
  const pose = track([[0, [-32, 42, 0, 0, -0.02, 1.50]], [18, [-28, 42, 0, 0, -0.02, 1.52]], [56, [-22, 38, 0, 0, -0.06, 1.58]],
    [70, [-18, 34, 0, 0, -0.14, 1.56]], [108, [-14, 28, 0, 0, -1.75, 1.45]], [119, [-14, 28, 0, 0, -1.80, 1.45]]], f);
  const lid = track([[0, 0], [16, 0], [50, 1], [119, 1]], f);
  const lift = track([[0, 0], [56, 0], [108, 1], [119, 1]], f);
  return { pose, lid, lift, flyY: -0.1, flyScale: 2.20, arc: 1.15, arcZ: 1.2 };
}
// the indicator during pairing: two short blue flashes, a pause, repeat (on 5, off 5, on 5, off 16 frames)
function blink(f, start, end) {
  if (f < start || f > end) return 0;
  const k = (f - start) % 31;
  return (k < 5 || (k >= 10 && k < 15)) ? 1 : 0;
}
function pairState(f, aim) {
  const pose = track([
    [0, aim.hero],
    [18, aim.hero],
    [54, aim.button],
    [130, aim.button],
    [140, aim.button],
    [164, aim.led],
    [180, aim.ledClose],
    [209, aim.hero],
  ], f);
  // press: the ring fades in and pulses while the button is held (frames 60 to 130)
  let glow = 0, grow = 0;
  if (f >= 60 && f <= 130) {
    const t = (f - 60) / 70;
    glow = Math.min(1, (f - 60) / 8) * (0.65 + 0.35 * Math.sin(t * Math.PI * 6)) * (f > 124 ? (130 - f) / 6 : 1);
    grow = t;
  }
  return { pose, glow, ringGrow: grow, led: blink(f, 140, 184) };
}

const page = await openHero({ query: 'device=wh1000xm5', width: SIZE, height: SIZE, log: false });
await page.inject('fit_page.js');
await page.evaluate('window.XMFIT.init()');
if (process.env.XM_OVR) await page.evaluate(`XMFIT.rebuild(${process.env.XM_OVR})`);
await page.inject('clip_page.js');
for (const clip of which === 'both' ? ['open', 'pair'] : [which]) {
  for (const c of colours) {
    await page.evaluate(`window.XMFIT.done()`);
    await page.evaluate(`(async () => { const d = await window.sunpods.debugMod('wh1000xm5', false); d.setColor('${c}', true); })()`);
    await new Promise((r) => setTimeout(r, 1500));
    await page.evaluate(`window.XMCLIP.init('${clip}')`);
    let aim = null;
    if (clip === 'pair') {
      // the hero view, then a gentle unclipped zoom toward the power button and status indicator
      const centre = async (pose, part) => {
        const p = pose.slice();
        for (let i = 0; i < 3; i++) {
          const [x, y] = await page.evaluate(`window.XMCLIP.project(${JSON.stringify(p)}, '${part}', ${SIZE})`);
          p[3] -= ((x - SIZE / 2) / SIZE) * 0.45; p[4] += ((y - SIZE / 2) / SIZE) * 0.35;
        }
        p[4] = Math.max(-0.04, Math.min(0.06, p[4]));
        return p;
      };
      const [yb, pb] = await page.evaluate(`window.XMCLIP.facing('powerButton')`);
      const hero = await page.evaluate(`(async () => { const d = await window.sunpods.debugMod('wh1000xm5', false); return d.M.HERO.rot; })()`);
      aim = {
        hero: [hero[0], hero[1], hero[2], 0, 0, 2.35],
        button: await centre([yb + 20, pb - 10, 0, 0, 0, 2.65], 'powerButton'),
        led: await centre([yb + 16, pb - 8, 0, 0, 0, 2.60], 'statusLed'),
        ledClose: await centre([yb + 14, pb - 7, 0, 0, 0, 2.68], 'statusLed'),
      };
    }
    const n = clip === 'open' ? 120 : 210;
    const dir = path.join(outDir, `${clip}_${c}`);
    fs.mkdirSync(dir, { recursive: true });
    const only = process.env.XM_FRAMES ? process.env.XM_FRAMES.split(",").map(Number) : null;   // tools: a few frames only
    for (let f = 0; f < n; f++) {
      if (only && !only.includes(f)) continue;
      const s = clip === 'open' ? openState(f) : pairState(f, aim);
      const url = await page.evaluate(`window.XMCLIP.frame(${JSON.stringify({ ...s, size: SIZE })})`);
      savePng(path.join(dir, `f${String(f).padStart(4, '0')}.png`), url);
    }
    await page.evaluate(`window.XMCLIP.done()`);
    console.log('clip', clip, c, n, 'frames');
  }
}
await page.close();
process.exit(0);
