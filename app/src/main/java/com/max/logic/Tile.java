package com.max.logic;

import android.graphics.Bitmap;

public class Tile {
    public final TilePos tp;
    public final Bitmap map;

    public Tile(TilePos tp, Bitmap map) { this.tp = tp; this.map = map; }

    @Override public String toString() { return tp.toString(); }
}
