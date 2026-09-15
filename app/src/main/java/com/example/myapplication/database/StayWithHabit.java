package com.example.myapplication.database;

import java.util.Date;
import java.util.Objects;

public class StayWithHabit implements TimelineItem {
    private final StillLocation stillLocation;
    private final long targetDwellMs;
    private final boolean isGoalMet;
    private final String habitBadgeText;
    private final int streakCount;

    public StayWithHabit(StillLocation stillLocation, long targetDwellMs, boolean isGoalMet, String habitBadgeText, int streakCount) {
        this.stillLocation = stillLocation;
        this.targetDwellMs = targetDwellMs;
        this.isGoalMet = isGoalMet;
        this.habitBadgeText = habitBadgeText;
        this.streakCount = streakCount;
    }

    public StillLocation getStillLocation() {
        return stillLocation;
    }

    public long getTargetDwellMs() {
        return targetDwellMs;
    }

    public boolean isGoalMet() {
        return isGoalMet;
    }

    public String getHabitBadgeText() {
        return habitBadgeText;
    }

    public int getStreakCount() {
        return streakCount;
    }

    public int getGoalProgressPct() {
        if (stillLocation == null || stillLocation.getStartTimeDate() == null || stillLocation.getEndTimeDate() == null) return 0;
        long actualDwell = stillLocation.getEndTimeDate().getTime() - stillLocation.getStartTimeDate().getTime();
        if (targetDwellMs <= 0) return 100;
        int pct = (int) ((actualDwell * 100) / targetDwellMs);
        return Math.min(100, Math.max(0, pct));
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
        StayWithHabit that = (StayWithHabit) o;
        return targetDwellMs == that.targetDwellMs &&
                isGoalMet == that.isGoalMet &&
                streakCount == that.streakCount &&
                Objects.equals(stillLocation, that.stillLocation) &&
                Objects.equals(habitBadgeText, that.habitBadgeText);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stillLocation, targetDwellMs, isGoalMet, habitBadgeText, streakCount);
    }
}
