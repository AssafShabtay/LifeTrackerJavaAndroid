package com.example.myapplication.mainScreen.homeScreen;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.LifeTrackerApp;
import com.example.myapplication.R;
import com.example.myapplication.database.ActivityDao;
import com.example.myapplication.database.ActivityDatabase;
import com.example.myapplication.database.MovementActivity;
import com.example.myapplication.database.MovementWithHabit;
import com.example.myapplication.database.StillLocation;
import com.example.myapplication.database.StayWithHabit;
import com.example.myapplication.database.TimelineItem;
import com.example.myapplication.helpers.HabitManager;
import com.example.myapplication.helpers.UiFormatters;

import java.util.ArrayList;
import java.util.Calendar;
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

    // Routine Hero Card Views
    private TextView tvStreakCount;
    private TextView tvRoutineScoreLabel;
    private TextView tvRoutinesDoneSub;
    private ProgressBar progressRoutineRing;
    private TextView tvCounterFocusGoal;
    private TextView tvCounterWalkGoal;
    private TextView tvCounterPunctualGoal;

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

        // Bind Routine Hero Card Views
        tvStreakCount = view.findViewById(R.id.tv_streak_count);
        tvRoutineScoreLabel = view.findViewById(R.id.tv_routine_score_label);
        tvRoutinesDoneSub = view.findViewById(R.id.tv_routines_done_sub);
        progressRoutineRing = view.findViewById(R.id.progress_routine_ring);
        tvCounterFocusGoal = view.findViewById(R.id.tv_counter_focus_goal);
        tvCounterWalkGoal = view.findViewById(R.id.tv_counter_walk_goal);
        tvCounterPunctualGoal = view.findViewById(R.id.tv_counter_punctual_goal);

        Button btnShowFullDay = view.findViewById(R.id.btn_show_full_day);
        RecyclerView rvTimeline = view.findViewById(R.id.rvTimeline);
        ImageView btnAddCustomActivity = view.findViewById(R.id.btnAddCustomActivity);
        ActivityDatabase db = ActivityDatabase.getDatabase(requireContext());
        dao = db.activityDao();
        app = (LifeTrackerApp) requireActivity().getApplication();

        if (btnAddCustomActivity != null) {
            btnAddCustomActivity.setOnClickListener(v -> {
                PopupMenu popup = new PopupMenu(requireContext(), btnAddCustomActivity);
                popup.getMenu().add(0, 1, 0, "Add Visit");
                popup.getMenu().add(0, 2, 0, "Add Movement");

                popup.setOnMenuItemClickListener(item -> {
                    Date date = calendarManager != null ? calendarManager.getSelectedDate() : new Date();

                    if (item.getItemId() == 1) {
                        AddCustomActivitySheet sheet = AddCustomActivitySheet.newInstance(date, () -> {
                            loadTimelineData(calendarManager.getSelectedDate());
                        });
                        sheet.show(getChildFragmentManager(), "AddCustomActivitySheet");
                        return true;
                    } else if (item.getItemId() == 2) {
                        AddCustomMovementActivitySheet sheet = AddCustomMovementActivitySheet.newInstance(date, () -> {
                            loadTimelineData(calendarManager.getSelectedDate());
                        });
                        sheet.show(getChildFragmentManager(), "AddCustomMovementActivitySheet");
                        return true;
                    }
                    return false;
                });
                popup.show();
            });
        }

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

        calendarManager = new CalendarManager(view, date -> {
            loadTimelineData(date);
        }, app.getDatabaseWriteExecutor());

        timelineAdapter = new TimelineAdapter(item -> {
            if (mapManager != null) {
                mapManager.focusOnItem(item);
            }
            if (item instanceof StayWithHabit) {
                RoutineDetailBottomSheet sheet = RoutineDetailBottomSheet.newInstanceForStay((StayWithHabit) item);
                sheet.show(getChildFragmentManager(), "RoutineDetailBottomSheet");
            } else if (item instanceof MovementWithHabit) {
                RoutineDetailBottomSheet sheet = RoutineDetailBottomSheet.newInstanceForMovement((MovementWithHabit) item);
                sheet.show(getChildFragmentManager(), "RoutineDetailBottomSheet");
            }
        },
                new TimelineAdapter.OnEditButtonClickListener() {
                    @Override
                    public void onStillEditButtonClick(StillLocation still) {
                        showStillEditSheet(still);
                    }

                    @Override
                    public void onMovementEditButtonClick(MovementActivity movement) {
                        showMovementEditSheet(movement);
                    }
                });
        rvTimeline.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvTimeline.setAdapter(timelineAdapter);
    }

    private void showStillEditSheet(StillLocation still) {
        EditStillActivitySheet sheet = EditStillActivitySheet.newInstance(still, new EditStillActivitySheet.OnVisitInteractionListener() {
            @Override
            public void onUpdate(StillLocation updatedStill) {
                app.getDatabaseWriteExecutor().execute(() -> {
                    dao.updateStillAndSyncPlace(updatedStill);
                    if (isAdded()) {
                        requireActivity().runOnUiThread(() -> {
                            loadTimelineData(calendarManager.getSelectedDate());
                        });
                    }
                });
            }

            @Override
            public void onDelete(StillLocation stillToDelete) {
                app.getDatabaseWriteExecutor().execute(() -> {
                    dao.deleteStillLocation(stillToDelete.getId());
                    if (isAdded()) {
                        requireActivity().runOnUiThread(() -> {
                            loadTimelineData(calendarManager.getSelectedDate());
                        });
                    }
                });
            }
        });
        sheet.show(getChildFragmentManager(), "PlaceLabelSheet");
    }

    private void showMovementEditSheet(MovementActivity movement) {
        EditMovementActivitySheet sheet = EditMovementActivitySheet.newInstance(movement, new EditMovementActivitySheet.OnVisitInteractionListener() {
            @Override
            public void onUpdate(MovementActivity movement) {
                app.getDatabaseWriteExecutor().execute(() -> {
                    dao.updateMovementActivity(movement);
                    if (isAdded()) {
                        requireActivity().runOnUiThread(() -> {
                            loadTimelineData(calendarManager.getSelectedDate());
                        });
                    }
                });
            }

            @Override
            public void onDelete(MovementActivity movement) {
                app.getDatabaseWriteExecutor().execute(() -> {
                    dao.deleteMovementActivity(movement.getId());
                    if (isAdded()) {
                        requireActivity().runOnUiThread(() -> {
                            loadTimelineData(calendarManager.getSelectedDate());
                        });
                    }
                });
            }
        });
        sheet.show(getChildFragmentManager(), "PlaceLabelSheet");
    }

    private void loadTimelineData(Date date) {
        if (date == null) return;

        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date start = cal.getTime();

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

            rawCombined.sort((a, b) -> {
                if (a.getStartTimeDate() == null || b.getStartTimeDate() == null) return 0;
                return a.getStartTimeDate().compareTo(b.getStartTimeDate());
            });

            List<TimelineItem> processedCombined = new ArrayList<>();
            MovementActivity lastMovement = null;
            for (TimelineItem item : rawCombined) {
                if (item instanceof StillLocation) {
                    StillLocation still = (StillLocation) item;
                    if (lastMovement != null && ((still.getStartTimeDate() != null && still.getEndTimeDate() != null &&
                            lastMovement.getStartTimeDate() != null && lastMovement.getEndTimeDate() != null &&
                            still.getStartTimeDate().after(lastMovement.getStartTimeDate()) &&
                            still.getEndTimeDate().before(lastMovement.getEndTimeDate())))) {
                        still.setIsStop(true);
                        lastMovement.getStops().add(still);
                    } else {
                        processedCombined.add(still);
                        lastMovement = null;
                    }
                } else if (item instanceof MovementActivity) {
                    lastMovement = (MovementActivity) item;
                    processedCombined.add(lastMovement);
                }
            }

            // Evaluate Daily Habit Summary
            HabitManager.DailyHabitSummary summary = HabitManager.evaluateDailyHabits(processedCombined);

            // Enrich Items with Habit Progress
            List<TimelineItem> enrichedList = new ArrayList<>();
            for (TimelineItem item : processedCombined) {
                if (item instanceof StillLocation) {
                    StillLocation still = (StillLocation) item;
                    String cat = still.getCategory() != null ? still.getCategory().toLowerCase() : "";
                    String name = still.getPlaceName() != null ? still.getPlaceName().toLowerCase() : "";

                    boolean isFocus = cat.contains("work") || cat.contains("office") || cat.contains("school")
                            || name.contains("work") || name.contains("office");

                    long target = isFocus ? HabitManager.DEFAULT_WORK_FOCUS_TARGET_MS : (2 * 60 * 60 * 1000L);
                    long actual = still.getEndTimeDate() != null && still.getStartTimeDate() != null
                            ? still.getEndTimeDate().getTime() - still.getStartTimeDate().getTime() : 0;
                    boolean isMet = actual >= target;
                    String badge = isMet ? "[✓ Goal Met]" : "[In Progress]";

                    enrichedList.add(new StayWithHabit(still, target, isMet, badge, summary.getStreakDays()));
                } else if (item instanceof MovementActivity) {
                    MovementActivity movement = (MovementActivity) item;
                    long target = HabitManager.DEFAULT_WALK_TARGET_MS;
                    long actual = movement.getEndTimeDate() != null && movement.getStartTimeDate() != null
                            ? movement.getEndTimeDate().getTime() - movement.getStartTimeDate().getTime() : 0;
                    boolean isMet = actual >= target;
                    String badge = isMet ? "[✓ Walk Goal Met]" : "[Active Movement]";

                    enrichedList.add(new MovementWithHabit(movement, target, isMet, badge));
                }
            }

            if (isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    timelineAdapter.submitList(enrichedList);

                    // Populate Routine Hero Card
                    if (tvStreakCount != null) {
                        tvStreakCount.setText("🔥 " + summary.getStreakDays() + " Day Streak");
                    }
                    if (tvRoutineScoreLabel != null) {
                        tvRoutineScoreLabel.setText("Routine Completion: " + summary.getOverallScorePct() + "%");
                    }
                    if (tvRoutinesDoneSub != null) {
                        tvRoutinesDoneSub.setText(summary.getCompletedGoals() + " / " + summary.getTotalGoals() + " Goals Met");
                    }
                    if (progressRoutineRing != null) {
                        progressRoutineRing.setProgress(summary.getOverallScorePct());
                    }

                    if (tvCounterFocusGoal != null) {
                        tvCounterFocusGoal.setText(UiFormatters.formatDurationMs(summary.getFocusActualMs()));
                    }
                    if (tvCounterWalkGoal != null) {
                        tvCounterWalkGoal.setText(UiFormatters.formatDurationMs(summary.getWalkActualMs())
                                + " / " + UiFormatters.formatDurationMs(summary.getWalkTargetMs()));
                    }
                    if (tvCounterPunctualGoal != null) {
                        tvCounterPunctualGoal.setText(summary.getArrivalPunctualityText());
                    }
                });
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

    @Override
    public void onDestroy() {
        super.onDestroy();
        calendarManager.destroy();
    }

    private void startPeriodicRefresh() {
        refreshHandler.removeCallbacks(refreshRunnable);
        refreshHandler.postDelayed(refreshRunnable, UPDATE_INTERVAL_MS);
    }

    private void stopPeriodicRefresh() {
        refreshHandler.removeCallbacks(refreshRunnable);
    }
}
