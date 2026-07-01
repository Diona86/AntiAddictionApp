package com.exampl.antiaddiction.model;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChildDashboardItem {
    private static final Gson GSON = new Gson();

    public String userId = "";
    public String username = "";
    public String nickname = "";
    public long totalTime = 0L;
    public String appJson = "[]";
    public Integer totalLimit = null;
    public String appLimitsJson = "{}";
    public boolean hasOverLimit = false;
    public String overLimitDetail = "";

    public static ChildDashboardItem fromChildMap(Map<String, Object> child) {
        ChildDashboardItem item = new ChildDashboardItem();
        if (child == null) {
            return item;
        }
        item.userId = child.get("id") == null ? "" : String.valueOf(child.get("id"));
        item.username = child.get("username") == null ? "" : String.valueOf(child.get("username"));
        item.nickname = child.get("nickname") == null ? "" : String.valueOf(child.get("nickname"));
        return item;
    }

    public static ChildDashboardItem fromChildUser(ChildUser user) {
        ChildDashboardItem item = new ChildDashboardItem();
        if (user == null) {
            return item;
        }
        item.userId = user.id == null ? "" : user.id;
        item.username = user.username == null ? "" : user.username;
        item.nickname = user.nickname == null ? "" : user.nickname;
        return item;
    }

    public List<AppUsageInfo> parseApps() {
        try {
            List<AppUsageInfo> apps = GSON.fromJson(appJson, new TypeToken<List<AppUsageInfo>>() {}.getType());
            return apps == null ? new ArrayList<>() : apps;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public Map<String, Double> parseAppLimits() {
        try {
            Map<String, Double> appLimits = GSON.fromJson(appLimitsJson, new TypeToken<Map<String, Double>>() {}.getType());
            return appLimits == null ? new HashMap<>() : appLimits;
        } catch (Exception e) {
            return new HashMap<>();
        }
    }
}
