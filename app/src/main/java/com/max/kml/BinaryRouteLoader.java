package com.max.kml;

import com.max.route.QuadPointArray;
import com.max.route.PathType;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

public class BinaryRouteLoader {
    public QuadPointArray loadRoute(InputStream is) throws InvalidKMLException {
        QuadPointArray points = null;
        try (DataInputStream dis = new DataInputStream(new BufferedInputStream(is, 65536))) {
            int pointCount = dis.readInt();
            int[] x = new int[pointCount];
            int[] y = new int[pointCount];
            PathType[] pathType = new PathType[pointCount];
            for (int n = 0; n < pointCount; ++n) {
                int pt = dis.readByte();
                x[n] = dis.readInt();
                y[n] = dis.readInt();
                pathType[n] = PathType.values()[pt];
            }
            points = new QuadPointArray(x, y, pathType);
        } catch (IOException ioe) {
            throw new IllegalStateException("Error while loading route", ioe);
        }
        return points;
    }
}
