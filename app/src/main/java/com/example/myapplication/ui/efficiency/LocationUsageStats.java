package com.example.myapplication.ui.efficiency;
import com.example.myapplication.tracking.usage.UsageStatsHelper;

import java.util.ArrayList;
import java.util.List;

public class LocationUsageStats {
    private final String locationName;
    private final String category;
    private final String iconName;
    private final Integer color;
    private final long totalStayDurationMs;
    private final long totalScreenTimeMs;
    private final List<UsageStatsHelper.AppUsageInfo> topApps;

    public LocationUsageStats(String locationName, String category, String iconName, Integer color,
                              long totalStayDurationMs, long totalScreenTimeMs, List<UsageStatsHelper.AppUsageInfo> topApps) {
        this.locationName = locationName;
        this.category = category;
        this.iconName = iconName;
        this.color = color;
        this.totalStayDurationMs = totalStayDurationMs;
        this.totalScreenTimeMs = totalScreenTimeMs;
        this.topApps = topApps != null ? topApps : new ArrayList<>();
    }

    public String getLocationName() {
        return locationName;
    }

    public String getCategory() {
        return category;
    }

    public String getIconName() {
        return iconName;
    }

    public Integer getColor() {
        return color;
    }

    public long getTotalStayDurationMs() {
        return totalStayDurationMs;
    }

    public long getTotalScreenTimeMs() {
        return totalScreenTimeMs;
    }

    public List<UsageStatsHelper.AppUsageInfo> getTopApps() {
        return topApps;
    }

    public int getUsagePercentage() {
        if (totalStayDurationMs <= 0) return 0;
        int pct = (int) Math.round((double) totalScreenTimeMs / totalStayDurationMs * 100);
        return Math.min(100, Math.max(0, pct));
    }
}
