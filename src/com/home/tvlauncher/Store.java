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

    // ---- custom categories ----
    List<String> categories() {
        String s = p.getString("categories", "");
        List<String> out = new ArrayList<>();
        if (!s.isEmpty()) out.addAll(Arrays.asList(s.split("\\|")));
        return out;
    }
    void setCategories(List<String> l) { p.edit().putString("categories", String.join("|", l)).apply(); }
    List<String> categoryApps(String name) { return list("cat_" + name); }
    void setCategoryApps(String name, List<String> l) { putList("cat_" + name, l); }
    void deleteCategory(String name) {
        List<String> c = categories(); c.remove(name); setCategories(c);
        p.edit().remove("cat_" + name).apply();
    }
    void renameCategory(String from, String to) {
        List<String> c = categories();
        int i = c.indexOf(from);
        if (i < 0) return;
        c.set(i, to);
        setCategories(c);
        setCategoryApps(to, categoryApps(from));
        p.edit().remove("cat_" + from).apply();
    }

    boolean categoryGrid(String name) { return p.getBoolean("catgrid_" + name, false); }
    void setCategoryGrid(String name, boolean grid) { p.edit().putBoolean("catgrid_" + name, grid).apply(); }

    // ---- wallpaper schedule, clock, data usage, focus border ----
    boolean dayNight() { return p.getBoolean("day_night", false); }
    void setDayNight(boolean b) { p.edit().putBoolean("day_night", b).apply(); }
    int nightWallpaper() { return p.getInt("night_wallpaper", 11); }
    void setNightWallpaper(int w) { p.edit().putInt("night_wallpaper", w).apply(); }
    int timeFormat() { return p.getInt("time_format", 0); }
    void setTimeFormat(int f) { p.edit().putInt("time_format", f).apply(); }
    int dateFormat() { return p.getInt("date_format", 0); }
    void setDateFormat(int f) { p.edit().putInt("date_format", f).apply(); }
    boolean showDataUsage() { return p.getBoolean("show_data", true); }
    void setShowDataUsage(boolean b) { p.edit().putBoolean("show_data", b).apply(); }
    static final int LAYOUT_SHOWCASE = 0, LAYOUT_CLASSIC = 1;
    int layout() { return p.getInt("layout", LAYOUT_SHOWCASE); }
    void setLayout(int l) { p.edit().putInt("layout", l).apply(); }
    static final int FOCUS_GLASS = 0, FOCUS_MINIMAL = 1;
    int focusStyle() { return p.getInt("focus_style", FOCUS_GLASS); }
    void setFocusStyle(int f) { p.edit().putInt("focus_style", f).apply(); }
    boolean sounds() { return p.getBoolean("sounds", true); }
    void setSounds(boolean b) { p.edit().putBoolean("sounds", b).apply(); }
    int border() { return p.getInt("border", 1); }
    void setBorder(int b) { p.edit().putInt("border", b).apply(); }

    // ---- look ----
    static final int STYLE_MODERN = 0, STYLE_PREMIUM = 1, STYLE_CAPSULE = 2;
    int cardStyle() { return p.getInt("card_style", STYLE_MODERN); }
    void setCardStyle(int s) { p.edit().putInt("card_style", s).apply(); }
    int accent() { return p.getInt("accent", 0); }
    void setAccent(int a) { p.edit().putInt("accent", a).apply(); }
    boolean continueWatching() { return p.getBoolean("continue_watching", true); }
    void setContinueWatching(boolean b) { p.edit().putBoolean("continue_watching", b).apply(); }

    // ---- misc ----
    int wallpaper() { return p.getInt("wallpaper", 8); }
    void setWallpaper(int i) { p.edit().putInt("wallpaper", i).apply(); }

    long sleepAt() { return p.getLong("sleep_at", 0); }
    void setSleepAt(long t) { p.edit().putLong("sleep_at", t).apply(); }

    /** While this time is in the future, the Home-button service lets Google's home screen through. */
    long googleHomeBypassUntil() { return p.getLong("bypass_until", 0); }
    void setGoogleHomeBypassUntil(long t) { p.edit().putLong("bypass_until", t).commit(); }
}
