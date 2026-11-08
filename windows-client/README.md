# MOMENTUM 4 on Windows

Two tools live here:
- **M4 Control** (`M4Control.exe`, or `m4_app.py` + `ui/index.html`): the Windows app. Noise control, EQ with presets, the listening and call settings, connections, a tray panel, and Power off.
- **`m4ctl`** (`m4ctl.exe` or `m4ctl.py`): the same controls from the command line, for hotkeys and scripts.
- **`gaia_client.py`**: the command-line client used for testing (below).

## M4 Control (0.2.2)

Same look and pages as the Android app (design v4): dot battery, noise control with round buttons (Adaptive, Custom, Transparency, Off), tiles, Poweramp-style EQ faders with the dot-matrix curve and preset sheet, Listening and Device pages, Power off. Follows the Windows light/dark setting. Not in it yet: automatic power-off when idle (the guard), which is still Android only.

Setup with the exe (no Python needed):
1. Pair the headphones with this PC in Windows Settings > Bluetooth.
2. Download `M4Control.exe` (and `m4ctl.exe` if you want the command line) and put them in a folder of your own, for example `%LOCALAPPDATA%\Programs\M4Control`.
3. Double-click `M4Control.exe`. It isn't signed, so Windows SmartScreen asks once: **More info** > **Run anyway**. Check the file against `SHA256SUMS` first if you like (`certutil -hashfile M4Control.exe SHA256`).

Or from source: install Python 3.9+ from python.org, then double-click `start-m4-app.bat` (or `pythonw m4_app.py`). To build the exes yourself: `python -m pip install pyinstaller==6.11.1` then `python build_exe.py` (CI does the same, `.github/workflows/windows-app.yml`).

It opens in its own Edge window (Edge comes with Windows; Chrome or the default browser if Edge is missing), finds the paired MOMENTUM 4 and connects. Your choice and your own EQ presets are saved in `%LOCALAPPDATA%\M4Control\settings.json`, logs in `%LOCALAPPDATA%\M4Control\logs` (no Bluetooth address in them). **Device > Copy report** copies a report for a GitHub issue.

**Tray icon** (the Windows version of the Android widgets and tiles): left-click the ear icon next to the clock for a small panel with the battery, the four noise modes and previous / next EQ preset; right-click for the same as a menu; double-click for the window. The icon shows the noise mode, like the Android tile. If the window is open and connected, the tray sends through that link; otherwise it connects, sends and disconnects (about 1 s), so nothing holds the headphones. If they're off you get a notification. Closing the window releases the headphones after 10 s and the app stays in the tray; **Quit M4 Control** in the tray menu ends it. **Start with Windows** in the same menu starts it in the tray at sign-in (`m4_app.py --tray`). Power off is not in the tray on purpose: it stays a button in the window, after a confirm.

How it works: `m4_app.py` owns the Bluetooth link (same code as the command-line client) and serves the window (`ui/index.html`) on 127.0.0.1 only, behind a random token checked on every request, with Host and Origin checks. No internet, nothing listens on the network. `m4_logic.py` builds every frame and only allows documented commands; Power off is the one exception, from its button only, after a confirm.

**New in 0.2.2** (ideas from other MOMENTUM 4 projects, all with documented commands):
- **Transparency** as a fourth noise mode: Custom at full transparency (level 100). Picking Custom again goes back to your own Custom level.
- **Tones and voice prompts**: Off, Tones, or Tones and voice (Listening page).
- **Connections** (Device page): the devices paired with the headphones, which are connected, which one is this PC. Tap one to connect it. The headphones keep two at a time. Their names stay on this PC: the log and Copy report hide them.
- **Charging** shows next to the status.
- **Reconnects on its own** every 20 s while the window is open, after the headphones went out of reach or were off. Not after you press Disconnect or Power off.
- **m4ctl** for hotkeys and scripts. Through the app when it runs, else it connects itself (about 2 s):
  ```
  m4ctl status
  m4ctl battery
  m4ctl noise adaptive|custom|transparency|off|next
  m4ctl level 0-100
  m4ctl eq next|prev|Rock
  m4ctl prompts off|tones|voice
  ```
  No power off from the command line, on purpose.

Not here on purpose: rename the headphones and disconnect a device. Both are blocked (`1500`, `1403`): on some firmware they wiped a pairing.

Good to know:
- The app talks to the headphones over the PC's own Bluetooth. A Sennheiser BTD 600/700 USB dongle only carries audio, so the PC also needs its own Bluetooth paired with the M4. Not tested yet with a dongle.
- The M4 accepts a second control link at the same time (seen 8 Nov 2026: two apps on this PC were connected together), so the phone app and this app can probably both be open.
- The phone app's widgets open their own short link to the headphones, which is why they work even when the phone isn't playing to them.
- If the guard is on in the Android app, it only sees the phone. It can switch the headphones off while you listen on the PC.

