# SunPods

A free, open source companion app for the Sony WF-1000XM5, in Material You. No account, no sign-in, no tracking.

## Download

Get the latest APK from [Releases](https://github.com/Sisyphusean/SonyPodsBattery/releases/latest).

SunPods tells you when a new version is out (Settings > Updates). To have updates installed for you, add it to Obtainium:

[<img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" alt="Get it on Obtainium" height="54">](https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/Sisyphusean/SonyPodsBattery)

You can also follow releases on GitHub: **Watch > Custom > Releases**.

## Features

* **Battery**: left, right and case, with charging state, in the app, a notification, widgets and a Quick Settings tile
* **Noise control**: noise cancelling, ambient (level 1 to 20, focus on voice) or off
* **Equaliser**: Sony's presets, Manual, and Custom 1 and 2 with five bands and Clear Bass
* **LE Audio**: switch between Classic and LE Audio from the app, with clearer calls on LE Audio
* **Touch controls**: choose what each earbud does, from the options your earbuds offer
* **Speak-to-Chat, wearing detection, DSEE Extreme, connection priority, voice prompts**
* **Devices**: see, connect and switch between the devices your earbuds remember
* **Call mic test**: hear yourself the way callers do
* **Automations, battery health and last seen location**, all kept on your phone

Every change is read back from the earbuds, so the app shows what they actually applied. In LE Audio mode each earbud keeps its own settings, so SunPods writes to both.

## Getting started

1. Pair the WF-1000XM5 in Android Bluetooth settings.
2. Install the APK, open SunPods and follow the setup.
3. Close Sony Sound Connect. The earbuds accept one control app at a time.

### Switching between Classic and LE Audio

Switching means your phone has to pair with the earbuds again, which Android only lets apps do with help. SunPods uses [Shizuku](https://shizuku.rikka.app/) for that, so the switch is automatic. Without Shizuku, SunPods doesn't switch and tells you why.

* LE Audio needs Android 13 or later and a phone that supports it. SunPods checks this before switching.
* Shizuku stops when your phone restarts. Some phones also close it in the background: set Shizuku's battery use to **Unrestricted**.
* On LE Audio, Connect to Two Devices, the device list and DSEE Extreme are off. Switch back to Classic to use them.

## Privacy

SunPods talks to your earbuds over Bluetooth and keeps everything on your phone. Its only use of the internet is a daily check for a new version on GitHub, which sends nothing about you or your earbuds and can be turned off in Settings > Updates.

## Build

JDK 17 and Android SDK 35:

```sh
./gradlew test assembleDebug
```

Release builds are signed in GitHub Actions when a `v*` tag is pushed (see `.github/workflows/release.yml`).

## Protocol

SunPods uses Sony's MDR v2 framing, over RFCOMM on Classic and over a GATT service on LE Audio. Command and value codes were checked against the command tables in Sony Sound Connect and against Gadgetbridge (AGPL-3.0-or-later) and SonyBridge (MIT). No code from any of them is included.

| Feature | GET / SET | Inquired type |
|---|---|---|
| Battery L/R, case | 0x22 | 0x09, 0x0a |
| Noise / ambient | 0x66 / 0x68 | 0x17 |
| Equaliser | 0x56 / 0x58 | 0x00 |
| DSEE, connection priority | 0xe6 / 0xe8 | 0x01, 0x00 |
| Pause when removed, touch, Speak-to-Chat | 0xf6 / 0xf8 | 0x01, 0x03, 0x0c |
| Touch options per earbud | 0xf0 | 0x03 |
| Speak-to-Chat settings, noise cycle | 0xfa / 0xfc | 0x0c, 0x03 |
| Auto power off | 0x26 / 0x28 | 0x05 |
| Classic / LE Audio | 0x46 / 0x48 | 0x0c |
| Model, firmware | 0x04 | 0x01, 0x02 |
| Codec | 0x12 | 0x02 |

Changes that make the earbuds reconnect are confirmed with an alert (0x99 / 0x98). Some firmware holds the change without sending the alert, so SunPods confirms it anyway and reads the result back.

## Licence

SunPods is free software under the [GNU General Public License v3.0](LICENSE).

Sony and WF-1000XM5 are trademarks of Sony Group Corporation. SunPods is independent and not endorsed by Sony.
