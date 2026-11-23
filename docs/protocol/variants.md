# MDR v2 message variants, from Sound Connect

Byte-level layouts of every message variant SunPods may meet on Sony earbuds, read from Sound Connect's own message classes (JADX decompile, the build SunPods already uses). Written for implementing the WF-C710N and for the simulator in `EarbudSimulator.kt`.

Class paths are relative to the decompile root `sc_src/sources/`. "SC" means Sound Connect. "(inferred)" marks a reading that the code does not state outright. Anything the code does not settle is listed in the last section.

## 0. Conventions that apply everywhere

| Item | Rule | Source |
|---|---|---|
| Frame type | Table 1 payloads ride on data type `0x0c` (DATA_MDR), table 2 on `0x0e` (DATA_MDR_NO2) | `SonyProtocol.kt`, `mdr/v2/table2/c.java` |
| Payload | `[command, inquiredType, fields...]` | every message class |
| Command block | GET_CAPABILITY = base, RET_CAPABILITY +1, GET_STATUS +2, RET_STATUS +3, SET_STATUS +4, NTFY_STATUS +5, GET_PARAM +6, RET_PARAM +7, SET_PARAM +8, NTFY_PARAM +9, then EXT GET/RET/SET/NTFY at +0xa..+0xd | `mdr/v2/table1/Command.java`, `table2/Command.java` |
| Builder shape | Each class has an inner builder `b`; `b(byte[])` is the validator, `super.d(Command)` writes byte 0, each `write()` appends one byte | e.g. `ff0/l.java` |
| Unsigned | `com.sony.songpal.util.e.m(b)` = `b & 0xff`; `e.k(i)` = `(byte) i` | `com/sony/songpal/util/e.java` |
| EnableDisable | `00` ENABLE, `01` DISABLE | `mdr/v2/EnableDisable.java` |
| OnOffSettingValue | `00` ON, `01` OFF (inverted) | `mdr/v2/OnOffSettingValue.java` |
| NcAsmOnOffValue | `00` OFF, `01` ON (not inverted, NC/ASM only) | `table1/ncasm/param/NcAsmOnOffValue.java` |
| RequestResult | `00` ACCEPTED, `01` DECLINED | `mdr/v2/RequestResult.java` |
| Strings | `[len][UTF-8 bytes]`, len at most 128 | `mdr/v2/table1/e.java`, `util/z.java` |
| BD address | 17 ASCII bytes `XX:XX:XX:XX:XX:XX`, no length byte | `util/a.java` |
| SET answered by NTFY | SC sends most SETs without waiting for a RET and updates its UI from the matching NTFY. Some table 2 SETs wait for the NTFY (voice guidance, safe listening, LEA). A simulator should answer every SET with an ACK and then the NTFY carrying the new value | agent reading of `le0/e`, `k70/h`, `lt/u`, `d30/q` |
| Timeout | SC waits 2000 ms per request | `u70/o1.java` |
| Capability cache | SC skips the capability fetch when the counter in CONNECT_RET_CAPABILITY_INFO and the display language match what it stored | `wv/e.java` D(), E() |

## 1. Connect: protocol, capability, device info, support functions

### 1.1 Messages

| Message | Layout | Notes | Class |
|---|---|---|---|
| GET_PROTOCOL_INFO | `00 00` | len 2 | `ff0/c.java` |
| RET_PROTOCOL_INFO | `01 00 v3 v2 v1 v0 t1 t2` | len 8. `v` = protocol version, 32-bit big-endian. `t1`, `t2` = EnableDisable: table 1 / table 2 support-function lists available (00 = yes). SC only asks GET_SUPPORT_FUNCTION on a table whose byte is 00 | `ff0/k.java`, `wv/e.java` K(), y0() |
| GET_CAPABILITY_INFO | `02 00` | | `ff0/a.java` |
| RET_CAPABILITY_INFO | `03 00 counter L str[L]` | len must be L+4 and greater than 4, so L at least 1. `counter` drives the capability cache | `ff0/e.java` |
| GET_DEVICE_INFO | `04 tt` | tt = DeviceInfoType: 01 MODEL_NAME, 02 FW_VERSION, 03 SERIES_AND_COLOR_INFO, 04 INSTRUCTION_GUIDE | `ff0/b.java` |
| RET_DEVICE_INFO 01 / 02 | `05 tt L str[L]` | len = L+3 | `ff0/i.java`, `ff0/g.java` |
| RET_DEVICE_INFO 03 | `05 03 series colour` | len 4. ModelSeries: 00 NO_SERIES, 10 EXTRA_BASS, 11 ULT_POWER_SOUND, 20 HEAR, 30 PREMIUM, 40 SPORTS, 50 CASUAL, 60 LINK_BUDS, 70 NECKBAND, 80 LINKPOD, 90 GAMING. ModelColor: 00 DEFAULT, 01 BLACK, 02 WHITE, 03 SILVER, 04 RED, 05 BLUE, 06 PINK, 07 YELLOW, 08 GREEN, 09 GRAY, 0a GOLD, 0b CREAM, 0c ORANGE, 0d BROWN, 0e VIOLET, 11..1e the "-I" variants | `ff0/j.java`, `table1/connect/param/ModelSeries.java`, `util/modelinfo/ModelColor.java` |
| RET_DEVICE_INFO 04 | `05 04 n cat1..catn` | GuidanceCategory list | `ff0/h.java` |
| GET_SUPPORT_FUNCTION (T1 and T2) | `06 00` | len 2, sent on frame 0x0c for table 1 and 0x0e for table 2 | `ff0/d.java`, `gg0/a.java` |
| RET_SUPPORT_FUNCTION (T1 and T2) | `07 00 n {fn order}*n` | len exactly 2n+3. `fn` = FunctionType code for that table. `order` = display priority: SC sorts the list by it (`ze0/c.java` compareTo, `ze0.c.g()`); XM5 uses `ff` for functions without their own card. Unknown codes are dropped | `ff0/l.java`, `gg0/b.java` |

Table 2 rule: SC rejects a table 2 list that holds both `0x54` SAFE_VOLUME_CONTROL and `0x55` MAX_VOLUME_LEVEL_LIMIT (`gg0/b.java` e()).

Protocol versions SC accepts are listed in `wv/e.java` field `f82444b` (0x01000000 to 0x03003032). The XM5 sends `0x03003018`. A version outside the list makes SC stop with UNAVAILABLE_PROTOCOL_VERSION.

### 1.2 FunctionType to inquired type

Mapping from `u70/f.java` (which handler is built for which FunctionType) and `wv/e.java` (which capability is fetched at connect). Table 1 unless marked T2.

