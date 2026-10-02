package com.pinmaster.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Vibrator;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Toast;

/**
 * GuardActivity — Görünmez arka plan kilidi.
 *
 * Nasıl çalışır:
 *   1. Accessibility Service pinlenmiş uygulamayı tespit eder.
 *   2. GuardActivity arka planda başlar (kullanıcı görmez).
 *   3. startLockTask() çağrılır → Home/Recents sessizce devre dışı.
 *   4. Kullanıcı pinlenmiş uygulamadan çıkarsa GuardActivity öne gelir.
 *   5. Öne gelince: "Geri Dön" butonu + gizli PIN çıkışı gösterir.
 *
 * Çıkış: PinMaster logosuna 5 kez dokun → PIN gir → kilit kalkar.
 */
public class GuardActivity extends Activity {

    public static final String EXTRA_PACKAGE = "guarded_pkg";

    private String guardedPkg = null;
    private int tapCount = 0;
    private boolean isVisible = false;

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_guard);

        guardedPkg = getIntent().getStringExtra(EXTRA_PACKAGE);

        // Kilidi sessizce başlat (DeviceOwner varsa dialog yok)
        try {
            startLockTask();
        } catch (Exception ignored) {}

        // Titreşim
        if (SettingsManager.isVibrateEnabled(this)) {
            try {
                Vibrator vib = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                if (vib != null) vib.vibrate(100);
            } catch (Exception ignored) {}
        }

        // Başlangıçta içerik gizli — pinlenmiş uygulama önde
        setContentVisible(false);
        setupClickListeners();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String newPkg = intent.getStringExtra(EXTRA_PACKAGE);
        if (newPkg != null) {
            guardedPkg = newPkg;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // GuardActivity öne geldi → pinlenmiş uygulama kapatıldı demek
        // Guard ekranını göster
        isVisible = true;
        tapCount = 0;
        updateAppInfo();
        setContentVisible(true);
    }

    @Override
    protected void onPause() {
        super.onPause();
        isVisible = false;
        tapCount = 0;
        // Arka plana geçerken içeriği gizle (tekrar öne gelince temiz görünsün)
        setContentVisible(false);
    }

    @Override
    public void onBackPressed() {
        // Kilitli modda geri tuşu devre dışı — PIN gerekli
    }

    // ─── UI Helpers ───────────────────────────────────────────────────────────

    private void setupClickListeners() {
        // 5 kez dokunma → PIN diyaloğu
        View exitTrigger = findViewById(R.id.guard_exit_trigger);
        if (exitTrigger != null) {
            exitTrigger.setOnClickListener(v -> {
                tapCount++;
                if (tapCount >= 5) {
                    tapCount = 0;
                    showPinDialog();
                }
            });
        }

        // "Uygulamaya Dön" butonu
        Button btnReturn = findViewById(R.id.guard_btn_return);
        if (btnReturn != null) {
            btnReturn.setOnClickListener(v -> returnToApp());
        }
    }

    private void updateAppInfo() {
        if (guardedPkg == null) return;
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(guardedPkg, 0);
            String label = pm.getApplicationLabel(info).toString();
            Drawable icon = pm.getApplicationIcon(info);

            ImageView ivIcon = findViewById(R.id.guard_app_icon);
            TextView tvName  = findViewById(R.id.guard_app_name);
            Button btnReturn = findViewById(R.id.guard_btn_return);

            if (ivIcon != null) ivIcon.setImageDrawable(icon);
            if (tvName != null) tvName.setText(label);
            if (btnReturn != null) btnReturn.setText(label + "'e Dön");
        } catch (Exception ignored) {}
    }

    private void setContentVisible(boolean visible) {
        View content = findViewById(R.id.guard_content);
        if (content != null) {
            content.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
        }
    }

    // ─── Actions ──────────────────────────────────────────────────────────────

    private void returnToApp() {
        if (guardedPkg == null) return;
        Intent launch = getPackageManager().getLaunchIntentForPackage(guardedPkg);
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(launch);
        }
    }

    private void showPinDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Yönetici Girişi");
        builder.setMessage("Korumayı kaldırmak için PIN'i girin.");
        builder.setCancelable(true);

        final EditText pinInput = new EditText(this);
        pinInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pinInput.setHint("PIN kodu");
        pinInput.setPadding(64, 28, 64, 28);
        builder.setView(pinInput);

        builder.setPositiveButton("Kilidi Kaldır", (dialog, which) -> {
            String entered = pinInput.getText().toString().trim();
            if (entered.equals(SettingsManager.getExitPin(GuardActivity.this))) {
                releaseAndExit();
            } else {
                Toast.makeText(GuardActivity.this, "Yanlış PIN", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("İptal", null);
        builder.show();
    }

    private void releaseAndExit() {
        try {
            stopLockTask();
        } catch (Exception ignored) {}
        finish();
    }
}
