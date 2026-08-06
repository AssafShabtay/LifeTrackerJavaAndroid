package com.example.myapplication.locationTracking.receiver;

import static com.example.myapplication.locationTracking.ActivityTrackingUtils.getActivityName;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.example.myapplication.helpers.Logger;
import com.example.myapplication.locationTracking.LocationService;
import com.google.android.gms.location.ActivityTransition;
import com.google.android.gms.location.ActivityTransitionEvent;
import com.google.android.gms.location.ActivityTransitionResult;
import com.google.android.gms.location.DetectedActivity;

public class ActivityTransitionReceiver extends BroadcastReceiver {

    private static final String TAG = "TransitionReceiver";
    public static final String ACTION_ACTIVITY_UPDATE = "com.example.myapplication.ACTIVITY_UPDATE";
    public static final String EXTRA_ACTIVITY_TYPE = "activity_type";
    public static final String EXTRA_TRANSITION_TYPE = "transition_type";
    public static final String EXTRA_TIMESTAMP_MS = "timestamp_ms";

    @Override
    public void onReceive(Context context, Intent intent) {
        String msg = "onReceive: Activity transitions broadcast received";
        Log.d(TAG, msg);
        Logger.saveLog(context, msg);

        if (ActivityTransitionResult.hasResult(intent)) {
            ActivityTransitionResult result = ActivityTransitionResult.extractResult(intent);
            if (result != null) {
                for (ActivityTransitionEvent event : result.getTransitionEvents()) {
                    long eventElapsedNanos = event.getElapsedRealTimeNanos();
                    long ageMs = (android.os.SystemClock.elapsedRealtimeNanos() - eventElapsedNanos) / 1_000_000L;
                    long eventTimestampMs = System.currentTimeMillis() - ageMs;
                    String eventMsg = "onReceive: Processing transition for " + getActivityName(event.getActivityType()) + " (" + getTransitionName(event.getTransitionType()) + ")";
                    Log.d(TAG, eventMsg);
                    Logger.saveLog(context, eventMsg);
                    notifyService(context, event.getActivityType(), event.getTransitionType(), eventTimestampMs);
                }
            }
        }

    }




    private String getTransitionName(int transitionType) {
        return switch (transitionType) {
            case ActivityTransition.ACTIVITY_TRANSITION_ENTER -> "ENTER";
            case ActivityTransition.ACTIVITY_TRANSITION_EXIT -> "EXIT";
            default -> "UNKNOWN (" + transitionType + ")";
        };
    }

    private void notifyService(Context context, int activityType, int transitionType, long timestampMs) {
        String msg = "notifyService: Sending activity update to LocationService for DB processing";
        Log.d(TAG, msg);
        Logger.saveLog(context, msg);

        Intent serviceIntent = new Intent(context, LocationService.class);
        serviceIntent.setAction(ACTION_ACTIVITY_UPDATE);
        serviceIntent.putExtra(EXTRA_ACTIVITY_TYPE, activityType);
        serviceIntent.putExtra(EXTRA_TRANSITION_TYPE, transitionType);
        serviceIntent.putExtra(EXTRA_TIMESTAMP_MS, timestampMs);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        } catch (Exception e) {
            String errorMsg = "notifyService: Failed to start service from receiver: " + e.getMessage();
            Log.e(TAG, errorMsg);
            Logger.saveLog(context, errorMsg);
        }
    }
}