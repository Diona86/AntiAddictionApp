package com.exampl.antiaddiction.data.datasource;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Build;

import java.util.Calendar;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class UsageStatsDataSource {

    private final Context appContext;

    public UsageStatsDataSource(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public Map<String, Long> collectTodayUsageMillisByPackage() {
        UsageStatsManager usm = (UsageStatsManager) appContext.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) {
            return new HashMap<>();
        }
        long endTime = System.currentTimeMillis();
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long startTime = calendar.getTimeInMillis();

        Map<String, Long> usageByPkg = collectUsageMillisByEvents(usm, startTime, endTime);
        if (usageByPkg.isEmpty()) {
            usageByPkg = collectUsageMillisByStats(usm, startTime, endTime);
        }
        return usageByPkg;
    }

    public List<Float> collectTodayCumulativeUsageMinutesBy2Hour() {
        UsageStatsManager usm = (UsageStatsManager) appContext.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) {
            return new ArrayList<>();
        }
        long endTime = System.currentTimeMillis();
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long startTime = calendar.getTimeInMillis();

        long[] usageByBucketMillis = new long[12];
        try {
            UsageEvents usageEvents = usm.queryEvents(startTime, endTime);
            if (usageEvents == null) {
                return new ArrayList<>();
            }
            UsageEvents.Event event = new UsageEvents.Event();
            String currentForegroundPkg = null;
            long currentForegroundStartMs = -1L;
            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event);
                String pkg = event.getPackageName();
                if (pkg == null || pkg.trim().isEmpty()) {
                    continue;
                }
                int type = event.getEventType();
                long ts = event.getTimeStamp();
                if (isForegroundEvent(type)) {
                    if (currentForegroundPkg != null && currentForegroundStartMs > 0 && ts > currentForegroundStartMs) {
                        distributeToTwoHourBuckets(usageByBucketMillis, startTime, currentForegroundStartMs, ts);
                    }
                    currentForegroundPkg = pkg;
                    currentForegroundStartMs = ts;
                } else if (isBackgroundEvent(type)) {
                    if (currentForegroundPkg != null
                            && currentForegroundPkg.equals(pkg)
                            && currentForegroundStartMs > 0
                            && ts > currentForegroundStartMs) {
                        distributeToTwoHourBuckets(usageByBucketMillis, startTime, currentForegroundStartMs, ts);
                        currentForegroundPkg = null;
                        currentForegroundStartMs = -1L;
                    }
                }
            }
            if (currentForegroundPkg != null && currentForegroundStartMs > 0 && endTime > currentForegroundStartMs) {
                distributeToTwoHourBuckets(usageByBucketMillis, startTime, currentForegroundStartMs, endTime);
            }
        } catch (Exception ignored) {
            return new ArrayList<>();
        }

        int currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int activeBucket = Math.max(1, Math.min(12, (currentHour / 2) + 1));
        long cumulative = 0L;
        List<Float> cumulativeMinutes = new ArrayList<>();
        for (int i = 0; i < activeBucket; i++) {
            cumulative += usageByBucketMillis[i];
            cumulativeMinutes.add(cumulative / 1000f / 60f);
        }
        return cumulativeMinutes;
    }

    private Map<String, Long> collectUsageMillisByEvents(UsageStatsManager usm, long startTime, long endTime) {
        Map<String, Long> usageByPkg = new HashMap<>();
        try {
            UsageEvents usageEvents = usm.queryEvents(startTime, endTime);
            if (usageEvents == null) {
                return usageByPkg;
            }
            UsageEvents.Event event = new UsageEvents.Event();
            String currentForegroundPkg = null;
            long currentForegroundStartMs = -1L;
            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event);
                String pkg = event.getPackageName();
                if (pkg == null || pkg.trim().isEmpty()) {
                    continue;
                }
                int type = event.getEventType();
                long ts = event.getTimeStamp();
                if (isForegroundEvent(type)) {
                    if (currentForegroundPkg != null && currentForegroundStartMs > 0 && ts > currentForegroundStartMs) {
                        long delta = ts - currentForegroundStartMs;
                        long old = usageByPkg.containsKey(currentForegroundPkg) ? usageByPkg.get(currentForegroundPkg) : 0L;
                        usageByPkg.put(currentForegroundPkg, old + delta);
                    }
                    currentForegroundPkg = pkg;
                    currentForegroundStartMs = ts;
                } else if (isBackgroundEvent(type)) {
                    if (currentForegroundPkg != null
                            && currentForegroundPkg.equals(pkg)
                            && currentForegroundStartMs > 0
                            && ts > currentForegroundStartMs) {
                        long delta = ts - currentForegroundStartMs;
                        long old = usageByPkg.containsKey(pkg) ? usageByPkg.get(pkg) : 0L;
                        usageByPkg.put(pkg, old + delta);
                        currentForegroundPkg = null;
                        currentForegroundStartMs = -1L;
                    }
                }
            }
            if (currentForegroundPkg != null && currentForegroundStartMs > 0 && endTime > currentForegroundStartMs) {
                long delta = endTime - currentForegroundStartMs;
                long old = usageByPkg.containsKey(currentForegroundPkg) ? usageByPkg.get(currentForegroundPkg) : 0L;
                usageByPkg.put(currentForegroundPkg, old + delta);
            }
        } catch (Exception ignored) {
        }
        return usageByPkg;
    }

    private Map<String, Long> collectUsageMillisByStats(UsageStatsManager usm, long startTime, long endTime) {
        Map<String, Long> usageByPkg = new HashMap<>();
        try {
            List<UsageStats> stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime);
            if (stats == null) {
                return usageByPkg;
            }
            for (UsageStats usageStats : stats) {
                if (usageStats == null) continue;
                String pkg = usageStats.getPackageName();
                if (pkg == null || pkg.trim().isEmpty()) continue;
                long time = usageStats.getTotalTimeInForeground();
                if (time <= 0) continue;
                usageByPkg.put(pkg, time);
            }
        } catch (Exception ignored) {
        }
        return usageByPkg;
    }

    private boolean isForegroundEvent(int type) {
        if (type == UsageEvents.Event.MOVE_TO_FOREGROUND) {
            return true;
        }
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && type == UsageEvents.Event.ACTIVITY_RESUMED;
    }

    private boolean isBackgroundEvent(int type) {
        if (type == UsageEvents.Event.MOVE_TO_BACKGROUND) {
            return true;
        }
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                && (type == UsageEvents.Event.ACTIVITY_PAUSED || type == UsageEvents.Event.ACTIVITY_STOPPED);
    }

    private void distributeToTwoHourBuckets(long[] usageByBucketMillis, long dayStart, long segmentStart, long segmentEnd) {
        if (usageByBucketMillis == null || usageByBucketMillis.length == 0 || segmentEnd <= segmentStart) {
            return;
        }
        long twoHourMillis = 2L * 60L * 60L * 1000L;
        for (int bucket = 0; bucket < usageByBucketMillis.length; bucket++) {
            long bucketStart = dayStart + bucket * twoHourMillis;
            long bucketEnd = bucketStart + twoHourMillis;
            long overlapStart = Math.max(segmentStart, bucketStart);
            long overlapEnd = Math.min(segmentEnd, bucketEnd);
            if (overlapEnd > overlapStart) {
                usageByBucketMillis[bucket] += overlapEnd - overlapStart;
            }
        }
    }
}
