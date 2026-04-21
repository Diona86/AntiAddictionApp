package com.exampl.antiaddiction.service;

import static com.exampl.antiaddiction.utils.Utils.formatTime;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.exampl.antiaddiction.R;
import com.exampl.antiaddiction.activity.MainActivity;
import com.exampl.antiaddiction.cloudbase.CloudBaseCallback;
import com.exampl.antiaddiction.cloudbase.CloudBaseClient;
import com.exampl.antiaddiction.manager.UserManager;
import com.exampl.antiaddiction.model.AppUsageInfo;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UsageMonitorService extends Service {

    private static final String TAG = "UsageMonitorService";
    private static final String MONITOR_CHANNEL_ID = "usage_monitor_channel";
    private static final String ALERT_CHANNEL_ID = "usage_alert_channel_v2";
    private static final int FOREGROUND_ID = 1001;
    // 30 秒高频上报，保证监管端更及时看到最新数据
    private static final long INTERVAL_MS = 30 * 1000L;
    // 策略拉取单独降频，避免每次轮询都访问云端
    private static final long POLICY_REFRESH_INTERVAL_MS = 2 * 60 * 1000L;
    private static final long CHILD_LIST_REFRESH_INTERVAL_MS = 2 * 60 * 1000L;
    private static final long BINDING_REFRESH_INTERVAL_MS = 2 * 60 * 1000L;
    private static final long EVENT_UPLOAD_COOLDOWN_MS = 5 * 60 * 1000L;
    private static final long LIMIT_NOTIFY_COOLDOWN_MS = 30 * 60 * 1000L;

    private final Map<String, Long> limitNotifyCache = new ConcurrentHashMap<>();
    private final Gson gson = new Gson();
    private HandlerThread monitorThread;
    private Handler monitorHandler;
    private volatile boolean monitorLoopStarted = false;
    private CloudBaseClient cloudbase;
    private volatile String reportRowId;
    private volatile String reportDateCache;
    private volatile long lastPolicyFetchMs = 0L;
    private volatile boolean policyFetching = false;
    private volatile Integer cachedTotalLimitMinutes;
    private volatile Map<String, Double> cachedAppLimits = new HashMap<>();
    private final Set<String> cachedBoundChildren = Collections.synchronizedSet(new LinkedHashSet<>());
    private volatile long lastChildListFetchMs = 0L;
    private volatile boolean childListFetching = false;
    private volatile long lastBindingFetchMs = 0L;
    private volatile boolean bindingFetching = false;
    private volatile String cachedSupervisorId;
    private final Map<String, Long> eventUploadCache = new ConcurrentHashMap<>();
    // 监管提醒去重：按超额类型分别比较增长（总时长 / 单应用）
    private final Map<String, Long> childTotalOverLimitNotifiedMinutesCache = new ConcurrentHashMap<>();
    private final Map<String, Long> childAppOverLimitNotifiedMinutesCache = new ConcurrentHashMap<>();
    private volatile boolean limitEventTableAvailable = true;
    // 云表字段可能尚未升级，先尝试扩展字段，失败后自动降级
    private volatile boolean supportsExtendedUsageColumns = true;

    private final Runnable monitorTask = new Runnable() {
        @Override
        public void run() {
            if (!monitorLoopStarted) {
                return;
            }
            runMonitorOnce();
            if (monitorHandler != null) {
                monitorHandler.postDelayed(this, INTERVAL_MS);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        cloudbase = new CloudBaseClient(getString(R.string.CLOUDBASE_ENV_ID), getString(R.string.CLOUDBASE_ACCESS_TOKEN));
        monitorThread = new HandlerThread("usage-monitor-worker");
        monitorThread.start();
        monitorHandler = new Handler(monitorThread.getLooper());
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        android.app.Notification notification = buildForegroundNotification("后台守护运行中");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(FOREGROUND_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(FOREGROUND_ID, notification);
        }
        startMonitorLoopIfNeeded();
        Log.i(TAG, "Service started in foreground");
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        monitorLoopStarted = false;
        if (monitorHandler != null) {
            monitorHandler.removeCallbacks(monitorTask);
            monitorHandler = null;
        }
        if (monitorThread != null) {
            monitorThread.quitSafely();
            monitorThread = null;
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private synchronized void startMonitorLoopIfNeeded() {
        if (monitorHandler == null) {
            return;
        }
        if (monitorLoopStarted) {
            Log.d(TAG, "monitor loop already started, skip duplicate start");
            return;
        }
        monitorLoopStarted = true;
        monitorHandler.post(monitorTask);
    }

    private void runMonitorOnce() {
        String role = UserManager.getInstance(this).getRole();
        String userId = UserManager.getInstance(this).getUserId();
        if (userId == null || userId.trim().isEmpty()) {
            Log.w(TAG, "skip tick: empty userId");
            return;
        }
        Log.d(TAG, "tick role=" + role + ", userId=" + userId);

        if ("self".equals(role) || "child".equals(role)) {
            runSelfMonitor(userId);
            return;
        }
        if ("supervisor".equals(role) || "parent".equals(role)) {
            runSupervisorMonitor(userId);
            return;
        }
        Log.w(TAG, "skip tick: unsupported role=" + role);
    }

    private void runSelfMonitor(String userId) {
        UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        PackageManager pm = getPackageManager();
        long endTime = System.currentTimeMillis();

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long startTime = calendar.getTimeInMillis();

        Map<String, Long> usageByPkg = collectUsageMillisByEvents(usm, startTime, endTime);
        if (usageByPkg.isEmpty()) {
            // 兜底：部分机型可能不返回 events，回退到 UsageStats 聚合
            usageByPkg = collectUsageMillisByStats(usm, startTime, endTime);
        }
        if (usageByPkg.isEmpty()) {
            Log.w(TAG, "No usage stats from system");
            return;
        }

        Map<String, AppUsageInfo> appMap = new HashMap<>();
        long totalMillis = 0L;
        for (Map.Entry<String, Long> entry : usageByPkg.entrySet()) {
            if (entry == null) continue;
            String pkg = entry.getKey();
            Long timeObj = entry.getValue();
            if (pkg == null || pkg.trim().isEmpty() || timeObj == null) continue;
            long time = timeObj;
            if (time <= 0) continue;
            totalMillis += time;
            String appName = resolveAppName(pm, pkg);
            appMap.put(pkg, new AppUsageInfo(appName, pkg, null, time, ""));
        }
        if (appMap.isEmpty()) {
            Log.w(TAG, "No positive usage after aggregation");
            return;
        }

        List<AppUsageInfo> list = new ArrayList<>(appMap.values());
        for (AppUsageInfo info : list) {
            info.timeFormatted = formatTime(info.usageTime);
        }

        refreshPolicyIfNeeded(userId);
        OverLimitState overLimitState = evaluateOverLimit(totalMillis, appMap);
        Log.d(TAG, "self tick totalMillis=" + totalMillis + ", appCount=" + list.size() + ", overLimit=" + overLimitState.hasOverLimit);
        syncUsageToCloud(userId, totalMillis, list, overLimitState);
        refreshSelfBindingIfNeeded(userId);
        reportLimitEventsIfNeeded(userId, overLimitState);
    }

    private Map<String, Long> collectUsageMillisByEvents(UsageStatsManager usm, long startTime, long endTime) {
        Map<String, Long> usageByPkg = new HashMap<>();
        if (usm == null) {
            return usageByPkg;
        }
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
                boolean foregroundEvent = isForegroundEvent(type);
                boolean backgroundEvent = isBackgroundEvent(type);
                if (foregroundEvent) {
                    if (currentForegroundPkg != null
                            && currentForegroundStartMs > 0
                            && ts > currentForegroundStartMs) {
                        long delta = ts - currentForegroundStartMs;
                        long old = usageByPkg.containsKey(currentForegroundPkg) ? usageByPkg.get(currentForegroundPkg) : 0L;
                        usageByPkg.put(currentForegroundPkg, old + delta);
                    }
                    currentForegroundPkg = pkg;
                    currentForegroundStartMs = ts;
                } else if (backgroundEvent) {
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
            // 把“当前仍在前台”的会话也计入，避免持续刷同一 app 时 totalMillis 卡住
            if (currentForegroundPkg != null
                    && currentForegroundStartMs > 0
                    && endTime > currentForegroundStartMs) {
                long delta = endTime - currentForegroundStartMs;
                long old = usageByPkg.containsKey(currentForegroundPkg) ? usageByPkg.get(currentForegroundPkg) : 0L;
                usageByPkg.put(currentForegroundPkg, old + delta);
            }
        } catch (Exception e) {
            Log.w(TAG, "queryEvents aggregation failed: " + e.getMessage());
        }
        return usageByPkg;
    }

    private Map<String, Long> collectUsageMillisByStats(UsageStatsManager usm, long startTime, long endTime) {
        Map<String, Long> usageByPkg = new HashMap<>();
        if (usm == null) {
            return usageByPkg;
        }
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
        } catch (Exception e) {
            Log.w(TAG, "queryUsageStats fallback failed: " + e.getMessage());
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

    private void runSupervisorMonitor(String supervisorId) {
        checkPendingLimitEvents(supervisorId);
        refreshBoundChildrenIfNeeded(supervisorId);
        // 优先使用 limit_event 单一通道，避免与 usage_report 直查同时通知导致重复
        if (!limitEventTableAvailable) {
            checkChildrenOverLimit();
        }
    }

    private void refreshBoundChildrenIfNeeded(String supervisorId) {
        long now = System.currentTimeMillis();
        if (childListFetching) return;
        if ((now - lastChildListFetchMs) < CHILD_LIST_REFRESH_INTERVAL_MS) {
            return;
        }
        childListFetching = true;
        String rawSupervisorId = supervisorId == null ? "" : supervisorId.trim();
        String normalizedSupervisorId = normalizeUserId(rawSupervisorId);
        Set<String> mergedChildren = new LinkedHashSet<>();

        String rawPath = "/v1/rdb/rest/user?boundUserId=eq." + rawSupervisorId;
        cloudbase.request("GET", rawPath, null, null, new TypeToken<List<Map<String, Object>>>() {}, new CloudBaseCallback<List<Map<String, Object>>>() {
            @Override
            public void onSuccess(List<Map<String, Object>> data) {
                collectChildIds(data, mergedChildren);
                if (!normalizedSupervisorId.equals(rawSupervisorId)) {
                    fetchBoundChildrenBySupervisorId(normalizedSupervisorId, mergedChildren);
                } else {
                    finishBoundChildrenRefresh(mergedChildren);
                }
            }

            @Override
            public void onError(int code, String message) {
                Log.e(TAG, "Fetch bound children failed(raw): " + message);
                if (!normalizedSupervisorId.equals(rawSupervisorId)) {
                    fetchBoundChildrenBySupervisorId(normalizedSupervisorId, mergedChildren);
                } else {
                    finishBoundChildrenRefresh(mergedChildren);
                }
            }
        });
    }

    private void checkChildrenOverLimit() {
        List<String> snapshot;
        synchronized (cachedBoundChildren) {
            if (cachedBoundChildren.isEmpty()) {
                return;
            }
            snapshot = new ArrayList<>(cachedBoundChildren);
        }
        if (snapshot.isEmpty()) {
            return;
        }
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        Log.d(TAG, "check children over limit, count=" + snapshot.size());
        for (String rawChildId : snapshot) {
            String normalizedChildId = normalizeUserId(rawChildId);
            fetchAndCheckChildOverLimit(rawChildId, normalizedChildId, today);
        }
    }

    private void fetchBoundChildrenBySupervisorId(String supervisorId, Set<String> mergedChildren) {
        String path = "/v1/rdb/rest/user?boundUserId=eq." + supervisorId;
        cloudbase.request("GET", path, null, null, new TypeToken<List<Map<String, Object>>>() {}, new CloudBaseCallback<List<Map<String, Object>>>() {
            @Override
            public void onSuccess(List<Map<String, Object>> data) {
                collectChildIds(data, mergedChildren);
                finishBoundChildrenRefresh(mergedChildren);
            }

            @Override
            public void onError(int code, String message) {
                Log.e(TAG, "Fetch bound children failed(normalized): " + message);
                finishBoundChildrenRefresh(mergedChildren);
            }
        });
    }

    private void finishBoundChildrenRefresh(Set<String> mergedChildren) {
        lastChildListFetchMs = System.currentTimeMillis();
        cachedBoundChildren.clear();
        cachedBoundChildren.addAll(mergedChildren);
        childListFetching = false;
        Log.d(TAG, "supervisor children refreshed count=" + cachedBoundChildren.size());
    }

    private void collectChildIds(List<Map<String, Object>> data, Set<String> outSet) {
        if (data == null || outSet == null) {
            return;
        }
        for (Map<String, Object> child : data) {
            if (child == null) continue;
            Object idObj = child.get("id");
            if (idObj == null) continue;
            String childId = String.valueOf(idObj).trim();
            if (!childId.isEmpty()) {
                outSet.add(normalizeUserId(childId));
            }
        }
    }

    private void fetchAndCheckChildOverLimit(String rawChildId, String normalizedChildId, String today) {
        String rawPath = "/v1/rdb/rest/usage_report?userId=eq." + rawChildId + "&dateStr=eq." + today;
        cloudbase.request("GET", rawPath, null, null, new TypeToken<List<Map<String, Object>>>() {}, new CloudBaseCallback<List<Map<String, Object>>>() {
            @Override
            public void onSuccess(List<Map<String, Object>> data) {
                if (handleChildOverLimitReport(data, normalizedChildId)) {
                    return;
                }
                if (!normalizedChildId.equals(rawChildId)) {
                    fetchAndCheckChildOverLimitById(normalizedChildId, normalizedChildId, today);
                }
            }

            @Override
            public void onError(int code, String message) {
                Log.e(TAG, "Fetch child usage_report failed(raw): " + message);
                if (!normalizedChildId.equals(rawChildId)) {
                    fetchAndCheckChildOverLimitById(normalizedChildId, normalizedChildId, today);
                }
            }
        });
    }

    private void fetchAndCheckChildOverLimitById(String childIdToQuery, String childIdForDisplay, String today) {
        String path = "/v1/rdb/rest/usage_report?userId=eq." + childIdToQuery + "&dateStr=eq." + today;
        cloudbase.request("GET", path, null, null, new TypeToken<List<Map<String, Object>>>() {}, new CloudBaseCallback<List<Map<String, Object>>>() {
            @Override
            public void onSuccess(List<Map<String, Object>> data) {
                handleChildOverLimitReport(data, childIdForDisplay);
            }

            @Override
            public void onError(int code, String message) {
                Log.e(TAG, "Fetch child usage_report failed(fallback): " + message);
            }
        });
    }

    private boolean handleChildOverLimitReport(List<Map<String, Object>> data, String childIdForDisplay) {
        if (data == null || data.isEmpty()) {
            return false;
        }
        Map<String, Object> report = data.get(0);
        String reportDate = report.get("dateStr") == null
                ? new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date())
                : String.valueOf(report.get("dateStr")).trim();
        if (!parseHasOverLimit(report.get("hasOverLimit"))) {
            clearChildOverLimitProgressCache(childIdForDisplay, reportDate);
            return true;
        }

        List<String> detailItems = parseOverLimitDetailList(report.get("overLimitDetail"));
        if (detailItems.isEmpty()) {
            detailItems.add("检测到超额");
        }
        // 清理已经不在本轮超额集合中的缓存，避免后续阈值下调时被旧分钟值卡住
        pruneOverLimitProgressCache(childIdForDisplay, reportDate, detailItems);
        List<String> notifyItems = new ArrayList<>();

        for (String detail : detailItems) {
            if (detail == null || detail.trim().isEmpty()) {
                continue;
            }
            String trimmedDetail = detail.trim();
            if (shouldNotifyTotalOverLimit(childIdForDisplay, reportDate, trimmedDetail)
                    || shouldNotifyAppOverLimit(childIdForDisplay, reportDate, trimmedDetail)) {
                notifyItems.add(trimmedDetail);
            }
        }
        if (notifyItems.isEmpty()) {
            Log.d(TAG, "skip supervisor notify: over-limit exists but no progress increase child=" + childIdForDisplay);
            return true;
        }

        String detailText = joinDetailItems(notifyItems);
        String content = "自律者 " + childIdForDisplay + " 出现超额";
        if (!detailText.trim().isEmpty()) {
            content = content + "：" + detailText;
        }
        String stateKey = "child_over_" + childIdForDisplay + "_" + reportDate + "_" + Math.abs(detailText.hashCode());
        Log.i(TAG, "notify supervisor over-limit child=" + childIdForDisplay + ", detail=" + detailText);
        notifyWithCooldown(stateKey, "监管提醒", content);
        return true;
    }

    private void clearChildOverLimitProgressCache(String childIdForDisplay, String reportDate) {
        String totalKey = childIdForDisplay + "_" + reportDate;
        childTotalOverLimitNotifiedMinutesCache.remove(totalKey);
        String appPrefix = childIdForDisplay + "_" + reportDate + "_";
        for (String key : childAppOverLimitNotifiedMinutesCache.keySet()) {
            if (key != null && key.startsWith(appPrefix)) {
                childAppOverLimitNotifiedMinutesCache.remove(key);
            }
        }
    }

    private void pruneOverLimitProgressCache(String childIdForDisplay, String reportDate, List<String> detailItems) {
        boolean hasTotalOverLimit = false;
        Set<String> activeAppNames = new HashSet<>();
        if (detailItems != null) {
            for (String detail : detailItems) {
                if (detail == null) continue;
                String trimmed = detail.trim();
                if (trimmed.isEmpty()) continue;
                if (trimmed.startsWith("总时长超额")) {
                    hasTotalOverLimit = true;
                } else {
                    String appName = extractAppNameFromDetail(trimmed);
                    if (!appName.isEmpty()) {
                        activeAppNames.add(appName);
                    }
                }
            }
        }

        if (!hasTotalOverLimit) {
            childTotalOverLimitNotifiedMinutesCache.remove(childIdForDisplay + "_" + reportDate);
        }

        String appPrefix = childIdForDisplay + "_" + reportDate + "_";
        for (String key : childAppOverLimitNotifiedMinutesCache.keySet()) {
            if (key == null || !key.startsWith(appPrefix)) {
                continue;
            }
            String appName = key.substring(appPrefix.length());
            if (!activeAppNames.contains(appName)) {
                childAppOverLimitNotifiedMinutesCache.remove(key);
            }
        }
    }

    private String extractAppNameFromDetail(String detail) {
        if (detail == null) {
            return "";
        }
        Matcher matcher = Pattern.compile("^(.+?)\\s+(\\d+)\\s*/\\s*(\\d+)\\s*分钟.*$").matcher(detail);
        if (!matcher.matches()) {
            return "";
        }
        String appName = matcher.group(1);
        return appName == null ? "" : appName.trim();
    }

    private List<String> parseOverLimitDetailList(Object detailObj) {
        List<String> details = new ArrayList<>();
        if (detailObj == null) {
            return details;
        }
        String detailStr = String.valueOf(detailObj).trim();
        if (detailStr.isEmpty()) {
            return details;
        }
        try {
            List<String> parsed = gson.fromJson(detailStr, new TypeToken<List<String>>() {}.getType());
            if (parsed != null) {
                for (String item : parsed) {
                    if (item != null && !item.trim().isEmpty()) {
                        details.add(item.trim());
                    }
                }
            }
        } catch (Exception ignored) {
            details.add(detailStr);
        }
        if (details.isEmpty()) {
            details.add(detailStr);
        }
        return details;
    }

    private String joinDetailItems(List<String> details) {
        if (details == null || details.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String detail : details) {
            if (detail == null || detail.trim().isEmpty()) continue;
            if (sb.length() > 0) {
                sb.append("；");
            }
            sb.append(detail.trim());
        }
        return sb.toString();
    }

    private boolean shouldNotifyTotalOverLimit(String childIdForDisplay, String reportDate, String detail) {
        if (detail == null || !detail.startsWith("总时长超额")) {
            return false;
        }
        Long totalMinutes = extractFirstLongByPattern(detail, "已达\\s*(\\d+)\\s*分钟");
        if (totalMinutes == null) {
            return true;
        }
        String key = childIdForDisplay + "_" + reportDate;
        Long last = childTotalOverLimitNotifiedMinutesCache.get(key);
        if (last != null && totalMinutes <= last) {
            return false;
        }
        childTotalOverLimitNotifiedMinutesCache.put(key, totalMinutes);
        return true;
    }

    private boolean shouldNotifyAppOverLimit(String childIdForDisplay, String reportDate, String detail) {
        if (detail == null || detail.startsWith("总时长超额")) {
            return false;
        }
        Matcher matcher = Pattern.compile("^(.+?)\\s+(\\d+)\\s*/\\s*(\\d+)\\s*分钟.*$").matcher(detail);
        if (!matcher.matches()) {
            return true;
        }
        String appName = matcher.group(1) == null ? "" : matcher.group(1).trim();
        if (appName.isEmpty()) {
            appName = "unknown_app";
        }
        long usedMinutes;
        try {
            usedMinutes = Long.parseLong(matcher.group(2));
        } catch (Exception e) {
            return true;
        }
        String key = childIdForDisplay + "_" + reportDate + "_" + appName;
        Long last = childAppOverLimitNotifiedMinutesCache.get(key);
        if (last != null && usedMinutes <= last) {
            return false;
        }
        childAppOverLimitNotifiedMinutesCache.put(key, usedMinutes);
        return true;
    }

    @Nullable
    private Long extractFirstLongByPattern(String source, String regex) {
        if (source == null || regex == null) {
            return null;
        }
        try {
            Matcher matcher = Pattern.compile(regex).matcher(source);
            if (!matcher.find() || matcher.groupCount() < 1) {
                return null;
            }
            return Long.parseLong(matcher.group(1));
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean parseHasOverLimit(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        if (value != null) {
            String str = String.valueOf(value).trim();
            return "true".equalsIgnoreCase(str) || "1".equals(str);
        }
        return false;
    }

    private String normalizeUserId(String rawUserId) {
        if (rawUserId == null) {
            return "";
        }
        String trimmed = rawUserId.trim();
        if (trimmed.endsWith(".0")) {
            return trimmed.substring(0, trimmed.length() - 2);
        }
        return trimmed;
    }

    private String buildReadableOverLimitDetail(Object detailObj) {
        if (detailObj == null) {
            return "";
        }
        String detailStr = String.valueOf(detailObj).trim();
        if (detailStr.isEmpty()) {
            return "";
        }
        try {
            List<String> detailList = gson.fromJson(detailStr, new TypeToken<List<String>>() {}.getType());
            if (detailList == null || detailList.isEmpty()) {
                return detailStr;
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < detailList.size(); i++) {
                String item = detailList.get(i);
                if (item == null || item.trim().isEmpty()) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append("；");
                }
                sb.append(item.trim());
            }
            return sb.length() == 0 ? detailStr : sb.toString();
        } catch (Exception e) {
            return detailStr;
        }
    }

    private void refreshSelfBindingIfNeeded(String userId) {
        if (!limitEventTableAvailable) return;
        long now = System.currentTimeMillis();
        if (bindingFetching) return;
        if (cachedSupervisorId != null && !cachedSupervisorId.trim().isEmpty() && (now - lastBindingFetchMs) < BINDING_REFRESH_INTERVAL_MS) {
            return;
        }
        bindingFetching = true;
        String rawUserId = userId == null ? "" : userId.trim();
        if (rawUserId.isEmpty()) {
            bindingFetching = false;
            return;
        }
        String normalizedUserId = normalizeUserId(rawUserId);
        fetchSupervisorBindingByUserId(rawUserId, normalizedUserId);
    }

    private void fetchSupervisorBindingByUserId(String queryUserId, String fallbackUserId) {
        String path = "/v1/rdb/rest/user?id=eq." + queryUserId;
        cloudbase.request("GET", path, null, null, new TypeToken<List<Map<String, Object>>>() {}, new CloudBaseCallback<List<Map<String, Object>>>() {
            @Override
            public void onSuccess(List<Map<String, Object>> data) {
                String supervisorId = extractSupervisorId(data);
                if ((supervisorId == null || supervisorId.trim().isEmpty()) && !fallbackUserId.equals(queryUserId)) {
                    fetchSupervisorBindingByUserId(fallbackUserId, fallbackUserId);
                    return;
                }
                cachedSupervisorId = supervisorId;
                lastBindingFetchMs = System.currentTimeMillis();
                bindingFetching = false;
                Log.d(TAG, "self binding refreshed supervisorId=" + cachedSupervisorId);
            }

            @Override
            public void onError(int code, String message) {
                if (!fallbackUserId.equals(queryUserId)) {
                    fetchSupervisorBindingByUserId(fallbackUserId, fallbackUserId);
                    return;
                }
                bindingFetching = false;
                Log.e(TAG, "Fetch self binding failed: " + message);
            }
        });
    }

    private String extractSupervisorId(List<Map<String, Object>> data) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        Map<String, Object> user = data.get(0);
        if (user == null || user.get("boundUserId") == null) {
            return null;
        }
        String supervisorId = String.valueOf(user.get("boundUserId")).trim();
        return supervisorId.isEmpty() ? null : supervisorId;
    }

    private void reportLimitEventsIfNeeded(String childUserId, OverLimitState overLimitState) {
        if (!limitEventTableAvailable) return;
        if (overLimitState == null || !overLimitState.hasOverLimit) return;
        if (cachedSupervisorId == null || cachedSupervisorId.trim().isEmpty()) return;

        List<String> details;
        try {
            details = gson.fromJson(overLimitState.summary, new TypeToken<List<String>>() {}.getType());
        } catch (Exception e) {
            details = new ArrayList<>();
        }
        if (details == null || details.isEmpty()) {
            details = new ArrayList<>();
            details.add("检测到超额");
        }

        long now = System.currentTimeMillis();
        for (String detail : details) {
            String safeDetail = detail == null ? "检测到超额" : detail.trim();
            if (safeDetail.isEmpty()) safeDetail = "检测到超额";
            String eventKey = childUserId + "|" + safeDetail;
            long lastReport = eventUploadCache.containsKey(eventKey) ? eventUploadCache.get(eventKey) : 0L;
            if (now - lastReport < EVENT_UPLOAD_COOLDOWN_MS) {
                continue;
            }

            Map<String, Object> body = new HashMap<>();
            body.put("childUserId", childUserId);
            body.put("supervisorUserId", cachedSupervisorId);
            body.put("eventType", safeDetail.startsWith("总时长超额") ? "total_limit" : "app_limit");
            body.put("detail", safeDetail);
            body.put("createdAt", formatDateTimeForCloud(now));
            body.put("notified", false);

            String finalSafeDetail = safeDetail;
            cloudbase.request("POST", "/v1/rdb/rest/limit_event", body, null, null, new CloudBaseCallback<Object>() {
                @Override
                public void onSuccess(Object res) {
                    eventUploadCache.put(eventKey, now);
                    Log.d(TAG, "limit_event posted detail=" + finalSafeDetail);
                }

                @Override
                public void onError(int c, String m) {
                    if (isLimitEventTableMissing(m)) {
                        limitEventTableAvailable = false;
                    }
                    Log.e(TAG, "limit_event post failed: " + m);
                }
            });
        }
    }

    private void checkPendingLimitEvents(String supervisorId) {
        if (!limitEventTableAvailable) return;
        String rawId = supervisorId == null ? "" : supervisorId.trim();
        if (rawId.isEmpty()) return;
        String normalizedId = normalizeUserId(rawId);
        fetchPendingLimitEventsBySupervisor(rawId, normalizedId);
    }

    private void fetchPendingLimitEventsBySupervisor(String queryId, String fallbackId) {
        String path = "/v1/rdb/rest/limit_event?supervisorUserId=eq." + queryId + "&notified=eq.false";
        cloudbase.request("GET", path, null, null, new TypeToken<List<Map<String, Object>>>() {}, new CloudBaseCallback<List<Map<String, Object>>>() {
            @Override
            public void onSuccess(List<Map<String, Object>> data) {
                processPendingLimitEvents(data);
                if (!fallbackId.equals(queryId)) {
                    fetchPendingLimitEventsBySupervisor(fallbackId, fallbackId);
                }
            }

            @Override
            public void onError(int code, String message) {
                if (isLimitEventTableMissing(message)) {
                    limitEventTableAvailable = false;
                }
                Log.e(TAG, "limit_event fetch failed: " + message);
                if (!fallbackId.equals(queryId)) {
                    fetchPendingLimitEventsBySupervisor(fallbackId, fallbackId);
                }
            }
        });
    }

    private void processPendingLimitEvents(List<Map<String, Object>> events) {
        if (events == null || events.isEmpty()) return;
        for (Map<String, Object> event : events) {
            if (event == null) continue;
            String eventId = event.get("id") == null ? null : String.valueOf(event.get("id"));
            if (eventId == null || eventId.trim().isEmpty()) continue;

            String childId = event.get("childUserId") == null ? "未知" : normalizeUserId(String.valueOf(event.get("childUserId")));
            String detail = event.get("detail") == null ? "检测到超额" : String.valueOf(event.get("detail"));
            // 使用 childId + detail 去重，避免数据库产生多条同义事件时连续弹多条
            String dedupeKey = "limit_evt_" + childId + "_" + Math.abs(detail.hashCode());
            notifyWithCooldown(dedupeKey, "监管提醒", "自律者 " + childId + " 超额：" + detail);
            markLimitEventNotified(eventId);
        }
    }

    private void markLimitEventNotified(String eventId) {
        Map<String, Object> body = new HashMap<>();
        body.put("notified", true);
        cloudbase.request("PATCH", "/v1/rdb/rest/limit_event?id=eq." + eventId, body, null, null, new CloudBaseCallback<Object>() {
            @Override
            public void onSuccess(Object res) {}

            @Override
            public void onError(int c, String m) {
                if (isLimitEventTableMissing(m)) {
                    limitEventTableAvailable = false;
                }
                Log.e(TAG, "limit_event patch notified failed: " + m);
            }
        });
    }

    private boolean isLimitEventTableMissing(String message) {
        if (message == null) return false;
        String lower = message.toLowerCase(Locale.ROOT);
        return lower.contains("limit_event") && (lower.contains("not found") || lower.contains("does not exist"));
    }

    private String formatDateTimeForCloud(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(millis));
    }

    private void syncUsageToCloud(String userId, long totalMillis, List<AppUsageInfo> list, OverLimitState overLimitState) {
        if (userId == null || userId.trim().isEmpty()) {
            Log.w(TAG, "skip usage sync: empty userId");
            return;
        }
        String rawUserId = userId.trim();
        String normalizedUserId = normalizeUserId(userId);
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        String appJson = gson.toJson(list);
        Map<String, Object> body = buildUsageReportBody(rawUserId, totalMillis, appJson, today, overLimitState);

        // 跨天后清理缓存，防止错误复用旧 rowId
        if (reportDateCache != null && !reportDateCache.equals(today)) {
            reportRowId = null;
            reportDateCache = null;
        }

        if (reportRowId != null && today.equals(reportDateCache)) {
            patchUsageReportByUserAndDate(rawUserId, today, body, rawUserId, totalMillis, appJson, overLimitState, true);
            return;
        }

        checkUsageReportAndSync(rawUserId, normalizedUserId, body, totalMillis, appJson, today, overLimitState, true);
    }

    private void checkUsageReportAndSync(
            String queryUserId,
            String normalizedUserId,
            Map<String, Object> body,
            long totalMillis,
            String appJson,
            String today,
            OverLimitState overLimitState,
            boolean allowFallbackToNormalizedId
    ) {
        String checkPath = "/v1/rdb/rest/usage_report?userId=eq."
                + Uri.encode(queryUserId)
                + "&dateStr=eq."
                + Uri.encode(today);
        cloudbase.request("GET", checkPath, null, null, new TypeToken<List<Map<String, Object>>>() {}, new CloudBaseCallback<List<Map<String, Object>>>() {
            @Override
            public void onSuccess(List<Map<String, Object>> data) {
                if (data != null && !data.isEmpty()) {
                    String rowId = String.valueOf(data.get(0).get("id"));
                    reportRowId = rowId;
                    reportDateCache = today;
                    patchUsageReportByUserAndDate(queryUserId, today, body, queryUserId, totalMillis, appJson, overLimitState, true);
                } else {
                    postUsageReport(body, queryUserId, totalMillis, appJson, today, overLimitState, true);
                }
            }

            @Override
            public void onError(int code, String message) {
                Log.e(TAG, "Check usage_report failed(userId=" + queryUserId + "): " + message);
                if (allowFallbackToNormalizedId && !normalizedUserId.equals(queryUserId)) {
                    checkUsageReportAndSync(normalizedUserId, normalizedUserId, body, totalMillis, appJson, today, overLimitState, false);
                    return;
                }
                // 查询失败时兜底直接 POST，避免本轮数据丢失
                postUsageReport(body, queryUserId, totalMillis, appJson, today, overLimitState, true);
            }
        });
    }

    private Map<String, Object> buildUsageReportBody(String userId, long totalMillis, String appJson, String today, OverLimitState overLimitState) {
        Map<String, Object> body = new HashMap<>();
        body.put("userId", userId);
        body.put("totalTime", totalMillis);
        body.put("appJson", appJson);
        body.put("dateStr", today);
        if (supportsExtendedUsageColumns) {
            body.put("hasOverLimit", overLimitState.hasOverLimit);
            body.put("overLimitDetail", overLimitState.summary);
            body.put("lastUploadAt", System.currentTimeMillis());
        }
        return body;
    }

    private void postUsageReport(Map<String, Object> body, String userId, long totalMillis, String appJson, String today, OverLimitState overLimitState, boolean allowDowngradeRetry) {
        cloudbase.request("POST", "/v1/rdb/rest/usage_report", body, null, null, new CloudBaseCallback<Object>() {
            @Override
            public void onSuccess(Object res) {
                reportDateCache = today;
                Log.d(TAG, "usage_report post success userId=" + userId + ", totalMillis=" + totalMillis + ", date=" + today);
            }

            @Override
            public void onError(int c, String m) {
                if (allowDowngradeRetry && shouldDowngradeUsageColumns(m)) {
                    supportsExtendedUsageColumns = false;
                    Log.w(TAG, "usage_report post fallback to base columns");
                    Map<String, Object> fallbackBody = buildUsageReportBody(userId, totalMillis, appJson, today, overLimitState);
                    postUsageReport(fallbackBody, userId, totalMillis, appJson, today, overLimitState, false);
                    return;
                }
                Log.e(TAG, "Post usage report failed: " + m);
            }
        });
    }

    private boolean shouldDowngradeUsageColumns(String errorMessage) {
        if (errorMessage == null) {
            return false;
        }
        String msg = errorMessage.toLowerCase(Locale.ROOT);
        return msg.contains("column lastuploadat not found")
                || msg.contains("column hasoverlimit not found")
                || msg.contains("column overlimitdetail not found");
    }

    private void patchUsageReportByUserAndDate(String queryUserId, String queryDate, Map<String, Object> body, String userId, long totalMillis, String appJson, OverLimitState overLimitState, boolean allowDowngradeRetry) {
        String patchPath = "/v1/rdb/rest/usage_report?userId=eq."
                + Uri.encode(queryUserId)
                + "&dateStr=eq."
                + Uri.encode(queryDate);
        cloudbase.request("PATCH", patchPath, body, null, null, new CloudBaseCallback<Object>() {
            @Override
            public void onSuccess(Object res) {
                Log.d(TAG, "usage_report patch success userId=" + queryUserId + ", totalMillis=" + totalMillis + ", date=" + queryDate);
            }

            @Override
            public void onError(int c, String m) {
                if (allowDowngradeRetry && shouldDowngradeUsageColumns(m)) {
                    supportsExtendedUsageColumns = false;
                    Log.w(TAG, "usage_report patch fallback to base columns");
                    Map<String, Object> fallbackBody = buildUsageReportBody(userId, totalMillis, appJson, queryDate, overLimitState);
                    patchUsageReportByUserAndDate(queryUserId, queryDate, fallbackBody, userId, totalMillis, appJson, overLimitState, false);
                    return;
                }
                Log.e(TAG, "Patch usage report failed: " + m);
                // PATCH 失败时清理缓存，避免下一轮一直打到错误行
                reportRowId = null;
            }
        });
    }

    private void refreshPolicyIfNeeded(String userId) {
        long now = System.currentTimeMillis();
        if (policyFetching) return;
        if (cachedTotalLimitMinutes != null && (now - lastPolicyFetchMs) < POLICY_REFRESH_INTERVAL_MS) {
            return;
        }

        String cleanUserId = userId.contains(".") ? userId.split("\\.")[0] : userId;
        String path = "/v1/rdb/rest/control_policy?userId=eq." + cleanUserId;
        policyFetching = true;
        cloudbase.request("GET", path, null, null, new TypeToken<List<Map<String, Object>>>() {}, new CloudBaseCallback<List<Map<String, Object>>>() {
            @Override
            public void onSuccess(List<Map<String, Object>> data) {
                lastPolicyFetchMs = System.currentTimeMillis();
                cachedTotalLimitMinutes = null;
                cachedAppLimits = new HashMap<>();

                if (data != null && !data.isEmpty()) {
                    Map<String, Object> policy = data.get(0);
                    if (policy.get("totalLimit") instanceof Number) {
                        cachedTotalLimitMinutes = ((Number) policy.get("totalLimit")).intValue();
                    }
                    if (policy.get("appLimits") != null) {
                        Map<String, Double> parsed = gson.fromJson(
                                String.valueOf(policy.get("appLimits")),
                                new TypeToken<Map<String, Double>>() {}.getType()
                        );
                        if (parsed != null) {
                            cachedAppLimits = parsed;
                        }
                    }
                }
                policyFetching = false;
            }

            @Override
            public void onError(int code, String message) {
                policyFetching = false;
                Log.e(TAG, "Fetch control policy failed: " + message);
            }
        });
    }

    private OverLimitState evaluateOverLimit(long totalMillis, Map<String, AppUsageInfo> appMap) {
        OverLimitState state = new OverLimitState();
        List<String> overItems = new ArrayList<>();

        if (cachedTotalLimitMinutes != null && cachedTotalLimitMinutes > 0) {
            long currentMinutes = totalMillis / 1000 / 60;
            if (currentMinutes >= cachedTotalLimitMinutes) {
                state.hasOverLimit = true;
                String msg = "今日使用已达 " + currentMinutes + " 分钟（限额 " + cachedTotalLimitMinutes + " 分钟）";
                overItems.add("总时长超额: " + msg);
                notifyWithCooldown("total_limit", "总时长提醒", msg);
            }
        }

        if (cachedAppLimits != null && !cachedAppLimits.isEmpty()) {
            for (Map.Entry<String, Double> entry : cachedAppLimits.entrySet()) {
                AppUsageInfo appInfo = appMap.get(entry.getKey());
                if (appInfo == null || entry.getValue() == null) continue;
                long usedMinutes = appInfo.usageTime / 1000 / 60;
                int limitMinutes = entry.getValue().intValue();
                if (limitMinutes <= 0) continue;
                if (usedMinutes >= limitMinutes) {
                    state.hasOverLimit = true;
                    String item = appInfo.appName + " " + usedMinutes + "/" + limitMinutes + " 分钟";
                    overItems.add(item);
                    notifyWithCooldown("app_" + entry.getKey(), "应用限额提醒", item + "（已超额）");
                }
            }
        }

        state.summary = overItems.isEmpty() ? "" : gson.toJson(overItems);
        return state;
    }

    private static class OverLimitState {
        boolean hasOverLimit = false;
        String summary = "";
    }

    private void notifyWithCooldown(String key, String title, String content) {
        long now = System.currentTimeMillis();
        long last = limitNotifyCache.containsKey(key) ? limitNotifyCache.get(key) : 0L;
        if (now - last < LIMIT_NOTIFY_COOLDOWN_MS) {
            Log.d(TAG, "skip notify by cooldown key=" + key);
            return;
        }
        limitNotifyCache.put(key, now);

        Log.i(TAG, "notify title=" + title + ", content=" + content);
        NotificationManagerCompat.from(this).notify((int) (now % Integer.MAX_VALUE), buildAlertNotification(title, content));
    }

    private android.app.Notification buildForegroundNotification(String text) {
        Intent launchIntent = new Intent(this, MainActivity.class);
        android.app.PendingIntent pendingIntent = android.app.PendingIntent.getActivity(
                this,
                1,
                launchIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE
        );
        return new NotificationCompat.Builder(this, MONITOR_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("防沉迷守护中")
                .setContentText(text)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private android.app.Notification buildAlertNotification(String title, String text) {
        Intent launchIntent = new Intent(this, MainActivity.class);
        android.app.PendingIntent pendingIntent = android.app.PendingIntent.getActivity(
                this,
                2,
                launchIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE
        );
        return new NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel monitorChannel = new NotificationChannel(
                    MONITOR_CHANNEL_ID,
                    "防沉迷后台服务",
                    NotificationManager.IMPORTANCE_LOW
            );
            monitorChannel.setDescription("后台采集使用数据并进行限额提醒");

            NotificationChannel alertChannel = new NotificationChannel(
                    ALERT_CHANNEL_ID,
                    "防沉迷提醒通知",
                    NotificationManager.IMPORTANCE_HIGH
            );
            alertChannel.setDescription("超额与监管提醒通知");
            alertChannel.enableVibration(true);
            alertChannel.setVibrationPattern(new long[]{0, 200, 120, 260});
            Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            alertChannel.setSound(soundUri, attrs);

            NotificationManager nm = ContextCompat.getSystemService(this, NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(monitorChannel);
                nm.createNotificationChannel(alertChannel);
            }
        }
    }
}
