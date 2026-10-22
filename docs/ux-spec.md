# android/gaia-test UI redesign spec

Target: one clean, single-scroll screen. Pure Java, programmatic views, Theme.Material.Light, no AndroidX / no libs. Only `android.widget` / `android.view` / `android.graphics`. minSdk 26.

Goal: replace the flat wall of grey full-width buttons with a small set of white **cards** on a light-grey background, one accent colour, advanced stuff collapsed, and the two things that matter (noise control, auto power-off guard) made obvious.

---

## 1. Palette (define once as int constants)

```
BG        #F2F3F5   screen background
CARD      #FFFFFF   card surface
TEXT      #1A1C1E   primary text
TEXT2     #5F6368   secondary / captions / section titles
DIVIDER   #E3E5E8   hairlines, unselected borders
ACCENT    #0E7C66   teal: selected segment, switches on, primary buttons
ACCENT_D  #0A5E4D   accent pressed
DANGER    #C0392B   POWER OFF NOW
DANGER_D  #9B2D22   danger pressed
DISABLED  alpha 0.4 on the whole control group when not connected
```

Metrics (all via `dp()`): screen side padding 16; card corner radius 16; card inner padding 16; gap between cards 12; button corner 10; button height 48; row vertical padding 8; segmented corner 10.
Text sizes (sp): screen title 20 bold; card/section title 13 bold, colour TEXT2, letterSpacing 0.08, uppercase; body 15; value 15 bold; caption 12 colour TEXT2; log 11 monospace.

---

## 2. Top-to-bottom widget tree

Root = vertical `LinearLayout` (bg BG). Two children: a fixed **Header** (outside the scroll) and a `ScrollView` (weight 1) holding the **Content** column.

### Header  (LinearLayout, bg CARD, elevation 3dp, padding 16dp, stays on top)
- Row: `TextView` "MOMENTUM 4" (20sp bold, TEXT) — weight 1 — + **status pill** `TextView`: rounded (radius 12dp) pill, text "Disconnected" on DIVIDER bg / "Connected" on ACCENT bg white text; padding 6x12dp.
- `Spinner` of bonded devices (the existing one), 8dp top margin.
- Row (8dp top): `Button` **Connect** (primary, weight 1) + `Button` **Disconnect** (outline, weight 1), 8dp gap.

### ScrollView → Content (vertical LinearLayout, padding: 16dp sides, 12dp top/bottom; 12dp between cards)

**Card "DEVICE"** — title row "DEVICE" + a small text-button "Refresh" (right) that calls readAllConfig().
- Four `labeledValue` rows, values default "—", updated as config replies arrive:
  - Battery · Model · Firmware · Hardware.

**Card "NOISE CONTROL"**
- **Segmented 3-way**: `[ ANC | Transparency | Off ]` (see §3). Mutually exclusive; tap sends the paired commands (ANC = 1804 00 + 1A04 01; Transparency = 1A04 00 + 1804 01; Off = both 00).
- **Transparency level block** (visibility GONE unless Transparency selected): caption row "Transparency level" + live value (right); `SeekBar` max 100.

**Card "LISTENING"** — four `switchRow`s: Smart pause · Auto-answer calls · Low latency · Sidetone. (Switch = 0/1 command on change.)

**Card "AUTO POWER-OFF"** (prominent — title in ACCENT):
- caption (2 lines, TEXT2): "Switches the headphones fully off after they've been idle — no audio playing, no call — for the set time. They stay usable until then; wake them with the button."
- Row: "Idle timeout" label + `EditText` (number, width ~72dp, default **15**) + "min".
- `switchRow` "Start automatically when the app opens".
- Full-width primary `Button` that toggles **START GUARD** ⇄ **STOP GUARD** (label from `AutoOffService.running`).
- status `TextView` (caption) showing the guard's latest line + a small "Log" text-button (dumps `AutoOffService.getLog()` into the Log card).

**Card "POWER"**
- Full-width **danger** `Button` "POWER OFF NOW" (keeps the existing confirm dialog).
- caption: "Fully off, same as the case or a 3-second button hold. Can only be turned back on with the button."

**Collapsible "ADVANCED"** (collapsed by default; header row title + "▸"/"▾"):
- Firmware auto-off timer: caption "Firmware timer (does not truly switch off on this firmware)"; Row: `Button` Read + `EditText` seconds + `Button` Set.
- Raw frame: `EditText` hex (hint `FF 03 00 00 04 95 06 03`) + `Button` Send.
- Row of small text-buttons: Limbo timer · Timers 2-4 · Read all config.

