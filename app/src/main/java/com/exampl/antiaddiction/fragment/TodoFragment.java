package com.exampl.antiaddiction.fragment;

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
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TodoFragment extends Fragment {
    public static final String KEY_BOARD = "board_key";

    // 数据源（所有任务）
    private  List<TodoItem> localTaskData = new ArrayList<>();

    // 当前显示的看板（默认 default）
    private String currentBoardKey = "default";

    // 三个适配器
    private TodoAdapter todoAdapter, processAdapter, doneAdapter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_todo, container, false);

        // 读取从 MainActivity 传来的 board_key
        Bundle args = getArguments();
        if (args != null) {
            String boardKey = args.getString(KEY_BOARD);
            if (boardKey != null && !boardKey.isEmpty()) {
                currentBoardKey = boardKey;
            }
        }

        // 显示当前看板名称
        TextView tvCurrentBoard = view.findViewById(R.id.tv_current_board);
        if (tvCurrentBoard != null) {
            tvCurrentBoard.setText(getBoardDisplayName(currentBoardKey));
        }

        // 初始化三列 UI
        setupColumn(view.findViewById(R.id.colTodo), "待处理", 0);
        setupColumn(view.findViewById(R.id.colProcessing), "进行中", 1);
        setupColumn(view.findViewById(R.id.colDone), "已完成", 2);

        // 绑定新增按钮
        view.findViewById(R.id.btnAddTodo).setOnClickListener(v -> showAddDialog());

        // 首次刷新
        refreshKanban();

        return view;
    }

    // 根据 key 返回显示名称
    private String getBoardDisplayName(String key) {
        switch (key) {
            case "study":
                return "学习笔记";
            case "work":
                return "工作任务";
            case "personal":
                return "个人备忘";
            default:
                return "默认看板";
        }
    }

    private void setupColumn(View columnView, String title, int status) {
        TextView tvTitle = columnView.findViewById(R.id.tvColumnTitle);
        tvTitle.setText(title);

        RecyclerView rv = columnView.findViewById(R.id.rvTaskList);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));

        TodoAdapter adapter = new TodoAdapter(new ArrayList<>(), item -> moveTask(item));
        rv.setAdapter(adapter);

        if (status == 0) todoAdapter = adapter;
        else if (status == 1) processAdapter = adapter;
        else doneAdapter = adapter;
    }

    private void moveTask(TodoItem item) {
        item.status = (item.status + 1) % 3;
        updateStatusOnBackend(item);
        refreshKanban();
    }

    private void refreshKanban() {
        List<TodoItem> todo = new ArrayList<>();
        List<TodoItem> processing = new ArrayList<>();
        List<TodoItem> done = new ArrayList<>();

        // 只显示当前看板的任务
        for (TodoItem item : localTaskData) {
            if (!currentBoardKey.equals(item.boardKey)) {
                continue;
            }
            if (item.status == 0) todo.add(item);
            else if (item.status == 1) processing.add(item);
            else if (item.status == 2) done.add(item);
        }

        if (todoAdapter != null) todoAdapter.updateList(todo);
        if (processAdapter != null) processAdapter.updateList(processing);
        if (doneAdapter != null) doneAdapter.updateList(done);
    }

    private void showAddDialog() {
        View v = LayoutInflater.from(getContext()).inflate(R.layout.dialog_add_todo, null);
        EditText etContent = v.findViewById(R.id.etTodoContent);
        Spinner spPriority = v.findViewById(R.id.spPriority);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("添加新任务")
                .setView(v)
                .setPositiveButton("添加", (dialog, which) -> {
                    String content = etContent.getText().toString();
                    String priority = spPriority.getSelectedItem().toString();
                    if (!content.trim().isEmpty()) {
                        addNewTaskLocal(content, priority);
                    } else {
                        Toast.makeText(getContext(), "内容不能为空", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void addNewTaskLocal(String content, String priority) {
        // 1. 准备数据
        String todayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        TodoItem newItem = new TodoItem(null, content, 0, priority, currentBoardKey, todayDate);

        // 2. 开启子线程执行“存”和“取”
        new Thread(() -> {
            // 【子线程】A. 执行存库
            AppDatabase.getInstance(requireContext()).todoDao().insert(newItem);

            // 【子线程】B. 存完后，立即读取最新的全部数据
            // 这样可以确保 localTaskData 是最新的“真理”
            List<TodoItem> newData = AppDatabase.getInstance(requireContext()).todoDao().getAllTasks();

            // 【主线程】C. 回到主线程更新 UI
            requireActivity().runOnUiThread(() -> {
                this.localTaskData = newData; // 更新内存数据
                refreshKanban(); // 刷新三个看板的适配器
                Toast.makeText(getContext(), "保存成功", Toast.LENGTH_SHORT).show();
            });

            // 顺便执行同步（如果以后有网络请求，也建议在子线程做）
            syncWithBackend(newItem);

        }).start();
    }


    private void updateStatusOnBackend(TodoItem item) {
        Log.d("ANTI_LOG", "状态更新准备同步: " + item.content + " -> 状态 " + item.status);
    }

    private void syncWithBackend(TodoItem item) {
        Log.d("ANTI_LOG", "新任务准备同步: " + item.content);
    }
}