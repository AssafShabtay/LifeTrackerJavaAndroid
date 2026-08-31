package com.example.myapplication;

import android.Manifest;
import android.app.AlertDialog;
import android.app.PendingIntent;
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
import androidx.appcompat.app.AppCompatDelegate; // Import AppCompatDelegate
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.myapplication.helpers.ErrorLogger;
import com.example.myapplication.helpers.Logger;
import com.example.myapplication.helpers.PermissionManager;
import com.example.myapplication.mainScreen.homeScreen.HomeFragment;
import com.example.myapplication.mainScreen.settingsScreen.SettingsFragment;
import com.example.myapplication.mainScreen.statisticsScreen.StatisticsFragment;
import com.example.myapplication.locationTracking.receiver.ActivityTransitionReceiver;
import com.example.myapplication.locationTracking.LocationService;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityTransition;
import com.google.android.gms.location.ActivityTransitionRequest;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory;

import java.util.ArrayList;
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

                if (!isAllPermissionsGranted) {
                    // avoid re opening the dialog
                    if (!isShowingRationaleDialog) {
                        handlePermissionDenied();
                    }
                }
                refreshPermissionUi(permissionManager.hasAllPermissions());
            });

    private final ActivityResultLauncher<String> backgroundPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    Logger.saveLog(this, TAG + ": Background location granted. Requesting transitions and starting service.");
                    requestTransitions();
                    startTrackingService();
                } else {
                    Logger.saveLog(this, TAG + ": Background location denied.");
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

        setContentView(R.layout.activity_main);

        FirebaseApp.initializeApp(this);
        FirebaseAppCheck firebaseAppCheck = FirebaseAppCheck.getInstance();
        firebaseAppCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
        );
        permissionBlocker = findViewById(R.id.permission_blocker);
        permissionAction = findViewById(R.id.permission_action);
        permissionSubtitle = findViewById(R.id.permission_subtitle);
        // Initialize the new views
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
            statisticsFragment = (StatisticsFragment) getSupportFragmentManager().findFragmentByTag("2");
            settingsFragment = (SettingsFragment) getSupportFragmentManager().findFragmentByTag("3");

            // Pull the manually saved tab ID from the bundle, defaulting to nav_home
            int selectedId = savedInstanceState.getInt("selected_nav_id", R.id.nav_home);

            if (selectedId == R.id.nav_statistics) {
                activeFragment = statisticsFragment;
            } else if (selectedId == R.id.nav_settings) {
                activeFragment = settingsFragment;
            } else {
                activeFragment = homeFragment;
            }
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
                new IntentFilter("com.example.myapplication.PERMISSION_REVOKED"),
                ContextCompat.RECEIVER_NOT_EXPORTED
        );
        // Refresh UI and ensure services are running if permissions are granted
        boolean hasAllPerms = permissionManager.hasAllPermissions();
        refreshPermissionUi(hasAllPerms);
        if (hasAllPerms) {
            requestTransitions();
            startTrackingService();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(permissionRevokedReceiver);
    }

    private final BroadcastReceiver permissionRevokedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.example.myapplication.PERMISSION_REVOKED".equals(intent.getAction())) {
                refreshPermissionUi(permissionManager.hasAllPermissions());

                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Tracking Paused")
                        .setMessage("Background location access was revoked. Please grant it in settings to resume timeline tracking.")
                        .show();
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
        if (hasPerms) {
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
// Step 1: Notifications & Activity Recognition
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !permissionManager.hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
            setupStep(1, totalSteps, "Notifications",
                    "We need notifications to keep you updated on your timeline status.",
                    android.R.drawable.ic_dialog_info); // Changed this line to use a default built-in icon
        }
        // Step 2: Foreground Location
        else if (!permissionManager.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            setupStep(2, totalSteps, "Location Access",
                    "Timeline needs location to track your visits while the app is open.",
                    android.R.drawable.ic_menu_mylocation);
        }
    }

    private void setupStep(int currentStep, int totalSteps, String title, String subtitle, int iconRes) {
        permissionProgressBar.setProgress(currentStep, true);
        permissionStepText.setText("Step " + currentStep + " of " + totalSteps);

        permissionTitle.setText(title);
        permissionSubtitle.setText(subtitle);

        // Show Icon, Hide Video
        permissionVideoCard.setVisibility(View.GONE);
        permissionIconCard.setVisibility(View.VISIBLE);
        permissionIcon.setImageResource(iconRes);

        permissionAction.setText("Grant Access");
        permissionAction.setOnClickListener(v -> requestPermissions());
    }

    private void requestTransitions() {
        Logger.saveLog(this, TAG + ": requestTransitions called. Current transitionsRegistered: " + transitionsRegistered + ", hasAllPermissions: " + permissionManager.hasAllPermissions());
        if (!permissionManager.hasAllPermissions()) { //check if permissions are granted
            Logger.saveLog(this, TAG + ": Aborting requestTransitions: permissions not fully granted.");
            return;
        }

        if (transitionsRegistered) {
            Logger.saveLog(this, TAG + ": requestTransitions: Already registered, skipping.");
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

        intent.setAction(ActivityTransitionReceiver.ACTION_ACTIVITY_UPDATE);
        intent.setPackage(getPackageName());

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
                        Logger.saveLog(this, TAG + ": Activity transitions registered successfully");
                    })
                    .addOnFailureListener(e -> {
                        transitionsRegistered = false;
                        Logger.saveLog(this, TAG + ": Registration failed: " + e.getMessage());
                    });

            // Initial quick detection to avoid "idle" state
            ActivityRecognition.getClient(this) //TODO THIS CHUNK MIGHT BE USELESS
                    .requestActivityUpdates(60000, activityUpdatePendingIntent)
                    .addOnSuccessListener(unused -> Logger.saveLog(this, TAG + ": Initial activity updates requested"))
                    .addOnFailureListener(e -> Logger.saveLog(this, TAG + ": Failed to request initial activity updates: " + e.getMessage()));

        } catch (SecurityException e) {
            ErrorLogger.logError(this, TAG, "Error", e);
            transitionsRegistered = false;
            Logger.saveLog(this, TAG + ": Missing permission for transitions: " + e.getMessage());
            Intent PermIntent = new Intent("com.example.myapplication.PERMISSION_REVOKED").setPackage(getPackageName());
            ;
            sendBroadcast(PermIntent);
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
}