**Collapsible "LOG"** (collapsed by default; header title + chevron):
- `ScrollView` fixed max height ~200dp → monospace `TextView` (11sp, selectable) + a "Copy" text-button.

---

## 3. Interaction

- **Connection state** drives everything. On connect: status pill → "Connected" ACCENT; enable the control group. On disconnect: pill → grey; set the control group to alpha 0.4 + `setEnabled(false)`. The control group = everything in DEVICE/NOISE/LISTENING/POWER/ADVANCED; **keep enabled regardless**: the device Spinner, Connect, and the entire AUTO POWER-OFF card (the guard runs as a service and connects on its own). Maintain a `List<View> gated` and loop over it.
- **Segmented ANC**: three buttons in one rounded container. Selected button = ACCENT rounded fill + white text; others = transparent + TEXT. Keep an `int selectedIndex`; a `setSelected(i)` restyles all three. Tapping sends commands AND calls setSelected. When `readAllConfig` replies arrive (1A05 ANC, 1805 transparency), derive the mode: transparency on → index 1, else ANC on → 0, else 2, and call setSelected **without** sending.
- **Transparency slider** visible only when index==1 (`setVisibility(GONE/VISIBLE)`). Update the value label on `onProgressChanged`, but **send `1A02 [value]` only in `onStopTrackingTouch`** (debounce). Seed its position/value from the 1A03 reply.
- **Switches echo-guard**: when applying device state from replies, set a `boolean applying=true`, call `setChecked(...)`, then `applying=false`; the `OnCheckedChangeListener` returns early if `applying`. Prevents a read from firing a write.
- **Collapsibles**: header row toggles the content LinearLayout `GONE/VISIBLE` and swaps "▸"/"▾". Advanced and Log start collapsed.
- **Guard button** reads `AutoOffService.running` to pick its label each time the screen resumes (onResume) and right after Start/Stop.
- **Persistence** (SharedPreferences): remember the selected device address and the "start automatically" flag. In `onCreate`, if the flag is set and a device is remembered, auto-start the guard (honours polish step 2).
- **Device-info values**: in the RX frame handler, `switch (frame.base())` → update the matching DEVICE value TextView: 0x0603 battery, 0x1206 model, 0x1201 firmware, 0x1200 hardware. (Reuse the decode already in `Gaia`.)

---

## 4. Reusable helpers to write (keep the code DRY)

```
GradientDrawable rounded(int fill, float radiusDp, int strokeColor, float strokeDp)  // null stroke = none
LinearLayout card(String title)                 // styled card, adds title row, returns the inner content column
View labeledValue(String label, TextView[] outValue)  // "Label ........ value", returns row; outValue[0] = the value TextView to update
View switchRow(String label, boolean initial, OnBool onChange)   // label + tinted Switch, with the applying-guard baked in
View segmented(String[] opts, OnInt onSelect, int[] outSetter)   // or return a small Segmented object exposing setSelected(int)
Button primaryBtn(String), outlineBtn(String), dangerBtn(String), textBtn(String)
View sectionHeader(String title)                // uppercase TEXT2 title used as card/section header
View collapsible(String title, View content, boolean startCollapsed)
void tintSwitch(Switch s)                        // setThumb/TrackTintList: ACCENT when checked, DIVIDER when not
void setGroupEnabled(boolean on)                 // alpha + setEnabled over the `gated` list
```

Switch tinting (no AndroidX): on API 26+ use `setThumbTintList`/`setTrackTintList` with a two-state `ColorStateList` (`state_checked` → ACCENT, else → #B0B4B8).

Buttons: build backgrounds with a `StateListDrawable` (pressed = darker variant) of `rounded(...)`; set `setStateListAnimator(null)` to drop the default Material elevation wobble; `setAllCaps(false)` except POWER OFF NOW and the guard button.

---

## 5. Biggest wins for least effort
1. **Three cards + whitespace** (DEVICE / NOISE / guard) over the flat list — the single biggest legibility jump; it's just `card()` + a background colour.
2. **Segmented ANC + slider** replaces 3 buttons and a numeric field and matches how people think about ANC/Transparency.
3. **Collapse ADVANCED and LOG** — hides ~60% of today's controls (raw hex, limbo, timers, the log wall) without losing them.
4. **DEVICE card** turns the "Read all config" log spam into four readable values.
5. **Status pill + graying out when disconnected** kills the "why does nothing happen" confusion.
6. **Guard card wording + default 15 min + auto-start switch** makes the actual feature self-explanatory.
