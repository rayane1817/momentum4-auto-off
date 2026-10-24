#!/usr/bin/env python3
"""Drop a time marker on the phone clock (plan Phase 3.5).

    python tools/mark.py LABEL [--session s2] [--dir PATH]

Runs `adb shell "log -t M4MARK LABEL; date +%s.%N; date +%z"` and appends
{label, phone_epoch, pc_epoch, tz_offset_s} to
%USERPROFILE%\\m4-private\\<session>\\markers.jsonl. btsnoop and logcat use the
phone clock; pc_epoch gives the offset for PC-client logs. tz_offset_s is
needed because Android writes btsnoop timestamps as local time.
"""
import argparse
import json
import os
import re
import subprocess
import sys
import time


def parse_tz(z):
    m = re.match(r"([+-])(\d\d)(\d\d)", z.strip())
    if not m:
        raise ValueError("bad tz %r" % z)
    s = int(m.group(2)) * 3600 + int(m.group(3)) * 60
    return -s if m.group(1) == "-" else s


def parse_out(text):
    lines = [l.strip() for l in text.splitlines() if l.strip()]
    epoch = float(lines[-2])
    return epoch, parse_tz(lines[-1])


def mark(label, path):
    if not re.fullmatch(r"[A-Za-z0-9_.-]+", label):
        raise ValueError("label must be [A-Za-z0-9_.-]+")
    pc0 = time.time()
    p = subprocess.run(["adb", "shell", "log -t M4MARK %s; date +%%s.%%N; date +%%z" % label],
                       capture_output=True, timeout=20)
    pc1 = time.time()
    if p.returncode != 0:
        raise RuntimeError(p.stderr.decode("utf-8", "replace"))
    phone, tz = parse_out(p.stdout.decode("ascii", "replace"))
    rec = {"label": label, "phone_epoch": phone, "pc_epoch": (pc0 + pc1) / 2, "tz_offset_s": tz,
           "adb_rtt_s": round(pc1 - pc0, 3)}
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "a", encoding="utf-8") as fh:
        fh.write(json.dumps(rec) + "\n")
    return rec


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("label")
    ap.add_argument("--session", default=os.environ.get("M4_SESSION", "s1"))
    ap.add_argument("--dir", default=None)
    a = ap.parse_args()
    d = a.dir or os.path.join(os.path.expanduser("~"), "m4-private", a.session)
    rec = mark(a.label, os.path.join(d, "markers.jsonl"))
    print(json.dumps(rec))
    return 0


if __name__ == "__main__":
    sys.exit(main())
