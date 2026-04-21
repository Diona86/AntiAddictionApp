package com.exampl.antiaddiction.adapter;

import android.graphics.Color;
import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.exampl.antiaddiction.R;
import com.exampl.antiaddiction.model.TodoItem;

import java.util.ArrayList;
import java.util.List;

public class CalendarTaskAdapter extends RecyclerView.Adapter<CalendarTaskAdapter.VH> {

    private List<TodoItem> tasks = new ArrayList<>();

    public CalendarTaskAdapter(List<TodoItem> tasks) {
        if (tasks != null) {
            this.tasks = tasks;
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_todo, parent, false);
        return new VH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        TodoItem item = tasks.get(position);
        boolean done = item != null && item.status == 2;

        String content = item == null ? "" : item.content;
        holder.tvContent.setText(content);

        String priority = item == null ? "中" : item.priority;
        String statusText = done ? "已完成" : "未完成";
        holder.tvPriority.setText(statusText + " · 优先级 " + priority);

        if (done) {
            holder.tvPriority.setTextColor(Color.parseColor("#2E7D32"));
            holder.tvPriority.setBackgroundColor(Color.parseColor("#E8F5E9"));
            holder.tvContent.setPaintFlags(holder.tvContent.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            holder.tvContent.setTextColor(Color.parseColor("#8A8A8A"));
        } else {
            holder.tvPriority.setTextColor(Color.parseColor("#EF6C00"));
            holder.tvPriority.setBackgroundColor(Color.parseColor("#FFF3E0"));
            holder.tvContent.setPaintFlags(holder.tvContent.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            holder.tvContent.setTextColor(Color.parseColor("#333333"));
        }
    }

    @Override
    public int getItemCount() {
        return tasks == null ? 0 : tasks.size();
    }

    public void updateList(List<TodoItem> newTasks) {
        tasks = (newTasks == null) ? new ArrayList<>() : newTasks;
        notifyDataSetChanged();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvContent;
        TextView tvPriority;

        VH(@NonNull View itemView) {
            super(itemView);
            tvContent = itemView.findViewById(R.id.tvTaskContent);
            tvPriority = itemView.findViewById(R.id.tvPriority);
        }
    }
}
