package com.pinmaster.app;

import android.accessibilityservice.AccessibilityService;
import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
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
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import java.lang.reflect.Method;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class PinAccessibilityService extends AccessibilityService {

    // Kullanıcı şu anda korunan uygulamanın içinde mi?
    private static volatile boolean isInsideProtectedApp = false;
    private static volatile String activeGuardedPkg = null;
    private static volatile String sessionLockedPkg = null;
    private static volatile String unpinnedPkg = null;
    private static volatile long unpinnedUntil = 0;
    private static volatile boolean wasLockTaskActive = false;

    // Sahte Uyarı (Fake Crash) Durum Değişkenleri
    private View fakeCrashView;
    private volatile boolean isFakeCrashShowing = false;
    private volatile String currentFakeCrashPkg = null;
    private static volatile String ownerUnlockedPkg = null; // Yalnızca sahibi 1.5 saniye basarak açtığında atanır
    private final Handler fakeCrashHandler = new Handler(Looper.getMainLooper());
    private boolean isFakeHoldUnlocked = false;

    // Süreli Kilit (Zamanlayıcı) Değişkenleri
    private static ScheduledExecutorService timerExecutor = null;
    private static ScheduledFuture<?> activeTimerFuture = null;
    private static volatile String activeTimerPkg = null;
    private static volatile long activeTimerExpireAt = 0;

    // PIN Kilit Ekranı (Overlay) Değişkenleri
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

    public static void setOwnerUnlocked(String pkg) {
        ownerUnlockedPkg = pkg;
    }

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

        // Mahremiyet Kalkanı: Kilitli uygulama açıkken SystemUI (bildirim çekmecesi) açılırsa hemen kapat
        if ("com.android.systemui".equals(currentPkg)) {
            if (isInsideProtectedApp && SettingsManager.isPrivacyShieldEnabled(this)) {
                collapseStatusBarPanels();
                ShizukuHelper.setPrivacyShield(true);
            }
            return;
        }

        // Kendi uygulamamız veya sistem iç süreçleri yoksay
        if (isSystemOrOverlayExempt(currentPkg)) {
            return;
        }

        // Ana anahtar kapalıysa korumayı sıfırla
        if (!SettingsManager.isMasterEnabled(this)) {
            cancelActiveTimer();
            ShizukuHelper.setPrivacyShield(false);
            isInsideProtectedApp = false;
            activeGuardedPkg = null;
            sessionLockedPkg = null;
            unpinnedPkg = null;
            unpinnedUntil = 0;
            ownerUnlockedPkg = null;
            hideOverlay();
            hideFakeCrashOverlay();
            return;
        }

        // Telefon ekranı kapalı veya kilit ekranındaysa müdahale etme
        if (isScreenOffOrKeyguard()) {
            return;
        }

        // Eğer şu anda Sahte Uyarı ekranda açık duruyorsa:
        if (isFakeCrashShowing) {
            // Eğer gelen pencere aynı hedef uygulamaya aitse veya sistem alt penceresiyse çökme açık kalsın!
            if (currentPkg.equals(currentFakeCrashPkg)) {
                return;
            }
            // Eğer kullanıcı gerçekten başka bir uygulamaya veya ana ekrana gittiyse:
            hideFakeCrashOverlay();
        }

        // Android sabitleme (LockTaskMode) durum kontrolü
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        int lockMode = am != null ? am.getLockTaskModeState() : ActivityManager.LOCK_TASK_MODE_NONE;
        boolean isCurrentlyLocked = (lockMode == ActivityManager.LOCK_TASK_MODE_PINNED || lockMode == ActivityManager.LOCK_TASK_MODE_LOCKED);

        // Kullanıcı donanım tuşlarına basarak sabitlemeyi kaldırdıysa:
        if (wasLockTaskActive && !isCurrentlyLocked) {
            cancelActiveTimer();
            ShizukuHelper.setPrivacyShield(false);
            if (activeGuardedPkg != null) {
                unpinnedPkg = activeGuardedPkg;
                long cooldownMs = SettingsManager.getCooldownSeconds(this) * 1000L;
                unpinnedUntil = System.currentTimeMillis() + cooldownMs;
                performGlobalAction(GLOBAL_ACTION_HOME);
            }
        }
        wasLockTaskActive = isCurrentlyLocked;

        long now = System.currentTimeMillis();

        // ─── 1. DURUM: KORUNAN UYGULAMA AÇILDI ───
        if (SettingsManager.isPackagePinned(this, currentPkg)) {
            int appMode = SettingsManager.getAppLockMode(this, currentPkg);
            android.util.Log.i("PinMaster", "KORUNAN UYGULAMA: " + currentPkg + " Mod: " + appMode + " ownerUnlockedPkg=" + ownerUnlockedPkg);

            // ══════════════════════════════════════════════
            // A. SAHTE UYARI (FAKE CRASH) MODU
            // ══════════════════════════════════════════════
            if (appMode == SettingsManager.LOCK_MODE_FAKE_CRASH) {
                cancelActiveTimer();
                ShizukuHelper.setPrivacyShield(false);
                // Sahibi 1.5 saniye basarak açtıysa ve hala içindeyse engelleme
                if (currentPkg.equals(ownerUnlockedPkg)) {
                    android.util.Log.i("PinMaster", "Sahibi kilidi asmis, erisime izin verildi: " + currentPkg);
                    return;
                }
                // Sahibi henüz kilidi açmadı: HER SEFERİNDE EKRANIN ÜSTÜNDE GERÇEK SİSTEM ÇÖKME DİYALOĞUNU AÇ!
                android.util.Log.i("PinMaster", "Sahibi henuz kilidi acmadi -> FakeCrashActivity aciliyor: " + currentPkg);
                Intent fakeIntent = new Intent(this, FakeCrashActivity.class);
                fakeIntent.putExtra(FakeCrashActivity.EXTRA_TARGET_PKG, currentPkg);
                fakeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(fakeIntent);
                return;
            }

            // ══════════════════════════════════════════════
            // B. KİLİTLİ (SÜRELİ YA DA SÜRESİZ) MODU
            // ══════════════════════════════════════════════
            hideFakeCrashOverlay();

            // Kullanıcı bu uygulamayı az önce donanım tuşlarıyla unpin yaptıysa bekleme süresince tekrar kilitleme
            if (currentPkg.equals(unpinnedPkg) && now < unpinnedUntil) {
                return;
            }

            // Bu uygulama bu oturumda zaten kilitlendiyse döngüye girmemek için tekrar kilitleme
            if (currentPkg.equals(sessionLockedPkg)) {
                return;
            }

            isInsideProtectedApp = true;
            activeGuardedPkg = currentPkg;
            sessionLockedPkg = currentPkg;
            hideOverlay();

            // Süre Kontrolü: Kullanıcı bu uygulama için bir süre seçti mi? (örn. 1 dk, 15 dk vs.)
            int timerMinutes = SettingsManager.getAppTimerMinutes(this, currentPkg);
            if (timerMinutes > 0) {
                long durationMs = timerMinutes * 60 * 1000L;
                if (!currentPkg.equals(activeTimerPkg) || activeTimerFuture == null || activeTimerFuture.isDone()) {
                    cancelActiveTimer();
                    activeTimerPkg = currentPkg;
                    activeTimerExpireAt = System.currentTimeMillis() + durationMs;
                    final String timerPkg = currentPkg;
                    android.util.Log.i("PinMaster", "Guvenli Sayac baslatildi: " + timerPkg + " Sure: " + timerMinutes + " dk (" + durationMs + " ms)");

                    if (timerExecutor == null || timerExecutor.isShutdown()) {
                        timerExecutor = Executors.newSingleThreadScheduledExecutor();
                    }

                    activeTimerFuture = timerExecutor.schedule(() -> {
                        android.util.Log.i("PinMaster", "SURE DOLDU! Hedef: " + timerPkg + " -> Kilit kaldirilip ekran kapatiliyor.");
                        vibrateDouble();
                        ShizukuHelper.setPrivacyShield(false);

                        // 1. Sabitlemeyi kaldır
                        if (ShizukuHelper.hasPermission()) {
                            ShizukuHelper.unlockTask();
                        }

                        // 2. Ana ekrana dön
                        performGlobalAction(GLOBAL_ACTION_HOME);
                        ShizukuHelper.executeCommand("input keyevent KEYCODE_HOME");

                        // 3. Ekranı kapat ve kilitle
                        mainHandler.postDelayed(() -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
                            }
                            ShizukuHelper.executeCommand("input keyevent 26");
                        }, 350);

                        cancelActiveTimer();
                    }, durationMs, TimeUnit.MILLISECONDS);
                }
            } else {
                if (activeTimerPkg != null && activeTimerPkg.equals(currentPkg)) {
                    cancelActiveTimer();
                }
            }

            // Mahremiyet Kalkanı Aktifleştir
            if (SettingsManager.isPrivacyShieldEnabled(this)) {
                ShizukuHelper.setPrivacyShield(true);
            }

            // Shizuku aktifse doğrudan işletim sistemi seviyesinde sabitle (App Pinning)
            if (ShizukuHelper.hasPermission()) {
                ShizukuHelper.lockTopApp(currentPkg);
            }
            return;
        }

        // ─── 2. DURUM: KULLANICI KORUNAN UYGULAMADAN AYRILDI (ANA EKRAN YA DA DİĞER UYGULAMA) ───
        // Eğer kullanıcı Sahte Uyarı ile açtığı uygulamadan çıktıysa oturumu hemen sıfırla!
        if (ownerUnlockedPkg != null && !ownerUnlockedPkg.equals(currentPkg) && !getPackageName().equals(currentPkg)) {
            android.util.Log.i("PinMaster", "Kullanici Sahte Uyari uygulamasindan cikti: " + ownerUnlockedPkg + " -> Yeni: " + currentPkg);
            ownerUnlockedPkg = null;
        }

        // Eğer kullanıcı gerçekten Ana Ekrana döndüyse:
        if (isHomeLauncher(currentPkg)) {
            if (activeTimerPkg != null) {
                android.util.Log.i("PinMaster", "Ana ekrana donuldu, sayac temizleniyor: " + activeTimerPkg);
                cancelActiveTimer();
            }
            ShizukuHelper.setPrivacyShield(false);
        }

        if (sessionLockedPkg != null && !sessionLockedPkg.equals(currentPkg)) {
            sessionLockedPkg = null;
            if (now >= unpinnedUntil) {
                unpinnedPkg = null;
            }
            isInsideProtectedApp = false;
            activeGuardedPkg = null;
            ShizukuHelper.setPrivacyShield(false);
        }
        hideOverlay();
    }

    // ─── Sahte Uyarı (Fake Crash) Overlay Yönetimi ─────────────────────────────

    private void showFakeCrashOverlay(final String targetPkg) {
        mainHandler.post(() -> {
            android.util.Log.i("PinMaster", "showFakeCrashOverlay called for: " + targetPkg);
            if (windowManager == null) {
                windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            }

            // Eğer zaten bu paket için çökme penceresi ekrandaysa tekrar oluşturma
            if (isFakeCrashShowing && fakeCrashView != null && targetPkg.equals(currentFakeCrashPkg)) {
                return;
            }

            hideFakeCrashOverlay();

            try {
                ContextThemeWrapper ctxWrapper = new ContextThemeWrapper(this, R.style.Theme_PinMaster);
                LayoutInflater inflater = LayoutInflater.from(ctxWrapper);
                fakeCrashView = inflater.inflate(R.layout.dialog_fake_crash, null);
                currentFakeCrashPkg = targetPkg;

                String appLabel = "Uygulama";
                try {
                    PackageManager pm = getPackageManager();
                    ApplicationInfo info = pm.getApplicationInfo(targetPkg, 0);
                    appLabel = pm.getApplicationLabel(info).toString();
                } catch (Exception ignored) {}

                TextView tvTitle = fakeCrashView.findViewById(R.id.tv_fake_app_title);
                TextView tvMessage = fakeCrashView.findViewById(R.id.tv_fake_app_message);
                TextView btnFeedback = fakeCrashView.findViewById(R.id.btn_fake_feedback);
                TextView btnClose = fakeCrashView.findViewById(R.id.btn_fake_close);

                tvTitle.setText(appLabel);
                tvMessage.setText(appLabel + " uygulaması sürekli olarak duruyor.");

                // Geri bildirim butonuna veya dış alana basılırsa ana ekrana at
                btnFeedback.setOnClickListener(v -> exitToHome());
                fakeCrashView.setOnClickListener(v -> exitToHome());

                // 3 Saniye Basılı Tutma Dinleyicisi
                final Runnable unlockRunnable = () -> {
                    android.util.Log.i("PinMaster", "Sahte Uyari 3 saniye kilit asildi: " + targetPkg);
                    isFakeHoldUnlocked = true;
                    vibrate(120);
                    ownerUnlockedPkg = targetPkg;
                    hideFakeCrashOverlay();
                };

                btnClose.setOnTouchListener((v, event) -> {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            isFakeHoldUnlocked = false;
                            fakeCrashHandler.postDelayed(unlockRunnable, 1500);
                            return true;

                        case MotionEvent.ACTION_UP:
                            fakeCrashHandler.removeCallbacks(unlockRunnable);
                            if (!isFakeHoldUnlocked) {
                                // 3 saniye dolmadan parmak çekildi: Sahte çökme gibi davran ve ana ekrana dön
                                exitToHome();
                            }
                            return true;

                        case MotionEvent.ACTION_CANCEL:
                            fakeCrashHandler.removeCallbacks(unlockRunnable);
                            return true;
                    }
                    return false;
                });

                // En üst katmanda, tüm sistem pencerelerinin üzerinde (TYPE_APPLICATION_OVERLAY)
                WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT,
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                                : WindowManager.LayoutParams.TYPE_PHONE,
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                                | WindowManager.LayoutParams.FLAG_FULLSCREEN
                                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                        PixelFormat.TRANSLUCENT
                );
                params.gravity = Gravity.CENTER;

                windowManager.addView(fakeCrashView, params);
                isFakeCrashShowing = true;
                android.util.Log.i("PinMaster", "showFakeCrashOverlay SUCCESS for: " + targetPkg);

            } catch (Exception e) {
                android.util.Log.e("PinMaster", "showFakeCrashOverlay ERROR: ", e);
            }
        });
    }

    private void hideFakeCrashOverlay() {
        mainHandler.post(() -> {
            if (isFakeCrashShowing && fakeCrashView != null && windowManager != null) {
                try {
                    windowManager.removeView(fakeCrashView);
                } catch (Exception ignored) {}
                fakeCrashView = null;
                isFakeCrashShowing = false;
                currentFakeCrashPkg = null;
            }
        });
    }

    private void exitToHome() {
        hideFakeCrashOverlay();
        performGlobalAction(GLOBAL_ACTION_HOME);
    }

    // ─── Key Event Filtering ──────────────────────────────────────────────────

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        // Sahte çökme açıkken geri tuşuna basılırsa doğrudan ana ekrana at
        if (isFakeCrashShowing) {
            int code = event.getKeyCode();
            if (code == KeyEvent.KEYCODE_BACK) {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    mainHandler.post(this::exitToHome);
                }
                return true;
            }
            if (code == KeyEvent.KEYCODE_HOME || code == KeyEvent.KEYCODE_APP_SWITCH) {
                return true;
            }
        }

        if (isOverlayShowing) {
            int code = event.getKeyCode();
            if (code == KeyEvent.KEYCODE_BACK) {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    mainHandler.post(() -> returnToGuardedApp(activeGuardedPkg));
                }
                return true;
            }
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
        dot1          = overlayView.findViewById(R.id.dot_1);
        dot2          = overlayView.findViewById(R.id.dot_2);
        dot3          = overlayView.findViewById(R.id.dot_3);
        dot4          = overlayView.findViewById(R.id.dot_4);

        updateAppInfo(targetPkg);

        if (btnReturnApp != null) {
            btnReturnApp.setOnClickListener(v -> returnToGuardedApp(activeGuardedPkg));
        }
    }

    private void updateAppInfo(String pkg) {
        if (pkg == null) return;
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
            if (tvAppName != null) tvAppName.setText(pm.getApplicationLabel(info));
            if (ivAppIcon != null) ivAppIcon.setImageDrawable(pm.getApplicationIcon(info));
        } catch (Exception ignored) {}
    }

    private void setupKeypad() {
        if (overlayView == null) return;
        int[] numBtnIds = {
                R.id.btn_key_0, R.id.btn_key_1, R.id.btn_key_2, R.id.btn_key_3,
                R.id.btn_key_4, R.id.btn_key_5, R.id.btn_key_6, R.id.btn_key_7,
                R.id.btn_key_8, R.id.btn_key_9
        };

        for (int id : numBtnIds) {
            Button btn = overlayView.findViewById(id);
            if (btn != null) {
                btn.setOnClickListener(v -> {
                    String digit = ((Button) v).getText().toString();
                    onDigitEntered(digit);
                });
            }
        }

        Button btnDelete = overlayView.findViewById(R.id.btn_key_del);
        if (btnDelete != null) {
            btnDelete.setOnClickListener(v -> onDeleteClicked());
        }
    }

    private void onDigitEntered(String digit) {
        if (enteredPin.length() >= 4) return;
        enteredPin.append(digit);
        updateDots();
        vibrate(30);

        if (enteredPin.length() == 4) {
            checkPin();
        }
    }

    private void onDeleteClicked() {
        if (enteredPin.length() > 0) {
            enteredPin.deleteCharAt(enteredPin.length() - 1);
            updateDots();
            vibrate(20);
        }
        if (tvErrorMsg != null) tvErrorMsg.setText("");
    }

    private void updateDots() {
        int len = enteredPin.length();
        setDot(dot1, len >= 1);
        setDot(dot2, len >= 2);
        setDot(dot3, len >= 3);
        setDot(dot4, len >= 4);
    }

    private void setDot(View dot, boolean filled) {
        if (dot == null) return;
        dot.setBackgroundResource(filled ? R.drawable.pin_dot_filled : R.drawable.pin_dot_empty);
    }

    private void checkPin() {
        String entered = enteredPin.toString();
        String correct = SettingsManager.getExitPin(this);

        if (entered.equals(correct)) {
            vibrate(80);
            hideOverlay();
            isInsideProtectedApp = false;
            activeGuardedPkg = null;
            sessionLockedPkg = null;

            if (ShizukuHelper.hasPermission()) {
                ShizukuHelper.unlockTask();
            }
            performGlobalAction(GLOBAL_ACTION_HOME);
        } else {
            vibrateDouble();
            if (tvErrorMsg != null) {
                tvErrorMsg.setText("Hatalı PIN, tekrar deneyin");
            }
            enteredPin.setLength(0);
            updateDots();
        }
    }

    private void returnToGuardedApp(String pkg) {
        hideOverlay();
        if (pkg != null) {
            Intent intent = getPackageManager().getLaunchIntentForPackage(pkg);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
            }
        }
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
        if (pkg.equals(getPackageName())) return true;
        if (pkg.equals("android")) return true;
        if (pkg.equals("com.android.systemui")) return true;
        if (pkg.startsWith("com.google.android.gms") || pkg.startsWith("com.google.android.play.games")) return true;
        if (pkg.equals("com.android.vending") || pkg.equals("com.google.android.webview") || pkg.startsWith("org.chromium")) return true;
        if (pkg.contains("permissioncontroller")) return true;
        if (pkg.startsWith("com.samsung.android.game") || pkg.startsWith("com.samsung.gamedriver")) return true;
        if (pkg.contains("incallui") || pkg.contains("telecom") || pkg.contains("dialer")) return true;
        if (pkg.contains("inputmethod") || pkg.contains("honeyboard") || pkg.contains("latin") || pkg.contains("keyboard")) return true;
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

    private void collapseStatusBarPanels() {
        try {
            Object statusBarService = getSystemService("statusbar");
            Class<?> statusBarManager = Class.forName("android.app.StatusBarManager");
            Method collapse = statusBarManager.getMethod("collapsePanels");
            collapse.invoke(statusBarService);
        } catch (Exception ignored) {}
    }

    private boolean isHomeLauncher(String pkg) {
        if (pkg == null) return false;
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            android.content.pm.ResolveInfo resolve = getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
            return resolve != null && resolve.activityInfo != null && pkg.equals(resolve.activityInfo.packageName);
        } catch (Exception ignored) {}
        return false;
    }

    private void cancelActiveTimer() {
        if (activeTimerFuture != null) {
            try {
                activeTimerFuture.cancel(true);
            } catch (Exception ignored) {}
            activeTimerFuture = null;
        }
        activeTimerPkg = null;
        activeTimerExpireAt = 0;
    }

    @Override
    public void onInterrupt() {
        cancelActiveTimer();
        ShizukuHelper.setPrivacyShield(false);
        isInsideProtectedApp = false;
        activeGuardedPkg = null;
        ownerUnlockedPkg = null;
        hideOverlay();
        hideFakeCrashOverlay();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        cancelActiveTimer();
        ShizukuHelper.setPrivacyShield(false);
        hideOverlay();
        hideFakeCrashOverlay();
    }
}
