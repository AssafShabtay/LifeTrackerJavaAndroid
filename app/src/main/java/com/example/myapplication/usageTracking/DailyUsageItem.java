package com.example.myapplication.usageTracking;

import java.util.Date;
import java.util.List;
public class DailyUsageItem {
    private final String dayLabel;
    private final Date date;
    private final long totalScreenTimeMs;
    private final int unlockCount;
    private final long[] hourlyUsageMs;
    private final List<UsageStatsHelper.AppUsageInfo> topApps;

    private final String peakHour;

    public DailyUsageItem(String dayLabel, Date date, long totalScreenTimeMs, int unlockCount,
                          long[] hourlyUsageMs, List<UsageStatsHelper.AppUsageInfo> topApps, String peakHour) {
        this.dayLabel = dayLabel;
        this.date = date;
        this.totalScreenTimeMs = totalScreenTimeMs;
        this.unlockCount = unlockCount;
        this.hourlyUsageMs = hourlyUsageMs != null ? hourlyUsageMs : new long[24];
        this.topApps = topApps;
        this.peakHour = peakHour;
    }

    public String getPeakHour() {
        return peakHour;
    }

    public String getDayLabel() {
        return dayLabel;
    }

    public Date getDate() {
        return date;
    }

    public long getTotalScreenTimeMs() {
        return totalScreenTimeMs;
    }

    public int getUnlockCount() {
        return unlockCount;
    }

    public long[] getHourlyUsageMs() {
        return hourlyUsageMs;
    }

    public List<UsageStatsHelper.AppUsageInfo> getTopApps() {
        return topApps;
    }
}
