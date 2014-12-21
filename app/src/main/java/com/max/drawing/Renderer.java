package com.max.drawing;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import com.max.logic.Tile;
import com.max.logic.TilePos;
import com.max.logic.TileRectangle;
import com.max.logic.XY;
import com.max.main.R;

import java.util.LinkedHashMap;

public class Renderer extends View {

    private Bitmap emptyTile;

    private LinkedHashMap<TilePos, Tile> tileCache = getTileCache();

    private static final int MIN_ZOOM_LEVEL = 0;
    private static final int MAX_ZOOM_LEVEL = 10;
    public int zoomLevel = 5;

    private static final double MIN_SCALE = 1 << MIN_ZOOM_LEVEL;
    private static final double MAX_SCALE = (1 << MAX_ZOOM_LEVEL) << 2;
    private double scaleFactor = 1 << zoomLevel;
    private double scalingZoom = 1;

    /** 100 corresponds to ~26 mb image data (256x256 pixels, 4 bytes per pixel) */
    private static final int TILE_CACHE_SIZE = 100;

    private XY centerUtm = new XY(669_715, 6_583_611);

    private LinkedHashMap<TilePos, Tile> getTileCache() {
        return new LinkedHashMap<TilePos, Tile>(TILE_CACHE_SIZE, 0.75f, true) {
            private static final long serialVersionUID = 1L;

            /**
             * Override get to load tiles not in the cache and insert them into the cache.
             *
             * @returns Null if image could not be loaded (typically out of bounds).
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
        String resName = "tile_" + this.zoomLevel + "_" + tp.tx + "_" + tp.ty;
        // XXX perf below, consider a local hash instead
        int id = getResources().getIdentifier(resName, "drawable", getContext().getPackageName());
        if (id != 0) {
            System.out.println("Loading resource " + resName);
            Bitmap map = BitmapFactory.decodeResource(getResources(), id, NO_SCALING);
            // resource is null if not found (should not happen since id is not 0)
            if (map != null)
                return new Tile(tp, map);
        }
        return null;
    }

    XY pixelToUtm(int zoomLevel, XY pixel) {
        return new XY(pixelToUtm(zoomLevel, pixel.x), pixelToUtm(zoomLevel, pixel.y));
    }

    int pixelToUtm(int zoomLevel, int pixel) {
        // resolution: 256px = 1024m (level 10) 2048m (level 9)
        return (int)(pixel*(1<<(20-zoomLevel-8)) / scalingZoom + 0.5); // 8 since tile is 256 pixels wide
    }

    XY utmToPixel(int zoomLevel, XY utmxy) {
        return new XY(utmToPixel(zoomLevel, utmxy.x), utmToPixel(zoomLevel, utmxy.y));
    }

    int utmToPixel(int zoomLevel, int utm) {
        // resolution: 256px = 1024m (level 10) 2048m (level 9)
        return (int)(utm/(1<<(20-zoomLevel-8)) * scalingZoom + 0.5); // 8 since tile is 256 pixels wide
    }

    XY utmToScreen(int zoomLevel, XY utmxy) {
        XY mid = getScreenSize().div(2).sub(1, 1); // y off by one?
        XY utp = utmToPixel(zoomLevel, utmxy.sub(centerUtm));
        return new XY(mid.x + utp.x, mid.y - utp.y);
    }

    long time;

    public XY getScreenSize() { return new XY(getWidth(), getHeight()); }

    @Override
    synchronized public void onDraw(Canvas canvas) {
        int zoomLevel = this.zoomLevel;

        time = System.nanoTime();

        // calculate utm coordinates for screen corners
        XY utm0 = centerUtm.sub(pixelToUtm(zoomLevel, getScreenSize().div(2).sub(1, 1)));
        XY utm1 = centerUtm.add(pixelToUtm(zoomLevel, getScreenSize().div(2)));

        TileRectangle tr = new TileRectangle(utm0.x, utm0.y, utm1.x, utm1.y, zoomLevel);

        for (int ty = tr.ty0; ty <= tr.ty1; ++ty) {
            for (int tx = tr.tx0; tx <= tr.tx1; ++tx) {
                // translate tile back to utm coordinates, then to screen coordinates
                int utx0 = tx * tr.tileSize - 1_200_000;
                int uty0 = 8_500_000 - ty * tr.tileSize;

                XY screenXY = utmToScreen(zoomLevel, new XY(utx0, uty0));

//                System.out.printf("Copy tile %d,%d to utm %d,%d, pixel %d,%d\n", tx, ty, utx0, uty0, screenX, screenY);
                Tile tile = tileCache.get(new TilePos(zoomLevel, tx, ty));
                Bitmap tileImg = tile == null ? emptyTile : tile.map;
                copyImage(canvas, tileImg, screenXY);
            }
        }

        Log.d("Perf", String.format("Load tiles: %.0f ms", (System.nanoTime() - time) * 1e-6)); time = System.nanoTime();

//        drawPath(zoomLevel);
//        drawPointsOfInterest(zoomLevel);
//        System.out.printf("Draw POIs: %.0f ms\n", (System.nanoTime()-time)*1e-6); time = System.nanoTime();
//        drawCrosshair();

//        imagePanel.repaint();
//        System.out.printf("Repaint: %.0f ms\n", (System.nanoTime()-time)*1e-6); time = System.nanoTime();
    }

    private void copyImage(Canvas canvas, Bitmap src, XY pos) {
        int sw = src.getWidth(), sh = src.getHeight();
        int dw = (int)(sw * scalingZoom + 0.5);
        int dh = (int)(sh * scalingZoom + 0.5);
        Rect srcRect = new Rect(0, 0, sw, sh);
        Rect dstRect = new Rect(pos.x, pos.y, pos.x+dw, pos.y+dh);
        canvas.drawBitmap(src, srcRect, dstRect, null);
//        int dx = pos.x, dy = pos.y;
//        int imgW = getWidth(), imgH = getHeight();
//        int sw = src.getWidth(), sh = src.getHeight();
//        int dw = (int)(dw * scalingZoom + 0.5);
//        int dh = (int)(dh * scalingZoom + 0.5);
//
//        if (dx >= imgW || dy >= imgH || dx+sw <= 0 || dy+sh <= 0)
//            return; // out of bounds
//
//        int copyWidth = Math.min(sw, imgW-dx) + Math.min(0, dx);
//        int copyHeight = Math.min(sh, imgH-dy) + Math.min(0, dy);
//
//        Rect srcRect = new Rect(-Math.min(0, dx), -Math.min(0, dy), -Math.min(0, dx) + copyWidth, -Math.min(0, dy) + copyHeight);
//        Rect dstRect = new Rect(Math.max(0, dx), Math.max(0, dy), Math.max(0, dx) + dstCopyWidth, Math.max(0, dy) + dstCopyHeight);
//        canvas.drawBitmap(src, srcRect, dstRect, null);
    }
///////////////////////////////////////////////////////////////////////////////////////////////////

    enum ActionMode { NONE, PAN, ZOOM }

    private ScaleGestureDetector zoomDetector;
    private ActionMode actionMode = ActionMode.NONE;
    private float panStartX, panStartY;

    public Renderer(Context context, AttributeSet aSet) {
        super(context, aSet);

        zoomDetector = new ScaleGestureDetector(getContext(), new ScaleListener());

        emptyTile = BitmapFactory.decodeResource(getResources(), R.drawable.empty, NO_SCALING);

        Log.d("Tile", "Initialized tiles -- size "+emptyTile.getWidth()+","+emptyTile.getHeight());
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
                    int dx = (int)(event.getX() - panStartX + 0.5);
                    int dy = (int)(event.getY() - panStartY + 0.5);
                    centerUtm = new XY(centerUtm.x - pixelToUtm(zoomLevel, dx),
                            centerUtm.y + pixelToUtm(zoomLevel, dy));
                    panStartX = event.getX();
                    panStartY = event.getY();
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

        zoomDetector.onTouchEvent(event);
        return true;
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            scaleFactor = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scaleFactor * detector.getScaleFactor()));
            zoomLevel = 31 - Integer.numberOfLeadingZeros((int)(scaleFactor+(1e-12)));
            zoomLevel = Math.max(MIN_ZOOM_LEVEL, Math.min(MAX_ZOOM_LEVEL, zoomLevel));
            scalingZoom = scaleFactor / (1 << zoomLevel);
//                float zoomTranslationX = (1 - scaleDifference) * detector.getFocusX() / scaleFactor;
//                float zoomTranslationY = (1 - scaleDifference) * detector.getFocusY() / scaleFactor;
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