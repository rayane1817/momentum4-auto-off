#!/usr/bin/env python3
"""
MOMENTUM 4 GAIA v3 client over Bluetooth Classic RFCOMM.

Runs on Windows 10/11 and Linux with stock Python 3.9+, no pip packages.
Linux uses stdlib AF_BLUETOOTH. Windows has no AF_BLUETOOTH, so it uses
WinSock AF_BTH via ctypes and connects by the GAIA service UUID. pybluez
is used only for SDP channel lookup if it happens to be installed.

See docs/protocol.md for the protocol and the candidate list.
The headset must NOT be connected to any other GAIA app while this runs
(force stop Smart Control Plus; it holds the channel).

Commands:
    verify              connect and read the auto-off timer (sanity check)
    get-timer [N]       read timer N (0 = auto power-off, default 0)
    set-timer N SECONDS write timer N
    battery             read battery %
    raw HEX             send one raw frame, e.g. "FF 03 00 00 04 95 06 03"
    sweep               scoped blind sweep for a power-off command (see --features)

Common options:
    --addr AA:BB:CC:DD:EE:FF   headset MAC (default from capture)
    --channel N                force RFCOMM channel (skip SDP/sweep)
    --log DIR                  log directory (default ./logs)

Examples:
    python gaia_client.py verify
    python gaia_client.py raw "FF 03 00 00 04 95 06 03"
    python gaia_client.py sweep --features 3
"""

import argparse
import os
import socket
import struct
import sys
import time
from datetime import datetime

DEFAULT_ADDR = "80:C3:BA:4D:6A:24"   # Ryane's M4, from the HCI capture
DOC_CHANNEL = 15                     # GAIA RFCOMM channel seen in the capture
SENNHEISER = 0x0495
QUALCOMM = 0x001D

# ---------------------------------------------------------------- framing

def build(vendor, command, payload=b""):
    """FF 03 | len(u16 BE) | vendor(u16 BE) | command(u16 BE) | payload"""
    return bytes([0xFF, 0x03, (len(payload) >> 8) & 0xFF, len(payload) & 0xFF,
                  (vendor >> 8) & 0xFF, vendor & 0xFF,
                  (command >> 8) & 0xFF, command & 0xFF]) + bytes(payload)


class Frame:
    __slots__ = ("version", "vendor", "command", "payload")

    def __init__(self, version, vendor, command, payload):
        self.version, self.vendor, self.command, self.payload = version, vendor, command, payload

    @property
    def type(self):
        return (self.command >> 7) & 3      # 0 cmd, 1 notif, 2 resp, 3 error

    @property
    def base(self):
        return self.command & ~(3 << 7)     # command id with type bits cleared

    def __str__(self):
        t = ("cmd", "notif", "resp", "ERROR")[self.type]
        s = "%04X:%04X %s [%s]" % (self.vendor, self.command, t, hexstr(self.payload))
        if self.vendor == SENNHEISER and self.base == 0x0601 and self.type == 2 and len(self.payload) >= 3:
            timer = self.payload[0]
            secs = (self.payload[1] << 8) | self.payload[2]
            name = {0: "auto power-off", 1: "LIMBO"}.get(timer, "timer %d" % timer)
            s += "  => %s = %d s" % (name, secs)
        elif self.vendor == SENNHEISER and self.base == 0x0603 and self.type != 3 and self.payload:
            s += "  => battery %d %%" % self.payload[-1]
        elif self.type == 3:
            code = self.payload[0] if self.payload else -1
            meaning = {1: "not supported / unknown", 5: "invalid parameter"}.get(code, "")
            s += "  => refused (%s)" % meaning if meaning else "  => refused"
        return s


def parse_frames(buf):
    """Pull complete frames out of a bytes buffer; return (frames, leftover)."""
    frames, i = [], 0
    while True:
        while i < len(buf) and buf[i] != 0xFF:
            i += 1
        if len(buf) - i < 8:
            break
        plen = (buf[i + 2] << 8) | buf[i + 3]
        if len(buf) - i < 8 + plen:
            break
        vendor = (buf[i + 4] << 8) | buf[i + 5]
        command = (buf[i + 6] << 8) | buf[i + 7]
        frames.append(Frame(buf[i + 1], vendor, command, bytes(buf[i + 8:i + 8 + plen])))
        i += 8 + plen
    return frames, buf[i:]


