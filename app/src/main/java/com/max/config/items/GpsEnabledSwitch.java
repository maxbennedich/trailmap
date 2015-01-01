package com.max.config.items;

import com.max.config.Config;
import com.max.config.ConfigItemSwitch;

public class GpsEnabledSwitch extends ConfigItemSwitch {
    public GpsEnabledSwitch(String title, Config config) {
        super(title, config);
    }

    @Override
    protected boolean getSelection() {
        return getConfig().gpsEnabled;
    }

    @Override
    protected void setSelection(boolean selected) {
        getConfig().gpsEnabled = selected;
        if (selected) {

        }
    }
}
