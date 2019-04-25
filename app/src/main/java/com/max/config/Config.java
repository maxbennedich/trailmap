package com.max.config;

import android.os.Bundle;

import com.max.main.Persistable;

public class Config implements Persistable {
    public OptionValue<Boolean> showGpsTrace = new OptionValue<>(false);
    public OptionValue<Boolean> followGps = new OptionValue<>(false);
    public OptionValue<Boolean> showRoute = new OptionValue<>(false);
    public OptionValue<Boolean> showPointsOfInterest = new OptionValue<>(false);

    /** Navigate mode, tracking progress along waypoints. */
    public OptionValue<Boolean> navigateEnabled = new OptionValue<>(false);

    public OptionValue<Boolean> gpsEnabled = new OptionValue<>(false);

    /** Enabling this will trigger gps location to be updated by "random walk". */
    public OptionValue<Boolean> mockLocationService = new OptionValue<>(false);

    /** Whether user touch will set gps location. (Used for testing.) */
    public OptionValue<Boolean> touchLocationService = new OptionValue<>(false);

    public OptionValue<Boolean> lockOrientation = new OptionValue<>(false);
    public OptionValue<Boolean> resetDistance = new OptionValue<>(false);
    public OptionValue<Integer> mapBrightness = new OptionValue<>(100);

    @Override
    public void saveInstanceState(Bundle savedInstanceState, String prefix) {
        savedInstanceState.putBoolean(prefix + "showGpsTrace", showGpsTrace.value);
        savedInstanceState.putBoolean(prefix + "followGps", followGps.value);
        savedInstanceState.putBoolean(prefix + "showRoute", showRoute.value);
        savedInstanceState.putBoolean(prefix + "showPointsOfInterest", showPointsOfInterest.value);
        savedInstanceState.putBoolean(prefix + "navigateEnabled", navigateEnabled.value);
        savedInstanceState.putBoolean(prefix + "gpsEnabled", gpsEnabled.value);
        savedInstanceState.putBoolean(prefix + "mockLocationService", mockLocationService.value);
        savedInstanceState.putBoolean(prefix + "touchLocationService", touchLocationService.value);
        savedInstanceState.putBoolean(prefix + "lockOrientation", lockOrientation.value);
        // not resetDistance (not a true switch option)
        savedInstanceState.putInt(prefix + "mapBrightness", mapBrightness.value);
    }

    @Override
    public void restoreInstanceState(Bundle savedInstanceState, String prefix) {
        showGpsTrace.value = savedInstanceState.getBoolean(prefix + "showGpsTrace");
        followGps.value = savedInstanceState.getBoolean(prefix + "followGps");
        showRoute.value = savedInstanceState.getBoolean(prefix + "showRoute");
        showPointsOfInterest.value = savedInstanceState.getBoolean(prefix + "showPointsOfInterest");
        navigateEnabled.value = savedInstanceState.getBoolean(prefix + "navigateEnabled");
        gpsEnabled.value = savedInstanceState.getBoolean(prefix + "gpsEnabled");
        mockLocationService.value = savedInstanceState.getBoolean(prefix + "mockLocationService");
        touchLocationService.value = savedInstanceState.getBoolean(prefix + "touchLocationService");
        lockOrientation.value = savedInstanceState.getBoolean(prefix + "lockOrientation");
        // not resetDistance (not a true switch option)
        mapBrightness.value = savedInstanceState.getInt(prefix + "mapBrightness");
    }
}
