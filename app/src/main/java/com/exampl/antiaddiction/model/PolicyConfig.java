package com.exampl.antiaddiction.model;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class PolicyConfig {
    public String userId;
    public Integer totalLimit;
    public String appLimitsJson;

    public static PolicyConfig fromMap(Map<String, Object> raw) {
        PolicyConfig policy = new PolicyConfig();
        if (raw == null) {
            policy.appLimitsJson = "{}";
            return policy;
        }
        policy.userId = raw.get("userId") == null ? "" : String.valueOf(raw.get("userId"));
        if (raw.get("totalLimit") instanceof Number) {
            policy.totalLimit = ((Number) raw.get("totalLimit")).intValue();
        }
        policy.appLimitsJson = raw.get("appLimits") == null ? "{}" : String.valueOf(raw.get("appLimits"));
        return policy;
    }

    public Map<String, Integer> parseAppLimitsAsIntMap() {
        if (appLimitsJson == null || appLimitsJson.trim().isEmpty()) {
            return new HashMap<>();
        }
        try {
            Map<String, Double> raw = new Gson().fromJson(appLimitsJson, new TypeToken<Map<String, Double>>() {}.getType());
            if (raw == null || raw.isEmpty()) {
                return new HashMap<>();
            }
            Map<String, Integer> parsed = new HashMap<>();
            for (Map.Entry<String, Double> entry : raw.entrySet()) {
                if (entry == null || entry.getKey() == null || entry.getValue() == null) continue;
                parsed.put(entry.getKey(), entry.getValue().intValue());
            }
            return parsed;
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    public Map<String, Double> parseAppLimitsAsDoubleMap() {
        if (appLimitsJson == null || appLimitsJson.trim().isEmpty()) {
            return new HashMap<>();
        }
        try {
            Map<String, Double> raw = new Gson().fromJson(appLimitsJson, new TypeToken<Map<String, Double>>() {}.getType());
            return raw == null ? new HashMap<>() : raw;
        } catch (Exception e) {
            return new HashMap<>();
        }
    }
}
