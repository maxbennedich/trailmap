package com.max.config;

import android.view.View;
import android.widget.Button;
import android.widget.Switch;

import com.max.main.R;

public abstract class ConfigItemButton extends ConfigItem<Void> implements View.OnClickListener {
    public ConfigItemButton(String title) {
        super(title, null);
    }

    private Button button;

    @Override public void buildView(View view) {
//        super.buildView(view);

        button = (Button) view.findViewById(R.id.button);

        // NPE here with null button if viewing the menu in landscape mode

        button.setText(title);

        button.setOnClickListener(this);
    }

    @Override public void onClick(View v) {
        onUpdate();
//                        drawerList.setItemChecked(position, selected[position]);
//                        drawerLayout.closeDrawer(drawerList);
    }

    @Override protected int getResource() {
        return R.layout.config_button;
    }
}
