# Local check: v0.11.0 (design v2 + the v0.10.1 retest findings)

v0.11.0 = v0.10.5 (design v2 direction A ("Copper and graphite", `docs/design-spec-v2.md`) on top of v0.10.1) plus the PR #20 findings: the guard panel names the device it will switch off and warns (red, and a confirm dialog on Start guard) when it is not a MOMENTUM; widget/tile failure toast is short and names the device ("Can't reach MOMENTUM 4. Are they on?"); EQ faders fill from the 0 dB centre, the ANC-to-transparency and call faders have ticks; the guard panel shows "Sent" + the time after a power-off, and a red line when the last attempt failed. Restyle only otherwise: the 28 `send(...)` calls in `MainActivity` are identical to v0.10.1 (checked by diff), `Remote`, `Gaia`, `Presets` are untouched; `AutoOffService` only gained two status fields (`firedAt`, `lastSendFailed`) for the panel, its logic and log lines are unchanged. Same rules as `docs/local-session-plan.md`. About 20 min, no `0605` needed.

## Install
- `adb install -r android/gaia-test/release/m4-gaia-test-0.11.0.apk` over 0.10.1 (same key, no uninstall).

## Checks
1. **Screenshots, light and dark:** `adb shell cmd uimode night no`, then `yes`. For each: open the app (connected and not connected), scroll top to bottom, `adb exec-out screencap -p > %USERPROFILE%\m4-private\v0105-<mode>-<n>.png`. Look for: text readable everywhere, no white blocks in dark, header not under the status bar, copper only on live things (status dot, selected key, active preset, armed guard, primary buttons, faders, switches).
2. **Widgets repaint in dark mode** without opening the app: toggle `cmd uimode night` with both widgets and both tiles on the home screen. Selected noise key = dark fill + dot; EQ widget shows the dot + "Preset n of N".
3. **Frame regression:** run the v0.10 UI script (noise modes, anti-wind, a preset, a slider, each switch, Hi-Res, call slider) and compare the `M4GAIA` `->` lines with the v0.10.1 run. Must be identical.
4. **Status line:** Not connected / Connecting... / Connected / "Connection lost. Tap Connect." (switch the headphones off while connected) / "Couldn't reach the headphones..." (connect while off) / Bluetooth off text.
5. **Guard panel:** big "Off" / "15 min", status text, Start guard (copper) / Stop guard (outline), dot in the title when armed.
6. Dialogs (save preset, power off) readable in dark mode; the power-off button text is red.
7. **Target device (PR #20 finding 1):** select the TV (or any non-MOMENTUM) in the picker: the guard panel turns red ("... is not a MOMENTUM 4"), Start guard asks "Use <name>?"; Cancel does not start it and does not save `dev`. Select the M4: "Switches off: MOMENTUM 4". Widget title shows the saved device name.
8. **Toast (finding 2):** headphones off, tap a widget: "Can't reach MOMENTUM 4. Are they on?" fits the toast, no ANR.
9. **Faders:** EQ fill grows from the centre (left = cut, right = boost); ticks on the ANC/transparency and call faders.
10. **Guard panel after the run's one `0605`:** big "Sent", "Power-off sent at HH:mm. Wake them with the button."

## Label changes (update selectors in `tools/adbui.py` callers and older test docs)
| v0.10.1 | v0.11.0 |
|---|---|
| START GUARD / STOP GUARD | Start guard / Stop guard |
| POWER OFF NOW | Power off now |
| Show guard log | Guard log |
| Save current curve as a custom preset | Save curve as custom preset |
| Hi-Res 24/96 (restart the headphones after changing) | Hi-Res 24/96 (+ caption) |
| Start automatically when the app opens | Start the guard when the app opens |
| Connect + Disconnect (two buttons) | one button, label follows the state |
| ★ Name | 1 Name (slot number) |
| Idle timeout | Idle time |
| — (unknown value) | not read |
| Section titles in capitals (NOISE CONTROL, EQUALIZER, ...) | Sentence case: Noise control, Sound, Auto power-off, Listening, Device |
| Disconnected / Connected pill | status line with a dot: Not connected / Connected |

Section order is now: Noise control, Sound, Auto power-off, Listening, Device, Power off, Advanced, Log.

## Deliver
T-entries in CLAUDE.md, screenshots stay in m4-private (they show the device name), a PR with findings. This check closes v0.11.0.

## Also for v0.11.1
- Install `m4-gaia-test-0.11.1.apk` instead (same checks above), plus:
- Rotate the phone while connected (and fold/unfold to the cover screen): still connected, no reconnect.
- Guard running, `adb shell am kill be.ryane.gaiatest`: within a minute `dumpsys activity services be.ryane.gaiatest` shows AutoOffService again and the guard log has "guard resumed after the app was killed".
- Guard notification: text changes only between "In use: audio or a call" and "Idle. Switches off in N min" (N counts down per minute), not every 15 s.
- The PR #23 checklist: rotate during Connect; Stop then Start the guard within 3 s; raw `FF 03 00 00 04 95 15 00` -> "BLOCKED (device rename)"; Device card fills.
- PC: put the M4 address in `windows-client/m4_addr.txt`; `gaia_client.py verify` connects "found by GAIA UUID"; with no address it stops with the help text.
