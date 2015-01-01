package com.max.location;

import android.content.Context;
import android.location.LocationListener;
import android.location.LocationManager;

public class GpsLocationService implements PausableLocationService {

    private final LocationManager locationManager;
    private final LocationListener locationListener;

    public GpsLocationService(LocationManager locationManager, LocationListener locationListener) {
        this.locationManager = locationManager;
        this.locationListener = locationListener;
    }

    @Override public void start() {
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
    }

    @Override public void stop() {
        locationManager.removeUpdates(locationListener);
    }
}
