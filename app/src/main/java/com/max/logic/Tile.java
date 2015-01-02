package com.max.logic;

import android.graphics.Bitmap;
import android.graphics.Canvas;

public class Tile {
    public final int zoomLevel;
    public final int tx;
    public final int ty;
    public final Bitmap map;
    public final Canvas canvas;

    public Tile(int zoomLevel, int tx, int ty, Bitmap map) {
        this.zoomLevel = zoomLevel;
        this.tx = tx;
        this.ty = ty;
        this.map = map;
        this.canvas = new Canvas(map);
    }

    @Override public String toString() { return zoomLevel + "," + tx + "," + ty; }
}
