# WH-1000XM5 profile (reconstructed)

Best evidence-based picture of what Sony's WH-1000XM5 over-ear headphones (2022) announce and answer, written for adding them to SunPods next to the WF-1000XM5 and WF-C710N. Nobody here has the headphones, so this is not a recording. A few bytes were seen on real units by other projects (marked "seen"); the rest is built from the layouts in `variants.md` (Sound Connect's serializers) and the evidence below. Research date: 23 Nov 2026.

Evidence used:

| Code | Source | What it gives |
|---|---|---|
| GB | Gadgetbridge `SonyWH1000XM5Coordinator.java`, `SonyProtocolImplV2.java`, `SonyHeadphonesProtocol.java`, `SonyHeadphonesSupport.java` (codeberg.org/Freeyourgadget/Gadgetbridge, master) | Protocol version detection, UUIDs, the bytes GB sends to a WH-1000XM5 |
| GB-PR | Gadgetbridge PR 6517 "Fix XM5 Connect Two Devices, add multipoint" (tested on a WH-1000XM5) | Multipoint lives in general setting `d2` on this model; multipoint device list bytes |
| GB-5934 | Gadgetbridge issue 5934 "Add missing Sony WH-1000XM5 features" | A user's walk through the app: what GB already drives on a WH-1000XM5 and the app's options |
| HL | `k0nnect/harald`, `devices/wh-1000xm5/protocol.md` (unit on firmware 2.5.1) | Seen bytes: handshake reply, `66 17`, `68 17`, `22 00`, framing |
| BL | BudsLink `deviceConfigs/WH-1000XM5.js`, `sonySocketV2.js`, feature matrix (maniacx.github.io/BudsLink/sony), issue 73 | Which messages a tested client uses for this model; SDP UUID list, PnP id, class of device |
| MOS | `mos9527/SonyHeadphonesClient` (libmdr), `docs/device-support/WH-1000XM5.md`, issue 4, PR 20 | User test results; libmdr's capability-driven paths |
| VY | `vyomjain6904/sony-headphones-linux`, `docs/COMPATIBILITY.md` (WH-1000XM5 firmware 2.5.1) | Capabilities the headphones reported, reversible write tests, raw volume reading |
| HG | Sony help guide, helpguide.sony.net/mdr/wh1000xm5/v1/en/ (pages cited by TP number) | Features, defaults, buttons, sensors, battery, pairing |
| HGW | WF-1000XM5 help guide, helpguide.sony.net/mdr/2963/v1/en/ | The earbuds' feature list, for comparison |
| JP | sony.jp `headphone/products/WH-1000XM5/` and `spec.html` | Official specs, colours with swatch values, product pictures |
| X5 | SunPods' WF-1000XM5 recording (`SimProfiles.kt`) and code | What SunPods sends today |
| SC | Sound Connect layouts via `variants.md` | Message layouts and FunctionType mapping |

Confidence: **High** = a seen byte, or two independent sources agree. **Medium** = one source, or the feature is certain but the variant is not. **Low / guess** = inferred from the WF-1000XM5 or from absence.

## 1. Protocol, transport and identity

| Question | Answer | Confidence | Source |
|---|---|---|---|
| Protocol | MDR **v2** (table 1 on frame `0x0c`, table 2 on `0x0e`), same framing and checksum as the WF-1000XM5 | High | GB picks v2 when the handshake reply is 8 bytes; HL |
| RFCOMM UUID | `956c7b26-d49a-4ba8-b03f-b17d393cb6e2`. The v1 UUID `96cc203e-...` is not served | High | GB `SonyHeadphonesSupport`; HL ("older sony models use a different uuid, which the xm5 does not serve"); BL issue 73 SDP list has only the v2 one |
| RFCOMM channel | 9 on one unit; read it from SDP, never hard-code | Medium | HL |
| Handshake `00 00` | Firmware 1.1.3: `01 00 03 00 00 00 00 00`. Firmware 2.5.1: `01 00 03 00 20 16 00 00` (seen) | High | GB comment in `SonyHeadphonesProtocol.java`; HL |
| What the version word means | It changes with firmware (0x03000000 then 0x03002016), so it is not a model id. MOS PR 20 guessed "WH1000XM5 = 0x2016" from one firmware; the WF-1000XM5 reports 0x1004 on 2.0.1 (GB) and 0x3018 on 6.1.0 (X5), so that guess does not hold | High | GB, HL, X5 |
| Table bytes | `00 00` (both support-function lists offered) | High | both seen replies end `00 00` |
| Classic name | `WH-1000XM5` | High | HG TP1000534535; BL issue 73 |
| BLE name | `LE_WH-1000XM5` may also appear in a phone's list (Sony: pick the plain name when both show) | High | HG TP1000534540, TP1000534541 |
| LE Audio | Not offered. Bluetooth 5.2; codecs SBC, AAC, LDAC; no LC3 anywhere in HG or JP specs. The `LE_` name is the BLE side (Fast Pair, Service Link), not LE Audio | Medium-high | HG TP1000541014, JP spec |
| Device ID (PnP) | Sony `v054C`, product `0x0DF0` (one unit: version `0x0202`). The WF-1000XM5 is `0x0E63` | Medium | BL `WH-1000XM5.js` / `WF-1000XM5.js` `modaliasPrefix`; BL issue 73 |
| Class of device | `0x240404` (audio, wearable headset) | Medium | BL issue 73 |
| Firmware history worth knowing | 2.0.2 (Jul 2023) added LDAC together with multipoint, and head tracking; 2.3.1 added Auto Switch with speakers; 2.5.1 is the latest any source tested | Medium | Notebookcheck (2.0.2), MOS issue 4 comment (2.3.1), HL and VY (2.5.1) |
| Variant | `WH-1000XM5SA` ships with a carrying pouch instead of the hard case; same headphones | High | HG TP1000534515 |

## 2. Reconstructed support-function lists

