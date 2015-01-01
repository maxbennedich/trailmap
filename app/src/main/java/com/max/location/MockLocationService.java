package com.max.location;

import android.location.Location;
import android.location.LocationListener;
import android.os.Handler;

import java.util.Random;

/** Mock location service that essentially does a random walk. */
public class MockLocationService implements Runnable, PausableLocationService {
    private static final long MOCK_LOCATION_UPDATE_INTERVAL_MS = 1000;

    private final LocationListener locationListener;

    private double lat = 59.356776, lng = 17.986762;
    private float bearing = 0;
    private Random rnd = new Random(0);

    private Handler handler = new Handler();

    public MockLocationService(LocationListener locationListener) {
        this.locationListener = locationListener;
    }

    @Override public void start() {
        handler.postDelayed(this, MOCK_LOCATION_UPDATE_INTERVAL_MS);
    }

    @Override public void stop() {
        handler.removeCallbacks(this);
    }

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
}
