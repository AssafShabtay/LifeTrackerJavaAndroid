package com.example.myapplication.mainScreen.dashboardScreen;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.LifeTrackerApp;
import com.example.myapplication.R;
import com.example.myapplication.helpers.UsageStatsHelper;
import com.example.myapplication.mainScreen.dashboardScreen.adapter.InsightCardAdapter;
import com.example.myapplication.mainScreen.dashboardScreen.adapter.LocationUsageAdapter;
import com.example.myapplication.mainScreen.dashboardScreen.adapter.TimeDrainAdapter;
import com.example.myapplication.mainScreen.dashboardScreen.model.DashboardData;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class DashboardFragment extends Fragment {

    private DashboardDataManager dataManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private Calendar currentDateCalendar = Calendar.getInstance();

    private TextView tvCurrentDate;
    private ImageView btnPrevDay;
    private ImageView btnNextDay;

    private MaterialCardView cardPermissionBanner;
    private MaterialButton btnGrantUsageAccess;

    private TextView tvBalanceHeadline;
    private LinearProgressIndicator progressBalance;
    private TextView tvActiveRealWorldTime;
    private TextView tvDigitalScreenTime;

    private RecyclerView rvInsights;
    private InsightCardAdapter insightCardAdapter;

    private RecyclerView rvLocationUsage;
    private LocationUsageAdapter locationUsageAdapter;
    private TextView tvNoPlacesData;

    private RecyclerView rvTimeDrains;
    private TimeDrainAdapter timeDrainAdapter;
    private TextView tvNoTimeDrains;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_dashboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        LifeTrackerApp app = (LifeTrackerApp) requireActivity().getApplication();
        dataManager = new DashboardDataManager(requireContext(), app);

        initViews(view);
        setupAdapters();
        setupListeners();

        updateDateLabel();
        loadData();
    }

    @Override
    public void onResume() {
        super.onResume();
        checkPermissionAndRefresh();
    }

    private void initViews(View view) {
        tvCurrentDate = view.findViewById(R.id.tvCurrentDate);
        btnPrevDay = view.findViewById(R.id.btnPrevDay);
        btnNextDay = view.findViewById(R.id.btnNextDay);

        cardPermissionBanner = view.findViewById(R.id.cardPermissionBanner);
        btnGrantUsageAccess = view.findViewById(R.id.btnGrantUsageAccess);

        tvBalanceHeadline = view.findViewById(R.id.tvBalanceHeadline);
        progressBalance = view.findViewById(R.id.progressBalance);
        tvActiveRealWorldTime = view.findViewById(R.id.tvActiveRealWorldTime);
        tvDigitalScreenTime = view.findViewById(R.id.tvDigitalScreenTime);

        rvInsights = view.findViewById(R.id.rvInsights);
        rvLocationUsage = view.findViewById(R.id.rvLocationUsage);
        tvNoPlacesData = view.findViewById(R.id.tvNoPlacesData);

        rvTimeDrains = view.findViewById(R.id.rvTimeDrains);
        tvNoTimeDrains = view.findViewById(R.id.tvNoTimeDrains);
    }

    private void setupAdapters() {
        // Horizontal Insights Carousel
        insightCardAdapter = new InsightCardAdapter();
        rvInsights.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        rvInsights.setAdapter(insightCardAdapter);

        // Vertical Location Breakdown
        locationUsageAdapter = new LocationUsageAdapter();
        rvLocationUsage.setLayoutManager(new LinearLayoutManager(getContext()));
        rvLocationUsage.setAdapter(locationUsageAdapter);

        // Vertical Time Drain Indicators
        timeDrainAdapter = new TimeDrainAdapter();
        rvTimeDrains.setLayoutManager(new LinearLayoutManager(getContext()));
        rvTimeDrains.setAdapter(timeDrainAdapter);
    }

    private void setupListeners() {
        btnPrevDay.setOnClickListener(v -> {
            currentDateCalendar.add(Calendar.DAY_OF_YEAR, -1);
            updateDateLabel();
            loadData();
        });

        btnNextDay.setOnClickListener(v -> {
            currentDateCalendar.add(Calendar.DAY_OF_YEAR, 1);
            updateDateLabel();
            loadData();
        });

        btnGrantUsageAccess.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            startActivity(intent);
        });
    }

    private void checkPermissionAndRefresh() {
        boolean hasPerm = UsageStatsHelper.hasUsageStatsPermission(requireContext());
        if (hasPerm) {
            cardPermissionBanner.setVisibility(View.GONE);
        } else {
            cardPermissionBanner.setVisibility(View.VISIBLE);
        }
        loadData();
    }

    private void updateDateLabel() {
        Calendar today = Calendar.getInstance();
        if (isSameDay(currentDateCalendar, today)) {
            tvCurrentDate.setText("Today");
            btnNextDay.setEnabled(false);
            btnNextDay.setAlpha(0.4f);
        } else {
            SimpleDateFormat sdf = new SimpleDateFormat("EEE, MMM d", Locale.getDefault());
            tvCurrentDate.setText(sdf.format(currentDateCalendar.getTime()));
            btnNextDay.setEnabled(true);
            btnNextDay.setAlpha(1.0f);
        }
    }

    private boolean isSameDay(Calendar cal1, Calendar cal2) {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
    }

    private void loadData() {
        Date targetDate = currentDateCalendar.getTime();
        dataManager.loadDashboardDataForDate(targetDate, data -> {
            mainHandler.post(() -> bindData(data));
        });
    }

    private void bindData(DashboardData data) {
        if (!isAdded()) return;

        // 1. Hero Balance Graphic
        long activeMs = data.getActiveRealWorldTimeMs();
        long screenMs = data.getDigitalScreenTimeMs();
        long totalMs = activeMs + screenMs;

        tvActiveRealWorldTime.setText(formatDuration(activeMs));
        tvDigitalScreenTime.setText(formatDuration(screenMs));

        if (totalMs > 0) {
            int activePct = (int) Math.round(((double) activeMs / (double) totalMs) * 100);
            progressBalance.setProgress(activePct);
            if (activePct >= 60) {
                tvBalanceHeadline.setText("Real-World Dominant (" + activePct + "%)");
            } else if (activePct <= 40) {
                tvBalanceHeadline.setText("High Screen Time (" + (100 - activePct) + "%)");
            } else {
                tvBalanceHeadline.setText("Balanced Day (" + activePct + "% Real-World)");
            }
        } else {
            progressBalance.setProgress(50);
            tvBalanceHeadline.setText("No Activity Recorded Yet");
        }

        // 2. Contextual Insights
        insightCardAdapter.setItems(data.getInsights());

        // 3. Location Usage Breakdown
        if (data.getLocationUsageItems().isEmpty()) {
            tvNoPlacesData.setVisibility(View.VISIBLE);
            rvLocationUsage.setVisibility(View.GONE);
        } else {
            tvNoPlacesData.setVisibility(View.GONE);
            rvLocationUsage.setVisibility(View.VISIBLE);
            locationUsageAdapter.setItems(data.getLocationUsageItems());
        }

        // 4. Time Drain Indicators
        if (data.getTimeDrainItems().isEmpty()) {
            tvNoTimeDrains.setVisibility(View.VISIBLE);
            rvTimeDrains.setVisibility(View.GONE);
        } else {
            tvNoTimeDrains.setVisibility(View.GONE);
            rvTimeDrains.setVisibility(View.VISIBLE);
            timeDrainAdapter.setItems(data.getTimeDrainItems());
        }
    }

    private String formatDuration(long millis) {
        long minutes = millis / (60 * 1000);
        long hours = minutes / 60;
        long remainingMinutes = minutes % 60;
        if (hours > 0) {
            return hours + "h " + remainingMinutes + "m";
        }
        return remainingMinutes + "m";
    }
}
