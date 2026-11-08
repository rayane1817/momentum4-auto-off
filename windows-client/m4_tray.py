"""Tray icon for M4 Control: the Windows counterpart of the Android widgets and Quick Settings tiles.

Click the icon for a menu: noise Adaptive / Custom / Off and previous / next EQ preset, like the
two Android widgets. m4_app.Core does the sending (through the open link, or connect, send,
disconnect). This file is only the icon and the menu: plain ctypes, no packages.

The icon is drawn here from the Android tile vectors (ic_tile_anc*.xml), white on a dark taskbar,
near-black on a light one, so it changes with the noise mode like the tile does.
"""

import math
import os
import re
import struct
import sys
import threading

# ------------------------------------------------------------ icon drawing (no Windows needed)

EAR = "M8,9a4,4 0,1 1,8 0c0,2.5 -2,3 -2.5,5s-1,4 -3,4a2.5,2.5 0,0 1,-2.5 -2.5"
EAR_LEFT = "M6,9a4,4 0,1 1,8 0c0,2.5 -2,3 -2.5,5s-1,4 -3,4a2.5,2.5 0,0 1,-2.5 -2.5"
ARCS = "M18.5,6.5a7,7 0,0 1,0 7M21,4a10.5,10.5 0,0 1,0 12"
# mode -> (stroked paths, filled rects) on the 24-unit grid of the Android vectors
GLYPHS = {
    0: ([EAR, ARCS], []),                                   # Adaptive: ear and sound arcs
    1: ([EAR_LEFT, "M19,4v16"], [(16.5, 10, 21.5, 13)]),    # Custom: ear and a fader
    2: ([EAR], []),                                         # Off: ear only
}
_TOK = re.compile(r"[MmLlHhVvCcSsAaZz]|[-+]?(?:\d*\.\d+|\d+\.?\d*)(?:[eE][-+]?\d+)?")


def _arc(x1, y1, rx, ry, phi, large, sweep, x2, y2, n):
    """Points of an SVG arc (endpoint form, SVG spec F.6.5), excluding the start point."""
    if rx == 0 or ry == 0:
        return [(x2, y2)]
    rx, ry = abs(rx), abs(ry)
    c, s = math.cos(math.radians(phi)), math.sin(math.radians(phi))
    dx, dy = (x1 - x2) / 2, (y1 - y2) / 2
    xp, yp = c * dx + s * dy, -s * dx + c * dy
    lam = xp * xp / (rx * rx) + yp * yp / (ry * ry)
    if lam > 1:
        rx, ry = rx * math.sqrt(lam), ry * math.sqrt(lam)
    num = rx * rx * ry * ry - rx * rx * yp * yp - ry * ry * xp * xp
    den = rx * rx * yp * yp + ry * ry * xp * xp
    co = math.sqrt(max(0.0, num / den)) if den else 0.0
    if large == sweep:
        co = -co
    cxp, cyp = co * rx * yp / ry, -co * ry * xp / rx
    cx, cy = c * cxp - s * cyp + (x1 + x2) / 2, s * cxp + c * cyp + (y1 + y2) / 2

    def ang(ux, uy, vx, vy):
        return math.atan2(ux * vy - uy * vx, ux * vx + uy * vy)

    ux, uy = (xp - cxp) / rx, (yp - cyp) / ry
    t1 = ang(1, 0, ux, uy)
    dt = ang(ux, uy, (-xp - cxp) / rx, (-yp - cyp) / ry)
    if not sweep and dt > 0:
        dt -= 2 * math.pi
    elif sweep and dt < 0:
        dt += 2 * math.pi
    out = []
    for k in range(1, n + 1):
        t = t1 + dt * k / n
        px, py = rx * math.cos(t), ry * math.sin(t)
        out.append((c * px - s * py + cx, s * px + c * py + cy))
    return out


