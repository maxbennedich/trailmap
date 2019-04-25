package com.max.latlng;

/**
 * Class to represent a latitude/longitude pair.
 *
 * (c) 2006 Jonathan Stott
 *
 * Created on 11-02-2006
 *
 * @author Jonathan Stott
 * @version 1.0
 * @since 1.0
 */
public class LatLng {

    /**
     * Latitude in degrees.
     */
    private double lat;

    /**
     * Longitude in degrees.
     */
    private double lng;


    /**
     * Create a new LatLng object to represent a latitude/longitude pair.
     *
     * @param lat
     *          the latitude in degrees
     * @param lng
     *          the longitude in degrees
     * @since 1.0
     */
    public LatLng(double lat, double lng) {
        this.lat = lat;
        this.lng = lng;
    }


    /**
     * Get a String representation of this LatLng object.
     *
     * @return a String representation of this LatLng object.
     * @since 1.0
     */
    public String toString() {
        return "(" + this.lat + ", " + this.lng + ")";
    }


    /**
     * Convert this latitude and longitude to a UTM reference.
     *
     * @return the converted UTM reference
     * @since 1.0
     */
    public UTMRef toUTMRef() {
        double longitude = this.lng;
        double latitude = this.lat;

        int longitudeZone = (int) Math.floor((longitude + 180.0) / 6.0) + 1;

        // Special zone for Norway
        if (latitude >= 56.0 && latitude < 64.0 && longitude >= 3.0
                && longitude < 12.0) {
            longitudeZone = 32;
        }

        // Special zones for Svalbard
        if (latitude >= 72.0 && latitude < 84.0) {
            if (longitude >= 0.0 && longitude < 9.0) {
                longitudeZone = 31;
            } else if (longitude >= 9.0 && longitude < 21.0) {
                longitudeZone = 33;
            } else if (longitude >= 21.0 && longitude < 33.0) {
                longitudeZone = 35;
            } else if (longitude >= 33.0 && longitude < 42.0) {
                longitudeZone = 37;
            }
        }

        return toUTMRef(longitudeZone);
    }

    public UTMRef toUTMRef(int longitudeZone) {
        double UTM_F0 = 0.9996;
        double a = RefEll.WGS84.getMaj();
        double eSquared = RefEll.WGS84.getEcc();

        double longitude = this.lng;
        double latitude = this.lat;
        double latitudeRad = latitude * (Math.PI / 180.0);
        double longitudeRad = longitude * (Math.PI / 180.0);

        double longitudeOrigin = (longitudeZone - 1) * 6 - 180 + 3;
        double longitudeOriginRad = longitudeOrigin * (Math.PI / 180.0);

        char UTMZone = UTMRef.getUTMLatitudeZoneLetter(latitude);

        double ePrimeSquared = (eSquared) / (1 - eSquared);

        double n =
                a
                        / Math.sqrt(1 - eSquared * Math.sin(latitudeRad)
                        * Math.sin(latitudeRad));
        double t = Math.tan(latitudeRad) * Math.tan(latitudeRad);
        double c = ePrimeSquared * Math.cos(latitudeRad) * Math.cos(latitudeRad);
        double A = Math.cos(latitudeRad) * (longitudeRad - longitudeOriginRad);

        double M =
                a
                        * ((1 - eSquared / 4 - 3 * eSquared * eSquared / 64 - 5 * eSquared
                        * eSquared * eSquared / 256)
                        * latitudeRad
                        - (3 * eSquared / 8 + 3 * eSquared * eSquared / 32 + 45
                        * eSquared * eSquared * eSquared / 1024)
                        * Math.sin(2 * latitudeRad)
                        + (15 * eSquared * eSquared / 256 + 45 * eSquared * eSquared
                        * eSquared / 1024) * Math.sin(4 * latitudeRad) - (35
                        * eSquared * eSquared * eSquared / 3072)
                        * Math.sin(6 * latitudeRad));

        double UTMEasting =
                (UTM_F0
                        * n
                        * (A + (1 - t + c) * Math.pow(A, 3.0) / 6 + (5 - 18 * t + t * t
                        + 72 * c - 58 * ePrimeSquared)
                        * Math.pow(A, 5.0) / 120) + 500000.0);

        double UTMNorthing =
                (UTM_F0 * (M + n
                        * Math.tan(latitudeRad)
                        * (A * A / 2 + (5 - t + (9 * c) + (4 * c * c)) * Math.pow(A, 4.0)
                        / 24 + (61 - (58 * t) + (t * t) + (600 * c) - (330 * ePrimeSquared))
                        * Math.pow(A, 6.0) / 720)));

        // Adjust for the southern hemisphere
        if (latitude < 0) {
            UTMNorthing += 10000000.0;
        }

        return new UTMRef(UTMEasting, UTMNorthing, UTMZone, longitudeZone);
    }

    /**
     * Calculate the surface distance in kilometres from the this LatLng to the
     * given LatLng.
     *
     * @param ll
     * @return the surface distance in km
     * @since 1.0
     */
    public double distance(LatLng ll) {
        double er = 6366.707;

        double latFrom = Math.toRadians(getLat());
        double latTo = Math.toRadians(ll.getLat());
        double lngFrom = Math.toRadians(getLng());
        double lngTo = Math.toRadians(ll.getLng());

        double d =
                Math.acos(Math.sin(latFrom) * Math.sin(latTo) + Math.cos(latFrom)
                        * Math.cos(latTo) * Math.cos(lngTo - lngFrom))
                        * er;

        return d;
    }


    /**
     * Return the latitude in degrees.
     *
     * @return the latitude in degrees
     * @since 1.0
     */
    public double getLat() {
        return lat;
    }


    /**
     * Return the longitude in degrees.
     *
     * @return the longitude in degrees
     * @since 1.0
     */
    public double getLng() {
        return lng;
    }
}
