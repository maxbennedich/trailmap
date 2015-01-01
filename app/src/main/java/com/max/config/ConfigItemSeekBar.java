package com.max.config;

import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import com.max.main.Controller;
import com.max.main.R;

public abstract class ConfigItemSeekBar extends ConfigItem<Integer> {
    public ConfigItemSeekBar(String title, OptionValue<Integer> value) {
        super(title, value);
    }

    @Override public void buildView(View view) {
        super.buildView(view);

        SeekBar seekBar = (SeekBar) view.findViewById(R.id.seekbar);
        final TextView seekText = (TextView) view.findViewById(R.id.seektext);

        Controller.globalSeekBar = seekBar;
        seekBar.setProgress(value.value);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                seekText.setText(Integer.toString(progress));
                select(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
    }

    @Override protected int getResource() {
        return R.layout.config_seek_bar;
    }
}
