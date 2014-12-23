package com.max.route;

import java.util.List;

public class Route {
    public final List<RouteSegment> segments;
    public final List<PointOfInterest> pointsOfInterest;

    public Route(List<RouteSegment> segments, List<PointOfInterest> pointsOfInterest) {
        this.segments = segments;
        this.pointsOfInterest = pointsOfInterest;
    }
}
