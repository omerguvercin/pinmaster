package com.pinmaster.app;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

public class SettingsManager {
    private static final String PREF_NAME    = "pinmaster_settings";
    private static final String KEY_PINNED   = "pinned_packages";
    private static final String KEY_MASTER   = "master_enabled";
    private static final String KEY_VIBRATE  = "vibrate_enabled";
    private static final String KEY_PIN      = "exit_pin";

    private static SharedPreferences getPrefs(Context ctx) {
        return ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // ─── Master switch ────────────────────────────────────────────────────────

    public static boolean isMasterEnabled(Context ctx) {
        return getPrefs(ctx).getBoolean(KEY_MASTER, true);
    }

    public static void setMasterEnabled(Context ctx, boolean enabled) {
        getPrefs(ctx).edit().putBoolean(KEY_MASTER, enabled).apply();
        applyLockTaskPackages(ctx);
        syncTargetsToFile(ctx);
    }

    // ─── Pinned apps ──────────────────────────────────────────────────────────

    public static Set<String> getPinnedPackages(Context ctx) {
        return new HashSet<>(getPrefs(ctx).getStringSet(KEY_PINNED, new HashSet<String>()));
    }

    public static boolean isPackagePinned(Context ctx, String pkg) {
        return pkg != null && getPinnedPackages(ctx).contains(pkg);
    }

    public static void setPackagePinned(Context ctx, String pkg, boolean pinned) {
        Set<String> set = getPinnedPackages(ctx);
        if (pinned) set.add(pkg); else set.remove(pkg);
        getPrefs(ctx).edit().putStringSet(KEY_PINNED, set).apply();
        applyLockTaskPackages(ctx);
        syncTargetsToFile(ctx);
    }

    // ─── Vibrate ──────────────────────────────────────────────────────────────

    public static boolean isVibrateEnabled(Context ctx) {
        return getPrefs(ctx).getBoolean(KEY_VIBRATE, true);
    }

    public static void setVibrateEnabled(Context ctx, boolean enabled) {
        getPrefs(ctx).edit().putBoolean(KEY_VIBRATE, enabled).apply();
        syncTargetsToFile(ctx);
    }

    // ─── Exit PIN ─────────────────────────────────────────────────────────────

    public static String getExitPin(Context ctx) {
        return getPrefs(ctx).getString(KEY_PIN, "1234");
    }

    public static void setExitPin(Context ctx, String pin) {
        getPrefs(ctx).edit().putString(KEY_PIN, pin).apply();
    }

    // ─── Sync Targets File for Daemon ─────────────────────────────────────────

    public static void syncTargetsToFile(Context context) {
        try {
            Set<String> pkgs = getPinnedPackages(context);
            boolean master = isMasterEnabled(context);

            StringBuilder sb = new StringBuilder();
            if (master) {
                for (String p : pkgs) {
                    if (p != null && !p.trim().isEmpty()) {
                        sb.append(p.trim()).append("\n");
                    }
                }
            }
            byte[] targetBytes = sb.toString().getBytes(StandardCharsets.UTF_8);
            byte[] vibBytes = (isVibrateEnabled(context) ? "1" : "0").getBytes(StandardCharsets.UTF_8);

            // 1. Write to internal storage (context.getFilesDir())
            File internalDir = context.getFilesDir();
            if (internalDir != null) {
                if (!internalDir.exists()) internalDir.mkdirs();
                File intTarget = new File(internalDir, "pin_targets.txt");
                try (FileOutputStream fos = new FileOutputStream(intTarget)) {
                    fos.write(targetBytes);
                }
                intTarget.setReadable(true, false);

                File intVib = new File(internalDir, "vibrate.txt");
                try (FileOutputStream fos = new FileOutputStream(intVib)) {
                    fos.write(vibBytes);
                }
                intVib.setReadable(true, false);
            }

            // 2. Write to external storage
            File extDir = context.getExternalFilesDir(null);
            if (extDir != null) {
                if (!extDir.exists()) extDir.mkdirs();
                File extTarget = new File(extDir, "pin_targets.txt");
                try (FileOutputStream fos = new FileOutputStream(extTarget)) {
                    fos.write(targetBytes);
                }
                File extVib = new File(extDir, "vibrate.txt");
                try (FileOutputStream fos = new FileOutputStream(extVib)) {
                    fos.write(vibBytes);
                }
            }

            // 3. If Shizuku is available, mirror directly to /data/local/tmp
            if (ShizukuHelper.hasPermission()) {
                String targetsStr = sb.toString();
                String encoded = targetsStr.replace("'", "'\\''");
                String vibStr = isVibrateEnabled(context) ? "1" : "0";
                String mirrorCmd = "printf '%s' '" + encoded + "' > /data/local/tmp/pin_targets.txt 2>/dev/null; " +
                                   "chmod 666 /data/local/tmp/pin_targets.txt 2>/dev/null; " +
                                   "printf '%s' '" + vibStr + "' > /data/local/tmp/vibrate.txt 2>/dev/null; " +
                                   "chmod 666 /data/local/tmp/vibrate.txt 2>/dev/null";
                ShizukuHelper.executeCommand(mirrorCmd);
            }
        } catch (Exception ignored) {}
    }

    // ─── Device Owner ─────────────────────────────────────────────────────────

    public static boolean isDeviceOwner(Context ctx) {
        DevicePolicyManager dpm = (DevicePolicyManager) ctx.getSystemService(Context.DEVICE_POLICY_SERVICE);
        return dpm != null && dpm.isDeviceOwnerApp(ctx.getPackageName());
    }

    public static void applyLockTaskPackages(Context ctx) {
        if (!isDeviceOwner(ctx)) return;
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) ctx.getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName admin = new ComponentName(ctx, PinDeviceAdminReceiver.class);

            Set<String> pkgs = new HashSet<>();
            if (isMasterEnabled(ctx)) {
                pkgs.addAll(getPinnedPackages(ctx));
            }
            pkgs.add(ctx.getPackageName());

            dpm.setLockTaskPackages(admin, pkgs.toArray(new String[0]));

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                dpm.setLockTaskFeatures(admin,
                        DevicePolicyManager.LOCK_TASK_FEATURE_KEYGUARD
                                | DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS);
            }
        } catch (Exception ignored) {}
    }
}
