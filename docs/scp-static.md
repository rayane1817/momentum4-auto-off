# Static command map: Smart Control Plus + community tables (24 Oct 2026)

Step 1 of the roundtable plan. Goal: fill gaps in our M4 command map without new captures, before the local session proves things on hardware.

## What was tried
- **Smart Control Plus 1.6.3** (latest on APKPure, version code 72844, already on disk). Device configs are in `assets/flutter_assets/assets/.encrypted/` (101 files plus `index.json` with a `keyFingerprint`). They are deliberately encrypted. **Not decrypted**: getting around a protection measure is a different legal category from interoperability decompiling, so we stay out of it.
- **SCP 1.5.0 / 1.5.1** (last versions with plain JSON configs). Direct mirror download was not possible from the cloud sandbox. If wanted, the local session can get it legally (it is not installed, just unzipped): see "Next" below.
- **Community tables** (public, read in full):
  - [DanSmith888/omarchy-momentum4 `PROTOCOL.md`](https://github.com/DanSmith888/omarchy-momentum4/blob/main/PROTOCOL.md) (MIT), M4 on fw 3.38.3, verified by set, readback and live watching.
  - [Oein/sennheiser-desktop-client `PROTOCOL.md`](https://github.com/Oein/sennheiser-desktop-client/blob/main/PROTOCOL.md): analysis of SCP 1.5.0's plain configs, verified on ACCENTUM. Same Sennheiser GAIA stack.
- Not used: JADX MCP (SCP is Flutter, the Java side has no protocol code), Frida (hooks the official app).

## New, usable now (not yet in our app)
| Feature | Command | Source | Confidence |
|---|---|---|---|
| **Comfort call** | SET `0814 [0/1]`, GET `0815` | omarchy (set, readback, app watch), Oein | high |
| **Touch controls** | SET `1606 [x]`, GET `1607`. **Inverted: 0 = enabled, 1 = disabled** | omarchy | high |
| **Restart pending** | GET `1407`: `01` right after a Hi-Res change, `00` after the headset restarted (Hi-Res stays on). GET only; write to `1406` rejected | omarchy | high, local session confirms |
| **EQ, all bands at once** | GET `1003 [00]` returns all 5 gains (empty payload = error) | omarchy | high |
| **EQ config** | GET `1000` returns `[bands, minGain, maxGain, ...]` (dB x10) | Oein (HDB 630 / ACCENTUM) | medium on M4 |
| **Charging** | notification `0682 [01/00]` cable in/out. No getter | omarchy | high |
| **On-head event** | notification `0482` (02/03 seen when handled) | omarchy | medium |
| **More notification features** for `0007 [id]` | battery 3, genericAudio 4, userEQ 8, versions 9, deviceManagement 10, mmi 11, transparentHearing 12, ANC 13 | Oein (from configs) | high (matches what SCP registers in our capture: 02,03,04,08,0a,0b,0c,0d,10) |

## Answers to open questions
- **`1407`**: already characterised by omarchy (above). The local-session check now confirms it instead of discovering it.
- **Call transparency**: omarchy never found it and guessed it is phone-side. Our capture 2 proves it is a headset write: `0805 [0-4]`. Our map wins.
- **Hi-Res**: omarchy didn't find the setting. Our capture: `0405 [00]` on, `[01]` off (Oein calls `0405/0406` "BT compatibility", same thing).

## Safety: new and confirmed traps
| Command | What happens | Status |
|---|---|---|
| **`1403 [index]`** | **Deletes that paired device** (omarchy lost the phone's pairing this way) | **Added to both blocklists in this change** |
| `1405`, `1406` | Delete entry / pairing (old app names) | already blocked |
| `0607`, `0613`, `0685` (and the `0609`-`06FF` group) | Link reset, headset offline | already blocked |
| `0801`, `0805`, `0817` with a device index | omarchy blocklisted them on the "indexed payload, empty OK" signature, effect unknown | **Not blocked.** SCP itself sends `0801 [02]` (voice prompts), `0805 [level]` (call transparency) and `0817 [0/1]` (low latency), and T13 used `0817`. Use only those exact forms. |

## Confirmed against our own capture 2 (25 Oct 2026)
- **EQ presets**: all 8 Smart Control presets (omarchy `presets.json`, also used by timmo001/omarchy-momentumctl) match the `1082` curves in capture 2 exactly, in the order Ryane tapped them (22:31:06 to 22:31:36). dB: Neutral 0/0/0/0/0, Rock 0/2/2.5/1.5/-2, Pop 0/-2.5/0/2.5/0, Dance 3.5/2/-1.5/1.5/3, Hip-Hop 3/1.5/-1.5/0/-1.5, Classical -2/-1.5/0/3.5/4, Movie 0/0/2/2/-2, Jazz -3.2/0/2.2/2.2/0.
- **EQ step**: the slider writes 0.1 dB steps (curves with 0.4 and 0.1 dB seen at 22:33), up to +6.0 dB.
- **`1A00` 2-byte writes work**: every `1A00 [01 x]` write in capture 2 is followed by a `1A81` notif showing the new value. omarchy's "no partial writes" does not hold on Ryane's M4 (both forms may work).

## More sources (25 Oct 2026)
- [timmo001/omarchy-momentumctl](https://github.com/timmo001/omarchy-momentumctl) (TypeScript CLI + panel): same command table as omarchy, adds a Harman preset fitted from AutoEq (-3.5/-0.5/2.5/-0.5/-0.5 dB), reads EQ with `1003 [00]`, writes anti-wind by read-modify-write of the 6-byte `1A01` table with order off/max/auto. Its `.agents/skills` are generic coding skills (Effect, TypeScript style, code review, writing style), nothing protocol-specific.
- [f3Y0/momentum4-control](https://github.com/f3Y0/momentum4-control) (Python GUI): ANC, transparency, bass boost; finds GAIA by probing RFCOMM channels `[2, 1, 15, 14, 12, 3..11]`. We keep SDP/UUID lookup: channel 14 can be Apple iAP2 on some hosts, and sending GAIA there powered an ACCENTUM off (Oein).
- [gjabell/momentumctl](https://codeberg.org/galen/momentumctl) (Rust CLI): source of the battery, on-head, auto-answer, smart pause and comfort-call IDs; nothing new beyond those.

## Conflicts to settle on hardware (local session, Phase 4/5)
| Item | Ours (capture 2) | omarchy | Note |
|---|---|---|---|
| Anti-wind values in `1A00 [01, x]` | 1 = Auto, 2 = Max | 1 = Max, 2 = Auto (timmo001 ships the same) | Session-start `1A81` had anti-wind = 2 while SCP showed Max, which supports ours. Phase 5 (SCP label oracle) settles it. |
| `1A00` write length | SCP sends 2 bytes `[mode, state]` | "no partial writes", full 6-byte table | **Settled for 2 bytes**: capture 2 shows the headset applying them (see above). |
| EQ band labels | SCP UI 63 / 250 / 1k / 4k / 8k Hz | `100B` centres 90 / 325 / 1500 / 6500 / 6500 Hz | Labels vs real filter centres. Cosmetic. Keep the SCP labels. |

## Next
1. **App v0.9** (cloud, after the local session's Phase 4 confirms the bytes): comfort call switch, touch-controls switch (inverted), EQ refresh via `1003 [00]`, charging indicator from `0682`, register `0007 [03]` for live battery, and a "Restart needed" hint from `1407` next to Hi-Res.
2. **Optional, only if gaps remain after v0.9**: the local session downloads SCP 1.5.1 (`com.sonova.chb.control` version code 68864) in a browser, unzips it (never installs it), and reads `assets/flutter_assets/assets/configs/m4.json`. That gives the feature-variant list for the M4 in plain JSON.
