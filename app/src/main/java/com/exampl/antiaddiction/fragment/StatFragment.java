package com.exampl.antiaddiction.fragment;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.exampl.antiaddiction.R;
import com.exampl.antiaddiction.adapter.UsageAdapter;
import com.exampl.antiaddiction.model.AppUsageInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StatFragment extends Fragment {

    private RecyclerView recyclerView;
    private UsageAdapter adapter;
    private TextView tvTotalTime;
    private ProgressBar progressBar;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // 加载 Fragment 的布局
        View view = inflater.inflate(R.layout.fragment_stat, container, false);

        // 初始化控件
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
        loadUsageData();
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
    }

    private String formatTime(long millis) {
        long minutes = (millis / (1000 * 60)) % 60;
        long hours = (millis / (1000 * 60 * 60));
        return String.format("%02dh %02dm", hours, minutes);
    }
}