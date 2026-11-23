// Shared headless-Chrome harness for the WH-1000XM5 tools: serves the real hero page
// (app/src/main/assets/hero3d) and this wh1000xm5 folder (at /xm/), opens the page with ?debug, and
// evaluates code in it over the DevTools protocol. No npm packages needed (Node 22+).
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
export const XMDIR = path.resolve(here, '..');
export const REPO = path.resolve(here, '..', '..', '..', '..');
export const HERO = process.env.HERO3D_DIR ? path.resolve(process.env.HERO3D_DIR) : path.join(REPO, 'app', 'src', 'main', 'assets', 'hero3d');   // (override: a copy, e.g. git HEAD)
const MIME = { '.html': 'text/html', '.js': 'text/javascript', '.mjs': 'text/javascript', '.wasm': 'application/wasm',
  '.glb': 'model/gltf-binary', '.png': 'image/png', '.jpg': 'image/jpeg', '.json': 'application/json', '.txt': 'text/plain' };
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

export async function openHero({ query = '', width = 1000, height = 712, log = true } = {}) {
  const server = http.createServer((req, res) => {
    const u = decodeURIComponent(new URL(req.url, 'http://x').pathname);
    const [root, rel] = u.startsWith('/xm/') ? [XMDIR, u.slice(4)] : [HERO, u];
    const p = path.join(root, rel);
    if (!p.startsWith(root) || !fs.existsSync(p) || fs.statSync(p).isDirectory()) { res.writeHead(404); res.end(); return; }
    res.writeHead(200, { 'Content-Type': MIME[path.extname(p)] || 'application/octet-stream', 'Cache-Control': 'no-store' });
    fs.createReadStream(p).pipe(res);
  });
  await new Promise((r) => server.listen(0, '127.0.0.1', r));
  const port = server.address().port;
  const CHROME = ['C:/Program Files/Google/Chrome/Application/chrome.exe', 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'].find((p) => fs.existsSync(p));
  const profile = fs.mkdtempSync(path.join(os.tmpdir(), 'xm-chrome-'));
  const dbgPort = 9300 + Math.floor(Math.random() * 600);
  const chrome = spawn(CHROME, ['--headless=new', `--remote-debugging-port=${dbgPort}`, `--user-data-dir=${profile}`,
    '--no-first-run', '--no-default-browser-check', '--ignore-gpu-blocklist', '--enable-gpu', `--use-angle=${process.env.XM_ANGLE || 'd3d11'}`,
    `--window-size=${width},${height}`, 'about:blank'], { stdio: 'ignore' });
  let target = null;
  for (let i = 0; i < 100 && !target; i++) {
    try { target = (await (await fetch(`http://127.0.0.1:${dbgPort}/json/list`)).json()).find((t) => t.type === 'page'); } catch { /* not up yet */ }
    if (!target) await sleep(200);
  }
  if (!target) throw new Error('chrome did not start');
  const ws = new WebSocket(target.webSocketDebuggerUrl);
  await new Promise((r) => ws.addEventListener('open', r, { once: true }));
  let seq = 0; const pending = new Map();
  ws.addEventListener('message', (ev) => {
    const m = JSON.parse(ev.data);
    if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); }
    else if (log && m.method === 'Runtime.consoleAPICalled') console.log('[page]', m.params.type, m.params.args.map((a) => a.value ?? a.description).join(' '));
    else if (m.method === 'Runtime.exceptionThrown') console.log('[page exception]', JSON.stringify(m.params.exceptionDetails).slice(0, 1200));
  });
  const send = (method, params = {}) => new Promise((r) => { const id = ++seq; pending.set(id, r); ws.send(JSON.stringify({ id, method, params })); });
  const evaluate = async (expression) => {
    const r = await send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true });
    if (r.result?.exceptionDetails) throw new Error(JSON.stringify(r.result.exceptionDetails).slice(0, 1500));
    return r.result?.result?.value;
  };
  await send('Runtime.enable'); await send('Page.enable');
  await send('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: 1, mobile: false });
  await send('Page.navigate', { url: `http://127.0.0.1:${port}/index.html?debug=1${query ? '&' + query : ''}` });
  let ok = false;
  for (let i = 0; i < 400 && !ok; i++) { ok = await evaluate('!!(window.sunpods && window.sunpods.isReady)').catch(() => false); if (!ok) await sleep(100); }
  if (!ok) throw new Error('page not ready');
  const close = () => new Promise((r) => {
    ws.close(); chrome.kill(); server.close();
    setTimeout(() => { try { fs.rmSync(profile, { recursive: true, force: true }); } catch { /* locked */ } r(); }, 500);
  });
  // load a tool script (from this folder) into the page
  const inject = async (file) => evaluate(fs.readFileSync(path.join(here, file), 'utf8') + '\n;true');
  return { evaluate, send, close, port, inject };
}

export function savePng(file, dataUrl) {
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, Buffer.from(dataUrl.split(',')[1], 'base64'));
}
