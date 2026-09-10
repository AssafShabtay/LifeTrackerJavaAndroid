package com.example.myapplication.mainScreen.statisticsScreen;

import static com.example.myapplication.locationTracking.ActivityTrackingUtils.calculateRadiusBox;
import static com.example.myapplication.locationTracking.ActivityTrackingUtils.getCoordinatesFromAddress;

import android.content.Context;
import android.os.Handler;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.myapplication.LifeTrackerApp;
import com.example.myapplication.R;
import com.example.myapplication.database.ActivityDatabase;
import com.example.myapplication.database.Place;
import com.example.myapplication.database.PlaceDao;

public class HomeStatisticsManager {

    private final Context context;
    private final LifeTrackerApp app;
    private final Handler mainHandler;
    private final HomeStatisticsListener listener;

    private final View cabinFeverContent;
    private final View cabinFeverPlaceholder;
    private final TextView scoreText;
    private final ProgressBar progressBar;
    private final TextView messageText;

    public interface HomeStatisticsListener {
        boolean isFragmentAdded();
    }

    public HomeStatisticsManager(Context context, LifeTrackerApp app, Handler mainHandler, HomeStatisticsListener listener, View rootView) {
        this.context = context;
        this.app = app;
        this.mainHandler = mainHandler;
        this.listener = listener;

        this.cabinFeverContent = rootView.findViewById(R.id.cabin_fever_content);
        this.cabinFeverPlaceholder = rootView.findViewById(R.id.cabin_fever_placeholder);
        this.scoreText = rootView.findViewById(R.id.tv_homebody_score);
        this.progressBar = rootView.findViewById(R.id.progress_cabin_fever);
        this.messageText = rootView.findViewById(R.id.tv_cabin_fever_message);
    }



    public void onHomeAddressSelected(String address) {
        saveHomeAddress(address);
    }


    private void saveHomeAddress(String address) {
        if (address.isEmpty()) {
            Toast.makeText(context, "Please enter a home address.", Toast.LENGTH_SHORT).show();
            return;
        }

        app.getDatabaseWriteExecutor().execute(() -> {
            if (!listener.isFragmentAdded()) return;
            double[] coords = getCoordinatesFromAddress(address, context);

            ActivityDatabase db = ActivityDatabase.getDatabase(context);
            PlaceDao placeDao = db.placeDao();
            Place homePlace = placeDao.getHomePlace();

            if (homePlace == null) {
                homePlace = new Place();
                homePlace.setName("Home");
                homePlace.setAddress(address);
                homePlace.setCategory("Home");
                homePlace.setIcon("Home");
                homePlace.setColor(0xFF9E9E9E);
                if (coords != null) {
                    homePlace.setLat(coords[0]);
                    homePlace.setLng(coords[1]);
                }
                long insertedId = placeDao.insertPlace(homePlace);
                homePlace.setId(insertedId);

                if (coords != null) {
                    double[] bounds = calculateRadiusBox(coords[0], coords[1], 50.0);
                    db.activityDao().updateStillsWithinBounds(bounds[0], bounds[1], bounds[2], bounds[3], "Home");
                }
            } else {
                homePlace.setAddress(address);
                homePlace.setCategory("Home");
                homePlace.setName("Home");
                homePlace.setIcon("Home");
                if (coords != null) {
                    homePlace.setLat(coords[0]);
                    homePlace.setLng(coords[1]);
                }
                placeDao.updatePlace(homePlace);

                if (coords != null) {
                    double[] bounds = calculateRadiusBox(coords[0], coords[1], 50.0);
                    db.activityDao().updateStillsWithinBounds(bounds[0], bounds[1], bounds[2], bounds[3], "Home");
                }
            }

            mainHandler.post(() -> {
                loadCabinFeverIndex();
            });

        });
    }

    public void loadCabinFeverIndex() {
        if (!listener.isFragmentAdded()) return;

        final long finalNow = System.currentTimeMillis();
        final long finalSevenDaysAgo = finalNow - (7L * 24 * 60 * 60 * 1000);

        app.getDatabaseWriteExecutor().execute(() -> {
            if (!listener.isFragmentAdded()) return;
            ActivityDatabase db = ActivityDatabase.getDatabase(context);
            Place homePlace = db.placeDao().getHomePlace();

            long timeAtHomeMs = 0;
            if (homePlace != null) {
                timeAtHomeMs = db.activityDao().getTimeAtHomeSince(homePlace.getId(), finalSevenDaysAgo, finalNow);
            }
            long windowMs = finalNow - finalSevenDaysAgo; // 7 days in ms
            long totalLoggedTime = db.activityDao().getSumDurationOfAllActivitiesLastSevenDays(finalSevenDaysAgo, finalNow);
            long denominator = Math.max(totalLoggedTime, windowMs);

            final long finalTimeAtHomeMs = timeAtHomeMs;
            final long finalDenominator = denominator;

            mainHandler.post(() -> {
                if (!listener.isFragmentAdded()) return;
                if (homePlace == null) {
                    if (cabinFeverContent != null) cabinFeverContent.setVisibility(View.GONE);
                    if (cabinFeverPlaceholder != null) cabinFeverPlaceholder.setVisibility(View.VISIBLE);
                } else {
                    if (cabinFeverContent != null) cabinFeverContent.setVisibility(View.VISIBLE);
                    if (cabinFeverPlaceholder != null) cabinFeverPlaceholder.setVisibility(View.GONE);

                    int percentage = 0;
                    if (finalDenominator > 0) {
                        percentage = (int) (((double) finalTimeAtHomeMs / finalDenominator) * 100.0);
                    }
                    if (percentage > 100) percentage = 100;
                    updateCabinFeverUi(percentage);
                }
            });
        });
    }

    private void updateCabinFeverUi(int percentage) {
        if (scoreText != null) scoreText.setText(percentage + "% of your week spent at Home");
        if (progressBar != null) progressBar.setProgress(percentage);

        if (messageText != null) {
            if (percentage > 85) {
                messageText.setVisibility(View.VISIBLE);
                messageText.setText("Warning: High Cabin Fever detected! 🚨 Go take a walk, the neighborhood misses you.");
            } else {
                messageText.setVisibility(View.GONE);
            }
        }
    }
}
