package com.exampl.antiaddiction.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import com.exampl.antiaddiction.model.UserInfo;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.gson.Gson;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class LoginActivity extends AppCompatActivity {

    FirebaseAuth auth;
    RadioButton radioChild;
    private boolean hasJumped=false;
    private Handler handler=new Handler(Looper.getMainLooper());
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
// 1. 获取选中的角色
        String role = radioChild.isChecked() ? "child" : "parent";
        Log.d("ANTI_LOG", "点击了登录按钮，角色是: " + role); // 打印日志
        auth = FirebaseAuth.getInstance();
        radioChild=findViewById(R.id.radioChild);
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("http://192.168.1.101:8080/") // 把 x.x 换成你电脑的 IP!!
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        ApiService apiService = retrofit.create(ApiService.class);

        Button btnLogin = findViewById(R.id.btnLogin);
        TextView tvRegister=findViewById(R.id.tvRegister);
        tvRegister.setOnClickListener(v -> {
            Intent intent=new Intent(this,RegisterActivity.class);
            startActivity(intent);
        });
        // 在 LoginActivity 的 btnLogin.setOnClickListener 内部：
        btnLogin.setOnClickListener(v -> {
            apiService.getUser(1).enqueue(new Callback<UserInfo>() {
                @Override
                public void onResponse(Call<UserInfo> call, Response<UserInfo> response) {
                    if(response.isSuccessful()&&response.body()!=null){
                        UserInfo user=response.body();
                        Log.d("ANTI_LOG", "用户信息详情: " + new Gson().toJson(user));
                    }
                }
                @Override
                public void onFailure(Call<UserInfo> call, Throwable t) {
                }
            });

            handler.postDelayed(()->{
                forceJump(role);
                Log.d("ANTI_LOG","这里是强制跳转");
            },3000);


            });
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.login), (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom);
                return insets;
        });
    }

    private void forceJump(String role) {
        if (hasJumped) return;
        hasJumped =true;
        handler.removeCallbacksAndMessages(null);
        Intent intent =new Intent(this,MainActivity.class);
        intent.putExtra("UESR_ROLE",role);
        startActivity(intent);
        finish();
    }
}
