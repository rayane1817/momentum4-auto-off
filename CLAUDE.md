# MOMENTUM 4 auto-off: handoff

Owner: Ryane (Brussels). Sennheiser MOMENTUM 4 (fw 3.38.3), Galaxy Z Flip6 (Android 16) + Nokia XR20 (Android 14).
Full history and test log T1-T42: `docs/history.md`. Read it only when a task needs a past result.

## Communication
- Direct, no preamble, no fluff. Ryane often reads on a phone: outcome first, short.
- Correct Ryane's English/Dutch/French phrasing briefly and show a better version.
- No em-dashes in texts written for others.

## The problem and the fix (settled)
- The firmware's auto-off timer only reaches a standby that keeps Bluetooth on (T1-T6). The case and the 3 s button press are true off.
- `0495:0605` (GAIA v3, feature 3, cmd 5, empty payload) is a remote true off. Proven on 3.38.3, also while the phone holds audio (T8-T10). Wake only with the button.
- The app's guard (AutoOffService) judges idle on the PHONE (no music, no call) for N min, then connects and sends 0605 (T12, T20, T29, T35, T42). Headset-side idle signals are useless here (T11).
- Known tradeoff: wearing them in silence past the idle time switches them off.

## Safety rules (never break)
- Never send `0495:0200` (DFU), `0040` (factory reset), feature 1, `1403/1405/1406`, `0607`, `0609-06FF`, `1500`, Qualcomm feature 6. App `Gaia.blockedReason` and PC `blocked_reason` must stay in parity (`tools/blocklist_parity_test.py`).
- Only documented commands (`docs/command-map.md`). No sweeps. `0605` only via the app (button or guard) or a planned test.
- No Bluetooth addresses or other identifiers in git. Raw captures stay in `%USERPROFILE%\m4-private\` on the laptop.
- No Sennheiser or Nothing marks or fonts in the app.

## Current state (1 Nov 2026)
- Released: v0.14.0 (1 Nov 2026) = design v4 (`docs/design-spec-v4.md`: Nothing X home, Poweramp `EqFaders` + `EqGesture`, dot-matrix `EqCurve`, preset sheet, grouped rows + line icons `res/drawable/ic_*`, guard page + tile switch) on top of 0.13.0 (guard back-off, Copy report, issue form). Ryane chose to skip the laptop run: 0.12-0.14 have never run on a phone; the Reddit testers are the first.
- Next: Ryane posts `docs/reddit-post-v2.md`; about 7 days later ONE session turns all replies into `docs/community-reports.md`, ranks fixes, builds one release.
- Open questions (only from community data or a planned capture): 0605 on other firmware and models; guard on aggressive battery-saver phones; meaning of `1405`, `1800`, `1407` (read-only, never probe).

## Map
- App: `android/gaia-test/` (pure Java, no libs, minSdk 26, targetSdk 35). `MainActivity` (UI + link; pages Home/Equaliser/Listening/Device/Auto power-off), `EqFaders` (vertical faders), `AutoOffService` (guard), `Gaia` (framing, decode, blocklist), `Remote` + `Widgets` + `*Tile` (one-shot sends), `Presets`, `EqSync`, `Watchdog`, `Tok` + `res/values(-night)/colors.xml` (all colours). JVM tests in `app/src/test`.
- PC client: `windows-client/gaia_client.py` (snapshot / diff / check; `--addr` or `M4_ADDR`). Tools in `tools/`.
- Specs: `docs/command-map.md` (every command), `docs/protocol.md`, `docs/config-spec.md`, `docs/design-spec-v3.md`, `docs/design-spec-v4.md` (current design, built in v0.14.0).
- Releases: bump `versionCode`/`versionName` in `app/build.gradle`, copy the APK to `android/gaia-test/release/` + `SHA256SUMS`, merge, then run `release.yml` on main (tests first; tag must equal v<versionName>). `release.yml` publishes the committed APK after checking its `SHA256SUMS` line; it never uploads a CI rebuild (T43). Signing key is public on purpose (`keystore/`).

## How to work (token budget)
- One task per session. Read only the files the task names; don't load `docs/history.md` or big specs unless needed.
- Before any release: sorted diff of `send(...)`/`toggle(...)` in MainActivity vs main; must be identical unless the change is intended.
- PR CI (`build.yml`) runs the unit tests; local JUnit downloads may fail in the cloud sandbox, so rely on CI.
- Laptop (hardware) sessions: one per release, combine minor versions, follow the checklist, add one line per test to the Test log below. Each entry: fw, app and version, cups, case, result.
- Reviews: diff only (code-review skill), not the whole repo. No max-effort advisor runs for docs.

## Test log (new entries only; T1-T42 are in docs/history.md)
- (none yet after T42)
