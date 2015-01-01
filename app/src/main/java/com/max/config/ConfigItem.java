package com.max.config;

import android.view.View;
import android.widget.TextView;

import com.max.main.R;

public abstract class ConfigItem {
    final private Config config;
    final protected String title;

    public ConfigItem(String title, Config config) {
        this.title = title;
        this.config = config;
    }

    public void buildView(View view) {
        TextView titleView = (TextView) view.findViewById(R.id.title);
        titleView.setText(title);
    }

    protected Config getConfig() {
        return config;
    }

    protected abstract int getResource();
}