def _cubic(p0, p1, p2, p3, n):
    out = []
    for k in range(1, n + 1):
        t = k / n
        u = 1 - t
        out.append((u ** 3 * p0[0] + 3 * u * u * t * p1[0] + 3 * u * t * t * p2[0] + t ** 3 * p3[0],
                    u ** 3 * p0[1] + 3 * u * u * t * p1[1] + 3 * u * t * t * p2[1] + t ** 3 * p3[1]))
    return out


def path_polylines(d, n=12):
    """The SVG path commands the tile icons use (M L H V C S A Z, both cases) as polylines."""
    toks, i = _TOK.findall(d), 0
    cmd, x, y, sx, sy, last_c = None, 0.0, 0.0, 0.0, 0.0, None
    lines, cur = [], None

    def num():
        nonlocal i
        v = float(toks[i])
        i += 1
        return v

    while i < len(toks):
        if toks[i].isalpha():
            cmd = toks[i]
            i += 1
            if cmd in "Zz":
                if cur:
                    cur.append(cur[0])
                x, y, last_c = sx, sy, None
                continue
        rel, c = cmd.islower(), cmd.upper()
        ox, oy = (x, y) if rel else (0.0, 0.0)
        if c == "M":
            x, y = ox + num(), oy + num()
            cur = [(x, y)]
            lines.append(cur)
            sx, sy = x, y
            cmd = "l" if rel else "L"
        elif c == "L":
            x, y = ox + num(), oy + num()
            cur.append((x, y))
        elif c == "H":
            x = ox + num()
            cur.append((x, y))
        elif c == "V":
            y = oy + num()
            cur.append((x, y))
        elif c in "CS":
            if c == "C":
                p1 = (ox + num(), oy + num())
            else:
                p1 = (2 * x - last_c[0], 2 * y - last_c[1]) if last_c else (x, y)
            p2 = (ox + num(), oy + num())
            p3 = (ox + num(), oy + num())
            cur.extend(_cubic((x, y), p1, p2, p3, n))
            x, y = p3
            last_c = p2
            continue
        elif c == "A":
            rx, ry, phi, large, sweep = num(), num(), num(), num(), num()
            ex, ey = ox + num(), oy + num()
            cur.extend(_arc(x, y, rx, ry, phi, int(large), int(sweep), ex, ey, n))
            x, y = ex, ey
        last_c = None
    return lines


def _seg_dist(px, py, ax, ay, bx, by):
    vx, vy = bx - ax, by - ay
    ll = vx * vx + vy * vy
    t = 0.0 if ll == 0 else max(0.0, min(1.0, ((px - ax) * vx + (py - ay) * vy) / ll))
    dx, dy = px - (ax + t * vx), py - (ay + t * vy)
    return math.sqrt(dx * dx + dy * dy)


def render(mode, size, rgb, alpha=1.0):
    """size x size coverage of the tile glyph for `mode` (0 Adaptive, 1 Custom, 2 Off, None
    unknown = the Off ear, dimmed by the caller). Returns rows of (r, g, b, a), top row first."""
    paths, rects = GLYPHS[mode if mode in GLYPHS else 2]
    scale = size / 24.0
    half = max(1.0, 2.0 * scale) / 2                 # 2-unit stroke, at least 1 px
    segs = []
    for d in paths:
        for line in path_polylines(d):
            segs += [(a[0] * scale, a[1] * scale, b[0] * scale, b[1] * scale) for a, b in zip(line, line[1:])]
    rows = []
    for py in range(size):
        row = []
        for px in range(size):
            cx, cy = px + 0.5, py + 0.5
            d = min(_seg_dist(cx, cy, *s) for s in segs) if segs else 99
            cov = max(0.0, min(1.0, half - d + 0.5))
            for x0, y0, x1, y1 in rects:
                ox = max(0.0, min(px + 1, x1 * scale) - max(px, x0 * scale))
                oy = max(0.0, min(py + 1, y1 * scale) - max(py, y0 * scale))
                cov = max(cov, ox * oy)
            row.append((rgb[0], rgb[1], rgb[2], int(round(255 * cov * alpha))))
        rows.append(row)
    return rows


