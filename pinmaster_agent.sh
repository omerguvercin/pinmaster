#!/system/bin/sh

TARGETS="/storage/emulated/0/Android/data/com.pinmaster.app/files/pin_targets.txt"
CMD_FILE="/storage/emulated/0/Android/data/com.pinmaster.app/files/pin_cmd"
LOG_FILE="/data/local/tmp/pinmaster.log"
CURRENT_LOCKED_TASK=""

echo "PinMaster ADB Agent Started at $(date)" > "$LOG_FILE"

while true; do
    # Check for stop / unlock command
    if [ -f "$CMD_FILE" ]; then
        CMD=$(cat "$CMD_FILE")
        rm -f "$CMD_FILE"
        if [ "$CMD" = "stop" ] || [ "$CMD" = "unlock" ]; then
            am task lock stop >> "$LOG_FILE" 2>&1
            CURRENT_LOCKED_TASK=""
            echo "Lock stopped at $(date)" >> "$LOG_FILE"
            input keyevent KEYCODE_HOME
            sleep 2
            continue
        fi
    fi

    # Read current top activity
    TOP=$(dumpsys activity activities | grep -m 1 "ResumedActivity: ActivityRecord")
    if [ -n "$TOP" ]; then
        PKG=$(echo "$TOP" | sed -n 's/.* u[0-9]* \([^/]*\).*/\1/p')
        TASK=$(echo "$TOP" | sed -n 's/.* t\([0-9]*\)}.*/\1/p')

        if [ -n "$PKG" ] && [ -n "$TASK" ]; then
            # If package is in target list
            if [ -f "$TARGETS" ] && grep -Fxq "$PKG" "$TARGETS"; then
                if [ "$CURRENT_LOCKED_TASK" != "$TASK" ]; then
                    echo "Locking task $TASK for package $PKG at $(date)" >> "$LOG_FILE"
                    am task lock "$TASK" >> "$LOG_FILE" 2>&1
                    CURRENT_LOCKED_TASK="$TASK"
                fi
            else
                if [ -n "$CURRENT_LOCKED_TASK" ]; then
                    STATE=$(dumpsys activity | grep -m 1 "mLockTaskModeState=" | tr -d ' \r\n')
                    if [ "$STATE" = "mLockTaskModeState=NONE" ]; then
                        CURRENT_LOCKED_TASK=""
                    fi
                fi
            fi
        fi
    fi

    sleep 0.4
done