| FunctionType | Name (short) | Command block, inquired type |
|---|---|---|
| 0x11 | CONNECTION_STATUS | COMMON 0x12, type 01 |
| 0x12 | CODEC_INDICATOR | COMMON 0x12, type 02 |
| 0x13 | UPSCALING_INDICATOR | COMMON 0x12, type 03 |
| 0x1b | DISPLAY_FW_VERSION | COMMON 0x10, type 09 |
| 0x20 | BATTERY_LEVEL_INDICATOR | POWER 0x22, type 00 |
| 0x21 | LEFT_RIGHT_BATTERY_LEVEL_INDICATOR | POWER 0x22, type 01 |
| 0x22 | CRADLE_BATTERY_LEVEL_INDICATOR | POWER 0x22, type 02 |
| 0x23 | POWER_OFF | POWER 0x24 SET_STATUS, type 03 |
| 0x24 | AUTO_POWER_OFF | POWER, type 04 |
| 0x25 | AUTO_POWER_OFF_WITH_WEARING_DETECTION | POWER, type 05 |
| 0x28 / 0x29 / 0x2a | BATTERY / LR / CRADLE _WITH_THRESHOLD | POWER 0x22, types 08 / 09 / 0a |
| 0x40 / 0x41 / 0x42 / 0x43 | LEA TWS / HBS / CLASSIC_ONLY_LE_CLASSIC_SETTING / TWS_LEA_UNI | LEA 0x40, types 00 / 01 / 0c / 02 |
| 0x45..0x4f | ..._CANT_BE_USED_WITH_LEA_CONNECTION | LEA status, types f5..ff |
| 0x50 | PRESET_EQ | EQEBB 0x50, type 00 |
| 0x51 | EBB (CLEAR BASS slider) | EQEBB, type 01 |
| 0x52 | PRESET_EQ_NON_CUSTOMIZABLE | EQEBB, type 02 |
| 0x53 | PRESET_EQ_AND_ULT_MODE | EQEBB, type 03 |
| 0x54 | SOUND_EFFECT | EQEBB, type 30 |
| 0x55 | CUSTOM_EQ | EQEBB, type 31 |
| 0x57 | PRESET_EQ_AND_ERRORCODE | EQEBB, type 04 |
| 0x61 | NOISE_CANCELLING_ONOFF | NCASM 0x60, type 01 |
| 0x62 | NC_ONOFF_AND_ASM_ONOFF | NCASM, type 11 |
| 0x63 | NC_DUAL_SINGLE_OFF_AND_ASM_ONOFF | NCASM, type 12 |
| 0x64 | NC_ONOFF_AND_ASM_LEVEL_ADJUSTMENT | NCASM, type 13 |
| 0x65 | NC_DUAL_SINGLE_OFF_ASM_LEVEL_ADJUSTMENT | NCASM, type 14 |
| 0x66 | AMBIENT_SOUND_MODE_ONOFF | NCASM, type 21 |
| 0x67 | AMBIENT_SOUND_MODE_LEVEL_ADJUSTMENT | NCASM, type 22 |
| 0x68 | MODE_NC_ASM_NC_DUAL_AUTO_ASM_LEVEL | NCASM, type 15 |
| 0x69 | AMBIENT_SOUND_CONTROL_MODE_SELECT | NCASM, type 30 |
| 0x6a | MODE_NC_ASM_NC_DUAL_SINGLE_ASM_LEVEL | NCASM, type 16 |
| 0x6b | MODE_NC_ASM_NC_DUAL_ASM_LEVEL | NCASM, type 17 |
| 0x6c | MODE_NC_NCSS_ASM_..._WITH_TEST_MODE | NCASM, type 18 (plus test mode type 40) |
| 0x6d | MODE_NC_ASM_..._NOISE_ADAPTATION | NCASM, type 19 |
| 0x70 / 0x71 | AUTO_NCASM / ADAPTIVE_CONTROL_WITH_PARAMETER_NOTIFICATION | SENSE 0x70, types 00 / 01 |
| 0x90 / 0x92 / 0x93 / 0x94 | FIXED_MESSAGE / ..._WITH_LR_SELECTION / VOICE_ASSISTANT_ALERT / LE_AUDIO_ALERT | ALERT 0x90, types 00 / 02 / 03 / 05 |
| 0xa1 | PLAYBACK_CONTROLLER_WITH_CALL_VOLUME_ADJUSTMENT | PLAY 0xa0, types 01 + 20 + 21 |
| 0xa2 | ..._AND_MUTE | PLAY, types 01 + 30 + 31 |
| 0xa3 | ..._AND_FUNCTION_CHANGE | PLAY, types 02 + 20 + 21 |
| 0xa4 | PLAYBACK_CONTROLLER_WITH_FUNCTION_CHANGE | PLAY, types 03 + 20 |
| 0xb1 | AUTO_PLAY | SAR_AUTO_PLAY 0xb0, type 01 (capability only) |
| 0xb2 | GATT_CONNECTABLE ("Service Link") | SAR_AUTO_PLAY, type 10 |
| 0xb8 | INTEGRATED_AUTO_PLAY | SAR_AUTO_PLAY, type 02 (capability only) |
| 0xc1 / 0xc2 / 0xc3 | ACTION_LOG / TIME_SERIES_OPERATIONLOG / SOUND_DROPOUT notifiers | LOG (0xc4 SET_STATUS, 0xc9 NTFY_PARAM); not decoded here |
| 0xd1..0xd4 | GENERAL_SETTING_1..4 | GENERAL_SETTING 0xd0, inquired type = the same byte (d1..d4) |
| 0xe1 | CONNECTION_MODE_SOUND_QUALITY_CONNECTION_QUALITY | AUDIO 0xe0, type 00 |
| 0xe2 | UPSCALING_AUTO_OFF | AUDIO, type 01 |
| 0xe3 | CONNECTION_MODE_..._WITH_LDAC_STATUS | AUDIO, type 02 |
| 0xe7 | CONNECTION_MODE_CLASSIC_AUDIO_LE_AUDIO | AUDIO, type 05 |
| 0xed | UPSCALING_AUTO_OFF_WITH_STATUS_DISABLE_REASON | AUDIO, type 0b (only if 0xe2 is absent) |
| 0xf0 | VIBRATOR_ON_OFF | SYSTEM 0xf0, type 00 |
| 0xf1 | PLAYBACK_CONTROL_BY_WEARING_REMOVING | SYSTEM, type 01 |
| 0xf2 | SMART_TALKING_MODE_TYPE1 (Speak-to-Chat) | SYSTEM, type 02 |
| 0xf3 | ASSIGNABLE_SETTING (touch) | SYSTEM, type 03 |
| 0xf4 | VOICE_ASSISTANT_SETTINGS | SYSTEM, type 04 |
| 0xf5 | VOICE_ASSISTANT_WAKE_WORD_ON_OFF | SYSTEM, type 05 |
| 0xf6 | WEARING_STATUS_DETECTOR (fit test) | SYSTEM, type 06 |
| 0xf7 | EARPIECE_SELECTION | SYSTEM, type 07 |
| 0xf8 | CALL_SETTINGS | SYSTEM, type 08 |
| 0xf9 | RESET_SETTINGS | SYSTEM, type 09 |
| 0xfa | AUTO_VOLUME | SYSTEM, type 0a |
| 0xfc | SMART_TALKING_MODE_TYPE2 (Speak-to-Chat) | SYSTEM, type 0c |
| 0xfd | QUICK_ACCESS | SYSTEM, type 0d |
| 0xfe | ASSIGNABLE_SETTING_WITH_LIMITATION | SYSTEM, type 0e |
| 0xff | HEAD_GESTURE_ON_OFF_TRAINING | SYSTEM, types 0f + 10 |
| T2 0x20 / 0x22 / 0x23 | AUTO_STANDBY / CARING_CHARGE_WITH_THRESHOLD / USB_SUBMERSION | T2 POWER 0x20, types 00 / 01 / 02 |
| T2 0x30 | PAIRING_DEVICE_MANAGEMENT_CLASSIC_BT | T2 PERI 0x30, type 00 |
| T2 0x31 | SOURCE_SWITCH_CONTROL | T2 PERI, type 01 |
| T2 0x32 / 0x33 | PAIRING_DEVICE_MANAGEMENT_WITH_CLASS_OF_DEVICE (BT / LE) | T2 PERI, type 02 |
| T2 0x34 | MUSIC_HAND_OVER_SETTING | T2 PERI, type 03 |
| T2 0x40..0x48 | voice guidance variants | T2 VOICE_GUIDANCE 0x40, see section 16 |
| T2 0x50..0x55 | safe listening variants | T2 SAFE_LISTENING 0x50, types 00..05 |
| T2 0x60..0x64 | LE audio | T2 LEA 0x60, types 00..04 |
| T2 0x6f | LINK_AUTO_SWITCH_CANT_BE_USED_WITH_LEA_CONNECTION | T2 LEA status, type fe |
| T2 0xf0 | WEARING_STATUS_CHECKER | T2 SYSTEM 0xf0, type 00 |
| T2 0xf1 | REPEAT_TAP_TRAINING_MODE | T2 SYSTEM, type 01 |
| T2 0xf2 | QUICK_ACCESS_EASY_SETTING | T2 SYSTEM, type 02 |
| T2 0xf3 / 0xf4 | AUTO_VOLUME_OPTIMIZER / AUTO_VOLUME_WITH_LIMITATION | T2 SYSTEM, types 03 / 04 |
| T2 0xf7 / 0xf8 | LINK_AUTO_SWITCH_FOR_SPEAKER / FOR_HEADSETS | T2 SYSTEM, types 09 / 0a |
| T2 0xf9 / 0xfa | MIC_ON_OFF_BY_HEADPHONE_OPERATION / FUNCTION_CHANGE | T2 SYSTEM, types 0b / 0c |

Table 2 SYSTEM FunctionType `0xf0+n` is not inquired type `n`. FT 0xf7 maps to type 09, 0xf8 to 0a (`f00/a.java`).

## 2. Battery (POWER, table 1, 0x20..0x29)

Variant choice in `u70/f.java`: exactly one earbud battery handler is built, first match wins: 0x20, then 0x21, then 0x28, then 0x29. The case (cradle) handlers are independent: 0x22 builds `e10/c`, 0x2a builds `e10/d`; both can exist.

| Inquired type | GET_STATUS | RET_STATUS `23` and NTFY_STATUS `25` | len | Class (RET / NTFY) |
|---|---|---|---|---|
| 00 BATTERY | `22 00` | `23 00 lvl chg` | 4 | `vf0/k0`, `vf0/l` |
| 01 LEFT_RIGHT_BATTERY | `22 01` | `23 01 Llvl Lchg Rlvl Rchg` | 6 | `vf0/r0`, `vf0/s` |
| 02 CRADLE_BATTERY | `22 02` | `23 02 lvl chg` | 4 | `vf0/p0`, `vf0/q` |
| 08 BATTERY_WITH_THRESHOLD | `22 08` | `23 08 lvl chg thr` | 5 | `vf0/m0`, `vf0/n` |
| 09 LR_BATTERY_WITH_THRESHOLD | `22 09` | `23 09 Llvl Lchg Rlvl Rchg Lthr Rthr` | 8 | `vf0/t0`, `vf0/u` |
| 0a CRADLE_BATTERY_WITH_THRESHOLD | `22 0a` | `23 0a lvl chg thr` | 5 | `vf0/q0`, `vf0/r` |

| Field | Values |
|---|---|
| lvl, thr | 0..100 (signed compare, so 0x00..0x64 only) |
| chg | BatteryChargingStatus: 00 NOT_CHARGING, 01 CHARGING, 02 UNKNOWN, 03 CHARGED |
| thr | Low-battery threshold. SC treats the battery as low when `lvl < thr` (`c10/e.java` d()). For the case this drives the "charge the case" notification |

GET_STATUS 0x22 accepts any type except 03 POWER_OFF, 07 LINK_CONTROL and 0b BATTERY_SAFE_MODE (`vf0/c.java`). The same RET_STATUS dispatcher also carries status for 04/05/06/0c..0f as `23 tt ed`, len 3 (`vf0/i0`, `vf0/j0`, `vf0/u0`, `vf0/o0`). The earbuds send `25 tt ...` on their own when a level changes.

## 3. Power off and auto power off

### 3.1 Power off (FT 0x23)

| Message | Layout | Class |
|---|---|---|
| SET_STATUS POWER_OFF | `24 03 01` (PowerOffSettingValue 01 USER_POWER_OFF; the validator accepts only 01) | `vf0/c1.java`, sent by `m40/a.java` |
| SET_STATUS LINK_CONTROL | `24 07 ed 05` (byte 3 must be 05) | `vf0/b1.java` |

