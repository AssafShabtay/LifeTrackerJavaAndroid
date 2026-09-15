package com.example.myapplication.database;

import java.util.Date;
import java.util.Objects;

public class MovementWithHabit implements TimelineItem {
    private final MovementActivity movementActivity;
    private final long targetMovementMs;
    private final boolean isGoalMet;
    private final String habitBadgeText;

    public MovementWithHabit(MovementActivity movementActivity, long targetMovementMs, boolean isGoalMet, String habitBadgeText) {
        this.movementActivity = movementActivity;
        this.targetMovementMs = targetMovementMs;
        this.isGoalMet = isGoalMet;
        this.habitBadgeText = habitBadgeText;
    }

    public MovementActivity getMovementActivity() {
        return movementActivity;
    }

    public long getTargetMovementMs() {
        return targetMovementMs;
    }

    public boolean isGoalMet() {
        return isGoalMet;
    }

    public String getHabitBadgeText() {
        return habitBadgeText;
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
        MovementWithHabit that = (MovementWithHabit) o;
        return targetMovementMs == that.targetMovementMs &&
                isGoalMet == that.isGoalMet &&
                Objects.equals(movementActivity, that.movementActivity) &&
                Objects.equals(habitBadgeText, that.habitBadgeText);
    }

    @Override
    public int hashCode() {
        return Objects.hash(movementActivity, targetMovementMs, isGoalMet, habitBadgeText);
    }
}