def hexstr(b):
    return " ".join("%02X" % x for x in b)


def parse_hex(text):
    clean = "".join(c for c in text if c in "0123456789abcdefABCDEF")
    if len(clean) % 2:
        raise ValueError("odd number of hex digits")
    return bytes(int(clean[i:i + 2], 16) for i in range(0, len(clean), 2))

# ----------------------------------------------------------- safety guard

def blocked_reason(frame):
    """Return a reason string if this frame must never be sent, else None.
    Matches the exclusions in docs/protocol.md. Applied to every send."""
    if len(frame) < 8 or frame[0] != 0xFF:
        return None
    vendor = (frame[4] << 8) | frame[5]
    cmd = (frame[6] << 8) | frame[7]
    feature = cmd >> 9
    if vendor == SENNHEISER:
        if cmd == 0x0040:
            return "factory reset"
        if feature == 1:
            return "firmware upgrade / DFU mode (feature 1)"
        if cmd in (0x1403, 0x1405, 0x1406):
            return "deletes paired device(s)"
        if cmd == 0x0607 or 0x0609 <= cmd <= 0x06FF:
            return "reset commands next to power-off (per omarchy-momentum4 PROTOCOL.md)"
        if cmd == 0x1500:  # RenameDevice on some models; block the set form if seen
            return "device rename"
    if vendor == QUALCOMM and feature == 6:
        return "Qualcomm firmware upgrade (feature 6)"
    return None

# --------------------------------------------------------- RFCOMM transport
# Stock CPython exposes socket.AF_BLUETOOTH only on Linux (BlueZ). On Windows
# there is no AF_BLUETOOTH; RFCOMM must go through the native WinSock Bluetooth
# stack (AF_BTH / BTHPROTO_RFCOMM) via ctypes. The protocol and framing above
# are untouched; this only replaces how the byte stream is opened.

HAVE_NATIVE_BT = hasattr(socket, "AF_BLUETOOTH")
IS_WINDOWS = sys.platform.startswith("win")