No source published a full `07 00 ...` reply for this model. The lists below come from HG's app feature list (TP1000534533), what other clients drive successfully, and the WF-1000XM5 recording.

### 2.1 Table 1

| FT | Name | Confidence | Evidence |
|---|---|---|---|
| 0x12 | CODEC_INDICATOR | High | HG "View the Bluetooth codec in use"; VY read LDAC |
| 0x13 | UPSCALING_INDICATOR | Medium | HG DSEE Extreme status; X5 lists it |
| 0x20 | BATTERY_LEVEL_INDICATOR | High | HL `22 00` answered (seen); GB `BatterySingle`; VY "main battery" |
| 0x28 | BATTERY_LEVEL_WITH_THRESHOLD | Low (alternative to 0x20) | not seen; BL asks `22 08` first if listed |
| 0x23 | POWER_OFF | High | HG "Turn off the headset"; GB `PowerOffFromPhone`; MOS chart "Power Off ✅" |
| 0x25 | AUTO_POWER_OFF_WITH_WEARING_DETECTION | High | HG TP1000534525, TP1000539062; GB and BL drive `26 05` / `28 05` |
| 0x50 | PRESET_EQ | High | GB 10-byte `57 00` reply; GB-5934 presets and bands; BL "Equalizer Six Bands" tested |
| 0x69 | AMBIENT_SOUND_CONTROL_MODE_SELECT (NC/AMB toggle, type 30) | High | BL drives `66 30` / `68 30` and marks it tested; libmdr only uses this path for the NC/AMB button and MOS reports "NC/AMB Button Setting ✅" |
| 0x6b | MODE_NC_ASM_NC_DUAL_ASM_LEVEL (type 17) | High | HL: `66 17` answered, `66 15` acked but never answered (fw 2.5.1); VY writes passed |
| 0x70 | AUTO_NCASM (Adaptive Sound Control) | Medium | HG item 25; X5 |
| 0x90 | FIXED_MESSAGE (alerts) | Medium | multipoint and assistant changes need confirmations; X5 |
| 0xa1 | PLAYBACK_CONTROLLER_WITH_CALL_VOLUME_ADJUSTMENT | Medium-high | HG volume during music and calls; HL saw `a5 01 ...` (type 01 status); VY volume writes |
| 0xb2 | GATT_CONNECTABLE (Service Link) | High | HG item 16 and voice guidance text |
| 0xb8 | INTEGRATED_AUTO_PLAY | Low | HG item 32 "Specify Auto Play settings"; X5 lists 0xb8 |
| 0xd1 | GENERAL_SETTING_1 = TOUCH_PANEL_SETTING (slot is a guess) | Medium (feature) / Low (slot) | HG item 7; MOS "Touch Sensor Enable/Disable ✅" |
| 0xd2 | GENERAL_SETTING_2 = MULTIPOINT_SETTING | High | GB-PR: "Connect Two Devices uses type 0xd2 on the WH-1000XM5"; same slot as X5 |
| 0xe2 | UPSCALING_AUTO_OFF, capability type 02 DSEE_HX_AI (DSEE Extreme) | High | HG TP1000539063; BL "DSEE" tested; VY DSEE write passed |
| 0xe3 | CONNECTION_MODE_..._WITH_LDAC_STATUS (type 02) | Medium | HG sound quality mode with LDAC; GB V2 drives LDAC-or-stable as `e6 02` / `e8 02`; 0xe1 (type 00) is the alternative |
| 0xf1 | PLAYBACK_CONTROL_BY_WEARING | High | HG TP1000539062; GB `f6 01` |
| 0xf4 | VOICE_ASSISTANT_SETTINGS | Medium | HG item 4; GB-5934 lists phone assistant, Google, Alexa, none; GB drives `f6 04` / `f8 04` |
| 0xf5 | VOICE_ASSISTANT_WAKE_WORD_ON_OFF | Medium | HG item 5 "Amazon Alexa wake word on or off" |
| 0xf9 | RESET_SETTINGS | High | HG item 15 |
| 0xfc | SMART_TALKING_MODE_TYPE2 (Speak-to-Chat, type 0c) | High | GB and BL drive `f6 0c` / `fa 0c` 4-byte config; VY writes passed |
| 0xfd | QUICK_ACCESS | High | HG TP1000562272; GB `f6 0d` / `f8 0d 02 dd tt`; GB-5934 "implemented" |
| 0x10, 0x14..0x17, 0x27, 0x34 | concierge, BLE setup, tutorial, connection time, auto-reconnect, keep-alive, FW update | Low / guess | X5 lists them; same app platform |
| 0xb0, 0xb6, 0xb7 | Sound AR / head tracking related | Unknown | GB-5934 mentions "Sound AR" in the app; firmware 2.0.2 added head tracking; nothing ties bytes to them |
| 0x11 | CONNECTION_STATUS (L/R link) | Absent (high) | earbud-only |
| 0x21, 0x29, 0x22, 0x2a | L/R and case batteries | Absent (high) | one battery, no case battery |
| 0xf3, 0xfe | ASSIGNABLE_SETTING (per-side touch) | Absent (medium) | HG offers only touch panel on/off; NC/AMB button uses 0x69 and 0xfd |
| 0xf6 | WEARING_STATUS_DETECTOR (fit test) | Absent (high) | earbud-only |
| 0xff | HEAD_GESTURE | Absent (medium) | not in HG |
| 0xc1, 0xc2 | usage logs | Absent (guess) | HGW lists "Record and display information on the usage"; HG for the WH does not |
| 0x40..0x4f, 0xe7, 0x94 | LE Audio functions | Absent (medium-high) | no LE Audio |
| 0x6d, 0x68 | noise adaptation, auto NC | Absent (medium) | type 17 seen; no wind or auto-ambient option in HG |

### 2.2 Table 2

