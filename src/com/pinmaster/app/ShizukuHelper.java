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

    public static void ensureAgentRunning() {
        executeCommand("nohup sh /data/local/tmp/pinmaster_agent.sh > /dev/null 2>&1 &");
    }
}
