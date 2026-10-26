package com.example.myapplication.ui.statistics.askTimeline;

import java.util.Date;

public class DateLink {
    private final String displayLabel;
    private final Date date;

    public DateLink(String displayLabel, Date date) {
        this.displayLabel = displayLabel;
        this.date = date;
    }

    public String getDisplayLabel() {
        return displayLabel;
    }

    public Date getDate() {
        return date;
    }
}
