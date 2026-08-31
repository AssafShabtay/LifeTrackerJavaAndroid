package com.example.myapplication.mainScreen.statisticsScreen;

import static com.example.myapplication.locationTracking.ActivityTrackingUtils.calculateRadiusBox;
import static com.example.myapplication.locationTracking.ActivityTrackingUtils.getCoordinatesFromAddress;

import android.content.Context;
import android.content.SharedPreferences;
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

public class StatisticsFragment extends Fragment implements HomeAddressPickerBottomSheet.OnHomeAddressSelectedListener, WorkAddressPickerBottomSheet.OnWorkAddressSelectedListener, HomeStatisticsManager.HomeStatisticsListener, WorkStatisticsManager.WorkStatisticsListener, PunctualityStatisticsManager.PunctualityStatisticsListener, LlmInsightsManager.LlmInsightsListener {

    private static final String TAG = "StatisticsFragment";


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
    private SharedPreferences sharedPreferences;
    private SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener;
    @Override
    public void onHomeAddressSelected(String address) {
        homeStatisticsManager.onHomeAddressSelected(address);
    }

    @Override
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

        Button btnOpenHomeAddressPicker = view.findViewById(R.id.btnOpenHomeAddressPicker);
        btnChangeHomeAddress = view.findViewById(R.id.btnChangeHomeAddress);


        Button btnOpenWorkAddressPicker = view.findViewById(R.id.btnOpenWorkAddressPicker);
        btnChangeWorkAddress = view.findViewById(R.id.btnChangeWorkAddress);

        app = (LifeTrackerApp) requireActivity().getApplication();
        homeStatisticsManager = new HomeStatisticsManager(requireContext(), app, mainHandler, this, view);
        workStatisticsManager = new WorkStatisticsManager(requireContext(), app, mainHandler, this, view);
        punctualityStatisticsManager = new PunctualityStatisticsManager(requireContext(), app, mainHandler, this, view);
        llmInsightsManager = new LlmInsightsManager(requireContext(), app, mainHandler, this, view);

        btnPrevPlace = view.findViewById(R.id.btn_prev_place);
        btnNextPlace = view.findViewById(R.id.btn_next_place);

        btnPrevPlace.setOnClickListener(v -> {
            if (currentPlaceIndex > 0) {
                punctualityStatisticsManager.setCurrentPlaceIndex(currentPlaceIndex - 1);
            }
        });

        btnNextPlace.setOnClickListener(v -> {
            if (currentPlaceIndex < topPlaceIds.size() - 1) {
                punctualityStatisticsManager.setCurrentPlaceIndex(currentPlaceIndex + 1);
            }
        });
        btnOpenHomeAddressPicker.setOnClickListener(v -> {
            HomeAddressPickerBottomSheet bottomSheet = HomeAddressPickerBottomSheet.newInstance(this, placeDuration);
            bottomSheet.show(getChildFragmentManager(), bottomSheet.getTag());
        });


        btnOpenWorkAddressPicker.setOnClickListener(v -> {
            WorkAddressPickerBottomSheet bottomSheet = WorkAddressPickerBottomSheet.newInstance(this, placeDuration);
            bottomSheet.show(getChildFragmentManager(), bottomSheet.getTag());
        });

        btnChangeHomeAddress.setOnClickListener(v -> {
            HomeAddressPickerBottomSheet bottomSheet = HomeAddressPickerBottomSheet.newInstance(this, placeDuration);
            bottomSheet.show(getChildFragmentManager(), bottomSheet.getTag());
        });

        btnChangeWorkAddress.setOnClickListener(v -> {
            WorkAddressPickerBottomSheet bottomSheet = WorkAddressPickerBottomSheet.newInstance(this, placeDuration);
            bottomSheet.show(getChildFragmentManager(), bottomSheet.getTag());
        });

        sharedPreferences = requireContext().getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
        preferenceChangeListener = (prefs, key) -> {
            if ("week_start_day".equals(key)) {
                loadStatistics();
            }
        };
        sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener);
        loadStatistics();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadStatistics();
    }
    @Override
    public void onDestroyView() {super.onDestroyView();
        if (sharedPreferences != null && preferenceChangeListener != null) {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener);
        }
    }
    private void loadStatistics() {
        if (!isAdded()) return;

        homeStatisticsManager.loadCabinFeverIndex();
        punctualityStatisticsManager.loadArrivalDepartureStats();
        llmInsightsManager.loadLlmInsights();
        workStatisticsManager.loadWorkStatistics();

    }

    @Override
    public boolean isFragmentAdded() {
        return isAdded();
    }

    @Override
    public View getFragmentView() {
        return requireView();
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