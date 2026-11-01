# Design spec v4: Nothing X layout + Poweramp faders

Status: **approved by Ryane (1 Nov 2026), built as v0.14.0.** Not yet run on a phone. Deviations from this spec: per-band TalkBack control is a single description of all five bands (no virtual views); the call-transparency fader sits inline in its row instead of opening a page; the device picker row is a Spinner; Advanced keeps the existing collapsible Firmware timer / Raw frame / Log sections under a "Tools" group; the EQ widget curve is a bitmap that repaints when the widget refreshes, not on a dark-mode switch.
Builds on v3 as shipped in v0.12.0 / v0.13.0 (`docs/design-spec-v3.md`: black-first tokens, copper as the only colour, home + sub-pages, `DotNumerals`, `EqCurve`, `FaderTrack`). v4 changes **how things look and feel**, not what they do.
Mock-up: board "v4" on the canvas https://claude.ai/artifact/PWtctghujwp6ReAKvvwHNz (opens focused on it; tap tiles, drag or sweep the faders, open the preset list).

Hard rules (unchanged): same controls and the same GAIA frames, same guard, same blocklists; pure `android.widget`, no libraries, no font files; no Sennheiser or Nothing marks or fonts; log lines byte-identical.

---

## 1. Sources: the real screens

Read on 1 Nov 2026 from the official Google Play listings (store screenshots, viewed directly, so **verified** for what they show; they are marketing captures of current versions):
- **Poweramp Equalizer** (`com.maxmpz.equalizer`): 12 screenshots (graphic EQ, parametric band, preset list, settings).
- **Nothing X** (`com.nothing.smartcenter`): 7 screenshots (home with Headphone (1), Controls, Equaliser, fitness pages).

Notes are in `docs/design-research.md` section 1c. Screenshots are not stored in the repo (third-party images).

## 2. What changes vs v3 (summary)

| Area | v3 (shipped) | v4 |
|---|---|---|
| EQ faders | horizontal faders, fill from 0 dB | **vertical Poweramp-style faders**: thin track, tick dashes, tall cap with grip, glowing fill from the bottom |
| EQ interaction | drag one fader, send on release | drag one, **or sweep across all five** to draw a curve; **double-tap** resets a band |
| EQ curve | `EqCurve` line on a grid | curve strip under the faders, drawn over a **dot matrix** (lit dots under the curve) |
| Presets | 2-column pill grid on the page | Poweramp row under the strip: `Flat` pill, **preset-name pill**, `⋮`; the name pill opens a **preset list with curve thumbnails** and a ✓ |
| Home | dot battery, noise pill keys, guard panel, 4 caps-label tiles | Nothing X home: dot battery + serif name, **noise card with 3 round buttons**, **2-column tiles with a tiny icon** (Equaliser, Listening, Auto power-off with switch, Device), "Power off now" row, settings cog top-right |
| Sub-pages | back arrow, sans title, flat rows | back arrow, **large light serif title**, small grey section labels, **grouped rows** (2 dp gaps, outer corners only) with a **subtle line icon** and chevron or switch |
| Labels | mono caps (`label` token) | sentence case like Nothing X; mono caps dropped from the UI |
| Guard | panel on home | **tile** (switch starts/stops) + **Auto power-off page** (dot minutes, status, settings, START/STOP button) |

## 3. Tokens

