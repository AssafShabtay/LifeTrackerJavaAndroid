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
import androidx.core.app.NotificationManagerCompat;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class LocationService extends Service {

    private int currentActivity = DetectedActivity.UNKNOWN;
    private FusedLocationProviderClient fusedLocationClient;
    private ActivityDao dao;
    private Long currentStillTrackingId = null;
    private final Map<Integer, Long> currentMovementTrackingIds = new HashMap<>();

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
        Log.d(TAG, "Service created");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForegroundSafe();

        if (intent != null && ActivityTransitionReceiver.ACTION_ACTIVITY_UPDATE.equals(intent.getAction())) {
            int activityType = intent.getIntExtra(ActivityTransitionReceiver.EXTRA_ACTIVITY_TYPE, DetectedActivity.UNKNOWN);
            int transitionType = intent.getIntExtra(ActivityTransitionReceiver.EXTRA_TRANSITION_TYPE, -1);
            io.execute(() -> handleActivityUpdate(activityType, transitionType));
        }
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void handleActivityUpdate(int activityType, int transitionType) {
        if (activityType == DetectedActivity.UNKNOWN) {
            Log.d(TAG, "Ignoring unknown activity update");
            return;
        }

        if (transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
            currentActivity = activityType;
            if (activityType == DetectedActivity.STILL) {
                startStillTracking();
            } else if (MOVEMENT_ACTIVITIES.contains(activityType)) {
                startMovementTracking(activityType);
            }
        } else if (transitionType == ActivityTransition.ACTIVITY_TRANSITION_EXIT) {
            if (activityType == DetectedActivity.STILL) {
                endStillTracking();
            } else if (MOVEMENT_ACTIVITIES.contains(activityType)) {
                endMovementTracking(activityType);
            }
            updateCurrentActivityAfterExit(activityType);
        } else {
            Log.w(TAG, "Ignoring unsupported transition type: " + transitionType);
            return;
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
            return Tasks.await(
                    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null),
                    15,
                    TimeUnit.SECONDS
            );
        } catch (Throwable t) {
            Log.e(TAG, "getCurrentLocation failed", t);
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
        channel.setDescription("Location Tracking");
        channel.setShowBadge(false);

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        String activityLabel = (currentActivity == DetectedActivity.UNKNOWN)
                ? "Unknown"
                : getActivityName(currentActivity);

        boolean activelyTracking = currentStillTrackingId != null || !currentMovementTrackingIds.isEmpty();
        String contentText = activelyTracking
                ? "Recording: " + activityLabel
                : "Idle • Waiting for activity updates…";

        Intent openAppIntent = new Intent(this, MainActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, openAppIntent, flags);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Tracking")
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
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification());
        } catch (Throwable t) {
            Log.w(TAG, "Failed to update notification", t);
        }
    }

    private void startStillTracking() {
        if (currentStillTrackingId != null) {
            Log.d(TAG, "Still tracking already active; ignoring duplicate enter");
            return;
        }

        Location currentLocation = getLocationOnceBlocking();
        StillLocation still = new StillLocation();
        still.lat = currentLocation != null ? currentLocation.getLatitude() : null;
        still.lng = currentLocation != null ? currentLocation.getLongitude() : null;
        still.startTimeDate = new Date();
        still.endTimeDate = null;

        try {
            currentStillTrackingId = dao.insertStillLocation(still);
        } catch (Exception e) {
            Log.e(TAG, "Failed to insert start still location", e);
            currentStillTrackingId = null;
        }
    }

    private void endStillTracking() {
        long id = currentStillTrackingId == null ? -1L : currentStillTrackingId;
        if (id <= 0L) {
            return;
        }

        Location currentLocation = getLocationOnceBlocking();
        StillLocation still;
        try {
            still = dao.getStillLocationById(id);
        } catch (Exception e) {
            Log.e(TAG, "DB read failed for still id=" + id, e);
            currentStillTrackingId = null;
            return;
        }

        if (still == null) {
            Log.e(TAG, "StillLocation missing for id=" + id);
            currentStillTrackingId = null;
            return;
        }

        Double startLat = still.lat;
        Double startLng = still.lng;
        Date startTime = still.startTimeDate;
        Date endTime = new Date();

        try {
            if (startLat != null && startLng != null && currentLocation != null) {
                String resolved = checkIfStillIsMovement(
                        startLat,
                        startLng,
                        startTime,
                        endTime,
                        currentLocation.getLatitude(),
                        currentLocation.getLongitude()
                );

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
        } catch (Exception e) {
            Log.e(TAG, "Failed to end still tracking", e);
        } finally {
            currentStillTrackingId = null;
        }
    }

    private void startMovementTracking(int activityType) {
        if (currentMovementTrackingIds.containsKey(activityType)) {
            Log.d(TAG, "Movement tracking already active for type=" + activityType);
            return;
        }

        Location currentLocation = getLocationOnceBlocking();

        MovementActivity movement = new MovementActivity();
        movement.activityType = getActivityName(activityType);
        movement.startLat = currentLocation != null ? currentLocation.getLatitude() : null;
        movement.startLng = currentLocation != null ? currentLocation.getLongitude() : null;
        movement.startTimeDate = new Date();
        movement.endTimeDate = null;

        try {
            long id = dao.insertMovementActivity(movement);
            currentMovementTrackingIds.put(activityType, id);
        } catch (Exception e) {
            Log.e(TAG, "Failed to insert start movement activity", e);
        }
    }

    private void endMovementTracking(int activityType) {
        Long id = currentMovementTrackingIds.get(activityType);
        if (id == null) {
            return;
        }

        Location currentLocation = getLocationOnceBlocking();

        try {
            dao.endMovementActivity(
                    id,
                    currentLocation != null ? currentLocation.getLatitude() : null,
                    currentLocation != null ? currentLocation.getLongitude() : null,
                    new Date()
            );
        } catch (Exception e) {
            Log.e(TAG, "Failed to end movement activity", e);
        } finally {
            currentMovementTrackingIds.remove(activityType);
        }
    }

    private String checkIfStillIsMovement(
            double startLatitude,
            double startLongitude,
            Date startTime,
            Date endTime,
            double endLatitude,
            double endLongitude
    ) {
        float distanceMeters = distanceInMeters(startLatitude, startLongitude, endLatitude, endLongitude);

        long durationMillis = endTime.getTime() - startTime.getTime();
        float durationSeconds = durationMillis > 0 ? (durationMillis / 1000f) : 0f;
        float speedMps = durationSeconds > 0 ? (distanceMeters / durationSeconds) : 0f;

        if (distanceMeters < 100f || speedMps < 0.3f) {
            return "Still";
        }
        if (speedMps < 2f) {
            return "Walking";
        }
        if (speedMps < 15f) {
            return "Running";
        }
        return "Driving";
    }

    private float distanceInMeters(double startLat, double startLon, double endLat, double endLon) {
        float[] results = new float[1];
        Location.distanceBetween(startLat, startLon, endLat, endLon, results);
        return results[0];
    }

    private String getActivityName(int activityType) {
        switch (activityType) {
            case DetectedActivity.IN_VEHICLE:
                return "Driving";
            case DetectedActivity.ON_BICYCLE:
                return "Cycling";
            case DetectedActivity.ON_FOOT:
                return "On Foot";
            case DetectedActivity.RUNNING:
                return "Running";
            case DetectedActivity.WALKING:
                return "Walking";
            case DetectedActivity.STILL:
                return "Still";
            case DetectedActivity.UNKNOWN:
            default:
                return "Unknown";
        }
    }
}
