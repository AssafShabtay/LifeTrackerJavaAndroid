package com.example.myapplication.mainScreen.dashboardScreen.model;

import com.example.myapplication.helpers.UsageStatsHelper;
import java.util.List;

public class LocationUsageItem {
    private final String placeName;
    private final String category;
    private final String icon;
    private final Integer color;
    private final long physicalDurationMs;
    private final long screenTimeMs;
    private final List<UsageStatsHelper.AppUsageInfo> topApps;

    public LocationUsageItem(String placeName, String category, String icon, Integer color,
                             long physicalDurationMs, long screenTimeMs,
                             List<UsageStatsHelper.AppUsageInfo> topApps) {
        this.placeName = placeName;
        this.category = category;
        this.icon = icon;
        this.color = color;
        this.physicalDurationMs = physicalDurationMs;
        this.screenTimeMs = screenTimeMs;
        this.topApps = topApps;
    }

    public String getPlaceName() {
        return placeName;
    }

    public String getCategory() {
        return category;
    }

    public String getIcon() {
        return icon;
    }

    public Integer getColor() {
        return color;
    }

    public long getPhysicalDurationMs() {
        return physicalDurationMs;
    }

    public long getScreenTimeMs() {
        return screenTimeMs;
    }

    public List<UsageStatsHelper.AppUsageInfo> getTopApps() {
        return topApps;
    }

    public int getScreenPercentageOfPhysicalTime() {
        if (physicalDurationMs <= 0) return 0;
        int pct = (int) Math.round(((double) screenTimeMs / (double) physicalDurationMs) * 100);
        return Math.min(100, Math.max(0, pct));
    }
}
