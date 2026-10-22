# Prompt for the Windows Claude Code session

Read CLAUDE.md and docs/ first; everything known so far is there.

Goal: find a command that makes my MOMENTUM 4 switch off for real (like the case or the button does), then build a Windows tool that sends it when the headset is idle. Work autonomously on discovery, using the decompiled app and the captures as your map rather than guessing.

Guardrails:
- Read-only commands you may send freely. Any command that writes or changes state: propose it with your evidence and wait for my OK, one at a time.
- Never touch firmware upgrade, factory reset, pairing list or device name. No brute-force sweeps of unknown command IDs.
- Max one command per second. If the headset stops responding, stop and tell me.
- Log every frame, keep docs/protocol.md up to date, commit and push after each step.
