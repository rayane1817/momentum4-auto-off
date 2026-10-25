# Prior art and ecosystem (research, 24 Oct 2026)

Web research on existing open-source tools for the MOMENTUM 4, Gadgetbridge, public GAIA v3 material, and widget/tray options for Android and Windows.
Status marks: **[verified]** = read in the project's own source/docs; **[claimed]** = stated by the project, not tested by us; **[unverified]** = our inference or not checked.

## 1. Open-source MOMENTUM 4 projects

| Project | Platform / stack | License | Features beyond ours | Notes |
|---|---|---|---|---|
| [DanSmith888/omarchy-momentum4](https://github.com/DanSmith888/omarchy-momentum4) | Linux, Python CLI + Omarchy/Waybar widget | MIT | EQ 5 bands + 8 presets, bass boost, Speech Clarity, anti-wind, touch controls on/off, comfort call, auto power-off timer | **Best find.** `PROTOCOL.md` is a full command table, tested on fw 3.38.3 (notes on 2.13.42). No Hi-Res, no voice prompts. [verified: read README + PROTOCOL.md] |
| [ijaz-miks/momentum-4-gnome](https://github.com/ijaz-miks/momentum-4-gnome) | GNOME extension + `momentumctl` CLI (v0.2.1) | MIT | Multipoint on/off with control-channel recovery, EQ, bass, speech/podcast modes, presets | Auto power-off described as "gated by firmware" (same finding as T1-T6). [verified: README] |
| [SilentSoulsSr/SenheiserMomentum4](https://github.com/SilentSoulsSr/SenheiserMomentum4) | Windows tray, .NET 10 Blazor Hybrid (WinForms + WebView2) | MIT | Multipoint device list (switch/connect/disconnect), EQ 5/10 bands, sound modes, battery, start with Windows | Uses WinRT `Windows.Devices.Bluetooth.Rfcomm`, service resolved by UUID. Its `NOTES.md` lists only 1A04/1A05, 1A02/1A03, 1008/1009, taken from f3Y0 and marked untested. [verified: README + NOTES.md] |
| [f3Y0/momentum4-control](https://github.com/f3Y0/momentum4-control) | Linux / KDE tray | not checked | Noise slider, ANC off, bass boost | Self-described "100% AI generated". [claimed] |
| [fxckthiswrld/m4-ctl](https://github.com/fxckthiswrld/m4-ctl) | Electron UI + Python BT bridge, Windows/macOS | not checked | not checked | [unverified] |
| [Jazden/noctalia-momentumpanel](https://github.com/Jazden/noctalia-momentumpanel) | Noctalia shell panel, `momentumd` daemon over Unix sockets | not checked | Daemon model (one RFCOMM holder, many clients) | Interesting architecture for a widget backend. [unverified] |
| [Asfax03L/openheadset-control](https://github.com/Asfax03L/openheadset-control) | MOMENTUM 5, Rust core + WinUI 3 + Kirigami | not checked | Shared Rust core across Windows and KDE | Different model, same GAIA v3 family likely. [unverified] |
| [Oein/sennheiser-desktop-client](https://github.com/Oein/sennheiser-desktop-client) (fork: zaval) | Desktop | see repo | Other Sennheiser models | Already credited in README; not re-read. |
| [momentumctl (zff.dev/galen)](https://zff.dev/galen/momentumctl) | CLI | see repo | | Already credited in README; not re-read. |

None of these does a phone-side idle guard + `0605` true power-off. That part is still unique to this repo [unverified: none of the READMEs mention 0605 or remote power-off].

## 2. omarchy PROTOCOL.md vs our docs/config-spec.md (Capture 2)

### Confirms ours

| Item | Both agree |
|---|---|
| Framing | `FF 03 len vendor cmd payload`, vendor 0x0495 |
| Errors | 0x05 malformed / invalid parameter; they add 0x83 = out of range |
| EQ | `1001 [band, dB x10]` set, `1002 [band]` get |
| EQ presets | App-side only, not stored on the headset |
| Speech Clarity | `0803` with `00 01` = EQ, `00 02` = Speech Clarity |
| Auto-off timer | `0600/0601`, `00 <sec u16>`, 0 = Never |
| On-head detection | `0400/0401` |
| Auto-answer, smart pause | `080A/080B`, `080C/080D` |
| Events | `0880` audio link, `089A` sample rate, `0482` on-head; they add `0682` charging |

### New to us (not in our capture)

| Cmd | Meaning (per omarchy) | Status |
|---|---|---|
| `1003` | Get all EQ gains in one call | **T15: error 05 with empty payload on 3.38.3** |
| `100B` | EQ centre frequencies (90, 325, 1500, 6500, 6500 Hz) | [claimed] |
| `100D` | EQ Q values | [claimed] |
| `100F` | EQ filter type (14 on fw 3.x) | [claimed] |
| `1011`, `1013` | EQ related, partly unknown | [claimed] |
| `1607` get / `1606` set | Touch controls, **inverted**: 0 = enabled, 1 = disabled | **confirmed T15-T17** (SCP sends it, notif `1687`) |
| `0814/0815` | Comfort call set/get (not inverted) | **confirmed T15-T17** (no notification) |
| `1401 [idx]` | Paired device name by index | [claimed] read-only, safe |
| `1407` | "Audio mode change pending" restart flag | GET answers `[01]` on 3.38.3, also after a power cycle (T15, T21): not a restart-pending flag here |
| `1409`, `140B` | Unknown | [claimed] |
| Bass boost `1008` | Rejected on fw 2.x | [claimed] |

### Contradictions to resolve

| Topic | Ours (Capture 2) | omarchy | Who is likely right |
|---|---|---|---|
| `1A00` noise-mode write | Official app sends 2 bytes `1A00 [mode, state]` | Says a set needs the full 6-byte table; partial writes rejected | **Resolved T15 (fw 3.38.3): both forms accepted.** Keep the 2-byte form (what SCP sends). |
| Anti-wind values | (Capture 2 note) 1 = Auto, 2 = Max | 1 = Max, 2 = Auto | **Resolved T17: omarchy right, 1 = Max, 2 = Auto** (marked capture, twice). Our v0.8 app has the labels swapped. |
| ANC set/get ids | `1A04` set, `1A05` get; `1A02` set level, `1A03` get | Lists `1A05` as set, `1A03` as level set | Capture confirms ours; also T13 accepted our ids. Ours. |
| Forget device | Official app sent `1405` on disconnect/delete | `1403` forgets a device | **T19: SCP's disconnect sends `1403 [idx]` and keeps the pairing** (plain disconnect). `1405` meaning still unknown. Both stay **blocked**. |
| `0801`, `0805`, `0817` | Voice prompt mode, call transparency 0-4, low latency | Blocklisted as "unknown actions" | Ours from capture. They were cautious, not contradicting. |

### Danger list (important, next to our `0605`)

| Cmd | Effect per omarchy |
|---|---|
| `0607` | Reset / power cycle |
| `0609` to `06FF` | Resets seen at `0613`, `0685` |

Our power-off `0605` sits in the same group. **Never sweep 0606 to 06FF.** Worth adding `0607` and `0609-06FF` to `Gaia.blockedReason` and to `gaia_client.py` (main agent decides).

## 3. Gadgetbridge

| Point | Finding |
|---|---|
| Sennheiser support | Only MOMENTUM In-Ear Wireless (M2 IEBT), "highly supported", nightly builds only. [verified: Gadgetbridge device page] |
| Source | Codeberg PR #6805. Includes "Power off from phone", no vendor app needed. [verified: page] |
| MOMENTUM 4 | Not supported. |
| Contribution path | New device coordinator + GAIA v3 framing over RFCOMM (Gadgetbridge already has RFCOMM support classes for other headphones, e.g. Sony, Nothing). Command tables from omarchy + ours. [unverified: difficulty not measured; codebase not read] |
| What it would give | Mature app on F-Droid, existing QS tiles, battery widget, intents for Tasker/automation. Idle guard would need to be ported as a new feature. |
| Licence | AGPL-3.0 (compatible direction: our GPL-3.0 code can go into AGPL). [unverified: check before contributing] |

Strategic option: keep our app small for the power-off guard, and upstream the M4 config features to Gadgetbridge instead of rebuilding Smart Control Plus feature by feature.

## 4. Public GAIA v3 resources

| Resource | Status |
|---|---|
| Public GAIA v3 spec | None found. |
| Qualcomm GAIA_Client.SRC.1.0 (Android/iOS) | Exists, licensed "QTIL Standard OEM", not public. |
| Best public references | omarchy PROTOCOL.md (M4-specific), Gadgetbridge Sennheiser M2 code, Oein/momentumctl (other models), our docs/protocol.md. |

## 5. Android widget / tile

### Prior art

| App | What | Relevance |
|---|---|---|
| [Quiet Rebellion](https://github.com/MadEyez/quietrebellion) (Bose QC Ultra 2) | Persistent notification with ANC toggle over RFCOMM | Same pattern we need. Notes the vendor app must not run (single RFCOMM holder), same as ours. |
| Noise Cancel (F-Droid `dev.danjackson.noisecancel`) | QS tile + launcher shortcut, ANC off only | QS tile model works. |
| Bluetooth Audio Connect Widget | QS tile, battery | Not RFCOMM control. |
| Exact OSS RFCOMM ANC home-screen widget | Not found | |

### Recommended design [unverified: best practice, not built yet]

| Choice | Recommendation |
|---|---|
| First surface | **Quick Settings tile** (`TileService`): cycles ANC / Transparency / Off. Simpler than a widget, works from lock screen, gets a foreground-ish context on tap. |
| Second surface | Home-screen widget (`AppWidgetProvider`, `RemoteViews`), buttons via `PendingIntent.getForegroundService(...)` (FLAG_IMMUTABLE). |
| Who opens RFCOMM | Short-lived foreground service, type `connectedDevice`: connect, send one command, read the echo, stop. Do not do RFCOMM in the widget's `onReceive` (10 s limit, ANR risk). |
| Reuse | If AutoOffService is running, route the command through it instead of opening a second socket. |
| Permissions | `BLUETOOTH_CONNECT` (runtime, Android 12+), `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_CONNECTED_DEVICE` (Android 14+). |
| Android 14/15 constraints | Starting an FGS from the background is restricted; a user tap on a widget/tile/notification is an allowed exemption. Android 15 limits some FGS types started from `BOOT_COMPLETED` (`connectedDevice` is not in the blocked list as far as known). [unverified: check current docs] |
| Collisions | Smart Control Plus holds RFCOMM when open: tile must show "busy" instead of failing silently. |
| Samsung | MARS/Device Care freezing (see CLAUDE.md) can kill the service: ask user to set the app to "Unrestricted" battery. |

Requested by u/TheUnAmericanGenral (ANC mode + EQ preset). EQ preset = 5 x `1001` writes (presets are app-side).

## 6. Windows

| Option | Pros | Cons |
|---|---|---|
| Tray app on top of `windows-client/gaia_client.py` (e.g. `pystray`) | Reuses our tested AF_BTH transport, small | Needs Python or a PyInstaller exe; pystray is an extra package |
| WinRT RFCOMM by UUID (like SilentSoulsSr) | Native, resolves channel by service UUID, no Python | New C# code, .NET runtime |
| Just use SilentSoulsSr's app + add `0605` | Already has tray, multipoint list | Not our code; would need a PR upstream for power-off |

Recommendation [unverified]: simplest is a pystray tray on `gaia_client.py` (power off, ANC cycle, battery). For a polished app, contribute `0605` + auto-off to SilentSoulsSr instead of writing a new one.

**BTD600 dongle limitation:** the dongle holds the headset link as its own audio device and does not expose RFCOMM to Windows. A PC tool only works if the M4 is also paired to the PC's built-in Bluetooth (multipoint: dongle + PC radio, or phone + PC radio). See docs/todo-cloud.md.

## Single most useful find

**DanSmith888/omarchy-momentum4 `PROTOCOL.md`**: a full MOMENTUM 4 command table tested on fw 3.38.3. It fills gaps we would otherwise need more captures for (touch controls `1606/1607`, comfort call `0814`, EQ metadata `1003-1013`, `1407` restart flag) and lists link-resetting commands (`0607`, `0609-06FF`) right next to our `0605`.
