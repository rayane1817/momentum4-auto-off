# What other projects know that we don't (yet)

Collected 23 Oct 2026 to extend the Android app with noise-control sub-modes, EQ and paired-device control. Facts only: no code from these projects is copied (Oein's repo shows no license; momentumctl's license is unknown). Everything here must be confirmed on our own headset before it ships.

## Sources
| project | platform | language | tested on | license |
|---|---|---|---|---|
| [Oein/sennheiser-desktop-client](https://github.com/Oein/sennheiser-desktop-client) (`PROTOCOL.md`) | macOS 13+ | Swift | ACCENTUM Wireless fw 3.34.0; EQ on HDB 630 | none visible |
| [momentumctl](https://zff.dev/galen/momentumctl) | Linux | Rust | MOMENTUM 4 (fw not stated) | LICENSE file present, type unknown |
| M4 Companion (r/sennheiser "I built an macOS app and widget for MOMENTUM 4", u/Lightingsky) | macOS | ? | MOMENTUM 4 | unknown |

## 1. Noise control sub-modes (adaptive ANC, anti-wind)
Our status: `1A00` SET never captured, so adaptive and anti-wind are not in the app (see `config-spec.md`).

Oein:
- `1A00 [mode, state]` SET -> `1B00`; `1A01` GET -> `1B01`; notification `1A81`.
- Modes: 1 = anti-wind, 2 = transparency, 3 = adaptive ANC. States 0/1/2.
- Doc says this combined property is used on the MOMENTUM 4; ACCENTUM reports all zero and ignores writes.

M4 Companion UI: noise control "Adaptive / Custom / Off", anti-wind "Off / Max / Auto". Three anti-wind states fits states 0/1/2. Mapping of 1 and 2 to Max/Auto is a guess.

Our capture (M4, fw 3.38.3): `1B01 01 00 02 00 03 00` = all three modes state 0.

Test to confirm (all reversible):
1. `FF 03 00 00 04 95 1A 01` (GET), note the reply.
2. `FF 03 00 02 04 95 1A 00 03 01` (adaptive state 1), then GET again and watch `1A81`.
3. Repeat for anti-wind (`01 01`, `01 02`) and listen.
4. Restore with state 0.
Better still: toggle adaptive and anti-wind in Smart Control Plus with HCI logging on and read the real `1A00` bytes.

## 2. Equalizer (feature 8, `10xx`)
Our status: only `1000`/`1001`/`1002` named from the old app map.

Oein (verified on HDB 630):
| cmd | what | payload |
|---|---|---|
| `1000` | GET config | -> band count, min gain, max gain |
| `1001` | SET band | `[band 0-4, gain i8]`, gain = dB x 10 |
| `1002` | GET band | `[band]` -> `[gain i8]` |
| `1082` | notification | all 5 band gains, i8, dB x 10 |
| `1008` / `1009` | SET / GET bass boost | `[0/1]` |
- Parametric EQ at `100A-1013` exists but has no payload spec.
- Presets: not documented.

Band centres differ per model: Oein lists 50, 250, 800, 3000, 8000 Hz (HDB 630); the M4 Companion UI shows 63, 250, 1k, 4k, 8k Hz for the M4. Read `1000` on the M4 to get the real band count and gain range.

Test: `FF 03 00 00 04 95 10 00` (config), then `FF 03 00 01 04 95 10 02 00` (band 0). Writes are reversible: set a band, read it back, set it to 0.

## 3. Paired devices / multipoint (feature 10, `14xx`)
Oein: IDs `1400-1405` listed, no payloads.
Old Smart Control `m4.json` (our `protocol.md`): `1400-1409`, connect `1402 [idx]`, disconnect `1403 [idx]`.
M4 Companion: lists paired devices with connection state, connects or disconnects the second device while keeping the Mac connected. So list + connect + disconnect work on the M4.
Our capture: notification `148B [6 x 00]` = device connection status.

Never send: `1405` (delete paired device), `1406` (delete pairing list).
Test order: find the list command first (read-only, likely `1400` or `1401`), decode the entries, then try disconnect `1403 [idx]` on a second device, then connect `1402 [idx]`.

## 4. Smaller extras
- Codec: `0800` -> `0900 [id]`, `05` = aptX-HD (Oein). Our app already reads codec.
- Podcast mode: `0803`/`0804`, payload `[00, mode]`, podcast = 2. Unverified anywhere.
- Prompt language: `0807`. Unverified.
- Serial: `001D:0003`. Avoid publishing it in logs.

## Do not take over
- Factory reset (`0040`), firmware update / DFU, delete paired devices: same exclusion list as our app.
