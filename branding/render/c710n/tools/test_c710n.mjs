// Behaviour test of the hero page's WF-C710N path in headless Chrome, phone sized with touch (like
// hero3d_tools/test_page.mjs): ?device=c710n, setDevice both ways, the four colours, case and lid,
// snapshot, spin, idle, and that the XM5 stays the default. Saves the live snapshots it takes to
// <previewDir> (live_<colour>_<view>.png).   node test_c710n.mjs <previewDir>
import fs from 'node:fs';
import path from 'node:path';
import { openHero, savePng } from './harness.mjs';

const outDir = process.argv[2];
fs.mkdirSync(outDir, { recursive: true });
const results = [];
const check = (name, pass, info = '') => { results.push(!!pass); console.log(pass ? 'PASS' : 'FAIL', name, info); };
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

// 1. default device (no parameter) is the XM5
{
  const page = await openHero({ query: '', width: 412, height: 300, log: false });
  check('default device is xm5', (await page.evaluate('sunpods.state.device')) === 'xm5');
  await page.close();
}

// 2. ?device=c710n
const page = await openHero({ query: 'device=c710n&color=blue', width: 412, height: 300, log: false });
const ev = (e) => page.evaluate(e);
check('URL ?device=c710n', (await ev('sunpods.state.device')) === 'c710n');
check('initial colour from URL (blue)', (await ev('sunpods.state.color')) === 'blue');
check('setDevice in the API', (await ev('typeof sunpods.setDevice')) === 'function');
await sleep(600);
const snap = async (name) => {
  const url = await ev('sunpods.snapshot()');
  if (url) savePng(path.join(outDir, name + '.png'), url);
  return url;
};
const alphaArea = (url) => ev(`(async () => { const i = new Image(); i.src = ${JSON.stringify(url)}; await i.decode();
  const c = document.createElement('canvas'); c.width = i.width; c.height = i.height; const g = c.getContext('2d'); g.drawImage(i, 0, 0);
  const d = g.getImageData(0, 0, c.width, c.height).data; let n = 0; for (let k = 3; k < d.length; k += 4) if (d[k] > 127) n++; return n / (c.width * c.height); })()`);
let u = await snap('live_blue_buds');
check('snapshot shows the C710N (Glass Blue)', u && (await alphaArea(u)) > 0.05);
// colours
for (const c of ['black', 'white', 'pink', 'blue']) {
  await ev(`sunpods.setColor('${c}')`); await sleep(700);
  check(`setColor ${c}`, (await ev('sunpods.state.color')) === c);
  await snap(`live_${c}_buds`);
}
await ev(`sunpods.setColor('silver')`); await sleep(300);
check('an XM5-only colour is ignored on the C710N', (await ev('sunpods.state.color')) === 'blue');
// case and lid
await ev(`sunpods.setModel('case')`); await sleep(900);
check('setModel case', (await ev('sunpods.state.model')) === 'case');
for (const c of ['black', 'white', 'pink', 'blue']) { await ev(`sunpods.setColor('${c}', true)`); await sleep(250); await snap(`live_${c}_case_closed`); }
await ev(`sunpods.setLid(1)`); await sleep(1400);
check('setLid opens the lid', (await ev('sunpods.state.lid')) === 1);
for (const c of ['black', 'white', 'pink', 'blue']) { await ev(`sunpods.setColor('${c}', true)`); await sleep(250); await snap(`live_${c}_case_open`); }
await ev(`sunpods.setLid(0)`); await ev(`sunpods.setModel('buds')`); await sleep(1000);
check('back to buds, lid closed', (await ev('sunpods.state.model')) === 'buds' && (await ev('sunpods.state.lid')) === 0);
// spin with touch
await page.send('Emulation.setTouchEmulationEnabled', { enabled: true, maxTouchPoints: 5 });
const touch = (type, x, y) => page.send('Input.dispatchTouchEvent', { type, touchPoints: type === 'touchEnd' ? [] : [{ x, y }] });
await touch('touchStart', 120, 150); for (let i = 1; i <= 10; i++) { await touch('touchMove', 120 + i * 14, 151); await sleep(16); } await touch('touchEnd');
const a1 = await ev('sunpods.state.angle');
check('horizontal drag spins the C710N', a1 > 100, `angle ${a1.toFixed(1)}`);
await sleep(5200);
check('eases home', (await ev('sunpods.state.angle')) === 0);
const f0 = await ev('sunpods.frames || 0'); await sleep(700);
check('idle: no frames rendered', (await ev('sunpods.frames || 0')) === f0);
// device switches
await ev(`sunpods.setColor('pink')`); await sleep(500);
await ev(`sunpods.setDevice('xm5')`); await sleep(1500);
check('setDevice xm5', (await ev('sunpods.state.device')) === 'xm5');
check('xm5 colour is a valid XM5 colour', ['black', 'silver', 'pink'].includes(await ev('sunpods.state.color')), await ev('sunpods.state.color'));
u = await snap('live_after_switch_xm5');
check('snapshot after the switch shows the XM5', u && (await alphaArea(u)) > 0.05);
await ev(`sunpods.setDevice('c710n')`); await sleep(1500);
check('setDevice c710n again', (await ev('sunpods.state.device')) === 'c710n');
check('the C710N colour is remembered (pink)', (await ev('sunpods.state.color')) === 'pink');
await ev(`sunpods.setDevice('nonsense')`); await sleep(400);
check('unknown device falls back to xm5', (await ev('sunpods.state.device')) === 'xm5');
const errs = await ev('(window.__errors || []).length');
check('no page errors', !errs, String(errs || 0));
await page.close();
console.log(results.every(Boolean) ? 'ALL PASS' : 'SOME FAILED');
process.exit(results.every(Boolean) ? 0 : 1);
