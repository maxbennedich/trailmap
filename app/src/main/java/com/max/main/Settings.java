package com.max.main;

import android.os.Environment;

import java.io.File;

/**
 * This class contains most of the route and device specific settings in the app. In addition, the
 * initial config can be found in the Config class. More technical settings can be found in the
 * Renderer class.
 *
 * To generate the tiles, use the independent LantmaterietTileDownloader class. This can download
 * tiles along a KML route, an outline (marked up) map, or within a rectangular area. Then push
 * them to the device, e.g. through:
 *
 * ./adb.exe push -p c:\\dev\\maps\\tiles\\sormlandsleden /sdcard/tiles
 *
 * To generate the route resource, use the Java program tilerenderer.RouteExporter.
 */
public class Settings {
    public static double START_CENTER_UTM_X = 673_905, START_CENTER_UTM_Y = 6_581_834; // holländargatan
//    public static double START_CENTER_UTM_X = 696_910, START_CENTER_UTM_Y = 6_393_950; // visby
//    public static double START_CENTER_UTM_X = 638_959, START_CENTER_UTM_Y = 6_548_129; // mölnbo

    public static int ROUTE_RESOURCE = R.raw.sormlandsleden;
    public static int WAYPOINTS_RESOURCE = R.raw.sormlandsleden_waypoints;
    public static int POINTS_OF_INTEREST_RESOURCE = R.raw.sormlandsleden_pois;

    public static boolean WAYPOINTS_NUMBERED = false; // Gotland: true

    public static final File TILE_ROOT_PATH = new File(Environment.getExternalStorageDirectory().getAbsolutePath(), "tiles");

    public static final String NAVIGATION_LOG_FILE = new File(Environment.getExternalStorageDirectory(), "navigationLog.txt").getAbsolutePath();
}
