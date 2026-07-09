package com.example.myapplication;

import android.app.Application;
import java.util.concurrent.ExecutorService;

public class LifeTrackerApp extends Application {

    private final ExecutorService databaseWriteExecutor = java.util.concurrent.Executors.newFixedThreadPool(4);

    @Override
    public void onCreate() {
        super.onCreate();
    }
    public ExecutorService getDatabaseWriteExecutor() {
        return databaseWriteExecutor;
    }
}