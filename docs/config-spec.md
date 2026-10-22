# M4 config command spec (for android/gaia-test)

For MOMENTUM 4, fw 3.38.3. Verified against the HCI capture (`btsnoop_hci_202610221800.cfa` (raw capture, not published: see `captures/README.md`)), the old-app command map (`m4.json` 4.0.0 + 4.9.3), and `docs/protocol.md`. Every value below marked "capture" was seen on the wire from Ryane's headset.

Frame: `FF 03 00 <len> 04 95 <cmdHi> <cmdLo> <payload>`. len = payload byte count. All vendor 0x0495. Responses come back as `04 95 <cmdLo|0x01xx>`; notifications as `|0x80`. All commands here are reversible and safe; none belong in the blocked list.

## Device state read from Ryane's M4 in the capture
ANC on/off = 1 (on); transparency on/off = 0 (off); transparency level = 0x28 (40); combined mode triples all state 0; smart pause = 1; auto-answer = 1; comfort call = 1; sidetone = 2; on-head detection = 1; firmware = 3.38.3; model = "M4AEBT SE"; battery = 60%.

## ANC / noise control — the M4 has a two-layer scheme
Three independent, verified controls plus one uncertain combined property.

| control | SET | GET | resp/notif | payload | verified in capture |
|---|---|---|---|---|---|
| **ANC master on/off** | `1A04 [0/1]` | `1A05` | `1B05` / `1A85` | 1 byte, 0 off 1 on | yes: `1B05 01`, `1A85 01` |
| **Transparency on/off** | `1804 [0/1]` | `1805` | `1905` / `1885` | 1 byte, 0 off 1 on | yes: `1905 00`, `1885 00` |
| **Transparency level** | `1A02 [0..100]` | `1A03` | `1B03` / `1A83` | 1 byte, **0-100** | yes: `1B03 28` = 40; ACCENTUM saw 0x64=100 → range is 0-100 |
| combined mode | `1A00 [mode,state,...]` | `1A01` | `1B01` / `1A81` | three pairs: (1=anti-wind),(2=transparency),(3=adaptive), each a state byte | GET only: `1B01 01 00 02 00 03 00` (all state 0) |

The combined `1A01` triples and the simple toggles coexist. The simple toggles clearly reflect reality (ANC=1, transp=0, level=40), so **drive the UI from the simple toggles.** ANC and transparency are mutually exclusive audible modes, so set both toggles together:

- **ANC on:**        `FF 03 00 01 04 95 18 04 00`  then  `FF 03 00 01 04 95 1A 04 01`
- **Transparency on:** `FF 03 00 01 04 95 1A 04 00`  then  `FF 03 00 01 04 95 18 04 01`
- **Both off (neutral):** `FF 03 00 01 04 95 1A 04 00`  then  `FF 03 00 01 04 95 18 04 00`
- **Set transparency level N (0-100):** `FF 03 00 01 04 95 1A 02 <N>` (e.g. 40 → `... 1A 02 28`)
- **Read all three:** `FF 03 00 00 04 95 1A 05` · `FF 03 00 00 04 95 18 05` · `FF 03 00 00 04 95 1A 03`

**UNCERTAIN — anti-wind and adaptive sub-modes.** These live in the combined `1A00` property, and no `1A00` SET was ever captured (only GETs). Plausible SET is `1A00 [mode,state]`, e.g. adaptive on = `FF 03 00 02 04 95 1A 00 03 01`, anti-wind on = `... 1A 00 01 01`, state 0 = off. Do not ship these as confirmed. One test resolves it: toggle adaptive/anti-wind in Smart Control Plus with HCI logging on, read the `1A00` SET bytes; or send the guess and watch the `1A81` notif change. Until then, expose only ANC on / Transparency on / Off / level.

## Other config (feature 4, 0x08xx) — all 1-byte unless noted
| control | SET | GET | notes / capture |
|---|---|---|---|
| Smart Pause | `080C [0/1]` | `080D` | capture `090D 01` = on |
| Auto-answer calls | `080A [0/1]` | `080B` | capture `090B 01` = on |
| Comfort call | `0814 [0/1]` | `0815` | capture `0915 01` = on |
| Low latency / gaming | `0817 [0/1]` | `0818` | old map; not in capture; safe |
| Sidetone | `0805 [level]` | `0806` | capture `0906 02`; range uncertain (0-? ; treat as 0-2 or a small int, verify) |
| Audio mode | `0803 [u16]` | `0804` | **2-byte** value; capture `0904 00 00` = 0 normal; 0x0002 = podcast/speech |
| Voice/tone prompts | `0801 [mode]` | `0802` | capture `0902 02`; enum (0 off, 1 tone, 2 voice) per old app, verify |
| Prompt language | — | `0807` | capture `0907 00` = English(0); set form not needed |
| Codec in use (read-only) | — | `0800` | capture `0900 FF` = none while idle |

## On-head / device (feature 2, 0x04xx)
| control | SET | GET | notes |
|---|---|---|---|
| On-head detection on/off | `0400 [0/1]` | `0401` | capture `0501 01` = on |
| BT compatibility mode | `0405 [0/1]` | `0406` | capture `0506 01`; 0 better audio, 1 better compatibility |
| Physical state (read-only) | — | `0402` | resp `0502 [state]`; 1 in case, 2 off head, 3 on head. NB: reports 3 even on a desk (sensor bug) |

## Device info readout (read-only)
| field | GET | parse (capture) |
|---|---|---|
| Battery % | `0603` | `0703 [pct]` → 0x3C = 60% |
| Model string | `1206` | `1306 [ascii\0]` → "M4AEBT SE" |
| Firmware | `1201` | `1301 [3×u16 BE]` → 00 03 / 00 26 / 00 03 = 3.38.3 |
| HW revision | `1200` | `1300 [u16...]` → 0x0004 = 4 |
| Charging | `0602` | `0702 [..]` → 0 not charging |

Battery/firmware/model GET hex: `FF 03 00 00 04 95 06 03` · `FF 03 00 00 04 95 12 06` · `FF 03 00 00 04 95 12 01`.

## Safe-to-expose vs hold
- **No-brainers (verified, reversible):** ANC on / Transparency on / Off, transparency level, smart pause, auto-answer, comfort call, low latency, on-head detection, BT compatibility, and the whole device-info readout.
- **Expose but mark the value meaning as "verify":** sidetone level range, voice-prompt enum, audio-mode values.
- **Hold until a capture confirms the SET:** anti-wind and adaptive sub-modes (`1A00`).
- **Blocked list unchanged:** only factory reset `0040`, DFU/feature 1, pairing-delete `1405/1406`, Qualcomm upgrade (feature 6). None of the above is destructive.

## M4 quirk
Unlike ACCENTUM (independent toggles only, combined mode all-zero), the M4 exposes both the combined `1A00/1B01` triples and the simple `1A04`/`1804` toggles. Driving the simple toggles in pairs (above) is the reliable path; the triples are a read-only status mirror until a SET is captured.

## Recommended UI grouping (android/gaia-test)
1. **Sound mode:** three buttons ANC / Transparency / Off (each sends the two-frame pair), plus a transparency-level field (0-100) with Set.
2. **Toggles:** Smart Pause, Auto-answer, Comfort call, Low latency, On-head detection, BT compatibility — each a read + on/off pair of small buttons.
3. **Device info:** one "Read device info" button that fires battery+model+firmware+hw and prints the decoded lines.
Each control should read-back after a set (send the GET) so the log shows the stored value, same pattern as the timer buttons.
