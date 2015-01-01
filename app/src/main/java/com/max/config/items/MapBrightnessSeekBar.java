package com.max.config.items;

import com.max.config.Config;
import com.max.config.ConfigItemSeekBar;

public class MapBrightnessSeekBar extends ConfigItemSeekBar {
    public MapBrightnessSeekBar(String title, Config config) {
        super(title, config);
    }

    @Override
    protected int getProgress() {
        return 0;
    }

    @Override
    protected void setProgress(int progress) {

    }
}
