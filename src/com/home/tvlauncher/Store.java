package com.home.tvlauncher;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Small wrapper around SharedPreferences for everything the launcher remembers. */
final class Store {
    static final int MAX_RECENTS = 6;

    private final SharedPreferences p;

    Store(Context c) {
        p = c.getSharedPreferences("home", Context.MODE_PRIVATE);
    }

    // ---- ordered package lists ----
    private List<String> list(String key) {
        String s = p.getString(key, "");
        List<String> out = new ArrayList<>();
        if (!s.isEmpty()) out.addAll(Arrays.asList(s.split(",")));
        return out;
    }

    private void putList(String key, List<String> l) {
        p.edit().putString(key, String.join(",", l)).apply();
    }

    List<String> favorites() { return list("favorites"); }
    void setFavorites(List<String> l) { putList("favorites", l); }
    boolean favoritesInitialized() { return p.getBoolean("fav_init", false); }
    void markFavoritesInitialized() { p.edit().putBoolean("fav_init", true).apply(); }

    List<String> hidden() { return list("hidden"); }
    void setHidden(List<String> l) { putList("hidden", l); }

    List<String> recents() { return list("recents"); }
    void pushRecent(String pkg) {
        List<String> r = recents();
        r.remove(pkg);
        r.add(0, pkg);
        while (r.size() > MAX_RECENTS) r.remove(r.size() - 1);
        putList("recents", r);
    }

    // ---- custom names ----
    String label(String pkg) { return p.getString("label_" + pkg, null); }
    void setLabel(String pkg, String name) {
        if (name == null || name.trim().isEmpty()) p.edit().remove("label_" + pkg).apply();
        else p.edit().putString("label_" + pkg, name.trim()).apply();
    }

    String inputName(String id) { return p.getString("input_" + id, null); }
    void setInputName(String id, String name) {
        if (name == null || name.trim().isEmpty()) p.edit().remove("input_" + id).apply();
        else p.edit().putString("input_" + id, name.trim()).apply();
    }

    // ---- misc ----
    int wallpaper() { return p.getInt("wallpaper", 0); }
    void setWallpaper(int i) { p.edit().putInt("wallpaper", i).apply(); }

    long sleepAt() { return p.getLong("sleep_at", 0); }
    void setSleepAt(long t) { p.edit().putLong("sleep_at", t).apply(); }

    /** While this time is in the future, the Home-button service lets Google's home screen through. */
    long googleHomeBypassUntil() { return p.getLong("bypass_until", 0); }
    void setGoogleHomeBypassUntil(long t) { p.edit().putLong("bypass_until", t).commit(); }
}
