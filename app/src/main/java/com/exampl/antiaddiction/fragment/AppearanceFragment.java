package com.exampl.antiaddiction.fragment;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.exampl.antiaddiction.R;
import com.exampl.antiaddiction.activity.MainActivity;
import com.exampl.antiaddiction.model.ThemeModel;
import com.exampl.antiaddiction.utils.ThemeUtils;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

public class AppearanceFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // 确保你的 layout 文件名是这个
        View view = inflater.inflate(R.layout.fragment_appearance, container, false);

        RecyclerView rv = view.findViewById(R.id.rvColorGrid);
        rv.setLayoutManager(new GridLayoutManager(getContext(), 2));

        List<ThemeModel> themes = new ArrayList<>();
        // 注意：这里的颜色要和你在 themes.xml 定义的保持一致
        themes.add(new ThemeModel("默认", "Blue", Color.parseColor("#4A90E2")));
        themes.add(new ThemeModel("松石", "Mint", Color.parseColor("#4DB6AC")));
        themes.add(new ThemeModel("桃天", "Pink", Color.parseColor("#F06292")));
        themes.add(new ThemeModel("暮山", "Purple", Color.parseColor("#9575CD")));

        rv.setAdapter(new ThemeAdapter(themes));
        return view;
    }

    // 适配器实现
    class ThemeAdapter extends RecyclerView.Adapter<ThemeAdapter.VH> {
        List<ThemeModel> list;

        ThemeAdapter(List<ThemeModel> list) {
            this.list = list;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            // 加载方块布局
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_theme_color, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            ThemeModel m = list.get(position);
            holder.tv.setText(m.name);
            holder.colorView.setBackgroundColor(m.colorRes);

            String currentTheme = ThemeUtils.getTheme(requireContext());
            boolean selected = m.key.equals(currentTheme);
            int selectedStroke = MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorPrimary);
            int normalStroke = MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorOutline);
            holder.card.setStrokeWidth(selected ? 5 : 1);
            holder.card.setStrokeColor(ColorStateList.valueOf(selected ? selectedStroke : normalStroke));

            holder.itemView.setOnClickListener(v -> {
                // 1. 保存主题 Key
                ThemeUtils.saveTheme(requireContext(), m.key);

                // 2. 重启 Activity 以应用新主题
                Intent intent = new Intent(getActivity(), MainActivity.class);
                // 这两行 Flag 会清空之前的 Activity 栈，防止返回时看到旧主题
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                if (getActivity() != null) {
                    getActivity().finish();
                }
            });
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        // 补全：ViewHolder 内部类
        class VH extends RecyclerView.ViewHolder {
            TextView tv;
            View colorView;
            MaterialCardView card;

            VH(View itemView) {
                super(itemView);
                tv = itemView.findViewById(R.id.tvThemeName);
                colorView = itemView.findViewById(R.id.colorView);
                card = itemView.findViewById(R.id.colorCard);
            }
        }
    }
}