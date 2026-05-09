package com.example.myapplication.locationTracking;

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
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;

import com.example.myapplication.MainActivity;
import com.example.myapplication.database.ActivityDao;
import com.example.myapplication.database.ActivityDatabase;
import com.example.myapplication.database.MovementActivity;
import com.example.myapplication.database.Place;
import com.example.myapplication.database.PlaceDao;
import com.example.myapplication.database.RoutePoint;
import com.example.myapplication.database.StillLocation;
import com.example.myapplication.helpers.Logger;
import com.google.android.gms.location.ActivityTransition;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.Tasks;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.PlaceLikelihood;
import com.google.android.libraries.places.api.net.FindCurrentPlaceRequest;
import com.google.android.libraries.places.api.net.FindCurrentPlaceResponse;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.android.libraries.places.api.model.Place.Field;


import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import com.example.myapplication.BuildConfig;

public class LocationService extends Service {

    private static volatile int currentActivityType = DetectedActivity.UNKNOWN;
    private static volatile Long currentStillTrackingId = null;
    private static final Map<Integer, Long> currentMovementTrackingIds = new ConcurrentHashMap<>(); // is list because of the possibly of that android thinks two activities are ongoing
    private static volatile boolean isInitializing = false;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback routeLocationCallback;
    private ActivityDao dao;
    private PlaceDao placeDao;
    private GeofenceManager geofenceManager;
    private PlacesClient placesClient;

    private final ExecutorService io = Executors.newSingleThreadExecutor();

    public static final int NOTIFICATION_ID = 101;
    public static final String CHANNEL_ID = "LocationServiceChannel";
    public static final String TAG = "LocationService";

