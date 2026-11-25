# WH-1000XM6 profile (reconstructed)

Best evidence-based picture of what the Sony WH-1000XM6 over-ear headphones (2025, help guide model YY2984) announce and answer over Sony's MDR protocol, for adding them to SunPods and simulating them in a SimProfile. Nobody here has a pair, so nothing below is a SunPods recording. Unlike the WF-C710N, two open projects have talked to real XM6 units, so several layouts below are observed rather than inferred. Byte layouts follow `variants.md`; this file says which variant the XM6 uses.

Evidence used:

| Code | Source | What it gives |
|---|---|---|
| HG | Sony help guide, helpguide.sony.net/mdr/2984/v1/en/ (topic pages cited as TP numbers) | Features, controls, defaults, battery, pairing |
| GB | Gadgetbridge master: `devices/sony/headphones/coordinators/SonyWH1000XM6Coordinator.java` (commit ad57864dcc, 2026-04-13, marked experimental) and `service/devices/sony/headphones/protocol/impl/v2/SonyProtocolImplV2.java` | What upstream Gadgetbridge sends to an XM6 |
| GBM | Gadgetbridge fork by an XM6 owner: codeberg.org/M2110/Gadgetbridge, branch `sony-wh1000xm6-support`, commits db4f664065cd to a8dba73131ed (June to July 2025), tested on a real XM6 | Protocol info bytes, NC/ASM type 0x19, touch panel slot, volume range |
| MOS | mos9527/SonyHeadphonesClient (github.com/mos9527/SonyHeadphonesClient), mainline XM6 support: `docs/device-support/WH-1000XM6.md`, `libmdr/src/HeadphonesV2.cpp`, `HeadphonesV2T1.cpp`, `DetailsV2.hpp`, `Headphones.cpp`, generated `libmdr/include/mdr/Generated/ProtocolV2*.hpp`; PR #20 (general setting capture), PR #48 (EQ presets, FW 2.x/3.0.0), issue #76 (listening mode, FW 3.1.5), PR #63 (wearing status), PR #44 (BLE) | Real XM6 behaviour, EQ shape, general settings, listening mode, LE name |
| SONY | Sony UK product and spec pages, Sony UK firmware page 00355431, Sony press releases on sony.mediaroom.com | Colours, SKUs, weight, firmware history, photos |
| X5 | SunPods' WF-1000XM5 recording, firmware 6.1.0 (`SimProfiles.kt`) | A real Sony list to compare with |
| SC | Sound Connect code, via `variants.md` | Layouts and FunctionType mapping |

Confidence: **High** = observed on a real XM6 by GBM or MOS, or two sources agree. **Medium** = one source, or the feature is certain but the variant is not. **Low / guess** = inferred from the XM5 or from absence.

## 1. Protocol version and transport

| Question | Answer | Confidence | Source |
|---|---|---|---|
| Protocol family | MDR **v2** (table 1 on frame 0x0c, table 2 on 0x0e). There is no "v3": Gadgetbridge only has v1 and v2 implementations, libmdr only ProtocolV1 and ProtocolV2, and both drive the XM6 as v2 | High | GB `SonyHeadphonesProtocol.java` (8-byte init reply selects v2); MOS `libmdr/include/mdr/Generated/` |
| RET_PROTOCOL_INFO | `01 00 03 00 30 13 00 00` on FW 2.0.2: version 0x03003013, both support-function tables available | High | GBM commit b71d92f82dad, comment added to `SonyHeadphonesProtocol.java` |
| Compared with the WF-1000XM5 | XM5 FW 6.1.0 sends 0x03003018. Both are inside SC's accepted range (0x01000000 to 0x03003032) | High | X5, `variants.md` 1.1 |
| RFCOMM UUID | `956c7b26-d49a-4ba8-b03f-b17d393cb6e2` (Sony v2), the one SunPods already tries first | High | GB `SonyHeadphonesSupport.java`; issue #4974 reporter ("Uses the Sony v2 protocol") |
| GATT (LE Audio) | Same Tandem service as the XM5 earbuds: `5b833e20-6bc7-4802-8e9a-723ceca4bd8f`, found by GATT enumeration after connecting. It is not in the advertisement, so filter by name, not service | High | MOS PR #44 (WH-1000XM6 on Windows 11 and Web Bluetooth); SunPods `SonyLink.kt` GattLink uses this service |
| BLE advertisement | Name `LE_WH-1000XM6`, resolvable private address (differs from the Classic address) | High | MOS PR #44 comment, 2026-05-05 |
| Over LE Audio | RFCOMM control does not work while LE Audio is in use; use GATT, as SunPods does for the XM5 | Medium | MOS PR #20 comment, 2025-10-04 |
| Other GATT service | Airoha RACE service `dc405470-a351-4a59-97d8-2e2e3b207fbb` (firmware flashing). SunPods must never open it | Medium | MOS PR #44 |
| Firmware history | 1.0.5 (launch), 2.0.2 (2025-07-01), 2.1.0 (2025-08-21), 3.0.0 (2025-10-01), 3.1.5 (2026-06-30, adds GMAP) | High | SONY firmware page 00355431; GB issue #4974 (1.0.5) |
| Model name / series | `WH-1000XM6`; series byte expected 0x30 PREMIUM like the XM5 | High / guess | HG, GB; series not in any source |

## 2. Facts that decide variants

