# Prompt for the Windows Claude Code session

Read CLAUDE.md and docs/ first; everything known so far is there.

Step 1: build a Windows tool that connects to my MOMENTUM 4 over its GAIA channel, sends commands, decodes the replies and logs every frame. Verify it with the known auto-off GET.

Step 2: use that tool to find a command that makes the headset switch off for real (like the case or the button). Work autonomously, using the decompiled app and the captures as your map rather than guessing. When a command works, add an idle mode to the tool that sends it automatically.

Guardrails:
- Read-only commands you may send freely. Any command that writes or changes state: propose it with your evidence and wait for my OK, one at a time.
- Never touch firmware upgrade, factory reset, pairing list or device name. No brute-force sweeps of unknown command IDs.
- Max one command per second. If the headset stops responding, stop and tell me.
- Keep docs/protocol.md up to date, commit and push after each step.
