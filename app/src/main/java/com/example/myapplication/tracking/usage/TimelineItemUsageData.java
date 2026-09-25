package com.example.myapplication.tracking.usage;

import java.util.ArrayList;
import java.util.List;

/**
 * Encapsulates the digital usage statistics for a specific timeline item (visit / movement / stop).
 */
public class TimelineItemUsageData {
    private final long screenTimeMs;
    private final long durationMs;
    private final int percentage;
    private final List<UsageStatsHelper.AppUsageInfo> topApps;

    public TimelineItemUsageData(long screenTimeMs, long durationMs, int percentage, List<UsageStatsHelper.AppUsageInfo> topApps) {
        this.screenTimeMs = screenTimeMs;
        this.durationMs = durationMs;
        this.percentage = percentage;
        this.topApps = topApps != null ? topApps : new ArrayList<>();
    }

    public long getScreenTimeMs() {
        return screenTimeMs;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public int getPercentage() {
        return percentage;
    }

    public List<UsageStatsHelper.AppUsageInfo> getTopApps() {
        return topApps;
    }
}
