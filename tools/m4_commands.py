#!/usr/bin/env python3
"""MOMENTUM 4 (fw 3.38.3) GAIA v3 command table, vendor 0x0495 unless noted.

One source of labels for tools/gaia_annotate.py and the documented-only guard
in windows-client/gaia_client.py (`check`, `snapshot`).

Sources: docs/config-spec.md (capture1, capture2), docs/protocol.md (capture1,
oldapp, sweep), docs/prior-art.md (omarchy PROTOCOL.md). status "confirmed" =
seen on the wire from Ryane's M4; "claimed" = documented elsewhere, not yet
seen by us.

Id arithmetic (docs/protocol.md): command = feature << 9 | type << 7 | n.
Response = cmd + 0x0100, notification = GET id + 0x0080, error = cmd + 0x0180
with payload 05 (invalid parameter), 83 (out of range, omarchy) or 01 (not
supported).

Payload specs: a list of alternatives; each alternative is a tuple of fields.
A field is one of
    ("u8", lo, hi)          one unsigned byte in [lo, hi]
    ("s8", lo, hi)          one signed byte in [lo, hi]
    ("u16", lo, hi)         big-endian u16 in [lo, hi]
    ("const", v)            one byte equal to v
An empty tuple () means "no payload".
"""

SENNHEISER = 0x0495
QUALCOMM = 0x001D

EMPTY = [()]
BOOL = [(("u8", 0, 1),)]


def _c(name, kind, feature_cmd, payload, source, status, get_for=None, notif=None, pc=True):
    return dict(name=name, kind=kind, payload=payload, source=source, status=status,
                feature=feature_cmd >> 9, get_for=get_for, notif=notif, pc_allowed=pc)


