package com.example.myapplication.mainScreen.statisticsScreen;

import static com.example.myapplication.locationTracking.ActivityTrackingUtils.calculateRadiusBox;
import static com.example.myapplication.locationTracking.ActivityTrackingUtils.getCoordinatesFromAddress;

import android.content.Context;
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

import com.example.myapplication.mainScreen.statisticsScreen.PrecisionResult;
import com.example.myapplication.mainScreen.statisticsScreen.HomeStatisticsManager;
import com.example.myapplication.mainScreen.statisticsScreen.WorkStatisticsManager;
import com.example.myapplication.mainScreen.statisticsScreen.PunctualityStatisticsManager;
import com.example.myapplication.mainScreen.statisticsScreen.LlmInsightsManager; // New import

public class StatisticsFragment extends Fragment implements HomeAddressPickerBottomSheet.OnHomeAddressSelectedListener, MainActivity.OnHomeAddressChangedListener, WorkAddressPickerBottomSheet.OnWorkAddressSelectedListener, HomeStatisticsManager.HomeStatisticsListener, WorkStatisticsManager.WorkStatisticsListener, PunctualityStatisticsManager.PunctualityStatisticsListener, LlmInsightsManager.LlmInsightsListener {

    private static final String TAG = "StatisticsFragment";

    private TextView tvNoData;

    private List<String> topPlaceIds = new ArrayList<>();
    private Map<String, String> placeNamesMap = new HashMap<>();
    private Map<String, List<Integer>> arrivalTimesMap = new HashMap<>();
    private Map<String, List<Integer>> departureTimesMap = new HashMap<>();
    private int currentPlaceIndex = 0;

    private TextView btnPrevPlace;
    private TextView btnNextPlace;


    private Button btnChangeHomeAddress;
    private Button btnChangeWorkAddress;

    private LifeTrackerApp app;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private Map<String, Long> placeDuration = new HashMap<>();

    private HomeStatisticsManager homeStatisticsManager;
    private WorkStatisticsManager workStatisticsManager;
    private PunctualityStatisticsManager punctualityStatisticsManager;
    private LlmInsightsManager llmInsightsManager;

    @Override
    public void onHomeAddressChanged() {
        homeStatisticsManager.onHomeAddressChanged();
        // The call to loadWorkStatistics() is moved to onHomeAddressChangedCallback
    }

    @Override // New: Listener for Work Address changes
    public void onWorkAddressSelected(String address) {
        workStatisticsManager.onWorkAddressSelected(address);
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

        Button btnOpenHomeAddressPicker = view.findViewById(R.id.btnOpenHomeAddressPicker);
        btnChangeHomeAddress = view.findViewById(R.id.btnChangeHomeAddress); // Initialize new button

        // New: Initialize Work Statistics views

        Button btnOpenWorkAddressPicker = view.findViewById(R.id.btnOpenWorkAddressPicker);
        btnChangeWorkAddress = view.findViewById(R.id.btnChangeWorkAddress); // Initialize new button

        app = (LifeTrackerApp) requireActivity().getApplication();
        homeStatisticsManager = new HomeStatisticsManager(requireContext(), app, mainHandler, this, view);
        workStatisticsManager = new WorkStatisticsManager(requireContext(), app, mainHandler, this, view);
        punctualityStatisticsManager = new PunctualityStatisticsManager(requireContext(), app, mainHandler, this, view);
        llmInsightsManager = new LlmInsightsManager(requireContext(), app, mainHandler, this, view); // Instantiate manager

        btnPrevPlace = view.findViewById(R.id.btn_prev_place);
        btnNextPlace = view.findViewById(R.id.btn_next_place);





        btnPrevPlace.setOnClickListener(v -> {
            if (currentPlaceIndex > 0) {
                punctualityStatisticsManager.setCurrentPlaceIndex(currentPlaceIndex - 1); // Delegate
            }
        });

        btnNextPlace.setOnClickListener(v -> {
            if (currentPlaceIndex < topPlaceIds.size() - 1) {
                punctualityStatisticsManager.setCurrentPlaceIndex(currentPlaceIndex + 1); // Delegate
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
            homeStatisticsManager.loadCabinFeverIndex(); // Delegate to manager
            punctualityStatisticsManager.loadArrivalDepartureStats(); // Delegate to manager
            llmInsightsManager.loadLlmInsights(); // Delegate to manager
            workStatisticsManager.loadWorkStatistics(); // Delegate to manager
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
        homeStatisticsManager.onAddressSelected(address);
    }

    // Removed saveHomeAddress, loadCabinFeverIndex, updateCabinFeverUi methods

    @Override
    public boolean isFragmentAdded() {
        return isAdded();
    }

    @Override
    public Context getFragmentContext() {
        return requireContext();
    }

    @Override
    public View getFragmentView() {
        return requireView();
    }

    // PunctualityStatisticsManager.PunctualityStatisticsListener implementations
    @Override
    public Map<String, Long> getPlaceDurationMap() {
        return placeDuration;
    }

    @Override
    public void updateTopPlaceIds(List<String> newTopPlaceIds) {
        this.topPlaceIds = newTopPlaceIds;
    }

    @Override
    public void updatePlaceNamesMap(Map<String, String> newPlaceNamesMap) {
        this.placeNamesMap = newPlaceNamesMap;
    }

    @Override
    public void updateArrivalTimesMap(Map<String, List<Integer>> newArrivalTimesMap) {
        this.arrivalTimesMap = newArrivalTimesMap;
    }

    @Override
    public void updateDepartureTimesMap(Map<String, List<Integer>> newDepartureTimesMap) {
        this.departureTimesMap = newDepartureTimesMap;
    }

    @Override
    public void updateCurrentPlaceIndex(int newCurrentPlaceIndex) {
        this.currentPlaceIndex = newCurrentPlaceIndex;
    }

    @Override
    public List<String> getTopPlaceIds() {
        return topPlaceIds;
    }

    @Override
    public Map<String, String> getPlaceNamesMap() {
        return placeNamesMap;
    }

    @Override
    public Map<String, List<Integer>> getArrivalTimesMap() {
        return arrivalTimesMap;
    }

    @Override
    public Map<String, List<Integer>> getDepartureTimesMap() {
        return departureTimesMap;
    }

    @Override
    public int getCurrentPlaceIndex() {
        return currentPlaceIndex;
    }
}