| FT | Name | Confidence | Evidence |
|---|---|---|---|
| 0x41 | VOICE_GUIDANCE_..._SUPPORT_LANGUAGE_SWITCH (no volume) | Medium | HG: language change takes about 20 minutes (a language-pack transfer); HG has no voice guidance volume (HGW does: "Set the voice guidance volume"); MOS marks "Voice Guidance Volume ❌" for this model |
| 0x42 | as 0x41 plus volume | Low (alternative) | X5 lists it; GB reads `46 20` for every model with voice notifications |
| 0x30 / 0x32 | PAIRING_DEVICE_MANAGEMENT | Medium-high | HG multipoint device change; GB-PR drives `36 00`, `3c 00`, `34 00`; VY "paired-device management, pairing" |
| 0x50 / 0x52 | SAFE_LISTENING_HBS_1 / HBS_2 (headband) | Medium (feature) / Low (which) | HG item 30; VY "safe listening"; MOS "Sound Pressure ✅". HBS is the headband variant of the TWS types the earbuds use |
| 0xf2 | QUICK_ACCESS_EASY_SETTING | Low | X5 lists it |
| 0xf8 | LINK_AUTO_SWITCH_FOR_HEADSETS | Low | HG Auto Switch (firmware 2.3.1 and later) |
| 0x53, 0x54 | SAFE_LISTENING_TWS_2, safe volume | Absent (guess) | X5 earbud types |
| 0x60..0x64, 0x6f | LE Audio | Absent (medium-high) | no LE Audio |

## 3. Message layouts, and how they differ from what SunPods sends the WF-1000XM5

Frame type `0x0c` unless marked T2 (`0x0e`). "SunPods today" is what `SonyFeatures.kt`, `SonyBatteryClient.kt`, `VoicePrompts.kt` and `EarbudExtras.kt` send to the WF-1000XM5.

