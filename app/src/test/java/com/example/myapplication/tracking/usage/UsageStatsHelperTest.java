package com.example.myapplication.tracking.usage;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Locale;

public class UsageStatsHelperTest {
    @Test
    public void peakHourIgnoresEmptyOrSubMinuteUsage() {
        long[] buckets = new long[24];
        assertEquals("N/A", UsageStatsHelper.getPeakUsageHour(buckets));
        buckets[8] = 59_999;
        assertEquals("N/A", UsageStatsHelper.getPeakUsageHour(buckets));
    }

    @Test
    public void peakHourUsesLargestBucketAndKeepsEarlierHourOnTie() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.US);
            long[] buckets = new long[24];
            buckets[8] = 60_000;
            buckets[14] = 120_000;
            buckets[18] = 120_000;
            assertEquals("2:00 PM", UsageStatsHelper.getPeakUsageHour(buckets));
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    public void peakHourFormatsMidnightAndNoon() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.US);
            long[] buckets = new long[24];
            buckets[0] = 60_000;
            assertEquals("12:00 AM", UsageStatsHelper.getPeakUsageHour(buckets));
            buckets[12] = 120_000;
            assertEquals("12:00 PM", UsageStatsHelper.getPeakUsageHour(buckets));
        } finally {
            Locale.setDefault(original);
        }
    }
}
