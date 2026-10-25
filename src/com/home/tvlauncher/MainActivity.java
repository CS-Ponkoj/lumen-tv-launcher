package com.home.tvlauncher;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lumen v2: one look (Winter Snowfall), laid out like Google TV / CleanYourTV.
 * Info panel top-left, pills top-right, the focused app's name in large type,
 * a favourites tray, then Recent and All apps rows.
 */
public class MainActivity extends Activity {
    private static final String[] DEFAULT_FAVORITES = {
            "com.netflix.ninja", "com.google.android.youtube.tv", "com.disney.disneyplus",
            "com.amazon.amazonvideo.livingroom", "com.wbd.stream", "com.hulu.livingroomplus"};
    private static final String[] TIME_FORMATS = {"h:mm a", "HH:mm", "h:mm"};
    private static final int GRID_COLUMNS = 7;

    private Store store;
    private PackageManager pm;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private FrameLayout root;
    private ScrollView scroll;
    private LinearLayout content;
    private TextView titleView, greetingView, statusView, weatherTemp, weatherPlace, wifiPillText;
    private ImageView weatherIcon, wifiPillIcon;
    private LinearLayout weatherRow;
    private View favoritesTray, firstFocus, settingsPill;
    private View panel;
    private View homePill;
    private String dataToday;
    private Bitmap wallpaper;

    private final Map<String, App> apps = new LinkedHashMap<>();
    private final Map<String, View> favTiles = new HashMap<>();
    private String movePkg;