| Question | Answer | Source |
|---|---|---|
| Battery | One battery, no case. Usable while charging; charging stops near 80 % while in use | HG TP1001856824 |
| Wearing detection | Proximity sensor in the left unit. Pauses when removed, resumes when worn; touch panel and NC/AMB button disabled while off the head | HG TP1001861255 |
| Auto power off | Turns off 15 minutes after being taken off. App options are "Off when headphones are removed" and "Do not turn off" | HG TP1001856801; SONY firmware page 00355431 |
| Noise control | NC/AMB button cycles NC and Ambient by default. App can pick 4 patterns: NC/Amb/Off, NC/Amb, Amb/Off, NC/Off | HG TP1001856861, TP1001863303 |
| Ambient extras | Auto Ambient Sound on/off with a detection sensitivity. Adaptive NC Optimizer is always on (no setting) | HG TP1001872735, TP1001872734, TP1001856857 |
| Speak-to-Chat | Yes, off by default, ends after about 15 s by default, sensitivity selectable | HG TP1001858904 |
| Touch panel | Fixed gestures (table below); only on/off in the app, no per-gesture assignment | HG TP1001867598, TP1001856857 |
| Quick Access | On the NC/AMB button, pressed 2 or 3 times; needs Service Link | HG TP1001864814, TP1001856908 |
| Equalizer | 10-band EQ plus presets, "for music and gaming" | HG TP1001856857; SONY press release 2025-05-15 |
| DSEE | DSEE Extreme, Off/Auto; disabled during calls | HG TP1001861254 |
| Sound quality mode | 3 modes: Prioritize Sound Quality (default), Prioritize Stable Connection, Low latency (an LE Audio connection) | HG TP1001856706 |
| Codecs | SBC, AAC, LDAC over Classic; LC3 over LE Audio; Auracast receive; GMAP from FW 3.1.5 | HG TP1001856790, TP1002013437; SONY |
| Listening mode | BGM effect (room size) and 360 Upmix for Cinema; BGM off during calls | HG TP1001856857, TP1001924935 |
| Head gestures | Nod to answer, shake to reject | HG TP1001856793 |
| Calls | Sidetone on/off; mic on/off by pressing NC/AMB twice during a call (notification every 7 s, can be muted) | HG TP1001856857, TP1001856793 |
| Voice assistants | Phone assistant (hold the touch panel), Google Assistant, Tencent Xiaowei, Sony Voice Control ("Hey headphones") | HG TP1001872736, TP1001858906 |
| Multipoint | 2 devices; 8 paired devices remembered | HG TP1001863603, TP1001856855 |
| Not usable over LE Audio | Voice assistant, Quick Access, Service Link, sound quality mode, easy pairing, Auto Switch | HG TP1001856857 footnote |
| Water in USB-C port | Detected from FW 2.0.0, with voice guidance and an app notice | HG TP1001856824, TP1001856908 |

Touch gestures (HG TP1001867598), all fixed:

| Gesture | Function |
|---|---|
| Tap twice | Play / pause; answer or end a call |
| Swipe forward / backward | Next / previous track (hold to fast-forward / rewind) |
| Swipe up / down | Volume up / down (hold to keep changing) |
| Hold a finger | Voice assistant; reject a call |
| Cover the panel with the hand | Quick Attention while covered |

## 3. Features in Sony's help guide

From "What you can do with the Sony | Sound Connect app" (HG TP1001856857); * = not available over LE Audio:

| Group | Features |
|---|---|
| Sound | Equalizer presets and custom EQ, DSEE Extreme on/off and status, BGM effects, 360 Upmix for Cinema, spatial sound and head tracking (360 Reality Audio), codec display |
| Noise | NC and Ambient Sound adjustment, NC/AMB button pattern, Auto Ambient Sound on/off and sensitivity, Adaptive Sound Control, Speak-to-Chat with detection setup |
| Controls | Touch panel on/off, Quick Access*, voice assistant*, Voice Control language, head gestures, play/pause/skip, music and call volume |
| Connection | Sound quality mode*, multipoint, LE Audio connection setting, LE Audio status, easy pairing*, Auto Switch with speakers*, Service Link* |
| Power and wear | Battery level, power off, auto power off when removed, pause and resume on removal |
| Calls | Sidetone on/off, mic on/off during calls |
| Other | Voice guidance on/off, language and volume; safe listening; Auto Play; firmware update; reset; firmware version |

## 4. Reconstructed support-function lists

### 4.1 Table 1

