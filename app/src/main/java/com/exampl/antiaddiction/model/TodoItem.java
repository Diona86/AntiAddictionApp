package com.exampl.antiaddiction.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Ignore;

// 1. 告诉 Room 这是一个数据库表，表名叫 todo_items
@Entity(tableName = "todo_items")
public class TodoItem {

    // 2. 设置主键自增
    @PrimaryKey(autoGenerate = true)
    public Long id;

    public String content;
    public int status;   // 0: Todo, 1: Process, 2: Done
    public String priority; // 高, 中, 低
    public String boardKey;

    // 3. 新增日期字段，格式建议为 "yyyy-MM-dd"，方便按天查询
    public String dateStr;

    // 4. Room 需要一个空构造方法或者全参构造方法
    public TodoItem(Long id, String content, int status, String priority, String boardKey, String dateStr) {
        this.id = id;
        this.content = content;
        this.status = status;
        this.priority = priority;
        this.boardKey = boardKey;
        this.dateStr = dateStr;
    }

    @Ignore
    public TodoItem( String content, int status, String priority, String boardKey, String dateStr) {
        this.content = content;
        this.status = status;
        this.priority = priority;
        this.boardKey = boardKey;
        this.dateStr = dateStr;
    }
}