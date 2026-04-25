package com.example.myapplication.locationTracking;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.example.myapplication.helpers.Logger;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityRecognitionResult;
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
        String msg = "onReceive: Activity transitions broadcast received";
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

        } else if (ActivityRecognitionResult.hasResult(intent)) { //TODO THIS CHUNK MIGHT BE USELESS
            ActivityRecognitionResult result = ActivityRecognitionResult.extractResult(intent);
            if (result != null) {
                DetectedActivity activity = result.getMostProbableActivity();
                String activityMsg = "onReceive: Initial activity detection: " + getActivityName(activity.getType()) + " confidence: " + activity.getConfidence();
                Log.d(TAG, activityMsg);
                Logger.saveLog(context, activityMsg);//TODO THIS CHUNK MIGHT BE USELESS
                
                if (activity.getConfidence() >= 75 && activity.getType() != DetectedActivity.UNKNOWN) {
                    // Treat the first recognized activity as an ENTER transition to kickstart tracking
                    notifyService(context, activity.getType(), ActivityTransition.ACTIVITY_TRANSITION_ENTER, android.os.SystemClock.elapsedRealtimeNanos());
                    
                    // After getting a solid initial activity, stop periodic updates to save battery and rely on transitions
                    stopActivityUpdates(context);//TODO THIS CHUNK MIGHT BE USELESS
                }
            }
        }//TODO THIS CHUNK MIGHT BE USELESS
    }
    //TODO THIS CHUNK MIGHT BE USELESS
    private void stopActivityUpdates(Context context) {
        Log.d(TAG, "Stopping periodic activity updates, relying on transitions now.");//TODO THIS CHUNK MIGHT BE USELESS
        Intent intent = new Intent(context, ActivityTransitionReceiver.class);//TODO THIS CHUNK MIGHT BE USELESS
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {//TODO THIS CHUNK MIGHT BE USELESS
            flags |= PendingIntent.FLAG_IMMUTABLE;//TODO THIS CHUNK MIGHT BE USELESS
        }
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 1, intent, flags);
        try {
            ActivityRecognition.getClient(context).removeActivityUpdates(pendingIntent);//TODO THIS CHUNK MIGHT BE USELESS
        } catch (Exception e) {
            Log.e(TAG, "Failed to remove activity updates", e);
        }
    }//TODO THIS CHUNK MIGHT BE USELESS

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
