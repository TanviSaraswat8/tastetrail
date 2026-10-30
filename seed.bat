@echo off
REM Reset the database to the original demo data (stop the server first)
call "%~dp0run.bat" --seed
