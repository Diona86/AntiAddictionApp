package com.exampl.antiaddiction.adapter;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.exampl.antiaddiction.R;
import com.exampl.antiaddiction.model.AppUsageInfo;
import com.exampl.antiaddiction.manager.UserManager;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.List;
import java.util.Map;

public class ChildDashboardAdapter extends RecyclerView.Adapter<ChildDashboardAdapter.VH> {

    private List<Map<String, Object>> dataList; // 存储每个自律者的综合信息
    private boolean isSupervisor;
    private OnLimitSetListener listener;

    public interface OnLimitSetListener {
        void onSetTotalLimit(String userId);
        void onSetAppLimit(String userId, String pkg);
    }

    public ChildDashboardAdapter(List<Map<String, Object>> dataList, boolean isSupervisor, OnLimitSetListener listener) {
        this.dataList = dataList;
        Log.d("ANTI_LOG_DATALIST",dataList.toString());
        this.isSupervisor = isSupervisor;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_child_dashboard, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Context context = holder.itemView.getContext();
        Map<String, Object> item = dataList.get(position);
        String userId = String.valueOf(item.get("userId"));
        String name = String.valueOf(item.get("username"));
        if(name.equals("null")) {
            Log.d("ANTI_LOG",name);
            name = UserManager.getInstance(context).getUsername();
        }
        Log.d("ANTI_LOG",name);
        holder.tvName.setText(name);

        long totalMillis = ((Number) item.get("totalTime")).longValue();
        holder.tvTotal.setText(formatTime(totalMillis));

        // 进度条逻辑 (假设总限额存在)
        int totalLimit = item.get("totalLimit") != null ? ((Number) item.get("totalLimit")).intValue() : 480;
        int progress = (int) ((totalMillis / 1000.0 / 60.0 / totalLimit) * 100);
        holder.pb.setProgress(Math.min(progress, 100));

        // 加载 App 列表
        String appJson = (String) item.get("appJson");
        List<AppUsageInfo> apps = new Gson().fromJson(appJson, new TypeToken<List<AppUsageInfo>>(){}.getType());

        // 恢复图标 (由于从云端拿的JSON没图标，需要本地PackageManager恢复)
        PackageManager pm = holder.itemView.getContext().getPackageManager();
        for (AppUsageInfo app : apps) {
            try { app.appIcon = pm.getApplicationIcon(app.packageName); }
            catch (Exception e) { app.appIcon = ContextCompat.getDrawable(holder.itemView.getContext(), R.mipmap.ic_launcher); }
        }

        // 绑定内部 App 列表
        UsageAdapter innerAdapter = new UsageAdapter(apps);
        // 如果是监管者，设置点击 App 弹窗
        if (isSupervisor) {
            // 这里假设你的 UsageAdapter 已经加了点击回调，如果没有请在 UsageAdapter 里加
            // innerAdapter.setOnItemClickListener(app -> listener.onSetAppLimit(userId, app.packageName));
        }
        holder.rvApps.setLayoutManager(new LinearLayoutManager(holder.itemView.getContext()));
        holder.rvApps.setAdapter(innerAdapter);

        holder.btnLimit.setVisibility(isSupervisor ? View.VISIBLE : View.GONE);
        holder.btnLimit.setOnClickListener(v -> listener.onSetTotalLimit(userId));
    }

    private String formatTime(long millis) {
        long minutes = (millis / (1000 * 60)) % 60;
        long hours = (millis / (1000 * 60 * 60));
        return String.format("%02dh %02dm", hours, minutes);
    }

    @Override public int getItemCount() { return dataList.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvName, tvTotal; ProgressBar pb; RecyclerView rvApps; View btnLimit;
        VH(View v) {
            super(v);
            tvName = v.findViewById(R.id.tvChildName);
            tvTotal = v.findViewById(R.id.tvTotalTimeDisplay);
            pb = v.findViewById(R.id.pbChildProgress);
            rvApps = v.findViewById(R.id.rvChildAppList);
            btnLimit = v.findViewById(R.id.btnSetTotalLimit);
        }
    }
}