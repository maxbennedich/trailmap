package com.max.drawing;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
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
import com.max.logic.XY;
import com.max.logic.XYd;
import com.max.main.R;
import com.max.route.PointOfInterest;
import com.max.route.QuadPoint;
import com.max.route.QuadNode;
import com.max.route.RoadSurface;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
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

    /** 100 corresponds to ~26 mb image data (256x256 pixels, 4 bytes per pixel) */
    private static final int TILE_CACHE_SIZE = 100;

    /** Contains all tile positions/resource ids for which we have a tile (on disk), for each zoom level. */
    private Map<Integer, Map<TilePos, Integer>> existingTilesByZoomLevel = new HashMap<>();

//    private XYd centerUtm = new XYd(669_715, 6_583_611);
    private XYd centerUtm = new XYd(712_650, 6_370_272);
    private XYd gpsCoordinate = new XYd(centerUtm.x+100, centerUtm.y);

    private static final XY ROUTE_PIXEL_OFFSET = new XY(-1, -1);

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
    }

    Bitmap gpsIcon;

    public void loadBitmaps() {
        gpsIcon = BitmapFactory.decodeResource(getResources(), R.drawable.gps_arrow, NO_SCALING);

        inventoryTiles();
    }

    /** Populate the structure of available tiles. */
    private void inventoryTiles() {
        Pattern p = Pattern.compile("tile_(\\d{1,2})_(\\d+)_(\\d+)");

        // TODO look into assets and AssetManager
        R.drawable dr = new R.drawable();
        Class<R.drawable> c = R.drawable.class;
        Field[] fields = c.getDeclaredFields();
        for (int i = 0; i < fields.length; ++i) {
            try {
                int resourceId = fields[i].getInt(dr);
                String name = fields[i].getName();
                Log.d("AccuMap", name);
                Matcher m = p.matcher(name);
                if (m.find()) {
                    int zoomLevel = Integer.valueOf(m.group(1));
                    int tx = Integer.valueOf(m.group(2));
                    int ty = Integer.valueOf(m.group(3));
                    Map<TilePos, Integer> tileMap = existingTilesByZoomLevel.get(zoomLevel);
                    if (tileMap == null)
                        existingTilesByZoomLevel.put(zoomLevel, tileMap = new HashMap<TilePos, Integer>());
                    tileMap.put(new TilePos(zoomLevel, tx, ty), resourceId);
                }
            } catch (Exception e) {
                continue;
            }
        }
    }

    private Tile loadTile(int zoomLevel, TilePos tp) {
        // see if tile exists
        Map<TilePos, Integer> tileMap = existingTilesByZoomLevel.get(zoomLevel);
        Integer resourceId;
        if (tileMap != null && (resourceId = tileMap.get(tp)) != null) {
            Log.d("AccuMap", "Loading resource for " + tp);
            Bitmap map = BitmapFactory.decodeResource(getResources(), resourceId, NO_SCALING);
            // resource is null if not found (should not happen since id is not 0)
            if (map != null)
                return new Tile(tp, map);
        }
        return null;
    }

    XYd pixelToUtm(XYd pixel) {
        return new XYd(pixelToUtm(pixel.x), pixelToUtm(pixel.y));
    }

    int pixelToUtm(double pixel) {
        // resolution: 256px = 1024m (level 10) 2048m (level 9)
        return (int)(pixel*(1<<(20-zoomLevel-8)) / scalingZoom + 0.5); // 8 since tile is 256 pixels wide
    }

    XYd utmToPixel(XYd utmxy) {
        return new XYd(utmToPixel(utmxy.x), utmToPixel(utmxy.y));
    }

    double utmToPixel(double utm) {
        // resolution: 256px = 1024m (level 10) 2048m (level 9)
        return utm/(1<<(20-zoomLevel-8)) * scalingZoom; // 8 since tile is 256 pixels wide
    }

    XYd utmToScreen(XYd utmxy) {
        XYd mid = getScreenSize().div(2).sub(1, 1); // y off by one?
        XYd utp = utmToPixel(utmxy.sub(centerUtm));
        return new XYd(mid.x + utp.x, mid.y - utp.y);
    }

    XYd screenToUtm(XYd pixel) {
        XYd mid = getScreenSize().div(2).sub(1, 1); // y off by one?
        XYd dif = new XYd(mid.x - pixel.x, pixel.y - mid.y);
        XYd utp = pixelToUtm(dif);
        return centerUtm.add(utp);
    }

    public void setGPSCoordinate(XYd utm) {
        gpsCoordinate = utm;
    }

    public XYd getScreenSize() { return new XYd(getWidth(), getHeight()); }

    @Override
    synchronized public void onDraw(Canvas canvas) {
        XYDC = XYC = 0;
        startLog();

        // clear screen
        Paint p = new Paint();
        p.setColor(Color.BLACK);
        p.setAlpha(255);
        p.setStrokeWidth(1);
        canvas.drawRect(0, 0, getWidth(), getHeight(), p);

        log("Clear screen");

        // calculate utm coordinates for screen corners
        XYd utm0 = centerUtm.sub(pixelToUtm(getScreenSize().div(2).sub(1, 1)));
        XYd utm1 = centerUtm.add(pixelToUtm(getScreenSize().div(2)));

        TileRectangle tr = new TileRectangle((int)Math.floor(utm0.x), (int)Math.floor(utm0.y),
                (int)Math.ceil(utm1.x), (int)Math.ceil(utm1.y), zoomLevel);

        for (int ty = tr.ty0; ty <= tr.ty1; ++ty) {
            for (int tx = tr.tx0; tx <= tr.tx1; ++tx) {
                // translate tile back to utm coordinates, then to screen coordinates
                int utx0 = tx * tr.tileSize - 1_200_000;
                int uty0 = 8_500_000 - ty * tr.tileSize;

                XYd screenXY = utmToScreen(new XYd(utx0, uty0));

                Tile tile = tileCache.get(new TilePos(zoomLevel, tx, ty));
                if (tile != null) {
                    Bitmap tileImg = tile == null ? emptyTile : tile.map;
                    copyImage(canvas, tileImg, screenXY);
                }
            }
        }

        log("Draw tiles");

        drawPath(canvas);
//        drawPointsOfInterest(canvas);
//        log("Draw points of interest");
//        System.out.printf("Draw POIs: %.0f ms\n", (System.nanoTime()-time)*1e-6); time = System.nanoTime();
//        drawCrosshair();

        drawGPSMarker(canvas);

//        imagePanel.repaint();
//        System.out.printf("Repaint: %.0f ms\n", (System.nanoTime()-time)*1e-6); time = System.nanoTime();

        log(String.format("Center = %.0f, %.0f, Scale = %d / %.0f", centerUtm.x, centerUtm.y, zoomLevel, scaleFactor));
        log("XYD count = " + XYDC);
        log("XY count = " + XYC);
        // when viewing Gotland: creating up to 16k XYd objs and 2k XY objs !!
        // XYd: 24b --> 384k
        // XY: 16b --> 32k
        // Tot --> 416k every frame !!
        // 30 fps --> 12M per second !!
        drawStats(canvas);
    }

    public static int XYDC = 0;
    public static int XYC = 0;

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
        Paint paint = new Paint();
        paint.setColor(0xffffffff);
        paint.setTextSize(20);
        for (int k = 0; k < stats.size(); ++k)
            canvas.drawText(stats.get(k).toString(), 0, k*20, paint);
    }

    private double log2(double d) {
        return Math.log(d)/Math.log(2);
    }

    private static final int MAX_QUAD_TREE_MATCHES = 4096;
    public static class QuadMatches {
        public int[] matchIdx = new int[MAX_QUAD_TREE_MATCHES];
        public int matchCount;

        public void clear() { matchCount = 0; }
        public void add(int idx) { matchIdx[matchCount++] = idx; }
        public void sort() { Arrays.sort(matchIdx, 0, matchCount); }
    }

    QuadMatches matches = new QuadMatches();

    private void drawPath(Canvas canvas) {
        // calculate utm coordinates for screen corners
        XYd utm0 = centerUtm.sub(pixelToUtm(getScreenSize().div(2).sub(1, 1)));
        XYd utm1 = centerUtm.add(pixelToUtm(getScreenSize().div(2)));

        // find route points visible on screen by querying quad tree
        matches.clear();
        // scaleFactor = 1024 -> 0
        //
        int queryLevel = (int)(16-log2(scaleFactor)*1.5);
        queryLevel = Math.min(10, Math.max(0, queryLevel));
        quadRoot.queryTree(queryLevel, (int) Math.floor(utm0.x), (int) Math.floor(utm0.y), (int) Math.ceil(utm1.x), (int) Math.ceil(utm1.y), points, matches);

        log(String.format("Calculate path scale=%.0f, ql=%d, points=%d", scaleFactor, queryLevel, matches.matchCount));

        Paint paint = new Paint();
        paint.setStrokeWidth(6);
        matches.sort();
        int lines = 0;
        for (int k = 0; k < matches.matchCount; ++k) {
            int idx = matches.matchIdx[k];
            QuadPoint p = points.get(idx);
//            paint.setColor(p.surface == RoadSurface.DIRT ? 0x6fff5f00 : 0x6fff0000);
            paint.setColor(p.surface == RoadSurface.DIRT ? 0xffff5f00 : 0xffff0000);

            XYd xyd = utmToScreen(new XYd(p.x, p.y));
            XY xy = new XY((int)(xyd.x+0.5), (int)(xyd.y+0.5));

            // draw a connecting line to the next point (unless this is the last point)
            if (idx != points.size()-1) {
                int nextIdx = Math.min(idx + (1 << queryLevel), points.size() - 1);
                QuadPoint next = points.get(nextIdx);

                XYd xyd2 = utmToScreen(new XYd(next.x, next.y));
                XY xy2 = new XY((int) (xyd2.x + 0.5), (int) (xyd2.y + 0.5));
                canvas.drawLine(xy.x, xy.y, xy2.x, xy2.y, paint);
                ++lines;
            }

            if (idx != 0) {
                int prevIdx = idx - (1 << queryLevel);
                if (k > 0 && matches.matchIdx[k-1] != prevIdx) {
                    // previous point is not on screen; draw connecting (partial) line since it
                    // would not be drawn by the code above
                    QuadPoint prev = points.get(prevIdx);

                    XYd xyd0 = utmToScreen(new XYd(prev.x, prev.y));
                    XY xy0 = new XY((int) (xyd0.x + 0.5), (int) (xyd0.y + 0.5));
                    canvas.drawLine(xy0.x, xy0.y, xy.x, xy.y, paint);
                }
            }
        }

        log("Draw path lines: "+lines);
    }

    private void drawPointsOfInterest(Canvas canvas) {
        Paint paint = new Paint();
//        paint.setTypeface();
        paint.setTextSize(12);
        paint.setStrokeWidth(8);

        for (int k = 0; k < pointsOfInterest.size(); ++k) {
            PointOfInterest poi = pointsOfInterest.get(k);
            String text = String.format("%s (%d/%d)", poi.name, k + 1, pointsOfInterest.size());
            drawPointOfInterest(canvas, paint, poi.utmX, poi.utmY, text);
        }
    }

    private void drawPointOfInterest(Canvas canvas, Paint paint, int utmX, int utmY, String text) {
        XYd xyd = utmToScreen(new XYd(utmX, utmY));
        if (xyd.x < 0 || xyd.x >= getWidth() || xyd.y < 0 || xyd.y >= getHeight())
            return; // note: might skip content near border

        paint.setColor(0xffffff00);
        canvas.drawPoint((float)xyd.x, (float)xyd.y, paint);

//        paint.setColor(0xffffffff);
//        canvas.drawText(text, (float)xyd.x+5, (float)xyd.y, paint);
    }

    private void drawGPSMarker(Canvas canvas) {
        XYd screenXY = utmToScreen(gpsCoordinate);
        canvas.drawBitmap(gpsIcon, (int)(screenXY.x-gpsIcon.getWidth()/2+0.5), (int)(screenXY.y-gpsIcon.getHeight()/2+0.5), null);
    }

    private void copyImage(Canvas canvas, Bitmap src, XYd pos) {
        int sw = src.getWidth(), sh = src.getHeight();
        double dw = sw * scalingZoom;
        double dh = sh * scalingZoom;
        Rect srcRect = new Rect(0, 0, sw, sh);
        Rect dstRect = new Rect((int)(pos.x+0.5), (int)(pos.y+0.5), (int)(pos.x+dw+0.5), (int)(pos.y+dh+0.5));
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
                    centerUtm = new XYd(centerUtm.x - pixelToUtm(dx), centerUtm.y + pixelToUtm(dy));
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
                centerUtm = new XYd(centerUtm.x - pixelToUtm(focusDiffX), centerUtm.y + pixelToUtm(focusDiffY));
            }

            // now zoom
            double actualScale = scaleFactor / oldScaleFactor;
            XYd p = screenToUtm(new XYd(detector.getFocusX(), detector.getFocusY()));
            centerUtm = p.sub(p.sub(centerUtm).mul(actualScale));

            prevFocusX = detector.getFocusX();
            prevFocusY = detector.getFocusY();

            invalidate();

            return true;
        }
    }

    //        Paint p = new Paint();
//        // clear screen
//        p.setColor(Color.BLACK);
//        p.setAlpha(255);
//        p.setStrokeWidth(1);
//        canvas.drawRect(0, 0, getWidth(), getHeight(), p);
//
//        p.setColor(Color.CYAN);
//        p.setAlpha(starAlpha+=starFade);
//        p.setStrokeWidth(5);
//        canvas.drawPoint(starField.get(i).x, starField.get(i).y, p);
}