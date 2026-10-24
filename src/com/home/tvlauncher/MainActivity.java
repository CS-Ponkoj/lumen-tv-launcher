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
    private static final String[] STYLE_NAMES = {"Modern (banners)", "Premium (large banners)", "Capsule (round icons)",
            "Mono (soft logos in a bubble)", "Compact (small dark tiles)", "Square (app icons with names)"};
    private static final int ACCENT = 0xFF42A5F5;

    private static final String[] DEFAULT_FAVORITES = {
            "com.netflix.ninja", "com.google.android.youtube.tv", "com.disney.disneyplus",
            "com.amazon.amazonvideo.livingroom", "com.wbd.stream", "com.hulu.livingroomplus"};

    private static final String[] WALLPAPER_NAMES = {
            "Midnight", "Deep blue", "Charcoal", "Forest", "Plum", "Black",
            "My picture (wallpaper.jpg)", "My video (wallpaper.mp4)",
            "Aurora (art)", "Sunset hills (art)", "Misty mountains (art)", "Moonlit sea (art)",
            "Slideshow (art + your pictures)", "Foggy forest (photo)", "Winter snowfall (animated)",
            "Above the clouds (art)"};
    private static final int WP_ART = 8, WP_SLIDESHOW = 12, WP_FOREST = 13, WP_SNOW = 14, WP_HORIZON = 15;
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

    /** A theme sets the whole look at once; anything can still be changed afterwards. */
    private static final class Theme {
        final String name; final int wallpaper, accent, card, focus, border, glassTint, panelColor;
        int header, dock, timeFormat = -1;
        Theme(String name, int wallpaper, int accent, int card, int focus, int border, int glassTint, int panelColor) {
            this.name = name; this.wallpaper = wallpaper; this.accent = accent; this.card = card;
            this.focus = focus; this.border = border; this.glassTint = glassTint; this.panelColor = panelColor;
        }
        Theme header(int h) { header = h; return this; }
        Theme dock(int d) { dock = d; return this; }
        Theme time(int t) { timeFormat = t; return this; }
    }
    private static final Theme[] THEMES = {
            new Theme("Northern Lights", 8, 0, Store.STYLE_MODERN, Store.FOCUS_GLASS, 1, 0x26FFFFFF, 0xF2181B22),
            new Theme("Golden Dusk", 9, 3, Store.STYLE_MODERN, Store.FOCUS_GLASS, 1, 0x33FFC890, 0xF2261420),
            new Theme("Moonlit Sea", 11, 5, Store.STYLE_PREMIUM, Store.FOCUS_MINIMAL, 1, 0x26B8CCFF, 0xF20E1626),
            new Theme("Misty Peaks", 10, 2, Store.STYLE_MODERN, Store.FOCUS_MINIMAL, 1, 0x33FFFFFF, 0xF2172230),
            new Theme("Neon Night", 0, 4, Store.STYLE_CAPSULE, Store.FOCUS_GLASS, 2, 0x2EFF7AE0, 0xF2190E28),
            new Theme("Pure Black", 5, 5, Store.STYLE_MODERN, Store.FOCUS_MINIMAL, 0, 0x14FFFFFF, 0xFA000000),
            new Theme("Gallery", 12, 0, Store.STYLE_MODERN, Store.FOCUS_GLASS, 1, 0x26FFFFFF, 0xF2181B22),
            new Theme("Foggy Forest", 13, 5, Store.STYLE_MONO, Store.FOCUS_GLASS, 1, 0x5CFFFFFF, 0xF21C2624).header(1).time(1),
            new Theme("Winter Snowfall", WP_SNOW, 5, Store.STYLE_COMPACT, Store.FOCUS_MINIMAL, 1, 0x26FFFFFF, 0xF2201A22)
                    .header(2).dock(1).time(2),
            new Theme("Above the Clouds", WP_HORIZON, 5, Store.STYLE_SQUARE, Store.FOCUS_MINIMAL, 1, 0x26FFFFFF, 0xF2121820)
                    .header(3).dock(1).time(0)};
    private boolean applyingTheme;

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
            if (greetingView != null) greetingView.setText(greeting());
            updateStatusLine();
            showWeather();
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
        handler.removeCallbacks(slideTick);
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
        if (panel != null) {
            if (panelOnSubPage) showMainPage(); else closePanel(true);
            return;
        }
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
        int hdr = store.headerStyle();
        View barView = !showcase ? buildTopBar() : hdr == 1 ? buildGreetingBar() : hdr == 2 ? buildPillBar()
                : hdr == 3 ? buildCenterClockBar() : buildMinimalBar();
        bw.addView(barView, new LinearLayout.LayoutParams(-1, -2));
        if (showcase && hdr != 3) bw.setPadding(dp(64), dp(40), dp(56), dp(16));
        if (showcase && hdr == 3) {
            bw.setPadding(dp(48), dp(28), dp(56), dp(16));
            bw.setBackground(null);
        }
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
            boolean plain = store.dockStyle() == 1;
            GlassPanel d = new GlassPanel(this, plain ? null : blurredWallpaper(), dp(28));
            d.setPlain(plain);
            d.setGlassTint(store.glassTint());
            d.setClipChildren(false);
            d.setClipToPadding(false);
            d.setPadding(dp(14), dp(2), dp(14), dp(0));
            View dockRow = tileRow(dockApps, favs.isEmpty() ? null : "fav", true);
            if (plain) {
                d.setPadding(0, 0, 0, 0);
                ViewGroup rg = (ViewGroup) ((HorizontalScrollView) dockRow).getChildAt(0);
                rg.setPadding(dp(4), dp(6), dp(4), dp(6));
            } else if (store.cardStyle() == Store.STYLE_MONO) {
                // Slim dock: logos spaced out, little vertical padding.
                d.setPadding(dp(24), dp(4), dp(24), dp(4));
                ViewGroup rowGroup = (ViewGroup) ((HorizontalScrollView) dockRow).getChildAt(0);
                rowGroup.setPadding(dp(4), dp(6), dp(4), dp(6));
                for (int i = 0; i < rowGroup.getChildCount(); i++) {
                    LinearLayout.LayoutParams tl = (LinearLayout.LayoutParams) rowGroup.getChildAt(i).getLayoutParams();
                    tl.setMargins(dp(16), 0, dp(16), 0);
                }
            }
            d.addView(dockRow);
            content.addView(d, new LinearLayout.LayoutParams(-1, -2));
            dock = d;
            final ScrollView sc = scroll;
            sc.post(() -> {
                // Forest look floats the dock higher, with scenery visible underneath.
                int gap = store.headerStyle() == 1 ? dp(120) : store.dockStyle() == 1 ? dp(14) : dp(30);
                int h = sc.getHeight() - d.getHeight() - gap;
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
        content.addView(credit(), new LinearLayout.LayoutParams(-1, -2));

        updateWifi();
        updateSleepLabel();
        updateDataUsage();
        // (Re)start the slideshow timer with the current interval.
        handler.removeCallbacks(slideTick);
        handler.postDelayed(slideTick, Math.max(1, store.slideMinutes()) * 60_000L);
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

    private final Map<String, Drawable> monoCache = new HashMap<>();

    /**
     * Turns an app banner into a clean white logo on a transparent background:
     * the banner's background colour (sampled from its edges) becomes transparent,
     * everything that differs from it becomes white.
     */
    private Drawable monoLogo(App a) {
        Drawable cached = monoCache.get(a.pkg);
        if (cached != null) return cached;
        Drawable src = a.banner != null ? a.banner : a.icon;
        int bw = a.banner != null ? 320 : 144, bh = a.banner != null ? 180 : 144;
        Bitmap bm = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        src.setBounds(0, 0, bw, bh);
        src.draw(new android.graphics.Canvas(bm));
        int[] px = new int[bw * bh];
        bm.getPixels(px, 0, bw, 0, 0, bw, bh);
        long rs = 0, gs = 0, bs = 0; int n = 0;
        for (int x = 0; x < bw; x++) for (int y : new int[]{0, 1, bh - 2, bh - 1}) {
            int c = px[y * bw + x]; rs += (c >> 16) & 255; gs += (c >> 8) & 255; bs += c & 255; n++;
        }
        for (int y = 0; y < bh; y++) for (int x : new int[]{0, 1, bw - 2, bw - 1}) {
            int c = px[y * bw + x]; rs += (c >> 16) & 255; gs += (c >> 8) & 255; bs += c & 255; n++;
        }
        int br = (int) (rs / n), bgc = (int) (gs / n), bb = (int) (bs / n);
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            int srcA = (c >>> 24);
            int d = Math.abs(((c >> 16) & 255) - br) + Math.abs(((c >> 8) & 255) - bgc) + Math.abs((c & 255) - bb);
            int alpha = Math.max(0, Math.min(255, (d - 60) * 3));
            alpha = alpha * srcA / 255;
            px[i] = (alpha << 24) | 0xFFFFFF;
        }
        bm.setPixels(px, 0, bw, 0, 0, bw, bh);
        Drawable out = new BitmapDrawable(getResources(), bm);
        monoCache.put(a.pkg, out);
        return out;
    }

    private Bitmap forestBitmap;

    /** Misty forest photo (CC0, Jay Mantri via Pexels) with a cool, softly desaturated grade. */
    private Drawable forestDrawable() {
        if (forestBitmap == null) {
            try {
                Bitmap src = BitmapFactory.decodeResource(getResources(), R.drawable.forest);
                Bitmap out = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
                android.graphics.ColorMatrix cm = new android.graphics.ColorMatrix();
                cm.setSaturation(0.45f);
                cm.postConcat(new android.graphics.ColorMatrix(new float[]{
                        0.86f, 0, 0, 0, 0,
                        0, 0.92f, 0, 0, 4,
                        0, 0, 0.98f, 0, 14,
                        0, 0, 0, 1, 0}));
                android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG);
                paint.setColorFilter(new android.graphics.ColorMatrixColorFilter(cm));
                new android.graphics.Canvas(out).drawBitmap(src, 0, 0, paint);
                src.recycle();
                forestBitmap = out;
            } catch (Throwable t) {
                return new ArtWallpaper(4);
            }
        }
        return new BitmapDrawable(getResources(), forestBitmap);
    }

    private Bitmap snowBitmap;

    /** Same misty forest photo, graded dark and moody with a soft mauve haze for the snowfall theme. */
    private Drawable snowForestDrawable() {
        if (snowBitmap == null) {
            try {
                Bitmap src = BitmapFactory.decodeResource(getResources(), R.drawable.forest);
                Bitmap out = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
                android.graphics.ColorMatrix cm = new android.graphics.ColorMatrix();
                cm.setSaturation(0.15f);
                cm.postConcat(new android.graphics.ColorMatrix(new float[]{
                        0.64f, 0, 0, 0, 8,
                        0, 0.62f, 0, 0, 6,
                        0, 0, 0.68f, 0, 16,
                        0, 0, 0, 1, 0}));
                android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG);
                paint.setColorFilter(new android.graphics.ColorMatrixColorFilter(cm));
                android.graphics.Canvas cv = new android.graphics.Canvas(out);
                cv.drawBitmap(src, 0, 0, paint);
                // darker bottom so the app row reads well
                android.graphics.Paint shade = new android.graphics.Paint();
                shade.setShader(new android.graphics.LinearGradient(0, out.getHeight() * 0.55f, 0, out.getHeight(),
                        0x00000000, 0x8C000000, android.graphics.Shader.TileMode.CLAMP));
                cv.drawRect(0, 0, out.getWidth(), out.getHeight(), shade);
                src.recycle();
                snowBitmap = out;
            } catch (Throwable t) {
                return new ArtWallpaper(4);
            }
        }
        return new BitmapDrawable(getResources(), snowBitmap);
    }

    // ---- info block: clock + date, weather, TV status, greeting (used by every theme)

    private TextView greetingView, statusView, weatherTemp, weatherPlace;
    private ImageView weatherIconView;
    private LinearLayout weatherRow;
    private String dataToday;

    private String greeting() {
        int h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        return h < 5 ? "Good night" : h < 12 ? "Good morning" : h < 17 ? "Good afternoon" : h < 22 ? "Good evening" : "Good night";
    }

    private TextView infoText(float sp, int color) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setShadowLayer(dp(6), 0, dp(1), 0x73000000);
        return t;
    }

    /**
     * The top-left block from the reference look:
     * 19:23  Friday, 23 October / (weather) 15° City · Rain / Living room · Wi-Fi good · 98 MB today / Good evening
     */
    private LinearLayout infoBlock(boolean centered) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(centered ? Gravity.CENTER_HORIZONTAL : Gravity.START);

        String tf = TIME_FORMATS[clamp(store.timeFormat(), TIME_FORMATS.length)];
        if (centered) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.BOTTOM);
            TextClock time = new TextClock(this);
            boolean twelve = tf.startsWith("h");
            time.setFormat12Hour(twelve ? "h:mm" : "HH:mm");
            time.setFormat24Hour(twelve ? "h:mm" : "HH:mm");
            time.setTextColor(Color.WHITE);
            time.setTextSize(TypedValue.COMPLEX_UNIT_SP, 62);
            time.setShadowLayer(dp(8), 0, dp(1), 0x80000000);
            row.addView(time);
            if (tf.endsWith("a")) {
                TextClock ampm = new TextClock(this);
                ampm.setFormat12Hour("a");
                ampm.setFormat24Hour("a");
                ampm.setTextColor(Color.WHITE);
                ampm.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
                ampm.setSingleLine(true);
                ampm.setShadowLayer(dp(6), 0, dp(1), 0x80000000);
                LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(-2, -2);
                al.leftMargin = dp(10);
                al.bottomMargin = dp(16);
                row.addView(ampm, al);
            }
            col.addView(row, new LinearLayout.LayoutParams(-2, -2));
            TextClock date = new TextClock(this);
            date.setFormat12Hour("EEEE, d MMMM yyyy");
            date.setFormat24Hour("EEEE, d MMMM yyyy");
            date.setTextColor(0xE6FFFFFF);
            date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            date.setShadowLayer(dp(6), 0, dp(1), 0x80000000);
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-2, -2);
            dl.topMargin = dp(2);
            col.addView(date, dl);
        } else {
            LinearLayout timeRow = new LinearLayout(this);
            timeRow.setGravity(Gravity.BOTTOM);
            TextClock time = new TextClock(this);
            time.setFormat12Hour(tf);
            time.setFormat24Hour(tf);
            time.setTextColor(Color.WHITE);
            time.setTextSize(TypedValue.COMPLEX_UNIT_SP, 46);
            time.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
            time.setLetterSpacing(0.02f);
            time.setShadowLayer(dp(8), 0, dp(1), 0x66000000);
            timeRow.addView(time);
            TextClock date = new TextClock(this);
            String df = store.headerStyle() == 0 ? DATE_FORMATS[clamp(store.dateFormat(), DATE_FORMATS.length)] : "EEEE, d MMMM";
            date.setFormat12Hour(df);
            date.setFormat24Hour(df);
            date.setTextColor(0xF2FFFFFF);
            date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            date.setShadowLayer(dp(6), 0, dp(1), 0x66000000);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-2, -2);
            dlp.leftMargin = dp(20);
            dlp.bottomMargin = dp(9);
            timeRow.addView(date, dlp);
            col.addView(timeRow);
        }

        // weather (only when switched on)
        weatherRow = new LinearLayout(this);
        weatherRow.setGravity(Gravity.CENTER_VERTICAL);
        weatherIconView = new ImageView(this);
        weatherRow.addView(weatherIconView, new LinearLayout.LayoutParams(dp(30), dp(30)));
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
        greetingView.setText(greeting());
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(-2, -2);
        gl.topMargin = dp(4);
        col.addView(greetingView, gl);

        dataView = null;
        sleepButton = new TextView(this); // the sleep timer shows in the status line
        sleepButton.setVisibility(View.GONE);
        updateStatusLine();
        showWeather();
        return col;
    }

    /** "Living room · Wi-Fi good · 98 MB today · Sleep in 20 min" */
    private void updateStatusLine() {
        if (statusView == null) return;
        StringBuilder sb = new StringBuilder(store.roomName());
        String net = networkSummary();
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

    /** Shows the cached weather (if any) and refreshes it in the background when it is older than 30 min. */
    private void showWeather() {
        if (weatherRow == null) return;
        if (!store.weatherOn() || store.weatherTime() == 0) {
            weatherRow.setVisibility(View.GONE);
        } else {
            weatherIconView.setImageDrawable(new WeatherIcon(WeatherIcon.fromCode(store.weatherCode())));
            weatherTemp.setText(Math.round(store.weatherTemp()) + "°");
            weatherPlace.setText(store.weatherPlace() + " · " + WeatherIcon.describe(store.weatherCode()));
            weatherRow.setVisibility(View.VISIBLE);
        }
        if (store.weatherOn() && System.currentTimeMillis() - store.weatherTime() > 30 * 60_000L) fetchWeather();
    }

    private boolean fetchingWeather;

    /** Open-Meteo: free, no account or key. Only the city's coordinates are sent. */
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
                // offline or blocked: keep the last known weather
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

    // ---- headers (all built around the info block)

    /** Default header: info block on the left, settings / search / network icons on the right. */
    private View buildMinimalBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.TOP);
        bar.addView(infoBlock(false), new LinearLayout.LayoutParams(0, -2, 1f));
        addRightIcons(bar, false);
        return bar;
    }

    /** Foggy Forest header: info block + small outline icons. */
    private View buildGreetingBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.TOP);
        bar.addView(infoBlock(false), new LinearLayout.LayoutParams(0, -2, 1f));
        addRightIcons(bar, true);
        return bar;
    }

    /** Winter Snowfall header: info block + a soft pill with Wi-Fi and settings. */
    private View buildPillBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.TOP);
        bar.addView(infoBlock(false), new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout pill = new LinearLayout(this);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(dp(8), dp(2), dp(8), dp(2));
        GradientDrawable pb = new GradientDrawable();
        pb.setCornerRadius(dp(24));
        pb.setColor(0x59D9A9BE);
        pill.setBackground(pb);
        wifiView = new TextView(this);
        wifiView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        wifiView.setGravity(Gravity.CENTER);
        wifiView.setPadding(dp(10), dp(8), dp(10), dp(8));
        wifiView.setFocusable(true);
        wifiView.setClickable(true);
        wifiView.setOnFocusChangeListener((v, has) -> {
            v.setBackground(has ? glassPill(true) : null);
            if (has) wifiView.setTextColor(0xFF0D1B2A); else updateWifi();
        });
        wifiView.setOnClickListener(v -> networkDialog());
        pill.addView(wifiView);
        View gear = smallIcon(android.R.drawable.ic_menu_preferences, "Menu");
        ((LinearLayout.LayoutParams) gear.getLayoutParams()).leftMargin = dp(2);
        gear.setOnClickListener(v -> openPanel());
        pill.addView(gear);
        barFocus = gear;
        bar.addView(pill);
        return bar;
    }

    /** Above the Clouds header: the info block centred under a big clock. */
    private View buildCenterClockBar() {
        FrameLayout f = new FrameLayout(this);
        LinearLayout col = infoBlock(true);
        int top = (int) (getResources().getDisplayMetrics().heightPixels * 0.18f);
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER_HORIZONTAL);
        cl.topMargin = top;
        f.addView(col, cl);
        View gear = smallIcon(android.R.drawable.ic_menu_preferences, "Menu");
        gear.setAlpha(0.55f);
        gear.setOnFocusChangeListener((v, has) -> v.setAlpha(has ? 1f : 0.55f));
        gear.setOnClickListener(v -> openPanel());
        f.addView(gear, new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.END | Gravity.TOP));
        barFocus = gear;
        wifiView = null;
        return f;
    }

    private void addRightIcons(LinearLayout bar, boolean outline) {
        wifiView = null;
        View net = smallIcon(android.R.drawable.ic_menu_share, "Network");
        net.setOnClickListener(v -> networkDialog());
        bar.addView(net);
        View pics = smallIcon(android.R.drawable.ic_menu_gallery, "Wallpaper");
        pics.setOnClickListener(v -> {
            openPanel();
            panelOnSubPage = true;
            panelReturnLabel = "Wallpaper";
            wallpaperPage();
        });
        bar.addView(pics);
        View search = smallIcon(android.R.drawable.ic_menu_search, "Search");
        search.setOnClickListener(v -> openSearch());
        bar.addView(search);
        View menu = smallIcon(android.R.drawable.ic_menu_preferences, "Menu");
        menu.setOnClickListener(v -> openPanel());
        bar.addView(menu);
        barFocus = menu;
    }

    // ---- weather & room settings page

    private void weatherPage() {
        List<PanelItem> items = new ArrayList<>();
        items.add(PanelItem.header("Weather"));
        boolean on = store.weatherOn();
        items.add(PanelItem.choice("Show weather (uses internet)", on, () -> {
            if (!on && store.weatherCity().isEmpty()) { askCity(); return; }
            applyLive(() -> store.setWeatherOn(!on), this::weatherPage);
        }));
        PanelItem city = new PanelItem(0, "City: " + (store.weatherCity().isEmpty() ? "not set" : store.weatherCity()), this::askCity);
        city.stay = true;
        items.add(city);
        items.add(PanelItem.choice("Celsius (°C)", !store.weatherFahrenheit(),
                () -> applyLive(() -> { store.setWeatherFahrenheit(false); store.setWeather(0, 0, 0); }, this::weatherPage)));
        items.add(PanelItem.choice("Fahrenheit (°F)", store.weatherFahrenheit(),
                () -> applyLive(() -> { store.setWeatherFahrenheit(true); store.setWeather(0, 0, 0); }, this::weatherPage)));
        items.add(PanelItem.header("Status line"));
        PanelItem room = new PanelItem(0, "Room name: " + store.roomName(), () ->
                textDialog("Room name", store.roomName(), "e.g. Living room, Bedroom", s -> {
                    store.setRoomName(s.trim().isEmpty() ? "Living room" : s.trim());
                    applyLive(() -> { }, this::weatherPage);
                }));
        room.stay = true;
        items.add(room);
        items.add(PanelItem.header("Privacy"));
        PanelItem note = new PanelItem(0, "Only your city is sent to open-meteo.com", () -> { });
        note.stay = true;
        items.add(note);
        renderPanel("Weather & info", items, false);
    }

    private void askCity() {
        textDialog("Your city", store.weatherCity(), "e.g. Istanbul, London, Dhaka", s -> {
            String c = s.trim();
            applyLive(() -> {
                store.setWeatherCity(c);
                store.setWeatherLocation("", "", "");
                store.setWeather(0, 0, 0);
                store.setWeatherOn(!c.isEmpty());
            }, this::weatherPage);
        });
    }

    /** Small outline-style icon (Forest header). */
    private View smallIcon(int iconRes, String description) {
        View v = iconButton(iconRes, description);
        ImageView img = (ImageView) ((FrameLayout) v).getChildAt(0);
        img.setLayoutParams(new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
        img.setAlpha(0.92f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(44), dp(44));
        lp.leftMargin = dp(10);
        v.setLayoutParams(lp);
        return v;
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

    // ---- side panel with sub-pages (Arc-style; replaces the old pop-up menus)

    private View panel;
    private LinearLayout panelList;
    private ScrollView panelScroll;
    private boolean panelOnSubPage;
    private String panelReturnLabel;

    private static final class PanelItem {
        final int icon; final String label; final Runnable action;
        boolean checked; int swatch; boolean header; boolean stay; boolean focus;
        PanelItem(int icon, String label, Runnable action) { this.icon = icon; this.label = label; this.action = action; }
        static PanelItem header(String text) { PanelItem p = new PanelItem(0, text, null); p.header = true; return p; }
        /** A choice row: stays in the panel, shows a check when selected. */
        static PanelItem choice(String label, boolean checked, Runnable action) {
            PanelItem p = new PanelItem(0, label, action); p.checked = checked; p.stay = true; return p;
        }
        PanelItem swatch(int color) { swatch = color; return this; }
    }

    private void openPanel() {
        if (panel == null) buildPanelFrame();
        showMainPage();
    }

    private void buildPanelFrame() {
        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(0x99000000);
        overlay.setClickable(true);

        panelScroll = new ScrollView(this);
        panelScroll.setVerticalScrollBarEnabled(false);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(store.panelColor());
        float r = dp(28);
        bg.setCornerRadii(new float[]{0, 0, r, r, r, r, 0, 0});
        panelScroll.setBackground(bg);
        panelList = new LinearLayout(this);
        panelList.setOrientation(LinearLayout.VERTICAL);
        panelList.setPadding(dp(18), dp(28), dp(18), dp(28));
        panelScroll.addView(panelList);

        overlay.addView(panelScroll, new FrameLayout.LayoutParams(dp(380), -1, Gravity.START));
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        panel = overlay;
        scroll.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        if (barWrap instanceof ViewGroup) ((ViewGroup) barWrap).setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        panelScroll.setTranslationX(-dp(380));
        panelScroll.animate().translationX(0).setDuration(220).start();
        overlay.setAlpha(0f);
        overlay.animate().alpha(1f).setDuration(220).start();
    }

    private void showMainPage() {
        panelOnSubPage = false;
        List<PanelItem> items = new ArrayList<>();
        items.add(PanelItem.header("Quick"));
        items.add(new PanelItem(android.R.drawable.ic_menu_search, "Search apps", this::openSearch));
        items.add(sub(android.R.drawable.ic_lock_idle_alarm, sleepLabel(), this::sleepPage));
        items.add(new PanelItem(android.R.drawable.ic_media_play, "Play Store", () -> launch("com.android.vending")));
        items.add(new PanelItem(android.R.drawable.ic_menu_slideshow, "HDMI inputs", this::inputsDialog));
        items.add(new PanelItem(android.R.drawable.ic_menu_manage, "TV settings",
                () -> safeStart(new Intent(Settings.ACTION_SETTINGS))));
        items.add(PanelItem.header("Look"));
        items.add(sub(android.R.drawable.ic_menu_gallery, "Theme: " + themeName(), this::themePage));
        items.add(sub(android.R.drawable.ic_menu_view, "Layout", this::layoutPage));
        items.add(sub(android.R.drawable.ic_menu_gallery, "Wallpaper", this::wallpaperPage));
        items.add(sub(android.R.drawable.ic_menu_crop, "Card style", this::cardStylePage));
        items.add(sub(android.R.drawable.ic_menu_edit, "Accent colour", this::accentPage));
        items.add(sub(android.R.drawable.ic_menu_zoom, "Focus style", this::focusPage));
        items.add(sub(android.R.drawable.ic_menu_recent_history, "Clock & date", this::clockPage));
        items.add(PanelItem.header("Home screen"));
        items.add(new PanelItem(android.R.drawable.ic_menu_sort_by_size, "Categories", this::categoriesDialog));
        items.add(new PanelItem(android.R.drawable.ic_menu_close_clear_cancel, "Hidden apps", this::hiddenDialog));
        items.add(new PanelItem(android.R.drawable.ic_menu_edit, "Rename inputs", this::renameInputsDialog));
        items.add(sub(android.R.drawable.ic_menu_day, "Weather & info", this::weatherPage));
        items.add(new PanelItem(android.R.drawable.ic_menu_upload, "Data usage", this::dataUsageDialog));
        PanelItem snd = new PanelItem(android.R.drawable.ic_lock_silent_mode_off,
                "Navigation sounds: " + (store.sounds() ? "On" : "Off"), () -> {
                    store.setSounds(!store.sounds());
                    showMainPage();
                });
        snd.stay = true;
        items.add(snd);
        items.add(PanelItem.header("System"));
        items.add(new PanelItem(android.R.drawable.ic_menu_slideshow, "Screensaver", this::screensaverDialog));
        items.add(new PanelItem(android.R.drawable.ic_menu_revert, "Home button setup", this::homeSetupDialog));
        items.add(new PanelItem(android.R.drawable.ic_menu_mapmode, "Open Google TV home", this::openGoogleHome));
        items.add(new PanelItem(android.R.drawable.ic_menu_info_details, "About Lumen", this::aboutDialog));
        renderPanel("Lumen", items, true);
    }

    private PanelItem sub(int icon, String label, Runnable page) {
        PanelItem p = new PanelItem(icon, label + "  ›", () -> {
            panelOnSubPage = true;
            panelReturnLabel = label;
            page.run();
        });
        p.focus = label.equals(panelReturnLabel) && !panelOnSubPage;
        p.stay = true;
        return p;
    }

    private void renderPanel(String title, List<PanelItem> items, boolean main) {
        panelList.removeAllViews();
        TextView t = new TextView(this);
        t.setText(main ? title : "‹  " + title);
        t.setTextColor(Color.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, main ? 26 : 22);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setPadding(dp(14), 0, 0, dp(8));
        panelList.addView(t);
        View focusRow = null, firstRow = null;
        for (PanelItem it : items) {
            if (it.header) {
                TextView h = new TextView(this);
                h.setText(it.label.toUpperCase(Locale.ROOT));
                h.setTextColor(0x80FFFFFF);
                h.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                h.setLetterSpacing(0.12f);
                h.setPadding(dp(14), dp(18), 0, dp(6));
                panelList.addView(h);
                continue;
            }
            View row = panelRow(it);
            if (firstRow == null) firstRow = row;
            if ((it.checked || it.focus) && focusRow == null) focusRow = row;
            panelList.addView(row);
        }
        if (main) {
            TextView by = credit();
            by.setGravity(Gravity.START);
            by.setPadding(dp(14), dp(24), 0, 0);
            panelList.addView(by);
        } else {
            TextView hint = new TextView(this);
            hint.setText("Press Back to return");
            hint.setTextColor(0x66FFFFFF);
            hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            hint.setPadding(dp(14), dp(20), 0, 0);
            panelList.addView(hint);
        }
        View target = focusRow != null ? focusRow : firstRow;
        panelScroll.scrollTo(0, 0);
        if (target != null) {
            final View tv = target;
            panelScroll.post(() -> {
                tv.requestFocus();
                panelScroll.smoothScrollTo(0, Math.max(0, tv.getTop() - dp(200)));
            });
        }
    }

    private View panelRow(PanelItem it) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setClickable(true);
        row.setPadding(dp(14), dp(11), dp(14), dp(11));
        if (it.icon != 0) {
            ImageView icon = new ImageView(this);
            icon.setImageResource(it.icon);
            icon.setColorFilter(0xE6FFFFFF);
            row.addView(icon, new LinearLayout.LayoutParams(dp(26), dp(26)));
        } else if (it.swatch != 0) {
            View sw = new View(this);
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(it.swatch);
            dot.setStroke(dp(1), 0x66FFFFFF);
            sw.setBackground(dot);
            row.addView(sw, new LinearLayout.LayoutParams(dp(24), dp(24)));
        }
        TextView label = new TextView(this);
        label.setText(it.label);
        label.setTextColor(0xE6FFFFFF);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(0, -2, 1f);
        llp.leftMargin = (it.icon != 0 || it.swatch != 0) ? dp(18) : 0;
        row.addView(label, llp);
        if (it.checked) {
            TextView check = new TextView(this);
            check.setText("✓");
            check.setTextColor(accentColor());
            check.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            row.addView(check);
        }
        row.setOnFocusChangeListener((v, has) -> {
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(14));
            g.setColor(has ? 0x1FFFFFFF : 0);
            if (has) g.setStroke(dp(2), Color.WHITE);
            v.setBackground(g);
            label.setTextColor(has ? Color.WHITE : 0xE6FFFFFF);
        });
        row.setOnClickListener(v -> {
            if (!it.stay) closePanel(false);
            it.action.run();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(2);
        row.setLayoutParams(lp);
        return row;
    }

    private void closePanel(boolean restoreFocus) {
        if (panel == null) return;
        panelReturnLabel = null;
        View p = panel;
        panel = null;
        panelOnSubPage = false;
        root.removeView(p);
        scroll.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        if (barWrap instanceof ViewGroup) ((ViewGroup) barWrap).setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        if (restoreFocus && barFocus != null) barFocus.requestFocus();
    }

    /** Apply a change and refresh the home screen behind the panel, keeping the panel on this page. */
    private void applyLive(Runnable change, Runnable page) {
        change.run();
        rebuild();          // removes the panel along with everything else
        buildPanelFrame();  // put it straight back, without the slide-in animation
        panelScroll.animate().cancel();
        panelScroll.setTranslationX(0);
        panel.animate().cancel();
        panel.setAlpha(1f);
        panelOnSubPage = true;
        page.run();
    }

    // ---- sub-pages

    private String themeName() {
        int t = store.theme();
        return t >= 0 && t < THEMES.length ? THEMES[t].name : "Custom";
    }

    private void themePage() {
        List<PanelItem> items = new ArrayList<>();
        for (int i = 0; i < THEMES.length; i++) {
            final int v = i;
            Theme th = THEMES[i];
            items.add(PanelItem.choice(th.name, store.theme() == i, () -> {
                applyingTheme = true;
                try {
                    applyLive(() -> applyTheme(v), this::themePage);
                } finally {
                    applyingTheme = false;
                }
            }).swatch(ACCENT_COLORS[th.accent]));
        }
        if (store.theme() < 0) items.add(0, PanelItem.choice("Custom (your own changes)", true, () -> { }));
        items.add(PanelItem.header("Tip"));
        PanelItem tip = new PanelItem(0, "You can still change the wallpaper and more after picking", () -> { });
        tip.stay = true;
        items.add(tip);
        renderPanel("Theme", items, false);
    }

    private void applyTheme(int i) {
        Theme th = THEMES[i];
        store.setTheme(i);
        store.setWallpaper(th.wallpaper);
        store.setDayNight(false);
        store.setAccent(th.accent);
        store.setCardStyle(th.card);
        store.setFocusStyle(th.focus);
        store.setBorder(th.border);
        store.setGlassTint(th.glassTint);
        store.setPanelColor(th.panelColor);
        store.setHeaderStyle(th.header);
        store.setDockStyle(th.dock);
        if (th.timeFormat >= 0) store.setTimeFormat(th.timeFormat);
        store.setLayout(Store.LAYOUT_SHOWCASE);
    }

    private void layoutPage() {
        List<PanelItem> items = new ArrayList<>();
        String[] names = {"Showcase (wallpaper + glass dock)", "Classic (rows)"};
        for (int i = 0; i < names.length; i++) {
            final int v = i;
            items.add(PanelItem.choice(names[i], store.layout() == i, () -> applyLive(() -> store.setLayout(v), this::layoutPage)));
        }
        renderPanel("Layout", items, false);
    }

    private void wallpaperPage() {
        List<PanelItem> items = new ArrayList<>();
        boolean dn = store.dayNight();
        items.add(PanelItem.header(dn ? "Daytime wallpaper" : "Wallpaper"));
        addWallpaperChoices(items, store.wallpaper(), i -> store.setWallpaper(i), this::wallpaperPage);
        items.add(PanelItem.header("Options"));
        items.add(PanelItem.choice("Day/night switch (night 7 PM – 7 AM)", dn,
                () -> applyLive(() -> store.setDayNight(!dn), this::wallpaperPage)));
        if (dn) items.add(sub(0, "Night wallpaper: " + WALLPAPER_NAMES[clamp(store.nightWallpaper(), WALLPAPER_NAMES.length)], this::nightWallpaperPage));
        if (store.wallpaper() == WP_SLIDESHOW || dn && store.nightWallpaper() == WP_SLIDESHOW) {
            items.add(sub(0, "Slideshow: change every " + store.slideMinutes() + " min", this::slideIntervalPage));
        }
        renderPanel("Wallpaper", items, false);
    }

    private void nightWallpaperPage() {
        List<PanelItem> items = new ArrayList<>();
        addWallpaperChoices(items, store.nightWallpaper(), i -> store.setNightWallpaper(i), this::nightWallpaperPage);
        renderPanel("Night wallpaper", items, false);
    }

    private void addWallpaperChoices(List<PanelItem> items, int current, IntResult set, Runnable page) {
        int[] order = {WP_SLIDESHOW, WP_FOREST, WP_SNOW, WP_HORIZON, WP_ART, WP_ART + 1, WP_ART + 2, WP_ART + 3, 0, 1, 2, 3, 4, 5, WP_PICTURE, WP_VIDEO};
        for (int w : order) {
            PanelItem it = PanelItem.choice(WALLPAPER_NAMES[w], current == w, () -> {
                if (w == WP_PICTURE && !wallpaperFile().exists()) {
                    toast("Copy a picture to " + wallpaperFile().getAbsolutePath() + " first");
                    return;
                }
                if (w == WP_VIDEO && !videoFile().exists()) {
                    toast("Copy a short video to " + videoFile().getAbsolutePath() + " first");
                    return;
                }
                applyLive(() -> { set.on(w); store.setTheme(-1); }, page);
            });
            if (w < WALLPAPER_COLORS.length) it.swatch(WALLPAPER_COLORS[w][2]);
            items.add(it);
        }
    }

    private void slideIntervalPage() {
        List<PanelItem> items = new ArrayList<>();
        for (int m : new int[]{1, 5, 15, 30, 60}) {
            items.add(PanelItem.choice("Every " + m + (m == 1 ? " minute" : " minutes"), store.slideMinutes() == m,
                    () -> applyLive(() -> store.setSlideMinutes(m), this::slideIntervalPage)));
        }
        File dir = getExternalFilesDir("wallpapers");
        items.add(PanelItem.header("Your pictures"));
        PanelItem info = new PanelItem(0, slidePictures().size() + " found in " + (dir != null ? dir.getAbsolutePath() : "-"), () -> { });
        info.stay = true;
        items.add(info);
        renderPanel("Slideshow", items, false);
    }

    private void cardStylePage() {
        List<PanelItem> items = new ArrayList<>();
        for (int i = 0; i < STYLE_NAMES.length; i++) {
            final int v = i;
            items.add(PanelItem.choice(STYLE_NAMES[i], store.cardStyle() == i, () -> applyLive(() -> { store.setCardStyle(v); store.setTheme(-1); }, this::cardStylePage)));
        }
        renderPanel("Card style", items, false);
    }

    private void accentPage() {
        List<PanelItem> items = new ArrayList<>();
        for (int i = 0; i < ACCENT_NAMES.length; i++) {
            final int v = i;
            items.add(PanelItem.choice(ACCENT_NAMES[i], store.accent() == i,
                    () -> applyLive(() -> { store.setAccent(v); store.setTheme(-1); }, this::accentPage)).swatch(ACCENT_COLORS[i]));
        }
        renderPanel("Accent colour", items, false);
    }

    private void focusPage() {
        List<PanelItem> items = new ArrayList<>();
        items.add(PanelItem.header("Style"));
        String[] names = {"Glass glow (lift and glow)", "Minimal (clean white border)"};
        for (int i = 0; i < names.length; i++) {
            final int v = i;
            items.add(PanelItem.choice(names[i], store.focusStyle() == i, () -> applyLive(() -> { store.setFocusStyle(v); store.setTheme(-1); }, this::focusPage)));
        }
        items.add(PanelItem.header("Border thickness"));
        for (int i = 0; i < BORDER_NAMES.length; i++) {
            final int v = i;
            items.add(PanelItem.choice(BORDER_NAMES[i], store.border() == i, () -> applyLive(() -> { store.setBorder(v); store.setTheme(-1); }, this::focusPage)));
        }
        renderPanel("Focus style", items, false);
    }

    private void clockPage() {
        List<PanelItem> items = new ArrayList<>();
        items.add(PanelItem.header("Time"));
        for (int i = 0; i < TIME_NAMES.length; i++) {
            final int v = i;
            items.add(PanelItem.choice(TIME_NAMES[i], store.timeFormat() == i, () -> applyLive(() -> store.setTimeFormat(v), this::clockPage)));
        }
        items.add(PanelItem.header("Date"));
        java.util.Date now = new java.util.Date();
        for (int i = 0; i < DATE_FORMATS.length; i++) {
            final int v = i;
            String ex = new java.text.SimpleDateFormat(DATE_FORMATS[i], Locale.getDefault()).format(now);
            items.add(PanelItem.choice(ex, store.dateFormat() == i, () -> applyLive(() -> store.setDateFormat(v), this::clockPage)));
        }
        renderPanel("Clock & date", items, false);
    }

    private void sleepPage() {
        List<PanelItem> items = new ArrayList<>();
        boolean on = store.sleepAt() > System.currentTimeMillis();
        items.add(PanelItem.choice("Off", !on, () -> { setSleep(0); sleepPage(); }));
        for (int m : new int[]{15, 30, 60, 90, 120}) {
            items.add(PanelItem.choice(m + " minutes", false, () -> { setSleep(m); closePanel(true); }));
        }
        renderPanel(on ? sleepLabel() : "Sleep timer", items, false);
    }

    // ---- wallpaper slideshow (art + your own pictures, all on the TV)

    private List<File> slidePictures() {
        List<File> out = new ArrayList<>();
        File dir = getExternalFilesDir("wallpapers");
        File[] fs = dir != null ? dir.listFiles() : null;
        if (fs != null) {
            for (File f : fs) {
                String n = f.getName().toLowerCase(Locale.ROOT);
                if (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp")) out.add(f);
            }
        }
        Collections.sort(out);
        return out;
    }

    private int slideCount() {
        return ArtWallpaper.NAMES.length + slidePictures().size();
    }

    private Drawable slideDrawable(int index) {
        int n = slideCount();
        int i = ((index % n) + n) % n;
        if (i == 4) return forestDrawable();
        if (i < ArtWallpaper.NAMES.length) return new ArtWallpaper(i);
        File f = slidePictures().get(i - ArtWallpaper.NAMES.length);
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = 2;
        Bitmap bm = BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        if (bm == null) return new ArtWallpaper(0);
        return new LayerDrawable(new Drawable[]{new BitmapDrawable(getResources(), bm), new ColorDrawable(0x66000000)});
    }

    private final Runnable slideTick = new Runnable() {
        @Override public void run() {
            if (effectiveWallpaper() == WP_SLIDESHOW && videoView == null && root != null) {
                store.setSlideIndex(store.slideIndex() + 1);
                Drawable next = slideDrawable(store.slideIndex());
                Drawable current = root.getBackground();
                if (current != null) {
                    android.graphics.drawable.TransitionDrawable td =
                            new android.graphics.drawable.TransitionDrawable(new Drawable[]{current, next});
                    td.setCrossFadeEnabled(true);
                    root.setBackground(td);
                    td.startTransition(1500);
                } else {
                    root.setBackground(next);
                }
                if (dock != null) dock.setBlur(blurredWallpaper());
            }
            handler.postDelayed(this, Math.max(1, store.slideMinutes()) * 60_000L);
        }
    };

    private String sleepLabel() {
        long left = store.sleepAt() - System.currentTimeMillis();
        return store.sleepAt() == 0 || left <= 0 ? "Sleep timer" : "Sleep timer · " + Math.max(1, (left + 59_999) / 60_000) + " min left";
    }

    private TextView credit() {
        TextView t = new TextView(this);
        t.setText("Lumen · Developed by Ponkoj Shill");
        t.setTextColor(0x80FFFFFF);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setLetterSpacing(0.04f);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setPadding(0, dp(28), 0, dp(8));
        t.setShadowLayer(dp(3), 0, dp(1), 0x80000000);
        return t;
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
        int key = w == WP_SLIDESHOW ? 1000 + ((store.slideIndex() % slideCount()) + slideCount()) % slideCount() : w;
        if (blurCache != null && blurCacheId == key) return blurCache;
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
            blurCacheId = key;
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
        boolean mono = style == Store.STYLE_MONO;
        boolean compact = style == Store.STYLE_COMPACT;
        boolean square = style == Store.STYLE_SQUARE;
        int w = capsule ? dp(72) : premium ? dp(144) : mono ? dp(66) : compact ? dp(100) : square ? dp(58) : dp(112);
        int h = capsule ? dp(72) : premium ? dp(81) : mono ? dp(37) : compact ? dp(56) : square ? dp(58) : dp(63);
        int radius = capsule ? 50 : square ? 16 : compact ? 14 : 18;
        // The glass frame only appears around the selected tile.
        FrameLayout card = new FrameLayout(this);
        card.setPadding(dp(9), dp(9), dp(9), dp(9));
        FrameLayout art = new FrameLayout(this);
        art.setClipToOutline(true);
        if (mono) {
            art.setBackground(null); // logos float straight on the glass
        } else if (compact) {
            art.setBackground(rounded(0x99262630, 0, 10)); // small dark translucent tile
        } else if (square) {
            art.setBackground(rounded(0x40FFFFFF, 0, 12));
        } else if (capsule) {
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(0x2EFFFFFF);
            circle.setStroke(dp(1), 0x40FFFFFF);
            art.setBackground(circle);
        } else {
            art.setBackground(rounded(0x33FFFFFF, 0, 12));
        }
        ImageView img = new ImageView(this);
        if (mono) {
            img.setImageDrawable(monoLogo(a));
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            art.addView(img, new FrameLayout.LayoutParams(-1, -1));
        } else if (capsule) {
            img.setImageDrawable(a.icon);
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            art.addView(img, new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER));
        } else if (compact && a.banner != null) {
            img.setImageDrawable(a.banner);
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            art.addView(img, new FrameLayout.LayoutParams(-1, -1));
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
        android.graphics.ColorMatrixColorFilter soft = null;
        if (mono) {
            // Soft, desaturated logos; full colour comes back on the selected app.
            android.graphics.ColorMatrix cm = new android.graphics.ColorMatrix();
            cm.setSaturation(0f);
            android.graphics.ColorMatrix lift = new android.graphics.ColorMatrix(new float[]{
                    1.1f, 0, 0, 0, 30, 0, 1.1f, 0, 0, 30, 0, 0, 1.1f, 0, 30, 0, 0, 0, 0.9f, 0});
            cm.postConcat(lift);
            soft = null;
            card.setPadding(dp(8), dp(8), dp(8), dp(8));
            card.addView(art, new FrameLayout.LayoutParams(w, h, Gravity.CENTER));
            card.setMinimumWidth(dp(78));
            card.setMinimumHeight(dp(78));
        } else {
            card.addView(art, new FrameLayout.LayoutParams(w, h));
        }
        final android.graphics.ColorMatrixColorFilter softFilter = soft;
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
        if (premium) label.setAlpha(0f); // name only shows on the selected tile
        if (mono || compact) label.setVisibility(View.GONE); // logos only, like a clean dock
        if (square) {
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            label.setPadding(0, dp(3), 0, 0);
            label.setTextColor(0xE6FFFFFF);
        }
        t.addView(label, new LinearLayout.LayoutParams(Math.max(w + dp(12), dp(96)), -2));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(dp(4), dp(4), dp(4), dp(4));
        t.setLayoutParams(lp);

        t.setOnFocusChangeListener((v, has) -> {
            if (has) glideTo(v);
            boolean minimal = store.focusStyle() == Store.FOCUS_MINIMAL;
            if (mono) {
                // Round frosted bubble behind the selected logo.
                GradientDrawable bubble = new GradientDrawable();
                bubble.setShape(GradientDrawable.OVAL);
                bubble.setColor(0x40FFFFFF);
                bubble.setStroke(dp(2), 0xD9FFFFFF);
                card.setBackground(has ? bubble : null);
                card.animate().scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f).alpha(has ? 1f : 0.9f)
                        .setDuration(200).start();
                return;
            }
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
        updateStatusLine();
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
            int okColor = store.headerStyle() == 2 ? Color.WHITE : 0xFF66BB6A; // white in the snowfall pill
            wifiView.setTextColor(wifiView.isFocused() ? 0xFF0D1B2A : ok ? okColor : 0xFFFFCA28);
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
        if (w == WP_SNOW) root.addView(new SnowView(this), new FrameLayout.LayoutParams(-1, -1));
    }

    private Drawable wallpaperDrawable(int w) {
        if (w == WP_SLIDESHOW) return slideDrawable(store.slideIndex());
        if (w == WP_FOREST) return forestDrawable();
        if (w == WP_SNOW) return snowForestDrawable();
        if (w == WP_HORIZON) return new ArtWallpaper(5);
        if (w >= WP_ART && w < WP_ART + 4) return new ArtWallpaper(w - WP_ART);
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
        Drawable g1 = glow(glows[0], 0.12f, 0.15f, 0.55f), g2 = glow(glows[1], 0.88f, 0.35f, 0.5f),
                g3 = glow(glows[2], 0.45f, 1.0f, 0.6f);
        if (w == 0) { // Midnight: add a soft star field
            return new LayerDrawable(new Drawable[]{base, g1, g2, g3, new ArtWallpaper(ArtWallpaper.STARS_ONLY)});
        }
        return new LayerDrawable(new Drawable[]{base, g1, g2, g3});
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
        if (!store.showDataUsage() || !hasUsageAccess()) {
            dataToday = null;
            if (dataView != null) dataView.setVisibility(View.GONE);
            updateStatusLine();
            return;
        }
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
            final String today = humanBytes(total) + " today";
            runOnUiThread(() -> {
                dataToday = today;
                if (target != null) { target.setText(text); target.setVisibility(View.VISIBLE); }
                updateStatusLine();
            });
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
        new AlertDialog.Builder(this).setTitle("Lumen 1.7")
                .setMessage("A simple, ad-free home screen.\nDeveloped by Ponkoj Shill.\n\nNo internet permission, no tracking.\n\n"
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
        AlertDialog dlg = new AlertDialog.Builder(this).setTitle(title).setView(box)
                .setPositiveButton("Save", (d, w) -> r.onText(e.getText().toString()))
                .setNegativeButton("Cancel", null)
                .show();
        // The keyboard's Done / Enter key saves straight away (easier with a remote).
        e.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        e.setOnEditorActionListener((v, action, ev) -> {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            return true;
        });
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
        return s == Store.STYLE_CAPSULE ? 8 : s == Store.STYLE_PREMIUM ? 5 : s == Store.STYLE_MONO ? 7
                : s == Store.STYLE_COMPACT ? 7 : s == Store.STYLE_SQUARE ? 12 : 6;
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