No reply: the earbuds turn off and the link drops.

### 3.2 Auto power off (FT 0x24, type 04) and with wearing detection (FT 0x25, type 05)

| Message | Layout | Class |
|---|---|---|
| GET_CAPABILITY | `20 04` or `20 05` (GET_CAP accepts only 04, 05, 0b, 0e) | `vf0/a.java` |
| RET_CAPABILITY | `21 tt n e1..en` | len n+3; `vf0/y.java` (04), `vf0/z.java` (05) |
| GET_STATUS / RET / NTFY | `22 tt` → `23 tt ed`, NTFY `25 tt ed` | `vf0/i0`, `vf0/j0`, `vf0/j`, `vf0/k` |
| GET_PARAM | `26 tt` | `vf0/b.java` |
| RET_PARAM / NTFY_PARAM | `27 tt active selectTime` / `29 ...` | len 4; `vf0/d0`, `vf0/e0`, `vf0/e`, `vf0/f` |
| SET_PARAM | `28 tt active selectTime` | len 4; `vf0/w0` (04), `vf0/x0` (05) |

`active` is the element in force. `selectTime` is the time option last chosen, kept so the UI can restore it when the user leaves "when removed" or "off" (SC log text "elementId / selectTimeElementId", `v00/b.java`, `v00/d.java`).

| Element | AutoPowerOffElements (04) | AutoPowerOffWearingDetectionElements (05) |
|---|---|---|
| 00 | 5 min | 5 min |
| 01 | 30 min | 30 min |
| 02 | 60 min | 60 min |
| 03 | 180 min | 180 min |
| 04 | 15 min | 15 min |
| 10 | (invalid) | POWER_OFF_WHEN_REMOVED_FROM_EARS |
| 11 | DISABLE | DISABLE |

Other POWER types (same `27/28/29 tt onoff` shape, len 3): 06 POWER_SAVE_MODE, 0c CARING_CHARGE, 0d BT_STANDBY, 0e STAMINA, 0f AUTOMATIC_TOUCH_PANEL_BACKLIGHT_TURN_OFF (`vf0/g0`, `vf0/z0`). 0b BATTERY_SAFE_MODE is `tt onoff control` (`vf0/f0`, `vf0/y0`).

## 4. Noise cancelling and Ambient Sound (NCASM, 0x60..0x69)

### 4.1 Header shared by RET_PARAM 0x67, SET_PARAM 0x68 and NTFY_PARAM 0x69

| Offset | Field | Values | Source |
|---|---|---|---|
| 0 | command | 67 / 68 / 69 | `pf0/i0`, `pf0/z0`, `pf0/d` |
| 1 | inquired type | see 4.2 | |
| 2 | ValueChangeStatus | 00 UNDER_CHANGING (slider still moving), 01 CHANGED | `ncasm/param/ValueChangeStatus.java` |
| 3 | NcAsmOnOffValue, "total effect" | 00 OFF (noise control off), 01 ON | |

SC sends `[2][3]` = `00 01` while the user drags the level slider and `01 01` (or `01 00` for Off) when it settles (`j2objc/tandem/features/ncasm/NcAsmSendStatus.java`). SC ignores a NTFY with `UNDER_CHANGING` for its "changed" side effects (`c40/c.java`).

### 4.2 Body per inquired type

| Type | FT | Bytes 4.. | len | Class (RET / SET / NTFY, field helper) |
|---|---|---|---|---|
| 01 NC_ON_OFF | 0x61 | `nc` (NcAsmOnOffValue) | 5 | `t0` / `k1` / `o`, `rf0/n` |
| 11 NC_ON_OFF_AND_ASM_ON_OFF | 0x62 | `nc asmMode asmOn` | 7 | no RET/SET/NTFY parser in SC (status only), `rf0/o` |
| 12 NC_MODE_SWITCH_AND_ASM_ON_OFF | 0x63 | `ncValue asmMode asmOn` | 7 | `q0` / `h1` / `l`, `rf0/k` |
| 13 NC_ON_OFF_AND_ASM_SEAMLESS | 0x64 | `nc asmMode level` | 7 | `v0` / `m1` / `q`, `rf0/p` |
| 14 NC_MODE_SWITCH_AND_ASM_SEAMLESS | 0x65 | `ncValue asmMode level` | 7 | `r0` / `i1` / `m`, `rf0/l` |
| 15 MODE_NC_ASM_AUTO_NC_MODE_SWITCH_AND_ASM_SEAMLESS | 0x68 | `ncAsmMode ncValue asmMode level` | 8 | `o0` / `f1` / `j`, `rf0/i` |
| 16 MODE_NC_ASM_DUAL_SINGLE_NC_MODE_SWITCH_AND_ASM_SEAMLESS | 0x6a | `ncAsmMode ncValue asmMode level` | 8 | `n0` / `e1` / `i`, `rf0/h` |
| 17 MODE_NC_ASM_DUAL_NC_MODE_SWITCH_AND_ASM_SEAMLESS | 0x6b | `ncAsmMode asmMode level` | 7 | `l0` / `c1` / `g`, `rf0/f` |
| 18 MODE_NC_NCSS_ASM_DUAL_... | 0x6c | `ncNcssAsmMode asmMode level` | 7 | `s0` / `j1` / `n`, `rf0/m` |
| 19 MODE_NC_ASM_DUAL_..._NA | 0x6d | `ncAsmMode asmMode level naOn naSens` | 9 | `m0` / `d1` / `h`, `rf0/g` |
| 21 ASM_ON_OFF | 0x66 | `asmMode asmOn` | 6 | SET builder `a1` exists but no dispatcher entry; `rf0/d` |
| 22 ASM_SEAMLESS | 0x67 | `asmMode level` | 6 | `k0` / `b1` / `f`, `rf0/e` |
| 30 NC_AMB_TOGGLE | 0x69 | none: payload is `67/68/69 30 function`, no header | 3 | `p0` / `g1` / `k`, `rf0/j` |

| Field | Values |
|---|---|
| ncAsmMode (NcAsmMode) | 00 NC, 01 ASM (ambient) |
| ncNcssAsmMode | 00 NC, 01 ASM (same bytes; type 19 validates with this enum but SC writes NcAsmMode) |
| asmMode (AmbientSoundMode) | 00 NORMAL, 01 VOICE ("Focus on Voice" / "Voice focus") |
| ncValue (NcValue) | 00 OFF, 01 ON_SINGLE, 02 ON_DUAL, 03 AUTO, 04 AUTO_SINGLE, 05 AUTO_DUAL |
| level | ambient level, 0..255 on the wire; the usable range comes from the capability (4.3) |
| naOn | NcAsmOnOffValue: noise adaptation on (01) / off (00) |
| naSens (NoiseAdaptiveSensitivity) | 00 STANDARD, 01 HIGH, 02 LOW |
| function (NC_AMB_TOGGLE) | table 1 SYSTEM Function: 01 NC_ASM_OFF, 02 NC_ASM, 03 NC_OFF, 04 ASM_OFF (only these four pass `rf0/j`) |

What "dual", "single" and "auto" mean: SC's NC screen labels ON_DUAL "Noise Canceling" and ON_SINGLE "Street" mode (`feature/ncasm/ModeNcAsmNcDualSingleModeSwitchSeamlessThirdLayerDetailView.java`, strings ASM_Param_NC and ASM_Param_Street). So "dual" is full NC and "single" is the lighter mode older headphones offered (Gadgetbridge v1 calls it wind noise reduction). AUTO, AUTO_SINGLE and AUTO_DUAL are the automatic NC choice of type 15. Types 17, 18 and 19 have no ncValue: SC fixes NoiseCancellingType DUAL and maps NC to ON_DUAL (`c40/c.java`, `c40/d.java`).

For type 17 the "mode" is the pair `[3] [4]`: Off = `00 xx`, Noise Cancelling = `01 00`, Ambient = `01 01`.

### 4.3 Capability and status

| Message | Layout | Class |
|---|---|---|
| GET_CAPABILITY | `60 tt` (len 2) | `pf0/a.java`; SC fetches it for every listed NC type except 01 (`wv/e.java`) |
| RET_CAPABILITY, type 01 | `61 01` (len 2) | `pf0/f0` |
| RET_CAPABILITY, type 12 | `61 12 n asmMode1..asmModen` (len n+3) | `pf0/c0`, `rf0/a` |
| RET_CAPABILITY, types 13 14 15 16 17 18 19 22 | `61 tt n {asmMode min max step}*n` (len 4n+3) | `pf0/h0 d0 a0 z x e0 y w`, `rf0/b`, `ze0/b.java` |
| RET_CAPABILITY, type 30 | `61 30 key` (len 3), key = table 1 SYSTEM Key: 00 LEFT, 01 RIGHT, 02 CUSTOM, 03 C, 04 NC_AMB_KEY, 05 NC_AMBIENT_KEY | `pf0/b0`, `rf0/c` |
| GET_STATUS | `62 tt` | `pf0/c.java` |
| RET_STATUS / NTFY_STATUS | `63 tt ed` / `65 tt ed` (len 3), ed = control usable now | `pf0/w0`, `pf0/r` |
| RET/NTFY_STATUS, type 40 | `63/65 40 st`, st 00 IN_TEST_MODE, 01 OUT | `pf0/y0`, `pf0/t` |
| SET_STATUS, type 40 | `64 40 op`, op 00 START, 01 FINISH | `pf0/o1` |

