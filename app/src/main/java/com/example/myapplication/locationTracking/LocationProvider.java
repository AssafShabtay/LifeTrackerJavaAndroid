package com.example.myapplication.locationTracking;

import static com.example.myapplication.locationTracking.ActivityTrackingUtils.isValidAccuracy;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.WorkerThread;
import androidx.core.app.ActivityCompat;

import com.example.myapplication.database.ActivityDao;
import com.example.myapplication.database.RoutePoint;
import com.example.myapplication.database.StillLocation;
import com.example.myapplication.helpers.Logger;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.gms.tasks.Tasks;

import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class LocationProvider {
    private static final String TAG = "LocationProvider";
    private final Context context;
    private final FusedLocationProviderClient fusedLocationClient;
    private final ActivityDao dao;
    private final ExecutorService io;
    private final GeofenceUtilsManager geofenceUtilsManager;

    private LocationCallback routeLocationCallback;
    private LocationCallback stillLocationCallback;
    private volatile boolean isRequestingStillLocationUpdates = false;
    private static final float STATIONARY_RADIUS_METERS = 30.0f; // Accounts for GPS drift
    private static final long LINGER_TIME_THRESHOLD_MS = 10 * 60 * 1000;
    private Location anchorLocation = null;
    private long anchorLocationTime = 0;
    private final LocationService locationService;
    public LocationProvider(Context context,
                                FusedLocationProviderClient fusedLocationClient,
                                ActivityDao dao,
                                ExecutorService io,
                                GeofenceUtilsManager geofenceUtilsManager,
    LocationService locationService) {
        this.context = context;
        this.fusedLocationClient = fusedLocationClient;
        this.dao = dao;
        this.io = io;
        this.geofenceUtilsManager = geofenceUtilsManager;
        this.locationService = locationService;
    }

    public boolean isRequestingStillLocationUpdates() {
        return isRequestingStillLocationUpdates;
    }
    void startRouteUpdates() {
        if (routeLocationCallback != null) return;

        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 30000)
                .setMinUpdateIntervalMillis(10000)
                .setMaxUpdateDelayMillis(60000)
                .build();

        routeLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) return;

                Location currentLocation = locationResult.getLastLocation();
                if (currentLocation != null) {
                    long currentTime = currentLocation.getTime();

                    // Initialize the anchor point
                    if (anchorLocation == null) {
                        anchorLocation = currentLocation;
                        anchorLocationTime = currentTime;
                    }
                    else {

                        // Check distance from the anchor point
                        float distance = currentLocation.distanceTo(anchorLocation);

                        // Check if the user is inside the stationary radius
                        if (distance <= STATIONARY_RADIUS_METERS) {
                            // User is still within the stationary radius,  Check the time.
                            if (currentTime - anchorLocationTime >= LINGER_TIME_THRESHOLD_MS) {
                                Log.i(TAG, "User lingered for over 10 minutes. Stopping tracking.");
                                Logger.saveLog(context, "User lingered for over 10 minutes. Stopping tracking.");

                                Date endTime = new Date(currentTime);

                                Set<Integer> activeMovementTypes = new HashSet<>(LocationService.currentMovementTrackingIds.keySet());
                                for (Integer activityType : activeMovementTypes) {
                                    locationService.endMovementTracking(activityType, endTime);
                                }
                                locationService.startStillTracking(endTime, null);
                                locationService.updateActivityTypeToStill(DetectedActivity.STILL);
                                stopRouteUpdates();
                                return;
                            }
                        } else {
                            // User moved outside the radius. Reset the anchor point and timer.
                            anchorLocation = currentLocation;
                            anchorLocationTime = currentTime;
                        }
                    }
                }
                io.execute(() -> {
                    for (Location location : locationResult.getLocations()) {
                        for (Long movementId : LocationService.currentMovementTrackingIds.values()) {
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

    void stopRouteUpdates() {
        if (routeLocationCallback != null) {
            fusedLocationClient.removeLocationUpdates(routeLocationCallback);
            routeLocationCallback = null;
        }
    }

    // New: Start frequent location updates specifically for still activities without a location
    void startFrequentStillLocationUpdates() {
        if (stillLocationCallback != null) return; // Already requesting updates
        if (LocationService.currentStillTrackingId == null) return; // No still activity to update

        Log.d(TAG, "Starting frequent still location updates for ID=" + LocationService.currentStillTrackingId);
        Logger.saveLog(context, "Starting frequent still location updates");

        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000) // Every 5 seconds
                .setMinUpdateIntervalMillis(20000) // Minimum every 20 seconds
                .setMaxUpdateDelayMillis(120000) // At most every 2 min
                .build();

        stillLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) return;
                io.execute(() -> {
                    for (Location location : locationResult.getLocations()) {
                        // Check for good accuracy before updating
                        if (location.hasAccuracy() && location.getAccuracy() <= 30.0f) { // Use a similar accuracy threshold as getLocationOnceBlocking
                            StillLocation still = dao.getStillLocationById(LocationService.currentStillTrackingId);
                            if (still != null && (still.lat == null || still.lng == null)) {
                                still.lat = location.getLatitude();
                                still.lng = location.getLongitude();
                                dao.updateStillLocation(still);

                                String msg = String.format(Locale.US, "DB Update from frequentStillLocation: Updated still %d with location [%.6f, %.6f]", still.id, still.lat, still.lng);
                                Log.d(TAG, msg);
                                Logger.saveLog(context, msg);

                                // Once we get a good location, try to find a place for it
                                geofenceUtilsManager.findPlaceAndUpdateStill(location, still);

                                dao.updateStillLocation(still);

                                stopFrequentStillLocationUpdates(); // Stop updates once location is obtained
                                return; // We only need one good location
                            }
                        }
                    }
                });
            }
        };

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, stillLocationCallback, Looper.getMainLooper());
            isRequestingStillLocationUpdates = true;
        } catch (SecurityException e) {
            Log.e(TAG, "Location permission missing for frequent still updates", e);
            Logger.saveLog(context, "Location permission missing for frequent still updates");
            isRequestingStillLocationUpdates = false;
        }
    }

    // New: Stop frequent location updates for still activities
    void stopFrequentStillLocationUpdates() {
        if (stillLocationCallback != null && isRequestingStillLocationUpdates) {
            Log.d(TAG, "Stopping frequent still location updates.");
            Logger.saveLog(context, TAG + ": Stopping frequent still location updates");
            fusedLocationClient.removeLocationUpdates(stillLocationCallback);
            stillLocationCallback = null;
            isRequestingStillLocationUpdates = false;
        }
    }


    @WorkerThread
    Location getLocationOnceBlocking() {
        Location loc = null;

        // STEP 1: Try Fresh High Accuracy (GPS-preferred)
        loc = tryCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, 12, TimeUnit.SECONDS);

        // STEP 2: Try Fresh Balanced Accuracy (Wi-Fi/Cell-preferred, great for indoors)
        if (loc == null || !isValidAccuracy(loc, 75.0f)) {
            String msg = String.format(TAG + ": High accuracy failed or too inaccurate. Trying Balanced Power...");
            Log.d(TAG, msg);
            Logger.saveLog(context, msg);
            loc = tryCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 8, TimeUnit.SECONDS);
        }

        // STEP 3: Fallback to Last Known Location (Accepting a wider margin of error)
        if (loc == null || !isValidAccuracy(loc, 150.0f)) {
            String msg = String.format(TAG + ": Fresh locations failed. Attempting getLastLocation fallback.");
            Log.d(TAG, msg);
            Logger.saveLog(context, msg);
            loc = tryLastLocationFallback();
        }

        return loc;
    }

    @WorkerThread
    private Location tryCurrentLocation(int priority, long timeout, TimeUnit unit) {
        CancellationTokenSource cancellationTokenSource = new CancellationTokenSource();
        try {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return null;
            }
            return Tasks.await(
                    fusedLocationClient.getCurrentLocation(priority, cancellationTokenSource.getToken()),
                    timeout,
                    unit
            );
        } catch (TimeoutException e) {
            String msg = String.format(TAG + ": getCurrentLocation timed out for priority: " + priority);
            Log.w(TAG, msg);
            Logger.saveLog(context, msg);
            cancellationTokenSource.cancel(); // Stop the sensor
        } catch (Exception e) {
            String msg = String.format(TAG + ": Exception in getCurrentLocation", e);
            Log.e(TAG, msg);
            Logger.saveLog(context, msg);
            cancellationTokenSource.cancel();
        }
        return null;
    }

    @WorkerThread
    private Location tryLastLocationFallback() {
        try {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return null;
            }
            Location lastLoc = Tasks.await(fusedLocationClient.getLastLocation(), 2, TimeUnit.SECONDS);

            // Loosen up the restrictions. 150-200m is acceptable when the alternative is failure.
            if (lastLoc != null && isValidAccuracy(lastLoc, 200.0f)) {

                String msg = String.format(TAG + ": Using lastLoc. Accuracy: " + lastLoc.getAccuracy() + "m");
                Log.d(TAG, msg);
                Logger.saveLog(context, msg);
                return lastLoc;
            }
        } catch (Exception e) {

            String msg = String.format(TAG + ": Exception in getLastLocation", e);
            Log.e(TAG, msg);
            Logger.saveLog(context, msg);
        }
        return null;
    }




}
