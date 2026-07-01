package com.exampl.antiaddiction.model;

import java.util.HashMap;
import java.util.Map;

public class UsageReportPayload {
    public String userId = "";
    public long totalTime = 0L;
    public String appJson = "[]";
    public String dateStr = "";

    public Map<String, Object> toMap() {
        Map<String, Object> body = new HashMap<>();
        body.put("userId", userId == null ? "" : userId);
        body.put("totalTime", totalTime);
        body.put("appJson", appJson == null ? "[]" : appJson);
        body.put("dateStr", dateStr == null ? "" : dateStr);
        return body;
    }
}
