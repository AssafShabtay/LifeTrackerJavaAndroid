package com.example.myapplication.ui.statistics.screenTime;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.example.myapplication.R;
import com.example.myapplication.data.db.ActivityDatabase;
import com.example.myapplication.data.model.StillLocation;
import com.example.myapplication.tracking.usage.AppCategoryHelper;
import com.example.myapplication.tracking.usage.UsageStatsHelper;
import com.example.myapplication.ui.general.ColorAndIcons;
import com.example.myapplication.ui.home.phoneUsage.PhoneUsageDonutView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class DetailedPhoneUsageCardManager {

    private final Context context;
    private final View cardView;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private PhoneUsageDonutView donutView;
    private TextView tvTotalTime;
    private TextView tvPeakCategory;

    private TextView tvHeaderTitle;
    private MaterialButton btnToggleTopApps;
    private MaterialButton btnToggleCategories;
    private MaterialButton btnToggleLocations;

    private LinearLayout layoutCategoriesContainer;
    private View layoutTopAppsContainer;
    private View layoutLocationsContainer;
    private LinearLayout layoutDailyLocationsList;
    private TextView tvDailyCardNoLocations;
    private int selectedMode = R.id.btn_toggle_top_apps;
    private long selectedStartTime;
    private long selectedEndTime;
    private boolean hasSelectedRange;
    private boolean locationsLoaded;
    private boolean destroyed;
    private int loadVersion;
    private Future<?> pendingLoad;
    private Future<?> pendingLocations;

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
        layoutCategoriesContainer = cardView.findViewById(R.id.layout_categories_container);
        layoutTopAppsContainer = cardView.findViewById(R.id.layout_top_apps_container);
        layoutLocationsContainer = cardView.findViewById(R.id.layout_locations_container);
        layoutDailyLocationsList = cardView.findViewById(R.id.layout_daily_locations_list);
        tvDailyCardNoLocations = cardView.findViewById(R.id.tv_daily_card_no_locations);

        btnToggleTopApps = cardView.findViewById(R.id.btn_toggle_top_apps);
        btnToggleCategories = cardView.findViewById(R.id.btn_toggle_categories);
        btnToggleLocations = cardView.findViewById(R.id.btn_toggle_locations);

        View.OnClickListener clickListener = v -> selectMode(v.getId());

        if (btnToggleTopApps != null) btnToggleTopApps.setOnClickListener(clickListener);
        if (btnToggleCategories != null) btnToggleCategories.setOnClickListener(clickListener);
        if (btnToggleLocations != null) btnToggleLocations.setOnClickListener(clickListener);

        selectMode(R.id.btn_toggle_top_apps);
    }

    // update ui according to selected(category/top apps/locations)
    private void selectMode(int selectedBtnId) {
        selectedMode = selectedBtnId;
        updateButtonState(btnToggleTopApps, selectedBtnId == R.id.btn_toggle_top_apps);
        updateButtonState(btnToggleCategories, selectedBtnId == R.id.btn_toggle_categories);
        updateButtonState(btnToggleLocations, selectedBtnId == R.id.btn_toggle_locations);

        if (selectedBtnId == R.id.btn_toggle_categories) {
            if (layoutCategoriesContainer != null) layoutCategoriesContainer.setVisibility(View.VISIBLE);
            if (layoutTopAppsContainer != null) layoutTopAppsContainer.setVisibility(View.GONE);
            if (layoutLocationsContainer != null) layoutLocationsContainer.setVisibility(View.GONE);
            if (tvHeaderTitle != null) tvHeaderTitle.setText("Category Breakdown");
        } else if (selectedBtnId == R.id.btn_toggle_locations) {
            if (layoutCategoriesContainer != null) layoutCategoriesContainer.setVisibility(View.GONE);
            if (layoutTopAppsContainer != null) layoutTopAppsContainer.setVisibility(View.GONE);
            if (layoutLocationsContainer != null) layoutLocationsContainer.setVisibility(View.VISIBLE);
            if (tvHeaderTitle != null) tvHeaderTitle.setText("Locations Ranked by Phone Usage");
            loadPhoneUsagePerLocation();
        } else {
            if (layoutCategoriesContainer != null) layoutCategoriesContainer.setVisibility(View.GONE);
            if (layoutTopAppsContainer != null) layoutTopAppsContainer.setVisibility(View.VISIBLE);
            if (layoutLocationsContainer != null) layoutLocationsContainer.setVisibility(View.GONE);
            if (tvHeaderTitle != null) tvHeaderTitle.setText("Top Apps for this Day");
        }
    }

    private void updateButtonState(MaterialButton button, boolean isSelected) {
        if (button == null) return;
        if (isSelected) {
            button.setBackgroundColor(ContextCompat.getColor(context, R.color.primary_container));
            button.setTextColor(ContextCompat.getColor(context, R.color.primary));
            button.setIconTint(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.primary)));
        } else {
            button.setBackgroundColor(Color.TRANSPARENT);
            int inactiveColor = ContextCompat.getColor(context, R.color.secondary);
            button.setTextColor(inactiveColor);
            button.setIconTint(ColorStateList.valueOf(inactiveColor));
        }
    }

    public void updateForUsageData(long startTime, long endTime, Map<String, Long> packageUsage, String customTotalText) {
        if (cardView == null || destroyed) return;
        cancelPendingLoad();
        selectedStartTime = startTime;
        selectedEndTime = endTime;
        hasSelectedRange = true;
        locationsLoaded = false;
        final int version = loadVersion;
        pendingLoad = executorService.submit(() -> {
            processAndBindUsageData(packageUsage, null, customTotalText, version);
        });
        loadPhoneUsagePerLocation();
    }

    private void loadPhoneUsagePerLocation() {
        if (destroyed || !hasSelectedRange || selectedMode != R.id.btn_toggle_locations
                || locationsLoaded || pendingLocations != null) return;
        final int version = loadVersion;
        final long startTime = selectedStartTime;
        final long endTime = selectedEndTime;
        if (layoutDailyLocationsList != null) layoutDailyLocationsList.removeAllViews();
        if (tvDailyCardNoLocations != null) {
            tvDailyCardNoLocations.setText("Loading locations…");
            tvDailyCardNoLocations.setVisibility(View.VISIBLE);
        }
        pendingLocations = executorService.submit(() -> {
            List<LocationUsageRankItem> ranks = calculateLocationUsageRanks(startTime, endTime);
            mainHandler.post(() -> {
                if (destroyed || version != loadVersion) return;
                pendingLocations = null;
                locationsLoaded = true;
                if (tvDailyCardNoLocations != null) tvDailyCardNoLocations.setText("No location data for this period.");
                renderLocationRanks(ranks, layoutDailyLocationsList, tvDailyCardNoLocations);
            });
        });
    }

    public void cancelPendingLoad() {
        loadVersion++;
        hasSelectedRange = false;
        if (pendingLoad != null) pendingLoad.cancel(true);
        if (pendingLocations != null) pendingLocations.cancel(true);
        pendingLoad = null;
        pendingLocations = null;
    }

    public void destroy() {
        destroyed = true;
        cancelPendingLoad();
        executorService.shutdownNow();
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

    public static class LocationUsageRankItem implements Comparable<LocationUsageRankItem> {
        public String locationKey;
        public String placeName;
        public String placeAddress;
        public String icon;
        public String category;
        public Integer color;
        public long totalScreenTimeMs;
        public long totalStayTimeMs;
        public Map<String, Long> appUsageMap = new HashMap<>();

        @Override
        public int compareTo(LocationUsageRankItem other) {
            int cmp = Long.compare(other.totalScreenTimeMs, this.totalScreenTimeMs);
            if (cmp != 0) return cmp;
            return Long.compare(other.totalStayTimeMs, this.totalStayTimeMs);
        }
    }

    private List<LocationUsageRankItem> calculateLocationUsageRanks(long startTime, long endTime) {
        List<LocationUsageRankItem> result = new ArrayList<>();
        try {
            ActivityDatabase db = ActivityDatabase.getDatabase(context);
            List<StillLocation> stills = db.activityDao().getStillsFromRange(new Date(startTime), new Date(endTime));
            if (stills.isEmpty()) return result;

            Map<String, LocationUsageRankItem> locationMap = new HashMap<>();

            for (StillLocation still : stills) {
                if (Thread.currentThread().isInterrupted()) return result;
                if (still == null || still.getStartTimeDate() == null) continue;

                long stayStart = Math.max(still.getStartTimeDate().getTime(), startTime);
                long stayEnd = Math.min(
                        still.getEndTimeDate() != null ? still.getEndTimeDate().getTime() : System.currentTimeMillis(),
                        endTime
                );

                if (stayStart >= stayEnd) continue;

                long stayDurationMs = stayEnd - stayStart;

                Map<String, Long> pkgUsage = UsageStatsHelper.getUsageTimePerPackage(context, stayStart, stayEnd);
                long stayScreenTimeMs = 0;
                for (long duration : pkgUsage.values()) {
                    stayScreenTimeMs += duration;
                }

                String key;
                if (still.getPlaceId() != null) {
                    key = "place_" + still.getPlaceId();
                } else if (still.getPlaceName() != null && !still.getPlaceName().trim().isEmpty()) {
                    key = "name_" + still.getPlaceName().trim().toLowerCase(Locale.ROOT);
                } else if (still.getPlaceAddress() != null && !still.getPlaceAddress().trim().isEmpty()) {
                    key = "addr_" + still.getPlaceAddress().trim().toLowerCase(Locale.ROOT);
                } else if (still.getLat() != null && still.getLng() != null) {
                    key = String.format(Locale.US, "coord_%.3f_%.3f", still.getLat(), still.getLng());
                } else {
                    key = "still_" + still.getId();
                }

                LocationUsageRankItem rankItem = locationMap.get(key);
                if (rankItem == null) {
                    rankItem = new LocationUsageRankItem();
                    rankItem.locationKey = key;

                    String name = still.getPlaceName();
                    if (name == null || name.trim().isEmpty()) {
                        name = still.getPlaceAddress();
                    }
                    if (name == null || name.trim().isEmpty()) {
                        if (still.getLat() != null && still.getLng() != null) {
                            name = String.format(Locale.US, "Location (%.3f, %.3f)", still.getLat(), still.getLng());
                        } else {
                            name = "Unknown Location";
                        }
                    }
                    rankItem.placeName = name;
                    rankItem.placeAddress = still.getPlaceAddress();
                    rankItem.icon = still.getIcon();
                    rankItem.category = still.getCategory();
                    rankItem.color = still.getColor();
                    locationMap.put(key, rankItem);
                }

                rankItem.totalScreenTimeMs += stayScreenTimeMs;
                rankItem.totalStayTimeMs += stayDurationMs;

                for (Map.Entry<String, Long> appEntry : pkgUsage.entrySet()) {
                    rankItem.appUsageMap.merge(appEntry.getKey(), appEntry.getValue(), Long::sum);
                }
            }

            result.addAll(locationMap.values());
            Collections.sort(result);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    private void processAndBindUsageData(Map<String, Long> packageUsage, List<LocationUsageRankItem> locationRanks,
                                        String customTotalText, int version) {
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

        if (specificCategories.size() > 5) {
            // Keep top 5 specific categories
            for (int i = 0; i < 5; i++) {
                displayCategories.add(specificCategories.get(i));
            }

            // Combine all leftover specific categories into otherCategory
            for (int i = 5; i < specificCategories.size(); i++) {
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

        mainHandler.post(() -> {
            if (destroyed || version != loadVersion) return;
            renderUi(fTotalMs, fCategories, customTotalText);
            if (locationRanks != null) renderLocationRanks(locationRanks, layoutDailyLocationsList, tvDailyCardNoLocations);
        });
    }

    private void renderUi(long totalMs, List<CategoryUsage> categories, String customTotalText) {
        if (tvTotalTime != null) {
            if (customTotalText != null) {
                tvTotalTime.setText(customTotalText);
            } else {
                tvTotalTime.setText(formatDuration(totalMs));
            }
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

                    MaterialCardView cardIconBg = rowView.findViewById(R.id.card_category_icon_bg);
                    ImageView ivIcon = rowView.findViewById(R.id.iv_category_icon);
                    TextView tvName = rowView.findViewById(R.id.tv_category_name);
                    TextView tvTime = rowView.findViewById(R.id.tv_category_time);
                    TextView tvPercentage = rowView.findViewById(R.id.tv_category_percentage);
                    LinearProgressIndicator progressBar = rowView.findViewById(R.id.progress_category_bar);
                    TextView tvTopApp = rowView.findViewById(R.id.tv_category_top_app);

                    if (cardIconBg != null) {
                        cardIconBg.setCardBackgroundColor(ColorUtils.setAlphaComponent(color, 40));
                    }
                    if (ivIcon != null) {
                        ivIcon.setImageResource(AppCategoryHelper.getCategoryIconRes(cat.name));
                        ivIcon.setColorFilter(color);
                    }
                    if (tvName != null) {
                        tvName.setText(cat.name);
                    }

                    int pct = totalMs > 0 ? (int) Math.round((double) cat.totalMs * 100 / totalMs) : 0;
                    pct = Math.min(100, Math.max(0, pct));

                    if (tvTime != null) {
                        tvTime.setText(formatDuration(cat.totalMs));
                    }
                    if (tvPercentage != null) {
                        tvPercentage.setText(pct + "% of total");
                    }
                    if (progressBar != null) {
                        progressBar.setProgress(pct);
                        progressBar.setIndicatorColor(color);
                    }

                    if (tvTopApp != null) {
                        String topAppText = getTopAppsTextForCategory(cat.appMap);
                        if (cat.totalMs > 0 && topAppText != null && !topAppText.isEmpty()) {
                            tvTopApp.setText("Top apps: " + topAppText);
                            tvTopApp.setVisibility(View.VISIBLE);
                        } else {
                            tvTopApp.setVisibility(View.GONE);
                        }
                    }

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

    private void renderLocationRanks(List<LocationUsageRankItem> locationRanks, LinearLayout listContainer, TextView noDataView) {
        if (listContainer == null) return;
        listContainer.removeAllViews();

        if (locationRanks == null || locationRanks.isEmpty()) {
            if (noDataView != null) noDataView.setVisibility(View.VISIBLE);
            return;
        }

        if (noDataView != null) noDataView.setVisibility(View.GONE);

        LayoutInflater inflater = LayoutInflater.from(context);

        for (LocationUsageRankItem item : locationRanks) {
            View rowView = inflater.inflate(R.layout.item_location_usage_rank, listContainer, false);

            MaterialCardView cardIcon = rowView.findViewById(R.id.card_location_icon);
            ImageView ivIcon = rowView.findViewById(R.id.iv_location_icon);
            TextView tvName = rowView.findViewById(R.id.tv_location_name);
            TextView tvStayTime = rowView.findViewById(R.id.tv_location_stay_time);
            TextView tvScreenTime = rowView.findViewById(R.id.tv_location_screen_time);
            TextView tvPercentage = rowView.findViewById(R.id.tv_location_percentage);
            LinearProgressIndicator progress = rowView.findViewById(R.id.progress_usage_percent);
            TextView tvTopApps = rowView.findViewById(R.id.tv_top_apps_label);

            if (tvName != null) {
                tvName.setText(item.placeName);
            }

            if (tvStayTime != null) {
                tvStayTime.setText("Visit: " + formatDuration(item.totalStayTimeMs));
            }

            if (tvScreenTime != null) {
                if (item.totalScreenTimeMs > 0) {
                    tvScreenTime.setText(formatDuration(item.totalScreenTimeMs) + " phone");
                    tvScreenTime.setTextColor(ContextCompat.getColor(context, R.color.primary));
                } else {
                    tvScreenTime.setText("Phone Free");
                    tvScreenTime.setTextColor(ContextCompat.getColor(context, R.color.green));
                }
            }

            int percentage = item.totalStayTimeMs > 0
                    ? (int) Math.round((double) item.totalScreenTimeMs / item.totalStayTimeMs * 100)
                    : 0;
            percentage = Math.min(100, Math.max(0, percentage));

            if (tvPercentage != null) {
                tvPercentage.setText(percentage + "% of visit");
            }

            if (progress != null) {
                progress.setProgress(percentage);
                progress.setIndicatorColor(getUsageColorForPercentage(context, percentage));
            }

            StillLocation dummyStill = new StillLocation();
            dummyStill.setIcon(item.icon);
            dummyStill.setCategory(item.category);
            dummyStill.setColor(item.color);

            int iconRes = ColorAndIcons.getStillIconRes(dummyStill);
            if (ivIcon != null) {
                ivIcon.setImageResource(iconRes);
            }

            int colorVal = ColorAndIcons.getStillColor(dummyStill, context);
            if (cardIcon != null) {
                cardIcon.setCardBackgroundColor(ColorUtils.setAlphaComponent(colorVal, 40));
                if (ivIcon != null) {
                    ivIcon.setColorFilter(colorVal);
                }
            }

            if (tvTopApps != null) {
                List<UsageStatsHelper.AppUsageInfo> topApps = UsageStatsHelper.getSortedAppUsageList(context, item.appUsageMap);
                if (!topApps.isEmpty()) {
                    StringBuilder sb = new StringBuilder("Top apps: ");
                    int limit = Math.min(2, topApps.size());
                    for (int i = 0; i < limit; i++) {
                        if (i > 0) sb.append(", ");
                        sb.append(topApps.get(i).getAppName())
                          .append(" (")
                          .append(formatDuration(topApps.get(i).getTotalTimeInForegroundMs()))
                          .append(")");
                    }
                    tvTopApps.setText(sb.toString());
                    tvTopApps.setVisibility(View.VISIBLE);
                } else {
                    tvTopApps.setVisibility(View.GONE);
                }
            }

            listContainer.addView(rowView);
        }
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

    private String getTopAppsTextForCategory(Map<String, Long> categoryAppMap) {
        if (categoryAppMap == null || categoryAppMap.isEmpty()) return null;

        List<UsageStatsHelper.AppUsageInfo> sortedList = UsageStatsHelper.getSortedAppUsageList(context, categoryAppMap);
        if (sortedList.isEmpty()) return null;

        StringBuilder sb = new StringBuilder();
        int limit = Math.min(2, sortedList.size());
        for (int i = 0; i < limit; i++) {
            UsageStatsHelper.AppUsageInfo app = sortedList.get(i);
            if (i > 0) sb.append(", ");
            sb.append(app.getAppName())
              .append(" (")
              .append(formatDuration(app.getTotalTimeInForegroundMs()))
              .append(")");
        }
        return sb.toString();
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
