package com.max.config;

import android.view.View;
import android.widget.Switch;

import com.max.main.R;

public abstract class ConfigItemSwitch extends ConfigItem<Boolean> implements View.OnClickListener {
    public ConfigItemSwitch(String title, OptionValue<Boolean> value) {
        super(title, value);
    }

    private Switch switchButton;

    @Override public void buildView(View view) {
        super.buildView(view);

        switchButton = (Switch) view.findViewById(R.id.switchButton);

        switchButton.setChecked(value.value);
        switchButton.setOnClickListener(this);
    }

    @Override public void onClick(View v) {
        value.value = !value.value;
        onUpdate();
//                        drawerList.setItemChecked(position, selected[position]);
//                        drawerLayout.closeDrawer(drawerList);
    }

    @Override protected int getResource() {
        return R.layout.config_switch;
    }

    /** Call this to programmatically turn on or off the switch. */
    public void forceClick() {
        switchButton.performClick();
    }
}
