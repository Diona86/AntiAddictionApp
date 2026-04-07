package com.exampl.antiaddiction.fragment;

import static com.exampl.antiaddiction.utils.Utils.formatTime;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.exampl.antiaddiction.R;
import com.exampl.antiaddiction.adapter.ChildDashboardAdapter;
import com.exampl.antiaddiction.adapter.UsageAdapter;
import com.exampl.antiaddiction.cloudbase.CloudBaseCallback;
import com.exampl.antiaddiction.cloudbase.CloudBaseClient;
import com.exampl.antiaddiction.manager.UserManager;
import com.exampl.antiaddiction.model.AppUsageInfo;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class StatFragment extends Fragment {

    private RecyclerView mainRecyclerView;
    private List<Map<String, Object>> displayData = new ArrayList<>();
    private ChildDashboardAdapter mainAdapter;
    private String role;
    private CloudBaseClient cloudbase;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_stat, container, false);
        mainRecyclerView = view.findViewById(R.id.mainRecyclerView);
        mainRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        role = UserManager.getInstance(requireContext()).getRole();
        cloudbase = new CloudBaseClient(getString(R.string.CLOUDBASE_ENV_ID), getString(R.string.CLOUDBASE_ACCESS_TOKEN));

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        loadData();
    }

    private void loadData() {
        if ("self".equals(role)) {
            runSelfLogic();
        } else {
            runSupervisorLogic();
        }
    }

    // ================= 自律者流程 =================
    private void runSelfLogic() {
        UsageStatsManager usm = (UsageStatsManager) requireContext().getSystemService(Context.USAGE_STATS_SERVICE);
        PackageManager pm = requireContext().getPackageManager();

        // 1. 设置时间范围（今日凌晨到现在）
        long endTime = System.currentTimeMillis();
        // 获取今天 00:00:00 的毫秒数
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0);
        calendar.set(java.util.Calendar.MINUTE, 0);
        calendar.set(java.util.Calendar.SECOND, 0);
        long startTime = calendar.getTimeInMillis();

        // 2. 抓取系统原始数据
        List<UsageStats> stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime);
        if (stats == null || stats.isEmpty()) {
            Log.e("ANTI_LOG", "系统未返回统计数据，请确认是否开启‘使用情况访问权限’");
            return;
        }

        // 3. 数据清洗：去重、合并时长、获取应用信息
        Map<String, AppUsageInfo> map = new HashMap<>();
        long totalMillis = 0;

        for (UsageStats usageStats : stats) {
            long time = usageStats.getTotalTimeInForeground();
            if (time <= 0) continue;

            String pkg = usageStats.getPackageName();
            totalMillis += time;

            if (map.containsKey(pkg)) {
                // 已存在，累加时长
                AppUsageInfo existing = map.get(pkg);
                if (existing != null) existing.usageTime += time;
            } else {
                // 新应用，抓取名字和图标
                String appName;
                Drawable icon;
                try {
                    ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                    appName = pm.getApplicationLabel(ai).toString();
                    icon = pm.getApplicationIcon(ai);
                } catch (Exception e) {
                    appName = pkg; // 找不到名字就用包名
                    icon = ContextCompat.getDrawable(requireContext(), R.mipmap.ic_launcher);
                }
                map.put(pkg, new AppUsageInfo(appName, pkg, icon, time, ""));
            }
        }

        // 4. 转换、排序、格式化时间
        List<AppUsageInfo> list = new ArrayList<>(map.values());
        // 降序排序：用时最多的排前面
        Collections.sort(list, (a, b) -> Long.compare(b.usageTime, a.usageTime));

        for (AppUsageInfo info : list) {
            info.timeFormatted = formatTime(info.usageTime); // 转换成 01h 20m
        }

        // 5. 上报云端（同步到 usage_report 表）
        syncUsageToCloud(totalMillis, list);

        // 6. 构造本地预览项显示在主 RecyclerView 中
        Map<String, Object> selfMap = new HashMap<>();
        selfMap.put("userId", UserManager.getInstance(requireContext()).getUserId());
        selfMap.put("nickname", "我 (本机实时)");
        selfMap.put("totalTime", totalMillis);
        selfMap.put("appJson", new Gson().toJson(list));

        displayData.clear();
        displayData.add(selfMap);

        // 7. 更新 UI 界面
        updateUI();

        // 8. 检查限额（如果超了就弹锁定窗）
        checkAndEnforceLimit(totalMillis);
    }

    // ================= 监管者流程 =================
    private void runSupervisorLogic() {
        String myId = UserManager.getInstance(requireContext()).getUserId();
        // 1. 找我的孩子列表
        cloudbase.request("GET", "/v1/rdb/rest/user?boundUserId=eq." + myId, null, null,
                new TypeToken<List<Map<String, Object>>>() {}, new CloudBaseCallback<List<Map<String, Object>>>() {
                    @Override
                    public void onSuccess(List<Map<String, Object>> children) {
                        if (children == null || children.isEmpty()) {
                            Toast.makeText(getContext(), "暂无绑定的自律者", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        displayData.clear();

                        // 2. 为每个孩子抓取今日报告
                        for (Map<String, Object> child : children) {
                            fetchReportForChild(child);
                        }
                    }
                    @Override public void onError(int code, String message) {}
                });
    }

    private void fetchReportForChild(Map<String, Object> child) {
        String childId = String.valueOf(child.get("id"));
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        cloudbase.request("GET", "/v1/rdb/rest/usage_report?userId=eq." + childId + "&dateStr=eq." + today, null, null,
                new TypeToken<List<Map<String, Object>>>() {}, new CloudBaseCallback<List<Map<String, Object>>>() {
                    @Override
                    public void onSuccess(List<Map<String, Object>> reports) {
                        Map<String, Object> displayItem = new HashMap<>(child);
                        displayItem.put("userId", childId);
                        if (reports != null && !reports.isEmpty()) {
                            displayItem.putAll(reports.get(0));
                        } else {
                            displayItem.put("totalTime", 0);
                            displayItem.put("appJson", "[]");
                        }
                        displayData.add(displayItem);
                        updateUI();
                    }
                    @Override public void onError(int code, String message) {}
                });
    }

    private void updateUI() {
        if (mainAdapter == null) {
            mainAdapter = new ChildDashboardAdapter(displayData, "supervisor".equals(role), new ChildDashboardAdapter.OnLimitSetListener() {
                @Override public void onSetTotalLimit(String userId) { showLimitDialog(userId, null); }
                @Override public void onSetAppLimit(String userId, String pkg) { showLimitDialog(userId, pkg); }
            });
            mainRecyclerView.setAdapter(mainAdapter);
        } else {
            mainAdapter.notifyDataSetChanged();
        }
    }

    private void showLimitDialog(String userId, String pkg) {
        EditText et = new EditText(getContext());
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setHint("输入分钟数");
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(pkg == null ? "设置总限额" : "设置App限额")
                .setView(et)
                .setPositiveButton("确认", (d, w) -> {
                    String val = et.getText().toString();
                    if (!val.isEmpty()) {
                        String rawId = userId;
                        // 如果包含 ".0"，则截掉
                        if (rawId.endsWith(".0")) {
                            rawId = rawId.substring(0, rawId.length() - 2);
                        }
                        updatePolicy(rawId, pkg, Integer.parseInt(val));
                    }
                }).show();
    }

    private void updatePolicy(String userId, String pkg, int minutes) {
        // 1. 彻底解决 6.0 问题：确保 ID 是纯数字字符串
        String cleanUserId = userId;
        if (userId.contains(".")) {
            cleanUserId = userId.split("\\.")[0];
        }
        final String finalUserId = cleanUserId; // 匿名内部类需要 final

        Log.d("ANTI_LOG", "准备管控: cleanUserId=" + finalUserId + ", pkg=" + pkg);

        //CloudBaseClient cloudbase = new CloudBaseClient(getString(R.string.CLOUDBASE_ENV_ID), getString(R.string.CLOUDBASE_ACCESS_TOKEN));
        TypeToken<List<Map<String, Object>>> typeToken = new TypeToken<List<Map<String, Object>>>() {};

        // 第一步：先 GET 查询该用户是否已有策略
        String queryPath = "/v1/rdb/rest/control_policy?userId=eq." + finalUserId;

        cloudbase.request("GET", queryPath, null, null, typeToken, new CloudBaseCallback<List<Map<String, Object>>>() {
            @Override
            public void onSuccess(List<Map<String, Object>> data) {
                // 准备 Body 数据
                Map<String, Object> body = new HashMap<>();
                body.put("userId", finalUserId);
                if (pkg == null) {
                    body.put("totalLimit", minutes);
                } else {
                    Map<String, Integer> appLimitMap = new HashMap<>();
                    appLimitMap.put(pkg, minutes);
                    body.put("appLimits", new Gson().toJson(appLimitMap));
                }

                if (data != null && !data.isEmpty()) {
                    // 情况 A：记录已存在 -> 执行 PATCH
                    Log.d("ANTI_LOG", "记录已存在，执行更新");
                    cloudbase.request("PATCH", "/v1/rdb/rest/control_policy?userId=eq." + finalUserId, body, null, null, new CloudBaseCallback<Object>() {
                        @Override public void onSuccess(Object res) {
                            if(isAdded()) Toast.makeText(getContext(), "限额已更新", Toast.LENGTH_SHORT).show();
                        }
                        @Override public void onError(int c, String m) { Log.e("ANTI_LOG", "PATCH失败: " + m); }
                    });
                } else {
                    // 情况 B：记录不存在 -> 执行 POST
                    Log.d("ANTI_LOG", "记录不存在，执行新增");
                    cloudbase.request("POST", "/v1/rdb/rest/control_policy", body, null, null, new CloudBaseCallback<Object>() {
                        @Override public void onSuccess(Object res) {
                            if(isAdded()) Toast.makeText(getContext(), "限额已创建", Toast.LENGTH_SHORT).show();
                        }
                        @Override public void onError(int c, String m) { Log.e("ANTI_LOG", "POST失败: " + m); }
                    });
                }
            }

            @Override
            public void onError(int code, String message) {
                Log.e("ANTI_LOG", "查询失败: " + message);
            }
        });
    }

    private void syncUsageToCloud(long totalMillis, List<AppUsageInfo> list) {
        String userId = UserManager.getInstance(requireContext()).getUserId();
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        // 将 App 列表序列化为 JSON 字符串，存入 TEXT 字段
        String appJson = new Gson().toJson(list);

        // 第一步：检查今天是否已经上报过
        // 语法：userId=eq.XXX&dateStr=eq.XXX
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
                    // 情况 A：今天有记录了，拿到该行 ID 执行 PATCH 更新
                    String rowId = String.valueOf(data.get(0).get("id"));
                    cloudbase.request("PATCH", "/v1/rdb/rest/usage_report?id=eq." + rowId, body, null, null, new CloudBaseCallback<Object>() {
                        @Override public void onSuccess(Object res) { Log.d("ANTI_LOG", "今日数据上报更新成功"); }
                        @Override public void onError(int c, String m) { Log.e("ANTI_LOG", "更新失败: " + m); }
                    });
                } else {
                    // 情况 B：今天还没上报过，执行 POST 新增
                    cloudbase.request("POST", "/v1/rdb/rest/usage_report", body, null, null, new CloudBaseCallback<Object>() {
                        @Override public void onSuccess(Object res) { Log.d("ANTI_LOG", "今日首次上报成功"); }
                        @Override public void onError(int c, String m) { Log.e("ANTI_LOG", "新增失败: " + m); }
                    });
                }
            }

            @Override
            public void onError(int code, String message) {
                Log.e("ANTI_LOG", "查询上报记录失败: " + message);
            }
        });
    }
    private void checkAndEnforceLimit(long currentLocalMillis) {
        Log.d("ANTI_LOG","检查限额逻辑");
        String rawId = UserManager.getInstance(requireContext()).getUserId();
        // 强制去掉 .0
        if (rawId.contains(".")) {
            rawId = rawId.split("\\.")[0];
        }
        final String userId = rawId; // 匿名内部类使用
        // 查找我的管控策略
        String path = "/v1/rdb/rest/control_policy?userId=eq." + userId;
        Log.d("ANTI_LOG",path);

        cloudbase.request("GET", path, null, null, new TypeToken<List<Map<String, Object>>>() {}, new CloudBaseCallback<List<Map<String, Object>>>() {
            @Override
            public void onSuccess(List<Map<String, Object>> data) {
                if (data == null || data.isEmpty()) { Log.w("ANTI_LOG","限额数据访问成功但是数据为空"); return;}
                Log.d("data",data.toString());
                Map<String, Object> policy = data.get(0);
                Log.d("policy",policy.toString());

                // --- 校验 1: 每日总时长拦截 ---
                if (policy.get("totalLimit") != null) {
                    // CloudBase 返回的数字默认是 Double 类型，需转为 int
                    int totalLimitMinutes = ((Number) policy.get("totalLimit")).intValue();
                    long currentMinutes = currentLocalMillis / 1000 / 60;
                    Log.d("COMPARTMENTAL",totalLimitMinutes+":"+currentMinutes);
                    if (currentMinutes >= totalLimitMinutes) {
                        showLockScreen(totalLimitMinutes); // 触发总限额拦截
                        return; // 如果总时长超了，就没必要查单应用了
                    }
                }

                // --- 校验 2: 单应用时长拦截 (可选功能，按需开启) ---
                if (policy.get("appLimits") != null) {
                    String appLimitsStr = (String) policy.get("appLimits");
                    // 解析格式如 {"com.tencent.mm": 30} 的 Map
                    Map<String, Double> appLimitsMap = new Gson().fromJson(appLimitsStr, new TypeToken<Map<String, Double>>(){}.getType());

                    // 此时我们需要拿到刚才 loadUsageData 算出来的 appMap
                    // 假设你已经把 loadUsageData 里的 map 存为了成员变量 currentAppMap
                    checkIndividualApps(appLimitsMap);
                }
            }

            @Override
            public void onError(int code, String message) {
                Log.e("ANTI_LOG", "拉取管控策略失败: " + message);
            }
        });
    }
    private void showLockScreen(int limitMinutes) {
        // 检查 Fragment 是否还依附在 Activity 上，防止异步回调导致的崩溃
        if (!isAdded() || getContext() == null) return;

        // 使用 Material 设计风格的对话框
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("⚠️ 每日限额已满")
                .setMessage("监管者设置的今日限额为 " + limitMinutes + " 分钟。\n\n你今天表现已经很棒了，请放下手机，休息一下眼睛吧。")
                .setCancelable(false) // 【关键】设置为不可取消（点击外部或返回键无效）
                .setPositiveButton("我知道了", (dialog, which) -> {
                    // 点击按钮后，强制退出应用或返回桌面
                    exitApp();
                })
                .show();
    }

    /**
     * 辅助方法：强制退出 App 或回到手机主屏幕
     */
    private void exitApp() {
        if (getActivity() != null) {
            // 1. 回到手机桌面（不销毁进程，但让用户看不了 App）
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);

            // 2. 可选：彻底关掉当前的 Activity
            getActivity().finish();
        }
    }
    // 辅助方法：检查具体每一个 App
    private void checkIndividualApps(Map<String, Double> appLimitsMap) {
        // 这个逻辑通常需要配合一个“当前运行中”的监测
        // 在 StatFragment 这种静态列表里，你可以根据之前算出的 list 进行循环判定
        // 如果某个包名的已用时间 > 限额，直接弹出对应的 App 锁定提示
    }
}