package com.example.myapplication.tracking.usage;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Process;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CancellationException;

public class  UsageStatsHelper {

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


    public static boolean isIgnoredPackage(String pkg) {
        if (pkg == null || pkg.trim().isEmpty()) return true;
        String lower = pkg.trim().toLowerCase(Locale.ROOT);
        return lower.equals("android") ||
                lower.equals("com.google.android.gms") ||
                lower.equals("com.google.android.googlequicksearchbox:search") ||
                lower.equals("com.google.android.projection.gearhead");
    }

    public static class TimeRange {
        public final long start;
        public final long end;

        public TimeRange(long start, long end) {
            this.start = start;
            this.end = end;
        }
    }

    public static long calculateUnionDuration(List<TimeRange> ranges) {
        if (ranges == null || ranges.isEmpty()) return 0;

        List<TimeRange> valid = new ArrayList<>();
        for (TimeRange r : ranges) {
            if (r != null && r.start < r.end) {
                valid.add(r);
            }
        }
        if (valid.isEmpty()) return 0;

        valid.sort(Comparator.comparingLong(r -> r.start));

        long totalUnionMs = 0;
        long currentStart = -1;
        long currentEnd = -1;

        for (TimeRange range : valid) {
            if (currentStart == -1) {
                currentStart = range.start;
                currentEnd = range.end;
            } else if (range.start <= currentEnd) {
                currentEnd = Math.max(currentEnd, range.end);
            } else {
                totalUnionMs += (currentEnd - currentStart);
                currentStart = range.start;
                currentEnd = range.end;
            }
        }

        if (currentStart != -1) {
            totalUnionMs += (currentEnd - currentStart);
        }

        return totalUnionMs;
    }

    public static List<TimeRange> getActiveTimeRanges(Context context, long startTime, long endTime) {
        List<TimeRange> ranges = new ArrayList<>();
        if (!hasUsageAccessPermission(context) || startTime >= endTime) return ranges;

        UsageStatsManager usageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usageStatsManager == null) return ranges;

        UsageEvents usageEvents = usageStatsManager.queryEvents(startTime, endTime);
        if (usageEvents == null) return ranges;

        UsageEvents.Event currentEvent = new UsageEvents.Event();
        Map<String, Long> lastAppStart = new HashMap<>();

        while (usageEvents.hasNextEvent()) {
            if (Thread.currentThread().isInterrupted()) throw new CancellationException();
            usageEvents.getNextEvent(currentEvent);
            String pkg = currentEvent.getPackageName();
            if (pkg == null || isIgnoredPackage(pkg)) continue;

            int eventType = currentEvent.getEventType();
            long timestamp = currentEvent.getTimeStamp();

            if (eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                lastAppStart.put(pkg, timestamp);
            } else if (eventType == UsageEvents.Event.ACTIVITY_PAUSED) {
                Long start = lastAppStart.remove(pkg);
                if (start != null && timestamp > start) {
                    ranges.add(new TimeRange(Math.max(start, startTime), Math.min(timestamp, endTime)));
                }
            }
        }

        // Account for any app that doesn't have an endtime
        for (Map.Entry<String, Long> entry : lastAppStart.entrySet()) {
            long start = entry.getValue();
            if (endTime > start) {
                ranges.add(new TimeRange(Math.max(start, startTime), endTime));
            }
        }

