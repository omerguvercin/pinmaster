#!/system/bin/sh
# PinMaster Dynamic Silent Background Locking Daemon
# Reads target packages, vibrate and delay settings directly from PinMaster

APP_DIR="/storage/emulated/0/Android/data/com.pinmaster.app/files"
TARGETS_FILE="$APP_DIR/pin_targets.txt"
VIB_FILE="$APP_DIR/vibrate.txt"
DELAY_FILE="$APP_DIR/delay.txt"
PREV_PKG=""

# ms cinsinden gecikmeyi saniyeye çevirip sleep için kullan
get_delay_sec() {
    DELAY_MS=$(cat "$DELAY_FILE" 2>/dev/null)
    if [ -z "$DELAY_MS" ] || [ "$DELAY_MS" -lt 100 ] 2>/dev/null; then
        DELAY_MS=350
    fi
    # Basit tamsayı bölümü (bash'ta float yok, awk kullan)
    awk "BEGIN{printf \"%.3f\n\", $DELAY_MS / 1000}"
}

while true; do
    FOCUS=$(dumpsys window 2>/dev/null | grep mCurrentFocus | head -n 1)

    # Kilit ekranı veya bildirim panelindeyken asla kilitleme
    if echo "$FOCUS" | grep -qE "NotificationShade|Keyguard|StatusBar"; then
        sleep 0.25
        continue
    fi

    LINE=$(dumpsys window 2>/dev/null | grep mFocusedApp | grep ActivityRecord | head -n 1)
    if [ -n "$LINE" ]; then
        CURR_PKG=$(echo "$LINE" | sed -E 's/.*u0 ([^/]+)\/.*/\1/')

        # Sadece başka bir uygulamadan / ana ekrandan yeni bir uygulamaya geçildiğinde
        if [ "$CURR_PKG" != "$PREV_PKG" ]; then
            if [ -f "$TARGETS_FILE" ]; then
                if grep -Fxq "$CURR_PKG" "$TARGETS_FILE" 2>/dev/null; then
                    TID=$(echo "$LINE" | sed -E 's/.* t([0-9]+)\}.*/\1/')
                    if [ -n "$TID" ]; then
                        # Sabitleme gecikmesini uygula
                        DELAY_SEC=$(get_delay_sec)
                        sleep "$DELAY_SEC"

                        # Gecikmeden sonra hâlâ aynı uygulama mı açık?
                        LINE2=$(dumpsys window 2>/dev/null | grep mFocusedApp | grep ActivityRecord | head -n 1)
                        CURR_PKG2=$(echo "$LINE2" | sed -E 's/.*u0 ([^/]+)\/.*/\1/')
                        if [ "$CURR_PKG2" = "$CURR_PKG" ]; then
                            am task lock "$TID" 2>/dev/null

                            # Titreşim kontrolü
                            VIB=$(cat "$VIB_FILE" 2>/dev/null)
                            if [ "$VIB" != "0" ]; then
                                cmd vibrator_manager synced oneshot 100 2>/dev/null
                            fi
                        fi
                    fi
                fi
            fi
            PREV_PKG="$CURR_PKG"
        fi
    fi
    sleep 0.2
done
