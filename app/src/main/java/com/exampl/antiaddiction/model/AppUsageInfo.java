package com.exampl.antiaddiction.model;

import android.graphics.drawable.Drawable;

public class AppUsageInfo {
    public String appName;      // 应用名（如：微信）
    public String packageName;  // 包名（如：com.tencent.mm）
    public android.graphics.drawable.Drawable appIcon; // 图标
    public long usageTime;      // 使用毫秒
    public String timeFormatted;// 格式化后的字符串（如：2小时30分）

    public AppUsageInfo(String appName, String packageName, android.graphics.drawable.Drawable appIcon, long usageTime, String timeFormatted) {
        this.appName = appName;
        this.packageName = packageName;
        this.appIcon = appIcon;
        this.usageTime = usageTime;
        this.timeFormatted = timeFormatted;
    }
}
