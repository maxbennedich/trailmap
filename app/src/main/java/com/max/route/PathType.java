package com.max.route;

import android.graphics.Paint;

import com.max.drawing.Paints;

public enum PathType {
    MAJOR_ROAD(Paints.PATH_MAJOR_ROAD),
    MINOR_ROAD(Paints.PATH_MINOR_ROAD),

    HISTORY(Paints.HISTORY_PATH),
    ;

    public final Paint paint;

    PathType(Paint paint) {
        this.paint = paint;
    }
}
