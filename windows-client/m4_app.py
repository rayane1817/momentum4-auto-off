#!/usr/bin/env python3
"""M4 Control: Windows app for the MOMENTUM 4 (noise control, EQ, settings, power off).

The window is ui/index.html (design v4, the Android app's look) in an Edge app window.
This script serves it on 127.0.0.1 only, behind a random token, and owns the Bluetooth link
(GAIA over RFCOMM, the same transport as gaia_client.py). Stock Python 3.9+, no packages, no
internet. Start with start-m4-app.bat or `pythonw m4_app.py`. It quits about 10 s after its
window closes.

Every frame comes from m4_logic (documented commands only). Logs:
%LOCALAPPDATA%\\M4Control\\logs, with no Bluetooth address in them.
"""

import http.server
import json
import os
import platform
import queue
import re
import secrets
import subprocess
import sys
import threading
import time
import urllib.parse
import urllib.request
import webbrowser

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import m4_logic as L  # noqa: E402

G = L.G
M4 = L.M4

APP_DIR = os.path.join(os.environ.get("LOCALAPPDATA") or os.path.expanduser("~"), "M4Control")
UI_FILE = os.path.join(HERE, "ui", "index.html")
BATTERY_POLL_S = 120
ADDR_RE = re.compile(r"(?:[0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}")
LOG_KEEP = 400


def scrub(text):
    return ADDR_RE.sub("XX:XX:XX:XX:XX:XX", text)


class UiLogger(G.Logger):
    """gaia_client's file logger, plus every line to the app's log view, with command labels on replies."""

    def __init__(self, directory, sink):
        self.sink = sink
        super().__init__(directory)

    def _w(self, line):
        super()._w(line)
        self.sink(line)

    def rx(self, frame):
        self._w("<- %s  (%s)" % (frame, M4.label(frame.vendor, frame.command)))


class Worker(threading.Thread):
    """Owns the RFCOMM link. Items: ("connect", addr), ("disconnect",), ("send", [frames]),
    ("poweroff",), ("quit",). Posts ("state", name, text) and ("frame", Frame)."""

    def __init__(self, post, log):
        super().__init__(daemon=True, name="m4-link")
        self.q = queue.Queue()
        self.post = post
        self.log = log
        self.link = None
        self.last_poll = 0.0

    def put(self, *item):
        self.q.put(item)

    def run(self):
        while True:
            try:
                item = self.q.get(timeout=0.05 if self.link else 0.5)
            except queue.Empty:
                item = None
            try:
                if item and self._handle(item) == "quit":
                    return
                if self.link:
                    self._pump()
            except OSError as e:
                self._drop("Link error: %s" % e)
            except Exception as e:  # keep the thread alive; the log has the details
                self.log.note("worker error: %r" % e)

    def _handle(self, item):
        kind = item[0]
        if kind == "quit":
            self._close()
            return "quit"
        if kind == "connect":
            self._close()
            self.post(("state", "connecting", None))
            link = G.Link(item[1], self.log)
            if link.connect(timeout=10.0):
                self.link = link
                self.post(("state", "connected", None))
                self._send_all(L.connect_frames())
                self.last_poll = time.monotonic()
            else:
                link.close()
                self.post(("state", "failed", None))
        elif kind == "disconnect":
            self._close()
            self.post(("state", "disconnected", None))
        elif kind == "send":
            self._send_all(item[1])
        elif kind == "poweroff":
            if self.link and self.link.send(L.POWER_OFF):
                self.post(("state", "poweroff_sent", None))
        return None

    def _send_all(self, frames):
        for f in frames:
            if not self.link:
                return
            self.link.send(f)

    def _pump(self):
        frames = self.link.read_frames(0.1)
        if frames is None:
            self._drop("The headphones closed the link.")
            return
        for f in frames:
            self.post(("frame", f))
        if time.monotonic() - self.last_poll > BATTERY_POLL_S:
            self.last_poll = time.monotonic()
            self._send_all([L.battery_frame()])

    def _drop(self, text):
        self._close()
        self.post(("state", "dropped", text))

    def _close(self):
        if self.link:
            self.link.close()
            self.link = None