| FT | Name | Confidence | Evidence |
|---|---|---|---|
| 0x12 | CODEC_INDICATOR | High | HG codec display |
| 0x13 | UPSCALING_INDICATOR | High | HG "Display the DSEE Extreme function setting status" |
| 0x20 | BATTERY_LEVEL_INDICATOR | Medium | GB and GBM use BatterySingle `22 00`; MOS reads either 0x20 or 0x28 |
| 0x28 | BATTERY_LEVEL_WITH_THRESHOLD | Low (alternative) | `22 08`; GB's coordinator notes battery as "probably not working" |
| 0x23 | POWER_OFF | High | HG; GBM PowerOffFromPhone; MOS Power Off works |
| 0x25 | AUTO_POWER_OFF_WITH_WEARING_DETECTION | High | HG; GB AutomaticPowerOffWhenTakenOff |
| 0x42 | CLASSIC_ONLY_LE_CLASSIC_SETTING | Medium | HG "LE Audio Priority" path for Xperia 1 IV/5 IV; X5 lists it |
| 0x45..0x4f | "can't be used with LE Audio" markers | Medium | HG LE footnote; X5 lists them over LE only |
| 0x57 | PRESET_EQ_AND_ERRORCODE | Medium-high | MOS `HeadphonesV2T1.cpp`: "WH-1000XM6 answers PRESET_EQ_AND_ERRORCODE: 10 bands, 13 steps, 9 presets" |
| 0x69 | AMBIENT_SOUND_CONTROL_MODE_SELECT (NC/AMB toggle) | Medium-high | MOS NC/AMB button setting works and is gated on 0x69 |
| 0x6d | MODE_NC_ASM_..._NOISE_ADAPTATION (type 0x19) | High | GBM b71d92f82dad (type 0x19, 9-byte reply); MOS adaptive ambient gated on 0x6d |
| 0x70 | AUTO_NCASM (Adaptive Sound Control) | High | HG |
| 0x90, 0x93, 0x94 | Alerts | Medium | X5; needed for multipoint, LE switch, assistant |
| 0xa1 | PLAYBACK_CONTROLLER_WITH_CALL_VOLUME_ADJUSTMENT | Medium | GBM, MOS volume 0..30; 0xa2 likely over LE as on X5 |
| 0xb2 | GATT_CONNECTABLE (Service Link) | High | HG |
| 0xb0, 0xb6, 0xb7, 0xb8 | SAR, SAR optimisation, head tracker, integrated auto play | Low | HG spatial sound, head tracking, Auto Play; variants copied from X5 |
| 0xc1, 0xc2 | Action log, time-series operation log | High | MOS PR #63: the XM6 logs `unitRemove` / `unitWear` there |
| 0xd1 | GENERAL_SETTING_1 = SIDETONE_SETTING | High | MOS PR #20 capture |
| 0xd2 | GENERAL_SETTING_2 = MULTIPOINT_SETTING | High | MOS PR #20 capture |
| 0xd3 | GENERAL_SETTING_3 = TOUCH_PANEL_SETTING | High | MOS PR #20 capture; GBM 564e8eff5393 uses d3 for the touch sensor |
| 0xe2 | UPSCALING_AUTO_OFF (DSEE Extreme) | Medium-high | HG; MOS DSEE gated on 0xe2. 0xed possible instead |
| 0xe5 | UPMIX_CINEMA | High | MOS issue #76 (Cinema status `e3 04`) |
| 0xe6 | LISTENING_OPTION | High | MOS listening mode gated on 0xe6, works on the XM6 |
| 0xeb | BGM_MODE_SMALL_MIDDLE_LARGE_AND_ERRORCODE | High | MOS issue #76: capability 0xeb, status `e3 09 st n codes` |
| 0xe7 | CONNECTION_MODE_CLASSIC_AUDIO_LE_AUDIO | High | HG 3 modes with Low latency; X5 lists it |
| 0xf1 | PLAYBACK_CONTROL_BY_WEARING_REMOVING | High | HG; GB PauseWhenTakenOff |
| 0xf4 | VOICE_ASSISTANT_SETTINGS | High | MOS issue #76 (voice assistant settings work). 0xf5 is not listed, yet the XM6 pushes wake-word status (MOS `HeadphonesV2T1.cpp`) |
| 0xf9 | RESET_SETTINGS | High | HG initialize |
| 0xfc | SMART_TALKING_MODE_TYPE2 (Speak-to-Chat) | High | GB SpeakToChatConfig on type 0c; MOS STC gated on 0xfc |
| 0xfd | QUICK_ACCESS | High | HG |
| 0xff | HEAD_GESTURE_ON_OFF_TRAINING | High | HG head gestures |
| 0x10, 0x14..0x17, 0x27, 0x34 | Platform functions | Low / guess | X5 lists them |
| 0x11 | CONNECTION_STATUS (L/R link) | Absent (guess) | A headset has no L/R link |
| 0x21, 0x22, 0x29, 0x2a | L/R and case battery | Absent (high) | One battery, no case |
| 0xf3, 0xfe | Assignable touch | Absent (medium) | Fixed gestures in HG; MOS lists only touch on/off and the NC/AMB button for the XM6 |
| 0xf6 | Fit test | Absent (high) | Not in HG |
| 0x6b, 0x68 | NC types 17 / 15 | Absent (high) | The XM6 answers on 0x19 (GBM) |
| 0x50 | PRESET_EQ | Absent or secondary (medium) | MOS picks 0x50 first if listed; it got a type 04 capability |

### 4.2 Table 2

