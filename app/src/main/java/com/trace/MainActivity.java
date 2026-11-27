package com.trace;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.VideoView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.trace.util.ErrorLogger;
import com.trace.util.Logger;
import com.trace.util.PermissionManager;
import com.trace.tracking.usage.UsageStatsHelper;
import com.trace.ui.home.HomeFragment;
import com.trace.ui.settings.SettingsFragment;
import com.trace.ui.statistics.StatisticsFragment;
import com.trace.tracking.location.LocationService;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.card.MaterialCardView;

import java.util.Date;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory;

import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private PermissionManager permissionManager;
    private String[] foregroundPermissions;
    private boolean isShowingRationaleDialog = false;

    private View permissionBlocker;
    private Button permissionAction;
    private Button permissionSkip;
    private TextView permissionSubtitle;

    private HomeFragment homeFragment = new HomeFragment();
    private StatisticsFragment statisticsFragment = new StatisticsFragment();
    private SettingsFragment settingsFragment = new SettingsFragment();
    private Fragment activeFragment = homeFragment;
    private LinearProgressIndicator permissionProgressBar;
    private TextView permissionStepText, permissionTitle;
    private MaterialCardView permissionIconCard, permissionVideoCard;
    private ImageView permissionIcon;
    private VideoView permissionVideo;
    private BottomNavigationView bottomNav;
    private static final String PREFS_NAME = "MyPrefs";
    private Date lastKnownDate = new Date();

    private final ActivityResultLauncher<String[]> foregroundPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean isAllPermissionsGranted = true;
                for (Map.Entry<String, Boolean> entry : result.entrySet()) {
                    permissionManager.markPermissionRequested(entry.getKey());
                    if (!entry.getValue()) {
                        isAllPermissionsGranted = false;
                    }
                }

                if (!isAllPermissionsGranted) {
                    // avoid reopening the dialog
                    if (!isShowingRationaleDialog) {
                        handlePermissionDenied();
                    }
                }
                refreshPermissionUi(permissionManager.hasAllPermissions());
            });

    private void handlePermissionDenied() {
        if (permissionManager.shouldShowAnyPermissionRationale()) {
            isShowingRationaleDialog = true;

            permissionManager.showPermissionRationaleDialog(() -> {
                // if allow is clicked
                isShowingRationaleDialog = false;
                requestPermissions();
            }, () -> {
                // if decline is clicked
                isShowingRationaleDialog = false;
                // If user declines rationale, send them to settings
                permissionManager.showGoToSettingsDialog(permissionManager::openAppSettings, () -> Logger.saveLog(this, TAG + ": Settings dialog cancelled from rationale cancel"));
            });
        } else if (permissionManager.isAnyPermissionPermanentlyDenied()) {
            // go to settings
            permissionManager.showGoToSettingsDialog(permissionManager::openAppSettings, () -> Logger.saveLog(this, TAG + ": Settings dialog cancelled"));
        }
    }

    public void requestPermissions() {
        // check if foreground permissions are granted(everything but background location)
        boolean hasForeground = true;
        for (String perm : foregroundPermissions) {
            if (!permissionManager.hasPermission(perm)) {
                hasForeground = false;
                break;
            }
        }

        if (!hasForeground) {
            Logger.saveLog(this, TAG + ": Not all foreground permissions granted. Requesting foreground permissions.");
            // if not all foreground permission are granted, request them
            foregroundPermissionLauncher.launch(foregroundPermissions);
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // applies themes before super
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int currentMode = preferences.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_NO);
        AppCompatDelegate.setDefaultNightMode(currentMode);
        super.onCreate(savedInstanceState);

        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this);
        }

        setContentView(R.layout.activity_main);

        FirebaseAppCheck firebaseAppCheck = FirebaseAppCheck.getInstance();
        firebaseAppCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
        );
        permissionBlocker = findViewById(R.id.permission_blocker);
        permissionAction = findViewById(R.id.permission_action);
        permissionSkip = findViewById(R.id.permission_skip);
        permissionSubtitle = findViewById(R.id.permission_subtitle);
        permissionProgressBar = findViewById(R.id.permission_progress_bar);
        permissionStepText = findViewById(R.id.permission_step_text);
        permissionTitle = findViewById(R.id.permission_title);
        permissionIconCard = findViewById(R.id.permission_icon_card);
        permissionVideoCard = findViewById(R.id.permission_video_card);
        permissionIcon = findViewById(R.id.permission_icon);
        permissionVideo = findViewById(R.id.permission_video);

        permissionManager = new PermissionManager(this);

        foregroundPermissions = permissionManager.getRequiredPermissions();


        bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setOnItemSelectedListener(navListener);

        if (savedInstanceState == null) {
            // Initial load: add new fragments
            getSupportFragmentManager().beginTransaction().add(R.id.fragment_container, settingsFragment, "3").hide(settingsFragment).commit();
            getSupportFragmentManager().beginTransaction().add(R.id.fragment_container, statisticsFragment, "2").hide(statisticsFragment).commit();
            getSupportFragmentManager().beginTransaction().add(R.id.fragment_container, homeFragment, "1").commit();
            activeFragment = homeFragment;
        } else {
            // Recreation (e.g., theme change): retrieve existing fragments
            homeFragment = (HomeFragment) getSupportFragmentManager().findFragmentByTag("1");
            if (homeFragment == null) homeFragment = new HomeFragment();

            statisticsFragment = (StatisticsFragment) getSupportFragmentManager().findFragmentByTag("2");
            if (statisticsFragment == null) statisticsFragment = new StatisticsFragment();

            settingsFragment = (SettingsFragment) getSupportFragmentManager().findFragmentByTag("3");
            if (settingsFragment == null) settingsFragment = new SettingsFragment();

            // Pull the manually saved tab ID from the bundle, defaulting to nav_home
            int selectedId = savedInstanceState.getInt("selected_nav_id", R.id.nav_home);

            if (selectedId == R.id.nav_statistics) {
                activeFragment = statisticsFragment;
            } else if (selectedId == R.id.nav_settings) {
                activeFragment = settingsFragment;
            } else {
                activeFragment = homeFragment;
            }

            // Ensure proper visibility: hide inactive fragments and show active fragment
            getSupportFragmentManager().beginTransaction()
                    .hide(homeFragment)
                    .hide(statisticsFragment)
                    .hide(settingsFragment)
                    .show(activeFragment)
                    .commit();
        }

        refreshPermissionUi(permissionManager.hasAllPermissions());
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (bottomNav != null) {
            outState.putInt("selected_nav_id", bottomNav.getSelectedItemId());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        ContextCompat.registerReceiver(
                this,
                permissionRevokedReceiver,
                new IntentFilter("com.trace.PERMISSION_REVOKED"),
                ContextCompat.RECEIVER_NOT_EXPORTED
        );

        IntentFilter dateFilter = new IntentFilter();
        dateFilter.addAction(Intent.ACTION_DATE_CHANGED);
        dateFilter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        dateFilter.addAction(Intent.ACTION_TIME_CHANGED);
        ContextCompat.registerReceiver(this, dateChangedReceiver, dateFilter, ContextCompat.RECEIVER_NOT_EXPORTED);

        Date now = new Date();
        if (!isSameDay(now, lastKnownDate)) {
            lastKnownDate = now;
            navigateToDate(now);
        }

        // Refresh UI and ensure services are running if permissions are granted
        boolean hasAllPerms = permissionManager.hasAllPermissions();
        refreshPermissionUi(hasAllPerms);
        if (hasAllPerms) {
            startTrackingService();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(permissionRevokedReceiver);
        unregisterReceiver(dateChangedReceiver);
    }

    private final BroadcastReceiver permissionRevokedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.trace.PERMISSION_REVOKED".equals(intent.getAction())) {
                refreshPermissionUi(permissionManager.hasAllPermissions());

                new MaterialAlertDialogBuilder(MainActivity.this)
                        .setTitle("Tracking Paused")
                        .setMessage("Background location access was revoked. Please grant it in settings to resume timeline tracking.")
                        .show();
            }

        }
    };

    private boolean isSameDay(Date d1, Date d2) {
        if (d1 == null || d2 == null) return false;
        java.util.Calendar cal1 = java.util.Calendar.getInstance();
        cal1.setTime(d1);
        java.util.Calendar cal2 = java.util.Calendar.getInstance();
        cal2.setTime(d2);
        return cal1.get(java.util.Calendar.YEAR) == cal2.get(java.util.Calendar.YEAR) &&
               cal1.get(java.util.Calendar.DAY_OF_YEAR) == cal2.get(java.util.Calendar.DAY_OF_YEAR);
    }

    private final BroadcastReceiver dateChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_DATE_CHANGED.equals(action) ||
                Intent.ACTION_TIMEZONE_CHANGED.equals(action) ||
                Intent.ACTION_TIME_CHANGED.equals(action)) {

                Date now = new Date();
                if (!isSameDay(now, lastKnownDate)) {
                    lastKnownDate = now;
                    navigateToDate(now);
                }
            }
        }
    };

    private final BottomNavigationView.OnItemSelectedListener navListener =
            // the navigation bar listener
            item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.nav_home) {
                    getSupportFragmentManager().beginTransaction().hide(activeFragment).show(homeFragment).commit();
                    activeFragment = homeFragment;
                } else if (itemId == R.id.nav_statistics) {
                    getSupportFragmentManager().beginTransaction().hide(activeFragment).show(statisticsFragment).commit();
                    activeFragment = statisticsFragment;
                } else if (itemId == R.id.nav_settings) {
                    getSupportFragmentManager().beginTransaction().hide(activeFragment).show(settingsFragment).commit();
                    activeFragment = settingsFragment;
                }
                return true;
            };


    private void refreshPermissionUi(boolean hasPerms) {
        Logger.saveLog(this, TAG + ": refreshPermissionUi called with hasPerms: " + hasPerms);
        boolean hasUsageAccess = UsageStatsHelper.hasUsageAccessPermission(this) || permissionManager.isUsageAccessSkipped();
        boolean isFullyGranted = hasPerms && hasUsageAccess;

        if (isFullyGranted) {
            permissionBlocker.setVisibility(View.GONE);
            if (bottomNav != null) bottomNav.setVisibility(View.VISIBLE);
            getSupportFragmentManager().beginTransaction().show(activeFragment).commit();
            if (permissionVideo.isPlaying()) permissionVideo.stopPlayback();
        } else {
            permissionBlocker.setVisibility(View.VISIBLE);
            if (bottomNav != null) bottomNav.setVisibility(View.GONE);
            getSupportFragmentManager().beginTransaction().hide(activeFragment).commit();

            boolean permanent = permissionManager.isAnyPermissionPermanentlyDenied();
            if (permanent) {
                // Handle permanently denied state
                permissionVideoCard.setVisibility(View.GONE);
                permissionIconCard.setVisibility(View.VISIBLE);
                permissionIcon.setImageResource(android.R.drawable.ic_dialog_alert);

                permissionTitle.setText("Permissions Denied");
                permissionSubtitle.setText("Permissions were denied permanently. Please enable them in Settings to continue.");
                permissionAction.setText("Open Settings");
                permissionAction.setOnClickListener(v -> permissionManager.openAppSettings());
                if (permissionSkip != null) {
                    permissionSkip.setVisibility(View.GONE);
                }
            } else {
                // Update the dynamic steps
                updatePermissionStepUI();
            }
        }
    }

    private void updatePermissionStepUI() {
        int totalSteps = 3;
        permissionProgressBar.setMax(totalSteps);

        // Stop video if it was playing
        if (permissionVideo.isPlaying()) {
            permissionVideo.pause();
        }

        // Step 1: Notifications & Activity Recognition
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !permissionManager.hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
            setupStep(1, totalSteps, "Notifications",
                    "We need notifications to keep you updated on your timeline status.",
                    android.R.drawable.ic_dialog_info, false);
        }
        // Step 2: Foreground Location
        else if (!permissionManager.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            setupStep(2, totalSteps, "Location Access",
                    "Timeline needs location to track your visits while the app is open.",
                    android.R.drawable.ic_menu_mylocation, false);
        }
        // Step 3: App Usage Access (Optional)
        else if (!UsageStatsHelper.hasUsageAccessPermission(this) && !permissionManager.isUsageAccessSkipped()) {
            setupStep(3, totalSteps, "App Usage Access",
                    "Timeline can track your screen time and app usage statistics alongside location visits. This permission is optional.",
                    android.R.drawable.ic_menu_recent_history, true);
        }
    }

    private void setupStep(int currentStep, int totalSteps, String title, String subtitle, int iconRes, boolean isOptional) {
        permissionProgressBar.setProgress(currentStep, true);
        permissionStepText.setText("Step " + currentStep + " of " + totalSteps);

        permissionTitle.setText(title);
        permissionSubtitle.setText(subtitle);

        // Show Icon, Hide Video
        permissionVideoCard.setVisibility(View.GONE);
        permissionIconCard.setVisibility(View.VISIBLE);
        permissionIcon.setImageResource(iconRes);

        permissionAction.setText("Grant Access");
        if (isOptional) {
            permissionAction.setOnClickListener(v -> UsageStatsHelper.openUsageAccessSettings(this));
            if (permissionSkip != null) {
                permissionSkip.setVisibility(View.VISIBLE);
                permissionSkip.setOnClickListener(v -> {
                    permissionManager.setUsageAccessSkipped(true);
                    refreshPermissionUi(permissionManager.hasAllPermissions());
                });
            }
        } else {
            permissionAction.setOnClickListener(v -> requestPermissions());
            if (permissionSkip != null) {
                permissionSkip.setVisibility(View.GONE);
            }
        }
    }

    private void startTrackingService() {

        Intent intent = new Intent(this, LocationService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                this.startForegroundService(intent);
            } else {
                this.startService(intent);
            }
            Logger.saveLog(this, TAG + ": Tracking service started successfully.");
        } catch (Throwable t) {
            ErrorLogger.logError(this, TAG, "Error", t);
            Logger.saveLog(this, TAG + ": Failed to start tracking service: " + t.getMessage());
        }
    }

    public void navigateToScreenTimeStatistics() {
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.nav_statistics);
        }
        if (statisticsFragment != null) {
            statisticsFragment.selectScreenTimeTab();
        }
    }

    public void navigateToDate(Date date) {
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.nav_home);
        }
        if (homeFragment != null) {
            homeFragment.setSelectedDate(date);
        }
    }
}
