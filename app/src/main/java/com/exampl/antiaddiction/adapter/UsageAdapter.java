package com.exampl.antiaddiction.adapter;

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

public class UsageAdapter extends RecyclerView.Adapter<UsageAdapter.ViewHolder> {

    private List<AppUsageInfo> mData;

    public UsageAdapter(List<AppUsageInfo> data) {
        this.mData = data;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_usage, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppUsageInfo info = mData.get(position);
        holder.tvAppName.setText(info.appName);
        holder.tvTimeStr.setText(info.timeFormatted);
        holder.imgIcon.setImageDrawable(info.appIcon);
    }

    @Override
    public int getItemCount() {
        return mData.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imgIcon;
        TextView tvAppName, tvTimeStr;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imgIcon = itemView.findViewById(R.id.imgAppIcon);
            tvAppName = itemView.findViewById(R.id.tvAppName);
            tvTimeStr = itemView.findViewById(R.id.tvTimeStr);
        }
    }
}
