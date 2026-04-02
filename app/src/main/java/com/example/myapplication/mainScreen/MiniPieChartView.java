package com.example.myapplication.mainScreen;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class MiniPieChartView extends View {

    public static class Slice {
        public float value;
        public int color;

        public Slice(float value, int color) {
            this.value = value;
            this.color = color;
        }
    }

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint emptyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final List<Slice> slices = new ArrayList<>();

    private boolean isSelected = false;
    private boolean isFuture = false;

    private int colorOutline;
    private int colorEmpty;
    private int colorPrimary;

    public MiniPieChartView(Context context) {
        super(context);
        init(context);
    }

    public MiniPieChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public MiniPieChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        fillPaint.setStyle(Paint.Style.FILL);
        strokePaint.setStyle(Paint.Style.STROKE);
        emptyPaint.setStyle(Paint.Style.FILL);

        TypedValue typedValue = new TypedValue();
        
        // Resolve Material 3 attributes.
        // If the specific library R classes are not resolving, we can also use the local project's R
        // if these attributes are inherited/used in the theme.
        
        // Attempt to resolve colorOutline (Material)
        if (context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorOutline, typedValue, true)) {
            colorOutline = typedValue.data;
        } else {
            colorOutline = 0xFFCCCCCC; // Default light gray
        }

        // Attempt to resolve colorSurfaceVariant (Material)
        if (context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorSurfaceVariant, typedValue, true)) {
            colorEmpty = typedValue.data;
        } else {
            colorEmpty = 0xFFEEEEEE; // Default lighter gray
        }

        // Resolve Primary color (standard in AppCompat/Material or Android system)
        if (context.getTheme().resolveAttribute(androidx.appcompat.R.attr.colorPrimary, typedValue, true)) {
            colorPrimary = typedValue.data;
        } else if (context.getTheme().resolveAttribute(android.R.attr.colorPrimary, typedValue, true)) {
            colorPrimary = typedValue.data;
        } else {
            colorPrimary = 0xFF6200EE; // Default purple
        }
    }

    public void setFuture(boolean future) {
        isFuture = future;
        invalidate();
    }

    public void setSelected(boolean selected) {
        isSelected = selected;
        invalidate();
    }

    public void setSlices(List<Slice> data) {
        slices.clear();
        if (data != null) {
            slices.addAll(data);
        }
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        float strokeWidth = isSelected ? dp(2) : dp(1);
        strokePaint.setStrokeWidth(strokeWidth);

        int borderColor = isSelected ? colorPrimary : colorOutline;
        strokePaint.setColor(borderColor);

        float halfStroke = strokeWidth / 2f;
        float width = getWidth();
        float height = getHeight();

        // Draw background circle
        emptyPaint.setColor(colorEmpty);
        emptyPaint.setAlpha(isFuture ? 76 : 255);
        canvas.drawOval(halfStroke, halfStroke, width - halfStroke, height - halfStroke, emptyPaint);

        if (isFuture) {
            canvas.drawOval(halfStroke, halfStroke, width - halfStroke, height - halfStroke, strokePaint);
            return;
        }

        if (!slices.isEmpty()) {
            float total = 0f;
            for (Slice slice : slices) total += slice.value;

            if (total > 0f) {
                float startAngle = -90f;
                for (Slice slice : slices) {
                    float sweep = (slice.value / total) * 360f;
                    fillPaint.setColor(slice.color);
                    canvas.drawArc(halfStroke, halfStroke, width - halfStroke, height - halfStroke, startAngle, sweep, true, fillPaint);
                    startAngle += sweep;
                }
            }
        }

        canvas.drawOval(halfStroke, halfStroke, width - halfStroke, height - halfStroke, strokePaint);
    }

    private float dp(int value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
