# WF-1000XM4 profile

What the WF-1000XM4 announces and answers, for adding it to SunPods and simulating it. Unlike the WF-C710N profile, most of this is **recorded**: a Gadgetbridge contributor attached btsnoop captures of Sony's own app talking to a WF-1000XM4 on firmware 1.4.2 to a public issue, and every byte marked GBI below was read from those files (parsed locally, nothing saved). Bluetooth addresses, serial-like IDs, log JSON and track titles in those captures are left out of this document on purpose.

Evidence used:

| Code | Source | What it gives |
|---|---|---|
| GBI | Gadgetbridge issue #2925, https://codeberg.org/Freeyourgadget/Gadgetbridge/issues/2925 : `btsnoop_hci.log` (issue body), `first connection.log`, `sony wf-1000xm4.zip` (38 per-action captures), `92`..`99` logs | Real WF-1000XM4 fw 1.4.2 traffic with Sony Headphones Connect, including the full support-function lists and every capability reply |
| GB | Gadgetbridge source, `app/src/main/java/nodomain/freeyourgadget/gadgetbridge/`: `devices/sony/headphones/coordinators/SonyWF1000XM4Coordinator.java`, `service/devices/sony/headphones/SonyHeadphonesProtocol.java`, `.../SonyHeadphonesSupport.java`, `.../protocol/impl/v1/SonyProtocolImplV1.java`, `.../protocol/impl/v2/SonyProtocolImplV2.java`, `app/src/test/.../v2/SonyProtocolImplV2Test.java` | Protocol version detection, UUIDs, the bytes GB sends |
| GBH | Gadgetbridge history: commit 8fdfbfa97c ("Sony WF-1000XM4: Initial support", the XM4-only V2 class), commit 0301d0e9a8 ("Merge V3 protocol into V2"), PR #5747 (Speak-to-Chat), PR #6519 (voice notifications), issue #3472 (fw 2.0.1 report) | What was tested on real XM4s, and a regression |
| HG | Sony help guide, https://helpguide.sony.net/mdr/wf1000xm4/v1/en/ (pages cited by TP number) | Features, gestures, ratings, pairing |
| SJ | Sony Japan product pages, https://www.sony.jp/headphone/products/WF-1000XM4/ (`spec.html`, `feature_1.html`, `feature_3.html`, `feature_4.html`) | Colours, battery with case, wind noise, multipoint with LDAC |
| X5 | SunPods' WF-1000XM5 recording (`SimProfiles.kt`, `c710n_profile.md` section 5) | The XM5 bytes to compare with |
| SC | Sound Connect layouts in `variants.md` | Meaning of each FunctionType and field |

Confidence: **Recorded** = seen in GBI. **High** = two sources agree. **Medium** = one source or a clean inference from SC. **Low / guess** = not seen anywhere.

## 1. Protocol version and transport

