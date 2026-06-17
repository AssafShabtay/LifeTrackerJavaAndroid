package com.example.myapplication.locationTracking;

import android.content.Context;
import android.location.Location;
import android.util.Log;

import com.example.myapplication.database.RoutePoint;
import com.example.myapplication.helpers.Logger;
import com.google.android.gms.location.DetectedActivity;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ActivityTrackingUtils {

    private static final String TAG = "ActivityTrackingUtils";

    private static final double ALLOWED_RADIUS_METERS = 50.0;
    private static final double MIN_REQUIRED_INSIDE_RATIO = 0.50;
    public static boolean isValidAccuracy(Location location, float maxAllowedMeters) {
        if (location == null) return false;
        // Some devices occasionally return locations without accuracy details
        if (!location.hasAccuracy()) return true;
        return location.getAccuracy() <= maxAllowedMeters;
    }

    public static boolean checkIfMovementIsStill (List<RoutePoint> routePoints) {
        // finds the center of all the route points
        if (routePoints == null || routePoints.size() <= 1) {
            return true;
        }
        // Calculate the Centroid (Average Lat and Lng)
        double sumLat = 0;
        double sumLng = 0;
        for (RoutePoint point : routePoints) {
            sumLat += point.lat;
            sumLng += point.lng;
        }
        double centroidLat = sumLat / routePoints.size();
        double centroidLng = sumLng / routePoints.size();

        int pointsInsideRadius = 0;

        // Iterate through points to calculate route distance and max radius
        for (int i = 0; i < routePoints.size(); i++) {
            RoutePoint currentPoint = routePoints.get(i);
            // Calculate radius (Distance from centroid to current point)
            double distanceFromCentroid = (double) distanceInMeters(centroidLat, centroidLng, currentPoint.lat, currentPoint.lng);

            if (distanceFromCentroid <= ALLOWED_RADIUS_METERS) {
                pointsInsideRadius++;
            }
        }

        double insideRatio = (double) pointsInsideRadius / routePoints.size();
        return insideRatio >= MIN_REQUIRED_INSIDE_RATIO; // returns true if the ratio is above the threshold, which concludes the activity is a still-**

    }
    public static String checkIfStillIsMovement(double startLat, double startLng, Date startTime, Date endTime, double endLat, double endLng, Context context) {
        float distance = distanceInMeters(startLat, startLng, endLat, endLng);
        long durationMs = endTime.getTime() - startTime.getTime();
        float durationSec = durationMs / 1000f;

        // Prevent division by zero and handle anomalous negative time jumps
        if (durationSec <= 0) return "Still";
        float speed = distance / durationSec;
        // --- REAL WORLD CONSTANTS ---
        // GPS wander is typically 10-20 meters. 25 meters safely filters stationary device drift.
        final float GPS_NOISE_RADIUS = 25f;
        // 2.2 m/s = ~7.9 km/h (Brisk human walking caps around here)
        final float MAX_WALK_SPEED = 2.2f;
        // 7.5 m/s = ~27.0 km/h (Covers sprinting and average cycling)
        final float MAX_RUN_SPEED = 7.5f;
        // 50.0 m/s = ~180 km/h (Speeds above this are highly likely to be GPS multi-path errors/jumps)
        final float MAX_REALISTIC_SPEED = 50.0f;

        // Sanity check: Impossible speeds usually indicate a GPS drift/jump, regardless of distance.
        if (speed > MAX_REALISTIC_SPEED) {
            String msg = String.format("Sanity check failed: Speed %.2f m/s exceeds realistic limits. Flagged as drift.", speed);
            Log.d(TAG, msg);
            Logger.saveLog(context, msg);
            return "Still";
        }

        // Drift Filter: If the distance is inside standard GPS error margins,
        // OR if the distance is slightly larger but the speed is a crawl (< 0.5 m/s or 1.8 km/h).
        if (distance < GPS_NOISE_RADIUS || (distance < 50f && speed < 0.5f)) {
            return "Still";
        }

        if (speed <= MAX_WALK_SPEED) return "Walking";
        if (speed <= MAX_RUN_SPEED) return "Running";


        if (durationMs > 120000) {
            return "Driving";
        }
        return "Still";
    }

    public static float distanceInMeters(double startLat, double startLng, double endLat, double endLng) {
        float[] results = new float[1];
        Location.distanceBetween(startLat, startLng, endLat, endLng, results);
        return results[0];
    }

    public static String getActivityName(int activityType) {
        switch (activityType) {
            case DetectedActivity.IN_VEHICLE: return "Driving";
            case DetectedActivity.ON_BICYCLE: return "Cycling";
            case DetectedActivity.ON_FOOT: return "Walking";
            case DetectedActivity.RUNNING: return "Running";
            case DetectedActivity.WALKING: return "Walking";
            case DetectedActivity.STILL: return "Still";
            default: return "Unknown";
        }
    }
    public static int getActivityTypeFromName(String name) {
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

}
