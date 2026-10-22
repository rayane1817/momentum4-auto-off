# MOMENTUM 4: a real auto power-off

The Sennheiser MOMENTUM 4 Wireless has an auto power-off setting in the app (15, 30 or 60 minutes). On current firmware it doesn't switch the headphones off. They go into a standby where Bluetooth stays on, they reconnect as soon as a phone looks for them, and the battery keeps draining.

This repo documents why, and gives a working fix: a command that switches the MOMENTUM 4 fully off over Bluetooth, plus a small Android app that sends it when the headphones haven't been used for a while.

Unofficial community project. Not affiliated with or endorsed by Sennheiser or Sonova.

## Status

| | |
|---|---|
| Remote power-off command | Found and confirmed: `0495:0605` |
| Android app with automatic power-off when idle | Confirmed working in v0.3. v0.5 adds a new interface and settings controls, not yet tested on the headphones |
| PC command-line client (Windows, Linux) | Working |
| Tested on | One MOMENTUM 4 (M4AEBT SE), firmware 3.38.3, Samsung phone |
| Other firmware versions or models | Unknown, reports welcome |

## What's going on

- The original manual (version 08/22) says the headphones "automatically switch off after 15 minutes" once you take them off. A later online FAQ says they "switch off almost completely" and wake when touched. The behaviour changed somewhere along the way.
- In our tests, on firmware 3.38.3, the timer never produced a real switch-off: not with 15 minutes, not with 60 seconds, not with nothing else connected. The headphones always reconnected within a second.
- While a phone is connected, the timer doesn't seem to run at all, and Android keeps the headphones connected even when nothing plays. A Reddit commenter adds that any phone notification resets it.
- The carry case and the 3-second button press still switch them off properly. So the firmware can do a real switch-off, the timer just doesn't use it.
- The app talks to the headphones with Qualcomm's GAIA v3 protocol over classic Bluetooth. A scoped search of the power/battery commands found `0495:0605`: no payload, and it switches the headphones fully off, even while a phone is connected for audio.

Details: [`docs/protocol.md`](docs/protocol.md) (protocol and command map) and [`docs/search-log.md`](docs/search-log.md) (how `0605` was found). The full test log, T1 to T12, is in [`CLAUDE.md`](CLAUDE.md), which doubles as the project's lab notebook.

## Android app

`android/gaia-test`: a small app with no internet permission, no tracking and no libraries, Bluetooth only.

- **Auto power-off when idle.** A background guard watches the phone. When no audio has played and there has been no call for the set time (default 15 minutes), it connects to the headphones and sends `0605`. Nothing else holds the connection while it waits.
- **Power off now.** One button, with a confirmation.
- **Noise control and settings.** ANC, transparency and its level, smart pause, auto-answer, low latency, sidetone, plus a readout of battery, model and firmware.

Install: download [`m4-gaia-test-0.5.apk`](android/gaia-test/release/m4-gaia-test-0.5.apk) on the phone and allow the install. It's a self-built app, so Play Protect will warn you. SHA-256 is in [`SHA256SUMS`](android/gaia-test/release/SHA256SUMS). If the app can't connect, force-stop Smart Control Plus, because only one app can use the control channel at a time. More in the [app README](android/gaia-test/README.md).

Known limits:
- If you wear the headphones in silence (ANC on, no audio) longer than the timeout, the guard switches them off. Raise the timeout if that bothers you.
- Once off, Bluetooth is off too, so they can't be switched back on remotely. Use the button.

## PC client

`windows-client/gaia_client.py`: a Python command-line tool, no packages needed. Tested on Windows 10 through the built-in Bluetooth stack; Linux is supported but untested. Read the battery and timer, set the timer, send single commands, or switch the headphones off:

```
python gaia_client.py verify
python gaia_client.py raw "FF 03 00 00 04 95 06 05"
```

More in the [client README](windows-client/README.md).

## Safety

Both tools refuse commands that could do damage, whatever you type: factory reset, firmware update mode, deleting paired devices and Qualcomm's upgrade feature. Use at your own risk anyway. This is a hobby project tested on one pair of headphones.

## Help wanted

Useful reports, ideally as a GitHub issue:
1. Your firmware version (Smart Control Plus > product info) and whether the auto power-off ever fully worked for you.
2. Whether `0605` switches off your MOMENTUM 4 on firmware other than 3.38.3.
3. Whether it works on other Sennheiser models that use Smart Control Plus (MOMENTUM True Wireless 4, ACCENTUM, MOMENTUM Sport).

## Repo layout

| path | what |
|---|---|
| `android/gaia-test/` | Android app (source and APK) |
| `windows-client/` | PC command-line client |
| `tools/btsnoop_parse.py` | parser for Android Bluetooth HCI logs, used to decode the protocol |
| `docs/` | protocol, findings, research and plans ([index](docs/README.md)) |
| `captures/` | logs of the power-off tests |
| `CLAUDE.md` | lab notebook and full test log |

## License

GPL-3.0, see [`LICENSE`](LICENSE). You may use, change and share the code, as long as modified versions stay open source under the same license.

## Credits

- [Oein/sennheiser-desktop-client](https://github.com/Oein/sennheiser-desktop-client) and [momentumctl](https://zff.dev/galen/momentumctl), whose protocol work on other Sennheiser models confirmed the framing and several commands.
- r/sennheiser users who shared their experiences.
- Built with Claude Code. Reverse engineering for interoperability only: no Sennheiser code or files are included in this repo.
