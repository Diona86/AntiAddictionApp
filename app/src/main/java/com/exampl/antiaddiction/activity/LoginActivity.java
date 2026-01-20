package com.exampl.antiaddiction.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.exampl.antiaddiction.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class LoginActivity extends AppCompatActivity {

    FirebaseAuth auth;
    RadioButton radioChild;
    private boolean hasJumped=false;
    private Handler handler=new Handler(Looper.getMainLooper());
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        auth = FirebaseAuth.getInstance();
        radioChild=findViewById(R.id.radioChild);

        Button btnLogin = findViewById(R.id.btnLogin);

        // 在 LoginActivity 的 btnLogin.setOnClickListener 内部：
        btnLogin.setOnClickListener(v -> {
            // 1. 获取选中的角色
            String role = radioChild.isChecked() ? "child" : "parent";
            Log.d("ANTI_LOG", "点击了登录按钮，角色是: " + role); // 打印日志

            // 2. 匿名登录
            auth.signInAnonymously()
                    .addOnSuccessListener(authResult -> {
                        String uid = auth.getCurrentUser().getUid();
                        Log.d("ANTI_LOG", "Firebase 登录成功，uid: " + uid);
                        handler.postDelayed(()->{
                            forceJump(role);
                            Log.d("ANTI_LOG","这里是强制跳转");
                        },3000);
                        // 3. 将用户信息存入 Firestore
                        Map<String, Object> user = new HashMap<>();
                        user.put("uid", uid);
                        user.put("role", role);

                        FirebaseFirestore.getInstance().collection("users")
                                .document(uid)
                                .set(user)
                                .addOnSuccessListener(aVoid -> {
                                    // 4. 跳转到主界面
                                    Log.d("ANTI_LOG","Firestroe写入成功");
                                    forceJump(role);
                                })
                                .addOnFailureListener(e -> {
                                    Log.e("ANTI_LOG","Firestroe写入失败:"+e.getMessage());
                                    forceJump(role);
                                });
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(this, "初始化失败: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                    );
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
