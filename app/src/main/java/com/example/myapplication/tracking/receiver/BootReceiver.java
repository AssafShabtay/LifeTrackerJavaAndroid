package com.example.myapplication.tracking.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.example.myapplication.tracking.location.LocationService;
import com.example.myapplication.util.ErrorLogger;
import com.example.myapplication.util.Logger;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;

        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
            Intent.ACTION_MY_PACKAGE_REPLACED.equals(action) ||
            "android.intent.action.QUICKBOOT_POWERON".equals(action)) {

            String msg = TAG + ": Boot completed or package replaced, starting LocationService";
            Logger.saveLog(context, msg);

            Intent serviceIntent = new Intent(context, LocationService.class);
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
            } catch (Exception e) {
                String errorMsg = TAG + ": Failed to start LocationService on boot: " + e.getMessage();
                ErrorLogger.logError(context, TAG, errorMsg, e);
                Logger.saveLog(context, errorMsg);
            }

        }
    }
}
