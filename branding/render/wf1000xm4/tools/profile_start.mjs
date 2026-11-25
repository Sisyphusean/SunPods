// Times the hero page's start (?debug marks) for a device:  node profile_start.mjs [device] [colour]
// X4_ANGLE=swiftshader mimics a phone/emulator without a fast GPU.
import { openHero } from './harness.mjs';
const [device = 'wf1000xm4', colour = 'black'] = process.argv.slice(2);
const t0 = Date.now();
const page = await openHero({ query: device === 'xm5' ? '' : `device=${device}&color=${colour}`, width: 712, height: 506, log: true });
console.log(device, 'ready after', Date.now() - t0, 'ms (wall, incl. Chrome start)');
await page.close();
process.exit(0);
