package com.max.route;

import android.os.Bundle;

import com.max.drawing.Renderer;
import com.max.main.Persistable;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Note: All distances are squared by default. */
public class Navigator implements Persistable {

    /** Maximum distance (squared) considered to be touching a waypoint. This is considered a
     * definite arrival at a waypoint. */
    private static final int TOUCHING_DISTANCE = 50*50;

    /** Maximum distance (squared) considered to be nearby a waypoint. Nearby is not considered
     * arriving at a waypoint per se, but combined with other factors it might be. */
    private static final int NEARBY_DISTANCE = 250*250;

    /** Staying within this distance (squared) for the time period specified by
     * gpsStationaryHistory is considered to be stationary. The reason this threshold is
     * needed is due to GPS noise and minor movements. */
    private static final int STATIONARY_DISTANCE = 100*100;

    private final Renderer renderer;

    private int utmX, utmY;

    private long startMs;

    private long lastUpdateMs;

    private int totalStoppedTimeMs;

    /** Timestamp when navigator was stopped. Used to subtract stopped time from total time. */
    private long stopMs = -1;

    private final GPSHistory leavingWaypointHistory = new GPSHistory(2000, 3);

    private final GPSHistory gpsStationaryHistory = new GPSHistory(1000, 60);

    /** The waypoint that we are expected to arrive at next. */
    private int nextWaypointIdx;

    private boolean routeCompleted;

    /** Maximum number of waypoints to skip if we are arriving at future points. */
    private static final int MAX_SKIP_SIZE = 4;

    /** Arrival status of the next (expected) waypoint and a few more future waypoints. */
    private WaypointStatus[] futureStatus = new WaypointStatus[MAX_SKIP_SIZE + 1];

    /** Index of the last future waypoint we arrived at. (Index into futureStatus; relative to nextWaypointIdx). */
    private int lastFutureWaypointArrivedAtIdx;

    private float topSpeed;

    /** End waypoint idx for time given by waypointTimesMs. Making this a list rather than fixed
     * size array allows it to be cyclical in case the route is repeated. */
    private ArrayList<Integer> waypointTimesIdx;
    private ArrayList<Integer> waypointTimesMs;

    public Navigator(Renderer renderer) {
        this.renderer = renderer;

        for (int k = 0; k < futureStatus.length; ++k)
            futureStatus[k] = new WaypointStatus();
    }

    private long getTimeMs() {
        return System.currentTimeMillis();
    }

    public void initRoute() {
        this.startMs = getTimeMs();
        waypointTimesIdx = new ArrayList<>();
        waypointTimesMs = new ArrayList<>();
        routeCompleted = false;
        totalStoppedTimeMs = 0;
        setNextWaypoint(1);
    }

    @Override
    public void saveInstanceState(Bundle savedInstanceState, String prefix) {
        prefix += "_";

        savedInstanceState.putInt(prefix + "utmX", utmX);
        savedInstanceState.putInt(prefix + "utmY", utmY);

        savedInstanceState.putLong(prefix + "startMs", startMs);
        savedInstanceState.putLong(prefix + "lastUpdateMs", lastUpdateMs);
        savedInstanceState.putInt(prefix + "totalStoppedTimeMs", totalStoppedTimeMs);
        savedInstanceState.putLong(prefix + "stopMs", stopMs);

        savedInstanceState.putInt(prefix + "nextWaypointIdx", nextWaypointIdx);
        savedInstanceState.putInt(prefix + "lastFutureWaypointArrivedAtIdx", lastFutureWaypointArrivedAtIdx);
        savedInstanceState.putBoolean(prefix + "routeCompleted", routeCompleted);

        savedInstanceState.putFloat(prefix + "topSpeed", topSpeed);
        savedInstanceState.putIntegerArrayList(prefix + "waypointTimesIdx", waypointTimesIdx);
        savedInstanceState.putIntegerArrayList(prefix + "waypointTimesMs", waypointTimesMs);

        leavingWaypointHistory.saveInstanceState(savedInstanceState, "leavingWaypointHistory");
        gpsStationaryHistory.saveInstanceState(savedInstanceState, "gpsStationaryHistory");

        for (int k = 0; k < futureStatus.length; ++k)
            futureStatus[k].saveInstanceState(savedInstanceState, "futureStatus[" + k + "]");
    }

