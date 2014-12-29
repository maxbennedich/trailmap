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
import android.util.Log;
import android.app.Activity;

import java.io.InputStream;
import java.io.ObjectInputStream;
import java.util.List;

public class Main extends Activity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        renderer = ((Renderer)findViewById(R.id.the_canvas));

        // start this as early as possibly to get the GPS going
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
        Log.d("AccuMap", String.format("source=%s, lat=%.4f, long=%.4f, bearing=%.4f, accuracy=%.4f, time=%d",
                location.getProvider(), location.getLatitude(), location.getLongitude(), location.getBearing(), location.getAccuracy(), location.getTime()));
        XYd xy = LatLngHelper.getXYdFromLatLng(location.getLatitude(), location.getLongitude());
        renderer.setGPSCoordinate(xy.x, xy.y);
        if (location.hasBearing())
            renderer.setGPSBearing(location.getBearing());
        renderer.invalidate();
    }
}