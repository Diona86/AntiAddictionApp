package com.exampl.antiaddiction.data.repository;

import com.exampl.antiaddiction.cloudbase.CloudBaseCallback;
import com.exampl.antiaddiction.cloudbase.CloudBaseClient;
import com.exampl.antiaddiction.data.common.UserIdNormalizer;
import com.exampl.antiaddiction.model.ChildUser;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public class UserRepository {

    private final CloudBaseClient cloudbase;

    public UserRepository(CloudBaseClient cloudbase) {
        this.cloudbase = cloudbase;
    }

    /**
     * 查询 boundUserId 等于当前监管者 id 的自律者列表。
     * 与 {@link com.exampl.antiaddiction.service.UsageMonitorService} 一致：依次尝试 raw、normalize、normalizeForCloudQuery，
     * 避免云端/本地 id 字符串格式不一致（如末尾 .0、或仅部分字段带小数点）导致列表为空。
     */
    public void getBoundChildren(String supervisorId, CloudBaseCallback<List<ChildUser>> callback) {
        LinkedHashSet<String> variants = new LinkedHashSet<>();
        String raw = supervisorId == null ? "" : supervisorId.trim();
        if (!raw.isEmpty()) {
            variants.add(raw);
        }
        String normalized = UserIdNormalizer.normalize(raw);
        if (!normalized.isEmpty()) {
            variants.add(normalized);
        }
        String forQuery = UserIdNormalizer.normalizeForCloudQuery(raw);
        if (!forQuery.isEmpty()) {
            variants.add(forQuery);
        }
        List<String> variantList = new ArrayList<>(variants);
        if (variantList.isEmpty()) {
            callback.onSuccess(new ArrayList<>());
            return;
        }
        LinkedHashMap<String, ChildUser> mergedById = new LinkedHashMap<>();
        fetchBoundChildrenForVariant(variantList, 0, mergedById, callback);
    }

    private void fetchBoundChildrenForVariant(
            List<String> variantList,
            int index,
            LinkedHashMap<String, ChildUser> mergedById,
            CloudBaseCallback<List<ChildUser>> finalCallback
    ) {
        if (index >= variantList.size()) {
            finalCallback.onSuccess(new ArrayList<>(mergedById.values()));
            return;
        }
        String queryId = variantList.get(index);
        String path = "/v1/rdb/rest/user?boundUserId=eq." + queryId;
        cloudbase.get(path, new TypeToken<List<Map<String, Object>>>() {}, new CloudBaseCallback<List<Map<String, Object>>>() {
            @Override
            public void onSuccess(List<Map<String, Object>> data) {
                if (data != null) {
                    for (Map<String, Object> row : data) {
                        ChildUser user = ChildUser.fromMap(row);
                        if (user.id == null || user.id.trim().isEmpty()) {
                            continue;
                        }
                        String key = UserIdNormalizer.normalize(user.id);
                        if (key.isEmpty()) {
                            continue;
                        }
                        if (!mergedById.containsKey(key)) {
                            mergedById.put(key, user);
                        }
                    }
                }
                fetchBoundChildrenForVariant(variantList, index + 1, mergedById, finalCallback);
            }

            @Override
            public void onError(int code, String message) {
                fetchBoundChildrenForVariant(variantList, index + 1, mergedById, finalCallback);
            }
        });
    }
}
