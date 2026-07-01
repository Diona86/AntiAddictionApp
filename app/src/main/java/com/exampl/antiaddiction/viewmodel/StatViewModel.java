package com.exampl.antiaddiction.viewmodel;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.exampl.antiaddiction.R;
import com.exampl.antiaddiction.cloudbase.CloudBaseCallback;
import com.exampl.antiaddiction.cloudbase.CloudBaseClient;
import com.exampl.antiaddiction.data.common.UserIdNormalizer;
import com.exampl.antiaddiction.data.datasource.UsageStatsDataSource;
import com.exampl.antiaddiction.data.repository.DailyUsageRepository;
import com.exampl.antiaddiction.data.repository.PolicyRepository;
import com.exampl.antiaddiction.data.repository.UsageRepository;
import com.exampl.antiaddiction.data.repository.UserRepository;
import com.exampl.antiaddiction.manager.UserManager;
import com.exampl.antiaddiction.model.AppUsageInfo;
import com.exampl.antiaddiction.model.ChildDashboardItem;
import com.exampl.antiaddiction.model.ChildUser;
import com.exampl.antiaddiction.model.DailyUsageRecord;
import com.exampl.antiaddiction.model.PolicyConfig;
import com.exampl.antiaddiction.model.UsageReport;
import com.exampl.antiaddiction.usecase.StatFlowUseCase;
import com.exampl.antiaddiction.usecase.StatUseCase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StatViewModel extends AndroidViewModel {

    private static final long SUPERVISOR_REFRESH_INTERVAL_MS = 30 * 1000L;

    private final MutableLiveData<StatUiState> uiState = new MutableLiveData<>(StatUiState.initial());
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService usageExecutor = Executors.newSingleThreadExecutor();

    private final UserManager userManager;
    private final UsageStatsDataSource usageStatsDataSource;
    private final UsageRepository usageRepository;
    private final PolicyRepository policyRepository;
    private final UserRepository userRepository;
    private final DailyUsageRepository dailyUsageRepository;
    private final StatUseCase statUseCase = new StatUseCase();
    private final StatFlowUseCase statFlowUseCase = new StatFlowUseCase();

    private long refreshToken = 0L;
    private final Runnable supervisorAutoRefreshTask = new Runnable() {
        @Override
        public void run() {
            StatUiState state = getState();
            if ("supervisor".equals(state.role)) {
                dispatch(new StatIntent.Refresh());
                mainHandler.postDelayed(this, SUPERVISOR_REFRESH_INTERVAL_MS);
            }
        }
    };

    public StatViewModel(@NonNull Application application) {
        super(application);
        userManager = UserManager.getInstance(application);
        CloudBaseClient cloudbase = new CloudBaseClient(
                application.getString(R.string.CLOUDBASE_ENV_ID),
                application.getString(R.string.CLOUDBASE_ACCESS_TOKEN)
        );
        usageStatsDataSource = new UsageStatsDataSource(application);
        usageRepository = new UsageRepository(cloudbase);
        policyRepository = new PolicyRepository(cloudbase);
        userRepository = new UserRepository(cloudbase);
        dailyUsageRepository = new DailyUsageRepository(application);
    }

    public LiveData<StatUiState> getUiState() {
        return uiState;
    }

    public void consumeMessageEvent() {
        setState(getState().withMessageEvent(""));
    }

    public void dispatch(StatIntent intent) {
        if (intent instanceof StatIntent.Refresh) {
            loadData();
        } else if (intent instanceof StatIntent.ResumeAutoRefresh) {
            startAutoRefreshIfNeeded();
        } else if (intent instanceof StatIntent.PauseAutoRefresh) {
            stopAutoRefresh();
        } else if (intent instanceof StatIntent.SubmitTotalLimit) {
            StatIntent.SubmitTotalLimit i = (StatIntent.SubmitTotalLimit) intent;
            submitPolicyUpdate(i.userId, null, i.minutes);
        } else if (intent instanceof StatIntent.SubmitAppLimit) {
            StatIntent.SubmitAppLimit i = (StatIntent.SubmitAppLimit) intent;
            submitPolicyUpdate(i.userId, i.packageName, i.minutes);
        }
    }

    private void loadData() {
        long token = ++refreshToken;
        String role = userManager.getRole();
        if (role == null || role.trim().isEmpty()) {
            role = "self";
        }
        setState(getState().withRole(role).withLoading(true));
        if ("self".equals(role) || "child".equals(role)) {
            loadSelfDashboard(token);
        } else {
            loadSupervisorDashboard(token);
        }
    }

    private void loadSelfDashboard(long token) {
        String userId = UserIdNormalizer.normalizeForCloudQuery(userManager.getUserId());
        usageExecutor.execute(() -> {
            Map<String, Long> usageByPkg = usageStatsDataSource.collectTodayUsageMillisByPackage();
            List<Float> todayTrendMinutes = usageStatsDataSource.collectTodayCumulativeUsageMinutesBy2Hour();
            if (usageByPkg == null || usageByPkg.isEmpty()) {
                mainHandler.post(() -> {
                    if (!isTokenActive(token)) return;
                    StatUiState next = getState()
                            .withLoading(false)
                            .withDashboardItems(new ArrayList<>())
                            .withMessageEvent("暂无统计数据，请先开启使用情况访问权限");
                    emitStateWithChartData(token, next, new ArrayList<>(), todayTrendMinutes);
                });
                return;
            }

            List<AppUsageInfo> appList = statUseCase.buildSortedUsageList(usageByPkg, getApplication().getPackageManager());
            long totalMillis = statUseCase.calcTotalMillis(appList);
            ChildDashboardItem selfItem = statUseCase.buildSelfDisplayItem(userId, totalMillis, appList);

            usageRepository.upsertTodayUsageReport(userId, totalMillis, appList, new CloudBaseCallback<Object>() {
                @Override
                public void onSuccess(Object data) {}

                @Override
                public void onError(int code, String message) {}
            });

            policyRepository.getPolicy(userId, new CloudBaseCallback<PolicyConfig>() {
                @Override
                public void onSuccess(PolicyConfig policy) {
                    if (!isTokenActive(token)) return;
                    statFlowUseCase.applyPolicyToItem(selfItem, policy);
                    dailyUsageRepository.saveDailyUsageSnapshot(
                            totalMillis,
                            statFlowUseCase.collectOverLimitApps(appList, policy, statUseCase),
                            null
                    );
                    List<ChildDashboardItem> items = new ArrayList<>();
                    items.add(selfItem);
                    StatUiState next = getState()
                            .withDashboardItems(items)
                            .withLoading(false);
                    emitStateWithChartData(token, next, appList, todayTrendMinutes);
                }

                @Override
                public void onError(int code, String message) {
                    if (!isTokenActive(token)) return;
                    dailyUsageRepository.saveDailyUsageSnapshot(totalMillis, Collections.emptySet(), null);
                    List<ChildDashboardItem> items = new ArrayList<>();
                    items.add(selfItem);
                    StatUiState next = getState()
                            .withDashboardItems(items)
                            .withLoading(false)
                            .withMessageEvent("拉取管控策略失败");
                    emitStateWithChartData(token, next, appList, todayTrendMinutes);
                }
            });
        });
    }

    private void loadSupervisorDashboard(long token) {
        String myId = userManager.getUserId() == null ? "" : userManager.getUserId().trim();
        userRepository.getBoundChildren(myId, new CloudBaseCallback<List<ChildUser>>() {
            @Override
            public void onSuccess(List<ChildUser> children) {
                if (!isTokenActive(token)) return;
                if (children == null || children.isEmpty()) {
                    StatUiState next = getState()
                            .withDashboardItems(new ArrayList<>())
                            .withLoading(false)
                            .withMessageEvent("暂无绑定的自律者");
                    emitStateWithChartData(token, next, new ArrayList<>(), new ArrayList<>());
                    return;
                }
                final List<ChildDashboardItem> merged = new ArrayList<>();
                final int[] pending = {children.size()};
                for (ChildUser child : children) {
                    fetchChildDashboardItem(child, token, new CloudBaseCallback<ChildDashboardItem>() {
                        @Override
                        public void onSuccess(ChildDashboardItem item) {
                            if (!isTokenActive(token)) return;
                            merged.add(item == null ? new ChildDashboardItem() : item);
                            pending[0]--;
                            if (pending[0] == 0) {
                                statFlowUseCase.sortDashboardItems(merged);
                                List<AppUsageInfo> todayApps = resolveTodayAppsForChart(merged);
                                StatUiState next = getState()
                                        .withDashboardItems(new ArrayList<>(merged))
                                        .withLoading(false);
                                emitStateWithChartData(token, next, todayApps, new ArrayList<>());
                            }
                        }

                        @Override
                        public void onError(int code, String message) {
                            if (!isTokenActive(token)) return;
                            pending[0]--;
                            if (pending[0] == 0) {
                                statFlowUseCase.sortDashboardItems(merged);
                                List<AppUsageInfo> todayApps = resolveTodayAppsForChart(merged);
                                StatUiState next = getState()
                                        .withDashboardItems(new ArrayList<>(merged))
                                        .withLoading(false)
                                        .withMessageEvent("部分数据加载失败");
                                emitStateWithChartData(token, next, todayApps, new ArrayList<>());
                            }
                        }
                    });
                }
            }

            @Override
            public void onError(int code, String message) {
                if (!isTokenActive(token)) return;
                StatUiState next = getState().withLoading(false).withMessageEvent("加载失败，请稍后重试");
                emitStateWithChartData(token, next, new ArrayList<>(), new ArrayList<>());
            }
        });
    }

    private void emitStateWithChartData(long token, StatUiState baseState, List<AppUsageInfo> todayApps, List<Float> todayTrendMinutes) {
        dailyUsageRepository.getRecentUsageRecords(7, new CloudBaseCallback<List<DailyUsageRecord>>() {
            @Override
            public void onSuccess(List<DailyUsageRecord> weekRecords) {
                if (!isTokenActive(token)) return;
                setState(baseState
                        .withTodayApps(todayApps)
                        .withTodayTrendMinutes(todayTrendMinutes)
                        .withWeekUsage(weekRecords));
            }

            @Override
            public void onError(int code, String message) {
                if (!isTokenActive(token)) return;
                setState(baseState
                        .withTodayApps(todayApps)
                        .withTodayTrendMinutes(todayTrendMinutes)
                        .withWeekUsage(new ArrayList<>()));
            }
        });
    }

    private List<AppUsageInfo> resolveTodayAppsForChart(List<ChildDashboardItem> merged) {
        if (merged == null || merged.isEmpty()) {
            return new ArrayList<>();
        }
        ChildDashboardItem best = merged.get(0);
        for (ChildDashboardItem item : merged) {
            if (item != null && best != null && item.totalTime > best.totalTime) {
                best = item;
            }
        }
        return best == null ? new ArrayList<>() : best.parseApps();
    }

    private void fetchChildDashboardItem(ChildUser child, long token, CloudBaseCallback<ChildDashboardItem> callback) {
        if (child == null) {
            callback.onSuccess(new ChildDashboardItem());
            return;
        }
        ChildDashboardItem item = ChildDashboardItem.fromChildUser(child);
        usageRepository.getTodayUsageReport(child.id, new CloudBaseCallback<UsageReport>() {
            @Override
            public void onSuccess(UsageReport report) {
                if (!isTokenActive(token)) return;
                if (report != null) {
                    item.totalTime = report.totalTime;
                    item.appJson = report.appJson;
                    item.hasOverLimit = report.hasOverLimit;
                    item.overLimitDetail = report.overLimitDetail;
                } else {
                    item.totalTime = 0L;
                    item.appJson = "[]";
                }
                policyRepository.getPolicy(child.id, new CloudBaseCallback<PolicyConfig>() {
                    @Override
                    public void onSuccess(PolicyConfig policy) {
                        if (!isTokenActive(token)) return;
                        statFlowUseCase.applyPolicyToItem(item, policy);
                        callback.onSuccess(item);
                    }

                    @Override
                    public void onError(int code, String message) {
                        if (!isTokenActive(token)) return;
                        statFlowUseCase.applyPolicyToItem(item, null);
                        callback.onSuccess(item);
                    }
                });
            }

            @Override
            public void onError(int code, String message) {
                if (!isTokenActive(token)) return;
                item.totalTime = 0L;
                item.appJson = "[]";
                policyRepository.getPolicy(child.id, new CloudBaseCallback<PolicyConfig>() {
                    @Override
                    public void onSuccess(PolicyConfig policy) {
                        if (!isTokenActive(token)) return;
                        statFlowUseCase.applyPolicyToItem(item, policy);
                        callback.onSuccess(item);
                    }

                    @Override
                    public void onError(int code, String message) {
                        if (!isTokenActive(token)) return;
                        statFlowUseCase.applyPolicyToItem(item, null);
                        callback.onSuccess(item);
                    }
                });
            }
        });
    }

    private void submitPolicyUpdate(String userId, String packageName, int minutes) {
        policyRepository.updatePolicy(userId, packageName, minutes, new CloudBaseCallback<Object>() {
            @Override
            public void onSuccess(Object data) {
                setState(getState().withMessageEvent("管控已下发"));
                dispatch(new StatIntent.Refresh());
            }

            @Override
            public void onError(int code, String message) {
                setState(getState().withMessageEvent("更新失败: " + message));
            }
        });
    }

    private void startAutoRefreshIfNeeded() {
        stopAutoRefresh();
        if ("supervisor".equals(getState().role)) {
            mainHandler.postDelayed(supervisorAutoRefreshTask, SUPERVISOR_REFRESH_INTERVAL_MS);
        }
    }

    private void stopAutoRefresh() {
        mainHandler.removeCallbacks(supervisorAutoRefreshTask);
    }

    private boolean isTokenActive(long token) {
        return token == refreshToken;
    }

    private void setState(StatUiState newState) {
        uiState.setValue(newState);
    }

    private StatUiState getState() {
        StatUiState state = uiState.getValue();
        return state == null ? StatUiState.initial() : state;
    }

    @Override
    protected void onCleared() {
        stopAutoRefresh();
        usageExecutor.shutdownNow();
        super.onCleared();
    }
}
