package com.example.myapplication.tracking.usage;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Process;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class UsageStatsHelper {

    public static class AppUsageInfo {
        private final String packageName;
        private final String appName;
        private final Drawable appIcon;
        private long totalTimeInForegroundMs;

        public AppUsageInfo(String packageName, String appName, Drawable appIcon, long totalTimeInForegroundMs) {
            this.packageName = packageName;
            this.appName = appName;
            this.appIcon = appIcon;
            this.totalTimeInForegroundMs = totalTimeInForegroundMs;
        }

        public String getPackageName() {
            return packageName;
        }

        public String getAppName() {
            return appName;
        }

        public Drawable getAppIcon() {
            return appIcon;
        }

        public long getTotalTimeInForegroundMs() {
            return totalTimeInForegroundMs;
        }

    }

    public static boolean hasUsageAccessPermission(Context context) {
        AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        if (appOps == null) return false;
        int mode;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mode = appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.getPackageName()
            );
        } else {
            mode = appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.getPackageName()
            );
        }
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    public static void openUsageAccessSettings(Context context) {
        Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    /**
     * Accurately aggregates foreground duration per package between startTime and endTime
     * using UsageEvents.
     */
    public static Map<String, Long> getForegroundTimePerPackage(Context context, long startTime, long endTime) {
        Map<String, Long> usageMap = new HashMap<>();
        if (!hasUsageAccessPermission(context) || startTime >= endTime) {
            return usageMap;
        }

        UsageStatsManager usageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usageStatsManager == null) return usageMap;

        UsageEvents usageEvents = usageStatsManager.queryEvents(startTime, endTime);
        if (usageEvents == null) return usageMap;

        UsageEvents.Event currentEvent = new UsageEvents.Event();
        Map<String, Long> lastForegroundStart = new HashMap<>();

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(currentEvent);
            String pkg = currentEvent.getPackageName();
            if (pkg == null) continue;

            int eventType = currentEvent.getEventType();
            long timestamp = currentEvent.getTimeStamp();

            if (eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                lastForegroundStart.put(pkg, timestamp);
            } else if (eventType == UsageEvents.Event.ACTIVITY_PAUSED) {
                Long start = lastForegroundStart.remove(pkg);
                if (start != null) {
                    long duration = timestamp - start;
                    if (duration > 0) {
                        Long existing = usageMap.get(pkg);
                        usageMap.put(pkg, (existing != null ? existing : 0L) + duration);
                    }
                }
            }
        }

        // Account for any app still in foreground at endTime
        for (Map.Entry<String, Long> entry : lastForegroundStart.entrySet()) {
            long duration = endTime - entry.getValue();
            if (duration > 0) {
                Long existing = usageMap.get(entry.getKey());
                usageMap.put(entry.getKey(), (existing != null ? existing : 0L) + duration);
            }
        }

        return usageMap;
    }

    /**
     * Calculates total screen time (foreground app time) in milliseconds in the given interval.
     */
    public static long getTotalScreenTimeMs(Context context, long startTime, long endTime) {
        Map<String, Long> packageUsage = getForegroundTimePerPackage(context, startTime, endTime);
        long total = 0;
        for (long duration : packageUsage.values()) {
            total += duration;
        }
        return total;
    }

    /**
     * Counts device unlock / interactive pickups in the given interval.
     */
    public static int getDeviceUnlockCount(Context context, long startTime, long endTime) {
        if (!hasUsageAccessPermission(context) || startTime >= endTime) return 0;
        UsageStatsManager usageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usageStatsManager == null) return 0;

        UsageEvents usageEvents = usageStatsManager.queryEvents(startTime, endTime);
        if (usageEvents == null) return 0;

        int unlockCount = 0;
        UsageEvents.Event currentEvent = new UsageEvents.Event();

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(currentEvent);
            int type = currentEvent.getEventType();
            // KEYGUARD_HIDDEN is event 18 in API 28+, SCREEN_INTERACTIVE is event 15
            if (type == 18 || type == 15) {
                unlockCount++;
            }
        }
        return unlockCount;
    }

    /**
     * Calculates the 24 hourly buckets (in milliseconds) for usage between startTime and endTime.
     */
    public static long[] getHourlyUsageBuckets(Context context, long startTime, long endTime) {
        long[] hourlyBucketsMs = new long[24];
        if (!hasUsageAccessPermission(context) || startTime >= endTime) return hourlyBucketsMs;
        UsageStatsManager usageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usageStatsManager == null) return hourlyBucketsMs;

        UsageEvents usageEvents = usageStatsManager.queryEvents(startTime, endTime);
        if (usageEvents == null) return hourlyBucketsMs;

        UsageEvents.Event currentEvent = new UsageEvents.Event();
        Map<String, Long> lastForegroundStart = new HashMap<>();
        Calendar cal = Calendar.getInstance();

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(currentEvent);
            String pkg = currentEvent.getPackageName();
            if (pkg == null) continue;

            int eventType = currentEvent.getEventType();
            long timestamp = currentEvent.getTimeStamp();

            if (eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                lastForegroundStart.put(pkg, timestamp);
            } else if (eventType == UsageEvents.Event.ACTIVITY_PAUSED) {
                Long start = lastForegroundStart.remove(pkg);
                if (start != null && timestamp > start) {
                    addDurationToHourlyBuckets(hourlyBucketsMs, start, timestamp, cal);
                }
            }
        }

        for (Map.Entry<String, Long> entry : lastForegroundStart.entrySet()) {
            if (endTime > entry.getValue()) {
                addDurationToHourlyBuckets(hourlyBucketsMs, entry.getValue(), endTime, cal);
            }
        }

        return hourlyBucketsMs;
    }

    private static void addDurationToHourlyBuckets(long[] buckets, long startMs, long endMs, Calendar cal) {
        if (startMs >= endMs) return;

        cal.setTimeInMillis(startMs);
        int startHour = cal.get(Calendar.HOUR_OF_DAY);

        cal.setTimeInMillis(endMs);
        int endHour = cal.get(Calendar.HOUR_OF_DAY);

        if (startHour == endHour) {
            buckets[startHour] += (endMs - startMs);
        } else {
            // Split across hour boundaries
            cal.setTimeInMillis(startMs);
            cal.set(Calendar.MINUTE, 59);
            cal.set(Calendar.SECOND, 59);
            cal.set(Calendar.MILLISECOND, 999);
            long endOfFirstHour = Math.min(cal.getTimeInMillis() + 1, endMs);
            buckets[startHour] += (endOfFirstHour - startMs);

            long cursor = endOfFirstHour;
            while (cursor < endMs) {
                cal.setTimeInMillis(cursor);
                int curHour = cal.get(Calendar.HOUR_OF_DAY);
                cal.add(Calendar.HOUR_OF_DAY, 1);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                long nextBoundary = Math.min(cal.getTimeInMillis(), endMs);

                if (curHour >= 0 && curHour < 24) {
                    buckets[curHour] += (nextBoundary - cursor);
                }
                cursor = nextBoundary;
            }
        }
    }

    /**
     * Calculates peak usage hour formatted string (e.g. "2:00 PM").
     */
    public static String getPeakUsageHour(Context context, long startTime, long endTime) {
        long[] hourlyBucketsMs = getHourlyUsageBuckets(context, startTime, endTime);

        int maxHour = -1;
        long maxDuration = 0;
        for (int h = 0; h < 24; h++) {
            if (hourlyBucketsMs[h] > maxDuration) {
                maxDuration = hourlyBucketsMs[h];
                maxHour = h;
            }
        }

        if (maxHour == -1 || maxDuration < 60000) {
            return "N/A";
        }

        int hour12 = maxHour % 12 == 0 ? 12 : maxHour % 12;
        String amPm = maxHour >= 12 ? "PM" : "AM";
        return String.format(Locale.getDefault(), "%d:00 %s", hour12, amPm);
    }

    /**
     * Returns a sorted list of AppUsageInfo with app names and icons resolved.
     * If an app name or icon cannot be resolved (system services/hidden daemons),
     * a clean user-friendly fallback is applied or the item is filtered if irrelevant.
     */
    public static List<AppUsageInfo> getSortedAppUsageList(Context context, Map<String, Long> packageUsage) {
        PackageManager pm = context.getPackageManager();
        List<AppUsageInfo> result = new ArrayList<>();

        for (Map.Entry<String, Long> entry : packageUsage.entrySet()) {
            String pkg = entry.getKey();
            long timeMs = entry.getValue();
            if (timeMs <= 0 || pkg == null) continue;

            // Filter internal android system daemons that are not user apps
            if (pkg.equals("android") || pkg.equals("com.google.android.gms") || pkg.equals("com.google.android.googlequicksearchbox:search")) {
                continue;
            }

            String appName = null;
            Drawable icon = null;
            try {
                ApplicationInfo appInfo = pm.getApplicationInfo(pkg, 0);
                CharSequence label = pm.getApplicationLabel(appInfo);
                if (label != null && !label.toString().trim().isEmpty()) {
                    appName = label.toString().trim();
                }
                icon = pm.getApplicationIcon(appInfo);
            } catch (Exception ignored) {
            }

            // If appName is still null or equals the raw package name, format a clean friendly name
            if (appName == null || appName.equals(pkg) || appName.contains(".")) {
                appName = cleanPackageNameToAppName(pkg);
            }

            // Fallback icon if null
            if (icon == null) {
                icon = pm.getDefaultActivityIcon();
            }

            result.add(new AppUsageInfo(pkg, appName, icon, timeMs));
        }

        result.sort((a, b) -> Long.compare(b.getTotalTimeInForegroundMs(), a.getTotalTimeInForegroundMs()));
        return result;
    }
    public static TimelineItemUsageData getUsageDataForPeriod(Context context, long startTime, long endTime) {
        if (!hasUsageAccessPermission(context) || startTime >= endTime) {
            return null;
        }

        Map<String, Long> packageUsage = getForegroundTimePerPackage(context, startTime, endTime);
        long totalScreenTimeMs = 0;
        for (long duration : packageUsage.values()) {
            totalScreenTimeMs += duration;
        }

        long totalDurationMs = endTime - startTime;
        if (totalDurationMs <= 0) return null;

        int percentage = (int) Math.round((double) totalScreenTimeMs / totalDurationMs * 100);
        percentage = Math.min(100, Math.max(0, percentage));

        List<AppUsageInfo> sortedApps = getSortedAppUsageList(context, packageUsage);
        return new TimelineItemUsageData(totalScreenTimeMs, totalDurationMs, percentage, sortedApps);
    }

    private static String cleanPackageNameToAppName(String pkg) {
        if (pkg == null) return "App";
        int lastDot = pkg.lastIndexOf('.');
        String name = (lastDot >= 0 && lastDot < pkg.length() - 1) ? pkg.substring(lastDot + 1) : pkg;
        // Capitalize first letter
        if (!name.isEmpty()) {
            return Character.toUpperCase(name.charAt(0)) + (name.length() > 1 ? name.substring(1) : "");
        }
        return pkg;
    }
}
