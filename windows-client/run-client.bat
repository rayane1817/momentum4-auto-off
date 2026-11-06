@echo off
setlocal
cd /d "%~dp0"
python tray_app.py
if errorlevel 1 pause

