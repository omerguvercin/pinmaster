package com.pinmaster.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {

    // Durum kartı
    private View cardStatus;
    private TextView tvStatusTitle, tvStatusDesc, tvAdbCommand;

    // Master switch
    private Switch switchMaster;
    private TextView tvSelectedSummary;

    // Sekmeler
    private Button tabBtnApps, tabBtnSettings, tabBtnGuide;
    private View viewTabApps, viewTabSettings, viewTabGuide;

    // Uygulamalar sekmesi
    private EditText etSearch;
    private ListView listApps;
    private AppAdapter appAdapter;
    private List<AppModel> appList = new ArrayList<>();

    // Ayarlar sekmesi
    private Switch switchVibrate;
    private TextView tvCurrentPin;
    private Button btnChangePin;
    private Button btnAccessibility;

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
        updateStatus();
        updateSelectedCountText();
        SettingsManager.applyLockTaskPackages(this);
    }

    // ─── Init ─────────────────────────────────────────────────────────────────

    private void initViews() {
        cardStatus       = findViewById(R.id.card_status);
        tvStatusTitle    = findViewById(R.id.tv_status_title);
        tvStatusDesc     = findViewById(R.id.tv_status_desc);
        tvAdbCommand     = findViewById(R.id.tv_adb_command);
        tvSelectedSummary = findViewById(R.id.tv_selected_summary);

        switchMaster = findViewById(R.id.switch_master);
        switchMaster.setChecked(SettingsManager.isMasterEnabled(this));
        switchMaster.setOnCheckedChangeListener((btn, checked) -> {
            SettingsManager.setMasterEnabled(MainActivity.this, checked);
            updateStatus();
        });

        tabBtnApps     = findViewById(R.id.tab_btn_apps);
        tabBtnSettings = findViewById(R.id.tab_btn_settings);
        tabBtnGuide    = findViewById(R.id.tab_btn_guide);

        viewTabApps     = findViewById(R.id.view_tab_apps);
        viewTabSettings = findViewById(R.id.view_tab_settings);
        viewTabGuide    = findViewById(R.id.view_tab_guide);

        etSearch  = findViewById(R.id.et_search);
        listApps  = findViewById(R.id.list_apps);

        switchVibrate  = findViewById(R.id.switch_vibrate);
        tvCurrentPin   = findViewById(R.id.tv_current_pin);
        btnChangePin   = findViewById(R.id.btn_change_pin);
        btnAccessibility = findViewById(R.id.btn_open_accessibility);

        if (btnAccessibility != null) {
            btnAccessibility.setOnClickListener(v ->
                    startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        }
    }

    // ─── Status card ──────────────────────────────────────────────────────────

    private void updateStatus() {
        boolean masterEnabled  = SettingsManager.isMasterEnabled(this);
        boolean accessEnabled  = isAccessibilityEnabled();
        boolean overlayEnabled = Settings.canDrawOverlays(this);

        if (!masterEnabled) {
            tvStatusTitle.setText("⏸  Kalkan Duraklatıldı");
            tvStatusDesc.setText("Otomatik koruma geçici olarak devre dışı.");
            cardStatus.setBackgroundResource(R.drawable.badge_inactive);
            tvAdbCommand.setVisibility(View.GONE);
        } else if (!accessEnabled) {
            tvStatusTitle.setText("⚠️  Erişilebilirlik İzni Gerekli");
            tvStatusDesc.setText("'Erişilebilirliği Aç' butonuna basıp PinMaster'ı etkinleştirin.");
            cardStatus.setBackgroundResource(R.drawable.badge_inactive);
            tvAdbCommand.setVisibility(View.GONE);
        } else if (!overlayEnabled) {
            tvStatusTitle.setText("⚠️  Üstte Gösterme İzni Gerekli");
            tvStatusDesc.setText("Kalkanın Home tuşundan etkilenmemesi için 'Üstte Göster' iznini açın.");
            cardStatus.setBackgroundResource(R.drawable.badge_inactive);
            tvAdbCommand.setVisibility(View.VISIBLE);
            tvAdbCommand.setText("👉 İzni Açmak İçin Dokunun");
            tvAdbCommand.setOnClickListener(v -> {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            });
        } else {
            tvStatusTitle.setText("🛡️  Sistem Kalkanı Aktif");
            tvStatusDesc.setText("Seçilen uygulamalardan çıkış koruma altında. Home tuşu ve sızıntılar engellendi.");
            cardStatus.setBackgroundResource(R.drawable.badge_active);
            tvAdbCommand.setVisibility(View.GONE);
        }

        switchMaster.setChecked(masterEnabled);
    }

    private boolean isAccessibilityEnabled() {
        try {
            int enabled = Settings.Secure.getInt(
                    getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 0);
            if (enabled == 1) {
                String services = Settings.Secure.getString(
                        getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
                return services != null && services.contains(getPackageName());
            }
        } catch (Exception ignored) {}
        return false;
    }

    // ─── Settings ─────────────────────────────────────────────────────────────

    private void setupSettings() {
        switchVibrate.setChecked(SettingsManager.isVibrateEnabled(this));
        switchVibrate.setOnCheckedChangeListener((btn, checked) ->
                SettingsManager.setVibrateEnabled(MainActivity.this, checked));

        refreshPinDisplay();
        btnChangePin.setOnClickListener(v -> showChangePinDialog());
    }

    private void refreshPinDisplay() {
        String pin = SettingsManager.getExitPin(this);
        StringBuilder stars = new StringBuilder();
        for (int i = 0; i < pin.length(); i++) stars.append('•');
        tvCurrentPin.setText("Mevcut PIN: " + stars);
    }

    private void showChangePinDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("PIN Değiştir");
        builder.setMessage("Kilitli moddan çıkmak için kullanılacak PIN (en az 4 hane).");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("Yeni PIN");
        input.setPadding(64, 32, 64, 32);
        builder.setView(input);

        builder.setPositiveButton("Kaydet", (d, w) -> {
            String pin = input.getText().toString().trim();
            if (pin.length() >= 4) {
                SettingsManager.setExitPin(MainActivity.this, pin);
                refreshPinDisplay();
            } else {
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage("PIN en az 4 haneli olmalıdır.")
                        .setPositiveButton("Tamam", null).show();
            }
        });
        builder.setNegativeButton("İptal", null);
        builder.show();
    }

    // ─── Tabs ─────────────────────────────────────────────────────────────────

    private void setupTabs() {
        tabBtnApps.setOnClickListener(v -> selectTab(0));
        tabBtnSettings.setOnClickListener(v -> selectTab(1));
        tabBtnGuide.setOnClickListener(v -> selectTab(2));
        selectTab(0);
    }

    private void selectTab(int index) {
        viewTabApps.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        viewTabSettings.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        viewTabGuide.setVisibility(index == 2 ? View.VISIBLE : View.GONE);

        int active   = getResources().getColor(R.color.accent_cyan);
        int inactive = getResources().getColor(R.color.text_secondary);

        tabBtnApps.setTextColor(index == 0 ? active : inactive);
        tabBtnSettings.setTextColor(index == 1 ? active : inactive);
        tabBtnGuide.setTextColor(index == 2 ? active : inactive);

        tabBtnApps.setBackgroundResource(index == 0 ? R.drawable.card_bg : android.R.color.transparent);
        tabBtnSettings.setBackgroundResource(index == 1 ? R.drawable.card_bg : android.R.color.transparent);
        tabBtnGuide.setBackgroundResource(index == 2 ? R.drawable.card_bg : android.R.color.transparent);
    }

    // ─── Search ───────────────────────────────────────────────────────────────

    private void setupSearch() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                if (appAdapter != null) appAdapter.filter(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    // ─── App list ─────────────────────────────────────────────────────────────

    private void updateSelectedCountText() {
        if (tvSelectedSummary == null) return;
        int count = SettingsManager.getPinnedPackages(this).size();
        tvSelectedSummary.setText(String.format(getString(R.string.selected_apps_count), count));
    }

    private void loadInstalledApps() {
        new AsyncTask<Void, Void, List<AppModel>>() {
            @Override
            protected List<AppModel> doInBackground(Void... v) {
                PackageManager pm = getPackageManager();
                List<PackageInfo> pkgs = pm.getInstalledPackages(0);
                List<AppModel> list = new ArrayList<>();
                Set<String> pinned = SettingsManager.getPinnedPackages(MainActivity.this);

                for (PackageInfo pi : pkgs) {
                    if (pm.getLaunchIntentForPackage(pi.packageName) == null) continue;
                    if (pi.packageName.equals(getPackageName())) continue;
                    String label = pm.getApplicationLabel(pi.applicationInfo).toString();
                    list.add(new AppModel(label, pi.packageName,
                            pm.getApplicationIcon(pi.applicationInfo),
                            pinned.contains(pi.packageName)));
                }
                Collections.sort(list);
                return list;
            }

            @Override
            protected void onPostExecute(List<AppModel> result) {
                appList = result;
                appAdapter = new AppAdapter(MainActivity.this, appList,
                        (app, isPinned) -> updateSelectedCountText());
                listApps.setAdapter(appAdapter);
                updateSelectedCountText();
            }
        }.execute();
    }
}
