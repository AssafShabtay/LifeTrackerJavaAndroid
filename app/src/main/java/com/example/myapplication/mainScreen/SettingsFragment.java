package com.example.myapplication.mainScreen;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.app.AlertDialog;
import android.content.DialogInterface;
import java.util.concurrent.Executors;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

import com.example.myapplication.R;
import com.example.myapplication.database.ActivityDatabase;
import com.example.myapplication.database.PlaceDao;
import com.google.android.material.button.MaterialButtonToggleGroup;

public class SettingsFragment extends Fragment {

    private static final String PREFS_NAME = "MyPrefs";

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

        setupThemePreferences(themeToggleGroup);
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


    private void setupDataPrivacyListeners(TextView btnExportData, TextView btnDeleteHistory) {
        btnExportData.setOnClickListener(v -> handleExportData());
        btnDeleteHistory.setOnClickListener(v -> handleDeleteHistory());
    }

    private void handleExportData() {
        // TODO: Implement actual export logic
        Toast.makeText(getContext(), "Exporting data...", Toast.LENGTH_SHORT).show();
    }

    private void handleDeleteHistory() {
        // TODO: Implement database deletion logic (Recommend showing a confirmation dialog first)
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete Location History")
                .setMessage("Are you sure you want to delete all your location history? This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    Executors.newSingleThreadExecutor().execute(() -> {
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