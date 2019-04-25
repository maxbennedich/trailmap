package com.max.route;

/** Level of detail configuration for drawing paths. */
public class PathLevelOfDetail {
    /**
     * Quad tree node query level for each zoom level (size: MAX_ZOOM_LEVEL+1). This specifies
     * the amount of detail desired for each zoom level. We want the least amount of detail
     * visibly acceptable so to minimize the number of lines needed to draw the path.
     */
    public final int[] queryLevelByZoomLevel;

    /**
     * Minimum zoom level for each query tree node level (size: Integer.SIZE+1). This is
     * the inverse of getQueryLevelByZoomLevel. Given a point/query level, this array can be
     * used to look up which tile zoom levels the point belongs to.
     */
    public final int[] zoomLevelByQueryLevel;

    public PathLevelOfDetail(int[] queryLevelByZoomLevel) {
        this.queryLevelByZoomLevel = queryLevelByZoomLevel;
        this.zoomLevelByQueryLevel = invertLevels(queryLevelByZoomLevel);
    }

    private static final int[] invertLevels(int[] queryByZoom) {
        int[] zoomByQuery = new int[Integer.SIZE + 1];
        for (int lvl = 0, zoom = queryByZoom.length - 1; lvl < zoomByQuery.length; ++lvl) {
            for (; zoom >= 0 && lvl >= queryByZoom[zoom]; --zoom) ;
            zoomByQuery[lvl] = zoom + 1;
        }
        return zoomByQuery;
    }
}
