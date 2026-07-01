package com.exampl.antiaddiction.activity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.exampl.antiaddiction.R;

public class LockActivity extends AppCompatActivity {

    public static final String EXTRA_LIMIT_MESSAGE = "limit_message";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setFinishOnTouchOutside(false);
        setContentView(R.layout.activity_lock);

        TextView tvMessage = findViewById(R.id.tvLockMessage);
        String message = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_LIMIT_MESSAGE);
        if (message == null || message.trim().isEmpty()) {
            message = "今日使用已超限，请先休息一会儿。";
        }
        tvMessage.setText(message);

        findViewById(R.id.btnLockConfirm).setOnClickListener(v -> goHome());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // 禁止返回绕过
            }
        });
    }

    private void goHome() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}
