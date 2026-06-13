package com.example.myapplication.mainScreen;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.MainActivity;
import com.example.myapplication.database.PlaceDao;
import com.example.myapplication.helpers.PermissionManagerCN;
import com.example.myapplication.locationTracking.ActivityTransitionReceiver;
import com.example.myapplication.locationTracking.GeofenceManager;
import com.example.myapplication.locationTracking.LocationService;
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
import com.example.myapplication.R;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;

public class HomeFragment extends Fragment implements MainActivity.OnPermissionsGrantedListener {

    private static final String TAG = "HomeFragment";

    private static final long UPDATE_INTERVAL_MS = 5 * 60 * 1000; // 5 minutes

    private View permissionBlocker;
    private Button permissionAction;
    private TextView permissionSubtitle;
    private View headerLayout;

    private Button btnInsertExample;
    private Button btnShowFullDay;

    private ActivityDao dao;
    private PlaceDao placeDao;
    private GeofenceManager geofenceManager;

    private boolean transitionsRegistered = false;
    private boolean trackingServiceStarted = false;
    private boolean areServicesInitialized = false;

    private RecyclerView rvTimeline;
    private TimelineAdapter timelineAdapter;

    private MapManager mapManager;
    private CalendarManager calendarManager;

    private PermissionManagerCN permissionManagerCN;


    //refresh ui every 5 minutes
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshRunnable = new Runnable() {
        public void run() {
            Log.d(TAG, "ui refreshed😁:)))");
            loadTimelineData(calendarManager.getSelectedDate());
            refreshHandler.postDelayed(this, UPDATE_INTERVAL_MS);
        }
    };

    @Override
    public void onPermissionsGranted() {
        refreshPermissionUi(true);
        if (!areServicesInitialized) {
            onAllPermissionsGranted();
            areServicesInitialized = true;
        }
    }

    private void onAllPermissionsGranted() {
        requestTransitions();
        startTrackingService();
    }

