// Behaviour test of the hero page in headless Chrome with touch input: ready signal, horizontal
// spin with fling and the 2.5 s ease home (+ settled haptic), vertical drags ignored, tap, render
// on demand (no frames while idle), colour crossfade, case model and lid.
//   node test_page.mjs
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(here, '..', '..', '..', 'app', 'src', 'main', 'assets', 'hero3d');
const MIME = { '.html': 'text/html', '.js': 'text/javascript', '.wasm': 'application/wasm', '.glb': 'model/gltf-binary', '.png': 'image/png' };
const requests = [];
const server = http.createServer((req, res) => {
  requests.push(req.url);
  const p = path.join(ROOT, decodeURIComponent(new URL(req.url, 'http://x').pathname));
  if (!p.startsWith(ROOT) || !fs.existsSync(p) || fs.statSync(p).isDirectory()) { res.writeHead(404); res.end(); return; }
  res.writeHead(200, { 'Content-Type': MIME[path.extname(p)] || 'application/octet-stream' });
  fs.createReadStream(p).pipe(res);
});
await new Promise((r) => server.listen(0, '127.0.0.1', r));
const CHROME = ['C:/Program Files/Google/Chrome/Application/chrome.exe', 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'].find((p) => fs.existsSync(p));
const profile = fs.mkdtempSync(path.join(os.tmpdir(), 'hero3d-test-'));
const dbgPort = 9300 + Math.floor(Math.random() * 500);
const chrome = spawn(CHROME, ['--headless=new', `--remote-debugging-port=${dbgPort}`, `--user-data-dir=${profile}`, '--no-first-run',
  '--ignore-gpu-blocklist', '--use-angle=d3d11', '--window-size=412,300', 'about:blank'], { stdio: 'ignore' });
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
let target = null;
for (let i = 0; i < 100 && !target; i++) {
  try { target = (await (await fetch(`http://127.0.0.1:${dbgPort}/json/list`)).json()).find((t) => t.type === 'page'); } catch { /* */ }
  if (!target) await sleep(200);
}
const ws = new WebSocket(target.webSocketDebuggerUrl);
await new Promise((r) => ws.addEventListener('open', r, { once: true }));
let seq = 0; const pending = new Map();
ws.addEventListener('message', (ev) => {
  const m = JSON.parse(ev.data);
  if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); }
  else if (m.method === 'Runtime.exceptionThrown') console.log('[page exception]', JSON.stringify(m.params.exceptionDetails).slice(0, 600));
  else if (m.method === 'Runtime.consoleAPICalled' && (m.params.type === 'error' || String(m.params.args[0]?.value).startsWith('hero3d'))) console.log('[page error]', m.params.args.map((a) => a.value ?? a.description).join(' '));
});
const send = (method, params = {}) => new Promise((r) => { const id = ++seq; pending.set(id, r); ws.send(JSON.stringify({ id, method, params })); });
const ev = async (expression) => (await send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true })).result?.result?.value;
await send('Runtime.enable'); await send('Page.enable');
await send('Emulation.setDeviceMetricsOverride', { width: 412, height: 300, deviceScaleFactor: 2, mobile: true });
await send('Emulation.setTouchEmulationEnabled', { enabled: true, maxTouchPoints: 5 });
await send('Page.addScriptToEvaluateOnNewDocument', { source: `window.__host = []; window.SunPodsHost = { ready() { __host.push('ready'); }, settled() { __host.push('settled'); }, tap() { __host.push('tap'); } };` });
const t0 = Date.now();
await send('Page.navigate', { url: `http://127.0.0.1:${server.address().port}/index.html?debug=1&color=pink` });
let ok = false;
for (let i = 0; i < 300 && !ok; i++) { ok = await ev('!!(window.sunpods && window.sunpods.isReady)'); if (!ok) await sleep(50); }
const results = [];
const check = (name, pass, info = '') => { results.push(pass); console.log(pass ? 'PASS' : 'FAIL', name, info); };
check('ready signal reaches SunPodsHost', ok && (await ev('__host.includes("ready")')), `${Date.now() - t0} ms to ready`);
check('initial colour from URL', (await ev('sunpods.state.color')) === 'pink');
await sleep(1500);   // let the second model load
const touch = async (type, x, y) => send('Input.dispatchTouchEvent', { type, touchPoints: type === 'touchEnd' ? [] : [{ x, y }] });
const frames = () => ev('sunpods.frames || 0');
let f0 = await frames(); await sleep(800);
check('idle: no frames rendered', (await frames()) === f0);
// vertical drag
await touch('touchStart', 200, 100); for (let i = 1; i <= 8; i++) { await touch('touchMove', 202, 100 + i * 15); await sleep(16); } await touch('touchEnd');
check('vertical drag leaves the earbuds alone', Math.abs(await ev('sunpods.state.angle')) < 0.01);
// horizontal drag with a flick at the end
await touch('touchStart', 120, 150); for (let i = 1; i <= 10; i++) { await touch('touchMove', 120 + i * 14, 151); await sleep(16); } await touch('touchEnd');
const a1 = await ev('sunpods.state.angle');
check('horizontal drag spins (about 1.125 deg per px)', a1 > 140, `angle ${a1.toFixed(1)}`);
await sleep(600);
const a2 = await ev('sunpods.state.angle');
check('fling momentum continues after release', a2 > a1 + 5, `angle ${a2.toFixed(1)}`);
await sleep(2700);
const a3 = await ev('sunpods.state.angle');
await sleep(1600);
const a4 = await ev('sunpods.state.angle');
check('eases home about 2.5 s after the fling stops', a4 === 0 && (await ev('__host.includes("settled")')), `angle ${a3.toFixed(1)} then ${a4}`);
f0 = await frames(); await sleep(700);
check('idle again after settling: no frames', (await frames()) === f0);
// tap
await touch('touchStart', 200, 150); await sleep(60); await touch('touchEnd');
await sleep(100);
check('tap reaches SunPodsHost.tap', await ev('__host.includes("tap")'));
// colour crossfade
f0 = await frames();
await ev(`sunpods.setColor('silver')`); await sleep(150);
const mid = await frames(); await sleep(600); const end = await frames(); await sleep(400);
check('colour crossfade animates then stops', mid > f0 && end > mid && (await frames()) === end && (await ev('sunpods.state.color')) === 'silver', `${end - f0} frames`);
// case + lid
await ev(`sunpods.setModel('case')`); await sleep(600);
check('setModel case', (await ev('sunpods.state.model')) === 'case');
await ev(`sunpods.setLid(1)`); await sleep(1200);
check('setLid opens the lid', (await ev('sunpods.state.lid')) === 1);
await ev(`sunpods.setLid(0)`); await ev(`sunpods.setModel('buds')`); await sleep(800);
check('back to buds', (await ev('sunpods.state.model')) === 'buds');
const external = requests.filter((u) => !u.startsWith('/'));
check('only bundled files requested', external.length === 0, `${requests.length} requests: ${[...new Set(requests.map((u) => u.split('?')[0]))].join(' ')}`);
console.log(results.every(Boolean) ? 'ALL PASS' : 'SOME FAILED');
ws.close(); chrome.kill(); server.close();
setTimeout(() => { try { fs.rmSync(profile, { recursive: true, force: true }); } catch { /* */ } process.exit(0); }, 500);
