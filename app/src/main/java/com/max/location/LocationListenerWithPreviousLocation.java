package com.max.location;

import android.location.Location;
import android.location.LocationListener;
import android.os.Bundle;

/**
 * LocationListener that keeps track of the previous location.
 * This is used to make multiple location services cooperate, for example using the GPS and
 * the mock service simultaneously, or for the mock service to continue where the GPS left off.
 */
abstract public class LocationListenerWithPreviousLocation implements LocationListener {
    public Location previousLocation = null;

    public void onLocationChanged(Location location) { previousLocation = location; }
    public void onStatusChanged(String provider, int status, Bundle extras) { }
    public void onProviderEnabled(String provider) { }
    public void onProviderDisabled(String provider) { }
}
