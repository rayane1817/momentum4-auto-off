# MOMENTUM 4 control protocol (from HCI capture, 22 Oct 2026)

Source: captures/btsnoop_hci_202610221800.cfa (Samsung, SysDump). Parser: tools/btsnoop_parse.py.
Capture clock note: timestamps decode ~2 h ahead of Brussels local time (e.g. 19:42 in the parser = ~17:42 local).

## Transport
- Bluetooth Classic, headset MAC 80:C3:BA:4D:6A:24.
- RFCOMM DLCI 20 (server channel 10): standard Hands-Free Profile AT commands. Not relevant.
- RFCOMM DLCI 30 (server channel 15): control protocol used by Smart Control Plus. Opened by the phone right after connect. Channel number may differ per device/firmware: resolve via SDP in tooling.

## Framing: Qualcomm GAIA v3
`FF | ver | flags | len | vendor_id (2, BE) | command_id (2, BE) | payload (len bytes)`
- ver: 0x03 phone->headset, 0x04 headset->phone (observed).
- vendor_id: 0x001D = Qualcomm core; 0x0495 = Sennheiser/Sonova vendor features.
- command_id = feature << 9 | type << 7 | cmd. type: 0 command, 1 notification, 2 response, 3 error.
- => The M4 runs a Qualcomm chip with the GAIA v3 framework.

## Decoded: auto-off timer (vendor 0x0495, feature 3)
Confirmed by Ryane changing the timer 15 -> 30 -> 15 min in the app:
- GET timer:  `FF 03 00 01 04 95 06 01 00`  -> response `... 07 01 00 03 84` (0x0384 = 900 s = 15 min)
- SET timer:  `FF 03 00 03 04 95 06 00 00 07 08` (0x0708 = 1800 s = 30 min) -> empty response = OK
- SET timer:  `FF 03 00 03 04 95 06 00 00 03 84` (900 s)
- Payload: one leading 0x00 byte (sub-id/index?) + uint16 big-endian seconds.

Other feature 3 values read on connect (meaning unknown yet):
- cmd 2 -> `00 00`; cmd 3 -> `3C` (60, also sent as notification); cmd 14 -> `03 FF`.

## Other Sennheiser features seen (vendor 0x0495), meaning TBD
- feature 2: cmd 1 -> 01, cmd 2 -> 03, cmd 6 -> 01 (read on connect and when opening a settings page; candidate: on-head detection / smart pause flags).
- features 4, 8, 9, 10, 11, 12, 13, 16, 19 also used on connect.
- feature 10 notification cmd 11 at ~17:51 local: `00 00 00 00 00 00`.

## Not seen
- No power-off / switch-off command in this capture. Next step: search the decompiled app for feature 3 command names, and capture again while toggling on-head detection and any power option.
