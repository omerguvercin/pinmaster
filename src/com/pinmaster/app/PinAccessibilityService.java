package com.pinmaster.app;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

public class PinAccessibilityService extends AccessibilityService {

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Daemon arka planda sessizce çalıştığı için ekstra UI müdahalesine gerek yoktur.
    }

    @Override
    public void onInterrupt() {}
}
