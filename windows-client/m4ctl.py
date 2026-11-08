#!/usr/bin/env python3
"""m4ctl: M4 Control from the command line, for hotkeys and scripts.

    m4ctl status                 connection, battery, noise mode, EQ preset
    m4ctl battery                just the number (empty and exit 1 if unknown)
    m4ctl noise adaptive|custom|transparency|off|next
    m4ctl level 0-100            Custom level: 0 = full ANC, 100 = full transparency
    m4ctl eq next|prev|NAME      EQ preset (built-in or one you saved in the app)
    m4ctl prompts off|tones|voice

If M4 Control is running it sends through the app (its open link, or the tray's one-shot).
Otherwise m4ctl connects itself, sends and disconnects (about 1 to 2 s). Only documented
commands, all built by m4_logic. There is no power off here on purpose: that stays a button in
the app, after a confirm. Exit code 0 = done, 1 = failed, 2 = bad usage.
"""

import json
import os
import sys
import time
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import m4_logic as L  # noqa: E402

G = L.G
APP_DIR = os.path.join(os.environ.get("LOCALAPPDATA") or os.path.expanduser("~"), "M4Control")
NOISE_WORDS = {"adaptive": 0, "custom": 1, "off": 2, "transparency": 3, "transparent": 3}
PROMPT_WORDS = {"off": 0, "tones": 1, "voice": 2}


class Usage(Exception):
    pass


def parse(argv):
    """argv -> ("status"|"battery", None) or (what, value) for Core.quick. Raises Usage."""
    if not argv or argv[0] in ("-h", "--help", "help"):
        raise Usage(__doc__)
    cmd, args = argv[0].lower(), [a.lower() for a in argv[1:]]
    if cmd in ("status", "battery") and not args:
        return cmd, None
    if cmd == "noise" and len(args) == 1 and (args[0] in NOISE_WORDS or args[0] == "next"):
        return "noise", NOISE_WORDS.get(args[0], "next")
    if cmd == "level" and len(args) == 1 and args[0].isdigit() and 0 <= int(args[0]) <= 100:
        return "level", int(args[0])
    if cmd == "eq" and args:
        if args == ["next"] or args == ["prev"]:
            return "eq", 1 if args[0] == "next" else -1
        return "eq", " ".join(argv[1:])
    if cmd == "prompts" and len(args) == 1 and args[0] in PROMPT_WORDS:
        return "prompts", PROMPT_WORDS[args[0]]
    raise Usage("Unknown or incomplete command. Run m4ctl help.")


def next_noise(current):
    order = list(L.NOISE_ORDER)
    return order[(order.index(current) + 1) % len(order)] if current in order else 0


def settings():
    try:
        with open(os.path.join(APP_DIR, "settings.json"), encoding="utf-8") as f:
            s = json.load(f)
        return s if isinstance(s, dict) else {}
    except (OSError, ValueError):
        return {}


# ------------------------------------------------------------ through the app

def app_url():
    try:
        with open(os.path.join(APP_DIR, "running.json"), encoding="utf-8") as f:
            r = json.load(f)
        return "http://127.0.0.1:%d/api?t=%s" % (int(r["port"]), r["token"])
    except (OSError, ValueError, KeyError):
        return None


def call_app(url, payload):
    req = urllib.request.Request(url, data=json.dumps(payload).encode(), method="POST",
                                 headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=3) as r:
        return json.loads(r.read())


def via_app(url, what, value):
    if what in ("status", "battery"):
        return show(what, call_app(url, {"action": "status"})["status"])
    if what == "noise" and value == "next":
        value = next_noise(call_app(url, {"action": "status"})["status"].get("noise"))
    r = call_app(url, {"action": "quick", "what": what, "value": value})
    if not r.get("ok"):
        print(r.get("error") or "failed", file=sys.stderr)
        return 1
    print("sent" + ("" if r.get("linked") else " (the app connects, sends and lets go)"))
    return 0


