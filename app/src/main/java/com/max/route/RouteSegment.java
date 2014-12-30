package com.max.route;

import com.max.logic.XY;

import java.util.List;

public class RouteSegment {
    public final String name;
    public RoadSurface roadSurface; // non-final for fixing inconsistent road types
    public final List<XY> points;

    public RouteSegment(String name, RoadSurface roadSurface, List<XY> points) {
        this.name = name;
        this.roadSurface = roadSurface;
        this.points = points;
    }
}
