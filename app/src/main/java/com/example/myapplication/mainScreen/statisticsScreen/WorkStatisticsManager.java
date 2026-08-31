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

import androidx.core.content.ContextCompat;

import com.example.myapplication.LifeTrackerApp;
import com.example.myapplication.R;
import com.example.myapplication.database.ActivityDatabase;
import com.example.myapplication.database.Place;
import com.example.myapplication.database.PlaceDao;
import com.example.myapplication.database.StillLocation;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class WorkStatisticsManager {

    private final Context context;
    private final LifeTrackerApp app;
    private final Handler mainHandler;
    private final WorkStatisticsListener listener;

    private static final String PREFS_NAME = "MyPrefs";
    private static final String KEY_WEEK_START_DAY = "week_start_day";

    private final View workStatisticsContent;
    private final View workStatisticsPlaceholder;
    private final LinearLayout chartWorkHoursContainer;
    private final TextView tvWorkHoursSummary;

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

            if (workPlace == null) {
                mainHandler.post(() -> {
                    if (!listener.isFragmentAdded()) return;
                    workStatisticsContent.setVisibility(View.GONE);
                    workStatisticsPlaceholder.setVisibility(View.VISIBLE);
                });
                return;
            }

            Calendar calendar = Calendar.getInstance();
            calendar.setFirstDayOfWeek(getWeekStartDayPreference());

            // Set to the first day of the current week (Monday or Sunday)
            calendar.setTime(new Date());
            calendar.set(Calendar.DAY_OF_WEEK, calendar.getFirstDayOfWeek());
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);

            // Go back one week to get last week
            calendar.add(Calendar.WEEK_OF_YEAR, -1);
            Date startOfLastWeek = calendar.getTime();

            // Calculate end of last week (6 days after startOfLastWeek)
            calendar.add(Calendar.DAY_OF_YEAR, 6);
            calendar.set(Calendar.HOUR_OF_DAY, 23);
            calendar.set(Calendar.MINUTE, 59);
            calendar.set(Calendar.SECOND, 59);
            calendar.set(Calendar.MILLISECOND, 999);
            Date endOfLastWeek = calendar.getTime();

            // Calculate dates for the week before last week
            Calendar calendarPrevious = Calendar.getInstance();
            calendarPrevious.setFirstDayOfWeek(getWeekStartDayPreference());
            calendarPrevious.setTime(startOfLastWeek);
            calendarPrevious.add(Calendar.WEEK_OF_YEAR, -1); // Go back one more week
            Date startOfPreviousWeek = calendarPrevious.getTime();

            calendarPrevious.add(Calendar.DAY_OF_YEAR, 6); // Add 6 days to get to the end of the previous week
            calendarPrevious.set(Calendar.HOUR_OF_DAY, 23);
            calendarPrevious.set(Calendar.MINUTE, 59);
            calendarPrevious.set(Calendar.SECOND, 59);
            calendarPrevious.set(Calendar.MILLISECOND, 999);
            Date endOfPreviousWeek = calendarPrevious.getTime();


            final List<StillLocation> stillsLastWeek = db.activityDao().getStillsFromRangeAndPlace(workPlace.getId(), startOfLastWeek, endOfLastWeek);
            final List<StillLocation> stillsPreviousWeek = db.activityDao().getStillsFromRangeAndPlace(workPlace.getId(), startOfPreviousWeek, endOfPreviousWeek);


            // create a map to store daily work minutes throughout the week
            final Map<Long, Long> dailyWorkMinutes = new HashMap<>();
            Calendar tempCal = Calendar.getInstance();
            tempCal.setTime(startOfLastWeek);
            for (int i = 0; i < 7; i++) {
                dailyWorkMinutes.put(getDayStartTimestamp(tempCal.getTime()), 0L);
                tempCal.add(Calendar.DAY_OF_YEAR, 1);
            }
            tempCal.setTime(startOfLastWeek); // Reset for later use

            for (StillLocation still : stillsLastWeek) {
                Date stillDate = still.getStartTimeDate();
                Long stillDayKey = getDayStartTimestamp(stillDate);
                if (dailyWorkMinutes.containsKey(stillDayKey)) {
                    long durationMs = calculateStillDuration(still, startOfLastWeek, endOfLastWeek);
                    long durationMins = durationMs / (1000 * 60);
                    if (durationMins > 0) {
                        dailyWorkMinutes.merge(stillDayKey, durationMins, Long::sum);
                    }
                }
            }

            // Calculate daily work minutes for the previous week
            final Map<Long, Long> dailyWorkMinutesPreviousWeek = new HashMap<>();
            Calendar tempCalPrevious = Calendar.getInstance();
            tempCalPrevious.setTime(startOfPreviousWeek);
            for (int i = 0; i < 7; i++) {
                dailyWorkMinutesPreviousWeek.put(getDayStartTimestamp(tempCalPrevious.getTime()), 0L);
                tempCalPrevious.add(Calendar.DAY_OF_YEAR, 1);
            }

            for (StillLocation still : stillsPreviousWeek) {
                Date stillDate = still.getStartTimeDate();
                Long stillDayKey = getDayStartTimestamp(stillDate);
                if (dailyWorkMinutesPreviousWeek.containsKey(stillDayKey)) {
                    long durationMs = calculateStillDuration(still, startOfPreviousWeek, endOfPreviousWeek);
                    long durationMins = durationMs / (1000 * 60);
                    if (durationMins > 0) {
                        dailyWorkMinutesPreviousWeek.merge(stillDayKey, durationMins, Long::sum);
                    }
                }
            }

            long totalWorkMinutesPreviousWeek = 0;
            for (Long minutes : dailyWorkMinutesPreviousWeek.values()) {
                totalWorkMinutesPreviousWeek += minutes;
            }
            final double previousWeekTotalHours = totalWorkMinutesPreviousWeek / 60.0;


            // move daily minutes to a list of hours for the chart
            List<Double> workHoursPerDay = new ArrayList<>();
            long totalWorkMinutes = 0;

            tempCal.setTime(startOfLastWeek); // Reset calendar to start of last week
            for (int i = 0; i < 7; i++) {
                Long dayKey = getDayStartTimestamp(tempCal.getTime());
                long minutes = Objects.requireNonNullElse(dailyWorkMinutes.get(dayKey), 0L);
                workHoursPerDay.add(minutes / 60.0);
                totalWorkMinutes += minutes;
                tempCal.add(Calendar.DAY_OF_YEAR, 1);
            }

            final double finalTotalWorkHours = totalWorkMinutes / 60.0;
            final List<Double> finalWorkHoursPerDay = workHoursPerDay;

            mainHandler.post(() -> {
                if (!listener.isFragmentAdded()) return;
                workStatisticsContent.setVisibility(View.VISIBLE);
                workStatisticsPlaceholder.setVisibility(View.GONE);

                // Display the bar chart
                drawWorkHoursBarChart(finalWorkHoursPerDay);


                tvWorkHoursSummary.setText(String.format(Locale.getDefault(),
                        "You worked %.0f hours last week. This is compared to last when you worked %.0f hours per week.",
                        finalTotalWorkHours, previousWeekTotalHours));
            });
        });
    }

    private long calculateStillDuration(StillLocation still, Date rangeStart, Date rangeEnd) {
        Date startTime = still.getStartTimeDate();
        Date endTime = still.getEndTimeDate();

        if (endTime == null) endTime = new Date(); // If still active, end should be now

        // if the start is before the range start or the end is after the range end, then adjust accordingly
        Date actualStart = startTime.before(rangeStart) ? rangeStart : startTime;
        Date actualEnd = endTime.after(rangeEnd) ? rangeEnd : endTime;

        if (actualStart.after(actualEnd)) {
            return 0;
        }

        return actualEnd.getTime() - actualStart.getTime();
    }

    private int getWeekStartDayPreference() {
        SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return preferences.getInt(KEY_WEEK_START_DAY, Calendar.SUNDAY);
    }

    private void drawWorkHoursBarChart(List<Double> dailyHours) {
        LinearLayout chartContainer = chartWorkHoursContainer;
        if (chartContainer == null || dailyHours == null || dailyHours.isEmpty()) return;

        chartContainer.removeAllViews();

        // Find max hours to scale the bars
        double maxHours = 0;
        for (Double hours : dailyHours) {
            if (hours > maxHours) {
                maxHours = hours;
            }
        }
        if (maxHours == 0) maxHours = 1;
        if (maxHours < 9) maxHours = 9;

        int colorDark = Color.parseColor("#4CAF50");// TODO FIX COLORS
        int colorLight = Color.parseColor("#DCEDC8");// TODO FIX COLORS

        String[] allDays = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
        String[] days = new String[7];

        for (int i = 0; i < 7; i++) {
            days[i] = allDays[(getWeekStartDayPreference() + i - 1) % 7];
        }


        for (int i = 0; i < dailyHours.size(); i++) {
            LinearLayout dayColumn = new LinearLayout(context);
            dayColumn.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams columnParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
            dayColumn.setLayoutParams(columnParams);
            dayColumn.setGravity(android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL);

            dayColumn.setWeightSum(1.0f);

            TextView hoursLabel = new TextView(context);
            hoursLabel.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            double hoursWorked = dailyHours.get(i);
            if (hoursWorked > 0) {
                hoursLabel.setText(new java.text.DecimalFormat("0.#").format(hoursWorked) + "h");
            } else {
                hoursLabel.setText("");
            }

            hoursLabel.setTextSize(9f);
            hoursLabel.setTextColor(ContextCompat.getColor(context, R.color.on_surface_variant));
            hoursLabel.setPadding(0, 0, 0, 4); // Small gap between text and the bar
            dayColumn.addView(hoursLabel);

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
            dayLabel.setTextColor(ContextCompat.getColor(context, R.color.on_surface_variant));

            dayColumn.addView(dayLabel);
            chartContainer.addView(dayColumn);
        }
    }

    private long getDayStartTimestamp(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }
}