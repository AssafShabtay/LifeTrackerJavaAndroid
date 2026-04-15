package com.example.myapplication.locationTracking;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.example.myapplication.helpers.Logger;
import com.google.android.gms.location.ActivityTransition;
import com.google.android.gms.location.ActivityTransitionEvent;
import com.google.android.gms.location.ActivityTransitionResult;
import com.google.android.gms.location.DetectedActivity;

public class ActivityTransitionReceiver extends BroadcastReceiver {

    private static final String TAG = "TransitionReceiver";
    public static final String ACTION_ACTIVITY_UPDATE = "com.example.myapplication.ACTIVITY_UPDATE";
    public static final String EXTRA_ACTIVITY_TYPE = "activity_type";
    public static final String EXTRA_TRANSITION_TYPE = "transition_type";
    public static final String EXTRA_TIMESTAMP_NANOS = "timestamp_nanos";

    @Override
    public void onReceive(Context context, Intent intent) {
        String msg = "onReceive: Activity transition broadcast received";
        Log.d(TAG, msg);
        Logger.saveLog(context, msg);
        
        if (ActivityTransitionResult.hasResult(intent)) {
            ActivityTransitionResult result = ActivityTransitionResult.extractResult(intent);
            if (result != null) {
                long receiptTimestampNanos = android.os.SystemClock.elapsedRealtimeNanos();
                for (ActivityTransitionEvent event : result.getTransitionEvents()) {
                    String eventMsg = "onReceive: Processing transition for " + getActivityName(event.getActivityType()) + " (" + getTransitionName(event.getTransitionType()) + ")";
                    Log.d(TAG, eventMsg);
                    Logger.saveLog(context, eventMsg);
                    notifyService(context, event.getActivityType(), event.getTransitionType(), receiptTimestampNanos);
                }
            }
        }
    }

    private String getActivityName(int activityType) {
        switch (activityType) {
            case DetectedActivity.IN_VEHICLE: return "Driving";
            case DetectedActivity.ON_BICYCLE: return "Cycling";
            case DetectedActivity.ON_FOOT: return "On Foot";
            case DetectedActivity.RUNNING: return "Running";
            case DetectedActivity.WALKING: return "Walking";
            case DetectedActivity.STILL: return "Still";
            default: return "Unknown (" + activityType + ")";
        }
    }

    private String getTransitionName(int transitionType) {
        switch (transitionType) {
            case ActivityTransition.ACTIVITY_TRANSITION_ENTER: return "ENTER";
            case ActivityTransition.ACTIVITY_TRANSITION_EXIT: return "EXIT";
            default: return "UNKNOWN (" + transitionType + ")";
        }
    }

    private void notifyService(Context context, int activityType, int transitionType, long timestampNanos) {
        String msg = "notifyService: Sending activity update to LocationService for DB processing";
        Log.d(TAG, msg);
        Logger.saveLog(context, msg);
        
        Intent serviceIntent = new Intent(context, LocationService.class);
        serviceIntent.setAction(ACTION_ACTIVITY_UPDATE);
        serviceIntent.putExtra(EXTRA_ACTIVITY_TYPE, activityType);
        serviceIntent.putExtra(EXTRA_TRANSITION_TYPE, transitionType);
        serviceIntent.putExtra(EXTRA_TIMESTAMP_NANOS, timestampNanos);

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
