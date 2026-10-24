package com.arzaroth.lumen;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityOptions;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.TransportInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public final class LumenActivity extends Activity implements LumenSettings.Host {
    static final String ACTION_INPUTS = "com.arzaroth.lumen.INPUTS";
    private static final int COLS = 5;
    private static final int ZONE_TOP = 0, ZONE_TILES = 1;
    private static final long LONG_PRESS_MS = 550;
    private static final List<String> PREFERRED = Arrays.asList(
            "com.netflix.ninja",
            "com.google.android.youtube.tv",
            "com.amazon.amazonvideo.livingroom",
            "com.disney.disneyplus",
            "com.apple.atve.androidtv.appletv",
            "com.plexapp.android",
            "org.jellyfin.androidtv",
            "com.spotify.tv.android",
            "com.google.android.youtube.tvmusic",
            "com.android.vending",
            "com.android.tv");
    private static final List<String> LAST = Arrays.asList("com.android.tv.settings");

    private final ArrayList<AppEntry> apps = new ArrayList<>();
    private final ArrayList<Tile> tiles = new ArrayList<>();
    private final Pill[] pills = new Pill[3];
    private final Handler main = new Handler(Looper.getMainLooper());

    private float s;
    private int width, height, side, gap, tileW, tileH, labelSpace, rowH, dockTop, bandTop, bandBottom, gridTop, contentH;

    private FrameLayout root, content;
    private Stage stage;
    private Highlight highlight;
    private Menu menu;
    private boolean resumed;
    private TextView greeting, title, appsHeader, hint;
    private DateView date;
    private ClockView clock;

    private int zone = ZONE_TILES, index, pillIndex, lastDockIndex;
    private boolean moving, longFired, firstLayout = true;
    private SharedPreferences prefs;
    private LumenSettings settings;
    private Ascent ascent;
    private ConnectivityManager.NetworkCallback netCallback;
    private String restoreKey;
    private int loadGeneration;

    private final BroadcastReceiver clockReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            updateClock();
        }
    };

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            main.removeCallbacks(reloadApps);
            main.postDelayed(reloadApps, 500);
        }
    };

    private final Runnable reloadApps = this::reloadApps;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("lumen", MODE_PRIVATE);
        restoreKey = prefs.getString("focus", null);
        settings = new LumenSettings(this, this, prefs);
        measure();
        buildViews();
        applySettings();

        IntentFilter pkg = new IntentFilter();
        pkg.addAction(Intent.ACTION_PACKAGE_ADDED);
        pkg.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pkg.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pkg.addAction(Intent.ACTION_PACKAGE_REPLACED);
        pkg.addDataScheme("package");
        register(packageReceiver, pkg);

        reloadApps();
        if (ACTION_INPUTS.equals(getIntent().getAction())) main.post(this::openInputs);
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packageReceiver);
        super.onDestroy();
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        f.addAction(Intent.ACTION_LOCALE_CHANGED);
        register(clockReceiver, f);
        updateClock();
        watchNetwork();
    }

    @Override
    protected void onStop() {
        unregisterReceiver(clockReceiver);
        if (netCallback != null) {
            getSystemService(ConnectivityManager.class).unregisterNetworkCallback(netCallback);
            netCallback = null;
        }
        if (moving) stopMoving();
        if (menu.isOpen()) menu.close();
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        LumenKeys.setHomeInFront(true);
        clock.setRunning(true);
        date.setRunning(true);
        stage.setRunning(true);
    }

    @Override
    protected void onPause() {
        resumed = false;
        LumenKeys.setHomeInFront(false);
        clock.setRunning(false);
        date.setRunning(false);
        stage.setRunning(false);
        if (zone == ZONE_TILES && index < tiles.size()) {
            prefs.edit().putString("focus", tiles.get(index).app.key()).apply();
        }
        super.onPause();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (menu.isOpen()) menu.close();
        if (moving) stopMoving();
        zone = ZONE_TILES;
        index = Math.min(lastDockIndex, Math.max(0, tiles.size() - 1));
        applyFocus(true);
        if (ACTION_INPUTS.equals(intent.getAction())) openInputs();
    }

    private void openInputs() {
        zone = ZONE_TOP;
        pillIndex = Pill.INPUTS;
        applyFocus(true);
        menu.open(Inputs.page(this, this::launchFromMenu));
    }

    private void launchFromMenu(Intent i) {
        if (start(i)) menu.close();
    }

    @Override
    public void applySettings() {
        int backdrop = prefs.getInt(Prefs.BACKDROP, 0);
        stage.setStyle(backdrop == 1 ? Stage.FIREWORKS : Stage.COSMOS);
        stage.setSkyStill(backdrop == 2);
        stage.setSkyTurning(prefs.getInt(Prefs.TURN, 0) == 1);
        if (zone == ZONE_TILES && !tiles.isEmpty()) stage.setParallax(parallaxOf(index));
        stage.setPace(prefs.getInt(Prefs.PACE, Stage.LIVELY));
        stage.setMeteors(new float[] {0f, 0.35f, 1f, 2.5f}[prefs.getInt(Prefs.METEORS, Prefs.METEORS_DEFAULT)]);
        stage.setFlat(prefs.getInt(Prefs.LOGO, 0) == 1);
        greeting.setVisibility(prefs.getInt(Prefs.GREETING, 0) == 0 ? View.VISIBLE : View.INVISIBLE);
        updateClock();
        if (resumed) LumenKeys.setHomeInFront(true);
    }

    /** Lumen's first appearance since the TV booted, when the intro is on; remembered per boot. */
    private boolean firstSinceBoot() {
        if (prefs.getInt(Prefs.INTRO, 0) != 0 || !ValueAnimator.areAnimatorsEnabled()) return false;
        int boot = Settings.Global.getInt(getContentResolver(), Settings.Global.BOOT_COUNT, -1);
        long id = boot >= 0 ? boot : (Wall.millis() - SystemClock.elapsedRealtime()) / 600_000;
        if (prefs.getLong(Prefs.INTRO_BOOT, Long.MIN_VALUE) == id) return false;
        prefs.edit().putLong(Prefs.INTRO_BOOT, id).apply();
        return true;
    }

    /**
     * The sky comes out star by star and meteors burst from the radiant, then the interface rises
     * into place while the clock gathers out of stardust, and the logo makes its entrance last.
     */
    @Override
    public void playIntro() {
        if (menu.isOpen()) menu.close();
        stage.intro(3.4f);
        float target = scrollTarget();
        content.animate().cancel();
        content.setAlpha(0f);
        content.setTranslationY(target + px(90));
        content.animate().alpha(1f).translationY(target).setStartDelay(2400).setDuration(900)
                .setInterpolator(new DecelerateInterpolator(2.2f)).withStartAction(() -> {
                    clock.intro();
                    date.intro();
                }).start();
    }

    @Override
    public void resetOrder() {
        prefs.edit().remove("order").apply();
        reloadApps();
    }

    @Override
    public void openSystemSettings() {
        if (start(new Intent(Settings.ACTION_SETTINGS))) menu.close();
    }

    @Override
    public void showUpdate(Updater.State state, String latest, int progress) {
        if (state == Updater.State.DOWNLOADING && menu.isOpen()) menu.close();
        ascent.follow(state, latest, progress);
    }

    @Override
    public Menu menu() {
        return menu;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void register(BroadcastReceiver r, IntentFilter f) {
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(r, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(r, f);
        }
    }

    private int px(float v) {
        return Math.round(v * s);
    }

    private void measure() {
        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(dm);
        width = Math.max(dm.widthPixels, dm.heightPixels);
        height = Math.min(dm.widthPixels, dm.heightPixels);
        s = width / 1920f;
        side = px(110);
        gap = px(34);
        tileW = (width - 2 * side - (COLS - 1) * gap) / COLS;
        tileH = Math.round(tileW * 9f / 16f);
        labelSpace = px(56);
        rowH = tileH + labelSpace + px(22);
        bandBottom = height - px(54);
        bandTop = bandBottom - (px(30) + tileH + labelSpace);
        dockTop = bandTop + px(30);
        gridTop = bandBottom + px(96);
        contentH = height;
    }

    private TextView text(int weight, float size, int color) {
        TextView t = new TextView(this);
        t.setTypeface(Fonts.outfit(this, weight));
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * s);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setSingleLine(true);
        return t;
    }

    private void buildViews() {
        root = new FrameLayout(this);
        root.setClipChildren(false);

        stage = new Stage(this, s);
        stage.layoutStage(new RectF(width * 0.36f, px(196), width - side, bandTop - px(178)), bandTop - px(8), width, height);
        root.addView(stage, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        content = new FrameLayout(this);
        content.setClipChildren(false);
        content.setClipToPadding(false);
        root.addView(content, new FrameLayout.LayoutParams(width, contentH));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        greeting = text(500, 28, 0x99FFFFFF);
        greeting.setLetterSpacing(0.02f);
        clock = new ClockView(this, 148 * s, true);
        date = new DateView(this, 30 * s, 400, 0xB3FFFFFF, 0f, false);
        header.addView(greeting);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = px(6) - Math.round(clock.inset());
        clp.leftMargin = -px(6) - Math.round(clock.inset());
        clp.bottomMargin = -Math.round(clock.inset());
        header.addView(clock, clp);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.topMargin = px(10) - Math.round(date.inset());
        dlp.leftMargin = -Math.round(date.inset());
        dlp.bottomMargin = -Math.round(date.inset());
        header.addView(date, dlp);
        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.leftMargin = side;
        hlp.topMargin = px(70);
        content.addView(header, hlp);

        LinearLayout pillRow = new LinearLayout(this);
        pillRow.setOrientation(LinearLayout.HORIZONTAL);
        pillRow.setClipChildren(false);
        pills[Pill.INPUTS] = new Pill(this, Pill.INPUTS, "Inputs", s);
        pills[Pill.WIFI] = new Pill(this, Pill.WIFI, "Wi-Fi", s);
        pills[Pill.SETTINGS] = new Pill(this, Pill.SETTINGS, "", s);
        for (int i = 0; i < pills.length; i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) lp.leftMargin = px(14);
            pillRow.addView(pills[i], lp);
        }
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        plp.topMargin = px(76);
        plp.rightMargin = side;
        content.addView(pillRow, plp);

        title = text(300, 64, 0xFFFFFFFF);
        title.setGravity(Gravity.BOTTOM | Gravity.START);
        title.setLetterSpacing(-0.01f);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(width - 2 * side, px(110));
        tlp.leftMargin = side;
        tlp.topMargin = bandTop - px(36) - px(110);
        content.addView(title, tlp);

        View band = new View(this);
        GradientDrawable bandBg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0x1FFFFFFF, 0x0DFFFFFF});
        bandBg.setCornerRadius(px(44));
        bandBg.setStroke(Math.max(1, px(1.5f)), 0x1FFFFFFF);
        band.setBackground(bandBg);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(width - 2 * (side - px(46)), bandBottom - bandTop);
        blp.leftMargin = side - px(46);
        blp.topMargin = bandTop;
        content.addView(band, blp);

        appsHeader = text(600, 20, 0x66FFFFFF);
        appsHeader.setLetterSpacing(0.16f);
        appsHeader.setText(R.string.all_apps);
        appsHeader.setAlpha(0f);
        FrameLayout.LayoutParams alp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        alp.leftMargin = side;
        alp.topMargin = gridTop - px(58);
        content.addView(appsHeader, alp);

        highlight = new Highlight(this, s);
        content.addView(highlight, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        menu = new Menu(this, s);
        FrameLayout.LayoutParams mlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        mlp.topMargin = px(76 + 60 + 18);
        mlp.rightMargin = side;
        root.addView(menu, mlp);

        hint = text(500, 24, 0xE6FFFFFF);
        hint.setText(R.string.rearrange_hint);
        hint.setPadding(px(30), px(16), px(30), px(16));
        GradientDrawable hintBg = new GradientDrawable();
        hintBg.setColor(0xE616161C);
        hintBg.setCornerRadius(px(40));
        hintBg.setStroke(Math.max(1, px(1.5f)), 0x2EFFFFFF);
        hint.setBackground(hintBg);
        hint.setAlpha(0f);
        FrameLayout.LayoutParams nlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        nlp.topMargin = px(80);
        root.addView(hint, nlp);

        ascent = new Ascent(this, s);
        root.addView(ascent, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        content.setAlpha(0f);
        setContentView(root);
        getWindow().setBackgroundDrawable(null);
    }

    private void reloadApps() {
        final int generation = ++loadGeneration;
        final Context app = getApplicationContext();
        new Thread(() -> {
            final ArrayList<AppEntry> loaded = Apps.load(app, tileW, tileH);
            main.post(() -> {
                if (generation == loadGeneration && !isDestroyed()) applyApps(loaded);
            });
        }, "lumen-apps").start();
    }

    private void applyApps(ArrayList<AppEntry> loaded) {
        String focusKey = restoreKey;
        if (focusKey == null && zone == ZONE_TILES && index < tiles.size()) focusKey = tiles.get(index).app.key();
        restoreKey = null;

        order(loaded);
        apps.clear();
        apps.addAll(loaded);
        for (Tile t : tiles) content.removeView(t);
        tiles.clear();
        for (AppEntry a : apps) {
            Tile t = new Tile(this, a, tileW, tileH, labelSpace, s);
            tiles.add(t);
            content.addView(t, new FrameLayout.LayoutParams(tileW, tileH + labelSpace));
        }

        int rows = (tiles.size() + COLS - 1) / COLS;
        contentH = rows > 1 ? Math.max(height, gridTop + (rows - 2) * rowH + tileH + labelSpace + px(120)) : height;
        ViewGroup.LayoutParams lp = content.getLayoutParams();
        lp.height = contentH;
        content.setLayoutParams(lp);
        appsHeader.setVisibility(rows > 1 ? View.VISIBLE : View.INVISIBLE);
        layoutTiles(false);

        index = 0;
        if (focusKey != null) {
            for (int i = 0; i < tiles.size(); i++) {
                if (tiles.get(i).app.key().equals(focusKey)) index = i;
            }
        }
        index = Math.min(index, Math.max(0, tiles.size() - 1));
        if (index < COLS) lastDockIndex = index;

        applyFocus(false);
        if (firstLayout) {
            firstLayout = false;
            if (firstSinceBoot()) {
                playIntro();
            } else {
                float target = scrollTarget();
                content.setTranslationY(target + px(24));
                content.animate().alpha(1f).translationY(target).setStartDelay(0)
                        .setDuration(520).setInterpolator(new DecelerateInterpolator(2f)).start();
            }
        }
    }

    private void order(ArrayList<AppEntry> list) {
        final HashMap<String, Integer> rank = new HashMap<>();
        String saved = prefs.getString("order", "");
        if (!saved.isEmpty()) {
            String[] keys = saved.split("\n");
            for (int i = 0; i < keys.length; i++) rank.put(keys[i], i);
        }
        Collections.sort(list, (a, b) -> {
            Integer ra = rank.get(a.key()), rb = rank.get(b.key());
            if (ra != null && rb != null) return ra - rb;
            if (ra != null) return -1;
            if (rb != null) return 1;
            int pa = defaultRank(a), pb = defaultRank(b);
            if (pa != pb) return pa - pb;
            return a.label.compareToIgnoreCase(b.label);
        });
    }

    private static int defaultRank(AppEntry a) {
        String pkg = a.component.getPackageName();
        int p = PREFERRED.indexOf(pkg);
        if (p >= 0) return p;
        if (LAST.contains(pkg)) return 10_000;
        return 1_000;
    }

    private void saveOrder() {
        StringBuilder sb = new StringBuilder();
        for (Tile t : tiles) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(t.app.key());
        }
        prefs.edit().putString("order", sb.toString()).apply();
    }

    private float tileX(int i) {
        return side + (i % COLS) * (tileW + gap);
    }

    private float tileY(int i) {
        int row = i / COLS;
        return row == 0 ? dockTop : gridTop + (row - 1) * rowH;
    }

    private void layoutTiles(boolean animate) {
        for (int i = 0; i < tiles.size(); i++) {
            Tile t = tiles.get(i);
            if (animate) {
                t.animate().x(tileX(i)).y(tileY(i)).setDuration(260).setInterpolator(new DecelerateInterpolator(2f)).start();
            } else {
                t.setX(tileX(i));
                t.setY(tileY(i));
            }
        }
    }

    private void applyFocus(boolean animate) {
        boolean onTiles = zone == ZONE_TILES && !tiles.isEmpty();
        for (int i = 0; i < tiles.size(); i++) {
            tiles.get(i).setFocusedState(onTiles && i == index, animate);
        }
        for (int p = 0; p < pills.length; p++) {
            pills[p].setFocusedState(zone == ZONE_TOP && p == pillIndex, animate);
        }
        if (onTiles) {
            Tile t = tiles.get(index);
            float halfW = tileW * Tile.FOCUS_SCALE / 2 + px(13);
            float halfH = tileH * Tile.FOCUS_SCALE / 2 + px(13);
            float cx = tileX(index) + tileW / 2f, cy = tileY(index) + tileH / 2f;
            highlight.moveTo(new RectF(cx - halfW, cy - halfH, cx + halfW, cy + halfH), t.app.accent, animate);
            setTitle(t.app, animate);
            clock.setAccent(t.app.accent);
            date.setAccent(t.app.accent);
            stage.setScene(t.app, animate);
            stage.setParallax(parallaxOf(index));
            if (index < COLS) lastDockIndex = index;
        } else {
            highlight.show(false, animate);
        }
        scroll(animate);
    }

    /** The focused column, from -1 at the left edge to 1 at the right; 0 with parallax off. */
    private float parallaxOf(int i) {
        int cols = Math.min(COLS, tiles.size());
        if (cols < 2 || prefs.getInt(Prefs.PARALLAX, 0) != 0) return 0f;
        return 2f * (i % COLS) / (cols - 1) - 1f;
    }

    private void setTitle(final AppEntry app, boolean animate) {
        final float visible = focusedRow() >= 1 ? 0f : 1f;
        final int glow = Apps.withAlpha(app.accent, 0xCC);
        title.animate().cancel();
        if (!animate || visible == 0f || app.label.contentEquals(title.getText())) {
            title.setText(app.label);
            title.setShadowLayer(30 * s, 0, 0, glow);
            title.animate().alpha(visible).translationX(0).setDuration(animate ? 220 : 0).withEndAction(null).start();
            return;
        }
        title.animate().alpha(0f).translationX(-px(10)).setDuration(90).withEndAction(() -> {
            title.setText(app.label);
            title.setShadowLayer(30 * s, 0, 0, glow);
            title.setTranslationX(px(14));
            title.animate().alpha(1f).translationX(0).setDuration(240).setInterpolator(new DecelerateInterpolator(2f)).withEndAction(null).start();
        }).start();
    }

    private int focusedRow() {
        return zone == ZONE_TILES ? index / COLS : 0;
    }

    private float scrollTarget() {
        int row = focusedRow();
        if (row < 1) return 0;
        float target = -(gridTop + (row - 1) * rowH - height * 0.36f);
        return Math.min(0, Math.max(target, height - contentH));
    }

    private void scroll(boolean animate) {
        boolean inGrid = focusedRow() >= 1;
        stage.setDim(inGrid ? 0.28f : 1f);
        appsHeader.animate().alpha(inGrid ? 1f : 0f).setDuration(animate ? 300 : 0).start();
        if (firstLayout) return;
        content.animate().translationY(scrollTarget()).setStartDelay(0).setDuration(animate ? 460 : 0).setInterpolator(new DecelerateInterpolator(2.2f)).start();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (ascent.shown()) {
            switch (e.getKeyCode()) {
                case KeyEvent.KEYCODE_BACK:
                case KeyEvent.KEYCODE_ESCAPE:
                    if (e.getAction() == KeyEvent.ACTION_UP) ascent.dismiss();
                    return true;
                case KeyEvent.KEYCODE_VOLUME_UP:
                case KeyEvent.KEYCODE_VOLUME_DOWN:
                case KeyEvent.KEYCODE_VOLUME_MUTE:
                    return super.dispatchKeyEvent(e);
                default:
                    return true;
            }
        }
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        switch (e.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
                if (down) {
                    if (e.getRepeatCount() == 0) {
                        longFired = false;
                    } else if (!longFired && !moving && zone == ZONE_TILES && !menu.isOpen()
                            && (e.isLongPress() || e.getEventTime() - e.getDownTime() >= LONG_PRESS_MS)) {
                        longFired = true;
                        startMoving();
                    }
                } else if (e.getAction() == KeyEvent.ACTION_UP) {
                    if (longFired) {
                        longFired = false;
                    } else if (!e.isCanceled()) {
                        activate();
                    }
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (down) navigate(e.getKeyCode());
                return true;
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
                if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) back();
                return true;
            case KeyEvent.KEYCODE_MENU:
                if (e.getAction() == KeyEvent.ACTION_UP && zone == ZONE_TILES && !menu.isOpen() && !tiles.isEmpty()) {
                    if (moving) stopMoving();
                    else startMoving();
                }
                return true;
            default:
                return super.dispatchKeyEvent(e);
        }
    }

    private int neighbour(int i, int key) {
        int n = tiles.size(), col = i % COLS;
        switch (key) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return col > 0 ? i - 1 : -1;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return col < COLS - 1 && i + 1 < n ? i + 1 : -1;
            case KeyEvent.KEYCODE_DPAD_UP:
                return i >= COLS ? i - COLS : -1;
            default:
                if (i + COLS < n) return i + COLS;
                return i / COLS < (n - 1) / COLS ? n - 1 : -1;
        }
    }

    private void navigate(int key) {
        if (menu.isOpen()) {
            if (key == KeyEvent.KEYCODE_DPAD_UP) {
                menu.move(-1);
            } else if (key == KeyEvent.KEYCODE_DPAD_DOWN) {
                menu.move(1);
            } else if (key == KeyEvent.KEYCODE_DPAD_RIGHT) {
                menu.right();
            } else if (!menu.left()) {
                menu.close();
            }
            return;
        }
        if (zone == ZONE_TOP) {
            if (key == KeyEvent.KEYCODE_DPAD_LEFT && pillIndex > 0) {
                pillIndex--;
            } else if (key == KeyEvent.KEYCODE_DPAD_RIGHT && pillIndex < pills.length - 1) {
                pillIndex++;
            } else if (key == KeyEvent.KEYCODE_DPAD_DOWN && !tiles.isEmpty()) {
                zone = ZONE_TILES;
                index = Math.min(lastDockIndex, tiles.size() - 1);
            } else {
                return;
            }
            applyFocus(true);
            return;
        }
        if (tiles.isEmpty()) {
            if (key == KeyEvent.KEYCODE_DPAD_UP) {
                zone = ZONE_TOP;
                applyFocus(true);
            }
            return;
        }
        if (key == KeyEvent.KEYCODE_DPAD_UP && index < COLS) {
            if (moving) return;
            zone = ZONE_TOP;
            applyFocus(true);
            return;
        }
        int target = neighbour(index, key);
        if (target < 0) return;
        if (moving) {
            Collections.swap(tiles, index, target);
            Collections.swap(apps, index, target);
            layoutTiles(true);
        }
        index = target;
        applyFocus(true);
    }

    private void activate() {
        if (menu.isOpen()) {
            menu.activate();
            return;
        }
        if (moving) {
            stopMoving();
            return;
        }
        if (zone == ZONE_TOP) {
            switch (pillIndex) {
                case Pill.INPUTS:
                    openInputs();
                    break;
                case Pill.WIFI:
                    if (!start(new Intent(Settings.ACTION_WIFI_SETTINGS))) start(new Intent(Settings.ACTION_SETTINGS));
                    break;
                default:
                    menu.open(settings.root());
                    break;
            }
            return;
        }
        if (index >= tiles.size()) return;
        Tile t = tiles.get(index);
        Intent intent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                .setComponent(t.app.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            startActivity(intent, ActivityOptions.makeScaleUpAnimation(t, 0, 0, t.imgW, t.imgH).toBundle());
        } catch (ActivityNotFoundException | SecurityException ex) {
            Toast.makeText(this, t.app.label + " can't be opened", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean start(Intent i) {
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }

    private void back() {
        if (menu.isOpen()) {
            if (!menu.pop()) menu.close();
            return;
        }
        if (moving) {
            stopMoving();
            return;
        }
        if (tiles.isEmpty()) return;
        if (zone == ZONE_TOP || index >= COLS) {
            zone = ZONE_TILES;
            index = Math.min(lastDockIndex, tiles.size() - 1);
            applyFocus(true);
        }
    }

    private void startMoving() {
        if (tiles.isEmpty()) return;
        moving = true;
        tiles.get(index).setWiggling(true);
        hint.animate().alpha(1f).setDuration(200).start();
    }

    private void stopMoving() {
        moving = false;
        for (Tile t : tiles) t.setWiggling(false);
        hint.animate().alpha(0f).setDuration(200).start();
        saveOrder();
    }

    private void updateClock() {
        if (clock == null) return;
        Calendar now = Calendar.getInstance();
        now.setTimeInMillis(Wall.millis());
        int hour = now.get(Calendar.HOUR_OF_DAY);
        greeting.setText(hour < 5 ? "Good night" : hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening");
        int format = prefs.getInt(Prefs.CLOCK, 0);
        boolean h24 = format == 0 ? DateFormat.is24HourFormat(this) : format == 1;
        clock.setFormat(h24);
        date.setText(DateFormat.format(DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM"), now));
    }

    private void watchNetwork() {
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        if (cm == null) return;
        Network active = cm.getActiveNetwork();
        showNetwork(active == null ? null : cm.getNetworkCapabilities(active));
        netCallback = Build.VERSION.SDK_INT >= 31
                ? new ConnectivityManager.NetworkCallback(ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO) {
                    @Override
                    public void onCapabilitiesChanged(Network n, NetworkCapabilities nc) {
                        showNetwork(nc);
                    }

                    @Override
                    public void onLost(Network n) {
                        showNetwork(null);
                    }
                }
                : new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onCapabilitiesChanged(Network n, NetworkCapabilities nc) {
                        showNetwork(nc);
                    }

                    @Override
                    public void onLost(Network n) {
                        showNetwork(null);
                    }
                };
        cm.registerDefaultNetworkCallback(netCallback, main);
    }

    private void showNetwork(NetworkCapabilities nc) {
        String label;
        if (nc == null) label = "Offline";
        else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) label = "Ethernet";
        else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) label = ssid(nc);
        else label = "Online";
        pills[Pill.WIFI].setLabel(label);
        pills[Pill.WIFI].setWired(nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
    }

    private String ssid(NetworkCapabilities nc) {
        String name = null;
        if (Build.VERSION.SDK_INT >= 29) {
            TransportInfo info = nc.getTransportInfo();
            if (info instanceof WifiInfo) name = ((WifiInfo) info).getSSID();
        }
        if (usable(name) == null) {
            WifiManager wm = getApplicationContext().getSystemService(WifiManager.class);
            if (wm != null) {
                WifiInfo wi = wm.getConnectionInfo();
                if (wi != null) name = wi.getSSID();
            }
        }
        name = usable(name);
        return name != null ? name : "Wi-Fi";
    }

    private static String usable(String ssid) {
        if (ssid == null) return null;
        String n = ssid.replace("\"", "").trim();
        if (n.isEmpty() || n.equals(WifiManager.UNKNOWN_SSID) || n.equals("<unknown ssid>")) return null;
        return n.length() > 22 ? n.substring(0, 21) + "…" : n;
    }
}
