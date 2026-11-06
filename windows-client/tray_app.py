#!/usr/bin/env python3
"""Windows tray client for the MOMENTUM 4 GAIA client."""

from __future__ import annotations

import json
import os
import re
import sys
import threading
import time
from pathlib import Path
import tkinter as tk
from tkinter import messagebox, ttk

import pystray
from PIL import Image, ImageDraw

import gaia_client as Gaia


APP_NAME = "Momentum 4 Companion"
APP_DATA = Path(os.environ.get("LOCALAPPDATA", Path.home())) / "Momentum4Companion"
SETTINGS = APP_DATA / "settings.json"
LOGS = APP_DATA / "logs"
ADDRESS_RE = re.compile(r"^(?:[0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")

# dB x 10 for bands 63 Hz, 250 Hz, 1 kHz, 4 kHz, 8 kHz. These are the
# Android companion's verified Smart Control presets, in its cycle order.
EQ_PRESETS = (
    ("Neutral", (0, 0, 0, 0, 0)),
    ("Rock", (0, 20, 25, 15, -20)),
    ("Pop", (0, -25, 0, 25, 0)),
    ("Dance", (35, 20, -15, 15, 30)),
    ("Hip-Hop", (30, 15, -15, 0, -15)),
    ("Classical", (-20, -15, 0, 35, 40)),
    ("Movie", (0, 0, 20, 20, -20)),
    ("Jazz", (-32, 0, 22, 22, 0)),
)
NOISE_MODES = ("Adaptive", "Custom", "Off")
TIMER_OPTIONS = (("Never", 0), ("15 minutes", 900), ("30 minutes", 1800), ("60 minutes", 3600))


def load_settings() -> dict:
    try:
        with SETTINGS.open("r", encoding="utf-8") as stream:
            values = json.load(stream)
        if not isinstance(values, dict):
            return {}
    except (OSError, json.JSONDecodeError):
        return {}
    return values


def save_settings(address: str, eq_index: int, noise_mode: int) -> None:
    APP_DATA.mkdir(parents=True, exist_ok=True)
    temporary = SETTINGS.with_suffix(".tmp")
    temporary.write_text(json.dumps({
        "address": address,
        "eq_index": eq_index,
        "noise_mode": noise_mode,
    }, indent=2), encoding="utf-8")
    temporary.replace(SETTINGS)


def tray_image() -> Image.Image:
    image = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw.rounded_rectangle((3, 3, 61, 61), radius=17, fill="#17202c")
    draw.ellipse((17, 17, 47, 47), outline="#88e0c3", width=4)
    draw.arc((24, 24, 40, 40), start=200, end=340, fill="#88e0c3", width=3)
    draw.ellipse((29, 28, 35, 34), fill="#f4f0e8")
    return image


