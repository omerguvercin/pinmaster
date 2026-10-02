package com.pinmaster.app;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AppAdapter extends BaseAdapter {
    private Context context;
    private List<AppModel> fullList;
    private List<AppModel> displayList;
    private OnAppPinChangeListener pinChangeListener;

    public interface OnAppPinChangeListener {
        void onPinChanged(AppModel app, boolean isPinned);
    }

    public AppAdapter(Context context, List<AppModel> apps, OnAppPinChangeListener listener) {
        this.context = context;
        this.fullList = apps;
        this.displayList = new ArrayList<>(apps);
        this.pinChangeListener = listener;
        Collections.sort(this.displayList);
    }

    public void filter(String query) {
        displayList.clear();
        if (query == null || query.trim().isEmpty()) {
            displayList.addAll(fullList);
        } else {
            String lower = query.toLowerCase().trim();
            for (AppModel app : fullList) {
                if (app.getAppName().toLowerCase().contains(lower) || 
                    app.getPackageName().toLowerCase().contains(lower)) {
                    displayList.add(app);
                }
            }
        }
        Collections.sort(displayList);
        notifyDataSetChanged();
    }

    public void updateList(List<AppModel> apps) {
        this.fullList = apps;
        this.displayList = new ArrayList<>(apps);
        Collections.sort(this.displayList);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return displayList.size();
    }

    @Override
    public AppModel getItem(int position) {
        return displayList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    private static class ViewHolder {
        ImageView icon;
        TextView name;
        TextView pkg;
        Switch toggle;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_app, parent, false);
            holder = new ViewHolder();
            holder.icon = convertView.findViewById(R.id.app_icon);
            holder.name = convertView.findViewById(R.id.app_name);
            holder.pkg = convertView.findViewById(R.id.app_package);
            holder.toggle = convertView.findViewById(R.id.app_switch);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        final AppModel app = getItem(position);
        holder.name.setText(app.getAppName());
        holder.pkg.setText(app.getPackageName());
        if (app.getIcon() != null) {
            holder.icon.setImageDrawable(app.getIcon());
        }
        holder.toggle.setChecked(app.isPinned());

        convertView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean newState = !app.isPinned();
                app.setPinned(newState);
                SettingsManager.setPackagePinned(context, app.getPackageName(), newState);
                notifyDataSetChanged();
                if (pinChangeListener != null) {
                    pinChangeListener.onPinChanged(app, newState);
                }
            }
        });

        return convertView;
    }
}
