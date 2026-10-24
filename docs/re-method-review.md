# Review of the reverse-engineering method (24 Oct 2026)

Scope: recover the settings commands Smart Control Plus sends to Ryane's own MOMENTUM 4, for interoperability. Setup: non-rooted Galaxy Z Flip6 (One UI), Windows 10 laptop. Out of scope throughout: firmware update/DFU, the voice-prompt install path, factory reset, pairing deletion, and modifying or instrumenting the official app.

## 1. What is inefficient today
| problem | effect |
|---|---|
| Snoop log is a ring buffer, pulled minutes or hours later via a 57 MB bugreport | early steps can be overwritten; one capture needed two files (`.log` + `.log.last`) |
| Every reconnect re-reads ~40 settings (Capture 2 had 6 reboot/reconnect blocks) | the ~60 real writes sit inside ~4,900 frames |
| Timestamps written by hand, clock offset uncertain | lining up actions with frames is guesswork; minutes-wide windows |
| GAIA channel moved from DLCI 30 to DLCI 3 between captures | a channel-filtered parser silently shows "no GAIA traffic" |
| Several settings toggled per session, sometimes not from a known start state | ambiguous mapping (e.g. comfort call never appeared, no way to tell if it was skipped or sent differently) |
| Decoding is manual each time | slow, and every new capture repeats the same work |

## 2. Better capture options (no root needed unless stated)
1. **Live btsnoop streaming over adb** (no root on most Android 11+ builds): with the HCI snoop log on, `adb shell` can't read the file directly, but Wireshark's **androiddump extcap** (ships with Wireshark for Windows) can stream "Android Bluetooth btsnoop" live from the phone over USB. If One UI blocks it, the fallback stays `adb bugreport`. Payoff: no ring-buffer loss, real-time view. **Try first; 15 min to check.**
2. **Shorter, single-feature sessions**: Bluetooth off/on, do ONE setting change, pull immediately. Even with bugreport this removes most ambiguity. Zero tooling.
3. **Probe with our own client instead of capturing** (PC `gaia_client.py` or the Android app): for any setting whose command we already know or suspect, read the GET before and after toggling it in the official app. No capture at all, no root. Limit: only one RFCOMM client at a time, so our client and Smart Control Plus can't be connected together; read, disconnect, toggle in the official app, reconnect, read again.
4. **In-app sniffing of the official app's traffic from our app**: not possible. Android gives each RFCOMM connection to one app; we can't see another app's frames without root.

## 3. Autonomous tooling worth building
| tool | what it does | effort | payoff |
|---|---|---|---|
| **Auto-annotating decoder** (extend `tools/btsnoop_parse.py`) | finds the GAIA channel by content (any DLCI carrying `FF 03/04 .. 04 95`), learns the connect-time read set, drops reconnect blocks, prints only writes + resulting notifications, groups bursts, labels known IDs from `config-spec.md` | ~half a day | high: Capture 2 decode becomes one command |
| **State-diff probe mode** in `gaia_client.py` (`snapshot` / `diff`) | reads every known GET, saves JSON; second run prints what changed | ~2-3 h | high: confirms a setting's GET/value without any capture |
| **UI automation of Smart Control Plus** via `adb shell input tap` / `uiautomator dump` | drives the official app through each setting from a script, logging exact host timestamps per tap | ~1 day, brittle across app updates and the French UI | medium: precise timestamps and repeatable runs; worth it only if many more features need capturing |

## 4. Static analysis
- Blutter on this Dart version gave names and enums but no function bodies, so byte values must come from traffic, not code. Newer blutter releases may restore bodies; worth one retry when a release targets Dart 3.10, low effort.
- The old NativeScript app's `m4.json` remains the best static source; the new app is only useful for feature/enum names.
- Hooking or repackaging Smart Control Plus is out of scope (it changes Sennheiser's app and conflicts with its terms); the traffic route gives the same answers. EU Directive 2009/24/EC art. 6 covers interoperability decompiling, not redistribution: keep Sennheiser code and assets out of the repo, as now.

## 5. Recommended pipeline
1. Build the **auto-annotating decoder** first (channel auto-detect, reconnect filtering, labels).
2. Add **snapshot/diff** to `gaia_client.py`.
3. For each remaining feature (comfort call, auto-pause, preset handling, anything new): known/suspected command → confirm with snapshot/diff; unknown → one short single-feature capture (try Wireshark androiddump live first, else bugreport) → run the decoder.
4. Only if a large batch remains, add UI automation.

The two tools worth building first: the auto-annotating decoder and snapshot/diff.