    public static final Set<Integer> MOVEMENT_ACTIVITIES = new HashSet<>() {{
        // HashSet with all possible movement activities
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
        ActivityDatabase db = ActivityDatabase.getDatabase(getApplicationContext());
        dao = db.activityDao();
        placeDao = db.placeDao();
        geofenceManager = new GeofenceManager(this);

        if (!Places.isInitialized()) {
            Places.initialize(getApplicationContext(), BuildConfig.GOOGLE_API_KEY); //TODO check wheter the api is working
        }
        placesClient = Places.createClient(this);

        createNotificationChannel();

        io.execute(() -> { // Recover previous activity state after restart in background thread

            // Recover still activity
            StillLocation activeStill = dao.getActiveStillLocation(); // check if there was an active still
            if (activeStill != null) {
                // Restore the previous active still
                currentStillTrackingId = activeStill.id;
                currentActivityType = DetectedActivity.STILL;
                Log.d(TAG, "Recovered active still tracking ID: " + currentStillTrackingId);
            }

            // Recover active movement activities
            for (MovementActivity m : dao.getActiveMovementActivities()) { //TODO COMEBACK TO ENSURE YOU UNDERSTAND
                int type = getActivityTypeFromName(m.activityType);
                if (type != DetectedActivity.UNKNOWN) {
                    currentMovementTrackingIds.put(type, m.id);
                    currentActivityType = type;
                }
            }

            if (!currentMovementTrackingIds.isEmpty()) { // if there are active movement activities, start route updates
                startRouteUpdates();
            }

            syncGeofences() ;
            updateNotificationSafe();
        });
    }

    private void syncGeofences() {
        // initializing all geofence points, and in geofence manager android watches out if the boundaries are crossed
        List<Place> places = placeDao.getAllPlacesSync();
        for (Place p : places) {
            geofenceManager.addGeofence("place_" + p.id, p.lat, p.lng, p.radius > 0 ? p.radius : 100f);
        }
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        stopRouteUpdates();
        io.shutdownNow();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Handles when intents are sent( activity is recognized or geofence is triggered)
        if (intent != null) {
            String action = intent.getAction();
            if (ActivityTransitionReceiver.ACTION_ACTIVITY_UPDATE.equals(action)) { // checks if the intent came from activity recognition
                // Extracts the activity data
                int activityType = intent.getIntExtra(ActivityTransitionReceiver.EXTRA_ACTIVITY_TYPE, DetectedActivity.UNKNOWN);
                int transitionType = intent.getIntExtra(ActivityTransitionReceiver.EXTRA_TRANSITION_TYPE, -1);
                long timestampNanos = intent.getLongExtra(ActivityTransitionReceiver.EXTRA_TIMESTAMP_NANOS, System.nanoTime()); // in nanoseconds

                // calling handleActivityUpdate in background
                io.execute(() -> handleActivityUpdate(activityType, transitionType, timestampNanos));
            } else if (GeofenceBroadcastReceiver.ACTION_GEOFENCE_UPDATE.equals(action)) { // checks if the intent came from geofence
                // Extracts the geofence data
                String geofenceId = intent.getStringExtra(GeofenceBroadcastReceiver.EXTRA_GEOFENCE_ID);
                int transitionType = intent.getIntExtra(GeofenceBroadcastReceiver.EXTRA_TRANSITION_TYPE, -1);
                // calling handleGeofenceUpdate in background
                io.execute(() -> handleGeofenceUpdate(geofenceId, transitionType));
            } else {
                startForeground();
            }
        } else {
            startForeground();
        }
        return START_STICKY;
    }

    private void handleGeofenceUpdate(String geofenceId, int transitionType) {
        Log.d(TAG, "Geofence update: " + geofenceId + " transition: " + transitionType);
        Date now = new Date();

        if (transitionType == Geofence.GEOFENCE_TRANSITION_ENTER || transitionType == Geofence.GEOFENCE_TRANSITION_DWELL) {
            if (geofenceId.startsWith("place_")) {
                // updates the active still with the geofence data
                long placeId = Long.parseLong(geofenceId.replace("place_", ""));
                Place place = placeDao.getPlaceById(placeId);
                if (place != null) {
                    updateActiveStillWithPlace(place);
                }
            }
        } else if (transitionType == Geofence.GEOFENCE_TRANSITION_EXIT) {
            //  if the user left the geofence, end the still tracking
            if (currentStillTrackingId != null) {
                StillLocation still = dao.getStillLocationById(currentStillTrackingId);
                if (still != null && geofenceId.equals("place_" + still.placeId)) {
                    endStillTracking(now);

                }
            }
        }
    }

    private void updateActiveStillWithPlace(Place place) {
        //updates the still data to the geofence data
        if (currentStillTrackingId != null) {
            StillLocation still = dao.getStillLocationById(currentStillTrackingId);
            if (still != null) {
                still.placeId = String.valueOf(place.id);
                still.placeName = place.name;
                still.icon = place.category; //TODO FIX THE NAMING OF CATEGORY TO ICON
                still.placeCoords = place.address;
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
            // Ensure only one activity is active by ending any ongoing ones before starting the new one
            // if the ongoing activity is the same as the new one, do nothing(later in the script they will be merged)
            if (currentStillTrackingId != null && activityType != DetectedActivity.STILL) {
                endStillTracking(eventTime);
            }
            for (Integer type : new HashSet<>(currentMovementTrackingIds.keySet())) {
                if (type != activityType) {
                    endMovementTracking(type, eventTime);
                }
            }

            currentActivityType = activityType;

            isInitializing = true;
            updateNotificationSafe();

            // call functions to start activities according to activity type
            if (activityType == DetectedActivity.STILL) {
                startStillTracking(eventTime);
            } else if (MOVEMENT_ACTIVITIES.contains(activityType)) {
                startMovementTracking(activityType, eventTime);
            }
            isInitializing = false;

        } else if (transitionType == ActivityTransition.ACTIVITY_TRANSITION_EXIT) {
            // call functions to end activities according to activity type
            if (activityType == DetectedActivity.STILL) {
                endStillTracking(eventTime);
            } else if (MOVEMENT_ACTIVITIES.contains(activityType)) {
                endMovementTracking(activityType, eventTime);
            }
            updateCurrentActivityAfterExit(activityType);
        }

        updateNotificationSafe();
    }

    private void updateCurrentActivityAfterExit(int exitedActivityType) { // TODO TRY TO UNDERSTAND
        if (currentActivityType != exitedActivityType) return;

        if (!currentMovementTrackingIds.isEmpty()) {
            currentActivityType = currentMovementTrackingIds.keySet().iterator().next();
        } else if (currentStillTrackingId != null) {
            currentActivityType = DetectedActivity.STILL;
        } else {
            currentActivityType = DetectedActivity.UNKNOWN;
        }
    }



    private void startStillTracking(Date startTime) {
        if (currentStillTrackingId != null) return;
        Location currentLocation = getLocationOnceBlocking();

        // Merge with last still if close enough
        StillLocation lastStill = dao.getLastCompletedStillLocation();
        if (lastStill != null && lastStill.lat != null && lastStill.lng != null && currentLocation != null) {
            float distance = distanceInMeters(currentLocation.getLatitude(), currentLocation.getLongitude(), lastStill.lat, lastStill.lng);
            if (distance < 75f) { // 100 meter threshold for merging
                // merge with last still
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
            // Try to find geofence
            Place nearby = findNearbyPlace(currentLocation.getLatitude(), currentLocation.getLongitude());
            if (nearby != null) {
                // Use geofence data
                still.placeId = String.valueOf(nearby.id);
                still.placeName = nearby.name;
                still.icon = nearby.category;
                still.placeCoords = nearby.address;
                still.lat = nearby.lat;
                still.lng = nearby.lng;
            } else {
                // Otherwise, use Google places
                detectGooglePlace(still);
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

    private void detectGooglePlace(StillLocation still) {
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        try {
            List<Field> placeFields =
                    Arrays.asList(
                            Field.DISPLAY_NAME,
                            Field.ID,
                            Field.TYPES,
                            Field.FORMATTED_ADDRESS);

            // request Google Place search
            FindCurrentPlaceRequest request = FindCurrentPlaceRequest.newInstance(placeFields);
            FindCurrentPlaceResponse response = Tasks.await(placesClient.findCurrentPlace(request), 5, TimeUnit.SECONDS);

            if (response != null && !response.getPlaceLikelihoods().isEmpty()) {

                // extract the most likely place
                PlaceLikelihood topResult = response.getPlaceLikelihoods().get(0);
                com.google.android.libraries.places.api.model.Place place = topResult.getPlace();

                //extract place data
                still.placeName = place.getDisplayName();
                still.placeId = place.getId();
                still.placeCoords = place.getFormattedAddress();
                still.confidence = topResult.getLikelihood();

                
                // Map Google Types to your icon/category if needed
                if (place.getPlaceTypes() != null && !place.getPlaceTypes().isEmpty()) {
                    still.icon = place.getPlaceTypes().get(0);
                }

                String msg = "Google Places detected: " + still.placeName + " (likelihood: " + still.confidence + ")";
                Log.d(TAG, msg);
                Logger.saveLog(this, msg);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to detect Google Place", e);
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

        String activityName = getActivityName(activityType);

        // --- MERGE LOGIC START ---
        // Check if a similar activity ended recently (e.g., within 3 minutes)
        MovementActivity lastMovement = dao.getLastCompletedMovementActivity(activityName);
        if (lastMovement != null && lastMovement.endTimeDate != null) {
            long gapMs = startTime.getTime() - lastMovement.endTimeDate.getTime();
            if (gapMs >= -20000 && gapMs < 180000) { // 3 minute threshold, allowing for small overlaps/jitter
                String msg = "DB Update: Resuming recent " + activityName + " activity " + lastMovement.id + " (gap: " + (gapMs/1000) + "s)";
                Log.d(TAG, msg);
                Logger.saveLog(this, msg);
                dao.resumeMovementActivity(lastMovement.id);
                currentMovementTrackingIds.put(activityType, lastMovement.id);
                startRouteUpdates();
                return;
            } else {
                Log.d(TAG, "Not merging " + activityName + ". Gap: " + gapMs + "ms");
            }
        }
        // --- MERGE LOGIC END ---

        Location currentLocation = getLocationOnceBlocking();
        MovementActivity movement = new MovementActivity();
        movement.activityType = activityName;
        movement.startLat = currentLocation != null ? currentLocation.getLatitude() : null;
        movement.startLng = currentLocation != null ? currentLocation.getLongitude() : null;
        movement.startTimeDate = startTime;

        try {
            String msg = "DB Update from startMovementTracking: Inserting movement activity " + movement.activityType;
            Log.d(TAG, msg);
            Logger.saveLog(this, msg);
            long id = dao.insertMovementActivity(movement);
            currentMovementTrackingIds.put(activityType, id);
            startRouteUpdates();
        } catch (Exception ignored) {}
    }

    private void endMovementTracking(int activityType, Date endTime) {
        Long id = currentMovementTrackingIds.get(activityType);
        if (id == null) return;
        Location currentLocation = getLocationOnceBlocking();

        try {
            MovementActivity movement = dao.getMovementActivityById(id);
            if (movement != null && movement.startLat != null && movement.startLng != null && currentLocation != null) {
                String resolved = checkIfStillIsMovement(movement.startLat, movement.startLng, movement.startTimeDate, endTime, currentLocation.getLatitude(), currentLocation.getLongitude());

                if ("Still".equalsIgnoreCase(resolved)) {
                    // 1. Check if we can merge with a previous still activity
                    StillLocation lastStill = dao.getLastCompletedStillLocation();
                    if (lastStill != null && lastStill.lat != null && lastStill.lng != null) {
                        float dist = distanceInMeters(movement.startLat, movement.startLng, lastStill.lat, lastStill.lng);
                        if (dist < 100f) {
                            // Extend instead of new record
                            String msg = "DB Update: Merging false movement " + id + " into previous STILL " + lastStill.id;
                            Log.d(TAG, msg);
                            Logger.saveLog(this, msg);
                            dao.deleteMovementAndExtendStill(id, lastStill.id, endTime);
                            return;
                        }
                    }

                    // 2. Check if we can merge with a CURRENT active still activity
                    StillLocation activeStill = dao.getActiveStillLocation();
                    if (activeStill != null && activeStill.lat != null && activeStill.lng != null) {
                        float dist = distanceInMeters(movement.startLat, movement.startLng, activeStill.lat, activeStill.lng);
                        if (dist < 100f) {
                            String msg = "DB Update: Merging false movement " + id + " into active STILL " + activeStill.id;
                            Log.d(TAG, msg);
                            Logger.saveLog(this, msg);
                            dao.deleteMovementAndPrependToStill(id, activeStill.id, movement.startTimeDate);
                            return;
                        }
                    }

                    // Fallback: Create new still as before
                    StillLocation still = new StillLocation();
                    still.lat = movement.startLat;
                    still.lng = movement.startLng;
                    still.startTimeDate = movement.startTimeDate;
                    still.endTimeDate = endTime;

                    Place nearby = findNearbyPlace(still.lat, still.lng);
                    if (nearby != null) {
                        still.placeId = String.valueOf(nearby.id);
                        still.placeName = nearby.name;
                        still.icon = nearby.category;
                        still.placeCoords = nearby.address;
                    } else {
                        // Use Google Places SDK
                        detectGooglePlace(still);
                    }

                    String msg = "DB Update from endMovementTracking: Replacing movement " + id + " with new still location";
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
            if (currentMovementTrackingIds.isEmpty()) {
                stopRouteUpdates();
            }
        }
    }

    private void startRouteUpdates() {
        if (routeLocationCallback != null) return;

        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 30000)
                .setMinUpdateIntervalMillis(10000)
                .setMaxUpdateDelayMillis(60000)
                .build();

        routeLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) return;
                io.execute(() -> {
                    for (Location location : locationResult.getLocations()) {
                        for (Long movementId : currentMovementTrackingIds.values()) {
                            RoutePoint point = new RoutePoint();
                            point.movementActivityId = movementId;
                            point.lat = location.getLatitude();
                            point.lng = location.getLongitude();
                            point.timestamp = location.getTime();
                            dao.insertRoutePoint(point);
                        }
                    }
                });
            }
        };

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, routeLocationCallback, Looper.getMainLooper());
        } catch (SecurityException e) {
            Log.e(TAG, "Location permission missing", e);
        }
    }

    private void stopRouteUpdates() {
        if (routeLocationCallback != null) {
            fusedLocationClient.removeLocationUpdates(routeLocationCallback);
            routeLocationCallback = null;
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


    private void startForeground() {
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
        String activityLabel = (currentActivityType == DetectedActivity.UNKNOWN) ? "Waiting..." : getActivityName(currentActivityType);
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
}
