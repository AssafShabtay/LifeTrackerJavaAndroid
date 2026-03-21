package com.example.myapplication;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.google.android.gms.location.ActivityTransitionEvent;
import com.google.android.gms.location.ActivityTransitionResult;
import com.google.android.gms.location.DetectedActivity;

public class ActivityTransitionReceiver extends BroadcastReceiver {

    public static final String ACTION_ACTIVITY_UPDATE = "com.example.myapplication.ACTIVITY_UPDATE";
    public static final String EXTRA_ACTIVITY_TYPE = "activity_type";
    public static final String EXTRA_TRANSITION_TYPE = "transition_type";

    @Override
    public void onReceive(Context context, Intent intent) {
        ActivityTransitionResult result = ActivityTransitionResult.extractResult(intent);
        if (result == null) return;

        for (ActivityTransitionEvent event : result.getTransitionEvents()) {
            int activityType = handleUnknownActivity(event.getActivityType());
            notifyService(context, activityType, event.getTransitionType());
        }
    }

    private void notifyService(Context context, int activityType, int transitionType) {
        Intent serviceIntent = new Intent(context, LocationService.class);
        serviceIntent.setAction(ACTION_ACTIVITY_UPDATE);
        serviceIntent.putExtra(EXTRA_ACTIVITY_TYPE, activityType);
        serviceIntent.putExtra(EXTRA_TRANSITION_TYPE, transitionType);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }
    }

    private int handleUnknownActivity(int activityType) {
        return activityType == DetectedActivity.UNKNOWN ? DetectedActivity.STILL : activityType;
    }
}
