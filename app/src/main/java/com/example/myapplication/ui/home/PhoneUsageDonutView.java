package com.example.myapplication.ui.home;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.myapplication.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom Donut Chart View to visualize category-based phone usage statistics.
 */
public class PhoneUsageDonutView extends View {

    public static class DonutSegment {
        private final float percentage; // 0.0 to 1.0
        private final int color;
        private final String label;

        public DonutSegment(float percentage, int color, String label) {
            this.percentage = percentage;
            this.color = color;
            this.label = label;
        }

        public float getPercentage() {
            return percentage;
        }

        public int getColor() {
            return color;
        }

        public String getLabel() {
            return label;
        }
    }

    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF ovalRect = new RectF();

    private List<DonutSegment> segments = new ArrayList<>();
    private float animationProgress = 1.0f;
    private ValueAnimator animator;

    private float strokeWidthPx;
    private static final float DEFAULT_STROKE_WIDTH_DP = 10f;
    private static final float SEGMENT_GAP_DEGREES = 3f;

    public PhoneUsageDonutView(Context context) {
        super(context);
        init(context, null);
    }

    public PhoneUsageDonutView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public PhoneUsageDonutView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, @Nullable AttributeSet attrs) {
        float density = context.getResources().getDisplayMetrics().density;
        strokeWidthPx = DEFAULT_STROKE_WIDTH_DP * density;

        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeWidth(strokeWidthPx);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);

        bgPaint.setStyle(Paint.Style.STROKE);
        bgPaint.setStrokeWidth(strokeWidthPx);
        bgPaint.setColor(ContextCompat.getColor(context, R.color.outline_variant));
    }

    public void setSegments(List<DonutSegment> newSegments, boolean animate) {
        this.segments = newSegments != null ? newSegments : new ArrayList<>();
        if (animate) {
            startAnimation();
        } else {
            animationProgress = 1.0f;
            invalidate();
        }
    }

    private void startAnimation() {
        if (animator != null && animator.isRunning()) {
            animator.cancel();
        }
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(800);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            animationProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float halfStroke = strokeWidthPx / 2f;
        float paddingLeft = getPaddingLeft() + halfStroke;
        float paddingTop = getPaddingTop() + halfStroke;
        float paddingRight = getPaddingRight() + halfStroke;
        float paddingBottom = getPaddingBottom() + halfStroke;

        float width = w - paddingLeft - paddingRight;
        float height = h - paddingTop - paddingBottom;
        float diameter = Math.min(width, height);

        float left = paddingLeft + (width - diameter) / 2f;
        float top = paddingTop + (height - diameter) / 2f;

        ovalRect.set(left, top, left + diameter, top + diameter);
    }

    @Override
    protected void onDraw(@androidx.annotation.NonNull Canvas canvas) {
        super.onDraw(canvas);

        // Always draw the subtle background circle first
        canvas.drawOval(ovalRect, bgPaint);

        if (segments.isEmpty()) {
            return;
        }

        float startAngle = -90f; // Start at 12 o'clock

        // Count non-zero segments to calculate gap overhead
        int validSegmentCount = 0;
        for (DonutSegment segment : segments) {
            if (segment.getPercentage() > 0.001f) {
                validSegmentCount++;
            }
        }

        float totalGapsDegrees = validSegmentCount > 1 ? validSegmentCount * SEGMENT_GAP_DEGREES : 0f;
        float availableDegrees = Math.max(0f, 360f - totalGapsDegrees);

        for (DonutSegment segment : segments) {
            if (segment.getPercentage() <= 0.001f) continue;

            float sweepAngle = (segment.getPercentage() * availableDegrees) * animationProgress;
            if (sweepAngle > 0.5f) {
                arcPaint.setColor(segment.getColor());
                canvas.drawArc(ovalRect, startAngle, sweepAngle, false, arcPaint);
                startAngle += sweepAngle + SEGMENT_GAP_DEGREES;
            }
        }
    }
}
