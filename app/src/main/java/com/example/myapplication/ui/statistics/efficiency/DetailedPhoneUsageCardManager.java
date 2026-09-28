package com.example.myapplication.ui.statistics.efficiency;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.myapplication.R;
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
 * Manages binding and rendering data for the Detailed Category Screen Time Card in Efficiency tab.
 */
public class DetailedPhoneUsageCardManager {

    private final Context context;
    private final View cardView;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    // Views
    private PhoneUsageDonutView donutView;
    private TextView tvTotalTime;
    private TextView tvPeakCategory;

    // Toggle Views
    private TextView tvHeaderTitle;
    private MaterialButtonToggleGroup toggleGroup;
    private View layoutCategoriesContainer;
    private View layoutTopAppsContainer;

    // Category Views: 1
    private View dotSocial;
    private TextView tvCat1Name;
    private TextView tvSocialTime;
    private ProgressBar pbSocial;
    private TextView tvSocialTopApp;

    // Category Views: 2
    private View dotMedia;
    private TextView tvCat2Name;
    private TextView tvMediaTime;
    private ProgressBar pbMedia;
    private TextView tvMediaTopApp;

    // Category Views: 3
    private View dotWork;
    private TextView tvCat3Name;
    private TextView tvWorkTime;
    private ProgressBar pbWork;
    private TextView tvWorkTopApp;

    // Category Views: 4
    private View dotOther;
    private TextView tvCat4Name;
    private TextView tvOtherTime;
    private ProgressBar pbOther;
    private TextView tvOtherTopApp;

    // Colors
    private final int colorSocial;
    private final int colorMedia;
    private final int colorWork;
    private final int colorOther;

    public DetailedPhoneUsageCardManager(Context context, View cardView) {
        this.context = context;
        this.cardView = cardView;

        colorSocial = ContextCompat.getColor(context, R.color.purple);
        colorMedia = ContextCompat.getColor(context, R.color.blue);
        colorWork = ContextCompat.getColor(context, R.color.green);
        colorOther = ContextCompat.getColor(context, R.color.orange);

        initViews();
    }

    private void initViews() {
        if (cardView == null) return;

        donutView = cardView.findViewById(R.id.eff_donut_view);
        tvTotalTime = cardView.findViewById(R.id.tv_eff_total_time);
        tvPeakCategory = cardView.findViewById(R.id.tv_eff_peak_category);

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
        }

        // Category 1
        dotSocial = cardView.findViewById(R.id.dot_eff_social);
        tvCat1Name = cardView.findViewById(R.id.tv_cat1_name);
        tvSocialTime = cardView.findViewById(R.id.tv_eff_social_time);
        pbSocial = cardView.findViewById(R.id.pb_eff_social);
        tvSocialTopApp = cardView.findViewById(R.id.tv_eff_social_top_app);

        // Category 2
        dotMedia = cardView.findViewById(R.id.dot_eff_media);
        tvCat2Name = cardView.findViewById(R.id.tv_cat2_name);
        tvMediaTime = cardView.findViewById(R.id.tv_eff_media_time);
        pbMedia = cardView.findViewById(R.id.pb_eff_media);
        tvMediaTopApp = cardView.findViewById(R.id.tv_eff_media_top_app);

        // Category 3
        dotWork = cardView.findViewById(R.id.dot_eff_work);
        tvCat3Name = cardView.findViewById(R.id.tv_cat3_name);
        tvWorkTime = cardView.findViewById(R.id.tv_eff_work_time);
        pbWork = cardView.findViewById(R.id.pb_eff_work);
        tvWorkTopApp = cardView.findViewById(R.id.tv_eff_work_top_app);

        // Category 4
        dotOther = cardView.findViewById(R.id.dot_eff_other);
        tvCat4Name = cardView.findViewById(R.id.tv_cat4_name);
        tvOtherTime = cardView.findViewById(R.id.tv_eff_other_time);
        pbOther = cardView.findViewById(R.id.pb_eff_other);
        tvOtherTopApp = cardView.findViewById(R.id.tv_eff_other_top_app);

