package com.max.main;

import com.max.drawing.GameBoard;
import java.util.Random;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.app.Activity;
import android.graphics.Point;

public class Main extends Activity implements OnClickListener {
    private Handler frame = new Handler();
//Divide the frame by 1000 to calculate how many times per second the screen will update.
    private static final int FRAME_RATE = 20; //50 frames per second
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        Handler h = new Handler();
        ((Button)findViewById(R.id.the_button)).setOnClickListener(this);
//We can't initialize the graphics immediately because the layout manager
//needs to run first, thus call back in a sec.
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                initGfx();
            }
        }, 1000);
    }

    private Point getRandomPoint() {
        Random r = new Random();
        int minX = 0;
        int maxX = findViewById(R.id.the_canvas).getWidth() - ((GameBoard)findViewById(R.id.the_canvas)).getSprite1Width();
        int x = 0;
        int minY = 0;
        int maxY = findViewById(R.id.the_canvas).getHeight() - ((GameBoard)findViewById(R.id.the_canvas)).getSprite1Height();
        int y = 0;
        x = r.nextInt(maxX-minX+1)+minX;
        y = r.nextInt(maxY-minY+1)+minY;
        return new Point (x,y);
    }

    synchronized public void initGfx() {

//Select two random points for our initial sprite placement.
//The loop is just to make sure we don't accidentally pick
//two points that overlap.
        Point p1, p2;
        {
            p1 = getRandomPoint();
            p2 = getRandomPoint();
        }
        ((GameBoard)findViewById(R.id.the_canvas)).setSprite1(p1);
        ((GameBoard)findViewById(R.id.the_canvas)).setSprite2(p2);
        ((Button)findViewById(R.id.the_button)).setEnabled(true);
        frame.removeCallbacks(frameUpdate);
        frame.postDelayed(frameUpdate, FRAME_RATE);
    }

    @Override
    synchronized public void onClick(View v) {
        initGfx();
    }

    private Runnable frameUpdate = new Runnable() {
        @Override
        synchronized public void run() {
            frame.removeCallbacks(frameUpdate);
//make any updates to on screen objects here
//then invoke the on draw by invalidating the canvas
            ((GameBoard)findViewById(R.id.the_canvas)).invalidate();
            frame.postDelayed(frameUpdate, FRAME_RATE);
        }
    };
}