Range entry: `min` 0..254, `max` 1..255, `step` 1..255 (`ze0/b.java`). One entry per AmbientSoundMode the earbuds offer, so a model without Focus on Voice lists only mode 00.

The earbuds send `69 tt ...` on their own when the mode changes from a touch or when one earbud goes in or out.

### 4.4 Adaptive Sound Control (SENSE, FT 0x70 type 00, FT 0x71 type 01)

| Message | Layout | Class |
|---|---|---|
| GET_CAPABILITY | `70 tt` | `xf0/b.java` |
| RET_CAPABILITY | `71 tt n af1..afn` (len n+3) | `xf0/k.java` |
| SET_STATUS / NTFY_STATUS | `74 tt ctl` / `75 tt ctl`, ctl 00 START_SETTING, 01 END_SETTING | `xf0/r.java`, `xf0/i.java` |
| SET_PARAM | `78 tt onoff n entry*n` (n 1..255) | `xf0/p.java`, sent by `h50/c.java` |
| NTFY_PARAM, type 00 | `79 00 result` (RequestResult) | `xf0/f.java` |
| NTFY_PARAM, type 01 | `79 01 n {result entry}*n` | `xf0/g.java`, `zf0/v.java` |
| GET_EXT_INFO / RET_EXT_INFO | `7a tt 10` / `7b tt 10 ...` (EQ preset info only) | `xf0/d.java`, `xf0/m.java` |

`af` (SenseApplicableFunction): 00..08 = NC types 11, 12, 13, 14, 15, 16, 17, 18, 19 in that order; 10 EQ_PRESET_ID; 20 SMART_TALKING_MODE. An `entry` is `af` followed by that function's body, for example for af 06 (type 17): `06 vcs onoff ncAsmMode asmMode level` (`zf0/b.java` j()). The activity detection runs in the app; the earbuds only accept these settings (inferred from the handler living in the app, `h50/c.java`).

## 5. Equalizer and CLEAR BASS (EQEBB, 0x50..0x5b)

| Message | Layout | len rule | Class |
|---|---|---|---|
| GET_CAPABILITY | `50 tt lang` (SC sends the 3-byte form with DisplayLanguage) | 3 | `gf0/b.java`, `wv/e.java` i0() |
| RET_CAPABILITY, types 00 02 04 | `51 tt bands steps n {presetId L name[L]}*n` | ends exactly after the last name | `gf0/z.java`, `hf0/e.java` |
| RET_CAPABILITY, type 03 | `51 03 bands steps x n {presetId L name}*n` (x stored by SC, not named) | | `gf0/a0.java` |
| RET_CAPABILITY, type 01 EBB | `51 01 min max` (signed, -127..127) | 4 | `gf0/y.java` |
| RET_CAPABILITY, type 31 CUSTOM_EQ | `51 31 bands steps` | 4 | `gf0/w.java` |
| GET_EXTENDED_INFO | `5a tt` (tt 00 02 03 04 31 33) | 2 | `gf0/c.java` |
| RET_EXTENDED_INFO | `5b tt n {infoType hi lo}*n` (one per band) | 3n+3 | `gf0/e0.java`, `hf0/b.java` |
| GET_STATUS | `52 tt` | 2 | `gf0/e.java` |
| RET_STATUS / NTFY_STATUS | `53 tt ed` / `55 tt ed` | 3 | `gf0/p0`, `gf0/t` |
| RET/NTFY_STATUS, type 04 | `53 04 ed n err1..errn` | at least n+3 | `gf0/p0`, `gf0/t` |
| GET_PARAM | `56 tt` | 2 | `gf0/d.java` |
| RET_PARAM / NTFY_PARAM, types 00 02 04 | `57 tt presetId n v1..vn` / `59 ...` | n+4 | `gf0/j0`, `gf0/m`, `hf0/c.java` |
| SET_PARAM, types 00 02 04 | `58 tt presetId n v1..vn` | n+4 (n may be 0) | `gf0/x0.java` |
| RET/SET/NTFY, type 01 EBB | `57/58/59 01 level` (signed) | 3 | `gf0/i0`, `gf0/w0`, `gf0/l` |
| RET/SET/NTFY, type 31 CUSTOM_EQ | `57/58/59 31 n v1..vn` | n+3 | `gf0/g0`, `gf0/s0`, `gf0/g`, `hf0/a.java` |

| Field | Values |
|---|---|
| bands | "eqBandCount" (`devicecapability/tableset2/n.java`, `s.java`) |
| steps | "eqLevelSteps": each band value is 0..steps-1 on the wire. With 21 steps the centre is 10, so wire = dB + 10 (inferred from the step count; the XM5 uses 0x0a as flat) |
| v1..vn | One byte per band, in the order of RET_EXTENDED_INFO. On models with CLEAR BASS in the EQ, v1 is CLEAR BASS |
| infoType | EqBandInformationType: 00 NO_INFORMATION, 01 HZ, 02 KHZ, 10 SPECIFIC_INFORMATION. `hi lo` = 16-bit value; for 10 the value 1 means CLEAR_BASS (`SpecificInformationType`) |
| presetId | EqPresetId: 00 OFF, 01 ROCK, 02 POP, 03 JAZZ, 04 DANCE, 05 EDM, 06 R&B/HIP-HOP, 07 ACOUSTIC, 10 BRIGHT, 11 EXCITED, 12 MELLOW, 13 RELAXED, 14 VOCAL, 15 TREBLE, 16 BASS, 17 SPEECH, 20 GAMING_EQ, 21..23 FPS_1..3, 30 HEAVY, 31 CLEAR, 32 HARD, 33 SOFT, a0 CUSTOM ("Manual"), a1..a5 USER_SETTING1..5, b0..bb ARTIST_COLLAB1..12, ff UNSPECIFIED |
| err | PresetEqErrorCodeType: 00 CALLING, 01 DEMO_MODE, 02 LISTENING_MODE, fe OTHER |

How presets are listed: by the RET_CAPABILITY list (id plus name string), not by a separate command. PRESET_EQ (type 00) means the custom presets can be edited; NON_CUSTOMIZABLE (02) means they cannot (`DeviceCapabilityTableset2Builder.java` fields `f38610w`, `f38613x`). PRESET_EQ_AND_ERRORCODE (04) adds a reason list to the status. A preset-only change is `58 00 id 00` (n = 0), which keeps the bands (validator `hf0/c` accepts n = 0).

## 6. Upscaling / DSEE (FT 0x13 indicator, 0xe2, 0xed)

| FT | Message | Layout | Class |
|---|---|---|---|
| 0x13 | GET_STATUS / RET / NTFY | `12 03` → `13 03 type status` / `15 03 type status` (len 4) | `ef0/s`, `ef0/h` |
| 0xe2 (type 01) | GET_CAPABILITY / RET | `e0 01` → `e1 01 upType` (len 3) | `cf0/e0` |
| 0xe2 | GET_STATUS / RET / NTFY | `e2 01` → `e3 01 ed` / `e5 01 ed` | `cf0/s0`, `cf0/l` |
| 0xe2 | GET_PARAM / RET / SET / NTFY | `e6 01` → `e7 01 v`; `e8 01 v`; `e9 01 v` (len 3) | `cf0/o0`, `cf0/i1`, `cf0/w` |
| 0xed (type 0b) | capability, param | as 0xe2 with type `0b` | same classes |
| 0xed | RET / NTFY_STATUS | `e3 0b ed n r1..rn` / `e5 0b ...` (len n+4) | `cf0/y0`, `cf0/r` |

| Field | Values |
|---|---|
| upType / type (UpscalingType) | 00 DSEE_HX, 01 DSEE, 02 DSEE_HX_AI (DSEE Extreme), 03 DSEE_ULTIMATE |
| status (indicator) | 00 OFF, 01 VALID (working), 02 INVALID |
| v (UpscalingTypeAutoOff) | 00 OFF, 01 AUTO (on) |
| r (StatusErrorCodeType) | 00 CALLING, 01 DEMO_MODE, 02 LE_AUDIO_CONNECTED, 03 LE_AUDIO_MUSIC_PLAYING, 04 TANDEM_OVER_GATT_CONNECTED, 05..07 VOICE_UI_GOOGLE / SONY / TENCENT, 08..0a UPMIX_CINEMA / GAME / MUSIC, 0b LISTENING_MODE_IS_NOT_STANDARD, fe OTHER |

The param has no type byte in this build. The 4-byte `[cmd 01 type on]` form that `SonyFeatures.readDsee` also accepts is not in SC's table 1 classes.

## 7. Connection mode / sound quality (FT 0xe1, 0xe3, 0xe7)

| FT | Type | Message | Layout | Class |
|---|---|---|---|---|
| 0xe1 | 00 | status | `e2 00` → `e3 00 ed`, NTFY `e5 00 ed` | `cf0/t0`, `cf0/m` |
| 0xe1 | 00 | param | `e6 00` → `e7 00 pm`; SET `e8 00 pm`; NTFY `e9 00 pm` | `cf0/i0`, `cf0/b1`, `cf0/t` |
| 0xe3 | 02 | status | `e3 02 ed b` (len 4; b unnamed, probably LDAC state, inferred) | `cf0/v0`, `cf0/o` |
| 0xe3 | 02 | param | `e7/e8/e9 02 pm` | `cf0/j0`, `cf0/d1`, `cf0/v` |
| 0xe7 | 05 | capability | `e0 05` → `e1 05 n pm1..pmn m f1..fm` (n at least 2, len n+m+4; f 00 = GATT_CONNECTABLE exclusive) | `cf0/z` |
| 0xe7 | 05 | status | `e3 05 ed b` (len 4) | `cf0/u0`, `cf0/n` |
| 0xe7 | 05 | param | RET `e7 05 pm`; SET `e8 05 pm x` (x EnableDisable, SC always 00); NTFY `e9 05 pm ss` | `cf0/i0`, `cf0/c1`, `cf0/u` |

