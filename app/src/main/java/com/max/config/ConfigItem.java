package com.max.config;

import android.view.View;
import android.widget.TextView;

import com.max.main.R;

public abstract class ConfigItem<V> {
    final protected String title;

    protected OptionValue<V> value;

    public ConfigItem(String title, OptionValue<V> value) {
        this.title = title;
        this.value = value;
    }

    public void buildView(View view) {
        TextView titleView = (TextView) view.findViewById(R.id.title);
        titleView.setText(title);
    }

    abstract protected int getResource();

    abstract protected void select(V selected);
}
