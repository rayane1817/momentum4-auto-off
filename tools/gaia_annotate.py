#!/usr/bin/env python3
"""Decode btsnoop captures to an annotated GAIA timeline (plan Phase 3.3).

    python tools/gaia_annotate.py FILE [FILE ...] [--markers markers.jsonl]
                                  [--redact] [--out DIR] [--tz-offset auto|SECONDS]
    python tools/gaia_annotate.py --selftest

- Merges several btsnoop files (.log.last + .log) by time, prints first/last record.
- Finds the GAIA channel BY CONTENT: any RFCOMM DLCI whose payloads start with
  FF 03/04 and carry vendor 04 95 or 00 1D. Never by a fixed DLCI.
- Splits into connect blocks (SABM on a GAIA DLCI, first GAIA frame after an ACL
  connect/disconnect). Drops each block's connect burst (known GETs, 0007
  registrations and their replies) until 2 s of quiet or the next marker.
  SETs and unknown commands are always kept.
- Labels every frame from tools/m4_commands.py. Unknown ids print as UNKNOWN
  (a finding, never something to send).
- Pairs each SET with its response and notifications.
- Aligns markers (markers.jsonl from tools/mark.py, phone epoch).
- --redact: MACs -> <M4>/<DEVn>, drops remote names and 1401 payloads, replaces
  payloads holding 8+ printable ASCII chars (except the model, 1306) with <ascii:n>.
Output with --out: decoded.md (redacted, safe to commit) and decoded_full.txt (private).

Clock: Android writes btsnoop timestamps as local time (UTC + gmtoff). The
phone's offset comes from the markers (tz_offset_s) or --tz-offset.
"""
import argparse
import datetime
import json
import os
import re
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import btsnoop_parse  # noqa: E402
import m4_commands as M4  # noqa: E402

VENDORS = (M4.SENNHEISER, M4.QUALCOMM)
BURST_QUIET_S = 2.0
PAIR_WINDOW_S = 2.0
MAC_RE = re.compile(r"([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}")


# ------------------------------------------------------------ extraction

def parse_rfcomm(payload):
    """(dlci, kind, data) for one RFCOMM frame, or None. kind: UIH/SABM/UA/DISC/DM."""
    if len(payload) < 3:
        return None
    dlci, ctrl = payload[0] >> 2, payload[1]
    if payload[2] & 1:
        plen, off = payload[2] >> 1, 3
    else:
        if len(payload) < 4:
            return None
        plen, off = (payload[2] >> 1) | (payload[3] << 7), 4
    ftype = ctrl & 0xEF
    if ftype == 0xEF:
        if ctrl & 0x10 and dlci != 0:
            off += 1
        return dlci, "UIH", bytes(payload[off:off + plen])
    names = {0x2F: "SABM", 0x63: "UA", 0x0F: "DM", 0x43: "DISC"}
    return dlci, names.get(ftype, "?"), b""


def looks_gaia(data):
    return (len(data) >= 6 and data[0] == 0xFF and data[1] in (0x03, 0x04)
            and ((data[4] << 8) | data[5]) in VENDORS)


def extract(paths):
    """Returns dict with uih [(raw_t, dir, addr, dlci, data)], ctl events, names, ranges."""
    uih, ctl, names, ranges = [], [], {}, []
    for path in paths:
        times = [t for t, _, _ in btsnoop_parse.records(path)]
        if times:
            ranges.append((path, times[0].timestamp(), times[-1].timestamp(), len(times)))
        st = btsnoop_parse.run(path)
        names.update(st.names)
        for t, d, addr, dlci, data in st.rfcomm:
            uih.append((t.timestamp(), d, addr, dlci, data))
        for t, d, addr, handle, cid, payload in st.unknown:
            r = parse_rfcomm(payload)
            if r and r[1] == "UIH" and r[0] != 0 and r[2]:
                uih.append((t.timestamp(), d, addr, r[0], r[2]))
        for t, ev in st.events:
            m = re.match(r"RFCOMM (SABM|UA|DISC|DM) dlci=(\d+) (->|<-) (\S+)", ev)
            if m:
                ctl.append((t.timestamp(), m.group(1), m.group(4), int(m.group(2))))
                continue
            m = re.match(r"ACL (connect|disconnect) handle=\S+ (\S+)", ev)
            if m:
                ctl.append((t.timestamp(), "ACL_" + m.group(1), m.group(2), None))
    # .log.last and .log can overlap: dedupe identical records
    uih = sorted(set(uih), key=lambda r: r[0])
    ctl = sorted(set(ctl), key=lambda r: r[0])
    return {"uih": uih, "ctl": ctl, "names": names, "ranges": ranges}


