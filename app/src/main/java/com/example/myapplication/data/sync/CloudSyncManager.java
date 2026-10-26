package com.example.myapplication.data.sync;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.work.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import java.util.concurrent.TimeUnit;

public final class CloudSyncManager {
    private static final String PERIODIC = "cloud-sync-periodic";
    private static final String IMMEDIATE = "cloud-sync-now";
    public static final String UID = "uid";
    private CloudSyncManager() {}
    public static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("LifeTrackerPrefs", Context.MODE_PRIVATE);
    }
    public static boolean enabled(Context context) {
        return preferences(context).getBoolean("is_cloud_enabled", false);
    }
    public static void setEnabled(Context context, boolean enabled) {
        preferences(context).edit().putBoolean("is_cloud_enabled", enabled).apply();
        if (enabled) schedule(context);
        else {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC);
            WorkManager.getInstance(context).cancelUniqueWork(IMMEDIATE);
            status(context, "Cloud sync paused. Local changes will wait until you enable it again.");
        }
    }
    public static void schedule(Context context) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (!enabled(context) || user == null) return;
        Constraints constraints = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        Data data = new Data.Builder().putString(UID, user.getUid()).build();
        WorkManager work = WorkManager.getInstance(context);
        work.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
                new PeriodicWorkRequest.Builder(CloudSyncWorker.class, 15, TimeUnit.MINUTES)
                        .setConstraints(constraints).setInputData(data)
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build());
        work.enqueueUniqueWork(IMMEDIATE, ExistingWorkPolicy.KEEP,
                new OneTimeWorkRequest.Builder(CloudSyncWorker.class).setConstraints(constraints).setInputData(data)
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build());
        status(context, "Sync scheduled; waiting for a connection. Ongoing activities sync when they finish.");
    }
    public static void status(Context context, String message) {
        preferences(context).edit().putString("cloud_sync_status", message).apply();
    }
}
