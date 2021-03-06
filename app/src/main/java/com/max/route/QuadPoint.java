package com.max.route;

import java.io.Serializable;

public class QuadPoint implements Serializable {
    private static final long serialVersionUID = 1L;

    public final int x, y;
    public final PathType surface;

    public QuadPoint(int x, int y, PathType surface) {
        this.x = x;
        this.y = y;
        this.surface = surface;
    }

    @Override public String toString() { return x + "," + y + "," + surface; }
}