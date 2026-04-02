package com.example.myapplication;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.helpers.PermissionManager;
import com.example.myapplication.locationTracking.ActivityTransitionReceiver;
import com.example.myapplication.locationTracking.LocationService;
import com.example.myapplication.mainScreen.CalendarManager;
import com.example.myapplication.mainScreen.MapManager;
import com.example.myapplication.mainScreen.TimelineAdapter;
import com.example.myapplication.database.ActivityDao;
import com.example.myapplication.database.ActivityDatabase;
import com.example.myapplication.database.MovementActivity;
import com.example.myapplication.database.StillLocation;
import com.example.myapplication.database.TimelineItem;
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

    private static final long UPDATE_INTERVAL_MS = 5 * 60 * 1000; // 5 minutes

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

    private PermissionManager permissionManager;


    //refresh ui every 5 minutes
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshRunnable = new Runnable() {
        public void run() {
            Log.d(TAG, "ui refreshed😁:)))");
            loadTimelineData(calendarManager.getSelectedDate());
            refreshHandler.postDelayed(this, UPDATE_INTERVAL_MS);
        }
    };
    //Permissions handling
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

                refreshPermissionUi(allGranted);

                if (allGranted) {
                    onAllPermissionsGranted();
                    return;
                }

                if (permissionManager.shouldShowAnyPermissionRationale()) {
                    permissionManager.showPermissionRationaleDialog(this::requestPermissions, () -> Log.d(TAG, "Permissions denied"));
                } else if (permissionManager.isAnyPermissionPermanentlyDenied()) {
                    permissionManager.showGoToSettingsDialog(permissionManager::openAppSettings, () -> Log.d(TAG, "Permissions denied"));
                }
            });
    private void requestPermissions() {
        permissionLauncher.launch(requiredPermissions);
    }

    private void onAllPermissionsGranted() {
        requestTransitions();
        startTrackingService();
    }

    private void refreshPermissionUi(boolean hasPerms) {
        //control what you see depending on whether  you accepted permissions
        View timelineLabel = findViewById(R.id.tv_timeline_label);
        if(hasPerms){// are permissions granted?
            permissionBlocker.setVisibility(View.GONE);
            headerLayout.setVisibility(View.VISIBLE);
            rvTimeline.setVisibility(View.VISIBLE);
            if (mapManager != null) {
                mapManager.setVisibility(View.VISIBLE);
            }
            if (timelineLabel != null) {
                timelineLabel.setVisibility(View.VISIBLE);
            }
        }
        else{
            permissionBlocker.setVisibility(View.VISIBLE);
            headerLayout.setVisibility(View.GONE);
            rvTimeline.setVisibility(View.GONE);
            if (mapManager != null) {
                mapManager.setVisibility(View.GONE);
            }
            if (timelineLabel != null) {
                timelineLabel.setVisibility(View.GONE);
            }

            //Ask for permissions again
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

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        requiredPermissions = PermissionManager.buildRequiredPermissions();
        permissionManager = new PermissionManager(this, requiredPermissions);

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

        mapManager = new MapManager(this, R.id.map);
        mapManager.init();
        View mapView = findViewById(R.id.map);
        if (mapView != null) {
            final float[] downX = new float[1];
            final float[] downY = new float[1];
            final int CLICK_THRESHOLD = 10;

            mapView.setOnTouchListener((v, event) -> {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        downX[0] = event.getX();
                        downY[0] = event.getY();
                        v.getParent().requestDisallowInterceptTouchEvent(true);
                        break;

                    case MotionEvent.ACTION_MOVE:
                        v.getParent().requestDisallowInterceptTouchEvent(true);
                        break;

                    case MotionEvent.ACTION_UP:
                        v.getParent().requestDisallowInterceptTouchEvent(false);

                        float dx = Math.abs(event.getX() - downX[0]);
                        float dy = Math.abs(event.getY() - downY[0]);
                        if (dx < CLICK_THRESHOLD && dy < CLICK_THRESHOLD) {
                            v.performClick();
                        }
                        break;

                    case MotionEvent.ACTION_CANCEL:
                        v.getParent().requestDisallowInterceptTouchEvent(false);
                        break;
                }
                return false;
            });
        }

        calendarManager = new CalendarManager(findViewById(android.R.id.content), date -> {
            loadTimelineData(date);
        });

        setupRecyclerView();
        loadTimelineData(calendarManager.getSelectedDate());

        boolean hasPerms = permissionManager.hasAllPermissions();
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
        if (date == null) return;
        //get start of the day
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date start = cal.getTime();
        //get end of the day
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 999);
        Date end = cal.getTime();

        Executors.newSingleThreadExecutor().execute(() -> { // runs on a background thread
            List<StillLocation> stills = dao.getStillForRange(start, end);
            List<MovementActivity> movements = dao.getMovementForRange(start, end);

            List<TimelineItem> combined = new ArrayList<>();
            combined.addAll(stills);
            combined.addAll(movements);

            // Sort by start time, from earliest to latest
            Collections.sort(combined, (a, b) -> {
                if (a.getStartTime() == null || b.getStartTime() == null) return 0; // If either item has no start time, treat them as equal
                return a.getStartTime().compareTo(b.getStartTime());
            });

            runOnUiThread(() -> timelineAdapter.submitList(combined)); // switch back to the main thread and updates the list
        });
    }


    protected void onResume() {
        // checks for permissions and then loads the timeline data and resumes everything else
        super.onResume();
        boolean hasPerms = permissionManager.hasAllPermissions();
        refreshPermissionUi(hasPerms);
        if (hasPerms) {
            onAllPermissionsGranted();
            loadTimelineData(calendarManager.getSelectedDate());
            if (mapManager != null) {
                mapManager.onResume();
            }
            startPeriodicRefresh();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopPeriodicRefresh();
    }

    private void startPeriodicRefresh() {
        refreshHandler.removeCallbacks(refreshRunnable);
        refreshHandler.postDelayed(refreshRunnable, UPDATE_INTERVAL_MS);
    }

    private void stopPeriodicRefresh() {
        refreshHandler.removeCallbacks(refreshRunnable);
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
