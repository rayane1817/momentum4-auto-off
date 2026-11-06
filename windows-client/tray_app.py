#!/usr/bin/env python3
"""Small Windows tray client for the MOMENTUM 4 GAIA command-line client."""

from __future__ import annotations

import json
import os
import re
import subprocess
import sys
import threading
from pathlib import Path
import tkinter as tk
from tkinter import messagebox, ttk

import pystray
from PIL import Image, ImageDraw


APP_NAME = "Momentum 4 Companion"
HERE = Path(__file__).resolve().parent
CLI = HERE / "gaia_client.py"
APP_DATA = Path(os.environ.get("LOCALAPPDATA", Path.home())) / "Momentum4Companion"
SETTINGS = APP_DATA / "settings.json"
LOGS = APP_DATA / "logs"
TIMER_OPTIONS = (("Never", 0), ("15 minutes", 900), ("30 minutes", 1800), ("60 minutes", 3600))
ADDRESS_RE = re.compile(r"^(?:[0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")


def load_settings() -> dict:
    try:
        with SETTINGS.open("r", encoding="utf-8") as stream:
            settings = json.load(stream)
        return settings if isinstance(settings, dict) else {}
    except (OSError, json.JSONDecodeError):
        return {}


def save_settings(address: str) -> None:
    APP_DATA.mkdir(parents=True, exist_ok=True)
    temporary = SETTINGS.with_suffix(".tmp")
    temporary.write_text(json.dumps({"address": address}, indent=2), encoding="utf-8")
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
        self.root.attributes("-topmost", True)
        self.root.protocol("WM_DELETE_WINDOW", self.hide)
        self.busy = False
        self.closing = False
        self._drag = None

        self.settings = load_settings()
        self.address = tk.StringVar(value=self.settings.get("address", ""))
        self.timer = tk.StringVar(value="15 minutes")
        self.status = tk.StringVar(value="Pair your headphones with Windows, then refresh.")
        self.battery = tk.StringVar(value="--")
        self.timer_status = tk.StringVar(value="--")

        self._build_window()
        self._build_tray()
        self.show()

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

        body = tk.Frame(outer, bg="#111821", padx=18, pady=16)
        body.pack(fill="both", expand=True)

        tk.Label(body, text="HEADPHONE STATUS", fg="#8492a3", bg="#111821",
                 font=("Segoe UI", 8, "bold")).pack(anchor="w")
        status_card = tk.Frame(body, bg="#1b2633", padx=14, pady=12)
        status_card.pack(fill="x", pady=(8, 14))
        line = tk.Frame(status_card, bg="#1b2633")
        line.pack(fill="x")
        self._value_card(line, "BATTERY", self.battery, "%")
        self._value_card(line, "AUTO POWER-OFF", self.timer_status, "")
        self._button(body, "Refresh status", self.refresh, primary=True).pack(fill="x")

        tk.Label(body, text="AUTO POWER-OFF TIMER", fg="#8492a3", bg="#111821",
                 font=("Segoe UI", 8, "bold")).pack(anchor="w", pady=(18, 7))
        timer_row = tk.Frame(body, bg="#111821")
        timer_row.pack(fill="x")
        self.timer_combo = ttk.Combobox(timer_row, textvariable=self.timer, state="readonly",
                                        values=[label for label, _seconds in TIMER_OPTIONS], width=18)
        self.timer_combo.pack(side="left", fill="x", expand=True, ipady=5)
        self.apply_button = self._button(timer_row, "Apply", self.apply_timer, primary=True)
        self.apply_button.pack(side="left", padx=(9, 0))

        tk.Label(body, text="BLUETOOTH ADDRESS", fg="#8492a3", bg="#111821",
                 font=("Segoe UI", 8, "bold")).pack(anchor="w", pady=(17, 7))
        address_row = tk.Frame(body, bg="#111821")
        address_row.pack(fill="x")
        self.address_entry = tk.Entry(address_row, textvariable=self.address, relief="flat",
                                      bg="#1b2633", fg="#f4f0e8", insertbackground="#f4f0e8",
                                      font=("Consolas", 10), highlightthickness=1,
                                      highlightbackground="#354354", highlightcolor="#88e0c3")
        self.address_entry.pack(side="left", fill="x", expand=True, ipady=8, padx=(0, 9))
        self._button(address_row, "Save", self.save_address).pack(side="left")
        tk.Label(body, text="Pair in Windows Settings first. Stop other headset control apps before connecting.",
                 fg="#8492a3", bg="#111821", wraplength=330, justify="left",
                 font=("Segoe UI", 8)).pack(anchor="w", pady=(6, 0))

        self.power_button = self._button(body, "Power off headphones", self.power_off, danger=True)
        self.power_button.pack(fill="x", pady=(17, 11), ipady=2)
        tk.Label(body, textvariable=self.status, fg="#c0cad6", bg="#111821", wraplength=330,
                 justify="left", font=("Segoe UI", 9)).pack(anchor="w")

        self.root.bind("<Escape>", lambda _event: self.hide())

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
                         padx=13, pady=9, bg=bg, fg=fg, activebackground="#a5efd8" if primary else bg,
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
            x = event.x_root - self._drag[0]
            y = event.y_root - self._drag[1]
            self.root.geometry(f"+{x}+{y}")

    def show(self) -> None:
        self.root.deiconify()
        self.root.update_idletasks()
        width, height = 390, 560
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
            save_settings(address)
        except OSError as error:
            messagebox.showerror("Could not save", str(error), parent=self.root)
            return
        self.address.set(address)
        self.status.set("Address saved on this PC." if address else "Saved address cleared.")

    def _command(self, command: str, *args: str) -> list[str]:
        LOGS.mkdir(parents=True, exist_ok=True)
        argv = [sys.executable, str(CLI), command, *args]
        address = self.address.get().strip().upper()
        if address:
            if not ADDRESS_RE.fullmatch(address):
                raise ValueError("Enter a Bluetooth address in the form AA:BB:CC:DD:EE:FF.")
            argv.extend(["--addr", address])
        argv.extend(["--log", str(LOGS)])
        result = subprocess.run(argv, cwd=HERE, capture_output=True, text=True,
                                timeout=30, creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        output = (result.stdout + "\n" + result.stderr).strip()
        if address:
            output = output.replace(address, "[saved Bluetooth address]")
        if result.returncode:
            raise RuntimeError(output or f"The client exited with code {result.returncode}.")
        return output.splitlines()

    def _background(self, message: str, work, done) -> None:
        if self.busy:
            return
        self.busy = True
        self.status.set(message)
        for widget in (self.apply_button, self.power_button):
            widget.configure(state="disabled")

        def runner() -> None:
            try:
                result, error = work(), None
            except Exception as exception:  # show the actionable CLI error in the popup
                result, error = None, str(exception)
            if not self.closing:
                self.root.after(0, lambda: finish(result, error))

        def finish(result, error) -> None:
            self.busy = False
            for widget in (self.apply_button, self.power_button):
                widget.configure(state="normal")
            done(result, error)

        threading.Thread(target=runner, name="gaia-command", daemon=True).start()

    def refresh(self) -> None:
        def work():
            battery = self._command("battery")
            timer = self._command("get-timer", "0")
            return "\n".join(battery + timer)

        def done(output, error):
            if error:
                self.status.set(error)
                return
            battery_match = re.search(r"battery\s+(\d+)\s*%", output, re.IGNORECASE)
            timer_match = re.search(r"auto power-off\s*=\s*(\d+)\s*s", output, re.IGNORECASE)
            if battery_match:
                self.battery.set(battery_match.group(1))
            if timer_match:
                seconds = int(timer_match.group(1))
                self.timer_status.set("Never" if seconds == 0 else f"{seconds // 60} min")
                for label, option_seconds in TIMER_OPTIONS:
                    if seconds == option_seconds:
                        self.timer.set(label)
                        break
            if battery_match or timer_match:
                self.status.set("Headphones connected. Status is up to date.")
            else:
                self.status.set("Connected, but the headset did not return battery or timer values.")

        self._background("Connecting and reading status…", work, done)

    def apply_timer(self) -> None:
        seconds = dict(TIMER_OPTIONS).get(self.timer.get())
        if seconds is None:
            return

        def work():
            return "\n".join(self._command("set-timer", "0", str(seconds)))

        def done(output, error):
            if error:
                self.status.set(error)
                return
            match = re.search(r"auto power-off\s*=\s*(\d+)\s*s", output, re.IGNORECASE)
            self.timer_status.set("Never" if seconds == 0 else f"{seconds // 60} min")
            self.status.set(f"Timer set to {match.group(1)} seconds." if match else "Timer command sent.")

        label = self.timer.get()
        if not messagebox.askyesno("Change auto power-off timer",
                                   f"Set the headphones to switch off after {label}?", parent=self.root):
            return
        self._background("Updating the timer…", work, done)

    def power_off(self) -> None:
        confirmed = messagebox.askyesno(
            "Power off headphones",
            "This switches the headphones fully off and disconnects Bluetooth.\n\n"
            "You will need to press the headphone power button to turn them on again.",
            icon="warning", parent=self.root)
        if not confirmed:
            return

        def work():
            return "\n".join(self._command("raw", "FF 03 00 00 04 95 06 05"))

        def done(_output, error):
            self.status.set(error if error else "Power-off command sent. Press the headphone button to wake them.")
            if not error:
                self.battery.set("--")

        self._background("Sending the power-off command…", work, done)


def main() -> None:
    if not sys.platform.startswith("win"):
        raise SystemExit("The tray client currently requires Windows 10 or later.")
    TrayClient().root.mainloop()


if __name__ == "__main__":
    main()

