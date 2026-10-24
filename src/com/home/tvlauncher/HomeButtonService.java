package com.home.tvlauncher;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Catches the remote's Home key and opens My Home instead of Google's home screen.
 * Also notes which app comes to the front, for the "Recent" row.
 * It never reads screen contents (canRetrieveWindowContent=false).
 */
public class HomeButtonService extends AccessibilityService {
    static volatile HomeButtonService instance;

    private static final Set<String> GOOGLE_HOMES = new HashSet<>(Arrays.asList(
            "com.google.android.apps.tv.launcherx",
            "com.google.android.tvlauncher",
            "com.google.android.leanbacklauncher"));

    private static final Set<String> NOT_RECENT = new HashSet<>(Arrays.asList(
            "com.android.systemui",
            "com.google.android.katniss",          // voice search overlay
            "com.google.android.inputmethod.latin",
            "com.google.android.tv.assistant",
            "android"));

    private Store store;

    @Override
    protected void onServiceConnected() {
        instance = this;
        store = new Store(this);
    }

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event.getKeyCode() != KeyEvent.KEYCODE_HOME) return false;
        if (event.getAction() == KeyEvent.ACTION_UP) {
            store.setGoogleHomeBypassUntil(0);
            openMyHome();
        }
        return true; // swallow both down and up so Google's home never sees it
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence cs = event.getPackageName();
        if (cs == null) return;
        String pkg = cs.toString();

        if (GOOGLE_HOMES.contains(pkg)) {
            // Fallback for when the system shows Google's home without a Home key press
            // (e.g. after exiting an app with Back, or after boot).
            if (System.currentTimeMillis() > store.googleHomeBypassUntil()) openMyHome();
            return;
        }
        if (pkg.equals(getPackageName()) || NOT_RECENT.contains(pkg)) return;
        if (isLaunchable(pkg)) store.pushRecent(pkg);
    }

    private boolean isLaunchable(String pkg) {
        PackageManager pm = getPackageManager();
        return pm.getLeanbackLaunchIntentForPackage(pkg) != null
                || pm.getLaunchIntentForPackage(pkg) != null;
    }

    void openMyHome() {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        startActivity(i);
    }

    boolean sleepNow() {
        return performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
    }

    @Override
    public void onInterrupt() { }
}
