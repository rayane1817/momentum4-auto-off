# MOMENTUM 4 command map (all known commands)

One reference for the app, the PC client and future sessions. State as of 25 Oct 2026, Ryane's M4, firmware 3.38.3. Details and evidence: `config-spec.md` (captures), `scp-static.md` (community tables), CLAUDE.md test log (T1-T22).

Framing: GAIA v3, `FF 03 | len | vendor | command | payload`. Command = `feature << 9 | type << 7 | cmd`. Replies = command | `0100`, notifications | `0080`, errors | `0180`. Codes below are the set/get command.

**Legend:** ✅ confirmed on Ryane's M4 (capture or local session) · 🟡 community tables, not tested by us · ❓ seen on the wire, meaning unknown · ⛔ blocked in the app (`Gaia.blockedReason`) and the PC client (`blocked_reason`)

**Rule for ❓ and ⛔:** never probe. Learn their meaning only by watching what Smart Control sends (marked capture), never by sending them.

## Sennheiser (vendor `0495`)

### Core (feature 0)
| Command | Meaning | Status |
|---|---|---|
| `0007 [feature]` | Subscribe to change notifications (3 battery, 4 audio, 8 EQ, 9 versions, 10 device mgmt, 11 MMI, 12 transparent hearing, 13 ANC) | ✅ |
| `0001` | Read at connect | ❓ |
| `0040` | Factory reset | ⛔ |

### Feature 1 (`02xx`-`03xx`)
| Command | Meaning | Status |
|---|---|---|
| whole range | Firmware update / DFU mode (`0200` = enter DFU) | ⛔ |

### Feature 2: audio and device (`04xx`)
| Command | Meaning | Status |
|---|---|---|
| `0400` / `0401` | On-head detection on/off | ✅ |
| `0402` | Physical state (3 = on head). Always reads on-head (sensor bug, T11) | ✅ |
| `0405` / `0406` | Hi-Res 24/96: `00` = on, `01` = off. Survives a restart | ✅ |
| `0482` | Notification when the on-head sensor is handled | 🟡 |

### Feature 3: power (`06xx`)
| Command | Meaning | Status |
|---|---|---|
| `0600` / `0601` | Auto-off timer `[00, seconds u16]`, 0 = never. Only reaches standby (T1-T6) | ✅ |
| `0603` | Battery % (notif `0683`) | ✅ |
| `0604` | Probably battery voltage (`0EE7` = 3815 mV) | ✅ |
| **`0605`** | **True power-off**, no payload (T8-T10, T12, T20) | ✅ |
| `0606` | Never probed | ❓ |
| `060E` | Seen | ❓ |
| `0682` | Charger in/out notification `[01/00]` | 🟡 |
| `0607`, `0609`-`06FF` | Link reset, headset goes offline (seen at `0613`, `0685`) | ⛔ |

### Feature 4: listening and calls (`08xx`)
| Command | Meaning | Status |
|---|---|---|
| `0800` | Codec in use | ✅ |
| `0880` / `089A` | Audio stream on/off, sample-rate notifications | ✅ |
| `0801` / `0802` | Voice prompts mode (`02` = voice prompts) | ✅ |
| `0803` / `0804` | Sound mode u16: `00 01` normal, `00 02` Speech Clarity | ✅ |
| `0805` / `0806` | Transparency during calls 0-4 (0 = off) | ✅ |
| `0807` | Prompt language, read only | 🟡 |
| `080A` / `080B` | Auto-answer calls | ✅ |
| `080C` / `080D` | Smart pause | ✅ |
| `0814` / `0815` | Comfort call (not inverted) | ✅ |
| `0817` / `0818` | Low latency | ✅ |
| `0808`, `0813`, `0819` | Seen (`0819` = `05 00 01 02 03 04`) | ❓ |
| `081A`, `081D`, `081E` | Answer "not supported" | ✅ |
| `0884`, `0898` | Notifications | ❓ |

### Feature 8: EQ (`10xx`)
| Command | Meaning | Status |
|---|---|---|
| `1001 [band, dB x10]` | Set one band. Bands 0-4 = 63 Hz, 250 Hz, 1 kHz, 4 kHz, 8 kHz. 0.1 dB steps, app range +/-6 dB | ✅ |
| `1002 [band]` | Read one band | ✅ |
| `1003 [00]` | Read all 5 bands (empty payload = error 05) | ✅ |
| `1008` / `1009` | Bass boost | ✅ |
| `1000` | EQ config (bands, min/max gain) | 🟡 |
| `100B` / `100D` / `100F` | Filter centre frequency (90/325/1500/6500/6500 Hz) / Q / type | 🟡 |
| `1011`, `1013` | Unknown | ❓ |
| `1082` | EQ curve notification (5 gains) | ✅ |
| `1089`, `108B`-`1093` | Notifications | ❓ |

