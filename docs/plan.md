# Plan: how to attack the puzzle

Four tracks run in parallel. Each one feeds the test log in CLAUDE.md.

## Track A: observe (physical tests, no code)
Goal: pin down exactly when the headphones fail to switch off.
1. Record baseline: firmware version (app > device info), app name + version (Android Settings > Apps).
2. Repeat T1 with cups facing UP on a table (manual: cups down can block removal detection).
3. Repeat in the case, cups facing the back of the case.
4. Clean the right-cup sensor, repeat step 2.
5. On-head detection OFF in app, repeat step 2 (expect: no auto-off at all, per manual).
6. After changing the timer in the app, force-stop the app, reopen: is the value still there?
Result format per test: date, fw, app version, position, result (connected instantly / needed button).

## Track B: capture (Bluetooth traffic)
Goal: find the commands the app sends for the auto-off timer and for power-off.
1. Android: Settings > About phone > tap Build number 7x to enable Developer options.
2. Developer options > "Enable Bluetooth HCI snoop log" (set to Enabled/Full). Toggle Bluetooth off and on.
3. In the app, do ONE action at a time with pauses, and note the clock time of each:
   - change auto-off 15 > 30 > 15 min
   - toggle on-head detection off/on
   - factory default actions are NOT needed, skip them
4. Get the log: connect phone to a PC, `adb bugreport bugreport.zip`, then find `btsnoop_hci.log` inside (path differs per Android version/vendor).
5. Open in Wireshark, filter on the headphones' MAC address, look for vendor packets (RFCOMM or BLE GATT writes) at the noted times. A changed byte that matches 15 vs 30 is the timer field.
6. Disable the snoop log afterwards (it keeps growing).

## Track C: static analysis (the app)
Goal: find command definitions without guessing from traffic alone.
1. Get the APK: Smart Control Plus package `com.sonova.chb.control` (APKPure/APKMirror), old Smart Control on APKMirror (4.5.0 to 4.9.3 listed, Nov 2023 to Jun 2025).
2. Decompile with jadx (jadx-gui), search for strings like "autoOff", "powerOff", "standby", "timeout", "onHead".
3. Note: app data under /data/data needs root. Not needed for this project; the commands live in the code and the traffic.
4. Compare an old Smart Control APK with Smart Control Plus to see if the timer/power commands changed.

## Track D: community (Reddit post)
Goal: get the missing pieces: last working firmware version, whether anyone captured the protocol or found a downgrade path.
Draft: docs/reddit-post-draft.md.

## Decision point
- If Track A shows auto-off works with cups up / clean sensor: problem is sensor detection, workaround is behavioural or an Android automation.
- If it never works: firmware regression. Then Track B/C decide whether an Android tool can send a real power-off command when idle.

## Open questions
- Last firmware version where auto-off fully worked: no source found yet.
- Is firmware downgrade possible? Unknown; no source found.
- Which app version is installed now (Smart Control or Smart Control Plus)?
