package com.example.myapplication.ui.statistics.screenTime;

import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.myapplication.R;
import com.example.myapplication.tracking.usage.DailyUsageItem;
import com.example.myapplication.tracking.usage.UsageStatsHelper;
import com.example.myapplication.tracking.usage.WeeklyUsageItem;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.tabs.TabLayout;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ScreenTimeStatsManager {

    public static final int PERIOD_DAILY = 0;
    public static final int PERIOD_WEEKLY = 1;

    private final Context context;
    private final View rootView;
    private final ScreenTimeViewModel viewModel;

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
    private DailyUsageGraphView graphDailyHourlyUsage;
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

    private DetailedPhoneUsageCardManager detailedCardManager;
    private LinearProgressIndicator loadingIndicator;

    public ScreenTimeStatsManager(Context context, View rootView, ScreenTimeViewModel viewModel) {
        this.context = context;
        this.rootView = rootView;
        this.viewModel = viewModel;

        initViews();
    }

    private void initViews() {
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
                    if (viewModel != null) {
                        viewModel.setSelectedPeriod(tab.getPosition());
                    }
                }

                @Override
                public void onTabUnselected(TabLayout.Tab tab) {}

                @Override
                public void onTabReselected(TabLayout.Tab tab) {}
            });
        }

        if (btnPrevDay != null) {
            btnPrevDay.setOnClickListener(v -> {
                if (viewModel != null) {
                    viewModel.navigatePrevious();
                }
            });
        }

        if (btnNextDay != null) {
            btnNextDay.setOnClickListener(v -> {
                if (viewModel != null) {
                    viewModel.navigateNext();
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

    public void renderPermissionState(boolean hasPermission) {
        if (!hasPermission) {
            if (permissionPromptCard != null) permissionPromptCard.setVisibility(View.VISIBLE);
            if (screenTimeContentLayout != null) screenTimeContentLayout.setVisibility(View.GONE);
            renderLoadingState(false);
        } else {
            if (permissionPromptCard != null) permissionPromptCard.setVisibility(View.GONE);
            if (screenTimeContentLayout != null) screenTimeContentLayout.setVisibility(View.VISIBLE);
        }
    }

    public void renderLoadingState(boolean loading) {
        if (loadingIndicator != null) loadingIndicator.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) {
            if (tvDailyCardTitle != null) tvDailyCardTitle.setText("Loading usage…");
            if (btnPrevDay != null) btnPrevDay.setEnabled(false);
            if (btnNextDay != null) btnNextDay.setEnabled(false);
        }
    }

    public void renderLoadError(boolean isError) {
        if (isError) {
            renderLoadingState(false);
            if (tvDailyCardTitle != null) tvDailyCardTitle.setText("Unable to load usage");
        }
    }

    public void renderPeriodSelected(int selectedPeriod) {
        if (tabLayoutPeriod != null && tabLayoutPeriod.getSelectedTabPosition() != selectedPeriod) {
            TabLayout.Tab tab = tabLayoutPeriod.getTabAt(selectedPeriod);
            if (tab != null) tab.select();
        }

        if (selectedPeriod == PERIOD_DAILY) {
            if (graphDailyHourlyUsage != null) graphDailyHourlyUsage.setVisibility(View.VISIBLE);
            if (graphWeeklyDailyUsage != null) graphWeeklyDailyUsage.setVisibility(View.GONE);
            if (tvGraphSectionTitle != null) tvGraphSectionTitle.setText("Hourly Usage Distribution (24h)");
        } else {
            if (graphDailyHourlyUsage != null) graphDailyHourlyUsage.setVisibility(View.GONE);
            if (graphWeeklyDailyUsage != null) graphWeeklyDailyUsage.setVisibility(View.VISIBLE);
            if (tvGraphSectionTitle != null) tvGraphSectionTitle.setText("Daily Usage Distribution (This Week)");
        }
    }

    public void renderDailyUsage(DailyUsageItem item, Map<String, Long> packageUsage, int currentDayIndex) {
        if (item == null) return;
        renderLoadingState(false);

        if (currentDayIndex == 0) {
            updateGeneralStats(item.getUnlockCount(), item.getPeakHour());
        }

        if (detailedCardManager != null) {
            Calendar end = Calendar.getInstance();
            end.setTime(item.getDate());
            end.add(Calendar.DAY_OF_YEAR, 1);
            detailedCardManager.updateForUsageData(item.getDate().getTime(),
                    Math.min(end.getTimeInMillis() - 1, System.currentTimeMillis()),
                    packageUsage, null);
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
        renderTopAppsList(item.getTopApps());
    }

    public void renderWeeklyUsage(WeeklyUsageItem item, Map<String, Long> packageUsage, int currentWeekIndex) {
        if (item == null) return;
        renderLoadingState(false);

        if (detailedCardManager != null) {
            detailedCardManager.updateForUsageData(item.getStartDate().getTime(), item.getEndDate().getTime(),
                    packageUsage, formatDuration(item.getTotalScreenTimeMs()));
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

        // Top Apps list
        renderTopAppsList(item.getTopApps());
    }

    private void updateGeneralStats(int unlockCount, String peakHour) {
        if (tvDeviceUnlocks != null) {
            tvDeviceUnlocks.setText(String.valueOf(unlockCount));
        }
        if (tvPeakUsageHour != null) {
            tvPeakUsageHour.setText(peakHour != null ? peakHour : "--");
        }
    }

    private void renderTopAppsList(List<UsageStatsHelper.AppUsageInfo> topApps) {
        if (layoutDailyTopAppsList != null) {
            layoutDailyTopAppsList.removeAllViews();

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

    public void destroy() {
        if (detailedCardManager != null) detailedCardManager.destroy();
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
