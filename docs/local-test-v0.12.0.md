# Local check: v0.12.0 + v0.12.1 + v0.13.0 (one run) (design v3 "Copper on black")

v0.12.0 is a restyle: the send(...) and toggle(...) calls in MainActivity are identical to v0.11.2 (sorted diff), so steps 1-10 are a UI check, not a protocol run. v0.12.1 and v0.13.0 change the guard (connect deadline, back-off) and add Copy report: steps 11-14. No `0605` except the guard steps (8 and 11).

Record per test: fw, app + version, cups, case, result.

## Setup
- `adb install -r android/gaia-test/release/m4-gaia-test-0.13.0.apk` over whatever 0.9.1+ build is installed (same key, no uninstall). Check versionCode 21 with `adb shell dumpsys package be.ryane.gaiatest | findstr versionCode`.
- The release APK on GitHub must have the same SHA-256 as the repo copy (`release/SHA256SUMS`).
- SCP force-stopped.

## Selector changes (spec section 8)
| v0.11.x | v0.12.0 |
|---|---|
| Header title + battery + status line | Home status `label` "MOMENTUM 4 · CONNECTED" (or CONNECTING / NOT CONNECTED); detail line below only for errors; battery = dot numerals, contentDescription "Battery 62 percent" |
| Device picker + Rescan in the header | Device page (tap tile "DEVICE") |
| Sound / Listening / Device sections on one scroll | Tiles "SOUND", "LISTENING", "DEVICE" on Home open pages; back arrow (contentDescription "Back") or system Back returns |
| "Power off now" button | Home tile "POWER OFF" (same confirm dialog) |
| Idle minutes field + autostart switch | Guard summary line "Idle time 15 min · Starts with the app" (tap to expand) |
| Guard log button in the guard panel | Device page, plus new outline button "Copy report" below it (v0.13.0) |
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

## Also for v0.12.1 and v0.13.0 (install v0.13.0 directly; it contains both)
11. Guard with the headphones already off: guard 2 min, switch the M4 off with the button, phone idle. Expect: first try fails (each connect gives up after ~8 s, "connect timeout after 8000 ms"), then "next try in 1 min", then 5 min, then 30 min (M4GUARD). Notification shows the next try. Play audio for a moment: failures reset. Switch the M4 on, stay idle: next timeout sends 0605.
12. Device > Copy report: paste in a note. Check: app 0.13.0, phone model, Android version, model/firmware/battery, guard line, both log tails. Any Bluetooth address shows as XX:XX:XX:XX:XX:XX; no TV or other device name.
13. Log card Copy: addresses replaced with XX.
14. GitHub: "New issue" shows the "Test report" form.
