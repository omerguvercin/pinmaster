package com.pinmaster.app;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;

/**
 * PinAccessibilityService — Uygulama açılışlarını izler.
 *
 * Android her boot sonrasında erişilebilirlik servislerini
 * otomatik olarak yeniden başlatır. Manuel adım gerekmez.
 *
 * Akış:
 *   Pinlenmiş uygulama açıldı
 *     → GuardActivity arka planda başlar
 *     → startLockTask() sessizce çalışır
 *     → Home/Recents devre dışı
 */
public class PinAccessibilityService extends AccessibilityService {

    // Son kilitlenen paket — tekrar tetiklenmeyi önler
    private String lastGuardedPkg = null;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Yalnızca uygulama/pencere değişimlerini dinle
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;

        CharSequence pkgSeq = event.getPackageName();
        if (pkgSeq == null) return;
        String pkg = pkgSeq.toString();

        // Kendi uygulamamızı ve sistem UI'yi yoksay
        if (pkg.equals(getPackageName())) return;
        if (pkg.equals("com.android.systemui")) return;
        if (pkg.equals("android")) return;

        // Ana ekrana / launcher'a geçildiyse kilidi sıfırla
        if (isLauncher(pkg)) {
            lastGuardedPkg = null;
            return;
        }

        // Koruma kapalıysa işlem yapma
        if (!SettingsManager.isMasterEnabled(this)) {
            lastGuardedPkg = null;
            return;
        }

        // Pinlenmiş uygulama açıldı mı?
        if (SettingsManager.isPackagePinned(this, pkg)) {
            // Aynı uygulama için tekrar tetiklenmesin
            if (pkg.equals(lastGuardedPkg)) return;

            lastGuardedPkg = pkg;
            startGuard(pkg);
        } else {
            // Pinlenmiş olmayan bir uygulama — takibi sıfırla
            lastGuardedPkg = null;
        }
    }

    /**
     * GuardActivity'yi arka planda başlatır.
     * Kullanıcı herhangi bir şey görmez — pinlenmiş uygulama önde.
     */
    private void startGuard(String pkg) {
        Intent intent = new Intent(this, GuardActivity.class);
        // FLAG_ACTIVITY_NEW_TASK: servisten activity başlatmak için zorunlu
        // FLAG_ACTIVITY_SINGLE_TOP: zaten açıksa yenisini açma, onNewIntent çağır
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra(GuardActivity.EXTRA_PACKAGE, pkg);
        startActivity(intent);
    }

    private boolean isLauncher(String pkg) {
        return pkg.contains("launcher")
                || pkg.contains("home")
                || pkg.contains("nexus")
                || pkg.contains("pixel");
    }

    @Override
    public void onInterrupt() {
        lastGuardedPkg = null;
    }
}