Presets are app-side (no preset command). Smart Control's 8 presets: see `android/gaia-test/app/src/main/java/be/ryane/gaiatest/Presets.java` (verified against capture 2).

### Feature 9: versions (`12xx`)
| Command | Meaning | Status |
|---|---|---|
| `1200` | Hardware revision (u16) | ✅ |
| `1201` | Firmware version (3 x u16) | ✅ |
| `1206` | Model name (ASCII) | ✅ |
| `1202` | Unknown | ❓ |

### Feature 10: connections (`14xx`)
| Command | Meaning | Status |
|---|---|---|
| `1401 [index]` | Paired device list (returns device names: privacy) | 🟡 |
| `1402 [index]` | Connect a device (seen from Smart Control) | ✅ |
| `1403 [index]` | Disconnect a device, pairing kept (T19). Blocked anyway | ⛔ |
| `1405` | "Delete entry" (old app name). Smart Control sent it once in capture 2. May remove a pairing | ⛔ |
| `1406` | Delete pairing / write rejected | ⛔ |
| `1407` | Not a restart flag: `01` through Hi-Res toggles and a restart (T21), then `00` after a guard power-off + button (T35). Not stable, meaning unknown. Read only | ❓ |
| `1409` (reads `02`), `140B` (6 zero bytes), `1400`, `1404` | Unknown | ❓ |
| `1484` | Connection notification | ✅ |
| `1500` | Rename device (blocked in the PC client) | ⛔ |

### Feature 11: controls (`16xx`)
| Command | Meaning | Status |
|---|---|---|
| `1606` / `1607` | Touch controls, **inverted**: `00` = on, `01` = off (notif `1687`) | ✅ |

### Feature 12: transparent hearing (`18xx`)
| Command | Meaning | Status |
|---|---|---|
| `1800 [0/1]` | Probably "pause automatically in transparency" (Smart Control write, T17) | ❓ |
| `1804` / `1805` | Transparency toggle (ACCENTUM-style). Smart Control doesn't use it on the M4, but the M4 accepts it (T13, notif `1885`) | ✅ |
| `1801` | Unknown | ❓ |

### Feature 13: noise control (`1Axx`)
| Command | Meaning | Status |
|---|---|---|
| `1A00 [mode, state]` | Mode 1 anti-wind: 0 off, **1 Max, 2 Auto** (T17). Mode 3 adaptive: 0/1. 2-byte and 6-byte forms both accepted (T15) | ✅ |
| `1A01` | Read the noise table `[01 x 02 x 03 x]` | ✅ |
| `1A02` / `1A03` | Level 0-100: 0 = full ANC, 100 = full transparency | ✅ |
| `1A04` / `1A05` | ANC on/off | ✅ |
| `1A81` / `1A83` / `1A85` | Notifications | ✅ |

Smart Control's three modes: Adaptive = `1A04 01` + `1A00 03 01`; Custom = `1A00 03 00` + `1A04 01` (+ level); Off = `1A00 03 00` + `1A04 00`.

### Feature 16 (`20xx`) and others
| Command | Meaning | Status |
|---|---|---|
| `2001`, `2003`, `2005`, `2007` | Zero-filled replies | ❓ |
| `2081` | Notification | ❓ |
| `2601` | Answers with an error | ❓ |

## Qualcomm (vendor `001D`)
| Command | Meaning | Status |
|---|---|---|
| `0001` | Supported features | ✅ |
| `0003` | Serial number (privacy) | 🟡 |
| `0E10` / `0E80` | Seen at connect | ❓ |
| `0Cxx` (feature 6) | Upgrade channel: voice-prompt language, firmware updates and their restart | ⛔ |

## Legacy Qualcomm (vendor `000A`)
| Command | Meaning | Status |
|---|---|---|
| `0204 [00]` | Old "set power state off": answered "not supported" (T7) | ✅ dead end |

## Transport rule (not a command, still forbidden)
Never send GAIA on a guessed RFCOMM channel. Find it by the service UUID `a2129ff3-081b-4c45-8afe-469d9c4842ec`. On some hosts channel 14 is Apple iAP2, and GAIA frames there switched an ACCENTUM off (Oein).

## Sources
- Our captures: capture 1 (22 Oct), capture 2 (`config-spec.md`), capture 3 / T17 (`captures/capture3_scp_writes_redacted.md`).
- Local hardware sessions: CLAUDE.md T15-T22.
- Community: [Oein/sennheiser-desktop-client](https://github.com/Oein/sennheiser-desktop-client), [DanSmith888/omarchy-momentum4](https://github.com/DanSmith888/omarchy-momentum4), [timmo001/omarchy-momentumctl](https://github.com/timmo001/omarchy-momentumctl), [momentumctl](https://codeberg.org/galen/momentumctl), [f3Y0/momentum4-control](https://github.com/f3Y0/momentum4-control).
