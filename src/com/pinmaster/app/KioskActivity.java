package com.pinmaster.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Vibrator;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * KioskActivity — Kiosk modunun ana ekranı.
 *
 * startLockTask() ile tamamen kilitlenir:
 *   - Home tuşu devre dışı
 *   - Recents devre dışı
 *   - Bildirim paneli devre dışı
 *
 * Çıkış: PinMaster başlığına 5 kez dok → PIN gir → çıkış
 */
public class KioskActivity extends Activity {

    private int tapCount = 0;
    private List<AppModel> pinnedApps = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_kiosk);

        // Kiosk moduna gir — Home/Recents/Bildirim paneli kilitlenir
        startLockTask();

        // Titreşim geri bildirimi
        if (SettingsManager.isVibrateEnabled(this)) {
            try {
                Vibrator vib = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                if (vib != null) vib.vibrate(150);
            } catch (Exception ignored) {}
        }

        loadPinnedApps();
        setupExitTrigger();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Uygulama listesi değişmiş olabilir, yenile
        loadPinnedApps();
    }

    private void loadPinnedApps() {
        Set<String> pinnedPkgs = SettingsManager.getPinnedPackages(this);
        PackageManager pm = getPackageManager();
        pinnedApps.clear();

        for (String pkg : pinnedPkgs) {
            try {
                String label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
                Drawable icon = pm.getApplicationIcon(pkg);
                pinnedApps.add(new AppModel(label, pkg, icon, true));
            } catch (Exception ignored) {}
        }

        // Alfabetik sırala
        java.util.Collections.sort(pinnedApps);

        GridView grid = findViewById(R.id.kiosk_grid);
        KioskAppAdapter adapter = new KioskAppAdapter(this, pinnedApps);
        grid.setAdapter(adapter);

        grid.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < pinnedApps.size()) {
                AppModel app = pinnedApps.get(position);
                Intent launch = pm.getLaunchIntentForPackage(app.packageName);
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(launch);
                }
            }
        });

        // Seçili uygulama yoksa kullanıcıyı bilgilendir
        TextView tvEmpty = findViewById(R.id.tv_kiosk_empty);
        if (tvEmpty != null) {
            tvEmpty.setVisibility(pinnedApps.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private void setupExitTrigger() {
        // PinMaster başlığına 5 kez dokunmak gizli çıkışı tetikler
        View trigger = findViewById(R.id.kiosk_exit_trigger);
        if (trigger != null) {
            trigger.setOnClickListener(v -> {
                tapCount++;
                if (tapCount >= 5) {
                    tapCount = 0;
                    showExitPinDialog();
                }
            });
        }
    }

    private void showExitPinDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Yönetici Girişi");
        builder.setMessage("Kiosk modundan çıkmak için PIN girin.");
        builder.setCancelable(true);

        final EditText pinInput = new EditText(this);
        pinInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pinInput.setHint("PIN kodu");
        pinInput.setPadding(64, 32, 64, 32);
        builder.setView(pinInput);

        builder.setPositiveButton("Giriş Yap", (dialog, which) -> {
            String entered = pinInput.getText().toString().trim();
            String correct = SettingsManager.getExitPin(KioskActivity.this);
            if (entered.equals(correct)) {
                exitKiosk();
            } else {
                Toast.makeText(KioskActivity.this, "Yanlış PIN", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("İptal", (dialog, which) -> dialog.cancel());

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void exitKiosk() {
        stopLockTask();
        Intent intent = new Intent(KioskActivity.this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    public void onBackPressed() {
        // Kiosk modunda geri tuşu devre dışı
    }
}
