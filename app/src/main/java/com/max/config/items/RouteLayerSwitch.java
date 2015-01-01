package com.max.config.items;

import com.max.config.Config;
import com.max.config.ConfigItemSwitch;

public class RouteLayerSwitch extends ConfigItemSwitch {
    public RouteLayerSwitch(String title, Config config) {
        super(title, config);
    }

    @Override
    protected boolean getSelection() {
        return getConfig().showRoute;
    }

    @Override
    protected void setSelection(boolean selected) {
        getConfig().showRoute = selected;
    }
}