def gaia_frames(uih):
    """Find GAIA channels by content and reassemble frames per (addr, dlci, dir)."""
    hits = {}
    for _, _, addr, dlci, data in uih:
        if looks_gaia(data):
            hits[(addr, dlci)] = hits.get((addr, dlci), 0) + 1
    chans = set(hits)
    bufs, frames = {}, []
    for t, d, addr, dlci, data in uih:
        if (addr, dlci) not in chans:
            continue
        key = (addr, dlci, d)
        buf = bufs.setdefault(key, bytearray())
        buf += data
        i = 0
        while True:
            while i < len(buf) and buf[i] != 0xFF:
                i += 1
            if len(buf) - i < 8:
                break
            plen = (buf[i + 2] << 8) | buf[i + 3]
            if len(buf) - i < 8 + plen:
                break
            frames.append({"t": t, "dir": d, "addr": addr, "dlci": dlci, "ver": buf[i + 1],
                           "vendor": (buf[i + 4] << 8) | buf[i + 5], "cmd": (buf[i + 6] << 8) | buf[i + 7],
                           "payload": bytes(buf[i + 8:i + 8 + plen])})
            i += 8 + plen
        del buf[:i]
    return sorted(chans), frames


# -------------------------------------------------------------- analysis

def is_burst_kind(f):
    """Frames dropped inside a connect burst: known GETs, 0007, and their replies."""
    if f["vendor"] != M4.SENNHEISER:
        return False
    b = M4.base(f["cmd"])
    e = M4.COMMANDS.get(b)
    if not e or e["kind"] not in ("get", "register"):
        return False
    return M4.ftype(f["cmd"]) in (0, 2, 3)


def blocks_and_filter(frames, ctl, chans, marker_times):
    gaia_dlcis = {(a, d) for a, d in chans}
    starts = [t for t, kind, addr, dlci in ctl if kind == "SABM" and (addr, dlci) in gaia_dlcis]
    acl = [t for t, kind, addr, _ in ctl if kind.startswith("ACL_")]
    kept, dropped, nblocks = [], 0, 0
    burst, last_t, last_ch = False, None, None
    for f in frames:
        t = f["t"]
        new_block = last_t is None or (f["addr"], f["dlci"]) != last_ch
        if last_t is not None:
            if any(last_t < s <= t for s in starts) or any(last_t < a <= t for a in acl):
                new_block = True
        last_ch = (f["addr"], f["dlci"])
        if new_block:
            nblocks += 1
            burst = True
        elif burst and (t - last_t >= BURST_QUIET_S or any(last_t < m <= t for m in marker_times)):
            burst = False
        last_t = t
        if burst and is_burst_kind(f):
            dropped += 1
            continue
        kept.append(f)
    return kept, dropped, nblocks


def pair_writes(kept):
    writes = []
    for i, f in enumerate(kept):
        if M4.ftype(f["cmd"]) != 0 or f["dir"] != "->":
            continue
        e = M4.COMMANDS.get(f["cmd"]) if f["vendor"] == M4.SENNHEISER else None
        if e and e["kind"] in ("get", "register"):
            continue
        reply, notifs = None, []
        for g in kept[i + 1:]:
            if g["t"] - f["t"] > PAIR_WINDOW_S:
                break
            if g["dir"] != "<-" or g["vendor"] != f["vendor"]:
                continue
            if reply is None and M4.base(g["cmd"]) == f["cmd"] and M4.ftype(g["cmd"]) in (2, 3):
                reply = g
            elif M4.ftype(g["cmd"]) == 1:
                notifs.append(g)
        writes.append({"frame": f, "reply": reply, "notifs": notifs})
    return writes


def unknown_ids(frames):
    out = set()
    for f in frames:
        if M4.label(f["vendor"], f["cmd"]) == "UNKNOWN":
            out.add((f["vendor"], M4.base(f["cmd"])))
    return sorted(out)


def load_markers(path):
    if not path:
        return []
    out = []
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if line:
                out.append(json.loads(line))
    return out


# ---------------------------------------------------------------- output

class Redactor:
    def __init__(self, gaia_addrs, enabled):
        self.enabled = enabled
        self.map = {}
        for i, a in enumerate(sorted(gaia_addrs)):
            self.map[a] = "<M4>" if i == 0 else "<M4-%d>" % (i + 1)
        self.n = 0

    def addr(self, a):
        if not self.enabled:
            return a
        if a not in self.map:
            self.n += 1
            self.map[a] = "<DEV%d>" % self.n
        return self.map[a]

    def payload(self, f):
        p = f["payload"]
        hx = " ".join("%02X" % b for b in p)
        if not self.enabled:
            return hx
        if f["vendor"] == M4.SENNHEISER and M4.base(f["cmd"]) == 0x1401 and M4.ftype(f["cmd"]) != 0:
            return "<1401 payload dropped>"
        if f["vendor"] == M4.SENNHEISER and M4.base(f["cmd"]) == 0x1206:
            return hx
        if re.search(rb"[\x20-\x7e]{8,}", p):
            return "<ascii:%d>" % len(p)
        return hx

    def text(self, s):
        if not self.enabled:
            return s
        return MAC_RE.sub(lambda m: self.addr(m.group(0).upper()), s)


