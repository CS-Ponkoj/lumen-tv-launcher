package com.home.tvlauncher;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Settings;

/**
 * Android turns accessibility services off when the TV restarts, which leaves the Home button opening
 * the stock home screen. If the TV has granted WRITE_SECURE_SETTINGS (once, over ADB:
 * pm grant com.home.tvlauncher android.permission.WRITE_SECURE_SETTINGS), switch it back on at boot.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        enableHomeButton(c);
    }

    static void enableHomeButton(Context c) {
        if (c.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") != PackageManager.PERMISSION_GRANTED) return;
        try {
            String me = new ComponentName(c, HomeButtonService.class).flattenToString();
            String on = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (on == null || on.isEmpty() || "null".equals(on)) on = "";
            String others = (":" + on + ":").replace(":" + me + ":", ":").replaceAll("^:+|:+$", "");
            boolean listed = !others.equals(on);
            if (listed && HomeButtonService.instance != null) return;     // already on and running
            String full = others.isEmpty() ? me : others + ":" + me;
            if (listed) {
                // Listed but not running (TCL blocks the bind at boot): drop it, and add it back a moment later,
                // so Android sees two changes and binds again (two quick writes are read as no change).
                Settings.Secure.putString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, others);
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> write(c, full), 1500);
            } else {
                write(c, full);
            }
        } catch (Exception ignored) { }
    }

    private static void write(Context c, String services) {
        try {
            Settings.Secure.putString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, services);
            Settings.Secure.putInt(c.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 1);
        } catch (Exception ignored) { }
    }
}
