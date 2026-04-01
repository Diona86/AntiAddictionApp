package com.exampl.antiaddiction.fragment;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import com.exampl.antiaddiction.adapter.UsageAdapter;
import com.exampl.antiaddiction.cloudbase.CloudBaseCallback;
import com.exampl.antiaddiction.cloudbase.CloudBaseClient;
import com.exampl.antiaddiction.manager.UserManager;
import com.exampl.antiaddiction.model.AppUsageInfo;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
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

    private RecyclerView recyclerView;
    private UsageAdapter adapter;
    private TextView tvTotalTime;
    private ProgressBar progressBar;
    private String role ;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // 加载 Fragment 的布局
        View view = inflater.inflate(R.layout.fragment_stat, container, false);

        // 初始化控件
         role = UserManager.getInstance(requireContext()).getRole();
        recyclerView = view.findViewById(R.id.rvStatList);
        tvTotalTime = view.findViewById(R.id.tvTotalTimeInFrag);
        progressBar = view.findViewById(R.id.pbTotalUsage);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // 页面创建好后加载数据
        if("self".equals(role))
        loadUsageData();
        else {
            Log.d("ANTI_LOG","是监管者");
            //展示绑定的自律者的使用情况
        }
    }

    private void loadUsageData() {
        UsageStatsManager usm = (UsageStatsManager) requireContext().getSystemService(Context.USAGE_STATS_SERVICE);
        PackageManager pm = requireContext().getPackageManager();

        long endTime = System.currentTimeMillis();
        long startTime = endTime - 1000 * 60 * 60 * 24;

        List<UsageStats> stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime);

        if (stats == null) return;

        // --- 核心优化：使用 Map 合并重复的应用包名 ---
        Map<String, AppUsageInfo> map = new HashMap<>();
        long totalMillis = 0;

        for (UsageStats usageStats : stats) {
            long time = usageStats.getTotalTimeInForeground();
            if (time <= 0) continue;

            String pkg = usageStats.getPackageName();
            totalMillis += time;

            if (map.containsKey(pkg)) {
                map.get(pkg).usageTime += time;
            } else {
                String appName;
                Drawable icon;
                try {
                    ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                    appName = pm.getApplicationLabel(ai).toString();
                    icon = pm.getApplicationIcon(ai);
                } catch (Exception e) {
                    appName = pkg;
                    icon = ContextCompat.getDrawable(requireContext(), R.mipmap.ic_launcher);
                }
                map.put(pkg, new AppUsageInfo(appName, pkg, icon, time, ""));
            }
        }

        // --- 核心优化：转换 List 并排序 ---
        List<AppUsageInfo> list = new ArrayList<>(map.values());
        Collections.sort(list, (a, b) -> Long.compare(b.usageTime, a.usageTime));

        // 格式化时间字符串
        for (AppUsageInfo info : list) {
            info.timeFormatted = formatTime(info.usageTime);
        }

        // 更新 UI
        tvTotalTime.setText(formatTime(totalMillis));
        progressBar.setProgress((int) (totalMillis / (1000 * 60 * 60 * 8.0) * 100)); // 假设 8 小时为 100%

        adapter = new UsageAdapter(list);
        recyclerView.setAdapter(adapter);

        //查询限额
        checkAndEnforceLimit(totalMillis);

    }
    //查询限额
    private void checkAndEnforceLimit(long currentLocalMillis) {
        String userId = UserManager.getInstance(requireContext()).getUserId();
        String path = "/v1/rdb/rest/control_policy?userId=eq." + userId;

        TypeToken<List<Map<String, Object>>> typeToken = new TypeToken<List<Map<String, Object>>>() {};
        CloudBaseClient cloudbase = new CloudBaseClient(getString(R.string.CLOUDBASE_ENV_ID), getString(R.string.CLOUDBASE_ACCESS_TOKEN));

        cloudbase.request("GET", path, null, null, typeToken, new CloudBaseCallback<List<Map<String, Object>>>() {
            @Override
            public void onSuccess(List<Map<String, Object>> data) {
                if (data != null && !data.isEmpty()) {
                    // 1. 拿到限额（分钟）
                    int limitMinutes = ((Double) data.get(0).get("totalLimit")).intValue();

                    // 2. 本地计算当前已玩分钟
                    long currentMinutes = currentLocalMillis / 1000 / 60;

                    // 3. 核心对比
                    if (currentMinutes >= limitMinutes) {
                        showLockScreen(limitMinutes); // ！！！执行拦截！！！
                    }
                }
            }
            @Override
            public void onError(int code, String message) {}
        });
    }

    private void showLockScreen(int limit) {
        // 弹出一个全屏、不可取消的 Dialog 或跳转到一个专门的 LockActivity
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("⚠️ 您的时间已用完")
                .setMessage("监管者设置的每日限额为 " + limit + " 分钟。请放下手机，去看看窗外的风景吧。")
                .setCancelable(false) // 强制不可取消
                .setPositiveButton("我知道了", (d, w) -> {
                    // 这里可以执行更狠的操作，比如返回桌面
                    getActivity().finish();
                })
                .show();
    }

    private String formatTime(long millis) {
        long minutes = (millis / (1000 * 60)) % 60;
        long hours = (millis / (1000 * 60 * 60));
        return String.format("%02dh %02dm", hours, minutes);
    }
    // 自律者上报数据
    private void syncUsageToCloud(long totalMillis) {
        String userId = UserManager.getInstance(requireContext()).getUserId();
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        String supervisorId=UserManager.getInstance(requireContext()).getSupervisorId();

        // 1. 准备数据
        Map<String, Object> body = new HashMap<>();
        body.put("userId", userId);
        body.put("totalTime", totalMillis);
        body.put("dateStr", today);
        body.put("supervisorId",supervisorId);

        CloudBaseClient cloudbase = new CloudBaseClient(getString(R.string.CLOUDBASE_ENV_ID), getString(R.string.CLOUDBASE_ACCESS_TOKEN));

        // 2. 这里简化逻辑：直接 POST。
        // 进阶逻辑应该是先 GET 查今天有没有记录，有则 PATCH，无则 POST。
        cloudbase.request("POST", "/v1/rdb/rest/usage_report", body, null, null, new CloudBaseCallback<Object>() {
            @Override
            public void onSuccess(Object data) {
                Toast.makeText(getContext(), "数据已同步至云端", Toast.LENGTH_SHORT).show();
            }
            @Override
            public void onError(int code, String message) {
                Log.e("ANTI_LOG", "上报失败: " + message);
            }
        });
    }
    // 监管者设置限额（比如设置 60 分钟）
    private void setLimitForChild(String childId, int minutes) {
        Map<String, Object> body = new HashMap<>();
        String userId=UserManager.getInstance(requireContext()).getUserId();
        body.put("userId", childId);
        body.put("totalLimit", minutes);

        CloudBaseClient cloudbase = new CloudBaseClient(getString(R.string.CLOUDBASE_ENV_ID), getString(R.string.CLOUDBASE_ACCESS_TOKEN));

        // 同样简化：直接往对应的 userId 发送 PATCH (假设记录已存在)
        // 路径：/v1/rdb/rest/control_policy?userId=eq.childId
        String path = "/v1/rdb/rest/control_policy?userId=eq." + childId;

        cloudbase.request("PATCH", path, body, null, null, new CloudBaseCallback<Object>() {
            @Override
            public void onSuccess(Object data) {
                Toast.makeText(getContext(), "限额设置成功", Toast.LENGTH_SHORT).show();
            }
            @Override
            public void onError(int code, String message) {
                // 如果 PATCH 失败（404），则执行 POST 创建
                cloudbase.request("POST", "/v1/rdb/rest/control_policy", body, null, null, new CloudBaseCallback<Object>() {
                    @Override
                    public void onSuccess(Object data) {

                    }

                    @Override
                    public void onError(int code, String message) {

                    }
                });
            }
        });
    }
}