package com.max.route;

import android.location.Location;
import android.os.Environment;
import android.util.Log;

import com.max.main.Settings;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * The idea of this class is to support real time logging (persisting) of any events related to
 * navigation, mainly the raw GPS updates. The motivation is that if the app crashes or bugs in the
 * code cause data to be misrepresented, the raw data will still be available for later analysis.
 */
public class NavigationLogger {
    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    /** This number is used to avoid opening and closing the log file too often in case of
     * frequent log writes. GPS fixes for example tend to arrive one per second. */
    private static final int LOG_CACHE_SIZE = 30;

    private static String[] LOG = new String[LOG_CACHE_SIZE];

    private static int logIdx = 0;

    public static void appStarted() {
        log("appStarted");
    }

    /** Bearing and speed may be null. */
    public static void gpsUpdate(Location location, double utmX, double utmY) {
        log("gpsUpdate" +
                String.format(" utmX=%.2f utmY=%.2f", utmX, utmY) +
                (location.hasAccuracy() ? " accuracy=" + location.getAccuracy() : "") +
                (location.hasSpeed() ? " speed=" + location.getSpeed() : "") +
                (location.hasBearing() ? " bearing=" + location.getBearing() : "") +
                " time=" + location.getTime() +
                " lat=" + location.getLatitude() +
                " long=" + location.getLongitude() +
                (location.hasAltitude() ? " alt=" + location.getAltitude() : "") +
                " provider=" + location.getProvider(),
                false);
    }

    public static void arrivedWaypoint(int idx) {
        log("arriveWaypoint idx="+idx);
    }

    public static void startNavigation() {
        log("startNavigation");
    }

    public static void stopNavigation() {
        log("stopNavigation");
    }

    public static void routeCompleted() {
        log("routeCompleted");
    }

    private static String getTimestamp() {
        return DATE_FORMAT.format(new Date());
    }

    private static void log(String str) {
        log(str, true);
    }

    private static void log(String str, boolean flush) {
        LOG[logIdx++] = getTimestamp() + " " + str;
        if (flush || logIdx == LOG_CACHE_SIZE) {
            try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(Settings.NAVIGATION_LOG_FILE, true)))) {
                for (int r = 0; r < logIdx; ++r)
                    out.println(LOG[r]);
            } catch (IOException ioe) {
                Log.e(NavigationLogger.class.getSimpleName(), "Error writing to log file (" + Settings.NAVIGATION_LOG_FILE + ")", ioe);
            }
            logIdx = 0;
        }
    }
}