PriorMode `pm`: 00 SOUND_QUALITY_PRIOR, 01 CONNECTION_QUALITY_PRIOR ("High Reliability" / stable), 02 LOW_LATENCY_PRIOR_BETA (SC shows it as the LE Audio choice). SwitchingStream `ss`: 00 NONE, 01 LE_AUDIO, 02 CLASSIC_AUDIO. Type 00 has no capability message (the RET_CAPABILITY dispatcher throws for it). Mapping: `v10/f` (0xe1), `v10/h` (0xe3), `v10/c` (0xe7).

## 8. Codec, L/R connection, firmware, model, colour

| What | Message | Layout | Class |
|---|---|---|---|
| Codec (FT 0x12) | `12 02` → `13 02 c`, NTFY `15 02 c` | c: 00 UNSETTLED, 01 SBC, 02 AAC, 10 LDAC, 20 aptX, 21 aptX HD, 30 LC3, ff OTHER | `ef0/o`, `ef0/e`, `common/param/AudioCodec.java` |
| L/R connection (FT 0x11) | `12 01` → `13 01 L R`, NTFY `15 01 L R` | 00 NOT_CONNECTED, 01 CONNECTED; byte 2 left, byte 3 right | `ef0/q`, `ef0/f` |
| Special mode (FT 0x18) | `12 06` → `13 06 n m..` | 00 NORMAL, 01 TWO_DEVICES_CONNECTION_MODE, 02 STEREO_PAIR ... | `ef0/r`, `ef0/g` |
| Display FW version (FT 0x1b) | `10 09` → `11 09 L str` | | `ef0/l` |
| Model name, FW, series/colour | DeviceInfoType 01, 02, 03 | section 1.1 | `ff0/i`, `ff0/g`, `ff0/j` |

COMMON GET_STATUS accepts only types 00, 01, 02, 03 and 06 (`ef0/b.java`).

## 9. Simple SYSTEM on/off settings (playback control by wearing and others)

Types 00 VIBRATOR, 01 PLAYBACK_CONTROL_BY_WEARING (FT 0xf1), 05 VOICE_ASSISTANT_WAKE_WORD, 0a AUTO_VOLUME, 0f HEAD_GESTURE_ON_OFF share one shape.

| Message | Layout | len | Class |
|---|---|---|---|
| GET_STATUS / RET / NTFY | `f2 tt` → `f3 tt ed` / `f5 tt ed` | 3 | `ag0/l1`, `ag0/c0` |
| GET_PARAM | `f6 tt` | 2 | `ag0/c.java` |
| RET_PARAM / SET_PARAM / NTFY_PARAM | `f7 tt onoff` / `f8 tt onoff` / `f9 tt onoff` (OnOffSettingValue, 00 = on) | 3 | `ag0/b1`, `ag0/a2`, `ag0/p` |

For type 01, ON (00) means music pauses when an earbud is removed and resumes when worn. Type 05 status is `f3/f5 05 wakeWordStatus` (`ag0/o1`, `ag0/g0`).

## 10. Touch controls: assignable settings (FT 0xf3 type 03, FT 0xfe type 0e)

### 10.1 Capability

`f0 03` → `f1 03 nKeys {keyBlock}*nKeys` (`ag0/j0.java`, parsed by `cg0/c`, `cg0/d`, `cg0/a`, `cg0/b`).

| Block | Layout |
|---|---|
| keyBlock | `key type defaultPreset nPresets {presetBlock}*nPresets` |
| presetBlock | `preset nActions nCustom {action function}*nActions {customBlock}*nCustom` (nActions + nCustom > 0) |
| customBlock | `action defaultFunction nFn fn1..fnN` (a gesture whose function the user can pick) |

FT 0xfe (type 0e) adds one byte after the type: `f1 0e limitation nKeys ...`; LimitationType 00 NO_DESCRIPTION, 01 CANT_USE_VOICE_ASSISTANT_PRESET_WHILE_LE_AUDIO, 02 CANT_USE_VA_FUNCTION_UNDER_PLAYBACK_CONTROL_WHILE_LE_AUDIO (`ag0/k0.java`).

### 10.2 Param, status, extended param

| Message | Layout | Class |
|---|---|---|
| GET_PARAM / RET / NTFY | `f6 03` → `f7 03 n preset1..presetn` / `f9 03 ...` (n = number of keys, usually 2: left, right) | `ag0/y0`, `ag0/m`, `cg0/g` |
| SET_PARAM | `f8 03 n preset1..presetn` | `ag0/x1` |
| GET_STATUS / RET / NTFY | `f2 03` → `f3 03 n ed1..edn` / `f5 03 ...` (per key usable) | `ag0/i1`, `ag0/z`, `cg0/h` |
| GET_EXT_PARAM | `fa 03` | `ag0/b.java` |
| RET / SET / NTFY_EXT_PARAM | `fb 03 n {preset nA {action function}*nA}*n` / `fc 03 ...` / `fd 03 ...` | `ag0/s0`, `ag0/r1`, `ag0/f`, `bg0/a`, `cg0/e` |
| Type 0e | same layouts with type `0e` | `ag0/z0`, `ag0/y1`, `ag0/n`, `ag0/j1`, `ag0/a0`, `ag0/t0`, `ag0/s1`, `ag0/g` |

The extended param carries the user's choice for each customBlock gesture. On the XM5 it is the noise-control cycle: `fb 03 01 00 01 00 03` = one entry, preset 00, one action, SINGLE_TAP gives function 03 NC_OFF.

### 10.3 Codes

| Key | Type | Action |
|---|---|---|
| 00 LEFT_SIDE, 01 RIGHT_SIDE, 02 CUSTOM, 03 C, 04 NC_AMB_KEY, 05 NC_AMBIENT_KEY | 00 TOUCH_SENSOR, 01 BUTTON, 02 FACE_TAP | 00 SINGLE_TAP, 01 DOUBLE_TAP, 02 TRIPLE_TAP, 03 REPEAT_TAP, 10 SINGLE_TAP_AND_HOLD, 11 DOUBLE_TAP_AND_HOLD, 21 LONG_PRESS_THEN_ACTIVATE, 22 LONG_PRESS_DURING_ACTIVATE |

Preset (`system/param/Preset.java`): 00 AMBIENT_SOUND_CONTROL, 10 VOLUME_CONTROL, 20 PLAYBACK_CONTROL, 21 TRACK_CONTROL, 22 PLAYBACK_CONTROL_VOICE_ASSISTANT_LIMITATION, 30 VOICE_RECOGNITION, 31 GOOGLE_ASSIST, 32 AMAZON_ALEXA, 33 TENCENT_XIAOWEI, 34 MS, 35 AMBIENT_SOUND_CONTROL_QUICK_ACCESS, 36 QUICK_ACCESS, 37 TENCENT_XIAOWEI_Q_MSC, 38 TEAMS, 39/40/41/42/43/44 "..._WITH_AVAILABLE_ONLY_ON_BT_CLASSIC_CONNECTION_CAUTION" variants, 45 AMBIENT_SOUND_CONTROL_MIC, 46 LISTENING_MODE_QUICK_ACCESS, 47 AMBIENT_SOUND_CONTROL_LISTENING_MODE, 70 CHAT_MIX, 71 CUSTOM1, 72 CUSTOM2, ff NO_FUNCTION.

Function (`system/param/Function.java`): 00 NO_FUNCTION, 01 NC_ASM_OFF, 02 NC_ASM, 03 NC_OFF, 04 ASM_OFF, 10 QUICK_ATTENTION, 11 NC_OPTIMIZER, 20 PLAY_PAUSE, 21 NEXT_TRACK, 22 PREV_TRACK, 23 VOLUME_UP, 24 VOLUME_DOWN, 30 VOICE_RECOGNITION, 31 GET_YOUR_NOTIFICATION, 32 TALK_TO_GOOGLE_ASSISTANT, 33 STOP_GOOGLE_ASSISTANT, 34 VOICE_INPUT_CANCEL, 35 TALK_TO_TENCENT_XIAOWEI, 36 CANCEL_VOICE_RECOGNITION, 37 VOICE_INPUT_AMAZON_ALEXA, 38 CANCEL_AMAZON_ALEXA, 39 CANCEL_TENCENT_XIAOWEI, 3a/3b NEXT/PREV_TRACK_STOP_GEMINI_LIVE, 40 LAUNCH_MLP, 41 TALK_TO_YOUR_MLP, 42 SPTF_ONE_TOUCH, 43 QUICK_ACCESS1, 44 QUICK_ACCESS2, 45 TALK_TO_TENCENT_XIAOWEI_CANCEL, 46 Q_MSC_ONE_TOUCH, 47 TEAMS, 48 TEAMS_VOICE_SKILLS, 50..56 NCSS cycles, 57 AMB_SETTING, 58 STANDARD_VOICE_SOUND, 59 LISTENING_MODE, 70 MIC_MUTE, 71 GAME_UP, 72 CHAT_UP.

Quick Attention is Function 0x10, reached in the XM5 capability by action 0x22 (hold) on preset 0x35. Volume is Function 0x23/0x24 on action 0x03 (tap repeatedly) inside presets 0x35 and 0x20; Preset 0x10 VOLUME_CONTROL is a separate whole-key preset that the XM5 does not offer.

### 10.4 Quick Access (FT 0xfd, type 0d)

