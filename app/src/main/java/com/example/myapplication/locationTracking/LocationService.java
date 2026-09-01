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
import android.util.Log;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.os.SystemClock;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;

import com.example.myapplication.LifeTrackerApp;
import com.example.myapplication.MainActivity;
import com.example.myapplication.database.ActivityDao;
import com.example.myapplication.database.ActivityDatabase;
import com.example.myapplication.database.MovementActivity;
import com.example.myapplication.database.Place;
import com.example.myapplication.database.PlaceDao;
import com.example.myapplication.database.RoutePoint;
import com.example.myapplication.database.StillLocation;
import com.example.myapplication.helpers.ErrorLogger;
import com.example.myapplication.helpers.Logger;
import com.example.myapplication.locationTracking.receiver.ActivityTransitionReceiver;
import com.example.myapplication.locationTracking.receiver.LocationServiceRestartReceiver;
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

import com.example.myapplication.BuildConfig;

public class LocationService extends Service {

    private static volatile int currentActivityType = DetectedActivity.UNKNOWN;
    static volatile Long currentStillTrackingId = null;
    static final Map<Integer, Long> currentMovementTrackingIds = new ConcurrentHashMap<>(); // is a list because of the possibly of that android thinks two activities are ongoing
    private static volatile boolean isInitializing = false;

    private ActivityDao dao;
    private PlaceDao placeDao;
    private GeofenceManager geofenceManager;
    private GeofenceUtilsManager geofenceUtilsManager;
    private ActivityMergeManager activityMergeManager;
    private LocationProvider locationProvider;
    private LifeTrackerApp app;
    private PlacesClient placesClient; // Added PlacesClient member variable
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
        app = (LifeTrackerApp) getApplication();
        FusedLocationProviderClient fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        ActivityDatabase db = ActivityDatabase.getDatabase(getApplicationContext());
        dao = db.activityDao();
        placeDao = db.placeDao();
        geofenceManager = new GeofenceManager(this);

        if (!Places.isInitialized()) {
            Places.initializeWithNewPlacesApiEnabled(getApplicationContext(), BuildConfig.GOOGLE_API_KEY);
        }
        placesClient = Places.createClient(this); // Initialize the member variable

        geofenceUtilsManager = new GeofenceUtilsManager(placeDao, this, placesClient, geofenceManager);

        locationProvider = new LocationProvider(this, fusedLocationClient, dao, app.getDatabaseWriteExecutor(), geofenceUtilsManager, this);
        activityMergeManager = new ActivityMergeManager(dao, this, locationProvider);
        createNotificationChannel();

        // Schedule the service restart alarm
        scheduleServiceRestartAlarm();

