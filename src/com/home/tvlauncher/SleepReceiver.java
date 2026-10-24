package com.home.tvlauncher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

/** Fired by the sleep timer alarm. */
public class SleepReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        new Store(c).setSleepAt(0);
        HomeButtonService s = HomeButtonService.instance;
        if (s == null || !s.sleepNow()) {
            Toast.makeText(c, "Sleep timer needs the My Home Home-button service turned on",
                    Toast.LENGTH_LONG).show();
        }
    }
}
