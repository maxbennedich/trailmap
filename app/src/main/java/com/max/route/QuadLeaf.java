package com.max.route;

import java.io.Serializable;

public class QuadLeaf implements Serializable {
    private static final long serialVersionUID = 1L;

    public final int x, y;
    public transient QuadLeaf next;
    public final RoadSurface surface;

    public QuadLeaf(int x, int y, QuadLeaf next, RoadSurface surface) {
        this.x = x;
        this.y = y;
        this.next = next;
        this.surface = surface;
    }

    @Override public String toString() { return x + "," + y + "," + surface; }
}
