# MOMENTUM 4 control protocol

Two sources, merged 22 Oct 2026:
- **Capture** (ground truth): `captures/btsnoop_hci_202610221800.cfa` (Samsung, SysDump), parsed with `tools/btsnoop_parse.py`. Ryane's M4, firmware 3.38.3, Smart Control Plus.
- **Static analysis**: Smart Control Plus 1.6.3 (`com.sonova.chb.control`, version code 72844, arm64 XAPK via APKPure, research only, never installed), plus two community projects that talk to an M4 (sources at the end).
Where they disagree, the capture wins.

Capture clock note: timestamps decode ~2 h ahead of Brussels local time (e.g. 19:42 in the parser = ~17:42 local).

## App architecture (static)
- Flutter app. All device logic is Dart, compiled to `lib/arm64-v8a/libapp.so` (Dart 3.10.8, obfuscated symbol names).
- The Java/Kotlin side has no protocol code. `com.sonova.chb.bluetooth_classic` is a generic RFCOMM pipe: Dart passes it a service UUID and raw bytes.
- An Airoha SDK (`com.airoha.*`, RACE protocol) is bundled for other models. The M4 is Qualcomm (GAIA v3).
- Device configs moved to `assets/.encrypted/` in 1.6.x (plain JSON in 1.5.0).
- Tools: jadx 1.5.1, blutter. Blutter recovered class names, enums and the object pool, but no function bodies for this Dart version, so byte values cannot be read from the code directly.

## Transport
- Bluetooth Classic, headset MAC 80:C3:BA:4D:6A:24.
- RFCOMM DLCI 20 (server channel 10): standard Hands-Free Profile AT commands. Not relevant.
- RFCOMM DLCI 30 (server channel 15): GAIA control channel. Opened by the phone right after connect.
- Find the channel through SDP, don't hard-code it. Service UUID `a2129ff3-081b-4c45-8afe-469d9c4842ec` (SDP name "GAIA").
- Warning from the ACCENTUM community work: on macOS, channel 14 was Apple iAP2, and sending GAIA frames there made the headset power off. Never fall back to a guessed channel. (Also listed as a last-resort power-off lead below.)

## Framing: Qualcomm GAIA v3
`FF | ver | flags | len | vendor_id (2, BE) | command_id (2, BE) | payload (len bytes)`
- ver: 0x03 phone->headset, 0x04 headset->phone (observed). Community code treats `FF 03` as start + flags.
- vendor_id: 0x001D = Qualcomm core; 0x0495 = Sennheiser/Sonova features.
- command_id = feature << 9 | type << 7 | cmd. type: 0 command, 1 notification (`|0x0080`), 2 response (`|0x0100`), 3 error (`|0x0180`).

## Decoded: auto-off timer (vendor 0x0495, feature 3) - capture-confirmed
Ryane changed the timer 15 -> 30 -> 15 min in the app:
- GET timer: `FF 03 00 01 04 95 06 01 00` -> response `... 07 01 00 03 84` (0x0384 = 900 s = 15 min)
- SET timer: `FF 03 00 03 04 95 06 00 00 07 08` (0x0708 = 1800 s = 30 min) -> empty response = OK
- SET timer: `FF 03 00 03 04 95 06 00 00 03 84` (900 s)
- Payload: timer id `00` + uint16 big-endian **seconds**.
- App options: 15, 30, 60 min, Never (`autoPowerOffFifteenMinutes`, `...ThirtyMinutes`, `...SixtyMinutes`, `autoPowerOffNever`). Encoding of "Never" not yet seen (likely 0).

Other feature 3 values read on connect:
- cmd 3 -> `3C`, also sent as a notification: **battery %** (60 %). Community confirms `0603` = battery GET, `0683` = battery notification.
- cmd 2 -> `00 00`; cmd 14 -> `03 FF`: unknown.

## Feature map (feature number -> id range -> meaning)
Meanings from community code, matched to the features seen in the capture.