def icon_resource(rows):
    """Bytes for CreateIconFromResourceEx: BITMAPINFOHEADER, 32-bit BGRA bottom-up, empty AND mask."""
    size = len(rows)
    head = struct.pack("<IiiHHIIiiII", 40, size, size * 2, 1, 32, 0, 0, 0, 0, 0, 0)
    xor = b"".join(bytes((b, g, r, a)) for row in reversed(rows) for (r, g, b, a) in row)
    mask = b"\0" * (((size + 31) // 32) * 4 * size)
    return head + xor + mask


# ----------------------------------------------------------------- start with Windows

RUN_KEY = r"Software\Microsoft\Windows\CurrentVersion\Run"
RUN_NAME = "M4 Control"


def autostart_command(script):
    """pythonw (no console) running the app in tray-only mode."""
    exe = sys.executable
    folder, base = os.path.split(exe)
    if base.lower() == "python.exe" and os.path.isfile(os.path.join(folder, "pythonw.exe")):
        exe = os.path.join(folder, "pythonw.exe")
    return '"%s" "%s" --tray' % (exe, os.path.abspath(script))


def autostart_enabled():
    try:
        import winreg
        with winreg.OpenKey(winreg.HKEY_CURRENT_USER, RUN_KEY) as k:
            winreg.QueryValueEx(k, RUN_NAME)
        return True
    except (ImportError, OSError):
        return False


def set_autostart(on, script):
    import winreg
    with winreg.OpenKey(winreg.HKEY_CURRENT_USER, RUN_KEY, 0, winreg.KEY_SET_VALUE) as k:
        if on:
            winreg.SetValueEx(k, RUN_NAME, 0, winreg.REG_SZ, autostart_command(script))
        else:
            try:
                winreg.DeleteValue(k, RUN_NAME)
            except FileNotFoundError:
                pass


def taskbar_is_light():
    try:
        import winreg
        with winreg.OpenKey(winreg.HKEY_CURRENT_USER,
                            r"Software\Microsoft\Windows\CurrentVersion\Themes\Personalize") as k:
            return winreg.QueryValueEx(k, "SystemUsesLightTheme")[0] == 1
    except (ImportError, OSError):
        return False


# ------------------------------------------------------------------ menu text (testable)

NOISE_NAMES = ("Adaptive", "Custom", "Off")
ID_OPEN, ID_NOISE, ID_PREV, ID_NEXT, ID_AUTOSTART, ID_QUIT = 1, 10, 20, 21, 30, 40


def tooltip(st):
    """At most 127 characters (the Windows limit)."""
    noise = NOISE_NAMES[st["noise"]] if st.get("noise") in (0, 1, 2) else "?"
    lines = ["M4 Control", "Noise: %s, EQ: %s" % (noise, st.get("eq") or "custom curve")]
    if st.get("busy"):
        lines.append("Sending...")
    elif st.get("battery") is not None:
        lines.append("Battery %d %%" % st["battery"])
    return "\n".join(lines)[:127]


def menu_items(st):
    """[(id or None, text, flags)] with flags from {"sep", "grey", "check", "radio", "default"}."""
    busy = bool(st.get("busy"))
    off = {"grey"} if busy else set()
    items = [(ID_OPEN, "Open M4 Control", {"default"}), (None, "", {"sep"}),
             (None, "Noise control" + (": sending..." if busy else ""), {"grey"})]
    for i, name in enumerate(NOISE_NAMES):
        items.append((ID_NOISE + i, name, off | ({"check", "radio"} if st.get("noise") == i else set())))
    items += [(None, "", {"sep"}), (None, "EQ: %s" % (st.get("eq") or "custom curve"), {"grey"}),
              (ID_PREV, "Previous preset", set(off)), (ID_NEXT, "Next preset", set(off)),
              (None, "", {"sep"}),
              (ID_AUTOSTART, "Start with Windows", {"check"} if st.get("autostart") else set()),
              (ID_QUIT, "Quit M4 Control", set())]
    return items


# ------------------------------------------------------------------- the Win32 tray

class Tray(threading.Thread):
    """Owns a hidden window, the notification-area icon and its menu, on its own thread.
    get_state() -> dict (noise, eq, battery, busy, autostart); on_command(id) runs on this thread
    and must not block. refresh(), balloon() and stop() are safe from any thread."""

    WM_TRAY, WM_REFRESH, WM_BALLOON = 0x8000 + 1, 0x8000 + 2, 0x8000 + 3

    def __init__(self, get_state, on_command):
        super().__init__(daemon=True, name="m4-tray")
        self.get_state = get_state
        self.on_command = on_command
        self.ready = threading.Event()
        self.ok = False
        self.hwnd = None
        self._balloon = None
        self._icon = None
        self._icon_key = None
        self._lock = threading.Lock()

    # ---- any thread
    def refresh(self):
        self._post(self.WM_REFRESH)

    def balloon(self, title, text):
        with self._lock:
            self._balloon = (title[:63], text[:255])
        self._post(self.WM_BALLOON)

    def stop(self):
        self._post(0x0010)                                    # WM_CLOSE

    def _post(self, msg):
        if self.hwnd:
            self.u.PostMessageW(self.hwnd, msg, 0, 0)

    # ---- tray thread
    def run(self):
        try:
            self._setup()
        except Exception:                                     # no tray: the app still works
            self.ready.set()
            return
        self.ok = True
        self.ready.set()
        msg = self.W.MSG()
        while self.u.GetMessageW(self.ctypes.byref(msg), None, 0, 0) > 0:
            self.u.TranslateMessage(self.ctypes.byref(msg))
            self.u.DispatchMessageW(self.ctypes.byref(msg))

    def _setup(self):
        import ctypes
        from ctypes import wintypes as W
        self.ctypes, self.W = ctypes, W
        u = self.u = ctypes.WinDLL("user32", use_last_error=True)
        sh = self.sh = ctypes.WinDLL("shell32", use_last_error=True)
        k32 = ctypes.WinDLL("kernel32", use_last_error=True)
        try:
            ctypes.WinDLL("shcore").SetProcessDpiAwareness(2)  # crisp icon at 125 / 150 %
        except (OSError, AttributeError):
            pass
        LRESULT = ctypes.c_ssize_t
        self.WNDPROC = ctypes.WINFUNCTYPE(LRESULT, W.HWND, W.UINT, W.WPARAM, W.LPARAM)

        class WNDCLASSW(ctypes.Structure):
            _fields_ = [("style", W.UINT), ("lpfnWndProc", self.WNDPROC), ("cbClsExtra", ctypes.c_int),
                        ("cbWndExtra", ctypes.c_int), ("hInstance", W.HINSTANCE), ("hIcon", W.HICON),
                        ("hCursor", W.HANDLE), ("hbrBackground", W.HBRUSH), ("lpszMenuName", W.LPCWSTR),
                        ("lpszClassName", W.LPCWSTR)]

        class GUID(ctypes.Structure):
            _fields_ = [("a", W.DWORD), ("b", W.WORD), ("c", W.WORD), ("d", ctypes.c_ubyte * 8)]

        class NOTIFYICONDATAW(ctypes.Structure):
            _fields_ = [("cbSize", W.DWORD), ("hWnd", W.HWND), ("uID", W.UINT), ("uFlags", W.UINT),
                        ("uCallbackMessage", W.UINT), ("hIcon", W.HICON), ("szTip", W.WCHAR * 128),
                        ("dwState", W.DWORD), ("dwStateMask", W.DWORD), ("szInfo", W.WCHAR * 256),
                        ("uVersion", W.UINT), ("szInfoTitle", W.WCHAR * 64), ("dwInfoFlags", W.DWORD),
                        ("guidItem", GUID), ("hBalloonIcon", W.HICON)]

        self.NID = NOTIFYICONDATAW
        u.DefWindowProcW.argtypes = [W.HWND, W.UINT, W.WPARAM, W.LPARAM]
        u.DefWindowProcW.restype = LRESULT
        u.CreateWindowExW.argtypes = [W.DWORD, W.LPCWSTR, W.LPCWSTR, W.DWORD, ctypes.c_int, ctypes.c_int,
                                      ctypes.c_int, ctypes.c_int, W.HWND, W.HMENU, W.HINSTANCE, W.LPVOID]
        u.CreateWindowExW.restype = W.HWND
        u.RegisterClassW.argtypes = [ctypes.POINTER(WNDCLASSW)]
        u.PostMessageW.argtypes = [W.HWND, W.UINT, W.WPARAM, W.LPARAM]
        u.CreatePopupMenu.restype = W.HMENU
        u.AppendMenuW.argtypes = [W.HMENU, W.UINT, ctypes.c_size_t, W.LPCWSTR]
        u.SetMenuDefaultItem.argtypes = [W.HMENU, W.UINT, W.UINT]
        u.TrackPopupMenu.argtypes = [W.HMENU, W.UINT, ctypes.c_int, ctypes.c_int, ctypes.c_int, W.HWND, W.LPVOID]
        u.DestroyMenu.argtypes = [W.HMENU]
        u.SetForegroundWindow.argtypes = [W.HWND]
        u.DestroyWindow.argtypes = [W.HWND]
        u.DestroyIcon.argtypes = [W.HICON]
        u.CreateIconFromResourceEx.argtypes = [ctypes.c_char_p, W.DWORD, W.BOOL, W.DWORD, ctypes.c_int,
                                               ctypes.c_int, W.UINT]
        u.CreateIconFromResourceEx.restype = W.HICON
        u.GetMessageW.argtypes = [ctypes.POINTER(W.MSG), W.HWND, W.UINT, W.UINT]
        sh.Shell_NotifyIconW.argtypes = [W.DWORD, ctypes.POINTER(NOTIFYICONDATAW)]
        k32.GetModuleHandleW.restype = W.HMODULE

        self._proc = self.WNDPROC(self._wndproc)              # keep a reference
        hinst = k32.GetModuleHandleW(None)
        wc = WNDCLASSW(lpfnWndProc=self._proc, hInstance=hinst, lpszClassName="M4ControlTray")
        u.RegisterClassW(ctypes.byref(wc))
        self.taskbar_created = u.RegisterWindowMessageW("TaskbarCreated")
        self.hwnd = u.CreateWindowExW(0, "M4ControlTray", "M4 Control", 0, 0, 0, 0, 0, None, None, hinst, None)
        if not self.hwnd:
            raise OSError("CreateWindowExW failed")
        self._notify(0)                                        # NIM_ADD

    def _nid(self, flags):
        n = self.NID()
        n.cbSize = self.ctypes.sizeof(self.NID)
        n.hWnd, n.uID, n.uFlags = self.hwnd, 1, flags
        return n

    def _notify(self, op):
        """op 0 add, 1 modify: icon, tooltip, callback."""
        st = self._state()
        n = self._nid(0x01 | 0x02 | 0x04)                     # NIF_MESSAGE | NIF_ICON | NIF_TIP
        n.uCallbackMessage = self.WM_TRAY
        n.hIcon = self._make_icon(st)
        n.szTip = tooltip(st)
        if not self.sh.Shell_NotifyIconW(op, self.ctypes.byref(n)) and op == 1:
            self.sh.Shell_NotifyIconW(0, self.ctypes.byref(n))  # explorer lost it: add again

    def _state(self):
        try:
            return self.get_state() or {}
        except Exception:
            return {}

    def _make_icon(self, st):
        light = taskbar_is_light()
        key = (st.get("noise"), bool(st.get("busy")), light)
        if key == self._icon_key and self._icon:
            return self._icon
        size = self.u.GetSystemMetrics(49) or 16               # SM_CXSMICON, DPI-aware
        rgb = (0x1A, 0x1A, 0x1A) if light else (0xFF, 0xFF, 0xFF)
        alpha = 0.55 if st.get("busy") or st.get("noise") not in (0, 1, 2) else 1.0
        res = icon_resource(render(st.get("noise"), size, rgb, alpha))
        icon = self.u.CreateIconFromResourceEx(res, len(res), True, 0x00030000, size, size, 0)
        if icon:
            if self._icon:
                self.u.DestroyIcon(self._icon)
            self._icon, self._icon_key = icon, key
        return self._icon

    def _show_menu(self):
        u, W = self.u, self.W
        st = self._state()
        st["autostart"] = autostart_enabled()
        h = u.CreatePopupMenu()
        for mid, text, flags in menu_items(st):
            if "sep" in flags:
                u.AppendMenuW(h, 0x800, 0, None)              # MF_SEPARATOR
                continue
            f = (0x1 if "grey" in flags else 0) | (0x8 if "check" in flags else 0) | (0x200 if "radio" in flags else 0)
            u.AppendMenuW(h, f, mid or 0, text)
        u.SetMenuDefaultItem(h, ID_OPEN, 0)
        pt = W.POINT()
        u.GetCursorPos(self.ctypes.byref(pt))
        u.SetForegroundWindow(self.hwnd)
        cmd = u.TrackPopupMenu(h, 0x0100 | 0x0002 | 0x0020, pt.x, pt.y, 0, self.hwnd, None)  # RETURNCMD|RIGHTBUTTON|BOTTOMALIGN
        u.PostMessageW(self.hwnd, 0, 0, 0)                    # WM_NULL, so the menu closes properly
        u.DestroyMenu(h)
        if cmd:
            try:
                self.on_command(cmd)
            except Exception:
                pass

    def _wndproc(self, hwnd, msg, wp, lp):
        try:
            if msg == self.WM_TRAY:
                if lp in (0x0202, 0x0205):                    # WM_LBUTTONUP, WM_RBUTTONUP
                    self._show_menu()
                elif lp == 0x0203:                            # WM_LBUTTONDBLCLK
                    self.on_command(ID_OPEN)
                return 0
            if msg == self.WM_REFRESH or msg == 0x001A:       # our refresh, WM_SETTINGCHANGE (theme)
                if msg == 0x001A:
                    self._icon_key = None
                self._notify(1)
                return 0
            if msg == self.WM_BALLOON:
                with self._lock:
                    b, self._balloon = self._balloon, None
                if b:
                    n = self._nid(0x10)                       # NIF_INFO
                    n.szInfoTitle, n.szInfo = b
                    n.dwInfoFlags = 0x02 | 0x10               # NIIF_WARNING | NIIF_NOSOUND
                    self.sh.Shell_NotifyIconW(1, self.ctypes.byref(n))
                return 0
            if msg == self.taskbar_created:                   # explorer restarted
                self._notify(0)
                return 0
            if msg == 0x0010:                                 # WM_CLOSE
                self.sh.Shell_NotifyIconW(2, self.ctypes.byref(self._nid(0)))   # NIM_DELETE
                self.u.DestroyWindow(hwnd)
                return 0
            if msg == 0x0002:                                 # WM_DESTROY
                self.hwnd = None
                if self._icon:
                    self.u.DestroyIcon(self._icon)
                self.u.PostQuitMessage(0)
                return 0
        except Exception:
            pass
        return self.u.DefWindowProcW(hwnd, msg, wp, lp)
