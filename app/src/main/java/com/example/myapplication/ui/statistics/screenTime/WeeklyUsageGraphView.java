package com.example.myapplication.ui.statistics.screenTime;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class WeeklyUsageGraphView extends BaseUsageGraphView {

    private long[] dailyUsageMs = new long[7];
    private String[] dayLabels = new String[]{"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};

    public WeeklyUsageGraphView(Context context) {
        super(context);
    }

    public WeeklyUsageGraphView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public WeeklyUsageGraphView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected float getPaddingLeftDp() {
        return 12f;
    }

    @Override
    protected float getPaddingRightDp() {
        return 12f;
    }

    @Override
    protected int getBarCount() {
        return 7;
    }


    public void setWeeklyData(long[] data, String[] labels) {
        if (data != null && data.length == 7) {
            this.dailyUsageMs = data.clone();
        } else {
            this.dailyUsageMs = new long[7];
        }
        if (labels != null && labels.length == 7) {
            this.dayLabels = labels.clone();
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

        // Find peak day
        long maxDuration = 0;
        int peakDay = -1;
        for (int i = 0; i < 7; i++) {
            if (dailyUsageMs[i] > maxDuration) {
                maxDuration = dailyUsageMs[i];
                peakDay = i;
            }
        }

        long maxScale = Math.max(maxDuration, 1 * 60 * 60 * 1000L); // at least 1h scale

        float yBottom = paddingTop + chartHeight;
        float yMid = paddingTop + (chartHeight / 2f);

        canvas.drawLine(paddingLeft, yBottom, paddingLeft + chartWidth, yBottom, gridPaint);
        canvas.drawLine(paddingLeft, yMid, paddingLeft + chartWidth, yMid, gridPaint);

        float barSlotWidth = chartWidth / 7f;
        float barWidth = Math.max(barSlotWidth * 0.55f, dpToPx(12f));
        float cornerRadius = dpToPx(6f);

        float selectedCenterX = -1;
        long selectedDuration = 0;
        String selectedLabel = "";

        for (int i = 0; i < 7; i++) {
            float slotLeft = paddingLeft + (i * barSlotWidth);
            float barLeft = slotLeft + ((barSlotWidth - barWidth) / 2f);
            float barRight = barLeft + barWidth;

            float usageFraction = (float) dailyUsageMs[i] / (float) maxScale;
            float currentBarHeight = chartHeight * usageFraction * animationFraction;

            if (currentBarHeight < dpToPx(2f) && dailyUsageMs[i] > 0) {
                currentBarHeight = dpToPx(2f);
            }

            float barTop = yBottom - currentBarHeight;

            barRect.set(barLeft, barTop, barRight, yBottom);

            if (i == selectedIndex) {
                barPaint.setColor(barColorSelected);
                selectedCenterX = barLeft + (barWidth / 2f);
                selectedDuration = dailyUsageMs[i];
                selectedLabel = dayLabels[i];
            } else if (i == peakDay && dailyUsageMs[i] > 0) {
                barPaint.setColor(barColorPeak);
            } else {
                barPaint.setColor(barColorDefault);
            }

            canvas.drawRoundRect(barRect, cornerRadius, cornerRadius, barPaint);

            // Draw Day Label under each bar
            float labelX = slotLeft + (barSlotWidth / 2f);
            float labelY = yBottom + dpToPx(16f);
            canvas.drawText(dayLabels[i], labelX, labelY, textPaint);
        }

        // Draw Interactive Tooltip if selected
        if (selectedIndex >= 0 && selectedCenterX >= 0) {
            String durationText = ScreenTimeStatsManager.formatDuration(selectedDuration);
            String tooltipText = selectedLabel + ": " + durationText;

            drawTooltip(canvas, tooltipText, selectedCenterX, paddingLeft, paddingRight, width);
        }
    }
}