        // Style dots & progress bars
        setupDotAndProgressBar(dotSocial, pbSocial, colorSocial);
        setupDotAndProgressBar(dotMedia, pbMedia, colorMedia);
        setupDotAndProgressBar(dotWork, pbWork, colorWork);
        setupDotAndProgressBar(dotOther, pbOther, colorOther);
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

    private static class CategoryUsage implements Comparable<CategoryUsage> {
        String name;
        long totalMs;
        Map<String, Long> appMap = new HashMap<>();

        CategoryUsage(String name) {
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

            String catName = getCategoryName(pm, pkg);
            CategoryUsage catUsage = categoryMap.get(catName);
            if (catUsage == null) {
                catUsage = new CategoryUsage(catName);
                categoryMap.put(catName, catUsage);
            }
            catUsage.totalMs += duration;
            catUsage.appMap.put(pkg, duration);
            totalMs += duration;
        }

        List<CategoryUsage> sortedCategories = new ArrayList<>(categoryMap.values());
        Collections.sort(sortedCategories);

        List<CategoryUsage> top4Categories = new ArrayList<>();
        if (sortedCategories.size() > 4) {
            top4Categories.add(sortedCategories.get(0));
            top4Categories.add(sortedCategories.get(1));
            top4Categories.add(sortedCategories.get(2));
            
            CategoryUsage otherCombined = new CategoryUsage("Other");
            for (int i = 3; i < sortedCategories.size(); i++) {
                CategoryUsage cat = sortedCategories.get(i);
                otherCombined.totalMs += cat.totalMs;
                for (Map.Entry<String, Long> appEntry : cat.appMap.entrySet()) {
                    Long currentVal = otherCombined.appMap.get(appEntry.getKey());
                    long newVal = (currentVal == null ? 0L : currentVal) + appEntry.getValue();
                    otherCombined.appMap.put(appEntry.getKey(), newVal);
                }
            }
            top4Categories.add(otherCombined);
        } else {
            top4Categories.addAll(sortedCategories);
            String[] defaultNames = {"Social", "Media", "Productivity", "Other"};
            while (top4Categories.size() < 4) {
                top4Categories.add(new CategoryUsage(defaultNames[top4Categories.size()]));
            }
        }

        final long fTotalMs = totalMs;
        final List<CategoryUsage> fTop4 = top4Categories;

        mainHandler.post(() -> renderUi(fTotalMs, fTop4));
    }

