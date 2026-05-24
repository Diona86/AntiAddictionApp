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
import com.exampl.antiaddiction.model.ChildDashboardItem;
import com.exampl.antiaddiction.manager.UserManager;

import java.util.List;
import java.util.Map;

public class ChildDashboardAdapter extends RecyclerView.Adapter<ChildDashboardAdapter.VH> {

    private List<ChildDashboardItem> dataList; // 存储每个自律者的综合信息
    private boolean isSupervisor;
    private OnLimitSetListener listener;

    public interface OnLimitSetListener {
        void onSetTotalLimit(String userId);
        void onSetAppLimit(String userId, String pkg);
        void onViewAppUsage(AppUsageInfo app, Double limitMinutes);
    }

    public ChildDashboardAdapter(List<ChildDashboardItem> dataList, boolean isSupervisor, OnLimitSetListener listener) {
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
        ChildDashboardItem item = dataList.get(position);
        String userId = item == null ? "" : item.userId;
        String name = resolveDisplayName(item, context);
        holder.tvName.setText(name);

        long totalMillis = item == null ? 0L : item.totalTime;
        holder.tvTotal.setText(formatTime(totalMillis));

        // 进度条逻辑 (假设总限额存在)
        Integer totalLimit = item == null ? null : item.totalLimit;
        if (totalLimit != null && totalLimit <= 0) {
            totalLimit = null;
        }
        if (totalLimit == null) {
            holder.pb.setVisibility(View.GONE);
        } else {
            holder.pb.setVisibility(View.VISIBLE);
            int progress = (int) ((totalMillis / 1000.0 / 60.0 / totalLimit) * 100);
            holder.pb.setProgress(Math.min(progress, 100));
        }
        holder.tvTotalLimitInline.setText(totalLimit == null
                ? "总限额：未设置"
                : "总限额：" + totalLimit + " 分钟");

        // 加载 App 列表
        List<AppUsageInfo> apps = item == null ? java.util.Collections.emptyList() : item.parseApps();

        Map<String, Double> appLimitsMap = item == null ? new java.util.HashMap<>() : item.parseAppLimits();

        // 恢复图标 (由于从云端拿的JSON没图标，需要本地PackageManager恢复)
        PackageManager pm = holder.itemView.getContext().getPackageManager();
        for (AppUsageInfo app : apps) {
            try { app.appIcon = pm.getApplicationIcon(app.packageName); }
            catch (Exception e) { app.appIcon = ContextCompat.getDrawable(holder.itemView.getContext(), R.mipmap.ic_launcher); }
        }

        // 绑定内部 App 列表，并把应用卡片点击回传给 Fragment 处理
        Map<String, Double> finalAppLimitsMap = appLimitsMap;
        UsageAdapter innerAdapter = new UsageAdapter(
                apps,
                appLimitsMap,
                app -> {
                    if (listener == null || app == null) {
                        return;
                    }
                    if (isSupervisor) {
                        listener.onSetAppLimit(userId, app.packageName);
                    } else {
                        listener.onViewAppUsage(app, finalAppLimitsMap.get(app.packageName));
                    }
                }
        );
        holder.rvApps.setLayoutManager(new LinearLayoutManager(holder.itemView.getContext()));
        holder.rvApps.setAdapter(innerAdapter);

        holder.btnLimit.setVisibility(isSupervisor ? View.VISIBLE : View.GONE);
        holder.btnLimit.setOnClickListener(v -> {
            if (listener != null) {
                listener.onSetTotalLimit(userId);
            }
        });
    }

    private String formatTime(long millis) {
        long minutes = (millis / (1000 * 60)) % 60;
        long hours = (millis / (1000 * 60 * 60));
        return String.format("%02dh %02dm", hours, minutes);
    }

    private String resolveDisplayName(ChildDashboardItem item, Context context) {
        if (item != null && item.username != null) {
            String username = item.username.trim();
            if (!username.isEmpty()) return username;
        }
        if (item != null && item.nickname != null) {
            String nickname = item.nickname.trim();
            if (!nickname.isEmpty()) return nickname;
        }
        return UserManager.getInstance(context).getUsername();
    }

    @Override public int getItemCount() { return dataList.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvName, tvTotal, tvTotalLimitInline;
        ProgressBar pb;
        RecyclerView rvApps;
        View btnLimit;
        VH(View v) {
            super(v);
            tvName = v.findViewById(R.id.tvChildName);
            tvTotal = v.findViewById(R.id.tvTotalTimeDisplay);
            tvTotalLimitInline = v.findViewById(R.id.tvTotalLimitInline);
            pb = v.findViewById(R.id.pbChildProgress);
            rvApps = v.findViewById(R.id.rvChildAppList);
            btnLimit = v.findViewById(R.id.btnSetTotalLimit);
        }
    }
}