package com.exampl.antiaddiction.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "daily_usage_records")
public class DailyUsageRecord {
    @PrimaryKey
    @NonNull
    public String dateStr;

    public long totalUsageMillis;

    // JSON 数组字符串，例如 ["微信", "抖音"]
    public String overLimitAppsJson;

    public DailyUsageRecord(@NonNull String dateStr, long totalUsageMillis, String overLimitAppsJson) {
        this.dateStr = dateStr;
        this.totalUsageMillis = totalUsageMillis;
        this.overLimitAppsJson = overLimitAppsJson;
    }
}
