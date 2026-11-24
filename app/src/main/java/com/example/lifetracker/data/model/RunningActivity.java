package com.example.lifetracker.data.model;

import com.example.lifetracker.R;

public final class RunningActivity extends MovementActivity {
    @Override public String getActivityTypeName() { return "Running"; }
    @Override public int getColorRes() { return R.color.activity_running; }
    @Override public int getIconRes() { return R.drawable.sprint_24px; }
}
