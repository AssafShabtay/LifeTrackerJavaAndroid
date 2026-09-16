package com.example.myapplication.helpers;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Process;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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

        public void addForegroundTime(long timeMs) {
            this.totalTimeInForegroundMs += timeMs;
        }
    }

    public static boolean hasUsageStatsPermission(Context context) {
        AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        if (appOps == null) return false;
        int mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.getPackageName()
        );
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    /**
     * Calculates foreground usage per package during the given [startTimeMs, endTimeMs] interval using UsageEvents.
     */
    @NonNull
    public static Map<String, Long> getForegroundUsageInRange(Context context, long startTimeMs, long endTimeMs) {
        Map<String, Long> usageMap = new HashMap<>();
        if (!hasUsageStatsPermission(context) || startTimeMs >= endTimeMs) {
            return usageMap;
        }

        UsageStatsManager usageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usageStatsManager == null) return usageMap;

        UsageEvents events = usageStatsManager.queryEvents(startTimeMs, endTimeMs);
        if (events == null) return usageMap;

        UsageEvents.Event event = new UsageEvents.Event();
        Map<String, Long> lastResumeTime = new HashMap<>();

        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            String pkg = event.getPackageName();
            if (pkg == null) continue;

            int eventType = event.getEventType();
            long eventTime = event.getTimeStamp();

            if (eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                lastResumeTime.put(pkg, eventTime);
            } else if (eventType == UsageEvents.Event.ACTIVITY_PAUSED || eventType == UsageEvents.Event.ACTIVITY_STOPPED) {
                Long resumeTime = lastResumeTime.remove(pkg);
                if (resumeTime != null && eventTime >= resumeTime) {
                    long duration = eventTime - resumeTime;
                    long currentTotal = usageMap.getOrDefault(pkg, 0L);
                    usageMap.put(pkg, currentTotal + duration);
                }
            }
        }

        // Account for any app still running at endTimeMs
        for (Map.Entry<String, Long> entry : lastResumeTime.entrySet()) {
            if (endTimeMs > entry.getValue()) {
                long duration = endTimeMs - entry.getValue();
                long currentTotal = usageMap.getOrDefault(entry.getKey(), 0L);
                usageMap.put(entry.getKey(), currentTotal + duration);
            }
        }

        return usageMap;
    }

    /**
     * Returns total foreground screen time in ms across all apps in [startTimeMs, endTimeMs].
     */
    public static long getTotalScreenTimeInRange(Context context, long startTimeMs, long endTimeMs) {
        Map<String, Long> usageMap = getForegroundUsageInRange(context, startTimeMs, endTimeMs);
        long total = 0;
        for (long duration : usageMap.values()) {
            total += duration;
        }
        return total;
    }

    /**
     * Converts a map of packageName -> duration to a sorted list of AppUsageInfo with resolved app name & icon.
     */
    @NonNull
    public static List<AppUsageInfo> getSortedAppUsageList(Context context, Map<String, Long> rawUsage) {
        PackageManager pm = context.getPackageManager();
        List<AppUsageInfo> result = new ArrayList<>();

        for (Map.Entry<String, Long> entry : rawUsage.entrySet()) {
            String pkg = entry.getKey();
            long duration = entry.getValue();
            if (duration <= 0) continue;

            String label = pkg;
            Drawable icon = null;
            try {
                ApplicationInfo appInfo = pm.getApplicationInfo(pkg, 0);
                CharSequence appLabel = pm.getApplicationLabel(appInfo);
                if (appLabel != null) {
                    label = appLabel.toString();
                }
                icon = pm.getApplicationIcon(appInfo);
            } catch (PackageManager.NameNotFoundException ignored) {
            }

            result.add(new AppUsageInfo(pkg, label, icon, duration));
        }

        result.sort((a, b) -> Long.compare(b.getTotalTimeInForegroundMs(), a.getTotalTimeInForegroundMs()));
        return result;
    }
}
