package com.max.drawing;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import com.max.logic.Rectangle;
import com.max.logic.Tile;
import com.max.logic.TilePos;
import com.max.logic.TileRectangle;
import com.max.logic.XY;
import com.max.logic.XYd;
import com.max.main.R;
import com.max.route.QuadLeaf;
import com.max.route.QuadNode;
import com.max.route.RoadSurface;
import com.max.route.Route;
import com.max.route.RouteSegment;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

public class Renderer extends View {

//    public Route route;
    public QuadNode quadRoot;

    private Bitmap emptyTile;

    private LinkedHashMap<TilePos, Tile> tileCache = getTileCache();

    private static final int MIN_ZOOM_LEVEL = 0;
    private static final int MAX_ZOOM_LEVEL = 10;
    public int zoomLevel = 5;

    private static final double MIN_SCALE = 1 << MIN_ZOOM_LEVEL;
    private static final double MAX_SCALE = (1 << MAX_ZOOM_LEVEL) << 3;
    private double scaleFactor = 1 << zoomLevel;
    private double scalingZoom = 1;

    /** 100 corresponds to ~26 mb image data (256x256 pixels, 4 bytes per pixel) */
    private static final int TILE_CACHE_SIZE = 100;

    private XYd centerUtm = new XYd(669_715, 6_583_611);
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

    private Tile loadTile(int zoomLevel, TilePos tp) {
        String resName = "tile_" + tp.zoomLevel + "_" + tp.tx + "_" + tp.ty;
        // XXX perf below, consider a local hash instead
        int id = getResources().getIdentifier(resName, "drawable", getContext().getPackageName());
        if (id != 0) {
            Log.d("AccuMap", "Loading resource " + resName);
            Bitmap map = BitmapFactory.decodeResource(getResources(), id, NO_SCALING);
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

    long time;

    public XYd getScreenSize() { return new XYd(getWidth(), getHeight()); }

    @Override
    synchronized public void onDraw(Canvas canvas) {
        time = System.nanoTime();

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

//        Log.d("AccuMap", String.format("Load tiles: %.0f ms", (System.nanoTime() - time) * 1e-6)); time = System.nanoTime();

        drawPath(canvas);
//        drawPointsOfInterest(zoomLevel);
//        System.out.printf("Draw POIs: %.0f ms\n", (System.nanoTime()-time)*1e-6); time = System.nanoTime();
//        drawCrosshair();

        drawGPSMarker(canvas);

//        imagePanel.repaint();
//        System.out.printf("Repaint: %.0f ms\n", (System.nanoTime()-time)*1e-6); time = System.nanoTime();
    }

    private void drawPath(Canvas canvas) {
        // calculate utm coordinates for screen corners
        XYd utm0 = centerUtm.sub(pixelToUtm(getScreenSize().div(2).sub(1, 1)));
        XYd utm1 = centerUtm.add(pixelToUtm(getScreenSize().div(2)));

        // find route points visible on screen by querying quad tree
        List<QuadLeaf> points = new ArrayList<>();
        int queryLevel = (MAX_ZOOM_LEVEL - zoomLevel)*2;
        quadRoot.queryTree(queryLevel, (int)Math.floor(utm0.x), (int)Math.floor(utm0.y), (int)Math.ceil(utm1.x), (int)Math.ceil(utm1.y), points);

//        System.out.printf("Calculate path: %.0f ms\n", (System.nanoTime()-time)*1e-6); time = System.nanoTime();

        Paint paint = new Paint();
        paint.setStrokeWidth(6);
        for (QuadLeaf p : points) {
//            paint.setColor(p.surface == RoadSurface.DIRT ? 0x6fff5f00 : 0x6fff0000);
            paint.setColor(p.surface == RoadSurface.DIRT ? 0xffff5f00 : 0xffff0000);

            XYd xyd = utmToScreen(new XYd(p.x, p.y));
            XY xy = new XY((int)(xyd.x+0.5), (int)(xyd.y+0.5));

            // TODO optimize
            QuadLeaf next = p;
            for (int k = 0; k < (1<<queryLevel); ++k)
                next = next.next;

            XYd xyd2 = utmToScreen(new XYd(next.x, next.y));
            XY xy2 = new XY((int)(xyd2.x+0.5), (int)(xyd2.y+0.5));
            canvas.drawLine(xy.x, xy.y, xy2.x, xy2.y, paint);

//            paint.setColor(0x6f3f3f3f);
//            canvas.drawPoint(xy.x, xy.y, paint);
        }

//        System.out.printf("Draw path: %.0f ms\n", (System.nanoTime()-time)*1e-6); time = System.nanoTime();
    }

/*    private void drawPath(Canvas canvas) {
        Set<XY> pavedPixels = new HashSet<>();
        Set<XY> dirtPixels = new HashSet<>();
        for (RouteSegment segment : route.segments) {
            // see if segment could possibly be on screen by looking at the bounding box
            // this will get rid of the majority of segments (as long as they are not too large)
            XYd min = utmToScreen(segment.boundingBox.min);
            XYd max = utmToScreen(segment.boundingBox.max);
            if ((min.x < 0 && max.x < 0) || (min.y < 0 && max.y < 0) ||
                    (min.x >= getWidth() && max.x >= getWidth()) ||
                    (min.y >= getHeight() && max.y >= getHeight())) {
                continue;
            }

            for (XY xy : segment.points) {
                XYd xyd = new XYd(xy.x, xy.y);
                XYd pdbl = utmToScreen(xyd);
                XY p = new XY((int)(pdbl.x+0.5), (int)(pdbl.y+0.5));
                if (!(p.x >= 0 && p.y >= 0 && p.x < getWidth() && p.y < getHeight()))
                    continue; // note: might skip pixels near border: should add w/2...

                (segment.roadSurface == RoadSurface.DIRT ? dirtPixels : pavedPixels).add(p);
            }
        }

//        System.out.printf("Calculate path: %.0f ms\n", (System.nanoTime()-time)*1e-6); time = System.nanoTime();

        pavedPixels.removeAll(dirtPixels);

        Paint paint = new Paint();
        paint.setStrokeWidth(5);

        paint.setColor(0x6fff0000);
        for (XY p : pavedPixels)
            canvas.drawPoint(p.x, p.y, paint);

        paint.setColor(0x6fff5f00);
        for (XY p : dirtPixels)
            canvas.drawPoint(p.x, p.y, paint);

//        System.out.printf("Draw path: %.0f ms\n", (System.nanoTime()-time)*1e-6); time = System.nanoTime();
    }*/

    private void drawGPSMarker(Canvas canvas) {
        XYd screenXY = utmToScreen(gpsCoordinate);
        Bitmap gpsIcon = BitmapFactory.decodeResource(getResources(), R.drawable.gps_arrow, NO_SCALING);
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