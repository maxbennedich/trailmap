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
import com.max.config.Config;
import com.max.main.R;
import com.max.route.PointOfInterest;
import com.max.route.QuadPoint;
import com.max.route.QuadNode;

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

    public List<QuadPoint> points;
    public QuadNode quadRoot;

    public List<PointOfInterest> pointsOfInterest;

    private Bitmap emptyTile, gpsIcon, scale;

    private LinkedHashMap<Integer, Tile> tileCache = getTileCache();

    private static final int MIN_ZOOM_LEVEL = 0;
    private static final int MAX_ZOOM_LEVEL = 10;

    private static final double MIN_SCALE = 1 << MIN_ZOOM_LEVEL;
    private static final double MAX_SCALE = (1 << MAX_ZOOM_LEVEL) << 3;
    private double scaleFactor = 70;

    public int zoomLevel = (int)log2(scaleFactor);

    /** Amount of "digital" zoom on top of integer tile level zoom, i.e. scaleFactor/2^zoomLevel. */
    private double scalingZoom = scaleFactor / (1<<zoomLevel);

    private static final int ZOOM_LEVEL_SHOW_LABELS = 7;

    /** 100 corresponds to ~26 mb image data (256x256 pixels, 4 bytes per pixel) */
    private static final int TILE_CACHE_SIZE = 100;

    /** Contains all tile indices for which we have a tile on disk. */
    private Set<Integer> existingTiles = new HashSet<>();

    private double centerUtmX = 669_715, centerUtmY = 6_583_611; // solna
