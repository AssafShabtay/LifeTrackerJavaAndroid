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
import com.example.myapplication.ui.statistics.askTimeline.AskTimelineBottomSheetFragment;

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

        View askTimelineButton = view.findViewById(R.id.ask_timeline_button);
        if (askTimelineButton != null) {
            askTimelineButton.setOnClickListener(v -> {
                AskTimelineBottomSheetFragment bottomSheet =
                        AskTimelineBottomSheetFragment.newInstance();
                bottomSheet.show(getChildFragmentManager(), "AskTimelineBottomSheet");
            });
        }

        setupObservers();
    }

    private void setupObservers() {
        // Usage access permission state
        viewModel.getPermissionGranted().observe(getViewLifecycleOwner(), hasPermission -> {
            if (screenTimeStatsManager != null) {
                screenTimeStatsManager.renderPermissionState(hasPermission);
            }
        });

        // Loading progress
        viewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
            if (screenTimeStatsManager != null) {
                screenTimeStatsManager.renderLoadingState(isLoading);
            }
        });

        // Error state display
        viewModel.getLoadError().observe(getViewLifecycleOwner(), isError -> {
            if (screenTimeStatsManager != null) {
                screenTimeStatsManager.renderLoadError(isError);
            }
        });

        // Selected tab daily or weekly
        viewModel.getSelectedPeriod().observe(getViewLifecycleOwner(), period -> {
            if (screenTimeStatsManager != null) {
                screenTimeStatsManager.renderPeriodSelected(period);
            }
        });

        // Daily usage metrics
        viewModel.getDailyUsageItem().observe(getViewLifecycleOwner(), item -> {
            if (screenTimeStatsManager != null && item != null) {
                int period = ScreenTimeViewModel.PERIOD_DAILY;
                if (viewModel.getSelectedPeriod().getValue() != null) {
                    period = viewModel.getSelectedPeriod().getValue();
                }
                if (period == ScreenTimeViewModel.PERIOD_DAILY) {
                    int dayIndex = 0;
                    if (viewModel.getCurrentDayIndex().getValue() != null) {
                        dayIndex = viewModel.getCurrentDayIndex().getValue();
                    }
                    screenTimeStatsManager.renderDailyUsage(item, viewModel.getCurrentPackageUsage().getValue(), dayIndex);
                }
            }
        });

        // Weekly usage metrics
        viewModel.getWeeklyUsageItem().observe(getViewLifecycleOwner(), item -> {
            if (screenTimeStatsManager != null && item != null) {
                int period = ScreenTimeViewModel.PERIOD_DAILY;
                if (viewModel.getSelectedPeriod().getValue() != null) {
                    period = viewModel.getSelectedPeriod().getValue();
                }
                if (period == ScreenTimeViewModel.PERIOD_WEEKLY) {
                    int weekIndex = 0;
                    if (viewModel.getCurrentWeekIndex().getValue() != null) {
                        weekIndex = viewModel.getCurrentWeekIndex().getValue();
                    }
                    screenTimeStatsManager.renderWeeklyUsage(item, viewModel.getCurrentPackageUsage().getValue(), weekIndex);
                }
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        // Refresh stats when returning to the fragment (e.g., coming back from system permission settings)
        if (viewModel != null) {
            viewModel.loadScreenTimeStats();
        }
    }

    @Override
    public void onDestroyView() {
        // Clean up UI manager references to prevent memory leaks across view lifecycles
        if (screenTimeStatsManager != null) {
            screenTimeStatsManager.destroy();
            screenTimeStatsManager = null;
        }
        super.onDestroyView();
    }
}