| Feature | GET → reply | SET → notify | Confidence, source | SunPods today (WF-1000XM5) and what changes |
|---|---|---|---|---|
| Model name | `04 01` → `05 01 0a` "WH-1000XM5" | | High (HG name; SC layout) | Same request. `EarbudModel` matches `WF[- ]?1000XM5` only, so a WH is "not supported" and never picked: add `WH[- ]?1000XM5` and `LE_WH-1000XM5` |
| Firmware | `04 02` → `05 02 05` "2.5.1" | | High (SC; HL, VY firmware) | Same |
| Series, colour | `04 03` → `05 03 30 cc` | | Series 30 PREMIUM: guess (X5 is 30). Colour: see section 6 | Same request; new art mapping |
| Battery | `22 00` → `23 00 lvl chg` (4 bytes), notify `25 00 lvl chg`. Seen: `23 00 3e 00` (62 %, not charging) | | High (HL seen; GB `BatterySingle`) | **Breaks today.** `readBattery` sends `22 09` (or `22 01`) and waits for a 6-byte reply, then throws "Earbud battery was not reported". Use 0x20 (`22 00`), or 0x28 (`22 08` → `23 08 lvl chg thr`) if listed. No case query: with the list read, `caseType` is already null |
| Charging flag | `chg`: 00 not charging, 01 charging, 02 unknown, 03 charged | | High (SC; HL) | In practice always 00 over Bluetooth: charging turns the headset off and no operation is possible while connected to power (HG TP1000534502). Charge state must be inferred across reconnects |
| Noise control read | `66 17` → `67 17 vcs on mode voice level`, e.g. `67 17 01 01 00 00 14` | | High (HL seen) | Same as the WF-1000XM5 (0x6b → type 17). `66 15` is acked but unanswered (HL) |
| Noise control set | | `68 17 01 01 00 00 14` (NC, seen working) → ACK, then `69 17 01 ...` (seen). Ambient + Focus on Voice level 10: `68 17 01 01 01 01 0a`. Off: `68 17 01 00 00 00 lv` | High (HL; libmdr) | Same bytes as `setAmbientSoundMode`. While dragging, SC sends `vcs` 00 |
| Ambient levels | 1 to 20 (capability `61 17` gives the range per mode; guess `61 17 02 00 01 14 01 01 01 14 01`) | | Medium (GB-5934 "1 to 20"; HL "0 to 20") | SunPods clamps 1..20: fine. Read `60 17` to be sure |
| Focus on Voice | `asmMode` byte 01 in type 17 | | High (GB-5934 "Voice passthrough"; VY write) | Same |
| Auto NC Optimizer | No command. Always on, automatic, includes air-pressure changes | | High (HG TP1000534561) | Nothing to send. The WH-1000XM4's manual optimizer (V1 `0x84`) does not exist here |
| Wind noise reduction | None. Type 17 has no NC-mode byte, HG has no wind option, GB gives the XM4 `WindNoiseReduction` but not the XM5 | | Medium-high | Nothing to send |
| NC/AMB button cycle | `66 30` → `67 30 fn`; capability `60 30` → `61 30 key` (key 04 NC_AMB_KEY, guess) | `68 30 fn` → ACK, `69 30 fn` | High (BL tested; libmdr + MOS ✅) | **Different variant.** SunPods reads `fa 03` and writes `fc 03 01 pp 01 00 fn`, gated on 0xf3; on the WH that is hidden and wrong. `fn` codes are the same (`NoiseCycle`): 01 NC/Amb/Off, 02 NC/Amb, 03 NC/Off, 04 Amb/Off. Factory default is 02 (HG TP1000534715); the recorded earbuds use 03 |
| Adaptive Sound Control | `70 00` → `71 00 03 06 10 20` (guess: NC type 17, EQ, Speak-to-Chat) | `78 00 onoff n entries` | Medium (HG item 25) | App-side activity logic, same as the earbuds |
| EQ capability | `50 00 01` → `51 00 06 15 0c` + 12 presets | | Medium (GB-5934 preset list) | Not read today; same shape as X5 |
| EQ bands | `5a 00` → `5b 00 06` CLEAR BASS, 400 Hz, 1 kHz, 2.5 kHz, 6.3 kHz, 16 kHz | | High for the bands (GB-5934), Medium for the bytes | Same 6 values, wire = dB + 10 |
| EQ read | `56 00` → `57 00 preset 06 v1..v6` (10 bytes) | | High (GB) | Same as `readEq` |
| EQ preset | | `58 00 id 00` | High (GB, BL) | Same as `setEqPreset` |
| EQ custom | | `58 00 a0 06 cb b1 b2 b3 b4 b5` | High (GB, BL) | Same as `setEqBands` |
| Presets | 00 Off, 10 Bright, 11 Excited, 12 Mellow, 13 Relaxed, 14 Vocal, 15 Treble Boost, 16 Bass Boost, 17 Speech, a0 Manual, a1 Custom 1, a2 Custom 2 | | High (GB-5934, GB `EqualizerPreset`) | Identical to `EqPreset` |
| DSEE Extreme | `e0 01` → `e1 01 02`; `e6 01` → `e7 01 v` (00 off, 01 auto) | `e8 01 v` → ACK, `e9 01 v` | High (BL, GB V2 layout, VY write) | Same 3-byte layout (`DSEE_SHORT_LAYOUT`); label stays "DSEE Extreme". HG: may be disabled while LDAC is in use |
| Speak-to-Chat on/off | `f6 0c` → `f7 0c onoff preview` | `f8 0c onoff 01` | High (GB, BL; VY write) | Same. Factory default off; two fingers held on the touch panel also toggles it (HG TP1000536742), so expect `f9 0c` notifies |
| Speak-to-Chat config | `fa 0c` → `fb 0c sens timeout` | `fc 0c sens timeout` | High (GB, BL) | Same. Default timeout about 15 s (01) |
| Touch panel on/off | `d0 d1 01` → `d1 d1 00 01 13` "TOUCH_PANEL_SETTING" ...; `d6 d1` → `d7 d1 00 v` (00 on, 01 off) | `d8 d1 00 v` → ACK, `d9 d1 00 v` | Feature High (HG, MOS); slot and key name Low | New for SunPods as a setting, but `readGeneral` already finds any slot by key. Add a title for `TOUCH_PANEL_SETTING`. GB's V2 code hard-codes `d6 d2` for its touch sensor, which on this model is multipoint (GB-PR), so do not copy it. Before switching the panel off, GB sets the voice assistant to "none" (`f8 04 ff`) if one is set, and sends `98 00 16 01` when it was Google or Alexa |
| Touch gestures | Fixed, not assignable: double tap play/pause or answer, swipe forward/back for tracks, swipe up/down for volume, hold about 1 s for the assistant, cover the panel for Quick Attention | | High (HG TP1000534542, TP1000534597, TP1000534514) | `TouchCapability` (`f0 03`) and left/right options do not apply: hide |
| Quick Attention | No setting, no message; works while the hand covers the panel | | High (HG TP1000534514) | Nothing to send |
| Quick Access | `f0 0d` → guess `f1 0d 01 01 02 01 00 00 02 00 00` (key 01 NC_AMB_KEY, type 01 BUTTON, double and triple press); `f6 0d` → `f7 0d 02 dd tt` | `f8 0d 02 dd tt` | High for param (GB, GB-5934), Low for capability | Same param bytes as the earbuds; the key is the NC/AMB button pressed 2 or 3 times, not a touch sensor. Service Link must be on |
| Pause when removed | `f6 01` → `f7 01 v` (00 on) | `f8 01 v` | High (GB, BL) | Same. Sensor: one proximity sensor in the left unit (HG TP1000539062) |
| Auto power off | `20 05` → guess `21 05 02 10 11`; `26 05` → `27 05 active time` | `28 05 10 00` (after 15 min not worn) / `28 05 11 00` (never) | High for param (GB `AutomaticPowerOff`, BL) | Same bytes as `setAutoOffOption`. No time choices: HG describes only "turns off after 15 minutes of not being worn" (TP1000534525) and "[Do not turn off]" (TP1000673390), which match 10 and 11, so "When removed" copy should say 15 minutes |
| Multipoint switch | `d0 d2 01` → `d1 d2 00 01 12` "MULTIPOINT_SETTING" ...; `d6 d2` → `d7 d2 00 v` | `d8 d2 00 v` | High (GB-PR unit tests `d6:d2`, `d8:d2:00:00` on, `d8:d2:00:01` off) | Same slot as the earbuds. GB-PR found the XM5 needs no `98 00 06 01` "apply"; with alerts on (`94 00 00`), expect alert 07 as on the earbuds (guess) |
| Multipoint devices | T2 `36 00` → `37 00 N {addr17 conn [status] L name}*N playing` | T2 `3c 00 01 addr17` connect, `3c 00 00 addr17` disconnect, `3c 01 addr17` switch audio, `38 01 00/01` fix/unfix audio, `34 00 01 00` / `34 00 00 00` pairing mode on/off | High (GB-PR, tested on a WH-1000XM5) | GB-PR: the status between address and name is 1 byte or 4 bytes depending on firmware (4 = conn plus 3-byte class of device, the type 02 layout). `Multipoint.kt` should accept both |
| Voice prompts | T2 `46 01` → `47 01 onoff lang` | T2 `48 01 onoff` (GB, 3 bytes) | Medium (GB) | SunPods sends 4 bytes `48 01 onoff lang`; test which the WH takes. Volume (`46 20`) probably not answered (section 2.2); `VoicePrompts.read` already tolerates null. The voice guidance capability's TWS byte should read 01 (DISABLE) on headphones (inferred) |
| Voice assistant | `f0 04` → guess `f1 04 01 04 30 31 32 ff` (touch panel, phone assistant, Google, Alexa, none); `f6 04` → `f7 04 va` | `f8 04 va` | Medium (GB codes ff/30/31/32; GB-5934 options) | X5 answers `f1 04 03 02 31 ff`. GB follows a change with `98 00 16 01` ("reboot") when Google or Alexa is involved |
| Alexa wake word | `f6 05` → `f7 05 onoff` | `f8 05 onoff` | Medium (HG item 5; SC type 05 layout) | Not driven today |
| Power off | | `24 03 01` (link drops) | High (GB, MOS) | Same as `powerOff` |
| Codec | `12 02` → `13 02 c` (01 SBC, 02 AAC, 10 LDAC) | | High (GB, VY read LDAC) | Same. No LC3 |
| Sound quality mode | `e2 02` → `e3 02 ed b`; `e6 02` → `e7 02 pm` (00 priority on sound quality, default; 01 priority on stable connection) | `e8 02 pm` → ACK, `e9 02 pm` | Medium (GB V2 `e6 02`/`e8 02` for LDAC models; HG TP1000534501). 0xe1 (`e6 00`) is the alternative | `readPriority` probes `e6 00` then `e6 02` and writes the one that answered: works if the WH answers only one. Read the list instead. A switch with multipoint on may raise LDAC alerts 70..73 |
| 360 Reality Audio | No MDR message in any source. Setup (ear photos, optimisation) runs in the app and the streaming service; VY deliberately excludes it | | Medium | Nothing to send |
| Head tracking | Added in 2.0.2 for Android spatial audio; the SDP list includes HID (`0x1124`), which is how Android reads head trackers (inference) | | Low | Nothing in MDR known |
| Service Link | `b0 10` → `b1 10 n f..`; `b6 10` → `b7 10 onoff` | `b8 10 onoff` | High (HG; GB) | Not driven today |
| Safe listening | T2 type 00 or 02 (headband): `58 tt logging preview`, `5a tt` → `5b tt dB cause` | | Medium (HG, VY, MOS) | `SafeListening` hard-codes type 03 (TWS_2): use the listed type. The headband status is one-sided (`53 tt st rec`, not L and R). "Put both earbuds in" copy does not fit |
| Volume | `a0 01` → `a1 01 1f cs`; `a6 20` → `a7 20 vol` | `a8 20 vol` | Medium-high (VY: raw 6 read as 20 %, so 0..30, 31 steps) | Same as the earbuds over Classic |
| Reset | | `f8 09 00` settings only; `f8 09 01` factory (GB) | High | Same |
| Auto Switch | T2 `f6 0a` → `f7 0a onoff idHi idLo addr17` | T2 `f8 0a onoff idHi idLo` | Low (HG Auto Switch; firmware 2.3.1+) | Same layout as the earbuds if listed |
| LE Audio switch | none | | Medium-high | `leModeSupported` becomes false; SunPods must never open the GATT control channel for this model |

