package com.example.myapplication.locationTracking;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.util.Log;

import com.example.myapplication.database.RoutePoint;
import com.example.myapplication.helpers.Logger;
import com.google.android.gms.location.DetectedActivity;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Locale;

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

    public static boolean checkIfMovementIsStill (List<RoutePoint> routePoints, double startLat, double startLng, double endLat, double endLng) {

        if (routePoints == null || routePoints.size() <= 1) {
            if (distanceInMeters(startLat, startLng, endLat, endLng) <  75f){
                return true;
            }
            return false;
        }
        // finds the center of all the route points
        // Calculate the Centroid (Average Lat and Lng)
        double sumLat = 0;
        double sumLng = 0;
        for (RoutePoint point : routePoints) {
            sumLat += point.getLat();
            sumLng += point.getLng();
        }
        double centroidLat = sumLat / routePoints.size();
        double centroidLng = sumLng / routePoints.size();

        int pointsInsideRadius = 0;

        // Iterate through points to calculate route distance and max radius
        for (int i = 0; i < routePoints.size(); i++) {
            RoutePoint currentPoint = routePoints.get(i);
            // Calculate radius (Distance from centroid to current point)
            double distanceFromCentroid = (double) distanceInMeters(centroidLat, centroidLng, currentPoint.getLat(), currentPoint.getLng());

            if (distanceFromCentroid <= ALLOWED_RADIUS_METERS) {
                pointsInsideRadius++;
            }
        }

        double insideRatio = (double) pointsInsideRadius / routePoints.size();
        return insideRatio >= MIN_REQUIRED_INSIDE_RATIO; // returns true if the ratio is above the threshold, which concludes the activity is a still-**

    }
    public static String checkIfStillIsMovement(double startLat, double startLng, Date startTime, Date endTime, double endLat, double endLng, Context context) {
        // 1. Prevent NullPointerExceptions if dates are missing
        if (startTime == null || endTime == null) {
            return "Still";
        }

        float distance = distanceInMeters(startLat, startLng, endLat, endLng);
        long durationMs = endTime.getTime() - startTime.getTime();
        float durationSec = durationMs / 1000f;

        // 2. Prevent division by zero and handle anomalous negative time jumps
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

        // 3. Sanity check: Impossible speeds usually indicate a GPS drift/jump
        if (speed > MAX_REALISTIC_SPEED) {
            String msg = String.format(Locale.US, "Sanity check failed: Speed %.2f m/s exceeds realistic limits. Flagged as drift.", speed);
            // Log.d(TAG, msg); // Assuming TAG is declared elsewhere in your class

            // Prevent a crash if the Context passed is null
            if (context != null) {
                Logger.saveLog(context, msg);
            }
            return "Still";
        }

        // 4. Drift Filter: If the distance is inside standard GPS error margins,
        // OR if the distance is slightly larger but the speed is a crawl (< 0.5 m/s or 1.8 km/h).
        if (distance < GPS_NOISE_RADIUS || (distance < 75f && speed < 0.5f)) {
            return "Still";
        }

        // 5. Determine State by Speed
        if (speed <= MAX_WALK_SPEED) return "Walking";
        if (speed <= MAX_RUN_SPEED) return "Running";

        // 6. Fixed Logic: If speed > MAX_RUN_SPEED and it hasn't been flagged as GPS drift,
        // it MUST be a vehicle. The previous 2-minute requirement caused valid short drives
        // to fall through and return "Still".
        return "Driving";
    }

    public static float distanceInMeters(double startLat, double startLng, double endLat, double endLng) {
        float[] results = new float[1];
        Location.distanceBetween(startLat, startLng, endLat, endLng, results);
        return results[0];
    }
    public static double[] getCoordinatesFromAddress(String address, Context context) {
        Geocoder geocoder = new Geocoder(context, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocationName(address, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address location = addresses.get(0);
                return new double[]{location.getLatitude(), location.getLongitude()};
            }
        } catch (IOException e) {
            Log.e("StatisticsFragment", "Geocoding failed", e);
        }
        return null;
    }
    public static double[] calculateRadiusBox(double lat, double lng, double radiusInMeters) {
        // some shity math I wont try to understand to calculate the radius
        double latDelta = radiusInMeters / 111320.0;
        double lngDelta = radiusInMeters / (111320.0 * Math.cos(Math.toRadians(lat)));

        double minLat = lat - latDelta;
        double maxLat = lat + latDelta;
        double minLng = lng - lngDelta;
        double maxLng = lng + lngDelta;

        return new double[]{minLat, maxLat, minLng, maxLng};
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
