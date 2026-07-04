package com.example.myapplication.locationTracking;

import static com.example.myapplication.locationTracking.ActivityTrackingUtils.checkIfMovementIsStill;
import static com.example.myapplication.locationTracking.ActivityTrackingUtils.checkIfStillIsMovement;
import static com.example.myapplication.locationTracking.ActivityTrackingUtils.distanceInMeters;
import static com.example.myapplication.locationTracking.ActivityTrackingUtils.getActivityName;
import static com.example.myapplication.locationTracking.ActivityTrackingUtils.getActivityTypeFromName;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
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
import com.example.myapplication.locationTracking.reciever.ActivityTransitionReceiver;
import com.example.myapplication.locationTracking.reciever.GeofenceBroadcastReceiver;
import com.google.android.gms.location.ActivityTransition;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.LocationServices;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.net.PlacesClient;


import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.example.myapplication.BuildConfig;

public class LocationService extends Service {

    private static volatile int currentActivityType = DetectedActivity.UNKNOWN;
    static volatile Long currentStillTrackingId = null;
    static final Map<Integer, Long> currentMovementTrackingIds = new ConcurrentHashMap<>(); // is a list because of the possibly of that android thinks two activities are ongoing
    private static volatile boolean isInitializing = false;

    private volatile boolean isRequestingStillLocationUpdates = false;

    private ActivityDao dao;
    private PlaceDao placeDao;
    private GeofenceManager geofenceManager;
    private GeofenceUtilsManager geofenceUtilsManager;
    private ActivityMergeManager activityMergeManager;
    private LocationProvider locationProvider;
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
        FusedLocationProviderClient fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        ActivityDatabase db = ActivityDatabase.getDatabase(getApplicationContext());
        dao = db.activityDao();
        placeDao = db.placeDao();
        geofenceManager = new GeofenceManager(this);

        if (!Places.isInitialized()) {
            Places.initializeWithNewPlacesApiEnabled(getApplicationContext(), BuildConfig.GOOGLE_API_KEY);
        }
        PlacesClient placesClient = Places.createClient(this);

        geofenceUtilsManager = new GeofenceUtilsManager(placeDao, this, placesClient, geofenceManager);

        locationProvider = new LocationProvider(this, fusedLocationClient, dao, io, geofenceUtilsManager, this);
        activityMergeManager = new ActivityMergeManager(dao, this, locationProvider);
        createNotificationChannel();