## 4. Over-ear only, and earbud-only features it lacks

| Over-ear only (WH-1000XM5) | Notes |
|---|---|
| Physical NC/AMB button and power button on the left unit | Button cycle is NC_AMB_TOGGLE (0x69); Quick Access on double and triple press |
| Touch sensor control panel on the right unit, swipes | One switch (on/off), no per-side assignment |
| Quick Attention by covering the right panel | No setting |
| Speak-to-Chat toggle by holding two fingers on the panel | Device-side change, arrives as a notify |
| One proximity sensor (left unit) for wearing detection | Pause/resume and auto power off after 15 minutes |
| Wired mode with the supplied 1.2 m cable | Bluetooth turns off when the cable goes in; NC still works if powered (HG TP1000534715) |
| USB-C charging turns the headset off | No Bluetooth while charging, so no live charging state |
| Auto NC Optimizer, always automatic | No command |
| Single battery, 30 h / 40 h | Needs its own ratings in `EarbudModel` |
| Safe listening HBS types | Not the TWS types |

| Earbud-only (WF-1000XM5) | WH-1000XM5 |
|---|---|
| Case battery and low-case notice (0x2a) | none |
| Left/right battery (0x29) and L/R link status (0x11) | one battery, one unit |
| Per-side touch assignment (0xf3) | touch panel on/off only |
| Ear tip fit test (0xf6) | none |
| Head gestures (0xff) | none |
| LE Audio (0x42, 0xe7 type 05, T2 0x60..0x6f) | none |
| Sidetone ("voice capture during calls") | not in HG; MOS PR 20 notes the option should be hidden on this model |
| Voice guidance volume | not in HG (section 2.2) |
| One-earbud NC | n/a |

## 5. Help guide features, WH-1000XM5 versus WF-1000XM5

From "What you can do with the app": WH TP1000534533, WF TP1000781980.

| Feature | WH-1000XM5 | WF-1000XM5 |
|---|---|---|
| Software update | yes | yes (plus auto-update switch) |
| Voice guidance language / on-off | yes / yes | yes / yes |
| Voice guidance volume | no | yes |
| Voice assistant choice, Alexa wake word | yes | yes (not over LE Audio) |
| Quick Access | yes (NC/AMB button) | yes (touch) |
| Touch | touch panel on/off | change touch functions per side |
| Sound quality mode | yes | yes (not over LE Audio) |
| Sidetone during calls | no | yes |
| Turn off from app | yes | yes |
| Auto power off when not worn | yes | yes |
| Pause and resume on removal | yes | yes |
| Multipoint on/off, change device | yes | yes (not over LE Audio) |
| LE Audio setting and status | no | yes |
| Initialize | yes | yes |
| Ear tip fit test | no | yes |
| Head gesture detection | no | yes |
| Service Link | yes | yes |
| Firmware version, codec | yes | yes |
| L/R connection status | no | yes |
| DSEE Extreme | yes | yes |
| Battery | headset | earbuds and case, case low notice |
| EQ presets and custom | yes | yes (EQ/CLEAR BASS) |
| NC and Ambient Sound adjust | yes | yes |
| NC/AMB switching pattern | yes | yes |
| Adaptive Sound Control | yes | yes |
| Speak-to-Chat | yes | yes |
| Playback and volume | yes | yes |
| Easy pairing | yes | yes |
| Usage log | not listed | yes |
| 360 Reality Audio | yes | yes |
| Safe listening | yes | yes |
| Auto Play | yes | yes |
| Auto Switch with speakers | yes (Sound Connect) | yes |

