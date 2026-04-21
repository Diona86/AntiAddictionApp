package com.exampl.antiaddiction.fragment;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.exampl.antiaddiction.R;
import com.exampl.antiaddiction.adapter.TodoAdapter;
import com.exampl.antiaddiction.db.AppDatabase;
import com.exampl.antiaddiction.model.TodoItem;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TodoFragment extends Fragment {
    public static final String KEY_BOARD = "board_key";
    private static final String DATE_PATTERN = "yyyy-MM-dd";

    private List<TodoItem> localTaskData = new ArrayList<>();
    private String currentBoardKey = "default";
    private TodoAdapter todoAdapter, processAdapter, doneAdapter;
    private TextView todoTitleView, processingTitleView, doneTitleView;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_todo, container, false);

        // 1. 获取看板 Key
        if (getArguments() != null) {
            String boardKey = getArguments().getString(KEY_BOARD);
            if (boardKey != null) currentBoardKey = boardKey;
        }

        // 2. 初始化标题
        TextView tvCurrentBoard = view.findViewById(R.id.tv_current_board);
        if (tvCurrentBoard != null) {
            tvCurrentBoard.setText(getBoardDisplayName(currentBoardKey));
        }

        // 3. 初始化三列 UI
        setupColumn(view.findViewById(R.id.colTodo), "待处理", 0);
        setupColumn(view.findViewById(R.id.colProcessing), "进行中", 1);
        setupColumn(view.findViewById(R.id.colDone), "已完成", 2);

        // 4. 绑定按钮
        view.findViewById(R.id.btnAddTodo).setOnClickListener(v -> showAddDialog());

        // 5. 加载数据
        loadDataFromDb();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadDataFromDb();
    }

    /**
     * 统一的数据加载方法
     */
    private void loadDataFromDb() {
        Context context = getContext();
        if (context == null) {
            return;
        }
        // 提前获取 ApplicationContext，防止子线程运行时 Fragment 已销毁
        Context appContext = context.getApplicationContext();
        final String boardKeySnapshot = currentBoardKey;
        final String todayDate = getTodayDate();

        new Thread(() -> {
            try {
                // 直接按看板 + 日期查询，避免把其他看板数据拉到内存再过滤
                List<TodoItem> tasks = AppDatabase.getInstance(appContext).todoDao().getTasksByBoardAndDate(boardKeySnapshot, todayDate);
                if (tasks == null) {
                    tasks = new ArrayList<>();
                }
                // 回到主线程更新，增加 isAdded() 判断防止崩溃
                if (isAdded() && getActivity() != null) {
                    List<TodoItem> finalTasks = tasks;
                    getActivity().runOnUiThread(() -> {
                        this.localTaskData = finalTasks;
                        refreshKanban();
                    });
                }
            } catch (Exception e) {
                Log.e("ANTI_LOG", "加载失败", e);
            }
        }).start();
    }

    private void setupColumn(View columnView, String title, int status) {
        TextView tvTitle = columnView.findViewById(R.id.tvColumnTitle);
        tvTitle.setText(title);
        TextView tvEmpty = columnView.findViewById(R.id.tvColumnEmpty);

        RecyclerView rv = columnView.findViewById(R.id.rvTaskList);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));

        TodoAdapter adapter = new TodoAdapter(new ArrayList<>(), item -> moveTask(item));
        rv.setAdapter(adapter);

        if (status == 0) {
            todoAdapter = adapter;
            todoTitleView = tvTitle;
        } else if (status == 1) {
            processAdapter = adapter;
            processingTitleView = tvTitle;
        } else {
            doneAdapter = adapter;
            doneTitleView = tvTitle;
        }
        updateColumnEmptyState(rv, tvEmpty, 0);
    }

    private void moveTask(TodoItem item) {
        if (item.status >= 2) {
            Toast.makeText(getContext(), "任务已完成，无需继续流转", Toast.LENGTH_SHORT).show();
            return;
        }
        item.status = item.status + 1;
        Context context = getContext();
        if (context == null) {
            return;
        }
        Context appContext = context.getApplicationContext();

        new Thread(() -> {
            try {
                AppDatabase.getInstance(appContext).todoDao().update(item);
                if (isAdded() && getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        loadDataFromDb();
                        Toast.makeText(getContext(), "状态已同步", Toast.LENGTH_SHORT).show();
                    });
                }
                updateStatusOnBackend(item);
            } catch (Exception e) {
                Log.e("ANTI_LOG", "更新失败", e);
            }
        }).start();
    }

    private void refreshKanban() {
        List<TodoItem> todo = new ArrayList<>();
        List<TodoItem> processing = new ArrayList<>();
        List<TodoItem> done = new ArrayList<>();

        for (TodoItem item : localTaskData) {
            if (item == null) continue;
            if (item.status == 0) todo.add(item);
            else if (item.status == 1) processing.add(item);
            else if (item.status == 2) done.add(item);
        }
        sortTasksForKanban(todo);
        sortTasksForKanban(processing);
        sortTasksForKanban(done);

        if (todoAdapter != null) {
            todoAdapter.updateList(todo);
        }
        if (processAdapter != null) {
            processAdapter.updateList(processing);
        }
        if (doneAdapter != null) {
            doneAdapter.updateList(done);
        }
        updateColumnTitles(todo.size(), processing.size(), done.size());
        syncColumnEmptyStates(todo.size(), processing.size(), done.size());
    }

    private void showAddDialog() {
        View v = LayoutInflater.from(getContext()).inflate(R.layout.dialog_add_todo, null);
        EditText etContent = v.findViewById(R.id.etTodoContent);
        Spinner spPriority = v.findViewById(R.id.spPriority);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("添加任务到 [" + getBoardDisplayName(currentBoardKey) + "]")
                .setView(v)
                .setPositiveButton("添加", (dialog, which) -> {
                    String content = etContent.getText().toString().trim();
                    String priority = spPriority.getSelectedItem().toString();
                    if (!content.isEmpty()) {
                        addNewTaskLocal(content, priority);
                    } else {
                        Toast.makeText(getContext(), "内容不能为空", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void addNewTaskLocal(String content, String priority) {
        String todayDate = getTodayDate();
        // ID 传 null，让 Room 自动生成
        TodoItem newItem = new TodoItem(null, content, 0, priority, currentBoardKey, todayDate);
        Context context = getContext();
        if (context == null) {
            return;
        }
        Context appContext = context.getApplicationContext();

        new Thread(() -> {
            try {
                // 存入数据库
                AppDatabase.getInstance(appContext).todoDao().insert(newItem);
                // 存完后重新拉取最新数据
                loadDataFromDb();
                // 同步后端
                syncWithBackend(newItem);
            } catch (Exception e) {
                Log.e("ANTI_LOG", "保存失败", e);
            }
        }).start();
    }

    private String getBoardDisplayName(String key) {
        if (key == null) return "默认看板";
        switch (key) {
            case "study": return "学习笔记";
            case "work": return "工作任务";
            case "personal": return "个人备忘";
            default: return "默认看板";
        }
    }

    private void updateStatusOnBackend(TodoItem item) {
        Log.d("ANTI_LOG", "状态更新准备同步: " + item.content + " -> 状态 " + item.status);
    }

    private void syncWithBackend(TodoItem item) {
        Log.d("ANTI_LOG", "新任务准备同步: " + item.content);
    }

    private void sortTasksForKanban(List<TodoItem> tasks) {
        Collections.sort(tasks, Comparator
                .comparingInt((TodoItem task) -> priorityRank(task.priority))
                .thenComparingLong(task -> task.id == null ? Long.MAX_VALUE : task.id));
    }

    private int priorityRank(String priority) {
        if ("高".equals(priority)) return 0;
        if ("中".equals(priority)) return 1;
        return 2;
    }

    private String getTodayDate() {
        return new SimpleDateFormat(DATE_PATTERN, Locale.getDefault()).format(new Date());
    }

    private void updateColumnTitles(int todoCount, int processingCount, int doneCount) {
        if (todoTitleView != null) {
            todoTitleView.setText("待处理 (" + todoCount + ")");
        }
        if (processingTitleView != null) {
            processingTitleView.setText("进行中 (" + processingCount + ")");
        }
        if (doneTitleView != null) {
            doneTitleView.setText("已完成 (" + doneCount + ")");
        }
    }

    private void syncColumnEmptyStates(int todoCount, int processingCount, int doneCount) {
        updateColumnEmptyState(getView() == null ? null : getView().findViewById(R.id.colTodo), todoCount);
        updateColumnEmptyState(getView() == null ? null : getView().findViewById(R.id.colProcessing), processingCount);
        updateColumnEmptyState(getView() == null ? null : getView().findViewById(R.id.colDone), doneCount);
    }

    private void updateColumnEmptyState(View column, int count) {
        if (column == null) return;
        RecyclerView rv = column.findViewById(R.id.rvTaskList);
        TextView tvEmpty = column.findViewById(R.id.tvColumnEmpty);
        updateColumnEmptyState(rv, tvEmpty, count);
    }

    private void updateColumnEmptyState(RecyclerView rv, TextView tvEmpty, int count) {
        if (rv == null || tvEmpty == null) return;
        boolean isEmpty = count <= 0;
        rv.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        tvEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
    }
}