    @Override
    public void restoreInstanceState(Bundle savedInstanceState, String prefix) {
        prefix += "_";

        utmX = savedInstanceState.getInt(prefix + "utmX");
        utmY = savedInstanceState.getInt(prefix + "utmY");

        startMs = savedInstanceState.getLong(prefix + "startMs");
        lastUpdateMs = savedInstanceState.getLong(prefix + "lastUpdateMs");
        totalStoppedTimeMs = savedInstanceState.getInt(prefix + "totalStoppedTimeMs");
        stopMs = savedInstanceState.getLong(prefix + "stopMs");

        nextWaypointIdx = savedInstanceState.getInt(prefix + "nextWaypointIdx");
        lastFutureWaypointArrivedAtIdx = savedInstanceState.getInt(prefix + "lastFutureWaypointArrivedAtIdx");
        routeCompleted = savedInstanceState.getBoolean(prefix + "routeCompleted");

        topSpeed = savedInstanceState.getFloat(prefix + "topSpeed");
        waypointTimesMs = savedInstanceState.getIntegerArrayList(prefix + "waypointTimesMs");
        waypointTimesIdx = savedInstanceState.getIntegerArrayList(prefix + "waypointTimesIdx");

        leavingWaypointHistory.restoreInstanceState(savedInstanceState, "leavingWaypointHistory");
        gpsStationaryHistory.restoreInstanceState(savedInstanceState, "gpsStationaryHistory");

        for (int k = 0; k < futureStatus.length; ++k)
            futureStatus[k].restoreInstanceState(savedInstanceState, "futureStatus[" + k + "]");

        // populate derived fields
        updateStats();
    }

    private class WaypointStatus implements Persistable {
        int idx;
        boolean beenNear;

        @Override
        public void saveInstanceState(Bundle savedInstanceState, String prefix) {
            prefix += "_";
            savedInstanceState.putInt(prefix + "idx", idx);
            savedInstanceState.putBoolean(prefix + "beenNear", beenNear);
        }

        @Override
        public void restoreInstanceState(Bundle savedInstanceState, String prefix) {
            prefix += "_";
            idx = savedInstanceState.getInt(prefix + "idx");
            beenNear = savedInstanceState.getBoolean(prefix + "beenNear");
        }

        /** This is used to recycle existing objects rather than creating new ones in order
         * to minimize new allocations. */
        void init(int idx) {
            this.idx = idx;
            beenNear = false;
        }

        /** @return True if having arrived at this waypoint. */
        boolean updatePosition(int utmX, int utmY) {
            boolean arrived = false;

            PointOfInterest point = renderer.waypoints.get(idx);
            long dist = point.dist2(utmX, utmY);
            if (dist < NEARBY_DISTANCE) {
                arrived = dist < TOUCHING_DISTANCE;
                beenNear = true;
            } else if (beenNear) {
                // Test if we've been near the waypoint, then left its vicinity, then have a few GPS
                // readings with monotonically increasing distance.
                long lastDist = -1;
                for (int k = 0; k < leavingWaypointHistory.getSize(); ++k) {
                    long histDist = point.dist2(leavingWaypointHistory.getX(k), leavingWaypointHistory.getY(k));
                    if (histDist < lastDist)
                        break;
                    lastDist = histDist;
                    if (k == leavingWaypointHistory.getSize() - 1)
                        arrived = true;
                }
            }

            if (arrived)
                beenNear = false;
            return arrived;
        }
    }

    public void updatePosition(int utmX, int utmY, float speed) {
        if (routeCompleted)
            return;

        long curTime = getTimeMs();
        lastUpdateMs = curTime;
        lastUpdatedStoppedTime = getRealTimeStoppedTime();

        this.utmX = utmX;
        this.utmY = utmY;

        if (speed > topSpeed)
            topSpeed = speed;

        leavingWaypointHistory.update(curTime, utmX, utmY);

//        if (gpsStationaryHistory.update(curTime, utmX, utmY)) {
//            isStationary();
//        }

        // See if we arrived at a future waypoint. There are basically two scenarios supported:
        //
        // 1. Arriving at the expected next point. This should be the normal case.
        // 2. Arriving at a future point. This is needed in case the expected next point was missed,
        //    for example if the GPS or point location was inaccurate or the object was unreachable.
        //    However, we can not immediately skip forward whenever we arrive at a future point.
        //    Imagine for example that a route goes from A, through C, on to
        //    B, back to C, then on to D (with waypoints ordered A, B, C, D). We must not skip
        //    to C the first time we touch it. Therefore, we require touching a future point
        //    immediately followed by touching its next point in order to skip forward.
        //
        // This implementation will not work well (or at all) for complicated routes that involve
        // a lot of waypoint revisits.
        for (int futureIdx = 0; futureIdx < futureStatus.length; ++futureIdx) {
            if (futureStatus[futureIdx].updatePosition(utmX, utmY)) {
                if (futureIdx == 0 || futureIdx == lastFutureWaypointArrivedAtIdx + 1) {
                    int nextIdx = clamp(nextWaypointIdx + 1 + futureIdx);
                    if (nextWaypointIdx == 0)
                        routeCompleted(nextIdx);
                    else
                        setNextWaypoint(nextIdx);
                    break;
                } else {
                    // arrived at a point without just having arrived at the previous point
                    lastFutureWaypointArrivedAtIdx = futureIdx;
                }
            }
        }

        updateStats();
    }

