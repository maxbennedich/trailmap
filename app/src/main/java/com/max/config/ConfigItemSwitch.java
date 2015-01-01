package com.max.config;

import android.view.View;
import android.widget.Switch;

import com.max.main.R;

public abstract class ConfigItemSwitch extends ConfigItem<Boolean> implements View.OnClickListener {
    public ConfigItemSwitch(String title, OptionValue<Boolean> value) {
        super(title, value);
    }

    @Override public void buildView(View view) {
        super.buildView(view);

        Switch switchButton = (Switch) view.findViewById(R.id.switchButton);

        switchButton.setChecked(value.value);
        switchButton.setOnClickListener(this);
    }

    @Override public void onClick(View v) {
        value.value = !value.value;
        select(value.value);
//                        drawerList.setItemChecked(position, selected[position]);
//                        drawerLayout.closeDrawer(drawerList);
    }

    @Override protected int getResource() {
        return R.layout.config_switch;
    }
}
