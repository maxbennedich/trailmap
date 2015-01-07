package com.max.drawing;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import com.max.logic.Tile;
import com.max.config.Config;
import com.max.main.LogStats;
import com.max.main.R;
import com.max.route.PathConfiguration;
import com.max.route.PathLevelOfDetail;
import com.max.route.PathType;
import com.max.route.PointOfInterest;
import com.max.route.QuadNode;
import com.max.route.QuadPointArray;

import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Renderer extends View {

    public Config config;

    public QuadPointArray points;
    public QuadNode quadRoot;

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
    private double scaleFactor = 70;

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

    private double centerUtmX = 669_715, centerUtmY = 6_583_611; // solna
//    private double centerUtmX = 712_650, centerUtmY = 6_370_272; // gotland
    private double gpsX = centerUtmX+100, gpsY = centerUtmY;
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

            @Override protected boolean removeEldestEntry(Entry<Integer, Tile> eldest) {
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

        loadBitmaps();

        inventoryTiles();

        timer.log("Initialized renderer");
    }

    private static final BitmapFactory.Options NO_SCALING = new BitmapFactory.Options();
    static {
        NO_SCALING.inScaled = false; // to keep original tile size
        NO_SCALING.inMutable = true; // to enable drawing on top of loaded tiles
    }

    private void loadBitmaps() {
        gpsIcon = BitmapFactory.decodeResource(getResources(), R.drawable.gps_arrow, NO_SCALING);
        scale = BitmapFactory.decodeResource(getResources(), R.drawable.scale, NO_SCALING);
        emptyTile = BitmapFactory.decodeResource(getResources(), R.drawable.empty, NO_SCALING);

        layerBitmap = Bitmap.createBitmap(TILE_WIDTH_PIXELS, TILE_WIDTH_PIXELS, Bitmap.Config.ARGB_8888);
        layerBitmap.setDensity(emptyTile.getDensity());
        layerCanvas = new Canvas(layerBitmap);
    }

    /** Populate the structure of available tiles. */
    private void inventoryTiles() {
        Pattern p = Pattern.compile("tile_(\\d{1,2})_(\\d+)_(\\d+).png");

        AssetManager assMan = getContext().getAssets();
        String[] assets;
        try {
            assets = assMan.list("tiles");
        } catch (IOException e) {
            throw new IllegalStateException("Error loading assets", e);
        }

        for (String asset : assets) {
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

            String tileName = "tile_"+zoom+"_"+tx+"_"+ty+".png";
            Log.d("AccuMap", "Loading " + tileName);
            Bitmap map;
            try {
                InputStream is = getContext().getAssets().open("tiles/" + tileName);
                map = BitmapFactory.decodeStream(is, null, NO_SCALING);
                is.close();
            } catch (IOException e) {
                throw new IllegalStateException("Error loading asset "+tileName, e);
            }

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
                tile.canvas.drawBitmap(layerBitmap, 0, 0, null);
            }

            // do any empty tile overwriting at the end since we don't want layers on top of it
            fixTileIfPartiallyEmpty(tile);

            return tile;
        }
        return null;
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
        // calculate utm coordinates for tile corners
        int tileSizeBits = ZOOM_0_TILE_BITS - tile.zoomLevel;
        int tileSizeUtm = 1 << tileSizeBits;
        int utx0 = (tile.tx << tileSizeBits) - 1_200_000;
        int uty0 = 8_500_000 - (tile.ty+1 << tileSizeBits);

        for (int k = 0; k < pointsOfInterest.size(); ++k) {
            PointOfInterest poi = pointsOfInterest.get(k);
            float x = utmToTilePixelX(poi.utmX, utx0, tileSizeUtm);
            float y = utmToTilePixelY(poi.utmY, uty0, tileSizeUtm);
            if (x >= -Paints.POINT_OF_INTEREST_SIZE/2 && x < tileSizeUtm+Paints.POINT_OF_INTEREST_SIZE/2 && y >= -Paints.POINT_OF_INTEREST_SIZE/2 && y < tileSizeUtm+Paints.POINT_OF_INTEREST_SIZE/2) {
                canvas.drawPoint(x, y, Paints.POINT_OF_INTEREST_OUTLINE);
                canvas.drawPoint(x, y, Paints.POINT_OF_INTEREST);
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
            new PathLevelOfDetail(new int[] {11,10,9,8,7,7,6,5,4,3,0}), true, Paints.PATH_WIDTH);

    // level of detail configs below have been empirically tested to be visibly acceptable
    private static final PathConfiguration GPS_PATH = new PathConfiguration(
            new PathLevelOfDetail(new int[] {10,9,8,7,6,5,4,3,3,2,0}), false, Paints.HISTORY_WIDTH);

    /**
     * Minimum distance (squared), in meters, between two consecutive GPS history points.
     * This is to not record too many points with unnecessary precision.
     */
    private static final int MIN_HISTORY_POINT_DIST2 = 20*20;

    private final QuadPointArray historyPoints = new QuadPointArray(1024);
    private final QuadNode historyQuadTree = new QuadNode(MapConstants.UTM_EXTREME_X0, MapConstants.UTM_EXTREME_Y0,
            MapConstants.UTM_EXTREME_X1, MapConstants.UTM_EXTREME_Y1);

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
    }

    public void setGPSBearing(float bearing) {
        gpsBearing = bearing;
    }

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
    private static final int SCALE_MARKER_WIDTH = 256;

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

        Rect dstRect = new Rect(getWidth()-36-scaledWidth, getHeight()-29, getWidth()-36, getHeight()-29+scale.getHeight());
        canvas.drawBitmap(scale, null, dstRect, null);

        int labelIdx = k + exp*3;
        float textWidth = Paints.FONT_OUTLINE_SCALE.measureText(SCALE_LABELS[labelIdx]);
        canvas.drawText(SCALE_LABELS[labelIdx], getWidth()-textWidth-8, getHeight()-36, Paints.FONT_OUTLINE_SCALE);
        canvas.drawText(SCALE_LABELS[labelIdx], getWidth()-textWidth-8, getHeight()-36, Paints.FONT_SCALE);

        canvas.drawText(SCALE_LABELS[0], getWidth()-36-scaledWidth-3, getHeight()-36, Paints.FONT_OUTLINE_SCALE);
        canvas.drawText(SCALE_LABELS[0], getWidth()-36-scaledWidth-3, getHeight()-36, Paints.FONT_SCALE);
    }

    private Matrix matrix = new Matrix(); // to not have to constantly reallocate

    private void drawGPSMarker(Canvas canvas) {
        double x = utmToScreenX(gpsX);
        double y = utmToScreenY(gpsY);
        matrix.reset();
        matrix.postRotate(gpsBearing, gpsIcon.getWidth()/2, gpsIcon.getHeight()/2);
        matrix.postTranslate((float)(x-gpsIcon.getWidth()/2), (float)(y-gpsIcon.getHeight()/2));
        canvas.drawBitmap(gpsIcon, matrix, null);
    }

    private void drawGPSStats(Canvas canvas) {
        String txt = (int)(gpsSpeed * 3.6 + 0.5) + " km/h   "; // 3.6 for m/s -> km/h
        if (gpsDist < 500) txt += (int)(gpsDist + 0.5) + " m";
        else txt += String.format("%.2f km", gpsDist *0.001);
        float wid = Paints.FONT_OUTLINE_GPS_STATS.measureText(txt) * 0.5f;
        canvas.drawText(txt, (float)screenMidX-wid, Paints.FONT_SIZE_GPS_STATS, Paints.FONT_OUTLINE_GPS_STATS);
        canvas.drawText(txt, (float)screenMidX-wid, Paints.FONT_SIZE_GPS_STATS, Paints.FONT_GPS_STATS);

    }

    private void copyTile(Canvas canvas, Bitmap src, float posX, float posY) {
        float size = (float)(TILE_WIDTH_PIXELS * scalingZoom);
        // note: need to use int rectangle here, since float will result in glitches between tiles
        Rect dstRect = new Rect((int)(posX + 0.5), (int)(posY + 0.5), (int)(posX + size + 0.5), (int)(posY + size + 0.5));
        canvas.drawBitmap(src, null, dstRect, null);
    }
