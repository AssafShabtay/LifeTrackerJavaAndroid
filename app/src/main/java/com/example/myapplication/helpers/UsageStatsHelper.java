package com.example.myapplication.helpers;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Process;

import com.example.myapplication.database.AppUsageSession;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class UsageStatsHelper {

    private static final Set<String> DISTRACTION_PACKAGES = new HashSet<>();
    private static final Set<String> PRODUCTIVE_PACKAGES = new HashSet<>();
    private static final Set<String> NAVIGATION_PACKAGES = new HashSet<>();

    static {
        // Known Distraction apps (Social media, video, games)
        DISTRACTION_PACKAGES.add("com.instagram.android");
        DISTRACTION_PACKAGES.add("com.zhiliaoapp.musically"); // TikTok
        DISTRACTION_PACKAGES.add("com.facebook.katana");
        DISTRACTION_PACKAGES.add("com.twitter.android");
        DISTRACTION_PACKAGES.add("com.reddit.frontpage");
        DISTRACTION_PACKAGES.add("com.google.android.youtube");
        DISTRACTION_PACKAGES.add("com.netflix.mediaclient");
        DISTRACTION_PACKAGES.add("com.snapchat.android");

        // Known Productive apps (Docs, Office, Email, IDEs, Slack)
        PRODUCTIVE_PACKAGES.add("com.Slack");
        PRODUCTIVE_PACKAGES.add("com.google.android.apps.docs");
        PRODUCTIVE_PACKAGES.add("com.google.android.gm");
        PRODUCTIVE_PACKAGES.add("com.microsoft.office.outlook");
        PRODUCTIVE_PACKAGES.add("com.microsoft.teams");
        PRODUCTIVE_PACKAGES.add("com.todoist");

        // Known Navigation apps
        NAVIGATION_PACKAGES.add("com.google.android.apps.maps");
        NAVIGATION_PACKAGES.add("com.waze");
        NAVIGATION_PACKAGES.add("com.citymapper.app.release");
    }

    public static boolean hasUsageStatsPermission(Context context) {
        if (context == null) return false;
        AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        if (appOps == null) return false;
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(), context.getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    public static List<AppUsageSession> queryAppUsageForInterval(Context context, long startTimeMs, long endTimeMs) {
        List<AppUsageSession> sessions = new ArrayList<>();
        if (context == null || startTimeMs >= endTimeMs) return sessions;

        UsageStatsManager usageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usageStatsManager == null || !hasUsageStatsPermission(context)) return sessions;

        UsageEvents events;
        try {
            events = usageStatsManager.queryEvents(startTimeMs, endTimeMs);
        } catch (Exception e) {
            return sessions;
        }

        if (events == null) return sessions;

        Map<String, Long> appForegroundDurations = new HashMap<>();
        Map<String, Long> appStartTimes = new HashMap<>();
        UsageEvents.Event event = new UsageEvents.Event();

        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            String pkg = event.getPackageName();
            if (pkg == null) continue;

            if (event.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED) {
                appStartTimes.put(pkg, event.getTimeStamp());
            } else if (event.getEventType() == UsageEvents.Event.ACTIVITY_PAUSED) {
                Long startTime = appStartTimes.get(pkg);
                if (startTime != null) {
                    long duration = event.getTimeStamp() - startTime;
                    if (duration > 0) {
                        appForegroundDurations.put(pkg, appForegroundDurations.getOrDefault(pkg, 0L) + duration);
                    }
                    appStartTimes.remove(pkg);
                }
            }
        }

        PackageManager pm = context.getPackageManager();
        for (Map.Entry<String, Long> entry : appForegroundDurations.entrySet()) {
            if (entry.getValue() < 3000) continue; // Skip noise < 3 seconds

            String pkgName = entry.getKey();
            String label = pkgName;
            Drawable icon = null;
            try {
                ApplicationInfo info = pm.getApplicationInfo(pkgName, 0);
                label = pm.getApplicationLabel(info).toString();
                icon = pm.getApplicationIcon(info);
            } catch (PackageManager.NameNotFoundException ignored) {}

            AppUsageSession.Category category = categorizeApp(pkgName);
            sessions.add(new AppUsageSession(pkgName, label, icon, entry.getValue(), category));
        }

        sessions.sort((a, b) -> Long.compare(b.getTotalForegroundTimeMs(), a.getTotalForegroundTimeMs()));
        return sessions;
    }

    public static int queryUnlockCountForInterval(Context context, long startTimeMs, long endTimeMs) {
        if (context == null || startTimeMs >= endTimeMs) return 0;
        UsageStatsManager usageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usageStatsManager == null || !hasUsageStatsPermission(context)) return 0;

        UsageEvents events;
        try {
            events = usageStatsManager.queryEvents(startTimeMs, endTimeMs);
        } catch (Exception e) {
            return 0;
        }

        if (events == null) return 0;

        int unlocks = 0;
        UsageEvents.Event event = new UsageEvents.Event();
        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            int type = event.getEventType();
            if (type == 16 || type == UsageEvents.Event.SCREEN_INTERACTIVE || type == UsageEvents.Event.USER_INTERACTION) {
                unlocks++;
            }
        }
        return unlocks;
    }

    public static AppUsageSession.Category categorizeApp(String packageName) {
        if (packageName == null) return AppUsageSession.Category.UTILITY;
        String lower = packageName.toLowerCase();

        if (DISTRACTION_PACKAGES.contains(packageName) || lower.contains("instagram") || lower.contains("facebook") || lower.contains("tiktok") || lower.contains("twitter") || lower.contains("game")) {
            return AppUsageSession.Category.DISTRACTION;
        }
        if (PRODUCTIVE_PACKAGES.contains(packageName) || lower.contains("slack") || lower.contains("docs") || lower.contains("excel") || lower.contains("outlook")) {
            return AppUsageSession.Category.PRODUCTIVE;
        }
        if (NAVIGATION_PACKAGES.contains(packageName) || lower.contains("maps") || lower.contains("waze") || lower.contains("navigation")) {
            return AppUsageSession.Category.NAVIGATION;
        }
        return AppUsageSession.Category.UTILITY;
    }

    public static String buildTimeReclaimCallout(List<AppUsageSession> sessions, String zoneCategory) {
        long totalDistractionMs = 0;
        for (AppUsageSession s : sessions) {
            if (s.getCategory() == AppUsageSession.Category.DISTRACTION) {
                totalDistractionMs += s.getTotalForegroundTimeMs();
            }
        }
        long mins = totalDistractionMs / (60 * 1000);
        if (mins < 5) {
            return "Great focus session! Phone distraction was under 5 minutes during this visit.";
        }
        if ("Work".equalsIgnoreCase(zoneCategory) || "School".equalsIgnoreCase(zoneCategory) || "Library".equalsIgnoreCase(zoneCategory)) {
            return mins + " minutes were spent on distracting apps during your core focus hours. Eliminating this would let your day wrap up " + mins + " minutes earlier.";
        }
        return mins + " minutes of screen distraction occurred during this stay. Reclaiming this time can help you stay present.";
    }
}
