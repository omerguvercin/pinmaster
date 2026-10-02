package com.pinmaster.app;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import java.util.HashSet;
import java.util.Set;

public class SettingsManager {
    private static final String PREF_NAME = "pinmaster_settings";
    private static final String KEY_PINNED_APPS = "pinned_packages";
    private static final String KEY_VIBRATE = "vibrate_enabled";
    private static final String KEY_EXIT_PIN = "exit_pin";

    private static SharedPreferences getPrefs(Context ctx) {
        return ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // ─── Pinned Apps ──────────────────────────────────────────────────────────

    public static Set<String> getPinnedPackages(Context ctx) {
        return new HashSet<>(getPrefs(ctx).getStringSet(KEY_PINNED_APPS, new HashSet<String>()));
    }

    public static boolean isPackagePinned(Context ctx, String pkg) {
        return pkg != null && getPinnedPackages(ctx).contains(pkg);
    }

    public static void setPackagePinned(Context ctx, String pkg, boolean pinned) {
        Set<String> set = getPinnedPackages(ctx);
        if (pinned) set.add(pkg); else set.remove(pkg);
        getPrefs(ctx).edit().putStringSet(KEY_PINNED_APPS, set).apply();
        applyLockTaskPackages(ctx);
    }

    // ─── Vibrate ──────────────────────────────────────────────────────────────

    public static boolean isVibrateEnabled(Context ctx) {
        return getPrefs(ctx).getBoolean(KEY_VIBRATE, true);
    }

    public static void setVibrateEnabled(Context ctx, boolean enabled) {
        getPrefs(ctx).edit().putBoolean(KEY_VIBRATE, enabled).apply();
    }

    // ─── Exit PIN ─────────────────────────────────────────────────────────────

    public static String getExitPin(Context ctx) {
        return getPrefs(ctx).getString(KEY_EXIT_PIN, "1234");
    }

    public static void setExitPin(Context ctx, String pin) {
        getPrefs(ctx).edit().putString(KEY_EXIT_PIN, pin).apply();
    }

    // ─── Device Owner ─────────────────────────────────────────────────────────

    public static boolean isDeviceOwner(Context ctx) {
        DevicePolicyManager dpm = (DevicePolicyManager) ctx.getSystemService(Context.DEVICE_POLICY_SERVICE);
        return dpm != null && dpm.isDeviceOwnerApp(ctx.getPackageName());
    }

    /**
     * DevicePolicyManager'a izinli paketleri uygular.
     * Bu metod çağrıldığında kiosk mod anında güncellenir.
     */
    public static void applyLockTaskPackages(Context ctx) {
        if (!isDeviceOwner(ctx)) return;
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) ctx.getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName admin = new ComponentName(ctx, PinDeviceAdminReceiver.class);

            Set<String> pkgs = getPinnedPackages(ctx);
            // Kendi paketimiz her zaman listede olmalı ki kiosktan çıkabilelim
            pkgs.add(ctx.getPackageName());

            dpm.setLockTaskPackages(admin, pkgs.toArray(new String[0]));

            // API 28+: Home, Recents ve bildirim panelini kiosk modunda gizle
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                dpm.setLockTaskFeatures(admin,
                        DevicePolicyManager.LOCK_TASK_FEATURE_KEYGUARD
                                | DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS);
            }
        } catch (Exception ignored) {}
    }
}
