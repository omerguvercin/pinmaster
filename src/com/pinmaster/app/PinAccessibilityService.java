package com.pinmaster.app;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

public class PinAccessibilityService extends AccessibilityService {

    // Kullanıcı şu anda korunan uygulamanın içinde mi?
    private static volatile boolean isInsideProtectedApp = false;
    private static volatile String activeGuardedPkg = null;

    private WindowManager windowManager;
    private View overlayView;
    private volatile boolean isOverlayShowing = false;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final StringBuilder enteredPin = new StringBuilder();

    private ImageView ivAppIcon;
    private TextView tvAppName;
    private Button btnReturnApp;
    private TextView tvErrorMsg;
    private View dot1, dot2, dot3, dot4;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
    }

    // ─── Event Handling ───────────────────────────────────────────────────────

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }

        CharSequence pkgSeq = event.getPackageName();
        if (pkgSeq == null) return;
        String currentPkg = pkgSeq.toString();

        // Kendi uygulamamız veya sistem iç süreçleri (Play Games, Game Booster, klavye vb.) yoksay
        if (isSystemOrOverlayExempt(currentPkg)) {
            return;
        }

        // Ana anahtar kapalıysa korumayı sıfırla
        if (!SettingsManager.isMasterEnabled(this)) {
            isInsideProtectedApp = false;
            activeGuardedPkg = null;
            hideOverlay();
            return;
        }

        // Telefon ekranı kapalı veya kilit ekranındaysa müdahale etme
        if (isScreenOffOrKeyguard()) {
            return;
        }

        // ─── 1. DURUM: KORUNAN UYGULAMA AÇILDI ───
        // Uygulama ŞİFRESİZ ve ÖZGÜRCE AÇILIR! Ekrana ASLA kalkan gelmez.
        if (SettingsManager.isPackagePinned(this, currentPkg)) {
            isInsideProtectedApp = true;
            activeGuardedPkg = currentPkg;
            hideOverlay();
            return;
        }

        // ─── 2. DURUM: KULLANICI UYGULAMADAN ÇIKMAYA ÇALIŞTI ───
        // Kullanıcı korunan uygulamanın içindeyken Home/Recents'e basıp ana ekrana veya başka uygulamaya geçmek istedi!
        if (isInsideProtectedApp && activeGuardedPkg != null) {
            if (currentPkg.equals(activeGuardedPkg)) {
                // Hâlâ korunan uygulamanın içinde
                hideOverlay();
                return;
            }

            // Kalkan açıkken bildirim paneli çekilirse otomatik kapat
            if (isOverlayShowing && currentPkg.equals("com.android.systemui")) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE);
                }
                return;
            }

            // Kullanıcı dışarı çıktı -> ÇIKIŞI ENGELLE, KALKANI GÖSTER!
            showOverlay(activeGuardedPkg);
        }
    }

    // ─── Key Event Filtering ──────────────────────────────────────────────────

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (isOverlayShowing) {
            int code = event.getKeyCode();
            // Geri tuşu -> Uygulamaya geri döndür
            if (code == KeyEvent.KEYCODE_BACK) {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    mainHandler.post(() -> returnToGuardedApp(activeGuardedPkg));
                }
                return true;
            }
            // Home veya Son Uygulamalar donanım tuşları -> Tamamen yut
            if (code == KeyEvent.KEYCODE_HOME || code == KeyEvent.KEYCODE_APP_SWITCH) {
                return true;
            }
        }
        return super.onKeyEvent(event);
    }

    // ─── WindowManager Overlay Management ─────────────────────────────────────

    private void showOverlay(final String targetPkg) {
        if (!Settings.canDrawOverlays(this)) {
            return;
        }

        mainHandler.post(() -> {
            if (windowManager == null) {
                windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            }

            if (isOverlayShowing && overlayView != null) {
                updateAppInfo(targetPkg);
                return;
            }

            try {
                ContextThemeWrapper ctxWrapper = new ContextThemeWrapper(this, R.style.Theme_PinMaster);
                LayoutInflater inflater = LayoutInflater.from(ctxWrapper);
                overlayView = inflater.inflate(R.layout.activity_lock, null);

                initOverlayViews(targetPkg);
                setupKeypad();

                WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                                | WindowManager.LayoutParams.FLAG_FULLSCREEN
                                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                        PixelFormat.TRANSLUCENT
                );
                params.gravity = Gravity.CENTER;

                windowManager.addView(overlayView, params);
                isOverlayShowing = true;
                enteredPin.setLength(0);

                if (SettingsManager.isVibrateEnabled(PinAccessibilityService.this)) {
                    vibrate(70);
                }
            } catch (Exception ignored) {}
        });
    }

    private void hideOverlay() {
        mainHandler.post(() -> {
            if (isOverlayShowing && overlayView != null && windowManager != null) {
                try {
                    windowManager.removeView(overlayView);
                } catch (Exception ignored) {}
                overlayView = null;
                isOverlayShowing = false;
                enteredPin.setLength(0);
            }
        });
    }

    private void initOverlayViews(String targetPkg) {
        if (overlayView == null) return;
        ivAppIcon     = overlayView.findViewById(R.id.iv_app_icon);
        tvAppName     = overlayView.findViewById(R.id.tv_app_name);
        btnReturnApp  = overlayView.findViewById(R.id.btn_return_app);
        tvErrorMsg    = overlayView.findViewById(R.id.tv_error_msg);

        dot1 = overlayView.findViewById(R.id.dot_1);
        dot2 = overlayView.findViewById(R.id.dot_2);
        dot3 = overlayView.findViewById(R.id.dot_3);
        dot4 = overlayView.findViewById(R.id.dot_4);

        btnReturnApp.setOnClickListener(v -> returnToGuardedApp(targetPkg));
        updateAppInfo(targetPkg);
    }

    private void updateAppInfo(String pkg) {
        if (pkg == null || overlayView == null) return;
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
            CharSequence label = pm.getApplicationLabel(info);
            Drawable icon = pm.getApplicationIcon(info);

            if (ivAppIcon != null) ivAppIcon.setImageDrawable(icon);
            if (tvAppName != null) tvAppName.setText(label);
            if (btnReturnApp != null) btnReturnApp.setText(label + " Uygulamasına Dön");
        } catch (Exception e) {
            if (tvAppName != null) tvAppName.setText(pkg);
            if (btnReturnApp != null) btnReturnApp.setText("Uygulamaya Dön");
        }
    }

    private void setupKeypad() {
        if (overlayView == null) return;
        int[] numBtnIds = {
                R.id.btn_key_0, R.id.btn_key_1, R.id.btn_key_2,
                R.id.btn_key_3, R.id.btn_key_4, R.id.btn_key_5,
                R.id.btn_key_6, R.id.btn_key_7, R.id.btn_key_8, R.id.btn_key_9
        };

        for (int i = 0; i <= 9; i++) {
            final String digit = String.valueOf(i);
            Button b = overlayView.findViewById(numBtnIds[i]);
            if (b != null) {
                b.setOnClickListener(v -> onDigitPressed(digit));
            }
        }

        View btnDel = overlayView.findViewById(R.id.btn_key_del);
        if (btnDel != null) {
            btnDel.setOnClickListener(v -> onDeletePressed());
        }
    }

    private void onDigitPressed(String digit) {
        if (enteredPin.length() >= 4) return;
        enteredPin.append(digit);
        updateDots();
        if (tvErrorMsg != null) tvErrorMsg.setVisibility(View.INVISIBLE);

        if (enteredPin.length() == 4) {
            verifyPin();
        }
    }

    private void onDeletePressed() {
        if (enteredPin.length() > 0) {
            enteredPin.deleteCharAt(enteredPin.length() - 1);
            updateDots();
            if (tvErrorMsg != null) tvErrorMsg.setVisibility(View.INVISIBLE);
        }
    }

    private void updateDots() {
        int len = enteredPin.length();
        if (dot1 != null) dot1.setBackgroundResource(len >= 1 ? R.drawable.pin_dot_filled : R.drawable.pin_dot_empty);
        if (dot2 != null) dot2.setBackgroundResource(len >= 2 ? R.drawable.pin_dot_filled : R.drawable.pin_dot_empty);
        if (dot3 != null) dot3.setBackgroundResource(len >= 3 ? R.drawable.pin_dot_filled : R.drawable.pin_dot_empty);
        if (dot4 != null) dot4.setBackgroundResource(len >= 4 ? R.drawable.pin_dot_filled : R.drawable.pin_dot_empty);
    }

    private void verifyPin() {
        String correct = SettingsManager.getExitPin(this);
        if (enteredPin.toString().equals(correct)) {
            // Başarılı PIN -> Korumadan çıkışa izin ver!
            if (SettingsManager.isVibrateEnabled(this)) {
                vibrate(120);
            }
            isInsideProtectedApp = false;
            activeGuardedPkg = null;
            hideOverlay();
            // Artık kalkan tamamen kapandı, kullanıcı ana ekranda tamamen serbest!
        } else {
            // Hatalı PIN
            if (SettingsManager.isVibrateEnabled(this)) {
                vibrateDouble();
            }
            if (tvErrorMsg != null) tvErrorMsg.setVisibility(View.VISIBLE);
            enteredPin.setLength(0);
            updateDots();
        }
    }

    private void returnToGuardedApp(String pkg) {
        if (pkg != null) {
            Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(launch);
            }
        }
        hideOverlay();
        // isInsideProtectedApp true kalır, çünkü kullanıcı tekrar korunan uygulamanın içinde!
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private boolean isScreenOffOrKeyguard() {
        try {
            KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            if (km != null && km.isKeyguardLocked()) return true;

            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isInteractive()) return true;
        } catch (Exception ignored) {}
        return false;
    }

    private boolean isSystemOrOverlayExempt(String pkg) {
        if (pkg == null) return true;
        // Kendi uygulamamız
        if (pkg.equals(getPackageName())) return true;
        // Android çekirdek
        if (pkg.equals("android")) return true;
        // Google Play Hizmetleri ve Oyun servisleri
        if (pkg.startsWith("com.google.android.gms") || pkg.startsWith("com.google.android.play.games")) return true;
        // Samsung Game Booster / Game Tools
        if (pkg.startsWith("com.samsung.android.game")) return true;
        // Telefon görüşmesi arayüzleri
        if (pkg.contains("incallui") || pkg.contains("telecom") || pkg.contains("dialer")) return true;
        // Klavyeler
        if (pkg.contains("inputmethod") || pkg.contains("honeyboard") || pkg.contains("latin")) return true;
        // Kimlik doğrulama / Autofill
        if (pkg.contains("autofill") || pkg.contains("credentials")) return true;

        return false;
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

    @Override
    public void onInterrupt() {
        isInsideProtectedApp = false;
        activeGuardedPkg = null;
        hideOverlay();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        hideOverlay();
    }
}
