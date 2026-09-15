package com.example.myapplication.database;

import android.graphics.drawable.Drawable;

public class AppUsageSession {
    public enum Category {
        PRODUCTIVE,
        DISTRACTION,
        UTILITY,
        NAVIGATION
    }

    private final String packageName;
    private final String appLabel;
    private final Drawable appIcon;
    private final long totalForegroundTimeMs;
    private final Category category;

    public AppUsageSession(String packageName, String appLabel, Drawable appIcon, long totalForegroundTimeMs, Category category) {
        this.packageName = packageName;
        this.appLabel = appLabel;
        this.appIcon = appIcon;
        this.totalForegroundTimeMs = totalForegroundTimeMs;
        this.category = category;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getAppLabel() {
        return appLabel;
    }

    public Drawable getAppIcon() {
        return appIcon;
    }

    public long getTotalForegroundTimeMs() {
        return totalForegroundTimeMs;
    }

    public Category getCategory() {
        return category;
    }
}
