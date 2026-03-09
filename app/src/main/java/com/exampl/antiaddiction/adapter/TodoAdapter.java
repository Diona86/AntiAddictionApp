package com.exampl.antiaddiction.adapter;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.exampl.antiaddiction.R;
import com.exampl.antiaddiction.model.TodoItem;
import java.util.List;

public class TodoAdapter extends RecyclerView.Adapter<TodoAdapter.TodoViewHolder> {

    private List<TodoItem> taskList;
    private OnTaskClickListener listener;

    // 定义一个接口，方便在 Fragment 里处理点击逻辑
    public interface OnTaskClickListener {
        void onTaskClick(TodoItem item);
    }

    public TodoAdapter(List<TodoItem> taskList, OnTaskClickListener listener) {
        this.taskList = taskList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public TodoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_todo, parent, false);
        return new TodoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TodoViewHolder holder, int position) {
        TodoItem item = taskList.get(position);
        holder.tvContent.setText(item.content);
        holder.tvPriority.setText(item.priority);

        // --- 设计感细节：根据优先级设置不同的颜色 ---
        if ("高".equals(item.priority)) {
            holder.tvPriority.setTextColor(Color.parseColor("#FF5252")); // 红色
            holder.tvPriority.setBackgroundColor(Color.parseColor("#FFE0E0"));
        } else if ("中".equals(item.priority)) {
            holder.tvPriority.setTextColor(Color.parseColor("#FFAB40")); // 橙色
            holder.tvPriority.setBackgroundColor(Color.parseColor("#FFF3E0"));
        } else {
            holder.tvPriority.setTextColor(Color.parseColor("#4CAF50")); // 绿色
            holder.tvPriority.setBackgroundColor(Color.parseColor("#E8F5E9"));
        }

        // 绑定点击事件：点击卡片，触发状态流转
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onTaskClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return taskList.size();
    }

    // 内部类：管理布局里的控件
    static class TodoViewHolder extends RecyclerView.ViewHolder {
        TextView tvContent, tvPriority;

        public TodoViewHolder(@NonNull View itemView) {
            super(itemView);
            tvContent = itemView.findViewById(R.id.tvTaskContent);
            tvPriority = itemView.findViewById(R.id.tvPriority);
        }
    }

    // 更新数据的方法
    public void updateList(List<TodoItem> newList) {
        this.taskList = newList;
        notifyDataSetChanged();
    }
}