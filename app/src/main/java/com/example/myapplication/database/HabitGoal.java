package com.example.myapplication.database;

public class HabitGoal {
    public enum GoalType {
        WORK_FOCUS,
        WALK_ACTIVITY,
        PUNCTUAL_ARRIVAL,
        CABIN_FEVER_OUTDOORS
    }

    private final GoalType goalType;
    private final String title;
    private final long targetValue; // in ms or minutes or time of day
    private final long currentValue;
    private final boolean isCompleted;
    private final int streakDays;

    public HabitGoal(GoalType goalType, String title, long targetValue, long currentValue, boolean isCompleted, int streakDays) {
        this.goalType = goalType;
        this.title = title;
        this.targetValue = targetValue;
        this.currentValue = currentValue;
        this.isCompleted = isCompleted;
        this.streakDays = streakDays;
    }

    public GoalType getGoalType() {
        return goalType;
    }

    public String getTitle() {
        return title;
    }

    public long getTargetValue() {
        return targetValue;
    }

    public long getCurrentValue() {
        return currentValue;
    }

    public boolean isCompleted() {
        return isCompleted;
    }

    public int getStreakDays() {
        return streakDays;
    }

    public int getProgressPercentage() {
        if (targetValue <= 0) return 100;
        int pct = (int) ((currentValue * 100) / targetValue);
        return Math.min(100, Math.max(0, pct));
    }
}