//    private double centerUtmX = 712_650, centerUtmY = 6_370_272; // gotland
    private double gpsX = centerUtmX+100, gpsY = centerUtmY;
    private float gpsBearing;

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

        zoomDetector = new ScaleGestureDetector(getContext(), new ScaleListener());

        loadBitmaps();

        inventoryTiles();

        Log.d("AccuMap", "Initialized renderer");
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
                InputStream is = getContext().getAssets().open(tileName);
                map = BitmapFactory.decodeStream(is, null, NO_SCALING);
                is.close();
            } catch (IOException e) {
                throw new IllegalStateException("Error loading asset "+tileName, e);
            }

            Tile tile = new Tile(zoom, tx, ty, map);
            Canvas canvas = new Canvas(tile.map);

            if (config.mapBrightness.value != 100) {
                // dim tile
                Paint p = Paints.DIM_SCREEN;
                p.setAlpha(255 - (255 * config.mapBrightness.value / 100));
                canvas.drawRect(0, 0, getWidth(), getHeight(), p);
            }

            if (config.showRoute.value)
                drawPath(canvas, tile);
            if (config.showGpsTrace.value)
                drawHistory(canvas, tile);
            if (config.showPointsOfInterest.value)
                drawPointsOfInterest(canvas, tile);

            return tile;
        }
        return null;
    }

    final float utmToTilePixelX(int utmx, int utmx0, int tileSize) {
        return (float)(utmx - utmx0)*256/tileSize;
    }

    float utmToTilePixelY(int utmy, int utmy0, int tileSize) {
        return (float)(utmy0 + tileSize-1 - utmy)*256/tileSize;
    }

    public void invalidateTileCache(boolean invalidateView) {
        tileCache.clear();
        if (invalidateView)
            invalidate();
    }

    QuadMatches matches = new QuadMatches();

    private void drawPath(Canvas canvas, Tile tile) {
        // TODO try arcs instead of lines

        // calculate utm coordinates for tile corners
        int tileSize = 1<<(20-tile.zoomLevel);
        int utx0 = tile.tx * tileSize - 1_200_000;
        int uty0 = 8_500_000 - tile.ty * tileSize - tileSize; // TODO check the math

        // find route points visible on tile by querying quad tree
        matches.clear();
        int pathWidthOffset = Paints.PATH_WIDTH * tileSize / 256;
        int queryUtx0 = utx0 - pathWidthOffset/2;
        int queryUty0 = uty0 - pathWidthOffset/2;
        byte queryLevel = (byte)(16-log2(scaleFactor)*1.5);
        queryLevel = (byte)Math.min(10, Math.max(0, queryLevel));
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

    private void drawHistory(Canvas canvas, Tile tile) {
        // calculate utm coordinates for tile corners
        int tileSize = 1<<(20-tile.zoomLevel);
        int utx0 = tile.tx * tileSize - 1_200_000;
        int uty0 = 8_500_000 - tile.ty * tileSize - tileSize; // TODO check the math

        // TODO insert into quad tree
        for (int k = 0; k < historyIdx; ++k) {
            float x = utmToTilePixelX(historyUtmX[k], utx0, tileSize);
            float y = utmToTilePixelY(historyUtmY[k], uty0, tileSize);
            if (x >= -Paints.HISTORY_WIDTH/2 && x < tileSize+Paints.HISTORY_WIDTH/2 && y >= -Paints.HISTORY_WIDTH/2 && y < tileSize+Paints.HISTORY_WIDTH/2) {
                canvas.drawPoint(x, y, Paints.HISTORY_PATH);
            }
        }
    }

    private void drawPointsOfInterest(Canvas canvas, Tile tile) {
        // calculate utm coordinates for tile corners
        int tileSize = 1<<(20-tile.zoomLevel);
        int utx0 = tile.tx * tileSize - 1_200_000;
        int uty0 = 8_500_000 - tile.ty * tileSize - tileSize; // TODO check the math

        for (int k = 0; k < pointsOfInterest.size(); ++k) {
            PointOfInterest poi = pointsOfInterest.get(k);
            float x = utmToTilePixelX(poi.utmX, utx0, tileSize);
            float y = utmToTilePixelY(poi.utmY, uty0, tileSize);
            if (x >= -Paints.POINT_OF_INTEREST_SIZE/2 && x < tileSize+Paints.POINT_OF_INTEREST_SIZE/2 && y >= -Paints.POINT_OF_INTEREST_SIZE/2 && y < tileSize+Paints.POINT_OF_INTEREST_SIZE/2) {
                canvas.drawPoint(x, y, Paints.POINT_OF_INTEREST_OUTLINE);
                canvas.drawPoint(x, y, Paints.POINT_OF_INTEREST);
            }

            if (tile.zoomLevel >= ZOOM_LEVEL_SHOW_LABELS) {
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

    final int pixelToUtm(double pixel) {
        return (int)(pixel*(1<<(20-zoomLevel-8)) / scalingZoom + 0.5); // 8 since tile is 256 pixels wide
    }

    final double utmToPixel(double utm) {
        return utm/(1<<(20-zoomLevel-8)) * scalingZoom; // 8 since tile is 256 pixels wide
    }

    final double utmToScreenX(int utmx) {
        return getWidth()/2-1 + utmToPixel(utmx - centerUtmX);
    }

    final double utmToScreenY(int utmy) {
        return getHeight()/2-1 - utmToPixel(utmy-centerUtmY);
    }

    private static final int MAX_HISTORY_POINTS = 1000;
    private int[] historyUtmX = new int[MAX_HISTORY_POINTS];
    private int[] historyUtmY = new int[MAX_HISTORY_POINTS];
    private int historyIdx = 0;

    public void setGPSCoordinate(double utmX, double utmY) {
        gpsX = utmX;
        gpsY = utmY;

        if (config.showGpsTrace.value) {
            int utmIX = historyUtmX[historyIdx] = (int) (utmX + 0.5);
            int utmIY = historyUtmY[historyIdx] = (int) (utmY + 0.5);
            historyIdx = (historyIdx+1) % MAX_HISTORY_POINTS;

            // mark point on tile
            int tileSizeUtm = 1<<(20-zoomLevel);
            int tx = (1_200_000 + utmIX - (utmIX < -1_200_000 ? tileSizeUtm-1 : 0)) / tileSizeUtm;
            int ty = (8_500_000 - utmIY - (utmIY > 8_500_000 ? tileSizeUtm-1 : 0)) / tileSizeUtm;
            Tile tile = tileCache.get(getTilePos(zoomLevel, tx, ty));
            if (tile != null) {
                int tileUtmX = tx * tileSizeUtm - 1_200_000;
                int tileUtmY = 8_500_000 - ty * tileSizeUtm;
                float tilePixelX = utmToTilePixelX(utmIX, tileUtmX, tileSizeUtm);
                float tilePixelY = utmToTilePixelY(utmIY, tileUtmY, tileSizeUtm);

                Canvas canvas = new Canvas(tile.map);
                canvas.drawPoint(tilePixelX, tilePixelY, Paints.HISTORY_PATH);
                Log.d("AccuMap", "Setting tile pixel "+tilePixelX+","+tilePixelY+" for tile "+tx+","+ty);
            }
        }
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
        int utm0x = (int)Math.floor(centerUtmX-pixelToUtm(getWidth()/2-1));
        int utm0y = (int)Math.floor(centerUtmY-pixelToUtm(getHeight()/2-1));
        int utm1x = (int)Math.ceil(centerUtmX+pixelToUtm(getWidth()/2));
        int utm1y = (int)Math.ceil(centerUtmY+pixelToUtm(getHeight()/2));

        // convert utm coordinates to tile indices
        int tileSizeUtm = 1<<(20-zoomLevel);

        // the ternaries below are needed to round negative as well as positive numbers down
        int tx0 = (1_200_000 + utm0x - (utm0x < -1_200_000 ? tileSizeUtm-1 : 0)) / tileSizeUtm;
        int ty0 = (8_500_000 - utm1y - (utm1y > 8_500_000 ? tileSizeUtm-1 : 0)) / tileSizeUtm;
        int tx1 = (1_200_000 + utm1x - (utm1x < -1_200_000 ? tileSizeUtm-1 : 0)) / tileSizeUtm;
        int ty1 = (8_500_000 - utm0y - (utm0y > 8_500_000 ? tileSizeUtm-1 : 0)) / tileSizeUtm;

        for (int ty = ty0; ty <= ty1; ++ty) {
            int tileUtmY = 8_500_000 - ty * tileSizeUtm;
            double tileScreenY = utmToScreenY(tileUtmY);

            for (int tx = tx0; tx <= tx1; ++tx) {
                int tileUtmX = tx * tileSizeUtm - 1_200_000;
                double tileScreenX = utmToScreenX(tileUtmX);

                Tile tile = tileCache.get(getTilePos(zoomLevel, tx, ty));
                if (tile != null) {
                    Bitmap tileImg = tile == null ? emptyTile : tile.map;
                    copyImage(canvas, tileImg, tileScreenX, tileScreenY);
                }
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

    private static double log2(double d) {
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
        Rect dstRect = new Rect((int)(posX+0.5), (int)(posY+0.5), (int)(posX+dw+0.5), (int)(posY+dh+0.5));
        canvas.drawBitmap(src, null, dstRect, null);
    }
///////////////////////////////////////////////////////////////////////////////////////////////////

    enum ActionMode { NONE, PAN, ZOOM }

    private ScaleGestureDetector zoomDetector;
    private ActionMode actionMode = ActionMode.NONE;
    private double panStartX, panStartY;

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

            // translate due to focus point moving and zoom due to pinch
            double focusX = detector.getFocusX(), focusY = detector.getFocusY();
            double omScale = 1 - scaleFactor / oldScaleFactor;
            centerUtmX += pixelToUtm((getWidth()/2-1 - focusX) * omScale - focusX + prevFocusX);
            centerUtmY -= pixelToUtm((getHeight()/2-1 - focusY) * omScale - focusY + prevFocusY);

            prevFocusX = focusX;
            prevFocusY = focusY;

            invalidate();

            return true;
        }
    }
}