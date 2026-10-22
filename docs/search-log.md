# Power-off search log

One entry per sweep or probe run against the real headset. Scope and safety rules: docs/protocol.md, "Power-off search: candidate list".

Setup for all runs below: Sennheiser MOMENTUM 4 (M4AEBT SE, hw 4, fw 3.38.3), MAC 80:C3:BA:4D:6A:24, paired to a Windows 10 PC (Intel Wireless Bluetooth). Client: windows-client/gaia_client.py, Python 3.12.10. Smart Control Plus force-stopped on the phone.

## 22 Oct 2026 23:09, verify and baseline
- `verify`: connected, `0495:0701 resp [00 00 3C]` => timer 0 = 60 s (left from T4b).
- `battery`: `0495:0703 resp [32]` => 50 %.

## 22 Oct 2026 23:22, sweep `--features 3` (T8)
Command: `python gaia_client.py sweep --features 3 --channel 15`
Scope: vendor 0495, feature 3, request cmds 0x04-0x7F, empty payload, max 1 command per second.

| time | sent | reply | result |
|---|---|---|---|
| 23:22:49.611 | `0495:0604` | `0495:0704 resp [0E E7 FF FF]` | data; 0x0EE7 = 3815, probably battery mV |
| 23:22:51.616 | `0495:0605` | `0495:0705 resp []`, then `0880 [FF]`, `089A [0]` (audio stop) x2 | link closed by headset at 23:22:51.860 |

Reconnect probe: 8 attempts between 23:22:56 and 23:23:56 all timed out => "headset appears OFF".
Checked on the headset: fully off.
Sweep stopped at the first candidate, so `0606-067F` were not probed.
Log: captures/gaia_sweep_f3_20261022_232249.log

## 22 Oct 2026 23:27, replication (T9)
Headphones switched back on with the button.
Command: `python gaia_client.py raw "FF 03 00 00 04 95 06 05" --channel 15`
- 23:27:05.165 sent, 23:27:05.189 `0495:0705 resp []`, 23:27:05.427 link closed by headset.
- `verify` at 23:27:15, 23:27:36, 23:27:56: all failed to connect (timed out).
Checked on the headset: fully off again.
Log: captures/gaia_replicate_0605_20261022_232705.log

## 22 Oct 2026 23:44, with the phone connected for audio (T10)
Phone connected to the M4 for audio, Smart Control Plus force-stopped.
Command: `python gaia_client.py raw "FF 03 00 00 04 95 06 05" --channel 15`
- 23:44:34.431 sent, 23:44:34.456 `0495:0705 resp []`, 23:44:34.741 link closed by headset.
- `verify` at 23:44:44, 23:45:05, 23:45:25: all failed to connect (timed out).
Checked on the headset: fully off.
Log: captures/gaia_phone_connected_0605_20261022_234434.log

## Result
`0495:0605` with an empty payload switches the MOMENTUM 4 fully off, also while the phone holds the audio link. Once off, the Bluetooth radio is off, so it cannot be switched back on remotely; wake it with the button. The `0q` (Qualcomm core) sweep was not needed and not run.

## Windows transport notes (first hardware run)
- Stock Windows CPython has no `socket.AF_BLUETOOTH`. The client now opens RFCOMM through WinSock `AF_BTH` with ctypes; Linux still uses `AF_BLUETOOTH`.
- `SOCKADDR_BTH` must be packed (`ws2bth.h` uses pshpack1, 30 bytes). With natural alignment (40 bytes) every connect and bind fails with WSA 10049.
- `pybluez2` does not install on Python 3.12 (no wheel, broken sdist). Not needed.
- The client connects by the GAIA service UUID and lets Windows resolve the channel by SDP; the explicit channel is only a fallback. The log line "connected on RFCOMM channel 15" shows the requested channel, not one confirmed by SDP.
