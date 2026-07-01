package com.exampl.antiaddiction.fragment;

import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.exampl.antiaddiction.R;
import com.exampl.antiaddiction.adapter.ChildDashboardAdapter;
import com.exampl.antiaddiction.model.AppUsageInfo;
import com.exampl.antiaddiction.model.ChildDashboardItem;
import com.exampl.antiaddiction.model.DailyUsageRecord;
import com.exampl.antiaddiction.viewmodel.StatIntent;
import com.exampl.antiaddiction.viewmodel.StatUiState;
import com.exampl.antiaddiction.viewmodel.StatViewModel;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.utils.ColorTemplate;
import com.github.mikephil.charting.animation.Easing;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class StatPageFragment extends Fragment {

    private static final int CHART_ANIM_DURATION_MS = 900;
    private static final int PIE_TOP_APP_COUNT = 4;

    private RecyclerView mainRecyclerView;
    private View loadingContainer;
    private ChildDashboardAdapter mainAdapter;
    private final List<ChildDashboardItem> displayData = new ArrayList<>();
    private StatViewModel viewModel;
    private String currentRole = "";
    private String adapterRole = "";
    private PieChart pieAppUsage;
    private LineChart lineTodayTrend;
    private LineChart lineWeekTrend;
    private HorizontalScrollView scrollChartChildFilter;
    private ChipGroup chipGroupChartChild;
    private TextView tvChartScopeHint;
    private TextView tvChartSwitchLabel;
    private View blockWeekTrend;
    private TextView tvWeekSupervisorNote;

    /** 监管端：上方饼图/今日趋势聚焦的自律者 userId；非监管端或未多选时为 null。 */
    private String chartFocusChildUserId;
    private boolean chartChipListenerSuspended;
    private StatUiState lastChartState;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_stat, container, false);
        mainRecyclerView = view.findViewById(R.id.mainRecyclerView);
        loadingContainer = view.findViewById(R.id.statLoadingContainer);
        pieAppUsage = view.findViewById(R.id.pieAppUsage);
        lineTodayTrend = view.findViewById(R.id.lineTodayTrend);
        lineWeekTrend = view.findViewById(R.id.lineWeekTrend);
        scrollChartChildFilter = view.findViewById(R.id.scrollChartChildFilter);
        chipGroupChartChild = view.findViewById(R.id.chipGroupChartChild);
        tvChartScopeHint = view.findViewById(R.id.tvChartScopeHint);
        tvChartSwitchLabel = view.findViewById(R.id.tvChartSwitchLabel);
        blockWeekTrend = view.findViewById(R.id.blockWeekTrend);
        tvWeekSupervisorNote = view.findViewById(R.id.tvWeekSupervisorNote);
        mainRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        initCharts();
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(StatViewModel.class);
        viewModel.getUiState().observe(getViewLifecycleOwner(), this::render);
        viewModel.dispatch(new StatIntent.Refresh());
    }

    @Override
    public void onResume() {
        super.onResume();
        if (viewModel != null) {
            viewModel.dispatch(new StatIntent.ResumeAutoRefresh());
            viewModel.dispatch(new StatIntent.Refresh());
        }
    }

    @Override
    public void onPause() {
        if (viewModel != null) {
            viewModel.dispatch(new StatIntent.PauseAutoRefresh());
        }
        super.onPause();
    }

    private void render(StatUiState state) {
        if (state == null) {
            return;
        }
        currentRole = state.role == null ? "" : state.role;
        setLoading(state.loading);

        displayData.clear();
        if (state.dashboardItems != null) {
            displayData.addAll(state.dashboardItems);
        }
        setupSupervisorChartChrome();
        applyTopCharts(state);
        bindOrRefreshAdapter();

        if (state.messageEvent != null && !state.messageEvent.trim().isEmpty()) {
            Toast.makeText(getContext(), state.messageEvent, Toast.LENGTH_SHORT).show();
            if (viewModel != null) {
                viewModel.consumeMessageEvent();
            }
        }
    }

    private void bindOrRefreshAdapter() {
        if (mainAdapter == null || !adapterRole.equals(currentRole)) {
            adapterRole = currentRole;
            mainAdapter = new ChildDashboardAdapter(displayData, "supervisor".equals(currentRole), new ChildDashboardAdapter.OnLimitSetListener() {
                @Override
                public void onSetTotalLimit(String userId) {
                    showLimitDialog(userId, null);
                }

                @Override
                public void onSetAppLimit(String userId, String pkg) {
                    showLimitDialog(userId, pkg);
                }

                @Override
                public void onViewAppUsage(AppUsageInfo app, Double limitMinutes) {
                    showAppUsageDialog(app, limitMinutes);
                }
            });
            mainRecyclerView.setAdapter(mainAdapter);
        } else {
            mainAdapter.notifyDataSetChanged();
        }
    }

    private void setLoading(boolean loading) {
        if (loadingContainer != null) {
            loadingContainer.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
    }

    private void initCharts() {
        if (pieAppUsage != null) {
            pieAppUsage.getDescription().setEnabled(false);
            pieAppUsage.setUsePercentValues(true);
            pieAppUsage.setEntryLabelColor(0xFF333333);
            pieAppUsage.setCenterText("App占比");
            Legend legend = pieAppUsage.getLegend();
            legend.setEnabled(true);
            legend.setWordWrapEnabled(true);
        }
        initLineChartStyle(lineTodayTrend);
        initLineChartStyle(lineWeekTrend);
    }

    private void initLineChartStyle(LineChart chart) {
        if (chart == null) {
            return;
        }
        chart.getDescription().setEnabled(false);
        chart.setDrawGridBackground(false);
        chart.setTouchEnabled(true);
        chart.setPinchZoom(false);
        chart.getAxisRight().setEnabled(false);
        chart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        chart.getXAxis().setGranularity(1f);
    }

    private void renderPieChart(PieChart chart, List<AppUsageInfo> list) {
        if (chart == null) {
            return;
        }
        List<AppUsageInfo> safeList = list == null ? new ArrayList<>() : new ArrayList<>(list);
        Collections.sort(safeList, (a, b) -> Long.compare(b == null ? 0L : b.usageTime, a == null ? 0L : a.usageTime));

        List<AppUsageInfo> filteredApps = new ArrayList<>();
        for (AppUsageInfo app : safeList) {
            if (app == null) {
                continue;
            }
            float minutes = app.usageTime / 1000f / 60f;
            if (minutes < 1f) {
                continue;
            }
            filteredApps.add(app);
        }

        List<PieEntry> entries = new ArrayList<>();
        float totalMinutes = 0f;
        float otherMinutes = 0f;
        for (int i = 0; i < filteredApps.size(); i++) {
            AppUsageInfo app = filteredApps.get(i);
            float minutes = app.usageTime / 1000f / 60f;
            if (i < PIE_TOP_APP_COUNT) {
                String appName = app.appName == null || app.appName.trim().isEmpty() ? "未知应用" : app.appName;
                entries.add(new PieEntry(minutes, appName));
            } else {
                otherMinutes += minutes;
            }
            totalMinutes += minutes;
        }
        if (otherMinutes > 0f) {
            entries.add(new PieEntry(otherMinutes, "其他"));
        }

        chart.getDescription().setEnabled(false);
        chart.setDrawHoleEnabled(true);
        chart.setHoleRadius(52f);
        chart.setTransparentCircleRadius(58f);
        chart.setCenterText("使用占比");
        chart.setCenterTextSize(14f);
        chart.setEntryLabelTextSize(11f);
        chart.setEntryLabelColor(0xFF333333);
        chart.setUsePercentValues(false);

        if (entries.isEmpty()) {
            chart.clear();
            chart.setNoDataText("暂无满足条件的数据（>=1分钟）");
            chart.invalidate();
            return;
        }

        final float finalTotalMinutes = totalMinutes <= 0f ? 1f : totalMinutes;
        final List<Float> displayPercents = new ArrayList<>();
        float percentAccumulated = 0f;
        for (int i = 0; i < entries.size(); i++) {
            PieEntry entry = entries.get(i);
            float rawPercent = entry.getValue() * 100f / finalTotalMinutes;
            float shownPercent;
            if (i == entries.size() - 1) {
                shownPercent = Math.max(0f, 100f - percentAccumulated);
            } else {
                shownPercent = Math.round(rawPercent * 10f) / 10f;
                percentAccumulated += shownPercent;
            }
            displayPercents.add(shownPercent);
        }

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(ColorTemplate.MATERIAL_COLORS);
        dataSet.setSliceSpace(2f);
        dataSet.setValueLinePart1OffsetPercentage(80f);
        dataSet.setValueLinePart1Length(0.3f);
        dataSet.setValueLinePart2Length(0.4f);
        dataSet.setYValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);
        dataSet.setXValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);

        PieData data = new PieData(dataSet);
        data.setValueTextSize(11f);
        data.setValueTextColor(0xFF444444);
        data.setValueFormatter(new ValueFormatter() {
            @Override
            public String getPieLabel(float value, PieEntry pieEntry) {
                int index = entries.indexOf(pieEntry);
                float percent = index >= 0 && index < displayPercents.size()
                        ? displayPercents.get(index)
                        : (value * 100f / finalTotalMinutes);
                return pieEntry.getLabel() + " " + String.format(Locale.getDefault(), "%.1f%%", percent);
            }
        });

        chart.setData(data);
        chart.spin(CHART_ANIM_DURATION_MS, 0f, 360f, Easing.EaseInOutQuad);
        chart.invalidate();
    }

    private void updateTodayLineChart(List<Float> todayTrendMinutes) {
        if (lineTodayTrend == null) {
            return;
        }
        List<Entry> entries = buildRealTodayTrendEntries(todayTrendMinutes);
        if (entries.isEmpty()) {
            ChildDashboardItem item = resolveChartFocusItem();
            long totalMillis = item == null ? 0L : item.totalTime;
            entries = buildTodayTrendEntries(totalMillis);
        }
        LineDataSet dataSet = new LineDataSet(entries, "累计使用（分钟）");
        styleLineDataSet(dataSet, 0xFF42A5F5);
        dataSet.setMode(LineDataSet.Mode.LINEAR);
        lineTodayTrend.setData(new LineData(dataSet));
        configureTodayTrendAxis(lineTodayTrend, entries);

        List<String> labels = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            labels.add((i * 2) + "时");
        }
        if (todayTrendMinutes != null && !todayTrendMinutes.isEmpty() && !labels.isEmpty()) {
            int currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            labels.set(labels.size() - 1, currentHour + "时");
        }
        lineTodayTrend.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        lineTodayTrend.getAxisLeft().setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.format(Locale.getDefault(), "%.0f分", value);
            }
        });
        lineTodayTrend.animateX(CHART_ANIM_DURATION_MS, Easing.EaseInOutQuad);
        lineTodayTrend.invalidate();
    }

    private List<Entry> buildRealTodayTrendEntries(List<Float> todayTrendMinutes) {
        List<Entry> entries = new ArrayList<>();
        if (todayTrendMinutes == null || todayTrendMinutes.isEmpty()) {
            return entries;
        }
        // 0 点时刻累计为 0；其后各点对应每 2 小时段结束时的累计值
        entries.add(new Entry(0, 0f));
        for (int i = 0; i < todayTrendMinutes.size(); i++) {
            Float minutes = todayTrendMinutes.get(i);
            entries.add(new Entry(i + 1, minutes == null ? 0f : minutes));
        }
        return entries;
    }

    private List<Entry> buildTodayTrendEntries(long totalMillis) {
        List<Entry> entries = new ArrayList<>();
        entries.add(new Entry(0, 0f));
        float totalMinutes = totalMillis / 1000f / 60f;
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int bucketCount = 12;
        int activeBucket = Math.max(1, Math.min(bucketCount, (hour / 2) + 1));
        for (int i = 0; i < bucketCount; i++) {
            float ratio = i < activeBucket ? ((float) (i + 1) / activeBucket) : 1f;
            float value = i < activeBucket ? totalMinutes * ratio : totalMinutes;
            entries.add(new Entry(i + 1, value));
        }
        return entries;
    }

    private void renderWeekChart(LineChart chart, List<DailyUsageRecord> list) {
        if (chart == null) {
            return;
        }

        Map<String, Long> dailyMap = new HashMap<>();
        if (list != null) {
            for (DailyUsageRecord record : list) {
                if (record == null || record.dateStr == null || record.dateStr.trim().isEmpty()) {
                    continue;
                }
                dailyMap.put(record.dateStr, record.totalUsageMillis);
            }
        }

        SimpleDateFormat keyFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        SimpleDateFormat xLabelFormat = new SimpleDateFormat("MM-dd", Locale.getDefault());
        Calendar calendar = Calendar.getInstance();
        List<Entry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            Calendar day = (Calendar) calendar.clone();
            day.add(Calendar.DAY_OF_YEAR, -i);
            String dateKey = keyFormat.format(day.getTime());
            long totalMillis = dailyMap.containsKey(dateKey) ? dailyMap.get(dateKey) : 0L;
            float hours = totalMillis / 1000f / 60f / 60f;
            entries.add(new Entry(6 - i, hours));
            labels.add(xLabelFormat.format(day.getTime()));
        }

        chart.getDescription().setEnabled(false);
        chart.setDrawGridBackground(false);
        chart.setDrawBorders(false);
        chart.getLegend().setEnabled(false);
        chart.getAxisRight().setEnabled(false);
        chart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        chart.getXAxis().setGranularity(1f);
        chart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        chart.getXAxis().setTextSize(10f);
        chart.getXAxis().setDrawGridLines(false);
        chart.getAxisLeft().setTextSize(10f);
        chart.getAxisLeft().setAxisMinimum(0f);
        chart.getAxisLeft().setDrawGridLines(true);

        LineDataSet dataSet = new LineDataSet(entries, "日总使用（小时）");
        dataSet.setColor(0xFF66BB6A);
        dataSet.setCircleColor(0xFF66BB6A);
        dataSet.setLineWidth(2f);
        dataSet.setCircleRadius(3.5f);
        dataSet.setDrawCircleHole(false);
        dataSet.setDrawValues(false);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);

        chart.setData(new LineData(dataSet));
        chart.animateX(CHART_ANIM_DURATION_MS, Easing.EaseInOutQuad);
        chart.invalidate();
    }

    private void configureTodayTrendAxis(LineChart chart, List<Entry> entries) {
        if (chart == null || entries == null || entries.isEmpty()) {
            return;
        }
        float maxY = 0f;
        for (Entry entry : entries) {
            maxY = Math.max(maxY, entry.getY());
        }
        float axisMax = maxY <= 0f ? 60f : (float) Math.ceil(maxY / 20f) * 20f;
        if (axisMax < maxY) {
            axisMax += 20f;
        }

        chart.setMinOffset(0f);
        chart.setAutoScaleMinMaxEnabled(false);

        YAxis left = chart.getAxisLeft();
        left.setAxisMinimum(0f);
        left.setAxisMaximum(axisMax);
        left.setSpaceTop(0f);
        left.setSpaceBottom(0f);
        left.setDrawGridLines(true);
        left.setTextSize(10f);

        float xMax = Math.max(1f, entries.size() - 1);
        XAxis xAxis = chart.getXAxis();
        xAxis.setAxisMinimum(0f);
        xAxis.setAxisMaximum(xMax);
        xAxis.setSpaceMin(0f);
        xAxis.setSpaceMax(0f);
        xAxis.setGranularity(1f);
        xAxis.setDrawGridLines(false);
        xAxis.setTextSize(10f);
    }

    private void styleLineDataSet(LineDataSet dataSet, int color) {
        dataSet.setColor(color);
        dataSet.setCircleColor(color);
        dataSet.setLineWidth(2f);
        dataSet.setCircleRadius(3f);
        dataSet.setDrawValues(false);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
    }

    private ChildDashboardItem resolveChartFocusItem() {
        if (displayData.isEmpty()) {
            return null;
        }
        if (chartFocusChildUserId != null && !chartFocusChildUserId.trim().isEmpty()) {
            for (ChildDashboardItem item : displayData) {
                if (item != null && chartFocusChildUserId.equals(item.userId)) {
                    return item;
                }
            }
        }
        return displayData.get(0);
    }

    private String displayNameForChartChild(ChildDashboardItem item) {
        if (item == null) {
            return "";
        }
        if (item.username != null) {
            String u = item.username.trim();
            if (!u.isEmpty()) {
                return u;
            }
        }
        if (item.nickname != null) {
            String n = item.nickname.trim();
            if (!n.isEmpty()) {
                return n;
            }
        }
        return getString(R.string.stat_child_unnamed);
    }

    private boolean isChartFocusChildStillPresent() {
        if (chartFocusChildUserId == null || chartFocusChildUserId.trim().isEmpty()) {
            return false;
        }
        for (ChildDashboardItem item : displayData) {
            if (item != null && chartFocusChildUserId.equals(item.userId)) {
                return true;
            }
        }
        return false;
    }

    private void setupSupervisorChartChrome() {
        if (tvChartSwitchLabel == null || scrollChartChildFilter == null || chipGroupChartChild == null) {
            return;
        }
        boolean supervisor = "supervisor".equals(currentRole);
        if (!supervisor) {
            chartFocusChildUserId = null;
            tvChartSwitchLabel.setVisibility(View.GONE);
            scrollChartChildFilter.setVisibility(View.GONE);
            chipGroupChartChild.setOnCheckedChangeListener(null);
            chipGroupChartChild.removeAllViews();
            return;
        }
        if (displayData.size() > 1) {
            tvChartSwitchLabel.setVisibility(View.VISIBLE);
            scrollChartChildFilter.setVisibility(View.VISIBLE);
            if (!isChartFocusChildStillPresent()) {
                ChildDashboardItem first = displayData.get(0);
                chartFocusChildUserId = first != null ? first.userId : null;
            }
            rebuildChartChildChips();
        } else {
            tvChartSwitchLabel.setVisibility(View.GONE);
            scrollChartChildFilter.setVisibility(View.GONE);
            chipGroupChartChild.setOnCheckedChangeListener(null);
            chipGroupChartChild.removeAllViews();
            if (displayData.size() == 1) {
                ChildDashboardItem only = displayData.get(0);
                chartFocusChildUserId = only != null ? only.userId : null;
            } else {
                chartFocusChildUserId = null;
            }
        }
    }

    private void rebuildChartChildChips() {
        if (chipGroupChartChild == null || getContext() == null) {
            return;
        }
        chartChipListenerSuspended = true;
        chipGroupChartChild.setOnCheckedChangeListener(null);
        chipGroupChartChild.removeAllViews();
        for (ChildDashboardItem item : displayData) {
            if (item == null || item.userId == null || item.userId.trim().isEmpty()) {
                continue;
            }
            Chip chip = new Chip(requireContext());
            chip.setText(displayNameForChartChild(item));
            chip.setCheckable(true);
            chip.setTag(item.userId);
            chip.setId(View.generateViewId());
            chipGroupChartChild.addView(chip);
        }
        int toCheck = View.NO_ID;
        for (int i = 0; i < chipGroupChartChild.getChildCount(); i++) {
            View v = chipGroupChartChild.getChildAt(i);
            if (!(v instanceof Chip)) {
                continue;
            }
            Object tag = v.getTag();
            if (tag != null && tag.equals(chartFocusChildUserId)) {
                toCheck = v.getId();
                break;
            }
        }
        if (toCheck == View.NO_ID && chipGroupChartChild.getChildCount() > 0) {
            View first = chipGroupChartChild.getChildAt(0);
            toCheck = first.getId();
            chartFocusChildUserId = (String) first.getTag();
        }
        if (toCheck != View.NO_ID) {
            chipGroupChartChild.check(toCheck);
        }
        chipGroupChartChild.setOnCheckedChangeListener((group, checkedId) -> {
            if (chartChipListenerSuspended || checkedId == View.NO_ID) {
                return;
            }
            Chip checked = group.findViewById(checkedId);
            if (checked == null || checked.getTag() == null) {
                return;
            }
            chartFocusChildUserId = String.valueOf(checked.getTag());
            if (lastChartState != null) {
                applyTopCharts(lastChartState);
            }
        });
        chipGroupChartChild.post(() -> chartChipListenerSuspended = false);
    }

    private void applyTopCharts(StatUiState state) {
        lastChartState = state;
        boolean supervisor = "supervisor".equals(currentRole);
        if (supervisor && !displayData.isEmpty()) {
            ChildDashboardItem focus = resolveChartFocusItem();
            List<AppUsageInfo> apps = focus == null ? new ArrayList<>() : focus.parseApps();
            renderPieChart(pieAppUsage, apps);
        } else {
            renderPieChart(pieAppUsage, state.todayApps == null ? new ArrayList<>() : state.todayApps);
        }
        updateTodayLineChart(state.todayTrendMinutes == null ? new ArrayList<>() : state.todayTrendMinutes);
        if (supervisor) {
            if (blockWeekTrend != null) {
                blockWeekTrend.setVisibility(View.GONE);
            }
            if (tvWeekSupervisorNote != null) {
                tvWeekSupervisorNote.setVisibility(displayData.isEmpty() ? View.GONE : View.VISIBLE);
            }
            if (lineWeekTrend != null) {
                lineWeekTrend.clear();
            }
        } else {
            if (blockWeekTrend != null) {
                blockWeekTrend.setVisibility(View.VISIBLE);
            }
            if (tvWeekSupervisorNote != null) {
                tvWeekSupervisorNote.setVisibility(View.GONE);
            }
            renderWeekChart(lineWeekTrend, state.weekUsage);
        }
        updateChartScopeHintText();
    }

    private void updateChartScopeHintText() {
        if (tvChartScopeHint == null) {
            return;
        }
        if (!"supervisor".equals(currentRole) || displayData.isEmpty()) {
            tvChartScopeHint.setVisibility(View.GONE);
            return;
        }
        ChildDashboardItem focus = resolveChartFocusItem();
        tvChartScopeHint.setText(getString(R.string.stat_chart_scope_hint, displayNameForChartChild(focus)));
        tvChartScopeHint.setVisibility(View.VISIBLE);
    }

    private void showAppUsageDialog(AppUsageInfo app, Double limitMinutes) {
        if (app == null || getContext() == null) {
            return;
        }
        long usedMinutes = app.usageTime / 1000 / 60;
        StringBuilder message = new StringBuilder();
        message.append("已使用: ").append(app.timeFormatted).append(" (").append(usedMinutes).append(" 分钟)");
        if (limitMinutes == null) {
            message.append("\n限制: 无限制");
        } else {
            int limit = limitMinutes.intValue();
            long remain = Math.max(0, limit - usedMinutes);
            int percent = limit > 0 ? (int) Math.min(100, (usedMinutes * 100 / limit)) : 100;
            message.append("\n限制: ").append(limit).append(" 分钟");
            message.append("\n进度: ").append(percent).append("%");
            message.append("\n剩余: ").append(remain).append(" 分钟");
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(app.appName)
                .setMessage(message.toString())
                .setPositiveButton("知道了", null)
                .show();
    }

    private void showLimitDialog(String userId, String pkg) {
        if (getContext() == null || viewModel == null) {
            return;
        }
        EditText et = new EditText(getContext());
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setHint("输入分钟数");
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(pkg == null ? "设置总限额" : "设置App限额")
                .setView(et)
                .setPositiveButton("确认", (d, w) -> {
                    String val = et.getText().toString();
                    if (val.isEmpty()) {
                        Toast.makeText(getContext(), "请输入分钟数", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    int limitMinutes;
                    try {
                        limitMinutes = Integer.parseInt(val);
                    } catch (NumberFormatException ex) {
                        Toast.makeText(getContext(), "限额格式不正确", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (limitMinutes <= 0) {
                        Toast.makeText(getContext(), "限额需大于 0 分钟", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (pkg == null) {
                        viewModel.dispatch(new StatIntent.SubmitTotalLimit(userId, limitMinutes));
                    } else {
                        viewModel.dispatch(new StatIntent.SubmitAppLimit(userId, pkg, limitMinutes));
                    }
                })
                .show();
    }
}
