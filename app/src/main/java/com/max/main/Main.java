package com.max.main;

import com.max.drawing.Renderer;
import com.max.latlng.LatLngHelper;
import com.max.logic.XYd;
import com.max.route.PointOfInterest;
import com.max.route.QuadPoint;
import com.max.route.QuadNode;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.app.Activity;

import java.io.InputStream;
import java.io.ObjectInputStream;
import java.util.List;
import java.util.Random;

public class Main extends Activity {
    private static final boolean USE_MOCK_LOCATION_SERVICE = true;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        renderer = ((Renderer)findViewById(R.id.the_canvas));

        // start this as early as possibly to get the GPS started
        initLocationService();

        loadRoute();
        loadPointsOfInterest();
    }

    private Renderer renderer;

    private void loadRoute() {
        InputStream is = getResources().openRawResource(R.raw.route);
        try {
            ObjectInputStream ois = new ObjectInputStream(is);
            List<QuadPoint> points = (List<QuadPoint>)ois.readObject();
            QuadNode quadRoot = (QuadNode)ois.readObject();
            renderer.points = points;
            renderer.quadRoot = quadRoot;
            ois.close();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load route resource", e);
        }
    }

    private void loadPointsOfInterest() {
        InputStream is = getResources().openRawResource(R.raw.poi);
        try {
            ObjectInputStream ois = new ObjectInputStream(is);
            List<PointOfInterest> poi = (List<PointOfInterest>)ois.readObject();

            // set labels
            for (int k = 0; k < poi.size(); ++k) {
                String name = poi.get(k).name;
                if (name.endsWith(" kyrka"))
                    name = name.substring(0, name.length()-" kyrka".length());
                if (name.endsWith(" - domkyrkan"))
                    name = name.substring(0, name.length()-" - domkyrkan".length());
                poi.get(k).label = String.format("%s (%d/%d)", name, k + 1, poi.size());
            }

            renderer.pointsOfInterest = poi;
            ois.close();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load points of interest resource", e);
        }
    }

    private final LocationListener locationListener = new LocationListener() {
        public void onLocationChanged(Location location) {
            // TODO use nanos, not getTime
            Log.d("AccuMap", String.format("source=%s, lat=%.4f, long=%.4f, bearing=%.4f (%s), accuracy=%.4f, time=%d",
                    location.getProvider(), location.getLatitude(), location.getLongitude(), location.getBearing(), location.hasBearing(), location.getAccuracy(), location.getTime()));
            XYd xy = LatLngHelper.getXYdFromLatLng(location.getLatitude(), location.getLongitude());
            renderer.setGPSCoordinate(xy.x, xy.y);
            if (location.hasBearing())
                renderer.setGPSBearing(location.getBearing());
            renderer.invalidate();
        }
        public void onStatusChanged(String provider, int status, Bundle extras) { }
        public void onProviderEnabled(String provider) { }
        public void onProviderDisabled(String provider) { }
    };

    private static final long MOCK_LOCATION_UPDATE_INTERVAL_MS = 1000;

    /** Mock location service that essentially does a random walk. */
    private final Runnable mockLocationService = new Runnable() {
        private double lat = 59.356776, lng = 17.986762;
        private float bearing = 0;
        private Random rnd = new Random(0);

        private Location mockLocation = new Location("mock") {
            @Override public double getLatitude() { return lat; }
            @Override public double getLongitude() { return lng; }
            @Override public boolean hasBearing() { return true; }
            @Override public float getBearing() { return bearing; }
            @Override public float getAccuracy() { return 0; }
            @Override public long getTime() { return 0; }
        };

        @Override public void run() {
            bearing += rnd.nextDouble() * 10;
            double speed = rnd.nextDouble() * 3e-4;
            lat += speed * Math.sin(bearing * Math.PI / 180);
            lng += speed * Math.cos(bearing * Math.PI / 180);
            locationListener.onLocationChanged(mockLocation);

            handler.postDelayed(this, MOCK_LOCATION_UPDATE_INTERVAL_MS);
        }
    };

    private Handler handler = new Handler();

    private void initLocationService() {
        if (USE_MOCK_LOCATION_SERVICE) {
            handler.postDelayed(new Runnable() {
                @Override public void run() {
                    handler.postDelayed(mockLocationService, MOCK_LOCATION_UPDATE_INTERVAL_MS);
                }
            }, 1000);
        } else {
            LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
        }
        Log.d("AccuMap", (USE_MOCK_LOCATION_SERVICE ? "Mock" : "GPS") + " location service initialized");
    }
}