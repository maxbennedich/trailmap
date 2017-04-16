package com.max.main;

import com.max.config.Config;
import com.max.config.ConfigItem;
import com.max.config.ConfigItemButton;
import com.max.config.ConfigItemLabel;
import com.max.config.ConfigItemSeekBar;
import com.max.config.ConfigItemSwitch;
import com.max.config.ConfigListAdapter;
import com.max.drawing.Renderer;
import com.max.kml.BinaryRouteLoader;
import com.max.kml.InvalidKMLException;
import com.max.latlng.LatLngHelper;
import com.max.location.LocationListenerWithPreviousLocation;
import com.max.location.LocationServiceController;
import com.max.logic.XYd;
import com.max.route.NavigationConfigDialog;
import com.max.route.NavigationLogger;
import com.max.route.PointOfInterest;
import com.max.route.QuadNode;
import com.max.route.QuadPointArray;

import android.Manifest;
import android.app.DialogFragment;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Point;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.app.Activity;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;
import android.widget.ListView;
import android.widget.SeekBar;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

public class Controller extends Activity implements NavigationConfigDialog.NavigationConfigDialogListener {

    private ListView drawerList;

    public static SeekBar globalSeekBar;

    private Config config;

    private Renderer renderer;

    private LocationServiceController locationServiceController;

    private LogStats loadTimer = new LogStats();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d("LogStats", "Starting onCreate");

        ensurePermissions();

        NavigationLogger.appStarted();

        LogStats onCreateTimer = new LogStats();
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

        loadPointsOfInterest();
        loadRoute();

        renderer.navigator.initRoute();

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        onCreateTimer.log("onCreate finished");
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        config.saveInstanceState(savedInstanceState, "config");
        renderer.saveInstanceState(savedInstanceState, "renderer");

        // call the superclass so it can save the view hierarchy state
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        // call the superclass so it can restore the view hierarchy state
        super.onRestoreInstanceState(savedInstanceState);

