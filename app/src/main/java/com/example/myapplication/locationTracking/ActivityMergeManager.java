package com.example.myapplication.locationTracking;

import static com.example.myapplication.locationTracking.ActivityTrackingUtils.distanceInMeters;

import android.location.Location;
import android.util.Log;

import com.example.myapplication.database.ActivityDao;
import com.example.myapplication.database.MovementActivity;
import com.example.myapplication.database.StillLocation;
import com.example.myapplication.helpers.Logger;

import java.util.Date;
import java.util.Locale;

public class ActivityMergeManager {
    private static final String TAG = "ActivityMergeManager";

    private final ActivityDao dao;
    private final LocationService locationService;
    private final LocationProvider locationProvider;
    public static float DISTANCE_THRESHOLD_FOR_STILL_MERGE_METERS = 75f;
    public static float MAX_GAP_THRESHOLD_FOR_STILL_MERGE_MS = 180000f; // 3 minutes
    public static float MIN_GAP_THRESHOLD_FOR_STILL_MERGE_MS = -20000f;
    public ActivityMergeManager(ActivityDao dao, LocationService locationService, LocationProvider locationProvider) {
        this.dao = dao;
        this.locationService = locationService;
        this.locationProvider = locationProvider;
    }

    boolean attemptMergeWithOngoingStill(Long currentStillTrackingId, Date startTime, Location currentLocation, GeofenceUtilsManager geofenceUtilsManager){
        // fun that tries to merge a new still activity with an ongoing still activity
        // If successful returns true else false

        if (currentStillTrackingId != null) {
            StillLocation activeStill = dao.getStillLocationById(currentStillTrackingId);
            if (activeStill != null && activeStill.getLat() != null && activeStill.getLng() != null && currentLocation != null) {
                float distance = distanceInMeters(currentLocation.getLatitude(), currentLocation.getLongitude(), activeStill.getLat(), activeStill.getLng());
                if (distance < DISTANCE_THRESHOLD_FOR_STILL_MERGE_METERS) {
                    String msg = String.format(Locale.US, "DB Update from startStillTracking: Extending current active still %d (location-based merge at [%.6f, %.6f])", activeStill.getId(), currentLocation.getLatitude(), currentLocation.getLongitude());
                    Log.d(TAG, msg);
                    Logger.saveLog(locationService, msg);
                    dao.updateStillEndTime(activeStill.getId(), null);
                    Log.d(TAG, "STILL already active at similar location: ID=" + currentStillTrackingId);
                    // If we successfully merged and the unactive still already has a location, stop any frequent updates.
                    if (locationProvider.isRequestingStillLocationUpdates() && activeStill.getLat() != null) {
                        locationProvider.stopFrequentStillLocationUpdates();
                    }
                    return true;
                } else {
                    // Ending the previous still activity if the distance is too far
                    locationService.endStillTracking(startTime);
                    return false;
                }

            }else if (activeStill != null) {
                // If the ongoing location doesnt have a location but we have the current location,
                // Merge and update the ongoing still with the current location if available
                if ((activeStill.getLat() == null || activeStill.getLng() == null) && currentLocation != null) {
                    //current location is available, so merge
                    activeStill.setLat(currentLocation.getLatitude());
                    activeStill.setLng(currentLocation.getLongitude());
                    geofenceUtilsManager.findPlaceAndUpdateStill(currentLocation, activeStill);
                    dao.updateStillLocation(activeStill);
                    // location obtained, stop frequent updates
                    if (locationProvider.isRequestingStillLocationUpdates()) {
                        locationProvider.stopFrequentStillLocationUpdates();
                    }
                }
                String msg = String.format(Locale.US, "2DB Update from startStillTracking: Extending current active still %d (location-based merge at [%.6f, %.6f])", activeStill.getId(), currentLocation.getLatitude(), currentLocation.getLongitude());
                Log.d(TAG, msg);
                Logger.saveLog(locationService, msg);
                Log.d(TAG, "STILL already active (location missing). Merging ID=" + currentStillTrackingId);
                return true;
            }
        }
        String msg = String.format("not externding");
        Log.d(TAG, msg);
        Logger.saveLog(locationService, msg);
        return false;
    }

