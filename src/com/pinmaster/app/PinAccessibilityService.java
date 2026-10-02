package com.pinmaster.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

public class PinAccessibilityService extends AccessibilityService {
    private static final String TAG = "PinAccessibility";
    private static boolean isServiceRunning = false;
    private Handler handler;
    private String lastPinnedPackage = "";
    private long lastPinnedTime = 0;
    private boolean isWaitingForRecents = false;
    private String pendingPinPackage = null;
    private boolean isPinningInProgress = false;

    public static boolean isRunning() {
        return isServiceRunning;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        Log.d(TAG, "PinAccessibilityService onCreate");
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        isServiceRunning = true;
        Log.d(TAG, "PinAccessibilityService onServiceConnected");
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
            setServiceInfo(info);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isServiceRunning = false;
        Log.d(TAG, "PinAccessibilityService onDestroy");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!SettingsManager.isMasterEnabled(this)) {
            return;
        }

        CharSequence pkgChar = event.getPackageName();
        if (pkgChar == null) return;
        final String packageName = pkgChar.toString();

        // PinMaster uygulamasının kendisini yoksay
        if (packageName.equals(getPackageName())) {
            return;
        }

        // Eğer Son Uygulamalar ekranı veya Menü açıldıysa
        if (isWaitingForRecents && isSystemUiOrLauncher(packageName)) {
            processRecentsPinning();
            return;
        }

        // Sistem onay diyalogları (Tamam / OK vb.)
        if (isSystemUiOrLauncher(packageName) || "android".equals(packageName)) {
            confirmSystemPinDialog();
            return;
        }

