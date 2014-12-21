package com.max.logic;

public class TilePos {
    public final int zoomLevel;
    public final int tx, ty;

    public TilePos(int zoomLevel, int tx, int ty) {
        this.zoomLevel = zoomLevel;
        this.tx = tx;
        this.ty = ty;
    }

    @Override public int hashCode() { return zoomLevel+tx*89+ty*997; }

    @Override public boolean equals(Object o) {
        if (!(o instanceof TilePos)) return false;
        TilePos otp = (TilePos) o;
        return zoomLevel == otp.zoomLevel && tx == otp.tx && ty == otp.ty;
    }

    @Override public String toString() { return zoomLevel + "," + tx + "," + ty; }
}
