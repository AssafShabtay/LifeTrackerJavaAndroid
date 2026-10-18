package com.example.myapplication.ui.statistics.screenTime;

import com.example.myapplication.tracking.usage.DailyUsageItem;
import com.example.myapplication.tracking.usage.UsageStatsHelper;
import com.example.myapplication.tracking.usage.WeeklyUsageItem;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.tabs.TabLayout;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.example.myapplication.LifeTrackerApp;

import java.util.concurrent.Future;
import java.util.concurrent.CancellationException;

public class ScreenTimeStatsManager {

    public static final int PERIOD_DAILY = 0;
    public static final int PERIOD_WEEKLY = 1;

    private final Context context;
    private final Handler mainHandler;
    private final View rootView;
    private final LifeTrackerApp app;

    private View permissionPromptCard;
    private Button btnGrantUsageAccess;
    private View screenTimeContentLayout;
    private TabLayout tabLayoutPeriod;

    private TextView tvDeviceUnlocks;
    private TextView tvPeakUsageHour;
    private TextView tvIdleScreenTime;


    private ImageButton btnPrevDay;
    private ImageButton btnNextDay;
    private TextView tvDailyCardTitle;
    private TextView tvDailyCardUnlocks;
    private TextView tvDailyCardTotalTime;
    private TextView tvGraphSectionTitle;
    private HourlyUsageGraphView graphDailyHourlyUsage;
    private WeeklyUsageGraphView graphWeeklyDailyUsage;
    private LinearLayout layoutDailyTopAppsList;
    private TextView tvDailyCardNoApps;

    // Doomscrolling Card Views
    private TextView tvDoomscrollTotalTime;
    private TextView tvDoomscrollSessionCount;
    private TextView tvDoomscrollLateNightTime;
    private TextView tvDoomscrollLongestScroll;
    private TextView tvDoomscrollLongestApp;
    private TextView tvDoomscrollRiskChip;
    private MaterialCardView cardDoomscrollRiskBadge;
    private TextView tvDoomscrollRatioLabel;
    private LinearProgressIndicator progressDoomscrollRatio;
    private LinearLayout layoutDoomscrollAppsList;
    private TextView tvDoomscrollNoData;

    private ImageButton btnDoomscrollInfo;

    private final Map<Integer, DailyUsageItem> dailyHistoryList = new HashMap<>();
    private int currentDayIndex = 0; // 0 = Today, 1 = Yesterday, etc.

    private final Map<Integer, WeeklyUsageItem> weeklyHistoryList = new HashMap<>();
    private int currentWeekIndex = 0; // 0 = This Week, 1 = Last Week, etc.

    private int selectedPeriod = PERIOD_DAILY;

    private DetailedPhoneUsageCardManager detailedCardManager;
    private final Map<Integer, Map<String, Long>> dailyPackageUsage = new HashMap<>();
    private final Map<Integer, Map<String, Long>> weeklyPackageUsage = new HashMap<>();
    private LinearProgressIndicator loadingIndicator;
    private Future<?> pendingLoad;
    private int loadVersion;
    private boolean destroyed;

    private final SharedPreferences.OnSharedPreferenceChangeListener prefListener = (sharedPreferences, key) -> {
        if ("week_start_day".equals(key)) {
            loadScreenTimeStats();
        }
    };

    public ScreenTimeStatsManager(Context context, Handler mainHandler, View rootView) {
        this.context = context;
        this.mainHandler = mainHandler;
        this.rootView = rootView;
        this.app = (LifeTrackerApp) context.getApplicationContext();

        initViews();
    }

