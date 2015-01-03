package com.max.route;

public class QuadPointArray {
    public int[] x;
    public int[] y;
    public PathType[] pathType;

    public int nrPoints;

    public QuadPointArray(int[] x, int[] y, PathType[] pathType) {
        this.x = x;
        this.y = y;
        this.pathType = pathType;
        this.nrPoints = x.length;
        assert y.length == nrPoints;
        assert pathType.length == nrPoints;
    }

    public QuadPointArray(int initialCapacity) {
        x = new int[initialCapacity];
        y = new int[initialCapacity];
        pathType = new PathType[initialCapacity];
        nrPoints = 0;
    }

    public void add(int px, int py, PathType pSurface) {
        if (nrPoints == x.length) {
            // arrays full, double them in size
            int newSize = nrPoints << 1;
            int[] newX = new int[newSize];
            int[] newY = new int[newSize];
            PathType[] newPathType = new PathType[newSize];

            System.arraycopy(x, 0, newX, 0, nrPoints);
            System.arraycopy(y, 0, newY, 0, nrPoints);
            System.arraycopy(pathType, 0, newPathType, 0, nrPoints);

            x = newX;
            y = newY;
            pathType = newPathType;
        }

        x[nrPoints] = px;
        y[nrPoints] = py;
        pathType[nrPoints] = pSurface;
        nrPoints++;
    }
}
