package com.example.myapplication.mainScreen.dashboardScreen.model;

import java.util.List;

public class DashboardData {
    private final long activeRealWorldTimeMs;
    private final long digitalScreenTimeMs;
    private final List<ContextualInsight> insights;
    private final List<LocationUsageItem> locationUsageItems;
    private final List<TimeDrainItem> timeDrainItems;

    public DashboardData(long activeRealWorldTimeMs,
                         long digitalScreenTimeMs,
                         List<ContextualInsight> insights,
                         List<LocationUsageItem> locationUsageItems,
                         List<TimeDrainItem> timeDrainItems) {
        this.activeRealWorldTimeMs = activeRealWorldTimeMs;
        this.digitalScreenTimeMs = digitalScreenTimeMs;
        this.insights = insights;
        this.locationUsageItems = locationUsageItems;
        this.timeDrainItems = timeDrainItems;
    }

    public long getActiveRealWorldTimeMs() {
        return activeRealWorldTimeMs;
    }

    public long getDigitalScreenTimeMs() {
        return digitalScreenTimeMs;
    }

    public List<ContextualInsight> getInsights() {
        return insights;
    }

    public List<LocationUsageItem> getLocationUsageItems() {
        return locationUsageItems;
    }

    public List<TimeDrainItem> getTimeDrainItems() {
        return timeDrainItems;
    }
}