        return ranges;
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
            if (Thread.currentThread().isInterrupted()) throw new CancellationException();
            usageEvents.getNextEvent(currentEvent);
            String pkg = currentEvent.getPackageName();
            if (pkg == null || isIgnoredPackage(pkg)) continue;

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
        List<TimeRange> ranges = getActiveTimeRanges(context, startTime, endTime);
        return calculateUnionDuration(ranges);
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
            if (Thread.currentThread().isInterrupted()) throw new CancellationException();
            usageEvents.getNextEvent(currentEvent);
            int type = currentEvent.getEventType();
            if (type == 18) { // KEYGUARD_HIDDEN is event 18 (phone being unlocked)
                unlockCount++;
            }
        }
        return unlockCount;
    }

    public static long[] getHourlyUsageArray(Context context, long startTime, long endTime) {
        long[] hourlyUsageMs = new long[24];
        List<TimeRange> activeRanges = getActiveTimeRanges(context, startTime, endTime);
        if (activeRanges.isEmpty()) return hourlyUsageMs;

        @SuppressWarnings("unchecked")
        List<TimeRange>[] hourBuckets = new List[24];
        for (int i = 0; i < 24; i++) {
            hourBuckets[i] = new ArrayList<>();
        }

        Calendar cal = Calendar.getInstance();

        for (TimeRange range : activeRanges) {
            long cursor = range.start;
            while (cursor < range.end) {
                cal.setTimeInMillis(cursor);
                int hour = cal.get(Calendar.HOUR_OF_DAY);

                cal.set(Calendar.MINUTE, 59);
                cal.set(Calendar.SECOND, 59);
                cal.set(Calendar.MILLISECOND, 999);
                long endOfHour = cal.getTimeInMillis() + 1;

                long sliceEnd = Math.min(range.end, endOfHour);
                if (hour >= 0 && hour < 24) {
                    hourBuckets[hour].add(new TimeRange(cursor, sliceEnd));
                }
                cursor = sliceEnd;
            }
        }

        for (int h = 0; h < 24; h++) {
            long unionMs = calculateUnionDuration(hourBuckets[h]);
            hourlyUsageMs[h] = Math.min(unionMs, 3600000L);
        }

        return hourlyUsageMs;
    }




    public static String getPeakUsageHour(long[] hourlyUsageMs) {

        int maxHour = -1;
        long maxDuration = 0;
        for (int h = 0; h < 24; h++) {
            if (hourlyUsageMs[h] > maxDuration) {
                maxDuration = hourlyUsageMs[h];
                maxHour = h;
            }
        }

        if (maxHour == -1 || maxDuration < 60000) {
            return "N/A";
        }

        int hour12 = (maxHour % 12 == 0) ? 12
                                         : maxHour % 12;
        String amPm = (maxHour >= 12) ? "PM"
                                      : "AM";
        return String.format(Locale.getDefault(), "%d:00 %s", hour12, amPm);
    }

    public static boolean isSystemPackage(Context context, String pkg) {
        if (pkg == null || pkg.isEmpty()) return false;

        try {
            PackageManager pm = context.getPackageManager();
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            ResolveInfo resolveInfo = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
            if (resolveInfo != null && resolveInfo.activityInfo != null && pkg.equalsIgnoreCase(resolveInfo.activityInfo.packageName)) {
                return true;
            }
        } catch (Exception ignored) {}

        String lower = pkg.toLowerCase(Locale.ROOT);
        return lower.contains("launcher") || lower.contains("nexuslauncher") ||
                lower.contains("trebuchet") || lower.contains("systemui") ||
                lower.contains("oneui") || lower.contains("miui.home") ||
                lower.contains("quickstep") || (lower.contains("home") && lower.contains("android"));
    }



    public static long getIdleHomeScreenTimeMs(Context context, Map<String, Long> packageUsage) {
        long idleMs = 0;
        for (Map.Entry<String, Long> entry : packageUsage.entrySet()) {
            String pkg = entry.getKey();
            if (isSystemPackage(context, pkg)) {
                idleMs += entry.getValue();
            }
        }
        return idleMs;
    }

    public static List<AppUsageInfo> getSortedAppUsageList(Context context, Map<String, Long> packageUsage) {
        PackageManager pm = context.getPackageManager();
        List<AppUsageInfo> result = new ArrayList<>();

        for (Map.Entry<String, Long> entry : packageUsage.entrySet()) {
            String pkg = entry.getKey();
            long timeMs = entry.getValue();
            if (timeMs <= 0 || pkg == null || isIgnoredPackage(pkg)) continue;

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

            //  format a package to a name
            if (appName == null || appName.equals(pkg) || appName.contains(".")) {
                appName = cleanPackageNameToAppName(pkg);
            }

            // Fallback icon
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
        String name = (lastDot >= 0 && lastDot < pkg.length() - 1) ? pkg.substring(lastDot + 1)
                                                                    : pkg;
        // Capitalize first letter
        if (!name.isEmpty()) {
            return Character.toUpperCase(name.charAt(0)) + ((name.length() > 1) ? name.substring(1) : "");
        }
        return pkg;
    }

    public static class DoomscrollAppItem {
        private final String packageName;
        private final String appName;
        private final Drawable appIcon;
        private final long totalDoomscrollTimeMs;
        private final long longestScrollMs;

        public DoomscrollAppItem(String packageName, String appName, Drawable appIcon, long totalDoomscrollTimeMs, long longestScrollMs) {
            this.packageName = packageName;
            this.appName = appName;
            this.appIcon = appIcon;
            this.totalDoomscrollTimeMs = totalDoomscrollTimeMs;
            this.longestScrollMs = longestScrollMs;
        }

        public String getPackageName() { return packageName; }
        public String getAppName() { return appName; }
        public Drawable getAppIcon() { return appIcon; }
        public long getTotalDoomscrollTimeMs() { return totalDoomscrollTimeMs; }
        public long getLongestScrollMs() { return longestScrollMs; }
    }

    public static class DoomscrollStats {
        private final long totalDoomscrollTimeMs;
        private final int sessionCount;
        private final long lateNightDoomscrollTimeMs;
        private final long longestSessionMs;
        private final String longestSessionAppName;
        private final List<DoomscrollAppItem> topDoomscrollApps;

        public DoomscrollStats(long totalDoomscrollTimeMs, int sessionCount,
                               long lateNightDoomscrollTimeMs, long longestSessionMs,
                               String longestSessionAppName, List<DoomscrollAppItem> topDoomscrollApps) {
            this.totalDoomscrollTimeMs = totalDoomscrollTimeMs;
            this.sessionCount = sessionCount;
            this.lateNightDoomscrollTimeMs = lateNightDoomscrollTimeMs;
            this.longestSessionMs = longestSessionMs;
            this.longestSessionAppName = (longestSessionAppName != null) ? longestSessionAppName
                                                                         : "N/A";
            this.topDoomscrollApps = (topDoomscrollApps != null) ? topDoomscrollApps
                                                                 : new ArrayList<>();
        }

        public long getTotalDoomscrollTimeMs() { return totalDoomscrollTimeMs; }
        public int getSessionCount() { return sessionCount; }
        public long getLateNightDoomscrollTimeMs() { return lateNightDoomscrollTimeMs; }
        public long getLongestSessionMs() { return longestSessionMs; }
        public String getLongestSessionAppName() { return longestSessionAppName; }
        public List<DoomscrollAppItem> getTopDoomscrollApps() { return topDoomscrollApps; }

        public String getRiskLevel() {
            long minutes = totalDoomscrollTimeMs / (1000 * 60);
            if (minutes < 15) return "LOW";
            if (minutes < 45) return "MODERATE";
            if (minutes < 90) return "HIGH";
            return "SEVERE";
        }

        public String getRiskLabel() {
            String level = getRiskLevel();
            switch (level) {
                case "LOW":
                    return "Mindful";
                case "MODERATE":
                    return "Moderate";
                case "HIGH":
                    return "High Risk";
                case "SEVERE":
                    return "Severe";
                default:
                    return "Mindful";
            }
        }
    }

    public static DoomscrollStats getDoomscrollStats(Context context, long startTime, long endTime) {
        // make sure we have usage access and there is data
        if (!hasUsageAccessPermission(context) || startTime >= endTime) {
            return new DoomscrollStats(0, 0, 0, 0, "N/A", new ArrayList<>());
        }

        UsageStatsManager usageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usageStatsManager == null) {
            return new DoomscrollStats(0, 0, 0, 0, "N/A", new ArrayList<>());
        }

        UsageEvents usageEvents = usageStatsManager.queryEvents(startTime, endTime);
        if (usageEvents == null) {
            return new DoomscrollStats(0, 0, 0, 0, "N/A", new ArrayList<>());
        }

        PackageManager pm = context.getPackageManager();
        Map<String, Long> lastAppStart = new HashMap<>();
        Map<String, Boolean> doomscrollAppCache = new HashMap<>();

        class AppSession {
            final String pkg;
            final long start;
            final long end;
            AppSession(String pkg, long start, long end) {
                this.pkg = pkg;
                this.start = start;
                this.end = end;
            }
        }

        List<AppSession> doomscrollSessions = new ArrayList<>();
        UsageEvents.Event currentEvent = new UsageEvents.Event();

        //extract all the doomscroll apps sessions
        while (usageEvents.hasNextEvent()) {
            if (Thread.currentThread().isInterrupted()) throw new CancellationException();
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
                    // Check if the app is a doomscroll app, if data is absent, check if it is a doomscroll app and return the result
                    if (doomscrollAppCache.computeIfAbsent(pkg, p -> AppCategoryHelper.isDoomscrollApp(pm, p))) {
                        doomscrollSessions.add(new AppSession(pkg, start, timestamp));
                    }
                }
            }
        }
        // handle app that were not closed
        for (Map.Entry<String, Long> entry : lastAppStart.entrySet()) {
            String pkg = entry.getKey();
            long start = entry.getValue();
            if (endTime > start && doomscrollAppCache.computeIfAbsent(pkg, p -> AppCategoryHelper.isDoomscrollApp(pm, p))) {
                doomscrollSessions.add(new AppSession(pkg, start, endTime));
            }
        }

        // check if there aren't any doomscroll sessions
        if (doomscrollSessions.isEmpty()) {
            return new DoomscrollStats(0, 0, 0, 0, "N/A", new ArrayList<>());
        }

        doomscrollSessions.sort(Comparator.comparingLong(a -> a.start));

        long totalDoomscrollTimeMs = 0;
        int sessionCount = 0;
        long lateNightDoomscrollTimeMs = 0;
        long longestSessionMs = 0;
        String longestSessionPkg = null;

        Map<String, Long> appTotalDoomscrollMs = new HashMap<>();
        Map<String, Long> appLongestScrollMs = new HashMap<>();

        Calendar cal = Calendar.getInstance();

        long currentSessionStart = -1;
        long currentSessionEnd = -1;
        Map<String, Long> currentSessionAppTimes = new HashMap<>();

        for (AppSession session : doomscrollSessions) {
            // the loop handles always the session behind it
            if (currentSessionStart == -1) {
                // initialize the first session
                currentSessionStart = session.start;
                currentSessionEnd = session.end;
                currentSessionAppTimes.put(session.pkg, session.end - session.start);
            } else {
                // if this interval starts no more than 60 seconds after the current session ends, treat it as part of the same session
                if (session.start <= currentSessionEnd + 60000) {
                    currentSessionEnd = Math.max(currentSessionEnd, session.end);
                    currentSessionAppTimes.merge(session.pkg, session.end - session.start, Long::sum);
                } else {
                    long sessionDuration = currentSessionEnd - currentSessionStart;
                    boolean isLateNight = isLateNightWindow(currentSessionStart, currentSessionEnd, cal);
                    // Late night threshold is 10 min, daytime threshold is 15 mins
                    long minThreshold = isLateNight ? 10 * 60 * 1000L
                                                    : 15 * 60 * 1000L;

                    if (sessionDuration >= minThreshold) {
                        totalDoomscrollTimeMs += sessionDuration;
                        sessionCount++;
                        // calculate late night usage
                        long lateNightMs = calculateLateNightOverlap(currentSessionStart, currentSessionEnd, cal);
                        lateNightDoomscrollTimeMs += lateNightMs;


                        // get longest session
                        String sessionTopPkg = getTopAppForSession(currentSessionAppTimes);
                        if (sessionDuration > longestSessionMs) {
                            longestSessionMs = sessionDuration;
                            longestSessionPkg = sessionTopPkg;
                        }

                        //updating the app statistics
                        for (Map.Entry<String, Long> appEntry : currentSessionAppTimes.entrySet()) {
                            String p = appEntry.getKey();
                            long timeInSession = appEntry.getValue();
                            appTotalDoomscrollMs.merge(p, timeInSession, Long::sum);
                            appLongestScrollMs.merge(p, timeInSession, Math::max);
                        }
                    }

                    currentSessionStart = session.start;
                    currentSessionEnd = session.end;
                    currentSessionAppTimes.clear();
                    currentSessionAppTimes.put(session.pkg, session.end - session.start);
                }
            }
        }
        // handles the last session(after the loop handled the session behind it always)
        if (currentSessionStart != -1) {
            long sessionDuration = currentSessionEnd - currentSessionStart;
            boolean isLateNight = isLateNightWindow(currentSessionStart, currentSessionEnd, cal);
            long minThreshold = isLateNight ? 10 * 60 * 1000L
                                            : 15 * 60 * 1000L;

            if (sessionDuration >= minThreshold) {
                totalDoomscrollTimeMs += sessionDuration;
                sessionCount++;
                // calculate late night usage
                long lateNightMs = calculateLateNightOverlap(currentSessionStart, currentSessionEnd, cal);
                lateNightDoomscrollTimeMs += lateNightMs;

                // get longest session
                String sessionTopPkg = getTopAppForSession(currentSessionAppTimes);
                if (sessionDuration > longestSessionMs) {
                    longestSessionMs = sessionDuration;
                    longestSessionPkg = sessionTopPkg;
                }

                //updating the app statistics
                for (Map.Entry<String, Long> appEntry : currentSessionAppTimes.entrySet()) {
                    String p = appEntry.getKey();
                    long timeInSession = appEntry.getValue();
                    appTotalDoomscrollMs.merge(p, timeInSession, Long::sum);
                    appLongestScrollMs.merge(p, timeInSession, Math::max);
                }
            }
        }

        //converts all the data into DoomscrollAppItem
        List<DoomscrollAppItem> topDoomscrollApps = new ArrayList<>();
        for (Map.Entry<String, Long> entry : appTotalDoomscrollMs.entrySet()) {
            String pkg = entry.getKey();
            long totalMs = entry.getValue();
            long maxScroll = appLongestScrollMs.getOrDefault(pkg, totalMs);

            String appName = null;
            Drawable icon = null;
            try {
                ApplicationInfo appInfo = pm.getApplicationInfo(pkg, 0);
                CharSequence label = pm.getApplicationLabel(appInfo);
                if (label != null) appName = label.toString().trim();
                icon = pm.getApplicationIcon(appInfo);
            } catch (Exception ignored) {}

            if (appName == null || appName.isEmpty() || appName.contains(".")) {
                appName = cleanPackageNameToAppName(pkg);
            }
            if (icon == null) {
                icon = pm.getDefaultActivityIcon();
            }

            topDoomscrollApps.add(new DoomscrollAppItem(pkg, appName, icon, totalMs, maxScroll));
        }

        topDoomscrollApps.sort((a, b) -> Long.compare(b.getTotalDoomscrollTimeMs(), a.getTotalDoomscrollTimeMs()));

        String longestAppName = "N/A";
        if (longestSessionPkg != null) {
            try {
                ApplicationInfo appInfo = pm.getApplicationInfo(longestSessionPkg, 0);
                CharSequence label = pm.getApplicationLabel(appInfo);
                if (label != null) longestAppName = label.toString().trim();
            } catch (Exception ignored) {
                longestAppName = cleanPackageNameToAppName(longestSessionPkg);
            }
        }

        return new DoomscrollStats(
                totalDoomscrollTimeMs,
                sessionCount,
                lateNightDoomscrollTimeMs,
                longestSessionMs,
                longestAppName,
                topDoomscrollApps
        );
    }

    private static boolean isLateNightWindow(long startMs, long endMs, Calendar cal) {
        cal.setTimeInMillis(startMs);
        int startHour = cal.get(Calendar.HOUR_OF_DAY);
        cal.setTimeInMillis(endMs);
        int endHour = cal.get(Calendar.HOUR_OF_DAY);

        return (startHour >= 22 || startHour < 6) || (endHour >= 22 || endHour < 6);
    }

    private static long calculateLateNightOverlap(long startMs, long endMs, Calendar cal) {
        if (startMs >= endMs) return 0;

        long lateNightMs = 0;
        long cursor = startMs;
        long step = 60000;

        while (cursor < endMs) {
            cal.setTimeInMillis(cursor);
            int hour = cal.get(Calendar.HOUR_OF_DAY);
            if (hour >= 22 || hour < 6) {
                lateNightMs += Math.min(step, endMs - cursor);
            }
            cursor += step;
        }

        return lateNightMs;
    }

    private static String getTopAppForSession(Map<String, Long> appTimes) {
        if (appTimes == null || appTimes.isEmpty()) return null;
        String topPkg = null;
        long maxTime = -1;
        for (Map.Entry<String, Long> entry : appTimes.entrySet()) {
            if (entry.getValue() > maxTime) {
                maxTime = entry.getValue();
                topPkg = entry.getKey();
            }
        }
        return topPkg;
    }
}
