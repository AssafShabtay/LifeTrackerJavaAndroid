package com.example.myapplication.ui.home.phoneUsage;

import android.content.Context;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PhoneUsageMiniCardManager {

    private final Context context;
    private final View cardView;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private View layoutUsageContent;
    private View layoutPermissionPrompt;
    private Button btnGrantPermission;

    private PhoneUsageDonutView donutView;
    private TextView tvTotalScreenTime;

    public PhoneUsageMiniCardManager(Context context, View cardView) {
        this.context = context;
        this.cardView = cardView;

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
        if (cardView == null) return;

        boolean hasPermission = UsageStatsHelper.hasUsageAccessPermission(context);
        if (!hasPermission) {
            if (layoutUsageContent != null) layoutUsageContent.setVisibility(View.GONE);
            if (layoutPermissionPrompt != null) layoutPermissionPrompt.setVisibility(View.VISIBLE);
            return;
        }

        if (layoutUsageContent != null) layoutUsageContent.setVisibility(View.VISIBLE);
        if (layoutPermissionPrompt != null) layoutPermissionPrompt.setVisibility(View.GONE);

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

            PackageManager pm = context.getPackageManager();
            Map<String, DetailedPhoneUsageCardManager.CategoryUsage> categoryMap = new HashMap<>();

            long totalMs = 0;

            for (Map.Entry<String, Long> entry : packageUsage.entrySet()) {
                String pkg = entry.getKey();
                long duration = entry.getValue();
                if (duration <= 0 || pkg == null) continue;

                String catName = AppCategoryHelper.getCategoryName(pm, pkg);
                DetailedPhoneUsageCardManager.CategoryUsage catUsage = categoryMap.get(catName);
                if (catUsage == null) {
                    catUsage = new DetailedPhoneUsageCardManager.CategoryUsage(catName);
                    categoryMap.put(catName, catUsage);
                }
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

            mainHandler.post(() -> updateUi(finalTotalMs, finalCategories));
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
                    TextView tvText = legendView.findViewById(R.id.tv_legend_text);

                    setDotColor(dotView, color);
                    if (tvText != null) {
                        tvText.setText(String.format(Locale.getDefault(), "%s: %s", cat.name, formatDuration(cat.totalMs)));
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
