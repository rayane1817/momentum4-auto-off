# MOMENTUM 4 control protocol (Step 1 result, 22 Oct 2026)

Static analysis of Smart Control Plus **1.6.3** (`com.sonova.chb.control`, version code 72844, arm64 XAPK from APKPure, research use only, never installed), cross-checked with two community projects that talk to a real MOMENTUM 4.

## App architecture
- Flutter app. All device logic is Dart, compiled to `lib/arm64-v8a/libapp.so` (Dart 3.10.8, obfuscated symbol names).
- Java/Kotlin side has no protocol code. `com.sonova.chb.bluetooth_classic` is a generic RFCOMM pipe: Dart passes it a service UUID and raw bytes.
- An Airoha SDK (`com.airoha.*`, RACE protocol) is bundled for other models. The M4 is Qualcomm and uses GAIA v3.
- Device configs moved to `assets/.encrypted/` in 1.6.x (plain JSON in 1.5.0).
- Tools: jadx 1.5.1, blutter (Dart AOT). Blutter recovered class names, enums and the object pool, but not function bodies for this Dart version. So exact byte values below come from the community sources, not from this APK.

## Transport
- Bluetooth Classic, RFCOMM (SPP-style).
- Service UUID: `a2129ff3-081b-4c45-8afe-469d9c4842ec` (SDP record named "GAIA").
- Warning from the ACCENTUM work: on macOS, RFCOMM channel 14 is Apple iAP2. Sending GAIA frames there made the headset power off. Never fall back to a guessed channel. (Possibly useful later, see "Power-off candidates".)

## Frame format (GAIA v3 over RFCOMM)
```
byte 0     0xFF               start of frame
byte 1     0x03               version/flags
byte 2-3   payload length     big-endian
byte 4-5   vendor id          0x0495 Sennheiser, 0x001D Qualcomm core
byte 6-7   command id         big-endian
byte 8..   payload
```
Command id = feature << 9 | type << 7 | command.
Type: request 0, notification 1 (`|0x0080`), response 2 (`|0x0100`), error 3 (`|0x0180`).

Verified example: get battery `FF 03 00 00 04 95 06 03` -> `FF 03 00 01 04 95 07 03 2A` (42 %).

## Commands relevant to us (vendor 0x0495)
| what | request | response | payload |
|---|---|---|---|
| Auto power-off timer SET | `0600` | `0700` | `[timerId 0, value u16]` |
| Auto power-off timer GET | `0601` | `0701` | |
| Battery % GET | `0603` | `0703` / notif `0683` | |
| On-head detection SET / GET | `0400` / `0401` | `0500` / `0501` | `[0/1]` |
| Smart Pause SET / GET | `080C` / `080D` | `090C` / `090D` | `[0/1]` |
| Register notifications | `0007` | `0107` | `[featureId]` (battery = 3) |
| Firmware version GET | `1201` | `1301` | 3 x u16 |
| Factory reset | `0040` | | do not send |
| Core: supported features | `001D:0001` | `001D:0101` | |

Auto power-off lives in feature 3 (battery/power). Unit of `value` (seconds or minutes) is not confirmed: Step 2 capture answers it (15 vs 30 in the app).

App-side options for the timer: 15, 30, 60 min, Never (`autoPowerOffFifteenMinutes`, `...ThirtyMinutes`, `...SixtyMinutes`, `autoPowerOffNever`).

## Does a remote power-off command exist?
Probably not as a dedicated command. The app's feature registry lists 59 features (AutoPowerOff, Battery, Reboot, FactoryReset, TouchControls, OnHeadDetection, PlaySound, ...). There is **no PowerOff feature** and no power-off button anywhere in the UI strings.

### Power-off candidates, best first
1. **MMI button action `powerOff`.** `Gaia3MmiConfigurationButtonAction` is an enum the app can write to the headset (button remapping), and value 8 is `powerOff`. So the firmware has a power-off action that can be bound to a gesture. Open question: is there a command to *trigger* an MMI action directly, or only to remap one? Capture needed.
2. **Short timer value.** Write `0600` with the smallest value the firmware accepts. Only helps if the timer path really switches off; T1/T2 say it doesn't on 3.38.3. Low value.
3. **Reboot (`RebootGaia3FeatureVariant`).** Exists for Gaia3. A reboot ends in "on", so not a fix by itself, but worth knowing the bytes.
4. **iAP2-channel side effect.** Accidental power-off reported on ACCENTUM. A crash, not a feature. Last resort only.

## Sources
- APK: Smart Control Plus 1.6.3 via APKPure (`apkeep`). Old Smart Control on APKMirror is behind a Cloudflare challenge, not fetched.
- [Oein/sennheiser-desktop-client PROTOCOL.md](https://github.com/Oein/sennheiser-desktop-client/blob/main/PROTOCOL.md) (analysis of Smart Control Plus 1.5.0, verified on ACCENTUM)
- [momentumctl](https://zff.dev/galen/momentumctl): CLI for MOMENTUM 4 on Linux (BlueZ RFCOMM, same UUID and frames)
