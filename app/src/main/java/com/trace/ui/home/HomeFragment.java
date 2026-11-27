package com.trace.ui.home;
import com.trace.data.model.SleepSession;
import com.trace.ui.home.calendar.CalendarManager;
import com.trace.ui.home.map.MapManager;
import com.trace.ui.home.phoneUsage.PhoneUsageMiniCardManager;
import com.trace.ui.home.sheet.AddCustomStillActivitySheet;
import com.trace.ui.home.sheet.AddCustomMovementActivitySheet;
import com.trace.ui.home.sheet.EditMovementActivitySheet;
import com.trace.ui.home.sheet.EditStillActivitySheet;
import com.trace.ui.home.sheet.EditSleepSheet;
import com.trace.ui.home.timeline.TimelineAdapter;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.trace.TraceApp;
import com.trace.data.db.ActivityDao;
import com.trace.data.db.ActivityDatabase;
import com.trace.data.model.MovementActivity;
import com.trace.data.model.StillLocation;
import com.trace.data.model.TimelineItem;
import com.trace.R;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class HomeFragment extends Fragment {

    private static final String TAG = "HomeFragment";


    private ActivityDao dao;
    private com.trace.data.db.SleepDao sleepDao;

    private TimelineAdapter timelineAdapter;;

    private MapManager mapManager;
    private CalendarManager calendarManager;
    private PhoneUsageMiniCardManager phoneUsageMiniCardManager;

    private TraceApp app;


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
        ImageView btnAddCustomActivity = view.findViewById(R.id.btnAddCustomActivity);
        ActivityDatabase db = ActivityDatabase.getDatabase(requireContext());
        dao = db.activityDao();
        sleepDao = db.sleepDao();
        app = (TraceApp) requireActivity().getApplication();

        View phoneUsageCard = view.findViewById(R.id.phone_usage_card_content);
        if (phoneUsageCard != null) {
            phoneUsageMiniCardManager = new PhoneUsageMiniCardManager(requireContext(), phoneUsageCard);
        }

        if (btnAddCustomActivity != null) {
            btnAddCustomActivity.setOnClickListener(v -> {
                PopupMenu popup = new PopupMenu(requireContext(), btnAddCustomActivity);
                popup.getMenu().add(0, 1, 0, "Add Visit");
                popup.getMenu().add(0, 2, 0, "Add Movement");

                popup.setOnMenuItemClickListener(item -> {
                    Date date = (calendarManager != null) ? calendarManager.getSelectedDate()
                            : new Date();

                    if (item.getItemId() == 1) {
                        AddCustomStillActivitySheet sheet = AddCustomStillActivitySheet.newInstance(date, () -> {
                            loadHomeData(calendarManager.getSelectedDate());
                        });
                        sheet.show(getChildFragmentManager(), "AddCustomStillActivitySheet");
                        return true;
                    } else if (item.getItemId() == 2) {
                        AddCustomMovementActivitySheet sheet = AddCustomMovementActivitySheet.newInstance(date, () -> {
                            loadHomeData(calendarManager.getSelectedDate());
                        });
                        sheet.show(getChildFragmentManager(), "AddCustomMovementActivitySheet");
                        return true;
                    }
                    return false;
                });
                popup.show();
            });
        }

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
        // ---------------- initialize calendar -----------------
        calendarManager = new CalendarManager(view, date -> {
            loadHomeData(date);
        }, app.getBackgroundWriteExecutor());

        // ----------------- initialize timeline --------------------
        timelineAdapter = new TimelineAdapter(item -> {
            if (mapManager != null) {
                mapManager.focusOnItem(item);
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

                    @Override
                    public void onSleepEditButtonClick(SleepSession sleep) {
                        showSleepEditSheet(sleep);
                    }
                });
        rvTimeline.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvTimeline.setAdapter(timelineAdapter);

    }

    private void showStillEditSheet(StillLocation still) {
        EditStillActivitySheet sheet = EditStillActivitySheet.newInstance(still.copy(), new EditStillActivitySheet.OnVisitChangeListener() {
            @Override
            public void onUpdate(StillLocation updatedStill) {
                app.getBackgroundWriteExecutor().execute(() -> {
                            dao.updateStillAndSyncPlace(updatedStill);
                            if (isAdded()) {
                                requireActivity().runOnUiThread(() -> {
                                    loadHomeData(calendarManager.getSelectedDate());
                                });
                            }
                        }
                );
            }

            @Override
            public void onDelete(StillLocation stillToDelete) {
                app.getBackgroundWriteExecutor().execute(() -> {
                            dao.deleteStillLocation(stillToDelete.getId());
                            if (isAdded()) {
                                requireActivity().runOnUiThread(() -> {
                                    loadHomeData(calendarManager.getSelectedDate());
                                });
                            }
                        }
                );
            }
        });
        sheet.show(getChildFragmentManager(), "PlaceLabelSheet");
    }

    private void showMovementEditSheet(MovementActivity movement) {
        EditMovementActivitySheet sheet = EditMovementActivitySheet.newInstance(movement.copy(), new EditMovementActivitySheet.OnVisitInteractionListener() {
            @Override
            public void onUpdate(MovementActivity movement) {
                app.getBackgroundWriteExecutor().execute(() -> {
                            dao.updateMovementActivity(movement);
                            if (isAdded()) {
                                requireActivity().runOnUiThread(() -> {
                                    loadHomeData(calendarManager.getSelectedDate());
                                });
                            }
                        }
                );
            }

            @Override
            public void onDelete(MovementActivity movement) {
                app.getBackgroundWriteExecutor().execute(() -> {
                            dao.deleteMovementActivity(movement.getId());
                            if (isAdded()) {
                                requireActivity().runOnUiThread(() -> {
                                    loadHomeData(calendarManager.getSelectedDate());
                                });
                            }
                        }
                );
            }
        });
        sheet.show(getChildFragmentManager(), "PlaceLabelSheet");
    }

    private void showSleepEditSheet(SleepSession sleep) {
        EditSleepSheet sheet = EditSleepSheet.newInstance(sleep.copy(), new EditSleepSheet.OnSleepInteractionListener() {
            @Override
            public void onUpdate(SleepSession updatedSleep) {
                app.getBackgroundWriteExecutor().execute(() -> {
                    sleepDao.updateSleepSession(updatedSleep);
                    if (isAdded()) {
                        requireActivity().runOnUiThread(() -> {
                            loadHomeData(calendarManager.getSelectedDate());
                        });
                    }
                });
            }

            @Override
            public void onDelete(SleepSession sleepToDelete) {
                app.getBackgroundWriteExecutor().execute(() -> {
                    sleepDao.deleteSleepSession(sleepToDelete);
                    if (isAdded()) {
                        requireActivity().runOnUiThread(() -> {
                            loadHomeData(calendarManager.getSelectedDate());
                        });
                    }
                });
            }
        });
        sheet.show(getChildFragmentManager(), "EditSleepSheet");
    }

    private void loadHomeData(Date date) {
        if (date == null) return;

        if (phoneUsageMiniCardManager != null) {
            phoneUsageMiniCardManager.loadUsageDataForDate(date);
        }

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

        app.getBackgroundWriteExecutor().execute(() -> {
            List<StillLocation> stills = dao.getStillsFromRange(start, end);
            List<MovementActivity> movements = dao.getMovementsFromRange(start, end);
            List<SleepSession> sleeps = sleepDao.getSleepSessionsFromRange(start, end);

            List<TimelineItem> rawCombined = new ArrayList<>();
            rawCombined.addAll(movements);
            rawCombined.addAll(sleeps);

            // hide portions of stills overlapping with sleep
            for (StillLocation still : stills) {
                if (still.getStartTimeDate() == null) {
                    rawCombined.add(still);
                    continue;
                }

                List<StillLocation> stillList = new ArrayList<>();
                stillList.add(still);

                for (SleepSession sleep : sleeps) {
                    if (sleep.getStartTimeDate() == null || sleep.getEndTimeDate() == null) continue;

                    List<StillLocation> newParts = new ArrayList<>();
                    for (StillLocation part : stillList) {
                        Date partStart = part.getStartTimeDate();
                        Date partEnd = part.getEndTimeDate();
                        boolean isOngoing = false;
                        if (partEnd == null) {
                            partEnd = new Date();
                            isOngoing = true;
                        }

                        if (partStart.before(sleep.getEndTimeDate()) && partEnd.after(sleep.getStartTimeDate())) {
                            // Overlap found
                            if (partStart.before(sleep.getStartTimeDate())) {
                                StillLocation before = part.copy();
                                before.setEndTimeDate(sleep.getStartTimeDate());
                                before.setOriginalStill(still);
                                newParts.add(before);
                            }
                            if (partEnd.after(sleep.getEndTimeDate())) {
                                StillLocation after = part.copy();
                                after.setStartTimeDate(sleep.getEndTimeDate());
                                if (isOngoing) {
                                    after.setEndTimeDate(null);
                                }
                                after.setOriginalStill(still);
                                newParts.add(after);
                            }
                        } else {
                            newParts.add(part);
                        }
                    }
                    stillList = newParts;
                }
                rawCombined.addAll(stillList);
            }

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
                } else if (item instanceof SleepSession) {
                    processedCombined.add(item);
                    lastMovement = null;
                }
            }

            if (isAdded()) {
                requireActivity().runOnUiThread(() -> timelineAdapter.submitList(processedCombined)); // updates the list
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (calendarManager != null) {
            calendarManager.setSelectedDate(calendarManager.getSelectedDate());
        }
        if (mapManager != null) {
            mapManager.onResume();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    public void setSelectedDate(Date date) {
        if (calendarManager != null) {
            calendarManager.setSelectedDate(date);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (phoneUsageMiniCardManager != null) {
            phoneUsageMiniCardManager.destroy();
        }
        calendarManager.destroy();
    }


}