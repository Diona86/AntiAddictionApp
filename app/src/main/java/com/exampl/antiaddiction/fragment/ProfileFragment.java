package com.exampl.antiaddiction.fragment;

import static com.exampl.antiaddiction.utils.Utils.parseCreatedAtToMs;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.exampl.antiaddiction.R;
import com.exampl.antiaddiction.activity.LoginActivity; // 假设你有登录页
import com.exampl.antiaddiction.cloudbase.CloudBaseCallback;
import com.exampl.antiaddiction.cloudbase.CloudBaseClient;
import com.exampl.antiaddiction.manager.UserManager;
import com.exampl.antiaddiction.utils.Utils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.reflect.TypeToken;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;

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

        //设定绑定行
        setupRow(view.findViewById(R.id.rowBound),"绑定",R.drawable.ic_add,"#535788");
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
        //绑定功能
        view.findViewById(R.id.rowBound).setOnClickListener(v->{
            String currentRole=UserManager.getInstance(requireContext()).getRole();
            Log.d("Profile",currentRole);
            if ("supervisor".equals(currentRole)) {
                // 情况 A：我是监管者 -> 生成邀请码给别人

                showSupervisorBindingUI();
            } else {
                // 情况 B：我是自律者 -> 输入邀请码绑定监管者
                showSelfBindingUI();
            }
        });
        // 1. Set username from UserManager
        TextView tvProfileNickname = view.findViewById(R.id.tvProfileNickname);
        String username = UserManager.getInstance(requireContext()).getUsername();
        if (!username.isEmpty()) {
            tvProfileNickname.setText(username);
        }

        // 2. Set avatar (adapt to your ic_default_avatar)
        ImageView ivAvatar = view.findViewById(R.id.ivAvatar);
        if (ivAvatar != null) {
            ivAvatar.setImageResource(R.drawable.ic_default_avatar);
        }
    }

    private void showSelfBindingUI() {
        // 1. 动态创建一个输入框布局
        EditText etCode = new EditText(getContext());
        etCode.setHint("请输入6位邀请码");
        etCode.setGravity(Gravity.CENTER);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("绑定监管者")
                .setView(etCode)
                .setPositiveButton("提交", (dialog, which) -> {
                    String inputCode = etCode.getText().toString().trim();
                    if (inputCode.length() != 6) {
                        Toast.makeText(getContext(), "请输入正确的6位邀请码", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    // 执行绑定查询
                    doBindingProcess(inputCode);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void doBindingProcess(String inputCode) {
        String queryPath = "/v1/rdb/rest/code?limit=100";
        TypeToken<List<Map<String, Object>>> typeToken = new TypeToken<List<Map<String, Object>>>() {};
        CloudBaseClient cloudbase = new CloudBaseClient(getString(R.string.CLOUDBASE_ENV_ID), getString(R.string.CLOUDBASE_ACCESS_TOKEN));

        cloudbase.request("GET", queryPath, null, null, typeToken, new CloudBaseCallback<List<Map<String, Object>>>() {
            @Override
            public void onSuccess(List<Map<String, Object>> data) {
                if (data == null || data.isEmpty()) {
                    Toast.makeText(getContext(), "当前没有可用的邀请码", Toast.LENGTH_SHORT).show();
                    return;
                }

                // 1. 获取这唯一的一行数据
                int l= data.size();
                Map<String, Object> codeData = data.get(l-1);
                Log.d("code",data.toString());
                Log.d("code",codeData.toString());

                // 2. 本地对比邀请码是否正确（因为没有 where 过滤）
                String dbCode = String.valueOf(codeData.get("code"));
                if (!inputCode.equals(dbCode)) {
                    Toast.makeText(getContext(), "邀请码不正确", Toast.LENGTH_SHORT).show();
                    Log.w("code",inputCode+":"+dbCode);
                    return;
                }

                // 3. 时间校验逻辑 (替换 Timer() 为 System.currentTimeMillis())
                long now = System.currentTimeMillis();
                long createTimeMs = parseCreatedAtToMs(codeData.get("createdAt")); // 见下方解析方法

                // 假设你说的 2000 是秒 (约 33 分钟)
                if (now - createTimeMs > 2000 * 1000) {
                    Toast.makeText(getContext(), "过期的邀请码", Toast.LENGTH_SHORT).show();
                    return;
                }

                // 4. 拿到监管者的 ID（对应字段 inviterId）
                String supervisorId = String.valueOf(codeData.get("inviterId"));

                // 5. 修改自律者（自己）的 boundUserId 字段
                Map<String, Object> bindBody = new HashMap<>();
                bindBody.put("boundUserId", supervisorId);

                String currentUserId = UserManager.getInstance(requireContext()).getUserId();

                cloudbase.request("PATCH", "/v1/rdb/rest/user?id=eq." + currentUserId, bindBody, null, null,
                        new CloudBaseCallback<Object>() {
                            @Override
                            public void onSuccess(Object result) {
                                // 注意：此时 code 表里没有 nickname，只能提示绑定成功。
                                Toast.makeText(getContext(), "成功绑定监管者！", Toast.LENGTH_LONG).show();
                                UserManager.getInstance(requireContext()).addSupervisor(supervisorId);
                            }

                            @Override
                            public void onError(int code, String message) {
                                Log.e("ANTI_LOG", "绑定失败：" + message);
                            }
                        }
                );
            }

            @Override
            public void onError(int code, String message) {
                Log.e("ANTI_LOG", "查询 code 失败：" + message);
            }
        });
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
    private void showSupervisorBindingUI() {
        String inviteCode = Utils.generateRandomCode(6);
        String currentUserId = UserManager.getInstance(requireContext()).getUserId();

        // 1. 准备新数据
        Map<String, Object> body = new HashMap<>();
        body.put("code", inviteCode);
        body.put("inviterId", currentUserId);

        CloudBaseClient cloudbase = new CloudBaseClient(getString(R.string.CLOUDBASE_ENV_ID), getString(R.string.CLOUDBASE_ACCESS_TOKEN));

        // 2. 第一步：清空表 (执行 DELETE)
        // 为了满足安全机制，我们加一个 id > 0 的条件
        String deletePath = "/v1/rdb/rest/code?where={\"id\":{\"$gt\":0}}";

        cloudbase.request("DELETE", deletePath, null, null, null, new CloudBaseCallback<Object>() {
            @Override
            public void onSuccess(Object data) {
                Log.d("ANTI_LOG", "旧码已清空，准备插入新码");

                // 3. 第二步：插入新码 (执行 POST)
                insertNewCode(cloudbase, body, inviteCode);
            }

            @Override
            public void onError(int code, String message) {
                // 如果表本来就是空的，DELETE 可能也会报错，我们直接尝试插入
                Log.w("ANTI_LOG", "清空失败（可能表已空），直接尝试插入");
                insertNewCode(cloudbase, body, inviteCode);
            }
        });
    }

    // 辅助方法：执行真正的插入动作
    private void insertNewCode(CloudBaseClient cloudbase, Map<String, Object> body, String inviteCode) {
        cloudbase.request("POST", "/v1/rdb/rest/code", body, null, null, new CloudBaseCallback<Object>() {
            @Override
            public void onSuccess(Object data) {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("您的绑定邀请码")
                        .setMessage("请将此码告诉自律者进行绑定：\n\n" + inviteCode)
                        .setPositiveButton("我知道了", null)
                        .show();
            }

            @Override
            public void onError(int code, String message) {
                Toast.makeText(getContext(), "生成失败：" + message, Toast.LENGTH_SHORT).show();
                Log.e("ANTI_LOG", "POST 插入失败: " + message);
            }
        });
    }
}