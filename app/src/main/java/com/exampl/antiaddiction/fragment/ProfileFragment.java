package com.exampl.antiaddiction.fragment;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.exampl.antiaddiction.R;
import com.exampl.antiaddiction.activity.LoginActivity; // 假设你有登录页
import com.exampl.antiaddiction.utils.Utils;

public class ProfileFragment extends Fragment {

    // Fragment 必须重写 onCreateView 来加载布局
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 1. 设置“外观”行 (适配你有的 ic_board_default)
        setupRow(view.findViewById(R.id.rowAppearance), "外观", R.drawable.ic_board_default, "#78C2AD");

        // 2. 设置“日期与时间”行 (适配你有的 ic_today)
        setupRow(view.findViewById(R.id.rowTime), "日期与时间", R.drawable.ic_today, "#5AB9EA");

        // 3. 设置“声音与通知”行 (适配你有的 ic_collection)
        setupRow(view.findViewById(R.id.rowNotice), "功能模块", R.drawable.ic_collection, "#FFB64D");

        // 4. 退出登录点击
        view.findViewById(R.id.btnLogout).setOnClickListener(v -> {
            Toast.makeText(getContext(), "已退出登录", Toast.LENGTH_SHORT).show();
            Utils.jumpPage(requireActivity(),LoginActivity.class,"source","profile");
        });
        // 外观共功能
        view.findViewById(R.id.rowAppearance).setOnClickListener(v -> {
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.mainFragmentContainer, new AppearanceFragment())
                    .addToBackStack(null) // 允许返回上一页
                    .commit();
        });

        // 设置头像 (适配你有的 ic_default_avatar)
        ImageView ivAvatar = view.findViewById(R.id.ivAvatar);
        if (ivAvatar != null) {
            ivAvatar.setImageResource(R.drawable.ic_default_avatar);
        }
    }

    private void setupRow(View rowView, String title, int iconRes, String colorHex) {
        if (rowView == null) return; // 防护：如果 ID 没找对，直接跳过不崩溃

        TextView tv = rowView.findViewById(R.id.rowTitle);
        ImageView icon = rowView.findViewById(R.id.rowIcon);
        View container = rowView.findViewById(R.id.iconContainer);

        if (tv != null) tv.setText(title);
        if (icon != null) {
            icon.setImageResource(iconRes);
            // 动态改颜色
            int color = Color.parseColor(colorHex);
            icon.setImageTintList(ColorStateList.valueOf(color));
        }
    }
}