package com.example.myapplication;

import android.app.Application;
import android.content.Intent;
import java.util.concurrent.ExecutorService;

import com.example.myapplication.helpers.Logger;
import com.example.myapplication.locationTracking.LocationService;

public class LifeTrackerApp extends Application {

    private final ExecutorService databaseWriteExecutor = java.util.concurrent.Executors.newFixedThreadPool(4);
    @Override
    public void onCreate() {
        super.onCreate();
        // Set up the global error handler
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            Logger.saveLog(getApplicationContext(), "FATAL CRASH: " + throwable.getMessage() + throwable.fillInStackTrace()+ throwable.getCause());

            // Restart the app
            Intent intent = new Intent(getApplicationContext(), MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);

            //Kill the current crashed process
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(1);
        });
    }
    public ExecutorService getDatabaseWriteExecutor() {
        return databaseWriteExecutor;
    }
}