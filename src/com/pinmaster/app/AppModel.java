package com.pinmaster.app;

import android.graphics.drawable.Drawable;

public class AppModel implements Comparable<AppModel> {
    private String appName;
    private String packageName;
    private Drawable icon;
    private boolean isPinned;

    public AppModel(String appName, String packageName, Drawable icon, boolean isPinned) {
        this.appName = appName;
        this.packageName = packageName;
        this.icon = icon;
        this.isPinned = isPinned;
    }

    public String getAppName() {
        return appName;
    }

    public String getPackageName() {
        return packageName;
    }

    public Drawable getIcon() {
        return icon;
    }

    public boolean isPinned() {
        return isPinned;
    }

    public void setPinned(boolean pinned) {
        this.isPinned = pinned;
    }

    @Override
    public int compareTo(AppModel other) {
        // Pinned apps first, then alphabetical
        if (this.isPinned && !other.isPinned) return -1;
        if (!this.isPinned && other.isPinned) return 1;
        return this.appName.compareToIgnoreCase(other.appName);
    }
}
