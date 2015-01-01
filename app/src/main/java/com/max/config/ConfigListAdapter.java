package com.max.config;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import com.max.main.R;

import java.util.List;

public class ConfigListAdapter extends BaseAdapter {
    private Context context;
    private List<ConfigItem<?>> configItems;

    public ConfigListAdapter(Context context, List<ConfigItem<?>> configItems){
        this.context = context;
        this.configItems = configItems;
    }

    @Override
    public int getCount() {
        return configItems.size();
    }

    @Override
    public Object getItem(int position) {
        return configItems.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ConfigItem item = configItems.get(position);

        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Activity.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(item.getResource(), null);
        }

        item.buildView(convertView);

        return convertView;
    }

}
