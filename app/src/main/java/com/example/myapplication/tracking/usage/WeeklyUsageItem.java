package com.example.myapplication.tracking.usage;

import java.util.Date;
import java.util.List;

public class WeeklyUsageItem {
    private final String weekLabel;
    private final Date startDate;
    private final Date endDate;
    private final long totalScreenTimeMs;
    private final long dailyAverageScreenTimeMs;
    private final int totalUnlockCount;
    private final int dailyAverageUnlocks;
    private final long idleScreenTimeMs;
    private final long[] dailyUsageMs;
    private final String[] dayLabels;
    private final String peakDayLabel;
    private final List<UsageStatsHelper.AppUsageInfo> topApps;
    private final UsageStatsHelper.DoomscrollStats doomscrollStats;

    public WeeklyUsageItem(String weekLabel, Date startDate, Date endDate,
                           long totalScreenTimeMs, long dailyAverageScreenTimeMs,
                           int totalUnlockCount, int dailyAverageUnlocks,
                           long idleScreenTimeMs, long[] dailyUsageMs,
                           String[] dayLabels, String peakDayLabel,
                           List<UsageStatsHelper.AppUsageInfo> topApps,
                           UsageStatsHelper.DoomscrollStats doomscrollStats) {
        this.weekLabel = weekLabel;
        this.startDate = startDate;
        this.endDate = endDate;
        this.totalScreenTimeMs = totalScreenTimeMs;
        this.dailyAverageScreenTimeMs = dailyAverageScreenTimeMs;
        this.totalUnlockCount = totalUnlockCount;
        this.dailyAverageUnlocks = dailyAverageUnlocks;
        this.idleScreenTimeMs = idleScreenTimeMs;
        this.dailyUsageMs = dailyUsageMs != null ? dailyUsageMs : new long[7];
        this.dayLabels = dayLabels != null ? dayLabels : new String[]{"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        this.peakDayLabel = peakDayLabel;
        this.topApps = topApps;
        this.doomscrollStats = doomscrollStats;
    }

    public String getWeekLabel() {
        return weekLabel;
    }

    public Date getStartDate() {
        return startDate;
    }

    public Date getEndDate() {
        return endDate;
    }

    public long getTotalScreenTimeMs() {
        return totalScreenTimeMs;
    }

    public long getDailyAverageScreenTimeMs() {
        return dailyAverageScreenTimeMs;
    }

    public int getTotalUnlockCount() {
        return totalUnlockCount;
    }

    public int getDailyAverageUnlocks() {
        return dailyAverageUnlocks;
    }

    public long getIdleScreenTimeMs() {
        return idleScreenTimeMs;
    }

    public long[] getDailyUsageMs() {
        return dailyUsageMs;
    }

    public String[] getDayLabels() {
        return dayLabels;
    }

    public String getPeakDayLabel() {
        return peakDayLabel;
    }

    public List<UsageStatsHelper.AppUsageInfo> getTopApps() {
        return topApps;
    }

    public UsageStatsHelper.DoomscrollStats getDoomscrollStats() {
        return doomscrollStats;
    }
}