Tested 8 Nov 2026 on Windows 10, Python 3.12, M4 fw 3.38.3: connect and read all; presets (Rock, Movie, Flat), a fader drag and Smart pause changed and read back, then restored. Power off: the confirm opens, not pressed. Tray (0.2.1): one-shot with nothing linked set EQ Rock (1.1 s) and noise Custom (0.9 s), read back correct; then Adaptive and Neutral through the open link. 0.2.2: Transparency (level read back 100, then 0), tones/voice prompts read, the connections list (3 devices, this PC = `1407`), charging read, m4ctl through the app and on its own (status 2.7 s, EQ Rock then Neutral), `m4ctl poweroff` refused; `M4Control.exe` and `m4ctl.exe` built with PyInstaller 6.11.1 and run on this PC. Unit tests: `python -m unittest discover -p "test_*.py"`.

# MOMENTUM 4 GAIA client (PC)

A command-line tool to talk to the MOMENTUM 4 over its GAIA control channel (Bluetooth Classic, RFCOMM), from Windows or Linux. Pure Python 3.9+, no packages to install. See `../docs/protocol.md` for the protocol and the power-off candidate list.

Tested against a real headset on Windows 10 with Python 3.12: `verify`, `battery`, `snapshot` and `check` work. On 22 Oct 2026 a scoped sweep of the power/battery feature found the remote power-off command `0495:0605` (see `../docs/search-log.md`). That sweep is now off by default (see below).

## Before you run
1. The headset must not be in use by any other GAIA app. On the phone that normally holds it, force stop Smart Control Plus, or just keep the headphones away from the phone.
2. Pair the headphones to the PC (Windows Settings > Bluetooth) and make sure they're connected.
3. Have Python 3.9+ installed.

## Quick check
```
python gaia_client.py verify
```
First time: put your headphones' Bluetooth address (Windows Settings > Bluetooth > the headphones > Properties) on the first line of `windows-client/m4_addr.txt`. That file is git-ignored. Or set `M4_ADDR`, or pass `--addr`.

Expected: `connected on RFCOMM channel found by GAIA UUID` (Windows) then `<- 0495:0701 resp [00 00 3C] => auto power-off = 60 s`. If you see that, the link and the framing work.

Other reads:
```
python gaia_client.py battery
python gaia_client.py get-timer 0
python gaia_client.py raw "FF 03 00 00 04 95 04 02"
```

Set a timer (seconds):
```
python gaia_client.py set-timer 0 900
```

## Sweeps are off by default
The power-off command is already known (`0495:0605`), so the blind sweep that found it is disabled: `python gaia_client.py sweep` refuses to run. A sweep sends undocumented commands to your headphones, one per second.

For research only, at your own risk, set `M4_ALLOW_SWEEP=1` first. Even then it skips every command the catalogue (`../tools/m4_commands.py`) marks as never sent from the PC, including `0605`, and everything on the blocklist. Scopes (`--features`): `3` power/battery, `0q` Qualcomm core, `2` on-head/physical, `0s` Sennheiser config, `all`. Range of request-command numbers: `--range 4:127` (default).

`raw` takes exactly one complete frame (`FF 03`, a length that matches, nothing after it). A typo in the hex, a second frame or stray bytes is refused and nothing is sent.

## Safety
The tool refuses to send, whatever you type: factory reset (`0495:0040`), firmware-update / DFU mode (`0495` feature 1), deleting or disconnecting paired devices (`0495:1403/1405/1406`), the reset commands next to power-off (`0495:0607`, `0609-06FF`), device rename (`0495:1500`), and Qualcomm's upgrade feature (`001D` feature 6). `tools/blocklist_parity_test.py` checks that the app blocks the same. A sweep (if you enable one) stays inside features identified as power/system and never touches the unknown features 16/19 (those need careful manual identification first, see `../docs/protocol.md`). Worst realistic outcome: a "ship/transport mode" that needs a USB charger plug to wake. Annoying, reversible. Stop and report if the headset goes unresponsive.

## Options
```
--addr AA:BB:CC:DD:EE:FF   headset MAC (default: $M4_ADDR, then windows-client/m4_addr.txt)
--channel N                force an RFCOMM channel, skip SDP
--log DIR                  where to write logs (default ./logs)
```

## How it connects
It finds the GAIA channel by its service UUID (Windows) or by SDP (pybluez). It never guesses a channel, not even 15, unless you pass `--channel`: GAIA frames on another RFCOMM service (e.g. iAP2) can switch a headset off. Every attempt and every frame, in and out, is timestamped in the log under `logs/`.

## Windows notes
- Stock Windows Python has no `socket.AF_BLUETOOTH`, so on Windows the client opens RFCOMM through the Microsoft Bluetooth stack (WinSock `AF_BTH`) with ctypes. It connects by the GAIA service UUID and lets Windows find the channel by SDP; `--channel` is the only way to force a raw channel number. No packages needed (`pybluez2` does not install on Python 3.12 anyway).
- If Windows keeps auto-reconnecting the audio profile and the control channel won't open, unpair the headset from any other nearby device first.
