package com.example.myapplication.database;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

public class StayWithUsage implements TimelineItem {
    private final StillLocation stillLocation;
    private final long totalScreenOnTimeMs;
    private final int unlockCount;
    private final List<AppUsageSession> appUsageList;

    public StayWithUsage(StillLocation stillLocation, long totalScreenOnTimeMs, int unlockCount, List<AppUsageSession> appUsageList) {
        this.stillLocation = stillLocation;
        this.totalScreenOnTimeMs = totalScreenOnTimeMs;
        this.unlockCount = unlockCount;
        this.appUsageList = appUsageList != null ? appUsageList : new ArrayList<>();
    }

    public StillLocation getStillLocation() {
        return stillLocation;
    }

    public long getTotalScreenOnTimeMs() {
        return totalScreenOnTimeMs;
    }

    public int getUnlockCount() {
        return unlockCount;
    }

    public List<AppUsageSession> getAppUsageList() {
        return appUsageList;
    }

    public float getScreenRatio() {
        if (getStartTimeDate() == null || getEndTimeDate() == null) return 0f;
        long duration = getEndTimeDate().getTime() - getStartTimeDate().getTime();
        if (duration <= 0) return 0f;
        return Math.min(1.0f, (float) totalScreenOnTimeMs / duration);
    }

    @Override
    public long getId() {
        return stillLocation != null ? stillLocation.getId() : -1;
    }

    @Override
    public Date getStartTimeDate() {
        return stillLocation != null ? stillLocation.getStartTimeDate() : new Date();
    }

    @Override
    public Date getEndTimeDate() {
        return stillLocation != null ? stillLocation.getEndTimeDate() : new Date();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StayWithUsage that = (StayWithUsage) o;
        return totalScreenOnTimeMs == that.totalScreenOnTimeMs &&
                unlockCount == that.unlockCount &&
                Objects.equals(stillLocation, that.stillLocation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stillLocation, totalScreenOnTimeMs, unlockCount);
    }
}
