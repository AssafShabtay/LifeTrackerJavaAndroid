package com.example.myapplication.mainScreen.dashboardScreen.model;

import android.graphics.drawable.Drawable;

public class TimeDrainItem {
    private final String appName;
    private final String packageName;
    private final Drawable appIcon;
    private final String focusPlaceName;
    private final long screenTimeMs;

    public TimeDrainItem(String appName, String packageName, Drawable appIcon, String focusPlaceName, long screenTimeMs) {
        this.appName = appName;
        this.packageName = packageName;
        this.appIcon = appIcon;
        this.focusPlaceName = focusPlaceName;
        this.screenTimeMs = screenTimeMs;
    }

    public String getAppName() {
        return appName;
    }

    public String getPackageName() {
        return packageName;
    }

    public Drawable getAppIcon() {
        return appIcon;
    }

    public String getFocusPlaceName() {
        return focusPlaceName;
    }

    public long getScreenTimeMs() {
        return screenTimeMs;
    }
}
