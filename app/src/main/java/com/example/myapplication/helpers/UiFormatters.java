package com.example.myapplication.helpers;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.stream.Collectors;

public class UiFormatters {

    public static String decimal(double value) {
        // DecimalFormat relies on the default locale internally as well.
        // Instantiating locally ensures safety and current locale.
        return new DecimalFormat("0.0000").format(value);
    }

    public static String category(String category) {
        return Arrays.stream(category.replace("_", " ").toLowerCase().trim().split("\\s+"))
                .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1))
                .collect(Collectors.joining(" "));
    }
    public static String speed(float metersPerSecond) {
        return new DecimalFormat("0.0").format(metersPerSecond) + " m/s";
    }

    public static String dateTime(Date date) {
        if (date == null) return "—";

        // Always fetches the most up-to-date user Locale
        SimpleDateFormat format = new SimpleDateFormat("MMM d, h:mm a", Locale.getDefault());
        return format.format(date);
    }

    public static String timeOnly(Date date) {
        if (date == null) return "Ongoing";

        SimpleDateFormat format = new SimpleDateFormat("h:mm a", Locale.getDefault());
        return format.format(date);
    }

    public static String time24Hour(Date date) {
        if (date == null) return "—";
        SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        return format.format(date);
    }

    public static String dayOfWeek(Date date) {
        if (date == null) return "—";
        SimpleDateFormat format = new SimpleDateFormat("EEEE", Locale.getDefault());
        return format.format(date);
    }

    public static String monthYear(Date date) {
        if (date == null) return "—";
        SimpleDateFormat format = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        return format.format(date);
    }

    public static String fullDate(Date date) {
        if (date == null) return "—";
        SimpleDateFormat format = new SimpleDateFormat("MMMM d, yyyy", Locale.getDefault());
        return format.format(date);
    }

    public static String isoDate(Date date) {
        if (date == null) return "—";
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        return format.format(date);
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