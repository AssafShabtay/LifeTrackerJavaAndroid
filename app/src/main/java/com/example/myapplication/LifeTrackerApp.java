package com.example.myapplication;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Process;

import androidx.appcompat.app.AppCompatDelegate;

import java.util.concurrent.ExecutorService;

import com.example.myapplication.util.ErrorLogger;
import com.example.myapplication.util.Logger;

public class LifeTrackerApp extends Application {

    private final ExecutorService backgroundWriteExecutor = java.util.concurrent.Executors.newFixedThreadPool(4);
    @Override
    public void onCreate() {
        super.onCreate();
        com.example.myapplication.data.sync.CloudSyncManager.schedule(this);

        SharedPreferences preferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
        int currentMode = preferences.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_NO);
        AppCompatDelegate.setDefaultNightMode(currentMode);
        // Set up the global error handler
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            Logger.saveLog(getApplicationContext(), "FATAL CRASH: " + throwable.getMessage() + throwable.fillInStackTrace()+ throwable.getCause());
            ErrorLogger.logError(getApplicationContext(), "FATAL CRASH", throwable.getMessage(), throwable);
            // Restart the app
            Intent intent = new Intent(getApplicationContext(), MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);

            //Kill the current crashed process
            Process.killProcess(Process.myPid());
            System.exit(1);
        });
    }
    public ExecutorService getBackgroundWriteExecutor() {
        return backgroundWriteExecutor;
    }
}
