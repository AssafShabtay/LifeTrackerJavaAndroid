package com.example.myapplication.locationTracking;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;

import java.util.ArrayList;
import java.util.List;

public class GeofenceManager {
    private static final String TAG = "GeofenceManager";
    private final GeofencingClient geofencingClient;
    private final Context context;
    private PendingIntent geofencePendingIntent;

    public GeofenceManager(Context context) {
        this.context = context;
        this.geofencingClient = LocationServices.getGeofencingClient(context);
    }

    @SuppressLint("MissingPermission")
    public void addGeofence(String requestId, double lat, double lng, float radius) {
        Geofence geofence = new Geofence.Builder()
                .setRequestId(requestId)
                .setCircularRegion(lat, lng, radius)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER | 
                                    Geofence.GEOFENCE_TRANSITION_EXIT | 
                                    Geofence.GEOFENCE_TRANSITION_DWELL)
                .setLoiteringDelay(120000) // 2 minutes to confirm stay
                .build();

        GeofencingRequest request = new GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofence(geofence)
                .build();

        geofencingClient.addGeofences(request, getGeofencePendingIntent())
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Geofence added: " + requestId))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to add geofence: " + requestId, e));
    }

    public void removeGeofence(String requestId) {
        List<String> ids = new ArrayList<>();
        ids.add(requestId);
        geofencingClient.removeGeofences(ids)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Geofence removed: " + requestId))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to remove geofence: " + requestId, e));
    }

    private PendingIntent getGeofencePendingIntent() {
        if (geofencePendingIntent != null) {
            return geofencePendingIntent;
        }
        Intent intent = new Intent(context, GeofenceBroadcastReceiver.class);
        geofencePendingIntent = PendingIntent.getBroadcast(context, 0, intent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        return geofencePendingIntent;
    }
}
