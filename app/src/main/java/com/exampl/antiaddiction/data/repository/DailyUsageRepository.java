package com.exampl.antiaddiction.data.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.exampl.antiaddiction.cloudbase.CloudBaseCallback;
import com.exampl.antiaddiction.db.AppDatabase;
import com.exampl.antiaddiction.model.DailyUsageRecord;
import com.google.gson.Gson;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DailyUsageRepository {

    private final Context appContext;
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Gson gson = new Gson();

    public DailyUsageRepository(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public void saveDailyUsageSnapshot(long totalMillis, Set<String> overLimitApps, CloudBaseCallback<Object> callback) {
        String dateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        DailyUsageRecord record = new DailyUsageRecord(
                dateStr,
                totalMillis,
                gson.toJson(new ArrayList<>(overLimitApps == null ? java.util.Collections.emptySet() : overLimitApps))
        );
        dbExecutor.execute(() -> {
            try {
                AppDatabase.getInstance(appContext).dailyUsageDao().upsert(record);
                if (callback != null) {
                    mainHandler.post(() -> callback.onSuccess(record));
                }
            } catch (Exception e) {
                if (callback != null) {
                    mainHandler.post(() -> callback.onError(-1, e.getMessage() == null ? "save daily usage failed" : e.getMessage()));
                }
            }
        });
    }

    public void getRecentUsageRecords(int limit, CloudBaseCallback<List<DailyUsageRecord>> callback) {
        dbExecutor.execute(() -> {
            try {
                List<DailyUsageRecord> records = AppDatabase.getInstance(appContext).dailyUsageDao().getRecentRecords(limit);
                if (callback != null) {
                    mainHandler.post(() -> callback.onSuccess(records == null ? new ArrayList<>() : records));
                }
            } catch (Exception e) {
                if (callback != null) {
                    mainHandler.post(() -> callback.onError(-1, e.getMessage() == null ? "load daily usage failed" : e.getMessage()));
                }
            }
        });
    }
}