class Core:
    """App state, actions from the page, frames from the worker. No HTTP in here (unit-tested)."""

    def __init__(self, app_dir=APP_DIR, worker=None, devices=None):
        self.app_dir = app_dir
        self.settings_path = os.path.join(app_dir, "settings.json")
        self.lock = threading.RLock()
        self.clients = []
        self.log_lines = []
        self.settings = self._load_settings()
        self.custom = {}
        for name, gains in (self.settings.get("presets") or {}).items():
            if isinstance(name, str) and isinstance(gains, list) and len(gains) == 5:
                try:
                    self.custom[name[:30]] = [L.clamp_gain(g) for g in gains]
                except (TypeError, ValueError):
                    pass
        self.s = self._blank()
        self.log = UiLogger(os.path.join(app_dir, "logs"), self._on_log)
        self.log.note("M4 Control %s, %s, Python %s" % (L.APP_VERSION, platform.platform(), platform.python_version()))
        self.worker = worker or Worker(self.on_worker, self.log)
        if worker is None:
            self.worker.start()
        self.devices, self.device_idx = [], None
        self.eq_wait = None          # (deadline, curve) after an EQ write from the page
        self.rescan(devices)

    # ----------------------------------------------------------- state

    @staticmethod
    def _blank():
        return dict(conn="disconnected", msg="Not connected", battery=None, model=None, firmware=None,
                    hardware=None, timer=None, anc_on=None, adaptive_on=None, level=0, anti_wind=None,
                    eq=[0, 0, 0, 0, 0], toggles={t[0]: None for t in L.TOGGLES}, speech=None, call=None,
                    poweroff_sent=False)

    def snapshot(self):
        with self.lock:
            d = json.loads(json.dumps(self.s))
            d["devices"] = [n for n, _a in self.devices]
            d["device"] = self.device_idx
            d["presets"] = {"builtin": [[n, list(g)] for n, g in L.BUILTIN_PRESETS],
                            "custom": [[n, list(g)] for n, g in sorted(self.custom.items())]}
            d["version"] = L.APP_VERSION
            return d

    def subscribe(self):
        q = queue.Queue()
        with self.lock:
            self.clients.append(q)
        return q

    def unsubscribe(self, q):
        with self.lock:
            if q in self.clients:
                self.clients.remove(q)

    def client_count(self):
        with self.lock:
            return len(self.clients)

    def broadcast(self):
        payload = json.dumps(self.snapshot())
        with self.lock:
            for q in self.clients:
                q.put(("state", payload))

    def _on_log(self, line):
        line = scrub(line)
        payload = json.dumps(line)
        with self.lock:
            self.log_lines.append(line)
            del self.log_lines[:-LOG_KEEP]
            for q in self.clients:
                q.put(("log", payload))

    def _load_settings(self):
        try:
            with open(self.settings_path, encoding="utf-8") as f:
                s = json.load(f)
            return s if isinstance(s, dict) else {}
        except (OSError, ValueError):
            return {}

    def _save_settings(self):
        os.makedirs(self.app_dir, exist_ok=True)
        tmp = self.settings_path + ".tmp"
        with open(tmp, "w", encoding="utf-8") as f:
            json.dump(self.settings, f, indent=2)
        os.replace(tmp, self.settings_path)

    # ---------------------------------------------------- from the worker

    def on_worker(self, item):
        with self.lock:
            if item[0] == "frame":
                self.apply(L.decode(item[1]))
            elif item[0] == "state":
                self._state(item[1], item[2])
        self.broadcast()

    def _state(self, name, text):
        s = self.s
        if name == "connecting":
            s["conn"], s["msg"] = "connecting", "Connecting..."
        elif name == "connected":
            self.s = s = self._blank()
            s["conn"], s["msg"] = "connected", "Connected"
        elif name == "poweroff_sent":
            s["poweroff_sent"] = True
            s["msg"] = "Power off sent. Press the button on the headphones to turn them on."
        else:
            s["conn"] = "disconnected"
            if s["poweroff_sent"]:
                s["msg"] = "Switched off. Press the button on the headphones to turn them on."
            elif name == "failed":
                s["msg"] = "Couldn't connect. Are the headphones on and paired with this PC?"
            elif name == "dropped":
                s["msg"] = "Disconnected. %s" % (text or "")
            else:
                s["msg"] = "Not connected"

    def apply(self, d):
        s = self.s
        if "error" in d:
            base, code = d["error"]
            self.log.note("headphones refused %04X (code %s)" % (base, "%02X" % code if code is not None else "?"))
            if base == 0x0803 and code != 0x80:
                self.send([L.req(0x0804)])           # read the real sound mode back (T39)
            return
        for key in ("battery", "model", "firmware", "hardware", "timer", "anc_on", "adaptive_on",
                    "level", "anti_wind", "speech"):
            if key in d:
                s[key] = d[key]
        if "call_transparency" in d:
            s["call"] = d["call_transparency"]
        if "eq" in d:
            # The headset reports the whole curve after each band write. For 1 s after the page
            # wrote a curve, skip the in-between ones so the faders don't jump back and forth.
            got = list(d["eq"])
            if self.eq_wait and time.monotonic() < self.eq_wait[0] and got != self.eq_wait[1]:
                pass
            else:
                self.eq_wait = None
                s["eq"] = got
        for key in s["toggles"]:
            if key in d:
                s["toggles"][key] = d[key]

    # ----------------------------------------------------- from the page

    def send(self, frames):
        if self.s["conn"] == "connected" and frames:
            self.worker.put("send", list(frames))

    def handle(self, a):
        """One action from the page. Returns a JSON-able dict; never raises on bad input."""
        try:
            with self.lock:
                res = self._handle(a if isinstance(a, dict) else {})
        except (L.Refused, ValueError, TypeError, KeyError, IndexError) as e:
            self.log.note("action refused: %s" % e)
            res = {"ok": False, "error": str(e)}
        self.broadcast()
        return res

    def _handle(self, a):
        act = a.get("action")
        s = self.s
        if act == "connect":
            idx = a.get("device", self.device_idx)
            if idx is None or not 0 <= int(idx) < len(self.devices):
                return {"ok": False, "error": "Pick the headphones first."}
            self.device_idx = int(idx)
            self.settings["address"] = self.devices[self.device_idx][1]
            self._save_settings()
            s["poweroff_sent"] = False
            self.worker.put("connect", self.devices[self.device_idx][1])
            return {"ok": True}
        if act == "disconnect":
            self.worker.put("disconnect")
            return {"ok": True}
        if act == "rescan":
            self.rescan()
            return {"ok": True}
        if act == "report":
            return {"ok": True, "text": self.report()}
        if act == "open_logs":
            if hasattr(os, "startfile"):
                os.startfile(os.path.join(self.app_dir, "logs"))
            return {"ok": True}
        if act == "save_preset":
            name = str(a.get("name", "")).strip()[:30]
            gains = [L.clamp_gain(int(g)) for g in a["gains"]][:5]
            if not name or len(gains) != 5:
                return {"ok": False, "error": "Give the preset a name."}
            if name in dict(L.BUILTIN_PRESETS):
                return {"ok": False, "error": "That's a built-in preset. Pick another name."}
            self.custom[name] = gains
            self._store_presets()
            return {"ok": True}
        if act == "delete_preset":
            self.custom.pop(str(a.get("name")), None)
            self._store_presets()
            return {"ok": True}
        if s["conn"] != "connected":
            return {"ok": False, "error": "Connect first."}
        if act == "noise":
            mode = int(a["mode"])
            if not 0 <= mode <= 2:
                raise ValueError("bad noise mode")
            self.send(L.noise_frames(mode, s["level"]))
            s["anc_on"], s["adaptive_on"] = mode != 2, mode == 0
        elif act == "level":
            s["level"] = max(0, min(100, int(a["value"])))
            self.send(L.level_frames(s["level"]))
        elif act == "wind":
            idx = int(a["index"])
            self.send([L.anti_wind_frame(idx)])
            s["anti_wind"] = idx
        elif act == "band":
            frames = []
            for band, gain in a["changes"][:5]:
                band, gain = int(band), L.clamp_gain(int(gain))
                frames.append(L.eq_band_frame(band, gain))
                s["eq"][band] = gain
            self.send(frames)
            self.eq_wait = (time.monotonic() + 1.0, list(s["eq"]))
        elif act == "preset":
            gains = [L.clamp_gain(int(g)) for g in a["gains"]]
            if len(gains) != 5:
                raise ValueError("a curve has 5 bands")
            self.send(L.eq_curve_frames(gains))
            s["eq"] = gains
            self.eq_wait = (time.monotonic() + 1.0, list(gains))
        elif act == "toggle":
            key, on = str(a["key"]), bool(a["on"])
            self.send(L.toggle_frames(key, on))
            s["toggles"][key] = on
        elif act == "speech":
            self.send(L.speech_frames(bool(a["on"])))
        elif act == "call":
            v = max(0, min(4, int(a["value"])))
            self.send(L.call_transparency_frames(v))
            s["call"] = v
        elif act == "poweroff":
            if a.get("confirmed") is not True:
                return {"ok": False, "error": "Power off needs a confirm."}
            self.worker.put("poweroff")
        else:
            return {"ok": False, "error": "unknown action"}
        return {"ok": True}

    def _store_presets(self):
        self.settings["presets"] = {k: list(v) for k, v in self.custom.items()}
        self._save_settings()

    def rescan(self, devices=None):
        devs = list(devices) if devices is not None else L.paired_devices()
        known = {a.upper() for _n, a in devs}
        cli = G.default_addr()                                 # M4_ADDR or m4_addr.txt, like the CLI
        if cli and L.format_addr(cli) and L.format_addr(cli) not in known:
            devs.append(("Headphones from m4_addr.txt", L.format_addr(cli)))
        seen, named = {}, []
        for n, a in devs:
            seen[n] = seen.get(n, 0) + 1
            named.append((n if seen[n] == 1 else "%s (%d)" % (n, seen[n]), a))
        self.devices = named
        self.device_idx = L.pick_default(named, self.settings.get("address"))
        if not named:
            self.s["msg"] = "No paired Bluetooth devices. Pair the headphones in Windows Settings."

    def should_autoconnect(self):
        if self.device_idx is None:
            return False
        return "MOMENTUM" in self.devices[self.device_idx][0].upper() or bool(self.settings.get("address"))

    def report(self):
        s = self.s
        lines = ["M4 Control %s (Windows app)" % L.APP_VERSION,
                 "PC: %s, Python %s" % (platform.platform(), platform.python_version()),
                 "Headphones: %s" % s["conn"],
                 "Model: %s, firmware: %s, hardware: %s, battery: %s" % (
                     s["model"] or "?", s["firmware"] or "?", s["hardware"] or "?",
                     "?" if s["battery"] is None else "%d %%" % s["battery"]),
                 "", "Log (last 80 lines):"] + self.log_lines[-80:]
        return scrub("\n".join(lines))

    def close(self):
        self.worker.put("quit")