Same names as `colors.xml` / `Tok.java`. Only values change (Nothing X's card and button greys), plus four fader values.

| Token | Dark | Light | v4 use |
|---|---|---|---|
| `bg` | `#000000` | `#F2F2F2` | page |
| `surface` | `#1C1C1C` (was `#121212`) | `#FFFFFF` | cards, tiles, rows, sheets |
| `sunk` | `#2C2C2C` (was `#1E1E1E`) | `#E6E6E6` | round buttons (unselected), `Flat` pill, switch off track |
| `ink` | `#F2F2F2` | `#0A0A0A` | text; **selected round button fill** |
| `ink2` | `#9E9E9E` | `#5E5E5E` | subtitles, icons, section labels, tick dashes |
| `rule` | `#2A2A2A` | `#E0E0E0` | hairlines, unlit matrix dots |
| `rule_strong` | `#3A3A3A` | `#CFCFCF` | fader track (1 dp) |
| `on_key` | `#000000` | `#FFFFFF` | icon on the selected round button |
| `copper` | `#D98C55` | `#9A4E1C` | fader fill + glow, curve, switch on, armed state, live dot column |
| `danger` | `#FF7A6E` | `#B3261E` | Power off row, errors |
| new `fader_cap` | `#262626` | `#FFFFFF` | fader cap fill |
| new `fader_edge` | `#555555` | `#9A9A9A` | fader cap 1.5 dp outline |
| new `glow` | copper at 75 % | copper at 25 % | fill shadow (Paint.setShadowLayer, 10 dp blur) |
| new `matrix_lit` | `#6E6E6E` | `#A8A8A8` | matrix dots under the curve |

Contrast (WCAG 2.x): ink on bg / surface / sunk = 18.76 / 15.22 / 12.47 (dark), 17.68 / 19.80 / 15.86 (light). ink2 = 7.84 / 6.36 / 5.21 and 5.79 / 6.48 / 5.20. copper = 7.83 / 6.36 / 5.21 and 5.39 / 6.03 / 4.83. danger = 8.26 / 6.70 / 5.49 and 5.84 / 6.54 / 5.24. All AA. `pilot`, `pilot_on_key`, `on_copper`, `on_danger` keep their v3 values.

## 4. Type

| Role | Face | Size | Notes |
|---|---|---|---|
| Page title | system `serif` (Noto Serif on Android), light if the device has it, else regular | 32 sp | Nothing X uses its own serif; we use the phone's built-in one |
| Device name on home | system `serif` | 26 sp | centred under the dot battery |
| Card / tile title | sans 15 sp | | sentence case: "Noise control", "Equaliser" |
| Subtitle | sans 13 sp, `ink2` | | "Rock", "6 of 7 on", "Armed · 15 min" |
| Fader Hz label | sans **bold** 15 sp | | "63", "250", "1K", "4K", "8K" |
| Fader value | mono 13 sp, `ink2` | | "+2.5" |
| Section label | sans 13 sp, `ink2` | | "Playback", "Calls", "Info" |
| Big numbers | `DotNumerals` (v3) | | battery, idle minutes |

## 5. Home (Nothing X layout)

Top to bottom on `bg`, 12 dp side padding:
1. Settings cog, top-right (opens Device).
2. Hero, centred: dot battery "62%" (last column copper when live), serif "MOMENTUM 4", status line (copper dot + "Connected · fw 3.38.3"), small `sunk` pill "Disconnect" / copper "Connect".
3. **Noise control card** (`surface`, radius 18, 16 dp padding): title, then 3 round buttons (60 dp circles) with a label under each: Adaptive, Custom, Off. Selected: `ink` circle, `on_key` icon, `ink` label medium. Others: `sunk` circle, `ink2` icon and label. Custom selected: a hairline, then the ANC to transparency slider ("ANC", level in mono, "Transparency") and anti-wind as three small pills (selected = `ink` fill).
4. **Tiles**, 2 columns, **4 dp gaps**, 150 dp tall, radius 18. Title top-left, subtitle under it, **tiny icon bottom-right** (18 dp, `ink2`):
   - Equaliser · preset name ("Custom curve" if none matches) · EQ icon
   - Listening · "6 of 7 on" · ear icon
   - Auto power-off · "Armed · 15 min" in copper, or "Off" · moon icon (copper when armed) · **switch bottom-left** = Start/Stop guard (same action as today's button)
   - Device · "M4AEBT · hw 4" · headphones icon
5. **"Power off now"** row (`surface`, 60 dp, radius 18): power icon + text in `danger`, chevron. Opens the existing confirm dialog.
Disconnected: noise card, Equaliser, Listening and Power off at 0.38 alpha; Auto power-off and Device stay usable.

## 6. Equaliser page (Poweramp faders)

Back arrow, serif title "Equaliser". Then:

### 6.1 Faders (new `EqFaders` view, replaces the 5 horizontal faders)
- One custom `View`, 340 dp tall, full width, 5 equal columns. Per column:
  - **Track**: 1 dp vertical line, `rule_strong`, 256 dp tall.
  - **Tick dashes**: two 6 dp dashes either side of the track at the top (+6 dB), middle (0 dB) and bottom (-6 dB), `ink2`.
  - **Fill**: 3 dp copper line from the cap's bottom edge down to the track's bottom, with the `glow` shadow. This is Poweramp's fill direction (from the bottom), not v3's from-centre fill.
  - **Cap**: 36 x 60 dp rounded rect (radius 15), `fader_cap` fill, 1.5 dp `fader_edge` outline, a 14 x 3 dp grip line in `ink` at its centre. Its centre sits at the gain (+6 dB top, -6 dB bottom).
  - **Labels** under the track: Hz in bold sans, value in mono `ink2`.
- **Touch** (all handled by `EqFaders.onTouchEvent`):
  - Down or move: band = column under the finger, gain = finger height, 0.1 dB steps, clamped to ±6.0 dB. Moving sideways changes the band, so **one sweep draws a curve**.
  - Up: send `1001 [band, gain]` for every band whose value changed during the gesture, in band order. These are the same frames a single-fader drag sends today, one per band; a sweep sends up to five.
  - Double-tap a column: that band to 0 dB, send `1001 [band, 0]`.
  - Values update live while dragging (labels, curve, matrix); frames go out only on release (as today).
- Accessibility: each column exposes a virtual view ("63 Hz, plus 2.5 dB") with scroll forward / backward = ±0.5 dB.

### 6.2 Curve strip
76 dp `surface` panel, radius 16, under the faders. A matrix of 34 x 7 dots (3.6 dp): dots under the curve in `matrix_lit`, others in `rule`; the smooth curve through the five gains in copper, 2 dp. Read-only. Replaces `EqCurve`'s grid look; `EqCurve` keeps its maths.

### 6.3 Preset row (Poweramp layout)
`Flat` pill (`sunk`) · **preset-name pill** (outline 1.5 dp `ink2`, fills the row, shows "Rock" or "Custom curve") · `⋮` round outline button (menu: "Save current curve as a preset", "Flat").
Under it, one caption: "Drag a fader, or sweep across all five. Double-tap resets a band to 0."

### 6.4 Preset list (bottom sheet)
Opened by the name pill. Rows 52 dp: **curve thumbnail** (64 x 24 dp, copper line over a hairline) · slot number in mono for custom presets · name · copper ✓ on the active one (row on `sunk`). "Save current curve as a preset" at the bottom. Long-press a custom row = today's rename / overwrite / delete dialog.

### 6.5 Bass boost, Speech clarity
Two Nothing X tiles side by side (120 dp): title, "On" / "Off", switch bottom-left, icon bottom-right. Same commands as today.

## 7. Sub-pages (Nothing X style)

Shared header: back arrow (24 dp, 1.6 dp stroke) top-left, serif title 32 sp below it, left aligned.
Body: section label, then **grouped rows**: each row is its own `surface` rect, 2 dp gap, 64 dp min height, outer corners of the group 18 dp, inner corners 6 dp. Row = icon (18 dp, `ink2`) · title (15 sp) + optional subtitle (13 sp `ink2`) · value (mono `ink2`) or switch or chevron.

- **Listening**: Playback (Smart pause "Pause when you take them off", Low latency, Hi-Res 24/96 "Takes effect after a restart"), Calls (Auto-answer calls, Comfort call, Transparency during calls `2 / 4` › opens the 0-4 fader), Headphones (On-head detection, Touch controls).
- **Device**: Headphones (device picker row "MOMENTUM 4 · Tap to choose another paired device" ›, Rescan), Info (Battery, Model, Firmware, Hardware as values, "not read" when unknown), Advanced (Firmware timer ›, Raw frame ›, Log ›, Guard log ›, Copy report ›).
- **Auto power-off**: dot minutes + "minutes idle, then fully off"; Status (Armed / Not running with the v2 microcopy, "Switches off: MOMENTUM 4"); Settings (Idle time `15 min` ›, Start the guard when the app opens switch); full-width pill START GUARD (copper) / STOP GUARD (`sunk`).

## 8. Icons (our own, 24 dp grid, 1.5 dp stroke, round caps)

Drawn as `VectorDrawable` path data, tinted `ink2` (copper when live). Paths as used in the mock-up board `V4App`: EQ (three sliders), ear, headphones, power, moon (guard), bass (waveform step), speech (bubble), pause, bolt (latency, battery), Hi-Res (box with "H"), call, hand (touch), head (on-head), info, clock (timer), code (raw, firmware), list (logs), refresh, play (autostart), noise modes: Adaptive (sparkle), Custom (two sliders), Off (circle with slash). None copy Nothing's or Poweramp's icons; they are generic line glyphs.

## 9. Widgets and tiles

Widgets follow the home: `surface` card, radius 18. Noise widget: the 3 round buttons with labels (selected `ink` circle). EQ widget: round ‹ › buttons, preset name, small curve thumbnail under it. Quick Settings tiles unchanged (system-drawn).

## 10. For the developer session

- Values in `values(-night)/colors.xml` (section 3) + 4 new tokens in `Tok`.
- New `EqFaders.java` (custom View: draw + touch + accessibility); `FaderTrack` stays for the transparency and call sliders. Sweep sends one `1001` per changed band on release: the sorted `send(...)` diff vs main will show this as **intended** (same command, now also reachable from a sweep).
- Home, tiles, rows, sheet: layout only in `MainActivity`; guard Start/Stop moves to the tile switch and the page button (same `toggleGuard`).
- Serif: `Typeface.create("serif", Typeface.NORMAL)` (no light weight guaranteed).
- Label and navigation changes for `docs/local-test-*.md` and selectors: tiles "SOUND/LISTENING/DEVICE/POWER OFF" become "Equaliser/Listening/Device" + row "Power off now"; new tile "Auto power-off" (switch); guard button text on its page; preset grid becomes the name pill + sheet; EQ bands are one view with virtual children instead of five SeekBars.
- Proof as always: frames, blocklist parity, JVM tests (add `EqFaders` gesture-to-frames tests), light and dark screenshots.
