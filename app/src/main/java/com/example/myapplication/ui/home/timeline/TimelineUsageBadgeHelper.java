package com.example.myapplication.ui.home.timeline;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.example.myapplication.R;
import com.example.myapplication.ui.efficiency.EfficiencyStatsManager;
import com.example.myapplication.tracking.usage.TimelineItemUsageData;
import com.example.myapplication.tracking.usage.UsageStatsHelper;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class TimelineUsageBadgeHelper {

    private final Set<String> expandedItemKeys = new HashSet<>();
    private final Map<String, TimelineItemUsageData> usageCache = new HashMap<>();

    private final ExecutorService backgroundExecutor = Executors.newFixedThreadPool(2); //TODO FIX
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public void bindUsageBadgeAndBreakdown(
            Context context,
            String itemKey,
            Date startDate,
            Date endDate,
            LinearLayout layoutUsageBadge,
            TextView tvUsageBadgeText,
            ImageView ivUsageBadgeIcon,
            ImageView ivUsageBadgeExpand,
            FrameLayout layoutMicroBreakdownContainer
    ) {
        if (layoutUsageBadge == null || layoutMicroBreakdownContainer == null) return;

        if (startDate == null || endDate == null || !UsageStatsHelper.hasUsageAccessPermission(context)) {
            layoutUsageBadge.setVisibility(View.GONE);
            layoutMicroBreakdownContainer.setVisibility(View.GONE);
            return;
        }

        long startDateTime = startDate.getTime();
        long endDateTime = endDate.getTime();
        if (startDateTime >= endDateTime) {
            layoutUsageBadge.setVisibility(View.GONE);
            layoutMicroBreakdownContainer.setVisibility(View.GONE);
            return;
        }

        // tag the view with the item key to prevent asynchronous data from populating the wrong view after recycling.
        layoutUsageBadge.setTag(itemKey);

        // see if data has already been calculated, and saved to cache
        TimelineItemUsageData cached = usageCache.get(itemKey);
        if (cached != null) {
            renderUsageBadgeAndBreakdown(
                    context,
                    itemKey,
                    cached,
                    layoutUsageBadge,
                    tvUsageBadgeText,
                    ivUsageBadgeIcon,
                    ivUsageBadgeExpand,
                    layoutMicroBreakdownContainer
            );
        } else {
            // Hide temporarily while querying
            layoutUsageBadge.setVisibility(View.GONE);
            layoutMicroBreakdownContainer.setVisibility(View.GONE);

            backgroundExecutor.execute(() -> {
                TimelineItemUsageData data = UsageStatsHelper.getUsageDataForPeriod(context, startDateTime, endDateTime);
                if (data != null) {
                    usageCache.put(itemKey, data);
                    mainHandler.post(() -> {
                        if (Objects.equals(itemKey, layoutUsageBadge.getTag())) {
                            renderUsageBadgeAndBreakdown(
                                    context,
                                    itemKey,
                                    data,
                                    layoutUsageBadge,
                                    tvUsageBadgeText,
                                    ivUsageBadgeIcon,
                                    ivUsageBadgeExpand,
                                    layoutMicroBreakdownContainer
                            );
                        }
                    });
                }
            });
        }
    }

    private void renderUsageBadgeAndBreakdown(
            Context context,
            String itemKey,
            TimelineItemUsageData data,
            LinearLayout layoutUsageBadge,
            TextView tvUsageBadgeText,
            ImageView ivUsageBadgeIcon,
            ImageView ivUsageBadgeExpand,
            FrameLayout layoutMicroBreakdownContainer
    ) {
        if (data == null || data.getScreenTimeMs() < 15000) {
            // Less than 15s phone usage: phone free badge
            layoutUsageBadge.setVisibility(View.VISIBLE);
            tvUsageBadgeText.setText("Phone Free");

            int greenColor = ContextCompat.getColor(context, R.color.green);
            applyUsageBadgeStyling(context, layoutUsageBadge, tvUsageBadgeText, ivUsageBadgeIcon, ivUsageBadgeExpand, greenColor);

            ivUsageBadgeExpand.setVisibility(View.GONE);
            layoutMicroBreakdownContainer.setVisibility(View.GONE);
            layoutUsageBadge.setOnClickListener(null);
            return;
        }

        layoutUsageBadge.setVisibility(View.VISIBLE);
        ivUsageBadgeExpand.setVisibility(View.VISIBLE);
        String formattedScreenTime = EfficiencyStatsManager.formatDuration(data.getScreenTimeMs());
        int percentage = data.getPercentage();
        tvUsageBadgeText.setText(String.format(Locale.getDefault(), "%s (%d%%)", formattedScreenTime, percentage));

        // green to red gradient color based on percentage (0% green, 50% yellow/orange, 100% red)
        int badgeAccentColor = getUsageColorForPercentage(context, percentage);
        applyUsageBadgeStyling(context, layoutUsageBadge, tvUsageBadgeText, ivUsageBadgeIcon, ivUsageBadgeExpand, badgeAccentColor);

        boolean isExpanded = expandedItemKeys.contains(itemKey);
        ivUsageBadgeExpand.setRotation(isExpanded ? 180f : 0f);

        if (isExpanded) {
            renderMicroBreakdownContent(context, data, layoutMicroBreakdownContainer);
            layoutMicroBreakdownContainer.setVisibility(View.VISIBLE);
        } else {
            layoutMicroBreakdownContainer.setVisibility(View.GONE);
            layoutMicroBreakdownContainer.removeAllViews();
        }

        // Tapping the chip toggles breakdown expansion
        layoutUsageBadge.setOnClickListener(v -> {
            boolean expandedNow = expandedItemKeys.contains(itemKey);
            if (expandedNow) {
                expandedItemKeys.remove(itemKey);
                ivUsageBadgeExpand.animate().rotation(0f).setDuration(200).start();
                layoutMicroBreakdownContainer.setVisibility(View.GONE);
                layoutMicroBreakdownContainer.removeAllViews();
            } else {
                expandedItemKeys.add(itemKey);
                ivUsageBadgeExpand.animate().rotation(180f).setDuration(200).start();
                renderMicroBreakdownContent(context, data, layoutMicroBreakdownContainer);
                layoutMicroBreakdownContainer.setVisibility(View.VISIBLE);
            }
        });
    }

    private void renderMicroBreakdownContent(
            Context context,
            TimelineItemUsageData data,
            FrameLayout layoutMicroBreakdownContainer
    ) {
        layoutMicroBreakdownContainer.removeAllViews();
        View breakdownView = LayoutInflater.from(context).inflate(
                R.layout.item_timeline_micro_breakdown,
                layoutMicroBreakdownContainer,
                false
        );

        TextView tvPercent = breakdownView.findViewById(R.id.tvBreakdownPercent);
        LinearProgressIndicator progress = breakdownView.findViewById(R.id.progressBreakdownPercent);
        LinearLayout appsContainer = breakdownView.findViewById(R.id.breakdownAppsContainer);
        TextView tvNoApps = breakdownView.findViewById(R.id.tvBreakdownNoApps);

        String screenTime = EfficiencyStatsManager.formatDuration(data.getScreenTimeMs());
        int pct = data.getPercentage();
        tvPercent.setText(String.format(Locale.getDefault(), "%s · %d%% of visit", screenTime, pct));
        progress.setProgress(pct);
        progress.setIndicatorColor(getUsageColorForPercentage(context, pct));

        List<UsageStatsHelper.AppUsageInfo> topApps = data.getTopApps();
        if (topApps == null || topApps.isEmpty()) {
            tvNoApps.setVisibility(View.VISIBLE);
        } else {
            tvNoApps.setVisibility(View.GONE);
            appsContainer.removeAllViews();
            int limit = Math.min(3, topApps.size()); // Top 3 apps during this specific visit
            LayoutInflater inflater = LayoutInflater.from(context);

            for (int i = 0; i < limit; i++) {
                UsageStatsHelper.AppUsageInfo app = topApps.get(i);
                View row = inflater.inflate(R.layout.item_time_drain_app, appsContainer, false);

                ImageView ivIcon = row.findViewById(R.id.iv_drain_app_icon);
                TextView tvName = row.findViewById(R.id.tv_drain_app_name);
                TextView tvDuration = row.findViewById(R.id.tv_drain_app_duration);

                if (ivIcon != null) {
                    ivIcon.setImageDrawable(app.getAppIcon());
                }

                if (tvName != null) {
                    tvName.setText(app.getAppName());
                }

                if (tvDuration != null) {
                    tvDuration.setText(EfficiencyStatsManager.formatDuration(app.getTotalTimeInForegroundMs()));
                    tvDuration.setTextColor(ContextCompat.getColor(context, R.color.primary));
                }

                appsContainer.addView(row);
            }
        }

        layoutMicroBreakdownContainer.addView(breakdownView);
    }

    private int getUsageColorForPercentage(Context context, int percentage) {
        int greenColor = ContextCompat.getColor(context, R.color.green);
        int yellowColor = ContextCompat.getColor(context, R.color.yellow);
        int redColor = ContextCompat.getColor(context, R.color.red);

        int clampedPct = Math.max(0, Math.min(100, percentage));
        if (clampedPct <= 50) {
            float ratio = clampedPct / 50.0f;
            return ColorUtils.blendARGB(greenColor, yellowColor, ratio);
        } else {
            float ratio = (clampedPct - 50) / 50.0f;
            return ColorUtils.blendARGB(yellowColor, redColor, ratio);
        }
    }

    private void applyUsageBadgeStyling(
            Context context,
            LinearLayout layoutUsageBadge,
            TextView tvUsageBadgeText,
            ImageView ivUsageBadgeIcon,
            ImageView ivUsageBadgeExpand,
            int accentColor
    ) {
        int bgTint = ColorUtils.setAlphaComponent(accentColor, 38);   // 15% opacity
        int strokeColor = ColorUtils.setAlphaComponent(accentColor, 89); // 35% opacity

        float radiusPx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                12,
                context.getResources().getDisplayMetrics()
        );
        int strokeWidthPx = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                1,
                context.getResources().getDisplayMetrics()
        );

        int padLeft = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, context.getResources().getDisplayMetrics());
        int padTop = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, context.getResources().getDisplayMetrics());
        int padRight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, context.getResources().getDisplayMetrics());
        int padBottom = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, context.getResources().getDisplayMetrics());

        GradientDrawable chipDrawable = new GradientDrawable();
        chipDrawable.setShape(GradientDrawable.RECTANGLE);
        chipDrawable.setCornerRadius(radiusPx);
        chipDrawable.setColor(bgTint);
        chipDrawable.setStroke(strokeWidthPx, strokeColor);

        layoutUsageBadge.setBackground(chipDrawable);
        layoutUsageBadge.setPadding(padLeft, padTop, padRight, padBottom);

        tvUsageBadgeText.setTextColor(accentColor);
        ivUsageBadgeIcon.setColorFilter(accentColor);
        ivUsageBadgeExpand.setColorFilter(accentColor);
    }
}