# --------------------------------------------------------------- HTTP

class Server(http.server.ThreadingHTTPServer):
    daemon_threads = True

    def __init__(self, core, token):
        super().__init__(("127.0.0.1", 0), Handler)
        self.core = core
        self.token = token


class Handler(http.server.BaseHTTPRequestHandler):
    server_version = "M4Control"

    def log_message(self, *_a):
        pass

    def _allowed(self):
        """Only this app's own page: right Host (no DNS rebinding), same Origin, and the token."""
        port = self.server.server_port
        if self.headers.get("Host") != "127.0.0.1:%d" % port:
            return False
        origin = self.headers.get("Origin")
        if origin and origin != "http://127.0.0.1:%d" % port:
            return False
        q = urllib.parse.parse_qs(urllib.parse.urlparse(self.path).query)
        return secrets.compare_digest(q.get("t", [""])[0], self.server.token)

    def _reply(self, code, body, ctype, extra=None):
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        for k, v in (extra or {}).items():
            self.send_header(k, v)
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if not self._allowed():
            return self._reply(403, b"forbidden", "text/plain")
        path = urllib.parse.urlparse(self.path).path
        if path == "/ping":
            return self._reply(200, b"m4", "text/plain")
        if path == "/":
            with open(UI_FILE, "rb") as f:
                body = f.read()
            return self._reply(200, body, "text/html; charset=utf-8", {
                "Content-Security-Policy": "default-src 'self'; script-src 'unsafe-inline'; "
                                           "style-src 'unsafe-inline'; img-src 'self' data:; "
                                           "connect-src 'self'; frame-ancestors 'none'"})
        if path == "/events":
            return self._events()
        return self._reply(404, b"not found", "text/plain")

    def do_POST(self):
        if not self._allowed() or urllib.parse.urlparse(self.path).path != "/api":
            return self._reply(403, b"forbidden", "text/plain")
        try:
            n = int(self.headers.get("Content-Length", "0"))
            data = json.loads(self.rfile.read(min(n, 65536)) or b"{}")
        except ValueError:
            data = {}
        res = self.server.core.handle(data)
        return self._reply(200, json.dumps(res).encode(), "application/json")

    def _events(self):
        core = self.server.core
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream")
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        q = core.subscribe()
        try:
            self._event("state", json.dumps(core.snapshot()))
            with core.lock:
                lines = json.dumps(core.log_lines[-200:])
            self._event("logs", lines)
            while True:
                try:
                    kind, payload = q.get(timeout=4)
                except queue.Empty:
                    self.wfile.write(b": ping\n\n")
                    self.wfile.flush()
                    continue
                self._event(kind, payload)
        except OSError:
            pass
        finally:
            core.unsubscribe(q)

    def _event(self, kind, payload):
        self.wfile.write(("event: %s\ndata: %s\n\n" % (kind, payload)).encode())
        self.wfile.flush()


