package com.example.myapplication;

import android.app.Application;
import android.content.Intent;
import java.util.concurrent.ExecutorService;
import com.example.myapplication.locationTracking.LocationService;

public class LifeTrackerApp extends Application {

    private final ExecutorService databaseWriteExecutor = java.util.concurrent.Executors.newFixedThreadPool(4);

    public ExecutorService getDatabaseWriteExecutor() {
        return databaseWriteExecutor;
    }
}