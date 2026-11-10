package com.example.myapplication.tracking.location;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.example.myapplication.tracking.receiver.ActivityTransitionReceiver;
import com.example.myapplication.util.ErrorLogger;
import com.example.myapplication.util.Logger;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityTransition;
import com.google.android.gms.location.ActivityTransitionRequest;
import com.google.android.gms.location.DetectedActivity;

import java.util.ArrayList;

public class ActivityTransitionManager {

    private static final String TAG = "ActivityTransitionManager";
    private static final Object registrationLock = new Object();
    private static boolean registered = false;
    private static boolean registrationInProgress = false;
    private static final ArrayList<RegistrationCallback> pendingCallbacks = new ArrayList<>();

    public interface RegistrationCallback {
        void onResult(boolean success);
    }

    public static void registerActivityTransitions(Context context) {
        registerActivityTransitions(context, null);
    }

    public static void registerActivityTransitions(Context context, RegistrationCallback callback) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
                String msg = TAG + ": Cannot register activity transitions - ACTIVITY_RECOGNITION permission not granted.";
                Log.w(TAG, msg);
                Logger.saveLog(context, msg);
                if (callback != null) callback.onResult(false);
                return;
            }
        }

        synchronized (registrationLock) {
            if (registered) {
                if (callback != null) callback.onResult(true);
                return;
            }
            if (callback != null) pendingCallbacks.add(callback);
            if (registrationInProgress) {
                return;
            }
            registrationInProgress = true;
        }

        ArrayList<ActivityTransition> transitions = new ArrayList<>();
        int[] types = new int[]{
                DetectedActivity.STILL,
                DetectedActivity.WALKING,
                DetectedActivity.RUNNING,
                DetectedActivity.IN_VEHICLE,
                DetectedActivity.ON_BICYCLE,
                DetectedActivity.ON_FOOT
        };

        for (int type : types) {
            transitions.add(new ActivityTransition.Builder()
                    .setActivityType(type)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                    .build());
            transitions.add(new ActivityTransition.Builder()
                    .setActivityType(type)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                    .build());
        }

        ActivityTransitionRequest request = new ActivityTransitionRequest(transitions);
        PendingIntent pendingIntent = getPendingIntent(context);

        try {
            ActivityRecognition.getClient(context)
                    .requestActivityTransitionUpdates(request, pendingIntent)
                    .addOnSuccessListener(unused -> {
                        String msg = TAG + ": Activity transitions registered successfully";
                        Log.d(TAG, msg);
                        Logger.saveLog(context, msg);
                        completeRegistration(true);
                    })
                    .addOnFailureListener(e -> {
                        String msg = TAG + ": Activity transitions registration failed: " + e.getMessage();
                        Log.e(TAG, msg);
                        Logger.saveLog(context, msg);
                        completeRegistration(false);
                    });
        } catch (SecurityException e) {
            ErrorLogger.logError(context, TAG, "SecurityException registering transitions", e);
            completeRegistration(false);
        } catch (Exception e) {
            ErrorLogger.logError(context, TAG, "Exception registering transitions", e);
            completeRegistration(false);
        }
    }

    private static void completeRegistration(boolean success) {
        ArrayList<RegistrationCallback> callbacks;
        synchronized (registrationLock) {
            registered = success;
            registrationInProgress = false;
            callbacks = new ArrayList<>(pendingCallbacks);
            pendingCallbacks.clear();
        }
        for (RegistrationCallback callback : callbacks) {
            callback.onResult(success);
        }
    }

    public static void unregisterActivityTransitions(Context context) {
        PendingIntent pendingIntent = getPendingIntent(context);
        try {
            ActivityRecognition.getClient(context)
                    .removeActivityTransitionUpdates(pendingIntent)
                    .addOnSuccessListener(unused -> {
                        synchronized (registrationLock) {
                            registered = false;
                        }
                        Log.d(TAG, "Activity transitions unregistered successfully");
                    })
                    .addOnFailureListener(e -> Log.e(TAG, "Failed to unregister activity transitions: " + e.getMessage()));
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException unregistering transitions", e);
        }
    }

    private static PendingIntent getPendingIntent(Context context) {
        Intent intent = new Intent(context, ActivityTransitionReceiver.class);
        intent.setAction(ActivityTransitionReceiver.ACTION_ACTIVITY_UPDATE);
        intent.setPackage(context.getPackageName());

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }

        return PendingIntent.getBroadcast(context, 0, intent, flags);
    }
}
