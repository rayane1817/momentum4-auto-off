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

## Is there a remote power-off command?
Not in the capture, and not as a dedicated feature in the app. The app's feature registry has 59 entries (AutoPowerOff, Battery, Reboot, FactoryReset, TouchControls, OnHeadDetection, PlaySound, ...) and **no PowerOff feature**. No power-off button in the UI strings either.

### Power-off leads, best first
1. **MMI button action `powerOff`.** `Gaia3MmiConfigurationButtonAction` is an enum the app can write to the headset (button remapping), and value 8 is `powerOff`. So the firmware has a power-off action. Open question: can the app trigger an MMI action directly, or only remap a button? Likely feature 11 (`16xx`). Capture the button/touch settings page.
2. **Short timer value.** Write `0600` with a small value (e.g. 60 s) to test. Only helps if the timer path really switches off; T1/T2 say it only reaches standby on 3.38.3.
3. **Reboot (`RebootGaia3FeatureVariant`).** Exists for Gaia3. Ends in "on", so not a fix, but worth knowing the bytes.
4. **iAP2-channel side effect.** Accidental power-off reported on ACCENTUM. A crash, not a feature. Last resort only.

## Sources
- APK: Smart Control Plus 1.6.3 via APKPure (`apkeep`).
- [Oein/sennheiser-desktop-client PROTOCOL.md](https://github.com/Oein/sennheiser-desktop-client/blob/main/PROTOCOL.md) (analysis of Smart Control Plus 1.5.0, verified on ACCENTUM)
- [momentumctl](https://zff.dev/galen/momentumctl): CLI for MOMENTUM 4 on Linux (BlueZ RFCOMM, same UUID and frames)
