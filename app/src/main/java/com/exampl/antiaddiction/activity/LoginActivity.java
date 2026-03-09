package com.exampl.antiaddiction.activity;

import android.content.Intent;
import android.os.Bundle;
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
import com.exampl.antiaddiction.api.ApiService;
import com.exampl.antiaddiction.model.Result;
import com.exampl.antiaddiction.model.UserInfo;
import com.exampl.antiaddiction.utils.Utils;
import com.google.android.material.textfield.TextInputEditText;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText etUsername;
    private TextInputEditText etPassword;
    private RadioButton radioChild;
    private Button btnLogin;
    private Button testLogin;
    private ApiService apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // 初始化控件
        etUsername = findViewById(R.id.etUsername);
        testLogin =findViewById(R.id.tsLogin);
        etPassword = findViewById(R.id.etPassword);
        radioChild = findViewById(R.id.radioChild);
        btnLogin = findViewById(R.id.btnLogin);
        TextView tvRegister = findViewById(R.id.tvRegister);

        // 创建 Retrofit
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("http://192.168.1.106:8080/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        apiService = retrofit.create(ApiService.class);

        // 注册跳转
        tvRegister.setOnClickListener(v -> {
            Intent intent = new Intent(this, RegisterActivity.class);
            startActivity(intent);
        });

        // 登录按钮
        btnLogin.setOnClickListener(v -> login());
        testLogin.setOnClickListener(v->{
            Utils.jumpPage(LoginActivity.this,MainActivity.class,"",null);
        });

        // 设置窗口边距
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.login), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void login() {
        // 1. 获取用户输入
        String username = etUsername.getText().toString().trim();  // ✅
        String password = etPassword.getText().toString().trim();  // ✅
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

        // 3. 禁用按钮
        btnLogin.setEnabled(false);
        btnLogin.setText("登录中...");

        // 4. 创建用户对象
        UserInfo user = new UserInfo(username, password, role);

        // 5. 发送登录请求
        apiService.login(user).enqueue(new Callback<Result<UserInfo>>() {
            @Override
            public void onResponse(Call<Result<UserInfo>> call, Response<Result<UserInfo>> response) {
                // 恢复按钮
                btnLogin.setEnabled(true);
                btnLogin.setText("登录");

                if (response.isSuccessful() && response.body() != null) {
                    Result<UserInfo> result = response.body();

                    if (result.getCode() == 200) {
                        // ✅ 登录成功
                        Log.d("ANTI_LOG", "登录成功: " + result.getMessage());
                        Toast.makeText(LoginActivity.this,
                                "登录成功", Toast.LENGTH_SHORT).show();



//                        // 可选：传递用户信息
//                        if (result.getData() != null) {
//                            intent.putExtra("USER_ID", result.getData().getId());
//                            intent.putExtra("USERNAME", result.getData().getUsername());
//                        }
                        Utils.jumpPage(LoginActivity.this,MainActivity.class,"role",role);

                    } else {
                        // ❌ 登录失败（用户名或密码错误等）
                        Toast.makeText(LoginActivity.this,
                                result.getMessage(), Toast.LENGTH_LONG).show();
                        Log.d("ANTI_LOG", "登录失败: " + result.getMessage());
                    }
                } else {
                    // HTTP 请求失败
                    Toast.makeText(LoginActivity.this,
                            "登录失败：" + response.code(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Result<UserInfo>> call, Throwable t) {
                // 恢复按钮
                btnLogin.setEnabled(true);
                btnLogin.setText("登录");

                // ✅ 网络错误，不应该跳转！
                Toast.makeText(LoginActivity.this,
                        "网络错误：" + t.getMessage(), Toast.LENGTH_SHORT).show();
                Log.e("ANTI_LOG", "网络错误", t);
            }
        });
    }
}