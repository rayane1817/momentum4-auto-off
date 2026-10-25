# Local retest: app v0.10.1 (the three v0.10 bug fixes)

For the LOCAL Claude Code session on the Windows laptop. Same rules, tools and safety as `docs/local-session-plan.md` (sections 1, 4, 5) and `docs/local-test-v0.10.md`. Expected time: about 25 min agent, 5 min human. Only the three fixes and their neighbours are retested; v0.10 passed everything else (T23 to T29).

## Kickoff prompt (paste into a new local session in the repo folder)

```
You are the LOCAL hardware session for the MOMENTUM 4 project. git pull first.
Read: CLAUDE.md, docs/local-session-plan.md (sections 1, 4, 5), then docs/local-test-v0.10.1.md
and follow it phase by phase.
Rules stay: only documented commands via gaia_client.py check; blocklists stay; raw data in
%USERPROFILE%\m4-private; every test entry records fw, app + version, cups, case, result.
The headphones are switched off with the button for phase B (no 0605 needed in this run).
Show me the START checklist from docs/local-test-v0.10.1.md now, then begin.
```

## START checklist (human, about 2 min)
1. Phone unlocked, USB in, Smart Control Plus force-stopped. Headphones on the desk, cups up, switched on.
2. v0.10 still installed, with the two widgets on the home screen and the two tiles in Quick Settings (from the v0.10 run). If not, add them again (see `docs/local-test-v0.10.md`, START 3 and 4).
3. Tell the agent "done".

## Phases (agent)

### A. Install over v0.10
- `python tools/apk_cert.py android/gaia-test/release/m4-gaia-test-0.10.1.apk`: expect `285fe924...4a78`, same as v0.10.
- `adb install -r` the 0.10.1 APK over v0.10. PASS: no uninstall needed; `dumpsys package be.ryane.gaiatest` shows versionCode 14, versionName 0.10.1; custom presets and widgets survive the update.

### B. Fix 1: widget tap with the headphones off (was ANR, T30)
Watch `adb logcat -s M4REMOTE ActivityManager` and `adb logcat -b events | findstr am_anr`.
1. Ask the human to switch the headphones off with the button (3 s hold). Open the app once, Home, `adb shell am kill be.ryane.gaiatest`.
2. Tap the noise widget "Custom". PASS: toast "Noise control: Headphones busy or off ..." within about 5 s of the tap (record the time); `M4REMOTE failed: ... connect timeout after 4500 ms` (or a read failed) once; NO "ANR in be.ryane.gaiatest", no "bg anr" kill; the highlighted chip stays on the old mode.
3. Tap EQ widget "next". PASS: toast "EQ: Headphones busy or off ...", no ANR, the preset name does not change.
4. Tap three widget buttons quickly in a row. PASS: no ANR, one toast per tap (they may queue).
5. Noise tile tap in the same state. PASS: tile greyed for at most about 10 s (was ~15 s), label restored, toast, no ANR.
6. Ask the human to switch the headphones on. Process killed again (`am kill`), then tap noise widget Adaptive, Custom, Off and EQ next five times. PASS: every tap applies (frames in `M4REMOTE`, PC readback matches, highlight follows); record tap to first frame (v0.10: 1.0 to 2.2 s). Any "connect timeout" with the headphones on = FAIL (the 4.5 s limit would be too short).
7. App open and connected: widget taps still go through the app link (`M4GAIA`, no second connection), as in v0.10 E4.

### C. Fix 2: cold start keeps the EQ widget (was reset to Neutral, T28)
1. EQ widget to "Pop" (widget or app), PC `1003 [00]` readback = Pop.
2. `adb shell am force-stop be.ryane.gaiatest`, open the app, do NOT connect, Home. PASS: EQ widget and EQ tile still show "Pop" (v0.10 showed "Neutral"). Next tap goes to "Dance".
3. Open the app and connect. PASS: the app reads the curve (`1003 [00]`), the Pop chip is highlighted, the widget stays "Pop".
4. Still connected: set the Rock curve from the PC (five `1001` writes via `check`). PASS: the app shows Rock (`1082` notification) and the widget follows to "Rock".
5. Disconnect in the app. Set Jazz from the PC. Save nothing. Home: widget still "Rock" (the app must not push a stale curve after disconnect). Reconnect: widget follows to "Jazz".
6. Restore Neutral.

### D. Fix 3: header visible (was under the status and action bars)
1. Open the app. `uiautomator dump`. PASS: no action bar ("M4 GAIA test" title bar gone); the "MOMENTUM 4" title, the Connected/Disconnected pill, the device picker and Rescan all lie below the status bar (top of the title row >= status bar height, about 100 px on the Flip6) and are fully on screen; status bar icons are dark and readable on the white strip.
2. Tap Rescan (uiautomator bounds): the device list reloads (log line). Connect / Disconnect: the pill changes text.
3. Scroll to the bottom: the LOG card and the last button are not hidden under the navigation bar or gesture handle.
4. Tap the idle-minutes field in AUTO POWER-OFF: the field stays visible above the keyboard. Back to close the keyboard.
5. Optional (human): rotate once if auto-rotate is on: header still visible. The cover screen is out of scope.

### E. Regression
- `tools/blocklist_parity_test.py` passes (app 1276 ids, PC 1277, PC-only 1500, unchanged).
- Guard: not changed in v0.10.1, so no `0605` in this run. Check only that the guard starts and stops from the AUTO POWER-OFF card and its status matches `dumpsys activity services be.ryane.gaiatest`.

## END checklist (human, about 1 min)
1. Headphones on? Switch them on with the button if needed.
2. Optional: say whether the header now looks right and the "busy or off" toast is clear.

## Deliver
- T31+ entries in CLAUDE.md (fw, app + version, cups, case, result per test).
- Branch `local/v0101-validation-<yyyymmdd>`, PR with a results table, like PR #15.
- Restore every setting; final snapshot diff empty.
