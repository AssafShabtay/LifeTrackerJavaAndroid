package com.example.myapplication.ui.statistics.screenTime;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.myapplication.R;
import com.example.myapplication.tracking.usage.AppCategoryHelper;
import com.example.myapplication.tracking.usage.UsageStatsHelper;
import com.example.myapplication.ui.home.PhoneUsageDonutView;
import com.google.android.material.button.MaterialButtonToggleGroup;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages binding and rendering data for the Detailed Category Screen Time Card in Screen Time tab.
 */
public class DetailedPhoneUsageCardManager {

    private final Context context;
    private final View cardView;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private PhoneUsageDonutView donutView;
    private TextView tvTotalTime;
    private TextView tvPeakCategory;

    private TextView tvHeaderTitle;
    private MaterialButtonToggleGroup toggleGroup;
    private LinearLayout layoutCategoriesContainer;
    private View layoutTopAppsContainer;

    public DetailedPhoneUsageCardManager(Context context, View cardView) {
        this.context = context;
        this.cardView = cardView;

        initViews();
    }

    private void initViews() {
        if (cardView == null) return;

        donutView = cardView.findViewById(R.id.screen_time_donut_view);
        tvTotalTime = cardView.findViewById(R.id.tv_screen_time_total_time);
        tvPeakCategory = cardView.findViewById(R.id.tv_screen_time_peak_category);

        tvHeaderTitle = cardView.findViewById(R.id.tv_breakdown_header_title);
        toggleGroup = cardView.findViewById(R.id.toggle_group_usage_mode);
        layoutCategoriesContainer = cardView.findViewById(R.id.layout_categories_container);
        layoutTopAppsContainer = cardView.findViewById(R.id.layout_top_apps_container);

        if (toggleGroup != null) {
            toggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                if (!isChecked) return;
                if (checkedId == R.id.btn_toggle_categories) {
                    if (layoutCategoriesContainer != null) layoutCategoriesContainer.setVisibility(View.VISIBLE);
                    if (layoutTopAppsContainer != null) layoutTopAppsContainer.setVisibility(View.GONE);
                    if (tvHeaderTitle != null) tvHeaderTitle.setText("Category Breakdown");
                } else if (checkedId == R.id.btn_toggle_top_apps) {
                    if (layoutCategoriesContainer != null) layoutCategoriesContainer.setVisibility(View.GONE);
                    if (layoutTopAppsContainer != null) layoutTopAppsContainer.setVisibility(View.VISIBLE);
                    if (tvHeaderTitle != null) tvHeaderTitle.setText("Top Apps for this Day");
                }
            });
            toggleGroup.check(R.id.btn_toggle_top_apps);
        }
    }

    private void setupDotAndProgressBar(View dotView, ProgressBar progressBar, int color) {
        if (dotView != null) {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.OVAL);
            drawable.setColor(color);
            dotView.setBackground(drawable);
        }
        if (progressBar != null) {
            progressBar.setProgressTintList(ColorStateList.valueOf(color));
        }
    }

    public void updateForDate(Date date) {
        if (cardView == null) return;

        Date selectedDate = date != null ? date : new Date();

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

        executorService.execute(() -> {
            Map<String, Long> packageUsage = UsageStatsHelper.getUsageTimePerPackage(context, startTime, endTime);
            processAndBindUsageData(packageUsage);
        });
    }

    public static class CategoryUsage implements Comparable<CategoryUsage> {
        public String name;
        public long totalMs;
        public Map<String, Long> appMap = new HashMap<>();

        public CategoryUsage(String name) {
            this.name = name;
        }

        @Override
        public int compareTo(CategoryUsage other) {
            return Long.compare(other.totalMs, this.totalMs); // Descending order
        }
    }

    public void processAndBindUsageData(Map<String, Long> packageUsage) {
        if (packageUsage == null) packageUsage = new HashMap<>();

        PackageManager pm = context.getPackageManager();
        Map<String, CategoryUsage> categoryMap = new HashMap<>();

        long totalMs = 0;

        for (Map.Entry<String, Long> entry : packageUsage.entrySet()) {
            String pkg = entry.getKey();
            long duration = entry.getValue();
            if (duration <= 0 || pkg == null) continue;

            String catName = AppCategoryHelper.getCategoryName(pm, pkg);
            CategoryUsage catUsage = categoryMap.get(catName);
            if (catUsage == null) {
                catUsage = new CategoryUsage(catName);
                categoryMap.put(catName, catUsage);
            }
            catUsage.totalMs += duration;
            catUsage.appMap.put(pkg, duration);
            totalMs += duration;
        }

        // Separate "Other" (unrecognized apps) from specific named categories
        CategoryUsage otherCategory = categoryMap.remove("Other");
        if (otherCategory == null) {
            otherCategory = new CategoryUsage("Other");
        }

        // Collect and sort specific named categories
        List<CategoryUsage> specificCategories = new ArrayList<>();
        for (CategoryUsage cat : categoryMap.values()) {
            if (cat.totalMs > 0) {
                specificCategories.add(cat);
            }
        }
        Collections.sort(specificCategories);

        List<CategoryUsage> displayCategories = new ArrayList<>();

        if (specificCategories.size() > 3) {
            // Keep top 3 specific categories
            for (int i = 0; i < 3; i++) {
                displayCategories.add(specificCategories.get(i));
            }

            // Combine all leftover specific categories into otherCategory
            for (int i = 3; i < specificCategories.size(); i++) {
                CategoryUsage cat = specificCategories.get(i);
                otherCategory.totalMs += cat.totalMs;
                for (Map.Entry<String, Long> appEntry : cat.appMap.entrySet()) {
                    Long currentVal = otherCategory.appMap.get(appEntry.getKey());
                    long newVal = (currentVal == null ? 0L : currentVal) + appEntry.getValue();
                    otherCategory.appMap.put(appEntry.getKey(), newVal);
                }
            }
        } else {
            displayCategories.addAll(specificCategories);
        }

        // Append the single consolidated "Other" category if it has usage
        if (otherCategory.totalMs > 0) {
            displayCategories.add(otherCategory);
        }

        final long fTotalMs = totalMs;
        final List<CategoryUsage> fCategories = displayCategories;

        mainHandler.post(() -> renderUi(fTotalMs, fCategories));
    }

    private void renderUi(long totalMs, List<CategoryUsage> categories) {
        if (tvTotalTime != null) {
            tvTotalTime.setText(formatDuration(totalMs));
        }

        // Determine Peak Category
        String peakName = "None";
        int peakPct = 0;
        if (totalMs > 0 && !categories.isEmpty() && categories.get(0).totalMs > 0) {
            peakName = categories.get(0).name;
            peakPct = (int) (categories.get(0).totalMs * 100 / totalMs);
        }

        if (tvPeakCategory != null) {
            if (totalMs > 0 && !categories.isEmpty()) {
                tvPeakCategory.setText(String.format(Locale.getDefault(), "Top Category: %s (%d%%)", peakName, peakPct));
            } else {
                tvPeakCategory.setText("No Usage Recorded");
            }
        }

        // Render Dynamic Category Rows
        if (layoutCategoriesContainer != null) {
            layoutCategoriesContainer.removeAllViews();

            if (categories.isEmpty() || totalMs <= 0) {
                TextView emptyTv = new TextView(context);
                emptyTv.setText("No usage recorded for this day.");
                emptyTv.setTextSize(12);
                emptyTv.setTextColor(ContextCompat.getColor(context, R.color.secondary));
                emptyTv.setPadding(0, 16, 0, 16);
                layoutCategoriesContainer.addView(emptyTv);
            } else {
                LayoutInflater inflater = LayoutInflater.from(context);
                int[] colorResIds = AppCategoryHelper.CATEGORY_COLOR_RES_IDS;

                for (int i = 0; i < categories.size(); i++) {
                    CategoryUsage cat = categories.get(i);
                    int color = ContextCompat.getColor(context, colorResIds[i % colorResIds.length]);

                    View rowView = inflater.inflate(R.layout.item_category_breakdown_row, layoutCategoriesContainer, false);

                    View dotView = rowView.findViewById(R.id.dot_category_color);
                    TextView tvName = rowView.findViewById(R.id.tv_category_name);
                    TextView tvTime = rowView.findViewById(R.id.tv_category_time);
                    ProgressBar pb = rowView.findViewById(R.id.pb_category_progress);
                    TextView tvTopApp = rowView.findViewById(R.id.tv_category_top_app);

                    setupDotAndProgressBar(dotView, pb, color);

                    if (tvName != null) tvName.setText(cat.name);

                    bindCategoryRow(cat.totalMs, totalMs, tvTime, pb, tvTopApp, getTopAppText(cat.appMap));

                    layoutCategoriesContainer.addView(rowView);
                }
            }
        }

        // Donut segments
        List<PhoneUsageDonutView.DonutSegment> segments = new ArrayList<>();
        if (totalMs > 0) {
            int[] colorResIds = AppCategoryHelper.CATEGORY_COLOR_RES_IDS;
            for (int i = 0; i < categories.size(); i++) {
                CategoryUsage cat = categories.get(i);
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

    private void bindCategoryRow(long durationMs, long totalMs, TextView tvTime, ProgressBar pb, TextView tvTopApp, String topAppText) {
        int pct = totalMs > 0 ? (int) (durationMs * 100 / totalMs) : 0;

        if (tvTime != null) {
            tvTime.setText(String.format(Locale.getDefault(), "%s · %d%%", formatDuration(durationMs), pct));
        }
        if (pb != null) {
            pb.setProgress(pct);
        }
        if (tvTopApp != null) {
            if (durationMs > 0 && topAppText != null) {
                tvTopApp.setText("Top App: " + topAppText);
                tvTopApp.setVisibility(View.VISIBLE);
            } else {
                tvTopApp.setText("No usage recorded");
                tvTopApp.setVisibility(View.VISIBLE);
            }
        }
    }

    private String getTopAppText(Map<String, Long> categoryAppMap) {
        if (categoryAppMap == null || categoryAppMap.isEmpty()) return null;

        List<UsageStatsHelper.AppUsageInfo> sortedList = UsageStatsHelper.getSortedAppUsageList(context, categoryAppMap);
        if (sortedList.isEmpty()) return null;

        UsageStatsHelper.AppUsageInfo topApp = sortedList.get(0);
        return String.format(Locale.getDefault(), "%s (%s)", topApp.getAppName(), formatDuration(topApp.getTotalTimeInForegroundMs()));
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
