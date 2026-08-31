package com.example.myapplication.mainScreen.homeScreen;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.example.myapplication.R;
import com.example.myapplication.database.ActivityDao;
import com.example.myapplication.database.ActivityDatabase;
import com.example.myapplication.database.MovementActivity;
import com.example.myapplication.database.StillLocation;
import com.example.myapplication.helpers.UiFormatters;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class CalendarManager {

    public interface OnDateSelectedListener {
        void onDateSelected(Date date);
    }

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
    private int measuredHeight = 0; // Field to store the measured height
    private final ExecutorService databaseWriteExecutor;
    private final ActivityDao dao;

    private SharedPreferences sharedPreferences;
    private SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener;
    private static final String PREFS_NAME = "MyPrefs";
    private static final String KEY_WEEK_START_DAY = "week_start_day";

    public CalendarManager(View root, OnDateSelectedListener listener, ExecutorService databaseWriteExecutor) {
        this.context = root.getContext();
        this.listener = listener;
        this.databaseWriteExecutor = databaseWriteExecutor;

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

        this.sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        this.preferenceChangeListener = (prefs, key) -> {
            if (KEY_WEEK_START_DAY.equals(key)) {
                Calendar cal = Calendar.getInstance();
                cal.setFirstDayOfWeek(getWeekStartDayPreference());
                cal.setTime(selectedDate);
                currentMonth = cal.get(Calendar.MONTH);
                currentYear = cal.get(Calendar.YEAR);

                buildWeekHeader();
                renderCalendar();
            }
        };


        this.sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener);

        init();
    }
    public void destroy() {
        if (sharedPreferences != null && preferenceChangeListener != null) {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener);
        }
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
            today.setFirstDayOfWeek(getWeekStartDayPreference()); // Ensure consistency
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

        // Measure the calendar container's height after it's laid out
        calendarContainer.post(() -> {
            measuredHeight = calendarContainer.getMeasuredHeight();
            collapseCalendar(); // Collapse after measuring
        });
    }

    public Date getSelectedDate() {
        return selectedDate;
    }

    public void toggleCalendar() {
        if (measuredHeight == 0) {
            // If for some reason height wasn't measured, force a layout and re-measure
            calendarContainer.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
            measuredHeight = calendarContainer.getMeasuredHeight();
        }

        calendarExpanded = !calendarExpanded;
        int startHeight = calendarExpanded ? 0 : measuredHeight;
        int endHeight = calendarExpanded ? measuredHeight : 0;
        float startRotation = calendarExpanded ? 0f : 180f;
        float endRotation = calendarExpanded ? 180f : 0f;

        if (calendarExpanded) {
            calendarContainer.setVisibility(View.VISIBLE);
            Calendar selected = Calendar.getInstance();
            selected.setFirstDayOfWeek(getWeekStartDayPreference()); // Ensure consistency
            selected.setTime(selectedDate);
            currentMonth = selected.get(Calendar.MONTH);
            currentYear = selected.get(Calendar.YEAR);
            renderCalendar();
        }

        ValueAnimator heightAnimator = ValueAnimator.ofInt(startHeight, endHeight);
        heightAnimator.addUpdateListener(animation -> {
            int value = (int) animation.getAnimatedValue();
            LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) calendarContainer.getLayoutParams();
            layoutParams.height = value;
            calendarContainer.setLayoutParams(layoutParams);
        });
        heightAnimator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(@NonNull Animator animation) {}

            @Override
            public void onAnimationEnd(@NonNull Animator animation) {
                if (!calendarExpanded) {
                    calendarContainer.setVisibility(View.GONE);
                }
            }

            @Override
            public void onAnimationCancel(@NonNull Animator animation) {}

            @Override
            public void onAnimationRepeat(@NonNull Animator animation) {}
        });
        heightAnimator.setDuration(300); // 300ms duration
        heightAnimator.start();

        ivExpand.animate().rotation(endRotation).setDuration(300).start();
    }

    public void collapseCalendar() {
        if (measuredHeight == 0) {
            // If for some reason height wasn't measured, force a layout and re-measure
            calendarContainer.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
            measuredHeight = calendarContainer.getMeasuredHeight();
        }

        if (calendarExpanded) { // Only collapse if it's currently expanded
            calendarExpanded = false;
            ValueAnimator heightAnimator = ValueAnimator.ofInt(measuredHeight, 0);
            heightAnimator.addUpdateListener(animation -> {
                int value = (int) animation.getAnimatedValue();
                LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) calendarContainer.getLayoutParams();
                layoutParams.height = value;
                calendarContainer.setLayoutParams(layoutParams);
            });
            heightAnimator.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(@NonNull Animator animation) {}

                @Override
                public void onAnimationEnd(@NonNull Animator animation) {
                    calendarContainer.setVisibility(View.GONE);
                }

                @Override
                public void onAnimationCancel(@NonNull Animator animation) {}

                @Override
                public void onAnimationRepeat(@NonNull Animator animation) {}
            });
            heightAnimator.setDuration(300);
            heightAnimator.start();

            ivExpand.animate().rotation(0f).setDuration(300).start();
        } else {
            // If not expanded, just ensure it's hidden and rotation is reset
            calendarContainer.setVisibility(View.GONE);
            LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) calendarContainer.getLayoutParams();
            layoutParams.height = 0;
            calendarContainer.setLayoutParams(layoutParams);
            ivExpand.setRotation(0f);
        }
    }

    private void updateHeader(Date date) {
        tvDayName.setText(UiFormatters.dayOfWeek(date));
        tvDateFull.setText(UiFormatters.fullDate(date));
    }

    private void buildWeekHeader() {
        weekHeader.removeAllViews();

        // Get the preferred start day of the week
        int preferredFirstDayOfWeek = getWeekStartDayPreference();

        String[] daysShort = {"S", "M", "T", "W", "T", "F", "S"}; // 0=Sunday, 1=Monday, ..., 6=Saturday
        String[] orderedDays = new String[7];

        // Convert Calendar.DAY_OF_WEEK (1-7) to a 0-6 index for daysShort array
        int firstDayOfWeekIndex = (preferredFirstDayOfWeek - 1 + 7) % 7;

        // Populate orderedDays starting from the preferred first day of the week
        for (int i = 0; i < 7; i++) {
            int currentDayShortIndex = (firstDayOfWeekIndex + i) % 7;
            orderedDays[i] = daysShort[currentDayShortIndex];
        }

        for (String day : orderedDays) {
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
        monthCal.setFirstDayOfWeek(getWeekStartDayPreference()); // Set preferred first day of week
        monthCal.set(Calendar.YEAR, currentYear);
        monthCal.set(Calendar.MONTH, currentMonth);
        monthCal.set(Calendar.DAY_OF_MONTH, 1);

        tvMonthYear.setText(UiFormatters.monthYear(monthCal.getTime()));

        calendarGrid.removeAllViews();

        // Calculate first day offset based on the preferred first day of the week
        int preferredFirstDayOfWeek = getWeekStartDayPreference();
        int firstDayOfMonth = monthCal.get(Calendar.DAY_OF_WEEK);
        int firstDayOffset = (firstDayOfMonth - preferredFirstDayOfWeek + 7) % 7;

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
                cellCal.setFirstDayOfWeek(preferredFirstDayOfWeek); // Ensure consistency
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

        LinearLayout.LayoutParams tvParams = new LinearLayout.LayoutParams(dp(32), dp(32));
        tvDay.setLayoutParams(tvParams);

        if (isFuture) {
            tvDay.setTextColor(ContextCompat.getColor(context, R.color.on_surface_variant));
            tvDay.setAlpha(0.3f);
        } else if (isSelected) {
            tvDay.setTextColor(ContextCompat.getColor(context, R.color.on_primary));
            tvDay.setTypeface(Typeface.DEFAULT_BOLD);
            
            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.OVAL);
            shape.setColor(ContextCompat.getColor(context, R.color.primary));
            tvDay.setBackground(shape);
        } else {
            tvDay.setTextColor(ContextCompat.getColor(context, R.color.on_surface));
        }
        root.addView(tvDay); // Add tvDay first

        if (!isFuture) {
            databaseWriteExecutor.execute(() -> {
                boolean hasData = hasDataForDate(cellDate);
                root.post(() -> {
                    DayDataIndicatorView indicatorView = new DayDataIndicatorView(context);
                    LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(6), dp(6));
                    dotParams.topMargin = dp(4);
                    indicatorView.setLayoutParams(dotParams);

                    indicatorView.setHasData(hasData);
                    root.addView(indicatorView);
                });
            });


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

        return root;
    }


    private boolean hasDataForDate(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long startOfDayMillis = cal.getTimeInMillis();

        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 999);
        long endOfDayMillis = cal.getTimeInMillis();

        List<StillLocation> stills = dao.getStillsFromRange(new Date(startOfDayMillis), new Date(endOfDayMillis));
        if (!stills.isEmpty()) return true;

        List<MovementActivity> movements = dao.getMovementsFromRange(new Date(startOfDayMillis), new Date(endOfDayMillis));
        return !movements.isEmpty();
    }


    private long calculateDurationInRange(Date start, Date end, Date rangeStart, Date rangeEnd) {
        if (start == null) return 0;
        long s = Math.max(start.getTime(), rangeStart.getTime());
        long e = (end == null) ? Math.min(System.currentTimeMillis(), rangeEnd.getTime()) : Math.min(end.getTime(), rangeEnd.getTime());
        return Math.max(0, e - s);
    }

    private void updateNextButtonState() {
        Calendar today = Calendar.getInstance();
        today.setFirstDayOfWeek(getWeekStartDayPreference()); // Ensure consistency
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

    private int getWeekStartDayPreference() {
        SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        // Default to Monday if no preference is set
        return preferences.getInt(KEY_WEEK_START_DAY, Calendar.MONDAY);
    }
}