package com.exampl.antiaddiction.adapter;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.exampl.antiaddiction.R;
import com.exampl.antiaddiction.model.AppUsageInfo;

import java.util.List;
import java.util.Map;

public class UsageAdapter extends RecyclerView.Adapter<UsageAdapter.ViewHolder> {

    private List<AppUsageInfo> list;
    private Map<String, Double> limitsMap; // 存储限额数据
    private OnItemClickListener clickListener;

    // 1. 定义自己的点击监听接口
    public interface OnItemClickListener {
        void onItemClick(AppUsageInfo app);
    }

    // 2. 构造函数
    public UsageAdapter(List<AppUsageInfo> list, Map<String, Double> limitsMap, OnItemClickListener listener) {
        this.list = list;
        this.limitsMap = limitsMap;
        this.clickListener = listener;
    }

    // 3. 创建 ViewHolder（加载 item_usage 布局）
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_usage, parent, false);
        return new ViewHolder(view);
    }

    // 4. 绑定数据（核心逻辑：显示 [已用 / 限额] 并在超标时变红）
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppUsageInfo info = list.get(position);

        holder.tvAppName.setText(info.appName);
        holder.imgAppIcon.setImageDrawable(info.appIcon);

        String displayTime = info.timeFormatted;

        // 检查是否有针对该应用的限额
        if (limitsMap != null && limitsMap.containsKey(info.packageName)) {
            Double limitVal = limitsMap.get(info.packageName);
            if (limitVal != null) {
                int limit = limitVal.intValue();
                displayTime += " / 限额 " + limit + "m";

                // 计算当前已用分钟数
                long usedMinutes = info.usageTime / 1000 / 60;

                // 如果超了，文字变红，否则保持默认灰色
                if (usedMinutes >= limit) {
                    holder.tvTimeStr.setTextColor(Color.RED);
                } else {
                    holder.tvTimeStr.setTextColor(Color.parseColor("#999999"));
                }
            }
        } else {
            // 如果没有限额，保持默认颜色
            holder.tvTimeStr.setTextColor(Color.parseColor("#999999"));
        }

        holder.tvTimeStr.setText(displayTime);

        // 处理点击事件
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onItemClick(info);
            }
        });
    }

    @Override
    public int getItemCount() {
        return list != null ? list.size() : 0;
    }

    // 5. 内部类 ViewHolder
    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imgAppIcon;
        TextView tvAppName, tvTimeStr;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            // 确保这些 ID 和你的 item_usage.xml 布局文件一致
            imgAppIcon = itemView.findViewById(R.id.imgAppIcon);
            tvAppName = itemView.findViewById(R.id.tvAppName);
            tvTimeStr = itemView.findViewById(R.id.tvTimeStr);
        }
    }
}