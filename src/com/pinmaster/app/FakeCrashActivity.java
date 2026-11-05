package com.pinmaster.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

public class FakeCrashActivity extends Activity {

    public static final String EXTRA_TARGET_PKG = "target_pkg";
    private static final long UNLOCK_HOLD_MS = 3000; // Tam 3 saniye basılı tutma

    private String targetPkg;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isUnlocked = false;

    private final Runnable unlockRunnable = new Runnable() {
        @Override
        public void run() {
            isUnlocked = true;
            // Titreşimli geri bildirim
            try {
                Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                if (v != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        v.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE));
                    } else {
                        v.vibrate(150);
                    }
                }
            } catch (Exception ignored) {}

            // 3 dakika (180.000 ms) boyunca sahibe kesintisiz bypass izni ver
            if (targetPkg != null) {
                SettingsManager.grantBypass(targetPkg, 180000);
            }

            // Çökme ekranını kapat ve asıl uygulamaya geç
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fake_crash);

        targetPkg = getIntent().getStringExtra(EXTRA_TARGET_PKG);

        String appName = "Uygulama";
        if (targetPkg != null) {
            try {
                PackageManager pm = getPackageManager();
                ApplicationInfo info = pm.getApplicationInfo(targetPkg, 0);
                appName = pm.getApplicationLabel(info).toString();
            } catch (Exception ignored) {}
        }

        TextView tvTitle = findViewById(R.id.tv_fake_app_title);
        TextView tvMessage = findViewById(R.id.tv_fake_app_message);
        TextView btnFeedback = findViewById(R.id.btn_fake_feedback);
        TextView btnClose = findViewById(R.id.btn_fake_close);

        tvTitle.setText(appName);
        tvMessage.setText(appName + " uygulaması sürekli olarak duruyor.");

        // Geri bildirim butonu doğrudan ana ekrana atar
        btnFeedback.setOnClickListener(v -> goHome());

        // Kapat butonu: Normal basışta ana ekrana atar, 3 saniye basılı tutulursa kilidi aşar
        btnClose.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    isUnlocked = false;
                    handler.postDelayed(unlockRunnable, UNLOCK_HOLD_MS);
                    return true;

                case MotionEvent.ACTION_UP:
                    handler.removeCallbacks(unlockRunnable);
                    if (!isUnlocked) {
                        // 3 saniye dolmadan el çekildi: Sahte çökme gibi davran ve ana ekrana dön
                        goHome();
                    }
                    return true;

                case MotionEvent.ACTION_CANCEL:
                    handler.removeCallbacks(unlockRunnable);
                    return true;
            }
            return false;
        });
    }

    private void goHome() {
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        home.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(home);
        finish();
    }

    @Override
    public void onBackPressed() {
        goHome();
    }
}