def describe(f, red):
    t = M4.ftype(f["cmd"])
    lab = M4.label(f["vendor"], f["cmd"])
    s = "%04X:%04X [%s] %s" % (f["vendor"], f["cmd"], red.payload(f), lab)
    if t in (1, 2) and f["vendor"] == M4.SENNHEISER:
        d = M4.decode(M4.base(f["cmd"]), f["payload"])
        if d and not (red.enabled and M4.base(f["cmd"]) == 0x1401):
            s += " => " + d
    if t == 3 and f["payload"]:
        s += " => error %02X %s" % (f["payload"][0], M4.ERROR_CODES.get(f["payload"][0], ""))
    return s


def render(ex, chans, kept, dropped, nblocks, writes, unknown, markers, tz, red):
    fmt = lambda raw: datetime.datetime.fromtimestamp(raw, datetime.timezone.utc).strftime("%H:%M:%S.%f")[:-3]
    real = lambda raw: raw - tz          # btsnoop raw (local) -> real epoch
    marks = sorted(((m["phone_epoch"] + tz, m["label"]) for m in markers), key=lambda x: x[0])
    L = []
    L.append("# GAIA decode%s" % (" (redacted)" if red.enabled else " (FULL, private)"))
    L.append("")
    L.append("Times are phone local wall clock (btsnoop raw); tz offset used: %+d s." % tz)
    L.append("")
    L.append("## Inputs")
    for path, a, b, n in ex["ranges"]:
        L.append("- `%s`: %d records, first %s, last %s" % (os.path.basename(path), n, fmt(a), fmt(b)))
    if markers:
        first_rec = min(a for _, a, _, _ in ex["ranges"]) if ex["ranges"] else None
        first_mark = marks[0][0]
        ok = first_rec is not None and first_rec <= first_mark
        L.append("- first marker `%s` at %s: %s" % (marks[0][1], fmt(first_mark),
                                                  "capture starts before it (ok)" if ok else
                                                  "CAPTURE STARTS AFTER IT (ring buffer wrapped?)"))
    L.append("- GAIA channels (by content): %s" % ", ".join("%s dlci %d" % (red.addr(a), d) for a, d in chans))
    L.append("- connect blocks: %d; connect-burst frames dropped: %d; frames kept: %d" % (nblocks, dropped, len(kept)))
    if not red.enabled and ex["names"]:
        L.append("- remote names: %s" % ", ".join("%s=%s" % kv for kv in sorted(ex["names"].items())))
    L.append("")
    L.append("## Writes (SET and unknown requests, with reply and notifications)")
    L.append("")
    L.append("| time | after marker | request | label | reply | notifications |")
    L.append("|---|---|---|---|---|---|")
    for w in writes:
        f = w["frame"]
        mk = [lab for mt, lab in marks if mt <= f["t"]]
        r = w["reply"]
        if r is None:
            rs = "none"
        elif M4.ftype(r["cmd"]) == 3:
            rs = "ERROR %s" % (" ".join("%02X" % b for b in r["payload"]))
        else:
            rs = "%04X [%s]" % (r["cmd"], red.payload(r))
        ns = "; ".join("%04X [%s]" % (n["cmd"], red.payload(n)) for n in w["notifs"]) or "-"
        L.append("| %s | %s | `%04X [%s]` | %s | %s | %s |" % (
            fmt(f["t"]), mk[-1] if mk else "-", f["cmd"], red.payload(f),
            M4.label(f["vendor"], f["cmd"]), rs, ns))
    L.append("")
    L.append("## UNKNOWN ids (findings only, never to probe)")
    L.append("")
    L.append(", ".join("%04X:%04X" % u for u in unknown) if unknown else "none")
    L.append("")
    L.append("## Timeline (connect bursts removed)")
    L.append("")
    L.append("```")
    mi = 0
    for f in kept:
        while mi < len(marks) and marks[mi][0] <= f["t"]:
            L.append("--- MARK %s (t=%s) ---" % (marks[mi][1], fmt(marks[mi][0])))
            mi += 1
        L.append("%s %s %s dlci=%d %s" % (fmt(f["t"]), f["dir"], red.addr(f["addr"]), f["dlci"], describe(f, red)))
    while mi < len(marks):
        L.append("--- MARK %s (t=%s) ---" % (marks[mi][1], fmt(marks[mi][0])))
        mi += 1
    L.append("```")
    return red.text("\n".join(L)) + "\n"


