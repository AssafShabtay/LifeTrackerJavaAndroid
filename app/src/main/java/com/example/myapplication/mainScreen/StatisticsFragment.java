package com.example.myapplication.mainScreen;

import static com.example.myapplication.locationTracking.ActivityTrackingUtils.calculateRadiusBox;
import static com.example.myapplication.locationTracking.ActivityTrackingUtils.getCoordinatesFromAddress;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import androidx.fragment.app.Fragment;

import com.example.myapplication.MainActivity;
import com.example.myapplication.R;
import com.example.myapplication.database.ActivityDatabase;
import com.example.myapplication.database.Place;
import com.example.myapplication.database.PlaceDao;
import com.example.myapplication.database.StillLocation;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StatisticsFragment extends Fragment implements HomeAddressPickerBottomSheet.OnHomeAddressSelectedListener, MainActivity.OnHomeAddressChangedListener {

    private LinearLayout topPlacesContainer;
    private TextView tvNoData;

    private View cabinFeverContent;
    private View cabinFeverPlaceholder;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private Map<String, Long> placeDuration = new HashMap<>();

    @Override
    public void onHomeAddressChanged() {
        loadCabinFeverIndex();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_statistics, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setOnHomeAddressChangedListener(this);
        }
        topPlacesContainer = view.findViewById(R.id.topPlacesContainer);
        tvNoData = view.findViewById(R.id.tvNoData);
        cabinFeverContent = view.findViewById(R.id.cabin_fever_content);
        cabinFeverPlaceholder = view.findViewById(R.id.cabin_fever_placeholder);
        Button btnOpenHomeAddressPicker = view.findViewById(R.id.btnOpenHomeAddressPicker);

        btnOpenHomeAddressPicker.setOnClickListener(v -> {
            HomeAddressPickerBottomSheet bottomSheet = HomeAddressPickerBottomSheet.newInstance(this, placeDuration);
            bottomSheet.show(getChildFragmentManager(), bottomSheet.getTag());
        });

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
            // Only querying StillLocations since MovementActivities were unused in the UI
            List<StillLocation> stills = db.activityDao().getStillsFromRange(start, end);

            getPlaceDurations(stills);
            loadCabinFeverIndex();
        });
    }

    private void getPlaceDurations(List<StillLocation> stills) {
        if (stills.isEmpty()) {
            mainHandler.post(() -> {
                if (!isAdded()) return;
                tvNoData.setVisibility(View.VISIBLE);
                topPlacesContainer.removeAllViews();
                placeDuration.clear();
            });
            return;
        }

        Map<String, Long> placeDurations = new HashMap<>();

        for (StillLocation item : stills) {
            Date startTime = item.getStartTimeDate();
            Date endTime = item.getEndTimeDate();
            if (endTime == null) endTime = new Date();

            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            Date todayStart = cal.getTime();

            Date start;
            if (startTime.before(todayStart)) start = todayStart;
            else start = startTime;

            long durationMs = endTime.getTime() - start.getTime();
            long durationMins = durationMs / 60000;
            if (durationMins <= 0) continue;
            String place = null;
            if (item.getPlaceName() != null && !item.getPlaceName().isEmpty()) place = item.getPlaceName();
            if(place != null) {
                placeDurations.merge(place, durationMins, Long::sum); // add duration according to the place
            }
        }

        placeDuration = placeDurations;
        mainHandler.post(() -> updateUi(placeDurations));
    }

    private void updateUi(Map<String, Long> placeDurations) {
        if (!isAdded()) return;

        tvNoData.setVisibility(placeDurations.isEmpty() ? View.VISIBLE : View.GONE);

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
                homePlace = new Place();
                homePlace.setName("Home");
                homePlace.setAddress(address);
                homePlace.setCategory("Home");
                homePlace.setIcon("Home");
                homePlace.setColor(0xFF9E9E9E);
                if(coords != null){
                    homePlace.setLat(coords[0]);
                    homePlace.setLng(coords[1]);
                }
                placeDao.insertPlace(homePlace);

                if (coords != null) {
                    double[] bounds = calculateRadiusBox(coords[0], coords[1], 50.0);
                    db.activityDao().updateStillsWithinBounds(bounds[0], bounds[1], bounds[2], bounds[3], "Home");
                }
            } else {
                homePlace.setAddress(address);
                homePlace.setCategory("Home");
                homePlace.setName("Home");
                homePlace.setIcon("Home");
                placeDao.updatePlace(homePlace);
            }

            mainHandler.post(() -> {
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
        long sevenDaysMs = 7L * 24 * 60 * 60 * 1000;
        long sevenDaysAgo = now - sevenDaysMs;

        executor.execute(() -> {
            if (!isAdded()) return;
            ActivityDatabase db = ActivityDatabase.getDatabase(requireContext());
            Place homePlace = db.placeDao().getHomePlace();

            long timeAtHomeMs = 0;
            if (homePlace != null) {
                timeAtHomeMs = db.activityDao().getTimeAtHomeSince(sevenDaysAgo, now);
            }
            long totalTime = db.activityDao().getSumDurationOfAllActivitiesLastSevenDays(sevenDaysAgo, now);
            final long finalTimeAtHomeMs = timeAtHomeMs;

            mainHandler.post(() -> {
                if (!isAdded()) return;
                if (homePlace == null) {
                    if (cabinFeverContent != null) cabinFeverContent.setVisibility(View.GONE);
                    if (cabinFeverPlaceholder != null) cabinFeverPlaceholder.setVisibility(View.VISIBLE);
                } else {
                    if (cabinFeverContent != null) cabinFeverContent.setVisibility(View.VISIBLE);
                    if (cabinFeverPlaceholder != null) cabinFeverPlaceholder.setVisibility(View.GONE);

                    int percentage = (int) (((float) finalTimeAtHomeMs / totalTime) * 100);
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