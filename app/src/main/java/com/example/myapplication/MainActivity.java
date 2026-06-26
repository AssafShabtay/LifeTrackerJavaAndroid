package com.example.myapplication;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
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

import com.example.myapplication.helpers.PermissionManagerCN;
import com.example.myapplication.mainScreen.HomeFragment;
import com.example.myapplication.mainScreen.SettingsFragment;
import com.example.myapplication.mainScreen.StatisticsFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private PermissionManagerCN permissionManagerCN;
    private String[] foregroundPermissions;
    private OnPermissionsGrantedListener onPermissionsGrantedListener;
    private OnHomeAddressChangedListener onHomeAddressChangedListener;
    private boolean isShowingRationaleDialog = false;

    private View permissionBlocker;
    private Button permissionAction;
    private TextView permissionSubtitle;

    private final HomeFragment homeFragment = new HomeFragment();
    private final StatisticsFragment statisticsFragment = new StatisticsFragment();
    private final SettingsFragment settingsFragment = new SettingsFragment();
    private Fragment activeFragment = homeFragment;

    private static final String PREFS_NAME = "MyPrefs";
    private static final String THEME_KEY = "theme_preference";

    public interface OnPermissionsGrantedListener {
        void onPermissionsGranted();
    }

    public void setOnPermissionsGrantedListener(OnPermissionsGrantedListener onPermissionsGrantedListener) {
        this.onPermissionsGrantedListener = onPermissionsGrantedListener;
    }

    public interface OnHomeAddressChangedListener { // New interface
        void onHomeAddressChanged();
    }

    public void setOnHomeAddressChangedListener(OnHomeAddressChangedListener listener) { // Setter for new listener
        this.onHomeAddressChangedListener = listener;
    }

    public OnHomeAddressChangedListener getOnHomeAddressChangedListener() { // Getter for new listener
        return onHomeAddressChangedListener;
    }

    private final ActivityResultLauncher<String[]> foregroundPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean isAllPermissionsGranted = true;
                for (Map.Entry<String, Boolean> entry : result.entrySet()) {
                    permissionManagerCN.markPermissionRequested(entry.getKey());
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
                refreshPermissionUi(permissionManagerCN.hasAllPermissions());
            });

    private final ActivityResultLauncher<String> backgroundPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                permissionManagerCN.markPermissionRequested(Manifest.permission.ACCESS_BACKGROUND_LOCATION); // TODO FIX API LEVEL
                if (isGranted) {
                    if (onPermissionsGrantedListener != null) {
                        onPermissionsGrantedListener.onPermissionsGranted();
                    }
                } else {
                    Log.d(TAG, "Background location permission denied in callback");
                    // We don't call handlePermissionDenied() here to avoid potential rationale loops
                    // for background location. The fragment UI will show permissions are missing.
                }
                refreshPermissionUi(permissionManagerCN.hasAllPermissions());
            });

    private void handlePermissionDenied() {
        if (permissionManagerCN.shouldShowAnyPermissionRationale()) {
            isShowingRationaleDialog = true; // Set flag when showing rationale dialog
            permissionManagerCN.showPermissionRationaleDialog(() -> {
                isShowingRationaleDialog = false; // Reset flag when "Allow" is clicked
                requestPermissions(); // This will launch the system permission dialog
            }, () -> {
                isShowingRationaleDialog = false; // Reset flag if "Not now" is clicked
                Log.d(TAG, "Rationale denied, showing settings dialog");
                // If user declines rationale, send them to settings
                permissionManagerCN.showGoToSettingsDialog(permissionManagerCN::openAppSettings, () -> Log.d(TAG, "Settings dialog cancelled from rationale cancel"));
            });
        } else if (permissionManagerCN.isAnyPermissionPermanentlyDenied()) {
            permissionManagerCN.showGoToSettingsDialog(permissionManagerCN::openAppSettings, () -> Log.d(TAG, "Settings dialog cancelled"));
        }
    }

    public void requestPermissions() {
        // If we already have foreground but missing background, go straight to background check
        boolean hasForeground = true;
        for (String p : foregroundPermissions) {
            if (!permissionManagerCN.hasPermission(p)) {
                hasForeground = false;
                break;
            }
        }

        if (hasForeground) {
            checkAndRequestBackgroundLocation();
        } else {
            foregroundPermissionLauncher.launch(foregroundPermissions);
        }
    }

    private void checkAndRequestBackgroundLocation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (permissionManagerCN.hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                if (onPermissionsGrantedListener != null) {
                    onPermissionsGrantedListener.onPermissionsGranted();
                }
            } else {
                // Show a custom dialog explaining why background location is needed before showing the system/settings prompt
                new AlertDialog.Builder(this)
                        .setTitle("Background Location Access")
                        .setMessage("This app collects location data to enable timeline visits and geofencing even when the app is closed or not in use. Please select 'Allow all the time' in the next screen.")
                        .setPositiveButton("Grant", (dialog, which) -> {
                            backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
                        })
                        .setNegativeButton("No thanks", (dialog, which) -> {
                            Log.d(TAG, "User declined background location explanation");
                            // If the user declines, check if the permission is permanently denied
                            // and offer to open settings.
                            if (permissionManagerCN.isPermanentlyDenied(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                                permissionManagerCN.showGoToSettingsDialog(permissionManagerCN::openAppSettings, () -> Log.d(TAG, "Settings dialog cancelled after background rationale decline"));
                            }
                            // Otherwise, if not permanently denied, just continue without background access.
                            // The UI in HomeFragment should reflect the missing permission.
                            refreshPermissionUi(false);
                        })
                        .setCancelable(false)
                        .show();
            }
        } else {
            if (onPermissionsGrantedListener != null) {
                onPermissionsGrantedListener.onPermissionsGranted();
            }
        }
    }

    public PermissionManagerCN getPermissionManager() {
        return permissionManagerCN;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // Apply theme before super.onCreate() to ensure it's set before views are created
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean isDarkModePreferred = preferences.getBoolean(THEME_KEY, false); // Default to light mode
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

        permissionManagerCN = new PermissionManagerCN(this);

        // Filter out background location for the initial request flow
        List<String> fgList = new ArrayList<>();
        for (String p : permissionManagerCN.getRequiredPermissions()) {
            if (!p.equals(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                fgList.add(p);
            }
        }
        foregroundPermissions = fgList.toArray(new String[0]);

        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setOnNavigationItemSelectedListener(navListener);

        getSupportFragmentManager().beginTransaction().add(R.id.fragment_container, settingsFragment, "3").hide(settingsFragment).commit();
        getSupportFragmentManager().beginTransaction().add(R.id.fragment_container, statisticsFragment, "2").hide(statisticsFragment).commit();
        getSupportFragmentManager().beginTransaction().add(R.id.fragment_container, homeFragment, "1").commit();

        // Initial check and UI refresh
        refreshPermissionUi(permissionManagerCN.hasAllPermissions());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh UI state when activity resumes, in case permissions were changed in settings
        refreshPermissionUi(permissionManagerCN.hasAllPermissions());
    }

    private final BottomNavigationView.OnNavigationItemSelectedListener navListener =
            item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.nav_home) {
                    getSupportFragmentManager().beginTransaction().hide(activeFragment).show(homeFragment).commit();
                    activeFragment = homeFragment;
                } else if (itemId == R.id.nav_statistics) {
                    getSupportFragmentManager().beginTransaction().hide(activeFragment).show(statisticsFragment).commit();
                    activeFragment = statisticsFragment;
                }
                // In SettingsFragment, when the theme is toggled, we need to recreate the activity
                // to apply the theme change immediately. So, instead of just showing the fragment,
                // we'll explicitly recreate the activity if the settings fragment is being navigated to.
                else if (itemId == R.id.nav_settings) {
                    getSupportFragmentManager().beginTransaction().hide(activeFragment).show(settingsFragment).commit();
                    activeFragment = settingsFragment;
                }
                return true;
            };

    // This method can be called from SettingsFragment to recreate the activity and apply theme changes.
    public void recreateActivity() {
        recreate();
    }

    private void refreshPermissionUi(boolean hasPerms) {
        if (hasPerms) {
            permissionBlocker.setVisibility(View.GONE);
            // headerLayout.setVisibility(View.VISIBLE); // These are HomeFragment specific
            // rvTimeline.setVisibility(View.VISIBLE); // These are HomeFragment specific
            // if (mapManager != null) { // These are HomeFragment specific
            // mapManager.setVisibility(View.VISIBLE); // These are HomeFragment specific
            // }
            // if (timelineLabel != null) { // These are HomeFragment specific
            // timelineLabel.setVisibility(View.VISIBLE); // These are HomeFragment specific
            // }
        } else {
            permissionBlocker.setVisibility(View.VISIBLE);
            // headerLayout.setVisibility(View.GONE); // These are HomeFragment specific
            // rvTimeline.setVisibility(View.GONE); // These are HomeFragment specific
            // if (mapManager != null) { // These are HomeFragment specific
            // mapManager.setVisibility(View.GONE); // These are HomeFragment specific
            // }
            // if (timelineLabel != null) { // These are HomeFragment specific
            // timelineLabel.setVisibility(View.GONE); // These are HomeFragment specific
            // }

            boolean permanent = permissionManagerCN.isAnyPermissionPermanentlyDenied();
            permissionSubtitle.setText(permanent
                    ? "Permissions were denied. Please enable them in Settings to continue"
                    : "Please grant permissions to continue.");
            permissionAction.setText(permanent ? "Open Settings" : "Grant");
            permissionAction.setOnClickListener(v -> {
                if (permanent) {
                    permissionManagerCN.openAppSettings();
                } else {
                    requestPermissions();
                }
            });
        }
    }
}
