package com.exampl.antiaddiction.manager;

import android.content.Context;
import android.content.SharedPreferences;

public class UserManager {

    private static final String SP_NAME = "user_info";

    private static final String KEY_ID = "id";
    private static final String KEY_SUPERID = "superId";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_ROLE = "role";
    private static final String KEY_LOGIN = "is_login";

    private static UserManager instance;
    private SharedPreferences sp;

    private UserManager(Context context) {
        sp = context.getApplicationContext()
                .getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
    }

    public static UserManager getInstance(Context context) {
        if (instance == null) {
            instance = new UserManager(context);
        }
        return instance;
    }

    // =====================
    // 保存用户
    // =====================
    public void saveUser(String id, String username, String role,String superId) {
        sp.edit()
                .putString(KEY_ID, id)
                .putString(KEY_SUPERID,superId)
                .putString(KEY_USERNAME, username)
                .putString(KEY_ROLE, role)
                .putBoolean(KEY_LOGIN, true)
                .apply();
    }
    public void addSupervisor(String superId){
        sp.edit()
                .putString(KEY_SUPERID,superId)
                .apply();
    }

    // =====================
    // 获取信息
    // =====================
    public String getUserId() {
        return sp.getString(KEY_ID, "");
    }
    public String getSupervisorId(){
        return sp.getString(KEY_SUPERID,"");
    }

    public String getUsername() {
        return sp.getString(KEY_USERNAME, "");
    }

    public String getRole() {
        return sp.getString(KEY_ROLE, "");
    }

    public boolean isLogin() {
        return sp.getBoolean(KEY_LOGIN, false);
    }


    // =====================
    // 退出登录
    // =====================
    public void logout() {
        sp.edit().clear().apply();
    }
}
