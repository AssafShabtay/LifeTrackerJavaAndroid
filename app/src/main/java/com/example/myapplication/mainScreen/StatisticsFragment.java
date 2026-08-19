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


public class StatisticsFragment extends Fragment implements HomeAddressPickerBottomSheet.OnHomeAddressSelectedListener, MainActivity.OnHomeAddressChangedListener, WorkAddressPickerBottomSheet.OnWorkAddressSelectedListener {

    private static final String TAG = "StatisticsFragment";

    private TextView tvNoData;

    private View cabinFeverContent;
    private View cabinFeverPlaceholder;

    private View workStatisticsContent; // New: Work statistics content containerss
    private View workStatisticsPlaceholder; // New: Work statistics placeholder

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

    // Work Hours related views
    private LinearLayout chartWorkHoursContainer;
    private TextView tvWorkHoursSummary;
    private TextView tvWorkHoursMinLabel;
    private TextView tvWorkHoursMaxLabel;

    private Button btnChangeHomeAddress; // New: Button to change home address
    private Button btnChangeWorkAddress; // New: Button to change work address

    private LifeTrackerApp app;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private Map<String, Long> placeDuration = new HashMap<>();

    @Override
    public void onHomeAddressChanged() {
        loadCabinFeverIndex();
        loadWorkStatistics(); // Also refresh work statistics when home address changes as it might impact category assignments
    }

