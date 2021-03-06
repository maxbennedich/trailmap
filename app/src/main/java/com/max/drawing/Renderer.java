package com.max.drawing;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import com.max.logic.Tile;
import com.max.config.Config;
import com.max.main.LogStats;
import com.max.main.Persistable;
import com.max.main.R;
import com.max.main.Settings;
import com.max.route.Navigator;
import com.max.route.PathConfiguration;
import com.max.route.PathLevelOfDetail;
import com.max.route.PathType;
import com.max.route.PointOfInterest;
import com.max.route.QuadNode;
import com.max.route.QuadPointArray;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Renderer extends View implements Persistable {

    public Config config;

    public QuadPointArray points;
    public QuadNode quadRoot;
    public QuadNode[] segmentQuadRoots;

    public Navigator navigator;

    /** Sequential points making up the route. */
    public List<PointOfInterest> waypoints;

    /** General points of interest such as gas stations and restaurants. */
    public List<PointOfInterest> pointsOfInterest;

    private Bitmap emptyTile, gpsIcon, scale, layerBitmap;
    private Canvas layerCanvas;

    private LinkedHashMap<Integer, Tile> tileCache = getTileCache();

    private static final int MIN_ZOOM_LEVEL = 0;
    private static final int MAX_ZOOM_LEVEL = 10;

    /** Log2 of tile utm size at zoom level 0. */
    private static final int ZOOM_0_TILE_BITS = 20;

    private static final double MIN_SCALE = 1 << MIN_ZOOM_LEVEL;
    private static final double MAX_SCALE = (1 << MAX_ZOOM_LEVEL) << 3;
    private double scaleFactor = 150;

    public int zoomLevel = (int)log2(scaleFactor);

    /** Amount of "digital" zoom on top of integer tile level zoom, i.e. scaleFactor/2^zoomLevel. */
    private double scalingZoom = scaleFactor / (1<<zoomLevel);

    private static final int ZOOM_LEVEL_SHOW_LABELS = 7;

    private static final int TILE_WIDTH_BITS = 8;
    private static final int TILE_WIDTH_PIXELS = 1<<TILE_WIDTH_BITS;

    /** 100 corresponds to ~26 mb image data (256x256 pixels, 4 bytes per pixel) */
    private static final int TILE_CACHE_SIZE = 100;

    /** Contains all tile indices for which we have a tile on disk. */
    private Set<Integer> existingTiles = new HashSet<>();

    private double centerUtmX = Settings.START_CENTER_UTM_X, centerUtmY = Settings.START_CENTER_UTM_Y;
    private double gpsX = centerUtmX, gpsY = centerUtmY;
    private float gpsBearing;
    private float gpsSpeed;
    private float gpsDist = 0;

    private final Typeface fontAurora;

    private LinkedHashMap<Integer, Tile> getTileCache() {
        return new LinkedHashMap<Integer, Tile>(TILE_CACHE_SIZE, 0.75f, true) {
            private static final long serialVersionUID = 1L;

            /**
             * Override get to load tiles not in the cache and insert them into the cache.
             *
             * @return Null if image could not be loaded (typically out of bounds).
             */
            @Override public Tile get(Object key) {
                Tile tile = super.get(key);
                if (tile != null)
                    return tile;

                Integer tp = (Integer) key;
                if ((tile = loadTile(tp)) != null)
                    put(tp, tile);
                return tile;
            }

            @Override protected boolean removeEldestEntry(Map.Entry<Integer, Tile> eldest) {
                return size() > TILE_CACHE_SIZE;
            }
        };
    }

    public Renderer(Context context, AttributeSet aSet) {
        super(context, aSet);

        LogStats timer = new LogStats();

        zoomDetector = new ScaleGestureDetector(getContext(), new ScaleListener());

        fontAurora = Typeface.createFromAsset(context.getAssets(), "fonts/aurora.otf");
        Paints.FONT_OUTLINE_GPS_STATS.setTypeface(fontAurora);
        Paints.FONT_GPS_STATS.setTypeface(fontAurora);
        Paints.FONT_OUTLINE_SCALE.setTypeface(fontAurora);
        Paints.FONT_SCALE.setTypeface(fontAurora);
        Paints.FONT_OUTLINE_POI.setTypeface(fontAurora);
        Paints.FONT_POI.setTypeface(fontAurora);
        Paints.FONT_OUTLINE_NAVIGATION_STATS.setTypeface(fontAurora);
        Paints.FONT_NAVIGATION_STATS.setTypeface(fontAurora);
        Paints.FONT_OUTLINE_NAVIGATION_SUBSCRIPT.setTypeface(fontAurora);
        Paints.FONT_NAVIGATION_SUBSCRIPT.setTypeface(fontAurora);

        loadBitmaps();

        inventoryTiles();

        navigator = new Navigator(this);

        timer.log("Initialized renderer");
    }

    @Override
    public void saveInstanceState(Bundle savedInstanceState, String prefix) {
        prefix += "_";

        // map position
        savedInstanceState.putDouble(prefix + "utmX", centerUtmX);
        savedInstanceState.putDouble(prefix + "utmY", centerUtmY);
        savedInstanceState.putDouble(prefix + "scaleFactor", scaleFactor);

        // gps info
        savedInstanceState.putDouble(prefix + "gpsX", gpsX);
        savedInstanceState.putDouble(prefix + "gpsY", gpsY);
        savedInstanceState.putFloat(prefix + "gpsDist", gpsDist);
        // TODO: gps track

        // navigator
        navigator.saveInstanceState(savedInstanceState, "navigator");
    }

    @Override
    public void restoreInstanceState(Bundle savedInstanceState, String prefix) {
        prefix += "_";

        // map position
        centerUtmX = savedInstanceState.getDouble(prefix + "utmX");
        centerUtmY = savedInstanceState.getDouble(prefix + "utmY");
        scaleFactor = savedInstanceState.getDouble(prefix + "scaleFactor");

        mapCenterUpdated(false);
        scaleFactorUpdated();

        // gps info
        gpsX = savedInstanceState.getDouble(prefix + "gpsX");
        gpsY = savedInstanceState.getDouble(prefix + "gpsY");
        gpsDist = savedInstanceState.getFloat(prefix + "gpsDist");
        // TODO: gps track

        // navigator
        navigator.restoreInstanceState(savedInstanceState, "navigator");
    }

    private static final BitmapFactory.Options NO_SCALING = new BitmapFactory.Options();
    static {
        NO_SCALING.inScaled = false; // to keep original tile size
        NO_SCALING.inMutable = true; // to enable drawing on top of loaded tiles
        NO_SCALING.inTempStorage = new byte[65536]; // to avoid allocating a new buffer for each loaded tile
    }

    private void loadBitmaps() {
        gpsIcon = BitmapFactory.decodeResource(getResources(), R.drawable.gps_arrow_yellow_120x120, NO_SCALING);
        scale = BitmapFactory.decodeResource(getResources(), R.drawable.scale, NO_SCALING);
        emptyTile = BitmapFactory.decodeResource(getResources(), R.drawable.empty, NO_SCALING);

        layerBitmap = Bitmap.createBitmap(TILE_WIDTH_PIXELS, TILE_WIDTH_PIXELS, Bitmap.Config.ARGB_8888);
        layerBitmap.setDensity(emptyTile.getDensity());
        layerCanvas = new Canvas(layerBitmap);
    }

    public static File getTileRoot() {
        return Settings.TILE_ROOT_PATH;
    }

    /** Populate the structure of available tiles. */
    private void inventoryTiles() {
        Pattern p = Pattern.compile("tile_(\\d{1,2})_(\\d+)_(\\d+)\\.png");

        Log.d("TileCache", "Root = " + getTileRoot());
        for (String asset : getTileRoot().list()) {
            Matcher m = p.matcher(asset);
            if (m.find()) {
                int zoomLevel = Integer.valueOf(m.group(1));
                int tx = Integer.valueOf(m.group(2));
                int ty = Integer.valueOf(m.group(3));
                existingTiles.add(getTilePos(zoomLevel, tx, ty));
            }
        }
    }

    static final int getTilePos(int zoomLevel, int tx, int ty) {
        // zoom: 4 bits (0-15)
        // tx/ty: 14 bits (0-16383)
        return (zoomLevel << 28) + (tx << 14) + ty;
    }

    static final int getZoomLevel(int tilePos) { return tilePos >>> 28; }
    static final int getTX(int tilePos) { return (tilePos >> 14) & 0x3fff; }
    static final int getTY(int tilePos) { return tilePos & 0x3fff; }

    private Tile loadTile(Integer tp) {
        // see if tile exists
        if (existingTiles.contains(tp)) {
            int zoom = getZoomLevel(tp);
            int tx = getTX(tp), ty = getTY(tp);

            String tileName = "tile_" + zoom + "_" + tx + "_" + ty + ".png";
            Log.d("AccuMap", "Loading " + tileName);
            Bitmap map = loadTileFromStorage(tileName);

            Tile tile = new Tile(zoom, tx, ty, map);

            // dim tile
            if (config.mapBrightness.value != 100)
                tile.canvas.drawColor(255 - (255 * config.mapBrightness.value / 100) << 24);

            boolean anyLayerPresent = config.showRoute.value || config.showGpsTrace.value || config.showPointsOfInterest.value;
            if (anyLayerPresent) {
                // clear left-overs from last tile (re-using the same bitmap/canvas)
                layerCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

                if (config.showRoute.value)
                    drawPath(points, quadRoot, ROUTE_PATH, layerCanvas, tile);
                if (config.showGpsTrace.value)
                    drawPath(historyPoints, historyQuadTree, GPS_PATH, layerCanvas, tile);
                if (config.showPointsOfInterest.value)
                    drawPointsOfInterest(layerCanvas, tile);

                // finally blit layers on top of tile
                Rect fullTile = new Rect(0, 0, TILE_WIDTH_PIXELS, TILE_WIDTH_PIXELS);
                Rect excludeBorder = new Rect(1, 1, TILE_WIDTH_PIXELS+1, TILE_WIDTH_PIXELS+1);
                tile.canvas.drawBitmap(layerBitmap, fullTile, excludeBorder, null);
            }

            // TODO this is broken! it has to be done during onDraw, or layers can draw on top of the empty part!!!
            // do any empty tile overwriting at the end since we don't want layers on top of it
            fixTileIfPartiallyEmpty(tile);

            return tile;
        }
        return null;
    }

    private Bitmap loadTileFromStorage(String tileName) {
        File tileFile = new File(getTileRoot(), tileName);
        try (InputStream is = new FileInputStream(tileFile)) {
            return BitmapFactory.decodeStream(is, null, NO_SCALING);
        } catch (IOException e) {
            throw new IllegalStateException("Error loading tile "+tileName, e);
        }
    }

    /**
     * This method is needed since the edge tiles for Sweden are partial (part tile,
     * part black). We therefore need special code to detect these edge tiles and replace
     * the part outside the region limits with the empty tile.
     */
    private void fixTileIfPartiallyEmpty(Tile tile) {
        if (tile.zoomLevel >= MapConstants.SWEDEN_ONLY_MIN_ZOOM && tile.zoomLevel <= MapConstants.ALL_OF_SWEDEN_MAX_ZOOM) {
            // calculate utm coordinates for tile corners
            int tileSizeBits = ZOOM_0_TILE_BITS - tile.zoomLevel;
            int tileSizeUtm = 1 << tileSizeBits;
            int utx0 = (tile.tx << tileSizeBits) - 1_200_000;
            int uty0 = 8_500_000 - (tile.ty + 1 << tileSizeBits);

            // note that the empty tile will be blitted twice for corner tiles
            if (utx0 < MapConstants.UTM_SWEDEN_X0 && utx0 + tileSizeUtm >= MapConstants.UTM_SWEDEN_X0) {
                int border = MapConstants.UTM_SWEDEN_X0 - utx0 >> tileSizeBits - TILE_WIDTH_BITS;
                Rect part = new Rect(0, 0, border, TILE_WIDTH_PIXELS);
                tile.canvas.drawBitmap(emptyTile, part, part, null);
            }
            if (utx0 <= MapConstants.UTM_SWEDEN_X1 && utx0 + tileSizeUtm > MapConstants.UTM_SWEDEN_X1) {
                int border = MapConstants.UTM_SWEDEN_X1 - utx0 >> tileSizeBits - TILE_WIDTH_BITS;
                Rect part = new Rect(border, 0, TILE_WIDTH_PIXELS, TILE_WIDTH_PIXELS);
                tile.canvas.drawBitmap(emptyTile, part, part, null);
            }
            if (uty0 < MapConstants.UTM_SWEDEN_Y0 && uty0 + tileSizeUtm >= MapConstants.UTM_SWEDEN_Y0) {
                int border = MapConstants.UTM_SWEDEN_Y0 - uty0 >> tileSizeBits - TILE_WIDTH_BITS;
                Rect part = new Rect(0, TILE_WIDTH_PIXELS - border - 1, TILE_WIDTH_PIXELS, TILE_WIDTH_PIXELS);
                tile.canvas.drawBitmap(emptyTile, part, part, null);
            }
            if (uty0 <= MapConstants.UTM_SWEDEN_Y1 && uty0 + tileSizeUtm > MapConstants.UTM_SWEDEN_Y1) {
                int border = MapConstants.UTM_SWEDEN_Y1 - uty0 >> tileSizeBits - TILE_WIDTH_BITS;
                Rect part = new Rect(0, 0, TILE_WIDTH_PIXELS, TILE_WIDTH_PIXELS - border);
                tile.canvas.drawBitmap(emptyTile, part, part, null);
            }
        }
    }

    final float utmToTilePixelX(int utmx, int utmx0, int tileSizeUtm) {
        return (float)(utmx - utmx0)*TILE_WIDTH_PIXELS/tileSizeUtm;
    }

    float utmToTilePixelY(int utmy, int utmy0, int tileSizeUtm) {
        return (float)(utmy0 + tileSizeUtm-1 - utmy)*TILE_WIDTH_PIXELS/tileSizeUtm;
    }

    /**
     * Clears the tile cache, forcing all tiles to be re-rendered.
     * Optionally also invalidates the view to force a display redraw.
     */
    public void invalidateTileCache(boolean invalidateView) {
        tileCache.clear();
        if (invalidateView)
            invalidate();
    }

    QuadMatches matches = new QuadMatches();

    // instance level to avoid re-instantiating for each call to drawPath
    boolean[] pathTypesUsed = new boolean[PathType.values().length];
    Path[] paths = new Path[PathType.values().length];
    { for (int n = 0; n < PathType.values().length; ++n) paths[n] = new Path(); }

    private void drawPath(QuadPointArray pathPoints, QuadNode pathQuadTree, PathConfiguration pathConfig, Canvas canvas, Tile tile) {
        // TODO try arcs instead of lines

        // calculate utm coordinates for tile corners
        int tileSizeBits = ZOOM_0_TILE_BITS - tile.zoomLevel;
        int tileSizeUtm = 1 << tileSizeBits;
        int utx0 = (tile.tx << tileSizeBits) - 1_200_000;
        int uty0 = 8_500_000 - (tile.ty+1 << tileSizeBits);

        // find route points visible on tile by querying quad tree
        matches.clear();
        int pathWidthOffset = pathConfig.width << tileSizeBits - TILE_WIDTH_BITS;
        int queryUtx0 = utx0 - pathWidthOffset/2;
        int queryUty0 = uty0 - pathWidthOffset/2;
        int queryLevel = pathConfig.levelOfDetail.queryLevelByZoomLevel[tile.zoomLevel];
        pathQuadTree.queryTree(queryLevel, queryUtx0, queryUty0, queryUtx0+tileSizeUtm+pathWidthOffset, queryUty0+tileSizeUtm+pathWidthOffset, pathPoints, matches);
        Log.d("OptiMap", "XYZ Got " + matches.matchCount + " matches");

        if (matches.matchCount != 0) {
            matches.sort();

            int stepSize = 1 << queryLevel;

            int prevIdx = -1, p2Idx = -1;
            float p2x = 0, p2y = 0;
            int p2Type = 0;

            for (int k = 0; k < matches.matchCount; ++k) {
                int idx = matches.get(k);

                if (prevIdx == -1 || idx - prevIdx > stepSize) {
                    // first point, or just entered screen, draw partial on-screen segment
                    float px = utmToTilePixelX(pathPoints.x[idx], utx0, tileSizeUtm);
                    float py = utmToTilePixelY(pathPoints.y[idx], uty0, tileSizeUtm);

                    if (idx != 0 || pathConfig.cyclic) {
                        // the math below for idx=0 is to select the last point for the current query level
                        // TODO won't this draw the last segment twice for cyclical routes when both the last and first point are visible?
                        // --> might check if point is visible on tile, or perhaps better set a flag and don't draw the final segment
                        int p0Idx = idx == 0 ? ((pathPoints.nrPoints - 1) / stepSize) * stepSize : idx - stepSize;
                        float p0x = utmToTilePixelX(pathPoints.x[p0Idx], utx0, tileSizeUtm);
                        float p0y = utmToTilePixelY(pathPoints.y[p0Idx], uty0, tileSizeUtm);
                        int p0Type = pathPoints.pathType[p0Idx].ordinal();
                        paths[p0Type].moveTo(p0x, p0y);
                        paths[p0Type].lineTo(px, py);
                        pathTypesUsed[p0Type] = true;
                    }

                    // start next path type segment
                    p2Type = pathPoints.pathType[idx].ordinal();
                    paths[p2Type].moveTo(px, py);
                } else {
                    // same point as previous "next"; don't calculate again; move if path type changed
                    int pType = pathPoints.pathType[p2Idx].ordinal();
                    if (pType != p2Type) {
                        p2Type = pType;
                        paths[p2Type].moveTo(p2x, p2y);
                    }
                }

                // draw line to the next point (which may or may not be on screen)
                p2Idx = Math.min(idx + stepSize, pathPoints.nrPoints - 1);
                p2x = utmToTilePixelX(pathPoints.x[p2Idx], utx0, tileSizeUtm);
                p2y = utmToTilePixelY(pathPoints.y[p2Idx], uty0, tileSizeUtm);
                paths[p2Type].lineTo(p2x, p2y);
                pathTypesUsed[p2Type] = true;

                prevIdx = idx;
            }

            // first draw all outlines
            for (int n = 0; n < PathType.values().length; ++n)
                if (pathTypesUsed[n] && PathType.values()[n].outlinePaint != null)
                    canvas.drawPath(paths[n], PathType.values()[n].outlinePaint);

            // then draw actual paths on top of outlines (this is needed in order to have
            // smooth connection points between adjacent paths)
            for (int n = 0; n < PathType.values().length; ++n) {
                if (pathTypesUsed[n]) {
                    canvas.drawPath(paths[n], PathType.values()[n].paint);
                    paths[n].reset();
                    pathTypesUsed[n] = false;
                }
            }
        }
    }

    private void drawPointsOfInterest(Canvas canvas, Tile tile) {
        if (pointsOfInterest != null)
            drawPointsOfInterest(canvas, tile, pointsOfInterest, Paints.POINT_OF_INTEREST, Paints.POINT_OF_INTEREST_OUTLINE);
        if (waypoints != null)
            drawPointsOfInterest(canvas, tile, waypoints, Paints.WAYPOINT, Paints.WAYPOINT_OUTLINE);
    }

    private void drawPointsOfInterest(Canvas canvas, Tile tile, List<PointOfInterest> pois,
                                      Paint pointPaint, Paint outlinePaint) {
        // calculate utm coordinates for tile corners
        int tileSizeBits = ZOOM_0_TILE_BITS - tile.zoomLevel;
        int tileSizeUtm = 1 << tileSizeBits;
        int utx0 = (tile.tx << tileSizeBits) - 1_200_000;
        int uty0 = 8_500_000 - (tile.ty+1 << tileSizeBits);

        for (int k = 0; k < pois.size(); ++k) {
            PointOfInterest poi = pois.get(k);
            float x = utmToTilePixelX(poi.utmX, utx0, tileSizeUtm);
            float y = utmToTilePixelY(poi.utmY, uty0, tileSizeUtm);
            if (x >= -Paints.POINT_OF_INTEREST_SIZE/2 && x < tileSizeUtm+Paints.POINT_OF_INTEREST_SIZE/2 && y >= -Paints.POINT_OF_INTEREST_SIZE/2 && y < tileSizeUtm+Paints.POINT_OF_INTEREST_SIZE/2) {
                canvas.drawPoint(x, y, outlinePaint);
                canvas.drawPoint(x, y, pointPaint);
            }

            if (tile.zoomLevel >= ZOOM_LEVEL_SHOW_LABELS) {
                float textWidth = Paints.FONT_OUTLINE_POI.measureText(poi.label);
                float textHeight = Paints.FONT_OUTLINE_POI.getTextSize(); // TODO: constant
                float tx = x+6, ty = y+6;
                if (tx >= -textWidth && tx < tileSizeUtm && ty >= -textHeight && ty < tileSizeUtm) {
                    canvas.drawText(poi.label, tx, ty, Paints.FONT_OUTLINE_POI);
                    canvas.drawText(poi.label, tx, ty, Paints.FONT_POI);
                }
            }
        }
    }

    // view size related variables
    private double minZoomFitsOnScreen;
    private double screenMidX, screenMidY;

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        Log.d("OptiMap", "On size changed: "+w+","+h);
        screenMidX = w * 0.5;
        screenMidY = h * 0.5;

        // calculate minimum zoom level that still covers the view fully
        double minZoomX = (1 << ZOOM_0_TILE_BITS - TILE_WIDTH_BITS) * w / (double)(MapConstants.UTM_EXTREME_X1 - MapConstants.UTM_EXTREME_X0);
        double minZoomY = (1 << ZOOM_0_TILE_BITS - TILE_WIDTH_BITS) * h / (double)(MapConstants.UTM_EXTREME_Y1 - MapConstants.UTM_EXTREME_Y0);
        minZoomFitsOnScreen = Math.max(MIN_SCALE, Math.max(minZoomX, minZoomY));

        scaleFactorUpdated();
        mapCenterUpdated(false);
    }

    final int pixelToUtm(double pixel) {
        return (int)(pixel*(1<<(ZOOM_0_TILE_BITS-zoomLevel-TILE_WIDTH_BITS)) / scalingZoom + 0.5);
    }

    final double utmToPixel(double utm) {
        return utm/(1<<(ZOOM_0_TILE_BITS-zoomLevel-TILE_WIDTH_BITS)) * scalingZoom;
    }

    final double utmToScreenX(int utmx) {
        return screenMidX + utmToPixel(utmx - centerUtmX);
    }

    final double utmToScreenY(int utmy) {
        return screenMidY - utmToPixel(utmy - centerUtmY);
    }

    final double utmToScreenX(double utmx) {
        return screenMidX + utmToPixel(utmx - centerUtmX);
    }

    final double utmToScreenY(double utmy) {
        return screenMidY - utmToPixel(utmy - centerUtmY);
    }

    // level of detail configs below have been empirically tested to be visibly acceptable
    private static final PathConfiguration ROUTE_PATH = new PathConfiguration(
            new PathLevelOfDetail(new int[] {11,10,9,8,7,7,6,5,4,3,0}), Navigator.CYCLIC_ROUTE, Paints.PATH_WIDTH);

    // level of detail configs below have been empirically tested to be visibly acceptable
    private static final PathConfiguration GPS_PATH = new PathConfiguration(
            new PathLevelOfDetail(new int[] {10,9,8,7,6,5,4,3,3,2,0}), false, Paints.HISTORY_WIDTH);

    /**
     * Minimum distance (squared), in meters, between two consecutive GPS history points.
     * This is to not record too many points with unnecessary precision.
     */
    private static final int MIN_HISTORY_POINT_DIST2 = 20*20;

    private QuadPointArray historyPoints;
    private QuadNode historyQuadTree;
    { resetGPS(); }

    public void resetGPS() {
        gpsDist = 0;
        historyPoints = new QuadPointArray(1024);
        historyQuadTree = new QuadNode(MapConstants.UTM_EXTREME_X0, MapConstants.UTM_EXTREME_Y0,
                MapConstants.UTM_EXTREME_X1, MapConstants.UTM_EXTREME_Y1);
    }

    public void setGPSCoordinate(double utmX, double utmY) {
        // TODO more sophisticated method is needed, such as Kalman filter
        gpsDist += Math.sqrt((utmX-gpsX)*(utmX-gpsX) + (utmY-gpsY)*(utmY-gpsY));

        gpsX = utmX;
        gpsY = utmY;

        if (config.showGpsTrace.value) {
            int utmIX = (int) (utmX + 0.5);
            int utmIY = (int) (utmY + 0.5);

            boolean addToHistory = true;
            int historyIdx = historyPoints.nrPoints;
            if (historyIdx > 0) {
                int difX = utmIX - historyPoints.x[historyIdx-1];
                int difY = utmIY - historyPoints.y[historyIdx-1];
                addToHistory = difX * difX + difY * difY >= MIN_HISTORY_POINT_DIST2;
            }

            if (addToHistory) {
                historyPoints.add(utmIX, utmIY, PathType.HISTORY);
                historyQuadTree.insertPoint(historyIdx, historyPoints);

                if (historyIdx > 0) {
                    // draw new point on all already cached tiles (since they won't get re-computed
                    // when drawn); draw for all zoom levels up until the level of the point added,
                    // to ensure paths are consistent when they are re-computed (in the tile
                    // loading code)
                    int idxLevel = QuadNode.level(historyIdx);
                    int minZoom = GPS_PATH.levelOfDetail.zoomLevelByQueryLevel[idxLevel];

                    for (int zoom = MAX_ZOOM_LEVEL; zoom >= minZoom; --zoom) {
                        int stepSize = 1 << GPS_PATH.levelOfDetail.queryLevelByZoomLevel[zoom];

                        int tileSizeBits = ZOOM_0_TILE_BITS - zoom;
                        int tileSizeUtm = 1 << tileSizeBits;
                        int tx = 1_200_000 + utmIX >> tileSizeBits;
                        int ty = 8_500_000 - utmIY >> tileSizeBits;

                        int tileUtmX = (tx << tileSizeBits) - 1_200_000;
                        int tileUtmY = 8_500_000 - (ty + 1 << tileSizeBits);
                        float tilePixelX1 = utmToTilePixelX(utmIX, tileUtmX, tileSizeUtm);
                        float tilePixelY1 = utmToTilePixelY(utmIY, tileUtmY, tileSizeUtm);

                        float tilePixelX0 = utmToTilePixelX(historyPoints.x[historyIdx-stepSize], tileUtmX, tileSizeUtm);
                        float tilePixelY0 = utmToTilePixelY(historyPoints.y[historyIdx-stepSize], tileUtmY, tileSizeUtm);

                        // Calculate first and last tile in each dimension that should be painted
                        // by the line from the previous point to the new point. This is needed
                        // to not create glitches between tiles. For the majority of points,
                        // these values will all be 0, meaning that just one tile is painted.
                        int txDif0 = (int)Math.floor(Math.min(tilePixelX0, tilePixelX1) - Paints.HISTORY_WIDTH/2) >> TILE_WIDTH_BITS;
                        int tyDif0 = (int)Math.floor(Math.min(tilePixelY0, tilePixelY1) - Paints.HISTORY_WIDTH/2) >> TILE_WIDTH_BITS;
                        int txDif1 = (int)Math.floor(Math.max(tilePixelX0, tilePixelX1) + Paints.HISTORY_WIDTH/2) >> TILE_WIDTH_BITS;
                        int tyDif1 = (int)Math.floor(Math.max(tilePixelY0, tilePixelY1) + Paints.HISTORY_WIDTH/2) >> TILE_WIDTH_BITS;

                        for (int tyDif = tyDif0; tyDif <= tyDif1; ++tyDif) {
                            for (int txDif = txDif0; txDif <= txDif1; ++txDif) {
                                int tilePos = getTilePos(zoom, tx+txDif, ty+tyDif);
                                if (tileCache.containsKey(tilePos)) { // only already cached tiles!
                                    Tile tile = tileCache.get(tilePos);
                                    float px0 = tilePixelX0 - (txDif<<TILE_WIDTH_BITS);
                                    float py0 = tilePixelY0 - (tyDif<<TILE_WIDTH_BITS);
                                    float px1 = tilePixelX1 - (txDif<<TILE_WIDTH_BITS);
                                    float py1 = tilePixelY1 - (tyDif<<TILE_WIDTH_BITS);
                                    tile.canvas.drawLine(px0, py0, px1, py1, Paints.HISTORY_PATH);
                                }
                            }
                        }
                    }
                }
            }
        }

        if (config.followGps.value) {
            // if user has manually moved around, wait a few seconds or so, then slowly pan towards the current position
            // TODO rewrite once we have a proper frame rate sync running
            long elapsed = SystemClock.uptimeMillis() - lastUserMoveMs;
            if (elapsed > DELAY_AFTER_USER_MOVE_MS) {
                float factor = Math.min(1, (elapsed - DELAY_AFTER_USER_MOVE_MS) / (float) DELAY_AFTER_USER_MOVE_MS * 0.1f);
                centerUtmX = centerUtmX * (1 - factor) + gpsX * factor;
                centerUtmY = centerUtmY * (1 - factor) + gpsY * factor;
                mapCenterUpdated(false);
            }
        }
    }

    /** In degrees (not radians). */
    public void setGPSBearing(float bearing) {
        gpsBearing = bearing;
    }

    /** In meters / second. */
    public void setGPSSpeed(float speed) {
        gpsSpeed = speed;
    }

    long prevOnDraw = -1;

    @Override
    synchronized public void onDraw(Canvas canvas) {
//        startLog();
        long t0 = LogStats.time();

        // calculate utm coordinates for screen corners
        double utmMidX = pixelToUtm(screenMidX);
        double utmMidY = pixelToUtm(screenMidY);
        int utm0x = (int)Math.floor(centerUtmX - utmMidX);
        int utm0y = (int)Math.floor(centerUtmY - utmMidY);
        int utm1x = (int)Math.ceil(centerUtmX + utmMidX);
        int utm1y = (int)Math.ceil(centerUtmY + utmMidY);

        // convert utm coordinates to tile indices
        int tileSizeBits = ZOOM_0_TILE_BITS - zoomLevel;

        int tx0 = 1_200_000 + utm0x >> tileSizeBits;
        int ty0 = 8_500_000 - utm1y >> tileSizeBits;
        int tx1 = 1_200_000 + utm1x >> tileSizeBits;
        int ty1 = 8_500_000 - utm0y >> tileSizeBits;

        float tileScreenY = (float)utmToScreenY(8_500_000 - (ty0 << tileSizeBits));
        for (int ty = ty0; ty <= ty1; ++ty) {
            float tileScreenX = (float)utmToScreenX((tx0 << tileSizeBits) - 1_200_000);
            for (int tx = tx0; tx <= tx1; ++tx) {
                Tile tile = tileCache.get(getTilePos(zoomLevel, tx, ty));
                Bitmap tileImg = tile == null ? emptyTile : tile.map;
                copyTile(canvas, tileImg, tileScreenX, tileScreenY);
                tileScreenX += TILE_WIDTH_PIXELS * scalingZoom;
            }
            tileScreenY += TILE_WIDTH_PIXELS * scalingZoom;
        }

//        log("Draw tiles");

        drawGPSMarker(canvas);
        drawScaleMarker(canvas);

        if (config.navigateEnabled.value)
            navigate(canvas);
        else
            drawGPSStats(canvas);

//        log(String.format("Center = %.0f, %.0f, Scale = %d / %.0f", centerUtmX, centerUtmY, zoomLevel, scaleFactor));

        long time = LogStats.time();
        if (prevOnDraw != -1) {
            long dif0 = time - t0;
            long dif = time - prevOnDraw;
            String txt = String.format("TOT TIME %d ms / %d FPS", dif, (1000+dif/2) / dif);
            canvas.drawText(txt, 4, 20, Paints.FONT_OUTLINE_POI);
            canvas.drawText(txt, 4, 20, Paints.FONT_POI);
            txt = "Frame: " +dif0;
            canvas.drawText(txt, 4, 40, Paints.FONT_OUTLINE_POI);
            canvas.drawText(txt, 4, 40, Paints.FONT_POI);
        }
        prevOnDraw = time;

//        drawStats(canvas);
    }

    List<Statistic> stats = new ArrayList<>();
    long lastTime;
    static class Statistic {
        final String event;
        final long ms;

        Statistic(String event, long ms) {
            this.event = event;
            this.ms = ms;
        }

        @Override public String toString() {
            return String.format("%d ms %s", ms, event);
        }
    }
    void startLog() {
        stats.clear();
        lastTime = LogStats.time();
    }
    void log(String event) {
        long curTime = LogStats.time();
        stats.add(new Statistic(event, curTime - lastTime));
        lastTime = curTime;
    }

    private void drawStats(Canvas canvas) {
        for (int k = 0; k < stats.size(); ++k) {
            canvas.drawText(stats.get(k).toString(), 4, k * 20 + 20, Paints.FONT_OUTLINE_POI);
            canvas.drawText(stats.get(k).toString(), 4, k * 20 + 20, Paints.FONT_POI);
        }
    }

    private static final double log2(double d) {
        return Math.log(d)/Math.log(2);
    }

    private static final int MAX_QUAD_TREE_MATCHES = 2048;
    public static class QuadMatches {
        private int[] matchIdx = new int[MAX_QUAD_TREE_MATCHES];
        public int matchCount;

        public void clear() {
            matchCount = 0;
        }
        public void add(int idx) {
            if (matchCount == matchIdx.length) {
                // array full, double it in size
                int[] newArray = new int[matchCount << 1];
                System.arraycopy(matchIdx, 0, newArray, 0, matchCount);
                matchIdx = newArray;
            }

            matchIdx[matchCount++] = idx;
        }
        public int get(int idx) {
            return matchIdx[idx];
        }
        public void sort() { Arrays.sort(matchIdx, 0, matchCount); }
    }

    /** in pixels */
    private static final int SCALE_MARKER_WIDTH = Paints.PAINT_SETTINGS.scaleMarkerWidth();

    /** Pre-generated labels for the scale marker (to avoid building strings while drawing). */
    private static final String[] SCALE_LABELS = generateScaleLabels();

    private static final String[] generateScaleLabels() {
        List<String> labels = new ArrayList<>();
        labels.add("0");

        DecimalFormat formatter = new DecimalFormat("#.#");
        double[] dist = {1, 2.5, 5};
        for (int k = 0; ; ++k) {
            int n = k%3, m = k/3;
            double d = dist[n] * Math.pow(10, m);
            if ((int)d > 1_000_000) break;
            labels.add(formatter.format(m > 2 ? d/1000 : d) + (m > 2 ? " km" : " m"));
        }
        return labels.toArray(new String[labels.size()]);
    }

    private void drawScaleMarker(Canvas canvas) {
        int utmDist = pixelToUtm(SCALE_MARKER_WIDTH);
        int mult = 1, exp = 0;
        for (int u = utmDist/10; u != 0; u /= 10, mult *= 10, ++exp) ;
        float d = (float)utmDist / mult;

        // find a "nice" number for the scale
        int k;
        if (d <= 1.75) { d = 1; k = 1; }
        else if (d < 3.75) { d = 2.5f; k = 2; }
        else if (d < 7.5) { d = 5; k = 3; }
        else { d = 1; k = 1; mult *= 10; ++exp; }
        int rounded = (int)(d * mult);

        double pixelLength = utmToPixel(rounded);
        int scaledWidth = (int)(pixelLength * scale.getWidth() / SCALE_MARKER_WIDTH + 0.5);

        int xAdj = - (getWidth() - scaledWidth - 36*2) / 2;

        Rect dstRect = new Rect(xAdj + getWidth()-36-scaledWidth, getHeight()-29, xAdj + getWidth()-36, getHeight()-29+scale.getHeight());
        canvas.drawBitmap(scale, null, dstRect, null);

        int labelIdx = k + exp*3;
        float textWidth = Paints.FONT_OUTLINE_SCALE.measureText(SCALE_LABELS[labelIdx]);
        canvas.drawText(SCALE_LABELS[labelIdx], xAdj + getWidth()-textWidth-8, getHeight()-36, Paints.FONT_OUTLINE_SCALE);
        canvas.drawText(SCALE_LABELS[labelIdx], xAdj + getWidth()-textWidth-8, getHeight()-36, Paints.FONT_SCALE);

        canvas.drawText(SCALE_LABELS[0], xAdj + getWidth()-36-scaledWidth-3, getHeight()-36, Paints.FONT_OUTLINE_SCALE);
        canvas.drawText(SCALE_LABELS[0], xAdj + getWidth()-36-scaledWidth-3, getHeight()-36, Paints.FONT_SCALE);
    }

    private Matrix matrix = new Matrix(); // to not have to constantly reallocate

    private void drawGPSMarker(Canvas canvas) {
        double x = utmToScreenX(gpsX);
        double y = utmToScreenY(gpsY);
        matrix.reset();
        matrix.postRotate(gpsBearing, gpsIcon.getWidth()/2, gpsIcon.getHeight()/2);
        matrix.postTranslate((float)(x-gpsIcon.getWidth()/2), (float)(y-gpsIcon.getHeight()/2));
        canvas.drawBitmap(gpsIcon, matrix, Paints.GPS_ICON);
    }

    private void drawGPSStats(Canvas canvas) {
        String txt = (int)(gpsSpeed * 3.6 + 0.5) + " km/h   "; // 3.6 for m/s -> km/h
        if (gpsDist < 500) txt += (int)(gpsDist + 0.5) + " m";
        else txt += String.format("%.2f km", gpsDist * 0.001);
        printCentered(canvas, txt, Paints.FONT_SIZE_GPS_STATS);
    }

    private void navigate(Canvas canvas) {
        navigator.updatePosition((int)(gpsX + 0.5), (int)(gpsY + 0.5), gpsSpeed);
        PointOfInterest p = navigator.getNextWaypoint();
        if (p != null) {
            printCentered(canvas, p.label, Paints.FONT_SIZE_GPS_STATS);

            // 3.6 for m/s -> km/h
            String stat1 = ""+(int)(gpsSpeed * 3.6 + 0.5);
            String stat2 = " km/h";
            String stat3 = "" + navigator.getDistanceToNextWaypoint();
            String stat4 = " m";
            float wid1 = Paints.FONT_OUTLINE_GPS_STATS.measureText(stat1);
            float wid3 = Paints.FONT_OUTLINE_GPS_STATS.measureText(stat3);
            float y = Paints.FONT_SIZE_GPS_STATS * 2;
            print(canvas, stat1, (float)screenMidX + Paints.PAINT_SETTINGS.navigationSpeedOffsetFromCenter() - wid1, y, Paints.FONT_GPS_STATS, Paints.FONT_OUTLINE_GPS_STATS);
            print(canvas, stat2, (float)screenMidX + Paints.PAINT_SETTINGS.navigationSpeedOffsetFromCenter(), y, Paints.FONT_NAVIGATION_STATS, Paints.FONT_OUTLINE_NAVIGATION_STATS);
            print(canvas, stat3, (float)screenMidX + Paints.PAINT_SETTINGS.navigationDistOffsetFromCenter() - wid3, y, Paints.FONT_GPS_STATS, Paints.FONT_OUTLINE_GPS_STATS);
            print(canvas, stat4, (float)screenMidX + Paints.PAINT_SETTINGS.navigationDistOffsetFromCenter(), y, Paints.FONT_NAVIGATION_STATS, Paints.FONT_OUTLINE_NAVIGATION_STATS);

            printNavigationStats(canvas, 10, getHeight() - Paints.FONT_SIZE_NAVIGATION_STATS*2 - 12, String.format("%.2f", navigator.getAvgSpeed() * 3.6), " km/h");
            printNavigationStats(canvas, 10, getHeight() - Paints.FONT_SIZE_NAVIGATION_STATS*1 - 12, formatSeconds(navigator.getElapsedTime()), " elapsed");
            printNavigationStats(canvas, 10, getHeight() - Paints.FONT_SIZE_NAVIGATION_STATS*0 - 12, String.format("%.1f", navigator.getDistanceTraveled() / 1000f), " km");

            if (Settings.NAVIGATION_MODE == Settings.NavigationMode.TO_FINISH) {
                int xi = getWidth() - Paints.PAINT_SETTINGS.navigationStatsOffsetFromRightEdge();
                printNavigationStatsPre(canvas, xi, getHeight() - Paints.FONT_SIZE_NAVIGATION_STATS*2 - 12, "ETA ", formatTime(navigator.getETA()));
                printNavigationStatsPre(canvas, xi, getHeight() - Paints.FONT_SIZE_NAVIGATION_STATS*1 - 12, "left ", formatSeconds(navigator.getRemainingTime()));
                printNavigationStats(canvas, xi, getHeight() - Paints.FONT_SIZE_NAVIGATION_STATS*0 - 12, String.format("%.1f", (navigator.getTotalDistance() - navigator.getDistanceTraveled()) / 1000f), " km");
            } else if (Settings.NAVIGATION_MODE == Settings.NavigationMode.POI_BY_POI_SORMLANDSLEDEN) {
                int yi = getHeight() - Paints.FONT_SIZE_NAVIGATION_STATS * 7 - 12;
                for (Navigator.NavigationPOI poi : navigator.getNextPOIsSormlandsleden()) {
                    String etaStr = TIME_FORMAT_HH_MM.format(poi.eta);
                    float xPos = getWidth() - 8 - Paints.FONT_NAVIGATION_STATS.measureText(etaStr);
                    print(canvas, etaStr, xPos, yi, Paints.FONT_NAVIGATION_STATS, Paints.FONT_OUTLINE_NAVIGATION_STATS);

                    String kmSubscript = " km ";
                    xPos -= Paints.FONT_OUTLINE_NAVIGATION_SUBSCRIPT.measureText(kmSubscript);
                    print(canvas, kmSubscript, xPos, yi, Paints.FONT_NAVIGATION_SUBSCRIPT, Paints.FONT_OUTLINE_NAVIGATION_SUBSCRIPT);

                    String kmStr = formatDistanceKms(poi.distance);
                    xPos -= Paints.FONT_NAVIGATION_STATS.measureText(kmStr);
                    print(canvas, kmStr, xPos, yi, Paints.FONT_NAVIGATION_STATS, Paints.FONT_OUTLINE_NAVIGATION_STATS);

                    String subscript = poi.label + " ";
                    xPos -= Paints.FONT_OUTLINE_NAVIGATION_SUBSCRIPT.measureText(subscript);
                    print(canvas, subscript, xPos, yi, Paints.FONT_NAVIGATION_SUBSCRIPT, Paints.FONT_OUTLINE_NAVIGATION_SUBSCRIPT);

                    yi += Paints.FONT_SIZE_NAVIGATION_STATS;
                }
            } else {
                throw new IllegalStateException("Unknown navigation mode: " + Settings.NAVIGATION_MODE);
            }
        }
    }

    private void printCentered(Canvas canvas, String text, int y) {
        float wid = Paints.FONT_OUTLINE_GPS_STATS.measureText(text) * 0.5f;
        print(canvas, text, (float)screenMidX-wid, y, Paints.FONT_GPS_STATS, Paints.FONT_OUTLINE_GPS_STATS);
    }

    private void printNavigationStats(Canvas canvas, int x, int y, String text, String subscript) {
        float wid = Paints.FONT_OUTLINE_NAVIGATION_STATS.measureText(text);
        print(canvas, text, x, y, Paints.FONT_NAVIGATION_STATS, Paints.FONT_OUTLINE_NAVIGATION_STATS);
        print(canvas, subscript, x + wid, y, Paints.FONT_NAVIGATION_SUBSCRIPT, Paints.FONT_OUTLINE_NAVIGATION_SUBSCRIPT);
    }

    private void printNavigationStatsPre(Canvas canvas, int x, int y, String subscript, String text) {
        float wid = Paints.FONT_OUTLINE_NAVIGATION_SUBSCRIPT.measureText(subscript);
        print(canvas, subscript, x - wid, y, Paints.FONT_NAVIGATION_SUBSCRIPT, Paints.FONT_OUTLINE_NAVIGATION_SUBSCRIPT);
        print(canvas, text, x, y, Paints.FONT_NAVIGATION_STATS, Paints.FONT_OUTLINE_NAVIGATION_STATS);
    }

    private void print(Canvas canvas, String text, float x, float y, Paint font, Paint outline) {
        canvas.drawText(text, x, y, outline);
        canvas.drawText(text, x, y, font);
    }

    private static final DateFormat TIME_FORMAT_HH_MM_SS = new SimpleDateFormat("HH:mm:ss");

    private static final DateFormat TIME_FORMAT_HH_MM = new SimpleDateFormat("HH:mm");

    public static String formatTime(Date time) {
        return TIME_FORMAT_HH_MM_SS.format(time);
    }

    public static String formatSeconds(int seconds) {
        int hrs = seconds / 3600;
        return hrs > 0 ?
                String.format("%d:%02d:%02d", hrs, seconds / 60 - hrs*60, seconds % 60) :
                String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    public static String formatDistanceKms(int meters) {
        return String.format(meters < 9950 ? "%.1f" : "%.0f", meters * 0.001);
    }

    private Rect srcRect = new Rect(1, 1, -1, -1); // left/top will always be 1 (for the 1px border), others filled in later
    private Rect dstRect = new Rect(-1, -1, -1, -1);

    private void copyTile(Canvas canvas, Bitmap src, float posX, float posY) {
        float size = (float)(TILE_WIDTH_PIXELS * scalingZoom);

        // cut away the 1 px border, it's only needed for the borders to look smooth when filtering
        srcRect.right = src.getWidth() - 1;
        srcRect.bottom = src.getHeight() - 1;

        // note: need to use int rectangle here, since float will result in glitches between tiles
        dstRect.left = (int)(posX + 0.5);
        dstRect.top = (int)(posY + 0.5);
        dstRect.right = (int)(posX + size + 0.5);
        dstRect.bottom = (int)(posY + size + 0.5);

        canvas.drawBitmap(src, srcRect, dstRect, Paints.FILTER_BITMAP);
    }
///////////////////////////////////////////////////////////////////////////////////////////////////

    /** After user has manually moved around screen position, wait this nr of ms until
     * resuming auto position update. */
    public static int DELAY_AFTER_USER_MOVE_MS = 5000;

    /** uptimemillis for the last time the user issued a request to move screen position */
    private long lastUserMoveMs = 0;

    enum ActionMode { NONE, PAN, ZOOM }

    private ScaleGestureDetector zoomDetector;
    private ActionMode actionMode = ActionMode.NONE;
    private float panPrevX, panPrevY;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (config.touchLocationService.value)
            if (handleTouchLocationUpdate(event))
                return true;

        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                panPrevX = event.getX();
                panPrevY = event.getY();
                actionMode = ActionMode.PAN;
                break;
            case MotionEvent.ACTION_MOVE:
                if (actionMode == ActionMode.PAN) {
                    double dx = event.getX() - panPrevX;
                    double dy = event.getY() - panPrevY;
                    centerUtmX -= pixelToUtm(dx);
                    centerUtmY += pixelToUtm(dy);
                    panPrevX = event.getX();
                    panPrevY = event.getY();

                    mapCenterUpdated(true);

                    invalidate();
                }
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                actionMode = ActionMode.ZOOM;
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                actionMode = ActionMode.NONE;
                break;
        }

        if (actionMode == ActionMode.ZOOM)
            zoomDetector.onTouchEvent(event);

        return true;
    }

    /** uptimemillis for the last time the user updated the gps location by touch (when enabled) */
    private long lastUserMoveToTouchMs = 0;

    /**
     * Translate the touch event into a GPS location update. (Used for testing.)
     * @return Whether the event was processed.
     */
    private boolean handleTouchLocationUpdate(MotionEvent event) {
        double px = event.getX() - screenMidX;
        double py = event.getY() - screenMidY;
        double newGpsX = centerUtmX + pixelToUtm(px);
        double newGpsY = centerUtmY - pixelToUtm(py);
        double dx = gpsX - newGpsX, dy = gpsY - newGpsY;
        double pdx = utmToPixel(dx), pdy = utmToPixel(dy);

        // Only pick up touch events near the current position. For other touch events,
        // fall back to regular pan/zoom.
        if (Math.abs(pdx) > 50 || Math.abs(pdy) > 50)
            return false;

        if (pdx != 0 || pdy != 0) {
            setGPSBearing((float) (Math.atan2(-pdy, pdx) / Math.PI * 180)-90);
            setGPSCoordinate(newGpsX, newGpsY);

            float dist = (float)Math.sqrt(dx*dx + dy*dy);
            long curTime = SystemClock.uptimeMillis();
            long elapsed = curTime - lastUserMoveToTouchMs;
            setGPSSpeed(dist * 1000 / elapsed);
            lastUserMoveToTouchMs = curTime;

            invalidate();
        }
        return true;
    }

    /** Ensure map center is valid and for example not scrolled outside map extreme borders. */
    private void mapCenterUpdated(boolean userIssued) {
        double utmMidX = pixelToUtm(screenMidX);
        double utmMidY = pixelToUtm(screenMidY);
        centerUtmX = Math.max(MapConstants.UTM_EXTREME_X0 + utmMidX, Math.min(MapConstants.UTM_EXTREME_X1 - utmMidX, centerUtmX));
        centerUtmY = Math.max(MapConstants.UTM_EXTREME_Y0 + utmMidY, Math.min(MapConstants.UTM_EXTREME_Y1 - utmMidY, centerUtmY));

        if (userIssued)
            lastUserMoveMs = SystemClock.uptimeMillis();
    }

    /** Ensure scale factor is valid and update all scale factor related variables. */
    private void scaleFactorUpdated() {
        scaleFactor = Math.max(minZoomFitsOnScreen, Math.min(MAX_SCALE, scaleFactor));

        zoomLevel = 31 - Integer.numberOfLeadingZeros((int)(scaleFactor / Paints.PAINT_SETTINGS.tileScaleFactor() + (1e-12)));
        zoomLevel = Math.max(MIN_ZOOM_LEVEL, Math.min(MAX_ZOOM_LEVEL, zoomLevel));
        scalingZoom = scaleFactor / (1 << zoomLevel);
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        double prevFocusX, prevFocusY;

        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            prevFocusX = detector.getFocusX();
            prevFocusY = detector.getFocusY();
            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            double oldScaleFactor = scaleFactor;

            scaleFactor *= detector.getScaleFactor();
            scaleFactorUpdated();

            // translate due to focus point moving and zoom due to pinch
            double focusX = detector.getFocusX(), focusY = detector.getFocusY();
            double omScale = 1 - scaleFactor / oldScaleFactor;
            centerUtmX += pixelToUtm((screenMidX - focusX) * omScale - focusX + prevFocusX);
            centerUtmY -= pixelToUtm((screenMidY - focusY) * omScale - focusY + prevFocusY);

            mapCenterUpdated(true);

            prevFocusX = focusX;
            prevFocusY = focusY;

            invalidate();

            return true;
        }
    }
}