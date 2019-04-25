package com.max.location;

import android.content.Context;
import android.location.LocationListener;
import android.location.LocationManager;

import com.max.config.Config;

public class LocationServiceController {
    private final LocationListenerWithPreviousLocation locationListener;

    private final MockLocationService mockLocationService;
    private final GpsLocationService gpsLocationService;

    public LocationServiceController(LocationManager locationManager,
                                     LocationListenerWithPreviousLocation locationListener,
                                     Config config) {
        this.locationListener = locationListener;

        mockLocationService = new MockLocationService(locationListener);
        gpsLocationService = new GpsLocationService(locationManager, locationListener);

        if (config.gpsEnabled.value)
            gpsLocationService.start();
        if (config.mockLocationService.value)
            mockLocationService.start();
    }

    public void enableGps(boolean enable) {
        if (enable) {
            gpsLocationService.start();
        } else {
            gpsLocationService.stop();
        }
    }

    public void enableMock(boolean enable) {
        if (enable) {
            mockLocationService.start();
        } else {
            mockLocationService.stop();
        }
    }
}
