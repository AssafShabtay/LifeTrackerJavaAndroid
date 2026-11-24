package com.example.lifetracker.data.model;

import com.example.lifetracker.R;

public final class UnknownMovementActivity extends MovementActivity {
    private final String name;

    public UnknownMovementActivity(String name) { this.name = name; }
    @Override public String getActivityTypeName() { return name; }
    @Override public int getColorRes() { return R.color.activity_walking; }
    @Override public int getIconRes() { return R.drawable.ic_walk; }
}