if IS_WINDOWS:
    import ctypes
    import select as _select

    _AF_BTH = 32
    _BTHPROTO_RFCOMM = 0x0003
    _WSAEWOULDBLOCK = 10035
    _SOCKET = ctypes.c_uint64 if ctypes.sizeof(ctypes.c_void_p) == 8 else ctypes.c_uint32

    class _GUID(ctypes.Structure):
        _fields_ = [("Data1", ctypes.c_ulong),
                    ("Data2", ctypes.c_ushort),
                    ("Data3", ctypes.c_ushort),
                    ("Data4", ctypes.c_ubyte * 8)]

    class _SOCKADDR_BTH(ctypes.Structure):
        # ws2bth.h declares this under pshpack1: sizeof == 30, btAddr at
        # offset 2. Natural alignment (40 bytes) makes WinSock read padding
        # as the address and fail with WSA 10049.
        _pack_ = 1
        _fields_ = [("addressFamily", ctypes.c_ushort),
                    ("btAddr", ctypes.c_ulonglong),
                    ("serviceClassId", _GUID),
                    ("port", ctypes.c_ulong)]

    _ws2 = ctypes.windll.ws2_32
    _ws2.connect.argtypes = [_SOCKET, ctypes.c_void_p, ctypes.c_int]
    _ws2.connect.restype = ctypes.c_int
    _ws2.WSAGetLastError.restype = ctypes.c_int

    def _bt_addr_to_int(addr):
        """'80:C3:BA:4D:6A:24' -> 0x80C3BA4D6A24 (MAC octets, big-endian)."""
        parts = addr.split(":")
        if len(parts) != 6:
            raise ValueError("bad BT address: %r" % addr)
        val = 0
        for p in parts:
            val = (val << 8) | int(p, 16)
        return val

    # GAIA RFCOMM service UUID (docs/protocol.md). The MS stack resolves the
    # channel from this via SDP, which is both more robust than a raw channel
    # number (raw connect gives WSA 10049) and matches "find the channel
    # through SDP, don't hard-code it".
    _GAIA_UUID = "a2129ff3-081b-4c45-8afe-469d9c4842ec"

    def _make_guid(u):
        u = u.replace("-", "")
        g = _GUID()
        g.Data1 = int(u[0:8], 16)
        g.Data2 = int(u[8:12], 16)
        g.Data3 = int(u[12:16], 16)
        g.Data4 = (ctypes.c_ubyte * 8)(*bytes.fromhex(u[16:32]))
        return g

    def _try_connect(addr_int, guid, port, timeout):
        s = socket.socket(_AF_BTH, socket.SOCK_STREAM, _BTHPROTO_RFCOMM)
        sa = _SOCKADDR_BTH()
        sa.addressFamily = _AF_BTH
        sa.btAddr = addr_int
        if guid is not None:
            sa.serviceClassId = guid
        sa.port = port
        s.setblocking(False)
        rc = _ws2.connect(s.fileno(), ctypes.byref(sa), ctypes.sizeof(sa))
        if rc != 0:
            err = _ws2.WSAGetLastError()
            if err != _WSAEWOULDBLOCK:
                s.close()
                raise OSError("WSA %d" % err)
            _, w, _x = _select.select([], [s], [s], timeout)
            if not w:
                s.close()
                raise OSError("timed out after %.1fs" % timeout)
            soerr = s.getsockopt(socket.SOL_SOCKET, socket.SO_ERROR)
            if soerr != 0:
                s.close()
                raise OSError("SO_ERROR %d" % soerr)
        s.setblocking(True)
        return s

    def open_rfcomm(addr, channel, timeout):
        """Open a connected RFCOMM stream socket on Windows. Returns a normal
        Python socket object (send/recv/settimeout/close work regardless of
        address family). Connects by the GAIA service UUID first (OS resolves
        the channel via SDP), then falls back to the explicit channel number."""
        addr_int = _bt_addr_to_int(addr)
        errors = []
        try:
            return _try_connect(addr_int, _make_guid(_GAIA_UUID), 0, timeout)
        except OSError as e:
            errors.append("GAIA-UUID connect: %s" % e)
        if channel:
            try:
                return _try_connect(addr_int, None, channel, timeout)
            except OSError as e:
                errors.append("ch %d connect: %s" % (channel, e))
        raise OSError("; ".join(errors))

elif HAVE_NATIVE_BT:
    def open_rfcomm(addr, channel, timeout):
        s = socket.socket(socket.AF_BLUETOOTH, socket.SOCK_STREAM, socket.BTPROTO_RFCOMM)
        s.settimeout(timeout)
        s.connect((addr, channel))
        return s

else:
    def open_rfcomm(addr, channel, timeout):
        raise OSError("no RFCOMM transport on this platform (no AF_BLUETOOTH, not Windows)")

# ------------------------------------------------------------- connection

