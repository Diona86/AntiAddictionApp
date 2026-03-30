package com.exampl.antiaddiction.utils;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import com.exampl.antiaddiction.R;

public class ThemeUtils {
    private static final String PREFS_NAME = "theme_settings";
    private static final String KEY_THEME = "selected_theme";

    // 保存选择
    public static void saveTheme(Context context, String themeName) {
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        editor.putString(KEY_THEME, themeName);
        editor.apply();
    }

    // 获取当前选择
    public static String getTheme(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_THEME, "Blue");
    }

    // 在 Activity.onCreate 中调用，必须在 super.onCreate 之前
    public static void applyTheme(Activity activity) {
        String theme = getTheme(activity);
        switch (theme) {
            case "Mint": activity.setTheme(R.style.Theme_AntiAddiction_Mint); break;
            case "Pink": activity.setTheme(R.style.Theme_AntiAddiction_Pink); break;
            case "Purple": activity.setTheme(R.style.Theme_AntiAddiction_Purple); break;
            default: activity.setTheme(R.style.Theme_AntiAddiction_Blue); break;
        }
    }
}