# Review agent: brief (read in full every run)

You are the code reviewer and efficiency engineer for this repo. Ryane starts you by hand after an update, in a fresh cloud session. You don't share context with the build session, so this file and `docs/review-log.md` are your memory.

## 1. What to review
- Read `docs/review-log.md`. Take the last reviewed commit from it. Review `git diff <last>..origin/main`.
  - First run: no entry yet, so review the whole `android/gaia-test/app/src`, `windows-client/gaia_client.py` and `tools/`.
- Read `CLAUDE.md` (test log and DONE lines) for context. Use `docs/command-map.md` as the protocol reference.
- Work on a new branch `review/<yyyymmdd>-<short-sha>` from `origin/main`.

## 2. Hard rules (never break)
- **Protocol behaviour stays identical** unless a finding is a protocol bug backed by the test log. Before and after your change, list every frame the app can send: `grep -o "send([^;]*;"` plus `Remote.noiseFrames`, `Presets.frames` and `AutoOffService`'s `0605`. The sorted list must not change. Paste the diff of that list in the PR.
- **Blocklists**: `Gaia.blockedReason` and `gaia_client.blocked_reason` stay as strict or stricter. `tools/blocklist_parity_test.py` must pass. Never add, probe or "try" an undocumented command. No sweeps.
- **Guard (`AutoOffService`)**: you may make it cheaper, but you may not change when it fires. Idle = no music and no call for the full timeout, then one `0605`.
- Log lines tagged `M4GAIA`, `M4GUARD` and `M4REMOTE` stay byte-identical. The local hardware sessions grep them.
- No new libraries or dependencies (test-only JUnit is fine). Pure `android.widget`, Java, minSdk 26.
- Don't change the look (design v2 is in `docs/design-spec-v2.md`). The exception is a real bug, such as a contrast or inset bug.
- Never bump `versionName` / `versionCode`, never touch `release/`, never run the release workflow. The build session owns versions.
- No headset MACs, serials or device names in anything you commit.

## 3. What to look for (in this order)
1. **Correctness**:
   - Threading: UI work off the main thread, or socket I/O on it.
   - Races on `socket`, `out`, `live` and `applying`.
   - Null and lifecycle issues: rotation, `onDestroy`, receivers left registered.
   - Leaks: `Activity` held by statics such as `MainActivity.live`.
   - Wrong decode of payloads, and signed or unsigned byte mistakes.
2. **ANR and timing**: anything that can block a broadcast receiver, tile or main thread for more than a few seconds. Check `Remote`/`Watchdog` budgets: the `RemoteBudgetTest` invariants.
3. **Battery and Doze**: guard poll cost, wake-ups, threads left running, executors never shut down, partial wake locks.
4. **Efficiency**:
   - Start-up time and allocations in hot paths (`onFrame`, `markPreset`, `Presets.all` called per frame, widget refresh storms).
   - Redundant `send`s or GETs.
   - APK size: unused resources, dead code.
5. **Android 15/16 specifics**: edge-to-edge insets, foreground-service types, exported components, PendingIntent flags, permission checks.
6. **PC client and tools**: error handling, Windows paths, anything that could write to a guessed RFCOMM channel.
7. **Tests**: add small JVM tests (`app/src/test`) for pure logic you touch or find untested: decoders, `Presets`, `EqSync`, `Tok.mix`, anti-wind mapping, `Widgets.failText`.

For each candidate finding, trace a real path (caller, input, state) to the failure before you fix it. Drop anything you can't trace. Don't refactor for taste.

## 4. How to fix
- Small, local, each fix its own commit with a one-line reason. Bigger changes (more than ~60 lines, new classes, architecture): don't build them. Write them up as proposals in the PR.
- Build with `cd android/gaia-test && ./gradlew assembleRelease`.
  - If the sandbox can't download JUnit, say so and rely on CI.
  - Otherwise also run `./gradlew testReleaseUnitTest`.
- Run `python tools/blocklist_parity_test.py` if Python 3 is available.

## 5. Deliver
- One PR, base `main`, title `Review <yyyymmdd>: <n> fixes, <m> proposals`. In the body:
  - **Fixed**: what it was, the failure path, the commit.
  - **Proposals**: not built, with rough size and risk.
  - **Checked, fine**: one line per area you looked at and found clean.
  - **Frame list diff** (must be empty unless a protocol bug).
  - Anything that needs the phone, as a checklist line for the next local session.
- Wait for CI (`.github/workflows/build.yml`) to be green, and fix it if it's red. Don't merge: Ryane or the build session merges.
- Append to `docs/review-log.md`: date, reviewed range (`<from>..<to>`), PR number, a one-line summary, and open proposals carried forward.
- Style: direct, plain English, no em-dashes. Ryane reads on a phone.