| Message | Layout | Class |
|---|---|---|
| RET_CAPABILITY | `f1 0d qaKey type n {action default m b1..bm}*n` | `ag0/n0.java` |
| RET / NTFY_PARAM | `f7 0d n b1..bn` / `f9 0d ...` (n at least 1, one byte per action, e.g. double and triple tap) | `ag0/d1`, `ag0/t` |
| SET_PARAM | `f8 0d n b1..bn` | `ag0/c2` |
| GET_STATUS / RET | `f2 0d` → `f3 0d ed` | `ag0/l1` |

QuickAccessKey: 00 L_R_KEY, 01 NC_AMB_KEY, 02 FIXED_QUICK_ACCESS_KEY. The `b` bytes are QuickAccessFunction (`j2objc/tandem/features/quickaccess/QuickAccessFunction.java`): 00 none, 01 Spotify Tap, 04 Tencent Xiaowei, 07 QQ Music; other services come from a server list (inferred). Table 2 QUICK_ACCESS_EASY_SETTING (T2 FT 0xf2) is `f8 02 gatt N p1..pN M f1..fM` (Preset, QuickAccessFunction), answered by NTFY `f9 02 rr` (00 OK, 01 CANCELED) on frame 0x0e (`tg0/c2`, `tg0/x`).

## 11. Noise-control cycle ("ambient sound control button mode")

Two variants exist:

| FT | Message | Layout |
|---|---|---|
| 0xf3 | via the assignable extended param (10.2) | `fa 03` → `fb 03 01 preset 01 00 fn`; SET `fc 03 01 preset 01 00 fn`; NTFY `fd 03 ...` |
| 0x69 | via NC_AMB_TOGGLE (4.2) | `66 30` → `67 30 fn`; SET `68 30 fn`; NTFY `69 30 fn`; capability `61 30 key` |

`fn`: 01 NC / Ambient / Off, 02 NC / Ambient, 03 NC / Off, 04 Ambient / Off. In the 0xf3 form, send back the preset byte the earbuds reported in `fb 03`; it is not fixed (the XM5 reports 00 there while its capability calls the left key 0x35).

## 12. Speak-to-Chat (FT 0xf2 type 02, FT 0xfc type 0c)

| Message | Type 02 (TYPE1) | Type 0c (TYPE2) | Class |
|---|---|---|---|
| RET_CAPABILITY | `f1 02 preview a b c` | `f1 0c preview a b c` | `ag0/o0` (len 6; preview = SmartTalkingModePreviewType 00 NOT_SUPPORT, 01 SUPPORT; a, b, c unnamed) |
| RET / NTFY_STATUS | `f3 02 ed effect` | `f3 0c ed effect` | `ag0/n1`, `ag0/f0` (effect 00 NOT_ACTIVE, 01 ACTIVE: chat mode running now) |
| RET / SET / NTFY_PARAM | `f7/f8/f9 02 onoff preview` | `f7/f8/f9 0c onoff preview` | `ag0/e1`, `ag0/e2`, `ag0/v` (both OnOffSettingValue) |
| RET / SET / NTFY_EXT_PARAM | `fb/fc/fd 02 sens voiceFocus outTime` (len 5) | `fb/fc/fd 0c sens outTime` (len 4) | `ag0/u0 u1 i`, `ag0/v0 v1 j`, `bg0/c`, `bg0/d` |

