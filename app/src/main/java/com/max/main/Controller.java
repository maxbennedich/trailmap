package com.max.main;

import com.max.config.Config;
import com.max.config.ConfigItem;
import com.max.config.ConfigItemLabel;
import com.max.config.ConfigItemSeekBar;
import com.max.config.ConfigItemSwitch;
import com.max.config.ConfigListAdapter;
import com.max.drawing.Renderer;
import com.max.latlng.LatLngHelper;
import com.max.location.GpsLocationService;
import com.max.location.LocationListenerWithPreviousLocation;
import com.max.location.LocationServiceController;
import com.max.location.MockLocationService;
import com.max.logic.XYd;
import com.max.route.PointOfInterest;
import com.max.route.QuadPoint;
import com.max.route.QuadNode;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.app.Activity;
import android.widget.ListView;
import android.widget.SeekBar;

import java.io.InputStream;
import java.io.ObjectInputStream;
import java.util.Arrays;
import java.util.List;

public class Controller extends Activity {

    private ListView drawerList;

    public static SeekBar globalSeekBar;

    private Config config;

    private LocationServiceController locationServiceController;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        config = new Config();

        renderer = ((Renderer)findViewById(R.id.content_frame));
        renderer.config = config;

        // start the location service as early as possible to get the GPS going
        locationServiceController = new LocationServiceController(
                (LocationManager) getSystemService(Context.LOCATION_SERVICE),
                locationListener,
                config);

        createMenu();

//        loadRoute();
//        loadPointsOfInterest();
    }

    private Renderer renderer;

    private void createMenu() {
        List<ConfigItem<?>> configItems = Arrays.asList(
                new ConfigItemLabel("General"),
                new ConfigItemSwitch("Use GPS", config.gpsEnabled) {
                    @Override protected void onUpdate() {
                        locationServiceController.enableGps(config.gpsEnabled.value);
                    }
                },
                new ConfigItemSwitch("Simulate movement", config.mockLocationService) {
                    @Override protected void onUpdate() {
                        locationServiceController.enableMock(config.mockLocationService.value);
                    }
                },
                new ConfigItemLabel("Layers"),
                new ConfigItemSwitch("Route", config.showRoute) {
                    @Override protected void onUpdate() { renderer.invalidateTileCache(true); }
                },
                new ConfigItemSwitch("Points of Interest", config.showPointsOfInterest) {
                    @Override protected void onUpdate() { renderer.invalidateTileCache(true); }
                },
                new ConfigItemSwitch("GPS Trace", config.showGpsTrace) {
                    @Override protected void onUpdate() { renderer.invalidateTileCache(true); }
                },
                new ConfigItemLabel("Map view"),
                new ConfigItemSeekBar("Brightness", config.mapBrightness) {
                    @Override protected void onUpdate() { renderer.invalidateTileCache(true); }
                }
//                new CacheSizeSeekBar("Cache Size", config),
        );

//        CustomInterceptDrawerLayout drawerLayout = (CustomInterceptDrawerLayout) findViewById(R.id.drawer_layout);
        drawerList = (ListView) findViewById(R.id.left_drawer);
        drawerList.setAdapter(new ConfigListAdapter(getApplicationContext(), configItems));
    }

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

    private final LocationListenerWithPreviousLocation locationListener = new LocationListenerWithPreviousLocation() {
        @Override public void onLocationChanged(Location location) {
            super.onLocationChanged(location);

            // TODO use nanos, not getTime
            Log.d("OptiMap", String.format("source=%s, lat=%.4f, long=%.4f, bearing=%.4f (%s), accuracy=%.4f, time=%d",
                    location.getProvider(), location.getLatitude(), location.getLongitude(), location.getBearing(), location.hasBearing(), location.getAccuracy(), location.getTime()));
            XYd xy = LatLngHelper.getXYdFromLatLng(location.getLatitude(), location.getLongitude());
            renderer.setGPSCoordinate(xy.x, xy.y);
            if (location.hasBearing())
                renderer.setGPSBearing(location.getBearing());
            renderer.invalidate();
        }
    };
}