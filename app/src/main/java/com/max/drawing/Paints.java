package com.max.drawing;

import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;

public class Paints {

    public static PaintSettings PAINT_SETTINGS = new PaintSettings.GalaxyS6();

    public static final int FONT_SIZE_POI = PAINT_SETTINGS.fontSizePoi();
    public static final Paint FONT_POI = pb().color(0xffffffff).textSize(FONT_SIZE_POI).antialias().get();
    public static final Paint FONT_OUTLINE_POI = pb().color(0xff000000).textSize(FONT_SIZE_POI).stroke(4).antialias().get();

    public static final int FONT_SIZE_SCALE = PAINT_SETTINGS.fontSizeScale();
    public static final Paint FONT_SCALE = pb().color(0xffffffff).textSize(FONT_SIZE_SCALE).antialias().get();
    public static final Paint FONT_OUTLINE_SCALE = pb().color(0xff000000).textSize(FONT_SIZE_SCALE).stroke(4).antialias().get();

    public static final int FONT_SIZE_NAVIGATION_STATS = PAINT_SETTINGS.fontSizeNavigationStats();
    public static final Paint FONT_NAVIGATION_STATS = pb().color(0xffffffff).textSize(FONT_SIZE_NAVIGATION_STATS).antialias().get();
    public static final Paint FONT_OUTLINE_NAVIGATION_STATS = pb().color(0xff000000).textSize(FONT_SIZE_NAVIGATION_STATS).stroke(6).antialias().get();

    public static final int FONT_SIZE_NAVIGATION_SUBSCRIPT = PAINT_SETTINGS.fontSizeNavigationSubscript();
    public static final Paint FONT_NAVIGATION_SUBSCRIPT = pb().color(0xffffffff).textSize(FONT_SIZE_NAVIGATION_SUBSCRIPT).antialias().get();
    public static final Paint FONT_OUTLINE_NAVIGATION_SUBSCRIPT = pb().color(0xff000000).textSize(FONT_SIZE_NAVIGATION_SUBSCRIPT).stroke(5).antialias().get();

    public static final int FONT_SIZE_GPS_STATS = PAINT_SETTINGS.fontSizeGPSStats();
    public static final Paint FONT_GPS_STATS = pb().color(0xffffffff).textSize(FONT_SIZE_GPS_STATS).antialias().get();
    public static final Paint FONT_OUTLINE_GPS_STATS = pb().color(0xff000000).textSize(FONT_SIZE_GPS_STATS).stroke(8).antialias().get();

    public static final int POINT_OF_INTEREST_SIZE = 12;

    public static final Paint POINT_OF_INTEREST = pb().color(0xffff9f00).strokeWidth(POINT_OF_INTEREST_SIZE - 4).antialias().get();
    public static final Paint POINT_OF_INTEREST_OUTLINE = pb().color(0xff000000).strokeWidth(POINT_OF_INTEREST_SIZE).antialias().get();
    public static final Paint WAYPOINT = pb().color(0xffffff00).strokeWidth(POINT_OF_INTEREST_SIZE - 4).antialias().get();
    public static final Paint WAYPOINT_OUTLINE = pb().color(0xff000000).strokeWidth(POINT_OF_INTEREST_SIZE).antialias().get();

    public static final int PATH_WIDTH = 8;

    public static final Paint PATH_MAJOR_ROAD_OUTLINE = null;
    public static final Paint PATH_MINOR_ROAD_OUTLINE = null;
    public static final Paint PATH_MAJOR_ROAD = pb().color(0x7f0000ff).stroke(PATH_WIDTH).round().dstAtop().antialias().get();
    public static final Paint PATH_MINOR_ROAD = pb().color(0x7f007fff).stroke(PATH_WIDTH).round().dstAtop().antialias().get();

    public static final int HISTORY_WIDTH = 4;

    public static final Paint HISTORY_PATH = pb().color(0xff0000ff).stroke(HISTORY_WIDTH).round().antialias().get();

    public static final Paint CONFIG_DIVIDER = pb().color(0xff3f3f3f).strokeWidth(2).get();

    public static final Paint FILTER_BITMAP = new Paint(Paint.FILTER_BITMAP_FLAG);

    public static final Paint GPS_ICON = pb().color(0x9fffffff).get();

    public static PaintBuilder pb() { return new PaintBuilder(); }

    static class PaintBuilder {
        private static final PorterDuffXfermode DST_ATOP = new PorterDuffXfermode(PorterDuff.Mode.DST_ATOP);

        Paint p;
        PaintBuilder() { p = new Paint(); }
        PaintBuilder color(int color) { p.setColor(color); return this; }
        PaintBuilder textSize(float textSize) { p.setTextSize(textSize); return this; }
        PaintBuilder stroke(float width) { p.setStyle(Paint.Style.STROKE); return strokeWidth(width); }
        PaintBuilder strokeWidth(float width) { p.setStrokeWidth(width); return this; }
        PaintBuilder round() { p.setStrokeCap(Paint.Cap.ROUND); return this; }
        PaintBuilder dstAtop() { p.setXfermode(DST_ATOP); return this; }
        PaintBuilder antialias() { p.setAntiAlias(true); return this; }
        Paint get() { return p; }
    }

}