    /** @return Input waypoint index clamped to 0-size range. */
    private int clamp(int idx) {
        return (idx + renderer.waypoints.size()) % renderer.waypoints.size();
    }

    /** This method accepts indexes outside the range and will wrap them into range correctly. */
    public void setNextWaypoint(int idx) {
        idx = clamp(idx);
        addWaypointTime(idx);

        nextWaypointIdx = idx;
        for (int k = 0; k < futureStatus.length; ++k)
            futureStatus[k].init(clamp(nextWaypointIdx + k));
        lastFutureWaypointArrivedAtIdx = -1;
    }

    /** Called when all waypoints in the route have been reached. */
    private void routeCompleted(int nextIdx) {
        NavigationLogger.routeCompleted();

        addWaypointTime(nextIdx);
        stop();
        routeCompleted = true;
    }

    private void addWaypointTime(int idx) {
        int prev = clamp(idx - 1);
        NavigationLogger.arrivedWaypoint(prev);
        waypointTimesIdx.add(prev);
        waypointTimesMs.add((int)(getTimeMs() - startMs - getStoppedTime()));
    }

    public void start() {
        NavigationLogger.startNavigation();
        if (stopMs != -1) {
            totalStoppedTimeMs += getTimeMs() - stopMs;
            stopMs = -1;
        }
    }

    public void stop() {
        NavigationLogger.stopNavigation();
        stopMs = getTimeMs();
    }

    /** In ms. */
    private int getStoppedTime() {
        return lastUpdatedStoppedTime;
    }

    /** In ms. */
    private int getRealTimeStoppedTime() {
        int t = totalStoppedTimeMs;
        if (stopMs != -1 && !routeCompleted)
            t += getTimeMs() - stopMs;
        return t;
    }

    /** @return True if we are not moving (and haven't been for a while). */
    private boolean isStationary() {
        // find center point of all historic points
        long centerX = 0, centerY = 0;
        for (int k = 0; k < gpsStationaryHistory.getSize(); ++k) {
            centerX += gpsStationaryHistory.getX(k);
            centerY += gpsStationaryHistory.getY(k);
        }
        centerX /= gpsStationaryHistory.getSize();
        centerY /= gpsStationaryHistory.getSize();

        // find maximum distance to center from any point
        long maxDist2 = 0;
        for (int k = 0; k < gpsStationaryHistory.getSize(); ++k) {
            long dx = gpsStationaryHistory.getX(k) - centerX;
            long dy = gpsStationaryHistory.getY(k) - centerY;
            long dist2 = dx*dx + dy*dy;
            if (dist2 > maxDist2)
                maxDist2 = dist2;
        }

        return maxDist2 < STATIONARY_DISTANCE;
    }

    // a few derived fields (calculated by updateStats; no need to persist)
    private int distanceTraveled;
    private int distanceToNextWaypoint;
    private int totalTimeElapsed;
    private int lastUpdatedStoppedTime;

    private void updateStats() {
        int prevIdx = clamp(nextWaypointIdx - 1);

        int routeIdxDiff = (nextWaypointIdx == 0 ? renderer.points.nrPoints : renderer.waypoints.get(nextWaypointIdx).routeIndex) - renderer.waypoints.get(prevIdx).routeIndex;
        int nearestRouteIdx = renderer.segmentQuadRoots[prevIdx].getNearestNeighbor(utmX, utmY, renderer.points);

        // TODO: This is slightly inaccurate (typically +- a few percent) since it equates index
        // with distance which is not correct since a diagonal route is sqrt(2) times the distance
        // of a straight route. A better way would be to store the distance for each route point.
        int distanceFromPrevWaypoint = (int)((float)(nearestRouteIdx - renderer.waypoints.get(prevIdx).routeIndex) / routeIdxDiff * SegmentDistances.SEGMENT_DISTANCES[prevIdx][2] + 0.5);
        distanceToNextWaypoint = SegmentDistances.SEGMENT_DISTANCES[prevIdx][2] - distanceFromPrevWaypoint;

        // In case we've left the route, use aerial distance as a minimum bound.
        // (If, for example, we are approaching the point from the opposite direction than
        // expected, we should not show a distance of 0.)
        long aerialDistance2 = renderer.waypoints.get(nextWaypointIdx).dist2(utmX, utmY);
        if ((long)distanceToNextWaypoint*distanceToNextWaypoint < aerialDistance2)
            distanceToNextWaypoint = (int)(Math.sqrt(aerialDistance2) + 0.5f);

        distanceTraveled = SegmentDistances.SEGMENT_CUMULATIVE_DISTANCES[prevIdx] + distanceFromPrevWaypoint;

        totalTimeElapsed = (int)((lastUpdateMs - startMs + 500) / 1000);
    }

