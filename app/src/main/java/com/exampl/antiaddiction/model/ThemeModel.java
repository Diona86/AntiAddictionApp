package com.exampl.antiaddiction.model;

public class ThemeModel {
    public String name;    // 显示的名字
    public String key;     // 对应 ThemeUtils 里的 key
    public int colorRes;   // 显示在方块里的颜色

    public ThemeModel(String name, String key, int colorRes) {
        this.name = name; this.key = key; this.colorRes = colorRes;
    }
}