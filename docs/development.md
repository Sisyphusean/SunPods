# Building and how SunPods works

## Build

JDK 17 and Android SDK 35:

```sh
./gradlew test assembleDebug
```

Debug builds include an adb hook for protocol work (`DebugCommandReceiver`, reachable only from
`adb shell`). Release builds don't.

## Releasing

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Add the changes to `CHANGELOG.md`.
3. Merge to `main`.

`.github/workflows/release.yml` sees a version with no release yet, then tests, builds and signs the
APK, tags the commit `v<version>` and publishes the release. Merges that don't change the version ship
nothing. Every release is signed with the same key (see `SECURITY.md` for its fingerprint), because
Android only installs an update signed like the app already installed.

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

