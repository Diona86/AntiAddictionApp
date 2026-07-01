package com.exampl.antiaddiction.model;

import java.util.Map;

public class ChildUser {
    public String id = "";
    public String username = "";
    public String nickname = "";

    public static ChildUser fromMap(Map<String, Object> raw) {
        ChildUser user = new ChildUser();
        if (raw == null) {
            return user;
        }
        user.id = raw.get("id") == null ? "" : String.valueOf(raw.get("id"));
        user.username = raw.get("username") == null ? "" : String.valueOf(raw.get("username"));
        user.nickname = raw.get("nickname") == null ? "" : String.valueOf(raw.get("nickname"));
        return user;
    }
}
