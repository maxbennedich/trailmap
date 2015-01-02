package com.max.drawing;

import android.graphics.Color;
import android.graphics.Paint;

public class Paints {

    // TODO: is strokewidth used with fill paint?
    /** Alpha needs to be added later. */
    public static final Paint DIM_SCREEN = pb().color(0).strokeWidth(1).get();

    public static final int FONT_SIZE_POI = 20;
    public static final Paint FONT_POI = pb().color(0xffffffff).textSize(FONT_SIZE_POI).antialias().get();
    public static final Paint FONT_OUTLINE_POI = pb().color(0xff000000).textSize(FONT_SIZE_POI).stroke(4).antialias().get();

    public static final int FONT_SIZE_SCALE = 22;
    public static final Paint FONT_SCALE = pb().color(0xffffffff).textSize(FONT_SIZE_SCALE).antialias().get();
    public static final Paint FONT_OUTLINE_SCALE = pb().color(0xff000000).textSize(FONT_SIZE_SCALE).stroke(4).antialias().get();

    public static final int POINT_OF_INTEREST_SIZE = 12;

    public static final Paint POINT_OF_INTEREST = pb().color(0xffffff00).strokeWidth(POINT_OF_INTEREST_SIZE - 4).antialias().get();
    public static final Paint POINT_OF_INTEREST_OUTLINE = pb().color(0xff000000).strokeWidth(POINT_OF_INTEREST_SIZE).antialias().get();

    public static final int PATH_WIDTH = 6;

    public static final Paint PATH_MAJOR_ROAD = pb().color(0x6fff0000).stroke(PATH_WIDTH).antialias().get();
    public static final Paint PATH_MINOR_ROAD = pb().color(0x6fff6f00).stroke(PATH_WIDTH).antialias().get();

    public static final int HISTORY_WIDTH = 4;

    public static final Paint HISTORY_PATH = pb().color(0xff0000ff).strokeWidth(HISTORY_WIDTH).antialias().get();

    public static final Paint CONFIG_DIVIDER = pb().color(0xff3f3f3f).strokeWidth(2).get();

    static PaintBuilder pb() { return new PaintBuilder(); }

    static class PaintBuilder {
        Paint p;
        PaintBuilder() { p = new Paint(); }
        PaintBuilder color(int color) { p.setColor(color); return this; }
        PaintBuilder textSize(float textSize) { p.setTextSize(textSize); return this; }
        PaintBuilder stroke(float width) { p.setStyle(Paint.Style.STROKE); return strokeWidth(width); }
        PaintBuilder strokeWidth(float width) { p.setStrokeWidth(width); return this; }
        PaintBuilder antialias() { p.setAntiAlias(true); return this; }
        Paint get() { return p; }
    }

}
