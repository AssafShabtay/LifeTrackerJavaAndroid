package com.example.myapplication.helpers;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class UiFormatters {

    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("0.0000");
    private static final DecimalFormat SPEED_FORMAT = new DecimalFormat("0.0");
    private static final SimpleDateFormat TIME_ONLY_FORMAT =
            new SimpleDateFormat("h:mm a", Locale.getDefault());
    private static final SimpleDateFormat DATE_TIME_FORMAT =
            new SimpleDateFormat("MMM d, h:mm a", Locale.getDefault());

    public static String decimal(double value) {
        return DECIMAL_FORMAT.format(value);
    }

    public static String speed(float metersPerSecond) {
        return SPEED_FORMAT.format(metersPerSecond) + " m/s";
    }

    public static String dateTime(Date date) {
        return date == null ? "—" : DATE_TIME_FORMAT.format(date);
    }

    public static String timeOnly(Date date) {
        return date == null ? "Ongoing" : TIME_ONLY_FORMAT.format(date);
    }

    public static String duration(Date start, Date end) {
        if (start == null) return "—";

        Date effectiveEnd = (end != null) ? end : new Date();
        long diffMs = Math.max(0, effectiveEnd.getTime() - start.getTime());
        long totalMinutes = diffMs / 60000;
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;

        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m";
    }
}