package com.example.myapplication.mainScreen.dashboardScreen;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.example.myapplication.LifeTrackerApp;
import com.example.myapplication.database.ActivityDao;
import com.example.myapplication.database.MovementActivity;
import com.example.myapplication.database.StillLocation;
import com.example.myapplication.helpers.UsageStatsHelper;
import com.example.myapplication.mainScreen.dashboardScreen.model.ContextualInsight;
import com.example.myapplication.mainScreen.dashboardScreen.model.DashboardData;
import com.example.myapplication.mainScreen.dashboardScreen.model.LocationUsageItem;
import com.example.myapplication.mainScreen.dashboardScreen.model.TimeDrainItem;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class DashboardDataManager {

    private static final String PREFS_NAME = "MyPrefs";
    private static final String HOME_ADDRESS_KEY = "home_address";
    private static final String WORK_ADDRESS_KEY = "work_address";

    public interface DashboardDataCallback {
        void onDataLoaded(DashboardData data);
    }

    private final Context context;
    private final LifeTrackerApp app;

    public DashboardDataManager(Context context, LifeTrackerApp app) {
        this.context = context.getApplicationContext();
        this.app = app;
    }

    public void loadDashboardDataForDate(Date date, DashboardDataCallback callback) {
        app.getDatabaseWriteExecutor().execute(() -> {
            DashboardData data = calculateDashboardData(date);
            callback.onDataLoaded(data);
        });
    }

    @NonNull
    private DashboardData calculateDashboardData(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date startOfDay = cal.getTime();

        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 999);
        Date endOfDay = cal.getTime();

        long now = System.currentTimeMillis();
        long queryEnd = Math.min(endOfDay.getTime(), now);
        long queryStart = startOfDay.getTime();

        ActivityDao dao = com.example.myapplication.database.ActivityDatabase.getDatabase(context).activityDao();
        List<StillLocation> stills = dao.getStillsFromRange(startOfDay, endOfDay);
        List<MovementActivity> movements = dao.getMovementsFromRange(startOfDay, endOfDay);

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String homeAddress = prefs.getString(HOME_ADDRESS_KEY, null);
        String workAddress = prefs.getString(WORK_ADDRESS_KEY, null);

        // 1. Calculate Active Real-World Time (Movements + Stills outside of home)
        long totalMovementDurationMs = 0;
        for (MovementActivity m : movements) {
            long s = Math.max(m.getStartTimeDate().getTime(), queryStart);
            long e = m.getEndTimeDate() != null ? Math.min(m.getEndTimeDate().getTime(), queryEnd) : queryEnd;
            if (e > s) {
                totalMovementDurationMs += (e - s);
            }
        }

        long totalNonHomeStillMs = 0;
        for (StillLocation sLoc : stills) {
            boolean isHome = isHomeLocation(sLoc, homeAddress);
            long s = Math.max(sLoc.getStartTimeDate().getTime(), queryStart);
            long e = sLoc.getEndTimeDate() != null ? Math.min(sLoc.getEndTimeDate().getTime(), queryEnd) : queryEnd;
            if (e > s) {
                if (!isHome) {
                    totalNonHomeStillMs += (e - s);
                }
            }
        }

        long activeRealWorldTimeMs = totalMovementDurationMs + totalNonHomeStillMs;

        // 2. Digital Screen Time across the entire day
        long totalDigitalScreenTimeMs = 0;
        if (UsageStatsHelper.hasUsageStatsPermission(context)) {
            totalDigitalScreenTimeMs = UsageStatsHelper.getTotalScreenTimeInRange(context, queryStart, queryEnd);
        }

        // 3. Location-Usage Breakdown
        // Aggregate physical intervals per place/location
        Map<String, PlaceAggregation> placeAggMap = new HashMap<>();
        for (StillLocation sLoc : stills) {
            String name = sLoc.getPlaceName();
            if (name == null || name.trim().isEmpty()) {
                name = sLoc.getPlaceAddress();
            }
            if (name == null || name.trim().isEmpty()) {
                name = "Unknown Location";
            }

            long s = Math.max(sLoc.getStartTimeDate().getTime(), queryStart);
            long e = sLoc.getEndTimeDate() != null ? Math.min(sLoc.getEndTimeDate().getTime(), queryEnd) : queryEnd;
            if (e <= s) continue;

            PlaceAggregation agg = placeAggMap.get(name);
            if (agg == null) {
                agg = new PlaceAggregation(name, sLoc.getCategory(), sLoc.getIcon(), sLoc.getColor());
                placeAggMap.put(name, agg);
            }
            agg.addInterval(s, e);
        }

        List<LocationUsageItem> locationUsageItems = new ArrayList<>();
        List<TimeDrainItem> timeDrainItems = new ArrayList<>();

        if (UsageStatsHelper.hasUsageStatsPermission(context)) {
            for (PlaceAggregation agg : placeAggMap.values()) {
                Map<String, Long> placeAppUsage = new HashMap<>();
                long placeScreenTime = 0;

                for (Interval interval : agg.intervals) {
                    Map<String, Long> intervalUsage = UsageStatsHelper.getForegroundUsageInRange(context, interval.start, interval.end);
                    for (Map.Entry<String, Long> entry : intervalUsage.entrySet()) {
                        long cur = placeAppUsage.getOrDefault(entry.getKey(), 0L);
                        placeAppUsage.put(entry.getKey(), cur + entry.getValue());
                        placeScreenTime += entry.getValue();
                    }
                }

                List<UsageStatsHelper.AppUsageInfo> sortedApps = UsageStatsHelper.getSortedAppUsageList(context, placeAppUsage);
                locationUsageItems.add(new LocationUsageItem(
                        agg.name,
                        agg.category,
                        agg.icon,
                        agg.color,
                        agg.totalDurationMs,
                        placeScreenTime,
                        sortedApps
                ));

                // Check for Time-Drain in Focus places (e.g. Work, School, Library)
                if (isFocusPlace(agg.name, agg.category, workAddress)) {
                    for (UsageStatsHelper.AppUsageInfo appInfo : sortedApps) {
                        // Consider distracting apps used for more than 5 minutes in a focus location
                        if (isDistractingApp(appInfo.getPackageName()) && appInfo.getTotalTimeInForegroundMs() >= 5 * 60 * 1000) {
                            timeDrainItems.add(new TimeDrainItem(
                                    appInfo.getAppName(),
                                    appInfo.getPackageName(),
                                    appInfo.getAppIcon(),
                                    agg.name,
                                    appInfo.getTotalTimeInForegroundMs()
                            ));
                        }
                    }
                }
            }

            // Sort locations by highest screen time first
            locationUsageItems.sort((a, b) -> Long.compare(b.getScreenTimeMs(), a.getScreenTimeMs()));
            // Sort time drains by screen time
            timeDrainItems.sort((a, b) -> Long.compare(b.getScreenTimeMs(), a.getScreenTimeMs()));
        }

        // 4. Generate Contextual Insight Cards
        List<ContextualInsight> insights = generateInsights(
                activeRealWorldTimeMs,
                totalDigitalScreenTimeMs,
                locationUsageItems,
                timeDrainItems
        );

        return new DashboardData(
                activeRealWorldTimeMs,
                totalDigitalScreenTimeMs,
                insights,
                locationUsageItems,
                timeDrainItems
        );
    }

    private boolean isHomeLocation(StillLocation sLoc, String homeAddress) {
        if ("Home".equalsIgnoreCase(sLoc.getCategory()) || "Home".equalsIgnoreCase(sLoc.getPlaceName())) {
            return true;
        }
        if (homeAddress != null && sLoc.getPlaceAddress() != null &&
                sLoc.getPlaceAddress().toLowerCase(Locale.ROOT).contains(homeAddress.toLowerCase(Locale.ROOT))) {
            return true;
        }
        return false;
    }

    private boolean isFocusPlace(String name, String category, String workAddress) {
        if (category != null) {
            String cat = category.toLowerCase(Locale.ROOT);
            if (cat.contains("work") || cat.contains("office") || cat.contains("school") ||
                    cat.contains("university") || cat.contains("library") || cat.contains("study")) {
                return true;
            }
        }
        if (name != null) {
            String n = name.toLowerCase(Locale.ROOT);
            if (n.contains("work") || n.contains("office") || n.contains("library") || n.contains("school")) {
                return true;
            }
        }
        if (workAddress != null && name != null && name.toLowerCase(Locale.ROOT).contains(workAddress.toLowerCase(Locale.ROOT))) {
            return true;
        }
        return false;
    }

    private static final Set<String> KNOWN_DISTRACTING_PACKAGES = new HashSet<>();
    static {
        KNOWN_DISTRACTING_PACKAGES.add("com.instagram.android");
        KNOWN_DISTRACTING_PACKAGES.add("com.facebook.katana");
        KNOWN_DISTRACTING_PACKAGES.add("com.zhiliaoapp.musically"); // TikTok
        KNOWN_DISTRACTING_PACKAGES.add("com.twitter.android");
        KNOWN_DISTRACTING_PACKAGES.add("com.google.android.youtube");
        KNOWN_DISTRACTING_PACKAGES.add("com.reddit.frontpage");
        KNOWN_DISTRACTING_PACKAGES.add("com.snapchat.android");
        KNOWN_DISTRACTING_PACKAGES.add("com.netflix.mediaclient");
        KNOWN_DISTRACTING_PACKAGES.add("com.pinterest");
    }

    private boolean isDistractingApp(String packageName) {
        if (packageName == null) return false;
        if (KNOWN_DISTRACTING_PACKAGES.contains(packageName)) return true;
        String lower = packageName.toLowerCase(Locale.ROOT);
        return lower.contains("tiktok") || lower.contains("instagram") || lower.contains("twitter") ||
                lower.contains("facebook") || lower.contains("reddit") || lower.contains("game") ||
                lower.contains("youtube");
    }

    private List<ContextualInsight> generateInsights(
            long activeTimeMs,
            long screenTimeMs,
            List<LocationUsageItem> locationUsageItems,
            List<TimeDrainItem> timeDrainItems) {

        List<ContextualInsight> insights = new ArrayList<>();

        // 1. Balance Insight
        long totalDayTrackedMs = activeTimeMs + screenTimeMs;
        if (totalDayTrackedMs > 0) {
            int activePercent = (int) Math.round(((double) activeTimeMs / (double) totalDayTrackedMs) * 100);
            if (activePercent >= 60) {
                insights.add(new ContextualInsight(
                        "Great Real-World Balance",
                        "You spent " + activePercent + "% of your tracked time engaged in the real world rather than on your screen.",
                        ContextualInsight.Type.POSITIVE,
                        activePercent + "% Active"
                ));
            } else if (activePercent <= 30 && screenTimeMs > 2 * 3600 * 1000) {
                insights.add(new ContextualInsight(
                        "High Digital Screen Time",
                        "Screen time dominates today (" + (100 - activePercent) + "%). Consider taking short device-free breaks.",
                        ContextualInsight.Type.WARNING,
                        (100 - activePercent) + "% Screen"
                ));
            }
        }

        // 2. Focus place time-drain insight
        if (!timeDrainItems.isEmpty()) {
            TimeDrainItem topDrain = timeDrainItems.get(0);
            long minutes = topDrain.getScreenTimeMs() / (60 * 1000);
            insights.add(new ContextualInsight(
                    "Focus Area Distraction",
                    "Spent " + minutes + "m on " + topDrain.getAppName() + " while at " + topDrain.getFocusPlaceName() + ".",
                    ContextualInsight.Type.WARNING,
                    minutes + "m"
            ));
        }

        // 3. Location specific efficiency insight
        for (LocationUsageItem item : locationUsageItems) {
            if (item.getPhysicalDurationMs() >= 30 * 60 * 1000) {
                int pct = item.getScreenPercentageOfPhysicalTime();
                if (pct <= 15) {
                    insights.add(new ContextualInsight(
                            "Mindful Presence at " + item.getPlaceName(),
                            "Only " + pct + "% of your time at " + item.getPlaceName() + " was spent on your phone. Great focus!",
                            ContextualInsight.Type.POSITIVE,
                            pct + "% Screen"
                    ));
                    break;
                } else if (pct >= 60 && !"Home".equalsIgnoreCase(item.getCategory())) {
                    insights.add(new ContextualInsight(
                            "Heavy Screen Usage at " + item.getPlaceName(),
                            "Over " + pct + "% of your physical visit was spent on device apps.",
                            ContextualInsight.Type.INFO,
                            pct + "% Screen"
                    ));
                    break;
                }
            }
        }

        // Fallback welcoming insight if no anomalies detected
        if (insights.isEmpty()) {
            insights.add(new ContextualInsight(
                    "Efficiency Engine Active",
                    "Timeline and digital screen statistics are syncing seamlessly to reveal your daily focus patterns.",
                    ContextualInsight.Type.INFO,
                    "Syncing"
            ));
        }

        return insights;
    }

    private static class Interval {
        long start;
        long end;
        Interval(long s, long e) {
            this.start = s;
            this.end = e;
        }
    }

    private static class PlaceAggregation {
        String name;
        String category;
        String icon;
        Integer color;
        long totalDurationMs = 0;
        List<Interval> intervals = new ArrayList<>();

        PlaceAggregation(String name, String category, String icon, Integer color) {
            this.name = name;
            this.category = category;
            this.icon = icon;
            this.color = color;
        }

        void addInterval(long start, long end) {
            intervals.add(new Interval(start, end));
            totalDurationMs += (end - start);
        }
    }
}
