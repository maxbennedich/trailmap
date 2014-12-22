package com.max.latlng;

import com.max.logic.XY;

public class LatLngHelper {
    public static XY getXYFromLatLng(double lat, double lng) {
        LatLng latLng = new LatLng(lat, lng);
        UTMRef utm = latLng.toUTMRef(33);
        return new XY((int)(utm.getEasting()+0.5), (int)(utm.getNorthing()+0.5));
    }
}
