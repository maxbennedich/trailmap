package com.max.route;

import com.max.logic.XY;

import java.io.Serializable;

public class PointOfInterest implements Serializable {
    private static final long serialVersionUID = 1L;

    public final String name;
    public String label;
    public final int utmX, utmY;

    /** Index into list of route points. -1 if not available/applicable. */
    public final int routeIndex;

    public PointOfInterest(String name, int utmX, int utmY) {
        this(name, utmX, utmY, -1);
    }

    public PointOfInterest(String name, int utmX, int utmY, int routeIndex) {
        this.name = name;
        this.utmX = utmX;
        this.utmY = utmY;
        this.routeIndex = routeIndex;
    }

    /** @return Distance to given coordinate in squared meters. */
    public long dist2(int utmX, int utmY) {
        return (long) (this.utmX - utmX) * (this.utmX - utmX) + (long) (this.utmY - utmY) * (this.utmY - utmY);
    }
}
