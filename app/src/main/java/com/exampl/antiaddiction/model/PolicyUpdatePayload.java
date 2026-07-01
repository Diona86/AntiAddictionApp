package com.exampl.antiaddiction.model;

import java.util.HashMap;
import java.util.Map;

public class PolicyUpdatePayload {
    public String userId = "";
    public Integer totalLimit = null;
    public String appLimitsJson = null;

    public Map<String, Object> toMap() {
        Map<String, Object> body = new HashMap<>();
        body.put("userId", userId == null ? "" : userId);
        if (totalLimit != null) {
            body.put("totalLimit", totalLimit);
        }
        if (appLimitsJson != null) {
            body.put("appLimits", appLimitsJson);
        }
        return body;
    }
}
