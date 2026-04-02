package com.example.myapplication;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

import com.google.android.gms.location.ActivityTransitionEvent;
import com.google.android.gms.location.ActivityTransitionResult;

public class ActivityTransitionReceiver extends BroadcastReceiver {

    private static final String TAG = "TransitionReceiver";
    public static final String ACTION_ACTIVITY_UPDATE = "com.example.myapplication.ACTIVITY_UPDATE";
    public static final String EXTRA_ACTIVITY_TYPE = "activity_type";
    public static final String EXTRA_TRANSITION_TYPE = "transition_type";
    public static final String EXTRA_TIMESTAMP_NANOS = "timestamp_nanos";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive triggered");
        
        if (ActivityTransitionResult.hasResult(intent)) {
            ActivityTransitionResult result = ActivityTransitionResult.extractResult(intent);
            if (result != null) {
                // Since ActivityTransitionEvent doesn't consistently provide a timestamp across all 
                // library versions, we use the current elapsed realtime as the base for the event time.
                long receiptTimestampNanos = SystemClock.elapsedRealtimeNanos();
                
                for (ActivityTransitionEvent event : result.getTransitionEvents()) {
                    Log.d(TAG, "Transition: " + event.getActivityType() + " Type: " + event.getTransitionType());
                    notifyService(context, event.getActivityType(), event.getTransitionType(), receiptTimestampNanos);
                }
            }
        }
    }

    private void notifyService(Context context, int activityType, int transitionType, long timestampNanos) {
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
            Log.e(TAG, "Failed to start service from receiver", e);
        }
    }
}
