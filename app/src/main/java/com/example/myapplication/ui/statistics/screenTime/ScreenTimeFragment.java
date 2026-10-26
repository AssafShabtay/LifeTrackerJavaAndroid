package com.example.myapplication.ui.statistics.screenTime;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.myapplication.R;

public class ScreenTimeFragment extends Fragment {

    private ScreenTimeViewModel viewModel;
    private ScreenTimeStatsManager screenTimeStatsManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_screen_time, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(ScreenTimeViewModel.class);
        screenTimeStatsManager = new ScreenTimeStatsManager(requireContext(), view, viewModel);

        setupObservers();
    }

    private void setupObservers() {
        viewModel.getPermissionGranted().observe(getViewLifecycleOwner(), hasPermission -> {
            if (screenTimeStatsManager != null) {
                screenTimeStatsManager.renderPermissionState(hasPermission);
            }
        });

        viewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
            if (screenTimeStatsManager != null) {
                screenTimeStatsManager.renderLoadingState(isLoading);
            }
        });

        viewModel.getLoadError().observe(getViewLifecycleOwner(), isError -> {
            if (screenTimeStatsManager != null) {
                screenTimeStatsManager.renderLoadError(isError);
            }
        });

        viewModel.getSelectedPeriod().observe(getViewLifecycleOwner(), period -> {
            if (screenTimeStatsManager != null) {
                screenTimeStatsManager.renderPeriodSelected(period);
            }
        });

        viewModel.getDailyUsageItem().observe(getViewLifecycleOwner(), item -> {
            if (screenTimeStatsManager != null && item != null) {
                int period = viewModel.getSelectedPeriod().getValue() != null ? viewModel.getSelectedPeriod().getValue() : ScreenTimeViewModel.PERIOD_DAILY;
                if (period == ScreenTimeViewModel.PERIOD_DAILY) {
                    int dayIndex = viewModel.getCurrentDayIndex().getValue() != null ? viewModel.getCurrentDayIndex().getValue() : 0;
                    screenTimeStatsManager.renderDailyUsage(item, viewModel.getCurrentPackageUsage().getValue(), dayIndex);
                }
            }
        });

        viewModel.getWeeklyUsageItem().observe(getViewLifecycleOwner(), item -> {
            if (screenTimeStatsManager != null && item != null) {
                int period = viewModel.getSelectedPeriod().getValue() != null ? viewModel.getSelectedPeriod().getValue() : ScreenTimeViewModel.PERIOD_DAILY;
                if (period == ScreenTimeViewModel.PERIOD_WEEKLY) {
                    int weekIndex = viewModel.getCurrentWeekIndex().getValue() != null ? viewModel.getCurrentWeekIndex().getValue() : 0;
                    screenTimeStatsManager.renderWeeklyUsage(item, viewModel.getCurrentPackageUsage().getValue(), weekIndex);
                }
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (viewModel != null) {
            viewModel.loadScreenTimeStats();
        }
    }

    @Override
    public void onDestroyView() {
        if (screenTimeStatsManager != null) {
            screenTimeStatsManager.destroy();
            screenTimeStatsManager = null;
        }
        super.onDestroyView();
    }
}
