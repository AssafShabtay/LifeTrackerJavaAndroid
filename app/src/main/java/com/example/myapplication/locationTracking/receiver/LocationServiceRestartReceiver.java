package com.example.myapplication.locationTracking.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.example.myapplication.helpers.Logger;
import com.example.myapplication.locationTracking.LocationService;

public class LocationServiceRestartReceiver extends BroadcastReceiver {
    public static final String ACTION_RESTART_SERVICE = "com.example.myapplication.ACTION_RESTART_SERVICE";
    private static final String TAG = "LSRestartReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && ACTION_RESTART_SERVICE.equals(intent.getAction())) {
            Logger.saveLog(context, "LocationServiceRestartReceiver: Alarm received, checking service status.");
            Log.d(TAG, "Alarm received, checking service status.");

            if (!LocationService.isServiceRunning(context)) {
                Logger.saveLog(context, "LocationServiceRestartReceiver: LocationService not running, restarting it.");
                Log.d(TAG, "LocationService not running, restarting it.");
                Intent serviceIntent = new Intent(context, LocationService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
            } else {
                Logger.saveLog(context, "LocationServiceRestartReceiver: LocationService is already running.");
                Log.d(TAG, "LocationService is already running.");
            }
        }
    }
}