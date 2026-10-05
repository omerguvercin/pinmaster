package com.pinmaster.app;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.view.accessibility.AccessibilityEvent;

public class PinAccessibilityService extends AccessibilityService {

    private static volatile boolean sIsGuardActive = false;
    private static volatile String sActiveGuardedPkg = null;
    private long lastLockLaunchTime = 0;

    public static void unlockSession() {
        sIsGuardActive = false;
        sActiveGuardedPkg = null;
    }

    public static boolean isGuardActive() {
        return sIsGuardActive;
    }

    public static String getActiveGuardedPackage() {
        return sActiveGuardedPkg;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }

        CharSequence pkgSeq = event.getPackageName();
        if (pkgSeq == null) return;
        String currentPkg = pkgSeq.toString();

        // Kendi uygulamamız ise atla
        if (currentPkg.equals(getPackageName())) {
            return;
        }

        // Ana anahtar kapalıysa her şeyi sıfırla
        if (!SettingsManager.isMasterEnabled(this)) {
            sIsGuardActive = false;
            sActiveGuardedPkg = null;
            return;
        }

        // Telefon kilitli veya ekran kapalıysa müdahale etme
        if (isScreenOffOrKeyguard()) {
            return;
        }

        // Çağrı ekranı veya klavye ise atla
        if (isExemptSystemPackage(currentPkg)) {
            return;
        }

        // 1. Durum: Kullanıcı korunan uygulamalardan birini açtı
        if (SettingsManager.isPackagePinned(this, currentPkg)) {
            sActiveGuardedPkg = currentPkg;
            sIsGuardActive = true;
            return;
        }

        // 2. Durum: Koruma aktifken kullanıcı korunan uygulamanın dışına çıkmaya çalıştı
        if (sIsGuardActive && sActiveGuardedPkg != null) {
            // Bildirim paneli aşağı çekildiyse engelleme (kullanıcı hızlı ayarları görebilsin)
            if (currentPkg.equals("com.android.systemui")) {
                return;
            }

            // Korunan uygulamanın dışına çıkıldı (Home tuşu, Recents, veya başka uygulama)
            long now = System.currentTimeMillis();
            if (now - lastLockLaunchTime < 300) {
                return; // Çok sık tetiklenmeyi önle
            }
            lastLockLaunchTime = now;

            // Kilit ekranını öne getir
            Intent lockIntent = new Intent(this, LockActivity.class);
            lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP
                    | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            lockIntent.putExtra(LockActivity.EXTRA_PACKAGE, sActiveGuardedPkg);
            startActivity(lockIntent);
        }
    }

    private boolean isScreenOffOrKeyguard() {
        try {
            KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            if (km != null && km.isKeyguardLocked()) return true;

            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isInteractive()) return true;
        } catch (Exception ignored) {}
        return false;
    }

    private boolean isExemptSystemPackage(String pkg) {
        if (pkg == null) return true;
        // Telefon görüşmesi arayüzleri
        if (pkg.contains("incallui") || pkg.contains("telecom") || pkg.contains("dialer")) {
            return true;
        }
        // Klavyeler
        if (pkg.contains("inputmethod") || pkg.contains("honeyboard") || pkg.contains("latin")) {
            return true;
        }
        return false;
    }

    @Override
    public void onInterrupt() {
        sIsGuardActive = false;
        sActiveGuardedPkg = null;
    }
}