| Question | Answer | Source |
|---|---|---|
| v1 or v2 | **MDR v2** (the same command tables SunPods already speaks to the XM5). GB picks v1 on a 4-byte init reply and v2 on an 8-byte one; its own comment lists "WF-1000XM4 1.1.5: 01:00:01:00:00:00:00:00" | GB `SonyHeadphonesProtocol.java` decodeResponse; GBI (fw 1.4.2 answers the same 8 bytes) |
| Protocol version | `0x01000000`, both tables available (`01 00 01 00 00 00 00 00`). The XM5 sends `0x03003018`. `0x01000000` is the lowest value Sound Connect accepts | GBI; SC section 1.1 |
| RFCOMM UUID | **956c7b26-d49a-4ba8-b03f-b17d393cb6e2 only**. GB's first XM4 build tried the v1 UUID 96CC203E-5068-46ad-B32D-E316F5E069BA and the socket never opened; the maintainer wrote "It looks like the serial port UUID changed on the WF-1000XM4" and switched to 956c7b26. The SDP list in the contributor's log contains 956c7b26 and no 96cc203e | GB issue #2925 comments of 2022-10-21/22; GB `SonyHeadphonesSupport.java` getSupportedService |
| Other SDP UUIDs seen | 1108, 110b, 110e, 111e (headset, A2DP sink, AVRCP, hands-free), plus 764cbf0d-..., 81c2e72a-..., 8901dfa8-..., 931c7e8a-..., df21fe2c-2515-4fdb-8886-f12c4d67927c (df21fe2c is Google Fast Pair's message stream: general knowledge, not from these sources) | GB issue #2925 log |
| LE / GATT control channel | None. No LE Audio, codecs SBC, AAC, LDAC only, Bluetooth 5.2 | HG TP1000428698, TP1000241088 |
| Frames | Same framing as the XM5: table 1 on type `0x0c`, table 2 on `0x0e`, ACK `0x01` | GBI |

SunPods today tries UUID_V2 first (`SonyBatteryClient.kt` getOrOpen), so the link opens unchanged. The v1 UUID attempt will just fail and can stay.

There is no "v3": GB had a `SonyProtocolImplV3` from 2023 that was merged back into V2 in March 2024 because "the different messages only depended on whether the devices supports wind noise reduction or not" (GBH 0301d0e9a8). The XM4 is the wind-noise-reduction device of that pair.

## 2. Recorded support-function lists (fw 1.4.2)

### 2.1 Table 1

`06 00` → `07 00 1b 10 ff 12 ff 13 ff 14 ff 23 ff 25 15 11 ff 27 ff 29 ff 2a ff 92 ff 32 1a 50 07 68 03 70 01 90 ff a1 08 c1 ff e1 09 e2 0a f1 17 f2 05 f3 0f f4 0d 93 ff f6 14 f9 1b` (27 functions, GBI)

| FT | Name (SC) | Note |
|---|---|---|
| 0x10, 0x14, 0x27 | app-platform functions (concierge, BLE setup, keep-alive: names as guessed in `c710n_profile.md`) | the XM5 lists them too |
| 0x11 | CONNECTION_STATUS | `12 01` → `13 01 01 01` |
| 0x12 | CODEC_INDICATOR | `13 02 10` = LDAC |
| 0x13 | UPSCALING_INDICATOR | `13 03 02 01` = DSEE Extreme, working |
| 0x23 | POWER_OFF | listed, although GB never enabled power off for the XM4 |
| 0x25 | AUTO_POWER_OFF_WITH_WEARING_DETECTION | capability `21 05 02 10 11` (same as XM5) |
| 0x29, 0x2a | LR_BATTERY / CRADLE_BATTERY _WITH_THRESHOLD | same as XM5 |
| 0x32 | a firmware-update function (name not in `variants.md`; the XM5 lists 0x34). The app asks `30 02` and `36 02` for it, inferred | GBI |
| 0x50 | PRESET_EQ | 6 values, 21 steps, 12 presets |
| **0x68** | **MODE_NC_ASM_NC_DUAL_AUTO_ASM_LEVEL → NC/ASM type 0x15** | XM5 lists 0x6b (type 0x17). This is the biggest difference |
| 0x70 | AUTO_NCASM (Adaptive Sound Control) | |
| 0x90, 0x92, 0x93 | FIXED_MESSAGE, ..._WITH_LR_SELECTION, VOICE_ASSISTANT_ALERT | `92 03` → `93 03 00` |
| 0xa1 | PLAYBACK_CONTROLLER_WITH_CALL_VOLUME_ADJUSTMENT | `a1 01 1f 10` (31 music, 16 call steps) |
| 0xc1 | ACTION_LOG_NOTIFIER | the app pulls JSON logs with `c4 00 00`; not needed |
| **0xe1** | **CONNECTION_MODE_SOUND_QUALITY_CONNECTION_QUALITY → AUDIO type 00** | XM5 lists 0xe7 (type 05) |
| 0xe2 | UPSCALING_AUTO_OFF (DSEE) | capability `e1 01 02` = DSEE_HX_AI = DSEE Extreme |
| 0xf1 | PLAYBACK_CONTROL_BY_WEARING_REMOVING | |
| **0xf2** | **SMART_TALKING_MODE_TYPE1 (Speak-to-Chat) → SYSTEM type 02** | XM5 lists 0xfc (type 0c) |
| 0xf3 | ASSIGNABLE_SETTING (touch) | presets differ from the XM5, section 4.8 |
| 0xf4 | VOICE_ASSISTANT_SETTINGS | `f1 04 03 04 30 31 32 ff` |
| 0xf6 | WEARING_STATUS_DETECTOR (earbud fit test) | `f1 06 07 01 01 03 01 02 03` (3 tip sizes; XM5 lists 4) |
| 0xf9 | RESET_SETTINGS | |

Not listed (so hide): 0x15..0x17, 0x19, 0x24, 0x34, 0x40..0x4f (LE Audio), 0x6b, 0x94, 0xa2, 0xb0..0xb8 (auto play, Service Link), 0xc2, 0xd1..0xd4 (general settings: no multipoint, no sidetone on 1.4.2), 0xe7, 0xf5 (wake word), 0xfc, 0xfd (Quick Access), 0xff (head gestures).

### 2.2 Table 2

T2 `06 00` → T2 `07 00 01 41 18` (GBI): only **0x41** = VOICE_GUIDANCE_SETTING_MTK_TRANSFER_..._SUPPORT_LANGUAGE_SWITCH, with **no volume** (the XM5 lists 0x42 with volume). No pairing-device management, safe listening, link auto switch or LE functions on 1.4.2.

### 2.3 Connect replies

| GET | Reply (GBI) | Meaning |
|---|---|---|
| `00 00` | `01 00 01 00 00 00 00 00` | v `0x01000000`, both tables |
| `02 00` | `03 00 cc 11` + 17 ASCII bytes | counter `cc` (03, later 05), then the earbuds' own Classic address as text (redacted here) |
| `04 01` | `05 01 0a 57 46 2d 31 30 30 30 58 4d 34` | "WF-1000XM4" |
| `04 02` | `05 02 05 31 2e 34 2e 32` | "1.4.2" |
| `04 03` | `05 03 30 01` | series 0x30 PREMIUM, colour 0x01 BLACK |
| `04 04` | `05 04 05 10 01 40 80 60` | 5 guidance categories |
| `10 04` | `11 04` + an address-like string and 8 hex chars | not needed; redacted |

## 3. Variant each SunPods feature must use (XM4 vs what SunPods sends the XM5)

| Feature | WF-1000XM5 (SunPods today) | WF-1000XM4 (recorded) | What SunPods must change |
|---|---|---|---|
| Model detection | `EarbudModel` WF_1000XM5, WF_C710N | name "WF-1000XM4" | add an entry: regex `WF[- ]?1000XM4`, 8.0 / 12.0 h, 2.0 case charges (section 7); without it `isSupported` rejects the pair |
| Earbud battery | `22 09` | `22 09` → `23 09 Ll Lc Rl Rc Lt Rt` | none |
| Case battery | `22 0a` | `22 0a` → `23 0a lvl chg 1e` | none |
| **Noise control** | type 0x17, 7 bytes | **type 0x15, 8 bytes** with an extra ncValue byte | `ModeReport.parse` reads 0x15 with the 7-byte layout and gets focus and level wrong; `setAmbientSoundMode` sends 7 bytes on 0x15. Needs the type 0x15 layout (4.4) |
| Automatic wind noise reduction | not in the XM5's type 0x17 | ncValue in type 0x15: write 03 = on, 02 = off; earbuds report 05 when on | new toggle |
| Noise cycle | `fa 03`, preset 00 | same, `fb 03 01 00 01 00 01` | none |
| Adaptive Sound Control | 0x70 | 0x70 | none |
| EQ | `56 00`, 6 values | identical layout and presets | none |
| DSEE Extreme | `e6 01` 3-byte | `e6 01` → `e7 01 v` 3-byte | none (label "DSEE Extreme" is right) |
| Sound quality mode | `e6 00` probe (lists 0xe7) | **type 00 is the listed one**: `e6 00` → `e7 00 pm` | `readPriority` already tries 00 first; better to pick by FT (0xe1 → 00) |
| **Speak-to-Chat** | type 0x0c: `fb 0c sens time` | **type 0x02**: `fb 02 sens voiceFocus time` (5 bytes) | `readSpeakToChat` / `setSpeakToChatConfig` hard-code 0x0c and take the last two bytes as sensitivity and time: with type 02 that reads voice focus as sensitivity. Use type 02 when 0xf2 is listed; add the voice-focus switch |
| **Touch functions** | presets 0x35 / 0x20 / 0xff | presets **0x00** / 0x20 / **0x10** / 0xff | 0x10 Volume Control is a real option on the XM4; noise control is 0x00 on fw 1.4.2 (`setTouch` falls back to 0x35 when no side currently has it: take the code from `f1 03` instead) |
| Pause when removed | `f6 01` | same | none |
| Auto power off | `26 05`, capability `02 10 11` | same | none |
| Voice assistant | 0xf4 | 0xf4: phone's assistant 30, Google 31, Alexa 32, none ff | same code |
| Fit test | 0xf6 | 0xf6, 3 tip sizes | same code |
| **Voice prompts** | T2 0x42, `48 01 onoff lang` and volume `48 20` | **T2 0x41**: on/off and language read only; **no volume** | send the 3-byte `48 01 onoff` (SC's form, and the one GB verified on an XM4, PR #6519); hide the volume row; `46 20` gets no answer |
| Codec | `12 02` | `12 02` → `13 02 c` (01 SBC, 02 AAC, 10 LDAC) | none |
| Multipoint | GS d2 | absent on 1.4.2; added in fw 2.0.0 (slot unknown) | probe as today; see section 9 |
| LE Audio switch | T1 0x42 | absent | hidden by the list already |
| Head gestures, Service Link, auto switch, safe listening, Quick Access | listed | absent | hidden by the list already |
| Power off | `24 03 01` | listed (0x23) | none |
| Reset | `f8 09 00` | listed (0xf9) | none |
| Alerts | `94 00 00`, `98 00 msg 01` | the app sends `94 04 00` and `94 00 00`; no alert was seen for any touch, EQ, sound-quality or wearing change on 1.4.2 | none |

## 4. Byte-level layouts (recorded unless marked)

Frame type is table 1 (`0x0c`) unless marked T2. "→" is the earbuds' answer; SETs are ACKed and then announced with the NTFY (`c+1`).

### 4.1 Device info, codec, link

| Feature | GET / SET | Reply | Notes |
|---|---|---|---|
| Model | `04 01` | `05 01 0a 57 46 2d 31 30 30 30 58 4d 34` | |
| Firmware | `04 02` | `05 02 05 31 2e 34 2e 32` | |
| Series, colour | `04 03` | `05 03 30 01` | Silver not recorded; SC ModelColor 03 SILVER is the expected value (medium) |
| Codec | `12 02` | `13 02 10`; NTFY `15 02 00` then `15 02 02` while switching to AAC | 00 unsettled, 01 SBC, 02 AAC, 10 LDAC |
| L/R link | `12 01` | `13 01 01 01` | |
| DSEE indicator | `12 03` | `13 03 02 01`; NTFY `15 03 02 02` after switching to LDAC | 02 DSEE Extreme; 01 valid, 02 invalid |

### 4.2 Battery

| GET | Reply | Notes |
|---|---|---|
| `22 09` | `23 09 61 00 62 00 64 64` | L 97 %, R 98 %, not charging, thresholds 100 / 100 (the XM5 sends 100 / 100 too) |
| `22 0a` | `23 0a 53 00 1e` | case 83 %, threshold 30 % (drives the "charge the case" popup, HG TP1000241109) |
| (unprompted) | `25 09 63 00 64 00 64 64` | |

Charging byte: 00 not charging, 01 charging, 02 unknown, 03 charged (SC).

### 4.3 Power, auto power off, wearing

| Feature | GET / SET | Reply | Notes |
|---|---|---|---|
| Power off | `24 03 01` | link drops | listed; HG voice guide "Power off (powered off with the app)" |
| Auto off capability | `20 05` | `21 05 02 10 11` | when removed, never |
| Auto off status | `22 05` | `23 05 00` | |
| Auto off read | `26 05` | `27 05 10 00` | when removed |
| Auto off set | `28 05 10 00` / `28 05 11 00` | `29 05 10 00` / `29 05 11 00` | "turn off when taken off" / "do not turn off" |
| Pause status | `f2 01` | `f3 01 00` | |
| Pause read | `f6 01` | `f7 01 00` | 00 = on |
| Pause set | `f8 01 00` / `f8 01 01` | `f9 01 00` / `f9 01 01` | on / off |
| Link control (unknown purpose) | `24 07 00 05`, `24 07 01 05` | `25 07 00`, `25 07 01` | seen before the first change of a session; SunPods does not need it |

Behaviour (HG TP1000239769, TP1000240498): IR sensors in each unit; off after about 15 minutes not worn; touch sensors ignore taps when not worn unless auto power off is disabled.

### 4.4 Noise cancelling and Ambient Sound (type 0x15)

Layout (SC section 4, type 15): `[67|68|69] 15 vcs total ncAsmMode ncValue asmMode level`, 8 bytes.

| Offset | Field | Values on the XM4 |
|---|---|---|
| 2 | vcs | 01 settled, 00 slider still moving |
| 3 | total | 00 noise control off, 01 on |
| 4 | ncAsmMode | 00 NC, 01 Ambient |
| 5 | ncValue | **02 ON_DUAL** (wind reduction off), **03 AUTO** (written to turn automatic wind noise reduction on), **05 AUTO_DUAL** (reported while it is on) |
| 6 | asmMode | 00 normal, 01 Focus on Voice |
| 7 | level | 1..20 |

What "auto" is: Sony says the headset turns the feed-forward microphone off when it detects wind (SJ feature_1: 風を検知すると、フィードフォワードマイクがOFFとなり自動的に風ノイズを低減), which is SC's switch between dual and single NC. HG TP1000241109 lists "Turn automatic wind noise reduction on or off"; HG TP1000241094 says it shortens battery life.

| Feature | GET / SET | Reply | Notes |
|---|---|---|---|
| Capability | `60 15` | `61 15 02 00 01 14 01 01 01 14 01` | normal 1..20 step 1, voice 1..20 step 1 |
| Status | `62 15` | `63 15 00` | usable |
| Read | `66 15` | `67 15 01 00 00 02 01 14` | off (remembers Ambient, voice, 20) |
| Read, wind auto on | `66 15` | `67 15 01 00 00 05 01 0e` | another session |
| Set NC | `68 15 01 01 00 02 01 0f` | `69 15 01 01 00 02 01 0f` | file "5 noise cancelling" |
| Set Ambient | `68 15 01 01 01 02 01 0f` | `69 15 01 01 01 02 01 0f` | file "3 ambient sound on" |
| Set Off | `68 15 01 00 00 02 01 0f` | `69 15 01 00 00 02 01 0f` | file "6 ALL OFF" |
| Ambient off from Ambient | `68 15 01 00 01 02 01 0f` | `69 15 01 00 00 02 01 0f` | earbuds normalise mode to NC when off |
| Wind auto on | `68 15 01 01 00 03 01 0f` | `69 15 01 01 00 05 01 0f` | file "97", app writes 03, earbuds report 05 |
| Focus on Voice on | `68 15 01 01 01 05 01 13` | `69 15 01 01 01 05 01 13` | the app echoes the reported 05 |
| Slider drag | `68 15 00 01 01 05 01 03` ... then `68 15 01 01 01 05 01 03` | NTFY only for the final one | file "95" |
| GB test (wind) | `68 15 01 01 00 03 01 0f` | | `SonyProtocolImplV2Test.java` (commit 8fdfbfa), same bytes |

How it differs from SunPods today: SunPods sends `68 17 01 en amb focus level` (7 bytes) and parses `[3] en, [4] mode, [5] focus, [6] level`. For type 0x15 insert ncValue at [5] and shift focus and level to [6] and [7]; echo the ncValue last reported (02 or 05) unless the user changes the wind setting.

Gadgetbridge today is wrong for the XM4 here: the 2024 merge turned `supportsWindNoiseCancelling() ? 0x15 : 0x17` around, so current GB sends `66 17` and an 8-byte `68 17 ...` to the XM4 (`SonyProtocolImplV2.java` getAmbientSoundControl). The original XM4 class (8fdfbfa) and every recording use 0x15. That same inversion is why GB sends `66 15` to the XM5 (`c710n_profile.md` section 6).

Gestures (HG TP1000239119, TP1000240498): left tap cycles NC → Ambient → Off by default; the cycle is set with the noise-cycle param (4.8). Left hold is Quick Attention. With one unit worn, Ambient switches on (HG TP1000238995).

### 4.5 Adaptive Sound Control

Listed (0x70) as on the XM5. Not exercised in the captures; use the XM5 handling (`variants.md` 4.4). The XM5 answers `71 00 03 06 10 20`; for type 0x15 the applicable-function byte is 04 (SC: 00..08 = types 11..19), so the XM4 should answer `71 00 03 04 10 20` (reconstructed, low).

### 4.6 Equalizer and CLEAR BASS

| Feature | GET / SET | Reply | Notes |
|---|---|---|---|
| Capability | `50 00 01` | `51 00 06 15 0c 00 00 10 00 11 00 12 00 13 00 14 00 15 00 16 00 17 00 a0 00 a1 00 a2 00` | 6 values, 21 steps, 12 presets with empty names |
| Bands | `5a 00` | `5b 00 06 10 00 01 01 01 90 01 03 e8 01 09 c4 01 18 9c 01 3e 80` | CLEAR BASS, 400 Hz, 1 kHz, 2.5 kHz, 6.3 kHz, 16 kHz |
| Status | `52 00` | `53 00 00` | |
| Read | `56 00` | `57 00 10 06 09 0a 0f 11 11 13` | Bright |
| Preset set | `58 00 pp 00` | `59 00 pp 06 v1..v6` | the app re-reads `5a 00` after each |
| Custom set | `58 00 a0 06 cb b1 b2 b3 b4 b5` | | not captured from Sony's app; GB sends this (`SonyProtocolImplV2Test.java`) |

Preset curves (`59 00 pp 06 ...`, wire value = dB + 10; recorded):

| pp | Preset | CLEAR BASS, 400, 1k, 2.5k, 6.3k, 16k |
|---|---|---|
| 00 | Off | `0a 0a 0a 0a 0a 0a` |
| 10 | Bright | `09 0a 0f 11 11 13` |
| 11 | Excited | `12 09 0b 0a 0d 0f` |
| 12 | Mellow | `07 09 08 07 06 04` |
| 13 | Relaxed | `01 07 09 07 05 02` |
| 14 | Vocal | `0a 10 0e 0c 0d 09` (identical to the XM5's recorded Vocal) |
| 15 | Treble Boost | `0a 0a 0a 0c 10 14` |
| 16 | Bass Boost | `11 0a 0a 0a 0a 0a` |
| 17 | Speech | `00 0e 0d 0b 0c 00` |
| a0, a1, a2 | Manual, Custom 1, Custom 2 | user values (flat when captured) |

Same as SunPods' XM5 code (`readEq`, `setEqPreset`, `setEqBands`).

### 4.7 DSEE Extreme and sound quality mode

| Feature | GET / SET | Reply | Notes |
|---|---|---|---|
| DSEE capability | `e0 01` | `e1 01 02` | DSEE Extreme |
| DSEE status | `e2 01` | `e3 01 00` | |
| DSEE read | `e6 01` | `e7 01 01` | on |
| DSEE set | `e8 01 00` / `e8 01 01` | `e9 01 00` / `e9 01 01` | |
| Sound quality status | `e2 00` | `e3 00 00` | |
| Sound quality read | `e6 00` | `e7 00 00` | 00 Priority on sound quality |
| Set stable | `e8 00 01` | `e9 00 01`, then codec NTFY `15 02 00`, `15 02 02` | AAC on that phone |
| Set sound quality | `e8 00 00` | `e9 00 00`, then `15 02 00`, `15 02 10`, `15 03 02 02` | LDAC |

HG TP1000241112: two modes, "Priority on sound quality" and "Priority on stable connection" (default). LDAC only plays in sound-quality mode (HG TP1000241088). No capability message for type 00 (SC section 7).

### 4.8 Touch controls and noise cycle

Capability (recorded): `f0 03` →

```
f1 03 02
   00 00 00 04                      left, touch sensor, default preset 00, 4 presets
      00 01 01  22 10  00 02 04 01 02 03 04
                                    00 Noise control: hold -> 10 Quick Attention; tap -> noise cycle (01..04)
      20 03 00  00 20  01 21  02 22 20 Playback: tap play/pause, 2x next, 3x previous
      10 02 00  00 23  22 24        10 Volume: tap volume up, hold volume down
      ff 01 00  00 00               ff Not assigned
   01 00 20 04  (same four presets) right, default preset 20
```

| Feature | GET / SET | Reply | Notes |
|---|---|---|---|
| Status | `f2 03` | `f3 03 02 00 00` | both usable |
| Read | `f6 03` | `f7 03 02 00 20` | left noise control, right playback (factory) |
| Set | `f8 03 02 L R` | `f9 03 02 L R` | every combination below was accepted with no alert |
| | `f8 03 02 20 20`, `02 00 20`, `02 10 20`, `02 ff 20`, `02 ff 00`, `02 ff 10`, `02 ff ff` | echoed | files 25..32 |
| Noise cycle read | `fa 03` | `fb 03 01 00 01 00 01` | NC / Ambient / Off |
| Noise cycle set | `fc 03 01 00 01 00 fn` | `fd 03 ...` | fn 01..04 as SC; not captured from the app, GB sends this layout (with 0x35 instead of 00 for the XM4, see 9) |

Differences from the XM5: the XM4 has a whole-key **Volume Control** preset (0x10: tap louder, hold quieter), no repeat-tap volume and no Quick Access actions, and on fw 1.4.2 noise control is preset **0x00** (the XM5 capability says 0x35). HG TP1000240498 defaults: left tap noise cycle, left hold Quick Attention, right tap play/pause, 2x next, 3x previous, right hold voice assistant; both held 5 s = pairing.

### 4.9 Speak-to-Chat (type 02)

| Feature | GET / SET | Reply | Notes |
|---|---|---|---|
| Capability | `f0 02` | `f1 02 01 05 0f 1e` | preview supported; a, b, c = 5, 15, 30: almost certainly the mode-out times in seconds for FAST, MID, SLOW (inferred) |
| Status | `f2 02` | `f3 02 00 00` | usable, not active |
| Read | `f6 02` | `f7 02 01 01` | off, preview off |
| Ext read | `fa 02` | `fb 02 00 01 01` | sensitivity AUTO, voice focus OFF, time MID (15 s) |
| On | `f8 02 00 01` | `f9 02 00 01`, `f5 02 00 00` | |
| On with preview | `f8 02 00 00` | `f9 02 00 00`, `f5 02 00 01` (chat mode active) | app's Speak-to-Chat screen open |
| Ext set | `fc 02 sens voiceFocus time` | `fd 02 ...` | seen as `fc 02 00 01 00` / `fc 02 00 00 00` (PR #5747) |

Fields: sens 00 auto, 01 high, 02 low; voiceFocus OnOff (00 on); time 00 short (5 s), 01 standard (15 s), 02 long (30 s), 03 never. HG TP1000428692: off by default, about 15 s default timeout, "L Sensitivity" option.

The XM4 also accepts GB's type 0x0c messages (`f8 0c onoff 01`, `fc 0c sens time`): PR #5747's tester found on/off, sensitivity and timeout "stick" and measured roughly 7, 18 and 33 s for 00, 01, 02. Use type 02 anyway, since that is what the earbuds list and what Sony's app sends.

### 4.10 Voice assistant, fit test, reset

| Feature | GET / SET | Reply | Notes |
|---|---|---|---|
| VA capability | `f0 04` | `f1 04 03 04 30 31 32 ff` | assignable sensor; phone, Google, Alexa, none |
| VA status / read | `f2 04`, `f6 04` | `f3 04 00`, `f7 04 ff` | none chosen |
| VA set | `f8 04 30` | `f9 04 30`, then `f9 03 02 00 20` | touch is re-announced |
| VA off | `94 04 00`, `f8 04 ff` | `f9 04 ff`, `f9 03 02 00 20` | |
| Fit capability | `f0 06` | `f1 06 07 01 01 03 01 02 03` | 3 tip sizes (S, M, L) |
| Fit status / read | `f2 06`, `f6 06` | `f3 06 00 00 00 00`, `f7 06 00 00 00 00 ff ff` | |
| Fit run | `f4 06 01 01`, `f8 06 00 00 01 ff` | `f5 06 00 01 01 00`, `f9 06 01 00 01 00 ff ff`, `f9 06 02 00 01 00 ff ff`, `fd 06 00 00 ff ff ff ff` | layouts not decoded in `variants.md` |
| Fit stop | `f4 06 00 01` | `f5 06 00 00 01 00` | |
| Reset settings | `f8 09 00` | `f9 09 00` | reconstructed (SC layout); 0xf9 listed |

### 4.11 Voice guidance (table 2, FT 0x41)

| Feature | GET / SET | Reply | Notes |
|---|---|---|---|
| Capability | T2 `40 01` | T2 `41 01 03 00 00 00 01 0f 01 02 03 04 05 06 07 08 09 0a 0b 0d 0f 10 f0` | same 15 languages as the XM5 |
| Status (on/off) | T2 `42 01 00` | T2 `43 01 00 00` | switch usable |
| Status (language) | T2 `42 01 01` | T2 `43 01 01 01` | |
| Read | T2 `46 01` | T2 `47 01 00 01` | on, English |
| Set | T2 `48 01 01` / `48 01 00` | ACK, T2 `49 01 ...` | 3 bytes (SC `xg0/i0`; GB PR #6519 "verified it works" on an XM4) |
| Language pack list | T2 `4a 01` | no reply in the captures | |

HG TP1000241090: English by default, language changed in the app takes about 20 minutes (a transfer), no volume setting. Initializing returns it to English (HG TP1000428697).

### 4.12 Playback and volume

| GET | Reply | Notes |
|---|---|---|
| `a0 01` | `a1 01 1f 10` | |
| `a2 01` | `a3 01 00 02 00` | paused, music; NTFY `a5 01 00 03 00` / `a5 01 01 02 00` seen |
| `a6 20` | `a7 20 0f` | music volume 15 of 0..30 |
| `a6 21` | `a7 21 06` | call volume |
| `a6 01` | track, album, artist, genre strings | same layout as XM5 |

### 4.13 Alerts

0x90, 0x92 and 0x93 are listed; `92 03` → `93 03 00`. The app switches alerts on with `94 04 00` and `94 00 00`. No `99 00 ...` arrived for any change in the captures. Multipoint (fw 2.x) will most likely confirm with message 07 as on the XM5 (guess).

### 4.14 Not supported by the WF-1000XM4

360 Reality Audio is supported by the earbuds (SJ: 360 Reality Audio 認定) but has no MDR function in the list; personalisation happens in the phone app and the streaming service. Nothing to drive. LE Audio, head gestures, Service Link, safe listening, auto device switching, Quick Access and sidetone are absent on 1.4.2.

## 5. Features: WF-1000XM4 versus WF-1000XM5

From the two recorded function lists (XM4 fw 1.4.2, XM5 fw 6.1.0) and HG TP1000241109.

| Feature | WF-1000XM4 | WF-1000XM5 |
|---|---|---|
| Battery L / R / case with low-case threshold | yes | yes |
| NC / Ambient, 20 levels, Focus on Voice | yes (type 0x15) | yes (type 0x17) |
| Automatic wind noise reduction switch | **yes** | no switch in its NC type |
| Adaptive Sound Control | yes | yes |
| Quick Attention | yes | yes |
| Speak-to-Chat | yes, with voice focus (type 02) | yes (type 0c) |
| EQ: CLEAR BASS + 5 bands, 21 steps, 12 presets | yes, same | yes |
| DSEE Extreme | yes | yes |
| Sound quality mode | 2 options (0xe1) | 3 incl. LE (0xe7) |
| Codecs | SBC, AAC, LDAC | SBC, AAC, LDAC, LC3 |
| LE Audio | **no** | yes |
| Multipoint | **fw 2.0.0 and later only** (not in the 1.4.2 list) | yes (GS d2) |
| Hear your voice on calls (sidetone) | no | yes (GS d1) |
| Touch: per-side preset | Noise control, Playback, **Volume**, None | Noise control, Playback, None |
| Volume by repeated taps | no | yes |
| Quick Access (Spotify Tap etc.) | no (1.4.2) | yes |
| Voice assistant choice | yes (phone, Google, Alexa) | yes |
| Alexa wake word switch (0xf5) | not listed (HG mentions it) | yes |
| Head gestures | no | yes |
| Earbud fit test | yes (3 sizes) | yes (4 sizes) |
| Pause when removed | yes | yes |
| Auto power off when removed | yes | yes |
| Power off from app | yes | yes |
| Reset from app | yes | yes |
| Voice guidance on/off + language | yes | yes |
| Voice guidance volume | **no** | yes |
| Safe listening, Service Link, auto device switch | no | yes |
| Qi wireless charging | yes (HG TP1000241093) | yes |
| 360 Reality Audio | yes, app-side | yes, app-side |

## 6. Colours and Bluetooth names

| Item | Value | Source |
|---|---|---|
| Colours sold | (B) Black, swatch `#000000`; (S) Platinum Silver, swatch `#dad7cc` | SJ product page colour chips |
| Other colours | none found (the "Silent White" limited edition was the WH-1000XM4 headphones, not these earbuds) | web search, Sony EU press centre |
| Colour code, black | `05 03 30 01` | GBI |
| Colour code, silver | `03` expected (SC ModelColor SILVER; SunPods already calls 0x03 Platinum Silver for the XM5) | not recorded |
| Classic name | `WF-1000XM4` | HG TP1000240431, GBI model string, GB regex `.*WF-1000XM4.*` |
| LE name | no LE Audio device and no `LE_WF-1000XM4`. The earbuds send a Microsoft Swift Pair beacon with no LE local name; the beacon's display name is `WF-1000XM4`, class of device 0x240404, "pairing over BR/EDR only, using LE for discovery" | nRF Connect screenshot attached to GB issue #2925 (`scan.jpeg`) |
| Passkey | 0000 | HG TP1000240431 |

## 7. Battery ratings, pairing, reset

| Item | Value | Source |
|---|---|---|
| Earbuds, NC on / off | max 8 h / 12 h (AAC, DSEE off) | SJ spec.html, HG TP1000241094 |
| With case | max 24 h NC on / 36 h NC off: "case 2 charges" | SJ feature_3 |
| Case charges for `EarbudModel` | 2.0 ((24 − 8) / 8 = (36 − 12) / 12) | computed |
| Other codecs | LDAC 5 / 6 h; SBC 7.5 / 11 h; DSEE Extreme AUTO lowers them (AAC 6 / 8 h) | HG TP1000241094 |
| Calls | 5.5 h NC on, 6 h off (5 h Ambient) | SJ spec.html, HG TP1000241094 |
| Quick charge | 5 min → about 60 min playback | HG TP1000241094, SJ feature_3 |
| Charge time | headset about 1.5 h; headset and case from empty about 3 h; USB-C or Qi | HG TP1000428698, TP1000241093 |
| Case indicator | orange 1..30 %, off at 0 %, green 3 s when full on removing the buds; red flashes on errors | HG TP1000241095, TP1000241100 |
| Pairing, first time | take both units out of the case: pairing starts by itself | HG TP1000240431 |
| Pairing, another device | wear both units, hold both touch sensors about 5 s until "Bluetooth pairing" | HG TP1000240431, TP1000240498 |
| Pairing limits | 5 minutes, then back in the case; up to 8 devices, the 9th replaces the oldest | HG TP1000240431 |
| Reset (keeps pairings) | one unit in the case, case on USB power, open and close the lid more than 5 times within 20 s; green flashes 4 times; repeat for the other unit | HG TP1000428696 |
| Initialize (factory) | both units in the case, lid open, hold both sensors about 10 s, release within 5 s of the case flashing red; green flashes 4 times. Deletes pairings and settings, resets the prompt language, keeps firmware | HG TP1000428697 |

## 8. Simulated replies for an `Xm4Profile` (SimProfile)

Lines marked R are recorded (GBI, fw 1.4.2; addresses and IDs replaced or left out); C are reconstructed. Format as in `C710nProfile.kt`: `12` = table 1, `14` = table 2.

```
# R: connect
12 01 00 01 00 00 00 00 00
12 03 00 05 11 30 32 3a 30 30 3a 30 30 3a 30 30 3a 30 30 3a 34 30
12 05 01 0a 57 46 2d 31 30 30 30 58 4d 34
12 05 02 05 31 2e 34 2e 32
12 05 03 30 ${colour}
12 05 04 05 10 01 40 80 60
12 07 00 1b 10 ff 12 ff 13 ff 14 ff 23 ff 25 15 11 ff 27 ff 29 ff 2a ff 92 ff 32 1a 50 07 68 03 70 01 90 ff a1 08 c1 ff e1 09 e2 0a f1 17 f2 05 f3 0f f4 0d 93 ff f6 14 f9 1b
14 07 00 01 41 18
# R: status and battery
12 13 01 01 01
12 13 02 10
12 13 03 02 01
12 23 09 61 00 62 00 64 64
12 23 0a 53 00 1e
12 25 09 63 00 64 00 64 64
12 21 05 02 10 11
12 23 05 00
12 27 05 10 00
# R: noise control (type 15); C: the 67 15 line is the recorded NTFY of "5 noise cancelling"
12 61 15 02 00 01 14 01 01 01 14 01
12 63 15 00
12 67 15 01 01 00 02 01 0f
# C: Adaptive Sound Control (XM5 bytes with applicable function 04 for type 15)
12 71 00 03 04 10 20
# R: EQ
12 51 00 06 15 0c 00 00 10 00 11 00 12 00 13 00 14 00 15 00 16 00 17 00 a0 00 a1 00 a2 00
12 5b 00 06 10 00 01 01 01 90 01 03 e8 01 09 c4 01 18 9c 01 3e 80
12 53 00 00
12 57 00 10 06 09 0a 0f 11 11 13
# R: DSEE Extreme, sound quality mode
12 e1 01 02
12 e3 01 00
12 e7 01 01
12 e3 00 00
12 e7 00 00
# R: wearing
12 f3 01 00
12 f7 01 00
# R: Speak-to-Chat (type 02)
12 f1 02 01 05 0f 1e
12 f3 02 00 00
12 f7 02 01 01
12 fb 02 00 01 01
# R: touch, noise cycle
12 f1 03 02 00 00 00 04 00 01 01 22 10 00 02 04 01 02 03 04 20 03 00 00 20 01 21 02 22 10 02 00 00 23 22 24 ff 01 00 00 00 01 00 20 04 00 01 01 22 10 00 02 04 01 02 03 04 20 03 00 00 20 01 21 02 22 10 02 00 00 23 22 24 ff 01 00 00 00
12 f3 03 02 00 00
12 f7 03 02 00 20
12 fb 03 01 00 01 00 01
# R: voice assistant, fit test
12 f1 04 03 04 30 31 32 ff
12 f3 04 00
12 f7 04 ff
12 f1 06 07 01 01 03 01 02 03
12 f3 06 00 00 00 00
12 f7 06 00 00 00 00 ff ff
# C: reset result (SC layout)
12 f9 09 00
# R: playback
12 a1 01 1f 10
12 a3 01 00 02 00
12 a7 20 0f
12 a7 21 06
12 93 03 00
# R: voice guidance (table 2, FT 0x41, no volume)
14 41 01 03 00 00 00 01 0f 01 02 03 04 05 06 07 08 09 0a 0b 0d 0f 10 f0
14 43 01 00 00
14 47 01 00 01
```

`${colour}`: 01 Black (recorded), 03 Platinum Silver (expected). The `03 00` address is a made-up one in the style of `SimProfiles.XM5`.

Profile behaviour to copy from the recordings: no `confirm` alerts on fw 1.4.2; `onSet` = `SimProfiles.eqSet`; a type 0x15 SET copies into `67 15` as is, except that ncValue 03 should be stored as 05 (file "97"); touch SETs with 00, 10, 20, ff are accepted as sent (files 25..32), so do not reuse the XM5's "refuse 0x10" rule. What the 1.4.2 earbuds do with a 0x35 touch preset is unknown.

Optional fw 2.x lines (C, guesses for testing multipoint):

```
# add d1 to the 07 00 list and 32 to the T2 list when using these
12 d1 d1 00 01 12 4d 55 4c 54 49 50 4f 49 4e 54 5f 53 45 54 54 49 4e 47 00
12 d3 d1 00
12 d7 d1 00 01
14 31 02 08 02 00
14 33 02 00 00
```

## 9. Firmware after 1.4.2 and other open points

| Point | What is known | Weight |
|---|---|---|
| Firmware 2.0.0 (Feb 2023) added multipoint | Notebookcheck report; HG TP1000954328 describes it; SJ feature_4 says multipoint works with LDAC | high that it exists; slot, FT and alert code not recorded |
| Noise-control touch preset on fw 2.x | GB issue #3472 (fw 2.0.1): GB showed both keys as "off" and writing noise control had no effect; GB then switched the XM4 to 0x35 (`encodeButtonMode`, commit 0301d0e9a8). So fw 2.x most likely uses 0x35 where 1.4.2 used 0x00 | medium; take it from `f1 03` |
| Latest firmware | a search result summarises Sony Asia's page as 2.3.1, 21 Oct 2025, needing Sound Connect 12.3.0 or later; Sony's sites refused automated fetches, so not checked | low |
| Protocol version on fw 2.x | GB's comment says 1.1.5 sends `01 00 01 00 ...`, as 1.4.2 does; 2.x not seen | unknown |
| Alexa wake word | HG lists "Turn the Amazon Alexa wake word on or off"; 0xf5 is not in the 1.4.2 list | unknown |
| Speak-to-Chat capability bytes 05 0f 1e as seconds | fits HG's 15 s default and PR #5747's timings | medium |
| Adaptive Sound Control capability | not captured | low |

Probes to run on a real XM4 on current firmware: `00 00`; `06 00` on both tables; `f0 03` and `f6 03`; `d0 d1 01` .. `d0 d4 01`; T2 `30 02`, `36 02`; `60 15`; `70 00`; T2 `40 01`.

## 10. Product photos (references only)

Opened and checked (Sony Japan; full-size files on www.sony.jp):

| View | URL |
|---|---|
| Both colours, outer face and side of each earbud | https://www.sony.jp/products/picture/WF-1000XM4.jpg (626×470); smaller copies at `/products/picture/middle/WF-1000XM4.jpg` and `/products/picture/small/WF-1000XM4.jpg` (not opened) |
| Black, outer face and side, tip callout | https://www.sony.jp/products/picture/original_WF-1000XM4_003.jpg |
| Black, angled outer and side | https://www.sony.jp/products/picture/y_WF-1000XM4_007.jpg |
| Black, worn in an ear | https://www.sony.jp/products/picture/original_WF-1000XM4_001.jpg |
| Cutaway of the earbud | https://www.sony.jp/products/picture/original_WF-1000XM4_004.jpg |
| Exploded view of all parts | https://www.sony.jp/products/picture/y_WF-1000XM4_045.jpg (1175×350) |
| Line drawing: units, every face incl. the inner side with IR sensor and contacts | https://helpguide.sony.net/mdr/wf1000xm4/v1/en/contents/image/i0100_wf1000xm4.png |
| Line drawing: case open and closed, indicator, USB-C | https://helpguide.sony.net/mdr/wf1000xm4/v1/en/contents/image/i0110_wf1000xm4.png |

Not found on pages that could be opened: photos of the case (open or closed) in each colour, and a silver-only inner-side view. Sony's global product pages returned HTTP 403 to automated requests, so these are to open by hand: https://electronics.sony.com/audio/headphones/truly-wireless/p/wf1000xm4-b , https://electronics.sony.com/audio/headphones/truly-wireless/p/wf1000xm4-s , https://www.sony.co.uk/electronics/truly-wireless/wf-1000xm4 .

## Appendix A. MDR v1, for reference (not used by the WF-1000XM4)

From GB `SonyProtocolImplV1.java` and `PayloadTypeV1.java`; used by the WH-1000XM2/XM3/XM4, WF-1000XM3 and WF-SP800N over UUID 96CC203E-5068-46ad-B32D-E316F5E069BA. Same framing; the command numbers and field orders differ from v2.

| Feature | v1 GET / SET | v1 reply | v2 (XM4 / XM5) |
|---|---|---|---|
| Init | `00 00` | `01 00 xx xx` (4 bytes) | 8 bytes |
| Firmware | `04 02` | `05 02 L str` | same |
| Battery | `10 00` single, `10 01` L/R, `10 02` case | `11 01 Ll Lc Rl Rc`; NTFY `13` | `22 09` / `22 0a`, NTFY `25` |
| Codec | `18 00` | `19 00 c`; NTFY `1b` | `12 02` |
| Power off | `22 00 01` | | `24 03 01` |
| NC / Ambient | `66 02`; SET `68 02 on(00/11) dual(00/02) mode 01 focus level` | `67 02 on dual mode xx focus level` (8 bytes; on 00/01). With dual 02: mode 00 Ambient, 01 wind reduction, 02 NC; with dual 00: 00 Ambient, 01 NC | `66 15` / `66 17` |
| EQ | `56 01`; SET `58 01 pp 00`, custom `58 01 ff 06 cb b1..b5` | 10 bytes | `56 00`, `58 00 pp 00`, custom `58 00 a0 06 ...` |
| DSEE | `e6 02`; SET `e8 02 00 v` | `e7 02 00 v` (4 bytes) | `e6 01` 3 bytes |
| Sound quality (LDAC) | `e6 01`; SET `e8 01 00 v` (00 sound quality) | 4 bytes | `e6 00` |
| Auto power off | `f6 04`; SET `f8 04 01 a b` | `f7 04 xx a b` (5 bytes) | `26 05` / `28 05 a b` |
| Pause when removed | `f6 03`; SET `f8 03 00 v` (01 = on, not inverted) | `f7 03 00 v` | `f6 01` / `f8 01 v` (00 = on) |
| Touch keys | `f6 06`; SET `f8 06 02 L R` (00, 20, 10, ff) | `f7 06 02 L R` | `f6 03` / `f8 03 02 L R` |
| Speak-to-Chat | `f6 05`; SET `f8 05 01 v` (01 = on) | `f7 05 01 v` | `f6 02` or `f6 0c` |
| Speak-to-Chat config | `fa 05`; SET `fc 05 00 sens focus time` | `fb 05 00 sens focus time` (6 bytes) | `fa 02` (5 bytes) or `fa 0c` (4) |
| NC/AMB button, touch panel | `d6 d1` / `d8 d1 02 mode`; `d6 d2` / `d8 d2 01 v` | 4 bytes | general settings `d6 dN` |
| Voice guidance | T2 `46 01 01`; SET `48 01 01 v` (01 = on) | T2 `47 01 01 v` | T2 `46 01` / `48 01 v` (00 = on) |
| Volume | `a6 01 20`; SET `a8 01 20 v` | `a7 01 20 v` | `a6 20` / `a8 20 v` |
| NC optimizer | `84 01 00 start`; `86 01` | `85`, `87` | not on these earbuds |
