#!/usr/bin/env python3
"""Small adb UI driver (plan Phase 3.4). All adb I/O goes through subprocess
with bytes, so PowerShell redirection and Git Bash path rewriting can't
corrupt anything.

    python tools/adbui.py dump [--save FILE.xml]
    python tools/adbui.py find [--text RE] [--desc RE] [--class RE] [--rid RE]
    python tools/adbui.py tap  [--text RE] [--desc RE] [--class RE] [--rid RE] [--nth N]
    python tools/adbui.py tap-xy X Y
    python tools/adbui.py wait [--text RE] [--desc RE] ... [--timeout S]
    python tools/adbui.py swipe-seek FRACTION [--text/--desc/--class/--rid RE] [--nth N]
    python tools/adbui.py shot OUT.png
    python tools/adbui.py launch PKG
    python tools/adbui.py stop PKG

Regexes are case-insensitive `search` matches. SCP (Flutter) exposes labels as
content-desc, so prefer --desc there. Dumps and screenshots can hold personal
data: save them under %USERPROFILE%\\m4-private only.
"""
import argparse
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ADB = "adb"


def adb(*args, timeout=60, check=True):
    p = subprocess.run([ADB, *args], capture_output=True, timeout=timeout)
    if check and p.returncode != 0:
        raise RuntimeError("adb %s failed: %s" % (" ".join(args), p.stderr.decode("utf-8", "replace").strip()))
    return p.stdout


def dump_xml():
    out = adb("exec-out", "uiautomator", "dump", "/dev/tty", check=False)
    i = out.find(b"<?xml")
    j = out.rfind(b"</hierarchy>")
    if i < 0 or j < 0:
        adb("shell", "uiautomator", "dump", "/sdcard/m4_ui.xml")
        out = adb("exec-out", "cat", "/sdcard/m4_ui.xml")
        i, j = out.find(b"<?xml"), out.rfind(b"</hierarchy>")
        if i < 0 or j < 0:
            raise RuntimeError("uiautomator dump returned no hierarchy")
    return out[i:j + len(b"</hierarchy>")]


_BOUNDS = re.compile(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]")


def parse_nodes(xml_bytes):
    root = ET.fromstring(xml_bytes)
    nodes = []
    for el in root.iter("node"):
        a = dict(el.attrib)
        m = _BOUNDS.match(a.get("bounds", ""))
        if m:
            x1, y1, x2, y2 = map(int, m.groups())
            a["_box"] = (x1, y1, x2, y2)
            a["_center"] = ((x1 + x2) // 2, (y1 + y2) // 2)
        nodes.append(a)
    return nodes


def find(nodes, text=None, desc=None, cls=None, rid=None):
    out = []
    for n in nodes:
        if text and not re.search(text, n.get("text", ""), re.I):
            continue
        if desc and not re.search(desc, n.get("content-desc", ""), re.I):
            continue
        if cls and not re.search(cls, n.get("class", ""), re.I):
            continue
        if rid and not re.search(rid, n.get("resource-id", ""), re.I):
            continue
        if "_center" in n:
            out.append(n)
    return out


def describe(i, n):
    flags = [k for k in ("checked", "selected", "enabled", "focused") if n.get(k) == "true"]
    return "#%d %s text=%r desc=%r rid=%r bounds=%s %s" % (
        i, n.get("class", "").split(".")[-1], n.get("text", ""), n.get("content-desc", ""),
        n.get("resource-id", ""), n.get("bounds", ""), ",".join(flags))


def tap_xy(x, y):
    adb("shell", "input", "tap", str(int(x)), str(int(y)))


def tap(text=None, desc=None, cls=None, rid=None, nth=0):
    hits = find(parse_nodes(dump_xml()), text, desc, cls, rid)
    if len(hits) <= nth:
        raise LookupError("no node #%d matches (found %d)" % (nth, len(hits)))
    n = hits[nth]
    tap_xy(*n["_center"])
    return n


def wait(text=None, desc=None, cls=None, rid=None, timeout=15.0):
    end = time.time() + timeout
    while time.time() < end:
        hits = find(parse_nodes(dump_xml()), text, desc, cls, rid)
        if hits:
            return hits
        time.sleep(0.7)
    return []


def seek_point(box, fraction, pad=None):
    """Point inside a horizontal slider's bounds for 0..1 (Android SeekBar pads ~ its thumb)."""
    x1, y1, x2, y2 = box
    if pad is None:
        pad = min(56, (x2 - x1) // 10)
    x = x1 + pad + fraction * ((x2 - x1) - 2 * pad)
    return int(round(x)), (y1 + y2) // 2


def swipe_seek(node, fraction):
    x1, y1, x2, y2 = node["_box"]
    tx, ty = seek_point(node["_box"], fraction)
    cx = (x1 + x2) // 2
    adb("shell", "input", "swipe", str(cx), str(ty), str(tx), str(ty), "400")
    return tx, ty


def shot(path):
    png = adb("exec-out", "screencap", "-p")
    if not png.startswith(b"\x89PNG"):
        raise RuntimeError("screencap did not return a PNG")
    with open(path, "wb") as fh:
        fh.write(png)


def launch(pkg):
    adb("shell", "monkey", "-p", pkg, "-c", "android.intent.category.LAUNCHER", "1")


def stop(pkg):
    adb("shell", "am", "force-stop", pkg)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("cmd")
    ap.add_argument("args", nargs="*")
    ap.add_argument("--text")
    ap.add_argument("--desc")
    ap.add_argument("--class", dest="cls")
    ap.add_argument("--rid")
    ap.add_argument("--nth", type=int, default=0)
    ap.add_argument("--timeout", type=float, default=15.0)
    ap.add_argument("--save")
    a = ap.parse_args()
    sel = dict(text=a.text, desc=a.desc, cls=a.cls, rid=a.rid)
    if a.cmd == "dump":
        x = dump_xml()
        if a.save:
            with open(a.save, "wb") as fh:
                fh.write(x)
        for i, n in enumerate(parse_nodes(x)):
            if n.get("text") or n.get("content-desc") or n.get("clickable") == "true":
                print(describe(i, n))
    elif a.cmd == "find":
        for i, n in enumerate(find(parse_nodes(dump_xml()), **sel)):
            print(describe(i, n))
    elif a.cmd == "tap":
        n = tap(nth=a.nth, **sel)
        print("tapped", describe(a.nth, n))
    elif a.cmd == "tap-xy":
        tap_xy(int(a.args[0]), int(a.args[1]))
    elif a.cmd == "wait":
        hits = wait(timeout=a.timeout, **sel)
        for i, n in enumerate(hits):
            print(describe(i, n))
        return 0 if hits else 1
    elif a.cmd == "swipe-seek":
        hits = find(parse_nodes(dump_xml()), **sel)
        if len(hits) <= a.nth:
            print("no slider found")
            return 1
        print("swiped to", swipe_seek(hits[a.nth], float(a.args[0])))
    elif a.cmd == "shot":
        shot(a.args[0])
        print("saved", a.args[0])
    elif a.cmd == "launch":
        launch(a.args[0])
    elif a.cmd == "stop":
        stop(a.args[0])
    else:
        ap.error("unknown command %s" % a.cmd)
    return 0


if __name__ == "__main__":
    sys.exit(main())
