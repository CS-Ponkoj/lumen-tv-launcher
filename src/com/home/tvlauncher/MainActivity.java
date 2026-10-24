package com.home.tvlauncher;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
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
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
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

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {
    private static final String[] ACCENT_NAMES = {"Cyan", "Purple", "Green", "Gold", "Pink", "White"};
    private static final int[] ACCENT_COLORS = {0xFF80DEFF, 0xFFB388FF, 0xFF69F0AE, 0xFFFFD54F, 0xFFFF80AB, 0xFFFFFFFF};
    private static final String[] STYLE_NAMES = {"Modern (banners)", "Premium (large banners)", "Capsule (round icons)"};
    private static final int ACCENT = 0xFF42A5F5;

    private static final String[] DEFAULT_FAVORITES = {
            "com.netflix.ninja", "com.google.android.youtube.tv", "com.disney.disneyplus",
            "com.amazon.amazonvideo.livingroom", "com.wbd.stream", "com.hulu.livingroomplus"};

    private static final String[] WALLPAPER_NAMES = {
            "Midnight", "Deep blue", "Charcoal", "Forest", "Plum", "Black",
            "My picture (wallpaper.jpg)"};
    private static final int[][] WALLPAPER_COLORS = {
            {0xFF0F2027, 0xFF203A43, 0xFF2C5364},
            {0xFF0B1D3A, 0xFF12305E, 0xFF1B3B6F},
            {0xFF141414, 0xFF1F1F1F, 0xFF2B2B2B},
            {0xFF0B2B1F, 0xFF143D2D, 0xFF1D4D3A},
            {0xFF1A0B2E, 0xFF2B1055, 0xFF3D1E6D},
            {0xFF000000, 0xFF000000, 0xFF000000}};

    // Soft colour glows per wallpaper (top-left, right, bottom).
    private static final int[][] WALLPAPER_GLOWS = {
            {0x8800B4D8, 0x667B2FF7, 0x5500F5A0},
            {0x882979FF, 0x6600E5FF, 0x55651FFF},
            {0x55FFFFFF, 0x44A0A0A0, 0x33FFFFFF},
            {0x7700E676, 0x6600BFA5, 0x55C6FF00},
            {0x88E040FB, 0x66FF4081, 0x557C4DFF},
            {0x442979FF, 0x337C4DFF, 0x2200E5FF}};

    private Store store;
    private PackageManager pm;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private FrameLayout root;
    private LinearLayout content;
    private TextView wifiView;
    private TextView sleepButton;
    private EditText searchBox;
    private GridLayout allGrid;
    private TextView allTitle;

    private final Map<String, App> apps = new LinkedHashMap<>();
    private ConnectivityManager.NetworkCallback netCallback;

    private static final class App {
        String pkg, label;
        Drawable banner, icon;
    }

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { rebuild(); }
    };

    private final Runnable minuteTick = new Runnable() {
        @Override public void run() {
            updateSleepLabel();
            handler.postDelayed(this, 30_000);
        }
    };

    // ------------------------------------------------------------------ lifecycle

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

        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        netCallback = new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(Network n) { runOnUiThread(MainActivity.this::updateWifi); }
            @Override public void onLost(Network n) { runOnUiThread(MainActivity.this::updateWifi); }
            @Override public void onCapabilitiesChanged(Network n, NetworkCapabilities c) {
                runOnUiThread(MainActivity.this::updateWifi);
            }
        };
        try { cm.registerDefaultNetworkCallback(netCallback); } catch (Exception ignored) { }

        if (checkSelfPermission(EPG_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{EPG_PERMISSION}, 1);
        }
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
        try { getSystemService(ConnectivityManager.class).unregisterNetworkCallback(netCallback); }
        catch (Exception ignored) { }
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home pressed while already here: jump back to the top.
        closeSearch();
        View first = firstFocusTarget;
        if (first != null) first.requestFocus();
    }

    @Override
    public void onBackPressed() {
        // A home screen has nowhere to go back to. Back only closes search.
        if (searchBox != null && searchBox.getVisibility() == View.VISIBLE) closeSearch();
    }

    // ------------------------------------------------------------------ building the screen

    private View firstFocusTarget;

    private void rebuild() {
        loadApps();
        initDefaultFavorites();
        root.removeAllViews();
        root.setBackground(wallpaperDrawable());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setClipChildren(false);
        content.setPadding(dp(40), dp(24), dp(40), dp(24));
        scroll.addView(content);
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        firstFocusTarget = null;
        content.addView(buildTopBar());

        searchBox = new EditText(this);
        searchBox.setHint("Type to search apps…");
        searchBox.setSingleLine(true);
        searchBox.setTextColor(Color.WHITE);
        searchBox.setHintTextColor(0x99FFFFFF);
        searchBox.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        searchBox.setBackground(glassPill(false));
        searchBox.setPadding(dp(16), dp(10), dp(16), dp(10));
        searchBox.setVisibility(View.GONE);
        searchBox.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(Editable e) { fillAllApps(e.toString()); }
        });
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.topMargin = dp(16);
        content.addView(searchBox, slp);

        // Favorites
        List<String> favs = new ArrayList<>();
        for (String p : store.favorites()) if (apps.containsKey(p)) favs.add(p);
        if (!favs.isEmpty()) {
            content.addView(sectionTitle("★  Favorites"));
            content.addView(tileRow(favs, true));
        }

        // Continue watching (system "Watch Next" list, read locally)
        if (store.continueWatching()) {
            List<WatchItem> watch = loadWatchNext();
            if (!watch.isEmpty()) {
                content.addView(sectionTitle("Continue watching"));
                HorizontalScrollView hs = new HorizontalScrollView(this);
                hs.setHorizontalScrollBarEnabled(false);
                hs.setClipToPadding(false);
                hs.setClipChildren(false);
                LinearLayout row = new LinearLayout(this);
                row.setClipChildren(false);
                row.setClipToPadding(false);
                row.setPadding(dp(8), dp(16), dp(8), dp(14));
                for (WatchItem w : watch) row.addView(watchTile(w));
                hs.addView(row);
                content.addView(hs);
            }
        }

        // Your own categories
        for (String cat : store.categories()) {
            List<String> pk = new ArrayList<>();
            for (String p : store.categoryApps(cat)) if (apps.containsKey(p)) pk.add(p);
            if (pk.isEmpty()) continue;
            content.addView(sectionTitle(cat));
            content.addView(tileRow(pk, false));
        }

        // Recent
        List<String> rec = new ArrayList<>();
        for (String p : store.recents()) if (apps.containsKey(p)) rec.add(p);
        if (!rec.isEmpty()) {
            content.addView(sectionTitle("Recent"));
            content.addView(tileRow(rec, false));
        }

        // Inputs (HDMI etc.) by name
        List<TvInputInfo> inputs = hdmiInputs();
        if (!inputs.isEmpty()) {
            content.addView(sectionTitle("Inputs"));
            HorizontalScrollView hs = new HorizontalScrollView(this);
            hs.setHorizontalScrollBarEnabled(false);
            hs.setClipToPadding(false);
        hs.setClipChildren(false);
            LinearLayout row = new LinearLayout(this);
        row.setClipChildren(false);
        row.setClipToPadding(false);
            row.setPadding(dp(8), dp(16), dp(8), dp(14));
            for (TvInputInfo info : inputs) {
                TextView chip = button(inputLabel(info));
                chip.setOnClickListener(v -> openInput(info));
                chip.setOnLongClickListener(v -> { renameInput(info); return true; });
                row.addView(chip);
            }
            hs.addView(row);
            content.addView(hs);
        }

        // All apps
        allTitle = sectionTitle("All apps");
        content.addView(allTitle);
        allGrid = new GridLayout(this);
        allGrid.setColumnCount(columns());
        allGrid.setClipChildren(false);
        allGrid.setClipToPadding(false);
        allGrid.setPadding(dp(8), dp(16), dp(8), dp(14));
        content.addView(allGrid);
        fillAllApps("");

        updateWifi();
        updateSleepLabel();
        final View first = firstFocusTarget;
        scroll.post(() -> {
            if (first != null) first.requestFocus();
            scroll.scrollTo(0, 0);
        });
    }

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout clockBox = new LinearLayout(this);
        clockBox.setOrientation(LinearLayout.VERTICAL);
        TextClock time = new TextClock(this);
        time.setFormat12Hour("h:mm a");
        time.setFormat24Hour("HH:mm");
        time.setTextColor(Color.WHITE);
        time.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34);
        time.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        clockBox.addView(time);

        LinearLayout sub = new LinearLayout(this);
        sub.setGravity(Gravity.CENTER_VERTICAL);
        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEE, MMM d");
        date.setFormat24Hour("EEE, MMM d");
        date.setTextColor(0xCCFFFFFF);
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        sub.addView(date);
        wifiView = new TextView(this);
        wifiView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        wifiView.setPadding(dp(16), 0, 0, 0);
        sub.addView(wifiView);
        clockBox.addView(sub);
        bar.addView(clockBox, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView search = button("Search");
        search.setOnClickListener(v -> openSearch());
        bar.addView(search);

        sleepButton = button("Sleep");
        sleepButton.setOnClickListener(v -> sleepDialog());
        bar.addView(sleepButton);

        TextView store_ = button("Play Store");
        store_.setOnClickListener(v -> launch("com.android.vending"));
        bar.addView(store_);

        TextView settings = button("Settings");
        settings.setOnClickListener(v -> safeStart(new Intent(Settings.ACTION_SETTINGS)));
        bar.addView(settings);

        TextView hdmi = button("HDMI");
        hdmi.setOnClickListener(v -> inputsDialog());
        bar.addView(hdmi);

        TextView more = button("⋯");
        more.setOnClickListener(v -> moreMenu());
        bar.addView(more);
        return bar;
    }

    private TextView sectionTitle(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(0xE6FFFFFF);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        t.setLetterSpacing(0.04f);
        t.setShadowLayer(dp(4), 0, dp(1), 0x80000000);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(dp(4), dp(20), 0, dp(2));
        return t;
    }

    private View tileRow(List<String> pkgs, boolean favorites) {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipToPadding(false);
        hs.setClipChildren(false);
        LinearLayout row = new LinearLayout(this);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        row.setPadding(dp(8), dp(16), dp(8), dp(14));
        for (String p : pkgs) {
            View t = tile(apps.get(p));
            if (favorites && firstFocusTarget == null) firstFocusTarget = t;
            row.addView(t);
        }
        hs.addView(row);
        return hs;
    }

    private void fillAllApps(String query) {
        allGrid.removeAllViews();
        String q = query.trim().toLowerCase(Locale.ROOT);
        List<String> hidden = store.hidden();
        List<App> list = new ArrayList<>();
        for (App a : apps.values()) {
            if (hidden.contains(a.pkg)) continue;
            if (!q.isEmpty() && !a.label.toLowerCase(Locale.ROOT).contains(q)) continue;
            list.add(a);
        }
        Collections.sort(list, (x, y) -> x.label.compareToIgnoreCase(y.label));
        for (App a : list) {
            View t = tile(a);
            if (firstFocusTarget == null) firstFocusTarget = t;
            allGrid.addView(t);
        }
        allTitle.setText(q.isEmpty() ? "All apps" : "Results (" + list.size() + ")");
    }

    private View tile(App a) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setFocusable(true);
        t.setClickable(true);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setClipChildren(false);
        t.setClipToPadding(false);

        int style = store.cardStyle();
        boolean capsule = style == Store.STYLE_CAPSULE;
        boolean premium = style == Store.STYLE_PREMIUM;
        int w = capsule ? dp(72) : premium ? dp(144) : dp(112);
        int h = capsule ? dp(72) : premium ? dp(81) : dp(63);
        int radius = capsule ? 50 : 18;
        // The glass frame only appears around the selected tile.
        FrameLayout card = new FrameLayout(this);
        card.setPadding(dp(9), dp(9), dp(9), dp(9));
        FrameLayout art = new FrameLayout(this);
        art.setClipToOutline(true);
        if (capsule) {
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(0x2EFFFFFF);
            circle.setStroke(dp(1), 0x40FFFFFF);
            art.setBackground(circle);
        } else {
            art.setBackground(rounded(0x33FFFFFF, 0, 12));
        }
        ImageView img = new ImageView(this);
        if (capsule) {
            img.setImageDrawable(a.icon);
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            art.addView(img, new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER));
        } else if (a.banner != null) {
            img.setImageDrawable(a.banner);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            art.addView(img, new FrameLayout.LayoutParams(-1, -1));
        } else {
            img.setImageDrawable(a.icon);
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER);
            art.addView(img, ilp);
        }
        card.addView(art, new FrameLayout.LayoutParams(w, h));
        card.setAlpha(0.85f);
        t.addView(card, new LinearLayout.LayoutParams(-2, -2));

        TextView label = new TextView(this);
        label.setText(a.label);
        label.setTextColor(0xB3FFFFFF);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER_HORIZONTAL);
        label.setPadding(0, dp(6), 0, 0);
        label.setShadowLayer(dp(3), 0, dp(1), 0x99000000);
        if (premium) label.setAlpha(0f); // Premium: name only shows on the selected tile
        t.addView(label, new LinearLayout.LayoutParams(Math.max(w + dp(12), dp(96)), -2));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(dp(4), dp(4), dp(4), dp(4));
        t.setLayoutParams(lp);

        t.setOnFocusChangeListener((v, has) -> {
            card.setBackground(has ? glass(true, radius) : null);
            card.setElevation(has ? dp(12) : 0);
            card.animate().scaleX(has ? 1.1f : 1f).scaleY(has ? 1.1f : 1f).alpha(has ? 1f : 0.85f)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(1.4f))
                    .setDuration(220).start();
            label.animate().translationY(has ? dp(4) : 0).alpha(premium && !has ? 0f : 1f)
                    .setDuration(220).start();
            label.setTextColor(has ? Color.WHITE : 0xB3FFFFFF);
            label.setTypeface(has ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        });
        t.setOnClickListener(v -> launch(a.pkg));
        t.setOnLongClickListener(v -> { appMenu(a); return true; });
        return t;
    }

    private TextView button(String text) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        b.setFocusable(true);
        b.setClickable(true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(18), dp(10), dp(18), dp(10));
        b.setBackground(glassPill(false));
        b.setOnFocusChangeListener((v, has) -> {
            v.setBackground(glassPill(has));
            b.setTextColor(has ? 0xFF0D1B2A : Color.WHITE);
            v.setElevation(has ? dp(8) : 0);
            v.animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f).setDuration(160).start();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(dp(6), 0, dp(6), 0);
        b.setLayoutParams(lp);
        return b;
    }

    // ------------------------------------------------------------------ apps

    private void loadApps() {
        apps.clear();
        List<ResolveInfo> all = new ArrayList<>();
        Intent lean = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        all.addAll(pm.queryIntentActivities(lean, 0));
        Intent phone = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        all.addAll(pm.queryIntentActivities(phone, 0));
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
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        store.pushRecent(pkg);
        safeStart(i);
    }

    private void appMenu(App a) {
        List<String> favs = store.favorites();
        boolean fav = favs.contains(a.pkg);
        List<String> opts = new ArrayList<>();
        opts.add(fav ? "Remove from Favorites" : "Add to Favorites");
        if (fav) { opts.add("Move left"); opts.add("Move right"); }
        opts.add("Add to category…");
        for (String c : store.categories())
            if (store.categoryApps(c).contains(a.pkg)) opts.add("Remove from " + c);
        opts.add("Rename");
        opts.add("Hide from All apps");
        opts.add("App info");

        new AlertDialog.Builder(this)
                .setTitle(a.label)
                .setItems(opts.toArray(new String[0]), (d, which) -> {
                    String o = opts.get(which);
                    List<String> f = store.favorites();
                    int idx = f.indexOf(a.pkg);
                    if (o.startsWith("Remove from ") && !o.equals("Remove from Favorites")) {
                        String c = o.substring("Remove from ".length());
                        List<String> ca = store.categoryApps(c); ca.remove(a.pkg); store.setCategoryApps(c, ca);
                        rebuild();
                        return;
                    }
                    switch (o) {
                        case "Add to category…": addToCategoryDialog(a); return;
                        case "Add to Favorites": f.add(a.pkg); store.setFavorites(f); break;
                        case "Remove from Favorites": f.remove(a.pkg); store.setFavorites(f); break;
                        case "Move left":
                            if (idx > 0) { Collections.swap(f, idx, idx - 1); store.setFavorites(f); }
                            break;
                        case "Move right":
                            if (idx >= 0 && idx < f.size() - 1) { Collections.swap(f, idx, idx + 1); store.setFavorites(f); }
                            break;
                        case "Rename":
                            textDialog("Rename " + a.label, a.label, "Leave empty to reset",
                                    s -> { store.setLabel(a.pkg, s); rebuild(); });
                            return;
                        case "Hide from All apps":
                            List<String> h = store.hidden(); h.add(a.pkg); store.setHidden(h);
                            toast(a.label + " hidden. Restore it from ⋯ → Hidden apps.");
                            break;
                        case "App info":
                            safeStart(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:" + a.pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                            return;
                    }
                    rebuild();
                })
                .show();
    }

    // ------------------------------------------------------------------ search

    private void openSearch() {
        searchBox.setVisibility(View.VISIBLE);
        searchBox.requestFocus();
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        handler.postDelayed(() -> imm.showSoftInput(searchBox, InputMethodManager.SHOW_IMPLICIT), 100);
    }

    private void closeSearch() {
        if (searchBox == null || searchBox.getVisibility() != View.VISIBLE) return;
        searchBox.setText("");
        searchBox.setVisibility(View.GONE);
        getSystemService(InputMethodManager.class).hideSoftInputFromWindow(searchBox.getWindowToken(), 0);
        if (firstFocusTarget != null) firstFocusTarget.requestFocus();
    }

    // ------------------------------------------------------------------ inputs

    private List<TvInputInfo> hdmiInputs() {
        List<TvInputInfo> out = new ArrayList<>();
        try {
            TvInputManager tim = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
            if (tim == null) return out;
            for (TvInputInfo info : tim.getTvInputList()) {
                if (info.isPassthroughInput() && info.getType() == TvInputInfo.TYPE_HDMI
                        && info.getParentId() == null) {
                    out.add(info);
                }
            }
            if (out.isEmpty()) {
                for (TvInputInfo info : tim.getTvInputList()) if (info.isPassthroughInput()) out.add(info);
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

    private void openInput(TvInputInfo info) {
        Intent i = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(info.getId()));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (Exception e) {
            launch("com.tcl.tv");
        }
    }

    private void inputsDialog() {
        List<TvInputInfo> inputs = hdmiInputs();
        if (inputs.isEmpty()) { launch("com.tcl.tv"); return; }
        String[] names = new String[inputs.size()];
        for (int i = 0; i < names.length; i++) names[i] = inputLabel(inputs.get(i));
        new AlertDialog.Builder(this).setTitle("Switch input")
                .setItems(names, (d, w) -> openInput(inputs.get(w)))
                .setNeutralButton("Rename inputs", (d, w) -> renameInputsDialog())
                .show();
    }

    private void renameInputsDialog() {
        List<TvInputInfo> inputs = hdmiInputs();
        String[] names = new String[inputs.size()];
        for (int i = 0; i < names.length; i++) names[i] = inputLabel(inputs.get(i));
        new AlertDialog.Builder(this).setTitle("Rename which input?")
                .setItems(names, (d, w) -> renameInput(inputs.get(w)))
                .show();
    }

    private void renameInput(TvInputInfo info) {
        textDialog("Name for " + info.loadLabel(this), inputLabel(info), "e.g. PS5, Cable box",
                s -> { store.setInputName(info.getId(), s); rebuild(); });
    }

    // ------------------------------------------------------------------ sleep timer

    private void sleepDialog() {
        String[] opts = {"Off", "15 minutes", "30 minutes", "60 minutes", "90 minutes"};
        int[] mins = {0, 15, 30, 60, 90};
        new AlertDialog.Builder(this).setTitle("Sleep timer")
                .setItems(opts, (d, w) -> setSleep(mins[w]))
                .show();
    }

    private void setSleep(int minutes) {
        AlarmManager am = getSystemService(AlarmManager.class);
        PendingIntent pi = PendingIntent.getBroadcast(this, 0,
                new Intent(this, SleepReceiver.class), PendingIntent.FLAG_IMMUTABLE);
        am.cancel(pi);
        if (minutes == 0) {
            store.setSleepAt(0);
            toast("Sleep timer off");
        } else {
            long at = System.currentTimeMillis() + minutes * 60_000L;
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            store.setSleepAt(at);
            if (HomeButtonService.instance == null) {
                toast("Turn on the My Home service (⋯ → Home button setup) so the timer can turn the TV off");
            } else {
                toast("TV will sleep in " + minutes + " minutes");
            }
        }
        updateSleepLabel();
    }

    private void updateSleepLabel() {
        if (sleepButton == null) return;
        long at = store.sleepAt();
        long left = at - System.currentTimeMillis();
        if (at == 0 || left <= 0) sleepButton.setText("Sleep");
        else sleepButton.setText("Sleep · " + Math.max(1, (left + 59_999) / 60_000) + "m");
    }

    // ------------------------------------------------------------------ wifi

    private void updateWifi() {
        if (wifiView == null) return;
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        NetworkCapabilities c = null;
        try { c = cm.getNetworkCapabilities(cm.getActiveNetwork()); } catch (Exception ignored) { }
        if (c == null) {
            wifiView.setText("●  Offline");
            wifiView.setTextColor(0xFFEF5350);
        } else {
            boolean wifi = c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
            boolean eth = c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);
            boolean ok = c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            String kind = wifi ? "Wi-Fi" : eth ? "Ethernet" : "Network";
            wifiView.setText("●  " + kind + (ok ? "" : " (no internet)"));
            wifiView.setTextColor(ok ? 0xFF66BB6A : 0xFFFFCA28);
        }
    }

    // ------------------------------------------------------------------ ⋯ menu

    private void moreMenu() {
        String[] opts = {"Wallpaper", "Card style", "Accent colour", "Categories",
                (store.continueWatching() ? "Hide" : "Show") + " Continue watching",
                "Screensaver", "Hidden apps", "Rename inputs", "Home button setup",
                "Open Google TV home", "About My Home"};
        new AlertDialog.Builder(this).setItems(opts, (d, w) -> {
            switch (w) {
                case 0: wallpaperDialog(); break;
                case 1: styleDialog(); break;
                case 2: accentDialog(); break;
                case 3: categoriesDialog(); break;
                case 4: store.setContinueWatching(!store.continueWatching()); rebuild(); break;
                case 5: screensaverDialog(); break;
                case 6: hiddenDialog(); break;
                case 7: renameInputsDialog(); break;
                case 8: homeSetupDialog(); break;
                case 9: openGoogleHome(); break;
                case 10: aboutDialog(); break;
            }
        }).show();
    }

    private void wallpaperDialog() {
        new AlertDialog.Builder(this).setTitle("Wallpaper")
                .setSingleChoiceItems(WALLPAPER_NAMES, store.wallpaper(), (d, w) -> {
                    if (w == WALLPAPER_NAMES.length - 1 && !wallpaperFile().exists()) {
                        toast("Copy a picture to " + wallpaperFile().getAbsolutePath() + " first");
                        return;
                    }
                    store.setWallpaper(w);
                    root.setBackground(wallpaperDrawable());
                    d.dismiss();
                }).show();
    }

    private File wallpaperFile() {
        return new File(getExternalFilesDir(null), "wallpaper.jpg");
    }

    private Drawable wallpaperDrawable() {
        int w = store.wallpaper();
        if (w == WALLPAPER_NAMES.length - 1) {
            File f = wallpaperFile();
            if (f.exists()) {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inSampleSize = 2;
                Bitmap bm = BitmapFactory.decodeFile(f.getAbsolutePath(), o);
                if (bm != null) {
                    BitmapDrawable bd = new BitmapDrawable(getResources(), bm);
                    // Darken the picture so text stays readable.
                    return new LayerDrawable(new Drawable[]{bd, new ColorDrawable(0x80000000)});
                }
            }
            w = 0;
        }
        if (w < 0 || w >= WALLPAPER_COLORS.length) w = 0;
        GradientDrawable base = new GradientDrawable(GradientDrawable.Orientation.TL_BR, WALLPAPER_COLORS[w]);
        int[] glows = WALLPAPER_GLOWS[w];
        return new LayerDrawable(new Drawable[]{base,
                glow(glows[0], 0.12f, 0.15f, 0.55f),
                glow(glows[1], 0.88f, 0.35f, 0.5f),
                glow(glows[2], 0.45f, 1.0f, 0.6f)});
    }

    private void hiddenDialog() {
        List<String> hidden = store.hidden();
        if (hidden.isEmpty()) { toast("No hidden apps"); return; }
        String[] names = new String[hidden.size()];
        for (int i = 0; i < names.length; i++) {
            App a = apps.get(hidden.get(i));
            names[i] = a != null ? a.label : hidden.get(i);
        }
        new AlertDialog.Builder(this).setTitle("Tap an app to show it again")
                .setItems(names, (d, w) -> {
                    List<String> h = store.hidden(); h.remove(hidden.get(w)); store.setHidden(h);
                    rebuild();
                }).show();
    }

    private void homeSetupDialog() {
        boolean on = HomeButtonService.instance != null;
        new AlertDialog.Builder(this).setTitle("Home button")
                .setMessage(on
                        ? "The Home button is set to open My Home. To go back to Google's home screen, "
                          + "turn off \"My Home - Home button\" in Accessibility settings."
                        : "To make the remote's Home button open My Home, turn on "
                          + "\"My Home - Home button\" in Accessibility settings.\n\n"
                          + "It only watches for the Home key and which app is open (for the Recent row). "
                          + "It can't read the screen and has no internet access.")
                .setPositiveButton("Open Accessibility settings",
                        (d, w) -> safeStart(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)))
                .setNegativeButton("Close", null)
                .show();
    }

    private void openGoogleHome() {
        store.setGoogleHomeBypassUntil(System.currentTimeMillis() + 5 * 60_000L);
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .setComponent(null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        for (String p : Arrays.asList("com.google.android.apps.tv.launcherx", "com.google.android.tvlauncher")) {
            i.setPackage(p);
            if (pm.resolveActivity(i, 0) != null) { safeStart(i); return; }
        }
        toast("Google TV home not found");
    }

    private void aboutDialog() {
        new AlertDialog.Builder(this).setTitle("My Home 1.1")
                .setMessage("A simple, ad-free home screen.\n\nNo internet permission, no tracking.\n\n"
                        + "Long-press OK on any app to pin, move, rename or hide it.\n"
                        + "Long-press an input to rename it.")
                .setPositiveButton("OK", null).show();
    }

    // ------------------------------------------------------------------ helpers

    private interface TextResult { void onText(String s); }

    private void textDialog(String title, String current, String hint, TextResult r) {
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setText(current);
        e.setHint(hint);
        e.setSelectAllOnFocus(true);
        FrameLayout box = new FrameLayout(this);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        box.addView(e);
        new AlertDialog.Builder(this).setTitle(title).setView(box)
                .setPositiveButton("Save", (d, w) -> r.onText(e.getText().toString()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void safeStart(Intent i) {
        try {
            if ((i.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) == 0) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            toast("Couldn't open that");
        }
    }

    private static final String EPG_PERMISSION = "com.android.providers.tv.permission.READ_EPG_DATA";

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        rebuild();
    }

    private int columns() {
        int s = store.cardStyle();
        return s == Store.STYLE_CAPSULE ? 8 : s == Store.STYLE_PREMIUM ? 5 : 6;
    }

    private int accentColor() {
        int a = store.accent();
        return ACCENT_COLORS[a >= 0 && a < ACCENT_COLORS.length ? a : 0];
    }

    private void styleDialog() {
        new AlertDialog.Builder(this).setTitle("Card style")
                .setSingleChoiceItems(STYLE_NAMES, store.cardStyle(), (d, w) -> {
                    store.setCardStyle(w); d.dismiss(); rebuild();
                }).show();
    }

    private void accentDialog() {
        new AlertDialog.Builder(this).setTitle("Accent colour")
                .setSingleChoiceItems(ACCENT_NAMES, store.accent(), (d, w) -> {
                    store.setAccent(w); d.dismiss(); rebuild();
                }).show();
    }

    private void screensaverDialog() {
        new AlertDialog.Builder(this).setTitle("Screensaver")
                .setMessage("My Home includes an OLED-safe clock screensaver: a dim clock on black "
                        + "that moves every 30 seconds to prevent burn-in.\n\n"
                        + "To use it, choose \"My Home clock\" in the screensaver settings.")
                .setPositiveButton("Open screensaver settings", (d, w) -> {
                    Intent i = new Intent(Settings.ACTION_DREAM_SETTINGS);
                    if (pm.resolveActivity(i, 0) == null) i = new Intent(Settings.ACTION_DISPLAY_SETTINGS);
                    safeStart(i);
                })
                .setNegativeButton("Close", null).show();
    }

    // ---- categories
    private void addToCategoryDialog(App a) {
        List<String> cats = store.categories();
        List<String> opts = new ArrayList<>(cats);
        opts.add("+ New category…");
        new AlertDialog.Builder(this).setTitle("Add " + a.label + " to")
                .setItems(opts.toArray(new String[0]), (d, w) -> {
                    if (w == cats.size()) {
                        textDialog("New category", "", "e.g. Kids, Games, Music", name -> {
                            String n = name.replace("|", "").trim();
                            if (n.isEmpty()) return;
                            List<String> c = store.categories();
                            if (!c.contains(n)) { c.add(n); store.setCategories(c); }
                            addAppToCategory(n, a.pkg);
                        });
                    } else {
                        addAppToCategory(cats.get(w), a.pkg);
                    }
                }).show();
    }

    private void addAppToCategory(String cat, String pkg) {
        List<String> l = store.categoryApps(cat);
        if (!l.contains(pkg)) l.add(pkg);
        store.setCategoryApps(cat, l);
        toast("Added to " + cat);
        rebuild();
    }

    private void categoriesDialog() {
        List<String> cats = store.categories();
        List<String> opts = new ArrayList<>(cats);
        opts.add("+ New category…");
        new AlertDialog.Builder(this).setTitle("Categories")
                .setItems(opts.toArray(new String[0]), (d, w) -> {
                    if (w == cats.size()) {
                        textDialog("New category", "", "e.g. Kids, Games, Music", name -> {
                            String n = name.replace("|", "").trim();
                            if (n.isEmpty()) return;
                            List<String> c = store.categories();
                            if (!c.contains(n)) { c.add(n); store.setCategories(c); }
                            toast("Long-press any app → Add to category… to fill it");
                            rebuild();
                        });
                    } else {
                        categoryActions(cats.get(w));
                    }
                }).show();
    }

    private void categoryActions(String cat) {
        String[] opts = {"Rename", "Move up", "Move down", "Delete"};
        new AlertDialog.Builder(this).setTitle(cat).setItems(opts, (d, w) -> {
            List<String> c = store.categories();
            int i = c.indexOf(cat);
            switch (w) {
                case 0:
                    textDialog("Rename " + cat, cat, "", name -> {
                        String n = name.replace("|", "").trim();
                        if (!n.isEmpty() && !c.contains(n)) store.renameCategory(cat, n);
                        rebuild();
                    });
                    return;
                case 1: if (i > 0) { Collections.swap(c, i, i - 1); store.setCategories(c); } break;
                case 2: if (i >= 0 && i < c.size() - 1) { Collections.swap(c, i, i + 1); store.setCategories(c); } break;
                case 3: store.deleteCategory(cat); break;
            }
            rebuild();
        }).show();
    }

    // ---- continue watching
    private static final class WatchItem {
        String title, pkg, intentUri;
        long position, duration;
        Drawable poster;
    }

    private List<WatchItem> loadWatchNext() {
        List<WatchItem> out = new ArrayList<>();
        if (checkSelfPermission(EPG_PERMISSION) != PackageManager.PERMISSION_GRANTED) return out;
        String[] cols = {
                TvContract.WatchNextPrograms.COLUMN_TITLE,
                TvContract.WatchNextPrograms.COLUMN_PACKAGE_NAME,
                TvContract.WatchNextPrograms.COLUMN_INTENT_URI,
                TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI,
                TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS,
                TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS};
        try (android.database.Cursor c = getContentResolver().query(
                TvContract.WatchNextPrograms.CONTENT_URI, cols, null, null,
                TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS + " DESC")) {
            if (c == null) return out;
            while (c.moveToNext() && out.size() < 12) {
                WatchItem w = new WatchItem();
                w.title = c.getString(0);
                w.pkg = c.getString(1);
                w.intentUri = c.getString(2);
                w.position = c.getLong(4);
                w.duration = c.getLong(5);
                String poster = c.getString(3);
                // Only local posters: My Home has no internet permission.
                if (poster != null && (poster.startsWith("content:") || poster.startsWith("android.resource:"))) {
                    try (java.io.InputStream in = getContentResolver().openInputStream(Uri.parse(poster))) {
                        Bitmap bm = BitmapFactory.decodeStream(in);
                        if (bm != null) w.poster = new BitmapDrawable(getResources(), bm);
                    } catch (Exception ignored) { }
                }
                if (w.title != null && w.intentUri != null) out.add(w);
            }
        } catch (Exception ignored) { }
        return out;
    }

    private View watchTile(WatchItem wi) {
        App app = apps.get(wi.pkg);
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setFocusable(true);
        t.setClickable(true);
        t.setClipChildren(false);
        t.setClipToPadding(false);
        int w = dp(160), h = dp(90);

        FrameLayout card = new FrameLayout(this);
        card.setPadding(dp(9), dp(9), dp(9), dp(9));
        FrameLayout art = new FrameLayout(this);
        art.setClipToOutline(true);
        art.setBackground(rounded(0xFF1E2430, 0, 12));
        ImageView img = new ImageView(this);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (wi.poster != null) img.setImageDrawable(wi.poster);
        else if (app != null && app.banner != null) { img.setImageDrawable(app.banner); img.setAlpha(0.45f); }
        art.addView(img, new FrameLayout.LayoutParams(-1, -1));
        if (wi.poster == null) {
            TextView tt = new TextView(this);
            tt.setText(wi.title);
            tt.setTextColor(Color.WHITE);
            tt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            tt.setTypeface(Typeface.DEFAULT_BOLD);
            tt.setMaxLines(2);
            tt.setEllipsize(TextUtils.TruncateAt.END);
            tt.setShadowLayer(dp(4), 0, dp(1), 0xCC000000);
            tt.setPadding(dp(10), dp(8), dp(10), dp(8));
            art.addView(tt, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        }
        if (wi.duration > 0 && wi.position > 0) {
            FrameLayout bar = new FrameLayout(this);
            bar.setBackgroundColor(0x55FFFFFF);
            View fill = new View(this);
            fill.setBackgroundColor(accentColor());
            int fw = (int) (w * Math.min(1f, wi.position / (float) wi.duration));
            bar.addView(fill, new FrameLayout.LayoutParams(fw, -1));
            art.addView(bar, new FrameLayout.LayoutParams(-1, dp(4), Gravity.BOTTOM));
        }
        card.addView(art, new FrameLayout.LayoutParams(w, h));
        card.setAlpha(0.85f);
        t.addView(card);

        TextView label = new TextView(this);
        label.setText(app != null ? app.label + " · " + wi.title : wi.title);
        label.setTextColor(0xB3FFFFFF);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER_HORIZONTAL);
        label.setPadding(0, dp(6), 0, 0);
        label.setShadowLayer(dp(3), 0, dp(1), 0x99000000);
        t.addView(label, new LinearLayout.LayoutParams(w + dp(18), -2));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(dp(4), dp(4), dp(4), dp(4));
        t.setLayoutParams(lp);
        t.setOnFocusChangeListener((v, has) -> {
            card.setBackground(has ? glass(true, 18) : null);
            card.setElevation(has ? dp(12) : 0);
            card.animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f).alpha(has ? 1f : 0.85f)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(1.4f))
                    .setDuration(220).start();
            label.setTextColor(has ? Color.WHITE : 0xB3FFFFFF);
        });
        t.setOnClickListener(v -> {
            try {
                Intent i = Intent.parseUri(wi.intentUri, Intent.URI_INTENT_SCHEME);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            } catch (Exception e) {
                if (wi.pkg != null) launch(wi.pkg);
            }
        });
        return t;
    }

    /** Frosted, tinted glass card. Focused = brighter fill, white rim and a soft cyan glow. */
    private Drawable glass(boolean focused, int radiusDp) {
        int glowPad = dp(4);
        GradientDrawable glowRing = new GradientDrawable();
        glowRing.setCornerRadius(dp(radiusDp + 4));
        glowRing.setColor(0);
        glowRing.setStroke(glowPad, focused ? (accentColor() & 0x00FFFFFF) | 0xAA000000 : 0);

        GradientDrawable body = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                focused ? new int[]{0x55FFFFFF, 0x2EFFFFFF} : new int[]{0x26FFFFFF, 0x0FFFFFFF});
        body.setCornerRadius(dp(radiusDp));
        body.setStroke(focused ? dp(3) : dp(1), focused ? 0xFFFFFFFF : 0x40FFFFFF);

        GradientDrawable sheen = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{focused ? 0x33FFFFFF : 0x1AFFFFFF, 0x00FFFFFF});
        sheen.setCornerRadius(dp(radiusDp));

        // Thin dark inner ring keeps the white border visible even on white banners.
        GradientDrawable inner = new GradientDrawable();
        inner.setCornerRadius(Math.max(0, dp(radiusDp) - dp(3)));
        inner.setColor(0);
        inner.setStroke(focused ? dp(2) : 0, 0x99000000);

        LayerDrawable ld = new LayerDrawable(new Drawable[]{glowRing, body, sheen, inner});
        ld.setLayerInset(1, glowPad, glowPad, glowPad, glowPad);
        ld.setLayerInset(2, glowPad, glowPad, glowPad, glowPad + dp(40));
        int in = glowPad + dp(3);
        ld.setLayerInset(3, in, in, in, in);
        return ld;
    }

    private Drawable glassPill(boolean focused) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                focused ? new int[]{0xFFFFFFFF, 0xE6E3F2FD} : new int[]{0x33FFFFFF, 0x14FFFFFF});
        g.setCornerRadius(dp(28));
        g.setStroke(dp(1), focused ? 0xFFFFFFFF : 0x4DFFFFFF);
        return g;
    }

    private Drawable glow(int color, float cx, float cy, float radiusFrac) {
        GradientDrawable g = new GradientDrawable();
        g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        g.setColors(new int[]{color, color & 0x00FFFFFF});
        g.setGradientCenter(cx, cy);
        g.setGradientRadius(getResources().getDisplayMetrics().widthPixels * radiusFrac);
        return g;
    }

    private GradientDrawable rounded(int fill, int stroke, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (stroke != 0) g.setStroke(dp(3), stroke);
        return g;
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
