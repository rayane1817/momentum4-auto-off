# MOMENTUM 4 Windows Companion

A Windows 10 and Windows 11 desktop client for the MOMENTUM 4 GAIA control channel. It is a normal desktop app that stays available from the notification area; the tray menu provides quick cycle actions for EQ presets and noise control.

## What it controls

- Noise control: Adaptive, Custom and Off; Custom includes ANC-to-transparency level and anti-wind settings.
- Equaliser: five bands (63 Hz to 8 kHz), eight verified presets, five Windows-local custom slots, bass boost and speech clarity.
- Listening: Smart Pause, Low Latency, Hi-Res 24/96, Auto-answer, Comfort Call, call transparency, on-head detection and touch controls.
- Device: battery, model, firmware and hardware revision; firmware timer; documented raw GAIA frame sender; report and local logs.
- Auto power-off: a tray-resident idle guard that checks Windows output audio every five seconds and sends the documented power-off command only after the selected silent period. If Core Audio cannot be read, it fails closed and does not switch the headphones off.

The device address is saved locally at `%LOCALAPPDATA%\Momentum4Companion\settings.json`; GAIA connection logs are saved under `%LOCALAPPDATA%\Momentum4Companion\logs`.

## Use

1. Pair the headphones in Windows Bluetooth settings. Close Smart Control Plus and any other GAIA control app that may hold the connection.
2. Run `Momentum4Companion.exe`.
3. Open **Device**, enter the Bluetooth address in `AA:BB:CC:DD:EE:FF` form, and select **Connect and refresh**. Windows exposes this address under Bluetooth device properties.
4. Use the Home page for the primary controls. Closing the app window keeps it in the notification area; use **Exit** in the tray menu to fully quit it.

The auto power-off guard listens for audible Core Audio output. Windows does not provide the Android phone-call state used by the Android client, so the guard is deliberately conservative and only powers off when its audio meter is available and silent for the entire interval.

## Build a standalone EXE

Install Python 3.9+ with Tcl/Tk, then from the repository root run:

```bat
py -3 -m pip install -r windows-client\requirements-build.txt
windows-client\build-exe.bat
```

The build is written to `windows-client\dist\Momentum4Companion.exe` and includes Python and all runtime dependencies.

