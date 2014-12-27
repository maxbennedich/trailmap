package com.max.drawing;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import com.max.logic.Tile;
import com.max.logic.TilePos;
import com.max.logic.TileRectangle;
import com.max.main.R;
import com.max.route.PointOfInterest;
import com.max.route.QuadPoint;
import com.max.route.QuadNode;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Renderer extends View {

    public List<QuadPoint> points;
    public QuadNode quadRoot;

    public List<PointOfInterest> pointsOfInterest;

    private Bitmap emptyTile;

    private LinkedHashMap<TilePos, Tile> tileCache = getTileCache();

    private static final int MIN_ZOOM_LEVEL = 0;
    private static final int MAX_ZOOM_LEVEL = 10;

    private static final double MIN_SCALE = 1 << MIN_ZOOM_LEVEL;
    private static final double MAX_SCALE = (1 << MAX_ZOOM_LEVEL) << 3;
    private double scaleFactor = 70;

    public int zoomLevel = (int)(Math.log(scaleFactor) / Math.log(2));

    /** Amount of "digital" zoom on top of integer tile level zoom, i.e. scaleFactor/2^zoomLevel. */
    private double scalingZoom = scaleFactor / (1<<zoomLevel);

    private static final int ZOOM_LEVEL_SHOW_LABELS = 7;

    /** 100 corresponds to ~26 mb image data (256x256 pixels, 4 bytes per pixel) */
    private static final int TILE_CACHE_SIZE = 100;

    /** Contains all tile positions/resource ids for which we have a tile (on disk), for each zoom level. */
    private Map<Integer, Set<TilePos>> existingTilesByZoomLevel = new HashMap<>();

//    private XYd centerUtm = new XYd(669_715, 6_583_611);
    private double centerUtmX = 712_650, centerUtmY = 6_370_272;
    private double gpsX = centerUtmX+100, gpsY = centerUtmY;
    private float gpsBearing;

    private static final int ROUTE_PIXEL_OFFSET_X = -1;
    private static final int ROUTE_PIXEL_OFFSET_Y = -1;

    private LinkedHashMap<TilePos, Tile> getTileCache() {
        return new LinkedHashMap<TilePos, Tile>(TILE_CACHE_SIZE, 0.75f, true) {
            private static final long serialVersionUID = 1L;

            /**
             * Override get to load tiles not in the cache and insert them into the cache.
             *
             * @return Null if image could not be loaded (typically out of bounds).
             */
            @Override public Tile get(Object key) {
                Tile tile = super.get(key);
                if (tile != null || !(key instanceof TilePos))
                    return tile;

                TilePos tp = (TilePos) key;
                if ((tile = loadTile(zoomLevel, tp)) != null)
                    put(tp, tile);
                return tile;
            }

            @Override protected boolean removeEldestEntry(Entry<TilePos, Tile> eldest) {
                return size() > TILE_CACHE_SIZE;
            }
        };
    }

    private static final BitmapFactory.Options NO_SCALING = new BitmapFactory.Options();
    static {
        NO_SCALING.inScaled = false;
        NO_SCALING.inMutable = true;
    }

    Bitmap gpsIcon, scale;
    Rect scaleRect;

    public void loadBitmaps() {
        gpsIcon = BitmapFactory.decodeResource(getResources(), R.drawable.gps_arrow, NO_SCALING);
        scale = BitmapFactory.decodeResource(getResources(), R.drawable.scale, NO_SCALING);
        scaleRect = new Rect(0, 0, scale.getWidth(), scale.getHeight());

        inventoryTiles();
    }

    /** Populate the structure of available tiles. */
    private void inventoryTiles() {
        Pattern p = Pattern.compile("tile_(\\d{1,2})_(\\d+)_(\\d+).png");

        AssetManager assMan = getContext().getAssets();
        String[] assets;
        try {
            assets = assMan.list("");
        } catch (IOException e) {
            throw new IllegalStateException("Error loading assets", e);
        }

        for (String asset : assets) {
            Matcher m = p.matcher(asset);
            if (m.find()) {
                int zoomLevel = Integer.valueOf(m.group(1));
                int tx = Integer.valueOf(m.group(2));
                int ty = Integer.valueOf(m.group(3));
                Set<TilePos> tileSet = existingTilesByZoomLevel.get(zoomLevel);
                if (tileSet == null)
                    existingTilesByZoomLevel.put(zoomLevel, tileSet = new HashSet<TilePos>());
                tileSet.add(new TilePos(zoomLevel, tx, ty));
            }
        }
    }

    private Tile loadTile(int zoomLevel, TilePos tp) {
        // see if tile exists
        Set<TilePos> tileSet = existingTilesByZoomLevel.get(zoomLevel);
        if (tileSet != null && tileSet.contains(tp)) {
            String tileName = "tile_"+zoomLevel+"_"+tp.tx+"_"+tp.ty+".png";
            Log.d("AccuMap", "Loading " + tileName);
            Bitmap map;
            try {
                InputStream is = getContext().getAssets().open(tileName);
                map = BitmapFactory.decodeStream(is, null, NO_SCALING);
                is.close();
            } catch (IOException e) {
                throw new IllegalStateException("Error loading asset "+tileName, e);
            }

            Tile tile = new Tile(tp, map);
            Canvas canvas = new Canvas(tile.map);

            // dim tile
            Paint p = new Paint();
            p.setColor(Color.BLACK);
            p.setAlpha(63);
            p.setStrokeWidth(1);
            canvas.drawRect(0, 0, getWidth(), getHeight(), p);

            drawPath(canvas, tile);
            drawPointsOfInterest(canvas, tile);
            return tile;
        }
        return null;
    }

    float utmToTilePixelX(int utmx, int utmx0, int tileSize) {
        return (float)(utmx - utmx0)*256/tileSize;
    }

    float utmToTilePixelY(int utmy, int utmy0, int tileSize) {
        return (float)(utmy0 + tileSize-1 - utmy)*256/tileSize;
    }

    private void drawPath(Canvas canvas, Tile tile) {
        // TODO try arcs instead of lines

        // calculate utm coordinates for screen corners
        int tileSize = 1<<(20-tile.tp.zoomLevel);
        int utx0 = tile.tp.tx * tileSize - 1_200_000;
        int uty0 = 8_500_000 - tile.tp.ty * tileSize - tileSize; // TODO check the math

        // find route points visible on tile by querying quad tree
        matches.clear();
        int pathWidthOffset = Paints.PATH_WIDTH * tileSize / 256;
        int queryUtx0 = utx0 - pathWidthOffset/2;
        int queryUty0 = uty0 - pathWidthOffset/2;
        int queryLevel = (int)(16-log2(scaleFactor)*1.5);
        queryLevel = Math.min(10, Math.max(0, queryLevel));
        quadRoot.queryTree(queryLevel, queryUtx0, queryUty0, queryUtx0+tileSize+pathWidthOffset, queryUty0+tileSize+pathWidthOffset, points, matches);

        if (matches.matchCount != 0) {
            matches.sort();

            Path[] paths = new Path[2];
            paths[0] = new Path();
            paths[1] = new Path();
            int stepSize = 1 << queryLevel;

            int prevIdx = -1;
            QuadPoint p, p2 = null;
            float p2x = 0, p2y = 0;
            int p2Surf = 0;

            for (int k = 0; k < matches.matchCount; ++k) {
                int idx = matches.get(k);

                if (prevIdx == -1 || idx - prevIdx > stepSize) {
                    // just entered screen, draw partial on-screen segment
                    p = points.get(idx);
                    float px = utmToTilePixelX(p.x, utx0, tileSize);
                    float py = utmToTilePixelY(p.y, uty0, tileSize);

                    // the math below for idx=0 is to select the last point for the current query level
                    QuadPoint p0 = points.get(idx == 0 ? ((points.size() - 1) / stepSize) * stepSize : idx - stepSize);
                    float p0x = utmToTilePixelX(p0.x, utx0, tileSize);
                    float p0y = utmToTilePixelY(p0.y, uty0, tileSize);
                    paths[p0.surface.ordinal()].moveTo(p0x, p0y);
                    paths[p0.surface.ordinal()].lineTo(px, py);

                    // start next surface type segment
                    paths[p.surface.ordinal()].moveTo(px, py);
                } else {
                    // same point as previous "next"; don't calculate again; move if surface changed
                    p = p2;
                    int pSurf = p2.surface.ordinal();
                    if (pSurf != p2Surf)
                        paths[pSurf].moveTo(p2x, p2y);
                }

                // draw line to the next point (which may or may not be on screen)
                p2 = points.get(Math.min(idx + stepSize, points.size() - 1));
                p2x = utmToTilePixelX(p2.x, utx0, tileSize);
                p2y = utmToTilePixelY(p2.y, uty0, tileSize);
                p2Surf = p.surface.ordinal();
                paths[p2Surf].lineTo(p2x, p2y);

                prevIdx = idx;
            }
            canvas.drawPath(paths[0], Paints.PATH_MAJOR_ROAD);
            canvas.drawPath(paths[1], Paints.PATH_MINOR_ROAD);
        }
    }

    private void drawPointsOfInterest(Canvas canvas, Tile tile) {
        // calculate utm coordinates for screen corners
        int tileSize = 1<<(20-tile.tp.zoomLevel);
        int utx0 = tile.tp.tx * tileSize - 1_200_000;
        int uty0 = 8_500_000 - tile.tp.ty * tileSize - tileSize; // TODO check the math

        for (int k = 0; k < pointsOfInterest.size(); ++k) {
            PointOfInterest poi = pointsOfInterest.get(k);
            float x = utmToTilePixelX(poi.utmX, utx0, tileSize);
            float y = utmToTilePixelY(poi.utmY, uty0, tileSize);
            if (x >= -Paints.POINT_OF_INTEREST_SIZE/2 && x < tileSize+Paints.POINT_OF_INTEREST_SIZE/2 && y >= -Paints.POINT_OF_INTEREST_SIZE/2 && y < tileSize+Paints.POINT_OF_INTEREST_SIZE/2) {
                canvas.drawPoint(x, y, Paints.POINT_OF_INTEREST_OUTLINE);
                canvas.drawPoint(x, y, Paints.POINT_OF_INTEREST);
            }

            if (tile.tp.zoomLevel >= ZOOM_LEVEL_SHOW_LABELS) {
                float textWidth = Paints.FONT_OUTLINE_POI.measureText(poi.label);
                float textHeight = Paints.FONT_OUTLINE_POI.getTextSize(); // TODO: constant
                float tx = x+6, ty = y+6;
                if (tx >= -textWidth && tx < tileSize && ty >= -textHeight && ty < tileSize) {
                    canvas.drawText(poi.label, tx, ty, Paints.FONT_OUTLINE_POI);
                    canvas.drawText(poi.label, tx, ty, Paints.FONT_POI);
                }
            }
        }
    }

    int pixelToUtm(double pixel) {
        return (int)(pixel*(1<<(20-zoomLevel-8)) / scalingZoom + 0.5); // 8 since tile is 256 pixels wide
    }

    double utmToPixel(double utm) {
        return utm/(1<<(20-zoomLevel-8)) * scalingZoom; // 8 since tile is 256 pixels wide
    }

    double utmToScreenX(int utmx) { return getWidth()/2-1 + utmToPixel(utmx-centerUtmX); }

    double utmToScreenY(int utmy) {
        return getHeight()/2-1 - utmToPixel(utmy-centerUtmY);
    }

    double screenToUtmX(double pixelX) {
        return centerUtmX + pixelToUtm(getWidth()/2-1 - pixelX);
    }

    double screenToUtmY(double pixelY) {
        return centerUtmY - pixelToUtm(getHeight()/2-1 - pixelY); // y off by one?
    }

    public void setGPSCoordinate(double utmX, double utmY) {
        gpsX = utmX;
        gpsY = utmY;
    }

    public void setGPSBearing(float bearing) {
        gpsBearing = bearing;
    }

    long prevOnDraw = -1;

    @Override
    synchronized public void onDraw(Canvas canvas) {
//        startLog();
        long t0 = time();

        // calculate utm coordinates for screen corners
        double utm0x = centerUtmX-pixelToUtm(getWidth()/2-1);
        double utm0y = centerUtmY-pixelToUtm(getHeight()/2-1);
        double utm1x = centerUtmX+pixelToUtm(getWidth()/2);
        double utm1y = centerUtmY+pixelToUtm(getHeight()/2);

        TileRectangle tr = new TileRectangle((int)Math.floor(utm0x), (int)Math.floor(utm0y),
                (int)Math.ceil(utm1x), (int)Math.ceil(utm1y), zoomLevel);

        for (int ty = tr.ty0; ty <= tr.ty1; ++ty) {
            for (int tx = tr.tx0; tx <= tr.tx1; ++tx) {
                // translate tile back to utm coordinates, then to screen coordinates
                int utx0 = tx * tr.tileSize - 1_200_000;
                int uty0 = 8_500_000 - ty * tr.tileSize;

                double screenX = utmToScreenX(utx0);
                double screenY = utmToScreenY(uty0);

                Tile tile = tileCache.get(new TilePos(zoomLevel, tx, ty));
                Bitmap tileImg = tile == null ? emptyTile : tile.map;
                copyImage(canvas, tileImg, screenX, screenY);
            }
        }

//        log("Draw tiles");

        drawScaleMarker(canvas);
        drawGPSMarker(canvas);

//        log(String.format("Center = %.0f, %.0f, Scale = %d / %.0f", centerUtmX, centerUtmY, zoomLevel, scaleFactor));

        long time = time();
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
        lastTime = time();
    }
    void log(String event) {
        long curTime = time();
        stats.add(new Statistic(event, curTime - lastTime));
        lastTime = curTime;
    }
    long time() {
        return SystemClock.uptimeMillis();
    }

    private void drawStats(Canvas canvas) {
        for (int k = 0; k < stats.size(); ++k) {
            canvas.drawText(stats.get(k).toString(), 4, k * 20 + 20, Paints.FONT_OUTLINE_POI);
            canvas.drawText(stats.get(k).toString(), 4, k * 20 + 20, Paints.FONT_POI);
        }
    }

    private double log2(double d) {
        return Math.log(d)/Math.log(2);
    }

    private static final int MAX_QUAD_TREE_MATCHES = 4096;
    public static class QuadMatches {
        private int[] matchIdx = new int[MAX_QUAD_TREE_MATCHES];
        public int matchCount;

        public void clear() {
            matchCount = 0;
        }
        public void add(int idx) { matchIdx[matchCount++] = idx; }
        public int get(int idx) {
            return matchIdx[idx];
        }
        public void sort() { Arrays.sort(matchIdx, 0, matchCount); }
    }

    QuadMatches matches = new QuadMatches();

    /** in pixels */
    private static final int SCALE_MARKER_WIDTH = 256;

    private void drawScaleMarker(Canvas canvas) {
        int utmDist = pixelToUtm(SCALE_MARKER_WIDTH);
        int mult = 1;
        for (int u = utmDist/10; u != 0; u /= 10, mult *= 10) ;
        float d = (float)utmDist / mult;

        // find a "nice" number for the scale
        if (d <= 1.75) d = 1;
        else if (d < 3.75) d = 2.5f;
        else if (d < 7.5) d = 5;
        else { d = 1; mult *= 10; }
        int rounded = (int)(d * mult);

        double pixelLength = utmToPixel(rounded);
        int scaledWidth = (int)(pixelLength * scale.getWidth() / SCALE_MARKER_WIDTH + 0.5);

        Rect dstRect = new Rect(getWidth()-36-scaledWidth, getHeight()-29, getWidth()-36, getHeight()-29+scale.getHeight());
        canvas.drawBitmap(scale, scaleRect, dstRect, null);

        String label = rounded >= 1000 ? (rounded == 2500 ? "2.5 km" : rounded / 1000 + " km") : rounded + " m";
        float textWidth = Paints.FONT_OUTLINE_SCALE.measureText(label);
        canvas.drawText(label, getWidth()-textWidth-8, getHeight()-36, Paints.FONT_OUTLINE_SCALE);
        canvas.drawText(label, getWidth()-textWidth-8, getHeight()-36, Paints.FONT_SCALE);

        canvas.drawText("0", getWidth()-36-scaledWidth-3, getHeight()-36, Paints.FONT_OUTLINE_SCALE);
        canvas.drawText("0", getWidth()-36-scaledWidth-3, getHeight()-36, Paints.FONT_SCALE);
    }

    Matrix matrix = new Matrix(); // to not have to constantly reallocate

    private void drawGPSMarker(Canvas canvas) {
        double x = utmToScreenX((int)(gpsX+0.5));
        double y = utmToScreenY((int)(gpsY+0.5));
        matrix.reset();
        matrix.postRotate(gpsBearing, gpsIcon.getWidth()/2, gpsIcon.getHeight()/2);
        matrix.postTranslate((float)(x-gpsIcon.getWidth()/2), (float)(y-gpsIcon.getHeight()/2));
        canvas.drawBitmap(gpsIcon, matrix, null);
    }

    private void copyImage(Canvas canvas, Bitmap src, double posX, double posY) {
        int sw = src.getWidth(), sh = src.getHeight();
        double dw = sw * scalingZoom;
        double dh = sh * scalingZoom;
        Rect srcRect = new Rect(0, 0, sw, sh);
        Rect dstRect = new Rect((int)(posX+0.5), (int)(posY+0.5), (int)(posX+dw+0.5), (int)(posY+dh+0.5));
        canvas.drawBitmap(src, srcRect, dstRect, null);
    }
///////////////////////////////////////////////////////////////////////////////////////////////////

    enum ActionMode { NONE, PAN, ZOOM }

    private ScaleGestureDetector zoomDetector;
    private ActionMode actionMode = ActionMode.NONE;
    private double panStartX, panStartY;

    public Renderer(Context context, AttributeSet aSet) {
        super(context, aSet);

        zoomDetector = new ScaleGestureDetector(getContext(), new ScaleListener());

        emptyTile = BitmapFactory.decodeResource(getResources(), R.drawable.empty, NO_SCALING);

        Log.d("AccuMap", "Initialized tiles -- size "+emptyTile.getWidth()+","+emptyTile.getHeight());
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                panStartX = event.getX();
                panStartY = event.getY();
                actionMode = ActionMode.PAN;
                break;
            case MotionEvent.ACTION_MOVE:
                if (actionMode == ActionMode.PAN) {
                    double dx = event.getX() - panStartX;
                    double dy = event.getY() - panStartY;
                    centerUtmX -= pixelToUtm(dx);
                    centerUtmY += pixelToUtm(dy);
                    panStartX = event.getX();
                    panStartY = event.getY();
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
            scaleFactor = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scaleFactor * detector.getScaleFactor()));
            zoomLevel = 31 - Integer.numberOfLeadingZeros((int)(scaleFactor+(1e-12)));
            zoomLevel = Math.max(MIN_ZOOM_LEVEL, Math.min(MAX_ZOOM_LEVEL, zoomLevel));
            scalingZoom = scaleFactor / (1 << zoomLevel);

            // TODO optimize the below

            // translate due to focus point moving
            double focusDiffX = detector.getFocusX() - prevFocusX;
            double focusDiffY = detector.getFocusY() - prevFocusY;
            if (focusDiffX != 0 || focusDiffY != 0) {
                centerUtmX -= pixelToUtm(focusDiffX);
                centerUtmY += pixelToUtm(focusDiffY);
            }

            // now zoom
            double actualScale = scaleFactor / oldScaleFactor;
            double px = screenToUtmX(detector.getFocusX());
            double py = screenToUtmY(detector.getFocusY());
            centerUtmX = px-((px-centerUtmX)*actualScale);
            centerUtmY = py-((py-centerUtmY)*actualScale);

            prevFocusX = detector.getFocusX();
            prevFocusY = detector.getFocusY();

            invalidate();

            return true;
        }
    }
}