# Design spec v3: "Copper on black" (Nothing-inspired simplicity)

Status: **proposal for Ryane's review, 25 Oct 2026. Not built.** Design only: no app code changes in this step.
Builds on what shipped in v0.11.1 (direction A from `docs/design-spec-v2.md`: `Tok.java`, `res/values(-night)/colors.xml`, `EqCurve`, `FaderTrack`, pilot dot). v3 changes the **palette**, the **structure** (one home screen + sub-pages instead of one long scroll) and the **type voice**. Everything else in v2 stays: hard rules, microcopy (v2 section 6), widget dark-mode mechanics (v2 4.3), tile rules (v2 5).
Mock-up: same canvas, boards "v3 ..." (https://claude.ai/artifact/PWtctghujwp6ReAKvvwHNz).

Hard rules (unchanged from v2): restyle and re-arrange only; same controls, same frames in the same order, same guard, same blocklists; pure `android.widget`, no libraries, no font files; no Sennheiser marks; **no Nothing marks either** (no Ndot, no NType, no Nothing glyphs or dot-matrix font files: our dot numerals are our own 5 x 7 grid, drawn in code); log lines byte-identical.

---

## 1. What we take from Nothing X, and what we don't

From `docs/design-research.md` section 1 and the Nothing X notes added there (section 1b).

| Nothing X / Nothing OS trait | Source status | v3 |
|---|---|---|
| Home shows only the daily things (battery, noise mode) and a few **tiles** that open sub-screens (Equaliser, Controls, ...) | claimed (SoundGuys: "On the main screen, you should see some tiles. Tap the Equalizer tile") | **Take.** Home = battery, noise control, guard, 4 tiles |
| "fairly straightforward and de-cluttered", "large buttons and well-labelled functions" | verified (GSMArena), claimed (Tom's Guide) | **Take.** 56 dp keys, one action per row |
| Two-level EQ: Simple (presets) first, Advanced behind it | verified (GSMArena, SoundGuys) | **Take.** Sound page: "Presets" / "Bands" switch |
| Monochrome UI, colour used as a single interrupt | claimed (brand analyses, they disagree on red) | **Take, with copper** instead of red: copper = live on the headphones, nothing else |
| Dot-matrix display face, only for numbers and names | claimed | **Take the idea, not the font**: our own 5 x 7 dot numerals for battery, idle minutes, transparency level |
| Small uppercase monospace labels | claimed (marketing site system) | **Take** for section labels only (`label` token) |
| Product render as the hero | opinion | **Skip**: no product photos allowed. The dot-matrix battery is the hero |
| Radar EQ panel, ear-tip test, Q factor | verified (SoundGuys) | **Skip**: the M4 has none of these |

## 2. Principles

1. **Black first.** Dark mode is the reference look (true black, OLED). Light mode is its negative: white on near-white, same structure.
2. **One colour.** Copper only marks what is live right now: connected, selected mode, active preset, armed guard. Danger red only on Power off.
3. **Home is for daily use.** Everything else is one tap away in a sub-page, never more than one level deep.
4. **Numbers are the display.** Big values in dot numerals; small values in monospace; labels in small mono caps; prose in the system sans.
5. **Big, round, flat.** Full pill keys, 24 dp tiles, no shadows, no gradients, no glass.

---

## 3. Tokens

Same names as today's `colors.xml` / `Tok.java`, so implementation is a value swap plus one new type role. Contrast computed with the WCAG 2.x formula.

### 3.1 Colour

| Token (`colors.xml`) | Dark (reference) | Light | Use |
|---|---|---|---|
| `bg` | `#000000` | `#F4F4F2` | page |
| `surface` | `#121212` | `#FFFFFF` | tiles, panels, header strip |
| `sunk` | `#1E1E1E` | `#EBEBE8` | key track, fader track, fields |
| `ink` | `#F5F5F5` | `#0A0A0A` | text, selected key fill, dot numerals |
| `ink2` | `#9A9A9A` | `#5E5E5E` | captions, labels, unlit dots (at 25 % alpha) |
| `rule` | `#262626` | `#E2E2DE` | hairlines (decorative) |
| `rule_strong` | `#6B6B6B` | `#8A8A85` | outlines, switch track off, ticks |
| `on_key` | `#000000` | `#FFFFFF` | text on a selected key |
| `copper` | `#D98C55` | `#9A4E1C` | live accent: text, fill, switch on, fader fill, EQ curve |
| `on_copper` | `#000000` | `#FFFFFF` | text on copper |
| `pilot` | `#D98C55` | `#9A4E1C` | pilot dot on `surface` / `bg` |
| `pilot_on_key` | `#9A4E1C` | `#D98C55` | pilot dot on a selected (inverted) key |
| `danger` | `#FF7A6E` | `#B3261E` | Power off, errors |
| `on_danger` | `#000000` | `#FFFFFF` | text on danger (dialog button) |

| Pair | Dark | Light | Needs |
|---|---|---|---|
| ink on bg / surface / sunk | 19.26 / 17.18 / 15.29 | 17.98 / 19.80 / 16.57 | 4.5 |
| ink2 on bg / surface / sunk | 7.46 / 6.66 / 5.92 | 5.89 / 6.48 / 5.43 | 4.5 |
| copper on bg / surface / sunk | 7.83 / 6.99 / 6.22 | 5.48 / 6.03 / 5.05 | 4.5 |
| on_copper on copper | 7.83 | 6.03 | 4.5 |
| on_key on ink (selected key) | 19.26 | 19.80 | 4.5 |
| pilot_on_key on ink | 5.53 | 7.38 | 3 |
| danger on bg / surface | 8.26 / 7.37 | 5.94 / 6.54 | 4.5 |
| rule_strong on surface / bg | 3.52 / 3.94 | 3.47 / 3.15 | 3 |

Why copper on black: the M4 is a matte black shell; the Copper Special Edition puts copper on black. On true black the copper reads warm and bright (7.8:1), which a monochrome UI needs from its only colour.

### 3.2 Type (system families, no font files)

| Token | Family | Size | Use |
|---|---|---|---|
| `dot` | **DotNumerals** view (section 3.4) | 7 dp dots, 2 dp gap (52 dp tall) | battery on home, idle minutes, guard "SENT" time |
| `dotS` | DotNumerals | 4 dp dots, 1.5 dp gap | transparency level, widget preset number |
| `label` | monospace, ALL CAPS, 11 sp, letter-spacing 0.12 | `ink2` | section labels above controls: NOISE CONTROL, AUTO POWER-OFF, tile titles |
| `title` | sans-serif-medium 22 sp | `ink` | sub-page title next to the back arrow |
| `key` | sans-serif-medium 16 sp | `ink` / `on_key` | keys, tiles' main line, buttons |
| `body` | sans-serif 16 sp | `ink` | rows |
| `caption` | sans-serif 13 sp | `ink2` | explanations, status lines |
| `readout` | monospace 16 sp | `ink` | small values (+2.0 dB, 3.38.3) |
| `log` | monospace 11 sp | `ink2` | log |

Mono caps labels were on the v2 "avoid" list as a generic tell. Here they are deliberate: one job (naming a control group), one face (monospace), small and quiet. They never replace a sentence.

### 3.3 Shape, spacing, elevation

- Radii: keys and buttons **full pill** (height / 2); tiles and panels **24**; fields 12; fader cap 6; widget outer 28 (API 31+: system radius), widget keys pill.
- Grid 4 dp; page gutter 16; tile gap 12; section gap 32 on home (Nothing-style air), 24 in sub-pages.
- Key height 56 (noise), 44 (anti-wind, Presets/Bands switch). Tiles 104 tall, 2 columns.
- Elevation none. Separation by tone only (`surface` on `bg`). No hairline under the header on home.

### 3.4 Signature: dot numerals + pilot dot

- **DotNumerals**: a small custom `View` that draws digits 0-9, `%`, `:`, `-` and A-Z caps needed for "SENT", "OFF" on our own 5 x 7 grid. Lit dots `ink`, unlit dots `ink2` at 25 % alpha (the grid stays faintly visible, like an LED panel). `contentDescription` carries the plain value ("Battery 62 percent").
- When the value is live and on the headphones (battery read, guard armed), the **last lit column** of the last glyph is drawn in `copper`. That is the only copper inside the numerals. Example: "62%" with the right edge of the % in copper.
- **Pilot dot** (unchanged from v2): 8 dp copper dot = live now; hollow ring = not connected.

---

## 4. Structure

One activity, five "pages" swapped in place (a `FrameLayout` holding Home, Sound, Listening, Device; Power off is a dialog). All views are still built once in `onCreate`, so `onFrame()` keeps updating every control while its page is hidden. System Back on a sub-page returns to Home; on Home it leaves the app as now.

### 4.1 Home (daily)

Top to bottom, `bg`:
1. **Status row**: pilot dot + `label` "MOMENTUM 4 · CONNECTED" (device name from Bluetooth, state word from v2 microcopy). Right: a small pill button "Disconnect" (outline) / "Connect" (copper fill). Status details (connect failed, Bluetooth off, ...) appear as a `caption` line under it, from v2 section 6.
2. **Hero**: battery in `dot` ("62%"), `label` "BATTERY" under it. Right column, `readout` `ink2`: firmware "3.38.3". Not read: the dot grid unlit with "--" and `caption` "not read".
3. **NOISE CONTROL** (`label`), then three pill keys `Adaptive | Custom | Off` on a `sunk` pill track, 56 dp. Selected key = `ink` fill, `on_key` text, pilot dot. Custom open: under the keys, the ANC to transparency fader with the level in `dotS` on the right, "ANC" / "Transparency" end labels, and anti-wind keys `Off | Auto | Max` (44 dp).
4. **AUTO POWER-OFF** (`label`), then one `surface` panel (radius 24):
   - left: idle minutes in `dot` ("15"), `label` "MIN IDLE"; right: one pill button "Start guard" (copper fill) / "Stop guard" (outline).
   - `caption` status line (v2 microcopy: "Armed. Off after 15 min with nothing playing and no call.", "Sent at 02:08 ...", red failure line).
   - `caption` "Switches off: MOMENTUM 4" (v0.11.0 target line, red for a non-MOMENTUM).
   - One collapsed row "Idle time 15 min · Starts with the app" (`caption`, chevron). Tap expands the existing minutes field and the "Start the guard when the app opens" switch in place. They stay on Home because the guard is the app's main job.
5. **Tiles**, 2 x 2, `surface`, radius 24, 104 dp:
   - **SOUND**: main line = active preset name with pilot dot ("Rock"), or "Custom curve".
   - **LISTENING**: main line = "6 of 7 on" (count of switches on).
   - **DEVICE**: main line = model ("M4AEBT"), `caption` "fw 3.38.3".
   - **POWER OFF**: main line "Power off" in `danger`; tap = the existing confirm dialog. Disabled at 0.38 when not connected.
   Disconnected: tiles stay tappable (pages show "not read" and disabled controls), as now.

### 4.2 Sound page
- Back arrow "‹" + `title` "Sound". Right: text button "Flat".
- Switch `Presets | Bands` (44 dp pill keys). Opens on Presets.
- **Presets**: `EqCurve` (copper curve on a faint grid), then presets as a 2-column grid of pill buttons (built-ins, then custom with the slot number in `readout`). Active = `ink` fill + pilot dot. Long-press custom = existing manage dialog. Text button "Save curve as custom preset".
- **Bands**: `EqCurve`, then the 5 faders (label + `readout` value, copper fill from 0 dB), unchanged behaviour (send on release).
- Bottom, always visible: Bass boost, Speech clarity switch rows.

### 4.3 Listening page
- Back + `title` "Listening". Switch rows with hairlines: Smart pause, Auto-answer calls, On-head detection, Low latency, Comfort call, Touch controls, Hi-Res 24/96 (+ caption). Then "Transparency during calls" fader with ticks and value "2 / 4".

### 4.4 Device page
- Back + `title` "Device". Device picker row + "Rescan" (moved here from the header: you set it once).
- Readouts: Battery, Model, Firmware, Hardware (`readout`, "not read"). Text button "Refresh".
- **Advanced** (collapsed): firmware timer, raw hex.
- **Log** (collapsed) + "Copy". "Guard log" text button sits here too.

### 4.5 Power off
- Tile on Home (4.1). Confirm dialog text unchanged from v2 3.7. Positive button "Power off" in `danger`.

---

## 5. Widgets (light and dark)

Nothing OS widgets are flat, monochrome and use big type. Ours, with the v3 tokens:
- **Noise widget (3 x 1)**: `surface` (dark: `#121212`), radius 28. `label` row "NOISE · AS LAST SET". Three pill keys, current = `ink` fill + pilot dot. Unknown: no key selected, label "NOISE · TAP A MODE".
- **EQ widget (3 x 1)**: round `sunk` buttons ‹ and › (48 dp circles); middle: preset name in `key` with pilot dot, under it `label` "PRESET 02 / 10" (number in mono, not dots: too small).
- Same resource mechanics as v2 4.3 (colours only from `@color`, state by swapping views), so they repaint when the system switches mode.

## 6. Quick Settings tiles

No change from v2 section 5 (system-drawn; we keep the per-mode noise icon and the EQ bars icon). Subtitle "M4".

## 7. States and microcopy

v2 section 6 applies word for word. Additions:
| State | Where | Copy |
|---|---|---|
| Tile, Listening, nothing read | Home tile | "not read" |
| Tile, Sound, curve matches no preset | Home tile | "Custom curve" |
| Sub-page while disconnected | top of page, `caption` | "Not connected. Values are from the headphones once you connect." |

## 8. Label and navigation changes vs v0.11.1 (for test selectors)

| v0.11.1 | v3 |
|---|---|
| Header title "MOMENTUM 4" + battery + status line | `label` "MOMENTUM 4 · CONNECTED" + dot battery (contentDescription "Battery 62 percent") |
| Device picker + Rescan in the header | Device page |
| Sound, Listening, Device sections on the main scroll | Home tiles "SOUND", "LISTENING", "DEVICE" open pages; tests tap the tile first, Back after |
| "Power off now" button | Home tile "POWER OFF" (same dialog) |
| Idle minutes field + autostart switch in the guard panel | Under "Settings" row in the guard panel (tap to expand) |
| Guard log text button in the guard panel | Device page |

All other labels ("Start guard", "Stop guard", "Connect", "Disconnect", "Flat", "Refresh", "Save curve as custom preset", switch labels) stay as in v0.11.1.

## 9. For the developer session (not done here)

- Swap the 14 colour values in `values/colors.xml` and `values-night/colors.xml` (section 3.1). No new token names.
- Add `DotNumerals.java` (custom `View`, own 5 x 7 glyph table, `contentDescription`).
- Page container + Back handling in `MainActivity`; move existing views, do not rebuild them; `onFrame()` untouched.
- Widgets: layout and drawable changes only (pill keys, round EQ buttons, labels).
- Same proof as before: identical `send(...)` frames, blocklist parity, JVM tests green, light and dark screenshots, `docs/local-test-*.md` selectors updated from section 8.
