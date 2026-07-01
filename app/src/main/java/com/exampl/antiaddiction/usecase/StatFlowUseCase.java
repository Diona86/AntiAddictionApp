package com.exampl.antiaddiction.usecase;

import com.exampl.antiaddiction.model.AppUsageInfo;
import com.exampl.antiaddiction.model.ChildDashboardItem;
import com.exampl.antiaddiction.model.PolicyConfig;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class StatFlowUseCase {

    public void applyPolicyToItem(ChildDashboardItem item, PolicyConfig policy) {
        if (item == null) {
            return;
        }
        if (policy == null) {
            item.totalLimit = null;
            item.appLimitsJson = "{}";
            return;
        }
        item.totalLimit = policy.totalLimit;
        item.appLimitsJson = policy.appLimitsJson == null ? "{}" : policy.appLimitsJson;
    }

    public void sortDashboardItems(List<ChildDashboardItem> items) {
        if (items == null) {
            return;
        }
        Collections.sort(items, (left, right) ->
                Long.compare(
                        right == null ? 0L : right.totalTime,
                        left == null ? 0L : left.totalTime
                )
        );
    }

    public Set<String> collectOverLimitApps(List<AppUsageInfo> apps, PolicyConfig policy, StatUseCase statUseCase) {
        Set<String> overLimitApps = new HashSet<>();
        if (policy == null || statUseCase == null) {
            return overLimitApps;
        }
        return statUseCase.collectOverLimitApps(apps, policy.parseAppLimitsAsDoubleMap(), overLimitApps);
    }
}
