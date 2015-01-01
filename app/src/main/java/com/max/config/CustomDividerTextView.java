package com.max.config;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.widget.TextView;

import com.max.drawing.Paints;

public class CustomDividerTextView extends TextView {
    public CustomDividerTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        measure(0, 0);
        int x0 = getPaddingLeft();
        int x1 = getMeasuredWidth();
        int y = getHeight() / 2 + 2;

        canvas.drawLine(8, y, x0-2, y, Paints.CONFIG_DIVIDER);
        canvas.drawLine(x1+2, y, getWidth() - 26, y, Paints.CONFIG_DIVIDER);
    }
}
