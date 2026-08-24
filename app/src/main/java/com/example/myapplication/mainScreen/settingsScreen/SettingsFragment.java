package com.example.myapplication.mainScreen.settingsScreen;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.app.AlertDialog;
import android.widget.RadioGroup;
import android.widget.RadioButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

import com.example.myapplication.LifeTrackerApp;
import com.example.myapplication.R;
import com.example.myapplication.database.ActivityDatabase;
import com.example.myapplication.database.PlaceDao;
import com.google.android.material.button.MaterialButtonToggleGroup;

import java.util.Calendar;

public class SettingsFragment extends Fragment {

    private static final String PREFS_NAME = "MyPrefs";
    private static final String KEY_WEEK_START_DAY = "week_start_day";
    private LifeTrackerApp app;

    private RadioGroup weekStartDayRadioGroup;
    private RadioButton radioMonday;
    private RadioButton radioSunday;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        MaterialButtonToggleGroup themeToggleGroup = view.findViewById(R.id.themeToggleGroup);
        TextView btnExportData = view.findViewById(R.id.btn_export_data);
        TextView btnDeleteHistory = view.findViewById(R.id.btn_delete_history);

        weekStartDayRadioGroup = view.findViewById(R.id.weekStartDayRadioGroup);
        radioMonday = view.findViewById(R.id.radio_monday);
        radioSunday = view.findViewById(R.id.radio_sunday);

        app = (LifeTrackerApp) requireActivity().getApplication();

        setupThemePreferences(themeToggleGroup);
        setupWeekStartDayPreference(weekStartDayRadioGroup);
        setupDataPrivacyListeners(btnExportData, btnDeleteHistory);
    }

    private void setupThemePreferences(MaterialButtonToggleGroup themeToggleGroup) {
        SharedPreferences preferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int currentMode = preferences.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);

        if (currentMode == AppCompatDelegate.MODE_NIGHT_YES) {
            themeToggleGroup.check(R.id.btn_theme_dark);
        } else if (currentMode == AppCompatDelegate.MODE_NIGHT_NO) {
            themeToggleGroup.check(R.id.btn_theme_light);
        } else {
            themeToggleGroup.check(R.id.btn_theme_system);
        }

        themeToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                int selectedMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;

                if (checkedId == R.id.btn_theme_dark) {
                    selectedMode = AppCompatDelegate.MODE_NIGHT_YES;
                } else if (checkedId == R.id.btn_theme_light) {
                    selectedMode = AppCompatDelegate.MODE_NIGHT_NO;
                }

                // Apply and save only if it's not the same
                if (AppCompatDelegate.getDefaultNightMode() != selectedMode) {
                    preferences.edit().putInt("theme_mode", selectedMode).apply();
                    AppCompatDelegate.setDefaultNightMode(selectedMode);
                }
            }
        });
    }

    private void setupWeekStartDayPreference(RadioGroup radioGroup) {
        int savedWeekStartDay = getWeekStartDayPreference();
        if (savedWeekStartDay == Calendar.MONDAY) {
            radioMonday.setChecked(true);
        } else {
            radioSunday.setChecked(true);
        }

        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            int weekStartDay = (checkedId == R.id.radio_monday) ? Calendar.MONDAY : Calendar.SUNDAY;
            saveWeekStartDayPreference(weekStartDay);
            // TODO: Potentially trigger a reload of statistics if the user is on the statistics screen
        });
    }

    private void saveWeekStartDayPreference(int weekStartDay) {
        SharedPreferences preferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        preferences.edit().putInt(KEY_WEEK_START_DAY, weekStartDay).apply();
    }

    private int getWeekStartDayPreference() {
        SharedPreferences preferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        // Default to Monday if no preference is set
        return preferences.getInt(KEY_WEEK_START_DAY, Calendar.MONDAY);
    }

    private void setupDataPrivacyListeners(TextView btnExportData, TextView btnDeleteHistory) {
        btnExportData.setOnClickListener(v -> handleExportData());
        btnDeleteHistory.setOnClickListener(v -> handleDeleteHistory());
    }

    private void handleExportData() {
        // TODO: Implement actual export logic
        Toast.makeText(getContext(), "Exporting data...", Toast.LENGTH_SHORT).show();
    }

    private void handleDeleteHistory() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete Location History")
                .setMessage("Are you sure you want to delete all your location history? This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    app.getDatabaseWriteExecutor().execute(() -> {
                        ActivityDatabase db = ActivityDatabase.getDatabase(requireContext());
                        PlaceDao placeDao = db.placeDao();
                        db.activityDao().deleteAllActivities();
                        placeDao.deleteAllPlaces();
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                Toast.makeText(getContext(), "Location history deleted", Toast.LENGTH_SHORT).show();
                            });
                        }
                    });
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    Toast.makeText(getContext(), "Deletion cancelled", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                })
                .show();
    }
}