class Link:
    def __init__(self, addr, log, channel=None):
        self.addr = addr
        self.log = log
        self.forced_channel = channel
        self.channel = None
        self.sock = None
        self.rxbuf = bytearray()

    def _candidate_channels(self):
        if self.forced_channel:
            return [self.forced_channel]
        chans = []
        sdp = sdp_lookup(self.addr)
        if sdp:
            self.log.note("SDP found GAIA on channel %d" % sdp)
            chans.append(sdp)
        if DOC_CHANNEL not in chans:
            chans.append(DOC_CHANNEL)            # documented fallback
        chans += [c for c in range(1, 31) if c not in chans]   # last resort sweep
        return chans

    def connect(self, timeout=10.0):
        for ch in self._candidate_channels():
            try:
                s = open_rfcomm(self.addr, ch, timeout)
                s.settimeout(1.0)
                self.sock, self.channel, self.rxbuf = s, ch, bytearray()
                self.log.note("connected on RFCOMM channel %d" % ch)
                return True
            except OSError as e:
                if self.forced_channel:
                    self.log.note("connect ch %d failed: %s" % (ch, e))
        return False

    def send(self, frame):
        reason = blocked_reason(frame)
        if reason:
            self.log.tx(frame, blocked=reason)
            return False
        self.sock.sendall(frame)
        self.log.tx(frame)
        return True

    def read_frames(self, window=1.0):
        """Collect frames arriving within `window` seconds. [] on timeout,
        None if the peer closed the link."""
        out = []
        end = time.time() + window
        while time.time() < end:
            self.sock.settimeout(max(0.05, end - time.time()))
            try:
                chunk = self.sock.recv(1024)
            except socket.timeout:
                break
            except OSError as e:
                self.log.note("link error: %s" % e)
                return None
            if not chunk:                 # peer closed
                self.log.note("link closed by headset")
                return None
            self.rxbuf += chunk
            frames, self.rxbuf = parse_frames(self.rxbuf)
            for f in frames:
                self.log.rx(f)
            out += frames
        return out

    def alive(self):
        """Liveness probe with a known-safe battery GET."""
        try:
            self.send(build(SENNHEISER, 0x0603))
        except OSError:
            return False
        return self.read_frames(1.5) is not None

    def close(self):
        if self.sock:
            try:
                self.sock.close()
            except OSError:
                pass
        self.sock = None


def sdp_lookup(addr):
    """Best-effort SDP lookup of the GAIA service channel. Uses pybluez if
    present; returns None otherwise (caller falls back to the doc channel)."""
    try:
        import bluetooth  # pybluez, optional
    except Exception:
        return None
    try:
        for svc in bluetooth.find_service(address=addr):
            uuids = [u.lower() for u in (svc.get("service-classes") or [])]
            name = (svc.get("name") or "").upper()
            if svc.get("protocol") == "RFCOMM" and ("GAIA" in name or
                    any("a2129ff3" in u for u in uuids)):
                return svc.get("port")
    except Exception:
        return None
    return None

# ---------------------------------------------------------------- logging

class Logger:
    def __init__(self, directory):
        os.makedirs(directory, exist_ok=True)
        self.path = os.path.join(directory, "gaia_%s.log" % datetime.now().strftime("%Y%m%d_%H%M%S"))
        self.fh = open(self.path, "a", buffering=1, encoding="utf-8")
        self.note("log file: %s" % self.path)

    def _w(self, line):
        stamp = datetime.now().strftime("%H:%M:%S.%f")[:-3]
        text = "%s %s" % (stamp, line)
        print(text)
        self.fh.write(text + "\n")

    def tx(self, frame, blocked=None):
        if blocked:
            self._w("BLOCKED (%s): %s" % (blocked, hexstr(frame)))
        else:
            self._w("-> " + hexstr(frame))

    def rx(self, frame):
        self._w("<- " + str(frame))

    def note(self, msg):
        self._w("# " + msg)

# -------------------------------------------------------------- the sweep

# feature -> (vendor, request cmd ids already known, so we skip them)
KNOWN = {
    (SENNHEISER, 3): {0x0600, 0x0601, 0x0602, 0x0603},
    (SENNHEISER, 2): {0x0400, 0x0401, 0x0402, 0x0405, 0x0406},
    (SENNHEISER, 0): {0x0007, 0x0040, 0x0041},
    (QUALCOMM, 0): {0x0000, 0x0001, 0x0002, 0x0003, 0x0007, 0x0009, 0x000A, 0x000C, 0x000D},
}

# default scope: (vendor, feature) pairs that are power/system, ranked
SCOPE = {
    "3": [(SENNHEISER, 3)],
    "2": [(SENNHEISER, 2)],
    "0s": [(SENNHEISER, 0)],
    "0q": [(QUALCOMM, 0)],
    "all": [(SENNHEISER, 3), (QUALCOMM, 0), (SENNHEISER, 2), (SENNHEISER, 0)],
}


