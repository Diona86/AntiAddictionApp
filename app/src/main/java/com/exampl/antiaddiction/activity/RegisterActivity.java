package com.exampl.antiaddiction.activity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.exampl.antiaddiction.R;
import com.exampl.antiaddiction.cloudbase.CloudBaseCallback;
import com.exampl.antiaddiction.cloudbase.CloudBaseClient;
import com.exampl.antiaddiction.utils.ThemeUtils;
import com.exampl.antiaddiction.utils.Utils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.reflect.TypeToken;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private Button btnRegister;
    private TextInputEditText etUsername;
    private TextInputEditText etPassword;
    private RadioButton radioChild;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtils.applyTheme(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_register);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.register), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 初始化控件
        btnRegister = findViewById(R.id.btnRegister);
        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        radioChild = findViewById(R.id.radioChild);

        // 设置点击监听
        btnRegister.setOnClickListener(v -> registerUser());
    }

    private void registerUser() {
        // 1. 获取数据
        CloudBaseClient cloudbase=new CloudBaseClient(getString(R.string.CLOUDBASE_ENV_ID),getString(R.string.CLOUDBASE_ACCESS_TOKEN));
        String username = etUsername.getText().toString().trim();  // ✅ 用 getText()
        String password = etPassword.getText().toString().trim();  // ✅ 用 getText()
        String role = radioChild.isChecked() ? "self" : "supervisor";

        // 2. 参数校验
        if (username.isEmpty()) {
            Toast.makeText(this, "用户名不能为空", Toast.LENGTH_SHORT).show();
            return;
        }

        if (password.isEmpty()) {
            Toast.makeText(this, "密码不能为空", Toast.LENGTH_SHORT).show();
            return;
        }

        if (password.length() < 6) {
            Toast.makeText(this, "密码不能少于6位", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!Utils.isNetworkAvailable(this)) {
            Toast.makeText(this, "网络状态检测异常，正在继续尝试注册...", Toast.LENGTH_SHORT).show();
        }

        // 3. 禁用按钮，防止重复点击
        btnRegister.setEnabled(false);
        btnRegister.setText("注册中...");

        // 4. 发送请求
        // 注册
        Map<String, Object> newUser = new HashMap<>();
        newUser.put("username", username);
        newUser.put("password", password); // 实际项目建议先 MD5 或 SHA256 加密再存
        newUser.put("role",role);

        TypeToken<Map<String, Object>> typeToken = new TypeToken<Map<String, Object>>() {};

        cloudbase.<Map<String, Object>>request(
                "POST",
                "/v1/rdb/rest/user",
                newUser,
                null,
                typeToken,
                new CloudBaseCallback<Map<String, Object>>() {
                    @Override
                    public void onSuccess(Map<String, Object> data) {
                        Log.d("CloudBase", "注册成功");
                        Utils.jumpPage(RegisterActivity.this,LoginActivity.class,"",null);
                    }

                    @Override
                    public void onError(int code, String message) {
                        Log.e("CloudBase", "注册失败: " + message);
                        String friendly = Utils.getFriendlyNetworkError(message);
                        Toast.makeText(RegisterActivity.this, friendly, Toast.LENGTH_SHORT).show();
                        maybeShowNetworkSettingsDialog(message);
                        btnRegister.setEnabled(true);
                        btnRegister.setText("注册");
                    }
                }
        );
    }

    private void jumpToLogin() {
        Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
        startActivity(intent);
        finish();
    }

    private void maybeShowNetworkSettingsDialog(String message) {
        if (message == null) return;
        String lower = message.toLowerCase();
        boolean blockedBySystem = lower.contains("missing internet permission")
                || lower.contains("eperm")
                || lower.contains("operation not permitted");
        if (!blockedBySystem) return;

        new MaterialAlertDialogBuilder(this)
                .setTitle("需要开启网络访问")
                .setMessage("系统可能限制了本应用联网。请在应用设置中开启 WLAN/移动数据访问后重试。")
                .setNegativeButton("取消", null)
                .setPositiveButton("去设置", (d, w) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(Uri.fromParts("package", getPackageName(), null));
                    startActivity(intent);
                })
                .show();
    }
}