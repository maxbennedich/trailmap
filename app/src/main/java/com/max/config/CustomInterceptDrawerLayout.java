package com.max.config;

import android.content.Context;
import android.content.res.Resources;
import android.support.v4.widget.DrawerLayout;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import com.max.main.Main;
import com.max.main.R;

public class CustomInterceptDrawerLayout extends DrawerLayout {
    public CustomInterceptDrawerLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        View drawerListView = findViewById(R.id.left_drawer);
        if(isDrawerOpen(drawerListView) || isDrawerVisible(drawerListView)) {
            // allow moving around in the map while navigation drawer is open
            if (event.getX() >= drawerListView.getWidth())
                return false;

            // don't affect navigation drawer when using the seek bars
            if (Main.globalSeekBar != null) {
                int[] location = new int[2];
                Main.globalSeekBar.getLocationInWindow(location);
                int seekX0, seekY0, seekX1, seekY1;
                seekX0 = location[0];
                seekY0 = location[1] - Main.globalSeekBar.getHeight() / 2;
                seekX1 = seekX0 + Main.globalSeekBar.getWidth();
                seekY1 = seekY0 + Main.globalSeekBar.getHeight();
                if (event.getX() >= seekX0 && event.getX() < seekX1 && event.getY() >= seekY0 && event.getY() < seekY1)
                    return false;
            }
        }
        return super.onInterceptTouchEvent(event);
    }
}
