package com.exampl.antiaddiction.activity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.exampl.antiaddiction.R;
import com.exampl.antiaddiction.cloudbase.CloudBaseCallback;
import com.exampl.antiaddiction.cloudbase.CloudBaseClient;
import com.exampl.antiaddiction.manager.UserManager;
import com.exampl.antiaddiction.utils.ThemeUtils;
import com.exampl.antiaddiction.utils.Utils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.reflect.TypeToken;

import java.util.List;
import java.util.Map;

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText etUsername;
    private TextInputEditText etPassword;
    private RadioButton radioChild;
    private Button btnLogin;
    private Button testLogin;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtils.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // 初始化控件

        etUsername = findViewById(R.id.etUsername);
        testLogin =findViewById(R.id.tsLogin);
        etPassword = findViewById(R.id.etPassword);
        radioChild = findViewById(R.id.radioChild);
        btnLogin = findViewById(R.id.btnLogin);
        TextView tvRegister = findViewById(R.id.tvRegister);

        // 注册跳转
        tvRegister.setOnClickListener(v -> {
            Intent intent = new Intent(this, RegisterActivity.class);
            startActivity(intent);
        });

        // 登录按钮
        btnLogin.setOnClickListener(v -> login());
        testLogin.setOnClickListener(v->{
            Utils.jumpPage(LoginActivity.this,MainActivity.class,"",null);
            //doTest();
        });

        // 设置窗口边距
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.login), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void doTest() {
        CloudBaseClient cloudbase=new CloudBaseClient(getString(R.string.CLOUDBASE_ENV_ID),getString(R.string.CLOUDBASE_ACCESS_TOKEN));
        TypeToken<List<Map<String, Object>>> typeToken = new TypeToken<List<Map<String, Object>>>() {};
        cloudbase.<List<Map<String,Object>>>request(
                "GET",
                "/v1/rdb/rest/user?limit=10",
                null,   // GET 请求无 body
                null,   // 无额外 headers
                typeToken,
                new CloudBaseCallback<List<Map<String, Object>>>() {
                    @Override
                    public void onSuccess(List<Map<String, Object>> data) {
                        if (data == null || data.isEmpty()) {
                            Log.e("CloudBase", "用户不存在");
                            return;
                        }

                        Map<String, Object> user = data.get(0);
                        String storedPassword = (String) user.get("password");

                        if ("123456".equals(storedPassword)) { // 加密的话这里也要先加密再比对
                            Log.d("CloudBase", "登录成功，用户id: " + user.get("id"));
                            // 跳转页面等后续逻辑
                        } else {
                            Log.e("CloudBase", "密码错误");
                        }
                    }

                    @Override
                    public void onError(int code, String message) {
                        Log.e("CloudBase", "请求失败 " + code + ": " + message);
                        Toast.makeText(LoginActivity.this, "请求失败: " + message, Toast.LENGTH_SHORT).show();
                        Log.d("Anti_LOG",message);
                    }
                }
        );
    }

    private void login() {
        // 1. 获取用户输入
        String username = etUsername.getText().toString().trim();  // ✅
        String password = etPassword.getText().toString().trim();  // ✅
        //String role = radioChild.isChecked() ? "self" : "supervisor";
        CloudBaseClient cloudbase=new CloudBaseClient(getString(R.string.CLOUDBASE_ENV_ID),getString(R.string.CLOUDBASE_ACCESS_TOKEN));

        // 2. 参数校验
        if (username.isEmpty()) {
            Toast.makeText(this, "用户名不能为空", Toast.LENGTH_SHORT).show();
            return;
        }

        if (password.isEmpty()) {
            Toast.makeText(this, "密码不能为空", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!Utils.isNetworkAvailable(this)) {
            Toast.makeText(this, "网络状态检测异常，正在继续尝试登录...", Toast.LENGTH_SHORT).show();
        }

        // 3. 禁用按钮
        btnLogin.setEnabled(false);
        btnLogin.setText("登录中...");

        // 4. 发送登录请求
        String path = "/v1/rdb/rest/user?username=eq." + Uri.encode(username);

        TypeToken<List<Map<String, Object>>> typeToken = new TypeToken<List<Map<String, Object>>>() {};

        cloudbase.<List<Map<String, Object>>>request(
                "GET",
                path,
                null,
                null,
                typeToken,
                new CloudBaseCallback<List<Map<String, Object>>>() {
                    @Override
                    public void onSuccess(List<Map<String, Object>> data) {
                        try {
                            if (data == null || data.isEmpty()) {
                                Toast.makeText(LoginActivity.this, "用户不存在", Toast.LENGTH_SHORT).show();
                                btnLogin.setEnabled(true);
                                btnLogin.setText("登录");
                                return;
                            }

                            // 在返回数据里找用户名匹配的那条
                            Map<String, Object> matchedUser = null;
                            for (Map<String, Object> item : data) {
                                if (username.equals(String.valueOf(item.get("username")))) {
                                    matchedUser = item;
                                    break;
                                }
                            }

                            if (matchedUser == null) {
                                Toast.makeText(LoginActivity.this, "用户不存在", Toast.LENGTH_SHORT).show();
                                btnLogin.setEnabled(true);
                                btnLogin.setText("登录");
                                return;
                            }

                            String storedPassword = String.valueOf(matchedUser.get("password"));
                            if (password.equals(storedPassword)) {
                                Log.d("CloudBase", "登录成功，用户id: " + matchedUser.get("id"));
                                String userId = String.valueOf(matchedUser.get("id"));
                                String role =String.valueOf(matchedUser.get("role"));
                                String superId=String.valueOf(matchedUser.get("boundUserId"));

                                // ⭐ 保存登录状态
                                UserManager.getInstance(LoginActivity.this)
                                        .saveUser(userId, username, role,superId);
                                Utils.jumpPage(LoginActivity.this, MainActivity.class, "role", role);
                            } else {
                                Toast.makeText(LoginActivity.this, "密码错误", Toast.LENGTH_SHORT).show();
                                btnLogin.setEnabled(true);
                                btnLogin.setText("登录");
                            }
                        } catch (Exception e) {
                            Log.e("CloudBase", "登录响应解析异常", e);
                            Toast.makeText(LoginActivity.this, "登录响应异常，请重试", Toast.LENGTH_SHORT).show();
                            btnLogin.setEnabled(true);
                            btnLogin.setText("登录");
                        }
                    }

                    @Override
                    public void onError(int code, String message) {
                        Log.e("CloudBase", "登录失败: " + message);
                        String friendly = Utils.getFriendlyNetworkError(message);
                        Toast.makeText(LoginActivity.this, friendly, Toast.LENGTH_SHORT).show();
                        maybeShowNetworkSettingsDialog(message);
                        btnLogin.setEnabled(true);
                        btnLogin.setText("登录");
                    }
                }
        );
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
