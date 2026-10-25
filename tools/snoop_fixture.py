#!/usr/bin/env python3
"""Synthetic btsnoop (H4, datalink 1002) writer for tests. No real data.

Builds just enough HCI/ACL/L2CAP/RFCOMM for tools/btsnoop_parse.py and
tools/gaia_annotate.py: ACL connect/disconnect, remote name, L2CAP channel
setup, RFCOMM SABM/UA/UIH (with and without credit byte, 1- and 2-byte
length), ACL fragmentation, and GAIA v3 frames.
"""
import struct

EPOCH_DELTA = 0x00DCDDB30F2F8000


def mac_bytes(mac):
    return bytes(int(x, 16) for x in reversed(mac.split(":")))


def gaia(cmd, payload=b"", from_headset=False, vendor=0x0495):
    return bytes([0xFF, 0x04 if from_headset else 0x03, (len(payload) >> 8) & 0xFF, len(payload) & 0xFF,
                  vendor >> 8, vendor & 0xFF, cmd >> 8, cmd & 0xFF]) + bytes(payload)


def rfcomm(dlci, ctrl, data=b"", credit=None):
    addr = (dlci << 2) | 0x02 | 0x01
    n = len(data)
    ln = bytes([(n << 1) | 1]) if n < 128 else struct.pack("<H", n << 1)
    cr = bytes([credit]) if credit is not None else b""
    return bytes([addr, ctrl]) + ln + cr + bytes(data) + b"\x00"   # trailing FCS (ignored by the parser)


SABM, UA, UIH, UIH_PF = 0x3F, 0x73, 0xEF, 0xFF


class Writer:
    def __init__(self, tz_offset_s=0):
        self.recs = []
        self.tz = tz_offset_s      # Android writes local time: raw = UTC + gmtoff

    def rec(self, t, sent, data, cmd_evt=False):
        flags = (0 if sent else 1) | (2 if cmd_evt else 0)
        self.recs.append((t, flags, bytes(data)))

    def save(self, path):
        with open(path, "wb") as f:
            f.write(b"btsnoop\x00" + struct.pack(">II", 1, 1002))
            for t, flags, data in sorted(self.recs, key=lambda r: r[0]):
                ts = int(round((t + self.tz) * 1e6)) + EPOCH_DELTA
                f.write(struct.pack(">IIIIq", len(data), len(data), flags, 0, ts))
                f.write(data)

    # ---- HCI events
    def conn_complete(self, t, handle, mac):
        p = bytes([0]) + struct.pack("<H", handle) + mac_bytes(mac) + bytes([1, 0])
        self.rec(t, False, bytes([4, 0x03, len(p)]) + p, True)

    def disconnect(self, t, handle, reason=0x13):
        p = bytes([0]) + struct.pack("<H", handle) + bytes([reason])
        self.rec(t, False, bytes([4, 0x05, len(p)]) + p, True)

    def remote_name(self, t, mac, name):
        p = bytes([0]) + mac_bytes(mac) + name.encode().ljust(248, b"\x00")
        self.rec(t, False, bytes([4, 0x07, len(p) & 0xFF]) + p, True)

    # ---- ACL / L2CAP
    def acl(self, t, sent, handle, l2, split=None):
        parts = [l2] if not split else [l2[:split], l2[split:]]
        for i, part in enumerate(parts):
            pb = 2 if i == 0 else 1
            self.rec(t + i * 0.0001, sent, bytes([2]) + struct.pack("<HH", handle | (pb << 12), len(part)) + part)

    @staticmethod
    def l2(cid, payload):
        return struct.pack("<HH", len(payload), cid) + bytes(payload)

    def l2cap_connect(self, t, handle, psm, scid, dcid, ident=1):
        req = bytes([0x02, ident]) + struct.pack("<H", 4) + struct.pack("<HH", psm, scid)
        self.acl(t, True, handle, self.l2(1, req))
        rsp = bytes([0x03, ident]) + struct.pack("<H", 8) + struct.pack("<HHHH", dcid, scid, 0, 0)
        self.acl(t + 0.001, False, handle, self.l2(1, rsp))


