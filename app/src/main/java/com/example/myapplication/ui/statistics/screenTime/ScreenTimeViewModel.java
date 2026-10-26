package com.example.myapplication.ui.statistics.screenTime;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.myapplication.LifeTrackerApp;
import com.example.myapplication.tracking.usage.DailyUsageItem;
import com.example.myapplication.tracking.usage.UsageStatsHelper;
import com.example.myapplication.tracking.usage.WeeklyUsageItem;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;

public class ScreenTimeViewModel extends AndroidViewModel {

    public static final int PERIOD_DAILY = 0;
    public static final int PERIOD_WEEKLY = 1;

    private final MutableLiveData<Boolean> permissionGranted = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> selectedPeriod = new MutableLiveData<>(PERIOD_DAILY);
    private final MutableLiveData<Integer> currentDayIndex = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> currentWeekIndex = new MutableLiveData<>(0);

    private final MutableLiveData<DailyUsageItem> dailyUsageItem = new MutableLiveData<>();
    private final MutableLiveData<WeeklyUsageItem> weeklyUsageItem = new MutableLiveData<>();
    private final MutableLiveData<Map<String, Long>> currentPackageUsage = new MutableLiveData<>(new HashMap<>());
    private final MutableLiveData<Boolean> loadError = new MutableLiveData<>(false);

    private final Map<Integer, DailyUsageItem> dailyHistoryList = new HashMap<>();
    private final Map<Integer, WeeklyUsageItem> weeklyHistoryList = new HashMap<>();
    private final Map<Integer, Map<String, Long>> dailyPackageUsage = new HashMap<>();
    private final Map<Integer, Map<String, Long>> weeklyPackageUsage = new HashMap<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    // async management
    private Future<?> pendingLoad;
    private int loadVersion = 0;

    // week day preference listener
    private final SharedPreferences.OnSharedPreferenceChangeListener prefListener = (sharedPreferences, key) -> {
        if ("week_start_day".equals(key)) {
            loadScreenTimeStats();
        }
    };

    public ScreenTimeViewModel(@NonNull Application application) {
        super(application);
        application.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
                .registerOnSharedPreferenceChangeListener(prefListener);
    }

    // Getters for LiveData
    public LiveData<Boolean> getPermissionGranted() { return permissionGranted; }
    public LiveData<Boolean> getIsLoading() { return isLoading; }
    public LiveData<Integer> getSelectedPeriod() { return selectedPeriod; }
    public LiveData<Integer> getCurrentDayIndex() { return currentDayIndex; }
    public LiveData<Integer> getCurrentWeekIndex() { return currentWeekIndex; }
    public LiveData<DailyUsageItem> getDailyUsageItem() { return dailyUsageItem; }
    public LiveData<WeeklyUsageItem> getWeeklyUsageItem() { return weeklyUsageItem; }
    public LiveData<Map<String, Long>> getCurrentPackageUsage() { return currentPackageUsage; }
    public LiveData<Boolean> getLoadError() { return loadError; }

    public void loadScreenTimeStats() {
        cancelPendingLoad();
        Context context = getApplication();
        boolean hasPermission = UsageStatsHelper.hasUsageAccessPermission(context);
        permissionGranted.setValue(hasPermission);

        if (!hasPermission) {
            isLoading.setValue(false);
            return;
        }

        // reset lists to update screen time stats
        dailyHistoryList.clear();
        weeklyHistoryList.clear();
        dailyPackageUsage.clear();
        weeklyPackageUsage.clear();
        currentDayIndex.setValue(0);
        currentWeekIndex.setValue(0);

        updatePeriodView();
    }


    public void setSelectedPeriod(int period) {
        /// Toggle between daily and weekly view modes.
        if (selectedPeriod.getValue() != null && selectedPeriod.getValue() == period) return;
        selectedPeriod.setValue(period);
        updatePeriodView();
    }

