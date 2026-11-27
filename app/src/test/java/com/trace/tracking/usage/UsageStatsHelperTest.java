package com.trace.tracking.usage;

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

    @Test
    public void testUnionDurationWithOverlappingRanges() {
        java.util.List<UsageStatsHelper.TimeRange> ranges = new java.util.ArrayList<>();
        // 8:00 - 8:30 (30 mins)
        ranges.add(new UsageStatsHelper.TimeRange(1000, 31000));
        // 8:15 - 8:45 (30 mins overlapping -> 8:00 to 8:45 = 45 mins)
        ranges.add(new UsageStatsHelper.TimeRange(16000, 46000));

        long unionMs = UsageStatsHelper.calculateUnionDuration(ranges);
        assertEquals(45000, unionMs);
    }

    @Test
    public void testUnionDurationWithDisjointRanges() {
        java.util.List<UsageStatsHelper.TimeRange> ranges = new java.util.ArrayList<>();
        ranges.add(new UsageStatsHelper.TimeRange(1000, 5000));   // 4000 ms
        ranges.add(new UsageStatsHelper.TimeRange(10000, 15000)); // 5000 ms

        long unionMs = UsageStatsHelper.calculateUnionDuration(ranges);
        assertEquals(9000, unionMs);
    }

    @Test
    public void testIgnoredPackages() {
        org.junit.Assert.assertTrue(UsageStatsHelper.isIgnoredPackage("com.google.android.projection.gearhead"));
        org.junit.Assert.assertTrue(UsageStatsHelper.isIgnoredPackage("android"));
        org.junit.Assert.assertTrue(UsageStatsHelper.isIgnoredPackage("com.google.android.gms"));
        org.junit.Assert.assertFalse(UsageStatsHelper.isIgnoredPackage("com.whatsapp"));
        org.junit.Assert.assertFalse(UsageStatsHelper.isIgnoredPackage("com.google.android.youtube"));
    }
}
