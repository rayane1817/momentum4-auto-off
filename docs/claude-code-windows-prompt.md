# Prompt for the Windows Claude Code session

Context is in CLAUDE.md and docs/protocol.md.

Objective: find a GAIA command that powers the MOMENTUM 4 fully off (same state as the case or button), working autonomously until you find one or exhaust the candidates.

1. Build a Windows CLI client for the GAIA v3 control channel (Bluetooth Classic, RFCOMM): SDP channel lookup with fallback to docs/protocol.md, frame encoder/decoder, TX/RX logging with timestamps to logs/, timeouts and clean reconnects. Verify with the known auto-off GET.
2. Build the candidate list: decompile Smart Control Plus (com.sonova.chb.control), map vendor and feature/command IDs to names, rank candidates by likelihood (power, off, shutdown, standby, sleep, idle). Record the mapping in docs/protocol.md.
3. Run the search loop without asking me, one candidate at a time:
   - send the command, max one command per second;
   - success test: the RFCOMM/ACL link drops and reconnect attempts fail for 60 s; then stop, report the command and wait for me to confirm the headset is off;
   - if the link drops but reconnect succeeds, log it as reset/disconnect and continue;
   - log every attempt and result to docs/search-log.md, commit and push regularly.
4. When a command is confirmed, add an idle mode to the tool that sends it automatically.

Exclusions, even when searching autonomously: firmware upgrade/DFU, factory reset, pairing list, device name, and anything the decompiled code labels as such. Only blind sweeps of unknown IDs within features you have identified as power/system related, never across all features. If the headset becomes unresponsive outside the success test, stop and report.
