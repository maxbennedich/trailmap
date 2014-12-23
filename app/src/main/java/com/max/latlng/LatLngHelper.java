package com.max.latlng;

import com.max.logic.XY;
import com.max.logic.XYd;

public class LatLngHelper {
    public static XYd getXYdFromLatLng(double lat, double lng) {
        LatLng latLng = new LatLng(lat, lng);
        UTMRef utm = latLng.toUTMRef(33);
        return new XYd(utm.getEasting(), utm.getNorthing());
    }

    public static XY getXYFromLatLng(double lat, double lng) {
        LatLng latLng = new LatLng(lat, lng);
        UTMRef utm = latLng.toUTMRef(33);
        return new XY((int)(utm.getEasting()+0.5), (int)(utm.getNorthing()+0.5));
    }
}
