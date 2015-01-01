package com.max.config.items;

import com.max.config.Config;
import com.max.config.ConfigItemSwitch;

public class PointsOfInterestLayerSwitch extends ConfigItemSwitch {
    public PointsOfInterestLayerSwitch(Config config) {
        super(config, "Points of Interest");
    }

    @Override
    protected boolean getSelection() {
        return getConfig().showPointsOfInterest;
    }

    @Override
    protected void setSelection(boolean selected) {
        getConfig().showPointsOfInterest = selected;
    }
}