| feature | ids | meaning | known commands |
|---|---|---|---|
| 2 | `04xx` | on-head detection / device settings | `0400` SET, `0401` GET (capture: `01` = on); `0405/0406` BT compatibility |
| 3 | `06xx` | battery + auto power-off | see above |
| 4 | `08xx` | generic audio | `0801/0802` voice prompts, `0805/0806` sidetone, `080A/080B` auto-answer, `080C/080D` smart pause, `0814/0815` comfort call |
| 8 | `10xx` | user EQ | `1000` config, `1001/1002` band SET/GET |
| 9 | `12xx` | versions / product info | `1201` firmware, `1206` model id |
| 10 | `14xx` | device management (paired devices) | `1400-1405`. Capture: notification cmd 11 at ~17:51 local, `00 00 00 00 00 00` |
| 11 | `16xx` | MMI (buttons) | not decoded yet |
| 12 | `18xx` | transparent hearing | `1804/1805` |
| 13 | `1Axx` | ANC / noise control | `1A00/1A01` mode, `1A04/1A05` ANC on/off |
| 16, 19 | `20xx`, `26xx` | seen in capture, unknown | |

Capture leftovers for feature 2: cmd 2 -> `03`, cmd 6 -> `01`, meaning unknown.

Other commands (community, not in capture): register notifications `0007 [featureId]`, factory reset `0040` (never send), Qualcomm core supported features `001D:0001`.

