package com.exampl.antiaddiction.viewmodel;

import com.exampl.antiaddiction.model.AppUsageInfo;
import com.exampl.antiaddiction.model.ChildDashboardItem;
import com.exampl.antiaddiction.model.DailyUsageRecord;

import java.util.ArrayList;
import java.util.List;

public class StatUiState {
    public final boolean loading;
    public final String role;
    public final List<ChildDashboardItem> dashboardItems;
    public final List<AppUsageInfo> todayApps;
    public final List<Float> todayTrendMinutes;
    public final List<DailyUsageRecord> weekUsage;
    public final String messageEvent;

    public StatUiState(
            boolean loading,
            String role,
            List<ChildDashboardItem> dashboardItems,
            List<AppUsageInfo> todayApps,
            List<Float> todayTrendMinutes,
            List<DailyUsageRecord> weekUsage,
            String messageEvent
    ) {
        this.loading = loading;
        this.role = role == null ? "" : role;
        this.dashboardItems = dashboardItems == null ? new ArrayList<>() : dashboardItems;
        this.todayApps = todayApps == null ? new ArrayList<>() : todayApps;
        this.todayTrendMinutes = todayTrendMinutes == null ? new ArrayList<>() : todayTrendMinutes;
        this.weekUsage = weekUsage == null ? new ArrayList<>() : weekUsage;
        this.messageEvent = messageEvent == null ? "" : messageEvent;
    }

    public static StatUiState initial() {
        return new StatUiState(false, "", new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), "");
    }

    public StatUiState withLoading(boolean newLoading) {
        return new StatUiState(newLoading, role, dashboardItems, todayApps, todayTrendMinutes, weekUsage, messageEvent);
    }

    public StatUiState withRole(String newRole) {
        return new StatUiState(loading, newRole, dashboardItems, todayApps, todayTrendMinutes, weekUsage, messageEvent);
    }

    public StatUiState withDashboardItems(List<ChildDashboardItem> newItems) {
        return new StatUiState(loading, role, new ArrayList<>(newItems == null ? new ArrayList<>() : newItems), todayApps, todayTrendMinutes, weekUsage, messageEvent);
    }

    public StatUiState withTodayApps(List<AppUsageInfo> newTodayApps) {
        return new StatUiState(loading, role, dashboardItems, new ArrayList<>(newTodayApps == null ? new ArrayList<>() : newTodayApps), todayTrendMinutes, weekUsage, messageEvent);
    }

    public StatUiState withTodayTrendMinutes(List<Float> newTodayTrendMinutes) {
        return new StatUiState(loading, role, dashboardItems, todayApps, new ArrayList<>(newTodayTrendMinutes == null ? new ArrayList<>() : newTodayTrendMinutes), weekUsage, messageEvent);
    }

    public StatUiState withWeekUsage(List<DailyUsageRecord> newWeekUsage) {
        return new StatUiState(loading, role, dashboardItems, todayApps, todayTrendMinutes, new ArrayList<>(newWeekUsage == null ? new ArrayList<>() : newWeekUsage), messageEvent);
    }

    public StatUiState withMessageEvent(String message) {
        return new StatUiState(loading, role, dashboardItems, todayApps, todayTrendMinutes, weekUsage, message);
    }
}
