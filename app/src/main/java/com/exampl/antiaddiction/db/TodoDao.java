package com.exampl.antiaddiction.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import com.exampl.antiaddiction.model.TodoItem;
import java.util.List;

@Dao
public interface TodoDao {

    // 插入新任务
    @Insert
    void insert(TodoItem item);

    // 更新任务状态（比如从 Todo 变 Done）
    @Update
    void update(TodoItem item);

    // 【核心方法】根据日期查询当天的所有任务
    @Query("SELECT * FROM todo_items WHERE dateStr = :date")
    List<TodoItem> getTasksByDate(String date);

    // 查询所有任务（可选）
    @Query("SELECT * FROM todo_items")
    List<TodoItem> getAllTasks();

    @Query("SELECT * FROM todo_items WHERE boardKey = :key AND dateStr = :date")
    List<TodoItem> getTasksByBoardAndDate(String key, String date);
}