package com.example.myapplication.locationTracking;

import static com.example.myapplication.locationTracking.ActivityTrackingUtils.isValidAccuracy;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
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
    private final ExecutorService databaseWriteExecutor;
    private final GeofenceUtilsManager geofenceUtilsManager;

    private LocationCallback routeLocationCallback;
    private LocationCallback stillLocationCallback;
    private volatile boolean isRequestingStillLocationUpdates = false;
    private static final long LINGER_TIME_THRESHOLD_MS = 10 * 60 * 1000;

    private static final float STILL_RADIUS_THRESHOLD = 75.0f;
    private Location anchorLocation = null;
    private long anchorLocationTime = 0;
    private final LocationService locationService;
    public LocationProvider(Context context,
                                FusedLocationProviderClient fusedLocationClient,
                                ActivityDao dao,
                                ExecutorService databaseWriteExecutor,
                                GeofenceUtilsManager geofenceUtilsManager,
    LocationService locationService) {
        this.context = context;
        this.fusedLocationClient = fusedLocationClient;
        this.dao = dao;
        this.databaseWriteExecutor = databaseWriteExecutor;
        this.geofenceUtilsManager = geofenceUtilsManager;
        this.locationService = locationService;
    }

    public boolean isRequestingStillLocationUpdates() {
        return isRequestingStillLocationUpdates;
    }
    void startRouteUpdates() {
        if (routeLocationCallback != null) return;

        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 30000) // 30 seconds
                .setMinUpdateIntervalMillis(10000) // 10 seconds
                .setMaxUpdateDelayMillis(60000) // 60 seconds
                .build();

        routeLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {

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
                        if (distance <= STILL_RADIUS_THRESHOLD) { // 50 meters
                            // User is still within the stationary radius,  check the time.
                            if (currentTime - anchorLocationTime >= LINGER_TIME_THRESHOLD_MS) {
                                // if user is still for over 10 minutes, stop tracking
                                Log.i(TAG, "User lingered for over 10 minutes. Stopping tracking.");
                                Logger.saveLog(context, "User lingered for over 10 minutes. Stopping tracking.");

                                Date endTime = new Date(currentTime);
                                databaseWriteExecutor.execute(() -> {
                                    Set<Integer> activeMovementTypes = new HashSet<>(LocationService.currentMovementTrackingIds.keySet());
                                    for (Integer activityType : activeMovementTypes) {
                                        locationService.endMovementTracking(activityType, endTime);
                                    }
                                    locationService.startStillTracking(endTime, null);
                                    locationService.updateCurrentActivityType(DetectedActivity.STILL);
                                });

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
                databaseWriteExecutor.execute(() -> {
                    // Insert the route point
                    for (Location location : locationResult.getLocations()) {
                        for (Long movementId : LocationService.currentMovementTrackingIds.values()) {
                            RoutePoint point = new RoutePoint();
                            point.setMovementActivityId(movementId);
                            point.setLat(location.getLatitude());
                            point.setLng(location.getLongitude());
                            point.setTimestamp(location.getTime());
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
            stopRouteUpdates();
            Intent PermIntent = new Intent("com.example.myapplication.PERMISSION_REVOKED").setPackage(context.getPackageName());;
            context.sendBroadcast(PermIntent);

        }
    }

    void stopRouteUpdates() {
        if (routeLocationCallback != null) {
            fusedLocationClient.removeLocationUpdates(routeLocationCallback);
            routeLocationCallback = null;
        }
    }

    // Start frequent location updates for still activities without a location to find their location
    void startFrequentStillLocationUpdates() {
        if (stillLocationCallback != null) return;
        if (LocationService.currentStillTrackingId == null) return;

        Log.d(TAG, "Starting frequent still location updates for ID=" + LocationService.currentStillTrackingId);
        Logger.saveLog(context, "Starting frequent still location updates");

        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 30000) // Every 5 seconds
                .setMinUpdateIntervalMillis(20000) // 20 seconds
                .setMaxUpdateDelayMillis(90000) // 1 min and 30 seconds
                .build();

        stillLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) return;
                databaseWriteExecutor.execute(() -> {
                    for (Location location : locationResult.getLocations()) {
                        if (location.hasAccuracy() && location.getAccuracy() <= STILL_RADIUS_THRESHOLD) {
                            StillLocation still = dao.getStillLocationById(LocationService.currentStillTrackingId);
                            if (still != null && (still.getLat() == null || still.getLng() == null)) {
                                still.setLat(location.getLatitude());
                                still.setLng(location.getLongitude());

                                // try to find a place for the still
                                geofenceUtilsManager.findPlaceAndUpdateStill(location, still);

                                dao.updateStillLocation(still);

                                String msg = String.format(Locale.US, "DB Update from frequentStillLocation: Updated still %d with location [%.6f, %.6f]", still.getId(), still.getLat(), still.getLng());
                                Log.d(TAG, msg);
                                Logger.saveLog(context, msg);

                                stopFrequentStillLocationUpdates(); // Stop updates once location is acquired
                                return;
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
            Intent PermIntent = new Intent("com.example.myapplication.PERMISSION_REVOKED").setPackage(context.getPackageName());;
            context.sendBroadcast(PermIntent);
            stopFrequentStillLocationUpdates();
        }
    }

    // Stop frequent location updates for still activities
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
        Location loc;

        // Try  High Accuracy
        loc = tryCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, 12, TimeUnit.SECONDS);

        //Try Balanced Accuracy
        if (!isValidAccuracy(loc, STILL_RADIUS_THRESHOLD)) {
            String msg = String.format(TAG + ": High accuracy failed or too inaccurate. Trying Balanced Power...");
            Log.d(TAG, msg);
            Logger.saveLog(context, msg);
            loc = tryCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 8, TimeUnit.SECONDS);
        }

        //Fallback to Last Known Location
        if (!isValidAccuracy(loc, STILL_RADIUS_THRESHOLD * 1.6f)) {
            String msg = String.format(TAG + ": Fresh locations failed. Attempting getLastLocation fallback.");
            Log.d(TAG, msg);
            Logger.saveLog(context, msg);
        }

        return loc;
    }

    @WorkerThread
    private Location tryCurrentLocation(int priority, long timeout, TimeUnit unit) {
        CancellationTokenSource cancellationTokenSource = new CancellationTokenSource();
        try {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Intent PermIntent = new Intent("com.example.myapplication.LOCATION_PERMISSION_REVOKED").setPackage(context.getPackageName());;
                context.sendBroadcast(PermIntent);

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
            cancellationTokenSource.cancel();
        } catch (Exception e) {
            String msg = String.format(TAG + ": Exception in getCurrentLocation", e);
            Log.e(TAG, msg);
            Logger.saveLog(context, msg);
            cancellationTokenSource.cancel();
        }
        return null;
    }

    //@WorkerThread
    //private Location tryLastLocationFallback() {
    //    try {
    //        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
    //            return null;
    //        }
    //        Location lastLoc = Tasks.await(fusedLocationClient.getLastLocation(), 2, TimeUnit.SECONDS);
//
    //        // Loosen up the restrictions. 150-200m is acceptable when the alternative is failure.
    //        if (lastLoc != null && isValidAccuracy(lastLoc, 200.0f)) {
//
    //            String msg = String.format(TAG + ": Using lastLoc. Accuracy: " + lastLoc.getAccuracy() + "m");
    //            Log.d(TAG, msg);
    //            Logger.saveLog(context, msg);
    //            return lastLoc;
    //        }
    //    } catch (Exception e) {
//
    //        String msg = String.format(TAG + ": Exception in getLastLocation", e);
    //        Log.e(TAG, msg);
    //        Logger.saveLog(context, msg);
    //    }
    //    return null;
    //}




}
