# Local check: v0.12.0 (design v3 "Copper on black")

Restyle only: the send(...) and toggle(...) calls in MainActivity are identical to v0.11.2 (sorted diff). So this is a UI check, not a protocol run. No `0605` except the guard step.

Record per test: fw, app + version, cups, case, result.

## Setup
- `adb install -r android/gaia-test/release/m4-gaia-test-0.12.0.apk` over v0.11.2 (same key, no uninstall). versionCode 19.
- SCP force-stopped.

## Selector changes (spec section 8)
| v0.11.x | v0.12.0 |
|---|---|
| Header title + battery + status line | Home status `label` "MOMENTUM 4 · CONNECTED" (or CONNECTING / NOT CONNECTED); detail line below only for errors; battery = dot numerals, contentDescription "Battery 62 percent" |
| Device picker + Rescan in the header | Device page (tap tile "DEVICE") |
| Sound / Listening / Device sections on one scroll | Tiles "SOUND", "LISTENING", "DEVICE" on Home open pages; back arrow (contentDescription "Back") or system Back returns |
| "Power off now" button | Home tile "POWER OFF" (same confirm dialog) |
| Idle minutes field + autostart switch | Guard summary line "Idle time 15 min · Starts with the app" (tap to expand) |
| Guard log button in the guard panel | Device page |
| Sound: chips row + 5 sliders | Segmented "Presets" / "Bands"; presets as a 2-column grid |
| Widget title device name + "as last set" | "NOISE · AS LAST SET" / "NOISE · TAP A MODE"; EQ "PRESET 02 / 10" |

Other labels unchanged ("Connect", "Disconnect", "Start guard", "Stop guard", "Flat", "Refresh", "Save curve as custom preset", switch labels).

## Checks
1. Light and dark mode: Home, each page, a dialog, both widgets, both tiles. Dark = true black. No text under the status bar or gesture bar.
2. Connect: status label, battery dots, fw on Home; tiles show model + "fw 3.38.3", preset name, "n of 7 on".
3. Not connected: each sub-page shows "Not connected. Values are from the headphones once you connect."; controls greyed; Presets/Bands still switch.
4. Noise Adaptive / Custom / Off + transparency slider + anti-wind: same frames as v0.11.2 (M4GAIA).
5. Sound: preset tap, band drag, Flat, save/rename/delete custom; Home SOUND tile follows.
6. Listening: each switch; LISTENING tile count follows.
7. Device: picker, Rescan, Refresh, Guard log, Advanced, Log.
8. Guard: Start / Stop; dots show minutes / OFF / SENT; summary line follows the autostart switch. One 2-min guard run to `0605`.
9. Back from a sub-page returns to Home; Back on Home leaves the app. Rotation and fold keep the current page and the link; a dark-mode switch recreates the activity and lands on Home.
10. Widgets after `am kill`: noise and EQ frames as in v0.10.1.
