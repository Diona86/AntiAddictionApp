package com.exampl.antiaddiction.service;

import static com.exampl.antiaddiction.utils.Utils.formatTime;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
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
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class UsageMonitorService extends Service {

    private static final String TAG = "UsageMonitorService";
    private static final String CHANNEL_ID = "usage_monitor_channel";
    private static final int FOREGROUND_ID = 1001;
    private static final long INTERVAL_MS = 10 * 60 * 1000L;
    private static final long LIMIT_NOTIFY_COOLDOWN_MS = 30 * 60 * 1000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, Long> limitNotifyCache = new HashMap<>();
    private CloudBaseClient cloudbase;
    private final Gson gson = new Gson();

    private final Runnable monitorTask = new Runnable() {
        @Override
        public void run() {
            runMonitorOnce();
            handler.postDelayed(this, INTERVAL_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        cloudbase = new CloudBaseClient(getString(R.string.CLOUDBASE_ENV_ID), getString(R.string.CLOUDBASE_ACCESS_TOKEN));
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(FOREGROUND_ID, buildForegroundNotification("后台守护运行中"));
        handler.removeCallbacks(monitorTask);
        handler.post(monitorTask);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(monitorTask);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void runMonitorOnce() {
        String role = UserManager.getInstance(this).getRole();
        if (!"self".equals(role)) {
            return;
        }
        String userId = UserManager.getInstance(this).getUserId();
        if (userId == null || userId.trim().isEmpty()) {
            return;
        }

        UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        PackageManager pm = getPackageManager();
        long endTime = System.currentTimeMillis();

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long startTime = calendar.getTimeInMillis();

        List<UsageStats> stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime);
        if (stats == null || stats.isEmpty()) {
            Log.w(TAG, "No usage stats from system");
            return;
        }

        Map<String, AppUsageInfo> appMap = new HashMap<>();
        long totalMillis = 0L;
        for (UsageStats usageStats : stats) {
            long time = usageStats.getTotalTimeInForeground();
            if (time <= 0) continue;
            String pkg = usageStats.getPackageName();
            totalMillis += time;

            if (appMap.containsKey(pkg)) {
                AppUsageInfo existing = appMap.get(pkg);
                if (existing != null) existing.usageTime += time;
            } else {
                String appName;
                try {
                    ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                    appName = pm.getApplicationLabel(ai).toString();
                } catch (Exception e) {
                    appName = pkg;
                }
                appMap.put(pkg, new AppUsageInfo(appName, pkg, null, time, ""));
            }
        }

        List<AppUsageInfo> list = new ArrayList<>(appMap.values());
        for (AppUsageInfo info : list) {
            info.timeFormatted = formatTime(info.usageTime);
        }

        syncUsageToCloud(userId, totalMillis, list);
        checkPolicyAndNotify(userId, totalMillis, appMap);
    }

    private void syncUsageToCloud(String userId, long totalMillis, List<AppUsageInfo> list) {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        String appJson = gson.toJson(list);
        String checkPath = "/v1/rdb/rest/usage_report?userId=eq." + userId + "&dateStr=eq." + today;

        cloudbase.request("GET", checkPath, null, null, new TypeToken<List<Map<String, Object>>>() {}, new CloudBaseCallback<List<Map<String, Object>>>() {
            @Override
            public void onSuccess(List<Map<String, Object>> data) {
                Map<String, Object> body = new HashMap<>();
                body.put("userId", userId);
                body.put("totalTime", totalMillis);
                body.put("appJson", appJson);
                body.put("dateStr", today);

                if (data != null && !data.isEmpty()) {
                    String rowId = String.valueOf(data.get(0).get("id"));
                    cloudbase.request("PATCH", "/v1/rdb/rest/usage_report?id=eq." + rowId, body, null, null, new CloudBaseCallback<Object>() {
                        @Override public void onSuccess(Object res) {}
                        @Override public void onError(int c, String m) { Log.e(TAG, "Patch usage report failed: " + m); }
                    });
                } else {
                    cloudbase.request("POST", "/v1/rdb/rest/usage_report", body, null, null, new CloudBaseCallback<Object>() {
                        @Override public void onSuccess(Object res) {}
                        @Override public void onError(int c, String m) { Log.e(TAG, "Post usage report failed: " + m); }
                    });
                }
            }

            @Override
            public void onError(int code, String message) {
                Log.e(TAG, "Check usage_report failed: " + message);
            }
        });
    }

    private void checkPolicyAndNotify(String userId, long totalMillis, Map<String, AppUsageInfo> appMap) {
        String cleanUserId = userId.contains(".") ? userId.split("\\.")[0] : userId;
        String path = "/v1/rdb/rest/control_policy?userId=eq." + cleanUserId;
        cloudbase.request("GET", path, null, null, new TypeToken<List<Map<String, Object>>>() {}, new CloudBaseCallback<List<Map<String, Object>>>() {
            @Override
            public void onSuccess(List<Map<String, Object>> data) {
                if (data == null || data.isEmpty()) return;
                Map<String, Object> policy = data.get(0);

                if (policy.get("totalLimit") instanceof Number) {
                    int totalLimitMinutes = ((Number) policy.get("totalLimit")).intValue();
                    long currentMinutes = totalMillis / 1000 / 60;
                    if (currentMinutes >= totalLimitMinutes) {
                        notifyWithCooldown("total_limit", "总时长提醒", "今日使用已达 " + currentMinutes + " 分钟（限额 " + totalLimitMinutes + " 分钟）");
                    }
                }

                if (policy.get("appLimits") != null) {
                    Map<String, Double> appLimitsMap = gson.fromJson(String.valueOf(policy.get("appLimits")),
                            new TypeToken<Map<String, Double>>() {}.getType());
                    if (appLimitsMap == null) return;

                    for (Map.Entry<String, Double> entry : appLimitsMap.entrySet()) {
                        AppUsageInfo appInfo = appMap.get(entry.getKey());
                        if (appInfo == null || entry.getValue() == null) continue;
                        long usedMinutes = appInfo.usageTime / 1000 / 60;
                        int limitMinutes = entry.getValue().intValue();
                        if (usedMinutes >= limitMinutes) {
                            String key = "app_" + entry.getKey();
                            String text = appInfo.appName + " 已使用 " + usedMinutes + " 分钟（限额 " + limitMinutes + " 分钟）";
                            notifyWithCooldown(key, "应用限额提醒", text);
                        }
                    }
                }
            }

            @Override
            public void onError(int code, String message) {
                Log.e(TAG, "Fetch control policy failed: " + message);
            }
        });
    }

    private void notifyWithCooldown(String key, String title, String content) {
        long now = System.currentTimeMillis();
        long last = limitNotifyCache.containsKey(key) ? limitNotifyCache.get(key) : 0L;
        if (now - last < LIMIT_NOTIFY_COOLDOWN_MS) return;
        limitNotifyCache.put(key, now);

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
        return new NotificationCompat.Builder(this, CHANNEL_ID)
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
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "防沉迷后台服务",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("后台采集使用数据并进行限额提醒");
            NotificationManager nm = ContextCompat.getSystemService(this, NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }
}
