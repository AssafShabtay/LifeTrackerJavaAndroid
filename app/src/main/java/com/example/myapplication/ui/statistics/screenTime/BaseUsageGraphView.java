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

public abstract class BaseUsageGraphView extends View {

    protected float animationFraction = 0f;
    protected int selectedIndex = -1;

    protected int barColorDefault;
    protected int barColorPeak;
    protected int barColorSelected;
    protected int gridLineColor;
    protected int textLabelColor;
    protected int tooltipBgColor;
    protected int tooltipTextColor;

    protected final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    protected final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    protected final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    protected final Paint tooltipBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    protected final Paint tooltipTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    protected final RectF barRect = new RectF();
    protected final RectF tooltipRect = new RectF();

    public BaseUsageGraphView(Context context) {
        super(context);
        init(context, null);
    }

    public BaseUsageGraphView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public BaseUsageGraphView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    protected void init(Context context, @Nullable AttributeSet attrs) {
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
        textPaint.setTextSize(spToPx(getTextLabelSizeSp()));
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

        setLayerType(LAYER_TYPE_SOFTWARE, null); // shadow layer
    }

    protected float getTextLabelSizeSp() {
        return 11f;
    }

    protected abstract int getBarCount();

    protected void startAnimation() {
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
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float paddingLeft = dpToPx(getPaddingLeftDp());
        float paddingRight = dpToPx(getPaddingRightDp());
        float chartWidth = getWidth() - paddingLeft - paddingRight;

        if (chartWidth <= 0) return super.onTouchEvent(event);

        int count = getBarCount();
        if (count <= 0) return super.onTouchEvent(event);

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                float relativeX = x - paddingLeft;
                if (relativeX < 0) relativeX = 0;
                if (relativeX >= chartWidth) relativeX = chartWidth - 1;

                int index = (int) (relativeX / (chartWidth / (float) count));
                if (index < 0) index = 0;
                if (index > count - 1) index = count - 1;

                if (index != selectedIndex) {
                    selectedIndex = index;
                    invalidate();
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

    protected float getPaddingLeftDp() {
        return 8f;
    }

    protected float getPaddingRightDp() {
        return 8f;
    }

    protected float dpToPx(float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    protected float spToPx(float sp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, getResources().getDisplayMetrics());
    }

    protected void drawTooltip(Canvas canvas, String tooltipText, float selectedCenterX, float paddingLeft, float paddingRight, int width) {
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
