package com.max.config;

import android.content.DialogInterface;
import android.view.View;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import com.max.main.Main;
import com.max.main.R;

public abstract class ConfigItemSwitch extends ConfigItem implements View.OnClickListener {
    public ConfigItemSwitch(String title, Config config) {
        super(title, config);
    }

    @Override public void buildView(View view) {
        super.buildView(view);

        Switch switchButton = (Switch) view.findViewById(R.id.switchButton);

        switchButton.setChecked(getSelection());
        switchButton.setOnClickListener(this);
    }

    @Override public void onClick(View v) {
        setSelection(!getSelection());
//                        drawerList.setItemChecked(position, selected[position]);
//                        drawerLayout.closeDrawer(drawerList);
    }

    @Override protected int getResource() {
        return R.layout.config_switch;
    }

    abstract protected boolean getSelection();
    abstract protected void setSelection(boolean selected);
}
