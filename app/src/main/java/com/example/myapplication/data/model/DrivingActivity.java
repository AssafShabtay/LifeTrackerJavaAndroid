package com.example.myapplication.data.model;

import com.example.myapplication.R;

public final class DrivingActivity extends MovementActivity {
    @Override public String getActivityTypeName() { return "Driving"; }
    @Override public int getColorRes() { return R.color.activity_vehicle; }
    @Override public int getIconRes() { return R.drawable.ic_car; }
}
