package com.max.logic;

public class XY {
    public final int x, y;

    public XY(int x, int y) { this.x = x; this.y = y; }

    public XY div(int n) { return new XY(x/n, y/n); }
    public XY add(int xa, int ya) { return new XY(x+xa, y+ya); }
    public XY add(XY xy) { return add(xy.x, xy.y); }
    public XY sub(int xa, int ya) { return new XY(x-xa, y-ya); }
    public XY sub(XY xy) { return sub(xy.x, xy.y); }

    public boolean within(Rectangle r) {
        return x >= r.min.x && y >= r.min.y && x <= r.max.x && y <= r.max.y;
    }

    @Override public int hashCode() { return x<<16 + y; }

    @Override public boolean equals(Object o) {
        if (!(o instanceof XY))
            return false;
        XY xy = (XY) o;
        return xy.x == x && xy.y == y;
    }

    @Override public String toString() { return x + "," + y; }
}