        io.execute(() -> {
            // Recover previous activity state after restart in background thread

            // Recover still activity
            StillLocation activeStill = dao.getActiveStillLocation(); // check if there was an active still
            if (activeStill != null) {
                // Restore the previous active still
                currentStillTrackingId = activeStill.getId();
                currentActivityType = DetectedActivity.STILL;
                Log.d(TAG, "Recovered active still tracking ID: " + currentStillTrackingId);

                // If a still was recovered and had no location, start frequent updates
                if (activeStill.getLat() == null || activeStill.getLng() == null) {
                    locationProvider.startFrequentStillLocationUpdates();
                }
            }
            // Recover active movement activities
            for (MovementActivity m : dao.getActiveMovementActivities()) { //TODO COMEBACK TO ENSURE YOU UNDERSTAND
                int type = getActivityTypeFromName(m.getActivityTypeName());
                if (type != DetectedActivity.UNKNOWN) {
                    currentMovementTrackingIds.put(type, m.getId());
                    currentActivityType = type;
                }
            }

            if (!currentMovementTrackingIds.isEmpty()) { // if there are active movement activities, start route updates
                locationProvider.startRouteUpdates();
            }

            // If no activities were recovered, start a new still activity
            if (currentStillTrackingId == null && currentMovementTrackingIds.isEmpty()) {
                Log.d(TAG, "No activity recovered, starting a new STILL activity.");
                currentActivityType = DetectedActivity.STILL;
                isInitializing = true;
                updateNotificationSafe();
                startStillTracking(new Date(), null);
                isInitializing = false;
            }

            syncGeofences() ;
            updateNotificationSafe();
        });
    }

    private void syncGeofences() {
        // initializing all geofence points, and in geofence manager android watches out if the boundaries are crossed
        List<Place> places = placeDao.getAllPlaces();
        for (Place p : places) {
            geofenceManager.addGeofence("place_" + p.getId(), p.getLat(), p.getLng(), 75f);
        }
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        Logger.saveLog(this, "LocationService onDestroy");
        locationProvider.stopRouteUpdates();
        locationProvider.stopFrequentStillLocationUpdates();
        io.shutdownNow();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            startForeground();

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

                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onStartCommand: " + e.getMessage(), e);
            Logger.saveLog(this, "Error in onStartCommand: " + e.getMessage());
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
                    // If we got a location from geofence, stop frequent still location updates
                    if (isRequestingStillLocationUpdates) {
                        locationProvider.stopFrequentStillLocationUpdates();
                    }
                }
            }
        } else if (transitionType == Geofence.GEOFENCE_TRANSITION_EXIT) {
            //  if the user left the geofence, end the still tracking
            if (currentStillTrackingId != null) {
                StillLocation still = dao.getStillLocationById(currentStillTrackingId);
                if (still != null && geofenceId.equals("place_" + still.getPlaceId())) {
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
                still.setPlaceId(place.getId());
                still.setPlaceName(place.getName());
                still.setIcon(place.getIcon());
                still.setCategory(place.getCategory());
                still.setColor(place.getColor());
                still.setPlaceAddress(place.getAddress());
                still.setLat(place.getLat());
                still.setLng(place.getLng());
                String msg = String.format(Locale.US, "DB Update from updateActiveStillWithPlace: Updating still location %d with place %s at [%.6f, %.6f]", still.getId(), place.getName(), place.getLat(), place.getLng());
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


        // keep the cpu running until the activity is processed
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyApp::LocationProcessingLock");

        wakeLock.acquire(30 * 1000L); // 30 seconds

        try {


        if (activityType == DetectedActivity.UNKNOWN) return;

        Date eventTime = new Date(System.currentTimeMillis() - TimeUnit.NANOSECONDS.toMillis(android.os.SystemClock.elapsedRealtimeNanos() - timestampNanos));

        if (transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
            String previousActivityName = null;
            if (activityType == DetectedActivity.STILL) {
                if (!currentMovementTrackingIds.isEmpty()) {
                    // Transitioning from a movement activity to STILL
                    int prevType = currentMovementTrackingIds.keySet().iterator().next();
                    previousActivityName = getActivityName(prevType);
                }
            }

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
                startStillTracking(eventTime, previousActivityName);
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
        } finally {
            if (wakeLock.isHeld()) {
                wakeLock.release();
            }
        }
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

    void startStillTracking(Date startTime, String wasSupposedToBeActivity) {
        Location currentLocation = locationProvider.getLocationOnceBlocking();

        // MERGE CHECK - check if possible to merge with ongoing activity
        if (activityMergeManager.attemptMergeWithOngoingStill(currentStillTrackingId, startTime,  currentLocation, geofenceUtilsManager)) {
            currentStillTrackingId = null;
            return;
        }

        // MERGE CHECK - Check if possible to merge with last completed still activity
        StillLocation lastStill = dao.getLastCompletedStillLocation();
        if (activityMergeManager.attemptMergeWithLastCompletedStill(currentStillTrackingId, startTime,  currentLocation, lastStill)) {
            currentStillTrackingId = lastStill.getId();
            return;
        }

        StillLocation still = new StillLocation();

        if (currentLocation != null) still.setLat(currentLocation.getLatitude()); else still.setLat(null);
        if (currentLocation != null) still.setLng(currentLocation.getLongitude()); else still.setLng(null);
        still.setStartTimeDate(startTime);
        still.setWasSupposedToBeActivity(wasSupposedToBeActivity);

        if (currentLocation != null) {
            // Try to find geofence or place
            geofenceUtilsManager.findPlaceAndUpdateStill(currentLocation, still);
        } else {
            // If no current location, start frequent updates to get one
            locationProvider.startFrequentStillLocationUpdates();
        }

        try {
            String msg = String.format("DB Update from startStillTracking: Inserting new still location %s %s",
                    (wasSupposedToBeActivity != null ? "(Stop: " + wasSupposedToBeActivity + ")" : ""),
                    (currentLocation != null ? String.format(Locale.US, "at [%.6f, %.6f]", currentLocation.getLatitude(), currentLocation.getLongitude()) : "(no location)"));
            Log.d(TAG, msg);
            Logger.saveLog(this, msg);
            currentStillTrackingId = dao.insertStillLocation(still);
            Log.d(TAG, "STILL started: ID=" + currentStillTrackingId);
        } catch (Exception e) {
            Log.d(TAG, "Error:" + e);
            Logger.saveLog(this, "Error:" + e);
            currentStillTrackingId = null;
        }
    }

    void endStillTracking(Date endTime) {
        // Get the id of the current still activity
        long id;
        if (currentStillTrackingId == null) {
            return;
        }
        id = currentStillTrackingId;


        Location currentLocation = locationProvider.getLocationOnceBlocking(); // get current location

        StillLocation still = dao.getStillLocationById(id);
        if (still == null) {
            currentStillTrackingId = null;
            locationProvider.stopFrequentStillLocationUpdates();
            return;
        }

        Double startLat = still.getLat(); Double startLng = still.getLng(); Date startTime = still.getStartTimeDate();
        if (startLat == null || startLng == null) {
            // if there is no location

            if (currentLocation != null) {
                //if there is currentLocation update the still location
                still.setLat(currentLocation.getLatitude());
                still.setLng(currentLocation.getLongitude());
                dao.updateStillLocation(still);
            }
            else{
                //TODO HANDLE NULL CURRENT LOCATION, IDK HOW TO IMPLEMENT SO FUTURE ME SHOULD REALLY FIGURE IT OUT
            }

            locationProvider.stopFrequentStillLocationUpdates(); // stop frequent still location updates because location was found

            // try to Merge with last still if close enough
            StillLocation lastStill = dao.getLastCompletedStillLocation();
            if(activityMergeManager.attemptMergeWithLastCompletedStillEnd(id, currentLocation, lastStill)){
                currentStillTrackingId = lastStill.getId();
                return;
            }

            if (currentLocation != null) {
                // Try to find geofence
                geofenceUtilsManager.findPlaceAndUpdateStill(currentLocation, still);
            }

            String msg = String.format(Locale.US, "DB Update from endStillTracking: Ending still location %d %s (no initial location info)", id,
                    (currentLocation != null ? String.format(Locale.US, "at [%.6f, %.6f]", currentLocation.getLatitude(), currentLocation.getLongitude()) : ""));
            Log.d(TAG, msg);
            Logger.saveLog(this, msg);
            dao.endStillLocation(id, endTime);
            currentStillTrackingId = null;
            Log.d(TAG, "STILL ended: ID=" + id);
            return;

        }
        else if (startLat != null && startLng != null && currentLocation != null) {
            String resolved = checkIfStillIsMovement(startLat, startLng, startTime, endTime, currentLocation.getLatitude(), currentLocation.getLongitude(), this);
            if ("Still".equalsIgnoreCase(resolved)) {
                String msg = String.format(Locale.US, "DB Update from endStillTracking: Ending still location %d. Start: [%.6f, %.6f], End: [%.6f, %.6f]", id, startLat, startLng, currentLocation.getLatitude(), currentLocation.getLongitude());
                Log.d(TAG, msg);
                Logger.saveLog(this, msg);

                dao.endStillLocation(id, endTime);
            } else {
                MovementActivity movement = new MovementActivity();
                movement.setActivityTypeName(resolved);
                movement.setStartLat(startLat);
                movement.setStartLng(startLng);
                movement.setEndLat(currentLocation.getLatitude());
                movement.setEndLng(currentLocation.getLongitude());
                movement.setStartTimeDate(startTime);
                movement.setEndTimeDate(endTime);
                String msg = String.format(Locale.US, "DB Update from endStillTracking: Replacing still %d with movement %s. Start: [%.6f, %.6f], End: [%.6f, %.6f]", id, resolved, startLat, startLng, currentLocation.getLatitude(), currentLocation.getLongitude());
                Log.d(TAG, msg);
                Logger.saveLog(this, msg);
                dao.replaceStillWithMovement(id, movement);
            }
        } else {
            String msg = String.format(Locale.US, "DB Update from endStillTracking: Ending still location %d (fallback, no location info)", id);
            Log.d(TAG, msg);
            Logger.saveLog(this, msg);
            dao.endStillLocation(id, endTime);
        }
        currentStillTrackingId = null;
        Log.d(TAG, "STILL ended: ID=" + id);
        locationProvider.stopFrequentStillLocationUpdates();
    }

    private void startMovementTracking(int activityType, Date startTime) {
        if (currentMovementTrackingIds.containsKey(activityType)) return;

        String activityName = getActivityName(activityType);

        // Check for an ongoing activity of the same type
        Long resumedId;
        resumedId = activityMergeManager.attemptMergeWithOngoingMovement(activityName);
        if (resumedId != null) {
            currentMovementTrackingIds.put(activityType, resumedId);
            return;
        }

        // Check if a similar activity ended recently, if so, merge
        resumedId = activityMergeManager.attemptMergeWithLastCompletedMovement(activityName, startTime);
        if (resumedId != null) {
            currentMovementTrackingIds.put(activityType, resumedId);
            return;
        }

        Location currentLocation = locationProvider.getLocationOnceBlocking();
        MovementActivity movement = new MovementActivity();
        movement.setActivityTypeName(activityName);
        if(currentLocation != null) movement.setStartLat(currentLocation.getLatitude()); else movement.setStartLat(null);
        if(currentLocation != null) movement.setStartLng(currentLocation.getLongitude()); else movement.setStartLng(null);
        movement.setStartTimeDate(startTime);

        try {
            String msg = String.format("DB Update from startMovementTracking: Inserting movement activity %s %s", movement.getActivityTypeName(),
                    (currentLocation != null ? String.format(Locale.US, "at [%.6f, %.6f]", currentLocation.getLatitude(), currentLocation.getLongitude()) : "(no location)"));
            Log.d(TAG, msg);
            Logger.saveLog(this, msg);
            long id = dao.insertMovementActivity(movement);
            currentMovementTrackingIds.put(activityType, id);
            locationProvider.startRouteUpdates();
        } catch (Exception e) {
            Log.e(TAG, "Error starting movement activity: " + activityName, e);
            Logger.saveLog(this, "Error starting movement activity: " + activityName + " " + e.getMessage());
        }
    }

    void endMovementTracking(int activityType, Date endTime) {
        Long id = currentMovementTrackingIds.get(activityType);
        if (id == null) return;
        Location currentLocation = locationProvider.getLocationOnceBlocking();

        try {
            MovementActivity movement = dao.getMovementActivityById(id);
            if (movement != null && movement.getStartLat() != null && movement.getStartLng() != null && currentLocation != null) {
                List<RoutePoint> routePoints = dao.getRoutePointsForMovement(id);
                boolean resolved = checkIfMovementIsStill(routePoints);


                if (resolved) {

                    // 1. Check if we can merge with a previous still activity
                    StillLocation lastStill = dao.getLastCompletedStillLocation();
                    if (lastStill != null && lastStill.getLat() != null && lastStill.getLng() != null) {
                        float dist = distanceInMeters(movement.getStartLat(), movement.getStartLng(), lastStill.getLat(), lastStill.getLng());
                        if (dist < 100f) {
                            // Extend instead of new record
                            String msg = String.format(Locale.US, "DB Update: Merging false movement %d into previous STILL %d. Start: [%.6f, %.6f], End: [%.6f, %.6f]", id, lastStill.getId(), movement.getStartLat(), movement.getStartLng(), currentLocation.getLatitude(), currentLocation.getLongitude());
                            Log.d(TAG, msg);
                            Logger.saveLog(this, msg);
                            dao.deleteMovementAndExtendStill(id, lastStill.getId(), endTime);
                            return;
                        }
                    }

                    // 2. Check if we can merge with a CURRENT active still activity
                    StillLocation activeStill = dao.getActiveStillLocation();
                    if (activeStill != null && activeStill.getLat() != null && activeStill.getLng() != null) {
                        float dist = distanceInMeters(movement.getStartLat(), movement.getStartLng(), activeStill.getLat(), activeStill.getLng());
                        if (dist < 100f) {
                            String msg = String.format(Locale.US, "DB Update: Merging false movement %d into active STILL %d. Start: [%.6f, %.6f], End: [%.6f, %.6f]", id, activeStill.getId(), movement.getStartLat(), movement.getStartLng(), currentLocation.getLatitude(), currentLocation.getLongitude());
                            Log.d(TAG, msg);
                            Logger.saveLog(this, msg);

                            // If the active still was previously marked as a \'stop\' (wasSupposedToBeActivity != null),\n                            // and the movement activity is ending and being re-classified as still,\n                            // then this \'still\' should revert to a normal still.\n                            if (activeStill.getWasSupposedToBeActivity() != null) {
                            if (activeStill.getWasSupposedToBeActivity() != null) {
                                String msg2 = "DB Update: Reverting active still " + activeStill.getId() + " from \'stop\' to \'normal still\' as movement " + id + " ended.";
                                Log.d(TAG, msg2);
                                Logger.saveLog(this, msg2);
                                activeStill.setWasSupposedToBeActivity(null);
                                dao.updateStillLocation(activeStill);
                            }

                            dao.deleteMovementAndPrependToStill(id, activeStill.getId(), movement.getStartTimeDate());
                            return;
                        }
                    }

                    // Fallback: Create new still as before
                    StillLocation still = new StillLocation();
                    still.setLat(movement.getStartLat());
                    still.setLng(movement.getStartLng()); // Fixed: Was movement.getStartLng() which could be null
                    still.setStartTimeDate(movement.getStartTimeDate());
                    still.setEndTimeDate(endTime);
                    still.setWasSupposedToBeActivity(movement.getActivityTypeName());

                    if (currentLocation != null) {
                        // Try to find geofence
                        geofenceUtilsManager.findPlaceAndUpdateStill(currentLocation, still);
                    }

                    String msg = String.format(Locale.US, "DB Update from endMovementTracking: Replacing movement %d with new still location (Stop). Start: [%.6f, %.6f], End: [%.6f, %.6f]", id, movement.getStartLat(), movement.getStartLng(), currentLocation.getLatitude(), currentLocation.getLongitude());
                    Log.d(TAG, msg);
                    Logger.saveLog(this, msg);
                    dao.replaceMovementWithStill(id, still);
                    Log.d(TAG, "Movement " + id + " re-classified as STILL (Stop)");
                } else {
                    long durationMs = endTime.getTime() - movement.getStartTimeDate().getTime();
                    if (durationMs < 120000) {
                        String msg = String.format(Locale.US, "DB Update: Deleting short movement activity %d (%s) - duration: %d s", id, movement.getActivityTypeName(), durationMs / 1000);
                        Log.d(TAG, msg);
                        Logger.saveLog(this, msg);
                        dao.deleteMovementActivity(id);
                        return;
                    }

                    String msg = String.format(Locale.US, "DB Update from endMovementTracking: Ending movement activity %d. Start: [%.6f, %.6f], End: [%.6f, %.6f]", id, movement.getStartLat(), movement.getStartLng(), currentLocation.getLatitude(), currentLocation.getLongitude());
                    Log.d(TAG, msg);
                    Logger.saveLog(this, msg);
                    dao.endMovementActivity(id, currentLocation.getLatitude(), currentLocation.getLongitude(), endTime);
                }
            } else {
                String msg = String.format(Locale.US, "DB Update from endMovementTracking: Ending movement activity %d (no location info)", id);
                Log.d(TAG, msg);
                Logger.saveLog(this, msg);
                dao.endMovementActivity(id,
                        currentLocation != null ? currentLocation.getLatitude() : null,
                        currentLocation != null ? currentLocation.getLongitude() : null,
                        endTime);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error ending movement activity: " + id, e);
            Logger.saveLog(this, "Error ending movement activity: " + id + " " + e.getMessage());
        } finally {
            currentMovementTrackingIds.remove(activityType);
            if (currentMovementTrackingIds.isEmpty()) {
                locationProvider.stopRouteUpdates();
            }
        }
    }
    public void updateActivityTypeToStill(int activityType) {
        currentActivityType = activityType;
        updateNotificationSafe(); //
    }
    private void startForeground() {
        Notification notification = buildNotification();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Permissions revoked. Cannot start foreground service.", e);
            stopSelf();
        } catch (Throwable e) {
            Log.e(TAG, "Error starting foreground service: " + e.getMessage(), e);
        }
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
        } catch (Throwable e) {
            Log.e(TAG, "Error updating notification: " + e.getMessage(), e);
            Logger.saveLog(this, "Error updating notification: " + e.getMessage());
        }
    }
}