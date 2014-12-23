package com.max.route;

import com.max.logic.Rectangle;
import com.max.logic.XY;
import com.max.logic.XYd;

import java.util.List;

public class RouteSegment {
    public final String name;
    public RoadSurface roadSurface; // non-final for fixing inconsistent road types
    public final List<XY> points;
    public final Rectangle boundingBox;

    public RouteSegment(String name, RoadSurface roadSurface, List<XY> points) {
        this.name = name;
        this.roadSurface = roadSurface;
        this.points = points;
        boundingBox = getBoundingBox();
    }

    private Rectangle getBoundingBox() {
        int minX = 1<<30, minY = 1<<30, maxX = -(1<<30), maxY = -(1<<30);
        for (XY xy : points) {
            minX = Math.min(minX, xy.x);
            minY = Math.min(minY, xy.y);
            maxX = Math.max(maxX, xy.x);
            maxY = Math.max(maxY, xy.y);
        }
        return new Rectangle(new XYd(minX, minY), new XYd(maxX, maxY));
    }
}
