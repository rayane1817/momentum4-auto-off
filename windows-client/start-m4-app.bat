@echo off
rem Starts M4 Control without a console window. Needs Python 3.9+ from python.org.
cd /d "%~dp0"
where pythonw >nul 2>nul && (start "" pythonw m4_app.py) || (start "" pyw m4_app.py)
