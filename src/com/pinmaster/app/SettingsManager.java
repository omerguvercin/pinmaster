package com.pinmaster.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

public class SettingsManager {
    private static final String PREF_NAME = "pinmaster_settings";
    private static final String KEY_PINNED_APPS = "pinned_packages";
    private static final String KEY_MASTER_ENABLED = "master_enabled";
    private static final String KEY_DELAY_MS = "pin_delay_ms";
    private static final String KEY_VIBRATE = "vibrate_enabled";

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isMasterEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_MASTER_ENABLED, true);
    }

    public static void setMasterEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_MASTER_ENABLED, enabled).apply();
        syncTargetsToFile(context);
    }

    public static Set<String> getPinnedPackages(Context context) {
        return new HashSet<>(getPrefs(context).getStringSet(KEY_PINNED_APPS, new HashSet<String>()));
    }

    public static boolean isPackagePinned(Context context, String packageName) {
        if (packageName == null) return false;
        return getPinnedPackages(context).contains(packageName);
    }

    public static void setPackagePinned(Context context, String packageName, boolean pinned) {
        Set<String> set = getPinnedPackages(context);
        if (pinned) {
            set.add(packageName);
        } else {
            set.remove(packageName);
        }
        getPrefs(context).edit().putStringSet(KEY_PINNED_APPS, set).apply();
        syncTargetsToFile(context);
    }

    public static void syncTargetsToFile(Context context) {
        try {
            File dir = context.getExternalFilesDir(null);
            if (dir == null) return;
            
            // 1. Hedef paketleri senkronize et
            File targetFile = new File(dir, "pin_targets.txt");
            Set<String> pkgs = getPinnedPackages(context);
            boolean master = isMasterEnabled(context);
            
            StringBuilder sb = new StringBuilder();
            if (master) {
                for (String p : pkgs) {
                    sb.append(p.trim()).append("\n");
                }
            }
            
            FileOutputStream fos = new FileOutputStream(targetFile);
            fos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            fos.flush();
            fos.close();

            // 2. Titreşim ayarını senkronize et (1 = Açık, 0 = Kapalı)
            File vibFile = new File(dir, "vibrate.txt");
            FileOutputStream vfos = new FileOutputStream(vibFile);
            vfos.write((isVibrateEnabled(context) ? "1" : "0").getBytes(StandardCharsets.UTF_8));
            vfos.flush();
            vfos.close();

            // 3. Sabitleme gecikmesini senkronize et (ms cinsinden)
            File delayFile = new File(dir, "delay.txt");
            FileOutputStream dfos = new FileOutputStream(delayFile);
            dfos.write(String.valueOf(getDelayMs(context)).getBytes(StandardCharsets.UTF_8));
            dfos.flush();
            dfos.close();

        } catch (Exception ignored) {}
    }

    public static int getDelayMs(Context context) {
        return getPrefs(context).getInt(KEY_DELAY_MS, 350);
    }

    public static void setDelayMs(Context context, int delayMs) {
        getPrefs(context).edit().putInt(KEY_DELAY_MS, delayMs).apply();
        syncTargetsToFile(context);
    }

    public static boolean isVibrateEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_VIBRATE, true);
    }

    public static void setVibrateEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_VIBRATE, enabled).apply();
        syncTargetsToFile(context);
    }
}
