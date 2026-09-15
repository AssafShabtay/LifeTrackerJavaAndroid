package com.example.myapplication.database;

import java.util.Date;
import java.util.Objects;

public class MovementWithUsage implements TimelineItem {
    private final MovementActivity movementActivity;
    private final long totalScreenOnTimeMs;
    private final int unlockCount;
    private final boolean isHeadsDownWalking;

    public MovementWithUsage(MovementActivity movementActivity, long totalScreenOnTimeMs, int unlockCount) {
        this.movementActivity = movementActivity;
        this.totalScreenOnTimeMs = totalScreenOnTimeMs;
        this.unlockCount = unlockCount;

        String type = movementActivity != null && movementActivity.getActivityTypeName() != null
                ? movementActivity.getActivityTypeName().toUpperCase() : "";
        this.isHeadsDownWalking = ("WALKING".contains(type) || "RUNNING".contains(type))
                && totalScreenOnTimeMs > 2 * 60 * 1000; // > 2 mins screen during walk
    }

    public MovementActivity getMovementActivity() {
        return movementActivity;
    }

    public long getTotalScreenOnTimeMs() {
        return totalScreenOnTimeMs;
    }

    public int getUnlockCount() {
        return unlockCount;
    }

    public boolean isHeadsDownWalking() {
        return isHeadsDownWalking;
    }

    @Override
    public long getId() {
        return movementActivity != null ? movementActivity.getId() : -1;
    }

    @Override
    public Date getStartTimeDate() {
        return movementActivity != null ? movementActivity.getStartTimeDate() : new Date();
    }

    @Override
    public Date getEndTimeDate() {
        return movementActivity != null ? movementActivity.getEndTimeDate() : new Date();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MovementWithUsage that = (MovementWithUsage) o;
        return totalScreenOnTimeMs == that.totalScreenOnTimeMs &&
                unlockCount == that.unlockCount &&
                isHeadsDownWalking == that.isHeadsDownWalking &&
                Objects.equals(movementActivity, that.movementActivity);
    }

    @Override
    public int hashCode() {
        return Objects.hash(movementActivity, totalScreenOnTimeMs, unlockCount, isHeadsDownWalking);
    }
}
