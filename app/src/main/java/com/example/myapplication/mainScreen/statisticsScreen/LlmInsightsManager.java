package com.example.myapplication.mainScreen.statisticsScreen;

import android.content.Context;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.example.myapplication.LifeTrackerApp;
import com.example.myapplication.R;
import com.example.myapplication.database.ActivityDatabase;
import com.example.myapplication.database.StillLocation;
import com.example.myapplication.helpers.ErrorLogger;
import com.example.myapplication.mainScreen.statisticsScreen.llm.LlmApiClient;
import com.example.myapplication.mainScreen.statisticsScreen.llm.LlmResponse;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public class LlmInsightsManager {

    private static final String TAG = "LlmInsightsManager";

    private final Context context;
    private final LifeTrackerApp app;
    private final Handler mainHandler;
    private final LlmInsightsListener listener;

    // UI elements to update, passed from the fragment
    private final TextView tvHabitText;
    private final TextView tvAnomalyText;
    private final TextView tvLlmLoadingError;

    public interface LlmInsightsListener {
        boolean isFragmentAdded();
    }

    public LlmInsightsManager(Context context, LifeTrackerApp app, Handler mainHandler, LlmInsightsListener listener, View rootView) {
        this.context = context;
        this.app = app;
        this.mainHandler = mainHandler;
        this.listener = listener;

        // Initialize LLM related views
        this.tvHabitText = rootView.findViewById(R.id.tv_habit_text);
        this.tvAnomalyText = rootView.findViewById(R.id.tv_anomaly_text);
        this.tvLlmLoadingError = rootView.findViewById(R.id.tv_llm_loading_error);
    }

    public void loadLlmInsights() {
        if (!listener.isFragmentAdded()) return;
        tvLlmLoadingError.setVisibility(View.VISIBLE);
        tvLlmLoadingError.setText("Loading habits and anomalies...");
        tvHabitText.setText("");
        tvAnomalyText.setText("");


        app.getDatabaseWriteExecutor().execute(() -> {
            if (!listener.isFragmentAdded()) return;
            try {
                long now = System.currentTimeMillis();
                long fourteenDaysMs = 14L * 24 * 60 * 60 * 1000;
                long fourteenDaysAgo = now - fourteenDaysMs;

                ActivityDatabase db = ActivityDatabase.getDatabase(context);
                List<StillLocation> recentStills = db.activityDao().getStillsFromRange(new Date(fourteenDaysAgo), new Date(now));

                // Sort by start time to create a chronological sequence
                recentStills.sort((s1, s2) -> s1.getStartTimeDate().compareTo(s2.getStartTimeDate()));

                final String timelineSequence = getTimelineSequenceString(recentStills);

                if (timelineSequence.isEmpty()) {
                    mainHandler.post(() -> {
                        if (!listener.isFragmentAdded()) return;
                        tvLlmLoadingError.setVisibility(View.VISIBLE);
                        tvLlmLoadingError.setText("No location data available for the last 14 days to analyze habits.");
                        tvHabitText.setText("");
                        tvAnomalyText.setText("");
                    });
                    return;
                }

                // Call the LLM (placeholder)
                LlmResponse llmResponse = null;
                CompletableFuture<LlmResponse> llmResponseFuture = LlmApiClient.getHabitAndAnomaly(timelineSequence);
                llmResponse = llmResponseFuture.get();


                LlmResponse finalLlmResponse = llmResponse;
                mainHandler.post(() -> {
                    if (!listener.isFragmentAdded()) return;
                    tvLlmLoadingError.setVisibility(View.GONE);
                    if (finalLlmResponse != null) {
                        tvHabitText.setText("Habit: " + finalLlmResponse.getHabit());
                        tvAnomalyText.setText("Anomaly: " + finalLlmResponse.getAnomaly());
                    } else {
                        tvLlmLoadingError.setVisibility(View.VISIBLE);
                        tvLlmLoadingError.setText("Failed to load insights from LLM. Please try again later.");
                    }
                });

            } catch (Exception e) {
            ErrorLogger.logError(context, TAG, "Error", e);
                // Log the exception for debugging
                Log.e(TAG, "Error loading LLM insights", e);
                mainHandler.post(() -> {
                    if (!listener.isFragmentAdded()) return;
                    tvLlmLoadingError.setVisibility(View.VISIBLE);
                    tvLlmLoadingError.setText("Error loading LLM insights: " + e.getMessage());
                    tvHabitText.setText("");
                    tvAnomalyText.setText("");
                });
            }
        });
    }

    @NonNull
    private static String getTimelineSequenceString(List<StillLocation> recentStills) {
        StringBuilder timelineSequenceBuilder = new StringBuilder();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

        for (StillLocation still : recentStills) {
            String placeName = still.getPlaceName();
            Date startTime = still.getStartTimeDate();
            Date endTime = still.getEndTimeDate();

            if (placeName != null && !placeName.isEmpty() && startTime != null) {
                if (timelineSequenceBuilder.length() > 0) {
                    timelineSequenceBuilder.append(" -> ");
                }
                timelineSequenceBuilder.append(placeName);
                timelineSequenceBuilder.append(" (").append(sdf.format(startTime));
                if (endTime != null) {
                    timelineSequenceBuilder.append(" - ").append(sdf.format(endTime));
                }
                timelineSequenceBuilder.append(")");
            }
        }

        final String timelineSequence = timelineSequenceBuilder.toString();
        return timelineSequence;
    }
}
