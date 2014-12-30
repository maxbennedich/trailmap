package com.max.logic;

public class XY {
    public final int x, y;

    public XY(int x, int y) { this.x = x; this.y = y; }

    @Override public int hashCode() { return x<<16 + y; }

    @Override public boolean equals(Object o) {
        if (!(o instanceof XY))
            return false;
        XY xy = (XY) o;
        return xy.x == x && xy.y == y;
    }

    @Override public String toString() { return x + "," + y; }
}
