package com.exampl.antiaddiction.fragment;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CalendarView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.exampl.antiaddiction.R;
import com.exampl.antiaddiction.adapter.CalendarTaskAdapter;
import com.exampl.antiaddiction.db.AppDatabase;
import com.exampl.antiaddiction.model.DailyUsageRecord;
import com.exampl.antiaddiction.model.TodoItem;
import com.exampl.antiaddiction.utils.Utils;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CalendarFragment extends Fragment {

    private CalendarView calendarView;
    private RecyclerView recyclerView;
    private CalendarTaskAdapter adapter;
    private TextView tvDateHint;
    private TextView tvUsageTotal;
    private TextView tvOverLimitApps;
    private TextView tvTaskSummary;
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile String latestRequestedDate = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_calendar, container, false);

        calendarView = view.findViewById(R.id.calendarView);
        recyclerView = view.findViewById(R.id.rvCalendarTasks);
        tvDateHint = view.findViewById(R.id.tvSelectedDate);
        tvUsageTotal = view.findViewById(R.id.tvUsageTotal);
        tvOverLimitApps = view.findViewById(R.id.tvOverLimitApps);
        tvTaskSummary = view.findViewById(R.id.tvTaskSummary);

        // 初始化列表
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setNestedScrollingEnabled(false);
        adapter = new CalendarTaskAdapter(new ArrayList<>());
        recyclerView.setAdapter(adapter);

        // 1. 设置日历点击监听
        calendarView.setOnDateChangeListener((view1, year, month, dayOfMonth) -> {
            // 注意：month 是从 0 开始的（1月是0），所以要 +1
            String selectedDate = String.format(Locale.getDefault(), "%d-%02d-%02d", year, month + 1, dayOfMonth);
            loadCalendarData(selectedDate);
        });

        // 默认加载今天
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendarView.getDate());
        loadCalendarData(today);

        return view;
    }

    private void loadCalendarData(String date) {
        latestRequestedDate = date;
        // 1. 更新标题提示
        tvDateHint.setText(date + " 使用概览");

        // 2/3. 异步加载任务与使用统计，避免主线程查询 Room
        loadCalendarDataAsync(date);
    }

    private void loadCalendarDataAsync(String date) {
        Context context = getContext();
        if (!isAdded() || context == null) {
            return;
        }
        Context appContext = context.getApplicationContext();
        dbExecutor.execute(() -> {
            List<TodoItem> tasks = AppDatabase.getInstance(appContext)
                    .todoDao()
                    .getTasksByDate(date);
            if (tasks == null) {
                tasks = new ArrayList<>();
            }
            Collections.sort(tasks, Comparator
                    .comparingInt((TodoItem t) -> t.status == 2 ? 1 : 0)
                    .thenComparing(t -> t.priority == null ? "中" : t.priority));

            DailyUsageRecord record = AppDatabase.getInstance(appContext)
                    .dailyUsageDao()
                    .getByDate(date);

            List<TodoItem> finalTasks = tasks;
            mainHandler.post(() -> {
                if (!isAdded() || !date.equals(latestRequestedDate)) {
                    return;
                }
                if (adapter != null) {
                    adapter.updateList(finalTasks);
                }
                updateTaskSummary(finalTasks);
                renderUsageSummary(record);
            });
        });
    }

    private void renderUsageSummary(DailyUsageRecord record) {
        if (record == null) {
            tvUsageTotal.setText("应用总时长: 暂无记录");
            tvOverLimitApps.setText("超额应用: 无");
            return;
        }

        tvUsageTotal.setText("应用总时长: " + Utils.formatTime(record.totalUsageMillis));
        List<String> overApps = new Gson().fromJson(record.overLimitAppsJson, new TypeToken<List<String>>() {}.getType());
        if (overApps == null || overApps.isEmpty()) {
            tvOverLimitApps.setText("超额应用: 无");
        } else {
            tvOverLimitApps.setText("超额应用: " + android.text.TextUtils.join("、", overApps));
        }
    }

    @Override
    public void onDestroyView() {
        mainHandler.removeCallbacksAndMessages(null);
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        dbExecutor.shutdownNow();
        super.onDestroy();
    }

    private void updateTaskSummary(List<TodoItem> tasks) {
        if (tvTaskSummary == null) {
            return;
        }
        int doneCount = 0;
        int todoCount = 0;
        if (tasks != null) {
            for (TodoItem item : tasks) {
                if (item != null && item.status == 2) {
                    doneCount++;
                } else {
                    todoCount++;
                }
            }
        }
        tvTaskSummary.setText("未完成 " + todoCount + " · 已完成 " + doneCount);
    }
}