    public PointOfInterest getNextWaypoint() {
        return renderer.waypoints.get(nextWaypointIdx);
    }

//    /** @return A string indicating the progress made. */
//    public String getRouteProgress() {
//        int prevIdx = (nextWaypointIdx + renderer.waypoints.size() - 1) % renderer.waypoints.size();
//        int nearestIdx = renderer.segmentQuadRoots[prevIdx].getNearestNeighbor(utmX, utmY, renderer.points);
//        int nearestFullIdx = renderer.quadRoot.getNearestNeighbor(utmX, utmY, renderer.points);
//        int dist = (int)(0.5 + Math.sqrt((renderer.points.x[nearestIdx] - utmX)*(renderer.points.x[nearestIdx] - utmX) + (renderer.points.y[nearestIdx] - utmY)*(renderer.points.y[nearestIdx] - utmY)));
//        int distFull = (int)(0.5 + Math.sqrt((renderer.points.x[nearestFullIdx] - utmX)*(renderer.points.x[nearestFullIdx] - utmX) + (renderer.points.y[nearestFullIdx] - utmY)*(renderer.points.y[nearestFullIdx] - utmY)));
//        return nearestIdx + " vs " + nearestFullIdx + " dist = " + dist + " / " + distFull;
////        return nextWaypointIdx + " / " + nrWaypoints;
//    }

    /** In meters. */
    public int getDistanceTraveled() {
        return distanceTraveled;
    }

    /** In meters. */
    public int getTotalDistance() {
        return SegmentDistances.SEGMENT_CUMULATIVE_DISTANCES[renderer.waypoints.size()];
    }

    /** In meters. */
    public int getDistanceToNextWaypoint() {
        return distanceToNextWaypoint;
    }

    /** In meters / second. */
    public float getAvgSpeed() {
        int elapsedTime = getElapsedTime();
        return elapsedTime == 0 ? 0 : (float) getDistanceTraveled() / elapsedTime;
    }

    /** In meters / second. */
    public float getTopSpeed() {
        return topSpeed;
    }

    /** In seconds. Not including stopped time. */
    public int getElapsedTime() {
        return totalTimeElapsed - (getStoppedTime() + 500) / 1000;
    }

    public void setStartTime(Date startTime) {
        startMs = startTime.getTime();
    }

    /** @return Estimated time of arrival (assuming a loop starting and ending at waypoint 0). */
    public Date getETA() {
        return new Date(startMs + getTotalTime() * 1000 + getStoppedTime());
    }

    /** In seconds, excluding stopped time. */
    private int getTotalTime() {
        return (int)(getElapsedTime() / ((float) getDistanceTraveled() / getTotalDistance()) + 0.5);
    }

    /** In seconds. */
    public int getRemainingTime() {
        return getTotalTime() - getElapsedTime();
    }

    public String getNavigationStats() {
        String stats = "ROUTE STATISTICS\n" +
                String.format("Dist traveled: %.1f km%n", getDistanceTraveled() * 0.001f) +
                String.format("Avg speed: %.1f km/h%n", getAvgSpeed() * 3.6) +
                String.format("Elapsed time: %s%n", Renderer.formatSeconds(getElapsedTime())) +
                String.format("Stopped time: %s%n", Renderer.formatSeconds((getStoppedTime() + 500) / 1000)) +
                String.format("Dist remaining: %.1f km%n", (getTotalDistance() - getDistanceTraveled()) * 0.001f) +
                String.format("Top speed: %.1f km/h%n", getTopSpeed() * 3.6) +
                "\n" +
                "WAYPOINTS\n";

        for (int k = 1; k < waypointTimesIdx.size(); ++k) {
            int idx = waypointTimesIdx.get(k), prev = waypointTimesIdx.get(k-1);
            int diffMs = waypointTimesMs.get(k) - waypointTimesMs.get(k-1);
            stats += String.format("%s  %d m  %s  %.1f km/h%n",
                    renderer.waypoints.get(idx).name,
                    SegmentDistances.SEGMENT_DISTANCES[prev][2],
                    Renderer.formatSeconds((diffMs + 500) / 1000),
                    (float)SegmentDistances.SEGMENT_DISTANCES[prev][2] * 3600f / diffMs);
        }

        return stats;
    }
}