        app.getDatabaseWriteExecutor().execute(() -> {
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
            for (MovementActivity movement : dao.getActiveMovementActivities()) {
                int type = getActivityTypeFromName(movement.getActivityTypeName());
                if (type != DetectedActivity.UNKNOWN) {
                    currentMovementTrackingIds.put(type, movement.getId());
                    currentActivityType = type;
                }
            }

            // if there are active movement activities, start route updates
            if (!currentMovementTrackingIds.isEmpty()) {
                locationProvider.startRouteUpdates();
            }

            // If no activities were recovered, start a new still activity
            if (currentStillTrackingId == null && currentMovementTrackingIds.isEmpty()) {
                currentActivityType = DetectedActivity.STILL;
                isInitializing = true;
                updateNotificationSafe();
                startStillTracking(new Date());
                isInitializing = false;
            }

            syncGeofences() ;
            updateNotificationSafe();
        });
    }

    private void syncGeofences() {
        // initializing all geofence points, and in geofence manager android watches out if the boundaries are crossed
        List<Place> places = placeDao.getAllPlaces();
        for (Place place : places) {
            geofenceManager.addGeofence("place_" + place.getId(), place.getLat(), place.getLng(), 75f);
        }
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        Logger.saveLog(this, "LocationService onDestroy");
        locationProvider.stopRouteUpdates();
        locationProvider.stopFrequentStillLocationUpdates();
        // Cancel the service restart alarm
        cancelServiceRestartAlarm();
        if (placesClient != null) {
            // There is no explicit shutdown method for PlacesClient.
            // Setting it to null allows for garbage collection.
            placesClient = null;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

            startForeground();
            // Handles when intents are sent( activity is recognized or geofence is triggered)
            if (intent != null) {
                String action = intent.getAction();
                if (ActivityTransitionReceiver.ACTION_ACTIVITY_UPDATE.equals(action)) { // checks if the intent came from activity recognition
                    // Extracts the activity data
                    int activityType = intent.getIntExtra(ActivityTransitionReceiver.EXTRA_ACTIVITY_TYPE, DetectedActivity.UNKNOWN);
                    int transitionType = intent.getIntExtra(ActivityTransitionReceiver.EXTRA_TRANSITION_TYPE, -1);
                    long timestampMs = intent.getLongExtra(ActivityTransitionReceiver.EXTRA_TIMESTAMP_MS, System.currentTimeMillis());
                    // calling handleActivityUpdate in background
                    app.getDatabaseWriteExecutor().execute(() -> {
                        handleActivityUpdate(activityType, transitionType, timestampMs);;
                    });
//TODO                } else if (GeofenceBroadcastReceiver.ACTION_GEOFENCE_UPDATE.equals(action)) { // checks if the intent came from geofence
//TODO                    // Extracts the geofence data
//TODO                    String geofenceId = intent.getStringExtra(GeofenceBroadcastReceiver.EXTRA_GEOFENCE_ID);
//TODO                    int transitionType = intent.getIntExtra(GeofenceBroadcastReceiver.EXTRA_TRANSITION_TYPE, -1);
//TODO                    // calling handleGeofenceUpdate in background
//TODO                    app.getDatabaseWriteExecutor().execute(() -> handleGeofenceUpdate(geofenceId, transitionType));
//TODO
                }
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
                    if (currentStillTrackingId != null) {
                        updateActiveStillWithPlace(place);
                        return;
                    }
                    if (!currentMovementTrackingIds.isEmpty()){
                        for (Integer type : currentMovementTrackingIds.keySet()) {
                            endMovementTracking(type, now);
                        }
                        currentMovementTrackingIds.clear();
                        locationProvider.stopRouteUpdates();
                        startStillTracking(now);
                        updateActiveStillWithPlace(place);

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

    private synchronized void handleActivityUpdate(int activityType, int transitionType, long timestampMs) {

        try {

            if (activityType == DetectedActivity.UNKNOWN) return;

            Date eventTime = new Date(timestampMs);
            Logger.saveLog(this, "1");
            if (transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
                //TODOString previousActivityName = null;
                //TODOif (activityType == DetectedActivity.STILL) {
                //TODO    if (!currentMovementTrackingIds.isEmpty()) {
                //TODO        // Transitioning from a movement activity to STILL
                //TODO        int prevType = currentMovementTrackingIds.keySet().iterator().next();
                //TODO        previousActivityName = getActivityName(prevType);
                //TODO    }
                //TODO}

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
        catch (Exception e) {
            ErrorLogger.logError(this, TAG, "Error", e);
            Log.e(TAG, "Error in handleActivityUpdate: " + e.getMessage(), e);
            Logger.saveLog(this, "Error in handleActivityUpdate: " + e.getMessage());
        }
    }

    private void updateCurrentActivityAfterExit(int exitedActivityType) {
        if (currentActivityType != exitedActivityType) return;

        if (!currentMovementTrackingIds.isEmpty()) {
            currentActivityType = currentMovementTrackingIds.keySet().iterator().next();
        } else if (currentStillTrackingId != null) {
            currentActivityType = DetectedActivity.STILL;
        } else {
            currentActivityType = DetectedActivity.UNKNOWN;
        }
    }

    void startStillTracking(Date startTime) {
        // If there's already an active still, prevent creating a duplicate
        if (currentStillTrackingId != null) {
            Log.w(TAG, "Attempted to start new still tracking while one is already active (ID: " + currentStillTrackingId + "). Aborting new still creation.");
            Logger.saveLog(this, "Attempted to start new still tracking while one is already active (ID: " + currentStillTrackingId + "). Aborting new still creation.");
            return;
        }

        Location currentLocation = locationProvider.getLocationOnceBlocking();

        // MERGE CHECK - check if possible to merge with ongoing activity
        if (activityMergeManager.attemptMergeWithOngoingStill(currentStillTrackingId, startTime,  currentLocation, geofenceUtilsManager)) {
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

        if (currentLocation != null) {
            // Try to find geofence or place
            geofenceUtilsManager.findPlaceAndUpdateStill(currentLocation, still);
        } else {
            // If no current location, start frequent updates to get one
            locationProvider.startFrequentStillLocationUpdates();
        }

        try {
            String msg = String.format("DB Update from startStillTracking: Inserting new still location %s",
                    (currentLocation != null ? String.format(Locale.US, "at [%.6f, %.6f]", currentLocation.getLatitude(), currentLocation.getLongitude()) : "(no location)"));
            Log.d(TAG, msg);
            Logger.saveLog(this, msg);
            currentStillTrackingId = dao.insertStillLocation(still);
            Log.d(TAG, "STILL started: ID=" + currentStillTrackingId);
        } catch (Exception e) {
            ErrorLogger.logError(this, TAG, "Error", e);
            Log.d(TAG, "Error:" + e);
            Logger.saveLog(this, "Error:" + e);
            currentStillTrackingId = null;
        }
    }

    void endStillTracking(Date endTime) {
        // Get the id of the current still activity
        if (currentStillTrackingId == null) {
            return;
        }
        long id = currentStillTrackingId;
        currentStillTrackingId = null;


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
            }
            else {
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
        }
        else {
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
            ErrorLogger.logError(this, TAG, "Error", e);
            Log.e(TAG, "Error starting movement activity: " + activityName, e);
            Logger.saveLog(this, "Error starting movement activity: " + activityName + " " + e.getMessage());
        }
    }

    void endMovementTracking(int activityType, Date endTime) {
        Long id = currentMovementTrackingIds.remove(activityType);
        if (id == null) return;
        Location currentLocation = locationProvider.getLocationOnceBlocking();

        try {
            MovementActivity movement = dao.getMovementActivityById(id);
            if (movement != null && movement.getStartLat() != null && movement.getStartLng() != null && currentLocation != null) {
                List<RoutePoint> routePoints = dao.getRoutePointsForMovement(id);
                boolean resolved = checkIfMovementIsStill(routePoints, movement.getStartLat(), movement.getStartLng(), currentLocation.getLatitude(), currentLocation.getLongitude());


                if (resolved) {

                    // Check if we can merge with a previous still activity
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

                    // Check if we can merge with a CURRENT active still activity
                    StillLocation activeStill = dao.getActiveStillLocation();
                    if (activeStill != null && activeStill.getLat() != null && activeStill.getLng() != null) {
                        float dist = distanceInMeters(movement.getStartLat(), movement.getStartLng(), activeStill.getLat(), activeStill.getLng());
                        if (dist < 100f) {
                            String msg = String.format(Locale.US, "DB Update: Merging false movement %d into active STILL %d. Start: [%.6f, %.6f], End: [%.6f, %.6f]", id, activeStill.getId(), movement.getStartLat(), movement.getStartLng(), currentLocation.getLatitude(), currentLocation.getLongitude());
                            Log.d(TAG, msg);
                            Logger.saveLog(this, msg);


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
            }
            else {
                String msg = String.format(Locale.US, "DB Update from endMovementTracking: Ending movement activity %d (no location info)", id);
                Log.d(TAG, msg);
                Logger.saveLog(this, msg);
                dao.endMovementActivity(id,
                        currentLocation != null ? currentLocation.getLatitude() : null,
                        currentLocation != null ? currentLocation.getLongitude() : null,
                        endTime);
            }
        } catch (Exception e) {
            ErrorLogger.logError(this, TAG, "Error", e);
            Log.e(TAG, "Error ending movement activity: " + id, e);
            Logger.saveLog(this, "Error ending movement activity: " + id + " " + e.getMessage());
        } finally {
            if (currentMovementTrackingIds.isEmpty()) {
                locationProvider.stopRouteUpdates();
            }
        }
    }
    public void updateCurrentActivityType(int activityType) {
        currentActivityType = activityType;
        updateNotificationSafe();
    }
    private void startForeground() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Notification permission missing. Shutting down service.");
                Intent permIntent = new Intent("com.example.myapplication.PERMISSION_REVOKED").setPackage(getPackageName());
                sendBroadcast(permIntent);
                stopSelf();
                return;
            }
            }
        Notification notification = buildNotification();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (SecurityException e) {
            ErrorLogger.logError(this, TAG, "Error", e);
            Log.e(TAG, "Permissions revoked. Cannot start foreground service.", e);
            Intent PermIntent = new Intent("com.example.myapplication.PERMISSION_REVOKED").setPackage(getPackageName());;
            sendBroadcast(PermIntent);
            stopSelf();
        } catch (Throwable e) {
            ErrorLogger.logError(this, TAG, "Error", e);
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
        } catch (SecurityException e) {
            ErrorLogger.logError(this, TAG, "Error", e);
            Intent PermIntent = new Intent("com.example.myapplication.PERMISSION_REVOKED").setPackage(getPackageName());;
            sendBroadcast(PermIntent);
            stopSelf();
            Log.e(TAG, "Notification permission revoked: " + e.getMessage(), e);
        }
    }

    public static boolean isServiceRunning(Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager != null) {
            for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
                if (LocationService.class.getName().equals(service.service.getClassName())) {
                    return service.foreground;
                }
            }
        }
        return false;
    }

    private void scheduleServiceRestartAlarm() {
        Log.d(TAG, "Scheduling service restart alarm.");
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, LocationServiceRestartReceiver.class);
        intent.setAction(LocationServiceRestartReceiver.ACTION_RESTART_SERVICE);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Repeat every 5 minutes (300 * 1000 milliseconds)
        alarmManager.setRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 5 * 60 * 1000, // Initial trigger after 5 minutes
                5 * 60 * 1000, // Repeat every 5 minutes
                pendingIntent
        );
    }

    private void cancelServiceRestartAlarm() {
        Log.d(TAG, "Cancelling service restart alarm.");
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, LocationServiceRestartReceiver.class);
        intent.setAction(LocationServiceRestartReceiver.ACTION_RESTART_SERVICE);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        alarmManager.cancel(pendingIntent);
    }
}