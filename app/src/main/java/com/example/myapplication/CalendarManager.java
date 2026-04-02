package com.example.myapplication;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.myapplication.db.ActivityDao;
import com.example.myapplication.db.ActivityDatabase;
import com.example.myapplication.db.MovementActivity;
import com.example.myapplication.db.StillLocation;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CalendarManager {

    public interface OnDateSelectedListener {
        void onDateSelected(Date date);
    }

    private static final ExecutorService diskExecutor = Executors.newSingleThreadExecutor();

    private final Context context;
    private final View dateCard;
    private final LinearLayout calendarContainer;
    private final LinearLayout weekHeader;
    private final GridLayout calendarGrid;
    private final TextView tvDayName;
    private final TextView tvDateFull;
    private final TextView tvMonthYear;
    private final ImageView ivExpand;
    private final ImageButton btnPrevMonth;
    private final ImageButton btnNextMonth;

    private final OnDateSelectedListener listener;

    private boolean calendarExpanded = false;
    private Date selectedDate = new Date();
    private int currentMonth;
    private int currentYear;

    private final ActivityDao dao;
    private final Map<String, List<MiniPieChartView.Slice>> sliceCache = new HashMap<>();

    public CalendarManager(View root, OnDateSelectedListener listener) {
        this.context = root.getContext();
        this.listener = listener;

        dateCard = root.findViewById(R.id.date_card);
        calendarContainer = root.findViewById(R.id.calendar_container);
        weekHeader = root.findViewById(R.id.week_header);
        calendarGrid = root.findViewById(R.id.calendar_grid);
        tvDayName = root.findViewById(R.id.tv_day_name);
        tvDateFull = root.findViewById(R.id.tv_date_full);
        tvMonthYear = root.findViewById(R.id.tv_month_year);
        ivExpand = root.findViewById(R.id.iv_expand);
        btnPrevMonth = root.findViewById(R.id.btn_prev_month);
        btnNextMonth = root.findViewById(R.id.btn_next_month);

        dao = ActivityDatabase.getDatabase(context.getApplicationContext()).activityDao();

        Calendar cal = Calendar.getInstance();
        cal.setTime(selectedDate);
        currentMonth = cal.get(Calendar.MONTH);
        currentYear = cal.get(Calendar.YEAR);

        init();
    }

    private void init() {
        buildWeekHeader();
        updateHeader(selectedDate);
        renderCalendar();

        dateCard.setOnClickListener(v -> toggleCalendar());

        btnPrevMonth.setOnClickListener(v -> {
            if (currentMonth == Calendar.JANUARY) {
                currentMonth = Calendar.DECEMBER;
                currentYear--;
            } else {
                currentMonth--;
            }
            renderCalendar();
        });

        btnNextMonth.setOnClickListener(v -> {
            Calendar today = Calendar.getInstance();
            int thisMonth = today.get(Calendar.MONTH);
            int thisYear = today.get(Calendar.YEAR);

            if (currentYear > thisYear || (currentYear == thisYear && currentMonth >= thisMonth)) {
                return;
            }

            if (currentMonth == Calendar.DECEMBER) {
                currentMonth = Calendar.JANUARY;
                currentYear++;
            } else {
                currentMonth++;
            }

            renderCalendar();
        });

        collapseCalendar();
    }

    public Date getSelectedDate() {
        return selectedDate;
    }

    public void toggleCalendar() {
        calendarExpanded = !calendarExpanded;
        calendarContainer.setVisibility(calendarExpanded ? View.VISIBLE : View.GONE);
        ivExpand.setRotation(calendarExpanded ? 180f : 0f);
        if (calendarExpanded) {
            Calendar selected = Calendar.getInstance();
            selected.setTime(selectedDate);
            currentMonth = selected.get(Calendar.MONTH);
            currentYear = selected.get(Calendar.YEAR);
            renderCalendar();
        }
    }

    public void collapseCalendar() {
        calendarExpanded = false;
        calendarContainer.setVisibility(View.GONE);
        ivExpand.setRotation(0f);
    }

    private void updateHeader(Date date) {
        SimpleDateFormat dayFmt = new SimpleDateFormat("EEEE", Locale.getDefault());
        SimpleDateFormat fullFmt = new SimpleDateFormat("MMMM d, yyyy", Locale.getDefault());
        tvDayName.setText(dayFmt.format(date));
        tvDateFull.setText(fullFmt.format(date));
    }

    private void buildWeekHeader() {
        weekHeader.removeAllViews();
        String[] days = {"S", "M", "T", "W", "T", "F", "S"};

        for (String day : days) {
            TextView tv = new TextView(context);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            tv.setLayoutParams(params);
            tv.setText(day);
            tv.setTextColor(ContextCompat.getColor(context, R.color.on_surface_variant));
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            tv.setTypeface(Typeface.DEFAULT_BOLD);
            tv.setGravity(Gravity.CENTER);
            weekHeader.addView(tv);
        }
    }

    private void renderCalendar() {
        Calendar monthCal = Calendar.getInstance();
        monthCal.set(Calendar.YEAR, currentYear);
        monthCal.set(Calendar.MONTH, currentMonth);
        monthCal.set(Calendar.DAY_OF_MONTH, 1);

        SimpleDateFormat monthFmt = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        tvMonthYear.setText(monthFmt.format(monthCal.getTime()));

        calendarGrid.removeAllViews();

        int firstDayOffset = monthCal.get(Calendar.DAY_OF_WEEK) - 1;
        int daysInMonth = monthCal.getActualMaximum(Calendar.DAY_OF_MONTH);

        Calendar today = Calendar.getInstance();
        clearTime(today);

        Calendar selectedCal = Calendar.getInstance();
        selectedCal.setTime(selectedDate);
        clearTime(selectedCal);

        int totalCells = 42;

        for (int cellIndex = 0; cellIndex < totalCells; cellIndex++) {
            int dayNumber = cellIndex - firstDayOffset + 1;

            if (dayNumber < 1 || dayNumber > daysInMonth) {
                calendarGrid.addView(createEmptyCell());
            } else {
                Calendar cellCal = Calendar.getInstance();
                cellCal.set(Calendar.YEAR, currentYear);
                cellCal.set(Calendar.MONTH, currentMonth);
                cellCal.set(Calendar.DAY_OF_MONTH, dayNumber);
                clearTime(cellCal);

                boolean isFuture = cellCal.after(today);
                boolean isSelected =
                        cellCal.get(Calendar.YEAR) == selectedCal.get(Calendar.YEAR) &&
                                cellCal.get(Calendar.MONTH) == selectedCal.get(Calendar.MONTH) &&
                                cellCal.get(Calendar.DAY_OF_MONTH) == selectedCal.get(Calendar.DAY_OF_MONTH);

                calendarGrid.addView(createDayCell(dayNumber, cellCal.getTime(), isSelected, isFuture));
            }
        }

        updateNextButtonState();
    }

    private View createEmptyCell() {
        LinearLayout cell = new LinearLayout(context);
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = dp(58);
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.setMargins(dp(2), dp(4), dp(2), dp(4));
        cell.setLayoutParams(params);
        return cell;
    }

    private View createDayCell(int day, Date cellDate, boolean isSelected, boolean isFuture) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = dp(58);
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.setMargins(dp(2), dp(4), dp(2), dp(4));
        root.setLayoutParams(params);

        TextView tvDay = new TextView(context);
        tvDay.setText(String.valueOf(day));
        tvDay.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvDay.setGravity(Gravity.CENTER);

        if (isFuture) {
            tvDay.setTextColor(ContextCompat.getColor(context, R.color.on_surface_variant));
            tvDay.setAlpha(0.3f);
        } else if (isSelected) {
            tvDay.setTextColor(ContextCompat.getColor(context, R.color.primary));
            tvDay.setTypeface(Typeface.DEFAULT_BOLD);
        } else {
            tvDay.setTextColor(ContextCompat.getColor(context, R.color.on_surface));
        }

        MiniPieChartView pieChartView = new MiniPieChartView(context);
        LinearLayout.LayoutParams circleParams = new LinearLayout.LayoutParams(dp(20), dp(20));
        circleParams.topMargin = dp(6);
        pieChartView.setLayoutParams(circleParams);

        pieChartView.setSelected(isSelected);
        pieChartView.setFuture(isFuture);

        if (!isFuture) {
            String cacheKey = getCacheKey(cellDate);
            if (sliceCache.containsKey(cacheKey)) {
                pieChartView.setSlices(sliceCache.get(cacheKey));
            } else {
                diskExecutor.execute(() -> {
                    List<MiniPieChartView.Slice> slices = calculateSlicesForDate(cellDate);
                    sliceCache.put(cacheKey, slices);
                    root.post(() -> pieChartView.setSlices(slices));
                });
            }

            root.setOnClickListener(v -> {
                selectedDate = cellDate;
                updateHeader(selectedDate);
                renderCalendar();
                collapseCalendar();

                if (listener != null) {
                    listener.onDateSelected(selectedDate);
                }
            });
        }

        root.addView(tvDay);
        root.addView(pieChartView);
        return root;
    }

    private String getCacheKey(Date date) {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        return fmt.format(date);
    }

    private List<MiniPieChartView.Slice> calculateSlicesForDate(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date startOfDay = cal.getTime();

        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 999);
        Date endOfDay = cal.getTime();

        List<StillLocation> stills = dao.getStillForRange(startOfDay, endOfDay);
        List<MovementActivity> movements = dao.getMovementForRange(startOfDay, endOfDay);

        long totalStillMillis = 0;
        for (StillLocation still : stills) {
            totalStillMillis += calculateDurationInRange(still.startTimeDate, still.endTimeDate, startOfDay, endOfDay);
        }

        Map<String, Long> movementDurations = new HashMap<>();
        for (MovementActivity movement : movements) {
            String type = movement.activityType != null ? movement.activityType : "Unknown";
            long duration = calculateDurationInRange(movement.startTimeDate, movement.endTimeDate, startOfDay, endOfDay);
            movementDurations.put(type, movementDurations.getOrDefault(type, 0L) + duration);
        }

        List<MiniPieChartView.Slice> slices = new ArrayList<>();
        
        if (totalStillMillis > 0) {
            slices.add(new MiniPieChartView.Slice(totalStillMillis / 60000f, ContextCompat.getColor(context, R.color.activity_still)));
        }
        
        for (Map.Entry<String, Long> entry : movementDurations.entrySet()) {
            if (entry.getValue() > 0) {
                slices.add(new MiniPieChartView.Slice(entry.getValue() / 60000f, getColorForActivity(entry.getKey())));
            }
        }

        float totalTrackedMillis = totalStillMillis;
        for (Long d : movementDurations.values()) totalTrackedMillis += d;
        
        float remainingMinutes = Math.max(0, 1440f - (totalTrackedMillis / 60000f));
        if (remainingMinutes > 0) {
            slices.add(new MiniPieChartView.Slice(remainingMinutes, Color.parseColor("#EEEEEE")));
        }

        return slices;
    }

    private int getColorForActivity(String type) {
        if (type == null) return ContextCompat.getColor(context, R.color.on_surface_variant);
        switch (type.toLowerCase()) {
            case "walking": 
            case "on foot":
            case "running":
                return ContextCompat.getColor(context, R.color.activity_walking);
            case "in_vehicle": 
            case "driving":
                return ContextCompat.getColor(context, R.color.activity_vehicle);
            default: 
                return ContextCompat.getColor(context, R.color.secondary);
        }
    }

    private long calculateDurationInRange(Date start, Date end, Date rangeStart, Date rangeEnd) {
        if (start == null) return 0;
        long s = Math.max(start.getTime(), rangeStart.getTime());
        long e = (end == null) ? Math.min(System.currentTimeMillis(), rangeEnd.getTime()) : Math.min(end.getTime(), rangeEnd.getTime());
        return Math.max(0, e - s);
    }

    private void updateNextButtonState() {
        Calendar today = Calendar.getInstance();
        int thisMonth = today.get(Calendar.MONTH);
        int thisYear = today.get(Calendar.YEAR);

        boolean canGoNext = currentYear < thisYear || (currentYear == thisYear && currentMonth < thisMonth);
        btnNextMonth.setEnabled(canGoNext);
        btnNextMonth.setAlpha(canGoNext ? 1f : 0.3f);
    }

    private void clearTime(Calendar cal) {
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                context.getResources().getDisplayMetrics()
        );
    }
}
