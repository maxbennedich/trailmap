package com.max.config;

import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.style.UnderlineSpan;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Switch;
import android.widget.TextView;

import com.max.main.R;

public class ConfigItemLabel extends ConfigItem {
    public ConfigItemLabel(String title) {
        super(title, null);
    }

    @Override public void buildView(View view) {
        TextView labelView = (TextView) view.findViewById(R.id.title);
        Typeface tf = Typeface.create("sans-serif-light", Typeface.ITALIC);
        labelView.setText(title);
        labelView.setTypeface(tf);
        view.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent event) { return true; }
        });
//        view.setBackgroundColor(0x7f7f0000);
    }

    @Override protected int getResource() {
        return R.layout.config_divider;
    }
}
