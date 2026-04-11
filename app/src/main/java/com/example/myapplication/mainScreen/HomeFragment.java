package com.example.myapplication.mainScreen;

import android.app.AlertDialog;
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
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.MainActivity;
import com.example.myapplication.database.Place;
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
        rvTimeline = view.findViewById(R.id.rvTimeline);

        ActivityDatabase db = ActivityDatabase.getDatabase(requireContext());
        dao = db.activityDao();
        placeDao = db.placeDao();
        geofenceManager = new GeofenceManager(requireContext());

        btnInsertExample.setOnClickListener(v -> {
            ExampleData.insertExampleDataAsync(dao);
            loadTimelineData(calendarManager.getSelectedDate());
        });

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
        timelineAdapter.setOnLabelClickListener(still -> {
            PlaceLabelSheet sheet = PlaceLabelSheet.newInstance(still, this::savePlaceAndRegisterGeofence);
            sheet.show(getChildFragmentManager(), "PlaceLabelSheet");
        });
        rvTimeline.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvTimeline.setAdapter(timelineAdapter);
    }

    private void showLabelDialog(StillLocation still) {
        String[] options = {"Home", "Work", "Gym", "School", "Custom..."};
        new AlertDialog.Builder(requireContext())
                .setTitle("Label this Place")
                .setItems(options, (dialog, which) -> {
                    String selected = options[which];
                    if (selected.equals("Custom...")) {
                        showCustomLabelInput(still);
                    } else {
                        savePlaceAndRegisterGeofence(still, selected, selected);
                    }
                })
                .show();
    }

    private void showCustomLabelInput(StillLocation still) {
        EditText input = new EditText(requireContext());
        input.setHint("Enter place name");
        new AlertDialog.Builder(requireContext())
                .setTitle("Custom Label")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        savePlaceAndRegisterGeofence(still, name, "Other");
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void savePlaceAndRegisterGeofence(StillLocation still, String name, String category) {
        if (still.lat == null || still.lng == null) {
            Toast.makeText(requireContext(), "Cannot label a place without coordinates", Toast.LENGTH_SHORT).show();
            return;
        }

        Executors.newSingleThreadExecutor().execute(() -> {
            // 1. Create and save the Place
            Place place = new Place();
            place.name = name;
            place.category = category;
            place.lat = still.lat;
            place.lng = still.lng;
            place.radius = 100f; // Default 100m radius
            
            long placeId = placeDao.insertPlace(place);
            
            // 2. Update the StillLocation record
            still.placeId = String.valueOf(placeId);
            still.placeName = name;
            still.placeCategory = category;
            dao.updateStillLocation(still);
            
            // 3. Register the geofence
            geofenceManager.addGeofence("place_" + placeId, place.lat, place.lng, place.radius);
            
            requireActivity().runOnUiThread(() -> {
                Toast.makeText(requireContext(), "Saved " + name + " and added geofence", Toast.LENGTH_SHORT).show();
                loadTimelineData(calendarManager.getSelectedDate());
                
                // Also notify the service to refresh its geofence list if it's running
                Intent intent = new Intent(requireContext(), LocationService.class);
                requireContext().startService(intent); 
            });
        });
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

            if(isAdded()) {
                requireActivity().runOnUiThread(() -> timelineAdapter.submitList(combined)); // switch back to the main thread and updates the list
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