## 6. Colours, codes and names

| Colour | Model suffix | Sold where (official pages seen) | Swatch on sony.jp | Colour code over MDR |
|---|---|---|---|---|
| Black | /B | JP, everywhere | `#000000` | 0x01 BLACK (inferred) |
| Platinum Silver ("Silver" in the US) | /S | JP, everywhere | `#dad7cc` | 0x03 SILVER (inferred) |
| Midnight Blue | /L (CA `wh1000xm5-l`, AU `WH1000XM5L`, IN `wh1000xm5-lmin`, HK `/LME`) | not on sony.jp; added 2023 (press coverage) | not available | 0x05 BLUE (inferred) |
| Smoky Pink | /P | JP; announced 19 Sep 2024 (Sony Hong Kong press release `sony.com.hk/press/pdf/20240919_e.pdf`, found by search) | `#CCBBBA` | 0x06 PINK (inferred) |

No source records a WH-1000XM5 `05 03` reply. The codes come from SC's ModelColor enum and match what SunPods' `budArt` uses for the WF-1000XM5 (0x03 silver, 0x06 pink). Series byte: 0x30 PREMIUM by analogy with the WF-1000XM5. The "-I" codes 0x11..0x1e may appear, as for the earbuds.

Bluetooth names: `WH-1000XM5` (Classic), `LE_WH-1000XM5` (BLE). Matching patterns in other clients: GB `.*WH-1000XM5.*`, BL `/.*WH-1000XM5.*/`.

## 7. Simulated replies for a SimProfile (reconstructed)

Format as in `SimProfiles.kt` (`12` = frame 0x0c, `14` = frame 0x0e). **Reconstructed, not recorded.** Lines marked "seen" in section 3 are the only ones observed on hardware: the handshake (firmware 2.5.1), `23 00` layout, `67 17` / `69 17` layout and `a5 01 00 02 01`. Values are the help guide's factory defaults where it gives them; fake addresses only.

```text
12 01 00 03 00 20 16 00 00
12 05 01 0a 57 48 2d 31 30 30 30 58 4d 35
12 05 02 05 32 2e 35 2e 31
12 05 03 30 01
12 07 00 1e 10 ff 12 ff 13 ff 14 ff 15 ff 16 ff 17 ff 20 ff 23 ff 25 36 27 ff 34 3f 50 11 69 ff 6b 08 70 01 90 ff a1 12 b2 43 b8 1f d1 3b d2 20 e2 1b e3 1a f1 38 f4 23 f5 24 f9 41 fc 0d fd 2b
14 07 00 05 41 3d 32 ff 52 ff f2 ff f8 ff
12 13 02 10
12 13 03 02 00
12 23 00 46 00
12 25 00 45 00
12 21 05 02 10 11
12 23 05 00
12 27 05 10 00
12 61 17 02 00 01 14 01 01 01 14 01
12 63 17 00
12 67 17 01 01 00 00 14
12 61 30 04
12 63 30 00
12 67 30 02
12 71 00 03 06 10 20
12 51 00 06 15 0c 00 00 10 00 11 00 12 00 13 00 14 00 15 00 16 00 17 00 a0 00 a1 00 a2 00
12 5b 00 06 10 00 01 01 01 90 01 03 e8 01 09 c4 01 18 9c 01 3e 80
12 53 00 00
12 57 00 00 06 0a 0a 0a 0a 0a 0a
12 e1 01 02
12 e3 01 00
12 e7 01 00
12 e3 02 00 00
12 e7 02 00
12 f3 01 00
12 f7 01 00
12 f3 0c 00 00
12 f7 0c 01 01
12 fb 0c 00 01
12 f1 04 01 04 30 31 32 ff
12 f3 04 00
12 f7 04 30
12 f7 05 01
12 f1 0d 01 01 02 01 00 00 02 00 00
12 f3 0d 00
12 f7 0d 02 00 00
12 b1 10 00
12 b3 10 00
12 b7 10 00
12 d1 d1 00 01 13 54 4f 55 43 48 5f 50 41 4e 45 4c 5f 53 45 54 54 49 4e 47 00
12 d3 d1 00
12 d7 d1 00 00
12 d1 d2 00 01 12 4d 55 4c 54 49 50 4f 49 4e 54 5f 53 45 54 54 49 4e 47 00
12 d3 d2 00
12 d7 d2 00 01
12 a1 01 1f 10
12 a3 01 00 02 00
12 a5 01 00 02 01
12 a7 20 06
12 a7 21 05
14 41 01 03 00 01 00 01 0f 01 02 03 04 05 06 07 08 09 0a 0b 0d 0f 10 f0
14 43 01 00 00
14 47 01 00 01
14 31 00 08 02 00
14 31 02 08 02 00
14 33 00 00 00
14 37 00 01 41 41 3a 42 42 3a 43 43 3a 44 44 3a 45 45 3a 30 31 01 05 50 69 78 65 6c 01
14 51 02 3c 38 6d 43 80 01 0f
14 57 02 00
14 5b 02 31 03
14 f3 0a 00
14 f7 0a 01 00 00 30 32 3a 30 30 3a 30 30 3a 30 30 3a 30 30 3a 31 34
```

