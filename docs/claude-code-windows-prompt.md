# Prompt for the Windows Claude Code session

Context is in CLAUDE.md and docs/protocol.md.

Task: implement a Windows CLI client for the MOMENTUM 4's GAIA v3 control channel (Bluetooth Classic, RFCOMM).

Requirements:
- Connect to the headset's GAIA RFCOMM channel; resolve the channel via SDP, fall back to the channel in docs/protocol.md.
- Frame encoder/decoder per docs/protocol.md (vendor ID, feature, type, command, payload).
- Send a single command from the CLI and print the decoded response, notifications and errors.
- Log every TX/RX frame with timestamps to logs/.
- Clean connect/disconnect, timeouts, clear error when the headset is unreachable.

Acceptance: the known auto-off GET returns the current value, decoded in seconds.

Constraints: only send commands already observed in captures/. Commit and push when acceptance passes.
