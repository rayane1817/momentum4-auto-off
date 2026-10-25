# Design spec v2: app, widgets, tiles (draft for Ryane's pick)

Status: **direction A chosen by Ryane (25 Oct 2026), built in app v0.10.5, completed in v0.11.0** (centre-fill EQ faders, ticks, guard "Sent" / failed status). Not yet checked on the phone: docs/local-test-v0.11.0.md. Name stays "M4 GAIA test".
Research behind it: `docs/design-research.md`. Mock-up (private canvas, Ryane's account): https://claude.ai/artifact/PWtctghujwp6ReAKvvwHNz Replaces the look of `docs/ux-spec.md`; the structure and every behaviour of v0.10 stay.

Hard rules for Phase 3:
- Restyle only. Same controls, same frames, same order, same guard, same blocklists. Diff the frames before and after.
- Pure `android.widget` / `android.view` / `android.graphics`, Java, views built in code, minSdk 26, no libraries, no font files.
- Our own identity: no Sennheiser logo, wordmark, product photos or brand fonts. "MOMENTUM 4" appears only as plain text (the Bluetooth device name or a compatibility note), never styled as a logo.
- Log lines (`M4GAIA`, `M4GUARD`) stay byte-identical: the local test session greps them. Button label changes are listed in section 9 so `docs/local-test-*.md` and `tools/adbui.py` selectors can be updated in the same PR.

---

## 1. Two directions

### A. Copper and graphite (recommended)
From the object. The M4 is a matte shell (black, white, graphite) with a woven headband; the Copper Special Edition adds copper detailing. The app is warm graphite and warm paper, with **one** copper accent that means only "live on the headphones right now". Controls look like gear: faders with a cap, segmented keys that read as pressed buttons, measured values in monospace like a readout. Two primary panels (Noise, Auto power-off) carry the weight; everything else is plain rows on the page.
- Why: every colour has a source, the accent has one job, and it fits a listening object you hold.
- Risk: copper on light paper is weak for small text, so text uses a darker copper (5.3:1) and the bright copper is kept for the pilot dot.

### B. Spec sheet
From the printed manual. No panels at all: warm white paper, black ink, hairline rules between sections, condensed labels, tabular monospace values, one signal orange for "selected". Reads like a datasheet you can touch.
- Why: radically calm, very legible outdoors, cheap to build.
- Risk: signal orange sits close to the danger red, so "selected" and "destructive" compete; flatter controls feel less tactile; harder to give Noise and the guard more weight without panels.

**Recommendation: A**, borrowing B's hairline rules for the secondary sections.

Naming (Ryane decides, not part of the pick): the launcher label is "M4 GAIA test". Options: keep it; "M4 Control"; "Offhand". The spec uses "M4" as the header fallback when no device name is known.

---

## 2. Tokens (direction A)

One Java class `Tok` (Phase 3) holds every colour, resolved once per configuration from `values/colors.xml` and `values-night/colors.xml`, so the app, widgets and drawables share one source. No colour literal anywhere else.

### 2.1 Colour

| Token | Light | Dark | Use |
|---|---|---|---|
| `bg` | `#F3F0EA` | `#141311` | page |
| `surface` | `#FBFAF7` | `#1C1B19` | primary panels, header, dialogs |
| `sunk` | `#E8E4DC` | `#272522` | segmented track, fader track, text fields |
| `ink` | `#1C1B19` | `#EEEAE3` | primary text, selected key fill |
| `ink2` | `#5C5851` | `#A9A39A` | secondary text, captions, secondary titles |
| `rule` | `#D6D0C5` | `#34312C` | hairlines (decorative only) |
| `ruleStrong` | `#8F887D` | `#6E685F` | control outlines, switch track off, ticks |
| `onKey` | `#F3F0EA` | `#141311` | text on a selected key |
| `copper` | `#9A4E1C` | `#E09A63` | accent text, primary button fill, switch on, fader fill |
| `onCopper` | `#FFFFFF` | `#1A1208` | text on copper fill |
| `pilot` | `#B8662B` | `#E09A63` | the pilot dot on `surface` |
| `pilotOnKey` | `#B8662B` | `#9A4E1C` | the pilot dot on a selected key |
| `danger` | `#A3241A` | `#F0857A` | power-off text and outline, error text |
| `onDanger` | `#FFFFFF` | `#1A0907` | text on danger fill (dialog button) |

Contrast (WCAG 2.x, computed with the standard formula; AA = 4.5:1 text, 3:1 large text and UI parts):

| Pair | Light | Dark | Needs |
|---|---|---|---|
| ink on bg | 15.13 | 15.49 | 4.5 |
| ink on surface | 16.49 | 14.35 | 4.5 |
| ink on sunk | 13.57 | 12.75 | 4.5 |
| ink2 on bg | 6.22 | 7.42 | 4.5 |
| ink2 on surface | 6.77 | 6.88 | 4.5 |
| ink2 on sunk | 5.58 | 6.11 | 4.5 |
| copper on bg | 5.30 | 7.93 | 4.5 |
| copper on surface | 5.78 | 7.35 | 4.5 |
| copper on sunk | 4.76 | 6.53 | 4.5 |
| onCopper on copper | 6.03 | 7.91 | 4.5 |
| onKey on ink (selected key) | 15.13 | 15.49 | 4.5 |
| pilot on surface | 4.05 | 7.35 | 3 (UI part) |
| pilotOnKey on ink | 4.08 | 5.03 | 3 |
| danger on surface | 7.12 | 6.84 | 4.5 |
| onDanger on danger | 7.43 | 7.68 | 4.5 |
| ruleStrong on surface | 3.36 | 3.12 | 3 |

`rule` (1.47 / 1.33) is decorative only and never the sole boundary of a control. Disabled controls use alpha 0.38 (exempt from contrast rules) plus a text reason, never colour alone.

### 2.2 Type (system families only)

| Token | Family | Size sp | Weight | Colour | Use |
|---|---|---|---|---|---|
| `display` | sans-serif-medium | 24 | medium | ink | device name in the header |
| `title1` | sans-serif-medium | 20 | medium | ink | primary panel titles: Noise control, Sound, Auto power-off |
| `title2` | sans-serif-medium | 16 | medium | ink2 | secondary section titles: Listening, Device, Advanced, Log |
| `body` | sans-serif | 16 | regular | ink | rows, buttons |
| `key` | sans-serif-medium | 15 | medium | ink / onKey | segmented keys, chips |
| `caption` | sans-serif | 13 | regular | ink2 | explanations, status lines |
| `readout` | monospace | 16 | regular | ink | every measured value: %, dB, min, firmware, slider values |
| `readoutL` | monospace | 28 | regular | ink | guard countdown, battery in the header |
| `log` | monospace | 11 | regular | ink2 | log |

Sentence case everywhere. No uppercase letter-spaced labels (that is the v0.5 card-title tell). Numbers always in `readout`, so "15 min", "+2.0 dB", "62 %" line up like an instrument. On Samsung, `sans-serif` resolves to the system UI font; that is fine.

### 2.3 Spacing, radii, elevation

- Grid 4 dp. Steps: 4, 8, 12, 16, 24, 32.
- Page gutter 16. Panel inner padding 16. Gap after a primary panel 24, between secondary sections 8 + a hairline.
- Row height min 52 (touch target 48 + air). Keys 48 tall, anti-wind keys 40.
- Radii: panel 14; key and button 10; inner key 8; chip 18 (full pill); fader cap 6; widget outer 20 (API 31+: `@android:dimen/system_app_widget_background_radius`), widget inner 12.
- Elevation: none. Separation comes from tone (`surface` on `bg`) and hairlines. Header gets a 1 px `rule` line at its bottom edge when the page scrolls under it.

### 2.4 Signature: the pilot dot

A 8 dp copper dot = "this is live on the headphones now". Used for, and only for:
- the header status (connected),
- the selected noise mode key and the selected anti-wind key,
- the active EQ preset chip,
- the armed guard,
- the current mode in the noise widget and the current preset in the EQ widget.
Not connected = hollow dot (1.5 dp `ruleStrong` ring). Busy = the dot at 40 % alpha, no animation (Doze and battery: nothing animates).

---

## 3. Main screen, top to bottom

`bg` page. A fixed header, then one scroll.

### 3.1 Header (fixed, `surface`)
- Line 1: device name in `display` (Bluetooth name, e.g. "MOMENTUM 4"; "M4" when none is selected). Right: battery in `readoutL` ("62 %"), blank until read.
- Line 2: pilot dot + status in `caption`: see section 6.
- Line 3: device picker as a single row: `body` "Headphones" + current name + "▾" (opens the existing Spinner list), right side text button "Rescan".
- Line 4: one full-width button. Disconnected: copper fill "Connect". Connected: outline "Disconnect". It calls the existing `connect()` / `disconnect()`.

### 3.2 Noise control (primary panel, `surface`, radius 14)
- `title1` "Noise control".
- Segmented keys `Adaptive | Custom | Off` on a `sunk` track, 48 dp. Selected key: `ink` fill, `onKey` text, pilot dot left of the label. Unselected: transparent, `ink` text.
- Custom block (visible only in Custom):
  - Fader "ANC to transparency". End labels under the track in `caption`: "ANC" left, "Transparency" right. Value in `readout` right of the title: "full ANC", "40", "full transparency". Ticks every 25 in `ruleStrong`.
  - "Anti-wind" `caption`, then keys `Off | Auto | Max` (40 dp).
- Disconnected: keys at 0.38 alpha, caption under the title: "Connect to change settings." (see section 7)

### 3.3 Sound (primary panel)
- `title1` "Sound". Right: text button "Flat".
- Preset chips, horizontal scroll: built-ins first, then custom presets. Custom chips show their slot number in `readout` before the name ("1 Commute") instead of ★. Selected chip: `ink` fill + pilot dot. Long-press a custom chip = existing manage dialog.
- EQ curve: a 96 dp custom `View` that draws the 5 gains as a smooth line over a ±6 dB grid, 0 dB line in `ruleStrong`, curve in `copper`, band points as 4 dp dots. Read-only, redrawn on every `setEq`. It summarises; the faders edit.
- 5 faders: label `body` ("63 Hz"), value `readout` ("+2.0 dB"). Fader: 4 dp `sunk` track, `copper` fill from the 0 dB centre to the thumb (shows boost or cut direction), thumb = 14 x 24 dp rounded cap in `ink` with a 2 dp `surface` notch line. Sends on release, as now.
- Text button "Save curve as custom preset".
- Switch rows: Bass boost, Speech clarity.

### 3.4 Auto power-off (primary panel; the reason this app exists)
- `title1` "Auto power-off". Right: pilot dot when armed.
- Status in `readoutL` + `caption`, see section 6 for every string: e.g. "15 min" big, "Armed. Off after 15 min with nothing playing and no call." small.
- Row: `body` "Idle time" + mono number field (`sunk`, 72 dp) + "min".
- Switch row: "Start the guard when the app opens".
- Full-width button: guard off = copper fill "Start guard"; guard on = outline "Stop guard".
- `caption`, always visible: "Idle means the phone plays nothing and has no call. If you wear them in silence past the time, they switch off. Wake them with the button."
- Text button "Guard log" (dumps the guard log into Log, as now).
- Stays enabled when disconnected (the guard connects on its own).

### 3.5 Listening (secondary: no panel)
- `title2` "Listening" with a hairline above.
- Switch rows on `bg`, hairline between rows: Smart pause, Auto-answer calls, On-head detection, Low latency, Comfort call, Touch controls.
- "Hi-Res 24/96" row with `caption` under the label: "Takes effect after you restart the headphones."
- Fader "Transparency during calls", value `readout` "off", "1 / 4" ... "4 / 4", ticks at each step.
- Switch styling: track `ruleStrong` off / `copper` 45 % on; thumb `surface` off / `copper` on.

### 3.6 Device (secondary)
- `title2` "Device", text button "Refresh".
- Readout rows: label `ink2`, value `readout` `ink`: Battery "62 %", Model "M4AEBT", Firmware "3.38.3", Hardware "4". Unknown = "not read" in `ink2`, not a dash.

### 3.7 Power off (secondary, set apart)
- Full-width outline button, `danger` text and 1.5 dp `danger` border: "Power off now".
- `caption`: "Fully off, like the case or a 3-second hold. Only the button turns them back on."
- Confirm dialog (existing): title "Power off the headphones?", message "They switch off fully. Only the button on the headphones turns them back on.", positive "Power off" in `danger`, negative "Cancel".

### 3.8 Advanced (collapsed)
- Row `title2` "Advanced" + "›" (rotates to "⌄" open). Content unchanged: firmware timer Read / seconds / Set with caption "Firmware timer. On 3.38.3 it only reaches standby, not off."; raw hex field (mono) + "Send raw".

### 3.9 Log (collapsed)
- Row `title2` "Log" + chevron. Mono 11 sp on `sunk`, 200 dp, "Copy" text button.

---

## 4. Home-screen widgets (light and dark)

Background: `surface` at 100 % (no translucency, so contrast holds over any wallpaper), radius 20 / system radius on API 31+, 12 dp padding. All colours from `@color/*` resources, with `values-night` variants, so the launcher redraws them itself when the system switches mode. `Widgets.java` sets no colour ints (see 4.3).

### 4.1 Noise widget (3 x 1)
- Top line `caption`: device name, right `caption` "as last set" (the widget cannot read the headset; this says so).
- Three keys: `Adaptive | Custom | Off` on a `sunk` track. Current = `ink` fill, `onKey` text, pilot dot. Unknown state = no key selected, top line reads "Tap a mode. Not read yet."
- Tap on the device name opens the app (existing).

### 4.2 EQ preset widget (3 x 1)
- `‹` key, preset name in `key` centred with the pilot dot before it, `›` key. Line under the name in `caption`: "Preset 3 of 10" in `readout`. Custom curve not matching any preset: name "Custom curve", no dot.

### 4.3 How dark mode works on widgets (Phase 3 note)
- Drawables `w_bg`, `w_track`, `w_key_on`, `w_key_off` in `drawable/` and `drawable-night/`, colours from `colors.xml` / `values-night/colors.xml`.
- Selected state switches by `setInt(id, "setBackgroundResource", ...)` (already used) and text colour by two TextViews per key (on/off), toggled with `setViewVisibility`, because `setTextColor(int)` freezes one mode's colour into the RemoteViews. On API 31+ `setColorStateList` could replace this; not needed.
- Check on the phone: toggle dark mode with the widget on the home screen; it must repaint without opening the app.

## 5. Quick Settings tiles

The system draws and tints tiles; we control the white 24 dp vector icon, the label (18 characters max) and the state. Google recommends at most two tiles per app; we have two.
- **Noise tile**: icon changes with the mode: Adaptive = ear with two arcs, Custom = ear with a fader line, Off = ear outline. Label "Noise: Adaptive" (15 chars), subtitle "M4". State ACTIVE for Adaptive and Custom, INACTIVE for Off, UNAVAILABLE while sending. Unknown: label "Noise: tap to set".
- **EQ tile**: icon = five bars of different heights. Label "EQ: Rock" (custom: "EQ: Commute", cut to 18 chars), subtitle "M4". ACTIVE, UNAVAILABLE while sending.
- Light and dark come from the system; nothing to theme beyond the icon shape. The icons are drawn on the 24 dp grid with 2 dp strokes to match system tiles.

---

## 6. States and microcopy

No em-dashes. Ellipsis only for a running action. Text says what is known and what to do.

| State | Where | Copy |
|---|---|---|
| Connected | header status | ● "Connected" |
| Connecting | header status | ◌ "Connecting..." (dot 40 %) |
| Not connected | header status | ○ "Not connected" |
| Connect failed | header status + log | ○ "Couldn't reach the headphones. They may be off or held by Smart Control (force-stop it)." |
| Link dropped | header status | ○ "Connection lost. Tap Connect." |
| Bluetooth off | header status | ○ "Bluetooth is off. The list fills in when you turn it on." |
| No permission | header status | ○ "Needs the Nearby devices permission. Allow it in Settings > Apps, then tap Rescan." |
| No paired devices | picker row | "No paired headphones. Pair them in Android Settings > Bluetooth, then tap Rescan." |
| Paired, no M4 | picker row | "No MOMENTUM 4 among the paired devices. Pair it, then tap Rescan." |
| Value unknown | readouts | "not read" |
| Guard off | guard status | big "Off", small "The guard is not running." |
| Guard armed | guard status | big "15 min", small "Armed. Off after 15 min with nothing playing and no call." |
| Guard fired | guard status | big "Sent", small "Power-off sent at 02:08. Wake them with the button." |
| Guard could not reach | guard status | small, `danger`: "Couldn't reach the headphones to power them off. Trying again next cycle." |
| Busy (widget/tile sending) | widget top line / tile state | "Sending..." / UNAVAILABLE |
| Busy: Smart Control holds the link | toast | "Smart Control has the headphones. Close it, then try again." |
| Widget never set | noise widget | "Tap a mode. Not read yet." |
| Blocked command | log + toast | "Blocked for safety: this command can reset or unpair the headphones." |

Guard status strings come from `MainActivity.refreshGuardButton` and the service's status text. Only the visible text changes; `AutoOffService` timing and logic do not.

## 7. Disconnected screen

Header shows the hollow dot and "Not connected". Noise, Sound, Listening, Device and Power controls are at 0.38 alpha and disabled, with one `caption` under the Noise title: "Connect to change settings." Auto power-off stays fully enabled. Device readouts keep "not read".

## 8. Dark mode

- Follow the system night setting. Phase 3: theme `@android:style/Theme.DeviceDefault.DayNight` (API 29+) with `Theme.Material.Light` fallback on 26-28 via `values-v29`; colours from `Tok` either way so pre-29 phones still get dark through `values-night` colours.
- Dark uses warm graphite, not pure black, so the copper reads as warm and panels separate by tone.
- Status and navigation bars: `bg` colour, light or dark icons to match.

## 9. Label changes Phase 3 would make (update test docs and selectors)

| v0.10 | v0.11 |
|---|---|
| START GUARD / STOP GUARD | Start guard / Stop guard |
| POWER OFF NOW | Power off now |
| Show guard log | Guard log |
| Save current curve as a custom preset | Save curve as custom preset |
| Hi-Res 24/96 (restart the headphones after changing) | Hi-Res 24/96 (+ caption) |
| Start automatically when the app opens | Start the guard when the app opens |
| Connect + Disconnect (two buttons) | one button, label follows the state |
| ★ Name | 1 Name |
| dash placeholder for an unknown value | not read |

## 10. Phase 3 checklist (for later)

- `Tok` class + `values(-night)/colors.xml`; grep for `0xFF` and `#FF` must find only `colors.xml`.
- Frame diff: run the v0.10 and v0.11 builds through the same UI script, compare `M4GAIA` `->` lines; identical.
- `versionName 0.11`, APK + `SHA256SUMS`, README, `CLAUDE.md` DONE line.
- Screenshots light and dark: emulator in the sandbox if it boots; otherwise a checklist for the local session (`adb shell cmd uimode night yes|no`, `adb exec-out screencap -p`).

## 10. Layout to-do from the hardware runs (25 Oct 2026, for the layout agent)

Found on two phones: Galaxy Z Flip6 (One UI 8.5, Nova launcher) and Nokia XR20 (stock Android 14, launcher3). Evidence: CLAUDE.md T37-T41, PR #25. Screenshots stay private (m4-private), ask Ryane.

### 10.1 Widget default size is too big (both phones)
Ryane had to shrink both widgets by hand on the Samsung; on the Nokia they landed as large cards with mostly empty space.
- Cause 1: `res/xml/widget_noise_info.xml` asks for `targetCellWidth="4" targetCellHeight="2"`; this spec (4.1) says **3 x 1**.
- Cause 2: `widget_eq_info.xml` is 3 x 1, but `minHeight="70dp"` still rounded up to 2 rows on launcher3. `minWidth`/`minHeight` (pre-API 31 launchers) and `targetCell*` (API 31+) must agree.
- Cause 3: both have `resizeMode="horizontal"` only and no `minResizeWidth` / `minResizeHeight`, so the height a launcher picks can't be made smaller by the user.
- Do: noise and EQ widget default 3 x 1 (`targetCellWidth="3" targetCellHeight="1"`, `minWidth`/`minHeight` sized for 3 x 1, i.e. height under ~60 dp so one row fits), `resizeMode="horizontal|vertical"`, set `minResizeWidth`/`minResizeHeight` (e.g. 2 x 1) and `maxResizeHeight` if a 2-row version makes no sense.
- Accept: dropped fresh on both launchers, each widget takes one row and 3 columns; it can be resized down to 2 x 1 and up, nothing clipped.

### 10.2 Widget content doesn't adapt to the widget's size
The layouts use fixed sizes (48 dp key row, 13 sp captions, `gravity="center_vertical"`), so a bigger widget only adds empty space and a smaller one may clip.
- Do: make the key row and the EQ row fill the available height within limits (e.g. keys 40-72 dp, label text scaling with it), or provide size-specific layouts: API 31+ `RemoteViews(Map<SizeF, RemoteViews>)` / `onAppWidgetOptionsChanged` + `OPTION_APPWIDGET_MIN/MAX_HEIGHT` to pick a compact (1 row) or tall (2 rows: bigger keys, EQ shows "Preset n of N") layout.
- Accept: at 3 x 1 nothing is clipped and the keys are at least 40 dp tall; at 4 x 2 the content grows, no large empty band.

### 10.3 Other layout findings (Nokia, stock Android)
- Values in the mono/tabular style (battery "60 %", EQ "-5.0 dB", "off", model, firmware) render in a typewriter-like fixed-width font with wide letter spacing (stock Android falls back to Droid Sans Mono). Use `sans-serif` with `fontFeatureSettings="tnum"` (tabular digits) instead of a monospace family, and drop the extra spacing; check on both phones.
- Quick Settings tile label "EQ: Custom curve" is truncated on stock Android ("EQ: Custon"). Shorten tile labels (e.g. "EQ: Custom", preset names fit already) or put the name in the subtitle.
- The save-preset dialog (and likely the other dialogs) uses Android's default blue accent for radio buttons and buttons, not copper. Theme the dialogs (`AlertDialog` with a theme that sets `colorAccent`/`colorPrimary` to the copper tokens), light and dark.

### 10.4 One UI state rule (small, touches MainActivity)
- Speech clarity is refused by the headset while Hi-Res is on (`0803` -> error `0x83`, T39). Show the Speech clarity switch disabled with a caption "Turn off Hi-Res 24/96 first" while the Hi-Res switch is on. Treat error `0x80` (value already set) as success, not a failure.

