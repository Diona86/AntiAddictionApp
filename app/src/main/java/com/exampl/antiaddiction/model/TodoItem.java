package com.exampl.antiaddiction.model;

public class TodoItem {
    public Long id;
    public String content;
    public int status; // 0: Todo, 1: In Progress, 2: Done
    public String priority; // "高", "中", "低"

    public TodoItem(Long id, String content, int status, String priority) {
        this.id = id;
        this.content = content;
        this.status = status;
        this.priority = priority;
    }
}