What each line stands for: protocol 0x03002016 with both tables; "WH-1000XM5", firmware "2.5.1", PREMIUM black; 30 table 1 functions and 5 table 2 functions from section 2 (order bytes copied from X5 where it has the function, `ff` otherwise); LDAC; DSEE Extreme indicator off; battery 70 % (notify 69 %); auto off after 15 minutes not worn; NC on, level 20; NC/AMB button NC and Ambient; ASC for NC, EQ, Speak-to-Chat; 6-band EQ with 12 presets, Off and flat; DSEE Extreme off; sound quality priority; pause on removal on; Speak-to-Chat off, auto sensitivity, 15 s; assistant is the phone's; Alexa wake word off; Quick Access on the NC/AMB key, nothing assigned; Service Link on; touch panel on (slot guessed); multipoint off; 31 volume steps, paused (the `a5` notify is the seen one; its last byte 01 is not explained), music volume 6; voice guidance on in English with the X5 language list and the TWS byte set to DISABLE; 8 paired, 2 connected, not discoverable; one phone "Pixel" AA:BB:CC:DD:EE:01 with the 1-byte status layout; safe listening HBS_2 at 49 dB; Auto Switch off.

Behaviours for the profile's hooks (all guesses unless noted):

| Hook | Behaviour |
|---|---|
| `confirm` | `d8 d2` (multipoint) → alert 07, as the earbuds. `f8 04` (assistant change) → GB follows with `98 00 16 01`; the alert code 16 is GB's, not seen |
| `onSet` | `SimProfiles.eqSet`; `68 30 fn` stores `67 30 fn`; `d8 d1 00 01` (panel off) also sets the assistant to `ff` if it was 31 or 32, as GB does |
| `dropsAfter` | `24 03 01` (power off) |
| Charging | never report `chg` 01 while connected (the real headset turns off on USB power) |

## 8. Pictures and size for 3D renders (references only)

Sony's global, US, UK, CA and IN sites block automated fetching, so their image files could not be checked. These official files all answered HTTP 200 on 23 Nov 2026:

| What | URL | Notes |
|---|---|---|
| Three colours, three-quarter view | https://www.sony.jp/products/picture/WH-1000XM5.jpg | Black, Platinum Silver, Smoky Pink side by side (626 x 470) |
| Side, three-quarter and front (black) | https://www.sony.jp/products/picture/y_WH-1000XM5_021.jpg | Best orthographic-style reference (1200 x 450) |
| Three-quarter with headband callouts (black) | https://www.sony.jp/products/picture/y_WH-1000XM5_020.jpg | Slider and joint detail |
| Inside of the ear cup and pad (black) | https://www.sony.jp/products/picture/original_WH-1000XM5_010.jpg and `original_WH-1000XM5_004.jpg` | |
| Folded flat in the open carrying case (black and silver) | https://www.sony.jp/products/picture/original_WH-1000XM5_014.jpg | The XM5 folds flat (cups turn) but does not collapse |
| Retail box | https://www.sony.jp/products/picture/original_WH-1000XM5_015.jpg | |
| Microphone and sensor placement (translucent) | https://www.sony.jp/products/picture/original_WH-1000XM5_001.jpg and `original_WH-1000XM5_009.jpg` | |
| Touch panel gestures | https://www.sony.jp/products/picture/y_WH-1000XM5_026.jpg | |
| Smoky Pink, lifestyle | https://www.sony.jp/products/picture/y_WH-1000XM5_044.jpg | |
| Line drawings: parts | https://helpguide.sony.net/mdr/wh1000xm5/v1/en/contents/image/i0030_wh1000xm5.png and `i0040_wh1000xm5.png` | Button, port, mic and sensor positions |
| Line drawing: in the carrying case | https://helpguide.sony.net/mdr/wh1000xm5/v1/en/contents/image/i0430_wh1000xm5.png | |
| Line drawings: wearing, slider | https://helpguide.sony.net/mdr/wh1000xm5/v1/en/contents/image/i0600_wh1000xm5.png to `i0630_wh1000xm5.png` | |
| Line drawing: touch panel location | https://helpguide.sony.net/mdr/wh1000xm5/v1/en/contents/image/i0280_wh1000xm5.png | |
| Midnight Blue product pages (images not checked) | https://www.sony.ca/en/audio/headphones/headband/p/wh1000xm5-l , https://store.sony.com.au/headphones-noisecancelling/WH1000XM5L.html | Found by search; no reachable official Midnight Blue image file |
| Gallery index | https://www.sony.jp/headphone/products/WH-1000XM5/ and `feature_1.html` to `feature_6.html` | More shots: `y_WH-1000XM5_001` to `_044`, `original_WH-1000XM5_001` to `_019` |

Size: Sony publishes weight only, about 250 g without the cable (HG TP1000541014, JP spec). No official dimensions for the headphones or the case were found on any Sony page checked. Drivers are 30 mm (JP spec); the sony.jp feature text speaks of 8 microphones driven by the QN1 and V1 processors.

## 9. Battery, charging, power and pairing

| Item | Value | Source |
|---|---|---|
| Music, NC or Ambient on | max 30 h (AAC), 26 h (LDAC), 28 h (SBC) | HG TP1000534508; JP spec "最大30時間（NC ON時）" |
| Music, NC off | max 40 h (AAC), 36 h (LDAC), 38 h (SBC) | same |
| Calls | 24 h NC on, 32 h off | same |
| Wired, powered on | 28 h NC on, 38 h off | HG TP1000534508 |
| Ratings for `EarbudModel` | 30.0 / 40.0 h, case charges 0 (no charging case) | from the rows above |
| Charging time | about 3.5 h, USB-C, 1.5 A or more | HG TP1000534502 |
| Quick charge | 3 min → about 1 h; 10 min → about 5 h; 3 min → about 3 h with a USB PD adaptor of 9 V / 2.3 A or more | HG TP1000534508 |
| While charging | the headset turns off; no Bluetooth operation while on USB power | HG TP1000534502 |
| Low battery | warning beep, "Low battery"; at 10 % or less the indicator flashes orange for about 15 s at power on | HG TP1000534506 |
| Power on / off | hold the power button about 2 s | HG TP1000534521, TP1000534525 |
| Pairing mode | first power-on (or after initializing) enters pairing automatically; otherwise hold power about 5 s until the blue light double-flashes and "Pairing" is heard. Cancels after 5 min; passkey 0000; up to 8 paired devices | HG TP1000534535 |
| Reset (keeps pairings) | on USB power, press power and NC/AMB together | HG TP1000541012 |
| Factory initialize | off and unplugged, hold power and NC/AMB about 7 s; blue flashes 4 times | HG TP1000541013 |

