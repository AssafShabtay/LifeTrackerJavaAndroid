package com.example.lifetracker.data.model;

import com.example.lifetracker.R;

public final class WalkingActivity extends MovementActivity {
    @Override public String getActivityTypeName() { return "Walking"; }
    @Override public int getColorRes() { return R.color.activity_walking; }
    @Override public int getIconRes() { return R.drawable.ic_walk; }
}
