package com.pinmaster.app;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class IntruderLog {
    private String id;
    private String photoPath;
    private String appName;
    private String packageName;
    private String reason;
    private long timestamp;

    public IntruderLog(String id, String photoPath, String appName, String packageName, String reason, long timestamp) {
        this.id = id;
        this.photoPath = photoPath;
        this.appName = appName;
        this.packageName = packageName;
        this.reason = reason;
        this.timestamp = timestamp;
    }

    public String getId() {
        return id;
    }

    public String getPhotoPath() {
        return photoPath;
    }

    public String getAppName() {
        return appName;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getReason() {
        return reason;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getFormattedDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, HH:mm", new Locale("tr"));
        return sdf.format(new Date(timestamp));
    }
}
