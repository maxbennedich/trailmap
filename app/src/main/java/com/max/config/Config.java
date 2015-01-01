package com.max.config;

public class Config {
    public boolean showGpsTrace = true;
    public boolean showRoute = false;
    public boolean showPointsOfInterest = false;
    public OptionValue<Boolean> gpsEnabled = new OptionValue<>(false);
    public OptionValue<Boolean> mockLocationService = new OptionValue<>(true);
}
