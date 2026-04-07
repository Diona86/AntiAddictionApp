package com.exampl.antiaddiction.model;

import android.graphics.drawable.Drawable;

public class AppUsageInfo {
    public String appName;
    public String packageName;
    public transient android.graphics.drawable.Drawable appIcon; // transient 表示不参与序列化
    public long usageTime;
    public String timeFormatted;

    public AppUsageInfo() {}
    public AppUsageInfo(String appName, String packageName, android.graphics.drawable.Drawable appIcon, long usageTime, String timeFormatted) {
        this.appName = appName; this.packageName = packageName; this.appIcon = appIcon; this.usageTime = usageTime; this.timeFormatted = timeFormatted;
    }
}