package com.max.config;

import android.content.Context;
import android.support.v4.widget.DrawerLayout;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import com.max.main.Controller;
import com.max.main.R;

public class CustomInterceptDrawerLayout extends DrawerLayout {
    public CustomInterceptDrawerLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /** Squared maximum pan distance to consider a click. TODO dpi instead of pixels */
    private static final float CLICK_DIST2 = 20*20f;

    private float touchStartX, touchStartY;

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        float ex = event.getX(), ey = event.getY();

        View drawerListView = findViewById(R.id.left_drawer);
        if(isDrawerOpen(drawerListView) || isDrawerVisible(drawerListView)) {
            // Allow moving around in the map while navigation drawer is open, while still closing
            // the drawer if the user single clicks (standard behavior).
            if (ex >= drawerListView.getWidth()) {
                int action = event.getAction() & MotionEvent.ACTION_MASK;
                if (action == MotionEvent.ACTION_DOWN) {
                    touchStartX = ex;
                    touchStartY = ey;
                } else if (action == MotionEvent.ACTION_UP) {
                    float distMoved2 = (ex - touchStartX) * (ex - touchStartX) + (ey - touchStartY) * (ey - touchStartY);
                    if (distMoved2 <= CLICK_DIST2) // pan movement short enough to consider a click
                        closeDrawer(drawerListView);
                }

                // return false to not give parent drawer a chance to process the event
                return false;
            }

            // don't affect navigation drawer when using the seek bars
            if (Controller.globalSeekBar != null) {
                int[] location = new int[2];
                Controller.globalSeekBar.getLocationInWindow(location);
                int seekX0, seekY0, seekX1, seekY1;
                seekX0 = location[0];
                seekY0 = location[1] - Controller.globalSeekBar.getHeight() / 2;
                seekX1 = seekX0 + Controller.globalSeekBar.getWidth();
                seekY1 = seekY0 + Controller.globalSeekBar.getHeight();
                if (ex >= seekX0 && ex < seekX1 && ey >= seekY0 && ey < seekY1)
                    return false;
            }
        }
        return super.onInterceptTouchEvent(event);
    }
}