# kind: get | set | register | action | notif
COMMANDS = {
    # ---- feature 0: system
    0x0007: _c("register notifications", "register", 0x0007,
               [(("u8", 0x04, 0x04),), (("u8", 0x08, 0x08),), (("u8", 0x0D, 0x0D),)],
               "capture2", "confirmed"),
    # ---- feature 2: on-head / device
    0x0400: _c("on-head detection set", "set", 0x0400, BOOL, "capture2", "confirmed", get_for=0x0401),
    0x0401: _c("on-head detection get", "get", 0x0401, EMPTY, "capture1", "confirmed", notif=0x0481),
    0x0402: _c("physical state get", "get", 0x0402, EMPTY, "capture1", "confirmed", notif=0x0482),
    0x0405: _c("Hi-Res set (0 = on)", "set", 0x0405, BOOL, "capture2", "confirmed", get_for=0x0406),
    0x0406: _c("Hi-Res get (0 = on)", "get", 0x0406, EMPTY, "capture2", "confirmed", notif=0x0486),
    # ---- feature 3: power / battery / timer
    0x0600: _c("timer set", "set", 0x0600, [(("const", 0), ("u16", 0, 3600))], "capture1", "confirmed",
               get_for=0x0601),
    0x0601: _c("timer get", "get", 0x0601, [(("const", 0),)], "capture1", "confirmed"),
    0x0602: _c("charging state get", "get", 0x0602, EMPTY, "capture1", "confirmed", notif=0x0682),
    0x0603: _c("battery get", "get", 0x0603, EMPTY, "capture1", "confirmed", notif=0x0683),
    0x0604: _c("battery voltage get (sweep T8)", "get", 0x0604, EMPTY, "sweep", "confirmed"),
    # The one remote power-off. Documented, but never sent from the PC in the
    # local session (plan section 1): the app guard sends it once.
    0x0605: _c("POWER OFF", "action", 0x0605, EMPTY, "sweep", "confirmed", pc=False),
    # ---- feature 4: generic audio
    0x0800: _c("codec get", "get", 0x0800, EMPTY, "capture1", "confirmed"),
    0x0801: _c("voice prompts set", "set", 0x0801, [(("u8", 0, 2),)], "capture2", "confirmed", get_for=0x0802),
    0x0802: _c("voice prompts get", "get", 0x0802, EMPTY, "capture1", "confirmed", notif=0x0882),
    0x0803: _c("audio mode set (2 = speech clarity)", "set", 0x0803, [(("u16", 0, 2),)], "capture2", "confirmed",
               get_for=0x0804),
    0x0804: _c("audio mode get", "get", 0x0804, EMPTY, "capture1", "confirmed", notif=0x0884),
    0x0805: _c("call transparency set", "set", 0x0805, [(("u8", 0, 4),)], "capture2", "confirmed",
               get_for=0x0806),
    0x0806: _c("call transparency get", "get", 0x0806, EMPTY, "capture1", "confirmed", notif=0x0886),
    0x0807: _c("prompt language get", "get", 0x0807, EMPTY, "capture1", "confirmed"),
    0x080A: _c("auto-answer set", "set", 0x080A, BOOL, "capture2", "confirmed", get_for=0x080B),
    0x080B: _c("auto-answer get", "get", 0x080B, EMPTY, "capture1", "confirmed", notif=0x088B),
    0x080C: _c("smart pause set", "set", 0x080C, BOOL, "capture2", "confirmed", get_for=0x080D),
    0x080D: _c("smart pause get", "get", 0x080D, EMPTY, "capture1", "confirmed", notif=0x088D),
    0x0814: _c("comfort call set", "set", 0x0814, BOOL, "omarchy", "claimed", get_for=0x0815),
    0x0815: _c("comfort call get", "get", 0x0815, EMPTY, "capture1", "confirmed", notif=0x0895),
    0x0817: _c("low latency set", "set", 0x0817, BOOL, "oldapp", "claimed", get_for=0x0818),
    0x0818: _c("low latency get", "get", 0x0818, EMPTY, "oldapp", "claimed", notif=0x0898),
    # ---- feature 8: EQ
    0x1001: _c("EQ band set", "set", 0x1001, [(("u8", 0, 4), ("s8", -60, 60))], "capture2", "confirmed",
               get_for=0x1002),
    0x1002: _c("EQ band get", "get", 0x1002, [(("u8", 0, 4),)], "capture2", "confirmed", notif=0x1082),
    0x1003: _c("EQ all gains get", "get", 0x1003, EMPTY, "omarchy", "claimed"),
    0x1008: _c("bass boost set", "set", 0x1008, BOOL, "capture2", "confirmed", get_for=0x1009),
    0x1009: _c("bass boost get", "get", 0x1009, EMPTY, "capture2", "confirmed", notif=0x1089),
    # ---- feature 9: product info
    0x1200: _c("hw revision get", "get", 0x1200, EMPTY, "capture1", "confirmed"),
    0x1201: _c("firmware get", "get", 0x1201, EMPTY, "capture1", "confirmed"),
    0x1206: _c("model get", "get", 0x1206, EMPTY, "capture1", "confirmed"),
    # ---- feature 10: device list. 1401 returns device names: private, never in snapshot.
    0x1401: _c("paired device name get", "get", 0x1401, [(("u8", 0, 7),)], "omarchy", "claimed"),
    0x1407: _c("audio mode change pending get", "get", 0x1407, EMPTY, "omarchy", "claimed"),
    # ---- feature 11: MMI
    0x1606: _c("touch controls set (0 = on)", "set", 0x1606, BOOL, "omarchy", "claimed", get_for=0x1607),
    0x1607: _c("touch controls get (0 = on)", "get", 0x1607, EMPTY, "omarchy", "claimed", notif=0x1687),
    # ---- feature 13: noise control
    0x1A00: _c("noise sub-mode set", "set", 0x1A00,
               [(("const", 1), ("u8", 0, 2)),                      # anti-wind 0 off / 1 / 2
                (("const", 3), ("u8", 0, 1)),                      # adaptive
                (("const", 1), ("u8", 0, 2), ("const", 2), ("u8", 0, 1),
                 ("const", 3), ("u8", 0, 1))],                     # 6-byte table (omarchy)
               "capture2", "confirmed", get_for=0x1A01),
    0x1A01: _c("noise sub-mode get", "get", 0x1A01, EMPTY, "capture1", "confirmed", notif=0x1A81),
    0x1A02: _c("ANC<->transparency level set", "set", 0x1A02, [(("u8", 0, 100),)], "capture2", "confirmed",
               get_for=0x1A03),
    0x1A03: _c("ANC<->transparency level get", "get", 0x1A03, EMPTY, "capture1", "confirmed", notif=0x1A83),
    0x1A04: _c("ANC master set", "set", 0x1A04, BOOL, "capture2", "confirmed", get_for=0x1A05),
    0x1A05: _c("ANC master get", "get", 0x1A05, EMPTY, "capture1", "confirmed", notif=0x1A85),
}

