package com.max.logic;

public class XYd {
    public final double x, y;

    public XYd(double x, double y) { this.x = x; this.y = y; }

    @Override public int hashCode() { return ((int)x)<<16 + (int)y; }

    @Override public boolean equals(Object o) {
        if (!(o instanceof XYd))
            return false;
        XYd xy = (XYd) o;
        return xy.x == x && xy.y == y;
    }

    @Override public String toString() { return x + "," + y; }
}
