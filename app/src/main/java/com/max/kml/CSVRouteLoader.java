package com.max.kml;

import com.max.logic.XY;
import com.max.route.PointOfInterest;
import com.max.route.RoadSurface;
import com.max.route.Route;
import com.max.route.RouteSegment;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class CSVRouteLoader {
    public Route loadRoute(InputStream is) throws InvalidKMLException {
        List<RouteSegment> segments = new ArrayList<>();

        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            String line;
            List<XY> points = new ArrayList<>();
            int lastSurface = -1, prevX = 0, prevY = 0;
            while ((line = br.readLine()) != null) {
                String[] values = line.split(",");
                int surface = Integer.valueOf(values[0]);
                int x = Integer.valueOf(values[1]) + prevX;
                int y = Integer.valueOf(values[2]) + prevY;
                prevX = x;
                prevY = y;
                if (surface != lastSurface) {
                    // new segment
                    if (!points.isEmpty()) {
                        segments.add(new RouteSegment("", RoadSurface.values()[lastSurface], points));
                        points = new ArrayList<>();
                    }
                    lastSurface = surface;
                }
                points.add(new XY(x, y));
            }
            segments.add(new RouteSegment("", RoadSurface.values()[lastSurface], points));

            br.close();
        } catch (IOException ioe) {
            throw new IllegalStateException("Error while loading route", ioe);
        }

        return new Route(segments, new ArrayList<PointOfInterest>());
    }
}