        config.restoreInstanceState(savedInstanceState, "config");
        renderer.restoreInstanceState(savedInstanceState, "renderer");
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // do any updates due to new screen orientation here
//        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
//        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT){
//        }
    }

    private static final String[] PERMISSIONS = { ACCESS_FINE_LOCATION, READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE };
    private void ensurePermissions() {
        boolean hasAllPermissions = true;
        for (String permission : PERMISSIONS) {
            if (ActivityCompat.checkSelfPermission(this, permission) != PERMISSION_GRANTED) {
                hasAllPermissions = false;
                break;
            }
        }
        if (!hasAllPermissions)
            ActivityCompat.requestPermissions(this, PERMISSIONS, 1);
    }

    private void createMenu() {
        final ConfigItemSwitch followGps = new ConfigItemSwitch("Follow GPS", config.followGps) { };

        List<ConfigItem<?>> configItems = Arrays.asList(
                new ConfigItemLabel("General"),
                new ConfigItemSwitch("Navigate", config.navigateEnabled) {
                    @Override
                    protected void onUpdate() {
                        if (config.navigateEnabled.value)
                            renderer.navigator.start();
                        else
                            renderer.navigator.stop();
                    }
                },
                new ConfigItemSwitch("Use GPS", config.gpsEnabled) {
                    @Override
                    protected void onUpdate() {
                        locationServiceController.enableGps(config.gpsEnabled.value);
                    }
                },
                new ConfigItemSwitch("Simulate Movement", config.mockLocationService) {
                    @Override
                    protected void onUpdate() {
                        locationServiceController.enableMock(config.mockLocationService.value);
                    }
                },
                new ConfigItemSwitch("Move to Touch", config.touchLocationService) {
                    @Override
                    protected void onUpdate() {
                        // turn off follow GPS if enabling this option since they're pointless together
                        if (config.touchLocationService.value && config.followGps.value)
                            followGps.forceClick();
                    }
                },
                followGps,
                new ConfigItemSwitch("Reset GPS", config.resetDistance) {
                    @Override
                    protected void onUpdate() {
                        renderer.resetGPS();
                        renderer.invalidateTileCache(true);
                    }
                },
                new ConfigItemLabel("Layers"),
                new ConfigItemSwitch("Route", config.showRoute) {
                    @Override
                    protected void onUpdate() {
                        renderer.invalidateTileCache(true);
                    }
                },
                new ConfigItemSwitch("Points of Interest", config.showPointsOfInterest) {
                    @Override
                    protected void onUpdate() {
                        renderer.invalidateTileCache(true);
                    }
                },
                new ConfigItemSwitch("GPS Trace", config.showGpsTrace) {
                    @Override
                    protected void onUpdate() {
                        renderer.invalidateTileCache(true);
                    }
                },
                new ConfigItemLabel("Map view"),
                new ConfigItemSwitch("Lock Orientation", config.lockOrientation) {
                    @Override
                    protected void onUpdate() {
                        if (config.lockOrientation.value) {
                            lockOrientation();
                        } else {
                            unlockOrientation();
                        }
                    }
                },
                new ConfigItemSeekBar("Brightness", config.mapBrightness) {
                    @Override
                    protected void onUpdate() {
                        renderer.invalidateTileCache(true);
                    }
                },
//                new CacheSizeSeekBar("Cache Size", config),
                new ConfigItemButton("Navigation Config") {
                    @Override
                    protected void onUpdate() {
                        DialogFragment newFragment = new NavigationConfigDialog();
                        newFragment.show(Controller.this.getFragmentManager(), "navconfig");
                    }
                }
        );

//        CustomInterceptDrawerLayout drawerLayout = (CustomInterceptDrawerLayout) findViewById(R.id.drawer_layout);
        drawerList = (ListView) findViewById(R.id.left_drawer);
        drawerList.setAdapter(new ConfigListAdapter(getApplicationContext(), configItems));
    }

    @Override
    public void updateNavigationConfig(Integer lastWaypointIdx, Date startTime) {
        if (lastWaypointIdx != null)
            renderer.navigator.setNextWaypoint(lastWaypointIdx + 1);
        if (startTime != null)
            renderer.navigator.setStartTime(startTime);
    }

    @Override
    public String getNavigationStats() {
        return renderer.navigator.getNavigationStats();
    }

    private static final int[] orientations = new int[] {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT,
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT};
    private static final List<Integer> rotations = Arrays.asList(
            Surface.ROTATION_0, Surface.ROTATION_90, Surface.ROTATION_180, Surface.ROTATION_270);

    private void lockOrientation() {
        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);

        // the following "magic" will lock the orientation in its current mode, taking current
        // rotation and default orientation in consideration
        int rot = rotations.indexOf(display.getRotation());
        int defaultOrientation = size.x > size.y ? 0 : 1; // 0 = landscape, 1 = portrait
        //noinspection ResourceType
        setRequestedOrientation(orientations[rot-(rot&1^defaultOrientation)&3]);
    }

    private void unlockOrientation() {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER);
    }

    private void loadRoute() {
        // Sample timings for a ~55k point route (Gotland) showed that loading the binary route took
        // around 400 ms and building the quad tree took around 700 ms with capacity 16 and 850 ms
        // with capacity 64, for a total of 1100-1250 ms. For comparison, deserializing the points
        // and the built tree from a pre-calculated resource took 2700 ms, i.e. >2 times slower.
        loadTimer.reset();
        InputStream is = getResources().openRawResource(Settings.ROUTE_RESOURCE);
        BinaryRouteLoader routeLoader = new BinaryRouteLoader();
        QuadPointArray points;
        try {
            points = routeLoader.loadRoute(is);
        } catch (InvalidKMLException e) {
            throw new IllegalStateException("Failed to load route resource", e);
        }
        loadTimer.log("Loaded route");

        QuadNode quadRoot = buildQuadTree(points);
        loadTimer.log("Built main quad tree");

        QuadNode[] segmentQuadRoots = new QuadNode[renderer.waypoints.size()];
        for (int k = 0; k < segmentQuadRoots.length; ++k) {
            segmentQuadRoots[k] = buildQuadTree(points, renderer.waypoints.get(k).routeIndex,
                    k == segmentQuadRoots.length - 1 ? points.nrPoints : renderer.waypoints.get(k + 1).routeIndex);
        }
        loadTimer.log("Built segment quad trees");

        renderer.points = points;
        renderer.quadRoot = quadRoot;
        renderer.segmentQuadRoots = segmentQuadRoots;
    }

    QuadNode buildQuadTree(QuadPointArray points) {
        return buildQuadTree(points, 0, points.nrPoints);
    }

    QuadNode buildQuadTree(QuadPointArray points, int beginIdx, int endIdx) {
        // find points bounding box (min/max)
        int x0 = 1<<30, y0 = 1<<30, x1 = -(1<<30), y1 = -(1<<30);
        for (int k = 0; k < points.nrPoints; ++k) {
            x0 = Math.min(x0, points.x[k]);
            y0 = Math.min(y0, points.y[k]);
            x1 = Math.max(x1, points.x[k]);
            y1 = Math.max(y1, points.y[k]);
        }

        QuadNode root = new QuadNode(x0, y0, x1, y1);
        for (int k = beginIdx; k < endIdx; ++k)
            root.insertPoint(k, points);

        return root;
    }

    private void loadPointsOfInterest() {
        loadTimer.reset();
        renderer.waypoints = loadPointsOfInterest(Settings.WAYPOINTS_RESOURCE, Settings.WAYPOINTS_NUMBERED);
        renderer.pointsOfInterest = loadPointsOfInterest(Settings.POINTS_OF_INTEREST_RESOURCE, false);
        loadTimer.log("Loaded points of interest");
    }

    private List<PointOfInterest> loadPointsOfInterest(int resourceId, boolean numbered) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(getResources().openRawResource(resourceId)))) {
            int count = Integer.valueOf(reader.readLine());
            List<PointOfInterest> poi = new ArrayList<>(count);
            for (int n = 0; n < count; ++n) {
                String name = reader.readLine();
                String coordinates = reader.readLine();
                String[] xy = coordinates.split(",");
                int utmX = Integer.valueOf(xy[0]);
                int utmY = Integer.valueOf(xy[1]);
                poi.add(xy.length <= 2 ?
                        new PointOfInterest(name, utmX, utmY) :
                        new PointOfInterest(name, utmX, utmY, Integer.valueOf(xy[2])));
                poi.get(n).label = numbered ? String.format("%s (%d)", name, ((n + count - 1) % count) + 1) : name;
            }

            return poi;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load points of interest resource " + resourceId, e);
        }
    }

    private final LocationListenerWithPreviousLocation locationListener = new LocationListenerWithPreviousLocation() {
        @Override public void onLocationChanged(Location location) {
            super.onLocationChanged(location);

            // TODO use time provided by location?
            XYd xy = LatLngHelper.getXYdFromLatLng(location.getLatitude(), location.getLongitude());
            NavigationLogger.gpsUpdate(location, xy.x, xy.y);

            renderer.setGPSCoordinate(xy.x, xy.y);
            if (location.hasBearing())
                renderer.setGPSBearing(location.getBearing());
            if (location.hasSpeed())
                renderer.setGPSSpeed(location.getSpeed());

            renderer.invalidate();
        }
    };
}