# Labels only (decoder): ids seen on the wire whose meaning is unknown or that we
# never send. Never valid for `check`.
SEEN_LABELS = {
    0x0880: "audio stream notif", 0x089A: "sample rate notif", 0x148B: "device connection notif",
    0x081A: "seen at connect (err 01)", 0x081D: "seen at connect (err 01)", 0x081E: "seen at connect (err 01)",
    0x2001: "feature 16, seen at connect", 0x2003: "feature 16, seen at connect",
    0x2005: "feature 16, seen at connect", 0x2007: "feature 16, seen at connect",
    0x2081: "feature 16 notif", 0x2601: "feature 19, seen at connect",
    0x1402: "device connect [idx]", 0x1405: "BLOCKED device delete/disconnect [idx]",
    0x1403: "NEVER: forget device (omarchy)", 0x1406: "BLOCKED delete pairing list",
    0x0040: "BLOCKED factory reset",
}

# Snapshot GET list (plan Phase 3.2). key -> (cmd, payload bytes)
SNAPSHOT_GETS = [
    ("0601/00", 0x0601, b"\x00"), ("0602", 0x0602, b""), ("0603", 0x0603, b""),
    ("0401", 0x0401, b""), ("0402", 0x0402, b""), ("0406", 0x0406, b""),
    ("0802", 0x0802, b""), ("0804", 0x0804, b""), ("0806", 0x0806, b""), ("0807", 0x0807, b""),
    ("080B", 0x080B, b""), ("080D", 0x080D, b""), ("0815", 0x0815, b""), ("0818", 0x0818, b""),
] + [("1002/%02X" % b, 0x1002, bytes([b])) for b in range(5)] + [
    ("1003", 0x1003, b""), ("1009", 0x1009, b""),
    ("1A01", 0x1A01, b""), ("1A03", 0x1A03, b""), ("1A05", 0x1A05, b""),
    ("1607", 0x1607, b""), ("1407", 0x1407, b""),
    ("1200", 0x1200, b""), ("1201", 0x1201, b""), ("1206", 0x1206, b""),
]

# Fields that change on their own; ignored by `diff`.
VOLATILE = {"0602", "0603", "0604", "0800", "0402"}

ERROR_CODES = {0x01: "not supported", 0x05: "invalid parameter", 0x83: "out of range"}


# ------------------------------------------------------------------ helpers

def ftype(command):
    return (command >> 7) & 3          # 0 cmd, 1 notif, 2 resp, 3 error


def base(command):
    return command & ~(3 << 7)


def resp_id(cmd):
    return cmd | 0x0100


def err_id(cmd):
    return cmd | 0x0180


def _field_ok(field, data, i):
    kind = field[0]
    if kind == "u16":
        if i + 2 > len(data):
            return None
        v = (data[i] << 8) | data[i + 1]
        return i + 2 if field[1] <= v <= field[2] else None
    if i >= len(data):
        return None
    v = data[i]
    if kind == "u8":
        return i + 1 if field[1] <= v <= field[2] else None
    if kind == "s8":
        s = v - 256 if v > 127 else v
        return i + 1 if field[1] <= s <= field[2] else None
    if kind == "const":
        return i + 1 if v == field[1] else None
    raise ValueError("bad field %r" % (field,))


def payload_ok(cmd, payload):
    """True if `payload` matches one documented alternative for `cmd`."""
    entry = COMMANDS.get(cmd)
    if not entry:
        return False
    for alt in entry["payload"]:
        i = 0
        for field in alt:
            i = _field_ok(field, payload, i)
            if i is None:
                break
        if i is not None and i == len(payload):
            return True
    return False


