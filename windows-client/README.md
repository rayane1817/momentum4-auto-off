# MOMENTUM 4 GAIA client (PC)

A command-line tool to talk to the MOMENTUM 4 over its GAIA control channel (Bluetooth Classic, RFCOMM), from Windows or Linux. Pure Python 3.9+, no packages to install. See `../docs/protocol.md` for the protocol and the power-off candidate list.

Tested against a real headset on Windows 10 with Python 3.12 (22 Oct 2026): `verify`, `battery` and the `--features 3` sweep work. The sweep found the remote power-off command `0495:0605` (see `../docs/search-log.md`).

## Before you run
1. The headset must not be in use by any other GAIA app. On the phone that normally holds it, force stop Smart Control Plus, or just keep the headphones away from the phone.
2. Pair the headphones to the PC (Windows Settings > Bluetooth) and make sure they're connected.
3. Have Python 3.9+ installed.

## Quick check
```
python gaia_client.py verify
```
Expected: `connected on RFCOMM channel 15` then `<- 0495:0701 resp [00 00 3C] => auto power-off = 60 s`. If you see that, the link and the framing work.

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

## The power-off search
```
python gaia_client.py sweep --features 3
```
This sends, one per second, every unknown request command in the power/battery feature with an empty payload, and logs the reply. It stops and prints the command if the link drops and then refuses to reconnect for 60 s (the sign of a real power-off), and keeps going if the link just resets.

Scopes (`--features`): `3` power/battery (default, start here), `0q` Qualcomm core, `2` on-head/physical, `0s` Sennheiser config, `all` the ranked set. Range of request-command numbers: `--range 4:127` (default).

When it finds a candidate, check the headphones: red LED blinking 3x and staying off means it worked. Paste the log here either way.

## Safety
The tool refuses to send, whatever you type: factory reset (`0495:0040`), firmware-update / DFU mode (`0495` feature 1), deleting or disconnecting paired devices (`0495:1403/1405/1406`), the reset commands next to power-off (`0495:0607`, `0609-06FF`), device rename (`0495:1500`), and Qualcomm's upgrade feature (`001D` feature 6). `tools/blocklist_parity_test.py` checks that the app blocks the same. The default sweep stays inside features identified as power/system; it never sweeps all features, and it never sweeps the unknown features 16/19 (those need careful manual identification first, see `../docs/protocol.md`).

Worst realistic outcome of a sweep: a "ship/transport mode" that needs a USB charger plug to wake. Annoying, reversible. Stop and report if the headset goes unresponsive without the 60 s reconnect test passing.

## Options
```
--addr AA:BB:CC:DD:EE:FF   headset MAC (default: Ryane's M4, from the capture)
--channel N                force an RFCOMM channel, skip SDP
--log DIR                  where to write logs (default ./logs)
```

## How it connects
It looks up the GAIA channel by SDP if pybluez is installed, otherwise it uses channel 15 (from the capture). It never tries other channels: GAIA frames on another RFCOMM service (e.g. iAP2) can switch a headset off. Every attempt and every frame, in and out, is timestamped in the log under `logs/`.

## Windows notes
- Stock Windows Python has no `socket.AF_BLUETOOTH`, so on Windows the client opens RFCOMM through the Microsoft Bluetooth stack (WinSock `AF_BTH`) with ctypes. It connects by the GAIA service UUID and lets Windows find the channel by SDP; `--channel` is only a fallback. No packages needed (`pybluez2` does not install on Python 3.12 anyway).
- If Windows keeps auto-reconnecting the audio profile and the control channel won't open, unpair the headset from any other nearby device first.
