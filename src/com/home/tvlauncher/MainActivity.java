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
            "My picture (wallpaper.jpg)", "My video (wallpaper.mp4)",
            "Aurora (art)", "Sunset hills (art)", "Misty mountains (art)", "Moonlit sea (art)"};
    private static final int WP_ART = 8;
    private static final int WP_PICTURE = 6, WP_VIDEO = 7;

    private static final String[] TIME_FORMATS = {"h:mm a", "HH:mm", "h:mm"};
    private static final String[] TIME_NAMES = {"12-hour (9:41 PM)", "24-hour (21:41)", "12-hour, no AM/PM (9:41)"};
    private static final String[] DATE_FORMATS = {"EEE, MMM d", "EEEE, MMMM d", "d MMM", "dd/MM/yyyy", "MM/dd/yyyy"};
    private static final String[] BORDER_NAMES = {"Thin", "Normal", "Thick"};
    private static final int[] BORDER_DP = {2, 3, 5};
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
    private ScrollView scroll;
    private View barWrap;
    private GlassPanel dock;
    private Bitmap blurCache;
    private int blurCacheId = -1;
    private View barFocus;
    private boolean barShown = true;
    private TextView dataView;
    private android.widget.VideoView videoView;
    private int appliedWallpaper = -1;

    // Arrow-key move mode for Favorites / category rows.
    private String moveSection, movePkg;
    private String pendingFocusKey;
    private final Map<String, View> tileIndex = new HashMap<>();
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
            updateDataUsage();
            if (effectiveWallpaper() != appliedWallpaper) rebuild(); // day/night switch
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

        // Bring the hidden status bar back whenever focus moves into it.
        root.getViewTreeObserver().addOnGlobalFocusChangeListener((oldF, newF) -> {
            if (newF != null && barWrap != null && isInside(newF, barWrap)) {
                setBarShown(true);
                if (scroll != null) scroll.smoothScrollTo(0, 0);
            }
        });

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
        if (videoView != null) videoView.stopPlayback();
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
        closePanel(false);
        closeSearch();
        if (scroll != null) scroll.smoothScrollTo(0, 0);
        View first = firstFocusTarget;
        if (first != null) first.requestFocus();
    }

    @Override
    public void onBackPressed() {
        if (panel != null) { closePanel(true); return; }
        // A home screen has nowhere to go back to. Back only closes search.
        if (searchBox != null && searchBox.getVisibility() == View.VISIBLE) closeSearch();
    }

    // ------------------------------------------------------------------ building the screen

    private View firstFocusTarget;

    private void rebuild() {
        loadApps();
        initDefaultFavorites();
        panel = null;
        root.removeAllViews();
        tileIndex.clear();
        videoView = null;
        dock = null;
        applyWallpaper();
        boolean showcase = store.layout() == Store.LAYOUT_SHOWCASE;

        scroll = new ScrollView(this) {
            // We scroll ourselves (smoothly, keeping the focused row in view), so stop the default jump.
            @Override protected int computeScrollDeltaToGetChildRectOnScreen(android.graphics.Rect r) { return 0; }
        };
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setClipChildren(false);
        content.setClipToPadding(false);
        // Classic: leave room at the top for the floating status bar.
        content.setPadding(dp(40), showcase ? 0 : dp(118), dp(40), dp(24));
        scroll.addView(content);
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        firstFocusTarget = null;

        // Status bar floats above the content and slides away when you scroll into the apps.
        LinearLayout bw = new LinearLayout(this);
        bw.setPadding(dp(40), dp(24), dp(40), dp(16));
        bw.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{showcase ? 0x40000000 : 0x66000000, 0x00000000}));
        bw.addView(showcase ? buildMinimalBar() : buildTopBar(), new LinearLayout.LayoutParams(-1, -2));
        barWrap = bw;
        barShown = true;
        root.addView(bw, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));
        scroll.setOnScrollChangeListener((v, x, y, ox, oy) -> {
            setBarShown(y < dp(40));
            if (dock != null) dock.invalidate(); // keep the frosted glass lined up with the wallpaper
        });

        // Favorites: a frosted dock low on the screen (Showcase) or a normal row (Classic)
        List<String> favs = new ArrayList<>();
        for (String p : store.favorites()) if (apps.containsKey(p)) favs.add(p);
        if (showcase) {
            View spacer = new View(this);
            content.addView(spacer, new LinearLayout.LayoutParams(-1, dp(300)));
            List<String> dockApps = favs;
            if (dockApps.isEmpty()) {
                dockApps = new ArrayList<>(apps.keySet());
                if (dockApps.size() > 6) dockApps = dockApps.subList(0, 6);
            }
            GlassPanel d = new GlassPanel(this, blurredWallpaper(), dp(28));
            d.setClipChildren(false);
            d.setClipToPadding(false);
            d.setPadding(dp(14), dp(2), dp(14), dp(0));
            d.addView(tileRow(dockApps, favs.isEmpty() ? null : "fav", true));
            content.addView(d, new LinearLayout.LayoutParams(-1, -2));
            dock = d;
            final ScrollView sc = scroll;
            sc.post(() -> {
                int h = sc.getHeight() - d.getHeight() - dp(30);
                spacer.getLayoutParams().height = Math.max(dp(140), h);
                spacer.requestLayout();
                // Re-assert focus once the dock has moved into place.
                View f = getCurrentFocus();
                if (moveSection == null && pendingFocusKey == null && (f == null || f == sc || !f.isShown())
                        && firstFocusTarget != null) {
                    sc.post(() -> firstFocusTarget.requestFocus());
                }
            });
        } else if (!favs.isEmpty()) {
            content.addView(sectionTitle("★  Favorites"));
            content.addView(tileRow(favs, "fav", true));
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
            content.addView(store.categoryGrid(cat) ? tileGrid(pk, "cat:" + cat) : tileRow(pk, "cat:" + cat, false));
        }

        // Recent
        List<String> rec = new ArrayList<>();
        for (String p : store.recents()) if (apps.containsKey(p)) rec.add(p);
        if (!rec.isEmpty()) {
            content.addView(sectionTitle("Recent"));
            content.addView(tileRow(rec, null, false));
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
            lockRowEnds(row);
            hs.addView(row);
            content.addView(hs);
        }

        // Search box sits right above All apps, so results show directly under it.
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
        slp.topMargin = dp(24);
        content.addView(searchBox, slp);

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
        updateDataUsage();
        final View pending = pendingFocusKey != null ? tileIndex.get(pendingFocusKey) : null;
        pendingFocusKey = null;
        final View first = firstFocusTarget;
        if (first != null) first.setFocusedByDefault(true);
        final ScrollView sc = scroll;
        sc.post(() -> {
            if (pending != null) {
                pending.requestFocus();
            } else {
                if (first != null) first.requestFocus();
                sc.scrollTo(0, 0);
            }
        });
    }

    /** Showcase status bar: a few icons on the left, a calm clock on the right. */
    private View buildMinimalBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        View menu = iconButton(android.R.drawable.ic_menu_preferences, "Menu");
        menu.setOnClickListener(v -> openPanel());
        bar.addView(menu);
        barFocus = menu;

        View search = iconButton(android.R.drawable.ic_menu_search, "Search");
        search.setOnClickListener(v -> openSearch());
        bar.addView(search);

        wifiView = new TextView(this);
        wifiView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        wifiView.setGravity(Gravity.CENTER);
        wifiView.setMinWidth(dp(52));
        wifiView.setPadding(dp(14), dp(10), dp(14), dp(10));
        wifiView.setFocusable(true);
        wifiView.setClickable(true);
        wifiView.setOnFocusChangeListener((v, has) -> {
            v.setBackground(has ? glassPill(true) : null);
            if (has) wifiView.setTextColor(0xFF0D1B2A); else updateWifi();
        });
        wifiView.setOnClickListener(v -> networkDialog());
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(-2, -2);
        wlp.leftMargin = dp(6);
        bar.addView(wifiView, wlp);

        bar.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));

        sleepButton = new TextView(this);
        sleepButton.setTextColor(0xCCFFFFFF);
        sleepButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        sleepButton.setPadding(dp(12), dp(4), dp(12), dp(4));
        sleepButton.setBackground(glassPill(false));
        sleepButton.setVisibility(View.GONE);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-2, -2);
        slp.rightMargin = dp(16);
        bar.addView(sleepButton, slp);

        dataView = new TextView(this);
        dataView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        dataView.setTextColor(0xB3FFFFFF);
        dataView.setVisibility(View.GONE);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-2, -2);
        dlp.rightMargin = dp(20);
        bar.addView(dataView, dlp);

        LinearLayout clockBox = new LinearLayout(this);
        clockBox.setOrientation(LinearLayout.VERTICAL);
        clockBox.setGravity(Gravity.END);
        TextClock time = new TextClock(this);
        String tf = TIME_FORMATS[clamp(store.timeFormat(), TIME_FORMATS.length)];
        time.setFormat12Hour(tf);
        time.setFormat24Hour(tf);
        time.setTextColor(Color.WHITE);
        time.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        time.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        time.setShadowLayer(dp(6), 0, dp(1), 0x80000000);
        clockBox.addView(time);
        TextClock date = new TextClock(this);
        String df = DATE_FORMATS[clamp(store.dateFormat(), DATE_FORMATS.length)];
        date.setFormat12Hour(df);
        date.setFormat24Hour(df);
        date.setTextColor(0xCCFFFFFF);
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        date.setShadowLayer(dp(4), 0, dp(1), 0x80000000);
        clockBox.addView(date);
        bar.addView(clockBox);
        return bar;
    }

    private View iconButton(int iconRes, String description) {
        FrameLayout f = new FrameLayout(this);
        f.setFocusable(true);
        f.setClickable(true);
        f.setContentDescription(description);
        ImageView img = new ImageView(this);
        img.setImageResource(iconRes);
        img.setColorFilter(Color.WHITE);
        f.addView(img, new FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER));
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        f.setOnFocusChangeListener((v, has) -> {
            circle.setColor(has ? 0xFFFFFFFF : 0);
            v.setBackground(has ? circle : null);
            img.setColorFilter(has ? 0xFF0D1B2A : Color.WHITE);
            v.animate().scaleX(has ? 1.1f : 1f).scaleY(has ? 1.1f : 1f).setDuration(150).start();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(52), dp(52));
        lp.rightMargin = dp(6);
        f.setLayoutParams(lp);
        return f;
    }

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout clockBox = new LinearLayout(this);
        clockBox.setOrientation(LinearLayout.VERTICAL);
        TextClock time = new TextClock(this);
        String tf = TIME_FORMATS[clamp(store.timeFormat(), TIME_FORMATS.length)];
        time.setFormat12Hour(tf);
        time.setFormat24Hour(tf);
        time.setTextColor(Color.WHITE);
        time.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34);
        time.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        clockBox.addView(time);

        LinearLayout sub = new LinearLayout(this);
        sub.setGravity(Gravity.CENTER_VERTICAL);
        TextClock date = new TextClock(this);
        String df = DATE_FORMATS[clamp(store.dateFormat(), DATE_FORMATS.length)];
        date.setFormat12Hour(df);
        date.setFormat24Hour(df);
        date.setTextColor(0xCCFFFFFF);
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        sub.addView(date);
        wifiView = new TextView(this);
        wifiView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        wifiView.setPadding(dp(12), dp(3), dp(12), dp(3));
        wifiView.setFocusable(true);
        wifiView.setClickable(true);
        wifiView.setOnFocusChangeListener((v, has) -> {
            v.setBackground(has ? glassPill(true) : null);
            if (has) wifiView.setTextColor(0xFF0D1B2A); else updateWifi();
        });
        wifiView.setOnClickListener(v -> networkDialog());
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(-2, -2);
        wlp.leftMargin = dp(6);
        wlp.rightMargin = dp(6);
        sub.addView(wifiView, wlp);
        dataView = new TextView(this);
        dataView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        dataView.setTextColor(0xB3FFFFFF);
        dataView.setVisibility(View.GONE);
        sub.addView(dataView);
        clockBox.addView(sub);
        bar.addView(clockBox, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView search = button("Search");
        search.setOnClickListener(v -> openSearch());
        bar.addView(search);
        barFocus = search;
        // Left from Search goes to the Wi-Fi button (not down into the apps), and back.
        search.setId(View.generateViewId());
        wifiView.setId(View.generateViewId());
        search.setNextFocusLeftId(wifiView.getId());
        wifiView.setNextFocusRightId(search.getId());
        wifiView.setNextFocusLeftId(wifiView.getId());
        wifiView.setNextFocusUpId(wifiView.getId());

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
        more.setOnClickListener(v -> openPanel());
        bar.addView(more);
        return bar;
    }

    // ---- side panel (replaces the old pop-up menu)

    private View panel;

    private static final class PanelItem {
        final int icon; final String label; final Runnable action;
        PanelItem(int icon, String label, Runnable action) { this.icon = icon; this.label = label; this.action = action; }
    }

    private void openPanel() {
        if (panel != null) return;
        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(0x99000000);
        overlay.setClickable(true);

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF2181B22);
        float r = dp(28);
        bg.setCornerRadii(new float[]{0, 0, r, r, r, r, 0, 0});
        sv.setBackground(bg);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(18), dp(28), dp(18), dp(28));
        sv.addView(list);

        TextView title = new TextView(this);
        title.setText("Lumen");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setPadding(dp(14), 0, 0, dp(8));
        list.addView(title);

        View firstRow = null;
        Object[][] sections = {
                {"Quick", new PanelItem[]{
                        new PanelItem(android.R.drawable.ic_menu_search, "Search apps", this::openSearch),
                        new PanelItem(android.R.drawable.ic_lock_idle_alarm, sleepLabel(), this::sleepDialog),
                        new PanelItem(android.R.drawable.ic_media_play, "Play Store", () -> launch("com.android.vending")),
                        new PanelItem(android.R.drawable.ic_menu_slideshow, "HDMI inputs", this::inputsDialog),
                        new PanelItem(android.R.drawable.ic_menu_manage, "TV settings",
                                () -> safeStart(new Intent(Settings.ACTION_SETTINGS)))}},
                {"Look", new PanelItem[]{
                        new PanelItem(android.R.drawable.ic_menu_view, "Layout: " + (store.layout() == Store.LAYOUT_SHOWCASE ? "Showcase" : "Classic"), this::layoutDialog),
                        new PanelItem(android.R.drawable.ic_menu_gallery, "Wallpaper", this::wallpaperDialog),
                        new PanelItem(android.R.drawable.ic_menu_crop, "Card style", this::styleDialog),
                        new PanelItem(android.R.drawable.ic_menu_edit, "Accent colour", this::accentDialog),
                        new PanelItem(android.R.drawable.ic_menu_zoom, "Focus style", this::focusStyleDialog),
                        new PanelItem(android.R.drawable.ic_menu_recent_history, "Clock & date format", this::clockFormatDialog)}},
                {"Home screen", new PanelItem[]{
                        new PanelItem(android.R.drawable.ic_menu_sort_by_size, "Categories", this::categoriesDialog),
                        new PanelItem(android.R.drawable.ic_menu_close_clear_cancel, "Hidden apps", this::hiddenDialog),
                        new PanelItem(android.R.drawable.ic_menu_edit, "Rename inputs", this::renameInputsDialog),
                        new PanelItem(android.R.drawable.ic_menu_upload, "Data usage", this::dataUsageDialog),
                        new PanelItem(android.R.drawable.ic_lock_silent_mode_off,
                                "Navigation sounds: " + (store.sounds() ? "On" : "Off"), () -> {
                                    store.setSounds(!store.sounds());
                                    toast("Navigation sounds " + (store.sounds() ? "on" : "off"));
                                })}},
                {"System", new PanelItem[]{
                        new PanelItem(android.R.drawable.ic_menu_slideshow, "Screensaver", this::screensaverDialog),
                        new PanelItem(android.R.drawable.ic_menu_revert, "Home button setup", this::homeSetupDialog),
                        new PanelItem(android.R.drawable.ic_menu_mapmode, "Open Google TV home", this::openGoogleHome),
                        new PanelItem(android.R.drawable.ic_menu_info_details, "About Lumen", this::aboutDialog)}}};

        for (Object[] sec : sections) {
            TextView h = new TextView(this);
            h.setText(((String) sec[0]).toUpperCase(Locale.ROOT));
            h.setTextColor(0x80FFFFFF);
            h.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            h.setLetterSpacing(0.12f);
            h.setPadding(dp(14), dp(18), 0, dp(6));
            list.addView(h);
            for (PanelItem it : (PanelItem[]) sec[1]) {
                View row = panelRow(it);
                if (firstRow == null) firstRow = row;
                list.addView(row);
            }
        }

        overlay.addView(sv, new FrameLayout.LayoutParams(dp(360), -1, Gravity.START));
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        panel = overlay;
        scroll.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        if (barWrap instanceof ViewGroup) ((ViewGroup) barWrap).setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        sv.setTranslationX(-dp(360));
        sv.animate().translationX(0).setDuration(220).start();
        overlay.setAlpha(0f);
        overlay.animate().alpha(1f).setDuration(220).start();
        if (firstRow != null) firstRow.requestFocus();
    }

    private View panelRow(PanelItem it) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setClickable(true);
        row.setPadding(dp(14), dp(11), dp(14), dp(11));
        ImageView icon = new ImageView(this);
        icon.setImageResource(it.icon);
        icon.setColorFilter(0xE6FFFFFF);
        row.addView(icon, new LinearLayout.LayoutParams(dp(26), dp(26)));
        TextView label = new TextView(this);
        label.setText(it.label);
        label.setTextColor(0xE6FFFFFF);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(0, -2, 1f);
        llp.leftMargin = dp(18);
        row.addView(label, llp);
        row.setOnFocusChangeListener((v, has) -> {
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(14));
            g.setColor(has ? 0x1FFFFFFF : 0);
            if (has) g.setStroke(dp(2), Color.WHITE);
            v.setBackground(g);
            label.setTextColor(has ? Color.WHITE : 0xE6FFFFFF);
        });
        row.setOnClickListener(v -> {
            closePanel(false);
            it.action.run();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(2);
        row.setLayoutParams(lp);
        return row;
    }

    private void closePanel(boolean restoreFocus) {
        if (panel == null) return;
        View p = panel;
        panel = null;
        root.removeView(p);
        scroll.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        if (barWrap instanceof ViewGroup) ((ViewGroup) barWrap).setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        if (restoreFocus && barFocus != null) barFocus.requestFocus();
    }

    private String sleepLabel() {
        long left = store.sleepAt() - System.currentTimeMillis();
        return store.sleepAt() == 0 || left <= 0 ? "Sleep timer" : "Sleep timer · " + Math.max(1, (left + 59_999) / 60_000) + " min left";
    }

    private void layoutDialog() {
        String[] names = {"Showcase (wallpaper + glass dock)", "Classic (rows)"};
        new AlertDialog.Builder(this).setTitle("Layout")
                .setSingleChoiceItems(names, store.layout(), (d, w) -> {
                    store.setLayout(w); d.dismiss(); rebuild();
                }).show();
    }

    private void focusStyleDialog() {
        String[] names = {"Glass glow (lift and glow)", "Minimal (clean white border)",
                "Border thickness: " + BORDER_NAMES[clamp(store.border(), BORDER_NAMES.length)]};
        new AlertDialog.Builder(this).setTitle("Focus style")
                .setItems(names, (d, w) -> {
                    if (w == 2) { borderDialog(); return; }
                    store.setFocusStyle(w);
                    rebuild();
                }).show();
    }

    /** Downscaled, blurred copy of the wallpaper for the glass dock (cached per wallpaper). */
    private Bitmap blurredWallpaper() {
        int w = effectiveWallpaper();
        if (w == WP_VIDEO && videoFile().exists()) return null; // video: plain tinted glass
        if (blurCache != null && blurCacheId == w) return blurCache;
        try {
            int sw = getResources().getDisplayMetrics().widthPixels;
            int sh = getResources().getDisplayMetrics().heightPixels;
            Bitmap full = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888);
            Drawable d = wallpaperDrawable(w);
            d.setBounds(0, 0, sw, sh);
            d.draw(new android.graphics.Canvas(full));
            Bitmap small = Bitmap.createScaledBitmap(full, Math.max(1, sw / 10), Math.max(1, sh / 10), true);
            full.recycle();
            blurCache = GlassPanel.blur(small, 4);
            small.recycle();
            blurCacheId = w;
            return blurCache;
        } catch (Throwable t) {
            return null;
        }
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

    private View tileGrid(List<String> pkgs, String section) {
        GridLayout g = new GridLayout(this);
        g.setColumnCount(columns());
        g.setClipChildren(false);
        g.setClipToPadding(false);
        g.setPadding(dp(8), dp(16), dp(8), dp(14));
        for (String p : pkgs) g.addView(tile(apps.get(p), section));
        lockGridEdges(g);
        return g;
    }

    private View tileRow(List<String> pkgs, String section, boolean favorites) {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipToPadding(false);
        hs.setClipChildren(false);
        LinearLayout row = new LinearLayout(this);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        row.setPadding(dp(8), dp(16), dp(8), dp(14));
        for (String p : pkgs) {
            View t = tile(apps.get(p), section);
            if (favorites && firstFocusTarget == null) firstFocusTarget = t;
            row.addView(t);
        }
        lockRowEnds(row);
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
            View t = tile(a, null);
            if (firstFocusTarget == null) firstFocusTarget = t;
            allGrid.addView(t);
        }
        lockGridEdges(allGrid);
        allTitle.setText(q.isEmpty() ? "All apps" : "Results (" + list.size() + ")");
    }

    private View tile(App a, String section) {
        boolean moving = section != null && section.equals(moveSection) && a.pkg.equals(movePkg);
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
        label.setText(moving ? "◀  " + a.label + "  ▶" : a.label);
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
            if (has) glideTo(v);
            boolean minimal = store.focusStyle() == Store.FOCUS_MINIMAL;
            card.setBackground(has ? (minimal ? minimalFrame(radius) : glass(true, radius)) : null);
            card.setElevation(has && !minimal ? dp(12) : 0);
            float grow = minimal ? 1.04f : 1.1f;
            card.animate().scaleX(has ? grow : 1f).scaleY(has ? grow : 1f).alpha(has ? 1f : 0.85f)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(1.4f))
                    .setDuration(220).start();
            label.animate().translationY(has ? dp(4) : 0).alpha(premium && !has ? 0f : 1f)
                    .setDuration(220).start();
            label.setTextColor(has ? Color.WHITE : 0xB3FFFFFF);
            label.setTypeface(has ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        });
        t.setOnClickListener(v -> launch(a.pkg));
        t.setOnLongClickListener(v -> { appMenu(a, section); return true; });
        if (section != null) tileIndex.put(section + "\n" + a.pkg, t);
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
            File cb = customBannerFile(pkg);
            if (cb != null) {
                Bitmap bm = BitmapFactory.decodeFile(cb.getAbsolutePath());
                if (bm != null) a.banner = new BitmapDrawable(getResources(), bm);
            }
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

    private void appMenu(App a, String section) {
        List<String> favs = store.favorites();
        boolean fav = favs.contains(a.pkg);
        List<String> opts = new ArrayList<>();
        if (section != null && !section.isEmpty()) opts.add("Move (use arrow keys)");
        opts.add(fav ? "Remove from Favorites" : "Add to Favorites");
        opts.add("Add to category…");
        for (String c : store.categories())
            if (store.categoryApps(c).contains(a.pkg)) opts.add("Remove from " + c);
        opts.add("Rename");
        opts.add("Custom banner");
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
                        case "Move (use arrow keys)":
                            moveSection = section;
                            movePkg = a.pkg;
                            pendingFocusKey = section + "\n" + a.pkg;
                            toast("Use the arrow keys to move " + a.label + ". Press OK when done.");
                            rebuild();
                            return;
                        case "Custom banner": customBannerDialog(a); return;
                        case "Add to category…": addToCategoryDialog(a); return;
                        case "Add to Favorites": f.add(a.pkg); store.setFavorites(f); break;
                        case "Remove from Favorites": f.remove(a.pkg); store.setFavorites(f); break;
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
        scroll.post(() -> glideTo(searchBox));
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
                toast("Turn on the Lumen service (⋯ → Home button setup) so the timer can turn the TV off");
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
        if (store.layout() == Store.LAYOUT_SHOWCASE) {
            boolean on = at != 0 && left > 0;
            sleepButton.setVisibility(on ? View.VISIBLE : View.GONE);
            if (on) sleepButton.setText("☾  Sleep in " + Math.max(1, (left + 59_999) / 60_000) + " min");
            return;
        }
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
            boolean compact = store.layout() == Store.LAYOUT_SHOWCASE;
            String kind = wifi ? (compact ? signalBars() : "Wi-Fi " + signalBars()) : eth ? "Ethernet" : "Network";
            wifiView.setText("●  " + kind + (ok ? "" : " (no internet)"));
            wifiView.setTextColor(wifiView.isFocused() ? 0xFF0D1B2A : ok ? 0xFF66BB6A : 0xFFFFCA28);
        }
    }

    // ------------------------------------------------------------------ ⋯ menu

    private void moreMenu() {
        openPanel();
    }

    // ---- wallpaper (with video and day/night schedule)

    private void wallpaperDialog() {
        boolean dn = store.dayNight();
        List<String> opts = new ArrayList<>();
        opts.add((dn ? "Day wallpaper: " : "Wallpaper: ") + WALLPAPER_NAMES[clamp(store.wallpaper(), WALLPAPER_NAMES.length)]);
        opts.add("Day/night switch: " + (dn ? "On (night is 7 PM – 7 AM)" : "Off"));
        if (dn) opts.add("Night wallpaper: " + WALLPAPER_NAMES[clamp(store.nightWallpaper(), WALLPAPER_NAMES.length)]);
        new AlertDialog.Builder(this).setTitle("Wallpaper")
                .setItems(opts.toArray(new String[0]), (d, w) -> {
                    if (w == 0) pickWallpaper(store.wallpaper(), i -> { store.setWallpaper(i); rebuild(); });
                    else if (w == 1) { store.setDayNight(!dn); rebuild(); wallpaperDialog(); }
                    else pickWallpaper(store.nightWallpaper(), i -> { store.setNightWallpaper(i); rebuild(); });
                }).show();
    }

    private interface IntResult { void on(int i); }

    private void pickWallpaper(int current, IntResult r) {
        new AlertDialog.Builder(this).setTitle("Choose wallpaper")
                .setSingleChoiceItems(WALLPAPER_NAMES, clamp(current, WALLPAPER_NAMES.length), (d, w) -> {
                    if (w == WP_PICTURE && !wallpaperFile().exists()) {
                        toast("Copy a picture to " + wallpaperFile().getAbsolutePath() + " first");
                        return;
                    }
                    if (w == WP_VIDEO && !videoFile().exists()) {
                        toast("Copy a short video to " + videoFile().getAbsolutePath() + " first");
                        return;
                    }
                    d.dismiss();
                    r.on(w);
                }).show();
    }

    private File wallpaperFile() {
        return new File(getExternalFilesDir(null), "wallpaper.jpg");
    }

    private File videoFile() {
        return new File(getExternalFilesDir(null), "wallpaper.mp4");
    }

    private int effectiveWallpaper() {
        if (!store.dayNight()) return store.wallpaper();
        int h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        return (h >= 7 && h < 19) ? store.wallpaper() : store.nightWallpaper();
    }

    /** Sets the background; for a video wallpaper adds a muted, looping video behind everything. */
    private void applyWallpaper() {
        int w = effectiveWallpaper();
        appliedWallpaper = w;
        if (w == WP_VIDEO && videoFile().exists()) {
            root.setBackgroundColor(Color.BLACK);
            android.widget.VideoView vv = new android.widget.VideoView(this);
            vv.setAudioFocusRequest(android.media.AudioManager.AUDIOFOCUS_NONE); // never pause your music
            vv.setVideoURI(Uri.fromFile(videoFile()));
            vv.setOnPreparedListener(mp -> { mp.setLooping(true); mp.setVolume(0f, 0f); vv.start(); });
            vv.setOnErrorListener((mp, what, extra) -> true);
            vv.setFocusable(false);
            root.addView(vv, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
            View dim = new View(this);
            dim.setBackgroundColor(0x66000000); // keeps text readable over bright videos
            root.addView(dim, new FrameLayout.LayoutParams(-1, -1));
            videoView = vv;
            return;
        }
        root.setBackground(wallpaperDrawable(w));
    }

    private Drawable wallpaperDrawable(int w) {
        if (w >= WP_ART && w < WP_ART + ArtWallpaper.NAMES.length) return new ArtWallpaper(w - WP_ART);
        if (w == WP_PICTURE) {
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

    // ---- floating status bar

    private void setBarShown(boolean show) {
        if (barWrap == null || show == barShown) return;
        barShown = show;
        barWrap.animate().translationY(show ? 0 : -barWrap.getHeight()).alpha(show ? 1f : 0f)
                .setDuration(250).start();
    }

    private static boolean isInside(View v, View parent) {
        for (android.view.ViewParent p = v.getParent(); p != null; p = p.getParent()) {
            if (p == parent) return true;
        }
        return v == parent;
    }

    private static int clamp(int i, int size) {
        return i < 0 || i >= size ? 0 : i;
    }

    // ---- smooth focus

    /** Glides the page so the focused row sits comfortably below the status bar. */
    private void glideTo(View v) {
        if (scroll == null || content == null || moveSection != null && pendingFocusKey != null) return;
        if (dock != null && isInside(v, dock)) { scroll.smoothScrollTo(0, 0); return; }
        android.graphics.Rect r = new android.graphics.Rect();
        v.getDrawingRect(r);
        try {
            content.offsetDescendantRectToMyCoords(v, r);
        } catch (IllegalArgumentException e) {
            return;
        }
        int target = Math.max(0, r.top - dp(150));
        if (r.top < dp(260)) target = 0; // first rows: keep the status bar in view
        scroll.smoothScrollTo(0, target);
    }

    /** Pressing left/right at the end of a row stays put instead of jumping to another row. */
    private void lockRowEnds(ViewGroup row) {
        int n = row.getChildCount();
        if (n == 0) return;
        View first = row.getChildAt(0), last = row.getChildAt(n - 1);
        if (first.getId() == View.NO_ID) first.setId(View.generateViewId());
        if (last.getId() == View.NO_ID) last.setId(View.generateViewId());
        first.setNextFocusLeftId(first.getId());
        last.setNextFocusRightId(last.getId());
    }

    private void lockGridEdges(GridLayout g) {
        int n = g.getChildCount(), cols = g.getColumnCount();
        for (int i = 0; i < n; i++) {
            View c = g.getChildAt(i);
            if (c.getId() == View.NO_ID) c.setId(View.generateViewId());
            if (i % cols == 0) c.setNextFocusLeftId(c.getId());
            if (i % cols == cols - 1 || i == n - 1) c.setNextFocusRightId(c.getId());
        }
    }

    // ---- navigation sounds

    private void navSound(int key) {
        if (!store.sounds()) return;
        int fx;
        switch (key) {
            case KeyEvent.KEYCODE_DPAD_UP: fx = android.media.AudioManager.FX_FOCUS_NAVIGATION_UP; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: fx = android.media.AudioManager.FX_FOCUS_NAVIGATION_DOWN; break;
            case KeyEvent.KEYCODE_DPAD_LEFT: fx = android.media.AudioManager.FX_FOCUS_NAVIGATION_LEFT; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: fx = android.media.AudioManager.FX_FOCUS_NAVIGATION_RIGHT; break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER: fx = android.media.AudioManager.FX_KEY_CLICK; break;
            default: return;
        }
        try {
            getSystemService(android.media.AudioManager.class).playSoundEffect(fx, 0.4f);
        } catch (Exception ignored) { }
    }

    // ---- network details

    private String signalBars() {
        int level = wifiLevel();
        String[] bars = {"▁", "▁▃", "▁▃▅", "▁▃▅▇"};
        return level < 0 ? "" : bars[Math.min(3, level)];
    }

    /** 0..3, or -1 when unknown. */
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

    private void networkDialog() {
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        StringBuilder sb = new StringBuilder();
        try {
            Network n = cm.getActiveNetwork();
            NetworkCapabilities c = n == null ? null : cm.getNetworkCapabilities(n);
            if (c == null) {
                sb.append("Status: Offline\n");
            } else {
                boolean wifi = c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
                boolean ok = c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
                sb.append("Connection: ").append(wifi ? "Wi-Fi" : c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ? "Ethernet" : "Other").append('\n');
                sb.append("Internet: ").append(ok ? "Working" : "Not reachable").append('\n');
                if (wifi) {
                    android.net.wifi.WifiManager wm = getApplicationContext().getSystemService(android.net.wifi.WifiManager.class);
                    android.net.wifi.WifiInfo info = wm.getConnectionInfo();
                    if (info != null) {
                        int pct = Math.max(0, Math.min(100, 2 * (info.getRssi() + 100)));
                        String[] quality = {"Weak", "Fair", "Good", "Excellent"};
                        int lvl = wifiLevel();
                        sb.append("Signal: ").append(lvl >= 0 ? quality[Math.min(3, lvl)] : "Unknown")
                                .append(" (").append(pct).append("%)\n");
                        sb.append("Link speed: ").append(info.getLinkSpeed()).append(" Mbps\n");
                        int f = info.getFrequency();
                        if (f > 0) sb.append("Band: ").append(f >= 5900 ? "6 GHz" : f >= 4900 ? "5 GHz" : "2.4 GHz").append('\n');
                    }
                }
                android.net.LinkProperties lp = cm.getLinkProperties(n);
                if (lp != null) {
                    for (android.net.LinkAddress la : lp.getLinkAddresses()) {
                        if (la.getAddress() instanceof java.net.Inet4Address) {
                            sb.append("TV address: ").append(la.getAddress().getHostAddress()).append('\n');
                        }
                    }
                }
            }
        } catch (Exception e) {
            sb.append("Couldn't read network details\n");
        }
        if (dataView != null && dataView.getVisibility() == View.VISIBLE) {
            sb.append("Used today: ").append(dataView.getText().toString().replace("↕ ", "").replace(" today", "")).append('\n');
        }
        new AlertDialog.Builder(this).setTitle("Network")
                .setMessage(sb.toString().trim())
                .setPositiveButton("Wi-Fi settings", (d, w) -> safeStart(new Intent(Settings.ACTION_WIFI_SETTINGS)))
                .setNeutralButton("Network settings", (d, w) -> {
                    Intent i = new Intent(Settings.ACTION_WIRELESS_SETTINGS);
                    if (pm.resolveActivity(i, 0) == null) i = new Intent(Settings.ACTION_SETTINGS);
                    safeStart(i);
                })
                .setNegativeButton("Close", null)
                .show();
    }

    // ---- arrow-key move mode

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) navSound(e.getKeyCode());
        if (e.getKeyCode() == KeyEvent.KEYCODE_MENU) {
            if (e.getAction() == KeyEvent.ACTION_UP) { if (panel == null) openPanel(); else closePanel(true); }
            return true;
        }
        if (moveSection == null) {
            if (panel != null) return super.dispatchKeyEvent(e);
            // Up from the top row: bring back the (possibly hidden) status bar.
            if (e.getAction() == KeyEvent.ACTION_DOWN && e.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP
                    && barWrap != null && barFocus != null) {
                View f = getCurrentFocus();
                if (f != null && !isInside(f, barWrap)) {
                    View next = f.focusSearch(View.FOCUS_UP);
                    if (next == null || isInside(next, barWrap) || !next.isShown()) {
                        setBarShown(true);
                        scroll.smoothScrollTo(0, 0);
                        barFocus.requestFocus();
                        return true;
                    }
                }
            }
            return super.dispatchKeyEvent(e);
        }
        int k = e.getKeyCode();
        boolean grid = moveSection.startsWith("cat:") && store.categoryGrid(moveSection.substring(4));
        int delta = 0;
        switch (k) {
            case KeyEvent.KEYCODE_DPAD_LEFT: delta = -1; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: delta = 1; break;
            case KeyEvent.KEYCODE_DPAD_UP: delta = grid ? -columns() : 0; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: delta = grid ? columns() : 0; break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_BACK:
                if (e.getAction() == KeyEvent.ACTION_UP) {
                    pendingFocusKey = moveSection + "\n" + movePkg;
                    moveSection = null;
                    movePkg = null;
                    toast("Saved");
                    rebuild();
                }
                return true;
            default:
                return true; // ignore other keys while moving
        }
        if (e.getAction() != KeyEvent.ACTION_DOWN || delta == 0) return true;

        List<String> stored = moveSection.equals("fav") ? store.favorites()
                : store.categoryApps(moveSection.substring(4));
        List<String> visible = new ArrayList<>();
        for (String p : stored) if (apps.containsKey(p)) visible.add(p);
        int from = visible.indexOf(movePkg);
        int to = Math.max(0, Math.min(visible.size() - 1, from + delta));
        if (from < 0 || to == from) return true;
        visible.remove(from);
        visible.add(to, movePkg);
        for (String p : stored) if (!visible.contains(p)) visible.add(p); // keep uninstalled ones
        if (moveSection.equals("fav")) store.setFavorites(visible);
        else store.setCategoryApps(moveSection.substring(4), visible);
        pendingFocusKey = moveSection + "\n" + movePkg;
        rebuild();
        return true;
    }

    // ---- custom banners

    private File customBannerFile(String pkg) {
        File dir = getExternalFilesDir("banners");
        if (dir == null) return null;
        for (String ext : new String[]{".png", ".jpg", ".jpeg", ".webp"}) {
            File f = new File(dir, pkg + ext);
            if (f.exists()) return f;
        }
        return null;
    }

    private void customBannerDialog(App a) {
        File dir = getExternalFilesDir("banners");
        File existing = customBannerFile(a.pkg);
        AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle("Custom banner for " + a.label)
                .setMessage("Copy a 16:9 picture (e.g. 320×180) named\n\n" + a.pkg + ".png\n\ninto\n\n"
                        + (dir != null ? dir.getAbsolutePath() : "(storage unavailable)")
                        + "\n\nIt replaces the app's banner on this home screen.")
                .setNegativeButton("Close", null);
        if (existing != null) {
            b.setPositiveButton("Remove custom banner", (d, w) -> {
                if (existing.delete()) toast("Custom banner removed");
                rebuild();
            });
        }
        b.show();
    }

    // ---- clock format, focus border

    private void clockFormatDialog() {
        new AlertDialog.Builder(this).setTitle("Time format")
                .setSingleChoiceItems(TIME_NAMES, clamp(store.timeFormat(), TIME_NAMES.length), (d, w) -> {
                    store.setTimeFormat(w);
                    d.dismiss();
                    String[] examples = new String[DATE_FORMATS.length];
                    java.util.Date now = new java.util.Date();
                    for (int i = 0; i < examples.length; i++) {
                        examples[i] = new java.text.SimpleDateFormat(DATE_FORMATS[i], Locale.getDefault()).format(now);
                    }
                    new AlertDialog.Builder(this).setTitle("Date format")
                            .setSingleChoiceItems(examples, clamp(store.dateFormat(), examples.length), (d2, w2) -> {
                                store.setDateFormat(w2);
                                d2.dismiss();
                                rebuild();
                            }).show();
                }).show();
    }

    private void borderDialog() {
        new AlertDialog.Builder(this).setTitle("Focus border")
                .setSingleChoiceItems(BORDER_NAMES, clamp(store.border(), BORDER_NAMES.length), (d, w) -> {
                    store.setBorder(w); d.dismiss(); rebuild();
                }).show();
    }

    // ---- data usage (today, read from the TV's own counters; no internet needed)

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
        if (dataView == null) return;
        if (!store.showDataUsage() || !hasUsageAccess()) { dataView.setVisibility(View.GONE); return; }
        final TextView target = dataView;
        new Thread(() -> {
            long total = 0;
            try {
                android.app.usage.NetworkStatsManager nsm =
                        getSystemService(android.app.usage.NetworkStatsManager.class);
                java.util.Calendar c = java.util.Calendar.getInstance();
                c.set(java.util.Calendar.HOUR_OF_DAY, 0);
                c.set(java.util.Calendar.MINUTE, 0);
                c.set(java.util.Calendar.SECOND, 0);
                long start = c.getTimeInMillis(), now = System.currentTimeMillis();
                for (int type : new int[]{ConnectivityManager.TYPE_WIFI, ConnectivityManager.TYPE_ETHERNET}) {
                    try {
                        android.app.usage.NetworkStats.Bucket b = nsm.querySummaryForDevice(type, null, start, now);
                        if (b != null) total += b.getRxBytes() + b.getTxBytes();
                    } catch (Exception ignored) { }
                }
            } catch (Exception ignored) { }
            final String text = "↕ " + humanBytes(total) + " today";
            runOnUiThread(() -> { target.setText(text); target.setVisibility(View.VISIBLE); });
        }).start();
    }

    private static String humanBytes(long b) {
        if (b >= 1L << 30) return String.format(Locale.US, "%.1f GB", b / (double) (1L << 30));
        if (b >= 1L << 20) return String.format(Locale.US, "%.0f MB", b / (double) (1L << 20));
        return String.format(Locale.US, "%.0f KB", b / 1024.0);
    }

    private void dataUsageDialog() {
        if (!hasUsageAccess()) {
            new AlertDialog.Builder(this).setTitle("Data usage")
                    .setMessage("To show how much internet the TV used today, turn on \"Usage access\" for "
                            + "Lumen.\n\nThis only reads the TV's own counters. Lumen still has no internet "
                            + "access.")
                    .setPositiveButton("Open Usage access settings", (d, w) -> {
                        Intent i = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
                        if (pm.resolveActivity(i, 0) == null) {
                            toast("Not available on this TV. From a PC run: adb shell appops set "
                                    + getPackageName() + " GET_USAGE_STATS allow");
                        } else {
                            safeStart(i);
                        }
                    })
                    .setNegativeButton("Close", null).show();
            return;
        }
        store.setShowDataUsage(!store.showDataUsage());
        toast(store.showDataUsage() ? "Data usage shown" : "Data usage hidden");
        updateDataUsage();
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
                        ? "The Home button is set to open Lumen. To go back to Google's home screen, "
                          + "turn off \"Lumen - Home button\" in Accessibility settings."
                        : "To make the remote's Home button open Lumen, turn on "
                          + "\"Lumen - Home button\" in Accessibility settings.\n\n"
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
        new AlertDialog.Builder(this).setTitle("Lumen 1.3")
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
                .setMessage("Lumen includes an OLED-safe clock screensaver: a dim clock on black "
                        + "that moves every 30 seconds to prevent burn-in.\n\n"
                        + "To use it, choose \"Lumen clock\" in the screensaver settings.")
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
        boolean grid = store.categoryGrid(cat);
        String[] opts = {"Rename", "Move up", "Move down", "Delete", grid ? "Show as a row" : "Show as a grid"};
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
                case 4: store.setCategoryGrid(cat, !grid); break;
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
                // Only local posters: Lumen has no internet permission.
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
        int bw = BORDER_DP[clamp(store.border(), BORDER_DP.length)];
        body.setStroke(focused ? dp(bw) : dp(1), focused ? 0xFFFFFFFF : 0x40FFFFFF);

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
        int in = glowPad + dp(bw);
        ld.setLayerInset(3, in, in, in, in);
        return ld;
    }

    /** Arc-style focus: a clean white border hugging the artwork, with a thin dark inner ring. */
    private Drawable minimalFrame(int radiusDp) {
        int bw = BORDER_DP[clamp(store.border(), BORDER_DP.length)];
        GradientDrawable outer = new GradientDrawable();
        outer.setCornerRadius(dp(radiusDp));
        outer.setColor(0);
        outer.setStroke(dp(bw), Color.WHITE);
        GradientDrawable inner = new GradientDrawable();
        inner.setCornerRadius(Math.max(0, dp(radiusDp) - dp(bw)));
        inner.setColor(0);
        inner.setStroke(dp(1), 0x80000000);
        LayerDrawable ld = new LayerDrawable(new Drawable[]{outer, inner});
        int in = dp(9) - dp(bw) - dp(1);
        ld.setLayerInset(0, in, in, in, in);
        ld.setLayerInset(1, in + dp(bw), in + dp(bw), in + dp(bw), in + dp(bw));
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
