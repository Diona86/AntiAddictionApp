package com.exampl.antiaddiction.fragment;

import android.app.usage.UsageStats;
import android.os.Bundle;
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
import com.exampl.antiaddiction.adapter.TodoAdapter;
import com.exampl.antiaddiction.db.AppDatabase;
import com.exampl.antiaddiction.model.TodoItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CalendarFragment extends Fragment {

    private CalendarView calendarView;
    private RecyclerView recyclerView;
    private TodoAdapter adapter;
    private TextView tvDateHint;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_calendar, container, false);

        calendarView = view.findViewById(R.id.calendarView);
        recyclerView = view.findViewById(R.id.rvCalendarTasks);
        tvDateHint = view.findViewById(R.id.tvSelectedDate);

        // 初始化列表
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        // 这里复用之前的 TodoAdapter，点击逻辑暂时传空或者传弹窗
        adapter = new TodoAdapter(new ArrayList<>(), item -> {
            // 这里可以点一下弹出任务详情，或者切换状态
        });
        recyclerView.setAdapter(adapter);

        // 1. 设置日历点击监听
        calendarView.setOnDateChangeListener((view1, year, month, dayOfMonth) -> {
            // 注意：month 是从 0 开始的（1月是0），所以要 +1
            String selectedDate = String.format(Locale.getDefault(), "%d-%02d-%02d", year, month + 1, dayOfMonth);

            // 2. 更新标题提示
            tvDateHint.setText(selectedDate + " 的任务清单");

            // 3. 去数据库查数据并刷新列表
            loadTasksFromDb(selectedDate);
        });

        return view;
    }

    private void loadTasksFromDb(String date) {
        // 直接从 Room 查数据
        // 注意：因为我们在 AppDatabase 开启了 allowMainThreadQueries，所以这里可以直接查
        List<TodoItem> tasks = AppDatabase.getInstance(requireContext())
                .todoDao()
                .getTasksByDate(date);

        // 4. 把查到的数据喂给 Adapter
        if(adapter!=null) {
            adapter.updateList(tasks);
        }
    }
}