| FT | Name | Confidence | Evidence |
|---|---|---|---|
| 0x30 or 0x32 | Pairing device management | High (variant medium) | MOS multipoint device list works; HG 8 paired devices |
| 0x42 | Voice guidance with language switch and volume | High | HG language change takes about 10 minutes (MTK transfer) plus volume; MOS voice guidance volume works; GBM notes the language needs Sony's app |
| 0x52 | SAFE_LISTENING_HBS_2 | Low | HG safe listening; HBS (headband) variant is a guess; MOS sound pressure works |
| 0x54 | SAFE_VOLUME_CONTROL | Medium | HG guidance "The [Safe volume control] limits the volume" |
| 0x60, 0x61, 0x63, 0x6f | LE Audio functions | Medium | X5 lists them; HG LE Audio |
| 0xf0 | WEARING_STATUS_CHECKER | Medium-high | MOS PR #63, read on a real XM6 |
| 0xf2 | QUICK_ACCESS_EASY_SETTING | Low | X5 |
| 0xf5 | SONY_VOICE_ASSISTANT (Voice Control) | Low | HG Voice Control; name from MOS FunctionType table (newer than SunPods' SC build) |
| 0xf8 | LINK_AUTO_SWITCH_FOR_HEADSETS | Medium | HG Auto Switch with speakers |
| 0xf9 | MIC_ON_OFF_BY_HEADPHONE_OPERATION | Medium | HG mic on/off with NC/AMB |
| 0x23 | USB_SUBMERSION | Low | HG water detection in the USB-C port |

### 4.3 Connect replies

| Item | Value | Confidence |
|---|---|---|
| Protocol info | `01 00 03 00 30 13 00 00` | High (FW 2.0.2, GBM) |
| Model name | `05 01 0a 57 48 2d 31 30 30 30 58 4d 36` ("WH-1000XM6") | High |
| Firmware | `05 02 05 33 2e 31 2e 35` ("3.1.5") | format high, value illustrative |
| Series, colour | `05 03 30 cc` | series guess; colour codes in section 9 |

## 5. Layouts, and what SunPods sends to the WF-1000XM5 today

| Feature | WF-1000XM5 (SunPods today) | WH-1000XM6 | What SunPods must change |
|---|---|---|---|
| Battery | `readBattery` sends `22 09` (or `22 01`), needs a 6+ byte reply, reads left at [2], right at [4]; case `22 0a` | `22 00` → `23 00 lvl chg` (4 bytes), or `22 08` → `23 08 lvl chg thr`. No case | Today the read fails outright (no `23 09` reply). Add a single-battery path picked from 0x20 / 0x28 and a model without a case |
| Noise control read | `66 17`; `ModeReport.parse` accepts only types 0x15 and 0x17 | `66 19` → `67 19 vcs onoff mode asm level naOn naSens` (9 bytes) | `Fn.ncAsmType(0x6d)` already maps to 0x19, but the parser rejects it. Accept 0x19 (same offsets 3..6) and keep bytes 7..8 |
| Noise control set | `68 tt 01 en amb focus level` (7 bytes) | `68 19 01 en amb focus level naOn naSens` (9 bytes) | Send 9 bytes; echo naOn and naSens from the last read. SC's validator for type 19 needs exactly 9 |
| Auto Ambient Sound | none | bytes 7, 8 of type 19: naOn (00 off, 01 on), naSens (00 standard, 01 high, 02 low) | New switch and sensitivity |
| NC/AMB pattern | `fa 03` → `fb 03 01 00 01 00 fn`, SET `fc 03 ...` | `66 30` → `67 30 fn`; SET `68 30 fn`; NTFY `69 30 fn`; capability `60 30` → `61 30 key` | Read the cycle from NC_AMB_TOGGLE when 0x69 is listed. fn: 01 NC/Amb/Off, 02 NC/Amb (default), 03 NC/Off, 04 Amb/Off |
| EQ read | `56 00` → 6 values, offset 10: CLEAR BASS + 5 bands | `56 00` → `57 00 preset 0a v1..v10`, 13 steps, wire 0..12, flat 06 (dB = wire - 6), no CLEAR BASS | `readEq` would read 6 of the 10 bands with the wrong offset. Take band count and steps from `51 04`, frequencies from `5b 00` |
| EQ bands | `58 00 a0 06 cb b1..b5` | `58 00 a0 0a v1..v10`; bands 31, 63, 125, 250, 500 Hz, 1, 2, 4, 8, 16 kHz | `setEqBands` requires 5 bands; generalise |
| EQ presets | 0x10..0x17 (Bright to Speech), a0..a2 | Only 0x30 HEAVY, 0x31 CLEAR, 0x32 HARD, 0x33 SOFT, 0xa0 CUSTOM and user settings 0xa1.. are accepted. 0x10..0x17 and 0x20..0x23 are ACKed and ignored (no NTFY) on FW 2.x and 3.0.0 | Build the preset list from `51 04` (id plus name); never offer the XM5 list |
| EQ capability | not read | `50 04 01` → `51 04 0a 0d 09 {id L name}*9` | New; the 9 ids are not published (guess in section 6) |
| DSEE | `e6 01` → `e7 01 v` | same; type 02 DSEE_HX_AI (Extreme) | none; 0xed variant possible |
| Sound quality | probes `e6 00`, then `e6 02` (the XM5 answers 02, unmappable) | 0xe7: `e6 05` → `e7 05 pm`; SET `e8 05 pm 00`; NTFY `e9 05 pm ss`. pm 00 sound quality (default), 01 stable, 02 low latency (LE Audio) | Use type 05 when 0xe7 is listed; map 02 to "Low latency" |
| LE Audio switch | `46 0c` / `48 0c onoff 00`, alerts 0x09 / 0x2e | same function (0x42) for the Xperia path; elsewhere through pm 02 above | none for 0x42; add pm 02 |
| Codec | `12 02` | `13 02 10` LDAC, `01` SBC, `02` AAC, `30` LC3 | none |
| Speak-to-Chat | `f6 0c`, `fa 0c` → `fb 0c sens outTime` | same type 0c | none |
| Touch functions | 0xf3: `f6 03` / `f8 03 02 l r` | none; touch panel on/off is general setting d3 `TOUCH_PANEL_SETTING` | Hide touch menus; add a title for TOUCH_PANEL_SETTING in `GeneralSetting.GS_TITLES` |
| General settings | d1 SIDETONE, d2 MULTIPOINT | d1 SIDETONE, d2 MULTIPOINT (summary key `MULTIPOINT_SETTING_SUMMARY_LDAC_AVAILABLE`), d3 TOUCH_PANEL_SETTING | Works as is (found by key) |
| Pause when removed | `f6 01` → `f7 01 v` | same | none |
| Auto power off | type 05, options `10 11` | type 05, options expected `10 11` ("when removed" means 15 minutes after removal) | Copy: "15 minutes after you take them off" |
| Head gestures | `f6 0f` | same | none |
| Voice assistant | `f0 04` → `f1 04 03 02 31 ff` | 0xf4, key type probably 01 (touch panel), adds 0x34 Sony Voice Control and 0x33 Tencent Xiaowei | List from the capability |
| Quick Access | `f1 0d 00 00 ...` (L/R key, touch) | expected `f1 0d 01 01 ...` (NC/AMB key, button, double and triple press) | Label actions as button presses |
| Listening mode | none | BGM (0xeb, type 09) and Cinema (0xe5, type 04), layouts below | New; check status before SET (see warning) |
| Wearing status | none | T2 `f2 00` → `f3 00 st` (00 worn, 04 not worn); not pushed, but logged as `unitRemove` / `unitWear` | Optional: on-head indicator |
| Mic on/off button | none | T2 0xf9, layout not decoded | Optional |
| Voice prompts | T2 0x42 | T2 0x42; language list unknown | none; same open question on `48 01` length |
| Playback, volume | `a6 20` → `a7 20 v` | same, 0..30 (`a1 01 1f ..`) | none |
| Power off, reset | `24 03 01`, `f8 09 00` | same | none |

Listening mode layouts (MOS generated `ProtocolV2T1.hpp`, issue #76):

| FT / type | Message | Layout |
|---|---|---|
| 0xeb / 09 | capability | `e0 09` → `e1 09 n f1..fn` (AudioExclusiveFunctionType: 00 EQ, 01 DSEE, 02 head tracker, 03 LE Audio, 07 spatial, 08 BGM, 09 Cinema, 0a Sony assistant ...) |
| 0xeb / 09 | status | `e2 09` → `e3 09 ed n c1..cn`, NTFY `e5 09 ...`. One count byte only (issue #76 corrects libmdr). c = StatusErrorCodeType: 00 calling, 02/03 LE Audio, 05/06/07 voice UI Google/Sony/Tencent, 08 Cinema on, 0b not standard |
| 0xeb / 09 | param | `e6 09` → `e7 09 onoff room`; SET `e8 09 onoff room`; NTFY `e9 09 ...`. onoff: 00 on, 01 off. room: 00 small, 01 middle, 02 large |
| 0xe4 / 03 | alternative | same param; status is plain `e3 03 ed` |
| 0xe5 / 04 | Cinema | status `e2 04` → `e3 04 ed` (01 while in a call); param `e6 04` → `e7 04 onoff`; SET `e8 04 onoff` |

Warning (MOS issue #76, FW 3.1.5): `e8 09 00 00` (BGM on) while Voice Control is enabled makes the headphones reboot. Sound Connect only sends the SET for the mode that changed, and only when the status byte says enabled. SunPods must do the same.

Wearing and logs: enable the time-series log with LOG SET_STATUS (`c4 01 00`, type 01, enable), then the XM6 sends `c9 01 L "unitRemove"` / `"unitWear"` with a one-byte length at [2] (MOS PR #63). GBM also saw a 4-byte `f5 10 xx yy` that flips with wearing; type 0x10 is HEAD_GESTURE_TRAINING, so that reading is uncertain.

## 6. Test vectors

Built from the layouts above; values are realistic examples. Frame type 0x0c unless marked T2 (0x0e). "obs." = observed on a real XM6 by GBM or MOS.

| Feature | GET (or SET) | Headphones reply | Notes |
|---|---|---|---|
| Protocol info | `00 00` | `01 00 03 00 30 13 00 00` | obs. FW 2.0.2 |
| Model name | `04 01` | `05 01 0a 57 48 2d 31 30 30 30 58 4d 36` | "WH-1000XM6" |
| Firmware | `04 02` | `05 02 05 33 2e 31 2e 35` | "3.1.5" |
| Series, colour | `04 03` | `05 03 30 01` | PREMIUM, black (guesses) |
| Support functions T1 | `06 00` | see section 7 | reconstructed, 42 functions |
| Support functions T2 | T2 `06 00` | see section 7 | reconstructed, 13 functions |
| Battery | `22 00` | `23 00 50 00` | 80 %, not charging |
| Battery, charging | (unprompted) | `25 00 4f 01` | 79 %, charging |
| Battery, alternative | `22 08` | `23 08 50 00 0a` | if 0x28 is listed; threshold 10 % from HG low-battery flash |
| Power off | `24 03 01` | (link drops) | |
| Auto power off capability | `20 05` | `21 05 02 10 11` | when removed, off (guess, matches the app options) |
| Auto power off | `26 05` | `27 05 10 00` | when removed (default) |
| Auto power off set | `28 05 11 00` | ACK, then `29 05 11 00` | Do not turn off |
| NC capability | `60 19` | `61 19 02 00 00 14 01 01 00 14 01` | NORMAL and VOICE, 0..20 (range guess) |
| NC status | `62 19` | `63 19 00` | usable |
| NC read | `66 19` | `67 19 01 01 00 00 14 00 00` | NC on, auto ambient off, standard (layout obs.) |
| NC set Ambient 10 | `68 19 01 01 01 00 0a 00 00` | ACK, then `69 19 01 01 01 00 0a 00 00` | |
| NC set Ambient + auto, high | `68 19 01 01 01 00 0a 01 01` | ACK, then `69 19 01 01 01 00 0a 01 01` | |
| NC set Off | `68 19 01 00 00 00 0a 00 00` | ACK, then `69 19 01 00 00 00 0a 00 00` | |
| NC/AMB key | `60 30` | `61 30 04` | key 04 NC_AMB_KEY (guess) |
| NC/AMB pattern | `66 30` | `67 30 02` | NC / Ambient (HG default) |
| NC/AMB pattern set | `68 30 01` | ACK, then `69 30 01` | NC / Ambient / Off |
| Adaptive Sound Control | `70 00` | `71 00 03 08 10 20` | NC type 19 (af 08), EQ, Speak-to-Chat |
| EQ capability | `50 04 01` | `51 04 0a 0d 09 00 00 30 00 31 00 32 00 33 00 a0 00 a1 00 a2 00 a3 00` | 10 bands, 13 steps, 9 presets (obs.); the 9 ids are a guess, names empty |
| EQ band info | `5a 00` | `5b 00 0a 01 00 1f 01 00 3f 01 00 7d 01 00 fa 01 01 f4 01 03 e8 01 07 d0 01 0f a0 01 1f 40 01 3e 80` | 31 Hz to 16 kHz, all HZ (obs.) |
| EQ status | `52 00` | `53 00 00` | |
| EQ read | `56 00` | `57 00 a0 0a 08 07 06 06 06 06 06 07 08 08` | Custom: +2 +1 0 0 0 0 0 +1 +2 +2 |
| EQ preset | `58 00 31 00` | ACK, then `59 00 31 0a 06 06 06 06 06 06 06 06 06 06` | Clear; preset bands illustrative |
| EQ preset rejected | `58 00 10 00` | ACK only | Bright: no NTFY, no change (obs., PR #48) |
| DSEE capability | `e0 01` | `e1 01 02` | DSEE_HX_AI (Extreme) |
| DSEE read / set | `e6 01`; `e8 01 01` | `e7 01 00`; ACK, then `e9 01 01` | |
| DSEE indicator | `12 03` | `13 03 02 00` | |
| Sound quality capability | `e0 05` | `e1 05 03 00 01 02 01 00` | 3 modes, GATT exclusive with LDAC |
| Sound quality | `e6 05` | `e7 05 00` | Prioritize Sound Quality (default) |
| Sound quality set | `e8 05 01 00` | ACK, then `e9 05 01 00` | stable |
| LE / Classic setting | `46 0c` | `47 0c 01` | Classic only |
| Codec | `12 02` | `13 02 10` | LDAC |
| BGM capability | `e0 09` | `e1 09 01 09` | exclusive with Cinema (guess) |
| BGM status | `e2 09` | `e3 09 00 00` | enabled, no blockers |
| BGM status, blocked | (unprompted) | `e5 09 01 02 06 00` | Voice Control on, in a call (obs., issue #76) |
| BGM read / set | `e6 09`; `e8 09 00 01` | `e7 09 01 00`; ACK, then `e9 09 00 01` | off; then on, middle room |
| Cinema | `e2 04`; `e6 04` | `e3 04 00`; `e7 04 01` | usable; off |
| Speak-to-Chat | `f6 0c`; `fa 0c` | `f7 0c 01 01`; `fb 0c 00 01` | off (HG default), auto sensitivity, about 15 s |
| Pause when removed | `f6 01` | `f7 01 00` | on |
| Head gestures | `f6 0f` | `f7 0f 01` | off |
| Voice assistant | `f0 04`; `f6 04` | `f1 04 01 03 30 31 34`; `f7 04 30` | touch panel; phone, Google, Sony (guess) |
| Quick Access | `f0 0d`; `f6 0d` | `f1 0d 01 01 02 01 00 00 02 00 00`; `f7 0d 02 00 00` | NC/AMB key, button (guess) |
| Service Link | `b0 10`; `b6 10` | `b1 10 01 00`; `b7 10 01` | off |
| Sidetone title | `d0 d1 01` | `d1 d1 00 01 10` "SIDETONE_SETTING" `18` "SIDETONE_SETTING_SUMMARY" | obs. (keys) |
| Multipoint title | `d0 d2 01` | `d1 d2 00 01 12` "MULTIPOINT_SETTING" `29` "MULTIPOINT_SETTING_SUMMARY_LDAC_AVAILABLE" | obs. (keys) |
| Touch panel title | `d0 d3 01` | `d1 d3 00 01 13` "TOUCH_PANEL_SETTING" `00` | obs. (keys) |
| General settings | `d6 d1`, `d6 d2`, `d6 d3` | `d7 d1 00 01`; `d7 d2 00 01`; `d7 d3 00 00` | sidetone off, multipoint off, touch panel on |
| Touch panel off | `d8 d3 00 01` | ACK, then `d9 d3 00 01` | GBM sends this form |
| Multipoint on | `94 00 00`, then `d8 d2 00 00` | `99 00 07 01`, app answers `98 00 07 01`, then `d9 d2 00 00` | alert code from SC, as for the XM5 |
| Playback | `a0 01`; `a2 01` | `a1 01 1f 10`; `a3 01 00 01 00` | 31 music steps (0..30, obs.) |
| Volume | `a6 20` | `a7 20 0b` | |
| Reset | `f8 09 00` | ACK, then `f9 09 00` | |
| Voice guidance | T2 `40 01`; T2 `46 01`; T2 `46 20` | T2 `41 01 03 00 00 00 01 0f ...` (XM5 list); T2 `47 01 00 01`; T2 `47 20 00` | on, English; language list unknown |
| Pairing | T2 `30 02`; T2 `32 02` | T2 `31 02 08 02 00`; T2 `33 02 00 00` | 8 paired, 2 connected (HG) |
| Wearing status | T2 `f2 00` | T2 `f3 00 00` | worn; `04` when taken off |

## 7. SimProfile lines (reconstructed)

Same format as `C710nProfile.kt`: first token is the frame type in decimal (12 = 0x0c, 14 = 0x0e). Everything here is reconstructed from the vectors above, not recorded. Lines marked obs. in section 6 carry observed layouts with example values.

```
12 01 00 03 00 30 13 00 00
12 05 01 0a 57 48 2d 31 30 30 30 58 4d 36
12 05 02 05 33 2e 31 2e 35
12 05 03 30 01
12 07 00 2a 10 ff 12 ff 13 ff 14 ff 15 ff 16 ff 17 ff 20 ff 23 ff 25 36 27 ff 34 3f 42 3a 57 11 69 ff 6d 08 70 01 90 ff 93 ff 94 ff a1 12 b0 42 b2 43 b6 1d b7 15 b8 1f c1 ff c2 ff d1 3b d2 20 d3 ff e2 1b e5 ff e6 ff e7 1a eb ff f1 38 f4 23 f9 41 fc 0d fd 2b ff 2d
14 07 00 0d 32 ff 42 3d 52 ff 54 ff 60 ff 61 ff 63 ff 6f 4a f0 ff f2 ff f5 ff f8 ff f9 ff
12 13 02 10
12 13 03 02 00
12 23 00 50 00
12 21 05 02 10 11
12 23 05 00
12 27 05 10 00
12 61 19 02 00 00 14 01 01 00 14 01
12 63 19 00
12 67 19 01 01 00 00 14 00 00
12 61 30 04
12 67 30 02
12 71 00 03 08 10 20
12 51 04 0a 0d 09 00 00 30 00 31 00 32 00 33 00 a0 00 a1 00 a2 00 a3 00
12 5b 00 0a 01 00 1f 01 00 3f 01 00 7d 01 00 fa 01 01 f4 01 03 e8 01 07 d0 01 0f a0 01 1f 40 01 3e 80
12 53 00 00
12 57 00 a0 0a 08 07 06 06 06 06 06 07 08 08
12 e1 01 02
12 e3 01 00
12 e7 01 00
12 e1 05 03 00 01 02 01 00
12 e3 05 00 00
12 e7 05 00
12 43 0c 00
12 47 0c 01
12 e1 09 01 09
12 e3 09 00 00
12 e7 09 01 00
12 e3 04 00
12 e7 04 01
12 f3 0c 00 00
12 f7 0c 01 01
12 fb 0c 00 01
12 f3 01 00
12 f7 01 00
12 f7 0f 01
12 f1 04 01 03 30 31 34
12 f3 04 00
12 f7 04 30
12 f1 0d 01 01 02 01 00 00 02 00 00
12 f3 0d 00
12 f7 0d 02 00 00
12 b1 10 01 00
12 b3 10 00
12 b7 10 01
12 d1 d1 00 01 10 53 49 44 45 54 4f 4e 45 5f 53 45 54 54 49 4e 47 18 53 49 44 45 54 4f 4e 45 5f 53 45 54 54 49 4e 47 5f 53 55 4d 4d 41 52 59
12 d1 d2 00 01 12 4d 55 4c 54 49 50 4f 49 4e 54 5f 53 45 54 54 49 4e 47 29 4d 55 4c 54 49 50 4f 49 4e 54 5f 53 45 54 54 49 4e 47 5f 53 55 4d 4d 41 52 59 5f 4c 44 41 43 5f 41 56 41 49 4c 41 42 4c 45
12 d1 d3 00 01 13 54 4f 55 43 48 5f 50 41 4e 45 4c 5f 53 45 54 54 49 4e 47 00
12 d3 d1 00
12 d3 d2 00
12 d3 d3 00
12 d7 d1 00 01
12 d7 d2 00 01
12 d7 d3 00 00
12 a1 01 1f 10
12 a3 01 00 01 00
12 a7 20 0b
12 a7 21 05
14 41 01 03 00 00 00 01 0f 01 02 03 04 05 06 07 08 09 0a 0b 0d 0f 10 f0
14 43 01 00 00
14 47 01 00 01
14 47 20 00
14 31 02 08 02 00
14 33 02 00 00
14 f3 00 00
```

Suggested `confirm` cases: `d8 d2` → alert 0x07 (multipoint, as XM5); `48 0c` → 0x09 or 0x2e (as XM5); `e8 05 02 ..` → an LE switch alert (code unknown). Suggested `onSet`: EQ preset-only SET keeps bands (`SimProfiles.eqSet` works for 10 values); EQ SET with an id outside 0x30..0x33, 0xa0..0xa5 is ACKed and ignored; `e8 09 00 ..` refused while `e3 09` reports 01. Model entry: ratings 30 h NC on / 40 h off (AAC), no case (case charges 0), name regex `WH[- ]?1000XM6` (must not match WF-1000XM6).

## 8. Differences from the WH-1000XM5 headphones

| Area | WH-1000XM5 | WH-1000XM6 | Source |
|---|---|---|---|
| Protocol info | `01 00 03 00 00 00 00 00` (FW 1.1.3) | `01 00 03 00 30 13 00 00` (FW 2.0.2) | GB, GBM comments |
| Noise control | type 0x15 (GB AmbientSoundControl) | type 0x19 with Auto Ambient Sound and sensitivity | GB XM5 coordinator; GBM |
| EQ | CLEAR BASS + 5 bands, classic presets | 10 bands, 13 steps, no CLEAR BASS, presets 0x30..0x33 | GB EqualizerWithCustomBands; MOS |
| Touch panel switch | general setting d1 | general setting d3 (d1 is sidetone) | MOS PR #20 |
| Sidetone | no general setting | d1 SIDETONE_SETTING | MOS PR #20 |
| LE Audio | none | LC3, Auracast, GMAP, Low latency mode | HG |
| New features | none of these | Listening mode (BGM, 360 Upmix for Cinema), head gestures, Voice Control, mic mute button, wearing status | HG, MOS |
| Same | single battery `22 00`, Speak-to-Chat type 0c, multipoint d2, Quick Access, pause when removed, auto power off when removed | | GB |
| Hardware | QN1 processor, 8 NC mics | QN3 (7x faster than QN1), 12 NC mics (1.5x XM5), folding design, 254 g | SONY press release; Sony UK spec |

Gadgetbridge master still drives the XM6 with the XM5's type 0x15, which is why its coordinator says ambient sound and others are "probably not working"; the GBM fork found type 0x19 on a real unit. Trust GBM and MOS over GB master here.

## 9. Colours, codes and names

| Colour | Sony suffix | Launch | ModelColor byte | Confidence (byte) |
|---|---|---|---|---|
| Black | /B (`WH1000XM6B.CE7`) | May 2025 | 0x01 BLACK | High (same as XM5) |
| Platinum Silver | /S | May 2025 | 0x03 SILVER | Medium (XM5 Platinum Silver is 0x03 per `SonyFeatures.kt`) |
| Midnight Blue | /L | May 2025 | 0x05 BLUE | Guess |
| Sand Pink | /P | February 2026 | 0x06 PINK | Guess (XM5 Smoky Pink is 0x06) |
| Sandstone | /T | May 2026 | 0x0b CREAM or 0x0d BROWN | Unknown |
| Olive Gray (Grey) | /G | August 2026 (Japan November 2026) | 0x08 GREEN or 0x09 GRAY | Unknown |

Bluetooth names: Classic `WH-1000XM6`; LE `LE_WH-1000XM6` (MOS PR #44). Gadgetbridge matches `.*WH-1000XM6.*`. Google Fast Pair and audio switch are supported (HG TP1001872732). Sources: Sony UK spec page SKUs; sony.ca variant URLs `wh1000xm6-b/-s/-l/-p/-t/-g`; Sony press releases 2025-05-15 and 2026-02-12; Sony EU press release for Olive Gray.

## 10. Product photos and dimensions

References only; Sony's own image servers. Primary shots (transparent PNG):

| Colour | URL |
|---|---|
| Black | https://sony.scene7.com/is/image/sonyglobalsolutions/WH1000XM6_Primary_image_Black?$primaryshotPreset$&fmt=png-alpha |
| Platinum Silver | https://sony.scene7.com/is/image/sonyglobalsolutions/WH1000XM6_Primary_image_Platinum_Silver?$primaryshotPreset$&fmt=png-alpha |
| Midnight Blue | https://sony.scene7.com/is/image/sonyglobalsolutions/WH1000XM6_Primary_image_Midnight_Blue?$primaryshotPreset$&fmt=png-alpha |
| Sand Pink | https://sony.scene7.com/is/image/sonyglobalsolutions/WH1000XM6_Primary_image_Sand-pink?$primaryshotPreset$&fmt=png-alpha |
| Sandstone | https://sony.scene7.com/is/image/sonyglobalsolutions/WH-1000XM6_Primary_image_Sandstone?$primaryshotPreset$&fmt=png-alpha |
| Olive Gray | https://sony.scene7.com/is/image/sonyglobalsolutions/WH1000XM6_Primary_image_Olive_Gray?$primaryshotPreset$&fmt=png-alpha |

Angle shots, 1534 x 1083: `https://sony.scene7.com/is/image/sonyglobalsolutions/<name>?$productIntroPlatemobile$&fmt=png-alpha`, names from the Sony UK product page (alt text and SKU list):

| View | Black | Platinum Silver | Midnight Blue | Sand Pink | Sandstone | Olive Gray |
|---|---|---|---|---|---|---|
| Side | WH1000XM6_Product_intro_01_M | _08_M | _12_M | _16_M | WH1000XM6_Product_intro_23 | WH-1000XM6_standard_Olive_Gray_01_Product_intro_M |
| Front | _02_M | _09_M | _13_M | _17_M | _24 | ..._Olive_Gray_02_Product_intro_M |
| Rear | _03_M | _10_M | _14_M | _18_M | none | none |
| Front, swivel | _04_M | none | none | _19_M | none | none |
| Cups turned in / folded | _05_M (side), _06_M (top-down) | none | none | _20_M, _21_M | _25 | ..._Olive_Gray_03_Product_intro_M |
| In case | _07_M | _11_M | _15_M | _22_M | _26 | ..._Olive_Gray_04_Product_intro_M |

Short names are `WH1000XM6_Product_intro_NN_M`. Sand Pink alt texts are "TBC" on Sony's page; 16, 19 and 22 were checked by eye (side, front swivel, case) and follow the Black order, so 17, 18, 20 and 21 are inferred. Press images (Sony release 2025-05-15, hosted by PR Newswire): all three launch colours https://mma.prnewswire.com/media/2688177/Sony_WH_1000XM6_ColorVariationvBlack_Silver_Blue_1920x1080.jpg; black https://mma.prnewswire.com/media/2688178/Sony_1_WH_1000XM6_standard_black_Large_1.jpg; silver folded with case https://mma.prnewswire.com/media/2688179/Sony_7_WH_1000XM6_foldable2_w_case_silver_Large_1.jpg; black buttons and touch panel https://mma.prnewswire.com/media/2688181/Sony_9_WH_1000XM6_button_black_Large.jpg; Sand Pink (release 2026-02-12) https://mma.prnewswire.com/media/2902660/1_WH_1000XM6_standard_SandPink_Mid.jpg.

Dimensions: Sony does not publish them. Sony UK spec: approx. 254 g, 30 mm driver, closed back, 1.2 m cable. Controls for renders (HG TP1001856813, TP1001861255): left unit has the power / Bluetooth button, NC/AMB button, indicator, headphone jack, USB-C port and a tactile dot; the right unit has the touch panel and no buttons; folds flat and the left unit folds towards the headband into a magnetic case.

## 11. Battery rating and pairing

Battery (HG TP1001856818): music with NC or Ambient on, 30 h (AAC, SBC, LC3) or 26 h (LDAC); NC off, 40 h (36 h LDAC). Calls 24 h (28 h NC off). Wired with power on 28 h (38 h off). Full charge about 3.5 h. 3 minutes gives about 1 h (5 V / 1.5 A) or about 3 h (USB PD 9 V / 2.3 A). Low-battery warning by voice and orange indicator; indicator flashes for 15 s at power-on when 10 % or lower (HG TP1001856820).

Pairing (HG TP1001856855): the first power-on with no pairing data enters pairing mode by itself. Otherwise hold the power (Bluetooth) button about 5 s until the blue indicator double-flashes and the voice says "Pairing". Pairing mode ends after 5 minutes. 8 devices remembered; the 9th replaces the oldest. LE Audio (HG TP1001872731): set Low latency (or LE Audio Priority on Xperia 1 IV/5 IV) in Sound Connect, delete the Classic pairing, hold the power button 5 s, pair again, and check the LE Audio switch in Android's device settings. Reset: hold power and NC/AMB 5 s while charging. Factory reset: power off, hold power and NC/AMB 10 s (blue flashes 4 times).

## 12. Open points to settle on a real WH-1000XM6

| Priority | Probe | Settles |
|---|---|---|
| 1 | `00 00`, then `06 00` on both frame types | Section 4 (battery 0x20 or 0x28, 0x50 vs 0x57, 0xf3 absence, 0xe2 vs 0xed) |
| 2 | `22 00` and `22 08` | Battery variant |
| 3 | `60 19`, `66 19` | Ambient range, Focus on Voice, default naSens |
| 4 | `50 04 01`, `5a 00`, `56 00` | The 9 preset ids and names, band values, whether param uses type 00 or 04 |
| 5 | `60 30`, `66 30` | NC/AMB key and pattern |
| 6 | `20 05` | Auto power off options |
| 7 | `e0 09`, `e2 09`, `e0 04` | Listening mode capability bytes |
| 8 | `f0 04`, `f0 0d` | Assistant list, Quick Access key |
| 9 | `04 03` | Series and colour bytes per colour |
| 10 | T2 `40 01` | Voice guidance language list |

Best unread source: the packet captures attached to MOS issue #76 (`WH-1000XM6-3.1.5-listening-mode-recordings.zip`). Not downloaded for this document; they likely hold the real support-function lists.

## Sources

- Sony help guide WH-1000XM6: https://helpguide.sony.net/mdr/2984/v1/en/index.html (topics under `contents/TP*.html`)
- Gadgetbridge XM6 coordinator: https://codeberg.org/Freeyourgadget/Gadgetbridge/src/branch/master/app/src/main/java/nodomain/freeyourgadget/gadgetbridge/devices/sony/headphones/coordinators/SonyWH1000XM6Coordinator.java
- Gadgetbridge v2 implementation: https://codeberg.org/Freeyourgadget/Gadgetbridge/src/branch/master/app/src/main/java/nodomain/freeyourgadget/gadgetbridge/service/devices/sony/headphones/protocol/impl/v2/SonyProtocolImplV2.java
- Gadgetbridge issue #4974 (XM6 request, FW 1.0.5): https://codeberg.org/Freeyourgadget/Gadgetbridge/issues/4974
- XM6 owner's fork: https://codeberg.org/M2110/Gadgetbridge/commits/branch/sony-wh1000xm6-support
- mos9527 client: https://github.com/mos9527/SonyHeadphonesClient (docs/device-support/WH-1000XM6.md, libmdr/src, libmdr/include/mdr/Generated), PR #20, #44, #48, #63, issue #76
- Sony UK product and spec pages: https://www.sony.co.uk/headphones/products/wh-1000xm6 and https://www.sony.co.uk/headphones/products/wh-1000xm6/spec
- Sony UK firmware 3.1.5: https://www.sony.co.uk/electronics/support/wireless-headphones-bluetooth-headphones/wh-1000xm6/software/00355431
- Sony launch release: https://sony.mediaroom.com/2025-05-15-Sony-Introduces-the-Best-Noise-Cancellation-with-the-WH-1000XM6-Headphones
- Sony Sand Pink release: https://sony.mediaroom.com/2026-02-12-C-O-R-R-E-C-T-I-O-N-Sony-Electronics,-Inc
- Sony EU Olive Gray release: https://www.sony.eu/presscentre/sonys-new-olive-gray-wh-1000xm6-combines-premium-noise-cancellation-with-refined-design
