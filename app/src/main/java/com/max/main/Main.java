package com.max.main;

import com.max.drawing.Renderer;
import java.util.Random;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.app.Activity;
import android.graphics.Point;

public class Main extends Activity {
    private Handler frame = new Handler();

    //Divide the frame by 1000 to calculate how many times per second the screen will update.
    private static final int FRAME_RATE = 20; // 10 frames per second

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        initLocationService();

//        ((Button)findViewById(R.id.zoom_out)).setOnClickListener(new OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                --((Renderer)findViewById(R.id.the_canvas)).zoomLevel;
//            }
//        });

        Handler h = new Handler();

        // We can't initialize the graphics immediately because the layout manager
        // needs to run first, thus call back in a sec.
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                initGfx();
            }
        }, 1000);
    }

    private void initLocationService() {
        Log.d("AccuMap", "Initializing location service");
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        LocationListener locationListener = new LocationListener() {
            public void onLocationChanged(Location location) {
                locationUpdated(location);
            }
            public void onStatusChanged(String provider, int status, Bundle extras) { }
            public void onProviderEnabled(String provider) { }
            public void onProviderDisabled(String provider) { }
        };

        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
        Log.d("AccuMap", "Location service initialized");
    }

    synchronized private void locationUpdated(Location location) {
        // TODO use nanos, not getTime
        Log.d("AccuMap", String.format("source=%s, lat=%.4f, long=%.4f, accuracy=%.4f, time=%d",
                location.getProvider(), location.getLatitude(), location.getLongitude(), location.getAccuracy(), location.getTime()));
    }

    synchronized public void initGfx() {
//        ((Button)findViewById(R.id.the_button)).setEnabled(true);
        frame.removeCallbacks(frameUpdate);
        frame.postDelayed(frameUpdate, FRAME_RATE);
    }

    private Runnable frameUpdate = new Runnable() {
        @Override
        synchronized public void run() {
            frame.removeCallbacks(frameUpdate);
            //make any updates to on screen objects here
            //then invoke the on draw by invalidating the canvas
            ((Renderer)findViewById(R.id.the_canvas)).invalidate();
            frame.postDelayed(frameUpdate, FRAME_RATE);
        }
    };
}