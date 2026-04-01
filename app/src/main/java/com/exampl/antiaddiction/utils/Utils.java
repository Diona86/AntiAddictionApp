package com.exampl.antiaddiction.utils;

import static androidx.core.content.ContextCompat.startActivity;

import android.app.Activity;
import android.content.Intent;

import com.exampl.antiaddiction.activity.LoginActivity;
import com.exampl.antiaddiction.activity.MainActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public class Utils {
    static public String formatTime(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        if (hours > 0) {
            return String.format("%02dh %02dm", hours, minutes % 60);
        } else {
            return String.format("%02dm %02ds", minutes, seconds % 60);
        }
    }
    /**
     * 自身activity
     * 目标class
     * **/
    static public void jumpPage(Activity activity,Class<?> targetClass,String msgName,String value){
        Intent intent = new Intent(activity, targetClass);
        intent.putExtra(msgName, value);  // 注意拼写：USER 不是 UESR
        activity.startActivity(intent);
        activity.finish();
    }


    public static String generateRandomCode(int i) {
        String chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        StringBuilder sb = new StringBuilder(i);

        for (int j = 0; j < i; j++) {
            int index = ThreadLocalRandom.current().nextInt(chars.length());
            sb.append(chars.charAt(index));
        }

        return sb.toString();
    }
    public static long parseCreatedAtToMs(Object createdAtObj) {
        if (createdAtObj == null) return 0;

        // 如果接口直接返回了时间戳（Long 或 Double）
        if (createdAtObj instanceof Number) {
            return ((Number) createdAtObj).longValue();
        }

        // 如果接口返回的是字符串（如 "2026-04-01 14:00:00"）
        if (createdAtObj instanceof String) {
            try {
                // 根据云端返回的真实格式微调格式化字符串
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                Date date = sdf.parse((String) createdAtObj);
                return date != null ? date.getTime() : 0;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return 0;
    }
}
