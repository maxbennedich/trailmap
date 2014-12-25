package com.max.route;

import com.max.logic.XY;

import java.io.Serializable;

public class PointOfInterest implements Serializable {
    private static final long serialVersionUID = 1L;

    public final String name;
    public final int utmX, utmY;

    public PointOfInterest(String name, int utmX, int utmY) {
        this.name = name;
        this.utmX = utmX;
        this.utmY = utmY;
    }
}
