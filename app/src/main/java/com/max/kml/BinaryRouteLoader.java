package com.max.kml;

import com.max.route.QuadPoint;
import com.max.route.RoadSurface;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class BinaryRouteLoader {
    public List<QuadPoint> loadRoute(InputStream is) throws InvalidKMLException {
        List<QuadPoint> points;

        try (DataInputStream dis = new DataInputStream(new BufferedInputStream(is, 65536))) {
            int pointCount = dis.readInt();
            points = new ArrayList<>(pointCount);
            for (int n = 0; n < pointCount; ++n) {
                int surface = dis.readByte();
                int x = dis.readInt();
                int y = dis.readInt();
                points.add(new QuadPoint(x, y, RoadSurface.values()[surface]));
            }
        } catch (IOException ioe) {
            throw new IllegalStateException("Error while loading route", ioe);
        }

        return points;
    }
}
