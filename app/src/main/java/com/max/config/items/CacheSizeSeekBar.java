package com.max.config.items;

import com.max.config.Config;
import com.max.config.ConfigItemSeekBar;

public class CacheSizeSeekBar extends ConfigItemSeekBar {
    public CacheSizeSeekBar(Config config) {
        super(config, "Cache Size");
    }

    @Override
    protected int getProgress() {
        return 0;
    }

    @Override
    protected void setProgress(int progress) {

    }
}
