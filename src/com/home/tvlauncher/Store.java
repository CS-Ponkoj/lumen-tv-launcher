package com.home.tvlauncher;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Everything Lumen remembers. Keys are the same as v1, so settings carry over on update. */
final class Store {
    static final int MAX_RECENTS = 8;

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

    // ---- clock, sounds, data usage, room ----
    /** 0 = 12-hour with AM/PM, 1 = 24-hour, 2 = 12-hour without AM/PM. */
    int timeFormat() { return p.getInt("time_format", 2); }
    void setTimeFormat(int f) { p.edit().putInt("time_format", f).apply(); }
    boolean sounds() { return p.getBoolean("sounds", true); }
    void setSounds(boolean b) { p.edit().putBoolean("sounds", b).apply(); }
    boolean showDataUsage() { return p.getBoolean("show_data", true); }
    void setShowDataUsage(boolean b) { p.edit().putBoolean("show_data", b).apply(); }
    float uv() {
        if (!p.getBoolean("extras_v1", false)) { // fetch UV and humidity once after this update
            p.edit().putBoolean("extras_v1", true).putLong("weather_time", 0).apply();
        }
        return p.getFloat("weather_uv", -1);
    }
    int humidity() { return p.getInt("weather_hum", -1); }
    void setExtras(float uv, int hum) { p.edit().putFloat("weather_uv", uv).putInt("weather_hum", hum).apply(); }
    String roomName() { return p.getString("room_name", "Living room"); }
    void setRoomName(String n) { p.edit().putString("room_name", n).apply(); }

    // ---- weather (opt-in) ----
    boolean weatherOn() { return p.getBoolean("weather_on", false); }
    void setWeatherOn(boolean b) { p.edit().putBoolean("weather_on", b).apply(); }
    String weatherCity() { return p.getString("weather_city", ""); }
    void setWeatherCity(String c) { p.edit().putString("weather_city", c).apply(); }
    String weatherLat() { return p.getString("weather_lat", ""); }
    String weatherLon() { return p.getString("weather_lon", ""); }
    String weatherPlace() { return p.getString("weather_place", weatherCity()); }
    void setWeatherLocation(String lat, String lon, String place) {
        p.edit().putString("weather_lat", lat).putString("weather_lon", lon).putString("weather_place", place).apply();
    }
    /** Fahrenheit by default (a one-time switch moves older installs over). */
    boolean weatherFahrenheit() {
        if (!p.getBoolean("units_f_default", false)) {
            p.edit().putBoolean("units_f_default", true).putBoolean("weather_f", true).putLong("weather_time", 0).apply();
        }
        return p.getBoolean("weather_f", true);
    }
    void setWeatherFahrenheit(boolean f) { p.edit().putBoolean("weather_f", f).apply(); }
    float weatherTemp() { return p.getFloat("weather_temp", 0); }
    int weatherCode() { return p.getInt("weather_code", 0); }
    long weatherTime() { return p.getLong("weather_time", 0); }
    void setWeather(float temp, int code, long time) {
        p.edit().putFloat("weather_temp", temp).putInt("weather_code", code).putLong("weather_time", time).apply();
    }

    // ---- live scene ----
    /** true = scene follows time of day and weather; false = always Winter Snowfall. */
    boolean liveScene() { return p.getBoolean("live_scene", true); }
    void setLiveScene(boolean b) { p.edit().putBoolean("live_scene", b).apply(); }
    /** Sunrise / sunset as minutes after midnight (local), -1 when unknown. */
    int sunrise() { return p.getInt("sunrise_min", -1); }
    int sunset() { return p.getInt("sunset_min", -1); }
    void setSun(int rise, int set) { p.edit().putInt("sunrise_min", rise).putInt("sunset_min", set).apply(); }

    // ---- sleep timer & Google home bypass ----
    long sleepAt() { return p.getLong("sleep_at", 0); }
    void setSleepAt(long t) { p.edit().putLong("sleep_at", t).apply(); }

    /** While this time is in the future, the Home-button service lets Google's home screen through. */
    long googleHomeBypassUntil() { return p.getLong("bypass_until", 0); }
    void setGoogleHomeBypassUntil(long t) { p.edit().putLong("bypass_until", t).commit(); }
}
