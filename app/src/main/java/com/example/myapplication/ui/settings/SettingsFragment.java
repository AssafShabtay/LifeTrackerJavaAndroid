package com.example.myapplication.ui.settings;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Process;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.example.myapplication.LifeTrackerApp;
import com.example.myapplication.MainActivity;
import com.example.myapplication.R;
import com.example.myapplication.data.backup.AutoBackupWorker;
import com.example.myapplication.data.backup.BackupManager;
import com.example.myapplication.data.db.ActivityDatabase;
import com.example.myapplication.tracking.usage.UsageStatsHelper;
import com.example.myapplication.ui.auth.LoginActivity;
import com.example.myapplication.util.PermissionManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class SettingsFragment extends Fragment {

    private static final String PREFS_NAME = "MyPrefs";
    private static final String KEY_WEEK_START_DAY = "week_start_day";
    private static final String KEY_LAST_BACKUP = "last_backup_time";
    private static final String KEY_AUTO_BACKUP = "auto_backup_enabled";

    private LifeTrackerApp app;

    private TextView tvLocationPermissionStatus;
    private TextView tvUsagePermissionStatus;

    private TextView tvAccountStatus;
    private MaterialButton btnAccountAction;
    private TextView tvLastBackup;
    private TextView tvBackupDisabledReason;
    private LinearProgressIndicator progressBackup;
    private MaterialButton btnBackupNow;
    private MaterialButton btnRestoreBackup;
    private MaterialButton btnDeleteCloudBackup;
    private MaterialSwitch switchAutoBackup;
    private View layoutOfflineInfo;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        app = (LifeTrackerApp) requireActivity().getApplication();

        MaterialButtonToggleGroup themeToggleGroup = view.findViewById(R.id.themeToggleGroup);
        MaterialButtonToggleGroup weekStartToggleGroup = view.findViewById(R.id.weekStartToggleGroup);
        View itemLocationPermission = view.findViewById(R.id.item_location_permission);
        View itemUsagePermission = view.findViewById(R.id.item_usage_permission);
        tvLocationPermissionStatus = view.findViewById(R.id.tv_location_permission_status);
        tvUsagePermissionStatus = view.findViewById(R.id.tv_usage_permission_status);
        View btnDeleteHistory = view.findViewById(R.id.btn_delete_history);

        // Bind Cloud Backup UI
        tvAccountStatus = view.findViewById(R.id.tv_account_status);
        btnAccountAction = view.findViewById(R.id.btn_account_action);
        tvLastBackup = view.findViewById(R.id.tv_last_backup);
        tvBackupDisabledReason = view.findViewById(R.id.tv_backup_disabled_reason);
        progressBackup = view.findViewById(R.id.progress_backup);
        btnBackupNow = view.findViewById(R.id.btn_backup_now);
        btnRestoreBackup = view.findViewById(R.id.btn_restore_backup);
        btnDeleteCloudBackup = view.findViewById(R.id.btn_delete_cloud_backup);
        switchAutoBackup = view.findViewById(R.id.switch_auto_backup);
        layoutOfflineInfo = view.findViewById(R.id.layout_offline_info);

        setupThemePreferences(themeToggleGroup);
        setupWeekStartDayPreference(weekStartToggleGroup);
        setupPermissionsSection(itemLocationPermission, itemUsagePermission);
        setupCloudBackupSection();
        setupDataPrivacyListeners(btnDeleteHistory);
    }

    @Override
    public void onResume() {
        super.onResume();
        updatePermissionStatuses();
        updateAccountAndBackupUi();
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
                int weekStartDay = (checkedId == R.id.btn_week_monday) ? Calendar.MONDAY
                                                                       : Calendar.SUNDAY;
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
        return (systemFirstDay == Calendar.SUNDAY) ? Calendar.SUNDAY
                                                   : Calendar.MONDAY;
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
            if (hasLocation) {
                tvLocationPermissionStatus.setText("Granted");
            } else {
                tvLocationPermissionStatus.setText("Not Granted — Tap to manage in settings");
            }
        }

        boolean hasUsage = UsageStatsHelper.hasUsageAccessPermission(requireContext());
        if (tvUsagePermissionStatus != null) {
            if (hasUsage) {
                tvUsagePermissionStatus.setText("Granted");
            } else {
                tvUsagePermissionStatus.setText("Not Granted — Tap to allow access");
            }
        }
    }

    private void setupCloudBackupSection() {
        btnAccountAction.setOnClickListener(v -> handleAccountAction());
        btnBackupNow.setOnClickListener(v -> handleBackupNow());
        btnRestoreBackup.setOnClickListener(v -> handleRestoreBackup());
        btnDeleteCloudBackup.setOnClickListener(v -> handleDeleteCloudBackup());

        switchAutoBackup.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SharedPreferences preferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            if (preferences.getBoolean(KEY_AUTO_BACKUP, false) != isChecked) {
                preferences.edit().putBoolean(KEY_AUTO_BACKUP, isChecked).apply();
                updateAutoBackupWorker(isChecked);
            }
        });
    }

    private void updateAccountAndBackupUi() {
        if (getContext() == null || getActivity() == null) return;

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        SharedPreferences preferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        if (currentUser != null) {
            tvAccountStatus.setText("Signed in as " + currentUser.getEmail());
            btnAccountAction.setText("Sign out");
            btnBackupNow.setEnabled(true);
            btnRestoreBackup.setEnabled(true);
            btnDeleteCloudBackup.setEnabled(true);
            switchAutoBackup.setEnabled(true);
            if (layoutOfflineInfo != null) layoutOfflineInfo.setVisibility(View.GONE);

            long lastBackupTime = preferences.getLong(KEY_LAST_BACKUP, 0);
            if (lastBackupTime > 0) {
                tvLastBackup.setText("Last backup: " + formatTimestamp(lastBackupTime));
            } else {
                tvLastBackup.setText("Last backup: Never");
            }

            // Fetch backup metadata asynchronously to refresh info
            BackupManager.getBackupInfo(requireContext(), (ok, updatedTimeMillis, sizeBytes, message) -> {
                if (!isAdded() || getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    if (ok && updatedTimeMillis > 0) {
                        preferences.edit().putLong(KEY_LAST_BACKUP, updatedTimeMillis).apply();
                        tvLastBackup.setText("Last backup: " + formatTimestamp(updatedTimeMillis) + " (" + formatFileSize(sizeBytes) + ")");
                    }
                });
            });

        } else {
            tvAccountStatus.setText("Offline mode");
            btnAccountAction.setText("Sign in");
            btnBackupNow.setEnabled(false);
            btnRestoreBackup.setEnabled(false);
            btnDeleteCloudBackup.setEnabled(false);
            switchAutoBackup.setEnabled(false);
            if (layoutOfflineInfo != null) layoutOfflineInfo.setVisibility(View.VISIBLE);
            tvBackupDisabledReason.setText("Sign in to save your timeline data and location history to the cloud.");
            tvLastBackup.setText("Last backup: Never");
        }

        boolean autoBackupEnabled = preferences.getBoolean(KEY_AUTO_BACKUP, false);
        switchAutoBackup.setChecked(autoBackupEnabled);
    }

    private void handleAccountAction() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Intent intent = new Intent(requireContext(), LoginActivity.class);
            startActivity(intent);
        } else {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Sign Out")
                    .setMessage("Do you also want to delete all local timeline data from this device before signing out?")
                    .setPositiveButton("Delete Local Data & Sign Out", (dialog, which) -> performSignOut(true))
                    .setNegativeButton("Sign Out Only", (dialog, which) -> performSignOut(false))
                    .setNeutralButton("Cancel", (dialog, which) -> dialog.dismiss())
                    .show();
        }
    }

    private void performSignOut(boolean deleteLocalData) {
        SharedPreferences preferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        if (deleteLocalData) {
            progressBackup.setVisibility(View.VISIBLE);
            app.getBackgroundWriteExecutor().execute(() -> {
                ActivityDatabase db = ActivityDatabase.getDatabase(requireContext());
                db.activityDao().deleteAllActivities();
                db.placeDao().deleteAllPlaces();

                SharedPreferences llmPrefs = requireContext().getSharedPreferences("llm_insights_prefs", Context.MODE_PRIVATE);
                llmPrefs.edit().clear().apply();

                preferences.edit().remove(KEY_LAST_BACKUP).apply();

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        FirebaseAuth.getInstance().signOut();
                        progressBackup.setVisibility(View.GONE);
                        updateAccountAndBackupUi();
                        Toast.makeText(requireContext(), "Signed out successfully", Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } else {
            FirebaseAuth.getInstance().signOut();
            updateAccountAndBackupUi();
            Toast.makeText(requireContext(), "Signed out successfully", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleBackupNow() {
        progressBackup.setVisibility(View.VISIBLE);
        setBackupButtonsEnabled(false);

        BackupManager.getBackupInfo(requireContext(), (ok, updatedTimeMillis, sizeBytes, message) -> {
            if (!isAdded() || getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                if (ok && updatedTimeMillis > 0) {
                    progressBackup.setVisibility(View.GONE);
                    setBackupButtonsEnabled(true);
                    String dateStr = formatTimestamp(updatedTimeMillis);
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Overwrite Cloud Backup?")
                            .setMessage("A cloud backup already exists from " + dateStr + ". Do you want to overwrite it with current local data?")
                            .setPositiveButton("Overwrite", (dialog, which) -> executeBackup())
                            .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                            .show();
                } else {
                    executeBackup();
                }
            });
        });
    }

    private void executeBackup() {
        progressBackup.setVisibility(View.VISIBLE);
        setBackupButtonsEnabled(false);

        BackupManager.backup(requireContext(), (ok, message) -> {
            if (!isAdded() || getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                progressBackup.setVisibility(View.GONE);
                setBackupButtonsEnabled(true);
                Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
                if (ok) {
                    updateAccountAndBackupUi();
                }
            });
        });
    }

    private void handleRestoreBackup() {
        progressBackup.setVisibility(View.VISIBLE);
        setBackupButtonsEnabled(false);

        BackupManager.getBackupInfo(requireContext(), (ok, updatedTimeMillis, sizeBytes, message) -> {
            if (!isAdded() || getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                progressBackup.setVisibility(View.GONE);
                setBackupButtonsEnabled(true);

                if (!ok) {
                    Toast.makeText(getContext(), (message != null) ? message
                                                                   : "No backup found for this account.", Toast.LENGTH_LONG).show();
                    return;
                }

                String dateStr = formatTimestamp(updatedTimeMillis);
                String sizeStr = formatFileSize(sizeBytes);

                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Restore Database from Cloud")
                        .setMessage("ALL local location history and saved places on this device will be replaced by the cloud backup from " + dateStr + " (" + sizeStr + "). This action cannot be undone.")
                        .setPositiveButton("Restore", (dialog, which) -> executeRestore())
                        .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                        .show();
            });
        });
    }

    private void executeRestore() {
        progressBackup.setVisibility(View.VISIBLE);
        setBackupButtonsEnabled(false);

        BackupManager.restore(requireContext(), (ok, message) -> {
            if (!isAdded() || getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                progressBackup.setVisibility(View.GONE);
                setBackupButtonsEnabled(true);

                if (ok) {
                    Toast.makeText(getContext(), "Restore successful! Relaunching app...", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(requireContext(), MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    if (getActivity() != null) {
                        getActivity().finish();
                    }
                    Process.killProcess(Process.myPid());
                    System.exit(0);
                } else {
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Restore Failed")
                            .setMessage(message)
                            .setPositiveButton("OK", null)
                            .show();
                }
            });
        });
    }

    private void handleDeleteCloudBackup() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete Cloud Backup")
                .setMessage("Are you sure you want to delete your cloud backup from Firebase Storage? This cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    progressBackup.setVisibility(View.VISIBLE);
                    setBackupButtonsEnabled(false);

                    BackupManager.deleteCloudBackup(requireContext(), (ok, message) -> {
                        if (!isAdded() || getActivity() == null) return;
                        getActivity().runOnUiThread(() -> {
                            progressBackup.setVisibility(View.GONE);
                            setBackupButtonsEnabled(true);
                            Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
                            if (ok) {
                                updateAccountAndBackupUi();
                            }
                        });
                    });
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void setBackupButtonsEnabled(boolean enabled) {
        if (btnBackupNow != null) btnBackupNow.setEnabled(enabled);
        if (btnRestoreBackup != null) btnRestoreBackup.setEnabled(enabled);
        if (btnDeleteCloudBackup != null) btnDeleteCloudBackup.setEnabled(enabled);
        if (btnAccountAction != null) btnAccountAction.setEnabled(enabled);
    }

    private void updateAutoBackupWorker(boolean enabled) {
        if (enabled) {
            Constraints constraints = new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .setRequiresBatteryNotLow(true)
                    .build();

            PeriodicWorkRequest backupRequest = new PeriodicWorkRequest.Builder(
                    AutoBackupWorker.class, 24, TimeUnit.HOURS)
                    .setConstraints(constraints)
                    .build();

            WorkManager.getInstance(requireContext()).enqueueUniquePeriodicWork(
                    "auto_cloud_backup",
                    ExistingPeriodicWorkPolicy.KEEP,
                    backupRequest
            );
        } else {
            WorkManager.getInstance(requireContext()).cancelUniqueWork("auto_cloud_backup");
        }
    }

    private void setupDataPrivacyListeners(View btnDeleteHistory) {
        btnDeleteHistory.setOnClickListener(v -> handleDeleteHistory());
    }

    private void handleDeleteHistory() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Delete Location History")
                    .setMessage("Are you sure you want to delete your location history? You can choose to delete local data only or also delete your cloud backup.")
                    .setPositiveButton("Delete Local Only", (dialog, which) -> deleteLocalHistoryOnly())
                    .setNeutralButton("Delete Local & Cloud Backup", (dialog, which) -> {
                        deleteLocalHistoryOnly();
                        BackupManager.deleteCloudBackup(requireContext(), (ok, message) -> {
                            if (!isAdded() || getActivity() == null) return;
                            getActivity().runOnUiThread(() ->
                                    Toast.makeText(getContext(), "Cloud backup also deleted", Toast.LENGTH_SHORT).show()
                            );
                        });
                    })
                    .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                    .show();
        } else {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Delete Location History")
                    .setMessage("Are you sure you want to delete all your location history and saved places? This action cannot be undone.")
                    .setPositiveButton("Delete", (dialog, which) -> deleteLocalHistoryOnly())
                    .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                    .show();
        }
    }

    private void deleteLocalHistoryOnly() {
        app.getBackgroundWriteExecutor().execute(() -> {
            ActivityDatabase db = ActivityDatabase.getDatabase(requireContext());
            db.activityDao().deleteAllActivities();
            db.placeDao().deleteAllPlaces();

            SharedPreferences llmPrefs = requireContext().getSharedPreferences("llm_insights_prefs", Context.MODE_PRIVATE);
            llmPrefs.edit().clear().apply();

            if (getActivity() != null) {
                getActivity().runOnUiThread(() ->
                        Toast.makeText(getContext(), "All location history deleted", Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    private String formatTimestamp(long millis) {
        if (millis <= 0) return "Never";
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
        return sdf.format(new Date(millis));
    }

    private String formatFileSize(long bytes) {
        if (bytes <= 0) return "0 B";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0);
        return String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