def annotate(paths, markers_path=None, tz_offset="auto"):
    markers = load_markers(markers_path)
    if tz_offset == "auto":
        tz = int(markers[0].get("tz_offset_s", 0)) if markers else 0
    else:
        tz = int(tz_offset)
    ex = extract(paths)
    chans, frames = gaia_frames(ex["uih"])
    marker_raw = [m["phone_epoch"] + tz for m in markers]
    kept, dropped, nblocks = blocks_and_filter(frames, ex["ctl"], chans, marker_raw)
    writes = pair_writes(kept)
    unknown = unknown_ids(frames)
    gaia_addrs = {a for a, _ in chans}
    res = {"chans": chans, "frames": frames, "kept": kept, "dropped": dropped, "blocks": nblocks,
           "writes": writes, "unknown": unknown, "tz": tz}
    res["redacted"] = render(ex, chans, kept, dropped, nblocks, writes, unknown, markers, tz,
                             Redactor(gaia_addrs, True))
    res["full"] = render(ex, chans, kept, dropped, nblocks, writes, unknown, markers, tz,
                         Redactor(gaia_addrs, False))
    return res


def selftest(verbose=True):
    import snoop_fixture
    with tempfile.TemporaryDirectory() as d:
        exp = snoop_fixture.build_fixture(d)
        res = annotate([os.path.join(d, "fixture.log.last"), os.path.join(d, "fixture.log")],
                       os.path.join(d, "markers.jsonl"))
    got_writes = ["%04X [%s]" % (w["frame"]["cmd"], " ".join("%02X" % b for b in w["frame"]["payload"]))
                  for w in res["writes"] if M4.label(0x0495, w["frame"]["cmd"]) != "UNKNOWN"]
    problems = []
    if got_writes != exp["writes"]:
        problems.append("writes %s != %s" % (got_writes, exp["writes"]))
    if {d for _, d in res["chans"]} != exp["dlcis"]:
        problems.append("dlcis %s" % res["chans"])
    if ["%04X" % c for _, c in res["unknown"]] != exp["unknown"]:
        problems.append("unknown %s" % res["unknown"])
    w0 = res["writes"][0]
    if not (w0["reply"] and w0["reply"]["cmd"] == 0x1B00 and w0["notifs"] and w0["notifs"][0]["cmd"] == 0x1A81):
        problems.append("1A00 pairing wrong")
    if res["blocks"] != 3:
        problems.append("blocks %d != 3" % res["blocks"])
    kept_cmds = [f["cmd"] for f in res["kept"]]
    for must_drop in (0x0007, 0x0107, 0x0603, 0x0703, 0x0806, 0x0906, 0x1401):
        if must_drop in kept_cmds:
            problems.append("burst frame %04X kept" % must_drop)
    for must_keep in (0x0683, 0x1A01, 0x1B01, 0x2009):
        if must_keep not in kept_cmds:
            problems.append("frame %04X dropped" % must_keep)
    red = res["redacted"]
    if MAC_RE.search(red):
        problems.append("MAC in redacted output")
    if exp["name"] in red or "Somebody" in red:
        problems.append("name in redacted output")
    if "<ascii:150>" not in red:
        problems.append("ascii run not redacted")
    if "MARK antiwind_max_begin" not in red or red.index("MARK antiwind_max_begin") > red.index("0495:1A00 [01 02]"):
        problems.append("marker not before the 1A00 write")
    if "CAPTURE STARTS AFTER" in red:
        problems.append("wrap warning on a capture that starts before the first marker")
    if exp["macs"][0] not in res["full"]:
        problems.append("full output lost the MAC")
    if verbose:
        print(red)
        print("SELFTEST", "OK" if not problems else "FAIL: " + "; ".join(problems))
    return problems


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("files", nargs="*")
    ap.add_argument("--markers")
    ap.add_argument("--redact", action="store_true", help="print the redacted version to stdout")
    ap.add_argument("--out", help="write decoded.md (redacted) and decoded_full.txt (private) here")
    ap.add_argument("--tz-offset", default="auto")
    ap.add_argument("--selftest", action="store_true")
    a = ap.parse_args()
    if a.selftest:
        return 1 if selftest() else 0
    if not a.files:
        ap.error("no input files")
    res = annotate(a.files, a.markers, a.tz_offset)
    if a.out:
        os.makedirs(a.out, exist_ok=True)
        with open(os.path.join(a.out, "decoded.md"), "w", encoding="utf-8") as fh:
            fh.write(res["redacted"])
        with open(os.path.join(a.out, "decoded_full.txt"), "w", encoding="utf-8") as fh:
            fh.write(res["full"])
        print("wrote %s and %s" % (os.path.join(a.out, "decoded.md"), os.path.join(a.out, "decoded_full.txt")))
    sys.stdout.write(res["redacted"] if (a.redact or not a.out) else "")
    return 0


if __name__ == "__main__":
    sys.exit(main())
