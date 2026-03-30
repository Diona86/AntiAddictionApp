package com.exampl.antiaddiction.db;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import com.exampl.antiaddiction.model.TodoItem;

@Database(entities = {TodoItem.class}, version = 1)
public abstract class AppDatabase extends RoomDatabase {

    public abstract TodoDao todoDao();

    private static AppDatabase instance;

    public static synchronized AppDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class, "anti_addiction_db")
                    .fallbackToDestructiveMigration()
                    .allowMainThreadQueries() // 注意：为了演示方便先允许主线程查询，实际开发建议用异步
                    .build();
        }
        return instance;
    }
}