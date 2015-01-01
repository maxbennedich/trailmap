package com.max.config.items;

import com.max.config.Config;
import com.max.config.ConfigItemSwitch;

public class GpsTraceLayerSwitch extends ConfigItemSwitch {
    public GpsTraceLayerSwitch(Config config) {
        super(config, "GPS Trace");
    }

    @Override
    protected boolean getSelection() {
        return getConfig().showGpsTrace;
    }

    @Override
    protected void setSelection(boolean selected) {
        getConfig().showGpsTrace = selected;
    }
}
