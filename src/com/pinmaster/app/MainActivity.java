package com.pinmaster.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
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

    // Status card
    private View cardDoStatus;
    private TextView tvStatusTitle;
    private TextView tvStatusDesc;
    private TextView tvAdbCommand;
    private TextView tvSelectedSummary;
    private Button btnStartKiosk;

    // Tabs
    private Button tabBtnApps, tabBtnSettings, tabBtnGuide;
    private View viewTabApps, viewTabSettings, viewTabGuide;

    // Apps tab
    private EditText etSearch;
    private ListView listApps;
    private AppAdapter appAdapter;
    private List<AppModel> appList = new ArrayList<>();

    // Settings tab
    private Switch switchVibrate;
    private TextView tvCurrentPin;
    private Button btnChangePin;

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
        updateDeviceOwnerStatus();
        updateSelectedCountText();
        SettingsManager.applyLockTaskPackages(this);
    }

    // ─── Init ─────────────────────────────────────────────────────────────────

    private void initViews() {
        cardDoStatus    = findViewById(R.id.card_do_status);
        tvStatusTitle   = findViewById(R.id.tv_status_title);
        tvStatusDesc    = findViewById(R.id.tv_status_desc);
        tvAdbCommand    = findViewById(R.id.tv_adb_command);
        tvSelectedSummary = findViewById(R.id.tv_selected_summary);
        btnStartKiosk   = findViewById(R.id.btn_start_kiosk);

        tabBtnApps      = findViewById(R.id.tab_btn_apps);
        tabBtnSettings  = findViewById(R.id.tab_btn_settings);
        tabBtnGuide     = findViewById(R.id.tab_btn_guide);

        viewTabApps     = findViewById(R.id.view_tab_apps);
        viewTabSettings = findViewById(R.id.view_tab_settings);
        viewTabGuide    = findViewById(R.id.view_tab_guide);

        etSearch        = findViewById(R.id.et_search);
        listApps        = findViewById(R.id.list_apps);

        switchVibrate   = findViewById(R.id.switch_vibrate);
        tvCurrentPin    = findViewById(R.id.tv_current_pin);
        btnChangePin    = findViewById(R.id.btn_change_pin);

        btnStartKiosk.setOnClickListener(v -> onStartKioskClicked());
    }

    // ─── Kiosk start ──────────────────────────────────────────────────────────

    private void onStartKioskClicked() {
        if (!SettingsManager.isDeviceOwner(this)) {
            showSetupDialog();
            return;
        }
        if (SettingsManager.getPinnedPackages(this).isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Uygulama Seçilmedi")
                    .setMessage("Önce 'Uygulamalar' sekmesinden en az bir uygulama seçmelisiniz.")
                    .setPositiveButton("Tamam", null)
                    .show();
            return;
        }
        SettingsManager.applyLockTaskPackages(this);
        Intent intent = new Intent(this, KioskActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    private void showSetupDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Bir Kerelik Kurulum Gerekli")
                .setMessage(
                    "Telefonu bilgisayara USB ile bağlayın ve aşağıdaki komutu çalıştırın.\n\n" +
                    "Bu işlem yalnızca bir kez yapılır; sonrasında bilgisayar gerekmez.\n\n" +
                    "adb shell dpm set-device-owner \\" +
                    "\ncom.pinmaster.app/.PinDeviceAdminReceiver"
                )
                .setPositiveButton("Anladım", null)
                .show();
    }

    // ─── Device Owner Status ──────────────────────────────────────────────────

    private void updateDeviceOwnerStatus() {
        boolean isOwner = SettingsManager.isDeviceOwner(this);

        if (isOwner) {
            tvStatusTitle.setText("✅  Sistem Hazır");
            tvStatusDesc.setText("Cihaz Sahibi yetkisi aktif. Kiosk modu çalışmaya hazır.");
            cardDoStatus.setBackgroundResource(R.drawable.badge_active);
            tvAdbCommand.setVisibility(View.GONE);
            btnStartKiosk.setAlpha(1.0f);
            btnStartKiosk.setEnabled(true);
        } else {
            tvStatusTitle.setText("⚠️  Kurulum Gerekli");
            tvStatusDesc.setText("Tek seferlik ADB kurulumu yapılmamış. Aşağıdaki komutu çalıştırın:");
            cardDoStatus.setBackgroundResource(R.drawable.badge_inactive);
            tvAdbCommand.setVisibility(View.VISIBLE);
            tvAdbCommand.setText("adb shell dpm set-device-owner com.pinmaster.app/.PinDeviceAdminReceiver");
            btnStartKiosk.setAlpha(0.4f);
            btnStartKiosk.setEnabled(false);
        }
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
        builder.setMessage("Kiosk modundan çıkmak için kullanılan PIN (en az 4 hane).");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("Yeni PIN");
        input.setPadding(64, 32, 64, 32);
        builder.setView(input);

        builder.setPositiveButton("Kaydet", (dialog, which) -> {
            String pin = input.getText().toString().trim();
            if (pin.length() >= 4) {
                SettingsManager.setExitPin(MainActivity.this, pin);
                refreshPinDisplay();
            } else {
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage("PIN en az 4 haneli olmalıdır.")
                        .setPositiveButton("Tamam", null)
                        .show();
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

        int activeColor   = getResources().getColor(R.color.accent_cyan);
        int inactiveColor = getResources().getColor(R.color.text_secondary);

        tabBtnApps.setTextColor(index == 0 ? activeColor : inactiveColor);
        tabBtnSettings.setTextColor(index == 1 ? activeColor : inactiveColor);
        tabBtnGuide.setTextColor(index == 2 ? activeColor : inactiveColor);

        tabBtnApps.setBackgroundResource(
                index == 0 ? R.drawable.card_bg : android.R.color.transparent);
        tabBtnSettings.setBackgroundResource(
                index == 1 ? R.drawable.card_bg : android.R.color.transparent);
        tabBtnGuide.setBackgroundResource(
                index == 2 ? R.drawable.card_bg : android.R.color.transparent);
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

    // ─── App Loading ──────────────────────────────────────────────────────────

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
