package com.exampl.antiaddiction.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import com.exampl.antiaddiction.network.ApiService;
import com.exampl.antiaddiction.model.Result;
import com.exampl.antiaddiction.model.UserInfo;
import com.exampl.antiaddiction.utils.ThemeUtils;
import com.exampl.antiaddiction.utils.Utils;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.reflect.TypeToken;

import java.util.HashMap;
import java.util.Map;

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

        // 3. 禁用按钮，防止重复点击
        btnRegister.setEnabled(false);
        btnRegister.setText("注册中...");

        // 4. 创建用户对象
        UserInfo newuser = new UserInfo(username, password, role);

        // 5. 发送请求
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
                    }
                }
        );
        btnRegister.setEnabled(true);
        btnRegister.setText("注册");
    }

    private void jumpToLogin() {
        Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
        startActivity(intent);
        finish();
    }
}