    public void navigatePrevious() {
        int period = selectedPeriod.getValue() != null ? selectedPeriod.getValue() : PERIOD_DAILY;
        if (period == PERIOD_DAILY) {
            int day = currentDayIndex.getValue() != null ? currentDayIndex.getValue() : 0;
            if (day < 6) {
                currentDayIndex.setValue(day + 1);
                updatePeriodView();
            }
        } else {
            int week = currentWeekIndex.getValue() != null ? currentWeekIndex.getValue() : 0;
            if (week < 3) {
                currentWeekIndex.setValue(week + 1);
                updatePeriodView();
            }
        }
    }

    public void navigateNext() {
        int period = selectedPeriod.getValue() != null ? selectedPeriod.getValue() : PERIOD_DAILY;
        if (period == PERIOD_DAILY) {
            int day = currentDayIndex.getValue() != null ? currentDayIndex.getValue() : 0;
            if (day > 0) {
                currentDayIndex.setValue(day - 1);
                updatePeriodView();
            }
        } else {
            int week = currentWeekIndex.getValue() != null ? currentWeekIndex.getValue() : 0;
            if (week > 0) {
                currentWeekIndex.setValue(week - 1);
                updatePeriodView();
            }
        }
    }
        private void updatePeriodView() {
        //// update screen time stats
        cancelPendingLoad();
        int period = selectedPeriod.getValue() != null ? selectedPeriod.getValue() : PERIOD_DAILY;
        int index = period == PERIOD_DAILY ?
                (currentDayIndex.getValue() != null ? currentDayIndex.getValue() : 0) :
                (currentWeekIndex.getValue() != null ? currentWeekIndex.getValue() : 0);

        loadError.setValue(false);

        if (period == PERIOD_DAILY) {
            // Check for cached data
            if (dailyHistoryList.containsKey(index)) {
                dailyUsageItem.setValue(dailyHistoryList.get(index));
                currentPackageUsage.setValue(dailyPackageUsage.get(index));
                isLoading.setValue(false);
            } else {
                loadSelectedUsage();
            }
        } else {
            // Check for cached data
            if (weeklyHistoryList.containsKey(index)) {
                weeklyUsageItem.setValue(weeklyHistoryList.get(index));
                currentPackageUsage.setValue(weeklyPackageUsage.get(index));
                isLoading.setValue(false);
            } else {
                loadSelectedUsage();
            }
        }
    }



    private void loadSelectedUsage() {
        final int version = ++loadVersion;
        final int period = selectedPeriod.getValue() != null ? selectedPeriod.getValue() : PERIOD_DAILY;
        // period offset is how far back in time the user has navigated
        final int periodOffset = period == PERIOD_DAILY ?
                (currentDayIndex.getValue() != null ? currentDayIndex.getValue() : 0) :
                (currentWeekIndex.getValue() != null ? currentWeekIndex.getValue() : 0);

        isLoading.setValue(true);
        LifeTrackerApp app = (LifeTrackerApp) getApplication();

        pendingLoad = app.getBackgroundWriteExecutor().submit(() -> {
            Map<String, Long> packageUsage = new HashMap<>();
            try {
                DailyUsageItem day = period == PERIOD_DAILY ? loadDailyUsage(periodOffset, packageUsage) : null;
                WeeklyUsageItem week = period == PERIOD_WEEKLY ? loadWeeklyUsage(periodOffset, packageUsage) : null;

                if (Thread.currentThread().isInterrupted()) return;

                mainHandler.post(() -> {
                    // Ignore if another request started in the meantime
                    if (version != loadVersion) return;
                    pendingLoad = null;

                    if (day != null) {
                        dailyHistoryList.put(periodOffset, day);
                        dailyPackageUsage.put(periodOffset, packageUsage);
                    }
                    if (week != null) {
                        weeklyHistoryList.put(periodOffset, week);
                        weeklyPackageUsage.put(periodOffset, packageUsage);
                    }
                    isLoading.setValue(false);
                    updatePeriodView();
                });
            } catch (CancellationException ignored) {
                // Task was canceled
            } catch (RuntimeException error) {
                Log.e("ScreenTimeViewModel", "Unable to load usage", error);
                mainHandler.post(() -> {
                    if (version != loadVersion) return;
                    pendingLoad = null;
                    isLoading.setValue(false);
                    loadError.setValue(true);
                });
            }
        });
    }

