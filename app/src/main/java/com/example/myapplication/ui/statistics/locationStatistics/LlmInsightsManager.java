package com.example.myapplication.ui.statistics.locationStatistics;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.example.myapplication.LifeTrackerApp;
import com.example.myapplication.R;
import com.example.myapplication.data.db.ActivityDatabase;
import com.example.myapplication.data.model.StillLocation;
import com.example.myapplication.ui.statistics.llm.LlmApiClient;
import com.example.myapplication.util.ErrorLogger;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class LlmInsightsManager {

    private static final String TAG = "LlmInsightsManager";

    private static final String PREFS_NAME = "llm_insights_prefs";
    private static final String KEY_HABIT = "cached_habit";
    private static final String KEY_ANOMALY = "cached_anomaly";
    private static final String KEY_LAST_FETCH_TIME = "cached_timestamp";
    private static final long SIX_HOURS_MS = 6L * 60 * 60 * 1000;

    private final Context context;
    private final LifeTrackerApp app;
    private final Handler mainHandler;
    private final LlmInsightsListener listener;

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

        this.tvHabitText = rootView.findViewById(R.id.tv_habit_text);
        this.tvAnomalyText = rootView.findViewById(R.id.tv_anomaly_text);
        this.tvLlmLoadingError = rootView.findViewById(R.id.tv_llm_loading_error);
    }



    public void loadLlmInsights() {
        if (!listener.isFragmentAdded()) return;

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long lastFetchTime = prefs.getLong(KEY_LAST_FETCH_TIME, 0);
        long now = System.currentTimeMillis();
        long timeSinceFetch = now - lastFetchTime;
        String cachedHabit = prefs.getString(KEY_HABIT, null);
        String cachedAnomaly = prefs.getString(KEY_ANOMALY, null);

        boolean isCacheValid = timeSinceFetch < SIX_HOURS_MS && cachedHabit != null && cachedAnomaly != null;

        if (isCacheValid) {
            tvLlmLoadingError.setVisibility(View.GONE);
            String habitFormatted = "<b>Habit:</b> " + cachedHabit;
            String anomalyFormatted = "<b>Anomaly:</b> " + cachedAnomaly;
            tvHabitText.setText(Html.fromHtml(habitFormatted, Html.FROM_HTML_MODE_LEGACY));
            tvAnomalyText.setText(Html.fromHtml(anomalyFormatted, Html.FROM_HTML_MODE_LEGACY));
            return;
        }

        tvLlmLoadingError.setVisibility(View.VISIBLE);
        tvLlmLoadingError.setText("Analyzing habits and anomalies with AI...");
        tvHabitText.setText("");
        tvAnomalyText.setText("");

        app.getBackgroundWriteExecutor().execute(() -> {
            if (!listener.isFragmentAdded()) return;
            try {
                long fourteenDaysMs = 14L * 24 * 60 * 60 * 1000;
                long fourteenDaysAgo = now - fourteenDaysMs;

                ActivityDatabase db = ActivityDatabase.getDatabase(context);
                List<StillLocation> recentStills = db.activityDao().getStillsFromRange(new Date(fourteenDaysAgo), new Date(now));

                if (recentStills.isEmpty()) {
                    mainHandler.post(() -> {
                        if (!listener.isFragmentAdded()) return;
                        tvLlmLoadingError.setVisibility(View.VISIBLE);
                        tvLlmLoadingError.setText("No location data available for the last 14 days to analyze habits.");
                        tvHabitText.setText("");
                        tvAnomalyText.setText("");
                    });
                    return;
                }

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

                LlmApiClient.getHabitAndAnomaly(timelineSequence).thenAccept(llmResponse -> mainHandler.post(() -> {
                    if (!listener.isFragmentAdded()) return;
                    tvLlmLoadingError.setVisibility(View.GONE);
                    if (llmResponse != null) {
                        prefs.edit()
                                .putString(KEY_HABIT, llmResponse.getHabit())
                                .putString(KEY_ANOMALY, llmResponse.getAnomaly())
                                .putLong(KEY_LAST_FETCH_TIME, System.currentTimeMillis())
                                .apply();

                        String habitFormatted = "<b>Habit:</b> " + llmResponse.getHabit();
                        String anomalyFormatted = "<b>Anomaly:</b> " + llmResponse.getAnomaly();
                        tvHabitText.setText(Html.fromHtml(habitFormatted, Html.FROM_HTML_MODE_LEGACY));
                        tvAnomalyText.setText(Html.fromHtml(anomalyFormatted, Html.FROM_HTML_MODE_LEGACY));
                    } else {
                        tvLlmLoadingError.setVisibility(View.VISIBLE);
                        tvLlmLoadingError.setText("Failed to load insights from AI. Please try again later.");
                    }
                })).exceptionally(throwable -> {
                    Log.e(TAG, "Error loading LLM insights", throwable);
                    ErrorLogger.logError(context, TAG, "Error loading LLM insights", throwable);
                    mainHandler.post(() -> {
                        if (!listener.isFragmentAdded()) return;
                        tvLlmLoadingError.setVisibility(View.VISIBLE);
                        tvLlmLoadingError.setText("Error loading AI insights: " + throwable.getLocalizedMessage());
                        tvHabitText.setText("");
                        tvAnomalyText.setText("");
                    });
                    return null;
                });

            } catch (Exception e) {
                Log.e(TAG, "Error fetching location stills", e);
                ErrorLogger.logError(context, TAG, "Error fetching location stills", e);
                mainHandler.post(() -> {
                    if (!listener.isFragmentAdded()) return;
                    tvLlmLoadingError.setVisibility(View.VISIBLE);
                    tvLlmLoadingError.setText("Error loading AI insights: " + e.getLocalizedMessage());
                    tvHabitText.setText("");
                    tvAnomalyText.setText("");
                });
            }
        });
    }

    @NonNull
    private static String getTimelineSequenceString(List<StillLocation> recentStills) {
        StringBuilder sb = new StringBuilder();
        SimpleDateFormat dayFormat = new SimpleDateFormat("EEE, MMM d HH:mm", Locale.getDefault());
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

        for (StillLocation still : recentStills) {
            String placeName = still.getPlaceName();
            Date startTime = still.getStartTimeDate();
            Date endTime = still.getEndTimeDate();

            if (placeName != null && !placeName.trim().isEmpty() && startTime != null) {
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append("• ").append(dayFormat.format(startTime));
                if (endTime != null) {
                    sb.append(" - ").append(timeFormat.format(endTime));
                    long durationMs = endTime.getTime() - startTime.getTime();
                    if (durationMs > 0) {
                        long hours = durationMs / (1000 * 60 * 60);
                        long mins = (durationMs / (1000 * 60)) % 60;
                        sb.append(" (");
                        if (hours > 0) {
                            sb.append(hours).append("h ");
                        }
                        sb.append(mins).append("m)");
                    }
                }
                sb.append(": ").append(placeName);
            }
        }

        return sb.toString();
    }
}
