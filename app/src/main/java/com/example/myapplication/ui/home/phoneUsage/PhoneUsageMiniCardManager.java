package com.example.myapplication.ui.home.phoneUsage;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.myapplication.LifeTrackerApp;
import com.example.myapplication.MainActivity;
import com.example.myapplication.R;
import com.example.myapplication.tracking.usage.AppCategoryHelper;
import com.example.myapplication.tracking.usage.UsageStatsHelper;
import com.example.myapplication.ui.statistics.screenTime.DetailedPhoneUsageCardManager;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class PhoneUsageMiniCardManager {

    private final Context context;
    private final View cardView;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final LifeTrackerApp app;

    private View layoutUsageContent;
    private View layoutPermissionPrompt;
    private Button btnGrantPermission;

    private PhoneUsageDonutView donutView;
    private TextView tvTotalScreenTime;

    private int loadVersion = 0;
    private boolean isDestroyed = false;

    public PhoneUsageMiniCardManager(Context context, View cardView) {
        this.context = context;
        this.cardView = cardView;
        this.app = (LifeTrackerApp) context.getApplicationContext();

        initViews();
    }

    private void initViews() {
        if (cardView == null) return;

        layoutUsageContent = cardView.findViewById(R.id.layout_usage_content);
        layoutPermissionPrompt = cardView.findViewById(R.id.layout_permission_prompt);
        btnGrantPermission = cardView.findViewById(R.id.btn_grant_usage_permission);

        donutView = cardView.findViewById(R.id.phone_usage_donut_view);
        tvTotalScreenTime = cardView.findViewById(R.id.tv_total_screen_time);

        if (btnGrantPermission != null) {
            btnGrantPermission.setOnClickListener(v -> UsageStatsHelper.openUsageAccessSettings(context));
        }

        View.OnClickListener navigateToStatsListener = v -> {
            Activity activity = getActivityFromContext(context);
            if (activity instanceof MainActivity) {
                ((MainActivity) activity).navigateToScreenTimeStatistics();
            }
        };

        if (layoutUsageContent != null) {
            layoutUsageContent.setOnClickListener(navigateToStatsListener);
        }
        cardView.setOnClickListener(navigateToStatsListener);
    }

    private Activity getActivityFromContext(Context context) {
        Context current = context;
        while (current instanceof ContextWrapper) {
            if (current instanceof Activity) {
                return (Activity) current;
            }
            current = ((ContextWrapper) current).getBaseContext();
        }
        return null;
    }

    private void setDotColor(View dotView, int color) {
        if (dotView != null) {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.OVAL);
            drawable.setColor(color);
            dotView.setBackground(drawable);
        }
    }

    public void loadUsageDataForDate(Date date) {
        if (cardView == null || isDestroyed) return;

        boolean hasPermission = UsageStatsHelper.hasUsageAccessPermission(context);
        if (!hasPermission) {
            if (layoutUsageContent != null) layoutUsageContent.setVisibility(View.GONE);
            if (layoutPermissionPrompt != null) layoutPermissionPrompt.setVisibility(View.VISIBLE);
            return;
        }

        if (layoutUsageContent != null) layoutUsageContent.setVisibility(View.VISIBLE);
        if (layoutPermissionPrompt != null) layoutPermissionPrompt.setVisibility(View.GONE);

        Date selectedDate = Objects.requireNonNullElseGet(date, Date::new);

        Calendar cal = Calendar.getInstance();
        cal.setTime(selectedDate);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long startTime = cal.getTimeInMillis();

        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 999);
        long endTime = Math.min(cal.getTimeInMillis(), System.currentTimeMillis());

        final int currentVersion = ++loadVersion;

        app.getBackgroundWriteExecutor().execute(() -> {
            Map<String, Long> packageUsage = UsageStatsHelper.getUsageTimePerPackage(context, startTime, endTime);

            if (isDestroyed || currentVersion != loadVersion) return;

            PackageManager pm = context.getPackageManager();
            Map<String, DetailedPhoneUsageCardManager.CategoryUsage> categoryMap = new HashMap<>();

            long totalMs = 0;

            for (Map.Entry<String, Long> entry : packageUsage.entrySet()) {
                String pkg = entry.getKey();
                long duration = entry.getValue();
                if (duration <= 0 || pkg == null) continue;

                String catName = AppCategoryHelper.getCategoryName(pm, pkg);
                DetailedPhoneUsageCardManager.CategoryUsage catUsage = categoryMap.computeIfAbsent(catName, DetailedPhoneUsageCardManager.CategoryUsage::new);
                catUsage.totalMs += duration;
                totalMs += duration;
            }

            // Separate "Other" (unrecognized apps) from specific named categories
            DetailedPhoneUsageCardManager.CategoryUsage otherCategory = categoryMap.remove("Other");
            if (otherCategory == null) {
                otherCategory = new DetailedPhoneUsageCardManager.CategoryUsage("Other");
            }

            List<DetailedPhoneUsageCardManager.CategoryUsage> specificCategories = new ArrayList<>();
            for (DetailedPhoneUsageCardManager.CategoryUsage cat : categoryMap.values()) {
                if (cat.totalMs > 0) {
                    specificCategories.add(cat);
                }
            }
            Collections.sort(specificCategories);

            List<DetailedPhoneUsageCardManager.CategoryUsage> displayCategories = new ArrayList<>();

            if (specificCategories.size() > 3) {
                for (int i = 0; i < 3; i++) {
                    displayCategories.add(specificCategories.get(i));
                }
                for (int i = 3; i < specificCategories.size(); i++) {
                    DetailedPhoneUsageCardManager.CategoryUsage cat = specificCategories.get(i);
                    otherCategory.totalMs += cat.totalMs;
                }
            } else {
                displayCategories.addAll(specificCategories);
            }

            if (otherCategory.totalMs > 0) {
                displayCategories.add(otherCategory);
            }

            final long finalTotalMs = totalMs;
            final List<DetailedPhoneUsageCardManager.CategoryUsage> finalCategories = displayCategories;

            mainHandler.post(() -> {
                if (isDestroyed || currentVersion != loadVersion) return;
                updateUi(finalTotalMs, finalCategories);
            });
        });
    }

    private void updateUi(long totalMs, List<DetailedPhoneUsageCardManager.CategoryUsage> categories) {
        if (tvTotalScreenTime != null) {
            tvTotalScreenTime.setText(formatDuration(totalMs));
        }

        ViewGroup legendContainer = cardView.findViewById(R.id.layout_category_legend_container);
        if (legendContainer != null) {
            legendContainer.removeAllViews();

            if (categories != null && !categories.isEmpty() && totalMs > 0) {
                LayoutInflater inflater = LayoutInflater.from(context);
                int[] colorResIds = AppCategoryHelper.CATEGORY_COLOR_RES_IDS;

                for (int i = 0; i < categories.size(); i++) {
                    DetailedPhoneUsageCardManager.CategoryUsage cat = categories.get(i);
                    int color = ContextCompat.getColor(context, colorResIds[i % colorResIds.length]);

                    View legendView = inflater.inflate(R.layout.item_phone_usage_legend, legendContainer, false);
                    View dotView = legendView.findViewById(R.id.dot_legend_color);
                    TextView tvCategory = legendView.findViewById(R.id.tv_legend_category);
                    TextView tvTime = legendView.findViewById(R.id.tv_legend_time);

                    setDotColor(dotView, color);
                    if (tvCategory != null) {
                        tvCategory.setText(cat.name);
                    }
                    if (tvTime != null) {
                        tvTime.setText(formatDuration(cat.totalMs));
                    }

                    legendContainer.addView(legendView);
                }
            }
        }

        List<PhoneUsageDonutView.DonutSegment> segments = new ArrayList<>();
        if (totalMs > 0 && categories != null) {
            int[] colorResIds = AppCategoryHelper.CATEGORY_COLOR_RES_IDS;
            for (int i = 0; i < categories.size(); i++) {
                DetailedPhoneUsageCardManager.CategoryUsage cat = categories.get(i);
                if (cat.totalMs > 0) {
                    int color = ContextCompat.getColor(context, colorResIds[i % colorResIds.length]);
                    segments.add(new PhoneUsageDonutView.DonutSegment((float) cat.totalMs / totalMs, color, cat.name));
                }
            }
        }

        if (donutView != null) {
            donutView.setSegments(segments, true);
        }
    }

    public void destroy() {
        isDestroyed = true;
    }

    private String formatDuration(long durationMs) {
        if (durationMs <= 0) return "0m";

        long totalMinutes = durationMs / (1000 * 60);
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;

        if (hours > 0) {
            return String.format(Locale.getDefault(), "%dh %dm", hours, minutes);
        } else {
            return String.format(Locale.getDefault(), "%dm", minutes);
        }
    }
}
