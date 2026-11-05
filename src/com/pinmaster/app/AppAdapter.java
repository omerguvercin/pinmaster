package com.pinmaster.app;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
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
        TextView lockMode;
        Button btnLaunch;
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
            holder.lockMode = convertView.findViewById(R.id.tv_lock_mode);
            holder.btnLaunch = convertView.findViewById(R.id.btn_launch_app);
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

        // Kilit Modu Rozeti (Sabit Kilit / Sahte Uyarı)
        if (app.isPinned()) {
            holder.lockMode.setVisibility(View.VISIBLE);
            int currentMode = SettingsManager.getAppLockMode(context, app.getPackageName());
            holder.lockMode.setText(SettingsManager.getLockModeLabel(currentMode));
            int colorRes = (currentMode == SettingsManager.LOCK_MODE_FAKE_CRASH) 
                    ? R.color.accent_violet 
                    : R.color.accent_cyan;
            holder.lockMode.setTextColor(context.getResources().getColor(colorRes));

            holder.lockMode.setOnClickListener(v -> showModeDialog(app));
        } else {
            holder.lockMode.setVisibility(View.GONE);
            holder.lockMode.setOnClickListener(null);
        }

        // Satıra veya Switch'e basıldığında PIN kilit durumunu değiştir
        View.OnClickListener toggleListener = v -> {
            boolean newState = !app.isPinned();
            app.setPinned(newState);
            holder.toggle.setChecked(newState);
            SettingsManager.setPackagePinned(context, app.getPackageName(), newState);
            notifyDataSetChanged();
            if (pinChangeListener != null) {
                pinChangeListener.onPinChanged(app, newState);
            }
        };

        convertView.setOnClickListener(toggleListener);
        holder.toggle.setOnClickListener(toggleListener);

        // Satıra uzun basıldığında da kilit türü menüsü açılsın
        convertView.setOnLongClickListener(v -> {
            if (app.isPinned()) {
                showModeDialog(app);
                return true;
            }
            return false;
        });

        // 'Aç' butonuna (↗) basıldığında uygulamayı doğrudan başlat
        if (holder.btnLaunch != null) {
            holder.btnLaunch.setOnClickListener(v -> {
                try {
                    PackageManager pm = context.getPackageManager();
                    Intent launchIntent = pm.getLaunchIntentForPackage(app.getPackageName());
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(launchIntent);
                    } else {
                        Toast.makeText(context, "Uygulama başlatılamadı: " + app.getAppName(), Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Toast.makeText(context, "Başlatma hatası: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        }

        return convertView;
    }

    private void showModeDialog(AppModel app) {
        final String[] options = {"Sabit Kilit", "Sahte Uyarı"};
        int currentMode = SettingsManager.getAppLockMode(context, app.getPackageName());
        int selectedIndex = (currentMode == SettingsManager.LOCK_MODE_FAKE_CRASH) ? 1 : 0;

        new AlertDialog.Builder(context)
                .setTitle("Kilit Türü")
                .setSingleChoiceItems(options, selectedIndex, (dialog, which) -> {
                    int newMode = (which == 1) ? SettingsManager.LOCK_MODE_FAKE_CRASH : SettingsManager.LOCK_MODE_PIN;
                    SettingsManager.setAppLockMode(context, app.getPackageName(), newMode);
                    notifyDataSetChanged();
                    dialog.dismiss();
                })
                .setNegativeButton("İptal", null)
                .show();
    }
}
