package com.exampl.antiaddiction.data.repository;

import com.exampl.antiaddiction.cloudbase.CloudBaseCallback;
import com.exampl.antiaddiction.cloudbase.CloudBaseClient;
import com.exampl.antiaddiction.data.common.UserIdNormalizer;
import com.exampl.antiaddiction.model.AppUsageInfo;
import com.exampl.antiaddiction.model.UsageReport;
import com.exampl.antiaddiction.model.UsageReportPayload;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class UsageRepository {

    private final CloudBaseClient cloudbase;
    private final Gson gson = new Gson();

    public UsageRepository(CloudBaseClient cloudbase) {
        this.cloudbase = cloudbase;
    }

    public void getTodayUsageReport(String userId, CloudBaseCallback<UsageReport> callback) {
        String queryId = UserIdNormalizer.normalizeForCloudQuery(userId);
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        String path = "/v1/rdb/rest/usage_report?userId=eq." + queryId + "&dateStr=eq." + today;
        cloudbase.get(path, new TypeToken<List<Map<String, Object>>>() {}, new CloudBaseCallback<List<Map<String, Object>>>() {
            @Override
            public void onSuccess(List<Map<String, Object>> data) {
                if (data == null || data.isEmpty()) {
                    callback.onSuccess(null);
                    return;
                }
                callback.onSuccess(UsageReport.fromMap(data.get(0)));
            }

            @Override
            public void onError(int code, String message) {
                callback.onError(code, message);
            }
        });
    }

    public void upsertTodayUsageReport(String userId, long totalMillis, List<AppUsageInfo> list, CloudBaseCallback<Object> callback) {
        String queryId = UserIdNormalizer.normalizeForCloudQuery(userId);
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        String appJson = gson.toJson(list);
        String checkPath = "/v1/rdb/rest/usage_report?userId=eq." + queryId + "&dateStr=eq." + today;
        cloudbase.get(checkPath, new TypeToken<List<Map<String, Object>>>() {}, new CloudBaseCallback<List<Map<String, Object>>>() {
            @Override
            public void onSuccess(List<Map<String, Object>> data) {
                UsageReportPayload payload = new UsageReportPayload();
                payload.userId = queryId;
                payload.totalTime = totalMillis;
                payload.appJson = appJson;
                payload.dateStr = today;
                Map<String, Object> body = payload.toMap();

                if (data != null && !data.isEmpty()) {
                    String rowId = String.valueOf(data.get(0).get("id"));
                    cloudbase.patch("/v1/rdb/rest/usage_report?id=eq." + rowId, body, null, callback);
                } else {
                    cloudbase.post("/v1/rdb/rest/usage_report", body, null, callback);
                }
            }

            @Override
            public void onError(int code, String message) {
                callback.onError(code, message);
            }
        });
    }
}
