package com.example.myapplication.locationTracking;

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
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

import com.example.myapplication.MainActivity;
import com.example.myapplication.database.ActivityDao;
import com.example.myapplication.database.ActivityDatabase;
import com.example.myapplication.database.MovementActivity;
import com.example.myapplication.database.Place;
import com.example.myapplication.database.PlaceDao;
import com.example.myapplication.database.StillLocation;
import com.example.myapplication.helpers.Logger;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityTransition;
import com.google.android.gms.location.ActivityTransitionRequest;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.Tasks;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
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
    private PlaceDao placeDao;
    private GeofenceManager geofenceManager;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    // Re-register every 30 minutes to stay fresh
    private static final long RE_REGISTRATION_INTERVAL = TimeUnit.MINUTES.toMillis(30);
    // Heartbeat interval for basic activity updates
    private static final long HEARTBEAT_INTERVAL_MS = TimeUnit.MINUTES.toMillis(5);

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

    private final Runnable reRegistrationRunnable = new Runnable() {
        @Override
        public void run() {
            if (checkActivityPermission()) {
                requestTransitionsAndHeartbeat();
            }
            mainHandler.postDelayed(this, RE_REGISTRATION_INTERVAL);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        ActivityDatabase db = ActivityDatabase.getDatabase(getApplicationContext());
        dao = db.activityDao();
        placeDao = db.placeDao();
        geofenceManager = new GeofenceManager(this);

        createNotificationChannel();
        startForegroundSafe();

        io.execute(() -> {
            StillLocation activeStill = dao.getActiveStillLocation();
            if (activeStill != null) {
                currentStillTrackingId = activeStill.id;
                currentActivity = DetectedActivity.STILL;
                Log.d(TAG, "Recovered active still tracking ID: " + currentStillTrackingId);
            }
            
            for (MovementActivity m : dao.getActiveMovementActivities()) {
                int type = getActivityTypeFromName(m.activityType);
                if (type != DetectedActivity.UNKNOWN) {
                    currentMovementTrackingIds.put(type, m.id);
                    currentActivity = type;
                }
            }
            
            syncGeofences();
            updateNotificationSafe();
        });

        // Initial registration and start periodic re-registration
        mainHandler.post(reRegistrationRunnable);
    }

    private boolean checkActivityPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void requestTransitionsAndHeartbeat() {
        String logMsg = "Refreshing activity recognition (Transitions + Heartbeat)";
        Log.d(TAG, logMsg);
        Logger.saveLog(this, logMsg);

        // 1. Setup Transition Updates
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
        Intent transitionIntent = new Intent(this, ActivityTransitionReceiver.class);
        
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }
        
        PendingIntent transitionPendingIntent = PendingIntent.getBroadcast(this, 1, transitionIntent, flags);

        try {
            // Request transitions
            ActivityRecognition.getClient(this)
                    .requestActivityTransitionUpdates(request, transitionPendingIntent)
                    .addOnSuccessListener(unused -> Log.d(TAG, "Transitions refreshed successfully"))
                    .addOnFailureListener(e -> Logger.saveLog(this, "Transitions refresh failed: " + e.getMessage()));

            // 2. Request slow regular updates as a "heartbeat" to keep the engine awake
            // We use the same receiver but different intent action if needed, 
            // but the transition receiver is specifically for transitions.
            // For simple activity updates, the system sends an intent with DetectedActivity list.
            ActivityRecognition.getClient(this)
                    .requestActivityUpdates(HEARTBEAT_INTERVAL_MS, transitionPendingIntent)
                    .addOnSuccessListener(unused -> Log.d(TAG, "Activity heartbeat active"))
                    .addOnFailureListener(e -> Log.e(TAG, "Heartbeat failure", e));

        } catch (SecurityException e) {
            Log.e(TAG, "Missing permission for activity recognition", e);
        }
    }

    private void syncGeofences() {
        List<Place> places = placeDao.getAllPlacesSync();
        for (Place p : places) {
            geofenceManager.addGeofence("place_" + p.id, p.lat, p.lng, p.radius > 0 ? p.radius : 100f);
        }
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
        mainHandler.removeCallbacks(reRegistrationRunnable);
        io.shutdownNow();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ActivityTransitionReceiver.ACTION_ACTIVITY_UPDATE.equals(action)) {
                int activityType = intent.getIntExtra(ActivityTransitionReceiver.EXTRA_ACTIVITY_TYPE, DetectedActivity.UNKNOWN);
                int transitionType = intent.getIntExtra(ActivityTransitionReceiver.EXTRA_TRANSITION_TYPE, -1);
                long timestampNanos = intent.getLongExtra(ActivityTransitionReceiver.EXTRA_TIMESTAMP_NANOS, System.nanoTime());
                io.execute(() -> handleActivityUpdate(activityType, transitionType, timestampNanos));
            } else if (GeofenceBroadcastReceiver.ACTION_GEOFENCE_UPDATE.equals(action)) {
                String geofenceId = intent.getStringExtra(GeofenceBroadcastReceiver.EXTRA_GEOFENCE_ID);
                int transitionType = intent.getIntExtra(GeofenceBroadcastReceiver.EXTRA_TRANSITION_TYPE, -1);
                io.execute(() -> handleGeofenceUpdate(geofenceId, transitionType));
            } else {
                startForegroundSafe();
            }
        } else {
            startForegroundSafe();
        }
        return START_STICKY;
    }

    private void handleGeofenceUpdate(String geofenceId, int transitionType) {
        Log.d(TAG, "Geofence update: " + geofenceId + " transition: " + transitionType);
        Date now = new Date();
        
        if (transitionType == Geofence.GEOFENCE_TRANSITION_ENTER || transitionType == Geofence.GEOFENCE_TRANSITION_DWELL) {
            if (geofenceId.startsWith("place_")) {
                long placeId = Long.parseLong(geofenceId.replace("place_", ""));
                Place place = placeDao.getPlaceById(placeId);
                if (place != null) {
                    updateActiveStillWithPlace(place);
                }
            }
        } else if (transitionType == Geofence.GEOFENCE_TRANSITION_EXIT) {
            if (currentStillTrackingId != null) {
                StillLocation still = dao.getStillLocationById(currentStillTrackingId);
                if (still != null && geofenceId.equals("place_" + still.placeId)) {
                    if (currentActivity != DetectedActivity.STILL) {
                        endStillTracking(now);
                    }
                }
            }
        }
    }

    private void updateActiveStillWithPlace(Place place) {
        if (currentStillTrackingId != null) {
            StillLocation still = dao.getStillLocationById(currentStillTrackingId);
            if (still != null) {
                still.placeId = String.valueOf(place.id);
                still.placeName = place.name;
                still.placeCategory = place.category;
                still.placeAddress = place.address;
                still.lat = place.lat;
                still.lng = place.lng;
                String msg = "DB Update from updateActiveStillWithPlace: Updating still location " + still.id + " with place " + place.name;
                Log.d(TAG, msg);
                Logger.saveLog(this, msg);
                dao.updateStillLocation(still);
            }
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void handleActivityUpdate(int activityType, int transitionType, long timestampNanos) {
        if (activityType == DetectedActivity.UNKNOWN) return;

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
        if (currentActivity != exitedActivityType) return;

        if (!currentMovementTrackingIds.isEmpty()) {
            currentActivity = currentMovementTrackingIds.keySet().iterator().next();
        } else if (currentStillTrackingId != null) {
            currentActivity = DetectedActivity.STILL;
        } else {
            currentActivity = DetectedActivity.UNKNOWN;
        }
    }

    private Location getLocationOnceBlocking() {
        try {
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
            return null;
        }
    }

    private void startForegroundSafe() {
        Notification notification = buildNotification();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (Throwable ignored) {}
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Location Tracking", NotificationManager.IMPORTANCE_LOW);
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.createNotificationChannel(channel);
    }

    private Notification buildNotification() {
        String activityLabel = (currentActivity == DetectedActivity.UNKNOWN) ? "Waiting..." : getActivityName(currentActivity);
        boolean activelyTracking = currentStillTrackingId != null || !currentMovementTrackingIds.isEmpty();
        String contentText = isInitializing ? "Initializing..." : (activelyTracking ? "Tracking: " + activityLabel : "Idle • Waiting for activity");

        Intent openAppIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Timeline Tracker")
                .setContentText(contentText)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void updateNotificationSafe() {
        try {
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) manager.notify(NOTIFICATION_ID, buildNotification());
        } catch (Throwable ignored) {}
    }

    private void startStillTracking(Date startTime) {
        if (currentStillTrackingId != null) return;
        Location currentLocation = getLocationOnceBlocking();
        
        StillLocation lastStill = dao.getLastCompletedStillLocation();
        if (lastStill != null && lastStill.lat != null && lastStill.lng != null && currentLocation != null) {
            float distance = distanceInMeters(currentLocation.getLatitude(), currentLocation.getLongitude(), lastStill.lat, lastStill.lng);
            if (distance < 100f) {
                currentStillTrackingId = lastStill.id;
                String msg = "DB Update from startStillTracking: Merging with last still " + currentStillTrackingId;
                Log.d(TAG, msg);
                Logger.saveLog(this, msg);
                dao.updateStillEndTime(currentStillTrackingId, null);
                Log.d(TAG, "STILL merged with last: ID=" + currentStillTrackingId);
                return;
            }
        }

        StillLocation still = new StillLocation();
        still.lat = currentLocation != null ? currentLocation.getLatitude() : null;
        still.lng = currentLocation != null ? currentLocation.getLongitude() : null;
        still.startTimeDate = startTime;
        
        if (currentLocation != null) {
            Place nearby = findNearbyPlace(currentLocation.getLatitude(), currentLocation.getLongitude());
            if (nearby != null) {
                still.placeId = String.valueOf(nearby.id);
                still.placeName = nearby.name;
                still.placeCategory = nearby.category;
                still.placeAddress = nearby.address;
                still.lat = nearby.lat;
                still.lng = nearby.lng;
            }
        }
        
        try {
            String msg = "DB Update from startStillTracking: Inserting new still location";
            Log.d(TAG, msg);
            Logger.saveLog(this, msg);
            currentStillTrackingId = dao.insertStillLocation(still);
            Log.d(TAG, "STILL started: ID=" + currentStillTrackingId);
        } catch (Exception e) {
            currentStillTrackingId = null;
        }
    }

    private Place findNearbyPlace(double lat, double lng) {
        List<Place> places = placeDao.getAllPlacesSync();
        for (Place p : places) {
            float dist = distanceInMeters(lat, lng, p.lat, p.lng);
            if (dist < (p.radius > 0 ? p.radius : 100f)) {
                return p;
            }
        }
        return null;
    }

    private void endStillTracking(Date endTime) {
        long id = currentStillTrackingId == null ? -1L : currentStillTrackingId;
        if (id <= 0L) return;
        
        Location currentLocation = getLocationOnceBlocking();
        StillLocation still = dao.getStillLocationById(id);
        if (still == null) {
            currentStillTrackingId = null;
            return;
        }

        Double startLat = still.lat; Double startLng = still.lng; Date startTime = still.startTimeDate;

        if (startLat != null && startLng != null && currentLocation != null) {
            String resolved = checkIfStillIsMovement(startLat, startLng, startTime, endTime, currentLocation.getLatitude(), currentLocation.getLongitude());
            if ("Still".equalsIgnoreCase(resolved)) {
                String msg = "DB Update from endStillTracking: Ending still location " + id;
                Log.d(TAG, msg);
                Logger.saveLog(this, msg);
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
                String msg = "DB Update from endStillTracking: Replacing still " + id + " with movement " + resolved;
                Log.d(TAG, msg);
                Logger.saveLog(this, msg);
                dao.replaceStillWithMovement(id, movement);
            }
        } else {
            String msg = "DB Update from endStillTracking: Ending still location " + id + " (no location info)";
            Log.d(TAG, msg);
            Logger.saveLog(this, msg);
            dao.endStillLocation(id, endTime);
        }
        currentStillTrackingId = null;
        Log.d(TAG, "STILL ended: ID=" + id);
    }

    private void startMovementTracking(int activityType, Date startTime) {
        if (currentMovementTrackingIds.containsKey(activityType)) return;
        Location currentLocation = getLocationOnceBlocking();
        MovementActivity movement = new MovementActivity();
        movement.activityType = getActivityName(activityType);
        movement.startLat = currentLocation != null ? currentLocation.getLatitude() : null;
        movement.startLng = currentLocation != null ? currentLocation.getLongitude() : null;
        movement.startTimeDate = startTime;
        
        try {
            String msg = "DB Update from startMovementTracking: Inserting movement activity " + movement.activityType;
            Log.d(TAG, msg);
            Logger.saveLog(this, msg);
            long id = dao.insertMovementActivity(movement);
            currentMovementTrackingIds.put(activityType, id);
        } catch (Exception ignored) {}
    }

    private void endMovementTracking(int activityType, Date endTime) {
        Long id = currentMovementTrackingIds.get(activityType);
        if (id == null) return;
        Location currentLocation = getLocationOnceBlocking();
        
        io.execute(() -> {
            try {
                MovementActivity movement = dao.getMovementActivityById(id);
                if (movement != null && movement.startLat != null && movement.startLng != null && currentLocation != null) {
                    String resolved = checkIfStillIsMovement(movement.startLat, movement.startLng, movement.startTimeDate, endTime, currentLocation.getLatitude(), currentLocation.getLongitude());
                    
                    if ("Still".equalsIgnoreCase(resolved)) {
                        StillLocation still = new StillLocation();
                        still.lat = movement.startLat;
                        still.lng = movement.startLng;
                        still.startTimeDate = movement.startTimeDate;
                        still.endTimeDate = endTime;
                        
                        Place nearby = findNearbyPlace(still.lat, still.lng);
                        if (nearby != null) {
                            still.placeId = String.valueOf(nearby.id);
                            still.placeName = nearby.name;
                            still.placeCategory = nearby.category;
                            still.placeAddress = nearby.address;
                        }
                        
                        String msg = "DB Update from endMovementTracking: Replacing movement " + id + " with still location";
                        Log.d(TAG, msg);
                        Logger.saveLog(this, msg);
                        dao.replaceMovementWithStill(id, still);
                        Log.d(TAG, "Movement " + id + " re-classified as STILL");
                    } else {
                        String msg = "DB Update from endMovementTracking: Ending movement activity " + id;
                        Log.d(TAG, msg);
                        Logger.saveLog(this, msg);
                        dao.endMovementActivity(id, currentLocation.getLatitude(), currentLocation.getLongitude(), endTime);
                    }
                } else {
                    String msg = "DB Update from endMovementTracking: Ending movement activity " + id + " (no location info)";
                    Log.d(TAG, msg);
                    Logger.saveLog(this, msg);
                    dao.endMovementActivity(id, 
                            currentLocation != null ? currentLocation.getLatitude() : null, 
                            currentLocation != null ? currentLocation.getLongitude() : null, 
                            endTime);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error ending movement activity: " + id, e);
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
