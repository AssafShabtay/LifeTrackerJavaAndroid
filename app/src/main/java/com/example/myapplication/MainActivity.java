package com.example.myapplication;

import android.Manifest;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
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
    private boolean isShowingRationaleDialog = false; // Re-introduce flag to prevent re-entrance

    private final HomeFragment homeFragment = new HomeFragment();
    private final StatisticsFragment statisticsFragment = new StatisticsFragment();
    private final SettingsFragment settingsFragment = new SettingsFragment();
    private Fragment activeFragment = homeFragment;

    public interface OnPermissionsGrantedListener {
        void onPermissionsGranted();
    }

    public void setOnPermissionsGrantedListener(OnPermissionsGrantedListener onPermissionsGrantedListener) {
        this.onPermissionsGrantedListener = onPermissionsGrantedListener;
    }

    private final ActivityResultLauncher<String[]> foregroundPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean allForegroundGranted = true;
                for (Map.Entry<String, Boolean> entry : result.entrySet()) {
                    permissionManagerCN.markPermissionRequested(entry.getKey());
                    if (!entry.getValue()) {
                        allForegroundGranted = false;
                    }
                }

                if (allForegroundGranted) {
                    checkAndRequestBackgroundLocation();
                } else {
                    // Only call handlePermissionDenied if we are not currently showing a rationale dialog
                    // to avoid re-entering the loop prematurely.
                    if (!isShowingRationaleDialog) {
                        handlePermissionDenied();
                    }
                }
            });

    private final ActivityResultLauncher<String> backgroundPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                permissionManagerCN.markPermissionRequested(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
                if (isGranted) {
                    if (onPermissionsGrantedListener != null) {
                        onPermissionsGrantedListener.onPermissionsGranted();
                    }
                } else {
                    Log.d(TAG, "Background location permission denied in callback");
                    // We don't call handlePermissionDenied() here to avoid potential rationale loops
                    // for background location. The fragment UI will show permissions are missing.
                }
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
                new android.app.AlertDialog.Builder(this)
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
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        String[] allRequired = PermissionManagerCN.buildRequiredPermissions();
        permissionManagerCN = new PermissionManagerCN(this, allRequired);

        // Filter out background location for the initial request flow
        List<String> fgList = new ArrayList<>();
        for (String p : allRequired) {
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
                } else if (itemId == R.id.nav_settings) {
                    getSupportFragmentManager().beginTransaction().hide(activeFragment).show(settingsFragment).commit();
                    activeFragment = settingsFragment;
                }
                return true;
            };
}