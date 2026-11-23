// Times the hero page's start (?debug marks) for a device: node profile_start.mjs <xm5|c710n>
// C7_ANGLE=swiftshader mimics a phone/emulator without a fast GPU.
import { openHero } from './harness.mjs';
const device = process.argv[2] || 'c710n';
const t0 = Date.now();
const page = await openHero({ query: device === 'c710n' ? 'device=c710n' + (process.env.C7_COLOR ? '&color=' + process.env.C7_COLOR : '') : '', width: 712, height: 506, log: true });
for (let i = 0; i < 600; i++) {
  if (await page.evaluate('!!(window.sunpods && window.sunpods.isReady)')) break;
  await new Promise((r) => setTimeout(r, 250));
}
console.log(device, 'ready after', Date.now() - t0, 'ms (wall, incl. Chrome start)');
await page.close();
process.exit(0);
