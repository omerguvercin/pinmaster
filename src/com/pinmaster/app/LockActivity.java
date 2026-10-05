package com.pinmaster.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

public class LockActivity extends Activity {

    public static final String EXTRA_PACKAGE = "guarded_package";

    private String guardedPkg = null;
    private final StringBuilder enteredPin = new StringBuilder();

    private ImageView ivAppIcon;
    private TextView tvAppName;
    private Button btnReturnApp;
    private TextView tvErrorMsg;
    private View dot1, dot2, dot3, dot4;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lock);

        guardedPkg = getIntent().getStringExtra(EXTRA_PACKAGE);

        initViews();
        setupKeypad();
        updateAppInfo();

        if (SettingsManager.isVibrateEnabled(this)) {
            vibrate(80);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String pkg = intent.getStringExtra(EXTRA_PACKAGE);
        if (pkg != null) {
            guardedPkg = pkg;
            updateAppInfo();
        }
        resetPin();
    }

    private void initViews() {
        ivAppIcon = findViewById(R.id.iv_app_icon);
        tvAppName = findViewById(R.id.tv_app_name);
        btnReturnApp = findViewById(R.id.btn_return_app);
        tvErrorMsg = findViewById(R.id.tv_error_msg);

        dot1 = findViewById(R.id.dot_1);
        dot2 = findViewById(R.id.dot_2);
        dot3 = findViewById(R.id.dot_3);
        dot4 = findViewById(R.id.dot_4);

        btnReturnApp.setOnClickListener(v -> returnToApp());
    }

    private void updateAppInfo() {
        if (guardedPkg == null) return;
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(guardedPkg, 0);
            CharSequence label = pm.getApplicationLabel(info);
            Drawable icon = pm.getApplicationIcon(info);

            ivAppIcon.setImageDrawable(icon);
            tvAppName.setText(label);
            btnReturnApp.setText(label + " Uygulamasına Dön");
        } catch (Exception e) {
            tvAppName.setText(guardedPkg);
            btnReturnApp.setText("Uygulamaya Dön");
        }
    }

    private void setupKeypad() {
        int[] numBtnIds = {
            R.id.btn_key_0, R.id.btn_key_1, R.id.btn_key_2,
            R.id.btn_key_3, R.id.btn_key_4, R.id.btn_key_5,
            R.id.btn_key_6, R.id.btn_key_7, R.id.btn_key_8, R.id.btn_key_9
        };

        for (int i = 0; i <= 9; i++) {
            final String digit = String.valueOf(i);
            findViewById(numBtnIds[i]).setOnClickListener(v -> onDigitPressed(digit));
        }

        findViewById(R.id.btn_key_del).setOnClickListener(v -> onDeletePressed());
    }

    private void onDigitPressed(String digit) {
        if (enteredPin.length() >= 4) return;

        enteredPin.append(digit);
        updateDots();
        tvErrorMsg.setVisibility(View.INVISIBLE);

        if (enteredPin.length() == 4) {
            verifyPin();
        }
    }

    private void onDeletePressed() {
        if (enteredPin.length() > 0) {
            enteredPin.deleteCharAt(enteredPin.length() - 1);
            updateDots();
            tvErrorMsg.setVisibility(View.INVISIBLE);
        }
    }

    private void updateDots() {
        int len = enteredPin.length();
        dot1.setBackgroundResource(len >= 1 ? R.drawable.pin_dot_filled : R.drawable.pin_dot_empty);
        dot2.setBackgroundResource(len >= 2 ? R.drawable.pin_dot_filled : R.drawable.pin_dot_empty);
        dot3.setBackgroundResource(len >= 3 ? R.drawable.pin_dot_filled : R.drawable.pin_dot_empty);
        dot4.setBackgroundResource(len >= 4 ? R.drawable.pin_dot_filled : R.drawable.pin_dot_empty);
    }

    private void verifyPin() {
        String correct = SettingsManager.getExitPin(this);
        if (enteredPin.toString().equals(correct)) {
            // Doğru PIN
            if (SettingsManager.isVibrateEnabled(this)) {
                vibrate(120);
            }
            PinAccessibilityService.unlockSession();
            finish();
        } else {
            // Hatalı PIN
            if (SettingsManager.isVibrateEnabled(this)) {
                vibrateDouble();
            }
            tvErrorMsg.setVisibility(View.VISIBLE);
            resetPin();
        }
    }

    private void resetPin() {
        enteredPin.setLength(0);
        updateDots();
    }

    private void returnToApp() {
        if (guardedPkg != null) {
            Intent launch = getPackageManager().getLaunchIntentForPackage(guardedPkg);
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(launch);
            }
        }
        finish();
    }

    @Override
    public void onBackPressed() {
        // Geri tuşuna basınca ana ekrana çıkamaz, korunan uygulamaya döner
        returnToApp();
    }

    private void vibrate(long ms) {
        try {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    v.vibrate(ms);
                }
            }
        } catch (Exception ignored) {}
    }

    private void vibrateDouble() {
        try {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                long[] pattern = {0, 60, 50, 60};
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createWaveform(pattern, -1));
                } else {
                    v.vibrate(pattern, -1);
                }
            }
        } catch (Exception ignored) {}
    }
}
