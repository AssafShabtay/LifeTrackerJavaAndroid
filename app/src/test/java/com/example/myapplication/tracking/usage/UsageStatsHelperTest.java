package com.example.myapplication.tracking.usage;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Locale;

public class UsageStatsHelperTest {
    @Test
    public void peakHourIgnoresEmptyOrSubMinuteUsage() {
        long[] hourlyUsage = new long[24];
        assertEquals("N/A", UsageStatsHelper.getPeakUsageHour(hourlyUsage));
        hourlyUsage[8] = 59_999;
        assertEquals("N/A", UsageStatsHelper.getPeakUsageHour(hourlyUsage));
    }

    @Test
    public void peakHourUsesLargestHourAndKeepsEarlierHourOnTie() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.US);
            long[] hourlyUsage = new long[24];
            hourlyUsage[8] = 60_000;
            hourlyUsage[14] = 120_000;
            hourlyUsage[18] = 120_000;
            assertEquals("2:00 PM", UsageStatsHelper.getPeakUsageHour(hourlyUsage));
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    public void peakHourFormatsMidnightAndNoon() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.US);
            long[] hourlyUsage = new long[24];
            hourlyUsage[0] = 60_000;
            assertEquals("12:00 AM", UsageStatsHelper.getPeakUsageHour(hourlyUsage));
            hourlyUsage[12] = 120_000;
            assertEquals("12:00 PM", UsageStatsHelper.getPeakUsageHour(hourlyUsage));
        } finally {
            Locale.setDefault(original);
        }
    }
}
