package com.example.myapplication;

import android.Manifest;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.databaseviewer.TimelineAdapter;
import com.example.myapplication.db.ActivityDao;
import com.example.myapplication.db.ActivityDatabase;
import com.example.myapplication.db.MovementActivity;
import com.example.myapplication.db.StillLocation;
import com.example.myapplication.db.TimelineItem;
import com.example.myapplication.helpers.ExampleData;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityTransition;
import com.google.android.gms.location.ActivityTransitionRequest;
import com.google.android.gms.location.DetectedActivity;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String PREFS_NAME = "permission_prefs";
    private static final String KEY_PERMISSION_REQUESTED_PREFIX = "requested_";

    private View permissionBlocker;
    private Button permissionAction;
    private TextView permissionSubtitle;
    private View headerLayout;

    private Button btnInsertExample;

    private String[] requiredPermissions;
    private ActivityDao dao;
    private boolean transitionsRegistered = false;
    private boolean trackingServiceStarted = false;

    private RecyclerView rvTimeline;
    private TimelineAdapter timelineAdapter;

    private MapManager mapManager;
    private CalendarManager calendarManager;

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean allGranted = true;
                for (String permission : requiredPermissions) {
                    markPermissionRequested(permission);
                    Boolean granted = result.get(permission);
                    if (granted == null || !granted) {
                        allGranted = false;
                    }
                }

                refreshPermissionUi(allGranted);

                if (allGranted) {
                    onAllPermissionsGranted();
                    return;
                }

                if (shouldShowAnyPermissionRationale()) {
                    showPermissionRationaleDialog(this::requestPermissions, () -> Log.d(TAG, "Permissions denied"));
                } else if (isAnyPermissionPermanentlyDenied()) {
                    showGoToSettingsDialog(this::openAppSettings, () -> Log.d(TAG, "Permissions denied"));
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        requiredPermissions = buildRequiredPermissions();

        permissionBlocker = findViewById(R.id.permission_blocker);
        permissionAction = findViewById(R.id.permission_action);
        permissionSubtitle = findViewById(R.id.permission_subtitle);
        headerLayout = findViewById(R.id.header_layout);

        btnInsertExample = findViewById(R.id.btn_insert_example);
        rvTimeline = findViewById(R.id.rvTimeline);

        dao = ActivityDatabase.getDatabase(getApplicationContext()).activityDao();

        btnInsertExample.setOnClickListener(v -> {
            ExampleData.insertExampleDataAsync(dao);
            loadTimelineData(calendarManager.getSelectedDate());
        });

        // Initialize Map
        mapManager = new MapManager(this, R.id.map);
        mapManager.init();

        // Initialize Calendar
        calendarManager = new CalendarManager(findViewById(android.R.id.content), date -> {
            loadTimelineData(date);
        });

        setupRecyclerView();
        loadTimelineData(calendarManager.getSelectedDate());

        boolean hasPerms = hasAllPermissions();
        refreshPermissionUi(hasPerms);
        if (!hasPerms) {
            requestPermissions();
        } else {
            onAllPermissionsGranted();
        }
    }

    private void setupRecyclerView() {
        timelineAdapter = new TimelineAdapter();
        timelineAdapter.setOnItemClickListener(item -> {
            if (mapManager != null) {
                mapManager.focusOnItem(item);
            }
        });
        rvTimeline.setLayoutManager(new LinearLayoutManager(this));
        rvTimeline.setAdapter(timelineAdapter);
    }

    private void loadTimelineData(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date start = cal.getTime();

        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 999);
        Date end = cal.getTime();

        Executors.newSingleThreadExecutor().execute(() -> {
            List<StillLocation> stills = dao.getStillForRange(start, end);
            List<MovementActivity> movements = dao.getMovementForRange(start, end);

            List<TimelineItem> combined = new ArrayList<>();
            combined.addAll(stills);
            combined.addAll(movements);

            // Sort by start time descending (newest first)
            Collections.sort(combined, (a, b) -> {
                if (a.getStartTime() == null || b.getStartTime() == null) return 0;
                return b.getStartTime().compareTo(a.getStartTime());
            });

            runOnUiThread(() -> timelineAdapter.submitList(combined));
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean hasPerms = hasAllPermissions();
        refreshPermissionUi(hasPerms);
        if (hasPerms) {
            onAllPermissionsGranted();
            loadTimelineData(calendarManager.getSelectedDate());
            if (mapManager != null) {
                mapManager.onResume();
            }
        }
    }

    private void onAllPermissionsGranted() {
        requestTransitions();
        startTrackingService();
    }

    private String[] buildRequiredPermissions() {
        ArrayList<String> perms = new ArrayList<>();
        perms.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        perms.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            perms.add(Manifest.permission.ACTIVITY_RECOGNITION);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        return perms.toArray(new String[0]);
    }

    private void requestPermissions() {
        permissionLauncher.launch(requiredPermissions);
    }

    private boolean hasAllPermissions() {
        for (String permission : requiredPermissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private SharedPreferences getPermissionPrefs() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
    }

    private void markPermissionRequested(String permission) {
        getPermissionPrefs().edit().putBoolean(KEY_PERMISSION_REQUESTED_PREFIX + permission, true).apply();
    }

    private boolean wasPermissionRequested(String permission) {
        return getPermissionPrefs().getBoolean(KEY_PERMISSION_REQUESTED_PREFIX + permission, false);
    }

    private boolean shouldShowAnyPermissionRationale() {
        for (String permission : requiredPermissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
                    && shouldShowRequestPermissionRationale(permission)) {
                return true;
            }
        }
        return false;
    }

    private boolean isPermanentlyDenied(String permission) {
        boolean denied = ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED;
        boolean noRationale = !shouldShowRequestPermissionRationale(permission);
        return denied && wasPermissionRequested(permission) && noRationale;
    }

    private boolean isAnyPermissionPermanentlyDenied() {
        for (String permission : requiredPermissions) {
            if (isPermanentlyDenied(permission)) {
                return true;
            }
        }
        return false;
    }

    private void refreshPermissionUi(boolean hasPerms) {
        permissionBlocker.setVisibility(hasPerms ? View.GONE : View.VISIBLE);
        headerLayout.setVisibility(hasPerms ? View.VISIBLE : View.GONE);
        rvTimeline.setVisibility(hasPerms ? View.VISIBLE : View.GONE);
        
        if (mapManager != null) {
            mapManager.setVisibility(hasPerms ? View.VISIBLE : View.GONE);
        }
        
        View timelineLabel = findViewById(R.id.tv_timeline_label);
        if (timelineLabel != null) {
            timelineLabel.setVisibility(hasPerms ? View.VISIBLE : View.GONE);
        }

        if (!hasPerms) {
            boolean permanent = isAnyPermissionPermanentlyDenied();
            permissionSubtitle.setText(permanent
                    ? "Permissions are permanently denied. Enable them in Settings."
                    : "Please grant permissions to continue.");
            permissionAction.setText(permanent ? "Open Settings" : "Grant");
            permissionAction.setOnClickListener(v -> {
                if (permanent) {
                    openAppSettings();
                } else {
                    requestPermissions();
                }
            });
        }
    }

    private void showPermissionRationaleDialog(Runnable onRetry, Runnable onCancel) {
        new AlertDialog.Builder(this)
                .setTitle("Permission required")
                .setMessage("We need these permissions to use the app.")
                .setPositiveButton("Allow", (d, w) -> onRetry.run())
                .setNegativeButton("Not now", (d, w) -> onCancel.run())
                .show();
    }

    private void showGoToSettingsDialog(Runnable onOpenSettings, Runnable onCancel) {
        new AlertDialog.Builder(this)
                .setTitle("Enable permissions in Settings")
                .setMessage("Permissions are denied permanently. Please enable them in Settings to continue.")
                .setPositiveButton("Open Settings", (d, w) -> onOpenSettings.run())
                .setNegativeButton("Cancel", (d, w) -> onCancel.run())
                .show();
    }

    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getPackageName(), null));
        startActivity(intent);
    }

    private void requestTransitions() {
        if (!hasAllPermissions()) {
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
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        try {
            ActivityRecognition.getClient(this)
                    .requestActivityTransitionUpdates(request, pendingIntent)
                    .addOnSuccessListener(unused -> transitionsRegistered = true)
                    .addOnFailureListener(e -> {
                        transitionsRegistered = false;
                        Log.e(TAG, "Registration failed", e);
                    });
        } catch (SecurityException e) {
            transitionsRegistered = false;
            Log.e(TAG, "SecurityException: missing permission for transitions", e);
        }
    }

    private void startTrackingService() {
        if (trackingServiceStarted) {
            return;
        }

        Intent intent = new Intent(this, LocationService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
            trackingServiceStarted = true;
        } catch (Throwable t) {
            trackingServiceStarted = false;
            Log.e(TAG, "Failed to start tracking service", t);
        }
    }

}
