@echo off
chcp 65001 > nul
echo ====================================================
echo   PinMaster & Shizuku ADB Servisi Başlatılıyor...
echo ====================================================
echo.

set ADB="C:\Users\User\Desktop\Yeni klasör\adb\adb.exe"

%ADB% devices
echo.

echo 1. Guncel PinMaster APK Kuruluyor...
%ADB% install -r -d "%~dp0PinMaster.apk"

echo.
echo 2. Shizuku Servisi Baslatiliyor...
%ADB% shell "/data/app/*moe.shizuku.privileged.api*/lib/arm64/libshizuku.so"

echo.
echo 3. PinMaster Yerel Kilit Ajani Baslatiliyor...
%ADB% shell "pkill -f pinmaster_agent; nohup sh /data/local/tmp/pinmaster_agent.sh > /dev/null 2>&1 &"

echo.
echo ====================================================
echo   HER SEY HAZIR! Kilit Servisi ve Shizuku Aktif.
echo   Telefon kablosunu cekebilir ve kullanabilirsiniz.
echo ====================================================
pause
