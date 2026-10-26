@echo off
chcp 65001 > nul
echo ====================================================
echo   PinMaster & Shizuku ADB Servisi Baslatiliyor...
echo ====================================================
echo.

set ADB="C:\Users\User\Desktop\Yeni klasör\adb\adb.exe"
if not exist %ADB% (
    set ADB=adb
)

echo Bagli cihaz kontrol ediliyor...
%ADB% devices
echo.

echo 1. Guncel PinMaster APK Kuruluyor...
%ADB% install -r -d "%~dp0PinMaster.apk"

echo.
echo 2. Shizuku Servisi Baslatiliyor...
%ADB% shell "PKG=$(pm path moe.shizuku.privileged.api 2>/dev/null | head -n 1 | cut -d: -f2 | tr -d '\r'); if [ -n \"$PKG\" ]; then LIB=$(dirname \"$PKG\")/lib/arm64/libshizuku.so; if [ -f \"$LIB\" ]; then \"$LIB\"; fi; fi"

echo.
echo 3. PinMaster Kilit Ajani Cihaza Yukleniyor...
%ADB% push "%~dp0pinmaster_agent.sh" /data/local/tmp/pinmaster_agent.sh
%ADB% shell "chmod 755 /data/local/tmp/pinmaster_agent.sh"

echo.
echo 4. PinMaster Yerel Kilit Ajani Baslatiliyor...
%ADB% shell "pkill -f pinmaster_agent; nohup sh /data/local/tmp/pinmaster_agent.sh </dev/null >/dev/null 2>&1 &"

echo.
echo ====================================================
echo   HER SEY HAZIR! Kilit Servisi ve Shizuku Aktif.
echo   Telefon kablosunu cekebilir ve kullanabilirsiniz.
echo ====================================================
pause
