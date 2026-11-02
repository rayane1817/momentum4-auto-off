# Local check: v0.14.2 auto-connect (+ v0.14.1 retest)

v0.14.2 opens the GAIA link by itself while the app is in the foreground. The send(...) and toggle(...) calls in MainActivity are identical to main (sorted diff), so this is a link check, not a protocol run. Run the v0.14.1 retest list (PR #37, steps 1-8) in the same session. No `0605` except where a step says so.

Record per test: fw, app + version, cups, case, result. Logs: `adb logcat -s M4GAIA`.

## Setup
- `adb install -r android/gaia-test/release/m4-gaia-test-0.14.2.apk` (same key, no uninstall). Check versionCode 24.
- Guard stopped, autostart off for steps 1-6 (autostart arms a guard on open).
- Saved device = MOMENTUM 4 (Device page row).

## Log lines to expect
- `Auto-connecting to MOMENTUM 4...` then the usual frames: an automatic try.
- `MOMENTUM 4 connected to the phone.`: the headset joined the phone while the app was open.
- `Auto-connect failed: <reason> (next try in 3 s)`: a retry is planned (at most 3 tries per arrival).

## Checks
1. **Already connected.** M4 connected to the phone (music works), app process killed (`am kill`). Open the app: status goes "Connecting..." then "Connected · fw 3.38.3" within ~3 s, no tap. Same after Back out of the app and reopening from recents.
2. **Switched on while open.** App open on Home, M4 off: status "○ Waiting for MOMENTUM 4". Switch the M4 on: `connected to the phone`, ~2 s later `Auto-connecting`, then Connected. Note how many tries it needed.
3. **Off and on while open.** Connected, then switch the M4 off with the button: status "Waiting for MOMENTUM 4" (not "Connection lost. Tap Connect."). Switch it on: reconnects by itself. Repeat once.
4. **Disconnect respected.** Connected, tap Disconnect: "Not connected", no reconnect for 30 s (watch logcat). Then switch the M4 off and on: it reconnects (the hold ends when the headset rejoins). Again Disconnect, then Home button and reopen the app: it reconnects (the hold ends when you come back).
5. **Other devices never auto-connect.** Pick a non-MOMENTUM paired device on the Device page (TV, earbuds) while it is connected to the phone, with the M4 off: no `Auto-connecting` line, status "Not connected", no RFCOMM attempt in logcat. Pick the MOMENTUM 4 back.
6. **Battery restricted.** Settings > Apps > M4 GAIA test > Battery > Restricted. Kill the app, reopen it with the M4 connected: auto-connects (step 1 again). With the app open, M4 off and on: auto-connects (step 2 again). Set Battery back to what it was.
7. **Phone Bluetooth off and on** (Z Flip6 does not reconnect the M4 by itself): app shows "Bluetooth is off...", then after BT on "Waiting for MOMENTUM 4"; tap MOMENTUM 4 in Android Bluetooth settings, go back to the app: Connected without a tap.
8. **Smart Control at the same time.** SCP open and connected, then open our app: both connected (T28), our controls work.
9. **Guard with the app connected.** Autostart on, 2-min guard, app connected: starting the guard keeps the link (no "Disconnected." line). When it fires (`0605`, the run's only one), the app shows "Waiting for MOMENTUM 4" and the guard page "Sent". Switch the M4 on with the button: reconnects by itself.
10. **Rotation and dark mode.** Rotate on the Equaliser page: link kept. Switch dark mode: the activity restarts and now reconnects by itself (was "Disconnected." in T51).
11. **Widgets with the app in the background.** App connected, Home button, use the noise widget: frames go through the app (M4GAIA), as in T28 E4.

End: PC `check` restore file and snapshot diff, `tools/blocklist_parity_test.py`.
