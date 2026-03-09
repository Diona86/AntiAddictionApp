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
import com.exampl.antiaddiction.model.TodoItem;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

public class TodoFragment extends Fragment {

    // 数据源
    private List<TodoItem> localTaskData = new ArrayList<>();
    // 三个适配器
    private TodoAdapter todoAdapter, processAdapter, doneAdapter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_todo, container, false);

        // 1. 初始化三列 UI (此时数据是空的)
        setupColumn(view.findViewById(R.id.colTodo), "待处理", 0);
        setupColumn(view.findViewById(R.id.colProcessing), "进行中", 1);
        setupColumn(view.findViewById(R.id.colDone), "已完成", 2);

        // 2. 绑定新增按钮
        view.findViewById(R.id.btnAddTodo).setOnClickListener(v -> showAddDialog());

        return view;
    }

    // 初始化每一列的 RecyclerView
    private void setupColumn(View columnView, String title, int status) {
        TextView tvTitle = columnView.findViewById(R.id.tvColumnTitle);
        tvTitle.setText(title);

        RecyclerView rv = columnView.findViewById(R.id.rvTaskList);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));

        // 每个列拥有一个独立的 Adapter 实例
        // 点击任务时，调用 moveTask 方法
        TodoAdapter adapter = new TodoAdapter(new ArrayList<>(), item -> moveTask(item));
        rv.setAdapter(adapter);

        // 保存引用，方便后面刷新数据
        if (status == 0) todoAdapter = adapter;
        else if (status == 1) processAdapter = adapter;
        else doneAdapter = adapter;
    }

    // 处理状态流转的核心逻辑
    private void moveTask(TodoItem item) {
        // 状态流转：0 -> 1 -> 2 -> 0
        item.status = (item.status + 1) % 3;

        // 留下的空方法
        updateStatusOnBackend(item);

        // 重新分发数据刷新 UI
        refreshKanban();
    }

    // 核心：将大列表 localTaskData 分配给三个不同的 Adapter
    private void refreshKanban() {
        List<TodoItem> todo = new ArrayList<>();
        List<TodoItem> processing = new ArrayList<>();
        List<TodoItem> done = new ArrayList<>();

        for (TodoItem item : localTaskData) {
            if (item.status == 0) todo.add(item);
            else if (item.status == 1) processing.add(item);
            else done.add(item);
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
        TodoItem newItem = new TodoItem(System.currentTimeMillis(), content, 0, priority);
        localTaskData.add(newItem);

        syncWithBackend(newItem); // 空方法
        refreshKanban(); // 刷新
    }

    // --- 留给未来的后端占位方法 ---
    private void updateStatusOnBackend(TodoItem item) {
        Log.d("ANTI_LOG", "状态更新准备同步: " + item.content + " -> 状态 " + item.status);
    }

    private void syncWithBackend(TodoItem item) {
        Log.d("ANTI_LOG", "新任务准备同步: " + item.content);
    }
}