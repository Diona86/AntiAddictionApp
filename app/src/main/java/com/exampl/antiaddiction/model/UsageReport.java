package com.exampl.antiaddiction.model;

import java.util.Map;

public class UsageReport {
    public String id;
    public String userId;
    public String dateStr;
    public long totalTime;
    public String appJson;
    public boolean hasOverLimit;
    public String overLimitDetail;

    public static UsageReport fromMap(Map<String, Object> raw) {
        UsageReport report = new UsageReport();
        if (raw == null) {
            report.appJson = "[]";
            report.overLimitDetail = "";
            return report;
        }
        report.id = raw.get("id") == null ? "" : String.valueOf(raw.get("id"));
        report.userId = raw.get("userId") == null ? "" : String.valueOf(raw.get("userId"));
        report.dateStr = raw.get("dateStr") == null ? "" : String.valueOf(raw.get("dateStr"));
        if (raw.get("totalTime") instanceof Number) {
            report.totalTime = ((Number) raw.get("totalTime")).longValue();
        }
        report.appJson = raw.get("appJson") == null ? "[]" : String.valueOf(raw.get("appJson"));
        Object hasOverLimitObj = raw.get("hasOverLimit");
        if (hasOverLimitObj instanceof Boolean) {
            report.hasOverLimit = (Boolean) hasOverLimitObj;
        } else if (hasOverLimitObj instanceof Number) {
            report.hasOverLimit = ((Number) hasOverLimitObj).intValue() != 0;
        } else if (hasOverLimitObj != null) {
            String text = String.valueOf(hasOverLimitObj).trim();
            report.hasOverLimit = "true".equalsIgnoreCase(text) || "1".equals(text);
        }
        report.overLimitDetail = raw.get("overLimitDetail") == null ? "" : String.valueOf(raw.get("overLimitDetail"));
        return report;
    }
}
