package com.example.myapplication.mainScreen;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.myapplication.R;
import com.example.myapplication.database.ActivityDatabase;
import com.example.myapplication.database.MovementActivity;
import com.example.myapplication.database.Place;
import com.example.myapplication.database.StillLocation;
import com.example.myapplication.database.TimelineItem;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StatisticsFragment extends Fragment {

    private MiniPieChartView pieChart;
    private TextView tvTotalTime;
    private LinearLayout legendContainer;
    private LinearLayout topPlacesContainer;
    private TextView tvNoData;

    private View cabinFeverContent;
    private View cabinFeverPlaceholder;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_statistics, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        pieChart = view.findViewById(R.id.pieChart);
        tvTotalTime = view.findViewById(R.id.tvTotalTime);
        legendContainer = view.findViewById(R.id.legendContainer);
        topPlacesContainer = view.findViewById(R.id.topPlacesContainer);
        tvNoData = view.findViewById(R.id.tvNoData);
        cabinFeverContent = view.findViewById(R.id.cabin_fever_content);
        cabinFeverPlaceholder = view.findViewById(R.id.cabin_fever_placeholder);

        loadStatistics();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadStatistics();
    }

    private void loadStatistics() {
        executor.execute(() -> {
            if (!isAdded()) return;

            // Get data for "Today"
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            Date start = cal.getTime();
            
            cal.set(Calendar.HOUR_OF_DAY, 23);
            cal.set(Calendar.MINUTE, 59);
            cal.set(Calendar.SECOND, 59);
            Date end = cal.getTime();

            ActivityDatabase db = ActivityDatabase.getDatabase(requireContext());
            List<StillLocation> stills = db.activityDao().getStillForRange(start, end);
            List<MovementActivity> movements = db.activityDao().getMovementForRange(start, end);

            List<TimelineItem> allItems = new ArrayList<>();
            allItems.addAll(stills);
            allItems.addAll(movements);

            processStats(allItems);
            loadCabinFeverIndex();
        });
    }

    private void processStats(List<TimelineItem> items) {
        if (items.isEmpty()) {
            mainHandler.post(() -> {
                if (!isAdded()) return;
                tvNoData.setVisibility(View.VISIBLE);
                pieChart.setSlices(Collections.emptyList());
                tvTotalTime.setText("0h\n0m");
                legendContainer.removeAllViews();
                topPlacesContainer.removeAllViews();
            });
            return;
        }

        List<MiniPieChartView.Slice> slices = new ArrayList<>();
        Map<String, Long> activityDurations = new HashMap<>();
        Map<String, Long> activityColors = new HashMap<>();
        Map<String, Long> placeDurations = new HashMap<>();
        long totalMinutes = 0;

        for (TimelineItem item : items) {
            Date startTime = item.getStartTime();
            Date endTime = item.getEndTime();
            if (endTime == null) endTime = new Date(); // Ongoing

            // Boundary checks for "Today"
            Calendar calStart = Calendar.getInstance();
            calStart.set(Calendar.HOUR_OF_DAY, 0); calStart.set(Calendar.MINUTE, 0);
            Date todayStart = calStart.getTime();

            Date effectiveStart = startTime.before(todayStart) ? todayStart : startTime;
            
            long durationMs = endTime.getTime() - effectiveStart.getTime();
            long durationMins = durationMs / 60000;
            if (durationMins <= 0) continue;

            totalMinutes += durationMins;

            Calendar cal = Calendar.getInstance();
            cal.setTime(effectiveStart);
            int startMinsFromDayStart = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);

            int color = Color.GRAY;
            String type = "Unknown";

            if (item instanceof StillLocation) {
                StillLocation still = (StillLocation) item;
                type = "Still";
                color = still.color != null ? still.color : ContextCompat.getColor(requireContext(), R.color.activity_still);
                
                String place = (still.placeName != null && !still.placeName.isEmpty()) ? still.placeName : "Unlabeled Place";
                placeDurations.put(place, placeDurations.getOrDefault(place, 0L) + durationMins);
            } else if (item instanceof MovementActivity) {
                MovementActivity move = (MovementActivity) item;
                type = move.activityTypeName != null ? move.activityTypeName : "Moving";
                
                if ("WALKING".equalsIgnoreCase(type)) color = ContextCompat.getColor(requireContext(), R.color.activity_walking);
                else if ("CYCLING".equalsIgnoreCase(type)) color = ContextCompat.getColor(requireContext(), R.color.activity_cycling);
                else if ("DRIVING".equalsIgnoreCase(type)) color = ContextCompat.getColor(requireContext(), R.color.activity_vehicle);
                else if ("RUNNING".equalsIgnoreCase(type)) color = ContextCompat.getColor(requireContext(), R.color.activity_running);
                else color = ContextCompat.getColor(requireContext(), R.color.activity_stop);
            }

            slices.add(new MiniPieChartView.Slice(startMinsFromDayStart, durationMins, color));
            activityDurations.put(type, activityDurations.getOrDefault(type, 0L) + durationMins);
            activityColors.put(type, (long)color);
        }

        final long finalTotal = totalMinutes;
        mainHandler.post(() -> updateUi(slices, activityDurations, activityColors, placeDurations, finalTotal));
    }

    private void updateUi(List<MiniPieChartView.Slice> slices, Map<String, Long> activityDurations, Map<String, Long> activityColors, Map<String, Long> placeDurations, long totalMins) {
        if (!isAdded()) return;

        pieChart.setSlices(slices);
        
        long hours = totalMins / 60;
        long mins = totalMins % 60;
        tvTotalTime.setText(hours + "h\n" + mins + "m");

        tvNoData.setVisibility(placeDurations.isEmpty() ? View.VISIBLE : View.GONE);

        // Update Legend
        legendContainer.removeAllViews();
        List<String> sortedActivities = new ArrayList<>(activityDurations.keySet());
        Collections.sort(sortedActivities, (a, b) -> activityDurations.get(b).compareTo(activityDurations.get(a)));

        for (String type : sortedActivities) {
            long duration = activityDurations.get(type);
            int color = activityColors.get(type).intValue();
            addLegendItem(type, duration, color);
        }

        // Update Top Places
        topPlacesContainer.removeAllViews();
        List<Map.Entry<String, Long>> sortedPlaces = new ArrayList<>(placeDurations.entrySet());
        Collections.sort(sortedPlaces, (e1, e2) -> e2.getValue().compareTo(e1.getValue()));

        int count = 0;
        for (Map.Entry<String, Long> entry : sortedPlaces) {
            if (count >= 5) break;
            addPlaceItem(entry.getKey(), entry.getValue());
            count++;
        }
    }

    private void addLegendItem(String label, long mins, int color) {
        if (!isAdded()) return;
        View view = LayoutInflater.from(requireContext()).inflate(android.R.layout.simple_list_item_2, legendContainer, false);
        TextView text1 = view.findViewById(android.R.id.text1);
        TextView text2 = view.findViewById(android.R.id.text2);
        
        text1.setText(label);
        text1.setTextColor(color);
        text1.setTextSize(14);
        
        text2.setText(mins / 60 + "h " + mins % 60 + "m");
        text2.setTextSize(12);
        
        legendContainer.addView(view);
    }

    private void addPlaceItem(String name, long mins) {
        if (!isAdded()) return;
        View view = LayoutInflater.from(requireContext()).inflate(android.R.layout.simple_list_item_2, topPlacesContainer, false);
        TextView text1 = view.findViewById(android.R.id.text1);
        TextView text2 = view.findViewById(android.R.id.text2);
        
        text1.setText(name);
        text1.setTextSize(14);
        
        text2.setText(mins / 60 + "h " + mins % 60 + "m");
        text2.setTextSize(12);
        
        topPlacesContainer.addView(view);
    }

    private void loadCabinFeverIndex() {
        long now = System.currentTimeMillis();
        long sevenDaysMs = 7L * 24 * 60 * 60 * 1000; // 7 days in milliseconds
        long sevenDaysAgo = now - sevenDaysMs;

        executor.execute(() -> {
            if (!isAdded()) return;
            ActivityDatabase db = ActivityDatabase.getDatabase(requireContext());
            Place homePlace = db.placeDao().getHomePlace();

            if (homePlace == null) {
                mainHandler.post(() -> {
                    if (cabinFeverContent != null) cabinFeverContent.setVisibility(View.GONE);
                    if (cabinFeverPlaceholder != null) cabinFeverPlaceholder.setVisibility(View.VISIBLE);
                });
                return;
            }

            long timeAtHomeMs = db.activityDao().getTimeAtHomeSince(sevenDaysAgo, now);
            int percentage = (int) (((float) timeAtHomeMs / sevenDaysMs) * 100);
            if (percentage > 100) percentage = 100;

            int finalPercentage = percentage;
            mainHandler.post(() -> {
                if (cabinFeverContent != null) cabinFeverContent.setVisibility(View.VISIBLE);
                if (cabinFeverPlaceholder != null) cabinFeverPlaceholder.setVisibility(View.GONE);
                updateCabinFeverUi(finalPercentage);
            });
        });
    }

    private void updateCabinFeverUi(int percentage) {
        if (getView() == null) return;
        
        TextView scoreText = getView().findViewById(R.id.tv_homebody_score);
        ProgressBar progressBar = getView().findViewById(R.id.progress_cabin_fever);
        TextView messageText = getView().findViewById(R.id.tv_cabin_fever_message);

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
