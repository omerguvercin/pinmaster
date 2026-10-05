@echo off
chcp 65001 > nul
echo ====================================================
echo   PinMaster ADB Yerel Kilit Ajanı Başlatılıyor...
echo ====================================================
echo.

set ADB="C:\Users\User\Desktop\Yeni klasör\adb\adb.exe"

%ADB% devices
echo.

echo Ajan cihaza aktarılıyor ve arka planda başlatılıyor...
%ADB% shell "pkill -f pinmaster_agent; nohup sh /data/local/tmp/pinmaster_agent.sh > /dev/null 2>&1 &"

echo.
%ADB% shell "ps -ef | grep pinmaster_agent"
echo.
echo ====================================================
echo   PinMaster ADB Ajanı Başarıyla Başlatıldı!
echo   Telefon kablosunu çekseniz dahi çalışmaya devam eder.
echo ====================================================
pause
