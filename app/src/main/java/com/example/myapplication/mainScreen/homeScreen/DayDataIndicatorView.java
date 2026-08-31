package com.example.myapplication.mainScreen.homeScreen;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class DayDataIndicatorView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean hasData = false;
    private int colorPrimary;

    public DayDataIndicatorView(Context context) {
        super(context);
        init(context);
    }

    public DayDataIndicatorView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public DayDataIndicatorView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        paint.setStyle(Paint.Style.FILL);

        // Use activity_still color for better visibility as a "data present" indicator
        colorPrimary = context.getColor(com.example.myapplication.R.color.activity_still);
        paint.setColor(colorPrimary);
    }

    public void setHasData(boolean hasData) {
        this.hasData = hasData;
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (hasData) {
            float centerX = getWidth() / 2f;
            float centerY = getHeight() / 2f;
            float radius = Math.min(centerX, centerY);
            canvas.drawCircle(centerX, centerY, radius, paint);
        }
    }
}