def documented_reason(vendor, cmd, payload):
    """None if the frame is a documented, PC-allowed request with an in-range
    payload. Otherwise a reason string. This is the documented-only guard."""
    if vendor != SENNHEISER:
        return "vendor %04X not in the documented table" % vendor
    if ftype(cmd) != 0:
        return "%04X is not a request id" % cmd
    entry = COMMANDS.get(cmd)
    if not entry:
        return "%04X is not documented" % cmd
    if not entry["pc_allowed"]:
        return "%04X (%s) is never sent from the PC" % (cmd, entry["name"])
    if not payload_ok(cmd, payload):
        return "payload [%s] outside the documented range for %04X (%s)" % (
            " ".join("%02X" % b for b in payload), cmd, entry["name"])
    return None


def label(vendor, command):
    """Human label for any frame id (request, resp, notif, error)."""
    t = ftype(command)
    b = base(command)
    tname = ("cmd", "notif", "resp", "ERROR")[t]
    if vendor == SENNHEISER:
        if command in SEEN_LABELS:
            return SEEN_LABELS[command]
        if t == 1:
            for k, e in COMMANDS.items():
                if e.get("notif") == command:
                    return "%s (notif)" % e["name"].replace(" get", "")
        e = COMMANDS.get(b)
        if e:
            return e["name"] if t == 0 else "%s (%s)" % (e["name"], tname)
        if b in SEEN_LABELS:
            return "%s (%s)" % (SEEN_LABELS[b], tname)
    elif vendor == QUALCOMM:
        return "Qualcomm core %04X (%s)" % (command, tname)
    return "UNKNOWN"


def decode(cmd_base, payload):
    """Decode a GET response / notification payload to a short string."""
    p = payload
    b0 = p[0] if p else None
    u16 = (lambda o: (p[o] << 8) | p[o + 1])
    try:
        if cmd_base == 0x1A05:
            return "ANC %s" % _onoff(b0)
        if cmd_base == 0x1A03:
            return "level %d" % b0
        if cmd_base == 0x1A01:
            names = {1: "antiWind", 2: "transparency", 3: "adaptive"}
            return " ".join("%s=%d" % (names.get(p[i], "m%d" % p[i]), p[i + 1]) for i in range(0, len(p) - 1, 2))
        if cmd_base == 0x0406:
            return "Hi-Res %s" % {0: "ON", 1: "off"}.get(b0, "?%s" % b0)
        if cmd_base == 0x1607:
            return "touch %s" % {0: "ON", 1: "off"}.get(b0, "?%s" % b0)
        if cmd_base in (0x0401, 0x080B, 0x080D, 0x0815, 0x0818, 0x1009):
            return _onoff(b0)
        if cmd_base == 0x0804:
            return "audio mode %d" % u16(0)
        if cmd_base == 0x0806:
            return "call transparency %d" % b0
        if cmd_base == 0x0402:
            return "physical %s" % {1: "in case", 2: "off head", 3: "on head"}.get(b0, "?%s" % b0)
        if cmd_base == 0x0603:
            return "battery %d %%" % p[-1]
        if cmd_base == 0x0601 and len(p) >= 3:
            return "timer %d = %d s" % (p[0], u16(1))
        if cmd_base == 0x1002:
            return "EQ dB " + " ".join("%.1f" % ((x - 256 if x > 127 else x) / 10.0) for x in p)
        if cmd_base == 0x1003:
            return "EQ dB " + " ".join("%.1f" % ((x - 256 if x > 127 else x) / 10.0) for x in p)
        if cmd_base == 0x1201 and len(p) >= 6:
            return "fw %d.%d.%d" % (u16(0), u16(2), u16(4))
        if cmd_base == 0x1200 and len(p) >= 2:
            return "hw %d" % u16(0)
        if cmd_base == 0x1206:
            return "model %s" % p.split(b"\x00")[0].decode("ascii", "replace")
    except (IndexError, TypeError):
        return None
    return None


def _onoff(v):
    return {0: "off", 1: "ON"}.get(v, "?%s" % v)
