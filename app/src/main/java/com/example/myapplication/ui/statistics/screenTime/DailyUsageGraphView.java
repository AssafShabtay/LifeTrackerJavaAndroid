package com.example.myapplication.ui.statistics.screenTime;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

public class DailyUsageGraphView extends BaseUsageGraphView {

    private long[] hourlyUsageMs = new long[24];

    public DailyUsageGraphView(Context context) {
        super(context);
    }

    public DailyUsageGraphView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public DailyUsageGraphView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected float getTextLabelSizeSp() {
        return 10f;
    }

    @Override
    protected int getBarCount() {
        return 24;
    }

    public void setHourlyData(long[] data) {
        if (data != null && data.length == 24) {
            this.hourlyUsageMs = data.clone();
        } else {
            this.hourlyUsageMs = new long[24];
        }
        startAnimation();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();

        float paddingLeft = dpToPx(getPaddingLeftDp());
        float paddingRight = dpToPx(getPaddingRightDp());
        float paddingTop = dpToPx(28f);
        float paddingBottom = dpToPx(24f);

        float chartWidth = width - paddingLeft - paddingRight;
        float chartHeight = height - paddingTop - paddingBottom;

        if (chartWidth <= 0 || chartHeight <= 0) return;

        // Find max usage across hours
        long maxDuration = 0;
        int peakHour = -1;
        for (int i = 0; i < 24; i++) {
            if (hourlyUsageMs[i] > maxDuration) {
                maxDuration = hourlyUsageMs[i];
                peakHour = i;
            }
        }

        long maxScale = Math.max(maxDuration, 15 * 60 * 1000L); // add 15 mins to max hours, for better scaling

        // Draw horizontal subtle grid lines (0%, 50%, 100%)
        float yBottom = paddingTop + chartHeight;
        float yMid = paddingTop + (chartHeight / 2f);

        canvas.drawLine(paddingLeft, yBottom, paddingLeft + chartWidth, yBottom, gridPaint);
        canvas.drawLine(paddingLeft, yMid, paddingLeft + chartWidth, yMid, gridPaint);

        // Draw Bars
        float barSlotWidth = chartWidth / 24f;
        float barWidth = Math.max(barSlotWidth * 0.65f, dpToPx(4f));
        float cornerRadius = dpToPx(4f);

        float selectedCenterX = -1;
        long selectedDuration = 0;

        for (int i = 0; i < 24; i++) {
            float slotLeft = paddingLeft + (i * barSlotWidth);
            float barLeft = slotLeft + ((barSlotWidth - barWidth) / 2f);
            float barRight = barLeft + barWidth;

            float usageFraction = (float) hourlyUsageMs[i] / (float) maxScale;
            float currentBarHeight = chartHeight * usageFraction * animationFraction;

            // Minimum bar height for aesthetic empty/low slot indicator
            if (currentBarHeight < dpToPx(2f) && hourlyUsageMs[i] > 0) {
                currentBarHeight = dpToPx(2f);
            }

            float barTop = yBottom - currentBarHeight;

            barRect.set(barLeft, barTop, barRight, yBottom);

            if (i == selectedIndex) {
                barPaint.setColor(barColorSelected);
                selectedCenterX = barLeft + (barWidth / 2f);
                selectedDuration = hourlyUsageMs[i];
            } else if (i == peakHour && hourlyUsageMs[i] > 0) {
                barPaint.setColor(barColorPeak);
            } else {
                barPaint.setColor(barColorDefault);
            }

            canvas.drawRoundRect(barRect, cornerRadius, cornerRadius, barPaint);

            // Draw axis labels at 12 AM (00), 6 AM (06), 12 PM (12), 6 PM (18)
            if (i == 0 || i == 6 || i == 12 || i == 18) {
                String label;
                if (i == 0) label = "12 AM";
                else if (i == 6) label = "6 AM";
                else if (i == 12) label = "12 PM";
                else label = "6 PM";

                float labelX = slotLeft + (barSlotWidth / 2f);
                float labelY = yBottom + dpToPx(16f);
                canvas.drawText(label, labelX, labelY, textPaint);
            }
        }

        // Draw Interactive Tooltip if an hour is selected
        if (selectedIndex >= 0 && selectedCenterX >= 0) {
            int minutes = (int) Math.round((double) selectedDuration / 60000.0);
            int hour12 = selectedIndex % 12 == 0 ? 12 : selectedIndex % 12;
            String amPm = selectedIndex >= 12 ? "PM" : "AM";
            String nextAmPm = ((selectedIndex + 1) % 24) >= 12 ? "PM" : "AM";
            int nextHour12 = (selectedIndex + 1) % 12 == 0 ? 12 : (selectedIndex + 1) % 12;

            String tooltipText = String.format(Locale.getDefault(), "%d%s-%d%s: %dm",
                    hour12, amPm, nextHour12, nextAmPm, minutes);

            drawTooltip(canvas, tooltipText, selectedCenterX, paddingLeft, paddingRight, width);
        }
    }
}
