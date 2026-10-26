#!/system/bin/sh

TARGETS_DEFAULT="/data/local/tmp/pin_targets.txt"
TARGETS_EXT="/storage/emulated/0/Android/data/com.pinmaster.app/files/pin_targets.txt"
TARGETS_INT="/data/user/0/com.pinmaster.app/files/pin_targets.txt"

get_targets_file() {
    if [ -f "$TARGETS_EXT" ]; then
        echo "$TARGETS_EXT"
    elif [ -f "$TARGETS_DEFAULT" ]; then
        echo "$TARGETS_DEFAULT"
    elif [ -f "$TARGETS_INT" ]; then
        echo "$TARGETS_INT"
    else
        echo "$TARGETS_EXT"
    fi
}

CMD_DEFAULT="/data/local/tmp/pin_cmd"
CMD_EXT="/storage/emulated/0/Android/data/com.pinmaster.app/files/pin_cmd"

get_cmd_file() {
    if [ -f "$CMD_DEFAULT" ]; then
        echo "$CMD_DEFAULT"
    elif [ -f "$CMD_EXT" ]; then
        echo "$CMD_EXT"
    else
        echo "$CMD_DEFAULT"
    fi
}

LOG_FILE="/data/local/tmp/pinmaster.log"
CURRENT_LOCKED_TASK=""
LAST_PINNED_PKG=""
UNPIN_COOLDOWN_PKG=""
UNPIN_COOLDOWN_EXPIRE=0
LAST_DEBUG_PKG=""

echo "PinMaster ADB Agent Started at $(date)" > "$LOG_FILE"

while true; do
    # Check for stop / unlock command
    CMD_FILE=$(get_cmd_file)
    if [ -f "$CMD_FILE" ]; then
        CMD=$(cat "$CMD_FILE")
        rm -f "$CMD_FILE"
        rm -f "$CMD_DEFAULT" "$CMD_EXT" 2>/dev/null
        if [ "$CMD" = "stop" ] || [ "$CMD" = "unlock" ]; then
            am task lock stop >> "$LOG_FILE" 2>&1
            CURRENT_LOCKED_TASK=""
            LAST_PINNED_PKG=""
            UNPIN_COOLDOWN_PKG=""
            UNPIN_COOLDOWN_EXPIRE=0
            echo "Lock stopped at $(date)" >> "$LOG_FILE"
            input keyevent KEYCODE_HOME
            sleep 2
            continue
        fi
    fi

    # Read current focused app from window manager (much faster and avoids broken pipes)
    LINE=$(dumpsys window 2>/dev/null | grep mFocusedApp | grep ActivityRecord | head -n 1)
    if [ -n "$LINE" ]; then
        PKG=$(echo "$LINE" | sed -E 's/.*u[0-9]+ ([^/]+)\/.*/\1/')
        TASK=$(echo "$LINE" | sed -E 's/.* t([0-9]+)\}.*/\1/')

        if [ -n "$PKG" ] && [ -n "$TASK" ]; then
            TARGETS=$(get_targets_file)
            LOCK_STATE=$(dumpsys activity 2>/dev/null | grep -m 1 "mLockTaskModeState=" | tr -d ' \r\n')
            NOW=$(date +%s)

            IS_LOCKED=0
            if [ "$LOCK_STATE" = "mLockTaskModeState=PINNED" ] || [ "$LOCK_STATE" = "mLockTaskModeState=LOCKED" ]; then
                IS_LOCKED=1
            fi

            # 1. Detect hardware unpin: was pinned before, but now NONE
            if [ -n "$LAST_PINNED_PKG" ] && [ "$IS_LOCKED" -eq 0 ]; then
                echo "Hardware unpin detected for $LAST_PINNED_PKG (task $CURRENT_LOCKED_TASK)! Navigating to HOME and setting 10s cooldown." >> "$LOG_FILE"
                UNPIN_COOLDOWN_PKG="$LAST_PINNED_PKG"
                UNPIN_COOLDOWN_EXPIRE=$((NOW + 10))
                LAST_PINNED_PKG=""
                CURRENT_LOCKED_TASK=""
                input keyevent KEYCODE_HOME
                sleep 0.5
                continue
            fi

            # 2. Track actively pinned package and task
            if [ "$IS_LOCKED" -eq 1 ]; then
                LAST_PINNED_PKG="$PKG"
                CURRENT_LOCKED_TASK="$TASK"
            fi

            # 3. Check cooldown expiration
            if [ -n "$UNPIN_COOLDOWN_PKG" ]; then
                if [ "$NOW" -ge "$UNPIN_COOLDOWN_EXPIRE" ]; then
                    UNPIN_COOLDOWN_PKG=""
                elif [ "$PKG" = "$UNPIN_COOLDOWN_PKG" ]; then
                    # Still in cooldown, do not touch!
                    sleep 0.3
                    continue
                fi
            fi

            # Log target check occasionally or on change
            if [ "$PKG" != "$LAST_DEBUG_PKG" ]; then
                echo "Focus changed to PKG: $PKG (task: $TASK), targets file: $TARGETS [$(cat "$TARGETS" 2>/dev/null | tr '\n' ' ')]" >> "$LOG_FILE"
                LAST_DEBUG_PKG="$PKG"
            fi

            # 4. If package is in target list
            if [ -f "$TARGETS" ] && grep -Fxq "$PKG" "$TARGETS" 2>/dev/null; then
                # Only lock if NOT in cooldown, NOT already locked, and lock state is NONE
                if [ "$PKG" != "$UNPIN_COOLDOWN_PKG" ] && [ "$CURRENT_LOCKED_TASK" != "$TASK" ] && [ "$IS_LOCKED" -eq 0 ]; then
                    # Short stabilization delay to let the window gain focus
                    sleep 0.25
                    CHECK_LINE=$(dumpsys window 2>/dev/null | grep mFocusedApp | grep ActivityRecord | head -n 1)
                    CHECK_PKG=$(echo "$CHECK_LINE" | sed -E 's/.*u[0-9]+ ([^/]+)\/.*/\1/')

                    if [ "$CHECK_PKG" = "$PKG" ]; then
                        echo "Locking task $TASK for package $PKG at $(date)" >> "$LOG_FILE"
                        am task lock "$TASK" >> "$LOG_FILE" 2>&1
                        CURRENT_LOCKED_TASK="$TASK"
                        LAST_PINNED_PKG="$PKG"

                        # Vibrate if enabled
                        VIB="0"
                        if [ -f "/data/local/tmp/vibrate.txt" ]; then
                            VIB=$(cat "/data/local/tmp/vibrate.txt" 2>/dev/null)
                        elif [ -f "/storage/emulated/0/Android/data/com.pinmaster.app/files/vibrate.txt" ]; then
                            VIB=$(cat "/storage/emulated/0/Android/data/com.pinmaster.app/files/vibrate.txt" 2>/dev/null)
                        fi
                        if [ "$VIB" != "0" ]; then
                            cmd vibrator_manager synced oneshot 80 2>/dev/null
                        fi
                    fi
                fi
            else
                if [ -n "$CURRENT_LOCKED_TASK" ] && [ "$IS_LOCKED" -eq 0 ]; then
                    CURRENT_LOCKED_TASK=""
                    LAST_PINNED_PKG=""
                fi
            fi
        fi
    fi

    sleep 0.3
done