# ------------------------------------------------------------ window

def find_browser():
    for env in ("ProgramFiles(x86)", "ProgramFiles", "LOCALAPPDATA"):
        root = os.environ.get(env)
        if not root:
            continue
        for rel in (r"Microsoft\Edge\Application\msedge.exe", r"Google\Chrome\Application\chrome.exe"):
            p = os.path.join(root, rel)
            if os.path.isfile(p):
                return p
    return None


def open_window(url):
    exe = find_browser()
    if exe:
        subprocess.Popen([exe, "--app=" + url, "--window-size=440,920",
                          "--user-data-dir=" + os.path.join(APP_DIR, "window"),
                          "--no-first-run", "--no-default-browser-check", "--disable-extensions"])
    else:
        webbrowser.open(url)


def running_instance():
    """URL of an instance that is already running, else None."""
    try:
        with open(os.path.join(APP_DIR, "running.json"), encoding="utf-8") as f:
            r = json.load(f)
        base = "http://127.0.0.1:%d/" % int(r["port"])
        with urllib.request.urlopen(base + "ping?t=" + r["token"], timeout=1) as resp:
            return base + "?t=" + r["token"] if resp.read() == b"m4" else None
    except (OSError, ValueError, KeyError):
        return None


def lifecycle(core, server, started):
    """Quit when the window has been gone for 10 s (or never opened within 90 s)."""
    seen, idle_since = False, None
    while True:
        time.sleep(1)
        now = time.monotonic()
        if core.client_count():
            seen, idle_since = True, None
            continue
        if seen:
            idle_since = idle_since or now
            if now - idle_since > 10:
                break
        elif now - started > 90:
            break
    server.shutdown()


def main(open_ui=True):
    url = running_instance() if sys.platform.startswith("win") else None
    if url:
        if open_ui:
            open_window(url)
        return
    core = Core()
    token = secrets.token_urlsafe(24)
    server = Server(core, token)
    url = "http://127.0.0.1:%d/?t=%s" % (server.server_port, token)
    run_file = os.path.join(APP_DIR, "running.json")
    os.makedirs(APP_DIR, exist_ok=True)
    with open(run_file, "w", encoding="utf-8") as f:
        json.dump({"port": server.server_port, "token": token}, f)
    if core.should_autoconnect():
        core.handle({"action": "connect"})
    if open_ui:
        open_window(url)
    threading.Thread(target=lifecycle, args=(core, server, time.monotonic()), daemon=True).start()
    try:
        server.serve_forever()
    finally:
        core.close()
        try:
            os.remove(run_file)
        except OSError:
            pass


if __name__ == "__main__":
    main()
