package com.trace.data.model;

import com.trace.R;

public final class WalkingActivity extends MovementActivity {
    @Override public String getActivityTypeName() { return "Walking"; }
    @Override public int getColorRes() { return R.color.activity_walking; }
    @Override public int getIconRes() { return R.drawable.ic_walk; }
}
