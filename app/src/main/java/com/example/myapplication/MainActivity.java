package com.example.myapplication;

import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.example.myapplication.helpers.PermissionManager;
import com.example.myapplication.mainScreen.HomeFragment;
import com.example.myapplication.mainScreen.StatisticsFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private PermissionManager permissionManager;
    private String[] requiredPermissions;
    private OnPermissionsGrantedListener onPermissionsGrantedListener;

    private final HomeFragment homeFragment = new HomeFragment();
    private final StatisticsFragment statisticsFragment = new StatisticsFragment();
    private Fragment activeFragment = homeFragment;

    public interface OnPermissionsGrantedListener {
        void onPermissionsGranted();
    }

    public void setOnPermissionsGrantedListener(OnPermissionsGrantedListener onPermissionsGrantedListener) {
        this.onPermissionsGrantedListener = onPermissionsGrantedListener;
    }

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean allGranted = true;
                for (String permission : requiredPermissions) {
                    permissionManager.markPermissionRequested(permission);
                    Boolean granted = result.get(permission);
                    if (granted == null || !granted) {
                        allGranted = false;
                    }
                }

                if (allGranted) {
                    if (onPermissionsGrantedListener != null) {
                        onPermissionsGrantedListener.onPermissionsGranted();
                    }
                } else {
                    if (permissionManager.shouldShowAnyPermissionRationale()) {
                        permissionManager.showPermissionRationaleDialog(this::requestPermissions, () -> Log.d(TAG, "Permissions denied"));
                    } else if (permissionManager.isAnyPermissionPermanentlyDenied()) {
                        permissionManager.showGoToSettingsDialog(permissionManager::openAppSettings, () -> Log.d(TAG, "Permissions denied"));
                    }
                }
            });

    public void requestPermissions() {
        permissionLauncher.launch(requiredPermissions);
    }

    public PermissionManager getPermissionManager() {
        return permissionManager;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        requiredPermissions = PermissionManager.buildRequiredPermissions();
        permissionManager = new PermissionManager(this, requiredPermissions);

        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setOnNavigationItemSelectedListener(navListener);

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
                }
                return true;
            };
}