    private static final class App {
        String pkg, label;
        Drawable banner, icon;
    }

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { rebuild(); }
    };

    private final Runnable minuteTick = new Runnable() {
        @Override public void run() {
            refreshInfo();
            handler.postDelayed(this, 30_000);
        }
    };

    // ================================================================== lifecycle

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        store = new Store(this);
        pm = getPackageManager();
        root = new FrameLayout(this);
        setContentView(root);

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(packageReceiver, f);

        try {
            getSystemService(ConnectivityManager.class).registerDefaultNetworkCallback(
                    new ConnectivityManager.NetworkCallback() {
                        @Override public void onAvailable(Network n) { runOnUiThread(MainActivity.this::refreshInfo); }
                        @Override public void onLost(Network n) { runOnUiThread(MainActivity.this::refreshInfo); }
                    });
        } catch (Exception ignored) { }
    }

    @Override
    protected void onResume() {
        super.onResume();
        rebuild();
        handler.post(minuteTick);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(minuteTick);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        try { unregisterReceiver(packageReceiver); } catch (Exception ignored) { }
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home pressed while already here: back to the top, first favourite selected.
        closePanel();
        movePkg = null;
        if (scroll != null) scroll.smoothScrollTo(0, 0);
        if (firstFocus != null) firstFocus.requestFocus();
    }

    @Override
    public void onBackPressed() {
        if (panel != null) { closePanel(); if (settingsPill != null) settingsPill.requestFocus(); return; }
        if (scroll != null && scroll.getScrollY() > 0) {
            scroll.smoothScrollTo(0, 0);
            if (firstFocus != null) firstFocus.requestFocus();
        }
    }

    // ================================================================== screen

    private void rebuild() {
        loadApps();
        initDefaultFavorites();
        root.removeAllViews();
        panel = null;
        favTiles.clear();
        firstFocus = null;

        // Wallpaper: misty forest, graded dark and mauve, with falling snow and a darker bottom.
        ImageView bg = new ImageView(this);
        bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        bg.setImageBitmap(wallpaper());
        root.addView(bg, new FrameLayout.LayoutParams(-1, -1));
        root.addView(new SnowView(this), new FrameLayout.LayoutParams(-1, -1));
        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x33000000, 0x00000000, 0x00000000, 0x8C000000}));
        root.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        scroll = new ScrollView(this) {
            // We glide to rows ourselves; stop the default jump.
            @Override protected int computeScrollDeltaToGetChildRectOnScreen(Rect r) { return 0; }
        };
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setClipChildren(false);
        content.setClipToPadding(false);
        content.setPadding(dp(64), dp(40), dp(64), dp(40));
        scroll.addView(content);
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        // 1. Header: info panel left, pills right
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.TOP);
        header.addView(infoPanel(), new LinearLayout.LayoutParams(0, -2, 1f));
        header.addView(pills());
        content.addView(header);

        // 2. Space, then the focused app's name and the favourites tray (fills the first screen)
        View spacer = new View(this);
        content.addView(spacer, new LinearLayout.LayoutParams(-1, dp(120)));

        titleView = new TextView(this);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40);
        titleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        titleView.setShadowLayer(dp(8), 0, dp(2), 0x80000000);
        titleView.setSingleLine(true);
        titleView.setPadding(dp(8), 0, 0, dp(12));
        content.addView(titleView);

        List<String> favs = visible(store.favorites());
        if (favs.isEmpty()) favs = new ArrayList<>(apps.keySet()).subList(0, Math.min(6, apps.size()));
        favoritesTray = favoritesTray(favs);
        content.addView(favoritesTray);

        // 3. Recent
        List<String> rec = visible(store.recents());
        rec.removeAll(favs);
        if (!rec.isEmpty()) {
            content.addView(sectionTitle("Recent"));
            content.addView(smallRow(rec));
        }

        // 4. All apps
        content.addView(sectionTitle("All apps"));
        List<App> all = new ArrayList<>();
        List<String> hidden = store.hidden();
        for (App a : apps.values()) if (!hidden.contains(a.pkg)) all.add(a);
        Collections.sort(all, (x, y) -> x.label.compareToIgnoreCase(y.label));
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(GRID_COLUMNS);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        for (App a : all) grid.addView(smallTile(a));
        lockEdges(grid, GRID_COLUMNS);
        content.addView(grid);

        // 5. Credit
        TextView credit = new TextView(this);
        credit.setText("Lumen · Developed by Ponkoj Shill");
        credit.setTextColor(0x80FFFFFF);
        credit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        credit.setGravity(Gravity.CENTER_HORIZONTAL);
        credit.setPadding(0, dp(36), 0, dp(8));
        content.addView(credit, new LinearLayout.LayoutParams(-1, -2));

        // Place the tray near the bottom of the first screen, like CleanYourTV.
        scroll.post(() -> {
            int used = header.getHeight() + titleView.getHeight() + favoritesTray.getHeight() + dp(40) + dp(96);
            spacer.getLayoutParams().height = Math.max(dp(40), scroll.getHeight() - used);
            spacer.requestLayout();
            if (firstFocus != null && (getCurrentFocus() == null || movePkg == null)) {
                View focusTarget = movePkg != null ? favTiles.get(movePkg) : firstFocus;
                if (focusTarget != null) focusTarget.requestFocus();
            }
            scroll.scrollTo(0, 0);
        });
        refreshInfo();
    }

    // ================================================================== info panel (unchanged look)

    private View infoPanel() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        LinearLayout timeRow = new LinearLayout(this);
        timeRow.setGravity(Gravity.BOTTOM);
        TextClock time = new TextClock(this);
        String tf = TIME_FORMATS[Math.max(0, Math.min(2, store.timeFormat()))];
        time.setFormat12Hour(tf);
        time.setFormat24Hour(tf);
        time.setTextColor(Color.WHITE);
        time.setTextSize(TypedValue.COMPLEX_UNIT_SP, 46);
        time.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        time.setLetterSpacing(0.02f);
        time.setShadowLayer(dp(8), 0, dp(1), 0x66000000);
        timeRow.addView(time);
        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE, d MMMM");
        date.setFormat24Hour("EEEE, d MMMM");
        date.setTextColor(0xF2FFFFFF);
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        date.setShadowLayer(dp(6), 0, dp(1), 0x66000000);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-2, -2);
        dlp.leftMargin = dp(20);
        dlp.bottomMargin = dp(9);
        timeRow.addView(date, dlp);
        col.addView(timeRow);

        weatherRow = new LinearLayout(this);
        weatherRow.setGravity(Gravity.CENTER_VERTICAL);
        weatherIcon = new ImageView(this);
        weatherRow.addView(weatherIcon, new LinearLayout.LayoutParams(dp(30), dp(30)));
        weatherTemp = infoText(28, Color.WHITE);
        weatherTemp.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        LinearLayout.LayoutParams wt = new LinearLayout.LayoutParams(-2, -2);
        wt.leftMargin = dp(10);
        weatherRow.addView(weatherTemp, wt);
        weatherPlace = infoText(14, 0xF2FFFFFF);
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(-2, -2);
        wp.leftMargin = dp(14);
        weatherRow.addView(weatherPlace, wp);
        weatherRow.setVisibility(View.GONE);
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(-2, -2);
        wl.topMargin = dp(8);
        col.addView(weatherRow, wl);

        statusView = infoText(14, 0xF2FFFFFF);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, -2);
        sl.topMargin = dp(8);
        col.addView(statusView, sl);

        greetingView = infoText(14, 0xF2FFFFFF);
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(-2, -2);
        gl.topMargin = dp(4);
        col.addView(greetingView, gl);
        return col;
    }

    private TextView infoText(float sp, int color) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setShadowLayer(dp(6), 0, dp(1), 0x73000000);
        return t;
    }

    private void refreshInfo() {
        if (greetingView != null) greetingView.setText(greeting());
        updateStatusLine();
        showWeather();
        updateDataUsage();
    }

    private String greeting() {
        int h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        return h < 5 ? "Good night" : h < 12 ? "Good morning" : h < 17 ? "Good afternoon" : h < 22 ? "Good evening" : "Good night";
    }

    /** "Living room · Wi-Fi good · 98 MB today · Sleep in 20 min" */
    private void updateStatusLine() {
        String net = networkSummary();
        if (wifiPillText != null) wifiPillText.setText(net.isEmpty() ? "Network" : net);
        if (wifiPillIcon != null) {
            int lvl = net.equals("Offline") ? -1 : net.equals("Ethernet") ? 3 : Math.max(0, wifiLevel());
            IconDrawable d = new IconDrawable(IconDrawable.WIFI, lvl);
            View pill = (View) wifiPillIcon.getParent();
            if (pill != null && pill.isFocused()) d.setColor(0xFF15151A);
            wifiPillIcon.setImageDrawable(d);
        }
        if (statusView == null) return;
        StringBuilder sb = new StringBuilder(store.roomName());
        if (!net.isEmpty()) sb.append(" · ").append(net);
        if (dataToday != null && store.showDataUsage()) sb.append(" · ").append(dataToday);
        long left = store.sleepAt() - System.currentTimeMillis();
        if (store.sleepAt() != 0 && left > 0) {
            sb.append(" · Sleep in ").append(Math.max(1, (left + 59_999) / 60_000)).append(" min");
        }
        statusView.setText(sb.toString());
    }

    private String networkSummary() {
        try {
            ConnectivityManager cm = getSystemService(ConnectivityManager.class);
            NetworkCapabilities c = cm.getNetworkCapabilities(cm.getActiveNetwork());
            if (c == null) return "Offline";
            if (c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return "Ethernet";
            if (c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                int lvl = wifiLevel();
                return lvl < 0 ? "Wi-Fi" : lvl >= 2 ? "Wi-Fi good" : "Wi-Fi weak";
            }
            return "Online";
        } catch (Exception e) {
            return "";
        }
    }

    private int wifiLevel() {
        try {
            android.net.wifi.WifiManager wm = getApplicationContext().getSystemService(android.net.wifi.WifiManager.class);
            android.net.wifi.WifiInfo info = wm.getConnectionInfo();
            if (info == null || info.getRssi() <= -127) return -1;
            return android.net.wifi.WifiManager.calculateSignalLevel(info.getRssi(), 4);
        } catch (Exception e) {
            return -1;
        }
    }

    // ---- weather (opt-in; Open-Meteo, only the city is sent)

    private boolean fetchingWeather;

    private void showWeather() {
        if (weatherRow == null) return;
        store.weatherFahrenheit(); // applies the one-time Fahrenheit switch before the cache is read
        if (!store.weatherOn() || store.weatherTime() == 0) {
            weatherRow.setVisibility(View.GONE);
        } else {
            weatherIcon.setImageDrawable(new WeatherIcon(WeatherIcon.fromCode(store.weatherCode())));
            weatherTemp.setText(Math.round(store.weatherTemp()) + (store.weatherFahrenheit() ? "°F" : "°C"));
            weatherPlace.setText(store.weatherPlace() + " · " + WeatherIcon.describe(store.weatherCode()));
            weatherRow.setVisibility(View.VISIBLE);
        }
        if (store.weatherOn() && System.currentTimeMillis() - store.weatherTime() > 30 * 60_000L) fetchWeather();
    }

    private void fetchWeather() {
        if (fetchingWeather || store.weatherCity().isEmpty()) return;
        fetchingWeather = true;
        new Thread(() -> {
            try {
                if (store.weatherLat().isEmpty()) {
                    String q = java.net.URLEncoder.encode(store.weatherCity(), "UTF-8");
                    org.json.JSONObject g = new org.json.JSONObject(httpGet(
                            "https://geocoding-api.open-meteo.com/v1/search?count=1&language=en&name=" + q));
                    org.json.JSONArray res = g.optJSONArray("results");
                    if (res == null || res.length() == 0) {
                        runOnUiThread(() -> toast("Weather: couldn't find \"" + store.weatherCity() + "\""));
                        return;
                    }
                    org.json.JSONObject r = res.getJSONObject(0);
                    store.setWeatherLocation(String.valueOf(r.getDouble("latitude")),
                            String.valueOf(r.getDouble("longitude")), r.optString("name", store.weatherCity()));
                }
                String unit = store.weatherFahrenheit() ? "&temperature_unit=fahrenheit" : "";
                org.json.JSONObject f = new org.json.JSONObject(httpGet("https://api.open-meteo.com/v1/forecast?latitude="
                        + store.weatherLat() + "&longitude=" + store.weatherLon()
                        + "&current=temperature_2m,weather_code" + unit));
                org.json.JSONObject cur = f.getJSONObject("current");
                store.setWeather((float) cur.getDouble("temperature_2m"), cur.getInt("weather_code"), System.currentTimeMillis());
                runOnUiThread(this::showWeather);
            } catch (Exception ignored) {
                // offline: keep the last known weather
            } finally {
                fetchingWeather = false;
            }
        }).start();
    }

    private static String httpGet(String url) throws java.io.IOException {
        java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        try (java.io.InputStream in = c.getInputStream()) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }

    // ---- data used today (TV's own counters)

    private boolean hasUsageAccess() {
        try {
            android.app.AppOpsManager ops = getSystemService(android.app.AppOpsManager.class);
            return ops.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(), getPackageName()) == android.app.AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return false;
        }
    }

    private void updateDataUsage() {
        if (!store.showDataUsage() || !hasUsageAccess()) { dataToday = null; return; }
        new Thread(() -> {
            long total = 0;
            try {
                android.app.usage.NetworkStatsManager nsm = getSystemService(android.app.usage.NetworkStatsManager.class);
                java.util.Calendar c = java.util.Calendar.getInstance();
                c.set(java.util.Calendar.HOUR_OF_DAY, 0);
                c.set(java.util.Calendar.MINUTE, 0);
                c.set(java.util.Calendar.SECOND, 0);
                for (int type : new int[]{ConnectivityManager.TYPE_WIFI, ConnectivityManager.TYPE_ETHERNET}) {
                    try {
                        android.app.usage.NetworkStats.Bucket b =
                                nsm.querySummaryForDevice(type, null, c.getTimeInMillis(), System.currentTimeMillis());
                        if (b != null) total += b.getRxBytes() + b.getTxBytes();
                    } catch (Exception ignored) { }
                }
            } catch (Exception ignored) { }
            final String t = (total >= 1L << 30 ? String.format(Locale.US, "%.1f GB", total / (double) (1L << 30))
                    : String.format(Locale.US, "%.0f MB", total / (double) (1L << 20))) + " today";
            runOnUiThread(() -> { dataToday = t; updateStatusLine(); });
        }).start();
    }

    // ================================================================== pills (Inputs · Wi-Fi · Settings)

    private View pills() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(pill(new IconDrawable(IconDrawable.INPUT), "Inputs", null, this::inputsDialog));
        homePill = pill(new IconDrawable(IconDrawable.HOME), "Home", null, this::openGoogleHomePanel);
        row.addView(homePill);
        LinearLayout wifi = pill(new IconDrawable(IconDrawable.WIFI, wifiLevel()), "Wi-Fi", null, this::networkDialog);
        wifiPillIcon = (ImageView) wifi.getChildAt(0);
        wifiPillText = (TextView) wifi.getChildAt(1);
        row.addView(wifi);
        settingsPill = pill(new IconDrawable(IconDrawable.GEAR), "Settings", null, this::openPanel);
        row.addView(settingsPill);
        return row;
    }

    private LinearLayout pill(IconDrawable icon, String text, String unused, Runnable action) {
        LinearLayout p = new LinearLayout(this);
        p.setGravity(Gravity.CENTER_VERTICAL);
        p.setFocusable(true);
        p.setClickable(true);
        p.setPadding(dp(16), dp(9), dp(20), dp(9));
        ImageView img = new ImageView(this);
        img.setImageDrawable(icon);
        p.addView(img, new LinearLayout.LayoutParams(dp(20), dp(20)));
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(Color.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
        tl.leftMargin = dp(10);
        p.addView(t, tl);
        p.setBackground(pillBg(false));
        p.setOnFocusChangeListener((v, has) -> {
            v.setBackground(pillBg(has));
            t.setTextColor(has ? 0xFF15151A : Color.WHITE);
            Drawable d = img.getDrawable();
            if (d instanceof IconDrawable) ((IconDrawable) d).setColor(has ? 0xFF15151A : Color.WHITE);
            v.animate().scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f).setDuration(140).start();
            if (has) scroll.smoothScrollTo(0, 0);
        });
        p.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = dp(12);
        p.setLayoutParams(lp);
        return p;
    }

    private GradientDrawable pillBg(boolean focused) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(24));
        g.setColor(focused ? Color.WHITE : 0x40000000);
        g.setStroke(dp(1), focused ? Color.WHITE : 0x59FFFFFF);
        return g;
    }

    // ================================================================== rows

    private int usableWidthPx() {
        return getResources().getDisplayMetrics().widthPixels - dp(128);
    }

    /** Big rounded banner cards in a soft tray; the focused app's name shows above in large type. */
    private View favoritesTray(List<String> pkgs) {
        LinearLayout tray = new LinearLayout(this);
        tray.setClipChildren(true);   // cards past the 4th scroll inside the tray, not out of it
        tray.setClipToPadding(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(26));
        bg.setColor(0x2E000000);
        bg.setStroke(dp(1), 0x26FFFFFF);
        tray.setBackground(bg);
        tray.setPadding(dp(12), dp(4), dp(12), dp(4));

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipChildren(false);
        hs.setClipToPadding(false);
        LinearLayout row = new LinearLayout(this);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        row.setPadding(dp(4), dp(8), dp(14), dp(8)); // room for the focused card to grow
        int w = (usableWidthPx() - dp(24) - dp(20) * 6) / 6;
        int h = w * 9 / 16;
        for (String p : pkgs) {
            App a = apps.get(p);
            View card = card(a, w, h, true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
            lp.setMargins(dp(10), 0, dp(10), 0);
            row.addView(card, lp);
            favTiles.put(p, card);
            if (firstFocus == null) {
                firstFocus = card;
                card.setFocusedByDefault(true);
                titleView.setText(a.label);
            }
        }
        lockRowEnds(row);
        // Up from any favourite goes to the pills; Down from a pill comes back to the first favourite.
        if (homePill != null) {
            if (homePill.getId() == View.NO_ID) homePill.setId(View.generateViewId());
            for (int i = 0; i < row.getChildCount(); i++) row.getChildAt(i).setNextFocusUpId(homePill.getId());
            View first = row.getChildCount() > 0 ? row.getChildAt(0) : null;
            if (first != null) {
                if (first.getId() == View.NO_ID) first.setId(View.generateViewId());
                ViewGroup pillRow = (ViewGroup) homePill.getParent();
                for (int i = 0; i < pillRow.getChildCount(); i++) pillRow.getChildAt(i).setNextFocusDownId(first.getId());
            }
        }
        hs.addView(row);
        tray.addView(hs);
        return tray;
    }

    private View smallRow(List<String> pkgs) {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipChildren(false);
        hs.setClipToPadding(false);
        LinearLayout row = new LinearLayout(this);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        row.setPadding(0, dp(6), 0, dp(6));
        for (String p : pkgs) row.addView(smallTile(apps.get(p)));
        lockRowEnds(row);
        hs.addView(row);
        return hs;
    }

    private View smallTile(App a) {
        int w = (usableWidthPx() - dp(20) * GRID_COLUMNS) / GRID_COLUMNS;
        int h = w * 9 / 16;
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setClipToPadding(false);
        View card = card(a, w, h, false);
        col.addView(card, new LinearLayout.LayoutParams(w, h));
        TextView label = new TextView(this);
        label.setText(a.label);
        label.setTextColor(Color.WHITE);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER_HORIZONTAL);
        label.setShadowLayer(dp(4), 0, dp(1), 0x99000000);
        label.setPadding(0, dp(8), 0, 0);
        label.setVisibility(View.INVISIBLE);
        col.addView(label, new LinearLayout.LayoutParams(w, -2));
        card.setTag(label);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(dp(10), dp(4), dp(10), dp(4));
        col.setLayoutParams(lp);
        return col;
    }

    /**
     * Frosted-glass card: white logo on a frosted tile; when selected it lights up in the
     * app's real colours, lifts, and glows in the app's own colour.
     */
    private View card(App a, int w, int h, boolean favorite) {
        FrameLayout card = new FrameLayout(this);
        card.setFocusable(true);
        card.setClickable(true);
        card.setClipToOutline(true);
        int glowColor = appColor(a);

        GradientDrawable frost = new GradientDrawable();
        frost.setCornerRadius(dp(14));
        frost.setColor(0x2EFFFFFF);
        frost.setStroke(dp(1), 0x4DFFFFFF);
        GradientDrawable solid = new GradientDrawable();
        solid.setCornerRadius(dp(14));
        solid.setColor(0xFF2A2A33);
        card.setBackground(frost);

        // Real colours (shown when selected)
        ImageView color = new ImageView(this);
        if (a.banner != null) {
            color.setImageDrawable(a.banner);
            color.setScaleType(ImageView.ScaleType.CENTER_CROP);
            card.addView(color, new FrameLayout.LayoutParams(-1, -1));
        } else {
            color.setImageDrawable(a.icon);
            color.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int s = Math.min(w, h) / 2;
            card.addView(color, new FrameLayout.LayoutParams(s, s, Gravity.CENTER));
        }
        color.setAlpha(0f);

        // White logo (shown when not selected)
        ImageView mono = new ImageView(this);
        mono.setImageDrawable(monoLogo(a));
        mono.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int pad = Math.min(w, h) / 6;
        mono.setPadding(pad, pad, pad, pad);
        card.addView(mono, new FrameLayout.LayoutParams(-1, -1));

        boolean moving = favorite && a.pkg.equals(movePkg);
        GradientDrawable ring = new GradientDrawable();
        ring.setCornerRadius(dp(14));
        ring.setColor(0);
        ring.setStroke(dp(3), moving ? 0xFFFFD54F : Color.WHITE);
        card.setOutlineSpotShadowColor(glowColor);
        card.setOutlineAmbientShadowColor(glowColor);

        card.setOnFocusChangeListener((v, has) -> {
            v.setBackground(has ? solid : frost);
            v.setForeground(has || moving ? ring : null);
            v.setElevation(has ? dp(28) : 0);
            color.animate().alpha(has ? 1f : 0f).setDuration(160).start();
            mono.animate().alpha(has ? 0f : 0.92f).setDuration(160).start();
            v.animate().scaleX(has ? 1.1f : 1f).scaleY(has ? 1.1f : 1f)
                    .setInterpolator(new DecelerateInterpolator()).setDuration(200).start();
            Object tag = v.getTag();
            if (tag instanceof TextView) ((TextView) tag).setVisibility(has ? View.VISIBLE : View.INVISIBLE);
            if (has) {
                if (favorite) {
                    titleView.setText(moving ? "◀  Move " + a.label + "  ▶" : a.label);
                    scroll.smoothScrollTo(0, 0);
                } else {
                    glideTo(v);
                }
            }
        });
        mono.setAlpha(0.92f);
        if (moving) card.setForeground(ring);
        card.setOnClickListener(v -> launch(a.pkg));
        card.setOnLongClickListener(v -> { appMenu(a); return true; });
        return card;
    }

    private final Map<String, Drawable> monoCache = new HashMap<>();
    private final Map<String, Integer> colorCache = new HashMap<>();

    private Bitmap snapshot(App a) {
        Drawable src = a.banner != null ? a.banner : a.icon;
        int bw = a.banner != null ? 320 : 144, bh = a.banner != null ? 180 : 144;
        Bitmap bm = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        src.setBounds(0, 0, bw, bh);
        src.draw(new android.graphics.Canvas(bm));
        return bm;
    }

    /** The banner turned into a white logo on transparent: its edge colour is treated as background. */
    private Drawable monoLogo(App a) {
        Drawable cached = monoCache.get(a.pkg);
        if (cached != null) return cached;
        Bitmap bm = snapshot(a);
        int bw = bm.getWidth(), bh = bm.getHeight();
        int[] px = new int[bw * bh];
        bm.getPixels(px, 0, bw, 0, 0, bw, bh);
        long rs = 0, gs = 0, bs = 0;
        int n = 0;
        for (int x = 0; x < bw; x++) for (int y : new int[]{0, 1, bh - 2, bh - 1}) {
            int c = px[y * bw + x]; rs += (c >> 16) & 255; gs += (c >> 8) & 255; bs += c & 255; n++;
        }
        for (int y = 0; y < bh; y++) for (int x : new int[]{0, 1, bw - 2, bw - 1}) {
            int c = px[y * bw + x]; rs += (c >> 16) & 255; gs += (c >> 8) & 255; bs += c & 255; n++;
        }
        int br = (int) (rs / n), bg = (int) (gs / n), bb = (int) (bs / n);
        int[] out = new int[px.length];
        int covered = 0;
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            int d = Math.abs(((c >> 16) & 255) - br) + Math.abs(((c >> 8) & 255) - bg) + Math.abs((c & 255) - bb);
            int alpha = Math.max(0, Math.min(255, (d - 60) * 3)) * (c >>> 24) / 255;
            if (alpha > 128) covered++;
            out[i] = (alpha << 24) | 0xFFFFFF;
        }
        if (covered > px.length * 0.4) {
            // Gradient or photo background: keep only the brightest parts (the logo) instead.
            for (int i = 0; i < px.length; i++) {
                int c = px[i];
                int lum = (((c >> 16) & 255) * 3 + ((c >> 8) & 255) * 6 + (c & 255)) / 10;
                int sat = Math.max(Math.max((c >> 16) & 255, (c >> 8) & 255), c & 255)
                        - Math.min(Math.min((c >> 16) & 255, (c >> 8) & 255), c & 255);
                int alpha = sat > 70 ? 0 : Math.max(0, Math.min(255, (lum - 190) * 5)) * (c >>> 24) / 255;
                out[i] = (alpha << 24) | 0xFFFFFF;
            }
        }
        px = out;
        bm.setPixels(px, 0, bw, 0, 0, bw, bh);
        Drawable logo = new android.graphics.drawable.BitmapDrawable(getResources(), bm);
        monoCache.put(a.pkg, logo);
        return logo;
    }

    /** The app's signature colour: the most common vivid colour in its banner (white if none). */
    private int appColor(App a) {
        Integer cached = colorCache.get(a.pkg);
        if (cached != null) return cached;
        Bitmap bm = snapshot(a);
        int[] buckets = new int[36];
        long[][] sum = new long[36][3];
        float[] hsv = new float[3];
        for (int y = 0; y < bm.getHeight(); y += 3) {
            for (int x = 0; x < bm.getWidth(); x += 3) {
                int c = bm.getPixel(x, y);
                if ((c >>> 24) < 200) continue;
                Color.colorToHSV(c, hsv);
                if (hsv[1] < 0.45f || hsv[2] < 0.35f) continue;
                int b = Math.min(35, (int) (hsv[0] / 10));
                buckets[b]++;
                sum[b][0] += (c >> 16) & 255; sum[b][1] += (c >> 8) & 255; sum[b][2] += c & 255;
            }
        }
        int best = -1;
        for (int i = 0; i < 36; i++) if (best < 0 || buckets[i] > buckets[best]) best = i;
        int out = Color.WHITE;
        if (best >= 0 && buckets[best] > 20) {
            int k = buckets[best];
            out = Color.rgb((int) (sum[best][0] / k), (int) (sum[best][1] / k), (int) (sum[best][2] / k));
        }
        colorCache.put(a.pkg, out);
        return out;
    }

    private TextView sectionTitle(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(0xE6FFFFFF);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setShadowLayer(dp(4), 0, dp(1), 0x80000000);
        t.setPadding(dp(10), dp(36), 0, dp(10));
        return t;
    }

    /** Scroll so the focused row sits comfortably near the top, Google TV style. */
    private void glideTo(View v) {
        Rect r = new Rect();
        v.getDrawingRect(r);
        try {
            content.offsetDescendantRectToMyCoords(v, r);
        } catch (IllegalArgumentException e) {
            return;
        }
        scroll.smoothScrollTo(0, Math.max(0, r.top - dp(110)));
    }

    private void lockRowEnds(ViewGroup row) {
        int n = row.getChildCount();
        if (n == 0) return;
        View first = focusableIn(row.getChildAt(0)), last = focusableIn(row.getChildAt(n - 1));
        first.setId(View.generateViewId());
        if (last.getId() == View.NO_ID) last.setId(View.generateViewId());
        first.setNextFocusLeftId(first.getId());
        last.setNextFocusRightId(last.getId());
    }

    private void lockEdges(GridLayout g, int cols) {
        int n = g.getChildCount();
        for (int i = 0; i < n; i++) {
            View c = focusableIn(g.getChildAt(i));
            if (c.getId() == View.NO_ID) c.setId(View.generateViewId());
            if (i % cols == 0) c.setNextFocusLeftId(c.getId());
            if (i % cols == cols - 1 || i == n - 1) c.setNextFocusRightId(c.getId());
        }
    }

    private static View focusableIn(View v) {
        if (v.isFocusable()) return v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View f = focusableIn(g.getChildAt(i));
                if (f.isFocusable()) return f;
            }
        }
        return v;
    }

    // ================================================================== keys

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) navSound(k);
        if (k == KeyEvent.KEYCODE_MENU) {
            if (e.getAction() == KeyEvent.ACTION_UP) { if (panel == null) openPanel(); else closePanel(); }
            return true;
        }
        if (movePkg != null) return moveKey(e);
        return super.dispatchKeyEvent(e);
    }

    /** Move mode: Left/Right reorders the favourite, OK or Back finishes. */
    private boolean moveKey(KeyEvent e) {
        int k = e.getKeyCode();
        if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_BACK) {
            if (e.getAction() == KeyEvent.ACTION_UP) {
                String p = movePkg;
                movePkg = null;
                rebuild();
                View t = favTiles.get(p);
                if (t != null) t.post(t::requestFocus);
            }
            return true;
        }
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
        int d = k == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : k == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : 0;
        if (d == 0) return true;
        List<String> f = store.favorites();
        int i = f.indexOf(movePkg), j = i + d;
        if (i < 0 || j < 0 || j >= f.size()) return true;
        Collections.swap(f, i, j);
        store.setFavorites(f);
        rebuild();
        View t = favTiles.get(movePkg);
        if (t != null) t.post(t::requestFocus);
        return true;
    }

    private void navSound(int key) {
        if (!store.sounds()) return;
        int fx;
        switch (key) {
            case KeyEvent.KEYCODE_DPAD_UP: fx = AudioManager.FX_FOCUS_NAVIGATION_UP; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: fx = AudioManager.FX_FOCUS_NAVIGATION_DOWN; break;
            case KeyEvent.KEYCODE_DPAD_LEFT: fx = AudioManager.FX_FOCUS_NAVIGATION_LEFT; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: fx = AudioManager.FX_FOCUS_NAVIGATION_RIGHT; break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER: fx = AudioManager.FX_KEY_CLICK; break;
            default: return;
        }
        try { getSystemService(AudioManager.class).playSoundEffect(fx, 0.4f); } catch (Exception ignored) { }
    }

    // ================================================================== apps

    private void loadApps() {
        apps.clear();
        List<ResolveInfo> all = new ArrayList<>();
        all.addAll(pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0));
        all.addAll(pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0));
        for (ResolveInfo ri : all) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || apps.containsKey(pkg)) continue;
            App a = new App();
            a.pkg = pkg;
            String custom = store.label(pkg);
            a.label = custom != null ? custom : String.valueOf(ri.loadLabel(pm));
            try {
                a.banner = ri.activityInfo.loadBanner(pm);
                if (a.banner == null) a.banner = ri.activityInfo.applicationInfo.loadBanner(pm);
            } catch (Exception ignored) { }
            a.icon = ri.loadIcon(pm);
            apps.put(pkg, a);
        }
    }

    private List<String> visible(List<String> pkgs) {
        List<String> out = new ArrayList<>();
        for (String p : pkgs) if (apps.containsKey(p) && !out.contains(p)) out.add(p);
        return out;
    }

    private void initDefaultFavorites() {
        if (store.favoritesInitialized()) return;
        List<String> favs = new ArrayList<>();
        for (String p : DEFAULT_FAVORITES) if (apps.containsKey(p)) favs.add(p);
        store.setFavorites(favs);
        store.markFavoritesInitialized();
    }

    private void launch(String pkg) {
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i == null) { toast("Can't open this app"); return; }
        store.pushRecent(pkg);
        safeStart(i);
    }

    private void appMenu(App a) {
        List<String> favs = store.favorites();
        boolean fav = favs.contains(a.pkg);
        List<String> opts = new ArrayList<>();
        opts.add(fav ? "Remove from favourites" : "Add to favourites");
        if (fav) opts.add("Move");
        opts.add("Rename");
        opts.add("Hide");
        opts.add("App info");
        new AlertDialog.Builder(this).setTitle(a.label)
                .setItems(opts.toArray(new String[0]), (d, w) -> {
                    List<String> f = store.favorites();
                    switch (opts.get(w)) {
                        case "Add to favourites": f.add(a.pkg); store.setFavorites(f); break;
                        case "Remove from favourites": f.remove(a.pkg); store.setFavorites(f); break;
                        case "Move":
                            movePkg = a.pkg;
                            toast("Left / Right to move, OK when done");
                            break;
                        case "Rename":
                            textDialog("Rename " + a.label, a.label, "Leave empty to reset",
                                    s -> { store.setLabel(a.pkg, s); rebuild(); });
                            return;
                        case "Hide":
                            List<String> h = store.hidden(); h.add(a.pkg); store.setHidden(h);
                            f.remove(a.pkg); store.setFavorites(f);
                            toast(a.label + " hidden. Settings → Hidden apps brings it back.");
                            break;
                        case "App info":
                            safeStart(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.pkg)));
                            return;
                    }
                    String keep = a.pkg;
                    rebuild();
                    View t = favTiles.get(keep);
                    if (t != null) t.post(t::requestFocus);
                }).show();
    }

    // ================================================================== inputs & network

    private List<TvInputInfo> hdmiInputs() {
        List<TvInputInfo> out = new ArrayList<>();
        try {
            TvInputManager tim = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
            for (TvInputInfo info : tim.getTvInputList()) {
                if (info.isPassthroughInput() && info.getParentId() == null) out.add(info);
            }
        } catch (Exception ignored) { }
        return out;
    }

    private String inputLabel(TvInputInfo info) {
        String custom = store.inputName(info.getId());
        if (custom != null) return custom;
        CharSequence l = info.loadLabel(this);
        return l != null ? l.toString() : "Input";
    }

    private void inputsDialog() {
        List<TvInputInfo> inputs = hdmiInputs();
        if (inputs.isEmpty()) { launch("com.tcl.tv"); return; }
        String[] names = new String[inputs.size()];
        for (int i = 0; i < names.length; i++) names[i] = inputLabel(inputs.get(i));
        new AlertDialog.Builder(this).setTitle("Inputs")
                .setItems(names, (d, w) -> {
                    Intent i = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(inputs.get(w).getId()));
                    try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception e) { launch("com.tcl.tv"); }
                })
                .setNeutralButton("Rename", (d, w) -> renameInputs())
                .show();
    }

    private void renameInputs() {
        List<TvInputInfo> inputs = hdmiInputs();
        String[] names = new String[inputs.size()];
        for (int i = 0; i < names.length; i++) names[i] = inputLabel(inputs.get(i));
        new AlertDialog.Builder(this).setTitle("Rename which input?")
                .setItems(names, (d, w) -> {
                    TvInputInfo info = inputs.get(w);
                    textDialog("Name for " + info.loadLabel(this), inputLabel(info), "e.g. PS5, Cable box",
                            s -> store.setInputName(info.getId(), s));
                }).show();
    }

    private void networkDialog() {
        StringBuilder sb = new StringBuilder();
        sb.append("Connection: ").append(networkSummary()).append('\n');
        try {
            android.net.wifi.WifiInfo info = getApplicationContext()
                    .getSystemService(android.net.wifi.WifiManager.class).getConnectionInfo();
            if (info != null && info.getRssi() > -127) {
                sb.append("Signal: ").append(Math.max(0, Math.min(100, 2 * (info.getRssi() + 100)))).append("%\n");
                sb.append("Link speed: ").append(info.getLinkSpeed()).append(" Mbps\n");
                int f = info.getFrequency();
                if (f > 0) sb.append("Band: ").append(f >= 4900 ? "5 GHz" : "2.4 GHz").append('\n');
            }
        } catch (Exception ignored) { }
        if (dataToday != null) sb.append("Used: ").append(dataToday).append('\n');
        new AlertDialog.Builder(this).setTitle("Network").setMessage(sb.toString().trim())
                .setPositiveButton("Wi-Fi settings", (d, w) -> safeStart(new Intent(Settings.ACTION_WIFI_SETTINGS)))
                .setNegativeButton("Close", null).show();
    }

    // ================================================================== settings panel

    private static final class Item {
        final String label; final Runnable action; final boolean header;
        Item(String label, Runnable action) { this.label = label; this.action = action; this.header = action == null; }
    }

    private void openPanel() {
        closePanel();
        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(0x99000000);
        overlay.setClickable(true);
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        GradientDrawable bg = new GradientDrawable();
        float r = dp(28);
        bg.setCornerRadii(new float[]{r, r, 0, 0, 0, 0, r, r});
        bg.setColor(0xF2201A22);
        sv.setBackground(bg);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(18), dp(28), dp(18), dp(28));
        sv.addView(list);

        TextView title = new TextView(this);
        title.setText("Settings");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        title.setPadding(dp(14), 0, 0, dp(6));
        list.addView(title);

        boolean wOn = store.weatherOn();
        Item[] items = {
                new Item("Weather & info", null),
                new Item("Weather: " + (wOn ? "On" : "Off"), () -> {
                    if (!wOn && store.weatherCity().isEmpty()) { askCity(); return; }
                    store.setWeatherOn(!wOn); store.setWeather(0, 0, 0); reopen();
                }),
                new Item("City: " + (store.weatherCity().isEmpty() ? "not set" : store.weatherCity()), this::askCity),
                new Item("Units: " + (store.weatherFahrenheit() ? "°F" : "°C"), () -> {
                    store.setWeatherFahrenheit(!store.weatherFahrenheit()); store.setWeather(0, 0, 0); reopen();
                }),
                new Item("Room name: " + store.roomName(), () -> textDialog("Room name", store.roomName(),
                        "e.g. Living room", s -> { store.setRoomName(s.trim().isEmpty() ? "Living room" : s.trim()); reopen(); })),
                new Item("Clock: " + new String[]{"12-hour", "24-hour", "12-hour, no AM/PM"}[Math.max(0, Math.min(2, store.timeFormat()))],
                        () -> { store.setTimeFormat((store.timeFormat() + 1) % 3); reopen(); }),
                new Item("Data used today: " + (store.showDataUsage() ? "Shown" : "Hidden"),
                        () -> { store.setShowDataUsage(!store.showDataUsage()); reopen(); }),
                new Item("Home screen", null),
                new Item("Hidden apps", this::hiddenDialog),
                new Item("Rename inputs", this::renameInputs),
                new Item("Navigation sounds: " + (store.sounds() ? "On" : "Off"),
                        () -> { store.setSounds(!store.sounds()); reopen(); }),
                new Item("TV", null),
                new Item(sleepLabel(), this::sleepDialog),
                new Item("Screensaver", () -> {
                    Intent i = new Intent(Settings.ACTION_DREAM_SETTINGS);
                    safeStart(pm.resolveActivity(i, 0) != null ? i : new Intent(Settings.ACTION_DISPLAY_SETTINGS));
                }),
                new Item("TV settings", () -> safeStart(new Intent(Settings.ACTION_SETTINGS))),
                new Item("Home button setup", this::homeSetupDialog),
                new Item("Open Google TV home", this::openGoogleHome),
                new Item("About Lumen", () -> new AlertDialog.Builder(this).setTitle("Lumen 2.0")
                        .setMessage("A clean home screen for Google TV.\nDeveloped by Ponkoj Shill.\n\n"
                                + "Long-press OK on an app to add it to favourites, move, rename or hide it.\n"
                                + "Internet is used only for the optional weather.")
                        .setPositiveButton("OK", null).show()),
        };
        View first = null;
        for (Item it : items) {
            if (it.header) {
                TextView h = new TextView(this);
                h.setText(it.label.toUpperCase(Locale.ROOT));
                h.setTextColor(0x80FFFFFF);
                h.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                h.setLetterSpacing(0.12f);
                h.setPadding(dp(14), dp(18), 0, dp(6));
                list.addView(h);
                continue;
            }
            TextView row = new TextView(this);
            row.setText(it.label);
            row.setTextColor(0xE6FFFFFF);
            row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            row.setSingleLine(true);
            row.setEllipsize(TextUtils.TruncateAt.END);
            row.setFocusable(true);
            row.setClickable(true);
            row.setPadding(dp(14), dp(12), dp(14), dp(12));
            row.setOnFocusChangeListener((v, has) -> {
                GradientDrawable g = new GradientDrawable();
                g.setCornerRadius(dp(14));
                g.setColor(has ? 0x1FFFFFFF : 0);
                if (has) g.setStroke(dp(2), Color.WHITE);
                v.setBackground(g);
            });
            row.setOnClickListener(v -> it.action.run());
            if (it.label.equals(lastPanelItem)) first = row;
            if (first == null && lastPanelItem == null) first = row;
            list.addView(row);
        }
        overlay.addView(sv, new FrameLayout.LayoutParams(dp(400), -1, Gravity.END));
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        panel = overlay;
        scroll.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        final View f = first;
        if (f != null) sv.post(() -> {
            f.requestFocus();
            sv.scrollTo(0, Math.max(0, f.getTop() - dp(200)));
        });
        if (lastPanelItem == null) {
            sv.setTranslationX(dp(400));
            sv.animate().translationX(0).setDuration(220).start();
        }
        lastPanelItem = null;
    }

    private String lastPanelItem;

    /** Re-draw the panel after a change, keeping the selected row. */
    private void reopen() {
        View f = getCurrentFocus();
        lastPanelItem = f instanceof TextView ? ((TextView) f).getText().toString() : null;
        rebuild();
        // the label may have changed: match by its first word(s) before the colon
        if (lastPanelItem != null && lastPanelItem.contains(":")) {
            String key = lastPanelItem.substring(0, lastPanelItem.indexOf(':'));
            lastPanelItem = matchPrefix(key);
        }
        openPanel();
    }

    private String matchPrefix(String key) {
        if (key.startsWith("Weather")) return "Weather: " + (store.weatherOn() ? "On" : "Off");
        if (key.startsWith("City")) return "City: " + (store.weatherCity().isEmpty() ? "not set" : store.weatherCity());
        if (key.startsWith("Units")) return "Units: " + (store.weatherFahrenheit() ? "°F" : "°C");
        if (key.startsWith("Room")) return "Room name: " + store.roomName();
        if (key.startsWith("Clock"))
            return "Clock: " + new String[]{"12-hour", "24-hour", "12-hour, no AM/PM"}[Math.max(0, Math.min(2, store.timeFormat()))];
        if (key.startsWith("Data")) return "Data used today: " + (store.showDataUsage() ? "Shown" : "Hidden");
        if (key.startsWith("Navigation")) return "Navigation sounds: " + (store.sounds() ? "On" : "Off");
        return null;
    }

    private void closePanel() {
        if (panel == null) return;
        root.removeView(panel);
        panel = null;
        if (scroll != null) scroll.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
    }

    private void askCity() {
        textDialog("Your city", store.weatherCity(), "e.g. Reno, London, Dhaka", s -> {
            String c = s.trim();
            store.setWeatherCity(c);
            store.setWeatherLocation("", "", "");
            store.setWeather(0, 0, 0);
            store.setWeatherOn(!c.isEmpty());
            lastPanelItem = "City: " + (c.isEmpty() ? "not set" : c);
            rebuild();
            openPanel();
        });
    }

    private void hiddenDialog() {
        List<String> hidden = store.hidden();
        if (hidden.isEmpty()) { toast("No hidden apps"); return; }
        String[] names = new String[hidden.size()];
        for (int i = 0; i < names.length; i++) {
            String l = store.label(hidden.get(i));
            try { if (l == null) l = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(hidden.get(i), 0))); }
            catch (Exception e) { l = hidden.get(i); }
            names[i] = l;
        }
        new AlertDialog.Builder(this).setTitle("Select an app to show it again")
                .setItems(names, (d, w) -> {
                    List<String> h = store.hidden(); h.remove(hidden.get(w)); store.setHidden(h); rebuild();
                }).show();
    }

    private String sleepLabel() {
        long left = store.sleepAt() - System.currentTimeMillis();
        return store.sleepAt() == 0 || left <= 0 ? "Sleep timer" : "Sleep timer · " + Math.max(1, (left + 59_999) / 60_000) + " min left";
    }

    private void sleepDialog() {
        String[] opts = {"Off", "15 minutes", "30 minutes", "60 minutes", "90 minutes", "120 minutes"};
        int[] mins = {0, 15, 30, 60, 90, 120};
        new AlertDialog.Builder(this).setTitle("Sleep timer").setItems(opts, (d, w) -> {
            AlarmManager am = getSystemService(AlarmManager.class);
            PendingIntent pi = PendingIntent.getBroadcast(this, 0, new Intent(this, SleepReceiver.class),
                    PendingIntent.FLAG_IMMUTABLE);
            am.cancel(pi);
            if (mins[w] == 0) {
                store.setSleepAt(0);
            } else {
                long at = System.currentTimeMillis() + mins[w] * 60_000L;
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
                store.setSleepAt(at);
                toast("TV will sleep in " + mins[w] + " minutes");
            }
            closePanel();
            updateStatusLine();
        }).show();
    }

    private void homeSetupDialog() {
        boolean on = HomeButtonService.instance != null;
        new AlertDialog.Builder(this).setTitle("Home button")
                .setMessage(on ? "The remote's Home button opens Lumen."
                        : "To make the remote's Home button open Lumen, turn on \"Lumen - Home button\" in Accessibility settings.")
                .setPositiveButton("Accessibility settings", (d, w) -> safeStart(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
                .setNegativeButton("Close", null).show();
    }

    /** Google Home panel (lights and other smart devices), part of Google TV's dreamx app. */
    private void openGoogleHomePanel() {
        try {
            startActivity(new Intent("com.google.android.libraries.tv.smarthome.intent.action.OPEN_SMART_HOME")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            try {
                startActivity(new Intent("android.app.action.OPEN_NOTIFICATION_HANDLER_PANEL")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception e2) {
                toast("Google Home panel isn't available on this TV");
            }
        }
    }

    private void openGoogleHome() {
        store.setGoogleHomeBypassUntil(System.currentTimeMillis() + 5 * 60_000L);
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .setPackage("com.google.android.apps.tv.launcherx");
        if (pm.resolveActivity(i, 0) != null) safeStart(i); else toast("Google TV home not found");
    }

    // ================================================================== wallpaper

    /** Misty forest photo (CC0, Jay Mantri via Pexels), graded dark and mauve for snowfall. */
    private Bitmap wallpaper() {
        if (wallpaper != null) return wallpaper;
        Bitmap src = BitmapFactory.decodeResource(getResources(), R.drawable.forest);
        Bitmap out = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0.15f);
        cm.postConcat(new ColorMatrix(new float[]{
                0.64f, 0, 0, 0, 8,
                0, 0.62f, 0, 0, 6,
                0, 0, 0.68f, 0, 16,
                0, 0, 0, 1, 0}));
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        paint.setColorFilter(new ColorMatrixColorFilter(cm));
        android.graphics.Canvas c = new android.graphics.Canvas(out);
        c.drawBitmap(src, 0, 0, paint);
        Paint shade = new Paint();
        shade.setShader(new LinearGradient(0, out.getHeight() * 0.55f, 0, out.getHeight(),
                0x00000000, 0x8C000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, out.getWidth(), out.getHeight(), shade);
        src.recycle();
        wallpaper = out;
        return out;
    }

    // ================================================================== helpers

    private interface TextResult { void onText(String s); }

    private void textDialog(String title, String current, String hint, TextResult r) {
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setText(current);
        e.setHint(hint);
        e.setSelectAllOnFocus(true);
        e.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        FrameLayout box = new FrameLayout(this);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        box.addView(e);
        AlertDialog dlg = new AlertDialog.Builder(this).setTitle(title).setView(box)
                .setPositiveButton("Save", (d, w) -> r.onText(e.getText().toString()))
                .setNegativeButton("Cancel", null).show();
        e.setOnEditorActionListener((v, action, ev) -> {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            return true;
        });
    }

    private void safeStart(Intent i) {
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            toast("Couldn't open that");
        }
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