# --------------------------------------------------------------- on our own

class FileLog(G.Logger):
    def _w(self, line):
        self.fh.write("%s %s\n" % (time.strftime("%H:%M:%S"), line))


def pick_address():
    devs = L.paired_devices()
    saved = settings().get("address")
    i = L.pick_default(devs, saved)
    return devs[i][1] if i is not None else (L.format_addr(G.default_addr() or "") or None)


def read_state(link):
    st = {}
    for fr, cmd in [(L.req(c), c) for c in (0x0603, 0x0602, 0x1A05, 0x1A01, 0x1A03, 0x0802)] + [(L.req(0x1003, 0), 0x1003)]:
        reply, _ = G.transact(link, fr, cmd, timeout=2.0, settle=0)
        if reply is not None:
            st.update(L.decode(reply))
    return st


def direct(what, value):
    addr = pick_address()
    if not addr:
        print("No paired headphones. Pair them in Windows Settings > Bluetooth.", file=sys.stderr)
        return 1
    log = FileLog(os.path.join(APP_DIR, "logs"))
    link = G.Link(addr, log)
    try:
        if not link.connect(timeout=6.0):
            print("Can't reach the headphones. Are they on?", file=sys.stderr)
            return 1
        st = read_state(link) if what in ("status", "battery", "eq") or value == "next" else {}
        noise = L.noise_mode_index(st["anc_on"], st.get("adaptive_on"), st.get("level")) if "anc_on" in st else None
        custom = settings().get("presets") or {}
        if what in ("status", "battery"):
            return show(what, {"conn": "connected", "battery": st.get("battery"), "charging": st.get("charging"),
                               "noise": noise, "eq": L.preset_name(st.get("eq", ()), custom), "level": st.get("level"),
                               "prompts": st.get("prompts")})
        if what == "noise":
            mode = next_noise(noise) if value == "next" else value
            frames = L.widget_noise_frames(mode)
        elif what == "level":
            frames = L.level_frames(value)
        elif what == "prompts":
            frames = L.prompts_frames(value)
        else:
            if isinstance(value, str):
                found = [g for n, g in L.preset_list(custom) if n.lower() == value.lower()]
                if not found:
                    print("No preset called %s" % value, file=sys.stderr)
                    return 1
                gains = found[0]
            else:
                gains = L.step_preset(L.preset_name(st.get("eq", ()), custom), value, custom)[1]
            frames = L.eq_curve_frames(gains)
        for f in frames:
            if not link.send(f):
                return 1
            time.sleep(0.06)
        time.sleep(0.4)
        print("sent")
        return 0
    except OSError as e:
        print("Link error: %s" % e, file=sys.stderr)
        return 1
    finally:
        link.close()


def show(what, st):
    if what == "battery":
        if st.get("battery") is None:
            return 1
        print(st["battery"])
        return 0
    noise = st.get("noise")
    print("headphones: %s" % st.get("conn", "?"))
    print("battery:    %s%s" % ("?" if st.get("battery") is None else "%d %%" % st["battery"],
                                ", charging" if st.get("charging") else ""))
    print("noise:      %s" % (L.NOISE_MODES[noise] if noise in (0, 1, 2, 3) else "?"))
    print("eq:         %s" % (st.get("eq") or "custom curve"))
    if st.get("prompts") is not None:
        print("prompts:    %s" % L.PROMPTS[st["prompts"]])
    return 0


def main(argv=None):
    try:
        what, value = parse(sys.argv[1:] if argv is None else argv)
    except Usage as e:
        print(str(e).strip(), file=sys.stderr)
        return 2
    url = app_url()
    if url:
        try:
            return via_app(url, what, value)
        except (OSError, ValueError, KeyError):
            pass                                  # stale running.json: do it ourselves
    return direct(what, value)


if __name__ == "__main__":
    sys.exit(main())