    private void refreshPermissionUi(boolean hasPerms) {
        //control what you see depending on whether  you accepted permissions
        View timelineLabel = requireView().findViewById(R.id.tv_timeline_label);
        if (hasPerms) {// are permissions granted?
            permissionBlocker.setVisibility(View.GONE);
            headerLayout.setVisibility(View.VISIBLE);
            rvTimeline.setVisibility(View.VISIBLE);
            if (mapManager != null) {
                mapManager.setVisibility(View.VISIBLE);
            }
            if (timelineLabel != null) {
                timelineLabel.setVisibility(View.VISIBLE);
            }
        } else {
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
            boolean permanent = permissionManagerCN.isAnyPermissionPermanentlyDenied();
            permissionSubtitle.setText(permanent
                    ? "Permissions were denied. Please enable them in Settings to continue"
                    : "Please grant permissions to continue.");
            permissionAction.setText(permanent ? "Open Settings" : "Grant");
            permissionAction.setOnClickListener(v -> {
                if (permanent) {
                    permissionManagerCN.openAppSettings();
                } else {
                    ((MainActivity) requireActivity()).requestPermissions();
                }
            });
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        MainActivity mainActivity = (MainActivity) requireActivity();
        permissionManagerCN = mainActivity.getPermissionManager();
        mainActivity.setOnPermissionsGrantedListener(this);

        permissionBlocker = view.findViewById(R.id.permission_blocker);
        permissionAction = view.findViewById(R.id.permission_action);
        permissionSubtitle = view.findViewById(R.id.permission_subtitle);
        headerLayout = view.findViewById(R.id.header_layout);

        btnInsertExample = view.findViewById(R.id.btn_insert_example);
        btnShowFullDay = view.findViewById(R.id.btn_show_full_day);
        rvTimeline = view.findViewById(R.id.rvTimeline);

        ActivityDatabase db = ActivityDatabase.getDatabase(requireContext());
        dao = db.activityDao();
        placeDao = db.placeDao();
        geofenceManager = new GeofenceManager(requireContext());

        btnInsertExample.setOnClickListener(v -> {
            ExampleData.insertExampleDataAsync(dao);
            loadTimelineData(calendarManager.getSelectedDate());
        });

        if (btnShowFullDay != null) {
            btnShowFullDay.setOnClickListener(v -> {
                if (mapManager != null && timelineAdapter != null) {
                    mapManager.showFullDay(timelineAdapter.getItems());
                }
            });
        }

        mapManager = new MapManager(this, R.id.map);
        mapManager.init();
        View mapView = view.findViewById(R.id.map);
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

        calendarManager = new CalendarManager(view, date -> {
            loadTimelineData(date);
        });

        setupRecyclerView();
    }

    private void setupRecyclerView() {
        timelineAdapter = new TimelineAdapter();
        timelineAdapter.setOnItemClickListener(item -> {
            if (mapManager != null) {
                mapManager.focusOnItem(item);
            }
        });
        timelineAdapter.setOnLabelClickListener(this::showEditSheet);
        rvTimeline.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvTimeline.setAdapter(timelineAdapter);
    }

    private void showEditSheet(StillLocation still) {
        EditActivitySheet sheet = EditActivitySheet.newInstance(still, updatedStill -> {
            Executors.newSingleThreadExecutor().execute(() -> {
                dao.updateStillLocation(updatedStill);
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), "Visit updated", Toast.LENGTH_SHORT).show();
                    loadTimelineData(calendarManager.getSelectedDate());
                });
            });
        });
        sheet.show(getChildFragmentManager(), "PlaceLabelSheet");
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

            List<TimelineItem> rawCombined = new ArrayList<>();
            rawCombined.addAll(stills);
            rawCombined.addAll(movements);

            // Sort by start time, from earliest to latest
            Collections.sort(rawCombined, (a, b) -> {
                if (a.getStartTime() == null || b.getStartTime() == null) return 0;
                return a.getStartTime().compareTo(b.getStartTime());
            });

            // Group stops into preceding movement activities
            List<TimelineItem> processedCombined = new ArrayList<>();
            MovementActivity lastMovement = null;


            for (TimelineItem item : rawCombined) {
                if (item instanceof StillLocation) {
                    StillLocation still = (StillLocation) item;
                    // Check if this is a stop
                    if (lastMovement != null && ((still.startTimeDate != null && still.endTimeDate != null &&
                            lastMovement.startTimeDate != null && lastMovement.endTimeDate != null &&
                            still.startTimeDate.after(lastMovement.startTimeDate) &&
                            still.endTimeDate.before(lastMovement.endTimeDate)) || still.wasSupposedToBeActivity != null)) {
                        still.isStop = true;
                        lastMovement.stops.add(still);
                    } else {
                        // Not a stop, add to combined list
                        processedCombined.add(still);
                        lastMovement = null;
                    }
                } else if (item instanceof MovementActivity) {
                    lastMovement = (MovementActivity) item;
                    processedCombined.add(lastMovement);
                }
            }

            if (isAdded()) {
                requireActivity().runOnUiThread(() -> timelineAdapter.submitList(processedCombined)); // switch back to the main thread and updates the list
            }
        });
    }

    @Override
    public void onResume() {
        // checks for permissions and then loads the timeline data and resumes everything else
        super.onResume();
        boolean hasPerms = permissionManagerCN.hasAllPermissions();
        refreshPermissionUi(hasPerms);
        if (hasPerms) {
            if (!areServicesInitialized) {
                onAllPermissionsGranted();
                areServicesInitialized = true;
            }
            loadTimelineData(calendarManager.getSelectedDate());
            if (mapManager != null) {
                mapManager.onResume();
            }
            startPeriodicRefresh();
        }
    }

    @Override
    public void onPause() {
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
        if (!permissionManagerCN.hasAllPermissions()) { //check if permissions are granted
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
        Intent intent = new Intent(requireContext(), ActivityTransitionReceiver.class);


        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                requireContext(),
                0,
                intent,
                flags
        );

        // Also request regular activity updates for a quick initial detection
        PendingIntent activityUpdatePendingIntent = PendingIntent.getBroadcast(//TODO THIS CHUNK MIGHT BE USELESS
                requireContext(),
                1, // Different request code
                intent,
                flags
        );

        try {
            ActivityRecognition.getClient(requireContext())
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
            ActivityRecognition.getClient(requireContext()) //TODO THIS CHUNK MIGHT BE USELESS
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

        Intent intent = new Intent(requireContext(), LocationService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                requireContext().startForegroundService(intent);
            } else {
                requireContext().startService(intent);
            }
            trackingServiceStarted = true;
        } catch (Throwable t) {
            trackingServiceStarted = false;
            Log.e(TAG, "Failed to start tracking service", t);
        }
    }

}