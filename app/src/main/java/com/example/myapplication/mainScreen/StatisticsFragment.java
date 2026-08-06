package com.example.myapplication.mainScreen;

import static com.example.myapplication.locationTracking.ActivityTrackingUtils.calculateRadiusBox;
import static com.example.myapplication.locationTracking.ActivityTrackingUtils.getCoordinatesFromAddress;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.myapplication.LifeTrackerApp;
import com.example.myapplication.MainActivity;
import com.example.myapplication.R;
import com.example.myapplication.database.ActivityDatabase;
import com.example.myapplication.database.Place;
import com.example.myapplication.database.PlaceDao;
import com.example.myapplication.database.StillLocation;
import com.example.myapplication.llm.LlmApiClient;
import com.example.myapplication.llm.LlmResponse;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;


public class StatisticsFragment extends Fragment implements HomeAddressPickerBottomSheet.OnHomeAddressSelectedListener, MainActivity.OnHomeAddressChangedListener {

    private static final String TAG = "StatisticsFragment";

    private TextView tvNoData;

    private View cabinFeverContent;
    private View cabinFeverPlaceholder;

    private List<String> topPlaceIds = new ArrayList<>();
    private Map<String, String> placeNamesMap = new HashMap<>();
    private Map<String, List<Integer>> arrivalTimesMap = new HashMap<>();
    private Map<String, List<Integer>> departureTimesMap = new HashMap<>();
    private int currentPlaceIndex = 0;

    private TextView btnPrevPlace;
    private TextView btnNextPlace;
    private TextView tvPunctualityTitle;

    // LLM related views
    private TextView tvHabitText;
    private TextView tvAnomalyText;
    private TextView tvLlmLoadingError;

    private LifeTrackerApp app;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private Map<String, Long> placeDuration = new HashMap<>();

    @Override
    public void onHomeAddressChanged() {
        loadCabinFeverIndex();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_statistics, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setOnHomeAddressChangedListener(this);
        }
        tvNoData = view.findViewById(R.id.tvNoData);
        cabinFeverContent = view.findViewById(R.id.cabin_fever_content);
        cabinFeverPlaceholder = view.findViewById(R.id.cabin_fever_placeholder);
        Button btnOpenHomeAddressPicker = view.findViewById(R.id.btnOpenHomeAddressPicker);
        app = (LifeTrackerApp) requireActivity().getApplication();
        btnPrevPlace = view.findViewById(R.id.btn_prev_place);
        btnNextPlace = view.findViewById(R.id.btn_next_place);
        tvPunctualityTitle = view.findViewById(R.id.tv_punctuality_title);

        // Initialize LLM related views
        tvHabitText = view.findViewById(R.id.tv_habit_text);
        tvAnomalyText = view.findViewById(R.id.tv_anomaly_text);
        tvLlmLoadingError = view.findViewById(R.id.tv_llm_loading_error);

        btnPrevPlace.setOnClickListener(v -> {
            if (currentPlaceIndex > 0) {
                currentPlaceIndex--;
                displayCurrentPlaceStats();
            }
        });

        btnNextPlace.setOnClickListener(v -> {
            if (currentPlaceIndex < topPlaceIds.size() - 1) {
                currentPlaceIndex++;
                displayCurrentPlaceStats();
            }
        });
        btnOpenHomeAddressPicker.setOnClickListener(v -> {
            HomeAddressPickerBottomSheet bottomSheet = HomeAddressPickerBottomSheet.newInstance(this, placeDuration);
            bottomSheet.show(getChildFragmentManager(), bottomSheet.getTag());
        });

