package com.max.config;

public class Config {
    public OptionValue<Boolean> showGpsTrace = new OptionValue<>(true);
    public OptionValue<Boolean> followGps = new OptionValue<>(true);
    public OptionValue<Boolean> showRoute = new OptionValue<>(true);
    public OptionValue<Boolean> showPointsOfInterest = new OptionValue<>(false);
    public OptionValue<Boolean> gpsEnabled = new OptionValue<>(false);

    /** Enabling this will trigger gps location to be updated by "random walk". */
    public OptionValue<Boolean> mockLocationService = new OptionValue<>(false);

    /** Whether user touch will set gps location. (Used for testing.) */
    public OptionValue<Boolean> touchLocationService = new OptionValue<>(false);

    public OptionValue<Boolean> lockOrientation = new OptionValue<>(false);
    public OptionValue<Boolean> resetDistance = new OptionValue<>(false);
    public OptionValue<Integer> mapBrightness = new OptionValue<>(100);
}