    private void initViews() {
        SharedPreferences prefs = context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
        prefs.registerOnSharedPreferenceChangeListener(prefListener);

        permissionPromptCard = rootView.findViewById(R.id.card_usage_permission);
        btnGrantUsageAccess = rootView.findViewById(R.id.btn_grant_usage_permission);
        screenTimeContentLayout = rootView.findViewById(R.id.screen_time_content_layout);
        tabLayoutPeriod = rootView.findViewById(R.id.tab_layout_period);
        loadingIndicator = rootView.findViewById(R.id.screen_time_loading);

        tvDeviceUnlocks = rootView.findViewById(R.id.tv_device_unlocks);
        tvPeakUsageHour = rootView.findViewById(R.id.tv_peak_usage_hour);
        tvIdleScreenTime = rootView.findViewById(R.id.tv_idle_screen_time);

        View cardIdleTime = rootView.findViewById(R.id.card_idle_time);
        if (cardIdleTime != null) {
            cardIdleTime.setOnClickListener(v -> showIdleTimeExplanationDialog());
        }

        // Usage Card Views
        btnPrevDay = rootView.findViewById(R.id.btn_prev_day);
        btnNextDay = rootView.findViewById(R.id.btn_next_day);
        tvDailyCardTitle = rootView.findViewById(R.id.tv_daily_card_title);
        tvDailyCardUnlocks = rootView.findViewById(R.id.tv_daily_card_unlocks);
        tvDailyCardTotalTime = rootView.findViewById(R.id.tv_daily_card_total_time);
        tvGraphSectionTitle = rootView.findViewById(R.id.tv_graph_section_title);
        graphDailyHourlyUsage = rootView.findViewById(R.id.graph_daily_hourly_usage);
        graphWeeklyDailyUsage = rootView.findViewById(R.id.graph_weekly_daily_usage);
        layoutDailyTopAppsList = rootView.findViewById(R.id.layout_daily_top_apps_list);
        tvDailyCardNoApps = rootView.findViewById(R.id.tv_daily_card_no_apps);

        if (tabLayoutPeriod != null) {
            tabLayoutPeriod.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
                @Override
                public void onTabSelected(TabLayout.Tab tab) {
                    selectedPeriod = tab.getPosition();
                    updatePeriodView();
                }

                @Override
                public void onTabUnselected(TabLayout.Tab tab) {}

                @Override
                public void onTabReselected(TabLayout.Tab tab) {}
            });
        }

        if (btnPrevDay != null) {
            btnPrevDay.setOnClickListener(v -> {
                if (selectedPeriod == PERIOD_DAILY) {
                    if (currentDayIndex < 6) {
                        currentDayIndex++;
                        updatePeriodView();
                    }
                } else {
                    if (currentWeekIndex < 3) {
                        currentWeekIndex++;
                        updatePeriodView();
                    }
                }
            });
        }

        if (btnNextDay != null) {
            btnNextDay.setOnClickListener(v -> {
                if (selectedPeriod == PERIOD_DAILY) {
                    if (currentDayIndex > 0) {
                        currentDayIndex--;
                        updatePeriodView();
                    }
                } else {
                    if (currentWeekIndex > 0) {
                        currentWeekIndex--;
                        updatePeriodView();
                    }
                }
            });
        }

        if (btnGrantUsageAccess != null) {
            btnGrantUsageAccess.setOnClickListener(v -> UsageStatsHelper.openUsageAccessSettings(context));
        }

        View detailedCard = rootView.findViewById(R.id.card_detailed_phone_usage);
        if (detailedCard != null) {
            detailedCardManager = new DetailedPhoneUsageCardManager(context, detailedCard);
        }

        // Doomscrolling Card Views Initialization
        tvDoomscrollTotalTime = rootView.findViewById(R.id.tv_doomscroll_total_time);
        tvDoomscrollSessionCount = rootView.findViewById(R.id.tv_doomscroll_session_count);
        tvDoomscrollLateNightTime = rootView.findViewById(R.id.tv_doomscroll_late_night_time);
        tvDoomscrollLongestScroll = rootView.findViewById(R.id.tv_doomscroll_longest_scroll);
        tvDoomscrollLongestApp = rootView.findViewById(R.id.tv_doomscroll_longest_app);
        tvDoomscrollRiskChip = rootView.findViewById(R.id.tv_doomscroll_risk_chip);
        cardDoomscrollRiskBadge = rootView.findViewById(R.id.card_doomscroll_risk_badge);
        tvDoomscrollRatioLabel = rootView.findViewById(R.id.tv_doomscroll_ratio_label);
        progressDoomscrollRatio = rootView.findViewById(R.id.progress_doomscroll_ratio);
        layoutDoomscrollAppsList = rootView.findViewById(R.id.layout_doomscroll_apps_list);
        tvDoomscrollNoData = rootView.findViewById(R.id.tv_doomscroll_no_data);
        btnDoomscrollInfo = rootView.findViewById(R.id.btn_doomscroll_info);

        if (btnDoomscrollInfo != null) {
            btnDoomscrollInfo.setOnClickListener(v -> showDoomscrollExplanationDialog());
        }
    }

    public void loadScreenTimeStats() {
        if (destroyed) return;
        cancelPendingLoad();
        boolean hasPermission = UsageStatsHelper.hasUsageAccessPermission(context);
        if (!hasPermission) {
            if (permissionPromptCard != null) permissionPromptCard.setVisibility(View.VISIBLE);
            if (screenTimeContentLayout != null) screenTimeContentLayout.setVisibility(View.GONE);
            setLoading(false);
            return;
        }

        if (permissionPromptCard != null) permissionPromptCard.setVisibility(View.GONE);
        if (screenTimeContentLayout != null) screenTimeContentLayout.setVisibility(View.VISIBLE);

        // Refresh on resume; history is fetched only when the user selects it.
        dailyHistoryList.clear();
        weeklyHistoryList.clear();
        dailyPackageUsage.clear();
        weeklyPackageUsage.clear();
        currentDayIndex = 0;
        currentWeekIndex = 0;
        updatePeriodView();
    }

    private void updatePeriodView() {
        if (destroyed) return;
        cancelPendingLoad();
        if (selectedPeriod == PERIOD_DAILY) {
            if (graphDailyHourlyUsage != null) graphDailyHourlyUsage.setVisibility(View.VISIBLE);
            if (graphWeeklyDailyUsage != null) graphWeeklyDailyUsage.setVisibility(View.GONE);
            if (tvGraphSectionTitle != null) tvGraphSectionTitle.setText("Hourly Usage Distribution (24h)");
            renderSelectedDay();
        } else {
            if (graphDailyHourlyUsage != null) graphDailyHourlyUsage.setVisibility(View.GONE);
            if (graphWeeklyDailyUsage != null) graphWeeklyDailyUsage.setVisibility(View.VISIBLE);
            if (tvGraphSectionTitle != null) tvGraphSectionTitle.setText("Daily Usage Distribution (This Week)");
            renderSelectedWeek();
        }
    }

    private void updateGeneralStats(int unlockCount, String peakHour) {
        if (tvDeviceUnlocks != null) {
            tvDeviceUnlocks.setText(String.valueOf(unlockCount));
        }
        if (tvPeakUsageHour != null) {
            tvPeakUsageHour.setText(peakHour != null ? peakHour : "--");
        }
    }

    private void renderSelectedDay() {
        DailyUsageItem item = dailyHistoryList.get(currentDayIndex);
        if (item == null) {
            loadSelectedUsage();
            return;
        }
        setLoading(false);
        if (currentDayIndex == 0) updateGeneralStats(item.getUnlockCount(), item.getPeakHour());

        if (detailedCardManager != null) {
            Calendar end = Calendar.getInstance();
            end.setTime(item.getDate());
            end.add(Calendar.DAY_OF_YEAR, 1);
            detailedCardManager.updateForUsageData(item.getDate().getTime(),
                    Math.min(end.getTimeInMillis() - 1, System.currentTimeMillis()),
                    dailyPackageUsage.get(currentDayIndex), null);
        }

        // Update button states & opacity
        if (btnPrevDay != null) {
            boolean hasPrev = currentDayIndex < 6;
            btnPrevDay.setEnabled(hasPrev);
            btnPrevDay.setAlpha(hasPrev ? 1.0f : 0.35f);
        }
        if (btnNextDay != null) {
            boolean hasNext = currentDayIndex > 0;
            btnNextDay.setEnabled(hasNext);
            btnNextDay.setAlpha(hasNext ? 1.0f : 0.35f);
        }

        // Title and meta
        if (tvDailyCardTitle != null) {
            tvDailyCardTitle.setText(item.getDayLabel());
        }
        if (tvDailyCardTotalTime != null) {
            tvDailyCardTotalTime.setText(formatDuration(item.getTotalScreenTimeMs()));
        }
        if (tvDailyCardUnlocks != null) {
            String peak = item.getPeakHour() != null && !item.getPeakHour().equals("N/A") && !item.getPeakHour().equals("--")
                    ? " · Peak " + item.getPeakHour() : "";
            tvDailyCardUnlocks.setText(String.format(Locale.getDefault(), "%d unlocks%s", item.getUnlockCount(), peak));
        }
        if (tvIdleScreenTime != null) {
            tvIdleScreenTime.setText(formatDuration(item.getIdleScreenTimeMs()));
        }

        // Graph data
        if (graphDailyHourlyUsage != null) {
            graphDailyHourlyUsage.setHourlyData(item.getHourlyUsageMs());
        }

        // Render Doomscrolling Radar Card
        renderDoomscrollCard(item.getDoomscrollStats(), item.getTotalScreenTimeMs(), false);



        // Top Apps list
        if (layoutDailyTopAppsList != null) {
            layoutDailyTopAppsList.removeAllViews();
            List<UsageStatsHelper.AppUsageInfo> topApps = item.getTopApps();

            if (topApps == null || topApps.isEmpty()) {
                if (tvDailyCardNoApps != null) tvDailyCardNoApps.setVisibility(View.VISIBLE);
            } else {
                if (tvDailyCardNoApps != null) tvDailyCardNoApps.setVisibility(View.GONE);

                int limit = Math.min(5, topApps.size());
                for (int i = 0; i < limit; i++) {
                    UsageStatsHelper.AppUsageInfo app = topApps.get(i);
                    View appRow = View.inflate(context, R.layout.item_time_drain_app, null);

                    ImageView ivIcon = appRow.findViewById(R.id.iv_drain_app_icon);
                    TextView tvName = appRow.findViewById(R.id.tv_drain_app_name);
                    TextView tvDuration = appRow.findViewById(R.id.tv_drain_app_duration);

                    if (ivIcon != null) {
                        if (app.getAppIcon() != null) {
                            ivIcon.setImageDrawable(app.getAppIcon());
                        } else {
                            ivIcon.setImageResource(R.drawable.ic_settings);
                        }
                    }

                    if (tvName != null) {
                        tvName.setText(app.getAppName());
                    }

                    if (tvDuration != null) {
                        tvDuration.setText(formatDuration(app.getTotalTimeInForegroundMs()));
                        tvDuration.setTextColor(context.getColor(R.color.primary));
                    }

                    layoutDailyTopAppsList.addView(appRow);
                }
            }
        }
    }

    private void renderSelectedWeek() {
        WeeklyUsageItem item = weeklyHistoryList.get(currentWeekIndex);
        if (item == null) {
            loadSelectedUsage();
            return;
        }
        setLoading(false);

        if (detailedCardManager != null) {
            detailedCardManager.updateForUsageData(item.getStartDate().getTime(), item.getEndDate().getTime(),
                    weeklyPackageUsage.get(currentWeekIndex), formatDuration(item.getTotalScreenTimeMs()));
        }

        // Navigation button states
        if (btnPrevDay != null) {
            boolean hasPrev = currentWeekIndex < 3;
            btnPrevDay.setEnabled(hasPrev);
            btnPrevDay.setAlpha(hasPrev ? 1.0f : 0.35f);
        }
        if (btnNextDay != null) {
            boolean hasNext = currentWeekIndex > 0;
            btnNextDay.setEnabled(hasNext);
            btnNextDay.setAlpha(hasNext ? 1.0f : 0.35f);
        }

        // Title and meta
        if (tvDailyCardTitle != null) {
            tvDailyCardTitle.setText(item.getWeekLabel());
        }
        if (tvDailyCardTotalTime != null) {
            tvDailyCardTotalTime.setText(formatDuration(item.getTotalScreenTimeMs()) + " (Avg " + formatDuration(item.getDailyAverageScreenTimeMs()) + "/d)");
        }
        if (tvDailyCardUnlocks != null) {
            String peak = item.getPeakDayLabel() != null && !item.getPeakDayLabel().equals("N/A")
                    ? " · Peak " + item.getPeakDayLabel() : "";
            tvDailyCardUnlocks.setText(String.format(Locale.getDefault(), "%d unlocks (Avg %d/d)%s", item.getTotalUnlockCount(), item.getDailyAverageUnlocks(), peak));
        }
        if (tvIdleScreenTime != null) {
            tvIdleScreenTime.setText(formatDuration(item.getIdleScreenTimeMs()));
        }

        // Weekly Graph
        if (graphWeeklyDailyUsage != null) {
            graphWeeklyDailyUsage.setWeeklyData(item.getDailyUsageMs(), item.getDayLabels());
        }

        // Doomscrolling Card
        renderDoomscrollCard(item.getDoomscrollStats(), item.getTotalScreenTimeMs(), true);


        // Top Apps list for the week
        if (layoutDailyTopAppsList != null) {
            layoutDailyTopAppsList.removeAllViews();
            List<UsageStatsHelper.AppUsageInfo> topApps = item.getTopApps();

            if (topApps == null || topApps.isEmpty()) {
                if (tvDailyCardNoApps != null) tvDailyCardNoApps.setVisibility(View.VISIBLE);
            } else {
                if (tvDailyCardNoApps != null) tvDailyCardNoApps.setVisibility(View.GONE);

                int limit = Math.min(5, topApps.size());
                for (int i = 0; i < limit; i++) {
                    UsageStatsHelper.AppUsageInfo app = topApps.get(i);
                    View appRow = View.inflate(context, R.layout.item_time_drain_app, null);

                    ImageView ivIcon = appRow.findViewById(R.id.iv_drain_app_icon);
                    TextView tvName = appRow.findViewById(R.id.tv_drain_app_name);
                    TextView tvDuration = appRow.findViewById(R.id.tv_drain_app_duration);

                    if (ivIcon != null) {
                        if (app.getAppIcon() != null) {
                            ivIcon.setImageDrawable(app.getAppIcon());
                        } else {
                            ivIcon.setImageResource(R.drawable.ic_settings);
                        }
                    }

                    if (tvName != null) {
                        tvName.setText(app.getAppName());
                    }

                    if (tvDuration != null) {
                        tvDuration.setText(formatDuration(app.getTotalTimeInForegroundMs()));
                        tvDuration.setTextColor(context.getColor(R.color.primary));
                    }

                    layoutDailyTopAppsList.addView(appRow);
                }
            }
        }
    }

    private void cancelPendingLoad() {
        loadVersion++;
        if (pendingLoad != null) {
            pendingLoad.cancel(true);
            pendingLoad = null;
        }
        if (detailedCardManager != null) detailedCardManager.cancelPendingLoad();
    }

    private void setLoading(boolean loading) {
        if (loadingIndicator != null) loadingIndicator.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) {
            if (tvDailyCardTitle != null) tvDailyCardTitle.setText("Loading usage…");
            if (btnPrevDay != null) btnPrevDay.setEnabled(false);
            if (btnNextDay != null) btnNextDay.setEnabled(false);
        }
    }

    private void loadSelectedUsage() {
        final int version = loadVersion;
        final int period = selectedPeriod;
        final int index = period == PERIOD_DAILY ? currentDayIndex : currentWeekIndex;
        setLoading(true);
        pendingLoad = app.getBackgroundWriteExecutor().submit(() -> {
            Map<String, Long> packageUsage = new HashMap<>();
            try {
                DailyUsageItem day = period == PERIOD_DAILY ? loadDailyUsage(index, packageUsage) : null;
                WeeklyUsageItem week = period == PERIOD_WEEKLY ? loadWeeklyUsage(index, packageUsage) : null;
                if (Thread.currentThread().isInterrupted()) return;
                mainHandler.post(() -> {
                    if (destroyed || version != loadVersion) return;
                    pendingLoad = null;
                    if (day != null) {
                        dailyHistoryList.put(index, day);
                        dailyPackageUsage.put(index, packageUsage);
                    }
                    if (week != null) {
                        weeklyHistoryList.put(index, week);
                        weeklyPackageUsage.put(index, packageUsage);
                    }
                    updatePeriodView();
                });
            } catch (CancellationException ignored) {
                // A different period was selected or the view was destroyed.
            } catch (RuntimeException error) {
                Log.e("ScreenTimeStats", "Unable to load usage", error);
                mainHandler.post(() -> {
                    if (destroyed || version != loadVersion) return;
                    pendingLoad = null;
                    setLoading(false);
                    if (tvDailyCardTitle != null) tvDailyCardTitle.setText("Unable to load usage");
                    if (btnPrevDay != null) btnPrevDay.setEnabled(index < (period == PERIOD_DAILY ? 6 : 3));
                    if (btnNextDay != null) btnNextDay.setEnabled(index > 0);
                });
            }
        });
    }

    public void destroy() {
        destroyed = true;
        cancelPendingLoad();
        context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
                .unregisterOnSharedPreferenceChangeListener(prefListener);
        if (detailedCardManager != null) detailedCardManager.destroy();
    }

    private DailyUsageItem loadDailyUsage(int dayOffset, Map<String, Long> appUsageMap) {
        SimpleDateFormat dayFormat = new SimpleDateFormat("EEE, MMM d", Locale.getDefault());

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -dayOffset);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long dayStartMs = cal.getTimeInMillis();

        long dayEndMs;
        if (dayOffset == 0) {
            dayEndMs = System.currentTimeMillis();
        } else {
            cal.set(Calendar.HOUR_OF_DAY, 23);
            cal.set(Calendar.MINUTE, 59);
            cal.set(Calendar.SECOND, 59);
            cal.set(Calendar.MILLISECOND, 999);
            dayEndMs = cal.getTimeInMillis();
        }

        String label;
        if (dayOffset == 0) {
            label = "Today, " + dayFormat.format(new Date(dayStartMs));
        } else if (dayOffset == 1) {
            label = "Yesterday, " + dayFormat.format(new Date(dayStartMs));
        } else {
            label = dayFormat.format(new Date(dayStartMs));
        }

        appUsageMap.putAll(UsageStatsHelper.getUsageTimePerPackage(context, dayStartMs, dayEndMs));
        long totalScreenTimeMs = 0;
        for (long duration : appUsageMap.values()) totalScreenTimeMs += duration;
        int unlockCount = UsageStatsHelper.getDeviceUnlockCount(context, dayStartMs, dayEndMs);
        long[] hourlyBuckets = UsageStatsHelper.getHourlyUsageBuckets(context, dayStartMs, dayEndMs);
        String peakHour = UsageStatsHelper.getPeakUsageHour(hourlyBuckets);
        long idleScreenTimeMs = UsageStatsHelper.getIdleHomeScreenTimeMs(context, appUsageMap);

        List<UsageStatsHelper.AppUsageInfo> topApps = UsageStatsHelper.getSortedAppUsageList(context, appUsageMap);
        UsageStatsHelper.DoomscrollStats doomscrollStats = UsageStatsHelper.getDoomscrollStats(context, dayStartMs, dayEndMs);

        return new DailyUsageItem(
                label,
                new Date(dayStartMs),
                totalScreenTimeMs,
                unlockCount,
                hourlyBuckets,
                topApps,
                peakHour,
                idleScreenTimeMs,
                doomscrollStats
        );
    }

    private WeeklyUsageItem loadWeeklyUsage(int weekOffset, Map<String, Long> appUsageMap) {
        SimpleDateFormat monthDayFormat = new SimpleDateFormat("MMM d", Locale.getDefault());
        SimpleDateFormat dayAbbrevFormat = new SimpleDateFormat("EEE", Locale.getDefault());

        int systemFirstDay = Calendar.getInstance().getFirstDayOfWeek();
        int defaultFirstDay = (systemFirstDay == Calendar.SUNDAY) ? Calendar.SUNDAY : Calendar.MONDAY;
        int firstDayOfWeek = context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
                .getInt("week_start_day", defaultFirstDay);

        Calendar cal = Calendar.getInstance();
        cal.setFirstDayOfWeek(firstDayOfWeek);

        int currentDayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
        int delta = currentDayOfWeek - firstDayOfWeek;
        if (delta < 0) delta += 7;
        cal.add(Calendar.DAY_OF_YEAR, -delta);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        cal.add(Calendar.WEEK_OF_YEAR, -weekOffset);
        long weekStartMs = cal.getTimeInMillis();
        Date startDate = new Date(weekStartMs);

        Calendar endCal = (Calendar) cal.clone();
        endCal.add(Calendar.DAY_OF_YEAR, 6);
        endCal.set(Calendar.HOUR_OF_DAY, 23);
        endCal.set(Calendar.MINUTE, 59);
        endCal.set(Calendar.SECOND, 59);
        endCal.set(Calendar.MILLISECOND, 999);
        long weekEndMs = Math.min(endCal.getTimeInMillis(), System.currentTimeMillis());
        Date endDate = new Date(weekEndMs);

        String label;
        if (weekOffset == 0) {
            label = "This Week (" + monthDayFormat.format(startDate) + " – " + monthDayFormat.format(endDate) + ")";
        } else if (weekOffset == 1) {
            label = "Last Week (" + monthDayFormat.format(startDate) + " – " + monthDayFormat.format(endDate) + ")";
        } else {
            label = monthDayFormat.format(startDate) + " – " + monthDayFormat.format(endDate);
        }

        appUsageMap.putAll(UsageStatsHelper.getUsageTimePerPackage(context, weekStartMs, weekEndMs));
        long totalScreenTimeMs = 0;
        for (long duration : appUsageMap.values()) totalScreenTimeMs += duration;
        int totalUnlocks = UsageStatsHelper.getDeviceUnlockCount(context, weekStartMs, weekEndMs);
        long totalIdleTimeMs = UsageStatsHelper.getIdleHomeScreenTimeMs(context, appUsageMap);

        long[] dailyBuckets = new long[7];
        String[] dayLabels = new String[7];

        long maxDayTime = 0;
        String peakDayLabel = "N/A";

        int daysElapsed = 0;
        long now = System.currentTimeMillis();

        for (int i = 0; i < 7; i++) {
            Calendar dayCal = (Calendar) cal.clone();
            dayCal.add(Calendar.DAY_OF_YEAR, i);
            long dStart = dayCal.getTimeInMillis();

            dayLabels[i] = dayAbbrevFormat.format(dayCal.getTime());

            if (dStart <= now) {
                daysElapsed++;
                Calendar dayEndCal = (Calendar) dayCal.clone();
                dayEndCal.set(Calendar.HOUR_OF_DAY, 23);
                dayEndCal.set(Calendar.MINUTE, 59);
                dayEndCal.set(Calendar.SECOND, 59);
                dayEndCal.set(Calendar.MILLISECOND, 999);
                long dEnd = Math.min(dayEndCal.getTimeInMillis(), now);

                long dayUsage = UsageStatsHelper.getTotalScreenTimeMs(context, dStart, dEnd);
                dailyBuckets[i] = dayUsage;

                if (dayUsage > maxDayTime) {
                    maxDayTime = dayUsage;
                    peakDayLabel = dayLabels[i] + " (" + formatDuration(dayUsage) + ")";
                }
            } else {
                dailyBuckets[i] = 0;
            }
        }

        daysElapsed = Math.max(1, daysElapsed);
        long dailyAverageMs = totalScreenTimeMs / daysElapsed;
        int dailyAverageUnlocks = (int) Math.round((double) totalUnlocks / daysElapsed);

        List<UsageStatsHelper.AppUsageInfo> topApps = UsageStatsHelper.getSortedAppUsageList(context, appUsageMap);
        UsageStatsHelper.DoomscrollStats doomscrollStats = UsageStatsHelper.getDoomscrollStats(context, weekStartMs, weekEndMs);

        return new WeeklyUsageItem(
                label,
                startDate,
                endDate,
                totalScreenTimeMs,
                dailyAverageMs,
                totalUnlocks,
                dailyAverageUnlocks,
                totalIdleTimeMs,
                dailyBuckets,
                dayLabels,
                peakDayLabel,
                topApps,
                doomscrollStats
        );
    }

    private List<InsightCardData> generateInsights(long digitalTimeMs) {
        List<InsightCardData> list = new ArrayList<>();

        if (digitalTimeMs > 4 * 60 * 60 * 1000) {
            list.add(new InsightCardData(
                    "Digital Rest Needed",
                    String.format(Locale.getDefault(), "Over %s of screen time logged today. Consider a screen-free walk.", formatDuration(digitalTimeMs)),
                    "alert"
            ));
        }

        if (dailyHistoryList != null && !dailyHistoryList.isEmpty()) {
            DailyUsageItem today = dailyHistoryList.get(0);
            if (today != null && today.getDoomscrollStats() != null) {
                UsageStatsHelper.DoomscrollStats ds = today.getDoomscrollStats();
                if (ds.getTotalDoomscrollTimeMs() >= 30 * 60 * 1000L) {
                    list.add(new InsightCardData(
                            "High Feed Scroll",
                            String.format(Locale.getDefault(), "%s spent doomscrolling feed apps today (%d sessions).", formatDuration(ds.getTotalDoomscrollTimeMs()), ds.getSessionCount()),
                            "alert"
                    ));
                }
            }
        }

        if (list.isEmpty()) {
            list.add(new InsightCardData(
                    "Data Syncing",
                    "As you use your phone throughout the day, contextual insights will appear here.",
                    "trend"
            ));
        }

        return list;
    }

    private List<InsightCardData> generateWeeklyInsights(WeeklyUsageItem item) {
        List<InsightCardData> list = new ArrayList<>();
        if (item == null) return list;

        if (item.getDailyAverageScreenTimeMs() > 4 * 60 * 60 * 1000L) {
            list.add(new InsightCardData(
                    "High Weekly Average",
                    String.format(Locale.getDefault(), "Averaging %s/day screen time this week. Try adding screen-free breaks.", formatDuration(item.getDailyAverageScreenTimeMs())),
                    "alert"
            ));
        } else {
            list.add(new InsightCardData(
                    "Balanced Screen Average",
                    String.format(Locale.getDefault(), "Averaging %s/day screen time this week across %d daily unlocks.", formatDuration(item.getDailyAverageScreenTimeMs()), item.getDailyAverageUnlocks()),
                    "trend"
            ));
        }

        if (item.getPeakDayLabel() != null && !item.getPeakDayLabel().equals("N/A")) {
            list.add(new InsightCardData(
                    "Peak Day",
                    "Highest screen time this week was on " + item.getPeakDayLabel() + ".",
                    "trend"
            ));
        }

        if (item.getDoomscrollStats() != null && item.getDoomscrollStats().getTotalDoomscrollTimeMs() >= 60 * 60 * 1000L) {
            list.add(new InsightCardData(
                    "Weekly Feed Time",
                    String.format(Locale.getDefault(), "%s total feed scrolling logged this week across %d sessions.", formatDuration(item.getDoomscrollStats().getTotalDoomscrollTimeMs()), item.getDoomscrollStats().getSessionCount()),
                    "alert"
            ));
        }

        if (list.isEmpty()) {
            list.add(new InsightCardData(
                    "Weekly Overview",
                    "Weekly usage trends and insights will update as you use your device.",
                    "trend"
            ));
        }

        return list;
    }

    private void renderDoomscrollCard(UsageStatsHelper.DoomscrollStats stats, long totalScreenTimeMs, boolean isWeekly) {
        if (stats == null) return;

        if (tvDoomscrollTotalTime != null) {
            tvDoomscrollTotalTime.setText(formatDuration(stats.getTotalDoomscrollTimeMs()));
        }

        if (tvDoomscrollSessionCount != null) {
            int count = stats.getSessionCount();
            tvDoomscrollSessionCount.setText(count == 1 ? "1 session" : count + " sessions");
        }

        if (tvDoomscrollLateNightTime != null) {
            tvDoomscrollLateNightTime.setText(formatDuration(stats.getLateNightDoomscrollTimeMs()));
        }

        if (tvDoomscrollLongestScroll != null) {
            tvDoomscrollLongestScroll.setText(formatDuration(stats.getLongestSessionMs()));
        }

        if (tvDoomscrollLongestApp != null) {
            String app = stats.getLongestSessionAppName();
            tvDoomscrollLongestApp.setText(app != null && !app.equals("N/A") ? "on " + app : "None");
        }

        String riskLevel = stats.getRiskLevel();
        if (tvDoomscrollRiskChip != null) {
            tvDoomscrollRiskChip.setText(stats.getRiskLabel());
        }

        if (cardDoomscrollRiskBadge != null) {
            int badgeColor;
            switch (riskLevel) {
                case "MODERATE":
                    badgeColor = context.getColor(R.color.orange);
                    break;
                case "HIGH":
                case "SEVERE":
                    badgeColor = context.getColor(R.color.red);
                    break;
                case "LOW":
                default:
                    badgeColor = context.getColor(R.color.green);
                    break;
            }
            cardDoomscrollRiskBadge.setCardBackgroundColor(badgeColor);
        }

        int percentage = totalScreenTimeMs > 0
                ? (int) Math.min(100, Math.round((double) stats.getTotalDoomscrollTimeMs() / totalScreenTimeMs * 100))
                : 0;

        if (tvDoomscrollRatioLabel != null) {
            String periodText = isWeekly ? "this week" : "today";
            if (stats.getTotalDoomscrollTimeMs() > 0) {
                tvDoomscrollRatioLabel.setText(String.format(Locale.getDefault(), "Doomscrolling made up %d%% of your total screen time %s.", percentage, periodText));
            } else {
                tvDoomscrollRatioLabel.setText("No continuous doomscrolling detected " + periodText + ".");
            }
        }

        if (progressDoomscrollRatio != null) {
            progressDoomscrollRatio.setProgress(percentage);
            int progressColor = percentage > 50 ? context.getColor(R.color.red) :
                    (percentage > 25 ? context.getColor(R.color.orange) : context.getColor(R.color.green));
            progressDoomscrollRatio.setIndicatorColor(progressColor);
        }

        if (layoutDoomscrollAppsList != null) {
            layoutDoomscrollAppsList.removeAllViews();
            List<UsageStatsHelper.DoomscrollAppItem> drainers = stats.getTopDoomscrollApps();

            if (drainers == null || drainers.isEmpty() || stats.getTotalDoomscrollTimeMs() <= 0) {
                if (tvDoomscrollNoData != null) tvDoomscrollNoData.setVisibility(View.VISIBLE);
            } else {
                if (tvDoomscrollNoData != null) tvDoomscrollNoData.setVisibility(View.GONE);

                int limit = Math.min(3, drainers.size());
                for (int i = 0; i < limit; i++) {
                    UsageStatsHelper.DoomscrollAppItem app = drainers.get(i);
                    View appRow = View.inflate(context, R.layout.item_time_drain_app, null);

                    ImageView ivIcon = appRow.findViewById(R.id.iv_drain_app_icon);
                    TextView tvName = appRow.findViewById(R.id.tv_drain_app_name);
                    TextView tvDuration = appRow.findViewById(R.id.tv_drain_app_duration);

                    if (ivIcon != null) {
                        if (app.getAppIcon() != null) {
                            ivIcon.setImageDrawable(app.getAppIcon());
                        } else {
                            ivIcon.setImageResource(R.drawable.ic_settings);
                        }
                    }

                    if (tvName != null) {
                        tvName.setText(app.getAppName());
                    }

                    if (tvDuration != null) {
                        String scrollText = formatDuration(app.getTotalDoomscrollTimeMs());
                        tvDuration.setText(scrollText);
                        tvDuration.setTextColor(context.getColor(R.color.orange));
                    }

                    layoutDoomscrollAppsList.addView(appRow);
                }
            }
        }
    }

    private void showDoomscrollExplanationDialog() {
        if (context == null) return;
        new MaterialAlertDialogBuilder(context)
                .setTitle("What is Doomscrolling?")
                .setMessage("Doomscrolling is continuous, prolonged scrolling through social media, news feeds, or video streams.\n\n" +
                        "• Continuous Sessions: Tracks periods where you used feed-based apps (Instagram, TikTok, Reddit, Twitter/X, News) continuously for 15+ minutes without a break.\n\n" +
                        "• Late-Night Scroll: Highlights scrolling that occurred late at night (10 PM – 6 AM), which can disrupt sleep cycles.\n\n" +
                        "• Risk Levels: Categorized as Mindful (<15m), Moderate (15–45m), High Risk (45–90m), or Severe (>90m).")
                .setPositiveButton("Got it", (dialog, which) -> dialog.dismiss())
                .show();
    }


    private void showIdleTimeExplanationDialog() {
        if (context == null) return;
        new MaterialAlertDialogBuilder(context)
                .setTitle("Home / Idle Screen Time")
                .setMessage("This tracks the time your phone screen was on or unlocked—such as when looking at the home screen or lock screen—without actively using any app.")
                .setPositiveButton("Got it", (dialog, which) -> dialog.dismiss())
                .show();
    }

    public static String formatDuration(long durationMs) {
        if (durationMs <= 0) return "0m";
        long minutes = (durationMs / (1000 * 60)) % 60;
        long hours = durationMs / (1000 * 60 * 60);
        if (hours > 0) {
            return String.format(Locale.getDefault(), "%dh %02dm", hours, minutes);
        } else {
            return String.format(Locale.getDefault(), "%dm", Math.max(1, minutes));
        }
    }
}
