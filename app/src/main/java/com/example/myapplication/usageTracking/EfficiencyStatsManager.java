package com.example.myapplication.usageTracking;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.LifeTrackerApp;
import com.example.myapplication.R;
import com.example.myapplication.database.ActivityDao;
import com.example.myapplication.database.ActivityDatabase;
import com.example.myapplication.database.MovementActivity;
import com.example.myapplication.database.StillLocation;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EfficiencyStatsManager {

    private final Context context;
    private final Handler mainHandler;
    private final View rootView;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    // UI elements
    private View permissionPromptCard;
    private Button btnGrantUsageAccess;
    private View efficiencyContentLayout;

    // Hero Graphic
    private TextView tvActiveRealWorldTime;
    private TextView tvDigitalScreenTime;
    private TextView tvBalanceSummary;
    private ProgressBar balanceProgressBar;

    // General Digital Activity
    private TextView tvDeviceUnlocks;
    private TextView tvPeakUsageHour;

    // Contextual Insights
    private RecyclerView rvInsights;

    // Location-Usage Breakdown
    private RecyclerView rvLocationUsage;
    private TextView tvNoLocationUsage;

    // Time-Drain Indicators
    private LinearLayout layoutTimeDrainContainer;
    private TextView tvNoTimeDrains;

    // Daily Usage Card Views (Interactive Day Switcher + Graph + Top Apps)
    private android.widget.ImageButton btnPrevDay;
    private android.widget.ImageButton btnNextDay;
    private TextView tvDailyCardTitle;
    private TextView tvDailyCardUnlocks;
    private TextView tvDailyCardTotalTime;
    private HourlyUsageGraphView graphDailyHourlyUsage;
    private LinearLayout layoutDailyTopAppsList;
    private TextView tvDailyCardNoApps;

    private List<DailyUsageItem> dailyHistoryList = new ArrayList<>();
    private int currentDayIndex = 0; // 0 = Today, 1 = Yesterday, etc.

    public EfficiencyStatsManager(Context context, Handler mainHandler, View rootView) {
        this.context = context;
        this.mainHandler = mainHandler;
        this.rootView = rootView;

        initViews();
    }

    private void initViews() {
        permissionPromptCard = rootView.findViewById(R.id.card_usage_permission);
        btnGrantUsageAccess = rootView.findViewById(R.id.btn_grant_usage_permission);
        efficiencyContentLayout = rootView.findViewById(R.id.efficiency_content_layout);

        tvActiveRealWorldTime = rootView.findViewById(R.id.tv_active_real_world_time);
        tvDigitalScreenTime = rootView.findViewById(R.id.tv_digital_screen_time);
        tvBalanceSummary = rootView.findViewById(R.id.tv_balance_summary);
        balanceProgressBar = rootView.findViewById(R.id.progress_balance_ratio);

        tvDeviceUnlocks = rootView.findViewById(R.id.tv_device_unlocks);
        tvPeakUsageHour = rootView.findViewById(R.id.tv_peak_usage_hour);

        // Daily Usage Card Views
        btnPrevDay = rootView.findViewById(R.id.btn_prev_day);
        btnNextDay = rootView.findViewById(R.id.btn_next_day);
        tvDailyCardTitle = rootView.findViewById(R.id.tv_daily_card_title);
        tvDailyCardUnlocks = rootView.findViewById(R.id.tv_daily_card_unlocks);
        tvDailyCardTotalTime = rootView.findViewById(R.id.tv_daily_card_total_time);
        graphDailyHourlyUsage = rootView.findViewById(R.id.graph_daily_hourly_usage);
        layoutDailyTopAppsList = rootView.findViewById(R.id.layout_daily_top_apps_list);
        tvDailyCardNoApps = rootView.findViewById(R.id.tv_daily_card_no_apps);

        if (btnPrevDay != null) {
            btnPrevDay.setOnClickListener(v -> {
                if (currentDayIndex < dailyHistoryList.size() - 1) {
                    currentDayIndex++;
                    renderSelectedDay();
                }
            });
        }

        if (btnNextDay != null) {
            btnNextDay.setOnClickListener(v -> {
                if (currentDayIndex > 0) {
                    currentDayIndex--;
                    renderSelectedDay();
                }
            });
        }

        rvInsights = rootView.findViewById(R.id.rv_insight_cards);
        if (rvInsights != null) {
            rvInsights.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
        }

        rvLocationUsage = rootView.findViewById(R.id.rv_location_usage_ranking);
        if (rvLocationUsage != null) {
            rvLocationUsage.setLayoutManager(new LinearLayoutManager(context));
            rvLocationUsage.setNestedScrollingEnabled(false);
        }
        tvNoLocationUsage = rootView.findViewById(R.id.tv_no_location_usage);

        layoutTimeDrainContainer = rootView.findViewById(R.id.layout_time_drain_items);
        tvNoTimeDrains = rootView.findViewById(R.id.tv_no_time_drains);

        if (btnGrantUsageAccess != null) {
            btnGrantUsageAccess.setOnClickListener(v -> UsageStatsHelper.openUsageAccessSettings(context));
        }
    }

    public void loadEfficiencyStats() {
        boolean hasPermission = UsageStatsHelper.hasUsageAccessPermission(context);
        if (!hasPermission) {
            if (permissionPromptCard != null) permissionPromptCard.setVisibility(View.VISIBLE);
            if (efficiencyContentLayout != null) efficiencyContentLayout.setVisibility(View.GONE);
            return;
        }

        if (permissionPromptCard != null) permissionPromptCard.setVisibility(View.GONE);
        if (efficiencyContentLayout != null) efficiencyContentLayout.setVisibility(View.VISIBLE);

        executorService.execute(() -> {
            ActivityDatabase db = ActivityDatabase.getDatabase(context);
            ActivityDao activityDao = db.activityDao();

            // Calculate start and end of today
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            Date startOfDay = cal.getTime();
            Date now = new Date();

            long startOfDayMs = startOfDay.getTime();
            long nowMs = now.getTime();

            // 1. Digital Screen Time Today & General Stats
            long totalScreenTimeMs = UsageStatsHelper.getTotalScreenTimeMs(context, startOfDayMs, nowMs);
            int unlockCount = UsageStatsHelper.getDeviceUnlockCount(context, startOfDayMs, nowMs);
            String peakHour = UsageStatsHelper.getPeakUsageHour(context, startOfDayMs, nowMs);

            // 2. Physical Movements & Stays
            List<MovementActivity> movements = activityDao.getMovementsFromRange(startOfDay, now);
            List<StillLocation> stills = activityDao.getStillsFromRange(startOfDay, now);

            long totalMovementDurationMs = 0;
            for (MovementActivity m : movements) {
                Date start = m.getStartTimeDate();
                Date end = m.getEndTimeDate() != null ? m.getEndTimeDate() : now;
                if (start != null) {
                    long s = Math.max(start.getTime(), startOfDayMs);
                    long e = Math.min(end.getTime(), nowMs);
                    if (e > s) {
                        totalMovementDurationMs += (e - s);
                    }
                }
            }

            // Identify home place ID if saved
            SharedPreferences prefs = context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
            long homePlaceId = prefs.getLong("home_place_id", -1);

            // Group stays by place name / place ID
            Map<String, PlaceStayAggregate> placeAggregates = new HashMap<>();
            long nonHomeStayDurationMs = 0;

            for (StillLocation s : stills) {
                Date start = s.getStartTimeDate();
                Date end = s.getEndTimeDate() != null ? s.getEndTimeDate() : now;
                if (start == null) continue;

                long sMs = Math.max(start.getTime(), startOfDayMs);
                long eMs = Math.min(end.getTime(), nowMs);
                if (eMs <= sMs) continue;

                long stayDuration = eMs - sMs;

                boolean isHome = (s.getPlaceId() != null && s.getPlaceId() == homePlaceId)
                        || "Home".equalsIgnoreCase(s.getCategory())
                        || "Home".equalsIgnoreCase(s.getPlaceName());

                if (!isHome) {
                    nonHomeStayDurationMs += stayDuration;
                }

                String placeKey = s.getPlaceName() != null && !s.getPlaceName().trim().isEmpty()
                        ? s.getPlaceName() : (s.getCategory() != null ? s.getCategory() : "Unknown Place");

                PlaceStayAggregate agg = placeAggregates.get(placeKey);
                if (agg == null) {
                    agg = new PlaceStayAggregate(placeKey, s.getCategory(), s.getIcon(), s.getColor());
                    placeAggregates.put(placeKey, agg);
                }
                agg.totalStayDurationMs += stayDuration;
                // Query digital usage during this specific stay
                Map<String, Long> appUsageDuringStay = UsageStatsHelper.getForegroundTimePerPackage(context, sMs, eMs);
                for (Map.Entry<String, Long> entry : appUsageDuringStay.entrySet()) {
                    Long prev = agg.appUsageMap.get(entry.getKey());
                    agg.appUsageMap.put(entry.getKey(), (prev != null ? prev : 0L) + entry.getValue());
                    agg.totalScreenTimeMs += entry.getValue();
                }
            }

            // "Active Real-World Time" = Movement time + Stays away from home
            long activeRealWorldTimeMs = totalMovementDurationMs + nonHomeStayDurationMs;

            // Build Ranked Location-Usage list
            List<LocationUsageStats> locationUsageList = new ArrayList<>();
            for (PlaceStayAggregate agg : placeAggregates.values()) {
                List<UsageStatsHelper.AppUsageInfo> sortedApps = UsageStatsHelper.getSortedAppUsageList(context, agg.appUsageMap);
                locationUsageList.add(new LocationUsageStats(
                        agg.placeName,
                        agg.category,
                        agg.icon,
                        agg.color,
                        agg.totalStayDurationMs,
                        agg.totalScreenTimeMs,
                        sortedApps
                ));
            }
            locationUsageList.sort((a, b) -> Long.compare(b.getTotalScreenTimeMs(), a.getTotalScreenTimeMs()));

            // Identify Time Drains in Focus Locations (Work, Library, School, Study)
            List<UsageStatsHelper.AppUsageInfo> focusTimeDrains = new ArrayList<>();
            Map<String, Long> focusAppUsageTotal = new HashMap<>();
            for (PlaceStayAggregate agg : placeAggregates.values()) {
                if (isFocusCategory(agg.category, agg.placeName)) {
                    for (Map.Entry<String, Long> entry : agg.appUsageMap.entrySet()) {
                        Long prev = focusAppUsageTotal.get(entry.getKey());
                        focusAppUsageTotal.put(entry.getKey(), (prev != null ? prev : 0L) + entry.getValue());
                    }
                }
            }
            if (!focusAppUsageTotal.isEmpty()) {
                focusTimeDrains = UsageStatsHelper.getSortedAppUsageList(context, focusAppUsageTotal);
            }

            // Generate Contextual Insight Cards
            List<InsightCardData> insights = generateInsights(activeRealWorldTimeMs, totalScreenTimeMs, locationUsageList, focusTimeDrains);

            // Build Daily Breakdown History (Last 7 Days) with throughout-the-day graphs & top apps
            List<DailyUsageItem> dailyHistory = loadDailyUsageHistory();

            final long finalActiveRealWorldTime = activeRealWorldTimeMs;
            final long finalDigitalScreenTime = totalScreenTimeMs;
            final int finalUnlockCount = unlockCount;
            final String finalPeakHour = peakHour;
            final List<LocationUsageStats> finalList = locationUsageList;
            final List<UsageStatsHelper.AppUsageInfo> finalFocusDrains = focusTimeDrains;
            final List<InsightCardData> finalInsights = insights;
            final List<DailyUsageItem> finalDailyHistory = dailyHistory;

            mainHandler.post(() -> {
                if (rootView == null) return;
                updateHeroGraphic(finalActiveRealWorldTime, finalDigitalScreenTime);
                updateGeneralStats(finalUnlockCount, finalPeakHour);
                updateDailyUsageHistory(finalDailyHistory);
                updateInsights(finalInsights);
                updateLocationRanking(finalList);
                updateTimeDrains(finalFocusDrains);
            });
        });
    }

    private boolean isFocusCategory(String category, String placeName) {
        String cat = (category != null ? category : "").toLowerCase(Locale.ROOT);
        String name = (placeName != null ? placeName : "").toLowerCase(Locale.ROOT);
        return cat.contains("work") || cat.contains("office") || cat.contains("school") ||
                cat.contains("library") || cat.contains("study") ||
                name.contains("work") || name.contains("office") || name.contains("school") ||
                name.contains("library");
    }

    private void updateHeroGraphic(long activeRealWorldMs, long digitalScreenMs) {
        if (tvActiveRealWorldTime != null) {
            tvActiveRealWorldTime.setText(formatDuration(activeRealWorldMs));
        }
        if (tvDigitalScreenTime != null) {
            tvDigitalScreenTime.setText(formatDuration(digitalScreenMs));
        }

        long total = activeRealWorldMs + digitalScreenMs;
        int activePercentage = total > 0 ? (int) Math.round((double) activeRealWorldMs / total * 100) : 50;

        if (balanceProgressBar != null) {
            balanceProgressBar.setProgress(Math.min(100, Math.max(0, activePercentage)));
        }

        if (tvBalanceSummary != null) {
            if (activeRealWorldMs > digitalScreenMs) {
                long diff = activeRealWorldMs - digitalScreenMs;
                tvBalanceSummary.setText(String.format(Locale.getDefault(), "🌟 Real-world active time leads by %s today!", formatDuration(diff)));
            } else if (digitalScreenMs > activeRealWorldMs) {
                long diff = digitalScreenMs - activeRealWorldMs;
                tvBalanceSummary.setText(String.format(Locale.getDefault(), "📱 Screen time exceeds active time by %s today.", formatDuration(diff)));
            } else {
                tvBalanceSummary.setText("⚖️ Perfect balance between active and digital time today!");
            }
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

    private void updateDailyUsageHistory(List<DailyUsageItem> items) {
        this.dailyHistoryList = items != null ? items : new ArrayList<>();
        this.currentDayIndex = 0; // Default to Today
        renderSelectedDay();
    }

    private void renderSelectedDay() {
        if (dailyHistoryList == null || dailyHistoryList.isEmpty()) {
            if (tvDailyCardNoApps != null) tvDailyCardNoApps.setVisibility(View.VISIBLE);
            return;
        }

        if (currentDayIndex < 0) currentDayIndex = 0;
        if (currentDayIndex >= dailyHistoryList.size()) currentDayIndex = dailyHistoryList.size() - 1;

        DailyUsageItem item = dailyHistoryList.get(currentDayIndex);

        // Update button states & opacity for crisp UX
        if (btnPrevDay != null) {
            boolean hasPrev = currentDayIndex < dailyHistoryList.size() - 1;
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

        // Graph data
        if (graphDailyHourlyUsage != null) {
            graphDailyHourlyUsage.setHourlyData(item.getHourlyUsageMs());
        }

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

    private List<DailyUsageItem> loadDailyUsageHistory() {
        List<DailyUsageItem> history = new ArrayList<>();
        java.text.SimpleDateFormat dayFormat = new java.text.SimpleDateFormat("EEE, MMM d", Locale.getDefault());

        // Process last 7 days starting from today down to 6 days ago
        for (int dayOffset = 0; dayOffset < 7; dayOffset++) {
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

            if (dayEndMs <= dayStartMs) continue;

            String label;
            if (dayOffset == 0) {
                label = "Today, " + dayFormat.format(new Date(dayStartMs));
            } else if (dayOffset == 1) {
                label = "Yesterday, " + dayFormat.format(new Date(dayStartMs));
            } else {
                label = dayFormat.format(new Date(dayStartMs));
            }

            long totalScreenTimeMs = UsageStatsHelper.getTotalScreenTimeMs(context, dayStartMs, dayEndMs);
            int unlockCount = UsageStatsHelper.getDeviceUnlockCount(context, dayStartMs, dayEndMs);
            long[] hourlyBuckets = UsageStatsHelper.getHourlyUsageBuckets(context, dayStartMs, dayEndMs);
            String peakHour = UsageStatsHelper.getPeakUsageHour(context, dayStartMs, dayEndMs);

            Map<String, Long> appUsageMap = UsageStatsHelper.getForegroundTimePerPackage(context, dayStartMs, dayEndMs);
            List<UsageStatsHelper.AppUsageInfo> topApps = UsageStatsHelper.getSortedAppUsageList(context, appUsageMap);

            history.add(new DailyUsageItem(
                    label,
                    new Date(dayStartMs),
                    totalScreenTimeMs,
                    unlockCount,
                    hourlyBuckets,
                    topApps,
                    peakHour
            ));
        }
        return history;
    }

    private List<InsightCardData> generateInsights(long activeTimeMs, long digitalTimeMs,
                                                   List<LocationUsageStats> locationUsageList,
                                                   List<UsageStatsHelper.AppUsageInfo> focusDrains) {
        List<InsightCardData> list = new ArrayList<>();

        if (!focusDrains.isEmpty()) {
            UsageStatsHelper.AppUsageInfo topDrain = focusDrains.get(0);
            list.add(new InsightCardData(
                    "Focus Area Alert",
                    String.format(Locale.getDefault(), "%s used %s during designated focus hours.",
                            topDrain.getAppName(), formatDuration(topDrain.getTotalTimeInForegroundMs())),
                    "alert"
            ));
        }

        if (!locationUsageList.isEmpty()) {
            LocationUsageStats topPlace = locationUsageList.get(0);
            if (topPlace.getTotalScreenTimeMs() > 0) {
                list.add(new InsightCardData(
                        "Highest Phone Usage",
                        String.format(Locale.getDefault(), "You spent %s on your phone while at %s (%d%% of your stay).",
                                formatDuration(topPlace.getTotalScreenTimeMs()), topPlace.getLocationName(), topPlace.getUsagePercentage()),
                        "trend"
                ));
            }
        }

        if (activeTimeMs > digitalTimeMs && activeTimeMs > 60 * 60 * 1000) {
            list.add(new InsightCardData(
                    "Daily Efficiency Win",
                    "You spent more time moving and exploring than on your screen today! Keep up the momentum.",
                    "win"
            ));
        } else if (digitalTimeMs > 4 * 60 * 60 * 1000) {
            list.add(new InsightCardData(
                    "Digital Rest Needed",
                    String.format(Locale.getDefault(), "Over %s of screen time logged today. Consider a screen-free walk.", formatDuration(digitalTimeMs)),
                    "alert"
            ));
        }

        if (list.isEmpty()) {
            list.add(new InsightCardData(
                    "Data Syncing",
                    "As you visit places and use your phone throughout the day, contextual insights will appear here.",
                    "trend"
            ));
        }

        return list;
    }

    private void updateInsights(List<InsightCardData> insights) {
        if (rvInsights != null) {
            rvInsights.setAdapter(new InsightCardAdapter(insights));
        }
    }

    private void updateLocationRanking(List<LocationUsageStats> list) {
        if (rvLocationUsage != null) {
            if (list.isEmpty()) {
                rvLocationUsage.setVisibility(View.GONE);
                if (tvNoLocationUsage != null) tvNoLocationUsage.setVisibility(View.VISIBLE);
            } else {
                rvLocationUsage.setVisibility(View.VISIBLE);
                if (tvNoLocationUsage != null) tvNoLocationUsage.setVisibility(View.GONE);
                rvLocationUsage.setAdapter(new LocationUsageAdapter(context, list));
            }
        }
    }

    private void updateTimeDrains(List<UsageStatsHelper.AppUsageInfo> focusDrains) {
        if (layoutTimeDrainContainer == null) return;
        layoutTimeDrainContainer.removeAllViews();

        if (focusDrains == null || focusDrains.isEmpty()) {
            if (tvNoTimeDrains != null) tvNoTimeDrains.setVisibility(View.VISIBLE);
            return;
        }

        if (tvNoTimeDrains != null) tvNoTimeDrains.setVisibility(View.GONE);

        int maxItems = Math.min(3, focusDrains.size());
        for (int i = 0; i < maxItems; i++) {
            UsageStatsHelper.AppUsageInfo info = focusDrains.get(i);
            View itemView = View.inflate(context, R.layout.item_time_drain_app, null);

            ImageView ivIcon = itemView.findViewById(R.id.iv_drain_app_icon);
            TextView tvName = itemView.findViewById(R.id.tv_drain_app_name);
            TextView tvDuration = itemView.findViewById(R.id.tv_drain_app_duration);

            if (ivIcon != null && info.getAppIcon() != null) {
                ivIcon.setImageDrawable(info.getAppIcon());
            }
            if (tvName != null) {
                tvName.setText(info.getAppName());
            }
            if (tvDuration != null) {
                tvDuration.setText(formatDuration(info.getTotalTimeInForegroundMs()));
            }

            layoutTimeDrainContainer.addView(itemView);
        }
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

    private static class PlaceStayAggregate {
        final String placeName;
        final String category;
        final String icon;
        final Integer color;
        long totalStayDurationMs = 0;
        long totalScreenTimeMs = 0;
        final Map<String, Long> appUsageMap = new HashMap<>();

        PlaceStayAggregate(String placeName, String category, String icon, Integer color) {
            this.placeName = placeName;
            this.category = category;
            this.icon = icon;
            this.color = color;
        }
    }
}
