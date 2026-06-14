@echo off
cd /d "%~dp0.."
java -cp "out;lib/*" MiniProjectApp %1 > server.out.log 2>&1
