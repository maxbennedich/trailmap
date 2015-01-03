package com.max.route;

public class QuadPointArray {
    public final int[] x;
    public final int[] y;
    public final RoadSurface[] surface;

    public final int nrPoints;

    public QuadPointArray(int[] x, int[] y, RoadSurface[] surface) {
        this.x = x;
        this.y = y;
        this.surface = surface;
        this.nrPoints = x.length;
        assert y.length == nrPoints;
        assert surface.length == nrPoints;
    }
}
