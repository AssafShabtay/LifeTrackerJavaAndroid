package com.example.myapplication.helpers;

import android.util.Log;

import com.example.myapplication.db.ActivityDao;
import com.example.myapplication.db.MovementActivity;
import com.example.myapplication.db.StillLocation;

import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ExampleData {
    private static final String TAG = "ExampleData";
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    private ExampleData() {}

    public static void insertExampleDataAsync(ActivityDao dao) {
        IO.execute(() -> {
            try {
                insertExampleData(dao);
            } catch (Exception e) {
                Log.e(TAG, "Failed inserting example data", e);
            }
        });
    }

    /** Rough Java port of ExampleData.kt (trimmed but same style). */
    public static void insertExampleData(ActivityDao dao) {
        Date now = new Date();
        long oneHour = 60L * 60L * 1000L;
        long fifteenMin = 15L * 60L * 1000L;
        long oneDay = 24L * oneHour;

        // TODAY
        StillLocation homeMorning = new StillLocation();
        homeMorning.lat = 52.52;
        homeMorning.lng = 13.405;
        homeMorning.startTimeDate = new Date(now.getTime() - 8 * oneHour);
        homeMorning.endTimeDate = new Date(now.getTime() - 6 * oneHour);
        homeMorning.placeName = "Home";
        dao.insertStillLocation(homeMorning);

        MovementActivity walk1 = new MovementActivity();
        walk1.activityType = "Walking";
        walk1.startLat = 52.52;
        walk1.startLng = 13.405;
        walk1.endLat = 52.523;
        walk1.endLng = 13.41;
        walk1.startTimeDate = new Date(now.getTime() - 6 * oneHour);
        walk1.endTimeDate = new Date(now.getTime() - 6 * oneHour + fifteenMin);
        dao.insertMovementActivity(walk1);

        StillLocation office = new StillLocation();
        office.lat = 52.53;
        office.lng = 13.42;
        office.startTimeDate = new Date(now.getTime() - 5 * oneHour);
        office.endTimeDate = new Date(now.getTime() - 2 * oneHour);
        office.placeName = "Office";
        dao.insertStillLocation(office);

        MovementActivity driveHome = new MovementActivity();
        driveHome.activityType = "Driving";
        driveHome.startLat = 52.53;
        driveHome.startLng = 13.42;
        driveHome.endLat = 52.52;
        driveHome.endLng = 13.405;
        driveHome.startTimeDate = new Date(now.getTime() - oneHour);
        driveHome.endTimeDate = now;
        dao.insertMovementActivity(driveHome);

        // YESTERDAY
        long yesterdayBase = now.getTime() - oneDay;

        StillLocation homeYesterday = new StillLocation();
        homeYesterday.lat = 52.52;
        homeYesterday.lng = 13.405;
        homeYesterday.startTimeDate = new Date(yesterdayBase - 9 * oneHour);
        homeYesterday.endTimeDate = new Date(yesterdayBase - 3 * oneHour);
        homeYesterday.placeName = "Home";
        dao.insertStillLocation(homeYesterday);

        MovementActivity run = new MovementActivity();
        run.activityType = "Running";
        run.startLat = 52.52;
        run.startLng = 13.405;
        run.endLat = 52.54;
        run.endLng = 13.415;
        run.startTimeDate = new Date(yesterdayBase - 3 * oneHour);
        run.endTimeDate = new Date(yesterdayBase - 2 * oneHour);
        dao.insertMovementActivity(run);
    }
}
