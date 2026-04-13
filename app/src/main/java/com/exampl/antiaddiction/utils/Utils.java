package com.exampl.antiaddiction.utils;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.util.Log;

import androidx.core.content.ContextCompat;

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

    public static boolean isNetworkAvailable(Context context) {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_NETWORK_STATE)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            Network network = cm.getActiveNetwork();
            if (network == null) return false;
            NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
            if (capabilities == null) return false;
            // Do not require VALIDATED here; some ROM/captive networks may skip it
            // while real requests still work.
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (SecurityException se) {
            Log.w("ANTI_LOG", "ACCESS_NETWORK_STATE missing in installed app", se);
            return false;
        }
    }

    public static String getFriendlyNetworkError(String rawMessage) {
        if (rawMessage == null || rawMessage.trim().isEmpty()) {
            return "网络请求失败，请稍后重试";
        }
        String msg = rawMessage.toLowerCase(Locale.ROOT);
        if (msg.contains("connection refused")) {
            return "服务器拒绝连接，请稍后重试";
        }
        if (msg.contains("missing internet permission")) {
            return "系统拒绝网络访问，请检查系统网络限制并重装应用";
        }
        if (msg.contains("eai_nodata") || msg.contains("unable to resolve host")) {
            return "网络不可用或域名解析失败，请检查网络连接";
        }
        if (msg.contains("eperm") || msg.contains("operation not permitted")) {
            return "当前网络被系统限制，请检查系统网络权限或安全策略";
        }
        if (msg.contains("timeout")) {
            return "请求超时，请稍后重试";
        }
        return "网络请求失败，请稍后重试";
    }
}