def build_fixture(dir_path, tz_offset_s=3600, t0=1_800_000_000.0):
    """Write fixture.log.last + fixture.log and markers.jsonl into dir_path.
    Returns a dict of expected decoder results."""
    import json
    import os
    # fake addresses, built at runtime so the commit leak check stays meaningful
    M4 = ":".join(["AA", "BB", "CC", "00", "00", "01"])
    OTHER = ":".join(["AA", "BB", "CC", "00", "00", "02"])
    w1 = Writer(tz_offset_s)
    H, LCID, RCID = 0x0001, 0x0040, 0x0041
    t = t0
    w1.conn_complete(t, H, M4)
    w1.remote_name(t - 2.0, OTHER, "Somebody's Phone")
    w1.l2cap_connect(t + 0.2, H, 0x0003, LCID, RCID)
    tx = lambda tt, rf, split=None: w1.acl(tt, True, H, Writer.l2(RCID, rf), split)
    rx = lambda tt, rf, split=None: w1.acl(tt, False, H, Writer.l2(LCID, rf), split)
    tx(t + 0.3, rfcomm(0, SABM)); rx(t + 0.31, rfcomm(0, UA))
    tx(t + 0.4, rfcomm(30, SABM)); rx(t + 0.41, rfcomm(30, UA))
    tx(t + 0.42, rfcomm(20, UIH, b"AT+BRSF=1023\r"))              # HFP on another DLCI: not GAIA
    # connect burst (dropped)
    tx(t + 0.5, rfcomm(30, UIH_PF, gaia(0x1A05), credit=1))
    rx(t + 0.55, rfcomm(30, UIH, gaia(0x1B05, b"\x01", True)))
    tx(t + 0.6, rfcomm(30, UIH, gaia(0x0007, b"\x0D")))
    rx(t + 0.65, rfcomm(30, UIH, gaia(0x0107, b"", True)))
    tx(t + 0.7, rfcomm(30, UIH, gaia(0x0603)))
    rx(t + 0.75, rfcomm(30, UIH, gaia(0x0703, b"\x3C", True)), split=5)   # ACL fragmentation
    rx(t + 0.8, rfcomm(30, UIH, gaia(0x0683, b"\x3C", True)))            # notif: kept
    tx(t + 0.85, rfcomm(30, UIH, gaia(0x1401, b"\x00")))
    rx(t + 0.9, rfcomm(30, UIH, gaia(0x1501, b"\x00Somebody's Phone\x00", True)))
    # action 1: anti-wind Max, SET split over two UIH frames
    t1 = t + 5.0
    setf = gaia(0x1A00, b"\x01\x02")
    tx(t1, rfcomm(30, UIH, setf[:5])); tx(t1 + 0.01, rfcomm(30, UIH, setf[5:]))
    rx(t1 + 0.05, rfcomm(30, UIH, gaia(0x1B00, b"", True)))
    rx(t1 + 0.06, rfcomm(30, UIH, gaia(0x1A81, b"\x01\x02\x02\x00\x03\x00", True)))
    tx(t1 + 0.5, rfcomm(30, UIH, gaia(0x1A01)))                         # GET outside a burst: kept
    rx(t1 + 0.55, rfcomm(30, UIH, gaia(0x1B01, b"\x01\x02\x02\x00\x03\x00", True)))
    long_payload = bytes(range(150))                                     # 2-byte RFCOMM length, ascii run
    rx(t1 + 0.7, rfcomm(30, UIH, gaia(0x2081, long_payload, True)))
    w1.disconnect(t1 + 2.0, H)
    # reconnect: new handle, GAIA now on DLCI 3
    H2 = 0x0002
    t2 = t + 20.0
    w1.conn_complete(t2, H2, M4)
    w1.l2cap_connect(t2 + 0.1, H2, 0x0003, LCID, RCID, ident=2)
    tx2 = lambda tt, rf: w1.acl(tt, True, H2, Writer.l2(RCID, rf))
    rx2 = lambda tt, rf: w1.acl(tt, False, H2, Writer.l2(LCID, rf))
    tx2(t2 + 0.2, rfcomm(3, SABM)); rx2(t2 + 0.21, rfcomm(3, UA))
    tx2(t2 + 0.3, rfcomm(3, UIH, gaia(0x1A05))); rx2(t2 + 0.31, rfcomm(3, UIH, gaia(0x1B05, b"\x01", True)))
    tx2(t2 + 0.4, rfcomm(3, UIH, gaia(0x0806))); rx2(t2 + 0.41, rfcomm(3, UIH, gaia(0x0906, b"\x02", True)))
    tx2(t2 + 0.5, rfcomm(3, UIH, gaia(0x2009)))                         # unknown request: kept even in burst
    rx2(t2 + 0.51, rfcomm(3, UIH, gaia(0x2109, b"\x01", True)))
    t3 = t + 30.0
    tx2(t3, rfcomm(3, UIH, gaia(0x1A04, b"\x00")))
    rx2(t3 + 0.05, rfcomm(3, UIH, gaia(0x1B04, b"", True)))
    rx2(t3 + 0.06, rfcomm(3, UIH, gaia(0x1A85, b"\x00", True)))
    w1.save(os.path.join(dir_path, "fixture.log.last"))

    # second file: capture started mid-connection (no L2CAP setup seen)
    w2 = Writer(tz_offset_s)
    H3, CID3 = 0x0003, 0x0045
    t4 = t + 40.0
    w2.acl(t4, True, H3, Writer.l2(CID3 + 1, rfcomm(3, UIH, gaia(0x0805, b"\x03"))))
    w2.acl(t4 + 0.05, False, H3, Writer.l2(CID3, rfcomm(3, UIH, gaia(0x0905, b"", True))))
    w2.acl(t4 + 0.06, False, H3, Writer.l2(CID3, rfcomm(3, UIH, gaia(0x0886, b"\x03", True))))
    w2.save(os.path.join(dir_path, "fixture.log"))

    with open(os.path.join(dir_path, "markers.jsonl"), "w", encoding="utf-8") as fh:
        for label, tt in [("session_start", t - 1), ("antiwind_max_begin", t1 - 0.5), ("antiwind_max_end", t1 + 1.0),
                          ("anc_off_begin", t3 - 0.5), ("anc_off_end", t3 + 1), ("session_end", t4 + 1)]:
            fh.write(json.dumps({"label": label, "phone_epoch": tt, "pc_epoch": tt + 0.2,
                                 "tz_offset_s": tz_offset_s}) + "\n")
    return {"macs": [M4, OTHER], "name": "Somebody's Phone",
            "writes": ["1A00 [01 02]", "1A04 [00]", "0805 [03]"],
            "unknown": ["2009"], "dlcis": {30, 3}}
