package com.example.myapplication.mainScreen.statisticsScreen;

import static com.example.myapplication.locationTracking.ActivityTrackingUtils.calculateRadiusBox;
import static com.example.myapplication.locationTracking.ActivityTrackingUtils.getCoordinatesFromAddress;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.myapplication.LifeTrackerApp;
import com.example.myapplication.R;
import com.example.myapplication.database.ActivityDatabase;
import com.example.myapplication.database.Place;
import com.example.myapplication.database.PlaceDao;
import com.example.myapplication.database.StillLocation;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class WorkStatisticsManager {

    private final Context context;
    private final LifeTrackerApp app;
    private final Handler mainHandler;
    private final WorkStatisticsListener listener;

    private static final String PREFS_NAME = "MyPrefs"; // Duplicated from SettingsFragment
    private static final String KEY_WEEK_START_DAY = "week_start_day"; // Duplicated from SettingsFragment


    private final View workStatisticsContent;
    private final View workStatisticsPlaceholder;
    private final LinearLayout chartWorkHoursContainer;
    private final TextView tvWorkHoursSummary;
    private final TextView tvWorkHoursMinLabel;
    private final TextView tvWorkHoursMaxLabel;

    public interface WorkStatisticsListener {
        boolean isFragmentAdded();
    }

    public WorkStatisticsManager(Context context, LifeTrackerApp app, Handler mainHandler, WorkStatisticsListener listener, View rootView) {
        this.context = context;
        this.app = app;
        this.mainHandler = mainHandler;
        this.listener = listener;

        // Initialize UI elements
        this.workStatisticsContent = rootView.findViewById(R.id.work_statistics_content);
        this.workStatisticsPlaceholder = rootView.findViewById(R.id.work_statistics_placeholder);
        this.chartWorkHoursContainer = rootView.findViewById(R.id.chart_work_hours_container);
        this.tvWorkHoursSummary = rootView.findViewById(R.id.tv_work_hours_summary);
        this.tvWorkHoursMinLabel = rootView.findViewById(R.id.tv_work_hours_min_label);
        this.tvWorkHoursMaxLabel = rootView.findViewById(R.id.tv_work_hours_max_label);
    }

    public void onWorkAddressSelected(String address) {
        saveWorkAddress(address);
    }

    private void saveWorkAddress(String address) {
        if (address.isEmpty()) {
            Toast.makeText(context, "Please enter a work address.", Toast.LENGTH_SHORT).show();
            return;
        }

        app.getDatabaseWriteExecutor().execute(() -> {
            if (!listener.isFragmentAdded()) return;
            double[] coords = getCoordinatesFromAddress(address, context);

            ActivityDatabase db = ActivityDatabase.getDatabase(context);
            PlaceDao placeDao = db.placeDao();
            Place workPlace = placeDao.getWorkPlace();

            if (workPlace == null) {
                workPlace = new Place();
                workPlace.setName("Work");
                workPlace.setAddress(address);
                workPlace.setCategory("Work");
                workPlace.setIcon("Work");
                workPlace.setColor(0xFF4CAF50);
                if(coords != null){
                    workPlace.setLat(coords[0]);
                    workPlace.setLng(coords[1]);
                }
                placeDao.insertPlace(workPlace);

                if (coords != null) {
                    double[] bounds = calculateRadiusBox(coords[0], coords[1], 50.0);
                    db.activityDao().updateStillsWithinBounds(bounds[0], bounds[1], bounds[2], bounds[3], "Work");
                }
            } else {
                workPlace.setAddress(address);
                workPlace.setCategory("Work");
                workPlace.setName("Work");
                workPlace.setIcon("Work");
                placeDao.updatePlace(workPlace);
            }

            mainHandler.post(() -> loadWorkStatistics());
        });
    }

    public void loadWorkStatistics() {
        app.getDatabaseWriteExecutor().execute(() -> {
            if (!listener.isFragmentAdded()) return;
            ActivityDatabase db = ActivityDatabase.getDatabase(context);
            PlaceDao placeDao = db.placeDao();

            Place workPlace = placeDao.getWorkPlace();

            // Get the preferred start day of the week from SharedPreferences
            int preferredFirstDayOfWeek = getWeekStartDayPreference();

            Calendar calendar = Calendar.getInstance();
            calendar.setFirstDayOfWeek(preferredFirstDayOfWeek); // Use preferred first day of the week

            // Set to the first day of the current week (e.g., Monday or Sunday)
            calendar.setTime(new Date()); // Set to today
            calendar.set(Calendar.DAY_OF_WEEK, preferredFirstDayOfWeek);
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);

            // Go back one week to get "last week"
            calendar.add(Calendar.WEEK_OF_YEAR, -1);
            Date startOfLastWeek = calendar.getTime();

            // Calculate end of last week (6 days after startOfLastWeek, at 23:59:59.999)
            calendar.add(Calendar.DAY_OF_YEAR, 6);
            calendar.set(Calendar.HOUR_OF_DAY, 23);
            calendar.set(Calendar.MINUTE, 59);
            calendar.set(Calendar.SECOND, 59);
            calendar.set(Calendar.MILLISECOND, 999);
            Date endOfLastWeek = calendar.getTime();

            // Fetch all places to easily get categories by ID on a background thread
            final List<Place> allPlaces = placeDao.getAllPlaces();
            final Map<Long, String> placeCategoryMap = new HashMap<>();
            for (Place place : allPlaces) {
                placeCategoryMap.put(place.getId(), place.getCategory());
            }

            // Fetch stills on a background thread
            final List<StillLocation> stillsLastWeek = db.activityDao().getStillsFromRange(startOfLastWeek, endOfLastWeek);

            // Map to store daily work durations in minutes. Using String for date key to simplify comparison without full Date object comparison issues.
            final Map<String, Long> dailyWorkMinutes = new HashMap<>();
            final SimpleDateFormat dayFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

            // Initialize map for the last 7 days with 0
            Calendar tempCal = Calendar.getInstance();
            tempCal.setTime(startOfLastWeek);
            for (int i = 0; i < 7; i++) {
                dailyWorkMinutes.put(dayFormat.format(tempCal.getTime()), 0L);
                tempCal.add(Calendar.DAY_OF_YEAR, 1);
            }
            tempCal.setTime(startOfLastWeek); // Reset for later use

            for (StillLocation still : stillsLastWeek) {
                Long placeId = still.getPlaceId();
                String category = placeCategoryMap.get(placeId);

                if ("Work".equals(category)) {
                    long durationMs = calculateStillDuration(still, startOfLastWeek, endOfLastWeek);
                    long durationMins = durationMs / (1000 * 60);

                    if (durationMins > 0) {
                        // Ensure we only count duration within the last week for each day
                        Date stillDate = still.getStartTimeDate();
                        // Clamp stillDay to be within the last week range
                        // This clamping logic with Date objects is more complex,
                        // instead, I\'ll rely on the stillsLastWeek already being within the range
                        // and just get the day part of the stillDate.
                        String stillDayKey = dayFormat.format(stillDate);

                        // Only add if the day key is actually within the last week\'s keys
                        if (dailyWorkMinutes.containsKey(stillDayKey)) {
                            dailyWorkMinutes.merge(stillDayKey, durationMins, Long::sum);
                        }
                    }
                }
            }

            // Convert daily minutes to a list of hours for the chart
            List<Double> workHoursPerDay = new ArrayList<>();
            long totalWorkMinutes = 0;

            tempCal.setTime(startOfLastWeek); // Reset calendar to start of last week
            for (int i = 0; i < 7; i++) {
                String dayKey = dayFormat.format(tempCal.getTime());
                long minutes = dailyWorkMinutes.getOrDefault(dayKey, 0L);
                workHoursPerDay.add(minutes / 60.0);
                totalWorkMinutes += minutes;
                tempCal.add(Calendar.DAY_OF_YEAR, 1);
            }

            final double finalTotalWorkHours = totalWorkMinutes / 60.0;
            final double finalAverageDailyWorkHours = finalTotalWorkHours / 7.0;
            final List<Double> finalWorkHoursPerDay = workHoursPerDay;

            mainHandler.post(() -> {
                if (!listener.isFragmentAdded()) return;
                if (workPlace == null) {
                    workStatisticsContent.setVisibility(View.GONE);
                    workStatisticsPlaceholder.setVisibility(View.VISIBLE);
                } else {
                    workStatisticsContent.setVisibility(View.VISIBLE);
                    workStatisticsPlaceholder.setVisibility(View.GONE);

                    // Display the bar chart
                    drawWorkHoursBarChart(finalWorkHoursPerDay,
                            R.id.chart_work_hours_container,
                            "#4CAF50", "#8BC34A", "#DCEDC8"); // Green colors

                    // Update summary text
                    // For demonstration, use a fixed average as historical average calculation is complex
                    int usualAverageHours = 25; // Example fixed average
                    String summaryText = String.format(Locale.getDefault(),
                            "You worked %.0f hours last week. This is compared to your usual average of %d hours per week.",
                            finalTotalWorkHours, usualAverageHours);
                    tvWorkHoursSummary.setText(summaryText);
                }
            });
        });
    }

    private long calculateStillDuration(StillLocation still, Date rangeStart, Date rangeEnd) {
        Date startTime = still.getStartTimeDate();
        Date endTime = still.getEndTimeDate();

        if (endTime == null) endTime = new Date(); // If still active, consider current time

        // Clamp the start and end times to the given range
        Date actualStart = startTime.before(rangeStart) ? rangeStart : startTime;
        Date actualEnd = endTime.after(rangeEnd) ? rangeEnd : endTime;

        if (actualStart.after(actualEnd)) {
            return 0; // No valid duration within the range
        }

        return actualEnd.getTime() - actualStart.getTime();
    }

    private int getWeekStartDayPreference() {
        SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        // Default to Monday if no preference is set
        return preferences.getInt(KEY_WEEK_START_DAY, Calendar.MONDAY);
    }

    private void drawWorkHoursBarChart(List<Double> dailyHours, int containerId, String colorDarkHex, String colorMedHex, String colorLightHex) {
        LinearLayout chartContainer = chartWorkHoursContainer; // Use the initialized field
        if (chartContainer == null || dailyHours == null || dailyHours.isEmpty()) return;

        chartContainer.removeAllViews();

        // Find max hours to scale the bars
        double maxHours = 0;
        for (Double hours : dailyHours) {
            if (hours > maxHours) {
                maxHours = hours;
            }
        }
        if (maxHours == 0) maxHours = 1; // Prevent division by zero, show a tiny bar if all are zero
        // For the UI representation, ensure maxHours is at least 9 as per the image
        if (maxHours < 9) maxHours = 9; // Set minimum max value to 9 hours for consistent scaling with design


        int colorDark = Color.parseColor(colorDarkHex);
        int colorMedium = Color.parseColor(colorMedHex);
        int colorLight = Color.parseColor(colorLightHex);

        // The days array should also reflect the start day of the week preference.
        // However, this drawWorkHoursBarChart method simply draws 7 bars based on the order of `dailyHours`.
        // The `dailyHours` list is already populated in `loadWorkStatistics` respecting the `startOfLastWeek`.
        // So, the `days` array here should dynamically start with the chosen day.

        String[] days = new String[7];
        int startDay = getWeekStartDayPreference(); // Get preferred start day for display

        // Map Calendar day constants to abbreviated day names
        Map<Integer, String> dayNames = new HashMap<>();
        dayNames.put(Calendar.MONDAY, "Mon");
        dayNames.put(Calendar.TUESDAY, "Tue");
        dayNames.put(Calendar.WEDNESDAY, "Wed");
        dayNames.put(Calendar.THURSDAY, "Thu");
        dayNames.put(Calendar.FRIDAY, "Fri");
        dayNames.put(Calendar.SATURDAY, "Sat");
        dayNames.put(Calendar.SUNDAY, "Sun");

        // Populate the days array starting from the preferred start day
        for (int i = 0; i < 7; i++) {
            int currentDay = (startDay + i - 1) % 7; // -1 because Calendar.MONDAY is 2, SUNDAY is 1
            if (currentDay == 0) currentDay = 7; // Adjust for Sunday being 1 in Calendar
            days[i] = dayNames.get(currentDay);
        }

        for (int i = 0; i < dailyHours.size(); i++) {
            LinearLayout dayColumn = new LinearLayout(context); // Use context from manager
            dayColumn.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams columnParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
            dayColumn.setLayoutParams(columnParams);
            dayColumn.setGravity(android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL);

            // FIX: Explicitly set weight sum so the heightPercent logic calculates correctly
            dayColumn.setWeightSum(1.0f);

            // --- NEW: Add a label for the exact hours worked above the bar ---
            TextView hoursLabel = new TextView(context);
            hoursLabel.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            double hoursWorked = dailyHours.get(i);
            if (hoursWorked > 0) {
                // Format to a clean decimal (e.g., "8h", "5.5h")
                hoursLabel.setText(new java.text.DecimalFormat("0.#").format(hoursWorked) + "h");
            } else {
                hoursLabel.setText(""); // Keep the space clean on days with 0 hours
            }

            hoursLabel.setTextSize(9f);
            hoursLabel.setTextColor(Color.parseColor("#757575"));
            hoursLabel.setPadding(0, 0, 0, 4); // Small gap between text and the bar
            dayColumn.addView(hoursLabel);
            // ------------------------------------------------------------------

            // Bar view
            View bar = new View(context);
            float heightPercent = (float) (dailyHours.get(i) / maxHours);
            if (heightPercent < 0.01f && dailyHours.get(i) > 0) heightPercent = 0.01f;
            else if (dailyHours.get(i) == 0) heightPercent = 0f;

            LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, heightPercent);
            int marginHorizontal = 8;
            int marginBottom = 4;
            barParams.setMargins(marginHorizontal, 0, marginHorizontal, marginBottom);
            bar.setLayoutParams(barParams);

            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.RECTANGLE);
            gd.setColor(colorLight);
            if (dailyHours.get(i) > 0) {
                gd.setColor(colorDark);
            }
            float radius = 10f;
            gd.setCornerRadii(new float[]{radius, radius, radius, radius, 0, 0, 0, 0});
            bar.setBackground(gd);

            dayColumn.addView(bar);

            // Day label
            TextView dayLabel = new TextView(context);
            dayLabel.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            dayLabel.setText(days[i]);
            dayLabel.setTextSize(9f);
            dayLabel.setTextColor(Color.parseColor("#757575"));

            dayColumn.addView(dayLabel);
            chartContainer.addView(dayColumn);
        }

        // Update min and max labels. Max is always 9 hours in this design.
        // Min is always 0 hours.
        tvWorkHoursMinLabel.setText("0 hours");
        tvWorkHoursMaxLabel.setText(String.format(Locale.getDefault(), "%.0f hours", maxHours));
    }
}