    private void renderUi(long totalMs, List<CategoryUsage> top4) {

        if (tvTotalTime != null) {
            tvTotalTime.setText(formatDuration(totalMs));
        }

        // Determine Peak Category
        String peakName = "None";
        int peakPct = 0;
        if (totalMs > 0 && !top4.isEmpty() && top4.get(0).totalMs > 0) {
            peakName = top4.get(0).name;
            peakPct = (int) (top4.get(0).totalMs * 100 / totalMs);
        }

        if (tvPeakCategory != null) {
            if (totalMs > 0) {
                tvPeakCategory.setText(String.format(Locale.getDefault(), "Top Category: %s (%d%%)", peakName, peakPct));
            } else {
                tvPeakCategory.setText("No Usage Recorded");
            }
        }

        CategoryUsage cat1 = top4.get(0);
        CategoryUsage cat2 = top4.get(1);
        CategoryUsage cat3 = top4.get(2);
        CategoryUsage cat4 = top4.get(3);

        if (tvCat1Name != null) tvCat1Name.setText(cat1.name);
        if (tvCat2Name != null) tvCat2Name.setText(cat2.name);
        if (tvCat3Name != null) tvCat3Name.setText(cat3.name);
        if (tvCat4Name != null) tvCat4Name.setText(cat4.name);

        bindCategoryRow(cat1.totalMs, totalMs, tvSocialTime, pbSocial, tvSocialTopApp, getTopAppText(cat1.appMap));
        bindCategoryRow(cat2.totalMs, totalMs, tvMediaTime, pbMedia, tvMediaTopApp, getTopAppText(cat2.appMap));
        bindCategoryRow(cat3.totalMs, totalMs, tvWorkTime, pbWork, tvWorkTopApp, getTopAppText(cat3.appMap));
        bindCategoryRow(cat4.totalMs, totalMs, tvOtherTime, pbOther, tvOtherTopApp, getTopAppText(cat4.appMap));

        // Donut segments
        List<PhoneUsageDonutView.DonutSegment> segments = new ArrayList<>();
        if (totalMs > 0) {
            if (cat1.totalMs > 0) segments.add(new PhoneUsageDonutView.DonutSegment((float) cat1.totalMs / totalMs, colorSocial, cat1.name));
            if (cat2.totalMs > 0) segments.add(new PhoneUsageDonutView.DonutSegment((float) cat2.totalMs / totalMs, colorMedia, cat2.name));
            if (cat3.totalMs > 0) segments.add(new PhoneUsageDonutView.DonutSegment((float) cat3.totalMs / totalMs, colorWork, cat3.name));
            if (cat4.totalMs > 0) segments.add(new PhoneUsageDonutView.DonutSegment((float) cat4.totalMs / totalMs, colorOther, cat4.name));
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

    private String getCategoryName(PackageManager pm, String pkg) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && pm != null) {
            try {
                ApplicationInfo appInfo = pm.getApplicationInfo(pkg, 0);
                int appCategory = appInfo.category;
                switch (appCategory) {
                    case ApplicationInfo.CATEGORY_GAME: return "Games";
                    case ApplicationInfo.CATEGORY_AUDIO: return "Audio";
                    case ApplicationInfo.CATEGORY_VIDEO: return "Video";
                    case ApplicationInfo.CATEGORY_IMAGE: return "Image";
                    case ApplicationInfo.CATEGORY_SOCIAL: return "Social";
                    case ApplicationInfo.CATEGORY_NEWS: return "News";
                    case ApplicationInfo.CATEGORY_MAPS: return "Maps";
                    case ApplicationInfo.CATEGORY_PRODUCTIVITY: return "Productivity";
                    case ApplicationInfo.CATEGORY_ACCESSIBILITY: return "Accessibility";
                    default: break; // Fallback below
                }
            } catch (PackageManager.NameNotFoundException ignored) {}
        }

        String lowerPkg = pkg.toLowerCase(Locale.ROOT);

        if (lowerPkg.contains("instagram") || lowerPkg.contains("facebook") || lowerPkg.contains("whatsapp") ||
                lowerPkg.contains("twitter") || lowerPkg.contains("tiktok") || lowerPkg.contains("telegram") ||
                lowerPkg.contains("snapchat") || lowerPkg.contains("reddit") || lowerPkg.contains("linkedin") ||
                lowerPkg.contains("pinterest") || lowerPkg.contains("messenger") || lowerPkg.contains("discord")) {
            return "Social";
        }

        if (lowerPkg.contains("youtube") || lowerPkg.contains("netflix") || lowerPkg.contains("spotify") ||
                lowerPkg.contains("twitch") || lowerPkg.contains("hulu") || lowerPkg.contains("disney") ||
                lowerPkg.contains("gallery") || lowerPkg.contains("photos") || lowerPkg.contains("vlc") || 
                lowerPkg.contains("music") || lowerPkg.contains("video") || lowerPkg.contains("audio")) {
            return "Media";
        }

        if (lowerPkg.contains("gmail") || lowerPkg.contains("outlook") || lowerPkg.contains("slack") ||
                lowerPkg.contains("teams") || lowerPkg.contains("docs") || lowerPkg.contains("sheets") ||
                lowerPkg.contains("calendar") || lowerPkg.contains("notes") || lowerPkg.contains("keep") ||
                lowerPkg.contains("calculator") || lowerPkg.contains("clock") || lowerPkg.contains("android.studio") ||
                lowerPkg.contains("drive") || lowerPkg.contains("zoom")) {
            return "Productivity";
        }
        
        if (lowerPkg.contains("chrome") || lowerPkg.contains("browser") || lowerPkg.contains("firefox") || lowerPkg.contains("edge")) {
            return "Browser";
        }
        
        if (lowerPkg.contains("launcher") || lowerPkg.contains("nexuslauncher") || lowerPkg.contains("trebuchet") || lowerPkg.contains("systemui")) {
            return "System";
        }

        return "Other";
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
