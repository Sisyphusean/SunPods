# Sunpods

A Material You companion for the Sony WF-1000XM5. No account, no sign-in, no internet permission.

## Features

* **Battery**: left, right and case, with charging state, in the app, a notification and two widgets
* **Noise control**: noise cancelling, ambient (level 1 to 20, focus on voice) or off
* **Equaliser**: Sony presets (Bright, Excited, Mellow, Relaxed, Vocal, Treble, Bass, Speech), plus Manual and Custom 1/2 with five bands and Clear Bass
* **DSEE Extreme** on or off
* **Connection quality**: prioritise sound quality (LDAC) or a stable connection
* **Speak-to-Chat**: on or off, sensitivity, and when chat mode ends
* **Touch controls**: assign ambient sound control, playback, volume or nothing to each earbud
* **Wearing detection**: pause when removed, power off when removed
* **Device info**: model, firmware, current codec
* **Widgets**: the full Sunpods widget (battery and noise control) and a compact 2×1 battery widget
* **Quick Settings tile**: tap to cycle noise cancelling, ambient and off, with battery in the subtitle

Every change is read back from the earbuds, so the app shows what they actually applied.

## Install

1. Pair the WF-1000XM5 in Android Bluetooth settings.
2. Install the APK and allow Nearby devices. Notification permission is optional.
3. Close Sony Sound Connect. Sony exposes one control connection, so only one app can hold it.
4. Open the case lid to read the case battery.
5. If something fails, use **Device → Share diagnostics**.

## Build

JDK 17 and Android SDK 35:

```sh
./gradlew test assembleDebug
```

## Protocol

This uses Sony's MDR v2 framing over RFCOMM. Command and value codes were checked against the command tables in Sound Connect 13.2.1 and against Gadgetbridge (AGPL-3.0-or-later) and SonyBridge (MIT). No code from either project is included.

| Feature | GET / SET | Inquired type |
|---|---|---|
| Battery L/R, case | 0x22 | 0x09, 0x0a |
| Noise / ambient | 0x66 / 0x68 | 0x15 |
| Equaliser | 0x56 / 0x58 | 0x00 |
| DSEE, connection mode | 0xe6 / 0xe8 | 0x01, 0x00 |
| Pause when removed, touch, Speak-to-Chat | 0xf6 / 0xf8 | 0x01, 0x03, 0x0c |
| Speak-to-Chat settings | 0xfa / 0xfc | 0x0c |
| Auto power off | 0x26 / 0x28 | 0x05 |
| Model, firmware | 0x04 | 0x01, 0x02 |
| Codec | 0x12 | 0x02 |

Sony and WF-1000XM5 are trademarks of Sony Group Corporation. This project is independent and not endorsed by Sony.
