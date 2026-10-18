package com.example.myapplication.ui.statistics.screenTime;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.myapplication.R;

import java.util.Locale;

public class WeeklyUsageGraphView extends View {

    private long[] dailyBucketsMs = new long[7];
    private String[] dayLabels = new String[]{"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
    private float animationFraction = 0f;
    private int selectedDay = -1;

    private int barColorDefault;
    private int barColorPeak;
    private int barColorSelected;
    private int gridLineColor;
    private int textLabelColor;
    private int tooltipBgColor;
    private int tooltipTextColor;

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tooltipBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tooltipTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF barRect = new RectF();
    private final RectF tooltipRect = new RectF();

    private OnDaySelectedListener onDaySelectedListener;

    public interface OnDaySelectedListener {
        void onDaySelected(int dayIndex, long durationMs, String dayLabel);
    }

    public WeeklyUsageGraphView(Context context) {
        super(context);
        init(context, null);
    }

    public WeeklyUsageGraphView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public WeeklyUsageGraphView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, @Nullable AttributeSet attrs) {
        barColorDefault = ContextCompat.getColor(context, R.color.primary_container);
        barColorPeak = ContextCompat.getColor(context, R.color.primary);
        barColorSelected = ContextCompat.getColor(context, R.color.blue);
        gridLineColor = ContextCompat.getColor(context, R.color.outline_variant);
        textLabelColor = ContextCompat.getColor(context, R.color.on_surface_variant);
        tooltipBgColor = ContextCompat.getColor(context, R.color.on_surface);
        tooltipTextColor = ContextCompat.getColor(context, R.color.surface);

        barPaint.setStyle(Paint.Style.FILL);

        gridPaint.setColor(gridLineColor);
        gridPaint.setStrokeWidth(dpToPx(1f));
        gridPaint.setStyle(Paint.Style.STROKE);

        textPaint.setColor(textLabelColor);
        textPaint.setTextSize(spToPx(11f));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setAntiAlias(true);

        tooltipBgPaint.setColor(tooltipBgColor);
        tooltipBgPaint.setStyle(Paint.Style.FILL);
        tooltipBgPaint.setShadowLayer(dpToPx(4f), 0, dpToPx(2f), Color.argb(40, 0, 0, 0));

        tooltipTextPaint.setColor(tooltipTextColor);
        tooltipTextPaint.setTextSize(spToPx(11f));
        tooltipTextPaint.setFakeBoldText(true);
        tooltipTextPaint.setTextAlign(Paint.Align.CENTER);
        tooltipTextPaint.setAntiAlias(true);

        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public void setWeeklyData(long[] data, String[] labels) {
        if (data != null && data.length == 7) {
            this.dailyBucketsMs = data.clone();
        } else {
            this.dailyBucketsMs = new long[7];
        }
        if (labels != null && labels.length == 7) {
            this.dayLabels = labels.clone();
        }
        startAnimation();
    }


    private void startAnimation() {
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(700);
        animator.setInterpolator(new DecelerateInterpolator(1.8f));
        animator.addUpdateListener(animation -> {
            animationFraction = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredHeight = (int) dpToPx(180f);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int height;
        if (heightMode == MeasureSpec.EXACTLY) {
            height = heightSize;
        } else if (heightMode == MeasureSpec.AT_MOST) {
            height = Math.min(desiredHeight, heightSize);
        } else {
            height = desiredHeight;
        }

        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), height);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();

        float paddingLeft = dpToPx(12f);
        float paddingRight = dpToPx(12f);
        float paddingTop = dpToPx(28f);
        float paddingBottom = dpToPx(24f);

        float chartWidth = width - paddingLeft - paddingRight;
        float chartHeight = height - paddingTop - paddingBottom;

        if (chartWidth <= 0 || chartHeight <= 0) return;

        // Find peak day
        long maxDuration = 0;
        int peakDay = -1;
        for (int i = 0; i < 7; i++) {
            if (dailyBucketsMs[i] > maxDuration) {
                maxDuration = dailyBucketsMs[i];
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

            float usageFraction = (float) dailyBucketsMs[i] / (float) maxScale;
            float currentBarHeight = chartHeight * usageFraction * animationFraction;

            if (currentBarHeight < dpToPx(2f) && dailyBucketsMs[i] > 0) {
                currentBarHeight = dpToPx(2f);
            }

            float barTop = yBottom - currentBarHeight;

            barRect.set(barLeft, barTop, barRight, yBottom);

            if (i == selectedDay) {
                barPaint.setColor(barColorSelected);
                selectedCenterX = barLeft + (barWidth / 2f);
                selectedDuration = dailyBucketsMs[i];
                selectedLabel = dayLabels[i];
            } else if (i == peakDay && dailyBucketsMs[i] > 0) {
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
        if (selectedDay >= 0 && selectedCenterX >= 0) {
            String durationText = ScreenTimeStatsManager.formatDuration(selectedDuration);
            String tooltipText = selectedLabel + ": " + durationText;

            float textWidth = tooltipTextPaint.measureText(tooltipText);
            float tipWidth = textWidth + dpToPx(20f);
            float tipHeight = dpToPx(24f);

            float tipLeft = selectedCenterX - (tipWidth / 2f);
            if (tipLeft < paddingLeft) tipLeft = paddingLeft;
            if (tipLeft + tipWidth > width - paddingRight) tipLeft = width - paddingRight - tipWidth;
            float tipTop = dpToPx(2f);
            float tipBottom = tipTop + tipHeight;

            tooltipRect.set(tipLeft, tipTop, tipLeft + tipWidth, tipBottom);
            canvas.drawRoundRect(tooltipRect, dpToPx(8f), dpToPx(8f), tooltipBgPaint);

            Paint.FontMetrics fm = tooltipTextPaint.getFontMetrics();
            float textY = tipTop + (tipHeight / 2f) - (fm.ascent + fm.descent) / 2f;
            canvas.drawText(tooltipText, tipLeft + (tipWidth / 2f), textY, tooltipTextPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float paddingLeft = dpToPx(12f);
        float paddingRight = dpToPx(12f);
        float chartWidth = getWidth() - paddingLeft - paddingRight;

        if (chartWidth <= 0) return super.onTouchEvent(event);

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                float relativeX = x - paddingLeft;
                if (relativeX < 0) relativeX = 0;
                if (relativeX >= chartWidth) relativeX = chartWidth - 1;

                int day = (int) (relativeX / (chartWidth / 7f));
                if (day < 0) day = 0;
                if (day > 6) day = 6;

                if (day != selectedDay) {
                    selectedDay = day;
                    invalidate();
                    if (onDaySelectedListener != null) {
                        onDaySelectedListener.onDaySelected(selectedDay, dailyBucketsMs[selectedDay], dayLabels[selectedDay]);
                    }
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                performClick();
                return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private float dpToPx(float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    private float spToPx(float sp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, getResources().getDisplayMetrics());
    }
}
