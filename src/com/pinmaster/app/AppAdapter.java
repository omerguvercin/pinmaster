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

        // Kilit Modu Rozeti (Sabit Kilit / Sahte Uyarı / Sayaç)
        if (app.isPinned()) {
            holder.lockMode.setVisibility(View.VISIBLE);
            int currentMode = SettingsManager.getAppLockMode(context, app.getPackageName());
            holder.lockMode.setText(SettingsManager.getLockModeLabel(context, app.getPackageName()));
            int colorRes = R.color.accent_cyan;
            if (currentMode == SettingsManager.LOCK_MODE_FAKE_CRASH) {
                colorRes = R.color.accent_violet;
            } else if (currentMode == SettingsManager.LOCK_MODE_TIMER) {
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
        final String[] options = {"Sabit Kilit", "Sahte Uyarı", "Sayaç Kilit"};
        int currentMode = SettingsManager.getAppLockMode(context, app.getPackageName());
        int selectedIndex = 0;
        if (currentMode == SettingsManager.LOCK_MODE_FAKE_CRASH) {
            selectedIndex = 1;
        } else if (currentMode == SettingsManager.LOCK_MODE_TIMER) {
            selectedIndex = 2;
        }

        new AlertDialog.Builder(context)
                .setTitle("Kilit Türü")
                .setSingleChoiceItems(options, selectedIndex, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == 0) {
                        SettingsManager.setAppLockMode(context, app.getPackageName(), SettingsManager.LOCK_MODE_PIN);
                        notifyDataSetChanged();
                    } else if (which == 1) {
                        SettingsManager.setAppLockMode(context, app.getPackageName(), SettingsManager.LOCK_MODE_FAKE_CRASH);
                        notifyDataSetChanged();
                    } else if (which == 2) {
                        showTimerDurationDialog(app);
                    }
                })
                .setNegativeButton("İptal", null)
                .show();
    }

    private void showTimerDurationDialog(AppModel app) {
        final int[] durations = {1, 5, 10, 15, 20, 30, 45, 60};
        final String[] labels = {"1 dakika", "5 dakika", "10 dakika", "15 dakika", "20 dakika", "30 dakika", "45 dakika", "60 dakika"};
        int currentMinutes = SettingsManager.getAppTimerMinutes(context, app.getPackageName());
        int selectedIndex = 3; // Varsayılan 15 dk
        for (int i = 0; i < durations.length; i++) {
            if (durations[i] == currentMinutes) {
                selectedIndex = i;
                break;
            }
        }

        new AlertDialog.Builder(context)
                .setTitle("Kullanım Süresi")
                .setSingleChoiceItems(labels, selectedIndex, (dialog, which) -> {
                    int chosenMinutes = durations[which];
                    SettingsManager.setAppTimerMinutes(context, app.getPackageName(), chosenMinutes);
                    SettingsManager.setAppLockMode(context, app.getPackageName(), SettingsManager.LOCK_MODE_TIMER);
                    notifyDataSetChanged();
                    dialog.dismiss();
                })
                .setNegativeButton("İptal", null)
                .show();
    }
}
