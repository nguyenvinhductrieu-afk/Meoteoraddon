@echo off
setlocal enabledelayedexpansion
title Farm Addon - Obfuscated Build

echo.
echo  ╔══════════════════════════════════════════╗
echo  ║   Farm Addon — Obfuscated Build Tool   ║
echo  ╚══════════════════════════════════════════╝
echo.

:: Check python
where python >nul 2>&1
if errorlevel 1 (
    echo  [ERROR] Python not found! Please install Python 3.x
    pause
    exit /b 1
)

:: ── STEP 1: Inject junk code ───────────────────────────────────────────────
echo  [1/3] Injecting junk code into source files...
python obfuscate.py --apply
if errorlevel 1 (
    echo  [ERROR] Junk injection failed!
    pause
    exit /b 1
)
echo.

:: ── STEP 2: Build + ProGuard ───────────────────────────────────────────────
echo  [2/3] Building and obfuscating JAR...
call gradlew.bat proguardJar --no-daemon
set BUILD_EXIT=%errorlevel%
echo.

:: ── STEP 3: Restore source (always, even if build failed) ─────────────────
echo  [3/3] Restoring original source files...
python obfuscate.py --restore
echo.

:: Check build result
if %BUILD_EXIT% neq 0 (
    echo  ╔══════════════════════════════════════════╗
    echo  ║  [FAILED] Build returned error code %BUILD_EXIT%   ║
    echo  ╚══════════════════════════════════════════╝
    pause
    exit /b %BUILD_EXIT%
)

:: Find the output JAR
set "OUTJAR="
for %%F in ("build\libs\*-obf.jar") do set "OUTJAR=%%F"

echo  ╔══════════════════════════════════════════╗
echo  ║  [SUCCESS] Build complete!               ║
if defined OUTJAR (
    echo  ║  Output: !OUTJAR!
)
echo  ╚══════════════════════════════════════════╝
echo.
pause
