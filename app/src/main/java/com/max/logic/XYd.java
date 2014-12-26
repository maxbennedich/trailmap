package com.max.logic;

public class XYd {
    public final double x, y;

    public XYd(double x, double y) { this.x = x; this.y = y; }

    public XYd div(double n) { return new XYd(x/n, y/n); }
    public XYd mul(double d) { return new XYd(x*d, y*d); }
    public XYd add(double xa, double ya) { return new XYd(x+xa, y+ya); }
    public XYd add(XYd xy) { return add(xy.x, xy.y); }
    public XYd sub(double xa, double ya) { return new XYd(x-xa, y-ya); }
    public XYd sub(XYd xy) { return sub(xy.x, xy.y); }

    public boolean within(Rectangle r) {
        return x >= r.min.x && y >= r.min.y && x <= r.max.x && y <= r.max.y;
    }

    @Override public int hashCode() { return ((int)x)<<16 + (int)y; }

    @Override public boolean equals(Object o) {
        if (!(o instanceof XYd))
            return false;
        XYd xy = (XYd) o;
        return xy.x == x && xy.y == y;
    }

    @Override public String toString() { return x + "," + y; }
}