    private void cancelPendingLoad() {
        if (pendingLoad != null) {
            pendingLoad.cancel(true);
            pendingLoad = null;
        }
        loadVersion++;
    }

    private DailyUsageItem loadDailyUsage(int dayOffset, Map<String, Long> appUsageMap) {
        Context context = getApplication();
        SimpleDateFormat dayFormat = new SimpleDateFormat("EEE, MMM d", Locale.getDefault());

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -dayOffset);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long dayStartMs = cal.getTimeInMillis();

        long dayEndMs;
        if (dayOffset == 0) {
            dayEndMs = System.currentTimeMillis();
        } else {
            cal.set(Calendar.HOUR_OF_DAY, 23);
            cal.set(Calendar.MINUTE, 59);
            cal.set(Calendar.SECOND, 59);
            cal.set(Calendar.MILLISECOND, 999);
            dayEndMs = cal.getTimeInMillis();
        }

        // Generate header label
        String label;
        if (dayOffset == 0) {
            label = "Today, " + dayFormat.format(new Date(dayStartMs));
        } else if (dayOffset == 1) {
            label = "Yesterday, " + dayFormat.format(new Date(dayStartMs));
        } else {
            label = dayFormat.format(new Date(dayStartMs));
        }

        // compute data for the day
        appUsageMap.putAll(UsageStatsHelper.getUsageTimePerPackage(context, dayStartMs, dayEndMs));
        long totalScreenTimeMs = 0;
        for (long duration : appUsageMap.values()) totalScreenTimeMs += duration;
        int unlockCount = UsageStatsHelper.getDeviceUnlockCount(context, dayStartMs, dayEndMs);
        long[] hourlyUsage = UsageStatsHelper.getHourlyUsageArray(context, dayStartMs, dayEndMs);
        String peakHour = UsageStatsHelper.getPeakUsageHour(hourlyUsage);
        long idleScreenTimeMs = UsageStatsHelper.getIdleHomeScreenTimeMs(context, appUsageMap);

        List<UsageStatsHelper.AppUsageInfo> topApps = UsageStatsHelper.getSortedAppUsageList(context, appUsageMap);
        UsageStatsHelper.DoomscrollStats doomscrollStats = UsageStatsHelper.getDoomscrollStats(context, dayStartMs, dayEndMs);

