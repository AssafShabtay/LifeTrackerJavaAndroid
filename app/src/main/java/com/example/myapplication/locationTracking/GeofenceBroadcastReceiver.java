package com.example.myapplication.locationTracking;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;

import java.util.List;

public class GeofenceBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "GeofenceReceiver";

    public static final String ACTION_GEOFENCE_UPDATE = "com.example.myapplication.GEOFENCE_UPDATE";
    public static final String EXTRA_GEOFENCE_ID = "geofence_id";
    public static final String EXTRA_TRANSITION_TYPE = "transition_type";

    @Override
    public void onReceive(Context context, Intent intent) {

        GeofencingEvent geofencingEvent = GeofencingEvent.fromIntent(intent);
        if (geofencingEvent == null || geofencingEvent.hasError()) {
            Log.e(TAG, "geofence error or null event");
            return;
        }

        int transitionType = geofencingEvent.getGeofenceTransition();
        List<Geofence> triggeringGeofences = geofencingEvent.getTriggeringGeofences();

        if (triggeringGeofences != null) {
            for (Geofence geofence : triggeringGeofences) {
                notifyService(context, geofence.getRequestId(), transitionType);
            }
        }
    }

    private void notifyService(Context context, String geofenceId, int transitionType) {
        Intent serviceIntent = new Intent(context, LocationService.class);
        serviceIntent.setAction(ACTION_GEOFENCE_UPDATE);
        serviceIntent.putExtra(EXTRA_GEOFENCE_ID, geofenceId);
        serviceIntent.putExtra(EXTRA_TRANSITION_TYPE, transitionType);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }
    }
}
