package com.example.myapplication.ui.home.calendar;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.myapplication.R;
import com.example.myapplication.data.db.ActivityDao;
import com.example.myapplication.data.db.ActivityDatabase;
import com.example.myapplication.data.model.MovementActivity;
import com.example.myapplication.data.model.StillLocation;
import com.example.myapplication.util.UiFormatters;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

public class CalendarManager {

    public interface OnDateSelectedListener {
        void onDateSelected(Date date);
    }

    private final Context context;
    private final ImageButton btnPrevDay;
    private final MaterialButton btnOpenCalendar;
    private final ImageButton btnNextDay;

    private final OnDateSelectedListener listener;

    private Date selectedDate = new Date();
    private int currentMonth;
    private int currentYear;
    private final ExecutorService databaseWriteExecutor;
    private final ActivityDao dao;

    private final SharedPreferences sharedPreferences;
    private final SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener;
    private static final String PREFS_NAME = "MyPrefs";
    private static final String KEY_WEEK_START_DAY = "week_start_day";

    public CalendarManager(View root, OnDateSelectedListener listener, ExecutorService databaseWriteExecutor) {
        this.context = root.getContext();
        this.listener = listener;
        this.databaseWriteExecutor = databaseWriteExecutor;

        btnPrevDay = root.findViewById(R.id.btn_prev_day);
        btnOpenCalendar = root.findViewById(R.id.btn_open_calendar);
        btnNextDay = root.findViewById(R.id.btn_next_day);

        dao = ActivityDatabase.getDatabase(context.getApplicationContext()).activityDao();

        this.sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        this.preferenceChangeListener = (prefs, key) -> {
            if (KEY_WEEK_START_DAY.equals(key)) {
                Calendar cal = Calendar.getInstance();
                cal.setFirstDayOfWeek(getWeekStartDayPreference());
                cal.setTime(selectedDate);
                currentMonth = cal.get(Calendar.MONTH);
                currentYear = cal.get(Calendar.YEAR);
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
        updateSmallCalendarUI();

        if (btnPrevDay != null) {
            btnPrevDay.setOnClickListener(v -> {
                Calendar cal = Calendar.getInstance();
                cal.setTime(selectedDate);
                cal.add(Calendar.DAY_OF_MONTH, -1);
                selectedDate = cal.getTime();

                updateSmallCalendarUI();
                if (listener != null) {
                    listener.onDateSelected(selectedDate);
                }
            });
        }

        if (btnNextDay != null) {
            btnNextDay.setOnClickListener(v -> {
                Calendar today = Calendar.getInstance();
                clearTime(today);

                Calendar cal = Calendar.getInstance();
                cal.setTime(selectedDate);
                clearTime(cal);

                if (cal.before(today)) {
                    cal.add(Calendar.DAY_OF_MONTH, 1);
                    selectedDate = cal.getTime();

                    updateSmallCalendarUI();
                    if (listener != null) {
                        listener.onDateSelected(selectedDate);
                    }
                }
            });
        }

        if (btnOpenCalendar != null) {
            btnOpenCalendar.setOnClickListener(v -> showCalendarDialog());
        }
    }

    public Date getSelectedDate() {
        return selectedDate;
    }

    private void updateSmallCalendarUI() {
        if (btnOpenCalendar != null) {
            btnOpenCalendar.setText(formatShortDate(selectedDate));
        }

        if (btnNextDay != null) {
            Calendar today = Calendar.getInstance();
            clearTime(today);

            Calendar cal = Calendar.getInstance();
            cal.setTime(selectedDate);
            clearTime(cal);

            boolean canGoNext = cal.before(today);
            btnNextDay.setEnabled(canGoNext);
            btnNextDay.setAlpha(canGoNext ? 1f : 0.3f);
        }
    }

    private String formatShortDate(Date date) {
        if (date == null) return "—";

        Calendar calToday = Calendar.getInstance();
        clearTime(calToday);
        long todayMs = calToday.getTimeInMillis();

        Calendar calYest = Calendar.getInstance();
        clearTime(calYest);
        calYest.add(Calendar.DAY_OF_MONTH, -1);
        long yesterdayMs = calYest.getTimeInMillis();

        Calendar targetCal = Calendar.getInstance();
        targetCal.setTime(date);
        clearTime(targetCal);
        long targetMs = targetCal.getTimeInMillis();

        if (targetMs == todayMs) {
            return "Today";
        } else if (targetMs == yesterdayMs) {
            return "Yesterday";
        } else {
            SimpleDateFormat format = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
            return format.format(date);
        }
    }

    private void showCalendarDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_calendar, null, false);
        dialog.setContentView(dialogView);

        TextView tvMonthYear = dialogView.findViewById(R.id.tv_month_year);
        ImageButton btnPrevMonth = dialogView.findViewById(R.id.btn_prev_month);
        ImageButton btnNextMonth = dialogView.findViewById(R.id.btn_next_month);
        LinearLayout weekHeader = dialogView.findViewById(R.id.week_header);
        GridLayout calendarGrid = dialogView.findViewById(R.id.calendar_grid);

        Calendar selected = Calendar.getInstance();
        selected.setFirstDayOfWeek(getWeekStartDayPreference());
        selected.setTime(selectedDate);
        currentMonth = selected.get(Calendar.MONTH);
        currentYear = selected.get(Calendar.YEAR);

        buildWeekHeader(weekHeader);
        renderCalendarInDialog(dialog, tvMonthYear, btnNextMonth, calendarGrid);

        btnPrevMonth.setOnClickListener(v -> {
            if (currentMonth == Calendar.JANUARY) {
                currentMonth = Calendar.DECEMBER;
                currentYear--;
            } else {
                currentMonth--;
            }
            renderCalendarInDialog(dialog, tvMonthYear, btnNextMonth, calendarGrid);
        });

        btnNextMonth.setOnClickListener(v -> {
            Calendar today = Calendar.getInstance();
            today.setFirstDayOfWeek(getWeekStartDayPreference());
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

            renderCalendarInDialog(dialog, tvMonthYear, btnNextMonth, calendarGrid);
        });

        dialog.show();
    }