class TrayClient:
    def __init__(self) -> None:
        self.root = tk.Tk()
        self.root.withdraw()
        self.root.title(APP_NAME)
        self.root.configure(bg="#111821")
        self.root.resizable(False, False)
        self.root.overrideredirect(True)
        self.root.attributes("-topmost", True)
        self.root.protocol("WM_DELETE_WINDOW", self.hide)
        self.busy = False
        self.closing = False
        self._drag = None

        settings = load_settings()
        self.address = tk.StringVar(value=str(settings.get("address", "")))
        self.eq_index = self._valid_index(settings.get("eq_index"), len(EQ_PRESETS))
        self.noise_mode = self._valid_index(settings.get("noise_mode"), len(NOISE_MODES))
        self.status = tk.StringVar(value="Pair your headphones with Windows, then refresh.")
        self.battery = tk.StringVar(value="--")
        self.timer_status = tk.StringVar(value="--")
        self.eq_label = tk.StringVar()
        self.noise_label = tk.StringVar()

        self._build_window()
        self._draw_quick_controls()
        self._build_tray()
        self.show()

    @staticmethod
    def _valid_index(value, count: int) -> int:
        try:
            index = int(value)
        except (TypeError, ValueError):
            return -1
        return index if 0 <= index < count else -1

    def _build_window(self) -> None:
        outer = tk.Frame(self.root, bg="#111821", highlightbackground="#354354", highlightthickness=1)
        outer.pack(fill="both", expand=True)

        header = tk.Frame(outer, bg="#17202c", padx=18, pady=13)
        header.pack(fill="x")
        header.bind("<ButtonPress-1>", self._start_drag)
        header.bind("<B1-Motion>", self._drag_window)
        title = tk.Label(header, text="MOMENTUM 4", fg="#f4f0e8", bg="#17202c",
                         font=("Segoe UI", 11, "bold"))
        title.pack(side="left")
        tk.Label(header, text="COMPANION", fg="#88e0c3", bg="#17202c",
                 font=("Segoe UI", 8, "bold")).pack(side="left", padx=(9, 0), pady=(2, 0))
        close = tk.Label(header, text="×", fg="#b7c1ce", bg="#17202c", cursor="hand2",
                         font=("Segoe UI", 17))
        close.pack(side="right")
        close.bind("<Button-1>", lambda _event: self.hide())

        body = tk.Frame(outer, bg="#111821", padx=18, pady=14)
        body.pack(fill="both", expand=True)

        tk.Label(body, text="QUICK CONTROLS", fg="#8492a3", bg="#111821",
                 font=("Segoe UI", 8, "bold")).pack(anchor="w")
        tiles = tk.Frame(body, bg="#111821")
        tiles.pack(fill="x", pady=(8, 12))
        self.eq_tile = self._quick_tile(tiles, "EQ", self.eq_label, self.cycle_eq)
        self.eq_tile.pack(side="left", expand=True, fill="x", padx=(0, 7))
        self.noise_tile = self._quick_tile(tiles, "ANC", self.noise_label, self.cycle_noise)
        self.noise_tile.pack(side="left", expand=True, fill="x", padx=(7, 0))

        tk.Label(body, text="Click a circle to cycle. The saved labels show the last mode set by this PC.",
                 fg="#8492a3", bg="#111821", wraplength=330, justify="left",
                 font=("Segoe UI", 8)).pack(anchor="w", pady=(0, 15))

        tk.Label(body, text="HEADPHONE STATUS", fg="#8492a3", bg="#111821",
                 font=("Segoe UI", 8, "bold")).pack(anchor="w")
        status_card = tk.Frame(body, bg="#1b2633", padx=14, pady=10)
        status_card.pack(fill="x", pady=(7, 9))
        line = tk.Frame(status_card, bg="#1b2633")
        line.pack(fill="x")
        self._value_card(line, "BATTERY", self.battery, "%")
        self._value_card(line, "AUTO POWER-OFF", self.timer_status, "")
        self.refresh_button = self._button(body, "Refresh status", self.refresh, primary=True)
        self.refresh_button.pack(fill="x")

        tk.Label(body, text="AUTO POWER-OFF TIMER", fg="#8492a3", bg="#111821",
                 font=("Segoe UI", 8, "bold")).pack(anchor="w", pady=(14, 6))
        timer_row = tk.Frame(body, bg="#111821")
        timer_row.pack(fill="x")
        self.timer = tk.StringVar(value="15 minutes")
        self.timer_combo = ttk.Combobox(timer_row, textvariable=self.timer, state="readonly",
                                        values=[label for label, _seconds in TIMER_OPTIONS], width=18)
        self.timer_combo.pack(side="left", fill="x", expand=True, ipady=4)
        self.apply_button = self._button(timer_row, "Apply", self.apply_timer, primary=True)
        self.apply_button.pack(side="left", padx=(9, 0))

        tk.Label(body, text="BLUETOOTH ADDRESS", fg="#8492a3", bg="#111821",
                 font=("Segoe UI", 8, "bold")).pack(anchor="w", pady=(13, 6))
        address_row = tk.Frame(body, bg="#111821")
        address_row.pack(fill="x")
        self.address_entry = tk.Entry(address_row, textvariable=self.address, relief="flat",
                                      bg="#1b2633", fg="#f4f0e8", insertbackground="#f4f0e8",
                                      font=("Consolas", 10), highlightthickness=1,
                                      highlightbackground="#354354", highlightcolor="#88e0c3")
        self.address_entry.pack(side="left", fill="x", expand=True, ipady=7, padx=(0, 9))
        self._button(address_row, "Save", self.save_address).pack(side="left")
        tk.Label(body, text="Pair in Windows Bluetooth settings first. Close other headset control apps before connecting.",
                 fg="#8492a3", bg="#111821", wraplength=330, justify="left",
                 font=("Segoe UI", 8)).pack(anchor="w", pady=(5, 0))

        self.power_button = self._button(body, "Power off headphones", self.power_off, danger=True)
        self.power_button.pack(fill="x", pady=(12, 8), ipady=1)
        tk.Label(body, textvariable=self.status, fg="#c0cad6", bg="#111821", wraplength=330,
                 justify="left", font=("Segoe UI", 8)).pack(anchor="w")
        self.root.bind("<Escape>", lambda _event: self.hide())

    def _quick_tile(self, parent: tk.Widget, short_name: str, label: tk.StringVar, command) -> tk.Frame:
        frame = tk.Frame(parent, bg="#1b2633", padx=8, pady=8)
        circle = tk.Canvas(frame, width=104, height=88, bg="#1b2633", highlightthickness=0,
                           cursor="hand2")
        fill = "#3a4b5d"
        circle.create_oval(10, 2, 94, 86, fill=fill, outline="#50677c", width=1, tags=("disc",))
        circle.create_text(52, 44, text=short_name, fill="#e9f2f6",
                           font=("Segoe UI", 16, "bold"), tags=("glyph",))
        circle.pack()
        circle.bind("<Button-1>", lambda _event: command())
        circle.bind("<Enter>", lambda _event: circle.itemconfigure("disc", fill="#496176"))
        circle.bind("<Leave>", lambda _event: circle.itemconfigure("disc", fill=fill))
        label_widget = tk.Label(frame, textvariable=label, fg="#f4f0e8", bg="#1b2633",
                                font=("Segoe UI", 9, "bold"), wraplength=130)
        label_widget.pack(pady=(5, 1))
        tk.Label(frame, text="CLICK TO CYCLE", fg="#8492a3", bg="#1b2633",
                 font=("Segoe UI", 7, "bold")).pack()
        frame._cycle_canvas = circle
        return frame

    def _draw_quick_controls(self) -> None:
        eq_name = EQ_PRESETS[self.eq_index][0] if self.eq_index >= 0 else "Tap to set"
        noise_name = NOISE_MODES[self.noise_mode] if self.noise_mode >= 0 else "Tap to set"
        self.eq_label.set(f"EQ · {eq_name}")
        self.noise_label.set(f"Noise · {noise_name}")
        eq_color = "#5a4b78" if self.eq_index >= 0 else "#3a4b5d"
        noise_color = "#478673" if self.noise_mode == 0 else "#586f9c" if self.noise_mode == 1 else "#3a4b5d"
        self.eq_tile._cycle_canvas.itemconfigure("disc", fill=eq_color)
        self.noise_tile._cycle_canvas.itemconfigure("disc", fill=noise_color)

    def _value_card(self, parent: tk.Widget, label: str, value: tk.StringVar, suffix: str) -> None:
        cell = tk.Frame(parent, bg="#1b2633")
        cell.pack(side="left", fill="x", expand=True)
        tk.Label(cell, text=label, fg="#8492a3", bg="#1b2633",
                 font=("Segoe UI", 7, "bold")).pack(anchor="w")
        value_row = tk.Frame(cell, bg="#1b2633")
        value_row.pack(anchor="w", pady=(4, 0))
        tk.Label(value_row, textvariable=value, fg="#f4f0e8", bg="#1b2633",
                 font=("Segoe UI", 20, "bold")).pack(side="left")
        if suffix:
            tk.Label(value_row, text=suffix, fg="#88e0c3", bg="#1b2633",
                     font=("Segoe UI", 10)).pack(side="left", padx=(3, 0), pady=(5, 0))

    def _button(self, parent: tk.Widget, label: str, command, *, primary: bool = False,
                danger: bool = False) -> tk.Button:
        bg = "#88e0c3" if primary else "#603b47" if danger else "#253344"
        fg = "#111821" if primary else "#ffccd4" if danger else "#e3e9ef"
        return tk.Button(parent, text=label, command=command, relief="flat", borderwidth=0,
                         padx=13, pady=8, bg=bg, fg=fg, activebackground="#a5efd8" if primary else bg,
                         activeforeground=fg, cursor="hand2", font=("Segoe UI", 9, "bold"))

    def _build_tray(self) -> None:
        menu = pystray.Menu(
            pystray.MenuItem("Open Momentum 4 Companion", self._tray_open, default=True),
            pystray.MenuItem("Refresh status", self._tray_refresh),
            pystray.Menu.SEPARATOR,
            pystray.MenuItem("Exit", self._tray_exit),
        )
        self.icon = pystray.Icon("Momentum4Companion", tray_image(), APP_NAME, menu)
        self.icon.run_detached()

    def _tray_open(self, _icon=None, _item=None) -> None:
        if not self.closing:
            self.root.after(0, self.show)

    def _tray_refresh(self, _icon=None, _item=None) -> None:
        if not self.closing:
            self.root.after(0, self.refresh)

    def _tray_exit(self, _icon=None, _item=None) -> None:
        if not self.closing:
            self.root.after(0, self.exit)

    def _start_drag(self, event) -> None:
        self._drag = (event.x_root - self.root.winfo_x(), event.y_root - self.root.winfo_y())

    def _drag_window(self, event) -> None:
        if self._drag:
            self.root.geometry(f"+{event.x_root - self._drag[0]}+{event.y_root - self._drag[1]}")

    def show(self) -> None:
        self.root.deiconify()
        self.root.update_idletasks()
        width, height = 390, 650
        x = max(0, self.root.winfo_screenwidth() - width - 14)
        y = max(0, self.root.winfo_screenheight() - height - 65)
        self.root.geometry(f"{width}x{height}+{x}+{y}")
        self.root.lift()
        self.root.focus_force()

    def hide(self) -> None:
        self.root.withdraw()

    def exit(self) -> None:
        self.closing = True
        try:
            self.icon.stop()
        finally:
            self.root.destroy()

    def save_address(self) -> None:
        address = self.address.get().strip().upper()
        if address and not ADDRESS_RE.fullmatch(address):
            messagebox.showerror("Check the address", "Enter a Bluetooth address in the form AA:BB:CC:DD:EE:FF.",
                                 parent=self.root)
            return
        try:
            save_settings(address, self.eq_index, self.noise_mode)
        except OSError as error:
            messagebox.showerror("Could not save", str(error), parent=self.root)
            return
        self.address.set(address)
        self.status.set("Address saved on this PC." if address else "Saved address cleared.")

    def _save_local_state(self):
        try:
            save_settings(self.address.get().strip().upper(), self.eq_index, self.noise_mode)
        except OSError as error:
            return str(error)
        return None

    def _with_link(self, address: str, action):
        if not address:
            raise ValueError("Enter and save the headset's Bluetooth address first.")
        if not ADDRESS_RE.fullmatch(address):
            raise ValueError("Enter a Bluetooth address in the form AA:BB:CC:DD:EE:FF.")
        LOGS.mkdir(parents=True, exist_ok=True)
        logger = Gaia.Logger(str(LOGS))
        link = Gaia.Link(address, logger)
        try:
            logger.note("connecting to saved headset")
            if not link.connect():
                logger.note("could not connect. Is Smart Control Plus force-stopped? Are the headphones on and paired?")
                raise RuntimeError("Could not connect. Check that the headphones are on, paired, and not in use by another app.")
            return action(link)
        finally:
            link.close()
            logger.note("done")
            logger.close()

    @staticmethod
    def _get(link, command: int, payload: bytes = b"") -> bytes:
        frame = Gaia.guarded_frame(command, payload)
        reply, _frames = Gaia.transact(link, frame, command, timeout=2.0, settle=0)
        if reply is None:
            raise RuntimeError(f"No response for {command:04X}.")
        if reply.type == 3:
            raise RuntimeError(f"Headphones refused {command:04X}: {reply}.")
        return reply.payload

    @staticmethod
    def _send_frames(link, frames: list[bytes]) -> None:
        for frame in frames:
            problem = Gaia.single_frame_problem(frame) or Gaia.blocked_reason(frame)
            if problem:
                raise ValueError(f"Command refused: {problem}.")
            if not link.send(frame):
                raise RuntimeError("The Bluetooth command was blocked before sending.")
            time.sleep(0.06)
        time.sleep(0.4)

    def _background(self, message: str, work, done) -> None:
        if self.busy:
            return
        self.busy = True
        self.status.set(message)
        self._set_actions_enabled(False)

        def runner() -> None:
            try:
                result, error = work(), None
            except Exception as exception:
                result, error = None, str(exception)
            if not self.closing:
                self.root.after(0, lambda: finish(result, error))

        def finish(result, error) -> None:
            self.busy = False
            self._set_actions_enabled(True)
            done(result, error)

        threading.Thread(target=runner, name="gaia-command", daemon=True).start()

    def _set_actions_enabled(self, enabled: bool) -> None:
        state = tk.NORMAL if enabled else tk.DISABLED
        for widget in (self.refresh_button, self.apply_button, self.power_button,
                       self.eq_tile._cycle_canvas, self.noise_tile._cycle_canvas):
            widget.configure(state=state)

    def refresh(self) -> None:
        address = self.address.get().strip().upper()

        def work():
            def read(link):
                battery = self._get(link, 0x0603)
                timer = self._get(link, 0x0601, b"\x00")
                if not battery or len(timer) < 3:
                    raise RuntimeError("The headset returned incomplete status data.")
                return battery[-1], (timer[1] << 8) | timer[2]
            return self._with_link(address, read)

        def done(values, error):
            if error:
                self.status.set(error)
                return
            battery, seconds = values
            self.battery.set(str(battery))
            self.timer_status.set("Never" if seconds == 0 else f"{seconds // 60} min")
            for label, option_seconds in TIMER_OPTIONS:
                if seconds == option_seconds:
                    self.timer.set(label)
                    break
            self.status.set("Headphones connected. EQ and noise labels show the last mode set by this PC.")

        self._background("Connecting and reading status…", work, done)

    def cycle_eq(self) -> None:
        index = (self.eq_index + 1) % len(EQ_PRESETS)
        name, gains = EQ_PRESETS[index]
        address = self.address.get().strip().upper()

        def work():
            def apply(link):
                frames = [Gaia.guarded_frame(0x1001, bytes((band, gain & 0xFF)))
                          for band, gain in enumerate(gains)]
                self._send_frames(link, frames)
                return index, name
            return self._with_link(address, apply)

        def done(result, error):
            if error:
                self.status.set(error)
                return
            self.eq_index, applied_name = result
            save_error = self._save_local_state()
            self._draw_quick_controls()
            suffix = f" The selection could not be saved: {save_error}" if save_error else ""
            self.status.set(f"EQ set to {applied_name}.{suffix}")

        self._background(f"Setting EQ to {name}…", work, done)

    def cycle_noise(self) -> None:
        mode = (self.noise_mode + 1) % len(NOISE_MODES)
        name = NOISE_MODES[mode]
        address = self.address.get().strip().upper()

        def work():
            def apply(link):
                if mode == 0:        # Adaptive
                    commands = ((0x1A04, b"\x01"), (0x1A00, b"\x03\x01"))
                elif mode == 1:      # Custom
                    commands = ((0x1A00, b"\x03\x00"), (0x1A04, b"\x01"))
                else:                # Off
                    commands = ((0x1A00, b"\x03\x00"), (0x1A04, b"\x00"))
                frames = [Gaia.guarded_frame(command, payload) for command, payload in commands]
                self._send_frames(link, frames)
                return mode, name
            return self._with_link(address, apply)

        def done(result, error):
            if error:
                self.status.set(error)
                return
            self.noise_mode, applied_name = result
            save_error = self._save_local_state()
            self._draw_quick_controls()
            suffix = f" The selection could not be saved: {save_error}" if save_error else ""
            self.status.set(f"Noise control set to {applied_name}.{suffix}")

        self._background(f"Setting noise control to {name}…", work, done)

    def apply_timer(self) -> None:
        seconds = dict(TIMER_OPTIONS).get(self.timer.get())
        if seconds is None:
            return
        address = self.address.get().strip().upper()
        label = self.timer.get()
        if not messagebox.askyesno("Change auto power-off timer",
                                   f"Set the headphones to switch off after {label}?", parent=self.root):
            return

        def work():
            def apply(link):
                payload = bytes((0, (seconds >> 8) & 0xFF, seconds & 0xFF))
                frame = Gaia.guarded_frame(0x0600, payload)
                reply, _frames = Gaia.transact(link, frame, 0x0600, timeout=2.0, settle=0)
                if reply is None or reply.type == 3:
                    raise RuntimeError("The headset did not accept the timer setting.")
                actual = self._get(link, 0x0601, b"\x00")
                if len(actual) < 3:
                    raise RuntimeError("The headset returned an incomplete timer value.")
                return (actual[1] << 8) | actual[2]
            return self._with_link(address, apply)

        def done(actual, error):
            if error:
                self.status.set(error)
                return
            self.timer_status.set("Never" if actual == 0 else f"{actual // 60} min")
            self.status.set(f"Auto power-off timer set to {actual} seconds.")

        self._background("Updating the timer…", work, done)

    def power_off(self) -> None:
        confirmed = messagebox.askyesno(
            "Power off headphones",
            "This switches the headphones fully off and disconnects Bluetooth.\n\n"
            "Press the headphone power button to turn them on again.",
            icon="warning", parent=self.root)
        if not confirmed:
            return
        address = self.address.get().strip().upper()

        def work():
            def apply(link):
                # This documented action is intentionally sent only by this explicit app button.
                frame = Gaia.build(Gaia.SENNHEISER, 0x0605)
                if not link.send(frame):
                    raise RuntimeError("The power-off command was blocked before sending.")
                time.sleep(0.4)
                return True
            return self._with_link(address, apply)

        def done(_result, error):
            if error:
                self.status.set(error)
                return
            self.battery.set("--")
            self.status.set("Power-off command sent. Press the headphone button to wake them.")

        self._background("Sending the power-off command…", work, done)


def main() -> None:
    if not sys.platform.startswith("win"):
        raise SystemExit("The tray client currently requires Windows 10 or later.")
    TrayClient().root.mainloop()


if __name__ == "__main__":
    main()

