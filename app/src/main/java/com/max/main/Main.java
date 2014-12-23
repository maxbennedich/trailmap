package com.max.main;

import com.max.drawing.Renderer;
import com.max.kml.CSVRouteLoader;
import com.max.kml.InvalidKMLException;
import com.max.kml.KMLRouteLoader;
import com.max.latlng.LatLngHelper;
import com.max.logic.XYd;
import com.max.route.Route;

import android.content.Context;
import android.content.res.XmlResourceParser;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.app.Activity;

import java.io.InputStream;

public class Main extends Activity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        Route route = null;
        try {
            InputStream is = getResources().openRawResource(R.raw.gotland_612878m);
            route = new CSVRouteLoader().loadRoute(is);
        } catch (InvalidKMLException e) {
            throw new IllegalStateException("Failed to load route file", e);
        }
        ((Renderer)findViewById(R.id.the_canvas)).route = route;

        initLocationService();
    }

    private void initLocationService() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        LocationListener locationListener = new LocationListener() {
            public void onLocationChanged(Location location) {
                locationUpdated(location);
            }
            public void onStatusChanged(String provider, int status, Bundle extras) { }
            public void onProviderEnabled(String provider) { }
            public void onProviderDisabled(String provider) { }
        };

        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
        Log.d("AccuMap", "Location service initialized");
    }

    private void locationUpdated(Location location) {
        // TODO use nanos, not getTime
        Log.d("AccuMap", String.format("source=%s, lat=%.4f, long=%.4f, accuracy=%.4f, time=%d",
                location.getProvider(), location.getLatitude(), location.getLongitude(), location.getAccuracy(), location.getTime()));
        XYd xy = LatLngHelper.getXYdFromLatLng(location.getLatitude(), location.getLongitude());
        Renderer renderer = ((Renderer)findViewById(R.id.the_canvas));
        renderer.setGPSCoordinate(xy);
        renderer.invalidate();
    }
}