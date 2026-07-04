package com.example.myapplication;

import android.Manifest;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate; // Import AppCompatDelegate
import androidx.fragment.app.Fragment;

import com.example.myapplication.helpers.PermissionManager;
import com.example.myapplication.mainScreen.HomeFragment;
import com.example.myapplication.mainScreen.SettingsFragment;
import com.example.myapplication.mainScreen.StatisticsFragment;
import com.example.myapplication.locationTracking.reciever.ActivityTransitionReceiver;
import com.example.myapplication.locationTracking.LocationService;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityTransition;
import com.google.android.gms.location.ActivityTransitionRequest;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private PermissionManager permissionManager;
    private String[] foregroundPermissions;
    private OnHomeAddressChangedListener onHomeAddressChangedListener;
    private boolean isShowingRationaleDialog = false;

    private View permissionBlocker;
    private Button permissionAction;
    private TextView permissionSubtitle;
    private boolean transitionsRegistered = false;
    private boolean trackingServiceStarted = false;

    private final HomeFragment homeFragment = new HomeFragment();
    private final StatisticsFragment statisticsFragment = new StatisticsFragment();
    private final SettingsFragment settingsFragment = new SettingsFragment();
    private Fragment activeFragment = homeFragment;

    private static final String PREFS_NAME = "MyPrefs";
    private static final String THEME_KEY = "theme_preference";

    public interface OnHomeAddressChangedListener {
        void onHomeAddressChanged();
    }

    private final ActivityResultLauncher<String[]> foregroundPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean isAllPermissionsGranted = true;
                for (Map.Entry<String, Boolean> entry : result.entrySet()) {
                    permissionManager.markPermissionRequested(entry.getKey());
                    if (!entry.getValue()) {
                        isAllPermissionsGranted = false;
                    }
                }

                if (isAllPermissionsGranted) {
                    checkAndRequestBackgroundLocation();
                } else {
                    // avoid re opening the dialog
                    if (!isShowingRationaleDialog) {
                        handlePermissionDenied();
                    }
                }
                refreshPermissionUi(permissionManager.hasAllPermissions());
            });

    private final ActivityResultLauncher<String> backgroundPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                permissionManager.markPermissionRequested(Manifest.permission.ACCESS_BACKGROUND_LOCATION); // TODO FIX API LEVEL
                if (isGranted) {
                    requestTransitions();
                    startTrackingService();
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
                permissionManager.showGoToSettingsDialog(permissionManager::openAppSettings, () -> Log.d(TAG, "Settings dialog cancelled from rationale cancel"));
            });
        } else if (permissionManager.isAnyPermissionPermanentlyDenied()) {
            // go to settings
            permissionManager.showGoToSettingsDialog(permissionManager::openAppSettings, () -> Log.d(TAG, "Settings dialog cancelled"));
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

        if (hasForeground) {
            // request background location if all foreground perms are granted
            checkAndRequestBackgroundLocation();
        } else {
            // if not all foreground permission are granted, request them
            foregroundPermissionLauncher.launch(foregroundPermissions);
        }
    }

    private void checkAndRequestBackgroundLocation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (permissionManager.hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                // if all permissions are granted start tracking
                requestTransitions();
                startTrackingService();
            } else {
                // Show a custom dialog explaining why background location is needed before showing the system/settings prompt
                new AlertDialog.Builder(this) //TODO CHANGE TEXT AND IMAGE EXPLANATION
                        .setTitle("Background Location Access")
                        .setMessage("This app collects location data to enable timeline visits and geofencing even when the app is closed or not in use. Please select 'Allow all the time' in the next screen.")
                        .setPositiveButton("Grant", (dialog, which) -> {
                            backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
                        })
                        .setNegativeButton("No thanks", (dialog, which) -> {

                            // If the user declines, check if the permission is permanently denied
                            // and offer to open settings.
                            if (permissionManager.isPermanentlyDenied(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                                permissionManager.showGoToSettingsDialog(permissionManager::openAppSettings, () -> Log.d(TAG, "Settings dialog cancelled after background rationale decline"));
                            }
                            // Otherwise, if not permanently denied, just continue without background access.
                            // The UI in HomeFragment should reflect the missing permission.
                            refreshPermissionUi(false);
                        })
                        .setCancelable(false)
                        .show();
            }
        } else {
            requestTransitions();
            startTrackingService();
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // applies themes before super
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean isDarkModePreferred = preferences.getBoolean(THEME_KEY, false);
        if (isDarkModePreferred) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        permissionBlocker = findViewById(R.id.permission_blocker);
        permissionAction = findViewById(R.id.permission_action);
        permissionSubtitle = findViewById(R.id.permission_subtitle);

        permissionManager = new PermissionManager(this);

        // Filter out background location from the permission list
        List<String> foregroudnPermList = new ArrayList<>();
        for (String perm : permissionManager.getRequiredPermissions()) {
            if (!perm.equals(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                foregroudnPermList.add(perm);
            }
        }
        foregroundPermissions = foregroudnPermList.toArray(new String[0]);

        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setOnItemSelectedListener(navListener);

        getSupportFragmentManager().beginTransaction().add(R.id.fragment_container, settingsFragment, "3").hide(settingsFragment).commit();
        getSupportFragmentManager().beginTransaction().add(R.id.fragment_container, statisticsFragment, "2").hide(statisticsFragment).commit();
        getSupportFragmentManager().beginTransaction().add(R.id.fragment_container, homeFragment, "1").commit();


        refreshPermissionUi(permissionManager.hasAllPermissions());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshPermissionUi(permissionManager.hasAllPermissions());
    }

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
        if (hasPerms) {
            permissionBlocker.setVisibility(View.GONE);
            getSupportFragmentManager().beginTransaction().show(activeFragment).commit();
        } else {
            permissionBlocker.setVisibility(View.VISIBLE);
            getSupportFragmentManager().beginTransaction().hide(activeFragment).commit();

            boolean permanent = permissionManager.isAnyPermissionPermanentlyDenied();
            permissionSubtitle.setText(permanent
                    ? "Permissions were denied. Please enable them in Settings to continue"
                    : "Please grant permissions to continue.");
            permissionAction.setText(permanent ? "Open Settings" : "Grant");
            permissionAction.setOnClickListener(v -> {
                if (permanent) {
                    permissionManager.openAppSettings();
                } else {
                    requestPermissions();
                }
            });
        }
    }

    private void requestTransitions() {
        if (!permissionManager.hasAllPermissions()) { //check if permissions are granted
            Log.w(TAG, "Aborting requestTransitions: permissions not fully granted.");
            return;
        }

        if (transitionsRegistered) {
            return;
        }

        ArrayList<ActivityTransition> transitions = new ArrayList<>();
        int[] types = new int[]{
                DetectedActivity.STILL,
                DetectedActivity.WALKING,
                DetectedActivity.RUNNING,
                DetectedActivity.IN_VEHICLE,
                DetectedActivity.ON_BICYCLE,
                DetectedActivity.ON_FOOT
        };

        for (int type : types) {
            transitions.add(new ActivityTransition.Builder()
                    .setActivityType(type)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                    .build());
            transitions.add(new ActivityTransition.Builder()
                    .setActivityType(type)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                    .build());
        }

        ActivityTransitionRequest request = new ActivityTransitionRequest(transitions);
        Intent intent = new Intent(this, ActivityTransitionReceiver.class);


        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                0,
                intent,
                flags
        );

        // Also request regular activity updates for a quick initial detection
        PendingIntent activityUpdatePendingIntent = PendingIntent.getBroadcast(//TODO THIS CHUNK MIGHT BE USELESS
                this,
                1,
                intent,
                flags
        );

        try {
            ActivityRecognition.getClient(this)
                    .requestActivityTransitionUpdates(request, pendingIntent)
                    .addOnSuccessListener(unused -> {
                        transitionsRegistered = true;
                        Log.d(TAG, "Activity transitions registered successfully");
                    })
                    .addOnFailureListener(e -> {
                        transitionsRegistered = false;
                        Log.e(TAG, "Registration failed", e);
                    });

            // Initial quick detection to avoid "idle" state
            ActivityRecognition.getClient(this) //TODO THIS CHUNK MIGHT BE USELESS
                    .requestActivityUpdates(5000, activityUpdatePendingIntent)
                    .addOnSuccessListener(unused -> Log.d(TAG, "Initial activity updates requested"))
                    .addOnFailureListener(e -> Log.e(TAG, "Failed to request initial activity updates", e));

        } catch (SecurityException e) {
            transitionsRegistered = false;
            Log.e(TAG, "missing permission for transitions", e);
        }
    }

    private void startTrackingService() {
        if (trackingServiceStarted) {
            return;
        }

        Intent intent = new Intent(this, LocationService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                this.startForegroundService(intent);
            } else {
                this.startService(intent);
            }
            trackingServiceStarted = true;
        } catch (Throwable t) {
            trackingServiceStarted = false;
            Log.e(TAG, "Failed to start tracking service", t);
        }
    }

    // Getters and Setters
    public void setOnHomeAddressChangedListener(OnHomeAddressChangedListener listener) {
        this.onHomeAddressChangedListener = listener;
    }

    public OnHomeAddressChangedListener getOnHomeAddressChangedListener() {
        return onHomeAddressChangedListener;
    }
}
