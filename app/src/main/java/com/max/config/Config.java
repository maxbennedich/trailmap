package com.max.config;

public class Config {
    public OptionValue<Boolean> showGpsTrace = new OptionValue<>(true);
    public OptionValue<Boolean> showRoute = new OptionValue<>(false);
    public OptionValue<Boolean> showPointsOfInterest = new OptionValue<>(false);
    public OptionValue<Boolean> gpsEnabled = new OptionValue<>(false);
    public OptionValue<Boolean> mockLocationService = new OptionValue<>(false);
    public OptionValue<Integer> mapBrightness = new OptionValue<>(65);
}
