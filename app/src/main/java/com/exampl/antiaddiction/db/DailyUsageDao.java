package com.exampl.antiaddiction.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.exampl.antiaddiction.model.DailyUsageRecord;

import java.util.List;

@Dao
public interface DailyUsageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(DailyUsageRecord record);

    @Query("SELECT * FROM daily_usage_records WHERE dateStr = :date LIMIT 1")
    DailyUsageRecord getByDate(String date);

    @Query("SELECT * FROM daily_usage_records ORDER BY dateStr DESC LIMIT :limit")
    List<DailyUsageRecord> getRecentRecords(int limit);
}