///////////////////////////////////////////////////////////////////////////////////////////////////

    enum ActionMode { NONE, PAN, ZOOM }

    private ScaleGestureDetector zoomDetector;
    private ActionMode actionMode = ActionMode.NONE;
    private float panPrevX, panPrevY;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
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

                    validateMapCenter();

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

    /** Ensure map center is valid and for example not scrolled outside map extreme borders. */
    private void validateMapCenter() {
        double utmMidX = pixelToUtm(screenMidX);
        double utmMidY = pixelToUtm(screenMidY);
        centerUtmX = Math.max(MapConstants.UTM_EXTREME_X0 + utmMidX, Math.min(MapConstants.UTM_EXTREME_X1 - utmMidX, centerUtmX));
        centerUtmY = Math.max(MapConstants.UTM_EXTREME_Y0 + utmMidY, Math.min(MapConstants.UTM_EXTREME_Y1 - utmMidY, centerUtmY));
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

            scaleFactor = Math.max(minZoomFitsOnScreen, Math.min(MAX_SCALE, scaleFactor * detector.getScaleFactor()));

            zoomLevel = 31 - Integer.numberOfLeadingZeros((int)(scaleFactor+(1e-12)));
            zoomLevel = Math.max(MIN_ZOOM_LEVEL, Math.min(MAX_ZOOM_LEVEL, zoomLevel));
            scalingZoom = scaleFactor / (1 << zoomLevel);

            // translate due to focus point moving and zoom due to pinch
            double focusX = detector.getFocusX(), focusY = detector.getFocusY();
            double omScale = 1 - scaleFactor / oldScaleFactor;
            centerUtmX += pixelToUtm((screenMidX - focusX) * omScale - focusX + prevFocusX);
            centerUtmY -= pixelToUtm((screenMidY - focusY) * omScale - focusY + prevFocusY);

            validateMapCenter();

            prevFocusX = focusX;
            prevFocusY = focusY;

            invalidate();

            return true;
        }
    }
}