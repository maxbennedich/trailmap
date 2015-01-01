package com.max.config;

import android.view.View;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import com.max.main.Main;
import com.max.main.R;

public abstract class ConfigItemSeekBar extends ConfigItem {
    public ConfigItemSeekBar(String title, Config config) {
        super(title, config);
    }

    @Override public void buildView(View view) {
        super.buildView(view);

        SeekBar seekBar = (SeekBar) view.findViewById(R.id.seekbar);
        final TextView seekText = (TextView) view.findViewById(R.id.seektext);

        Main.globalSeekBar = seekBar;
        seekBar.setProgress(getProgress());
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                seekText.setText(Integer.toString(progress));
                setProgress(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
    }

    @Override protected int getResource() {
        return R.layout.config_seek_bar;
    }

    abstract protected int getProgress();

    abstract protected void setProgress(int progress);
}
