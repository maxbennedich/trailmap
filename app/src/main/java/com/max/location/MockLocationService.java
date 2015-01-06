package com.max.location;

import android.location.Location;
import android.os.Handler;
import android.util.Log;

import java.util.Random;

/** Mock location service that essentially does a random walk. */
public class MockLocationService implements Runnable, PausableLocationService {
    private static final long MOCK_LOCATION_UPDATE_INTERVAL_MS = 30;

    private final LocationListenerWithPreviousLocation locationListener;

    private static final double DEFAULT_LAT = 59.356776, DEFAULT_LNG = 17.986762;

    private double lat, lng;
    private float bearing = 0;

    /** Relative speed, between 0 and 1. Multiplied with speed factor. */
    private float latLngSpd = 0.5f;

    /** Max speed (added to lat/lng coordinates directly). */
    private float latLngSpdFactor = 1e-5f;

    /** Meters per second. */
    private float speed = 0;
    private Random rnd = new Random(0);

    private Handler handler = new Handler();

    public MockLocationService(LocationListenerWithPreviousLocation locationListener) {
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
        @Override public boolean hasSpeed() { return true; }
        @Override public float getSpeed() { return speed; }
        @Override public float getAccuracy() { return 0; }
        @Override public long getTime() { return 0; }
    };

    @Override public void run() {
        // populate lat/lng -- start from previous location to be able to cooperate
        // with other location services such as the GPS
        if (locationListener.previousLocation == null) {
            lat = DEFAULT_LAT;
            lng = DEFAULT_LNG;
        } else {
            lat = locationListener.previousLocation.getLatitude();
            lng = locationListener.previousLocation.getLongitude();
        }

        bearing += (rnd.nextDouble() - 0.5) * 15;

        double r = rnd.nextDouble();
        if (r > latLngSpd) latLngSpd = Math.min(1f, latLngSpd + 0.005f);
        else latLngSpd = Math.max(0f, latLngSpd - 0.005f);
        float move = latLngSpd * latLngSpdFactor;

        double newLat = lat + move * Math.cos(bearing * Math.PI / 180);
        double newLng = lng + move * Math.sin(bearing * Math.PI / 180);

        // use simple equirectangular ("flat earth") approximation since we don't need exact numbers
        double x = Math.toRadians(newLng - lng) * Math.cos(Math.toRadians(lat + newLat)/2);
        double y = Math.toRadians(newLat - lat);
        double dist = Math.sqrt(x*x + y*y) * 6371000;
        speed = (float)(dist * 1000 / MOCK_LOCATION_UPDATE_INTERVAL_MS);

        lat = newLat;
        lng = newLng;

        locationListener.onLocationChanged(mockLocation);

        handler.postDelayed(this, MOCK_LOCATION_UPDATE_INTERVAL_MS);
    }
}