    private void buildWeekHeader(LinearLayout weekHeader) {
        weekHeader.removeAllViews();

        int preferredFirstDayOfWeek = getWeekStartDayPreference();
        String[] daysShort = {"S", "M", "T", "W", "T", "F", "S"};
        String[] orderedDays = new String[7];

        int firstDayOfWeekIndex = (preferredFirstDayOfWeek - 1 + 7) % 7;

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

    private void renderCalendarInDialog(BottomSheetDialog dialog, TextView tvMonthYear, ImageButton btnNextMonth, GridLayout calendarGrid) {
        Calendar monthCal = Calendar.getInstance();
        monthCal.setFirstDayOfWeek(getWeekStartDayPreference());
        monthCal.set(Calendar.YEAR, currentYear);
        monthCal.set(Calendar.MONTH, currentMonth);
        monthCal.set(Calendar.DAY_OF_MONTH, 1);

        tvMonthYear.setText(UiFormatters.monthYear(monthCal.getTime()));

        calendarGrid.removeAllViews();

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
                cellCal.setFirstDayOfWeek(preferredFirstDayOfWeek);
                cellCal.set(Calendar.YEAR, currentYear);
                cellCal.set(Calendar.MONTH, currentMonth);
                cellCal.set(Calendar.DAY_OF_MONTH, dayNumber);
                clearTime(cellCal);

                boolean isFuture = cellCal.after(today);
                boolean isSelected =
                        cellCal.get(Calendar.YEAR) == selectedCal.get(Calendar.YEAR) &&
                                cellCal.get(Calendar.MONTH) == selectedCal.get(Calendar.MONTH) &&
                                cellCal.get(Calendar.DAY_OF_MONTH) == selectedCal.get(Calendar.DAY_OF_MONTH);

                calendarGrid.addView(createDayCell(dialog, dayNumber, cellCal.getTime(), isSelected, isFuture));
            }
        }

        updateNextButtonState(btnNextMonth);
    }

    private View createEmptyCell() {
        LinearLayout cell = new LinearLayout(context);
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = dp(52);
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.setMargins(dp(2), dp(2), dp(2), dp(2));
        cell.setLayoutParams(params);
        return cell;
    }

    private View createDayCell(BottomSheetDialog dialog, int day, Date cellDate, boolean isSelected, boolean isFuture) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = dp(52);
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.setMargins(dp(2), dp(2), dp(2), dp(2));
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
        root.addView(tvDay);

        if (!isFuture) {
            databaseWriteExecutor.execute(() -> {
                boolean hasData = hasDataForDate(cellDate);
                root.post(() -> {
                    DayDataIndicatorView indicatorView = new DayDataIndicatorView(context);
                    LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(6), dp(6));
                    dotParams.topMargin = dp(2);
                    indicatorView.setLayoutParams(dotParams);

                    indicatorView.setHasData(hasData);
                    root.addView(indicatorView);
                });
            });

            root.setOnClickListener(v -> {
                selectedDate = cellDate;
                updateSmallCalendarUI();
                if (listener != null) {
                    listener.onDateSelected(selectedDate);
                }
                if (dialog != null && dialog.isShowing()) {
                    dialog.dismiss();
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

    private void updateNextButtonState(ImageButton btnNextMonth) {
        if (btnNextMonth == null) return;
        Calendar today = Calendar.getInstance();
        today.setFirstDayOfWeek(getWeekStartDayPreference());
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
        return preferences.getInt(KEY_WEEK_START_DAY, Calendar.MONDAY);
    }
}
