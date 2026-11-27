package com.trace.data.model;

import com.trace.R;

public final class RunningActivity extends MovementActivity {
    @Override public String getActivityTypeName() { return "Running"; }
    @Override public int getColorRes() { return R.color.activity_running; }
    @Override public int getIconRes() { return R.drawable.sprint_24px; }
}
