// Times the hero page's start (?debug marks) for a device: node profile_start.mjs <xm5|c710n|wh1000xm5>
// XM_ANGLE=swiftshader mimics a phone/emulator without a fast GPU.
import { openHero } from './harness.mjs';
const device = process.argv[2] || 'wh1000xm5';
const t0 = Date.now();
const page = await openHero({ query: device !== 'xm5' ? 'device=' + device + (process.env.XM_COLOR ? '&color=' + process.env.XM_COLOR : '') : '', width: 712, height: 506, log: true });
for (let i = 0; i < 600; i++) {
  if (await page.evaluate('!!(window.sunpods && window.sunpods.isReady)')) break;
  await new Promise((r) => setTimeout(r, 250));
}
console.log(device, 'ready after', Date.now() - t0, 'ms (wall, incl. Chrome start)');
await page.close();
process.exit(0);
