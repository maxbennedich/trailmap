package com.max.route;

/**
 * Top level configuration for a path. Typically there would be one instance of this class
 * for the route path, one for the GPS trace path, and any other paths drawn.
 */
public class PathConfiguration {
    public final PathLevelOfDetail levelOfDetail;

    /** Whether the path is cyclic. A cyclic path's end points will be connected when drawn. */
    public final boolean cyclic;

    /**
     * Width in pixels of path. This matters when drawing the path, since although the path is
     * a few pixels outside the drawing area, part of it might still be visible.
     */
    public final int width;

    public PathConfiguration(PathLevelOfDetail levelOfDetail, boolean cyclic, int width) {
        this.levelOfDetail = levelOfDetail;
        this.cyclic = cyclic;
        this.width = width;
    }
}
