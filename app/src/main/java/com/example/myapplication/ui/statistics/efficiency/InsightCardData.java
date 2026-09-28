package com.example.myapplication.ui.statistics.efficiency;

public class InsightCardData {
    private final String title;
    private final String message;
    private final String iconType; // e.g. "win", "alert", "trend"

    public InsightCardData(String title, String message, String iconType) {
        this.title = title;
        this.message = message;
        this.iconType = iconType;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public String getIconType() {
        return iconType;
    }
}
