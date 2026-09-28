package com.example.myapplication.ui.statistics.efficiency;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.CornerPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
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

public class HourlyUsageGraphView extends View {

    private long[] hourlyBucketsMs = new long[24];
    private float animationFraction = 0f;
    private int selectedHour = -1;

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

    private OnHourSelectedListener onHourSelectedListener;

    public interface OnHourSelectedListener {
        void onHourSelected(int hour, long durationMs);
    }

    public HourlyUsageGraphView(Context context) {
        super(context);
        init(context, null);
    }

    public HourlyUsageGraphView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public HourlyUsageGraphView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
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
        textPaint.setTextSize(spToPx(10f));
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

        setLayerType(LAYER_TYPE_SOFTWARE, null); //  shadow layer
    }

    public void setHourlyData(long[] data) {
        if (data != null && data.length == 24) {
            this.hourlyBucketsMs = data.clone();
        } else {
            this.hourlyBucketsMs = new long[24];
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

        float paddingLeft = dpToPx(8f);
        float paddingRight = dpToPx(8f);
        float paddingTop = dpToPx(28f);
        float paddingBottom = dpToPx(24f);

        float chartWidth = width - paddingLeft - paddingRight;
        float chartHeight = height - paddingTop - paddingBottom;

        if (chartWidth <= 0 || chartHeight <= 0) return;

        // Find max usage across hours
        long maxDuration = 0;
        int peakHour = -1;
        for (int i = 0; i < 24; i++) {
            if (hourlyBucketsMs[i] > maxDuration) {
                maxDuration = hourlyBucketsMs[i];
                peakHour = i;
            }
        }

        long maxScale = Math.max(maxDuration, 15 * 60 * 1000L); // add 15 mins to max hours, for better scaling

        // Draw horizontal subtle grid lines (0%, 50%, 100%)
        float yBottom = paddingTop + chartHeight;
        float yMid = paddingTop + (chartHeight / 2f);
        float yTop = paddingTop;

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

            float usageFraction = (float) hourlyBucketsMs[i] / (float) maxScale;
            float currentBarHeight = chartHeight * usageFraction * animationFraction;

            // Minimum bar height for aesthetic empty/low slot indicator
            if (currentBarHeight < dpToPx(2f) && hourlyBucketsMs[i] > 0) {
                currentBarHeight = dpToPx(2f);
            }

            float barTop = yBottom - currentBarHeight;

            barRect.set(barLeft, barTop, barRight, yBottom);

            if (i == selectedHour) {
                barPaint.setColor(barColorSelected);
                selectedCenterX = barLeft + (barWidth / 2f);
                selectedDuration = hourlyBucketsMs[i];
            } else if (i == peakHour && hourlyBucketsMs[i] > 0) {
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
        if (selectedHour >= 0 && selectedCenterX >= 0) {
            int minutes = (int) Math.round((double) selectedDuration / 60000.0);
            int hour12 = selectedHour % 12 == 0 ? 12 : selectedHour % 12;
            String amPm = selectedHour >= 12 ? "PM" : "AM";
            String nextAmPm = ((selectedHour + 1) % 24) >= 12 ? "PM" : "AM";
            int nextHour12 = (selectedHour + 1) % 12 == 0 ? 12 : (selectedHour + 1) % 12;

            String tooltipText = String.format(Locale.getDefault(), "%d%s-%d%s: %dm",
                    hour12, amPm, nextHour12, nextAmPm, minutes);

            float textWidth = tooltipTextPaint.measureText(tooltipText);
            float tipWidth = textWidth + dpToPx(20f);
            float tipHeight = dpToPx(24f);

            float tipLeft = selectedCenterX - (tipWidth / 2f);
            // Keep tooltip within view bounds
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
        float paddingLeft = dpToPx(8f);
        float paddingRight = dpToPx(8f);
        float chartWidth = getWidth() - paddingLeft - paddingRight;

        if (chartWidth <= 0) return super.onTouchEvent(event);

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                float relativeX = x - paddingLeft;
                if (relativeX < 0) relativeX = 0;
                if (relativeX >= chartWidth) relativeX = chartWidth - 1;

                int hour = (int) (relativeX / (chartWidth / 24f));
                if (hour < 0) hour = 0;
                if (hour > 23) hour = 23;

                if (hour != selectedHour) {
                    selectedHour = hour;
                    invalidate();
                    if (onHourSelectedListener != null) {
                        onHourSelectedListener.onHourSelected(selectedHour, hourlyBucketsMs[selectedHour]);
                    }
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                // Keep selected hour visible for clean user inspection, or handle tap
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

