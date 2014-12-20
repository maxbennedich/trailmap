package com.max.drawing;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

public class GameBoard extends View {

    private Paint p;
//Add private variables to keep up with sprite position and size
    private Rect sprite1Bounds = new Rect(0,0,0,0);
    private Rect sprite2Bounds = new Rect(0,0,0,0);
    private Point sprite1;
    private Point sprite2;
//Bitmaps that hold the actual sprite images
    private Bitmap bm1 = null;
    private Bitmap bm2 = null;

//Allow our controller to get and set the sprite positions

//sprite 1 setter
    synchronized public void setSprite1(Point p) {
        sprite1=p;
    }

//sprite 1 getter
    synchronized public Point getSprite1() {
        return sprite1;
    }

//sprite 2 setter
    synchronized public void setSprite2(Point p) {
        sprite2=p;
    }

//sprite 2 getter
    synchronized public Point getSprite2() {
        return sprite2;
    }

//expose sprite bounds to controller
    synchronized public int getSprite1Width() {
        return sprite1Bounds.width();
    }

    synchronized public int getSprite1Height() {
        return sprite1Bounds.height();
    }

    synchronized public int getSprite2Width() {
        return sprite2Bounds.width();
    }

    synchronized public int getSprite2Height() {
        return sprite2Bounds.height();
    }

    public GameBoard(Context context, AttributeSet aSet) {
        super(context, aSet);
        p = new Paint();
//load our bitmaps and set the bounds for the controller

        sprite1 = new Point(-1,-1);
        sprite2 = new Point(-1,-1);
        p = new Paint();
        int id1 = getResources().getIdentifier("tile_5_57_64", "drawable", getContext().getPackageName());
        int id2 = getResources().getIdentifier("tile_4_28_33", "drawable", getContext().getPackageName());
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false; // prevent scaling
        bm1 = BitmapFactory.decodeResource(getResources(), id1, options);
        bm2 = BitmapFactory.decodeResource(getResources(), id2, options);

        sprite1Bounds = new Rect(0,0, bm1.getWidth(), bm1.getHeight());
        sprite2Bounds = new Rect(0,0, bm2.getWidth(), bm2.getHeight());

        Log.d("Tile", "Initialized tiles -- size "+bm1.getWidth()+","+bm1.getHeight());
    }

    @Override
    synchronized public void onDraw(Canvas canvas) {
        // clear screen
        p.setColor(Color.BLACK);
        p.setAlpha(255);
        p.setStrokeWidth(1);
        canvas.drawRect(0, 0, getWidth(), getHeight(), p);

//        p.setColor(Color.CYAN);
//        p.setAlpha(starAlpha+=starFade);
//        p.setStrokeWidth(5);
//        canvas.drawPoint(starField.get(i).x, starField.get(i).y, p);

        if (sprite1.x>=0)
            canvas.drawBitmap(bm1, sprite1.x, sprite1.y, null);
        if (sprite2.x>=0)
            canvas.drawBitmap(bm2, sprite2.x, sprite2.y, null);
    }
}