        int eventType = event.getEventType();
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }

        // Hedef uygulamalardan biri açıldı mı kontrol et
        if (SettingsManager.isPackagePinned(this, packageName)) {
            long now = System.currentTimeMillis();
            if (packageName.equals(lastPinnedPackage) && (now - lastPinnedTime < 5000)) {
                return; // Debounce: 5 saniye içinde aynı uygulama için tekrarlama
            }

            lastPinnedPackage = packageName;
            lastPinnedTime = now;
            pendingPinPackage = packageName;
            isPinningInProgress = true;

            int delay = SettingsManager.getDelayMs(this);
            Log.d(TAG, "Target app detected: " + packageName + ", scheduling pin in " + delay + "ms");

            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    triggerPinAction();
                }
            }, delay);
        }
    }

    private boolean isSystemUiOrLauncher(String pkg) {
        if (pkg == null) return false;
        return pkg.equals("com.android.systemui") ||
               pkg.equals("com.sec.android.app.launcher") ||
               pkg.equals("com.google.android.apps.nexuslauncher") ||
               pkg.equals("com.miui.home") ||
               pkg.equals("com.oppo.launcher") ||
               pkg.equals("com.oplus.launcher") ||
               pkg.equals("com.motorola.launcher3") ||
               pkg.equals("com.android.launcher3") ||
               pkg.equals("com.transsion.launcher") ||
               pkg.equals("com.huawei.android.launcher");
    }

    private void triggerPinAction() {
        Log.d(TAG, "Triggering GLOBAL_ACTION_RECENTS");
        isWaitingForRecents = true;
        performGlobalAction(GLOBAL_ACTION_RECENTS);

        // Birinci deneme: 200ms sonra
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isWaitingForRecents) {
                    processRecentsPinning();
                }
            }
        }, 220);

        // İkinci deneme güvenlik zaman aşımı: 2.5 saniye sonra durumu sıfırla
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                isWaitingForRecents = false;
                isPinningInProgress = false;
            }
        }, 2500);
    }

    private void processRecentsPinning() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        // 1. Önce menü zaten açık mı? "Bu uygulamayı sabitle" vb. var mı?
        boolean clickedPin = clickPinMenuItem(root);
        if (clickedPin) {
            Log.d(TAG, "Clicked Pin menu option successfully!");
            isWaitingForRecents = false;
            onPinSuccess();
            // Onay kutusu varsa tıkla
            scheduleConfirmDialogClicks();
            return;
        }

        // 2. Menü açık değilse, aktif kartın uygulama simgesine/başlığına tıkla
        boolean clickedIcon = clickActiveAppIconInRecents(root);
        if (clickedIcon) {
            Log.d(TAG, "Clicked active app icon in Recents. Waiting for menu...");
            // Menü açılınca otomatik "Sabitle"ye tıklanması için peş peşe kontrol
            schedulePinMenuChecks();
        }
    }

    private void schedulePinMenuChecks() {
        int[] delays = {100, 200, 350, 500, 700};
        for (final int d : delays) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!isWaitingForRecents) return;
                    AccessibilityNodeInfo activeRoot = getRootInActiveWindow();
                    if (activeRoot != null) {
                        if (clickPinMenuItem(activeRoot)) {
                            Log.d(TAG, "Pin menu clicked at delay " + d + "ms");
                            isWaitingForRecents = false;
                            onPinSuccess();
                            scheduleConfirmDialogClicks();
                        }
                    }
                }
            }, d);
        }
    }

    private void scheduleConfirmDialogClicks() {
        int[] delays = {150, 350, 600};
        for (final int d : delays) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    confirmSystemPinDialog();
                }
            }, d);
        }
    }

    private boolean clickPinMenuItem(AccessibilityNodeInfo root) {
        String[] pinTexts = {
            "Bu uygulamayı sabitle",
            "Uygulamayı sabitle",
            "Sabitle",
            "Pin this app",
            "Pin app",
            "Pin to screen",
            "Pin"
        };

        for (String text : pinTexts) {
            if (findAndClickNodeByText(root, text)) {
                return true;
            }
        }
        return false;
    }

    private boolean clickActiveAppIconInRecents(AccessibilityNodeInfo root) {
        String targetLabel = pendingPinPackage != null ? getAppLabel(pendingPinPackage) : "";

        // A) Samsung One UI: taskView listesinden hedef uygulamayı bul
        List<AccessibilityNodeInfo> taskViews = root.findAccessibilityNodeInfosByViewId("com.sec.android.app.launcher:id/taskView");
        if (taskViews != null && !taskViews.isEmpty()) {
            // Önce doğrudan hedef uygulamanın (örn. Instagram) adını taşıyan kartı ara
            for (int i = taskViews.size() - 1; i >= 0; i--) {
                AccessibilityNodeInfo task = taskViews.get(i);
                CharSequence desc = task.getContentDescription();
                if (desc != null) {
                    String descStr = desc.toString();
                    if (descStr.contains("PinMaster")) continue; // PinMaster'ı kesinlikle atla
                    if (!targetLabel.isEmpty() && descStr.toLowerCase().contains(targetLabel.toLowerCase())) {
                        if (clickIconInsideNode(task)) return true;
                    }
                }
            }
            // İkincil kontrol: PinMaster olmayan en son aktif karta tıkla
            for (int i = taskViews.size() - 1; i >= 0; i--) {
                AccessibilityNodeInfo task = taskViews.get(i);
                CharSequence desc = task.getContentDescription();
                if (desc != null && desc.toString().contains("PinMaster")) continue;
                if (clickIconInsideNode(task)) return true;
            }
        }

        // B) Gelişmiş seçenekler / Advanced options içeren içerik açıklaması (PinMaster hariç)
        List<AccessibilityNodeInfo> optionNodes = new ArrayList<>();
        findNodesByContentDescKeyword(root, optionNodes, "seçenekler");
        findNodesByContentDescKeyword(root, optionNodes, "options");
        for (AccessibilityNodeInfo node : optionNodes) {
            CharSequence desc = node.getContentDescription();
            if (desc != null && desc.toString().contains("PinMaster")) continue;
            if (performClickOnNode(node)) {
                return true;
            }
        }

        // C) Standart AOSP / Pixel launcher
        List<AccessibilityNodeInfo> icons = root.findAccessibilityNodeInfosByViewId("com.android.systemui:id/icon");
        if (icons == null || icons.isEmpty()) {
            icons = root.findAccessibilityNodeInfosByViewId("com.google.android.apps.nexuslauncher:id/icon");
        }
        if (icons != null && !icons.isEmpty()) {
            for (AccessibilityNodeInfo icon : icons) {
                if (performClickOnNode(icon)) {
                    return true;
                }
            }
        }

        // D) Doğrudan Samsung Launcher ikon kimliği
        List<AccessibilityNodeInfo> secIcons = root.findAccessibilityNodeInfosByViewId("com.sec.android.app.launcher:id/icon");
        if (secIcons != null && !secIcons.isEmpty()) {
            for (AccessibilityNodeInfo icon : secIcons) {
                // Sadece taskView içinde olan ikona tıkla (alttaki önerilen uygulamaları atla)
                AccessibilityNodeInfo parent = icon.getParent();
                if (parent != null) {
                    CharSequence parentClass = parent.getClassName();
                    if (parentClass != null && parentClass.toString().contains("FrameLayout")) {
                        if (performClickOnNode(icon)) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    private boolean clickIconInsideNode(AccessibilityNodeInfo parent) {
        if (parent == null) return false;
        // İçindeki "icon" veya "label" id'li elemanı ara
        List<AccessibilityNodeInfo> icons = parent.findAccessibilityNodeInfosByViewId("com.sec.android.app.launcher:id/icon");
        if (icons != null && !icons.isEmpty()) {
            for (AccessibilityNodeInfo icon : icons) {
                if (performClickOnNode(icon)) return true;
            }
        }
        List<AccessibilityNodeInfo> labels = parent.findAccessibilityNodeInfosByViewId("com.sec.android.app.launcher:id/label");
        if (labels != null && !labels.isEmpty()) {
            for (AccessibilityNodeInfo label : labels) {
                if (performClickOnNode(label)) return true;
            }
        }
        return false;
    }

    private void findNodesByContentDescKeyword(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> out, String keyword) {
        if (node == null) return;
        CharSequence desc = node.getContentDescription();
        if (desc != null && desc.toString().toLowerCase().contains(keyword.toLowerCase())) {
            out.add(node);
        }
        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                findNodesByContentDescKeyword(child, out, keyword);
            }
        }
    }

    private void confirmSystemPinDialog() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        String[] confirmTexts = {"Tamam", "TAMAM", "Anladım", "ANLADIM", "OK", "Got it", "GOT IT", "Sabitle"};
        for (String text : confirmTexts) {
            if (findAndClickNodeByText(root, text)) {
                Log.d(TAG, "Confirmed system dialog button: " + text);
                break;
            }
        }
    }

    private boolean findAndClickNodeByText(AccessibilityNodeInfo root, String targetText) {
        if (root == null) return false;
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(targetText);
        if (nodes != null && !nodes.isEmpty()) {
            for (AccessibilityNodeInfo node : nodes) {
                if (performClickOnNode(node)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean performClickOnNode(AccessibilityNodeInfo node) {
        if (node == null) return false;
        if (node.isClickable()) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        }
        AccessibilityNodeInfo parent = node.getParent();
        if (parent != null) {
            boolean clicked = performClickOnNode(parent);
            parent.recycle();
            return clicked;
        }
        return false;
    }

    private void onPinSuccess() {
        isWaitingForRecents = false;
        isPinningInProgress = false;

        if (SettingsManager.isVibrateEnabled(this)) {
            vibrateDevice();
        }
        // Bildirim (Toast) kullanıcı isteği doğrultusunda tamamen kaldırıldı
    }

    private void vibrateDevice() {
        try {
            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(80);
                }
            }
        } catch (Exception ignored) {}
    }

    private String getAppLabel(String packageName) {
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
            return pm.getApplicationLabel(info).toString();
        } catch (Exception e) {
            return packageName;
        }
    }

    @Override
    public void onInterrupt() {
        isServiceRunning = false;
        isWaitingForRecents = false;
    }
}
