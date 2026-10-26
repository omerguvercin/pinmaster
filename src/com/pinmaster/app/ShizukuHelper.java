package com.pinmaster.app;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;
import moe.shizuku.server.IShizukuService;
import rikka.shizuku.Shizuku;

public class ShizukuHelper {

    private static final String TAG = "ShizukuHelper";
    public static final int REQUEST_CODE_SHIZUKU = 1001;

    public static boolean isShizukuAvailable() {
        try {
            return Shizuku.pingBinder();
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean hasPermission() {
        try {
            if (!isShizukuAvailable()) return false;
            return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void requestPermission(int requestCode) {
        try {
            if (isShizukuAvailable()) {
                Shizuku.requestPermission(requestCode);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Failed to request Shizuku permission", t);
        }
    }

    public static boolean executeCommand(String command) {
        try {
            if (!isShizukuAvailable()) {
                Log.w(TAG, "Shizuku is not available to run: " + command);
                return false;
            }
            IShizukuService service = IShizukuService.Stub.asInterface(Shizuku.getBinder());
            if (service == null) {
                Log.w(TAG, "IShizukuService binder interface is null");
                return false;
            }
            service.newProcess(new String[]{"sh", "-c", command}, null, null);
            Log.i(TAG, "Executed command via Shizuku: " + command);
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "Error executing command via Shizuku: " + command, t);
            return false;
        }
    }

    public static boolean lockTask(int taskId) {
        return executeCommand("am task lock " + taskId);
    }

    public static boolean unlockTask() {
        return executeCommand("am task lock stop");
    }

    public static void lockTopApp(final String expectedPkg) {
        if (!hasPermission()) return;
        new Thread(() -> {
            try {
                // Short stabilization delay to let the target window gain full focus
                Thread.sleep(250);
                String cmd = "LOCK_STATE=$(dumpsys activity 2>/dev/null | grep -m 1 'mLockTaskModeState=' | tr -d ' \r\n'); " +
                             "if [ \"$LOCK_STATE\" != \"mLockTaskModeState=PINNED\" ] && [ \"$LOCK_STATE\" != \"mLockTaskModeState=LOCKED\" ]; then " +
                             "  LINE=$(dumpsys window 2>/dev/null | grep mFocusedApp | grep ActivityRecord | head -n 1); " +
                             "  PKG=$(echo \"$LINE\" | sed -E 's/.*u[0-9]+ ([^/]+)\\/.*/\\1/'); " +
                             "  TID=$(echo \"$LINE\" | sed -E 's/.* t([0-9]+)\\}.*/\\1/'); " +
                             "  if [ \"$PKG\" = \"" + expectedPkg + "\" ] && [ -n \"$TID\" ]; then " +
                             "    am task lock \"$TID\" 2>&1; " +
                             "    cmd vibrator_manager synced oneshot 80 2>/dev/null; " +
                             "  fi; " +
                             "fi";
                executeCommand(cmd);
            } catch (Exception ignored) {}
        }).start();
    }

    public static void ensureAgentRunning() {
        executeCommand("nohup sh /data/local/tmp/pinmaster_agent.sh </dev/null >/dev/null 2>&1 &");
    }

    public static boolean deployAndStartAgent(Context context) {
        if (!hasPermission()) {
            Log.w(TAG, "Cannot deploy agent: Shizuku permission not granted");
            return false;
        }

        try {
            // 1. Extract agent script from assets to app private storage
            java.io.File internalFile = new java.io.File(context.getFilesDir(), "pinmaster_agent.sh");
            try (java.io.InputStream in = context.getAssets().open("pinmaster_agent.sh");
                 java.io.FileOutputStream out = new java.io.FileOutputStream(internalFile)) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                out.flush();
            }

            internalFile.setReadable(true, false);
            internalFile.setExecutable(true, false);

            // 2. Copy via Shizuku shell to /data/local/tmp and make executable
            String cpCmd = "cp " + internalFile.getAbsolutePath() + " /data/local/tmp/pinmaster_agent.sh && chmod 755 /data/local/tmp/pinmaster_agent.sh";
            executeCommand(cpCmd);

            // Sync target packages to files for daemon
            SettingsManager.syncTargetsToFile(context);

            // 3. Launch agent daemon
            String launchCmd = "pkill -f pinmaster_agent; nohup sh /data/local/tmp/pinmaster_agent.sh </dev/null >/dev/null 2>&1 &";
            boolean success = executeCommand(launchCmd);
            Log.i(TAG, "PinMaster agent successfully deployed and started via Shizuku!");
            return success;
        } catch (Throwable t) {
            Log.e(TAG, "Failed to deploy and start agent", t);
            // Fallback to starting existing script
            ensureAgentRunning();
            return false;
        }
    }
}
