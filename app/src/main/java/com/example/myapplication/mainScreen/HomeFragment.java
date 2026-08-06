package com.example.myapplication.mainScreen;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

// Removed import com.example.myapplication.locationTracking.GeofenceManager;
import com.example.myapplication.LifeTrackerApp;
import com.example.myapplication.MainActivity;
import com.example.myapplication.database.ActivityDao;
import com.example.myapplication.database.ActivityDatabase;
import com.example.myapplication.database.MovementActivity;
import com.example.myapplication.database.StillLocation;
import com.example.myapplication.database.TimelineItem;
import com.example.myapplication.R;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class HomeFragment extends Fragment {

    private static final String TAG = "HomeFragment";

    private static final long UPDATE_INTERVAL_MS = 2 * 60 * 1000; // 2 minutes

    private ActivityDao dao;

    private TimelineAdapter timelineAdapter;

    private MapManager mapManager;
    private CalendarManager calendarManager;

    private LifeTrackerApp app;

    // refresh ui every 5 minutes
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshRunnable = new Runnable() {
        public void run() {
            loadTimelineData(calendarManager.getSelectedDate());
            refreshHandler.postDelayed(this, UPDATE_INTERVAL_MS);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Button btnShowFullDay = view.findViewById(R.id.btn_show_full_day);
        RecyclerView rvTimeline = view.findViewById(R.id.rvTimeline);
        ActivityDatabase db = ActivityDatabase.getDatabase(requireContext());
        dao = db.activityDao();
        app = (LifeTrackerApp) requireActivity().getApplication();

        // --------------- initialize map ---------------
        if (btnShowFullDay != null) {
            btnShowFullDay.setOnClickListener(v -> {
                if (mapManager != null && timelineAdapter != null) {
                    mapManager.showFullDay(timelineAdapter.getCurrentList());
                }
            });
        }

        mapManager = new MapManager(this, R.id.map);
        mapManager.init();
        View mapView = view.findViewById(R.id.map);
        if (mapView != null) {
            final float[] downX = new float[1];
            final float[] downY = new float[1];
            final int CLICK_THRESHOLD = 10;

            mapView.setOnTouchListener((v, event) -> {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        downX[0] = event.getX();
                        downY[0] = event.getY();
                        v.getParent().requestDisallowInterceptTouchEvent(true);
                        break;

                    case MotionEvent.ACTION_MOVE:
                        v.getParent().requestDisallowInterceptTouchEvent(true);
                        break;

                    case MotionEvent.ACTION_UP:
                        v.getParent().requestDisallowInterceptTouchEvent(false);

                        float dx = Math.abs(event.getX() - downX[0]);
                        float dy = Math.abs(event.getY() - downY[0]);
                        if (dx < CLICK_THRESHOLD && dy < CLICK_THRESHOLD) {
                            v.performClick();
                        }
                        break;

                    case MotionEvent.ACTION_CANCEL:
                        v.getParent().requestDisallowInterceptTouchEvent(false);
                        break;
                }
                return false;
            });
        }
        // ---------------- initialize calendar -----------------
        calendarManager = new CalendarManager(view, date -> {
            loadTimelineData(date);
        }, app.getDatabaseWriteExecutor());

        // ----------------- initialize timeline --------------------
        timelineAdapter = new TimelineAdapter(item -> {
            if (mapManager != null) {
                mapManager.focusOnItem(item);
            }
        },
                still -> this.showEditSheet(still));
        rvTimeline.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvTimeline.setAdapter(timelineAdapter);

    }

    private void showEditSheet(StillLocation still) {
        EditActivitySheet sheet = EditActivitySheet.newInstance(still, updatedStill -> {
            app.getDatabaseWriteExecutor().execute(() -> {
                dao.updateStillLocation(updatedStill);
                if (isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    loadTimelineData(calendarManager.getSelectedDate());
                });
            }});
        });
        sheet.show(getChildFragmentManager(), "PlaceLabelSheet");
    }

    private void loadTimelineData(Date date) {
        if (date == null) return;
        //get start of the day
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date start = cal.getTime();
        //get end of the day
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 999);
        Date end = cal.getTime();

        app.getDatabaseWriteExecutor().execute(() -> {
            List<StillLocation> stills = dao.getStillsFromRange(start, end);
            List<MovementActivity> movements = dao.getMovementsFromRange(start, end);

            List<TimelineItem> rawCombined = new ArrayList<>();
            rawCombined.addAll(stills);
            rawCombined.addAll(movements);

            // Sort by start time, from earliest to latest
            rawCombined.sort((a, b) -> {
                if (a.getStartTimeDate() == null || b.getStartTimeDate() == null) return 0;
                return a.getStartTimeDate().compareTo(b.getStartTimeDate());
            });

            // check which stills are stops and add them to movement activities
            List<TimelineItem> processedCombined = new ArrayList<>();
            MovementActivity lastMovement = null;
            for (TimelineItem item : rawCombined) {
                if (item instanceof StillLocation) {
                    StillLocation still = (StillLocation) item;
                    // Check if this is a stop
                    if (lastMovement != null && ((still.getStartTimeDate() != null && still.getEndTimeDate() != null &&
                            lastMovement.getStartTimeDate() != null && lastMovement.getEndTimeDate() != null &&
                            still.getStartTimeDate().after(lastMovement.getStartTimeDate()) &&
                            still.getEndTimeDate().before(lastMovement.getEndTimeDate())))) {
                        still.setIsStop(true);
                        lastMovement.getStops().add(still);
                        // stops not added to processedCombined
                    } else {
                        // Not a stop, add to combined list
                        processedCombined.add(still);
                        lastMovement = null;
                    }
                } else if (item instanceof MovementActivity) {
                    lastMovement = (MovementActivity) item;
                    processedCombined.add(lastMovement);
                }
            }

            if (isAdded()) {
                requireActivity().runOnUiThread(() -> timelineAdapter.submitList(processedCombined)); // switch back to the main thread and updates the list
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        loadTimelineData(calendarManager.getSelectedDate());
        if (mapManager != null) {
            mapManager.onResume();
        }
        startPeriodicRefresh();
    }

    @Override
    public void onPause() {
        super.onPause();
        stopPeriodicRefresh();
    }


    private void startPeriodicRefresh() {
        refreshHandler.removeCallbacks(refreshRunnable);
        refreshHandler.postDelayed(refreshRunnable, UPDATE_INTERVAL_MS);
    }

    private void stopPeriodicRefresh() {
        refreshHandler.removeCallbacks(refreshRunnable);
    }


}