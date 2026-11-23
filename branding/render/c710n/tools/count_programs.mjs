// Shader programs the hero needs for its first frame:   node count_programs.mjs <xm5|c710n> <colour>
import { openHero } from './harness.mjs';
const [device = 'c710n', color = 'black'] = process.argv.slice(2);
const page = await openHero({ query: `device=${device}&color=${color}`, width: 712, height: 506, log: false });
const r = await page.evaluate(`(async () => {
  const d = await window.sunpods.debugC7(false);
  const progs = d.renderer.info.programs || [];
  const mats = new Map();
  d.scene.traverseVisible((o) => { if (o.isMesh) for (const m of [].concat(o.material)) mats.set(m.uuid, m.type + (m.name ? ':' + m.name : '') + (m.userData && m.userData.pass ? ':' + m.userData.pass : '')); });
  return JSON.stringify({ programs: progs.length, names: progs.map((p) => p.name).slice(0, 80), visibleMaterials: mats.size, types: [...new Set(mats.values())].slice(0, 60) });
})()`);
console.log(device, color, r);
await page.close(); process.exit(0);
