package com.pinmaster.app;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import java.util.List;

public class KioskAppAdapter extends BaseAdapter {

    private final Context context;
    private final List<AppModel> apps;

    public KioskAppAdapter(Context context, List<AppModel> apps) {
        this.context = context;
        this.apps = apps;
    }

    @Override public int getCount()              { return apps.size(); }
    @Override public Object getItem(int pos)     { return apps.get(pos); }
    @Override public long getItemId(int pos)     { return pos; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_kiosk_app, parent, false);
            holder = new ViewHolder();
            holder.icon = convertView.findViewById(R.id.kiosk_app_icon);
            holder.name = convertView.findViewById(R.id.kiosk_app_name);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        AppModel app = apps.get(position);
        holder.icon.setImageDrawable(app.icon);
        holder.name.setText(app.label);
        return convertView;
    }

    static class ViewHolder {
        ImageView icon;
        TextView name;
    }
}
