package com.max.route;

import com.max.logic.XY;

import java.util.List;

public class RouteSegment {
    public final String name;
    public PathType pathType; // non-final for fixing inconsistent road types
    public final List<XY> points;

    public RouteSegment(String name, PathType pathType, List<XY> points) {
        this.name = name;
        this.pathType = pathType;
        this.points = points;
    }
}