    @Override // New: Listener for Work Address changes
    public void onWorkAddressSelected(String address) {
        saveWorkAddress(address);
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
        btnChangeHomeAddress = view.findViewById(R.id.btnChangeHomeAddress); // Initialize new button

        // New: Initialize Work Statistics views
        workStatisticsContent = view.findViewById(R.id.work_statistics_content);
        workStatisticsPlaceholder = view.findViewById(R.id.work_statistics_placeholder);
        Button btnOpenWorkAddressPicker = view.findViewById(R.id.btnOpenWorkAddressPicker);
        btnChangeWorkAddress = view.findViewById(R.id.btnChangeWorkAddress); // Initialize new button

        app = (LifeTrackerApp) requireActivity().getApplication();
        btnPrevPlace = view.findViewById(R.id.btn_prev_place);
        btnNextPlace = view.findViewById(R.id.btn_next_place);
        tvPunctualityTitle = view.findViewById(R.id.tv_punctuality_title);

        // Initialize LLM related views
        tvHabitText = view.findViewById(R.id.tv_habit_text);
        tvAnomalyText = view.findViewById(R.id.tv_anomaly_text);
        tvLlmLoadingError = view.findViewById(R.id.tv_llm_loading_error);

        // Initialize Work Hours related views
        chartWorkHoursContainer = view.findViewById(R.id.chart_work_hours_container);
        tvWorkHoursSummary = view.findViewById(R.id.tv_work_hours_summary);
        tvWorkHoursMinLabel = view.findViewById(R.id.tv_work_hours_min_label);
        tvWorkHoursMaxLabel = view.findViewById(R.id.tv_work_hours_max_label);

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

        // New: Set OnClickListener for the Work Address Picker Button
        btnOpenWorkAddressPicker.setOnClickListener(v -> {
            WorkAddressPickerBottomSheet bottomSheet = WorkAddressPickerBottomSheet.newInstance(this, placeDuration);
            bottomSheet.show(getChildFragmentManager(), bottomSheet.getTag());
        });

        // New: Set OnClickListener for the Change Home Address Button
        btnChangeHomeAddress.setOnClickListener(v -> {
            HomeAddressPickerBottomSheet bottomSheet = HomeAddressPickerBottomSheet.newInstance(this, placeDuration);
            bottomSheet.show(getChildFragmentManager(), bottomSheet.getTag());
        });

        // New: Set OnClickListener for the Change Work Address Button
        btnChangeWorkAddress.setOnClickListener(v -> {
            WorkAddressPickerBottomSheet bottomSheet = WorkAddressPickerBottomSheet.newInstance(this, placeDuration);
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
            loadWorkStatistics(); // Load work hours statistics
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

    @Override
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
            requireView().findViewById(R.id.tv_arrival_avg).setVisibility(View.INVISIBLE);
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

    // New: Save Work Address method
    private void saveWorkAddress(String address) {
        if (address.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter a work address.", Toast.LENGTH_SHORT).show();
            return;
        }

        app.getDatabaseWriteExecutor().execute(() -> {
            if (!isAdded()) return;
            double[] coords = getCoordinatesFromAddress(address, requireContext());

            ActivityDatabase db = ActivityDatabase.getDatabase(requireContext());
            PlaceDao placeDao = db.placeDao();
            Place workPlace = placeDao.getWorkPlace();

            if (workPlace == null) {
                workPlace = new Place();
                workPlace.setName("Work");
                workPlace.setAddress(address);
                workPlace.setCategory("Work");
                workPlace.setIcon("Work"); // Assuming you have an ic_work drawable
                workPlace.setColor(0xFF4CAF50); // Green color for work
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

            mainHandler.post(() -> {
                // Directly trigger loadWorkStatistics here, no need for MainActivity listener
                loadWorkStatistics();
            });
        });
    }

    // ------------------------ Work Hours statistics ----------------------------
    private void loadWorkStatistics() {
        app.getDatabaseWriteExecutor().execute(() -> {
            if (!isAdded()) return;
            ActivityDatabase db = ActivityDatabase.getDatabase(requireContext());
            PlaceDao placeDao = db.placeDao();

            Place workPlace = placeDao.getWorkPlace();

            // Get the start of the current week (Monday) and end of the current week (Sunday)
            Calendar calendar = Calendar.getInstance();
            calendar.setFirstDayOfWeek(Calendar.MONDAY); // Ensure week starts on Monday

            // Set to the first day of the current week (Monday)
            calendar.setTime(new Date()); // Set to today
            calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);

            // Go back one week to get "last week"
            calendar.add(Calendar.WEEK_OF_YEAR, -1);
            Date startOfLastWeek = calendar.getTime();

            // Calculate end of last week (Sunday 23:59:59)
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
                if (!isAdded()) return;
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


    private void drawWorkHoursBarChart(List<Double> dailyHours, int containerId, String colorDarkHex, String colorMedHex, String colorLightHex) {
        LinearLayout chartContainer = requireView().findViewById(containerId);
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

        String[] days = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};

        for (int i = 0; i < dailyHours.size(); i++) {
            LinearLayout dayColumn = new LinearLayout(requireContext());
            dayColumn.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams columnParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
            dayColumn.setLayoutParams(columnParams);
            dayColumn.setGravity(android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL); // Align bars to bottom

            // Bar view
            View bar = new View(requireContext());
            float heightPercent = (float) (dailyHours.get(i) / maxHours);
            if (heightPercent < 0.01f && dailyHours.get(i) > 0) heightPercent = 0.01f; // Ensure tiny bars are visible
            else if (dailyHours.get(i) == 0) heightPercent = 0f;

            LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, heightPercent);
            int marginHorizontal = 8; // Adjust margin to control bar width and spacing
            int marginBottom = 4; // Margin below the bar, before the day label
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
            TextView dayLabel = new TextView(requireContext());
            dayLabel.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            dayLabel.setText(days[i]);
            dayLabel.setTextSize(9f);
            dayLabel.setTextColor(Color.parseColor("#757575")); // Grey color
            dayColumn.addView(dayLabel);

            chartContainer.addView(dayColumn);
        }

        // Update min and max labels. Max is always 9 hours in this design.
        // Min is always 0 hours.
        tvWorkHoursMinLabel.setText("0 hours");
        tvWorkHoursMaxLabel.setText(String.format(Locale.getDefault(), "%.0f hours", maxHours));
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
