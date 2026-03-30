package com.exampl.antiaddiction.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.exampl.antiaddiction.R;
import com.exampl.antiaddiction.network.ApiService;
import com.exampl.antiaddiction.model.Result;
import com.exampl.antiaddiction.model.UserInfo;
import com.exampl.antiaddiction.utils.ThemeUtils;
import com.google.android.material.textfield.TextInputEditText;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RegisterActivity extends AppCompatActivity {

    private Button btnRegister;
    private TextInputEditText etUsername;
    private TextInputEditText etPassword;
    private RadioButton radioChild;
    private ApiService apiService;

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
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    Request original = chain.request();
                    Request request = original.newBuilder()
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                            .header("Accept", "application/json")
                            .header("Content-Type", "application/json")  // 确保
                            .method(original.method(), original.body())
                            .build();
                    return chain.proceed(request);
                })
                .build();
        // 创建 Retrofit（只创建一次）
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("http://192.168.1.106:8080/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        apiService = retrofit.create(ApiService.class);

        // 设置点击监听
        btnRegister.setOnClickListener(v -> registerUser());
    }

    private void registerUser() {
        // 1. 获取用户输入
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

        // 3. 禁用按钮，防止重复点击
        btnRegister.setEnabled(false);
        btnRegister.setText("注册中...");

        // 4. 创建用户对象
        UserInfo newUser = new UserInfo(username, password, role);

        // 5. 发送请求
        apiService.registerUser(newUser).enqueue(new Callback<Result<UserInfo>>() {
            @Override
            public void onResponse(Call<Result<UserInfo>> call, Response<Result<UserInfo>> response) {
                // 恢复按钮
                btnRegister.setEnabled(true);
                btnRegister.setText("注册");

                // 处理响应
                if (response.isSuccessful() && response.body() != null) {
                    Result<UserInfo> result = response.body();

                    // 显示消息
                    Toast.makeText(RegisterActivity.this,
                            result.getMessage(), Toast.LENGTH_LONG).show();

                    // 判断是否成功
                    if (result.getCode() == 200) {
                        // 延迟跳转，让用户看到 Toast
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            jumpToLogin();
                        }, 1500);
                    }
                } else {
                    Toast.makeText(RegisterActivity.this,
                            "注册失败：" + response.code(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Result<UserInfo>> call, Throwable t) {
                // 恢复按钮
                btnRegister.setEnabled(true);
                btnRegister.setText("注册");

                // 显示错误
                Toast.makeText(RegisterActivity.this,
                        "网络错误：" + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void jumpToLogin() {
        Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
        startActivity(intent);
        finish();
    }
}