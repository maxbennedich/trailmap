package com.max.route;

import java.util.List;

public class Route {
    public final List<RouteSegment> segments;

    /** Sequential points making up the route. */
    public final List<PointOfInterest> waypoints;

    /** General points of interest such as gas stations and restaurants. */
    public final List<PointOfInterest> pointsOfInterest;

    public Route(List<RouteSegment> segments, List<PointOfInterest> wayPoints, List<PointOfInterest> pointsOfInterest) {
        this.segments = segments;
        this.waypoints = wayPoints;
        this.pointsOfInterest = pointsOfInterest;
    }

    /**
     * Fix inconsistent road types, i.e. if a tiny stretch (like 20 meters) of one road surface is
     * surrounded by another (longer) road path type, change the short stretch to make the road consistent.
     * <br/>
     * This should be run on raw routes coming from KML files.
     */
    public void fixInconsistencies() {
        for (int s = 1; s < segments.size()-1; ++s) {
            if (segments.get(s).points.size() <= 3 &&
                    segments.get(s-1).points.size() + segments.get(s+1).points.size() >= 6 &&
                    segments.get(s-1).pathType != segments.get(s).pathType &&
                    segments.get(s-1).pathType == segments.get(s+1).pathType) {
                segments.get(s).pathType = segments.get(s-1).pathType;
            }
        }
    }
}
