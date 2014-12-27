package com.max.drawing;

import android.graphics.Paint;

public class Paints {
    public static final Paint FONT = pb().color(0xffffffff).textSize(20).get();
    public static final Paint FONT_OUTLINE = pb().color(0xff000000).textSize(20).stroke(4).get();

    public static final Paint POINT_OF_INTEREST = pb().color(0xffffff00).strokeWidth(8).get();

    public static final Paint PATH_MAJOR_ROAD = pb().color(0xffff0000).stroke(6).get();
    public static final Paint PATH_MINOR_ROAD = pb().color(0xffff8f00).stroke(6).get();

    static PaintBuilder pb() { return new PaintBuilder(); }

    static class PaintBuilder {
        Paint p;
        PaintBuilder() { p = new Paint(); }
        PaintBuilder color(int color) { p.setColor(color); return this; }
        PaintBuilder textSize(float textSize) { p.setTextSize(textSize); return this; }
        PaintBuilder stroke(float width) { p.setStyle(Paint.Style.STROKE); return strokeWidth(width); }
        PaintBuilder strokeWidth(float width) { p.setStrokeWidth(width); return this; }
        Paint get() { return p; }
    }

}
