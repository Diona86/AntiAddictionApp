package com.exampl.antiaddiction.data.repository;

import com.exampl.antiaddiction.cloudbase.CloudBaseCallback;
import com.exampl.antiaddiction.cloudbase.CloudBaseClient;
import com.exampl.antiaddiction.data.common.UserIdNormalizer;
import com.exampl.antiaddiction.model.PolicyConfig;
import com.exampl.antiaddiction.model.PolicyUpdatePayload;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PolicyRepository {

    private final CloudBaseClient cloudbase;
    private final Gson gson = new Gson();

    public PolicyRepository(CloudBaseClient cloudbase) {
        this.cloudbase = cloudbase;
    }

    public void getPolicy(String userId, CloudBaseCallback<PolicyConfig> callback) {
        String queryId = UserIdNormalizer.normalizeForCloudQuery(userId);
        String path = "/v1/rdb/rest/control_policy?userId=eq." + queryId;
        cloudbase.get(path, new TypeToken<List<Map<String, Object>>>() {}, new CloudBaseCallback<List<Map<String, Object>>>() {
            @Override
            public void onSuccess(List<Map<String, Object>> data) {
                if (data == null || data.isEmpty()) {
                    callback.onSuccess(null);
                    return;
                }
                callback.onSuccess(PolicyConfig.fromMap(data.get(0)));
            }

            @Override
            public void onError(int code, String message) {
                callback.onError(code, message);
            }
        });
    }

    public void updatePolicy(String userId, String packageName, int minutes, CloudBaseCallback<Object> callback) {
        String queryId = UserIdNormalizer.normalizeForCloudQuery(userId);
        String path = "/v1/rdb/rest/control_policy?userId=eq." + queryId;
        cloudbase.get(path, new TypeToken<List<Map<String, Object>>>() {}, new CloudBaseCallback<List<Map<String, Object>>>() {
            @Override
            public void onSuccess(List<Map<String, Object>> data) {
                PolicyUpdatePayload payload = new PolicyUpdatePayload();
                payload.userId = queryId;
                if (packageName == null) {
                    payload.totalLimit = minutes;
                } else {
                    Map<String, Integer> currentLimits = new HashMap<>();
                    if (data != null && !data.isEmpty()) {
                        PolicyConfig config = PolicyConfig.fromMap(data.get(0));
                        currentLimits.putAll(config.parseAppLimitsAsIntMap());
                    }
                    currentLimits.put(packageName, minutes);
                    payload.appLimitsJson = gson.toJson(currentLimits);
                }

                String method = (data != null && !data.isEmpty()) ? "PATCH" : "POST";
                String finalPath = method.equals("PATCH") ? path : "/v1/rdb/rest/control_policy";
                Map<String, Object> body = payload.toMap();
                if ("PATCH".equals(method)) {
                    cloudbase.patch(finalPath, body, null, callback);
                } else {
                    cloudbase.post(finalPath, body, null, callback);
                }
            }

            @Override
            public void onError(int code, String message) {
                callback.onError(code, message);
            }
        });
    }
}
