package com.pinmaster.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {
    private Switch switchMaster;
    private TextView tvStatusTitle;
    private TextView tvSelectedSummary;
    private View layoutWarnings;
    private Button btnFixAccessibility;
    private Button btnFixSystemPin;
    private Button btnToggleMaster;

    private Button tabBtnApps;
    private Button tabBtnSettings;
    private Button tabBtnGuide;

    private View viewTabApps;
    private View viewTabSettings;
    private View viewTabGuide;

    private EditText etSearch;
    private ListView listApps;
    private AppAdapter appAdapter;
    private List<AppModel> appList = new ArrayList<>();

    private SeekBar seekDelay;
    private TextView tvDelayVal;
    private Switch switchVibrate;
    private Button btnOpenSecurity;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupTabs();
        setupSettings();
        setupSearch();
        loadInstalledApps();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateServiceAndSystemStatus();
    }

    private void initViews() {
        switchMaster = findViewById(R.id.switch_master);
        tvStatusTitle = findViewById(R.id.tv_status_title);
        tvSelectedSummary = findViewById(R.id.tv_selected_summary);
        layoutWarnings = findViewById(R.id.layout_warnings);
        btnFixAccessibility = findViewById(R.id.btn_fix_accessibility);
        btnFixSystemPin = findViewById(R.id.btn_fix_system_pin);
        btnToggleMaster = findViewById(R.id.btn_toggle_master);

        btnToggleMaster.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean currentState = SettingsManager.isMasterEnabled(MainActivity.this);
                SettingsManager.setMasterEnabled(MainActivity.this, !currentState);
                updateServiceAndSystemStatus();
            }
        });

        tabBtnApps = findViewById(R.id.tab_btn_apps);
        tabBtnSettings = findViewById(R.id.tab_btn_settings);
        tabBtnGuide = findViewById(R.id.tab_btn_guide);

        viewTabApps = findViewById(R.id.view_tab_apps);
        viewTabSettings = findViewById(R.id.view_tab_settings);
        viewTabGuide = findViewById(R.id.view_tab_guide);

        etSearch = findViewById(R.id.et_search);
        listApps = findViewById(R.id.list_apps);

        seekDelay = findViewById(R.id.seek_delay);
        tvDelayVal = findViewById(R.id.tv_delay_val);
        switchVibrate = findViewById(R.id.switch_vibrate);
        btnOpenSecurity = findViewById(R.id.btn_open_security);

        switchMaster.setChecked(SettingsManager.isMasterEnabled(this));
        switchMaster.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                SettingsManager.setMasterEnabled(MainActivity.this, isChecked);
                updateServiceAndSystemStatus();
            }
        });

        btnFixAccessibility.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                startActivity(intent);
            }
        });

        btnFixSystemPin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Settings.ACTION_SECURITY_SETTINGS);
                startActivity(intent);
            }
        });

        btnOpenSecurity.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Settings.ACTION_SECURITY_SETTINGS);
                startActivity(intent);
            }
        });
    }

    private void setupTabs() {
        tabBtnApps.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectTab(0);
            }
        });
        tabBtnSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectTab(1);
            }
        });
        tabBtnGuide.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectTab(2);
            }
        });
    }

    private void selectTab(int index) {
        viewTabApps.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        viewTabSettings.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        viewTabGuide.setVisibility(index == 2 ? View.VISIBLE : View.GONE);

        int activeColor = getResources().getColor(R.color.accent_cyan);
        int inactiveColor = getResources().getColor(R.color.text_secondary);

        tabBtnApps.setTextColor(index == 0 ? activeColor : inactiveColor);
        tabBtnApps.setBackgroundResource(index == 0 ? R.drawable.card_bg : android.R.color.transparent);

        tabBtnSettings.setTextColor(index == 1 ? activeColor : inactiveColor);
        tabBtnSettings.setBackgroundResource(index == 1 ? R.drawable.card_bg : android.R.color.transparent);

        tabBtnGuide.setTextColor(index == 2 ? activeColor : inactiveColor);
        tabBtnGuide.setBackgroundResource(index == 2 ? R.drawable.card_bg : android.R.color.transparent);
    }

    private void setupSettings() {
        int delay = SettingsManager.getDelayMs(this);
        seekDelay.setProgress(delay);
        tvDelayVal.setText(delay + " ms");

        seekDelay.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (progress < 100) progress = 100;
                tvDelayVal.setText(progress + " ms");
                SettingsManager.setDelayMs(MainActivity.this, progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        switchVibrate.setChecked(SettingsManager.isVibrateEnabled(this));
        switchVibrate.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                SettingsManager.setVibrateEnabled(MainActivity.this, isChecked);
            }
        });
    }

    private void setupSearch() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (appAdapter != null) {
                    appAdapter.filter(s.toString());
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void updateServiceAndSystemStatus() {
        boolean masterOn = SettingsManager.isMasterEnabled(this);
        boolean systemPinOn = isSystemPinningEnabled();

        if (!masterOn) {
            tvStatusTitle.setText("Sabitleme Duraklatıldı");
            tvStatusTitle.setTextColor(getResources().getColor(R.color.text_muted));
            layoutWarnings.setVisibility(View.GONE);
            findViewById(R.id.card_status).setBackgroundResource(R.drawable.badge_inactive);
            btnToggleMaster.setText("Korumayı Başlat");
            btnToggleMaster.setTextColor(getResources().getColor(R.color.accent_cyan));
            switchMaster.setChecked(false);
        } else if (!systemPinOn) {
            tvStatusTitle.setText("Sistem Sabitleme Kapalı");
            tvStatusTitle.setTextColor(getResources().getColor(R.color.accent_red));
            layoutWarnings.setVisibility(View.VISIBLE);
            btnFixAccessibility.setVisibility(View.GONE);
            btnFixSystemPin.setVisibility(View.VISIBLE);
            findViewById(R.id.card_status).setBackgroundResource(R.drawable.badge_inactive);
            btnToggleMaster.setText("Korumayı Duraklat");
            btnToggleMaster.setTextColor(getResources().getColor(R.color.text_primary));
            switchMaster.setChecked(true);
        } else {
            tvStatusTitle.setText("Otomatik Sabitleme Aktif");
            tvStatusTitle.setTextColor(getResources().getColor(R.color.accent_cyan));
            layoutWarnings.setVisibility(View.GONE);
            findViewById(R.id.card_status).setBackgroundResource(R.drawable.badge_active);
            btnToggleMaster.setText("Korumayı Duraklat");
            btnToggleMaster.setTextColor(getResources().getColor(R.color.text_primary));
            switchMaster.setChecked(true);
        }

        updateSelectedCountText();
        SettingsManager.syncTargetsToFile(this);
    }

    private boolean isAccessibilityServiceEnabled() {
        try {
            int accessibilityEnabled = Settings.Secure.getInt(
                getContentResolver(),
                Settings.Secure.ACCESSIBILITY_ENABLED, 0);
            if (accessibilityEnabled == 1) {
                String services = Settings.Secure.getString(
                    getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
                if (services != null) {
                    return services.contains(getPackageName());
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private boolean isSystemPinningEnabled() {
        try {
            int enabled = Settings.System.getInt(getContentResolver(), "lock_to_app_enabled", 0);
            return enabled == 1;
        } catch (Exception e) {
            return true; // Fallback
        }
    }

    private void updateSelectedCountText() {
        int count = SettingsManager.getPinnedPackages(this).size();
        tvSelectedSummary.setText(String.format(getString(R.string.selected_apps_count), count));
    }

    private void loadInstalledApps() {
        new AsyncTask<Void, Void, List<AppModel>>() {
            @Override
            protected List<AppModel> doInBackground(Void... voids) {
                PackageManager pm = getPackageManager();
                List<PackageInfo> packages = pm.getInstalledPackages(0);
                List<AppModel> list = new ArrayList<>();
                Set<String> pinnedSet = SettingsManager.getPinnedPackages(MainActivity.this);

                for (PackageInfo pi : packages) {
                    // Sadece kullanıcı tarafından başlatılabilir (Launcher Intent'e sahip) uygulamaları filtrele
                    if (pm.getLaunchIntentForPackage(pi.packageName) != null) {
                        if (pi.packageName.equals(getPackageName())) {
                            continue; // Kendini listeleme
                        }
                        String label = pm.getApplicationLabel(pi.applicationInfo).toString();
                        boolean isPinned = pinnedSet.contains(pi.packageName);
                        list.add(new AppModel(label, pi.packageName, pm.getApplicationIcon(pi.applicationInfo), isPinned));
                    }
                }

                Collections.sort(list);
                return list;
            }

            @Override
            protected void onPostExecute(List<AppModel> result) {
                appList = result;
                appAdapter = new AppAdapter(MainActivity.this, appList, new AppAdapter.OnAppPinChangeListener() {
                    @Override
                    public void onPinChanged(AppModel app, boolean isPinned) {
                        updateSelectedCountText();
                    }
                });
                listApps.setAdapter(appAdapter);
                updateSelectedCountText();
            }
        }.execute();
    }
}