        loadStatistics();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadStatistics();
    }

    private void loadStatistics() {
        app.getDatabaseWriteExecutor().execute(() -> {
            if (!isAdded()) return;

            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            Date start = cal.getTime();

            cal.set(Calendar.HOUR_OF_DAY, 23);
            cal.set(Calendar.MINUTE, 59);
            cal.set(Calendar.SECOND, 59);
            Date end = cal.getTime();

            ActivityDatabase db = ActivityDatabase.getDatabase(requireContext());
            // Only querying StillLocations since MovementActivities were unused in the UI
            List<StillLocation> stills = db.activityDao().getStillsFromRange(start, end);

            getPlaceDurations(stills);
            loadCabinFeverIndex();
            loadArrivalDepartureStats();
            loadLlmInsights(); // Call the new LLM insight loading method
        });
    }

    private void getPlaceDurations(List<StillLocation> stills) {
        if (stills.isEmpty()) {
            mainHandler.post(() -> {
                if (!isAdded()) return;
                tvNoData.setVisibility(View.VISIBLE);
                placeDuration.clear();
            });
            return;
        }

        Map<String, Long> placeDurations = new HashMap<>();

        for (StillLocation item : stills) {
            long durationMins = getDurationMins(item);
            if (durationMins <= 0) continue;
            String place = null;
            if (item.getPlaceName() != null && !item.getPlaceName().isEmpty()) place = item.getPlaceName();
            if(place != null) {
                placeDurations.merge(place, durationMins, Long::sum); // add duration according to the place
            }
        }

        placeDuration = placeDurations;
        mainHandler.post(() -> updateUi(placeDurations));
    }

    private static long getDurationMins(StillLocation item) {
        Date startTime = item.getStartTimeDate();
        Date endTime = item.getEndTimeDate();
        if (endTime == null) endTime = new Date();

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        Date todayStart = cal.getTime();

        Date start;
        if (startTime.before(todayStart)) start = todayStart;
        else start = startTime;

        long durationMs = endTime.getTime() - start.getTime();
        long durationMins = durationMs / 60000;
        return durationMins;
    }

    private void updateUi(Map<String, Long> placeDurations) {
        if (!isAdded()) return;

        tvNoData.setVisibility(placeDurations.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // ------------------------ Cabin fever statistics ----------------------------

    public void onAddressSelected(String address) {
        saveHomeAddress(address);
    }

    private void saveHomeAddress(String address) {
        if (address.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter a home address.", Toast.LENGTH_SHORT).show();
            return;
        }

        app.getDatabaseWriteExecutor().execute(() -> {
            if (!isAdded()) return;
            double[] coords = getCoordinatesFromAddress(address, requireContext());

            ActivityDatabase db = ActivityDatabase.getDatabase(requireContext());
            PlaceDao placeDao = db.placeDao();
            Place homePlace = placeDao.getHomePlace();

            if (homePlace == null) {
                homePlace = new Place();
                homePlace.setName("Home");
                homePlace.setAddress(address);
                homePlace.setCategory("Home");
                homePlace.setIcon("Home");
                homePlace.setColor(0xFF9E9E9E);
                if(coords != null){
                    homePlace.setLat(coords[0]);
                    homePlace.setLng(coords[1]);
                }
                placeDao.insertPlace(homePlace);

                if (coords != null) {
                    double[] bounds = calculateRadiusBox(coords[0], coords[1], 50.0);
                    db.activityDao().updateStillsWithinBounds(bounds[0], bounds[1], bounds[2], bounds[3], "Home");
                }
            } else {
                homePlace.setAddress(address);
                homePlace.setCategory("Home");
                homePlace.setName("Home");
                homePlace.setIcon("Home");
                placeDao.updatePlace(homePlace);
            }

            mainHandler.post(() -> {
                if (getActivity() instanceof MainActivity) {
                    MainActivity.OnHomeAddressChangedListener listener = ((MainActivity) getActivity()).getOnHomeAddressChangedListener();
                    if (listener != null) {
                        listener.onHomeAddressChanged();
                    }
                }
            });

        });
    }

    private void loadCabinFeverIndex() {
        long now = System.currentTimeMillis();
        long sevenDaysMs = 7L * 24 * 60 * 60 * 1000;
        long sevenDaysAgo = now - sevenDaysMs;

        app.getDatabaseWriteExecutor().execute(() -> {
            if (!isAdded()) return;
            ActivityDatabase db = ActivityDatabase.getDatabase(requireContext());
            Place homePlace = db.placeDao().getHomePlace();

            long timeAtHomeMs = 0;
            if (homePlace != null) {
                timeAtHomeMs = db.activityDao().getTimeAtHomeSince(sevenDaysAgo, now);
            }
            long totalTime = db.activityDao().getSumDurationOfAllActivitiesLastSevenDays(sevenDaysAgo, now);
            final long finalTimeAtHomeMs = timeAtHomeMs;

            mainHandler.post(() -> {
                if (!isAdded()) return;
                if (homePlace == null) {
                    if (cabinFeverContent != null) cabinFeverContent.setVisibility(View.GONE);
                    if (cabinFeverPlaceholder != null) cabinFeverPlaceholder.setVisibility(View.VISIBLE);
                } else {
                    if (cabinFeverContent != null) cabinFeverContent.setVisibility(View.VISIBLE);
                    if (cabinFeverPlaceholder != null) cabinFeverPlaceholder.setVisibility(View.GONE);

                    int percentage = (int) (((float) finalTimeAtHomeMs / totalTime) * 100);
                    if (percentage > 100) percentage = 100;
                    updateCabinFeverUi(percentage);
                }
            });
        });
    }

    private void updateCabinFeverUi(int percentage) {
        if (getView() == null) return;

        TextView scoreText = getView().findViewById(R.id.tv_homebody_score);
        ProgressBar progressBar = getView().findViewById(R.id.progress_cabin_fever);
        TextView messageText = getView().findViewById(R.id.tv_cabin_fever_message);

        if (scoreText != null) scoreText.setText(percentage + "% of your week spent at Home");
        if (progressBar != null) progressBar.setProgress(percentage);

        if (messageText != null) {
            if (percentage > 85) {
                messageText.setVisibility(View.VISIBLE);
                messageText.setText("Warning: High Cabin Fever detected! 🚨 Go take a walk, the neighborhood misses you.");
            } else {
                messageText.setVisibility(View.GONE);
            }
        }
    }
    private void loadArrivalDepartureStats() {
        long now = System.currentTimeMillis();
        long thirtyDaysMs = 30L * 24 * 60 * 60 * 1000;
        long thirtyDaysAgo = now - thirtyDaysMs;

        app.getDatabaseWriteExecutor().execute(() -> {
            if (!isAdded()) return;

            ActivityDatabase db = ActivityDatabase.getDatabase(requireContext());
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

            mainHandler.post(this::displayCurrentPlaceStats);
        });
    }

    private void displayCurrentPlaceStats() {
        if (!isAdded()) return;

        if (topPlaceIds.isEmpty()) {
            tvPunctualityTitle.setText("No data");
            btnPrevPlace.setVisibility(View.INVISIBLE);
            btnNextPlace.setVisibility(View.INVISIBLE);

            // Clear graphs safely
            ((LinearLayout) requireView().findViewById(R.id.chart_arrival_container)).removeAllViews();
            ((LinearLayout) requireView().findViewById(R.id.chart_departure_container)).removeAllViews();
            return;
        }

        // Update button visibilities depending on the current page
        btnPrevPlace.setVisibility(currentPlaceIndex > 0 ? View.VISIBLE : View.INVISIBLE);
        btnNextPlace.setVisibility(currentPlaceIndex < topPlaceIds.size() - 1 ? View.VISIBLE : View.INVISIBLE);

        String currentPlaceId = topPlaceIds.get(currentPlaceIndex);
        String currentPlaceName = placeNamesMap.get(currentPlaceId);

        tvPunctualityTitle.setText("Punctuality: " + currentPlaceName);

        List<Integer> currentArrivals = arrivalTimesMap.get(currentPlaceId);
        List<Integer> currentDepartures = departureTimesMap.get(currentPlaceId);

        PrecisionResult arrivalStats = currentArrivals != null ? calculatePrecision(currentArrivals) : null;
        PrecisionResult departureStats = currentDepartures != null ? calculatePrecision(currentDepartures) : null;

        // Draw Arrival Graph
        if (arrivalStats != null) {
            drawHistogram(currentArrivals, arrivalStats.mean,
                    R.id.chart_arrival_container, R.id.tv_arrival_start,
                    R.id.tv_arrival_avg, R.id.tv_arrival_end,
                    "#5C5CD6", "#8A8ADF", "#E0E0F8");
        } else {
            ((LinearLayout) requireView().findViewById(R.id.chart_arrival_container)).removeAllViews();
            requireView().findViewById(R.id.tv_arrival_avg).setVisibility(View.INVISIBLE);
        }

        // Draw Departure Graph
        if (departureStats != null) {
            drawHistogram(currentDepartures, departureStats.mean,
                    R.id.chart_departure_container, R.id.tv_departure_start,
                    R.id.tv_departure_avg, R.id.tv_departure_end,
                    "#009688", "#4DB6AC", "#E0F2F1");
        } else {
            ((LinearLayout) requireView().findViewById(R.id.chart_departure_container)).removeAllViews();
            requireView().findViewById(R.id.tv_departure_avg).setVisibility(View.INVISIBLE);
        }
    }


    private void drawHistogram(List<Integer> times, int mean, int containerId, int startLabelId, int avgLabelId, int endLabelId, String colorDarkHex, String colorMedHex, String colorLightHex) {
        TextView tvLabelStart = requireView().findViewById(startLabelId);
        TextView tvLabelAvg = requireView().findViewById(avgLabelId);
        TextView tvLabelEnd = requireView().findViewById(endLabelId);
        LinearLayout chartContainer = requireView().findViewById(containerId);

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
            LinearLayout barContainer = new LinearLayout(requireContext());
            barContainer.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
            barContainer.setLayoutParams(containerParams);

            float heightPercent = (float) bins[i] / maxBinCount;
            if (heightPercent < 0.08f) heightPercent = 0.08f;

            View spacer = new View(requireContext());
            LinearLayout.LayoutParams spacerParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f - heightPercent);
            spacer.setLayoutParams(spacerParams);
            barContainer.addView(spacer);

            View bar = new View(requireContext());
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

    private void loadLlmInsights() {
        mainHandler.post(() -> {
            if (!isAdded()) return;
            tvLlmLoadingError.setVisibility(View.VISIBLE);
            tvLlmLoadingError.setText("Loading habits and anomalies...");
            tvHabitText.setText("");
            tvAnomalyText.setText("");
        });

        app.getDatabaseWriteExecutor().execute(() -> {
            if (!isAdded()) return;
            try {
                long now = System.currentTimeMillis();
                long fourteenDaysMs = 14L * 24 * 60 * 60 * 1000;
                long fourteenDaysAgo = now - fourteenDaysMs;

                ActivityDatabase db = ActivityDatabase.getDatabase(requireContext());
                List<StillLocation> recentStills = db.activityDao().getStillsFromRange(new Date(fourteenDaysAgo), new Date(now));

                // Sort by start time to create a chronological sequence
                recentStills.sort((s1, s2) -> s1.getStartTimeDate().compareTo(s2.getStartTimeDate()));

                final String timelineSequence = getTimelineSequenceString(recentStills);

                if (timelineSequence.isEmpty()) {
                    mainHandler.post(() -> {
                        if (!isAdded()) return;
                        tvLlmLoadingError.setVisibility(View.VISIBLE);
                        tvLlmLoadingError.setText("No location data available for the last 14 days to analyze habits.");
                        tvHabitText.setText("");
                        tvAnomalyText.setText("");
                    });
                    return;
                }

                // Call the LLM (placeholder)
                LlmResponse llmResponse = null;
                CompletableFuture<LlmResponse> llmResponseFuture = LlmApiClient.getHabitAndAnomaly(timelineSequence);
                llmResponse = llmResponseFuture.get();


                LlmResponse finalLlmResponse = llmResponse;
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    tvLlmLoadingError.setVisibility(View.GONE);
                    if (finalLlmResponse != null) {
                        tvHabitText.setText("Habit: " + finalLlmResponse.getHabit());
                        tvAnomalyText.setText("Anomaly: " + finalLlmResponse.getAnomaly());
                    } else {
                        tvLlmLoadingError.setVisibility(View.VISIBLE);
                        tvLlmLoadingError.setText("Failed to load insights from LLM. Please try again later.");
                    }
                });

            } catch (Exception e) {
                // Log the exception for debugging
                Log.e(TAG, "Error loading LLM insights", e);
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    tvLlmLoadingError.setVisibility(View.VISIBLE);
                    tvLlmLoadingError.setText("Error loading LLM insights: " + e.getMessage());
                    tvHabitText.setText("");
                    tvAnomalyText.setText("");
                });
            }
        });
    }

    @NonNull
    private static String getTimelineSequenceString(List<StillLocation> recentStills) {
        StringBuilder timelineSequenceBuilder = new StringBuilder();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

        for (StillLocation still : recentStills) {
            String placeName = still.getPlaceName();
            Date startTime = still.getStartTimeDate();
            Date endTime = still.getEndTimeDate();

            if (placeName != null && !placeName.isEmpty() && startTime != null) {
                if (timelineSequenceBuilder.length() > 0) {
                    timelineSequenceBuilder.append(" -> ");
                }
                timelineSequenceBuilder.append(placeName);
                timelineSequenceBuilder.append(" (").append(sdf.format(startTime));
                if (endTime != null) {
                    timelineSequenceBuilder.append(" - ").append(sdf.format(endTime));
                }
                timelineSequenceBuilder.append(")");
            }
        }

        final String timelineSequence = timelineSequenceBuilder.toString();
        return timelineSequence;
    }

    private String formatMinutesToTime(int totalMinutes) {
        int hours = totalMinutes / 60;
        int mins = totalMinutes % 60;
        String ampm = hours >= 12 ? "PM" : "AM";
        hours = hours % 12;
        if (hours == 0) hours = 12;
        return String.format(Locale.getDefault(), "%d:%02d %s", hours, mins, ampm);
    }

    // Helper class to store standard deviation results
    private static class PrecisionResult {
        int mean;
        int stdDev;

        PrecisionResult(int mean, int stdDev) {
            this.mean = mean;
            this.stdDev = stdDev;
        }
    }
}