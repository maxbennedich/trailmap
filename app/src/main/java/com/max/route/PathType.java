package com.max.route;

import android.graphics.Paint;

import com.max.drawing.Paints;

public enum PathType {
    MAJOR_ROAD(Paints.PATH_MAJOR_ROAD, Paints.PATH_MAJOR_ROAD_OUTLINE),
    MINOR_ROAD(Paints.PATH_MINOR_ROAD, Paints.PATH_MINOR_ROAD_OUTLINE),

    HISTORY(Paints.HISTORY_PATH, null),
    ;

    public final Paint paint;
    public final Paint outlinePaint;

    PathType(Paint paint, Paint outlinePaint) {
        this.paint = paint;
        this.outlinePaint = outlinePaint;
    }
}
