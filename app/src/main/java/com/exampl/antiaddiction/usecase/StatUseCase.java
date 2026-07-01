package com.exampl.antiaddiction.usecase;

import static com.exampl.antiaddiction.utils.Utils.formatTime;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import com.exampl.antiaddiction.model.AppUsageInfo;
import com.exampl.antiaddiction.model.ChildDashboardItem;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class StatUseCase {

    private final Gson gson = new Gson();

    public List<AppUsageInfo> buildSortedUsageList(Map<String, Long> usageByPkg, PackageManager pm) {
        Map<String, AppUsageInfo> merged = new HashMap<>();
        if (usageByPkg == null || usageByPkg.isEmpty()) {
            return new ArrayList<>();
        }
        for (Map.Entry<String, Long> entry : usageByPkg.entrySet()) {
            if (entry == null) continue;
            String pkg = entry.getKey();
            Long timeObj = entry.getValue();
            if (pkg == null || pkg.trim().isEmpty() || timeObj == null || timeObj <= 0) {
                continue;
            }
            String appName = resolveAppName(pm, pkg);
            merged.put(pkg, new AppUsageInfo(appName, pkg, null, timeObj, ""));
        }

        List<AppUsageInfo> list = new ArrayList<>(merged.values());
        Collections.sort(list, (a, b) -> Long.compare(b.usageTime, a.usageTime));
        for (AppUsageInfo info : list) {
            info.timeFormatted = formatTime(info.usageTime);
        }
        return list;
    }

    public long calcTotalMillis(List<AppUsageInfo> apps) {
        long total = 0L;
        if (apps == null) {
            return total;
        }
        for (AppUsageInfo app : apps) {
            if (app == null || app.usageTime <= 0) continue;
            total += app.usageTime;
        }
        return total;
    }

    public ChildDashboardItem buildSelfDisplayItem(String userId, long totalMillis, List<AppUsageInfo> apps) {
        ChildDashboardItem selfItem = new ChildDashboardItem();
        selfItem.userId = userId == null ? "" : userId;
        selfItem.nickname = "我 (本机实时)";
        selfItem.totalTime = totalMillis;
        selfItem.appJson = gson.toJson(apps == null ? new ArrayList<>() : apps);
        selfItem.appLimitsJson = "{}";
        return selfItem;
    }

    public Set<String> collectOverLimitApps(List<AppUsageInfo> apps, Map<String, Double> appLimitsMap, Set<String> out) {
        out.clear();
        if (apps == null || apps.isEmpty() || appLimitsMap == null || appLimitsMap.isEmpty()) {
            return out;
        }
        for (AppUsageInfo app : apps) {
            if (app == null || app.packageName == null) {
                continue;
            }
            Double limitMinutes = appLimitsMap.get(app.packageName);
            if (limitMinutes == null || limitMinutes <= 0) {
                continue;
            }
            long usedMinutes = app.usageTime / 1000 / 60;
            if (usedMinutes >= limitMinutes.intValue()) {
                out.add(app.appName == null ? app.packageName : app.appName);
            }
        }
        return out;
    }

    private String resolveAppName(PackageManager pm, String pkg) {
        if (pm == null || pkg == null || pkg.trim().isEmpty()) {
            return pkg == null ? "" : pkg;
        }
        try {
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            return pm.getApplicationLabel(ai).toString();
        } catch (Exception e) {
            return pkg;
        }
    }
}
