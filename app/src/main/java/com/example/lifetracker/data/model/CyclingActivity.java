package com.example.lifetracker.data.model;

import com.example.lifetracker.R;

public final class CyclingActivity extends MovementActivity {
    @Override public String getActivityTypeName() { return "Cycling"; }
    @Override public int getColorRes() { return R.color.activity_cycling; }
    @Override public int getIconRes() { return R.drawable.ic_bike; }
}
