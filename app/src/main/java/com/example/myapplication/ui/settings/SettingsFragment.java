package com.example.myapplication.ui.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

import com.example.myapplication.LifeTrackerApp;
import com.example.myapplication.R;
import com.example.myapplication.data.db.ActivityDatabase;
import com.example.myapplication.tracking.usage.UsageStatsHelper;
import com.example.myapplication.util.PermissionManager;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.Calendar;

public class SettingsFragment extends Fragment {

    private static final String PREFS_NAME = "MyPrefs";
    private static final String KEY_WEEK_START_DAY = "week_start_day";
    private LifeTrackerApp app;
    private CloudSyncSettings cloudSyncSettings;

    private TextView tvLocationPermissionStatus;
    private TextView tvUsagePermissionStatus;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        cloudSyncSettings = new CloudSyncSettings(this, view);
        app = (LifeTrackerApp) requireActivity().getApplication();

        MaterialButtonToggleGroup themeToggleGroup = view.findViewById(R.id.themeToggleGroup);
        MaterialButtonToggleGroup weekStartToggleGroup = view.findViewById(R.id.weekStartToggleGroup);
        View itemLocationPermission = view.findViewById(R.id.item_location_permission);
        View itemUsagePermission = view.findViewById(R.id.item_usage_permission);
        tvLocationPermissionStatus = view.findViewById(R.id.tv_location_permission_status);
        tvUsagePermissionStatus = view.findViewById(R.id.tv_usage_permission_status);
        View btnDeleteHistory = view.findViewById(R.id.btn_delete_history);

        setupThemePreferences(themeToggleGroup);
        setupWeekStartDayPreference(weekStartToggleGroup);
        setupPermissionsSection(itemLocationPermission, itemUsagePermission);
        setupDataPrivacyListeners(btnDeleteHistory);
    }

    @Override
    public void onResume() {
        super.onResume();
        updatePermissionStatuses();
    }

    @Override public void onDestroyView() {
        if (cloudSyncSettings != null) cloudSyncSettings.close();
        cloudSyncSettings = null;
        super.onDestroyView();
    }

    private void setupThemePreferences(MaterialButtonToggleGroup themeToggleGroup) {
        SharedPreferences preferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int currentMode = preferences.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);

        switch (currentMode) {
            case AppCompatDelegate.MODE_NIGHT_YES:
                themeToggleGroup.check(R.id.btn_theme_dark);
                break;
            case AppCompatDelegate.MODE_NIGHT_NO:
                themeToggleGroup.check(R.id.btn_theme_light);
                break;
            default:
                themeToggleGroup.check(R.id.btn_theme_system);
                break;
        }

        themeToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                int selectedMode;
                if (checkedId == R.id.btn_theme_dark) {
                    selectedMode = AppCompatDelegate.MODE_NIGHT_YES;
                } else if (checkedId == R.id.btn_theme_light) {
                    selectedMode = AppCompatDelegate.MODE_NIGHT_NO;
                } else {
                    selectedMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
                }

                if (preferences.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_NO) != selectedMode) {
                    preferences.edit().putInt("theme_mode", selectedMode).apply();
                    AppCompatDelegate.setDefaultNightMode(selectedMode);
                }
            }
        });
    }

    private void setupWeekStartDayPreference(MaterialButtonToggleGroup toggleGroup) {
        int savedWeekStartDay = getWeekStartDayPreference();
        if (savedWeekStartDay == Calendar.MONDAY) {
            toggleGroup.check(R.id.btn_week_monday);
        } else {
            toggleGroup.check(R.id.btn_week_sunday);
        }

        toggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                int weekStartDay = (checkedId == R.id.btn_week_monday) ? Calendar.MONDAY : Calendar.SUNDAY;
                saveWeekStartDayPreference(weekStartDay);
            }
        });
    }

    private void saveWeekStartDayPreference(int weekStartDay) {
        SharedPreferences preferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        preferences.edit().putInt(KEY_WEEK_START_DAY, weekStartDay).apply();
    }

    private int getWeekStartDayPreference() {
        SharedPreferences preferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return preferences.getInt(KEY_WEEK_START_DAY, getSystemFirstDayOfWeek());
    }

    private int getSystemFirstDayOfWeek() {
        int systemFirstDay = Calendar.getInstance().getFirstDayOfWeek();
        return (systemFirstDay == Calendar.SUNDAY) ? Calendar.SUNDAY : Calendar.MONDAY;
    }

    private void setupPermissionsSection(View itemLocation, View itemUsage) {
        itemLocation.setOnClickListener(v -> {
            PermissionManager permissionManager = new PermissionManager(requireActivity());
            if (!permissionManager.hasAllPermissions()) {
                permissionManager.openAppSettings();
            } else {
                Toast.makeText(getContext(), "Location permissions are granted", Toast.LENGTH_SHORT).show();
            }
        });

        itemUsage.setOnClickListener(v -> {
            if (!UsageStatsHelper.hasUsageAccessPermission(requireContext())) {
                UsageStatsHelper.openUsageAccessSettings(requireContext());
            } else {
                Toast.makeText(getContext(), "App usage stats access is granted", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updatePermissionStatuses() {
        if (getContext() == null || getActivity() == null) return;

        PermissionManager permissionManager = new PermissionManager(requireActivity());
        boolean hasLocation = permissionManager.hasAllPermissions();
        if (tvLocationPermissionStatus != null) {
            tvLocationPermissionStatus.setText(hasLocation ? "Granted" : "Not Granted — Tap to manage in settings");
        }

        boolean hasUsage = UsageStatsHelper.hasUsageAccessPermission(requireContext());
        if (tvUsagePermissionStatus != null) {
            tvUsagePermissionStatus.setText(hasUsage ? "Granted" : "Not Granted — Tap to allow access");
        }
    }

    private void setupDataPrivacyListeners(View btnDeleteHistory) {
        btnDeleteHistory.setOnClickListener(v -> handleDeleteHistory());
    }

    private void handleDeleteHistory() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete Location History")
                .setMessage("Delete all history and saved places on this phone? These deletions will also sync to your account when cloud sync is enabled. This cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    Context context = requireContext().getApplicationContext();
                    app.getBackgroundWriteExecutor().execute(() -> {
                        ActivityDatabase db = ActivityDatabase.getDatabase(context);
                        db.runInTransaction(() -> {
                            db.activityDao().deleteAllActivities();
                            db.placeDao().deleteAllPlaces();
                        });
                        com.example.myapplication.data.sync.CloudSyncManager.schedule(context);

                        SharedPreferences llmPrefs = context.getSharedPreferences("llm_insights_prefs", Context.MODE_PRIVATE);
                        llmPrefs.edit().clear().apply();

                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() ->
                                    Toast.makeText(getContext(), "All location history deleted", Toast.LENGTH_SHORT).show()
                            );
                        }
                    });
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }
}