    boolean attemptMergeWithLastCompletedStill(Long currentStillTrackingId, Date startTime, Location currentLocation,  StillLocation lastStill) {

        if (lastStill != null) {
            boolean shouldMerge = false;

            // if the last still is close enough, merge
            if (lastStill.getLat() != null && lastStill.getLng() != null && currentLocation != null) {
                float distance = distanceInMeters(currentLocation.getLatitude(), currentLocation.getLongitude(), lastStill.getLat(), lastStill.getLng());
                if (distance < DISTANCE_THRESHOLD_FOR_STILL_MERGE_METERS) {
                    shouldMerge = true;
                }
            }
            //else if ((lastStill.lat != null || lastStill.lng == null) && currentLocation == null) {
            //    //TODO IMPLEMENT FALLBACK TO NOT HAVING A LOCATION
            //}
            //else if ((lastStill.lat == null || lastStill.lng == null) & currentLocation !=null) {
            //    //TODO IMPLEMENT FALLBACK TO NOT HAVING A LOCATION
            //}
            // if there is no location but the last still is close enough in time, merge
            else if (lastStill.getEndTimeDate() != null) {
                long gapMs = startTime.getTime() - lastStill.getEndTimeDate().getTime();
                // If the gap is less than the max threshold, merge
                if (gapMs >= MIN_GAP_THRESHOLD_FOR_STILL_MERGE_MS && gapMs < MAX_GAP_THRESHOLD_FOR_STILL_MERGE_MS) {
                    shouldMerge = true;
                }
            }


            if (shouldMerge) {
                // Check for intervening movement activities
                if (lastStill.getEndTimeDate() != null && startTime != null && dao.countMovementActivitiesBetween(lastStill.getEndTimeDate(), startTime) > 0) {
                    String msg = String.format(Locale.US, "Not merging with last still %d due to intervening movement activity between %s and %s",
                            lastStill.getId(), lastStill.getEndTimeDate().toString(), startTime.toString());
                    Log.d(TAG, msg);
                    Logger.saveLog(locationService, msg);
                    return false; // Do not merge if there was movement
                }

                String msg = String.format(Locale.US, "DB Update from startStillTracking: Merging with last still %d %s", currentStillTrackingId,
                        (currentLocation == null ? "(Time-based fallback)" : String.format(Locale.US, "at [%.6f, %.6f]", currentLocation.getLatitude(), currentLocation.getLongitude())));
                Log.d(TAG, msg);
                Logger.saveLog(locationService, msg);

                if (currentStillTrackingId != null) {
                    dao.updateStillEndTime(currentStillTrackingId, null);
                    Log.d(TAG, "STILL merged with last: ID=" + currentStillTrackingId);
                } else {
                    dao.updateStillEndTime(lastStill.getId(), null);
                    Log.d(TAG, "STILL merged with last: ID=" + lastStill.getId());
                }
                // If merged with a still that already has a location, stop location updates
                if (locationProvider.isRequestingStillLocationUpdates() && lastStill.getLat() != null) {
                    locationProvider.stopFrequentStillLocationUpdates();
                }
                return true;
            }

        }
        return false;
    }
    boolean attemptMergeWithLastCompletedStillEnd(Long id, Location currentLocation,  StillLocation lastStill) {
        if (lastStill != null && lastStill.getLat() != null && lastStill.getLng() != null && currentLocation != null) {
            float distance = distanceInMeters(currentLocation.getLatitude(), currentLocation.getLongitude(), lastStill.getLat(), lastStill.getLng());
            if (distance < DISTANCE_THRESHOLD_FOR_STILL_MERGE_METERS) { // meter threshold for merging
                // merge with last still
                dao.deleteStillLocation(id);
                long lastStillId = lastStill.getId();
                String msg = String.format(Locale.US, "DB Update from endStillTracking: Merging with last still %d at [%.6f, %.6f]", id, currentLocation.getLatitude(), currentLocation.getLongitude());
                Log.d(TAG, msg);
                Logger.saveLog(locationService, msg);
                dao.updateStillEndTime(lastStillId, null);
                Log.d(TAG, "STILL merged with last: ID=" + lastStillId);
                return true;
            }
        }
        return false;
    }


    Long attemptMergeWithOngoingMovement(String activityName) {
        // Check for an ongoing activity of the same type
        MovementActivity activeMovement = dao.getActiveMovementActivityByType(activityName);
        if (activeMovement != null) {
            String msg = "DB Update: Found active " + activityName + " activity " + activeMovement.getId() + ". Resuming.";
            Log.d(TAG, msg);
            Logger.saveLog(locationService, msg);
            locationProvider.startRouteUpdates();
            return activeMovement.getId();
        }
        return null;
    }

    Long attemptMergeWithLastCompletedMovement(String activityName, Date startTime) {
        // Check if a similar activity ended recently, if so, merge
        MovementActivity lastMovement = dao.getLastCompletedMovementActivity(activityName);
        if (lastMovement != null && lastMovement.getEndTimeDate() != null) {
            long gapMs = startTime.getTime() - lastMovement.getEndTimeDate().getTime();
            if (gapMs >= -20000 && gapMs < 180000) { // 3 minute threshold, allowing for small overlaps/jitter
                String msg = "DB Update: Resuming recent " + activityName + " activity " + lastMovement.getId() + " (gap: " + (gapMs/1000) + "s)";
                Log.d(TAG, msg);
                Logger.saveLog(locationService, msg);
                dao.resumeMovementActivity(lastMovement.getId());
                locationProvider.startRouteUpdates();
                return lastMovement.getId();
            } else {
                Log.d(TAG, "Not merging " + activityName + ". Gap: " + gapMs + "ms");
            }
        }
        return null;
    }
}