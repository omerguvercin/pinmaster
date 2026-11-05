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

    public void resort() {
        if (fullList != null) {
            Collections.sort(fullList);
        }
        if (displayList != null) {
            Collections.sort(displayList);
        }
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

        // Kilit Modu Rozeti (Kilitli / Kilitli: 15dk / Sahte Uyarı)
        if (app.isPinned()) {
            holder.lockMode.setVisibility(View.VISIBLE);
            int currentMode = SettingsManager.getAppLockMode(context, app.getPackageName());
            int timerMin = SettingsManager.getAppTimerMinutes(context, app.getPackageName());
            holder.lockMode.setText(SettingsManager.getLockModeLabel(context, app.getPackageName()));
            int colorRes = R.color.accent_cyan;
            if (currentMode == SettingsManager.LOCK_MODE_FAKE_CRASH) {
                colorRes = R.color.accent_violet;
            } else if (timerMin > 0) {
                colorRes = R.color.accent_orange;
            }
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
            resort();
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
        final String[] options = {
                "Kilitli (Süresiz)",
                "Kilitli: 1 dakika",
                "Kilitli: 5 dakika",
                "Kilitli: 10 dakika",
                "Kilitli: 15 dakika",
                "Kilitli: 20 dakika",
                "Kilitli: 30 dakika",
                "Kilitli: 45 dakika",
                "Kilitli: 60 dakika",
                "Sahte Uyarı"
        };
        final int[] durations = {0, 1, 5, 10, 15, 20, 30, 45, 60, -1};

        int currentMode = SettingsManager.getAppLockMode(context, app.getPackageName());
        int currentMin = SettingsManager.getAppTimerMinutes(context, app.getPackageName());

        int selectedIndex = 0; // Kilitli (Süresiz)
        if (currentMode == SettingsManager.LOCK_MODE_FAKE_CRASH) {
            selectedIndex = options.length - 1; // Sahte Uyarı
        } else {
            for (int i = 0; i < durations.length - 1; i++) {
                if (durations[i] == currentMin) {
                    selectedIndex = i;
                    break;
                }
            }
        }

        new AlertDialog.Builder(context)
                .setTitle("Kilit Ayarı")
                .setSingleChoiceItems(options, selectedIndex, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == options.length - 1) {
                        SettingsManager.setAppLockMode(context, app.getPackageName(), SettingsManager.LOCK_MODE_FAKE_CRASH);
                    } else {
                        int chosenMin = durations[which];
                        SettingsManager.setAppLockMode(context, app.getPackageName(), SettingsManager.LOCK_MODE_PIN);
                        SettingsManager.setAppTimerMinutes(context, app.getPackageName(), chosenMin);
                    }
                    resort();
                })
                .setNegativeButton("İptal", null)
                .show();
    }
}
