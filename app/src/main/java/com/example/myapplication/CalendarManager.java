package com.example.myapplication;

import android.view.View;
import android.widget.CalendarView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class CalendarManager {

    public interface OnDateSelectedListener {
        void onDateSelected(Date date);
    }

    private final View dateCard;
    private final CalendarView calendarView;
    private final TextView tvDayName;
    private final TextView tvDateFull;
    private final TextView tvCalendarHint;
    
    private boolean calendarExpanded = false;
    private Date selectedDate = new Date();
    private OnDateSelectedListener listener;

    public CalendarManager(View root, OnDateSelectedListener listener) {
        this.dateCard = root.findViewById(R.id.date_card);
        this.calendarView = root.findViewById(R.id.calendar_view);
        this.tvDayName = root.findViewById(R.id.tv_day_name);
        this.tvDateFull = root.findViewById(R.id.tv_date_full);
        this.tvCalendarHint = root.findViewById(R.id.tv_calendar_hint);
        this.listener = listener;

        init();
    }

    private void init() {
        dateCard.setOnClickListener(v -> toggleCalendar());
        
        calendarView.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.YEAR, year);
            cal.set(Calendar.MONTH, month);
            cal.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            selectedDate = cal.getTime();

            updateHeader(selectedDate);
            collapseCalendar();
            
            if (listener != null) {
                listener.onDateSelected(selectedDate);
            }
        });

        updateHeader(selectedDate);
        collapseCalendar();
    }

    public Date getSelectedDate() {
        return selectedDate;
    }

    public void toggleCalendar() {
        calendarExpanded = !calendarExpanded;
        calendarView.setVisibility(calendarExpanded ? View.VISIBLE : View.GONE);
        tvCalendarHint.setText(calendarExpanded ? "Tap to hide calendar" : "Tap to show calendar");
    }

    public void collapseCalendar() {
        calendarExpanded = false;
        calendarView.setVisibility(View.GONE);
        tvCalendarHint.setText("Tap to show calendar");
    }

    private void updateHeader(Date date) {
        SimpleDateFormat dayFmt = new SimpleDateFormat("EEEE", Locale.getDefault());
        SimpleDateFormat fullFmt = new SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault());

        tvDayName.setText(dayFmt.format(date));
        tvDateFull.setText(fullFmt.format(date));
        tvCalendarHint.setText(calendarExpanded ? "Tap to hide calendar" : "Tap to show calendar");
    }
}
