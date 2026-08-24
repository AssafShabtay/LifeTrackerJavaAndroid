package com.example.myapplication.mainScreen.statisticsScreen;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.myapplication.LifeTrackerApp;
import com.example.myapplication.R;
import com.example.myapplication.database.ActivityDatabase;
import com.example.myapplication.database.StillLocation;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PunctualityStatisticsManager {

    private final Context context;
    private final LifeTrackerApp app;
    private final Handler mainHandler;
    private final PunctualityStatisticsListener listener;

    // UI elements to update, passed from the fragment
    private final TextView tvPunctualityTitle;
    private final TextView btnPrevPlace;
    private final TextView btnNextPlace;
    private final LinearLayout chartArrivalContainer;
    private final LinearLayout chartDepartureContainer;
    private final TextView tvArrivalAvg;
    private final TextView tvDepartureAvg;


    // Data shared with fragment (could be passed in or managed via callbacks)
    private List<String> topPlaceIds;
    private Map<String, String> placeNamesMap;
    private Map<String, List<Integer>> arrivalTimesMap;
    private Map<String, List<Integer>> departureTimesMap;
    private int currentPlaceIndex;

    public interface PunctualityStatisticsListener {
        boolean isFragmentAdded();

        View getFragmentView();
        Map<String, Long> getPlaceDurationMap(); // To pass placeDuration to bottom sheets if needed
        // Callbacks for updating fragment's internal state
        void updateTopPlaceIds(List<String> newTopPlaceIds);
        void updatePlaceNamesMap(Map<String, String> newPlaceNamesMap);
        void updateArrivalTimesMap(Map<String, List<Integer>> newArrivalTimesMap);
        void updateDepartureTimesMap(Map<String, List<Integer>> newDepartureTimesMap);
        void updateCurrentPlaceIndex(int newCurrentPlaceIndex);
        List<String> getTopPlaceIds();
        Map<String, String> getPlaceNamesMap();
        Map<String, List<Integer>> getArrivalTimesMap();
        Map<String, List<Integer>> getDepartureTimesMap();
        int getCurrentPlaceIndex();
    }

    public PunctualityStatisticsManager(Context context, LifeTrackerApp app, Handler mainHandler, PunctualityStatisticsListener listener, View rootView) {
        this.context = context;
        this.app = app;
        this.mainHandler = mainHandler;
        this.listener = listener;

        this.tvPunctualityTitle = rootView.findViewById(R.id.tv_punctuality_title);
        this.btnPrevPlace = rootView.findViewById(R.id.btn_prev_place);
        this.btnNextPlace = rootView.findViewById(R.id.btn_next_place);
        this.chartArrivalContainer = rootView.findViewById(R.id.chart_arrival_container);
        this.chartDepartureContainer = rootView.findViewById(R.id.chart_departure_container);
        this.tvArrivalAvg = rootView.findViewById(R.id.tv_arrival_avg);
        this.tvDepartureAvg = rootView.findViewById(R.id.tv_departure_avg);


        this.topPlaceIds = listener.getTopPlaceIds();
        this.placeNamesMap = listener.getPlaceNamesMap();
        this.arrivalTimesMap = listener.getArrivalTimesMap();
        this.departureTimesMap = listener.getDepartureTimesMap();
        this.currentPlaceIndex = listener.getCurrentPlaceIndex();
    }

    public void loadArrivalDepartureStats() {
        long now = System.currentTimeMillis();
        long thirtyDaysMs = 30L * 24 * 60 * 60 * 1000;
        long thirtyDaysAgo = now - thirtyDaysMs;

        app.getDatabaseWriteExecutor().execute(() -> {
            if (!listener.isFragmentAdded()) return;

            ActivityDatabase db = ActivityDatabase.getDatabase(context);
            List<StillLocation> history = db.activityDao().getStillsFromRange(new Date(thirtyDaysAgo), new Date(now));

            Map<String, List<Integer>> tempArrivals = new HashMap<>();
            Map<String, List<Integer>> tempDepartures = new HashMap<>();
            Map<String, Integer> visitCounts = new HashMap<>();
            Map<String, String> tempNames = new HashMap<>();

            for (StillLocation item : history) {
                String placeId = String.valueOf(item.getPlaceId());
                String placeName = item.getPlaceName();

                if (placeId == null || placeId.equals("null") || placeId.isEmpty() || "Home".equals(placeName)) continue;

                tempNames.put(placeId, placeName != null ? placeName : "Unknown Place");

                if (item.getStartTimeDate() != null) {
                    Calendar cal = Calendar.getInstance();
                    cal.setTime(item.getStartTimeDate());
                    int arrivalMins = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
                    tempArrivals.computeIfAbsent(placeId, k -> new ArrayList<>()).add(arrivalMins);
                }

                if (item.getEndTimeDate() != null) {
                    Calendar cal = Calendar.getInstance();
                    cal.setTime(item.getEndTimeDate());
                    int departureMins = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
                    tempDepartures.computeIfAbsent(placeId, k -> new ArrayList<>()).add(departureMins);
                }

                visitCounts.put(placeId, visitCounts.getOrDefault(placeId, 0) + 1);
            }

            // --- NEW FILTERING LOGIC ---
            int MIN_VISITS = 3; // Must have visited at least 3 times in the timeframe
            int MAX_STD_DEV_MINUTES = 60; // Standard deviation must be <= 60 minutes to be considered a "routine"

            List<Map.Entry<String, Integer>> interestingPlaces = new ArrayList<>();

            for (Map.Entry<String, Integer> entry : visitCounts.entrySet()) {
                String placeId = entry.getKey();
                int count = entry.getValue();

                // 1. Check if frequent enough
                if (count < MIN_VISITS) continue;

                List<Integer> arrivals = tempArrivals.get(placeId);
                List<Integer> departures = tempDepartures.get(placeId);

                PrecisionResult arrStats = calculatePrecision(arrivals);
                PrecisionResult depStats = calculatePrecision(departures);

                boolean hasConsistentArrival = arrStats != null && arrStats.stdDev <= MAX_STD_DEV_MINUTES;
                boolean hasConsistentDeparture = depStats != null && depStats.stdDev <= MAX_STD_DEV_MINUTES;

                // 2. Check if the times are "close enough" (a tight routine on either arrival OR departure)
                if (hasConsistentArrival || hasConsistentDeparture) {
                    interestingPlaces.add(entry);
                }
            }

            // Sort the *filtered* places by visit count (descending)
            interestingPlaces.sort((e1, e2) -> e2.getValue().compareTo(e1.getValue()));

            // Save data to class-level variables to allow fast UI switching
            topPlaceIds.clear();
            for (int i = 0; i < Math.min(5, interestingPlaces.size()); i++) {
                topPlaceIds.add(interestingPlaces.get(i).getKey());
            }

            arrivalTimesMap = tempArrivals;
            departureTimesMap = tempDepartures;
            placeNamesMap = tempNames;
            currentPlaceIndex = 0; // Reset index to the most visited place

            // Update fragment's state
            listener.updateTopPlaceIds(topPlaceIds);
            listener.updateArrivalTimesMap(arrivalTimesMap);
            listener.updateDepartureTimesMap(departureTimesMap);
            listener.updatePlaceNamesMap(placeNamesMap);
            listener.updateCurrentPlaceIndex(currentPlaceIndex);

            mainHandler.post(this::displayCurrentPlaceStats);
        });
    }

    public void displayCurrentPlaceStats() {
        if (!listener.isFragmentAdded()) return;

        // Retrieve current state from listener
        List<String> currentTopPlaceIds = listener.getTopPlaceIds();
        Map<String, String> currentPlaceNamesMap = listener.getPlaceNamesMap();
        Map<String, List<Integer>> currentArrivalTimesMap = listener.getArrivalTimesMap();
        Map<String, List<Integer>> currentDepartureTimesMap = listener.getDepartureTimesMap();
        int currentPlaceIndex = listener.getCurrentPlaceIndex();


        if (currentTopPlaceIds.isEmpty()) {
            tvPunctualityTitle.setText("No data");
            btnPrevPlace.setVisibility(View.INVISIBLE);
            btnNextPlace.setVisibility(View.INVISIBLE);

            // Clear graphs safely
            chartArrivalContainer.removeAllViews();
            chartDepartureContainer.removeAllViews();
            tvArrivalAvg.setVisibility(View.INVISIBLE);
            tvDepartureAvg.setVisibility(View.INVISIBLE);
            return;
        }

        // Update button visibilities depending on the current page
        btnPrevPlace.setVisibility(currentPlaceIndex > 0 ? View.VISIBLE : View.INVISIBLE);
        btnNextPlace.setVisibility(currentPlaceIndex < currentTopPlaceIds.size() - 1 ? View.VISIBLE : View.INVISIBLE);

        String currentPlaceId = currentTopPlaceIds.get(currentPlaceIndex);
        String currentPlaceName = currentPlaceNamesMap.get(currentPlaceId);

        tvPunctualityTitle.setText("Punctuality: " + currentPlaceName);

        List<Integer> currentArrivals = currentArrivalTimesMap.get(currentPlaceId);
        List<Integer> currentDepartures = currentDepartureTimesMap.get(currentPlaceId);

        PrecisionResult arrivalStats = currentArrivals != null ? calculatePrecision(currentArrivals) : null;
        PrecisionResult departureStats = currentDepartures != null ? calculatePrecision(currentDepartures) : null;

        // Draw Arrival Graph
        if (arrivalStats != null) {
            drawHistogram(currentArrivals, arrivalStats.mean,
                    R.id.chart_arrival_container, R.id.tv_arrival_start,
                    R.id.tv_arrival_avg, R.id.tv_arrival_end,
                    "#5C5CD6", "#8A8ADF", "#E0E0F8");
        } else {
            chartArrivalContainer.removeAllViews();
            tvArrivalAvg.setVisibility(View.INVISIBLE);
        }

        // Draw Departure Graph
        if (departureStats != null) {
            drawHistogram(currentDepartures, departureStats.mean,
                    R.id.chart_departure_container, R.id.tv_departure_start,
                    R.id.tv_departure_avg, R.id.tv_departure_end,
                    "#009688", "#4DB6AC", "#E0F2F1");
        } else {
            chartDepartureContainer.removeAllViews();
            tvDepartureAvg.setVisibility(View.INVISIBLE);
        }
    }


    private void drawHistogram(List<Integer> times, int mean, int containerId, int startLabelId, int avgLabelId, int endLabelId, String colorDarkHex, String colorMedHex, String colorLightHex) {
        TextView tvLabelStart = listener.getFragmentView().findViewById(startLabelId);
        TextView tvLabelAvg = listener.getFragmentView().findViewById(avgLabelId);
        TextView tvLabelEnd = listener.getFragmentView().findViewById(endLabelId);
        LinearLayout chartContainer = listener.getFragmentView().findViewById(containerId);

        if (chartContainer == null || times == null || times.isEmpty()) return;

        int minTime = Collections.min(times);
        int maxTime = Collections.max(times);

        // Expand edges to prevent flat-lining if the user is too punctual
        if (maxTime - minTime < 30) {
            minTime = mean - 15;
            maxTime = mean + 15;
        }

        tvLabelStart.setText(formatMinutesToTime(minTime));
        tvLabelAvg.setText(formatMinutesToTime(mean)); // Removed "Avg" text so it fits the half-screen width
        tvLabelEnd.setText(formatMinutesToTime(maxTime));

        int numBins = 5;
        int[] bins = new int[numBins];
        double binWidth = (maxTime - minTime) / (double) numBins;

        for (int time : times) {
            int binIndex = (int) ((time - minTime) / binWidth);
            if (binIndex >= numBins) binIndex = numBins - 1;
            if (binIndex < 0) binIndex = 0;
            bins[binIndex]++;
        }

        int maxBinCount = 0;
        for (int count : bins) {
            maxBinCount = Math.max(maxBinCount, count);
        }
        if (maxBinCount == 0) maxBinCount = 1;

        chartContainer.removeAllViews();

        int colorDark = Color.parseColor(colorDarkHex);
        int colorMedium = Color.parseColor(colorMedHex);
        int colorLight = Color.parseColor(colorLightHex);

        for (int i = 0; i < numBins; i++) {
            LinearLayout barContainer = new LinearLayout(context);
            barContainer.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
            barContainer.setLayoutParams(containerParams);

            float heightPercent = (float) bins[i] / maxBinCount;
            if (heightPercent < 0.08f) heightPercent = 0.08f;

            View spacer = new View(context);
            LinearLayout.LayoutParams spacerParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f - heightPercent);
            spacer.setLayoutParams(spacerParams);
            barContainer.addView(spacer);

            View bar = new View(context);
            LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, heightPercent);
            barParams.setMargins(4, 0, 4, 0); // Tighter margins to fit side-by-side perfectly
            bar.setLayoutParams(barParams);

            int barColor = colorLight;
            if (bins[i] == maxBinCount) {
                barColor = colorDark;
            } else if (bins[i] >= maxBinCount / 2.0f) {
                barColor = colorMedium;
            }

            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.RECTANGLE);
            gd.setColor(barColor);
            float radius = 10f;
            gd.setCornerRadii(new float[]{radius, radius, radius, radius, 0, 0, 0, 0});
            bar.setBackground(gd);

            barContainer.addView(bar);
            chartContainer.addView(barContainer);
        }
    }

    public void setCurrentPlaceIndex(int index) {
        this.currentPlaceIndex = index;
        listener.updateCurrentPlaceIndex(index);
        displayCurrentPlaceStats();
    }


    private PrecisionResult calculatePrecision(List<Integer> minutesFromMidnight) {
        if (minutesFromMidnight == null || minutesFromMidnight.isEmpty()) return null;

        int sum = 0;
        for (int min : minutesFromMidnight) {
            sum += min;
        }
        int mean = sum / minutesFromMidnight.size();

        double varianceSum = 0;
        for (int min : minutesFromMidnight) {
            varianceSum += Math.pow(min - mean, 2);
        }
        int stdDev = (int) Math.sqrt(varianceSum / minutesFromMidnight.size());

        return new PrecisionResult(mean, stdDev);
    }

    private String formatMinutesToTime(int totalMinutes) {
        int hours = totalMinutes / 60;
        int mins = totalMinutes % 60;
        String ampm = hours >= 12 ? "PM" : "AM";
        hours = hours % 12;
        if (hours == 0) hours = 12;
        return String.format(Locale.getDefault(), "%d:%02d %s", hours, mins, ampm);
    }
}
