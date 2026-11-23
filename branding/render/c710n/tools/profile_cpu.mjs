// CPU profile of a C710N page start: top functions by self time.   node profile_cpu.mjs
import { openHero } from './harness.mjs';
const page = await openHero({ query: 'device=c710n', width: 712, height: 506, log: false });
await page.send('Profiler.enable');
await page.send('Profiler.setSamplingInterval', { interval: 1000 });
await page.send('Profiler.start');
await page.send('Page.reload', { ignoreCache: false });
await new Promise((r) => setTimeout(r, 1500));
for (let i = 0; i < 600; i++) { if (await page.evaluate('!!(window.sunpods && window.sunpods.isReady)').catch(() => false)) break; await new Promise((r) => setTimeout(r, 200)); }
const pr = await page.send('Profiler.stop'); const profile = pr.profile || (pr.result && pr.result.profile);
const byId = new Map(profile.nodes.map((n) => [n.id, n]));
const self = new Map();
const dt = profile.timeDeltas; const samples = profile.samples;
for (let i = 0; i < samples.length; i++) { const n = byId.get(samples[i]); const cf = n.callFrame; const k = `${cf.functionName || '(anon)'} ${cf.url.split('/').pop()}:${cf.lineNumber + 1}`; self.set(k, (self.get(k) || 0) + (dt[i] || 0)); }
const total = [...self.values()].reduce((a, b) => a + b, 0);
console.log('total sampled ms', Math.round(total / 1000));
for (const [k, v] of [...self.entries()].sort((a, b) => b[1] - a[1]).slice(0, 25)) console.log(String(Math.round(v / 1000)).padStart(7), 'ms', k);
// inclusive time for c710n.js functions
const parent = new Map(); for (const n of profile.nodes) for (const c of n.children || []) parent.set(c, n.id);
const incl = new Map();
for (let i = 0; i < samples.length; i++) { const seen = new Set(); let id = samples[i]; while (id) { const cf = byId.get(id).callFrame; if (cf.url.endsWith('c710n.js')) { const k = `${cf.functionName || '(anon)'}:${cf.lineNumber + 1}`; if (!seen.has(k)) { seen.add(k); incl.set(k, (incl.get(k) || 0) + (dt[i] || 0)); } } id = parent.get(id); } }
console.log('--- c710n.js inclusive');
for (const [k, v] of [...incl.entries()].sort((a, b) => b[1] - a[1]).slice(0, 25)) console.log(String(Math.round(v / 1000)).padStart(7), 'ms', k);
await page.close(); process.exit(0);
