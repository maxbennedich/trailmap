package com.max.config.items;

import com.max.config.Config;
import com.max.config.ConfigItemSwitch;

public class PointsOfInterestLayerSwitch extends ConfigItemSwitch {
    public PointsOfInterestLayerSwitch(String title, Config config) {
        super(title, config);
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
