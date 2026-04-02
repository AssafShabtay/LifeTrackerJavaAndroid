package com.example.myapplication;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

import com.example.myapplication.db.ActivityDao;
import com.example.myapplication.db.ActivityDatabase;
import com.example.myapplication.db.MovementActivity;
import com.example.myapplication.db.StillLocation;
import com.google.android.gms.location.ActivityTransition;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.Tasks;

import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class LocationService extends Service {

    private static volatile int currentActivity = DetectedActivity.UNKNOWN;
    private static volatile Long currentStillTrackingId = null;
    private static final Map<Integer, Long> currentMovementTrackingIds = new ConcurrentHashMap<>();
    private static volatile boolean isInitializing = false;

    private FusedLocationProviderClient fusedLocationClient;
    private ActivityDao dao;

    private final ExecutorService io = Executors.newSingleThreadExecutor();

    public static final int NOTIFICATION_ID = 101;
    public static final String CHANNEL_ID = "LocationServiceChannel";
    public static final String TAG = "LocationService";

    public static final Set<Integer> MOVEMENT_ACTIVITIES = new HashSet<Integer>() {{
        add(DetectedActivity.IN_VEHICLE);
        add(DetectedActivity.RUNNING);
        add(DetectedActivity.WALKING);
        add(DetectedActivity.ON_FOOT);
        add(DetectedActivity.ON_BICYCLE);
    }};

    @Override
    public void onCreate() {
        super.onCreate();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        dao = ActivityDatabase.getDatabase(getApplicationContext()).activityDao();
        createNotificationChannel();
        Log.d(TAG, "Service created. Current activity: " + currentActivity);

        // Recover active tracking state from DB on restart
        io.execute(() -> {
            StillLocation activeStill = dao.getActiveStillLocation();
            if (activeStill != null) {
                currentStillTrackingId = activeStill.id;
                currentActivity = DetectedActivity.STILL;
                Log.d(TAG, "Recovered active still tracking ID: " + currentStillTrackingId);
            }
            
            for (MovementActivity m : dao.getActiveMovementActivities()) {
                // Approximate the activity type from the name
                int type = getActivityTypeFromName(m.activityType);
                if (type != DetectedActivity.UNKNOWN) {
                    currentMovementTrackingIds.put(type, m.id);
                    currentActivity = type;
                    Log.d(TAG, "Recovered active movement tracking ID: " + m.id + " for type: " + m.activityType);
                }
            }
            updateNotificationSafe();
        });
    }

    private int getActivityTypeFromName(String name) {
        if (name == null) return DetectedActivity.UNKNOWN;
        switch (name) {
            case "Driving": return DetectedActivity.IN_VEHICLE;
            case "Cycling": return DetectedActivity.ON_BICYCLE;
            case "Running": return DetectedActivity.RUNNING;
            case "Walking": return DetectedActivity.WALKING;
            case "On Foot": return DetectedActivity.ON_FOOT;
            case "Still": return DetectedActivity.STILL;
            default: return DetectedActivity.UNKNOWN;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ActivityTransitionReceiver.ACTION_ACTIVITY_UPDATE.equals(intent.getAction())) {
            int activityType = intent.getIntExtra(ActivityTransitionReceiver.EXTRA_ACTIVITY_TYPE, DetectedActivity.UNKNOWN);
            int transitionType = intent.getIntExtra(ActivityTransitionReceiver.EXTRA_TRANSITION_TYPE, -1);
            long timestampNanos = intent.getLongExtra(ActivityTransitionReceiver.EXTRA_TIMESTAMP_NANOS, System.nanoTime());
            Log.d(TAG, "Received activity update: " + activityType + " transition: " + transitionType);
            io.execute(() -> handleActivityUpdate(activityType, transitionType, timestampNanos));
        } else {
            startForegroundSafe();
        }
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void handleActivityUpdate(int activityType, int transitionType, long timestampNanos) {
        if (activityType == DetectedActivity.UNKNOWN) {
            return;
        }

        // Calculate event time based on elapsed realtime nanos to be accurate
        Date eventTime = new Date(System.currentTimeMillis() - TimeUnit.NANOSECONDS.toMillis(android.os.SystemClock.elapsedRealtimeNanos() - timestampNanos));

        if (transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
            currentActivity = activityType;
            isInitializing = true;
            updateNotificationSafe();

            if (activityType == DetectedActivity.STILL) {
                startStillTracking(eventTime);
            } else if (MOVEMENT_ACTIVITIES.contains(activityType)) {
                startMovementTracking(activityType, eventTime);
            }
            isInitializing = false;
        } else if (transitionType == ActivityTransition.ACTIVITY_TRANSITION_EXIT) {
            Log.d(TAG, "EXIT detected for: " + activityType);
            if (activityType == DetectedActivity.STILL) {
                endStillTracking(eventTime);
            } else if (MOVEMENT_ACTIVITIES.contains(activityType)) {
                endMovementTracking(activityType, eventTime);
            }
            updateCurrentActivityAfterExit(activityType);
        }

        updateNotificationSafe();
    }

    private void updateCurrentActivityAfterExit(int exitedActivityType) {
        if (currentActivity != exitedActivityType) {
            return;
        }

        if (!currentMovementTrackingIds.isEmpty()) {
            for (Integer activeMovementType : currentMovementTrackingIds.keySet()) {
                currentActivity = activeMovementType;
                return;
            }
        }

        if (currentStillTrackingId != null) {
            currentActivity = DetectedActivity.STILL;
        } else {
            currentActivity = DetectedActivity.UNKNOWN;
        }
    }

    private Location getLocationOnceBlocking() {
        try {
            Log.d(TAG, "Requesting high accuracy location...");
            Location loc = Tasks.await(
                    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null),
                    10,
                    TimeUnit.SECONDS
            );
            if (loc == null) {
                loc = Tasks.await(fusedLocationClient.getLastLocation(), 2, TimeUnit.SECONDS);
            }
            return loc;
        } catch (Throwable t) {
            Log.e(TAG, "Location request failed", t);
            return null;
        }
    }

    private void startForegroundSafe() {
        Notification notification = buildNotification();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                        this,
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                );
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Failed to start foreground service", t);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Location Tracking",
                NotificationManager.IMPORTANCE_LOW
        );
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        String activityLabel = (currentActivity == DetectedActivity.UNKNOWN) ? "Waiting..." : getActivityName(currentActivity);
        boolean activelyTracking = currentStillTrackingId != null || !currentMovementTrackingIds.isEmpty();
        
        String contentText = isInitializing ? "Initializing..." : (activelyTracking ? "Tracking: " + activityLabel : "Idle • Waiting for activity");

        Intent openAppIntent = new Intent(this, MainActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, openAppIntent, flags);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Activity Tracker")
                .setContentText(contentText)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void updateNotificationSafe() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        try {
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) manager.notify(NOTIFICATION_ID, buildNotification());
        } catch (Throwable ignored) {}
    }

    private void startStillTracking(Date startTime) {
        if (currentStillTrackingId != null) return;
        Location currentLocation = getLocationOnceBlocking();
        StillLocation still = new StillLocation();
        still.lat = currentLocation != null ? currentLocation.getLatitude() : null;
        still.lng = currentLocation != null ? currentLocation.getLongitude() : null;
        still.startTimeDate = startTime;
        still.endTimeDate = null;
        try {
            currentStillTrackingId = dao.insertStillLocation(still);
            Log.d(TAG, "STILL started: ID=" + currentStillTrackingId);
        } catch (Exception e) {
            currentStillTrackingId = null;
        }
    }

    private void endStillTracking(Date endTime) {
        long id = currentStillTrackingId == null ? -1L : currentStillTrackingId;
        if (id <= 0L) return;
        Location currentLocation = getLocationOnceBlocking();
        StillLocation still;
        try {
            still = dao.getStillLocationById(id);
        } catch (Exception e) {
            currentStillTrackingId = null;
            return;
        }
        if (still == null) {
            currentStillTrackingId = null;
            return;
        }
        Double startLat = still.lat;
        Double startLng = still.lng;
        Date startTime = still.startTimeDate;
        try {
            if (startLat != null && startLng != null && currentLocation != null) {
                String resolved = checkIfStillIsMovement(startLat, startLng, startTime, endTime, currentLocation.getLatitude(), currentLocation.getLongitude());
                if ("Still".equalsIgnoreCase(resolved)) {
                    dao.endStillLocation(id, endTime);
                } else {
                    MovementActivity movement = new MovementActivity();
                    movement.activityType = resolved;
                    movement.startLat = startLat;
                    movement.startLng = startLng;
                    movement.endLat = currentLocation.getLatitude();
                    movement.endLng = currentLocation.getLongitude();
                    movement.startTimeDate = startTime;
                    movement.endTimeDate = endTime;
                    dao.replaceStillWithMovement(id, movement);
                }
            } else {
                dao.endStillLocation(id, endTime);
            }
            Log.d(TAG, "STILL ended: ID=" + id);
        } catch (Exception ignored) {
        } finally {
            currentStillTrackingId = null;
        }
    }

    private void startMovementTracking(int activityType, Date startTime) {
        if (currentMovementTrackingIds.containsKey(activityType)) return;
        Location currentLocation = getLocationOnceBlocking();
        MovementActivity movement = new MovementActivity();
        movement.activityType = getActivityName(activityType);
        movement.startLat = currentLocation != null ? currentLocation.getLatitude() : null;
        movement.startLng = currentLocation != null ? currentLocation.getLongitude() : null;
        movement.startTimeDate = startTime;
        movement.endTimeDate = null;
        try {
            long id = dao.insertMovementActivity(movement);
            currentMovementTrackingIds.put(activityType, id);
            Log.d(TAG, "MOVEMENT started: " + movement.activityType + " ID=" + id);
        } catch (Exception ignored) {}
    }

    private void endMovementTracking(int activityType, Date endTime) {
        Long id = currentMovementTrackingIds.get(activityType);
        if (id == null) return;
        Location currentLocation = getLocationOnceBlocking();
        
        io.execute(() -> {
            MovementActivity movement = dao.getMovementActivityById(id);
            if (movement == null) {
                currentMovementTrackingIds.remove(activityType);
                return;
            }

            Double startLat = movement.startLat;
            Double startLng = movement.startLng;
            Date startTime = movement.startTimeDate;

            try {
                if (startLat != null && startLng != null && currentLocation != null) {
                    String resolved = checkIfStillIsMovement(startLat, startLng, startTime, endTime, currentLocation.getLatitude(), currentLocation.getLongitude());
                    if ("Still".equalsIgnoreCase(resolved)) {
                        StillLocation still = new StillLocation();
                        still.lat = startLat;
                        still.lng = startLng;
                        still.startTimeDate = startTime;
                        still.endTimeDate = endTime;
                        dao.replaceMovementWithStill(id, still);
                        Log.d(TAG, "MOVEMENT resolved as STILL: ID=" + id);
                    } else {
                        // It's still a movement, but we keep the original detected type
                        dao.endMovementActivity(id, currentLocation.getLatitude(), currentLocation.getLongitude(), endTime);
                        Log.d(TAG, "MOVEMENT ended normally: ID=" + id);
                    }
                } else {
                    dao.endMovementActivity(id, currentLocation != null ? currentLocation.getLatitude() : null, currentLocation != null ? currentLocation.getLongitude() : null, endTime);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error ending movement tracking", e);
            } finally {
                currentMovementTrackingIds.remove(activityType);
            }
        });
    }

    private String checkIfStillIsMovement(double startLat, double startLng, Date startTime, Date endTime, double endLat, double endLng) {
        float distance = distanceInMeters(startLat, startLng, endLat, endLng);
        long durationMs = endTime.getTime() - startTime.getTime();
        float durationSec = durationMs / 1000f;
        float speed = durationSec > 5 ? (distance / durationSec) : 0f;
        if (distance < 50f || (distance < 150f && speed < 0.5f)) return "Still";
        if (speed < 2.5f) return "Walking";
        if (speed < 8f) return "Running";
        return "Driving";
    }

    private float distanceInMeters(double startLat, double startLon, double endLat, double endLon) {
        float[] results = new float[1];
        Location.distanceBetween(startLat, startLon, endLat, endLon, results);
        return results[0];
    }

    private String getActivityName(int activityType) {
        switch (activityType) {
            case DetectedActivity.IN_VEHICLE: return "Driving";
            case DetectedActivity.ON_BICYCLE: return "Cycling";
            case DetectedActivity.ON_FOOT: return "On Foot";
            case DetectedActivity.RUNNING: return "Running";
            case DetectedActivity.WALKING: return "Walking";
            case DetectedActivity.STILL: return "Still";
            default: return "Unknown";
        }
    }
}
