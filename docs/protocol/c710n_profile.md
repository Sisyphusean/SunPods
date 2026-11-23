# WF-C710N profile (reconstructed)

Best evidence-based guess at what the WF-C710N announces and answers, for implementing it in SunPods and simulating it in `C710nProfile.kt`. Nobody here has a C710N, so nothing below is a recording. Byte layouts come from `variants.md` (Sound Connect's serializers and validators); this file decides which variant applies.

Evidence used:

| Code | Source | What it gives |
|---|---|---|
| HG | Sony help guide, helpguide.sony.net/mdr/2986/v1/en/ | Features, touch defaults, sensors (pages cited inline) |
| GB | Gadgetbridge `SonyWFC710NCoordinator` and `SonyProtocolImplV2` (cross-check only) | The bytes Gadgetbridge sends to a C710N |
| X5 | SunPods' WF-1000XM5 recording, firmware 6.1.0 (`SimProfiles.kt`, `SimulatedXm5Test.kt`) | A real Sony list to compare with |
| SC | Sound Connect code, via `variants.md` | Layouts and FunctionType mapping |

Confidence: **High** = two independent sources agree, or the help guide names the exact feature and only one variant fits. **Medium** = one source, or the feature is certain but the variant is not. **Low / guess** = inferred from the XM5 or from absence.

## 1. Facts that decide variants

| Question | Answer | Source |
|---|---|---|
| Wearing detection | Yes. IR sensor in each unit detects insertion and removal. Playback pauses when a unit is removed and resumes when worn; both can be changed in the app | HG TP1001827919 (parts), TP1001824068 |
| Auto power off | Turns off when not worn for about 15 minutes after leaving the case; can be disabled in the app. GB notes Sony's app offers 15 min, 30 min, 1 h, 3 h, off | HG TP1001825937, GB coordinator comment |
| Speak-to-Chat | Not mentioned anywhere | HG (all pages read), GB declares none |
| Focus on Voice | Yes, as "Voice focus" in Ambient Sound Mode | HG TP1001825833 |
| Ambient level steps | Not stated | HG TP1001825833 |
| Quick Attention | Yes, hold the left sensor | HG TP1001827214 |
| Quick Access, Service Link | Yes; Service Link must be on for Quick Access | HG TP1001829246, TP1001827909 |
| Adaptive Sound Control | Yes, listed in the app features | HG TP1001827909 |
| Wind noise reduction, auto NC | No | HG TP1001825833 |
| Codecs, LE Audio | SBC and AAC only; Bluetooth 5.3; LE Audio not mentioned | HG TP1001827902, TP1001829716 |
| DSEE | Plain DSEE ("up to CD level"), not DSEE Extreme | HG TP1001829260 |
| Sound quality mode | Two options: Priority on Sound Quality, High Reliability (default) | HG TP1001827904 |
| Multipoint | Two devices; on/off and device change in the app | HG TP1001833093, TP1001827909 |
| Voice guidance | On/off, language switch (can take up to 10 minutes), volume; default English or Chinese; language list not given | HG TP1001827928 |
| Case battery | Shown in the app, plus a notification to charge when the case runs low | HG TP1001827909 |
| BLE name | GB matches `(?!LE_).*WF-C710N$`, so the earbuds also advertise an `LE_WF-C710N` name | GB coordinator |

Touch defaults (HG TP1001827238):

| Gesture | Left | Right |
|---|---|---|
| Tap | NC / Ambient switch | Play / pause |
| Double tap | Answer or end call; Quick Access | Next track; answer or end call |
| Triple tap | Quick Access | Previous track |
| Tap 4 or more times | Volume down | Volume up |
| Hold | Quick Attention | Voice assistant (phone's) |

This is the same gesture map as the XM5's recorded touch capability (presets 0x35 and 0x20, section 4), which makes that layout the best model for the C710N.

## 2. Reconstructed support-function lists

### 2.1 Table 1

| FT | Name | Confidence | Evidence |
|---|---|---|---|
| 0x11 | CONNECTION_STATUS | High | HG "Bluetooth connection status between the left or right units and the smartphone"; X5 |
| 0x12 | CODEC_INDICATOR | High | HG codec display; GB init sends `12 02`; X5 |
| 0x13 | UPSCALING_INDICATOR | High | HG "Displaying the DSEE function setting status"; X5 |
| 0x21 | LEFT_RIGHT_BATTERY_LEVEL_INDICATOR | Medium-high | GB BatteryDual2 sends `22 01` for the C710N only (it sends `22 09` to the XM5, which matches X5's 0x29) |
| 0x29 | LR_BATTERY_LEVEL_WITH_THRESHOLD | Low (alternative to 0x21) | X5 lists it; SC uses 0x21 before 0x29 if both appear |
| 0x2a | CRADLE_BATTERY_LEVEL_WITH_THRESHOLD | Medium | HG low-case notification is exactly SC's threshold logic (`c10/e.java` d(): low when level < threshold); GB sends `22 0a`; X5 |
| 0x22 | CRADLE_BATTERY_LEVEL_INDICATOR | Low (alternative to 0x2a) | case level without the alert |
| 0x23 | POWER_OFF | High | HG "Turning off the headset"; GB PowerOffFromPhone `24 03 01`; X5 |
| 0x25 | AUTO_POWER_OFF_WITH_WEARING_DETECTION | Medium-high | HG "Setting the wearing detection automatic power off function"; GB drives `26 05` / `28 05`; X5 |
| 0x24 | AUTO_POWER_OFF | Low (alternative) | plain time-based auto off |
| 0x50 | PRESET_EQ | High | HG Equalizer/CLEAR BASS plus custom; GB EqualizerWithCustomBands `56 00` with a 10-byte reply; X5 |
| 0x6b | MODE_NC_ASM_NOISE_CANCELLING_DUAL_AMBIENT_SOUND_MODE_LEVEL_ADJUSTMENT (type 17) | Medium-high | GB AmbientSoundControl2 sends `66 17` for the C710N; HG NC, Ambient with level and Voice focus; X5 lists the same |
| 0x70 | AUTO_NCASM (Adaptive Sound Control) | Medium | HG lists Adaptive Sound Control; X5 lists it |
| 0x90 | FIXED_MESSAGE (alerts) | Medium | needed for the multipoint and connection-mode confirmations; X5 |
| 0xa1 | PLAYBACK_CONTROLLER_WITH_CALL_VOLUME_ADJUSTMENT | High | HG play/pause/skip and "Adjusting the volume during music playback/phone call"; X5 over Classic |
| 0xb2 | GATT_CONNECTABLE (Service Link) | High | HG Service Link on/off; GB drives Service Link with `b6 10` / `b8 10`; X5 |
| 0xc1, 0xc2 | ACTION_LOG_NOTIFIER, TIME_SERIES_OPERATIONLOG_NOTIFIER | Medium | HG "Recording and displaying information on the usage of the headset"; X5 |
| 0xd1 | GENERAL_SETTING_1 = MULTIPOINT_SETTING | Medium (slot is a guess) | HG multipoint on/off; X5 keeps multipoint in a general setting (its slot d2, d1 is sidetone). The C710N has no sidetone setting in HG, so slot d1 is the guess |
| 0xe1 | CONNECTION_MODE_SOUND_QUALITY_CONNECTION_QUALITY (type 00) | Medium | HG two-option sound quality mode; no LDAC rules out 0xe3; no LE Audio rules out 0xe7 |
| 0xe2 | UPSCALING_AUTO_OFF (type 01) | Medium | HG DSEE on/off; GB AudioUpsampling `e6 01`; X5 |
| 0xed | UPSCALING_AUTO_OFF_WITH_STATUS_DISABLE_REASON (type 0b) | Low (alternative to 0xe2) | newer firmware may use it; SC prefers 0xe2 when both appear |
| 0xf1 | PLAYBACK_CONTROL_BY_WEARING_REMOVING_HEADPHONE_ON_OFF | High | HG IR sensors and pause/resume setting; X5 |
| 0xf3 | ASSIGNABLE_SETTING | High | HG "Changing the function of the touch sensor"; GB ButtonModesLeftRight `f6 03` and AmbientSoundControlButtonMode `fa 03`; X5 |
| 0xf9 | RESET_SETTINGS | High | HG "Initializing the headset" from the app; X5 |
| 0xfd | QUICK_ACCESS | High | HG Quick Access on double and triple tap; X5 |
| 0x10, 0x14, 0x15, 0x16, 0x17, 0x27 | concierge, BLE setup, tutorial, connection time, auto-reconnect, keep-alive | Low / guess | X5 lists them; app-platform functions of the same generation |
| 0x34 | FW_UPDATE_MTK_TRANSFER_WITHOUT_DISCONNECTION_AUTO_UPDATE | Low / guess | HG firmware update via the app; which FW_UPDATE variant is unknown, X5 uses 0x34 |
| 0xf2, 0xfc | Speak-to-Chat | Absent (high) | HG never mentions it; GB declares none |
| 0xf4 | VOICE_ASSISTANT_SETTINGS | Absent (guess) | HG's app list has no assistant choice; the right hold uses the phone's assistant |
| 0xf6, 0xff, 0xb0, 0xb6, 0xb7 | fit test, head gestures, Sound AR, head tracking | Absent (medium) | none in HG |
| 0x40..0x4f, 0xe7, 0x94 | LE Audio functions | Absent (medium-high) | SBC/AAC only; no LE Audio in HG |
| 0x68, 0x6d | auto NC, noise adaptation | Absent (medium) | HG has neither |

### 2.2 Table 2

| FT | Name | Confidence | Evidence |
|---|---|---|---|
| 0x42 | VOICE_GUIDANCE_SETTING_MTK_TRANSFER_..._SUPPORT_LANGUAGE_SWITCH_AND_VOLUME_ADJUSTMENT | Medium-high | HG on/off, language switch that takes up to 10 minutes (a language-pack transfer, which is what the MTK types do), and volume; X5 lists 0x42 |
| 0x41 + 0x43 | language switch + 5-step volume | Low (alternative) | |
| 0x30 or 0x32 | PAIRING_DEVICE_MANAGEMENT (with class of device for 0x32) | Medium | HG "Changing the device connected in a multipoint connection". SC fetches the type 02 capability even without these listed, and X5 answers `36 00` / `36 02` without listing them |
| 0xf2 | QUICK_ACCESS_EASY_SETTING | Low-medium | X5 lists it; the C710N has Quick Access |
| 0x50..0x55 | Safe listening | Absent (guess) | not in HG's app list |
| 0x60..0x64, 0x6f | LE Audio | Absent (medium-high) | no LE Audio |
| 0xf7, 0xf8 | Link auto switch | Absent (guess) | not in HG |

### 2.3 Connect replies

| Item | Value | Confidence |
|---|---|---|
| Protocol version | unknown; must be one SC accepts (`wv/e.java` list). The simulator can reuse the XM5's `03 00 30 18` | guess |
| Table bytes in RET_PROTOCOL_INFO | `00 00` (both tables listed), since voice guidance lives in table 2 | medium |
| Model name | `WF-C710N` | high (HG, GB) |
| Series byte | `50` CASUAL is the natural fit for the C line; not in any source | guess |
| Colour byte | ModelColor code; SC's code does not say which colours the C710N uses | unknown |

## 3. Variant each SunPods feature must use

| Feature | WF-1000XM5 (recorded) | WF-C710N (expected) | What SunPods must handle |
|---|---|---|---|
| Earbud battery | 0x29: `22 09` → 8 bytes with thresholds | 0x21: `22 01` → `23 01 Ll Lc Rl Rc` (6 bytes) | `readBattery` hard-codes `22 09`; pick 0x20 / 0x21 / 0x28 / 0x29 in SC's order |
| Case battery | 0x2a: `22 0a` → 5 bytes | 0x2a likely, 0x22 possible (`22 02` → 4 bytes) | try the listed one; show a low-case notice when level < threshold |
| Noise control | 0x6b → type 17 | 0x6b → type 17 (same layout) | read `60 17` for the real level range and whether VOICE is offered |
| Noise cycle | assignable ext param, preset byte reported as 00 | same; GB writes preset 00 | echo the preset from `fb 03`; `setNoiseCycle` writes a fixed 0x35 today |
| Adaptive Sound Control | 0x70, applicable `06 10 20` | 0x70, likely `06 10` (no Speak-to-Chat) | app-side activity logic, then `78 00 onoff n entries` |
| EQ | 0x50, 6 values (CLEAR BASS + 5 bands), flat 0x0a | 0x50, same shape per GB's 10-byte reply | take band count and steps from `51 00`, band order from `5b 00` |
| DSEE | 0xe2, capability type 02 DSEE_HX_AI | 0xe2 (or 0xed), capability type 01 DSEE | 3-byte param `e7 01 v`; label from capability |
| Sound quality mode | 0xe7 → type 05 (with LE choice) | 0xe1 → type 00 | `readPriority` probes 00 then 02; use the listed FT instead: 0xe1 → `e6 00`, 0xe7 → `e6 05` |
| Codec | 0x12 | 0x12 (SBC 01 or AAC 02 only) | none |
| L/R link status | 0x11 | 0x11 | none |
| Pause when removed | 0xf1 → type 01 | 0xf1 → type 01 | none |
| Auto power off | 0x25, capability `21 05 02 10 11` (when removed / off) | 0x25, capability probably time elements (GB: 15 min / 30 min / 1 h / 3 h / off) | build the options from `21 05`; today's on/off switch (0x10 / 0x11) may not apply |
| Touch functions | 0xf3, presets 0x35 / 0x20 / 0xff per side | 0xf3, presumably the same presets (section 4) | build the menu from `f1 03`, never from fixed codes |
| Quick Access | 0xfd | 0xfd | Service Link (0xb2) must be on first |
| Service Link | 0xb2 (Classic list only) | 0xb2 | new for SunPods |
| Speak-to-Chat | 0xfc → type 0c | none | hide |
| Voice assistant choice | 0xf4 | probably none | hide unless listed |
| Head gestures, fit test | 0xff, 0xf6 | none | hide |
| Multipoint | general setting d2 MULTIPOINT_SETTING (Classic only) | general setting, slot unknown (guess d1) | find the slot by its key, as `readGeneral` already does |
| Voice prompts | T2 0x42: type 01 + volume type 20 | T2 0x42 (likely) | same code; language change needs the transfer flow |
| Paired devices | answers T2 `36 00` / `36 02` (not listed) | T2 0x30 / 0x32 | same code |
| Safe listening | T2 0x53, 0x54 | none (guess) | hide unless listed |
| LE Audio switch | T1 0x42, 0xe7; T2 0x60, 0x61, 0x63, 0x6f | none | hide; never open the LE control channel |
| Playback and volume | 0xa1 (Classic), 0xa2 (LE) | 0xa1 | volume steps from `a1 01` |
| Reset | 0xf9 | 0xf9 | none |
| Power off | 0x23 | 0x23 | none |
| Alerts | 0x90, 0x92, 0x93, 0x94 | 0x90 | answer `99 00 msg at` with `98 00 msg 01` |

## 4. WF-C710N test vectors

Each RET is built from the SC layout in `variants.md`. Values (levels, presets, strings) are illustrative; where a byte depends on an unknown, the note says so. GET payloads are what SC or Gadgetbridge send. Frame type is 0x0c unless marked T2 (0x0e).

| Feature | GET (or SET) | Earbuds reply | Notes |
|---|---|---|---|
| Protocol info | `00 00` | `01 00 03 00 30 18 00 00` | version copied from the XM5 (guess) |
| Model name | `04 01` | `05 01 08 57 46 2d 43 37 31 30 4e` | "WF-C710N" |
| Firmware | `04 02` | `05 02 05 31 2e 30 2e 35` | "1.0.5", illustrative |
| Series, colour | `04 03` | `05 03 50 01` | CASUAL, black: both guesses |
| Support functions T1 | `06 00` | `07 00 1d 10 ff 11 ff 12 ff 13 ff 14 ff 15 ff 16 ff 17 ff 21 ff 23 ff 25 36 27 ff 2a ff 34 3f 50 11 6b 08 70 01 90 ff a1 12 b2 43 c1 ff c2 ff d1 3b e1 1a e2 1b f1 38 f3 26 f9 41 fd 2b` | 29 functions from section 2.1 (High and Medium rows plus the platform guesses); order bytes copied from the XM5 where it has the same function |
| Support functions T2 | T2 `06 00` | T2 `07 00 03 42 3d 32 ff f2 ff` | voice guidance 0x42, pairing management 0x32, QA easy setting 0xf2 |
| Battery L/R | `22 01` | `23 01 46 00 3c 01` | left 70 % not charging, right 60 % charging |
| Battery notify | (unprompted) | `25 01 45 00 3c 01` | |
| Case | `22 0a` | `23 0a 50 00 1e` | 80 %, not charging, threshold 30 % (the XM5's case threshold) |
| Case, alternative | `22 02` | `23 02 50 00` | if 0x22 is listed instead |
| Power off | `24 03 01` | (link drops) | |
| Auto power off capability | `20 05` | `21 05 05 04 01 02 03 11` | 15, 30, 60, 180 min, off: from GB's comment, element set is a guess |
| Auto power off status | `22 05` | `23 05 00` | |
| Auto power off | `26 05` | `27 05 04 04` | active 15 min, last time 15 min |
| Auto power off set | `28 05 11 04` | ACK, then `29 05 11 04` | off, remember 15 min |
| NC capability | `60 17` | `61 17 02 00 00 14 01 01 00 14 01` | NORMAL and VOICE, each 0..20 step 1: range is a guess, read it |
| NC status | `62 17` | `63 17 00` | usable |
| NC read | `66 17` | `67 17 01 01 00 00 14` | NC on |
| NC set Ambient + Voice focus | `68 17 01 01 01 01 0a` | ACK, then `69 17 01 01 01 01 0a` | level 10 |
| NC set Off | `68 17 01 00 00 00 0a` | ACK, then `69 17 01 00 00 00 0a` | |
| NC slider moving | `68 17 00 01 01 00 07` | ACK, then `69 17 00 01 01 00 07` | VCS 00 = still changing |
| Noise cycle | `fa 03` | `fb 03 01 00 01 00 01` | cycle NC, Ambient, Off |
| Noise cycle set | `fc 03 01 00 01 00 02` | ACK, then `fd 03 01 00 01 00 02` | NC and Ambient only |
| Adaptive Sound Control | `70 00` | `71 00 02 06 10` | applies to NC type 17 and EQ (guess, no Speak-to-Chat) |
| EQ capability | `50 00 01` | `51 00 06 15 0c 00 00 10 00 11 00 12 00 13 00 14 00 15 00 16 00 17 00 a0 00 a1 00 a2 00` | 6 bands, 21 steps, 12 presets with empty names (names may be sent; not recorded) |
| EQ bands | `5a 00` | `5b 00 06 10 00 01 01 01 90 01 03 e8 01 09 c4 01 18 9c 01 3e 80` | CLEAR BASS, 400 Hz, 1 kHz, 2.5 kHz, 6.3 kHz, 16 kHz (GB's band keys; illustrative) |
| EQ status | `52 00` | `53 00 00` | |
| EQ read | `56 00` | `57 00 10 06 0a 0a 0a 0a 0a 0a` | Bright, flat |
| EQ preset | `58 00 16 00` | ACK, then `59 00 16 06 0a 0a 0a 0a 0a 0a` | Bass Boost, bands kept |
| EQ custom | `58 00 a0 06 0d 0b 0a 0a 0c 0e` | ACK, then `59 00 a0 06 0d 0b 0a 0a 0c 0e` | CLEAR BASS +3, bands +1 0 0 +2 +4 |
| DSEE capability | `e0 01` | `e1 01 01` | DSEE |
| DSEE status | `e2 01` | `e3 01 00` | |
| DSEE read | `e6 01` | `e7 01 00` | off |
| DSEE set | `e8 01 01` | ACK, then `e9 01 01` | |
| DSEE, 0xed variant | `e2 0b` | `e3 0b 00 00` | enabled, no disable reasons |
| DSEE indicator | `12 03` | `13 03 01 01` | DSEE, working |
| Sound quality status | `e2 00` | `e3 00 00` | |
| Sound quality read | `e6 00` | `e7 00 01` | High Reliability (HG default) |
| Sound quality set | `e8 00 00` | ACK, then `e9 00 00` | an alert (for example `99 00 1c 01`) may come first: guess |
| Codec | `12 02` | `13 02 02` | AAC |
| L/R link | `12 01` | `13 01 01 01` | both connected |
| Pause when removed | `f6 01` | `f7 01 00` | on |
| Pause set | `f8 01 01` | ACK, then `f9 01 01` | off |
| Pause status | `f2 01` | `f3 01 00` | |
| Touch capability | `f0 03` | `f1 03 02 00 00 35 03 35 04 01 01 43 02 44 22 10 03 24 00 02 04 01 02 03 04 20 05 00 00 20 01 21 02 22 21 30 03 24 ff 01 00 00 00 01 00 20 03 35 04 01 01 43 02 44 22 10 03 23 00 02 04 01 02 03 04 20 05 00 00 20 01 21 02 22 21 30 03 23 ff 01 00 00 00` | the XM5 recording, whose gestures match HG's C710N defaults; left default preset 0x35, right 0x20 |
| Touch read | `f6 03` | `f7 03 02 00 20` | left noise control (reported as 00, as the XM5 does), right playback |
| Touch set | `f8 03 02 20 00` | ACK, alert `99 00 0f 01`, app answers `98 00 0f 01`, then `f9 03 02 20 00` | moves playback from right to left; the XM5 asks 0f when playback leaves a side (`SonyFeatures.setTouch` comment); for the C710N a guess |
| Quick Access capability | `f0 0d` | `f1 0d 00 00 02 01 00 00 02 00 00` | the XM5 recording: L/R key, touch, double and triple tap |
| Quick Access read | `f6 0d` | `f7 0d 02 00 00` | nothing assigned |
| Service Link capability | `b0 10` | `b1 10 01 00` | exclusive with FW update |
| Service Link status | `b2 10` | `b3 10 00` | |
| Service Link read | `b6 10` | `b7 10 01` | off |
| Service Link set | `b8 10 00` | ACK, then `b9 10 00` | on |
| Multipoint title | `d0 d1 01` | `d1 d1 00 01 12 4d 55 4c 54 49 50 4f 49 4e 54 5f 53 45 54 54 49 4e 47 00` | "MULTIPOINT_SETTING", empty summary; slot guessed |
| Multipoint status | `d2 d1` | `d3 d1 00` | |
| Multipoint read | `d6 d1` | `d7 d1 00 00` | on |
| Multipoint off | `94 00 00`, then `d8 d1 00 01` | `99 00 07 01`, app answers `98 00 07 01`, then `d9 d1 00 01` | message 07 DISCONNECT_CAUSED_BY_CHANGING_MULTIPOINT is the SC code for this; which code the C710N sends is a guess |
| Reset settings | `f8 09 00` | ACK, then `f9 09 00` | settings only, success |
| Playback capability | `a0 01` | `a1 01 1f 10` | 31 music steps, 16 call steps (copied from the XM5, guess) |
| Playback status | `a2 01` | `a3 01 00 01 00` | enabled, playing, music |
| Pause | `a4 01 00 01` | ACK, then `a5 01 00 02 00` | |
| Music volume | `a6 20` | `a7 20 0b` | |
| Call volume | `a6 21` | `a7 21 05` | |
| Volume set | `a8 20 10` | ACK, then `a9 20 10` | |
| Alerts on | `94 00 00` | ACK | SC sends it at every connection |
| Voice guidance capability | T2 `40 01` | T2 `41 01 03 00 00 00 01 0f 01 02 03 04 05 06 07 08 09 0a 0b 0d 0f 10 f0` | the XM5's 15 languages: the C710N list is not documented anywhere |
| Voice guidance status | T2 `42 01 00` | T2 `43 01 00 00` | on/off switch usable |
| Voice guidance read | T2 `46 01` | T2 `47 01 00 01` | on, English |
| Voice guidance set | T2 `48 01 01` | ACK, then T2 `49 01 01` | off (SC's 3-byte form) |
| Voice guidance volume | T2 `46 20` | T2 `47 20 00` | middle of -2..+2 |
| Volume set | T2 `48 20 01 00` | ACK, then T2 `49 20 01` | one step louder, no test phrase |
| Pairing capability | T2 `30 02` | T2 `31 02 08 02 00` | 8 paired, 2 connected |
| Pairing status | T2 `32 02` | T2 `33 02 00 00` | normal (not discoverable) |
| Device list | T2 `36 02` | T2 `37 02 01` + `41 41 3a 42 42 3a 43 43 3a 44 44 3a 45 45 3a 30 31` + `01 5a 02 0c 05 50 69 78 65 6c 01` | one phone "Pixel" AA:BB:CC:DD:EE:01, connected first, class of device 5a020c, holds playback |

## 5. WF-1000XM5 test vectors (recorded)

From `SimProfiles.XM5` (real earbuds, firmware 6.1.0, addresses replaced) and SunPods' code. Lines marked "cut" are truncated in the recording.

| Feature | GET (or SET) | Earbuds reply | Notes |
|---|---|---|---|
| Protocol info | `00 00` | `01 00 03 00 30 18 00 00` | version 0x03003018, both tables |
| Model, FW, colour | `04 01`, `04 02`, `04 03` | `05 01 0a 57 46 2d 31 30 30 30 58 4d 35`; `05 02 05 36 2e 31 2e 30`; `05 03 30 01` | "WF-1000XM5", "6.1.0", PREMIUM black |
| Support functions T1, Classic | `06 00` | `07 00 2e 10 ff 12 ff 13 ff 14 ff 15 ff 16 ff 17 ff 23 ff 25 36 11 ff 27 ff 29 ff 2a ff 92 ff 50 11 6b 08 70 01 90 ff a1 12 c1 ff c2 ff e2 1b f1 38 fc 0d 93 ff f6 33 f9 41 d1 3b 94 ff 40 2e 42 3a 44 ff ff 2d 19 ff f3 26 e7 1a d2 20 fd 2b b6 1d b0 42 b2 43 b8 1f b7 15 34 3f f4 23 f5 24` | 46 functions |
| Support functions T1, LE Audio | `06 00` | `07 00 2e 10 ff 12 ff 13 ff 14 ff 15 ff 16 ff 17 ff 23 ff 25 36 11 ff 27 ff 29 ff 2a ff 92 ff 40 2e 42 3a 44 ff 50 11 6b 08 70 01 90 ff a2 13 c1 ff c2 ff e2 1b f1 38 fc 0d 93 ff f6 33 f9 41 d1 3b ff 2d 94 ff f3 26 b7 15 47 46 4c 48 4b ff 48 49 4a ff 49 ff e7 1a 34 3f f4 23 4e 47 4f ff` | over LE: a2 instead of a1, the "not with LE" entries 0x47..0x4c, 0x4e, 0x4f, and no d2 / fd / b0 / b2 / b6 / b8 / f5 / 19 |
| Support functions T2 | T2 `06 00` | T2 `07 00 09 42 3d 53 ff 54 ff f1 ff 60 ff 61 ff f2 ff 63 ff 6f 4a` | |
| Battery L/R | `22 09` | `23 09 5b 00 64 00 64 64` | 91 %, 100 %, thresholds 100 / 100 |
| Battery notify | (unprompted) | `25 09 52 00 64 00 64 64` | |
| Case | `22 0a` | `23 0a 42 00 1e` | 66 %, threshold 30 % |
| Codec | `12 02` | `13 02 00` | unsettled at the time of recording |
| DSEE indicator | (unprompted) | `15 03 02 00` | DSEE_HX_AI, off |
| L/R link | (unprompted) | `15 01 01 00` | left connected, right not |
| Auto power off capability | `20 04`, `20 05` | `21 04 05 04 01 02 03 11`; `21 05 02 10 11` | type 05: when removed, off |
| Auto power off | `26 04`, `26 05` | `27 04 10 00`; `27 05 11 00` | type 05 off. The XM5 lists only 0x25 yet answers type 04 too; 10 is not a valid type 04 element, so SC would reject that reply |
| NC read | `66 17` | `67 17 01 01 01 00 14` | Ambient, normal, level 20 |
| NC notify | (unprompted) | `69 17 01 01 00 01 14` | NC (byte 5 is VOICE but unused in NC) |
| NC set (SunPods) | `68 17 01 en amb focus level` | ACK, then `69 17 ...` | `SonyBatteryClient.setAmbientSoundMode` |
| Adaptive Sound Control | `70 00` | `71 00 03 06 10 20` | NC type 17, EQ, Speak-to-Chat |
| EQ read | `56 00` | `57 00 00 06 0a 0a 0a 0a 0a 0a` | Off, flat |
| EQ notify | (unprompted) | `59 00 14 06 0a 10 0e 0c 0d 09` | Vocal |
| DSEE | `e0 01`, `e2 01`, `e6 01` | `e1 01 02`; `e3 01 00`; `e7 01 00` | DSEE_HX_AI, usable, off |
| DSEE notify | (unprompted) | `e9 01 01` | on |
| Connection mode | `e6 05` (SC), `e6 00` / `e6 02` (SunPods) | `e9 05 01 00` (notify); `e7 00 02`, `e7 02 02` | the XM5 lists 0xe7; type 00 and 02 answers carry 02, which SunPods' ConnectionPriority cannot map |
| Touch capability | `f0 03` | see the C710N row above (same bytes) | |
| Touch notify | (unprompted) | `f9 03 02 00 00` | both sides noise control, reported as preset 00 |
| Noise cycle | `fa 03` | `fb 03 01 00 01 00 03` | NC / Off; notify `fd 03 01 00 01 00 03` |
| Pause when removed | `f6 01` | `f7 01 01` | off |
| Speak-to-Chat | `f6 0c`, `fa 0c`, `f2 0c` | `f7 0c 00 01`; `fb 0c 00 01`; `f5 0c 00 01` (notify) | on, preview off; auto sensitivity, mid timeout; enabled, active |
| Voice assistant | `f0 04` | `f1 04 03 02 31 ff` | assignable sensor; Google, none. Notify `f9 04 30` |
| Quick Access | `f0 0d`, `f6 0d` | `f1 0d 00 00 02 01 00 00 02 00 00`; `f7 0d 02 00 00` | |
| Head gestures | `f6 0f` | `f7 0f 00` | on |
| Fit test capability | `f0 06` | `f1 06 07 01 01 04 00 01 02 03` | |
| Reset | `f8 09 00` | `f9 09 00` | |
| Multipoint title | `d0 d2 01` | `d1 d2 00 01 12 4d 55 4c 54 49 50 4f 49 4e 54 5f 53 45 54 54 49 4e 47 29 ...` | "MULTIPOINT_SETTING", summary cut |
| Sidetone title | `d0 d1 01` | `d1 d1 00 01 10 53 49 44 45 54 4f 4e 45 5f 53 45 54 54 49 4e 47 18 ...` | "SIDETONE_SETTING" |
| General settings | `d6 d1`, `d6 d2` | `d7 d1 00 01`; `d7 d2 00 01` | both off |
| LE Audio setting | `46 0c`, `42 0c` | `47 0c` (cut); `43 0c 00` | notify `49 0c` (cut) |
| LEA addresses | `40 00` | `41 00` + 3 x 17-character addresses | |
| Service Link | `b0 10`, `b6 10` | `b1 10 00`; `b7 10 00` | on |
| Playback | `a0 01`, `a2 01` | `a1 01 1f 10`; `a3 01 00 01 00` | |
| Volumes | `a6 20`, `a6 21`, `a6 30`, `a6 31` | `a7 20 0b`; `a7 21 05`; `a7 30 0b 01`; `a7 31 05 01` | |
| Alert (flexible) | (unprompted, LE switch) | `99 06 0d 09 18 0f 0b 09 0c 17 15 19 12 01` | answer `98 06 0d 01` |
| Alert (fixed) | (unprompted, LE off) | `99 00 2e ..` (cut) | answer `98 00 2e 01` |
| Voice guidance capability | T2 `40 01` | T2 `41 01 03 00 00 00 01 0f 01 02 03 04 05 06 07 08 09 0a 0b 0d 0f 10 f0` | X = 03, switch supported, 15 languages |
| Voice guidance | T2 `46 01`, T2 `46 20` | T2 `47 01 01 01`; T2 `47 20 00` | off, English; volume 0 |
| Safe listening | T2 `50 03`, T2 `52 03`, T2 `5a 03` | T2 `51 03 3c 38 6d 43 80 01 0f`; T2 `53 03 00 00 01 02 9c 4b 34 00 ...`; T2 `5b 03 31 03` | live level 49 dB |
| Pairing capability | T2 `30 00`, T2 `30 02` | T2 `31 00 08 02 00`; T2 `31 02 08 02 00` | answered though not listed |
| Link auto switch | T2 `f6 0a`, T2 `f2 0a` | T2 `f7 0a 00 00 00` + "02:00:00:00:00:14"; T2 `f3 0a 00` | on, no speaker id |

## 6. Gadgetbridge cross-check and where it disagrees

| Point | Gadgetbridge | SunPods recording / SC | Weight |
|---|---|---|---|
| XM5 noise control type | `66 15` (AmbientSoundControl) | the XM5 lists 0x6b and answers on 17; SunPods' code notes an unknown type is acknowledged and ignored | GB is wrong for the XM5 here, so GB is not proof by itself |
| C710N noise control type | `66 17` (AmbientSoundControl2) | fits 0x6b; GB added the "2" variants for the C710N only | supports 0x6b |
| C710N battery | `22 01` (BatteryDual2) | fits 0x21; GB's XM5 value `22 09` matches the recording | supports 0x21 |
| Case battery | `22 0a` for both models | GB uses 0a for every V2 model, so it is not C710N-specific | weak |
| Noise cycle preset byte | 00 for the C710N, 35 for the XM5 | the XM5 itself reports 00 in `fb 03` | echo what the earbuds report |
| Speak-to-Chat config | `fc 0c sens timeout` | matches SC type 0c (4 bytes) | agrees |
| Protocol version | V2 when the protocol reply is 8 bytes | every SC table 1 RET_PROTOCOL_INFO is 8 bytes | agrees |

## 7. Open points to settle on a real C710N

| Priority | Probe | Settles |
|---|---|---|
| 1 | `00 00`, then `06 00` on both frame types | the whole of section 2 |
| 2 | `60 17` | ambient level range and Voice focus |
| 3 | `20 05` (or `20 04`) | auto power off options |
| 4 | `50 00 01` and `5a 00` | EQ band count, steps, band order, preset names |
| 5 | `f0 03`, `f6 03`, `fa 03` | touch presets, and whether the left key reports 00 or 35 |
| 6 | `22 01` / `22 09`, `22 0a` / `22 02` | battery variants |
| 7 | `d0 d1 01` .. `d0 d4 01` | which general-setting slot holds multipoint |
| 8 | T2 `40 01` / `40 02` | voice guidance variant and language list |
