package com.pinmaster.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
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
import android.widget.Button;

public class FakeCrashActivity extends Activity {

    public static final String EXTRA_TARGET_PKG = "target_pkg";
    private static final long UNLOCK_HOLD_MS = 1500; // 1.5 saniye basılı tutma

    private String targetPkg;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isUnlocked = false;
    private AlertDialog crashDialog;

    private final Runnable unlockRunnable = new Runnable() {
        @Override
        public void run() {
            isUnlocked = true;
            try {
                Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                if (v != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        v.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE));
                    } else {
                        v.vibrate(120);
                    }
                }
            } catch (Exception ignored) {}

            // Sahibe oturum izni ver
            if (targetPkg != null) {
                PinAccessibilityService.setOwnerUnlocked(targetPkg);
            }

            if (crashDialog != null && crashDialog.isShowing()) {
                crashDialog.dismiss();
            }
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        targetPkg = getIntent().getStringExtra(EXTRA_TARGET_PKG);

        String appName = "Uygulama";
        if (targetPkg != null) {
            try {
                PackageManager pm = getPackageManager();
                ApplicationInfo info = pm.getApplicationInfo(targetPkg, 0);
                appName = pm.getApplicationLabel(info).toString();
            } catch (Exception ignored) {}
        }

        // Birebir orijinal sistem Android Alert Dialog'u
        AlertDialog.Builder builder = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK);
        builder.setTitle(appName);
        builder.setMessage(appName + " uygulaması yanıt vermiyor.");
        builder.setCancelable(false);

        builder.setPositiveButton("Uygulamayı kapat", (dialog, which) -> goHome());
        builder.setNegativeButton("Geri bildirim gönder", (dialog, which) -> goHome());

        crashDialog = builder.create();
        crashDialog.setCanceledOnTouchOutside(false);
        crashDialog.show();

        // 'Uygulamayı kapat' butonuna 3 saniye basılı tutma dinleyicisi
        Button btnClose = crashDialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (btnClose != null) {
            btnClose.setOnTouchListener((v, event) -> {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        isUnlocked = false;
                        handler.postDelayed(unlockRunnable, UNLOCK_HOLD_MS);
                        return true;

                    case MotionEvent.ACTION_UP:
                        handler.removeCallbacks(unlockRunnable);
                        if (!isUnlocked) {
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
    }

    private void goHome() {
        if (crashDialog != null && crashDialog.isShowing()) {
            crashDialog.dismiss();
        }
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
