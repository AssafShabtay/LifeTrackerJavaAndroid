package com.example.myapplication.tracking.usage;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
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
            mode = appOps.checkOpNoThrow(
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
        try {
            // try to open the usage access for the specific app
            Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            intent.setData(Uri.parse("package:" + context.getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            // open the general Usage Access settings list
            Intent fallbackIntent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(fallbackIntent);
        }
    }


    public static Map<String, Long> getUsageTimePerPackage(Context context, long startTime, long endTime) {
        Map<String, Long> usageMap = new HashMap<>();
        if (!hasUsageAccessPermission(context) || startTime >= endTime) {
            return usageMap;
        }

        UsageStatsManager usageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usageStatsManager == null) return usageMap;

        UsageEvents usageEvents = usageStatsManager.queryEvents(startTime, endTime);
        if (usageEvents == null) return usageMap;

        UsageEvents.Event currentEvent = new UsageEvents.Event();
        Map<String, Long> lastAppStart = new HashMap<>();

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(currentEvent);
            String pkg = currentEvent.getPackageName();
            if (pkg == null) continue;

            int eventType = currentEvent.getEventType();
            long timestamp = currentEvent.getTimeStamp();

            if (eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                lastAppStart.put(pkg, timestamp);
            } else if (eventType == UsageEvents.Event.ACTIVITY_PAUSED) {
                Long start = lastAppStart.remove(pkg);
                if (start != null) {
                    long duration = timestamp - start;
                    if (duration > 0) {
                        usageMap.merge(pkg, duration, Long::sum); // add duration to existing or create new entry
                    }
                }
            }
        }

        // Account for any app that doesn't have an endtime
        for (Map.Entry<String, Long> entry : lastAppStart.entrySet()) {
            long duration = endTime - entry.getValue();
            if (duration > 0) {
                usageMap.merge(entry.getKey(), duration, Long::sum); // add duration to existing or create new entry
            }
        }

        return usageMap;
    }

    public static long getTotalScreenTimeMs(Context context, long startTime, long endTime) {
        /// Calculates total screen time
        Map<String, Long> packageUsage = getUsageTimePerPackage(context, startTime, endTime);
        long total = 0;
        for (long duration : packageUsage.values()) {
            total += duration;
        }
        return total;
    }

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
            if (type == 18) { // KEYGUARD_HIDDEN is event 18
                unlockCount++;
            }
        }
        return unlockCount;
    }

    public static long[] getHourlyUsageBuckets(Context context, long startTime, long endTime) {
        long[] hourlyBucketsMs = new long[24];
        if (!hasUsageAccessPermission(context) || startTime >= endTime) return hourlyBucketsMs;
        UsageStatsManager usageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usageStatsManager == null) return hourlyBucketsMs;

        UsageEvents usageEvents = usageStatsManager.queryEvents(startTime, endTime);
        if (usageEvents == null) return hourlyBucketsMs;

        UsageEvents.Event currentEvent = new UsageEvents.Event();
        Map<String, Long> lastAppStart = new HashMap<>();
        Calendar cal = Calendar.getInstance();

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(currentEvent);
            String pkg = currentEvent.getPackageName();
            if (pkg == null) continue;

            int eventType = currentEvent.getEventType();
            long timestamp = currentEvent.getTimeStamp();

            if (eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                lastAppStart.put(pkg, timestamp);
            } else if (eventType == UsageEvents.Event.ACTIVITY_PAUSED) {
                Long start = lastAppStart.remove(pkg);
                if (start != null && timestamp > start) {
                    addDurationToHourlyBuckets(hourlyBucketsMs, start, timestamp, cal);
                }
            }
        }

        // Account for any app that doesn't have an endtime
        for (Map.Entry<String, Long> entry : lastAppStart.entrySet()) {
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

    public static String getAppCategoryName(PackageManager pm, String pkg) {
        if (pkg == null) return "Other";
        String lowerPkg = pkg.toLowerCase(Locale.ROOT);

        // 1. Query ApplicationInfo.category on API 26+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && pm != null) {
            try {
                ApplicationInfo appInfo = pm.getApplicationInfo(pkg, 0);
                int cat = appInfo.category;
                switch (cat) {
                    case ApplicationInfo.CATEGORY_GAME:
                        return "Games";
                    case ApplicationInfo.CATEGORY_AUDIO:
                        return "Audio";
                    case ApplicationInfo.CATEGORY_VIDEO:
                        return "Video";
                    case ApplicationInfo.CATEGORY_IMAGE:
                        return "Photos";
                    case ApplicationInfo.CATEGORY_SOCIAL:
                        return "Social";
                    case ApplicationInfo.CATEGORY_NEWS:
                        return "News";
                    case ApplicationInfo.CATEGORY_MAPS:
                        return "Navigation";
                    case ApplicationInfo.CATEGORY_PRODUCTIVITY:
                        return "Productivity";
                    case ApplicationInfo.CATEGORY_ACCESSIBILITY:
                        return "Tools";
                }
            } catch (Exception ignored) {
            }
        }

        // 2. Keyword fallback matching
        if (lowerPkg.contains("instagram") || lowerPkg.contains("facebook") || lowerPkg.contains("whatsapp") ||
                lowerPkg.contains("twitter") || lowerPkg.contains("tiktok") || lowerPkg.contains("telegram") ||
                lowerPkg.contains("snapchat") || lowerPkg.contains("reddit") || lowerPkg.contains("linkedin") ||
                lowerPkg.contains("pinterest") || lowerPkg.contains("messenger") || lowerPkg.contains("discord") ||
                lowerPkg.contains("x.android") || lowerPkg.contains("threads")) {
            return "Social";
        }

        if (lowerPkg.contains("youtube") || lowerPkg.contains("netflix") || lowerPkg.contains("spotify") ||
                lowerPkg.contains("twitch") || lowerPkg.contains("hulu") || lowerPkg.contains("disney") ||
                lowerPkg.contains("vlc") || lowerPkg.contains("music") || lowerPkg.contains("podcast") ||
                lowerPkg.contains("primevideo") || lowerPkg.contains("hbo")) {
            return "Entertainment";
        }

        if (lowerPkg.contains("chrome") || lowerPkg.contains("browser") || lowerPkg.contains("firefox") ||
                lowerPkg.contains("opera") || lowerPkg.contains("edge") || lowerPkg.contains("duckduckgo")) {
            return "Browsing";
        }

        if (lowerPkg.contains("gmail") || lowerPkg.contains("outlook") || lowerPkg.contains("slack") ||
                lowerPkg.contains("teams") || lowerPkg.contains("docs") || lowerPkg.contains("sheets") ||
                lowerPkg.contains("calendar") || lowerPkg.contains("notes") || lowerPkg.contains("keep") ||
                lowerPkg.contains("calculator") || lowerPkg.contains("clock") || lowerPkg.contains("android.studio") ||
                lowerPkg.contains("drive") || lowerPkg.contains("zoom") || lowerPkg.contains("notion") ||
                lowerPkg.contains("word") || lowerPkg.contains("excel")) {
            return "Productivity";
        }

        if (lowerPkg.contains("game") || lowerPkg.contains("unity") || lowerPkg.contains("supercell") ||
                lowerPkg.contains("roblox") || lowerPkg.contains("pubg") || lowerPkg.contains("chess") ||
                lowerPkg.contains("minecraft") || lowerPkg.contains("candycrush")) {
            return "Games";
        }

        if (lowerPkg.contains("map") || lowerPkg.contains("moovit") || lowerPkg.contains("waze") || lowerPkg.contains("uber") ||
                lowerPkg.contains("lyft") || lowerPkg.contains("transit")) {
            return "Navigation";
        }

        return "Other";
    }

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

    public static List<AppUsageInfo> getSortedAppUsageList(Context context, Map<String, Long> packageUsage) {
        PackageManager pm = context.getPackageManager();
        List<AppUsageInfo> result = new ArrayList<>();

        for (Map.Entry<String, Long> entry : packageUsage.entrySet()) {
            String pkg = entry.getKey();
            long timeMs = entry.getValue();
            if (timeMs <= 0 || pkg == null) continue;

            //// Filter internal android system daemons that are not user apps
            //if (pkg.equals("android") || pkg.equals("com.google.android.gms") || pkg.equals("com.google.android.googlequicksearchbox:search")) {
            //    continue;
            //}

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

            //  format a clean friendly name
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

        Map<String, Long> packageUsage = getUsageTimePerPackage(context, startTime, endTime);
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