        return new DailyUsageItem(
                label,
                new Date(dayStartMs),
                totalScreenTimeMs,
                unlockCount,
                hourlyUsage,
                topApps,
                peakHour,
                idleScreenTimeMs,
                doomscrollStats
        );
    }

    private WeeklyUsageItem loadWeeklyUsage(int weekOffset, Map<String, Long> appUsageMap) {
        Context context = getApplication();
        SimpleDateFormat monthDayFormat = new SimpleDateFormat("MMM d", Locale.getDefault());
        SimpleDateFormat dayAbbrevFormat = new SimpleDateFormat("EEE", Locale.getDefault());

        // week start day preference
        int systemFirstDay = Calendar.getInstance().getFirstDayOfWeek();
        int defaultFirstDay = (systemFirstDay == Calendar.SUNDAY) ? Calendar.SUNDAY : Calendar.MONDAY;
        int firstDayOfWeek = context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
                .getInt("week_start_day", defaultFirstDay);

        Calendar cal = Calendar.getInstance();
        cal.setFirstDayOfWeek(firstDayOfWeek);

        int currentDayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
        int delta = currentDayOfWeek - firstDayOfWeek;
        if (delta < 0) delta += 7;
        cal.add(Calendar.DAY_OF_YEAR, -delta);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        cal.add(Calendar.WEEK_OF_YEAR, -weekOffset);
        long weekStartMs = cal.getTimeInMillis();
        Date startDate = new Date(weekStartMs);

        Calendar endCal = (Calendar) cal.clone();
        endCal.add(Calendar.DAY_OF_YEAR, 6);
        endCal.set(Calendar.HOUR_OF_DAY, 23);
        endCal.set(Calendar.MINUTE, 59);
        endCal.set(Calendar.SECOND, 59);
        endCal.set(Calendar.MILLISECOND, 999);
        long weekEndMs = Math.min(endCal.getTimeInMillis(), System.currentTimeMillis());
        Date endDate = new Date(weekEndMs);

        // Generate header label
        String label;
        if (weekOffset == 0) {
            label = "This Week (" + monthDayFormat.format(startDate) + " – " + monthDayFormat.format(endDate) + ")";
        } else if (weekOffset == 1) {
            label = "Last Week (" + monthDayFormat.format(startDate) + " – " + monthDayFormat.format(endDate) + ")";
        } else {
            label = monthDayFormat.format(startDate) + " – " + monthDayFormat.format(endDate);
        }

        // compute data for the week
        appUsageMap.putAll(UsageStatsHelper.getUsageTimePerPackage(context, weekStartMs, weekEndMs));
        long totalScreenTimeMs = 0;
        for (long duration : appUsageMap.values()) totalScreenTimeMs += duration;
        int totalUnlocks = UsageStatsHelper.getDeviceUnlockCount(context, weekStartMs, weekEndMs);
        long totalIdleTimeMs = UsageStatsHelper.getIdleHomeScreenTimeMs(context, appUsageMap);

        long[] dailyUsage = new long[7];
        String[] dayLabels = new String[7];

        long maxDayTime = 0;
        String peakDayLabel = "N/A";

        int daysElapsed = 0;
        long now = System.currentTimeMillis();

        // Calculate usage per day for bar charts and identify peak day
        for (int i = 0; i < 7; i++) {
            Calendar dayCal = (Calendar) cal.clone();
            dayCal.add(Calendar.DAY_OF_YEAR, i);
            long dayStart = dayCal.getTimeInMillis();

            dayLabels[i] = dayAbbrevFormat.format(dayCal.getTime());

            // dont query days that are in the future
            if (dayStart <= now) {
                daysElapsed++;
                Calendar dayEndCal = (Calendar) dayCal.clone();
                dayEndCal.set(Calendar.HOUR_OF_DAY, 23);
                dayEndCal.set(Calendar.MINUTE, 59);
                dayEndCal.set(Calendar.SECOND, 59);
                dayEndCal.set(Calendar.MILLISECOND, 999);
                long dEnd = Math.min(dayEndCal.getTimeInMillis(), now);

                long dayUsage = UsageStatsHelper.getTotalScreenTimeMs(context, dayStart, dEnd);
                dailyUsage[i] = dayUsage;

                if (dayUsage > maxDayTime) {
                    maxDayTime = dayUsage;
                    peakDayLabel = dayLabels[i] + " (" + formatDuration(dayUsage) + ")";
                }
            } else {
                dailyUsage[i] = 0;
            }
        }

        daysElapsed = Math.max(1, daysElapsed);
        long dailyAverageMs = totalScreenTimeMs / daysElapsed;
        int dailyAverageUnlocks = (int) Math.round((double) totalUnlocks / daysElapsed);

        List<UsageStatsHelper.AppUsageInfo> topApps = UsageStatsHelper.getSortedAppUsageList(context, appUsageMap);
        UsageStatsHelper.DoomscrollStats doomscrollStats = UsageStatsHelper.getDoomscrollStats(context, weekStartMs, weekEndMs);

        return new WeeklyUsageItem(
                label,
                startDate,
                endDate,
                totalScreenTimeMs,
                dailyAverageMs,
                totalUnlocks,
                dailyAverageUnlocks,
                totalIdleTimeMs,
                dailyUsage,
                dayLabels,
                peakDayLabel,
                topApps,
                doomscrollStats
        );
    }

    private String formatDuration(long durationMs) {
        long totalMinutes = durationMs / (1000 * 60);
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        } else {
            return minutes + "m";
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        cancelPendingLoad();
        getApplication().getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
                .unregisterOnSharedPreferenceChangeListener(prefListener);
    }
}
