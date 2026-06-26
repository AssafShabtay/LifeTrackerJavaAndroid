package com.example.myapplication.mainScreen;

import static com.example.myapplication.locationTracking.ActivityTrackingUtils.calculateRadiusBox;
import static com.example.myapplication.locationTracking.ActivityTrackingUtils.getCoordinatesFromAddress;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.myapplication.MainActivity;
import com.example.myapplication.R;
import com.example.myapplication.database.ActivityDatabase;
import com.example.myapplication.database.MovementActivity;
import com.example.myapplication.database.Place;
import com.example.myapplication.database.PlaceDao;
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

public class StatisticsFragment extends Fragment implements HomeAddressPickerBottomSheet.OnHomeAddressSelectedListener {

    private LinearLayout topPlacesContainer;
    private TextView tvNoData;

    private View cabinFeverContent;
    private View cabinFeverPlaceholder;
    private Button btnOpenHomeAddressPicker;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private Map<String, Long> currentPlaceDurations = new HashMap<>(); // Added field

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_statistics, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        topPlacesContainer = view.findViewById(R.id.topPlacesContainer);
        tvNoData = view.findViewById(R.id.tvNoData);
        cabinFeverContent = view.findViewById(R.id.cabin_fever_content);
        cabinFeverPlaceholder = view.findViewById(R.id.cabin_fever_placeholder);
        btnOpenHomeAddressPicker = view.findViewById(R.id.btnOpenHomeAddressPicker);

        btnOpenHomeAddressPicker.setOnClickListener(v -> {
            // Pass currentPlaceDurations to the bottom sheet
            HomeAddressPickerBottomSheet bottomSheet = HomeAddressPickerBottomSheet.newInstance(this, currentPlaceDurations);
            bottomSheet.show(getChildFragmentManager(), bottomSheet.getTag());
        });

        loadStatistics();
        // loadHomeAddress() is now implicitly handled by loadCabinFeverIndex which shows/hides placeholder
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
            loadCabinFeverIndex(); // This now also handles showing/hiding the address picker button
        });
    }

    private void processStats(List<TimelineItem> items) {
        if (items.isEmpty()) {
            mainHandler.post(() -> {
                if (!isAdded()) return;
                tvNoData.setVisibility(View.VISIBLE);
                topPlacesContainer.removeAllViews();
                currentPlaceDurations.clear(); // Clear the map if no data
            });
            return;
        }

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

            activityDurations.put(type, activityDurations.getOrDefault(type, 0L) + durationMins);
            activityColors.put(type, (long)color);
        }

        currentPlaceDurations = placeDurations; // Update the class field

        final long finalTotal = totalMinutes;
        mainHandler.post(() -> updateUi(activityDurations, activityColors, placeDurations, finalTotal));
    }

    private void updateUi(Map<String, Long> activityDurations, Map<String, Long> activityColors, Map<String, Long> placeDurations, long totalMins) {
        if (!isAdded()) return;

        tvNoData.setVisibility(placeDurations.isEmpty() ? View.VISIBLE : View.GONE);

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


    // ------------------------ Cabin fever statistics ----------------------------

    public void onAddressSelected(String address) {
        saveHomeAddress(address);
    }

    private void saveHomeAddress(String address) {
        if (address.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter a home address.", Toast.LENGTH_SHORT).show();
            return;
        }

        executor.execute(() -> {
            if (!isAdded()) return;
            double[] coords = getCoordinatesFromAddress(address, requireContext());

            ActivityDatabase db = ActivityDatabase.getDatabase(requireContext());
            PlaceDao placeDao = db.placeDao();
            Place homePlace = placeDao.getHomePlace();
            if (homePlace == null) {
                // Create a new home place
                homePlace = new Place();
                homePlace.name = "Home";
                homePlace.address = address;
                homePlace.category = "Home";
                homePlace.icon = "Home";
                homePlace.color = 0xFF9E9E9E; // TODO CHANGE Default color: light grey, consistent with StillLocation fallback
                if(coords != null){
                    homePlace.lat = coords[0];
                    homePlace.lng = coords[1];
                }
                placeDao.insertPlace(homePlace);
                if (coords != null) {

                    double[] bounds = calculateRadiusBox(coords[0], coords[1], 50.0);
                    db.activityDao().updateStillsWithinBounds(bounds[0], bounds[1], bounds[2], bounds[3], "Home");
                }
            } else {
                // Update existing home place
                homePlace.address = address;
                homePlace.category = "Home";
                homePlace.name = "Home";
                homePlace.icon = "Home";
                placeDao.updatePlace(homePlace);
            }

            mainHandler.post(() -> {
                if (!isAdded()) return;
                // Reload cabin fever index
                loadCabinFeverIndex();

                // Notify HomeFragment about the home address change
                if (getActivity() instanceof MainActivity) {
                    MainActivity.OnHomeAddressChangedListener listener = ((MainActivity) getActivity()).getOnHomeAddressChangedListener();
                    if (listener != null) {
                        listener.onHomeAddressChanged();
                    }
                }
            });
        });
    }


    private void loadCabinFeverIndex() {
        long now = System.currentTimeMillis();
        long sevenDaysMs = 7L * 24 * 60 * 60 * 1000; // 7 days in milliseconds
        long sevenDaysAgo = now - sevenDaysMs;

        executor.execute(() -> {
            if (!isAdded()) return;
            ActivityDatabase db = ActivityDatabase.getDatabase(requireContext());
            Place homePlace = db.placeDao().getHomePlace();

            long timeAtHomeMs = 0;
            if (homePlace != null) {
                timeAtHomeMs = db.activityDao().getTimeAtHomeSince(sevenDaysAgo, now);
            }

            final long finalTimeAtHomeMs = timeAtHomeMs; // make time final
            mainHandler.post(() -> {
                if (!isAdded()) return;
                if (homePlace == null) {
                    if (cabinFeverContent != null) cabinFeverContent.setVisibility(View.GONE);
                    if (cabinFeverPlaceholder != null) cabinFeverPlaceholder.setVisibility(View.VISIBLE);
                } else {
                    if (cabinFeverContent != null) cabinFeverContent.setVisibility(View.VISIBLE);
                    if (cabinFeverPlaceholder != null) cabinFeverPlaceholder.setVisibility(View.GONE);

                    // Update the cabin fever UI if a home address exists
                    int percentage = (int) (((float) finalTimeAtHomeMs / sevenDaysMs) * 100);
                    if (percentage > 100) percentage = 100;
                    updateCabinFeverUi(percentage);
                }
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