DetectSensitivity `sens`: 00 AUTO, 01 HIGH, 02 LOW. ModeOutTime `outTime`: 00 FAST, 01 MID, 02 SLOW, 03 NONE (SunPods labels these about 5 s, 15 s, 30 s and Never; SC's protocol code has no durations). The second param byte is ON only while SC's Speak-to-Chat screen is open (set true in `onViewCreated`, false in `onDestroyView`, `dl/r.java`), so it reads as a preview flag (inferred). The middle byte of the type 02 extended param is an OnOffSettingValue whose meaning SC does not name.

## 13. Voice assistant (FT 0xf4, type 04)

| Message | Layout | Class |
|---|---|---|
| RET_CAPABILITY | `f1 04 keyType n va1..van` | `ag0/p0.java` |
| RET / SET / NTFY_PARAM | `f7/f8/f9 04 va` (len 3) | `ag0/f1`, `ag0/f2`, `ag0/w`, `bg0/h` |
| RET / NTFY_STATUS | `f3 04 ed` / `f5 04 ed` | `ag0/l1`, `ag0/c0` |

VoiceAssistantKeyType: 00 FIXED_BUTTON, 01 TOUCH_SENSOR_CONTROL_PANEL, 02 ASSIGNABLE_BUTTON, 03 ASSIGNABLE_SENSOR. VoiceAssistant `va`: 30 VOICE_RECOGNITION (phone's assistant), 31 GOOGLE_ASSISTANT, 32 AMAZON_ALEXA, 33 TENCENT_XIAOWEI, 34 SONY_VOICE_ASSISTANT, 3f ENABLED_IN_OTHER_DEVICE, ff NO_FUNCTION.

## 14. Reset (FT 0xf9, type 09)

SET `f8 09 rt` (ResetType 00 SETTINGS_ONLY, 01 FACTORY_RESET; `ag0/d2`), then NTFY `f9 09 res` (ResetResult 00 SUCCESS, 01 ERROR_CONNECTION_LEFT, 02 ERROR_CONNECTION_RIGHT; `ag0/u`).

## 15. General settings (FT 0xd1..0xd4)

| Message | Layout | Class |
|---|---|---|
| GET_CAPABILITY | `d0 gs lang` (len 3, DisplayLanguage) | `if0/a` |
| RET_CAPABILITY, BOOLEAN | `d1 gs 00 fmt L1 title L2 summary` (len L1+L2+6) | `if0/h`, `if0/i` |
| RET_CAPABILITY, LIST | `d1 gs 01 fmt L1 title L2 summary n {fmt Lt title Ls summary}*n` (n 1..64) | `if0/j`, `jf0/a` |
| GET_STATUS / RET / NTFY | `d2 gs` → `d3 gs ed` / `d5 gs ed` | `if0/n`, `if0/g` |
| GET_PARAM / RET / SET / NTFY | `d6 gs` → `d7 gs type val`; `d8 gs type val`; `d9 gs type val` (len 4) | `if0/l m p q e f` |

`gs` = d1..d4 (the FunctionType byte itself). GsSettingType 00 BOOLEAN, 01 LIST. GsStringFormat 00 RAW_NAME, 01 ENUM_NAME (the title is a key such as MULTIPOINT_SETTING, SIDETONE_SETTING, TOUCH_PANEL_SETTING, TWS_ONE_SIDE_USE_NCASM_SETTING, FACETAP_SETTING, VOICE_GUIDANCE_SETTING, TAP_SENSITIVITY_SETTING, ASSIGNABLE_KEY_SETTING...). BOOLEAN `val`: 00 ON, 01 OFF. LIST `val`: index 0..63. DisplayLanguage: 00 UNDEFINED, 01 EN, 02 FR, 03 DE, 04 ES, 05 IT, 06 PT, 07 NL, 08 SV, 09 FI, 0a RU, 0b JA, 0c zh-Hans, 0d pt-BR, 0e zh-Hant, 0f KO, 10 TR.

## 16. Voice guidance (table 2, 0x40..0x4b)

FunctionType to inquired type (`u70/f.java` → `k70/*`, `wv/e.java`):

| FT | Name | Types used | Language | Volume |
|---|---|---|---|---|
| 0x40 | MTK_TRANSFER_..._NOT_SUPPORT_LANGUAGE_SWITCH | 00 | no | no |
| 0x41 | MTK_TRANSFER_..._SUPPORT_LANGUAGE_SWITCH | 01 (+ EXT 01) | read and changed by language-pack transfer | no |
| 0x42 | as 0x41 _AND_VOLUME_ADJUSTMENT | 01 + 20 | as 0x41 | yes, type 20 |
| 0x43 | VOLUME_SETTING_MTK_FIXED_TO_5_STEPS | 21 | no | yes, type 21 |
| 0x44 | SUPPORT_LANGUAGE_SWITCH | 02 | read and SET directly | no |
| 0x45 | ONLY_ON_OFF_SWITCH | 03 | no | no |
| 0x46 / 0x47 / 0x48 | BATTERY_LEVEL_VOICE / POWER_ON_OFF_SOUND / SOUND_EFFECT_ULT_BEEP | 30 / 31 / 32 | no | no |

| Message | Types | Layout | len | Class (`xg0/`) |
|---|---|---|---|---|
| GET_CAPABILITY | any | `40 tt` | 2 | `a` |
| RET_CAPABILITY | 00 | `41 00 X rs tw bg sw` | 7 | `r`, `s` |
| RET_CAPABILITY | 01 | `41 01 X rs tw bg sw N lang1..langN` | N+8 | `r`, `t` |
| RET_CAPABILITY | 02 | `41 02 sw N lang1..langN` | N+4 | `u` |
| GET_STATUS | 00 01 02 | `42 tt st` (st 00 ON_OFF, 01 LANGUAGE) | 3 | `f` |
| GET_STATUS | 03 21 30..32 | `42 tt` | 2 | `e` |
| RET / NTFY_STATUS | 00 01 02 | `43/45 tt st ed` | 4 | `e0 f0 g0`, `n o p` |
| RET / NTFY_STATUS | 03 21 30..32 | `43/45 tt ed` | 3 | `d0`, `m` |
| SET_STATUS | 00 01 | `44 tt st ed` | 4 | `n0`, `o0` |
| GET_PARAM | any | `46 tt` | 2 | `c` |
| RET_PARAM | 00 03 30..32 | `47 tt onoff` | 3 | `y`, `a0` |
| RET_PARAM | 01 02 | `47 tt onoff lang` | 4 | `z` |
| SET / NTFY_PARAM | 00 01 03 30..32 | `48/49 tt onoff` | 3 | `i0`, `h`, `j0`, `i` |
| SET / NTFY_PARAM | 02 | `48/49 02 onoff lang` | 4 | `k0`, `j` |
| RET / NTFY_PARAM | 20 21 | `47/49 tt vol` | 3 | `b0`, `k` |
| SET_PARAM | 20 21 | `48 tt vol feedback` | 4 | `l0` |
| GET / RET_EXT_PARAM | 01 | `4a 01` → `4b 01 ...` (language pack list: requiredTime, categoryId, serialNumber, thresholds, uniqueId, then N {lang, serviceId}) | | `b`, `v`, `w` |

| Field | Values |
|---|---|
| onoff | OnOffSettingValue, 00 = prompts on |
| vol | signed int8, -2..+2 (`fe..02`); the SET builder throws outside it |
| feedback | OnOffSettingValue: play the "voice guidance test" phrase (inferred from log text in `k70/q`) |
| sw | VoiceGuidanceSupportsSwitch 00 NOT_SUPPORT, 01 SUPPORT (on/off switch offered) |
| rs, tw, bg | EnableDisable: resumable transfer, TWS, background transfer (`wv/a.java` Q()) |
| X | not read by SC; the validator needs len at least X+3. The XM5 sends 03 |
| lang (VoiceGuidanceLanguage) | 00 UNDEFINED, 01 English, 02 French, 03 German, 04 Spanish, 05 Italian, 06 Portuguese, 07 Dutch, 08 Swedish, 09 Finnish, 0a Russian, 0b Japanese, 0d Brazilian Portuguese, 0f Korean, 10 Turkish, f0 Chinese (0c and 0e invalid) |

For 0x41/0x42 the language cannot be SET with a param message: SC's SET for type 01 carries on/off only, and the language list comes with per-language service ids for a firmware-style transfer (RET_EXT 4b). SunPods' `VoicePrompts.setOn` sends a 4-byte `48 01 onoff lang`; SC's validator for type 01 SET (`xg0/i0`) requires exactly 3 bytes. See the open questions.

## 17. Safe listening (table 2, 0x50..0x5b)

Types 00 HBS_1, 01 TWS_1, 02 HBS_2, 03 TWS_2, 04 SAFE_VOLUME_CONTROL, 05 MAX_VOL_LV_LIMIT; FT 0x50..0x55 map to 00..05.

| Message | Types | Layout | Class |
|---|---|---|---|
| RET_CAPABILITY | 00..03 | `51 tt roundBase ts(4) minInterval logDays` (len 9) | `og0/d` |
| RET_CAPABILITY | 05 | `51 05 pattern minInterval` (LimitPattern 00 CLOSED_MDR, 01 OPEN_AIR_MDR) | `og0/c` |
| RET_STATUS | 01 | `53 01 stL stR rec12 rec12` (len 28) | `rg0/s` |
| RET_STATUS | 03 | `53 03 stL stR rec13 rec13` (len 30) | `rg0/t` |
| RET_STATUS | 00 / 02 | `53 tt st rec12` / `st rec13` | `rg0/n`, `rg0/o` |
| RET / NTFY_STATUS | 05 | `53/55 05 onoff` | `rg0/p`, `rg0/f` |
| SET_STATUS | 01 / 03 | `54 tt stL stR t6L t6R` (len 16) | `rg0/z` |
| SET_STATUS | 04 | `54 04 who` (00 NORMAL, 01 SENSITIVE) | `rg0/y` |
| NTFY_STATUS | 01 / 03 | `55 tt stL stR N rec*N` | `rg0/j`, `rg0/k` |
| NTFY_STATUS | 04 | `55 04 who` | `rg0/h` |
| RET_PARAM | 00..03 | `57 tt ed` | `qg0/h` |
| RET_PARAM | 05 | `57 05 lvl` (0..4) | `qg0/g` |
| SET / NTFY_PARAM | 00..03 | `58/59 tt logging preview` (OnOff each) | `qg0/k`, `qg0/d` |
| SET / NTFY_PARAM | 04 | `58/59 04 a b` (a limiting active, b SVC on, inferred) | `qg0/l`, `qg0/e` |
| GET / RET_EXT_PARAM | 00..03 | `5a tt` → `5b tt dB cause` (cause 00 NOT_PLAYING, 01 IN_CALL, 02 DETACHED, other = valid) | `pg0/a`, `pg0/b` |

rec12 = `target(1) timestamp(4 BE) rtcResetCounter(2) viewTime(1) soundPressure(4)`; rec13 adds `ambientTime(1)`; t6 = `timestamp(4) rtcResetCounter(2)` (`sg0/b c d`). Log status: 00 DISCONNECTED, 01 SENDING, 02 COMPLETED, 03 NOT_SENDING, 04 ERROR. SVC (type 04) has no GET; SC learns it from NTFY only.

## 18. Pairing device management, source switch, link auto switch (table 2)

| Message | Layout | Class (`lg0/`) |
|---|---|---|
| GET_CAPABILITY / RET | `30 tt` → `31 tt maxPaired maxConnected fileTransfer` (00 POSSIBLE, 01 IMPOSSIBLE) | `p` |
| GET_STATUS / RET / NTFY | `32 tt` → `33 tt mode ed` / `35 ...` (mode 00 NORMAL, 01 INQUIRY_SCAN = discoverable) | `w`, `n` |
| SET_STATUS | `34 tt mode 00` (byte 3 must be 00) | `e0` |
| GET_PARAM / RET / NTFY, type 00 | `36 00` → `37 00 N {addr17 conn L name}*N playing` / `39 ...` | `s`, `j` |
| RET / NTFY, type 02 | same with 3 bytes class of device (big-endian) after `conn` | `t`, `k`, `mg0/b` |
| SET_EXT_PARAM, types 00 02 | `3c tt action addr17` (00 DISCONNECT, 01 CONNECT, 02 UNPAIR) | `y` |
| NTFY_EXT_PARAM, types 00 02 | `3d tt action result addr17` | `f` |
| Source switch | RET/SET `37/38 01 keep`; NTFY `39 01 keep rr`; SET_EXT `3c 01 addr17`; NTFY_EXT `3d 01 rr addr17` | `u c0 l z g` |
| Music hand over | `37/38/39 03 onoff` | `r b0 i` |

`conn` 0 = paired only; above 0 = connected, ordered by this value; `playing` equals the `conn` of the device holding playback. PeripheralResult: 00..03 DISCONNECTION SUCCESS / ERROR / IN_PROGRESS / BUSY, 10..13 CONNECTION_*, 20..23 UNPAIRING_*, 30..33 PAIRING_*. SourceSwitchControlResult: 00 SUCCESS, 01 FAIL, 02 FAIL_CALLING, 03 FAIL_A2DP_NOT_CONNECT, 04 FAIL_GIVE_PRIORITY_TO_VOICE_ASSISTANT. SC always fetches the type 02 capability, even when no 0x30..0x33 function is listed (`wv/e.java`, the `else` branch requests type 02 too).

Link auto switch for headsets (T2 FT 0xf8, type 0a), frame 0x0e (`tg0/`, `f00/a`, `f00/b`):

| Message | Layout | Class |
|---|---|---|
| GET_STATUS / RET / NTFY | `f2 0a` → `f3 0a ed` / `f5 0a ed` | `n1`, `f0` |
| GET_PARAM / RET / NTFY | `f6 0a` → `f7 0a onoff idHi idLo addr17` (len 22) / `f9 ...` | `i1`, `u` |
| SET_PARAM | `f8 0a onoff idHi idLo` (len 5, no address) | `z1` |

`id` = linked speaker identifier (u16); `addr17` = the phone that owns the setting; SC compares it with its own address. FT 0xf7 (type 09, for speakers): RET `f7 09 N addr17*N`, SET `f8 09 action` (00 REGISTER, 01 UNREGISTER).

## 19. LE Audio

| Where | Message | Layout | Class |
|---|---|---|---|
| T1 LEA type 0c (FT 0x42) | GET_PARAM / RET / NTFY | `46 0c` → `47 0c onoff` / `49 0c onoff` (00 = LE Audio + Classic, 01 = Classic only) | `kf0/a0`, `kf0/i` |
| | SET_PARAM | `48 0c onoff changeType` (00 SETTING_AND_CONNECTION_METHOD_CHANGE, 01 SETTING_CHANGE) | `kf0/m0`, sent by `d30/b` |
| | GET_STATUS / RET | `42 0c` → `43 0c ed` | `kf0/e0`, `kf0/l` |
| T1 LEA types 00 02 | RET_CAPABILITY | `41 tt uid17 leL17 leR17` (len 53) | `kf0/t`, `kf0/u` |
| | RET_STATUS | `43 00 ed ssA ssB` (StreamingStatus 00 POWER_OFF, 01 NONE, 02 VIA_A2DP, 03 VIA_LE_AUDIO_UNICAST) | `kf0/i0` |
| T1 LEA f5..ff | RET / NTFY_STATUS | `43/45 tt reason` (00 BY_LE_AUDIO_PRIOR, 01 UNAVAILABLE, 02 BY_LE_AUDIO_SWITCH_ON) | `kf0/f0`, `kf0/m` |
| T1 AUDIO type 05 (FT 0xe7) | | section 7 | |
| T2 LEA type 01 (FT 0x61) | RET_CAPABILITY | `61 01 ver` | `hg0/l` |
| T2 LEA type 02 (FT 0x62) | capability / param | `61 02 N mode..`; `67 02 mode`; SET `68 02 mode` → NTFY `69 02 mode res` (mode 00 HIGH_RELIABILITY, 01 LOW_LATENCY, 02 LOW_LATENCY_WITH_EQ_RESTRICTION; res 00 SUCCESS, 01/02 ERROR_CONNECTION_LEFT/RIGHT) | `hg0/k p w e` |
| T2 LEA type 04 (FT 0x64) | capability / status / param | `61 04 uid17 le17`; `63 04 ed stream`; `67 04 hist` | `hg0/m u q` |
| T2 LEA type fe (FT 0x6f) | status | `63 fe reason` | `hg0/s`, `hg0/h` |

## 20. Auto play, GATT connectable (Service Link), playback controller

| What | Message | Layout | Class |
|---|---|---|---|
| AUTO_PLAY (FT 0xb1) | RET_CAPABILITY only | `b1 01 n svc..` (00 MS, 01 SPTF, 02 EDL) | `wf0/k` |
| INTEGRATED_AUTO_PLAY (FT 0xb8) | RET_CAPABILITY only | `b1 02 hi lo` (u16 AutoPlayUniqueId) | `wf0/m` |
| GATT_CONNECTABLE (FT 0xb2) | capability | `b0 10` → `b1 10 n f..` (00 FW_UPDATE) | `wf0/l` |
| | status | `b2 10` → `b3 10 ed`, NTFY `b5 10 ed` | `wf0/r`, `wf0/h` |
| | param | `b6 10` → `b7 10 onoff`; SET `b8 10 onoff`; NTFY `b9 10 onoff` | `wf0/p u e` |
| PLAY capability | types 01 02 | `a0 tt` → `a1 tt musicSteps callSteps` | `tf0/p` |
| | type 03 | `a1 03 musicSteps` | `tf0/q` |
| PLAY status | 01 / 02 / 03 | `a3 01 ed ps mc`; `a3 02 ed ps mc pce`; `a3 03 ed ps pce` (NTFY `a5` same) | `tf0/a0 y z`, `tf0/m k l` |
| PLAY control | SET_STATUS | `a4 tt 00 pc` (pc 01 PAUSE, 02 TRACK_UP, 03 TRACK_DOWN, 07 PLAY) | `tf0/f0` |
| PLAY metadata | RET / NTFY_PARAM 01..03 | `a7 tt` + 4 entries {nameStatus L utf8}: track, album, artist, genre | `tf0/t f`, `uf0/a` |
| Volume | 20 music / 21 call | `a6 tt` → `a7 tt vol`; SET `a8 tt vol`; NTFY `a9 tt vol` | `tf0/u g d0` |
| Volume with mute | 30 / 31 | RET/NTFY `a7/a9 tt vol mute` (mute OnOff, 00 = muted); SET `a8 tt vol` (no mute byte) | `tf0/v h d0` |

PlaybackStatus `ps`: 00 UNSETTLED, 01 PLAY, 02 PAUSE, 03 STOP. MusicCallStatus `mc`: 00 MUSIC, 01 CALL. `vol` runs 0..steps-1 (inferred from the UI slider max = steps - 1). Service Link is GATT_CONNECTABLE: Gadgetbridge drives it with `b6 10` / `b8 10` and then answers `98 00 29 01` (on) or `98 00 1e 01` (off), message codes 0x29 CANT_USE_LDAC_WHILE_GATT_IS_ON and 0x1e CAUTION_FOR_DISABLE_SAR_GM1 (Gadgetbridge cross-check, not SC code).

## 21. Alerts (table 1, 0x90..0x99)

| Message | Layout | Class |
|---|---|---|
| SET_STATUS (enable alerts) | `94 00 00`; also `94 04 00` (app in foreground) | `bf0/t`, `bf0/s`, sent by `i00/a` |
| NTFY_PARAM type 00 (device asks) | `99 00 msg actionType` (AlertActionType 00 CONFIRMATION_ONLY, 01 POSITIVE_NEGATIVE, 02 POSITIVE_CONFIRMATION_WITH_REPLY) | `bf0/d` |
| SET_PARAM type 00 (app answers) | `98 00 msg action` (00 NEGATIVE, 01 POSITIVE) | `bf0/n` |
| NTFY / SET type 02 | `99 02 msgLR default` / `98 02 msgLR choice` (choice 00 NEGATIVE, 01 LEFT, 02 RIGHT) | `bf0/e`, `bf0/o` |
| NTFY / SET type 06 (flexible) | `99 06 ft n item1..itemn actionType` / `98 06 ft action` | `bf0/f`, `bf0/p` |
| NTFY / SET type 04 | `99 04 msg actionType` / `98 04 msg action` | `bf0/c`, `bf0/m` |
| RET / NTFY_STATUS type 05 | `93/95 05 onoff` (LE Audio alert active) | `bf0/j`, `bf0/h` |

Message codes most relevant here (AlertMessageType, `alert/param/AlertMessageType.java`): 00 disconnect for connection mode change, 01 disconnect for key assign change, 07 DISCONNECT_CAUSED_BY_CHANGING_MULTIPOINT, 09 CAUTION_FOR_DUAL_ASSIGNMENT_OF_PLAYBACK_CONTROL, 0f CAUTION_FOR_DISABLE_VOICE_ASSISTANT_ASSIGNABLE_SENSOR, 1c CAUTION_FOR_CONNECTION_MODE_SOUND_QUALITY_PRIOR, 1d CAUTION_FOR_FW_UPDATE_IN_PROGRESS, 1e CAUTION_FOR_DISABLE_SAR_GM1, 20 CAUTION_FOR_GATT_TO_ON, 29 CANT_USE_LDAC_WHILE_GATT_IS_ON, 2c/2d disconnect for standby mode Classic only / LE+Classic, 2e DISCONNECT_CAUSED_BY_ENTER_PAIRING_MODE_CLASSIC, 70..73 multipoint with LDAC cautions. For message codes 19, 1a, 53 and 73, SC answers with type 04 instead of 00 (`i00/a.java` f()). Flexible type `ft` 0d = disconnect for "change connection" with LE Audio limitation; the items are FlexibleMessageItemId (00 EQUALIZER, 01 DSEE, 02 SPEAK_TO_CHAT, 05 GATT, 0b MULTI_POINT, 10 LE_AUDIO, 12 LINK_AUTO_SWITCHING, 17 QUICK_ACCESS, 18 CONNECTION_MODE, ...).

## 22. What the earbuds send unprompted

| Command | When | Shape |
|---|---|---|
| `25 tt` POWER NTFY_STATUS | battery level or charging change (L/R, case) | section 2 |
| `29 tt` POWER NTFY_PARAM | auto power off changed on the device | section 3 |
| `15 01` / `15 02` / `15 03` COMMON | L/R link, codec, DSEE indicator change | section 8 |
| `65 tt` / `69 tt` NCASM | NC usable or not; mode changed by touch or wearing | section 4 |
| `59 tt` / `55 tt` EQEBB | EQ changed elsewhere | section 5 |
| `e5` / `e9` AUDIO | DSEE, connection mode, stream switch | sections 6, 7 |
| `f5 tt` / `f9 tt` / `fd tt` SYSTEM | touch, Speak-to-Chat state, VA, wearing, reset result, noise cycle | sections 9..14 |
| `d5` / `d9` GENERAL_SETTING | general setting changed | section 15 |
| `99 tt` ALERT | a change the earbuds want confirmed | section 21 |
| `a5` / `a9` PLAY | playback state, volume | section 20 |
| `b5` / `b9` SAR_AUTO_PLAY | GATT connectable state | section 20 |
| `49 0c` / `49 0d` / `49 0e` / `49 0f` LEA | LE setting changed, tandem target change, profile change, disconnecting | section 19 |
| T2 `45` / `49` | voice guidance status and values | section 16 |
| T2 `35` / `39` / `3d` | pairing mode, device list change, action results | section 18 |
| T2 `55` / `59` | safe listening log batches and settings | section 17 |
| T2 `65` / `69` | LE streaming state, LE mode result | section 19 |
| T2 `f5 00` | wearing status (00 NORMAL, 01 ILLEGAL, 02 LEFT_NOT_WORN, 03 RIGHT_NOT_WORN, 04 BOTH_NOT_WORN) | `tg0/q1`, `tg0/i0` |

## 23. Not settled by Sound Connect's code

| Question | What the code shows |
|---|---|
| Voice guidance SET type 01 length | SC's validator (`xg0/i0`) takes only `48 01 onoff` (3 bytes). SunPods sends `48 01 onoff lang` and its comment says the XM5 ignores the change without the language. Both can't be the whole story; test both on a device and make the simulator accept both |
| Second status byte of AUDIO types 02 and 05 | stored, never named |
| AUDIO SET type 05 byte 3 | SC always writes 00 |
| Speak-to-Chat capability bytes a, b, c and the type 02 extended middle byte | read but not named |
| NC level range | comes only from RET_CAPABILITY `61 tt`; no fixed range in code |
| EQ band frequencies | only from RET_EXTENDED_INFO `5b`; the 21-step / +10 offset reading rests on the XM5 flat value 0x0a |
| Quick Access service bytes beyond 00/01/04/07 | come from outside the protocol code |
| AUTO_PLAY / INTEGRATED_AUTO_PLAY on/off | table 1 has capability only; the setting travels another way |
| L/R order of LEA type 00 StreamingStatus | not named |
| Log (0xc1..0xc3) layouts | not decoded in this document |