## 10. Where sources disagree

| Point | Source A | Source B | Weight |
|---|---|---|---|
| NC inquired type | GB sends `66 15` / 7-byte `68 15 ...` (`AmbientSoundControl`, not "2") | HL: `66 15` is acked and never answered on 2.5.1; `66 17` works. libmdr uses type 17 too | GB's type looks wrong or firmware-bound (its init sample is 1.1.3). Use the listed FT |
| Touch sensor slot | GB V2 `getTouchSensor` uses `d6 d2` | GB-PR: `d2` is multipoint on the WH-1000XM5 | Find the slot by key, as SunPods does |
| Voice notifications type | GB `46 01` (type 01) | BL `46 03` (type 03, on/off only) | Sound Connect logic: use the listed FT (0x41 → 01, 0x45 → 03) |
| NC/AMB button | GB V2 `fc 03 01 pp 01 00 fn` (left TODO for this model) | BL and libmdr `68 30 fn`, both reported working | Use 0x69 / type 30 |
| Battery on Linux client | MOS issue 4: "100 % on R" when Bluetooth showed 90 % | HL, GB: single battery `22 00` | the report predates libmdr's single-battery handling |

## 11. Open points to settle on a real WH-1000XM5

| Priority | Probe | Settles |
|---|---|---|
| 1 | `00 00`, then `06 00` on both frames | sections 2.1 and 2.2 |
| 2 | `22 00` and `22 08` | battery variant (0x20 or 0x28) |
| 3 | `d0 d1 01` .. `d0 d4 01` | slot and key of the touch panel switch, and whether a sidetone slot exists |
| 4 | `60 17`, `60 30` | ambient range per mode; NC/AMB key byte |
| 5 | `e6 00`, `e6 02`, `e0 05` | which sound quality variant answers |
| 6 | T2 `40 01`, `40 03`, `46 20` | voice guidance variant, language list, whether volume exists |
| 7 | `20 05`, `f0 04`, `f0 0d` | auto off options, assistant key type and list, Quick Access key |
| 8 | T2 `50 00` / `50 02`, `5a 00` / `5a 02` | safe listening HBS type |
| 9 | T2 `36 00` | which device-list layout this firmware sends |
| 10 | `04 03` on each colour | real colour codes |

## Sources

- Gadgetbridge: `devices/sony/headphones/coordinators/SonyWH1000XM5Coordinator.java`, `service/devices/sony/headphones/protocol/impl/v2/SonyProtocolImplV2.java`, `service/devices/sony/headphones/SonyHeadphonesProtocol.java`, `service/devices/sony/headphones/SonyHeadphonesSupport.java`, `devices/sony/headphones/prefs/*` (https://codeberg.org/Freeyourgadget/Gadgetbridge, master); PR https://codeberg.org/Freeyourgadget/Gadgetbridge/pulls/6517; issue https://codeberg.org/Freeyourgadget/Gadgetbridge/issues/5934
- harald: https://github.com/k0nnect/harald/blob/main/devices/wh-1000xm5/protocol.md
- BudsLink: https://github.com/maniacx/BudsLink/blob/main/src/lib/devices/sony/deviceConfigs/WH-1000XM5.js, `src/lib/devices/sony/sonySocketV2.js`, https://maniacx.github.io/BudsLink/sony, https://github.com/maniacx/BudsLink/issues/73
- SonyHeadphonesClient / libmdr: https://github.com/mos9527/SonyHeadphonesClient/blob/v1-compat/docs/device-support/WH-1000XM5.md, `libmdr/src/HeadphonesV2.cpp`, https://github.com/mos9527/SonyHeadphonesClient/issues/4, https://github.com/mos9527/SonyHeadphonesClient/pull/20
- sony-headphones-linux: https://github.com/vyomjain6904/sony-headphones-linux/blob/main/docs/COMPATIBILITY.md
- Sony help guide WH-1000XM5: https://helpguide.sony.net/mdr/wh1000xm5/v1/en/contents/ pages TP1000534533 (app features), TP1000534501, TP1000534502, TP1000534506, TP1000534508, TP1000534513, TP1000534514, TP1000534515, TP1000534521, TP1000534525, TP1000534535, TP1000534540, TP1000534541, TP1000534542, TP1000534561, TP1000534597, TP1000534600, TP1000534689, TP1000534715, TP1000536742, TP1000539062, TP1000539063, TP1000541012, TP1000541013, TP1000541014, TP1000541015, TP1000541784, TP1000562272, TP1000673390, TP1001834180
- Sony help guide WF-1000XM5: https://helpguide.sony.net/mdr/2963/v1/en/contents/TP1000781980.html, TP1000781965.html
- Sony Japan: https://www.sony.jp/headphone/products/WH-1000XM5/ and https://www.sony.jp/headphone/products/WH-1000XM5/spec.html
- Sony Singapore store spec page: https://store.sony.com.sg/blogs/7442273665179_42637659439259/7442273665179__specifications
- Firmware 2.0.2: https://www.notebookcheck.net/Sony-WH-1000XM5-receives-Multipoint-LDAC-and-head-tracking-support-via-new-update.736850.0.html
- SunPods: `docs/protocol/variants.md`, `docs/protocol/c710n_profile.md`, `SimProfiles.kt`, `SonyFeatures.kt`, `SonyBatteryClient.kt`, `SupportedFunctions.kt`, `VoicePrompts.kt`, `EarbudExtras.kt`, `EarbudModels.kt`, `SunDesign.kt`