## Old Smart Control (pre-Plus) analysis
Package `com.sennheiser.control`. APKPure has 4.0.0 to 4.9.3 (release dates not retrievable; APKPure's version page blocks scripted access). Downloaded 4.0.0, 4.0.5, 4.1.0, 4.1.6, 4.2.0, 4.3.0, 4.4.0, 4.5.0, 4.6.0, 4.7.0, 4.8.0, 4.9.3 (4.9.0 download was corrupt).
- **4.0.0 is the oldest version available and already supports the M4** (`assets/app/m4.json`, `"protocol": "GAIAV3"`). It also already has the auto power-off setting for the M4. So 4.0.0 is the answer to "oldest app with M4 auto-off" as far as mirrors go.
- NativeScript/Angular app: logic is plain JavaScript (`assets/app/bundle.js`), and each product has a readable JSON command table. Much easier to read than the Flutter app.
- The M4 table barely changes from 4.0.0 to 4.9.3 (adds personalised sound, model id, serial, BT compatibility, low latency, core API version). The timer commands are identical in every version.

### M4 command table (from m4.json 4.0.0, vendor 0x0495 unless noted)
| name in app | request | response | payload |
|---|---|---|---|
| `Config_SetTimerConfiguration` | `0600` | `0700` | `[timer u8, value u16]` |
| `Config_GetTimerConfiguration` | `0601` | `0701` | `[timer u8]` -> `[timer, value u16]` |
| `Battery_ChargingState_Get` | `0602` | | |
| `Battery_GetBatteryLevels` | `0603` | | |
| `OnHeadDetection_Mode_Set/Get` | `0200` / `0201` in 4.0.0 config | | see warning below |
| `Upgrade_EnterDfuMode` | `0200` | `0300` | **never send** |
| `Upgrade_SetUpgradeFilesize` | `0201` | `0301` | |
| `Service_FactoryReset` | `0040` | `0140` | **never send** |
| `Development_Primary_Side` | `0041` | | |
| `MMI_SetConfig` | `1600` | `1700` | `[button, pattern, function]` |
| `MMI_GetConfig` | `1601` | `1701` | `[button, pattern]` -> `[button, pattern, function]` |
| `MMI_SetDefaultConfig` / `MMI_IsDefaultConfig` | `1604` / `1605` | | |
| `System_AutoLock_Set/Get` (touch lock) | `1606` / `1607` | | `[mode]` |
| `DeviceList_*` (paired devices) | `1400`-`1409` | | connect `1402 [idx]`, disconnect `1403 [idx]` |
| `Config_RegisterNotification` | `0007` (vendor 0x001D and 0x0495) | `0107` | `[featureId]` |
| Upgrade connect / disconnect | `001D:0C00` / `001D:0C01` | | |

**Warning:** in the 4.0.0 config, `0x0200` is listed both as on-head detection SET and as Enter DFU (firmware update) mode. The 3.38.3 capture uses feature 2 (`04xx`) for on-head detection. Treat `0495:0200` as "enter update mode" and never send it.

### Timers: the key finding
`Config_GetTimer` maps timer numbers to names:
- `POWER_OFF_TIMER = 0`: the one the app sets. Options: Never = 0, 15 / 30 / 60 min = 900 / 1800 / 3600 s.
- `LIMBO_TIMEOUT = 1`: **defined but never written by any app version.**

In Qualcomm's headset firmware (ADK), "limbo" is the state where the application is logically off but the chip is still powered. The device leaves limbo for real power-off ("dormant") after a timeout. That matches T1/T2: after 15 min the M4 goes logically off but the radio stays reachable. Hypothesis: the auto-off timer (timer 0) only sends the M4 into limbo, and the limbo timeout (timer 1) is very long or disabled on 3.38.3.

**Result 22 Oct 2026 (fw 3.38.3): timer 1 does not exist.** GET/SET timer 1 (and 2-4) return error `0781`/`0780` with code `05` (GAIA v3: invalid parameter). Timer 0 accepts any value, e.g. 60 s (`06 00 00 00 3C` -> `0700` OK).
Feature 4 notifications: `0880 [codec]` (FF = no stream, 02 seen while streaming) and `089A [u32 sample rate]` (0xAC44 = 44100 Hz, 0 = stopped): audio stream start/stop, not on-head events. `148B [6 x 00]` (feature 10 notif): device connection status.
T5: with the phone connected and timer 0 = 60 s, the headset stayed on and connected for 9+ min off-head. The timer does not run while a device is connected.

Original test plan (kept for reference):
1. Read timer 1: `FF 03 00 01 04 95 06 01 01`. Expect `... 07 01 01 HH LL`.
2. If it's large or 0, set it short, e.g. 60 s: `FF 03 00 03 04 95 06 00 01 00 3C`.
3. Repeat T2 with the auto-off timer at 15 min. If the M4 no longer reconnects, that's the fix, and no idle-watching app is needed.
4. Restore the original value if anything looks wrong.

### MMI action codes (same in old and new app)
Function: NONE 0, PLAY_PAUSE 1, NEXT 2, PREVIOUS 3, VOL_UP 4, VOL_DOWN 5, TRANSPARENT_HEARING 6, VOICE_ASSISTANT 7, **POWER_OFF 8**, ANC 9, ANSWER 128, REJECT 129.
Pattern: TAP_1, TAP_2, TAP_3, LONG_PRESS. Button: LEFT, RIGHT. (Numeric values for pattern/button not yet pinned.)
`MMI_SetConfig` only remaps a gesture; there is no "trigger action now" command. The old app only shows this for earbuds.

### Remote power-off on other Sennheiser products
TW1, TW2 and CX400 (Qualcomm GAIA v1/v2) have a real remote power-off in the old app ("switchoff_title" tile with a confirm dialog): `Control_SetPowerState`, vendor `0x000A`, command `0x0204`, payload `00` = off, `01` = on. This is Qualcomm's legacy GAIA "Set Power State" command. The M4 config never lists it, and GAIA v3 framing differs, so it probably doesn't exist on the M4. It's cheap to try once as a long shot.
**Result T7 (fw 3.38.3): not supported.** Both framings answer `000A:8204 [01]` (legacy ack bit 0x8000, status 01 = not supported). The firmware still parses legacy vendor 000A frames, it just doesn't implement this one.

## Is there a remote power-off command?
Not in the capture, and not as a dedicated feature in the app. The app's feature registry has 59 entries (AutoPowerOff, Battery, Reboot, FactoryReset, TouchControls, OnHeadDetection, PlaySound, ...) and **no PowerOff feature**. No power-off button in the UI strings either.

### Power-off leads, best first
0. **Limbo timeout (timer 1).** See "Timers: the key finding" above. Cheapest test, and it fixes the problem in firmware if it works.
1. **MMI button action `powerOff`.** `Gaia3MmiConfigurationButtonAction` is an enum the app can write to the headset (button remapping), and value 8 is `powerOff`. So the firmware has a power-off action. Open question: can the app trigger an MMI action directly, or only remap a button? Likely feature 11 (`16xx`). Capture the button/touch settings page.
2. **Short timer value.** Write `0600` with a small value (e.g. 60 s) to test. Only helps if the timer path really switches off; T1/T2 say it only reaches standby on 3.38.3.
3. **Reboot (`RebootGaia3FeatureVariant`).** Exists for Gaia3. Ends in "on", so not a fix, but worth knowing the bytes.
4. **iAP2-channel side effect.** Accidental power-off reported on ACCENTUM. A crash, not a feature. Last resort only.

## Sources
- APK: Smart Control Plus 1.6.3 via APKPure (`apkeep`).
- [Oein/sennheiser-desktop-client PROTOCOL.md](https://github.com/Oein/sennheiser-desktop-client/blob/main/PROTOCOL.md) (analysis of Smart Control Plus 1.5.0, verified on ACCENTUM)
- [momentumctl](https://zff.dev/galen/momentumctl): CLI for MOMENTUM 4 on Linux (BlueZ RFCOMM, same UUID and frames)