def sweep(link, scope_key, start, end):
    targets = SCOPE[scope_key]
    link.log.note("sweep scope %s, request-cmd range 0x%02X..0x%02X" % (scope_key, start, end))
    for vendor, feature in targets:
        known = KNOWN.get((vendor, feature), set())
        for n in range(start, end + 1):
            cmd = (feature << 9) | n       # type 0 request
            if cmd in known:
                continue
            frame = build(vendor, cmd)
            if blocked_reason(frame):
                link.log.note("skip excluded %04X:%04X" % (vendor, cmd))
                continue
            link.send(frame)
            frames = link.read_frames(1.0)
            if frames is None:
                if confirm_off(link):
                    link.log.note("*** SUCCESS: link down after %04X:%04X ***" % (vendor, cmd))
                    print("\n>>> Candidate power-off command: %04X:%04X" % (vendor, cmd))
                    print(">>> Frame: %s" % hexstr(frame))
                    print(">>> Check the headset: red LED 3x and stays off? Then this is it.")
                    return cmd
                link.log.note("link dropped then recovered after %04X:%04X (reset/disconnect)" % (vendor, cmd))
            elif not frames:
                # No reply: could be a silent action. Verify the link is still up.
                if not link.alive():
                    if confirm_off(link):
                        link.log.note("*** SUCCESS: silent, link down after %04X:%04X ***" % (vendor, cmd))
                        print("\n>>> Candidate power-off command: %04X:%04X" % (vendor, cmd))
                        print(">>> Frame: %s" % hexstr(frame))
                        return cmd
                    link.log.note("link dropped then recovered after %04X:%04X" % (vendor, cmd))
            time.sleep(1.0)               # max one command per second
    link.log.note("sweep finished, no power-off command found in scope %s" % scope_key)
    return None


def confirm_off(link, window=60.0):
    """After a link drop: try to reconnect for `window` s. Reconnect failing
    the whole time = headset is really off (success). Returns True if off."""
    link.close()
    link.log.note("link dropped; probing reconnect for %ds to tell power-off from a reset" % window)
    end = time.time() + window
    while time.time() < end:
        if link.connect(timeout=5.0):
            link.log.note("reconnected: not a power-off")
            return False
        time.sleep(3.0)
    link.log.note("no reconnect in %ds: headset appears OFF" % window)
    return True

# ------------------------------------------------------------------- main

def main():
    ap = argparse.ArgumentParser(description="MOMENTUM 4 GAIA client")
    ap.add_argument("command", nargs="?", default="verify")
    ap.add_argument("args", nargs="*")
    ap.add_argument("--addr", default=DEFAULT_ADDR)
    ap.add_argument("--channel", type=int, default=None)
    ap.add_argument("--log", default="logs")
    ap.add_argument("--features", default="3", choices=list(SCOPE),
                    help="sweep scope (default 3 = the power/battery feature)")
    ap.add_argument("--range", default="4:127", help="sweep request-cmd range lo:hi (default 4:127)")
    args = ap.parse_args()

    log = Logger(args.log)
    link = Link(args.addr, log, channel=args.channel)
    log.note("connecting to %s" % args.addr)
    if not link.connect():
        log.note("could not connect. Is Smart Control Plus force-stopped? Are the headphones on and paired?")
        return 2
    try:
        cmd = args.command
        if cmd in ("verify", "get-timer"):
            n = int(args.args[0]) if args.args else 0
            link.send(build(SENNHEISER, 0x0601, bytes([n])))
            link.read_frames(2.0)
        elif cmd == "battery":
            link.send(build(SENNHEISER, 0x0603))
            link.read_frames(2.0)
        elif cmd == "set-timer":
            n, secs = int(args.args[0]), int(args.args[1])
            link.send(build(SENNHEISER, 0x0600, bytes([n, (secs >> 8) & 0xFF, secs & 0xFF])))
            link.read_frames(2.0)
            link.send(build(SENNHEISER, 0x0601, bytes([n])))
            link.read_frames(2.0)
        elif cmd == "raw":
            link.send(parse_hex(" ".join(args.args)))
            link.read_frames(2.0)
        elif cmd == "sweep":
            lo, hi = (int(x, 0) for x in args.range.split(":"))
            sweep(link, args.features, lo, hi)
        else:
            log.note("unknown command: %s" % cmd)
            return 2
    finally:
        link.close()
        log.note("done")
    return 0


if __name__ == "__main__":
    sys.exit(main())
