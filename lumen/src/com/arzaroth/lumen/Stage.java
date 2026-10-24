package com.arzaroth.lumen;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;

import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/**
 * The empty middle of Home. The focused app's banner becomes a logo of lit voxels under a night sky
 * that keeps telling itself differently: each cycle draws an entrance, a hold and an exit at random,
 * never the same one twice in a row. Switching apps tears the current logo down and turns the sky
 * to the next app's (see Cosmos).
 *
 * Everything that moves is drawn as triangle meshes (drawVertices): thick points and lines become
 * one path each on the GPU renderer, which a TV's GPU cannot afford by the thousand.
 */
final class Stage extends View {
    private static final String TAG = "lumen";
    static final int COSMOS = 0, FIREWORKS = 1;
    static final int LIVELY = 0, CALM = 1, STILL = 2;
    private static final int COLS = 112, ROWS = 63, CAP = 4000, K = 8;
    private static final int[][] GRIDS = {{176, 99}, {144, 81}, {COLS, ROWS}};
    private static final float COVER = 0.35f;
    private static final int ENTER = 0, HOLD = 1, EXIT = 2;
    private static final int E_CONVERGE = 0, E_RAIN = 1, E_PRINT = 2, E_WARP = 3, E_SPIRAL = 4, E_DEPTH = 5, E_SWEEP = 6, E_TILE = 7;
    private static final int H_SHIMMER = 0, H_PULSE = 1, H_RIPPLE = 2, H_SHOW = 3, H_STORM = 4, H_FLAG = 5, H_SPIN = 6;
    private static final int X_DETONATE = 0, X_VORTEX = 1, X_DUST = 2, X_MELT = 3, X_GLITCH = 4, X_LAUNCH = 5, X_HYPERSPACE = 6, X_ZOOM = 7, X_SHATTER = 8;
    private static final int[] COSMOS_HOLDS = {H_SHIMMER, H_PULSE, H_RIPPLE, H_STORM, H_FLAG, H_SPIN};
    private static final int[] FIREWORK_HOLDS = {H_SHIMMER, H_PULSE, H_RIPPLE, H_SHOW, H_FLAG, H_SPIN};
    private static final int[] COSMOS_EXITS = {X_DETONATE, X_VORTEX, X_DUST, X_MELT, X_GLITCH, X_HYPERSPACE, X_ZOOM, X_SHATTER};
    private static final int[] FIREWORK_EXITS = {X_DETONATE, X_VORTEX, X_DUST, X_MELT, X_GLITCH, X_LAUNCH, X_ZOOM, X_SHATTER};
    private static final int[] QUICK_EXITS = {X_DETONATE, X_VORTEX, X_DUST, X_GLITCH, X_ZOOM, X_SHATTER};
    private static final int[] ALL_ENTERS = {E_CONVERGE, E_RAIN, E_PRINT, E_WARP, E_SPIRAL, E_DEPTH, E_SWEEP};
    private static final int[] CALM_ENTERS = {E_CONVERGE, E_RAIN, E_PRINT, E_SWEEP};
    private static final int[] CALM_HOLDS = {H_SHIMMER, H_PULSE, H_FLAG};
    private static final int[] CALM_EXITS = {X_DUST, X_MELT};
    private static final float[] ENTER_T = {1.3f, 1.75f, 1.6f, 1.3f, 1.9f, 1.8f, 1.5f, 1.7f};
    private static final float[] EXIT_T = {2.6f, 1.9f, 2.3f, 2.5f, 1.7f, 2.7f, 1.6f, 1.7f, 2.0f};
    private static final float CHARGE_T = 0.8f, VORTEX_T = 1.45f, GLITCH_T = 0.8f;
    private static final int SPARKS = 2400, RINGS = 8, ROCKETS = 12, LIGHTS = 4, RIPPLES = 3, SPARK_COLORS = 6;
    private static final int SHELLS = 8, PEONY = 0, WILLOW = 1, HALO = 2, CROSSETTE = 3, CHRYSANTHEMUM = 4;
    private static final int PALM = 5, BROCADE = 6, LOGO = 7;
    private static final int TWINKLE = 1, SPLIT = 2, CRACKLE = 4, TRAIL = 8, SMOKE = 16, RAY = 32;
    /** Rays barely brake and fade by age as they fly; below this speed (in units a second) they also dim with it, so none ever hangs in the air. */
    private static final float RAY_FADE = 300f;
    private static final int SPRITE = 64;
    private static final int GHOST_CAP = 1200;
    private static final int GLOW_QUADS = CAP + SPARKS, GHOSTS = CAP, INDEXED = Math.max(GLOW_QUADS, CAP + GHOSTS);

    /** A banner reduced to a lit mosaic: target cells, an 8-colour palette, and its sky. */
    static final class Mosaic {
        final int count;
        final float[] gx, gy;
        final byte[] bucket;
        final int[] palette;
        final int[] sparks;
        final float bottom;
        final int cols, rows;
        final Cosmos.Theme theme;

        Mosaic(int count, int cols, int rows, float[] gx, float[] gy, byte[] bucket, int[] palette, int[] sparks, float bottom,
               Cosmos.Theme theme) {
            this.count = count;
            this.cols = cols;
            this.rows = rows;
            this.gx = gx;
            this.gy = gy;
            this.bucket = bucket;
            this.palette = palette;
            this.sparks = sparks;
            this.bottom = bottom;
            this.theme = theme;
        }
    }

    private static HandlerThread worker;
    private static final Map<Bitmap, Mosaic> mosaics = new WeakHashMap<>();
    private final Handler builder;
    private final Random rnd = new Random();
    private final float s;
    private final Cosmos cosmos;
    private int style = COSMOS;
    private int generation;
    private AppEntry shownApp;

    // The logo's cells. Targets (gx, gy) are normalised to the logo box; pa is opacity, pw a white
    // flash that decays on its own. fx/fy hold each choreography's starting state.
    private final float[] px = new float[CAP], py = new float[CAP], vx = new float[CAP], vy = new float[CAP];
    private final float[] fx = new float[CAP], fy = new float[CAP], gx = new float[CAP], gy = new float[CAP];
    private final float[] pa = new float[CAP], pw = new float[CAP], delay = new float[CAP], seed = new float[CAP];
    private final float[] pz = new float[CAP];
    private final boolean[] loose = new boolean[CAP];
    private final byte[] bk = new byte[CAP];
    private int count;
    private final int[] slots = new int[2 * K];
    private int half;
    /** The floor every logo stands on: how far under the logo box, and how far its line reaches past it. */
    private float floorGap, floorReach, lift;
    /** Where the next logo materialises from: its tile's banner, when the focus has just moved to it. */
    private final RectF source = new RectF();
    private Supplier<RectF> bannerOf;
    /** Where the logo's cells actually are, inside its box: the centre the animations play around. */
    private final RectF art = new RectF();

    private Mosaic pending;
    private boolean hasPending;

    private final float[] sx = new float[SPARKS], sy = new float[SPARKS], svx = new float[SPARKS], svy = new float[SPARKS];
    private final float[] sLife = new float[SPARKS], sMax = new float[SPARKS], sDrag = new float[SPARKS];
    private final float[] sGrav = new float[SPARKS], sTail = new float[SPARKS], sWidth = new float[SPARKS];
    private final byte[] sColor = new byte[SPARKS], sColor2 = new byte[SPARKS], sFlags = new byte[SPARKS];
    private int sparkNext;
    private final int[] sparkColors = new int[SPARK_COLORS];

    private final float[] ringX = new float[RINGS], ringY = new float[RINGS], ringR = new float[RINGS], ringSpeed = new float[RINGS];
    private final float[] ringLife = new float[RINGS], ringWidth = new float[RINGS], ringSquash = new float[RINGS];
    private final int[] ringColor = new int[RINGS];
    private int ringNext;

    private final float[] kx = new float[ROCKETS], ky = new float[ROCKETS], kvx = new float[ROCKETS], kvy = new float[ROCKETS];
    private final float[] kFuse = new float[ROCKETS];
    private final int[] kShell = new int[ROCKETS], kColor = new int[ROCKETS];
    private int lastShell = -1;

    private final float[] lightX = new float[LIGHTS], lightY = new float[LIGHTS], lightR = new float[LIGHTS], lightLife = new float[LIGHTS];
    private final ColorFilter[] lightTint = new ColorFilter[LIGHTS];
    private int lightNext;

    private final float[] waveX = new float[RIPPLES], waveY = new float[RIPPLES], waveT = new float[RIPPLES];
    private int waveNext;

    // Meshes: voxel faces (untextured; side, top/bottom and front faces in three painter-ordered
    // runs) and everything that glows (one soft sprite, tinted per vertex).
    private final float[][] coreV = {new float[(CAP + GHOSTS) * 8], new float[(CAP + GHOSTS) * 8], new float[(CAP + GHOSTS) * 8]};
    private final int[][] coreC = {new int[(CAP + GHOSTS) * 4], new int[(CAP + GHOSTS) * 4], new int[(CAP + GHOSTS) * 4]};

    // Ghosts: the previous logo's cells still in flight when the next one takes the cells over.
    // They keep their momentum and fade out instead of vanishing mid-move.
    private final float[] hx = new float[GHOSTS], hy = new float[GHOSTS], hz = new float[GHOSTS];
    private final float[] hvx = new float[GHOSTS], hvy = new float[GHOSTS], hLife = new float[GHOSTS], hMax = new float[GHOSTS];
    private final float[] hAlpha = new float[GHOSTS];
    private final int[] hColor = new int[GHOSTS];
    private int ghostNext;

    // The shatter exit's pieces: centre, velocity, depth and spin of each, released one by one.
    private static final int CHUNKS_X = 5, CHUNKS_Y = 3, CHUNKS = CHUNKS_X * CHUNKS_Y;
    private final float[] qx = new float[CHUNKS], qy = new float[CHUNKS], qz = new float[CHUNKS];
    private final float[] qvx = new float[CHUNKS], qvy = new float[CHUNKS], qvz = new float[CHUNKS];
    private final float[] qa = new float[CHUNKS], qw = new float[CHUNKS], qd = new float[CHUNKS];
    private final byte[] chunk = new byte[CAP];
    private float spinYaw;
    private final int[] faceQ = new int[3];
    private final float[] axes = new float[9];
    private float frontLight = 1f;
    private final float[] glowV = new float[GLOW_QUADS * 8];
    private final float[] glowT = new float[GLOW_QUADS * 8];
    private final int[] glowC = new int[GLOW_QUADS * 4];
    private int glowQ;
    private final short[] quads = new short[INDEXED * 6];
    private final float[] point = new float[2];

    private final Paint corePaint = new Paint();
    private final Paint glowPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint tintPaint = new Paint();
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint light = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint horizon = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final PorterDuffColorFilter reflection = new PorterDuffColorFilter(0x30FFFFFF, PorterDuff.Mode.DST_IN);
    private final PorterDuffColorFilter[] chromaTints = {
            new PorterDuffColorFilter(0x66FF2A6D, PorterDuff.Mode.SRC_IN),
            new PorterDuffColorFilter(0x6605D9E8, PorterDuff.Mode.SRC_IN),
    };
    private final RadialGradient whiteLight = new RadialGradient(0, 0, 1,
            new int[] {0xFFFFFFFF, 0x66FFFFFF, 0x00FFFFFF}, new float[] {0f, 0.3f, 1f}, Shader.TileMode.CLAMP);
    private final Matrix lightMatrix = new Matrix();
    private RadialGradient flashShader;
    private RadialGradient coreShader;
    private ColorFilter beamTint;

    private final RectF logo = new RectF();
    private float cell, floorY, clipBottom, unit;
    private int gridRows = ROWS;
    private boolean laidOut;
    private int accent = 0xFF8FA6C8;

    private int phase = HOLD, enterKind = -1, holdKind = -1, exitKind = -1;
    private float phaseT, holdT, clock, speed = 1f, nextRocket, nextWave;
    private int beats, launchOrder;
    private boolean burst;
    private final boolean[] clusterBurst = new boolean[5];
    private final int[] clusterAt = new int[5];
    private float flash, flashX, flashY, shake, chroma, warp;
    private float dim = 1f;
    private ValueAnimator dimAnim;

    private boolean running;
    private final boolean motion;
    private int pace = LIVELY;
    private boolean flat;
    private long last;
    private float slowFrames;
    private int quality = 2;
    private float wait;

    Stage(Context c, float scale) {
        super(c);
        s = scale;
        cosmos = new Cosmos(scale);
        motion = ValueAnimator.areAnimatorsEnabled();
        if (Build.VERSION.SDK_INT < 29) setLayerType(LAYER_TYPE_SOFTWARE, null);
        synchronized (Stage.class) {
            if (worker == null) {
                worker = new HandlerThread("lumen-stage", android.os.Process.THREAD_PRIORITY_BACKGROUND);
                worker.start();
            }
        }
        builder = new Handler(worker.getLooper());

        PorterDuffXfermode add = new PorterDuffXfermode(PorterDuff.Mode.ADD);
        glowPaint.setShader(new BitmapShader(sprite(), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        glowPaint.setXfermode(add);
        tintPaint.setXfermode(add);
        ring.setStyle(Paint.Style.STROKE);
        ring.setXfermode(add);
        light.setXfermode(add);

        for (int q = 0; q < INDEXED; q++) {
            int v = q * 4, t = q * 8, i = q * 6;
            if (q < GLOW_QUADS) {
                glowT[t + 2] = SPRITE;
                glowT[t + 4] = SPRITE;
                glowT[t + 5] = SPRITE;
                glowT[t + 7] = SPRITE;
            }
            quads[i] = (short) v;
            quads[i + 1] = (short) (v + 1);
            quads[i + 2] = (short) (v + 2);
            quads[i + 3] = (short) v;
            quads[i + 4] = (short) (v + 2);
            quads[i + 5] = (short) (v + 3);
        }
        for (int i = 0; i < CAP; i++) seed[i] = rnd.nextFloat();
        for (int k = 0; k < SPARK_COLORS; k++) sparkColors[k] = 0xFFFFFFFF;
        for (int w = 0; w < RIPPLES; w++) waveT[w] = -1;
    }

    private static Bitmap sprite() {
        Bitmap b = Bitmap.createBitmap(SPRITE, SPRITE, Bitmap.Config.ARGB_8888);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(SPRITE / 2f, SPRITE / 2f, SPRITE / 2f,
                new int[] {0xFFFFFFFF, 0x99FFFFFF, 0x26FFFFFF, 0x00FFFFFF}, new float[] {0f, 0.18f, 0.5f, 1f},
                Shader.TileMode.CLAMP));
        new Canvas(b).drawCircle(SPRITE / 2f, SPRITE / 2f, SPRITE / 2f, p);
        return b;
    }

    /** COSMOS (night sky and meteors) or FIREWORKS (rockets and shells). */
    void setStyle(int newStyle) {
        style = newStyle;
    }

    /** LIVELY (every choreography), CALM (the gentle ones) or STILL (the logo stands still). */
    void setPace(int newPace) {
        if (pace == newPace) return;
        boolean wasStill = pace == STILL;
        pace = newPace;
        if (laidOut && count > 0 && (wasStill || pace == STILL)) startEnter();
        if (running) postInvalidateOnAnimation();
    }

    /** Flat squares instead of lit cubes. */
    void setFlat(boolean f) {
        flat = f;
        invalidate();
    }

    /** The sky's first light after a boot; the logo makes its entrance after `logoDelay` seconds. */
    void intro(float logoDelay) {
        wait = logoDelay;
        cosmos.intro();
        if (running) postInvalidateOnAnimation();
    }

    void setSkyTurning(boolean on) {
        cosmos.setTurning(on);
    }

    void setNebula(boolean on) {
        cosmos.setNebula(on);
        invalidate();
    }

    /** The sky's parallax, from -1 (looking left) to 1, and from 0 (level) to 1 (looking down). */
    void setParallax(float x, float y) {
        cosmos.setParallax(x, y);
        if (running) postInvalidateOnAnimation();
    }

    /** Freezes the sky: no twinkling, no meteors. */
    void setSkyStill(boolean still) {
        cosmos.setStill(still);
        if (running) postInvalidateOnAnimation();
    }

    /** How many meteors cross the sky, 1 being the normal shower. */
    void setMeteors(float rate) {
        cosmos.setMeteors(rate);
    }

    /** Where the logo lives (the empty middle), and where its reflection must stop. */
    void layoutStage(RectF area, float reflectionLimit, int screenW, int screenH) {
        float w = area.width(), h = area.height();
        float lw = Math.min(w, h * 16f / 9f), lh = lw * 9f / 16f;
        logo.set(area.centerX() - lw / 2, area.centerY() - lh / 2, area.centerX() + lw / 2, area.centerY() + lh / 2);
        cell = lw / COLS;
        unit = lw / 760f;
        floorGap = cell * 4f;
        floorReach = cell * 14f;
        floorY = logo.bottom + floorGap;
        art.set(logo);
        clipBottom = reflectionLimit;
        cosmos.layout(logo, screenW, screenH);
        laidOut = true;
    }

    void setRunning(boolean run) {
        if (running == run) return;
        running = run;
        last = SystemClock.uptimeMillis();
        if (run) postInvalidateOnAnimation();
    }

    void setDim(float target) {
        if (dimAnim != null) dimAnim.cancel();
        if (dim == target) return;
        dimAnim = ValueAnimator.ofFloat(dim, target);
        dimAnim.setDuration(380);
        dimAnim.addUpdateListener(a -> {
            dim = (float) a.getAnimatedValue();
            invalidate();
        });
        dimAnim.start();
    }

    void setScene(final AppEntry app, final boolean animate) {
        setScene(app, animate, null);
    }

    /**
     * Shows `app`; when its tile's banner can be found, the new logo first materialises out of it.
     * Asked for only once the entrance starts: by then the rows may have scrolled.
     */
    void setScene(final AppEntry app, final boolean animate, Supplier<RectF> banner) {
        if (app == shownApp) return;
        shownApp = app;
        bannerOf = animate ? banner : null;
        final int gen = ++generation;
        final int artW = Math.max(COLS, Math.round(logo.width())), artH = Math.max(ROWS, Math.round(logo.height()));
        builder.removeCallbacksAndMessages(null);
        builder.post(() -> {
            Mosaic m;
            synchronized (mosaics) {
                m = mosaics.get(app.banner);
            }
            if (m == null) {
                m = build(getContext(), app, artW, artH);
                synchronized (mosaics) {
                    mosaics.put(app.banner, m);
                }
            }
            final Mosaic ready = m;
            post(() -> {
                if (gen == generation) apply(ready, app.accent, animate);
            });
        });
    }

    // ------------------------------------------------------------------ mosaic, off the UI thread

    /**
     * The banner as cells. Each cell is kept when enough of its pixels stand out from the banner's
     * background, and takes those pixels' colour alone, so thin strokes (a wordmark) stay whole and
     * crisp instead of averaging away into the background. Small logos get the finest grid that
     * fits under CAP; big full-bleed banners fall back to coarser ones.
     */
    private static Mosaic build(Context context, AppEntry app, int artW, int artH) {
        Bitmap art = Apps.banner(context, app.component, artW, artH);
        Bitmap src = art != null ? art : app.banner;
        int w = src.getWidth(), h = src.getHeight();
        int[] px = new int[w * h];
        src.getPixels(px, 0, w, 0, 0, w, h);
        if (art != null) art.recycle();

        long r = 0, g = 0, b = 0;
        int n = 0, band = Math.max(2, Math.min(w, h) / 30);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (x >= band && x < w - band && y >= band && y < h - band) continue;
                int c = px[y * w + x];
                r += Color.red(c);
                g += Color.green(c);
                b += Color.blue(c);
                n++;
            }
        }
        int bg = Color.rgb((int) (r / n), (int) (g / n), (int) (b / n));
        int[] far = new int[px.length];
        for (int i = 0; i < px.length; i++) far[i] = Color.alpha(px[i]) < 128 ? 0 : (int) Math.min(Integer.MAX_VALUE, dist2(px[i], bg));

        // The tightest background cut that still leaves a logo: some banners (Kodi) paint a
        // textured backdrop that a single threshold either keeps whole or wipes out entirely.
        long min = 0;
        for (int threshold : new int[] {83, 60, 42, 28}) {
            min = (long) threshold * threshold * 3;
            int on = 0;
            for (int f : far) if (f > min) on++;
            if (on >= px.length * 0.02f && on <= px.length * 0.6f) break;
        }

        int cols = COLS, rows = ROWS, kept = 0;
        boolean[] keep = null;
        int[] cellColor = null;
        for (int[] grid : GRIDS) {
            cols = grid[0];
            rows = grid[1];
            int cells = cols * rows;
            long[] cr = new long[cells], cg = new long[cells], cb = new long[cells];
            int[] on = new int[cells], all = new int[cells];
            for (int y = 0; y < h; y++) {
                int row = y * rows / h;
                for (int x = 0; x < w; x++) {
                    int i = row * cols + x * cols / w, p = y * w + x;
                    all[i]++;
                    if (far[p] <= min) continue;
                    on[i]++;
                    cr[i] += Color.red(px[p]);
                    cg[i] += Color.green(px[p]);
                    cb[i] += Color.blue(px[p]);
                }
            }
            keep = new boolean[cells];
            cellColor = new int[cells];
            kept = 0;
            for (int i = 0; i < cells; i++) {
                if (all[i] == 0 || on[i] < all[i] * COVER) continue;
                keep[i] = true;
                cellColor[i] = Color.rgb((int) (cr[i] / on[i]), (int) (cg[i] / on[i]), (int) (cb[i] / on[i]));
                kept++;
            }
            if (kept <= CAP) break;
        }
        if (kept < 40) {
            Bitmap small = Bitmap.createScaledBitmap(src == art ? app.banner : src, cols, rows, true);
            small.getPixels(cellColor, 0, cols, 0, 0, cols, rows);
            if (small != src && small != app.banner) small.recycle();
            for (int i = 0; i < keep.length; i++) keep[i] = true;
            kept = keep.length;
        }

        float[] hsv = new float[3];
        int[] colors = new int[kept];
        float[] gxs = new float[kept], gys = new float[kept];
        float bottom = 0;
        int j = 0;
        for (int i = 0; i < keep.length; i++) {
            if (!keep[i]) continue;
            Color.colorToHSV(cellColor[i], hsv);
            if (hsv[1] < 0.2f) {
                hsv[1] *= 0.5f;
                hsv[2] = Math.max(hsv[2], 0.9f);
            } else {
                hsv[1] = Math.min(1f, hsv[1] * 1.12f);
                hsv[2] = Math.max(hsv[2], 0.7f);
            }
            colors[j] = Color.HSVToColor(hsv);
            gxs[j] = (i % cols + 0.5f) / cols;
            gys[j] = (i / cols + 0.5f) / rows;
            bottom = Math.max(bottom, gys[j]);
            j++;
        }

        int[] centroid = new int[K];
        byte[] bucket = new byte[kept];
        for (int k = 0; k < K; k++) centroid[k] = colors[(int) ((long) k * kept / K)];
        for (int iter = 0; iter < 6; iter++) {
            long[] sr = new long[K], sg = new long[K], sb = new long[K];
            int[] sn = new int[K];
            for (int i = 0; i < kept; i++) {
                int best = 0;
                long bestD = Long.MAX_VALUE;
                for (int k = 0; k < K; k++) {
                    long d = dist2(colors[i], centroid[k]);
                    if (d < bestD) {
                        bestD = d;
                        best = k;
                    }
                }
                bucket[i] = (byte) best;
                sr[best] += Color.red(colors[i]);
                sg[best] += Color.green(colors[i]);
                sb[best] += Color.blue(colors[i]);
                sn[best]++;
            }
            for (int k = 0; k < K; k++) {
                if (sn[k] > 0) centroid[k] = Color.rgb((int) (sr[k] / sn[k]), (int) (sg[k] / sn[k]), (int) (sb[k] / sn[k]));
            }
        }

        // Shuffled so delays look organic rather than raster, and so a cap thins the logo evenly.
        Random shuffle = new Random(app.key().hashCode());
        for (int i = kept - 1; i > 0; i--) {
            int o = shuffle.nextInt(i + 1);
            float t = gxs[i]; gxs[i] = gxs[o]; gxs[o] = t;
            t = gys[i]; gys[i] = gys[o]; gys[o] = t;
            byte u = bucket[i]; bucket[i] = bucket[o]; bucket[o] = u;
        }

        int[] sparks = sparkPalette(centroid, app.accent);
        Random look = new Random(app.key().hashCode() * 31L + 7);
        return new Mosaic(Math.min(kept, CAP), cols, rows, gxs, gys, bucket, centroid, sparks, bottom,
                Cosmos.theme(sparks, app.accent, look));
    }

    private static long dist2(int a, int b) {
        long dr = Color.red(a) - Color.red(b), dg = Color.green(a) - Color.green(b), db = Color.blue(a) - Color.blue(b);
        return dr * dr + dg * dg + db * db;
    }

    private static int[] sparkPalette(int[] palette, int accent) {
        int[] byVivid = palette.clone();
        float[] hsv = new float[3];
        float[] score = new float[byVivid.length];
        for (int i = 0; i < byVivid.length; i++) {
            Color.colorToHSV(byVivid[i], hsv);
            score[i] = hsv[1] * hsv[2];
        }
        for (int i = 0; i < byVivid.length; i++) {
            for (int k = i + 1; k < byVivid.length; k++) {
                if (score[k] > score[i]) {
                    float t = score[i]; score[i] = score[k]; score[k] = t;
                    int c = byVivid[i]; byVivid[i] = byVivid[k]; byVivid[k] = c;
                }
            }
        }
        return new int[] {0xFFFFFFFF, 0xFFFFD27A, byVivid[0], byVivid[1], byVivid[2], accent};
    }

    // ------------------------------------------------------------------ cycle

    private void apply(Mosaic m, int newAccent, boolean animate) {
        if (!laidOut) return;
        pending = m;
        hasPending = true;
        accent = newAccent;
        cosmos.setTheme(m.theme, animate && count > 0 && motion);
        if (count == 0 || !animate || !lively()) {
            startEnter();
            return;
        }
        if (phase == EXIT) {
            speed = 1.6f;
        } else {
            startExit(true);
        }
    }

    /** Takes the pending logo: palette, light colours and target cells. */
    private void adopt() {
        Mosaic m = pending;
        hasPending = false;
        int newHalf = half == 0 ? K : 0;
        for (int k = 0; k < K; k++) slots[newHalf + k] = m.palette[k];
        half = newHalf;
        System.arraycopy(m.sparks, 0, sparkColors, 0, SPARK_COLORS);
        lift = 1f - m.bottom;
        gridRows = m.rows;
        cell = logo.width() / m.cols;
        float left = 1f, right = 0f, top = 1f;
        for (int i = 0; i < m.count; i++) {
            left = Math.min(left, m.gx[i]);
            right = Math.max(right, m.gx[i]);
            top = Math.min(top, m.gy[i] + lift);
        }
        if (m.count > 0) {
            art.set(target(left, true) - cell / 2, target(top, false) - cell / 2, target(right, true) + cell / 2, logo.bottom);
        } else {
            art.set(logo);
        }
        flashShader = new RadialGradient(0, 0, 1, new int[] {0xFFFFFFFF, Apps.withAlpha(m.sparks[2], 0xC0), 0x00000000},
                new float[] {0f, 0.35f, 1f}, Shader.TileMode.CLAMP);
        coreShader = new RadialGradient(0, 0, 1, new int[] {0xFFFFFFFF, Apps.withAlpha(accent, 0xA0), 0x00000000},
                new float[] {0f, 0.3f, 1f}, Shader.TileMode.CLAMP);
        beamTint = new PorterDuffColorFilter(m.sparks[2], PorterDuff.Mode.MULTIPLY);
        horizon.setShader(new LinearGradient(logo.left - floorReach, 0, logo.right + floorReach, 0,
                new int[] {0x00000000, Apps.withAlpha(m.sparks[2], 0x90), Apps.withAlpha(m.sparks[3], 0x90), 0x00000000},
                new float[] {0f, 0.35f, 0.65f, 1f}, Shader.TileMode.CLAMP));
        boolean first = count == 0;
        for (int i = 0; i < m.count; i++) {
            gx[i] = m.gx[i];
            gy[i] = m.gy[i] + lift;
            bk[i] = (byte) (newHalf + m.bucket[i]);
            if (first || i >= count) {
                float a = rnd.nextFloat() * 6.2832f, d = logo.width() * (0.7f + rnd.nextFloat() * 0.6f);
                px[i] = art.centerX() + (float) Math.cos(a) * d;
                py[i] = art.centerY() + (float) Math.sin(a) * d * 0.6f;
                pa[i] = 0;
            }
        }
        count = m.count;
    }

    private boolean lively() {
        return motion && pace != STILL;
    }

    private int chooseFrom(int previous, int[] options) {
        int k;
        do {
            k = options[rnd.nextInt(options.length)];
        } while (k == previous && options.length > 1);
        return k;
    }

    private float target(float g, boolean horizontal) {
        return horizontal ? logo.left + g * logo.width() : logo.top + g * logo.height();
    }

    private void startEnter() {
        RectF banner = hasPending && bannerOf != null ? bannerOf.get() : null;
        if (hasPending) bannerOf = null;
        if (banner != null) source.set(banner);
        if (lively()) enterKind = banner != null ? E_TILE : chooseFrom(enterKind, pace == CALM ? CALM_ENTERS : ALL_ENTERS);
        int reused = lively() && enterKind == E_CONVERGE ? (hasPending ? Math.min(count, pending.count) : count) : 0;
        ghost(reused);
        if (hasPending) adopt();
        for (int i = 0; i < count; i++) {
            pz[i] = 0;
        }
        phase = ENTER;
        phaseT = 0;
        speed = 1f;
        if (!lively()) {
            for (int i = 0; i < count; i++) {
                px[i] = target(gx[i], true);
                py[i] = target(gy[i], false);
                vx[i] = vy[i] = 0;
                pa[i] = 1;
            }
            phase = HOLD;
            holdKind = H_SHIMMER;
            holdT = Float.MAX_VALUE;
            invalidate();
            return;
        }
        float cx = art.centerX(), cy = art.centerY();
        for (int i = 0; i < count; i++) {
            float tx = target(gx[i], true), ty = target(gy[i], false);
            loose[i] = false;
            pw[i] = 0;
            switch (enterKind) {
                case E_TILE:
                    px[i] = source.left + gx[i] * source.width();
                    py[i] = source.top + (gy[i] - lift) * source.height();
                    fx[i] = px[i];
                    fy[i] = py[i];
                    delay[i] = seed[i] * 0.3f + (1 - gy[i]) * 0.15f;
                    pa[i] = 0;
                    pw[i] = 1f;
                    break;
                case E_CONVERGE: {
                    if (pa[i] < 0.5f) {
                        float a = rnd.nextFloat() * 6.2832f, d = logo.width() * (0.7f + rnd.nextFloat() * 0.6f);
                        px[i] = cx + (float) Math.cos(a) * d;
                        py[i] = cy + (float) Math.sin(a) * d * 0.6f;
                    }
                    fx[i] = px[i];
                    fy[i] = py[i];
                    float spread = (float) Math.hypot((tx - cx) / logo.width(), (ty - cy) / logo.height());
                    delay[i] = Math.min(0.42f, spread * 0.35f + seed[i] * 0.12f);
                    break;
                }
                case E_RAIN:
                    fx[i] = tx + (seed[i] - 0.5f) * cell * 3;
                    fy[i] = logo.top - logo.height() * (0.35f + seed[i] * 1.1f);
                    delay[i] = seed[i] * 0.45f + gx[i] * 0.3f;
                    pa[i] = 0;
                    break;
                case E_PRINT:
                    pa[i] = 0;
                    px[i] = tx;
                    py[i] = ty;
                    break;
                case E_WARP:
                    delay[i] = seed[i] * 0.45f;
                    pa[i] = 0;
                    break;
                case E_SPIRAL: {
                    float dx = tx - cx, dy = (ty - cy) / 0.62f;
                    fx[i] = (float) Math.hypot(dx, dy);
                    fy[i] = (float) Math.atan2(dy, dx);
                    delay[i] = seed[i] * 0.3f;
                    pa[i] = 0;
                    break;
                }
                case E_DEPTH:
                    fx[i] = cx + (rnd.nextFloat() - 0.5f) * logo.width() * 2.4f;
                    fy[i] = cy + (rnd.nextFloat() - 0.5f) * logo.height() * 2.4f;
                    px[i] = fx[i];
                    py[i] = fy[i];
                    pz[i] = logo.width() * 7f;
                    delay[i] = seed[i] * 0.5f;
                    pa[i] = 0;
                    break;
                case E_SWEEP:
                    px[i] = tx + logo.width() * 0.7f;
                    py[i] = ty;
                    delay[i] = (1 - gx[i]) * 0.5f + seed[i] * 0.06f;
                    pa[i] = 0;
                    break;
            }
        }
    }

    /**
     * Hands the visible cells from `from` on to ghosts that drift on and fade, thinned to GHOST_CAP:
     * the ghosts are drawn on top of the next logo, so a scattered logo would double the mesh.
     */
    private void ghost(int from) {
        float still = 40 * s;
        int visible = 0;
        for (int i = from; i < count; i++) if (pa[i] > 0.03f) visible++;
        float keep = Math.min(1f, (float) GHOST_CAP / Math.max(1, visible)), owed = 0f;
        for (int i = from; i < count; i++) {
            if (pa[i] <= 0.03f) continue;
            owed += keep;
            if (owed < 1f) {
                pa[i] = 0;
                continue;
            }
            owed -= 1f;
            int g = ghostNext;
            ghostNext = (ghostNext + 1) % GHOSTS;
            hx[g] = px[i];
            hy[g] = py[i];
            hz[g] = pz[i];
            hvx[g] = vx[i];
            hvy[g] = vy[i];
            if (vx[i] * vx[i] + vy[i] * vy[i] < still * still) {
                float a = seed[i] * 6.2832f, v = (40 + 90 * seed[(i * 7) % count]) * unit;
                hvx[g] = (float) Math.cos(a) * v;
                hvy[g] = (float) Math.sin(a) * v - 30 * unit;
            }
            hMax[g] = hLife[g] = 0.45f + 0.45f * seed[i];
            hAlpha[g] = pa[i];
            hColor[g] = pw[i] > 0 ? Apps.mix(slots[bk[i]], 0xFFFFFFFF, Math.min(1f, pw[i])) : slots[bk[i]];
            pa[i] = 0;
        }
    }

    private void startHold() {
        phase = HOLD;
        phaseT = 0;
        holdKind = chooseFrom(holdKind, pace == CALM ? CALM_HOLDS : style == COSMOS ? COSMOS_HOLDS : FIREWORK_HOLDS);
        holdT = 3.0f + rnd.nextFloat() * 1.4f;
        beats = 0;
        nextRocket = holdKind == H_SHOW ? 0.2f : 1.2f + rnd.nextFloat() * 2f;
        nextWave = 0.25f;
        for (int i = 0; i < count; i++) {
            pa[i] = 1;
            vx[i] = vy[i] = 0;
        }
        if (holdKind == H_STORM) {
            holdT = 5.5f;
            cosmos.storm(holdT);
        } else if (holdKind == H_SPIN) {
            holdT = Math.max(holdT, 3.7f);
        }
    }

    private void startExit(boolean quick) {
        if (quick) {
            int k;
            do {
                k = QUICK_EXITS[rnd.nextInt(QUICK_EXITS.length)];
            } while (k == exitKind);
            exitKind = k;
        } else {
            exitKind = chooseFrom(exitKind, pace == CALM ? CALM_EXITS : style == COSMOS ? COSMOS_EXITS : FIREWORK_EXITS);
        }
        phase = EXIT;
        phaseT = 0;
        speed = quick ? 1.4f : 1f;
        burst = false;
        float cx = art.centerX(), cy = art.centerY();
        for (int i = 0; i < count; i++) {
            loose[i] = false;
            switch (exitKind) {
                case X_VORTEX: {
                    float dx = px[i] - cx, dy = (py[i] - cy) / 0.62f;
                    fx[i] = (float) Math.hypot(dx, dy);
                    fy[i] = (float) Math.atan2(dy, dx);
                    break;
                }
                case X_DUST:
                    delay[i] = gx[i] * 1.1f + seed[i] * 0.25f;
                    break;
                case X_MELT:
                    delay[i] = gx[i] * 0.55f + seed[i] * 0.5f;
                    break;
                case X_GLITCH:
                    delay[i] = GLITCH_T + hash01((int) (gy[i] * gridRows / 3), 7) * 0.35f;
                    break;
                case X_ZOOM:
                    delay[i] = (float) Math.hypot((px[i] - cx) / logo.width(), (py[i] - cy) / logo.height()) * 0.55f + seed[i] * 0.2f;
                    break;
                case X_SHATTER: {
                    int c = Math.min(CHUNKS_X - 1, (int) (gx[i] * CHUNKS_X)) + CHUNKS_X * Math.min(CHUNKS_Y - 1, (int) (gy[i] * CHUNKS_Y));
                    chunk[i] = (byte) c;
                    fx[i] = px[i] - target((c % CHUNKS_X + 0.5f) / CHUNKS_X, true);
                    fy[i] = py[i] - target((c / CHUNKS_X + 0.5f) / CHUNKS_Y, false);
                    break;
                }
                case X_HYPERSPACE: {
                    float dx = px[i] - cx, dy = py[i] - cy;
                    float d = (float) Math.hypot(dx, dy);
                    if (d < 1f) {
                        float a = seed[i] * 6.2832f;
                        dx = (float) Math.cos(a);
                        dy = (float) Math.sin(a);
                        d = 1f;
                    }
                    fx[i] = dx / d;
                    fy[i] = dy / d;
                    delay[i] = d / logo.width();
                    break;
                }
            }
        }
        if (exitKind == X_SHATTER) {
            for (int c = 0; c < CHUNKS; c++) {
                float u = (c % CHUNKS_X + 0.5f) / CHUNKS_X, v = (c / CHUNKS_X + 0.5f) / CHUNKS_Y;
                qx[c] = target(u, true);
                qy[c] = target(v, false);
                qz[c] = 0;
                qvx[c] = (u - 0.5f) * 2f * (260 + rnd.nextFloat() * 260) * unit;
                qvy[c] = -(180 + rnd.nextFloat() * 300) * unit;
                qvz[c] = (rnd.nextFloat() - 0.5f) * 900 * unit;
                qa[c] = 0;
                qw[c] = (rnd.nextFloat() - 0.5f) * 5f;
                qd[c] = 0.15f + rnd.nextFloat() * 0.5f;
            }
        }
        if (exitKind == X_LAUNCH) {
            launchOrder = rnd.nextInt(120);
            for (int c = 0; c < 5; c++) clusterBurst[c] = false;
        }
        if (quick && exitKind == X_DETONATE) {
            phaseT = CHARGE_T;
        }
    }

    private static float hash01(int a, int b) {
        int h = a * 73856093 ^ b * 19349663;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return (h & 0xFFFF) / 65535f;
    }

    // ------------------------------------------------------------------ effects

    private void emit(float x, float y, float vxs, float vys, float life, float drag, float gravity, int color,
                      float tail, float width, int flags, int color2) {
        int i = sparkNext;
        sparkNext = (sparkNext + 1) % SPARKS;
        sx[i] = x;
        sy[i] = y;
        svx[i] = vxs;
        svy[i] = vys;
        sLife[i] = sMax[i] = life;
        sDrag[i] = drag;
        sGrav[i] = gravity;
        sColor[i] = (byte) color;
        sColor2[i] = (byte) color2;
        sTail[i] = tail;
        sWidth[i] = width;
        sFlags[i] = (byte) flags;
    }

    private void spark(float x, float y, float vxs, float vys, float life, float drag, float gravity, int color) {
        emit(x, y, vxs, vys, life, drag, gravity, color, 0.035f, 1f, 0, -1);
    }

    private void ring(float x, float y, float speed, float width, int color, float squash) {
        int i = ringNext;
        ringNext = (ringNext + 1) % RINGS;
        ringX[i] = x;
        ringY[i] = y;
        ringR[i] = cell * 3;
        ringSpeed[i] = speed;
        ringWidth[i] = width;
        ringLife[i] = 1f;
        ringColor[i] = color;
        ringSquash[i] = squash;
    }

    private void glare(float x, float y, float radius, int color) {
        int i = lightNext;
        lightNext = (lightNext + 1) % LIGHTS;
        lightX[i] = x;
        lightY[i] = y;
        lightR[i] = radius;
        lightLife[i] = 1f;
        lightTint[i] = new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY);
    }

    private void detonate(float cx, float cy) {
        for (int i = 0; i < count; i++) {
            float dx = px[i] - cx, dy = py[i] - cy;
            float d = (float) Math.hypot(dx, dy) + 1f;
            float v = (240 + rnd.nextFloat() * rnd.nextFloat() * 1300) * unit;
            vx[i] = dx / d * v + (rnd.nextFloat() - 0.5f) * 160 * unit;
            vy[i] = dy / d * v * 0.8f + (rnd.nextFloat() - 0.5f) * 160 * unit - 140 * unit;
            loose[i] = true;
        }
        for (int i = 0; i < 120; i++) {
            float a = rnd.nextFloat() * 6.2832f;
            float v = (500 + rnd.nextFloat() * 1500) * unit;
            emit(cx, cy, (float) Math.cos(a) * v, (float) Math.sin(a) * v * 0.75f, 0.35f + rnd.nextFloat() * 0.45f, 0.6f,
                    90 * unit, rnd.nextInt(3) == 0 ? 0 : 1 + rnd.nextInt(SPARK_COLORS - 1), 0.035f, 1f, RAY, -1);
        }
        for (int i = 0; i < 30; i++) {
            float a = -1.5708f + (rnd.nextFloat() - 0.5f) * 2.4f;
            float v = (90 + rnd.nextFloat() * 340) * unit;
            spark(cx + (rnd.nextFloat() - 0.5f) * logo.width() * 0.5f, cy, (float) Math.cos(a) * v, (float) Math.sin(a) * v,
                    1.4f + rnd.nextFloat() * 1.4f, 0.9f, -40 * unit, 1);
        }
        ring(cx, cy, 1500 * unit, 6 * s, 0xFFFFFFFF, 0.62f);
        ring(cx, cy, 980 * unit, 10 * s, sparkColors[2], 0.62f);
        ring(cx, cy, 620 * unit, 16 * s, sparkColors[3], 0.62f);
        flash = 1f;
        flashX = cx;
        flashY = cy;
        shake = 0.38f;
        chroma = 0.22f;
    }

    private void supernova(float cx, float cy) {
        for (int i = 0; i < 160; i++) {
            float a = rnd.nextFloat() * 6.2832f;
            float v = (300 + rnd.nextFloat() * 1700) * unit;
            emit(cx, cy, (float) Math.cos(a) * v, (float) Math.sin(a) * v * 0.62f, 0.4f + rnd.nextFloat() * 0.6f, 0.5f,
                    60 * unit, rnd.nextInt(4) == 0 ? 0 : 2 + rnd.nextInt(SPARK_COLORS - 2), 0.05f, 1.3f, TWINKLE | RAY, -1);
        }
        ring(cx, cy, 1900 * unit, 5 * s, 0xFFFFFFFF, 0.3f);
        ring(cx, cy, 1300 * unit, 12 * s, sparkColors[2], 0.62f);
        ring(cx, cy, 800 * unit, 20 * s, sparkColors[5], 0.9f);
        flash = 1f;
        flashX = cx;
        flashY = cy;
        shake = 0.38f;
        for (int i = 0; i < count; i++) {
            pa[i] = 0;
            px[i] = cx;
            py[i] = cy;
        }
    }

    private void launchRocket(float x) {
        for (int k = 0; k < ROCKETS; k++) {
            if (kFuse[k] > 0) continue;
            kx[k] = x;
            ky[k] = floorY;
            kvx[k] = (rnd.nextFloat() - 0.5f) * 120 * unit;
            kvy[k] = -(520 + rnd.nextFloat() * 140) * unit;
            kFuse[k] = 0.7f + rnd.nextFloat() * 0.35f;
            int shell;
            do {
                shell = rnd.nextInt(SHELLS);
            } while (shell == lastShell);
            lastShell = shell;
            kShell[k] = shell;
            kColor[k] = 2 + rnd.nextInt(SPARK_COLORS - 2);
            return;
        }
    }

    private void shell(int type, float x, float y, int color) {
        int other = 2 + (color - 1) % (SPARK_COLORS - 2);
        float big = 1.45f * unit;
        switch (type) {
            case PEONY:
                for (int i = 0; i < 180; i++) {
                    float a = rnd.nextFloat() * 6.2832f;
                    float v = (380 + rnd.nextFloat() * 90) * big * (0.8f + 0.2f * (float) Math.sqrt(rnd.nextFloat()));
                    emit(x, y, (float) Math.cos(a) * v, (float) Math.sin(a) * v, 1.5f + rnd.nextFloat() * 0.5f, 1.3f, 150 * unit,
                            rnd.nextInt(6) == 0 ? 0 : color, 0.06f, 1.4f, TWINKLE | (rnd.nextInt(5) < 2 ? CRACKLE : 0), -1);
                }
                break;
            case WILLOW:
                for (int i = 0; i < 130; i++) {
                    float a = rnd.nextFloat() * 6.2832f, v = (260 + rnd.nextFloat() * 100) * big;
                    emit(x, y, (float) Math.cos(a) * v, (float) Math.sin(a) * v, 2.8f + rnd.nextFloat() * 0.6f, 2.2f, 70 * unit,
                            1, 0.18f, 1.1f, TWINKLE, -1);
                }
                break;
            case HALO: {
                float tilt = (rnd.nextFloat() - 0.5f) * 1.4f, ct = (float) Math.cos(tilt), st = (float) Math.sin(tilt);
                for (int i = 0; i < 110; i++) {
                    float a = i / 110f * 6.2832f, v = 360 * big;
                    float ex = (float) Math.cos(a) * v, ey = (float) Math.sin(a) * v * 0.42f;
                    emit(x, y, ex * ct - ey * st, ex * st + ey * ct, 1.6f, 1.2f, 110 * unit, color, 0.05f, 1.5f, CRACKLE, -1);
                }
                for (int i = 0; i < 40; i++) {
                    float a = rnd.nextFloat() * 6.2832f, v = rnd.nextFloat() * 150 * big;
                    emit(x, y, (float) Math.cos(a) * v, (float) Math.sin(a) * v, 1.3f, 1.4f, 90 * unit, 0, 0.04f, 1.2f, TWINKLE, -1);
                }
                break;
            }
            case CROSSETTE:
                for (int i = 0; i < 28; i++) {
                    float a = i / 28f * 6.2832f, v = 460 * big;
                    emit(x, y, (float) Math.cos(a) * v, (float) Math.sin(a) * v, 0.8f, 1.0f, 120 * unit, color, 0.07f, 1.6f, SPLIT, -1);
                }
                break;
            case CHRYSANTHEMUM:
                for (int i = 0; i < 160; i++) {
                    float a = rnd.nextFloat() * 6.2832f, v = (330 + rnd.nextFloat() * 90) * big;
                    emit(x, y, (float) Math.cos(a) * v, (float) Math.sin(a) * v, 1.8f + rnd.nextFloat() * 0.4f, 1.3f, 130 * unit,
                            color, 0.09f, 1.2f, rnd.nextInt(4) == 0 ? CRACKLE : 0, other);
                }
                break;
            case PALM:
                for (int i = 0; i < 9; i++) {
                    float a = i / 9f * 6.2832f + (rnd.nextFloat() - 0.5f) * 0.3f, v = (420 + rnd.nextFloat() * 60) * big;
                    emit(x, y, (float) Math.cos(a) * v, (float) Math.sin(a) * v, 1.7f, 0.9f, 160 * unit, 1, 0.22f, 3.4f, TRAIL, -1);
                }
                for (int i = 0; i < 30; i++) {
                    float a = rnd.nextFloat() * 6.2832f, v = rnd.nextFloat() * 120 * big;
                    emit(x, y, (float) Math.cos(a) * v, (float) Math.sin(a) * v, 1.1f, 1.5f, 80 * unit, 0, 0.03f, 1.2f, TWINKLE, -1);
                }
                break;
            case BROCADE:
                for (int i = 0; i < 150; i++) {
                    float a = rnd.nextFloat() * 6.2832f, v = (240 + rnd.nextFloat() * 100) * big;
                    emit(x, y, (float) Math.cos(a) * v, (float) Math.sin(a) * v, 3.0f + rnd.nextFloat() * 0.6f, 1.9f, 85 * unit,
                            1, 0.12f, 1.15f, TWINKLE | CRACKLE, -1);
                }
                break;
            case LOGO: {
                if (count == 0) {
                    shell(PEONY, x, y, color);
                    return;
                }
                // Stars decelerate into the logo's own silhouette, in its own colours, then fall.
                int stride = Math.max(1, count / 320);
                float span = logo.width() * 0.62f, drag = 3f;
                for (int i = 0; i < count; i += stride) {
                    float ox = (gx[i] - 0.5f) * span, oy = (gy[i] - 0.5f) * span * 0.5625f;
                    emit(x, y, ox * drag, oy * drag, 2f + rnd.nextFloat() * 0.25f, drag, 26 * unit,
                            SPARK_COLORS + bk[i] - half, 0.03f, 1.3f, TWINKLE, -1);
                }
                break;
            }
        }
        for (int i = 0; i < 10; i++) {
            emit(x + (rnd.nextFloat() - 0.5f) * logo.width() * 0.3f, y + (rnd.nextFloat() - 0.5f) * logo.width() * 0.15f,
                    (rnd.nextFloat() - 0.5f) * 30 * unit, -10 * unit, 2.4f + rnd.nextFloat(), 0.6f, -6 * unit,
                    color, 0, 16f, SMOKE, -1);
        }
        glare(x, y, logo.width() * 0.8f, colorOf(color));
        ring(x, y, 640 * unit, 3 * s, colorOf(color), 1f);
        float reach = logo.width() * 0.6f;
        for (int i = 0; i < count; i++) {
            float d = (float) Math.hypot(px[i] - x, py[i] - y);
            if (d < reach) pw[i] = Math.max(pw[i], 0.45f * (1 - d / reach));
        }
    }

    private void salvo() {
        int n = 3 + rnd.nextInt(3);
        for (int k = 0; k < n; k++) {
            launchRocket(logo.left - logo.width() * 0.15f + (k + 0.2f + rnd.nextFloat() * 0.6f) / n * logo.width() * 1.3f);
        }
    }

    // ------------------------------------------------------------------ simulation

    private void update(float dt) {
        clock += dt;
        float t = dt * speed;
        phaseT += t;
        for (int i = 0; i < count; i++) pw[i] = Math.max(0, pw[i] - dt * 2.5f);
        switch (phase) {
            case ENTER:
                enter(t);
                if (phaseT >= ENTER_T[enterKind]) startHold();
                break;
            case HOLD:
                hold(dt);
                if (phaseT >= holdT) startExit(false);
                break;
            case EXIT:
                exit(t);
                if (phaseT >= EXIT_T[exitKind]) startEnter();
                break;
        }
        if (!(phase == HOLD && holdKind == H_SPIN) && spinYaw != 0f) {
            spinYaw = (float) Math.IEEEremainder(spinYaw, Math.PI * 2) * (float) Math.pow(0.02f, dt);
            if (Math.abs(spinYaw) < 0.001f) spinYaw = 0f;
        }
        float drag = (float) Math.pow(0.35f, dt);
        for (int g = 0; g < GHOSTS; g++) {
            if (hLife[g] <= 0) continue;
            hLife[g] -= dt;
            hvx[g] *= drag;
            hvy[g] *= drag;
            hx[g] += hvx[g] * dt;
            hy[g] += hvy[g] * dt;
        }
        effects(dt);
        if (!(phase == EXIT && exitKind == X_HYPERSPACE)) warp = Math.max(0, warp - dt * 1.5f);
        cosmos.setWarp(warp);
        cosmos.update(dt);
    }

    private void place(int i, float x, float y, float inv) {
        vx[i] = (x - px[i]) * inv;
        vy[i] = (y - py[i]) * inv;
        px[i] = x;
        py[i] = y;
    }

    private void enter(float dt) {
        float inv = 1f / Math.max(dt, 0.001f), cx = art.centerX(), cy = art.centerY(), t = phaseT;
        float beamY = art.top + t / 1.25f * (art.height() + cell * 3);
        for (int i = 0; i < count; i++) {
            float tx = target(gx[i], true), ty = target(gy[i], false);
            switch (enterKind) {
                case E_CONVERGE: {
                    float u = (t - delay[i]) / 0.85f;
                    float e = u <= 0 ? 0 : u >= 1 ? 1 : settle(u);
                    place(i, fx[i] + (tx - fx[i]) * e, fy[i] + (ty - fy[i]) * e, inv);
                    if (u > 0) pa[i] = Math.max(pa[i], Math.min(1f, u * 3f));
                    break;
                }
                case E_RAIN: {
                    float u = (t - delay[i]) / 0.75f;
                    if (u <= 0) {
                        px[i] = fx[i];
                        py[i] = fy[i];
                        break;
                    }
                    float e = u >= 1 ? 1 : bounceOut(u);
                    place(i, fx[i] + (tx - fx[i]) * Math.min(1f, u), fy[i] + (ty - fy[i]) * e, inv);
                    pa[i] = 1;
                    break;
                }
                case E_PRINT:
                    vx[i] = vy[i] = 0;
                    if (ty <= beamY && pa[i] == 0) {
                        pa[i] = 1;
                        pw[i] = 1;
                    }
                    break;
                case E_TILE: {
                    float born = Math.min(1f, t / 0.3f), u = (t - delay[i] - 0.15f) / 1.1f;
                    float e = u <= 0 ? 0 : u >= 1 ? 1 : settle(u);
                    float arc = (float) Math.sin(e * Math.PI) * art.height() * 0.25f;
                    place(i, fx[i] + (tx - fx[i]) * e, fy[i] + (ty - fy[i]) * e - arc, inv);
                    pa[i] = born;
                    pw[i] = 1f - e * 0.9f;
                    break;
                }
                case E_WARP: {
                    float u = (t - delay[i]) / 0.7f;
                    if (u <= 0) {
                        px[i] = cx;
                        py[i] = cy;
                        break;
                    }
                    float e = u >= 1 ? 1 : 1 - (float) Math.pow(2, -10 * u);
                    place(i, cx + (tx - cx) * e, cy + (ty - cy) * e, inv);
                    pa[i] = Math.min(1f, u * 4f);
                    break;
                }
                case E_DEPTH: {
                    float u = (t - delay[i]) / 1.2f;
                    if (u <= 0) break;
                    float e = u >= 1 ? 1 : 1 - (float) Math.pow(1 - u, 3);
                    place(i, fx[i] + (tx - fx[i]) * e, fy[i] + (ty - fy[i]) * e, inv);
                    pz[i] = (1 - e) * logo.width() * 7f;
                    pa[i] = Math.min(1f, u * 2.5f);
                    break;
                }
                case E_SWEEP: {
                    float u = (t - delay[i]) / 0.75f;
                    if (u <= 0) break;
                    float e = u >= 1 ? 1 : settle(u), arc = (float) Math.sin(Math.min(1f, u) * Math.PI);
                    place(i, tx + (1 - e) * logo.width() * 0.7f, ty + arc * cell * 2.5f * (seed[i] - 0.5f), inv);
                    pz[i] = -arc * logo.width() * 0.12f;
                    pa[i] = Math.min(1f, u * 3f);
                    break;
                }
                case E_SPIRAL: {
                    float u = (t - delay[i]) / 1.35f;
                    if (u <= 0) continue;
                    float e = u >= 1 ? 1 : u < 0.5f ? 4 * u * u * u : 1 - (float) Math.pow(-2 * u + 2, 3) / 2;
                    float r0 = fx[i] + logo.width() * 0.85f, a0 = fy[i] - (3.5f + seed[i] * 2f);
                    float r = r0 + (fx[i] - r0) * e, a = a0 + (fy[i] - a0) * e;
                    place(i, cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r * 0.62f, inv);
                    pa[i] = Math.min(1f, u * 3f);
                    break;
                }
            }
        }
        if (enterKind == E_PRINT && t < 1.3f && rnd.nextFloat() < dt * 40f) {
            float x = logo.left + rnd.nextFloat() * logo.width();
            spark(x, beamY, (rnd.nextFloat() - 0.5f) * 200 * unit, -(60 + rnd.nextFloat() * 160) * unit, 0.4f, 2f, 420 * unit, 0);
        }
    }

    private void hold(float dt) {
        if (!lively()) {
            for (int i = 0; i < count; i++) {
                px[i] = target(gx[i], true);
                py[i] = target(gy[i], false);
                pz[i] = 0;
            }
            return;
        }
        float t = phaseT, cx = art.centerX(), cy = art.centerY();
        float scale = 1f;
        if (holdKind == H_PULSE) {
            float b = (t % 0.95f) / 0.95f;
            float k1 = (b - 0.04f) * 13f, k2 = (b - 0.24f) * 13f;
            scale = 1f + 0.045f * ((float) Math.exp(-k1 * k1) + 0.65f * (float) Math.exp(-k2 * k2));
            int beat = (int) (t / 0.95f);
            if (beat >= beats) {
                beats = beat + 1;
                ring(cx, cy, 700 * unit, 4 * s, accent, 0.62f);
            }
        }
        if (holdKind == H_RIPPLE && t >= nextWave) {
            int w = waveNext;
            waveNext = (waveNext + 1) % RIPPLES;
            int i = count > 0 ? rnd.nextInt(count) : 0;
            waveX[w] = count > 0 ? target(gx[i], true) : cx;
            waveY[w] = count > 0 ? target(gy[i], false) : cy;
            waveT[w] = 0.0001f;
            nextWave = t + 1.0f + rnd.nextFloat() * 0.4f;
        }
        if (holdKind == H_SPIN) {
            float u = Math.max(0f, Math.min(1f, (t - 0.5f) / 2.6f));
            spinYaw = (u < 0.5f ? 4 * u * u * u : 1 - (float) Math.pow(-2 * u + 2, 3) / 2) * 6.2832f;
        }
        float spinCos = (float) Math.cos(spinYaw), spinSin = (float) Math.sin(spinYaw);
        float flag = Math.min(1f, t / 0.6f) * Math.min(1f, Math.max(0f, (holdT - t) / 0.6f));
        float front = 560 * unit, width = 70 * unit;
        for (int i = 0; i < count; i++) {
            float tx = target(gx[i], true), ty = target(gy[i], false);
            float x = cx + (tx - cx) * scale, y = cy + (ty - cy) * scale;
            pz[i] = holdKind == H_SHIMMER ? (float) Math.sin(clock * 1.7f + gx[i] * 7f + gy[i] * 3f) * cell * 0.9f : 0f;
            if (holdKind == H_FLAG) {
                float wave = (float) Math.sin(gx[i] * 9f - t * 3.4f + gy[i] * 1.2f);
                pz[i] = wave * cell * 3.5f * flag;
                y += wave * cell * 0.25f * flag;
            } else if (holdKind == H_SPIN) {
                x = cx + (tx - cx) * spinCos;
                pz[i] = (tx - cx) * spinSin;
            }
            if (holdKind == H_SHIMMER || holdKind == H_SHOW || holdKind == H_STORM) {
                y += (float) Math.sin(clock * 2.1f + gx[i] * 9f + gy[i] * 4f) * cell * 0.22f;
            } else if (holdKind == H_RIPPLE) {
                for (int w = 0; w < RIPPLES; w++) {
                    if (waveT[w] < 0 || waveT[w] > 2.2f) continue;
                    float dx = tx - waveX[w], dy = ty - waveY[w];
                    float d = (float) Math.sqrt(dx * dx + dy * dy) + 0.001f;
                    float off = Math.abs(d - waveT[w] * front);
                    if (off > width) continue;
                    float grow = Math.min(1f, waveT[w] / 0.35f);
                    float k = (0.5f + 0.5f * (float) Math.cos(Math.PI * off / width)) * (1 - waveT[w] / 2.2f) * grow * grow;
                    x += dx / d * k * cell * 1.8f;
                    y += dy / d * k * cell * 1.8f;
                    pw[i] = Math.max(pw[i], k * 0.7f);
                }
            }
            px[i] = x;
            py[i] = y;
        }
        for (int w = 0; w < RIPPLES; w++) if (waveT[w] >= 0) waveT[w] += dt;
        if (!motion) return;
        if (count > 0 && rnd.nextFloat() < dt * 9f) {
            int i = rnd.nextInt(count);
            spark(px[i], py[i], (rnd.nextFloat() - 0.5f) * 60 * unit, -40 * unit - rnd.nextFloat() * 80 * unit,
                    0.35f + rnd.nextFloat() * 0.4f, 2f, 0, 0);
        }
        if (style == COSMOS) return;
        if (t >= nextRocket) {
            if (holdKind == H_SHOW) {
                salvo();
                nextRocket = t + 1.2f + rnd.nextFloat() * 0.6f;
            } else {
                launchRocket(logo.left - logo.width() * 0.1f + rnd.nextFloat() * logo.width() * 1.2f);
                nextRocket = t + 2.5f + rnd.nextFloat() * 3f;
            }
        }
    }

    private void exit(float dt) {
        float t = phaseT, cx = art.centerX(), cy = art.centerY(), inv = 1f / Math.max(dt, 0.001f);
        switch (exitKind) {
            case X_DETONATE:
                if (t < CHARGE_T) {
                    float j = (t / CHARGE_T) * (t / CHARGE_T) * cell * 0.9f;
                    for (int i = 0; i < count; i++) {
                        px[i] = target(gx[i], true) + (rnd.nextFloat() - 0.5f) * j;
                        py[i] = target(gy[i], false) + (rnd.nextFloat() - 0.5f) * j;
                    }
                    for (int k = 0; k < 3; k++) {
                        float a = rnd.nextFloat() * 6.2832f, d = logo.width() * (0.35f + rnd.nextFloat() * 0.3f);
                        float x = cx + (float) Math.cos(a) * d, y = cy + (float) Math.sin(a) * d * 0.55f;
                        spark(x, y, (cx - x) * 2.4f, (cy - y) * 2.4f, 0.38f, 0f, 0, 2 + rnd.nextInt(3));
                    }
                    break;
                }
                if (!burst) {
                    burst = true;
                    detonate(cx + (rnd.nextFloat() - 0.5f) * cell * 10, cy + (rnd.nextFloat() - 0.5f) * cell * 6);
                }
                physics(dt, 0.42f, 300);
                break;
            case X_VORTEX:
                if (t < VORTEX_T) {
                    float k = t / VORTEX_T;
                    for (int i = 0; i < count; i++) {
                        float r = fx[i] * (1 - (float) Math.pow(k, 2.2f));
                        float a = fy[i] + (2.2f + seed[i] * 1.5f) * k * k * 6.3f;
                        place(i, cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r * 0.62f, inv);
                        pw[i] = Math.max(pw[i], k * 0.8f);
                    }
                } else if (!burst) {
                    burst = true;
                    supernova(cx, cy);
                }
                break;
            case X_DUST:
                for (int i = 0; i < count; i++) {
                    if (t < delay[i]) continue;
                    if (!loose[i]) {
                        loose[i] = true;
                        vx[i] = (60 + rnd.nextFloat() * 160) * unit;
                        vy[i] = -(30 + rnd.nextFloat() * 120) * unit;
                        pw[i] = 0.6f;
                    }
                    vx[i] += ((float) Math.sin(clock * 2.7f + seed[i] * 31f) * 260 + 180) * unit * dt;
                    vy[i] += ((float) Math.cos(clock * 2.3f + seed[i] * 17f) * 240 - 110) * unit * dt;
                    px[i] += vx[i] * dt;
                    py[i] += vy[i] * dt;
                    pa[i] = Math.max(0, pa[i] - dt * 0.8f);
                }
                break;
            case X_MELT:
                for (int i = 0; i < count; i++) {
                    if (t < delay[i]) continue;
                    if (!loose[i]) {
                        loose[i] = true;
                        vx[i] = (rnd.nextFloat() - 0.5f) * 20 * unit;
                        vy[i] = 0;
                    }
                    vy[i] += 1100 * unit * dt;
                    px[i] += vx[i] * dt;
                    py[i] += vy[i] * dt;
                    if (py[i] > floorY) {
                        py[i] = floorY;
                        if (vy[i] > 120 * unit && rnd.nextFloat() < 0.08f) {
                            for (int k = 0; k < 3; k++) {
                                spark(px[i], floorY, (rnd.nextFloat() - 0.5f) * 260 * unit, -(80 + rnd.nextFloat() * 220) * unit,
                                        0.45f, 1.5f, 900 * unit, 2 + rnd.nextInt(3));
                            }
                        }
                        vy[i] *= -0.28f;
                        vx[i] = vx[i] * 0.6f + (rnd.nextFloat() - 0.5f) * 90 * unit;
                    }
                    if (t > delay[i] + 1.1f) pa[i] = Math.max(0, pa[i] - dt * 1.4f);
                }
                break;
            case X_GLITCH: {
                int step = (int) (t / 0.07f);
                chroma = Math.max(chroma, t < GLITCH_T ? 0.12f : 0f);
                for (int i = 0; i < count; i++) {
                    int row = (int) (gy[i] * gridRows / 3);
                    if (t < delay[i]) {
                        float h = hash01(row, step);
                        float off = h < 0.32f ? (h * 25f - 4f) * cell * 1.8f : 0;
                        px[i] = target(gx[i], true) + off;
                        py[i] = target(gy[i], false);
                        vx[i] = vy[i] = 0;
                        pa[i] = hash01(row + 99, step) < 0.15f ? 0.35f : 1f;
                        continue;
                    }
                    if (!loose[i]) {
                        loose[i] = true;
                        vx[i] = (row % 2 == 0 ? 1 : -1) * (2200 + seed[i] * 800) * unit;
                        vy[i] = 0;
                        pa[i] = 1;
                    }
                    px[i] += vx[i] * dt;
                    pa[i] = Math.max(0, pa[i] - dt * 1.6f);
                }
                break;
            }
            case X_HYPERSPACE: {
                if (!burst) {
                    burst = true;
                    for (int i = 0; i < count; i++) pw[i] = 1f;
                    flash = 0.55f;
                    flashX = cx;
                    flashY = cy;
                    ring(cx, cy, 1400 * unit, 4 * s, 0xFFFFFFFF, 0.62f);
                }
                warp = Math.min(1f, t / 0.5f);
                for (int i = 0; i < count; i++) {
                    float v = (80 + 2600 * t * t) * unit * (0.4f + delay[i] * 1.2f);
                    vx[i] = fx[i] * v;
                    vy[i] = fy[i] * v * 0.7f;
                    px[i] += vx[i] * dt;
                    py[i] += vy[i] * dt;
                    if (t > 1.0f) pa[i] = Math.max(0, pa[i] - dt * 3f);
                }
                break;
            }
            case X_ZOOM: {
                float focal = logo.width() * 2.4f;
                for (int i = 0; i < count; i++) {
                    float u = (t - delay[i]) / 1.0f;
                    if (u <= 0) continue;
                    float e = Math.min(1f, u);
                    pz[i] = -e * e * focal * 0.8f;
                    pw[i] = Math.max(pw[i], e * 0.7f);
                    pa[i] = Math.max(0f, 1f - Math.max(0f, (u - 0.55f) / 0.45f));
                    vx[i] = vy[i] = 0;
                }
                break;
            }
            case X_SHATTER: {
                for (int c = 0; c < CHUNKS; c++) {
                    if (qd[c] >= 0 && t >= qd[c]) {
                        qd[c] = -1f;
                        for (int k = 0; k < 6; k++) {
                            spark(qx[c], qy[c], (rnd.nextFloat() - 0.5f) * 420 * unit, -(rnd.nextFloat() * 320) * unit,
                                    0.5f, 1.4f, 800 * unit, 2 + rnd.nextInt(3));
                        }
                    }
                    if (qd[c] >= 0) continue;
                    qvy[c] += 900 * unit * dt;
                    qx[c] += qvx[c] * dt;
                    qy[c] += qvy[c] * dt;
                    qz[c] += qvz[c] * dt;
                    qa[c] += qw[c] * dt;
                }
                for (int i = 0; i < count; i++) {
                    int c = chunk[i];
                    float cos = (float) Math.cos(qa[c]), sin = (float) Math.sin(qa[c]);
                    float jitter = qd[c] >= 0 ? (rnd.nextFloat() - 0.5f) * cell * 0.4f * t / Math.max(0.01f, qd[c]) : 0f;
                    place(i, qx[c] + fx[i] * cos - fy[i] * sin + jitter, qy[c] + fx[i] * sin + fy[i] * cos, inv);
                    pz[i] = qz[c];
                    if (qd[c] < 0 && t > 1.2f) pa[i] = Math.max(0f, pa[i] - dt * 1.8f);
                }
                break;
            }
            case X_LAUNCH: {
                for (int i = 0; i < count; i++) {
                    int c = Math.min(4, (int) (gx[i] * 5));
                    float launch = ((c * 3 + launchOrder) % 5) * 0.22f;
                    if (t < launch) {
                        float j = cell * 0.5f;
                        px[i] = target(gx[i], true) + (rnd.nextFloat() - 0.5f) * j;
                        py[i] = target(gy[i], false) + (rnd.nextFloat() - 0.5f) * j;
                        continue;
                    }
                    if (!loose[i]) {
                        loose[i] = true;
                        vx[i] = ((c + 0.5f) / 5f - 0.5f) * 220 * unit + (rnd.nextFloat() - 0.5f) * 50 * unit;
                        vy[i] = -(640 + seed[i] * 80) * unit;
                        clusterAt[c] = i;
                    }
                    vy[i] += 900 * unit * dt;
                    px[i] += vx[i] * dt;
                    py[i] += vy[i] * dt;
                    if (clusterBurst[c]) {
                        pa[i] = Math.max(0, pa[i] - dt * 5f);
                    } else if (i == clusterAt[c] && vy[i] > -60 * unit) {
                        clusterBurst[c] = true;
                        shell(rnd.nextInt(SHELLS), px[i], py[i], 2 + c % (SPARK_COLORS - 2));
                    }
                }
                break;
            }
        }
    }

    private void physics(float dt, float dragPerSecond, float gravity) {
        float drag = (float) Math.pow(dragPerSecond, dt), g = gravity * unit * dt;
        for (int i = 0; i < count; i++) {
            vx[i] *= drag;
            vy[i] = vy[i] * drag + g;
            px[i] += vx[i] * dt;
            py[i] += vy[i] * dt;
        }
    }

    private void effects(float dt) {
        for (int k = 0; k < ROCKETS; k++) {
            if (kFuse[k] <= 0) continue;
            kvy[k] += 240 * unit * dt;
            kx[k] += kvx[k] * dt + (float) Math.sin(clock * 30 + k) * 0.6f * s;
            ky[k] += kvy[k] * dt;
            emit(kx[k], ky[k], kvx[k] * 0.2f, kvy[k] * 0.2f, 0.06f, 0f, 0, 0, 0.02f, 2.4f, 0, -1);
            for (int n = 0; n < 3; n++) {
                emit(kx[k], ky[k], -kvx[k] * 0.1f + (rnd.nextFloat() - 0.5f) * 50 * unit, -kvy[k] * 0.1f + rnd.nextFloat() * 40 * unit,
                        0.3f + rnd.nextFloat() * 0.3f, 2.5f, 200 * unit, 1, 0.03f, 0.8f, TWINKLE, -1);
            }
            kFuse[k] -= dt;
            if (kFuse[k] <= 0) shell(kShell[k], kx[k], ky[k], kColor[k]);
        }
        for (int i = 0; i < SPARKS; i++) {
            if (sLife[i] <= 0) continue;
            sLife[i] -= dt;
            float d = (float) Math.exp(-sDrag[i] * dt);
            svx[i] *= d;
            svy[i] = svy[i] * d + sGrav[i] * dt;
            sx[i] += svx[i] * dt;
            sy[i] += svy[i] * dt;
            if (sLife[i] > 0 && (sFlags[i] & TRAIL) != 0 && rnd.nextFloat() < dt * 28f) {
                emit(sx[i], sy[i], svx[i] * 0.05f + (rnd.nextFloat() - 0.5f) * 40 * unit, svy[i] * 0.05f,
                        0.5f + rnd.nextFloat() * 0.4f, 2f, 140 * unit, sColor[i], 0.02f, 0.8f, TWINKLE, -1);
            }
            if (sLife[i] <= 0 && (sFlags[i] & CRACKLE) != 0) {
                for (int q = 0; q < 2; q++) {
                    float a = rnd.nextFloat() * 6.2832f, v = 90 * unit;
                    emit(sx[i], sy[i], (float) Math.cos(a) * v, (float) Math.sin(a) * v, 0.06f + rnd.nextFloat() * 0.1f, 1f, 0, 0,
                            0.01f, 1.8f, 0, -1);
                }
            }
            if (sLife[i] <= 0 && (sFlags[i] & SPLIT) != 0) {
                float v = 190 * unit, ux = svx[i], uy = svy[i];
                float len = (float) Math.sqrt(ux * ux + uy * uy) + 0.001f;
                ux /= len;
                uy /= len;
                int color = sColor[i];
                for (int q = 0; q < 4; q++) {
                    float cs = q == 0 ? 1 : q == 2 ? -1 : 0, sn = q == 1 ? 1 : q == 3 ? -1 : 0;
                    float dx = ux * cs - uy * sn, dy = ux * sn + uy * cs;
                    emit(sx[i], sy[i], dx * v, dy * v, 0.65f, 1.4f, 120 * unit, q % 2 == 0 ? color : 0, 0.05f, 1f, TWINKLE, -1);
                }
            }
        }
        for (int i = 0; i < RINGS; i++) {
            if (ringLife[i] <= 0) continue;
            ringLife[i] -= dt * 1.25f;
            ringR[i] += ringSpeed[i] * dt;
            ringSpeed[i] *= (float) Math.pow(0.25f, dt);
        }
        for (int i = 0; i < LIGHTS; i++) if (lightLife[i] > 0) lightLife[i] -= dt * 2.2f;
        flash = Math.max(0, flash - dt * 3.2f);
        shake = Math.max(0, shake - dt);
        chroma = Math.max(0, chroma - dt * 2f);
    }

    /** Fast, then gliding into place: never past it, so nothing stops in mid-air and turns back. */
    private static float settle(float t) {
        return 1 - (float) Math.pow(2, -10 * t);
    }

    private static float bounceOut(float t) {
        if (t < 1 / 2.75f) return 7.5625f * t * t;
        if (t < 2 / 2.75f) {
            t -= 1.5f / 2.75f;
            return 7.5625f * t * t + 0.75f;
        }
        if (t < 2.5f / 2.75f) {
            t -= 2.25f / 2.75f;
            return 7.5625f * t * t + 0.9375f;
        }
        t -= 2.625f / 2.75f;
        return 7.5625f * t * t + 0.984375f;
    }

    // ------------------------------------------------------------------ meshes

    private static void square(float[] v, int q, float x, float y, float h) {
        int o = q * 8;
        v[o] = x - h;
        v[o + 1] = y - h;
        v[o + 2] = x + h;
        v[o + 3] = y - h;
        v[o + 4] = x + h;
        v[o + 5] = y + h;
        v[o + 6] = x - h;
        v[o + 7] = y + h;
    }

    /** A quad from (x0,y0) to (x1,y1), `h` thick on each side and `e` longer at both ends. */
    private static void segment(float[] v, int q, float x0, float y0, float x1, float y1, float h, float e) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        float ux, uy;
        if (len < 0.001f) {
            ux = 1;
            uy = 0;
        } else {
            ux = dx / len;
            uy = dy / len;
        }
        float nx = -uy * h, ny = ux * h, ex = ux * e, ey = uy * e;
        int o = q * 8;
        v[o] = x0 - ex + nx;
        v[o + 1] = y0 - ey + ny;
        v[o + 2] = x1 + ex + nx;
        v[o + 3] = y1 + ey + ny;
        v[o + 4] = x1 + ex - nx;
        v[o + 5] = y1 + ey - ny;
        v[o + 6] = x0 - ex - nx;
        v[o + 7] = y0 - ey - ny;
    }

    private static void tint(int[] c, int q, int color) {
        int o = q * 4;
        c[o] = color;
        c[o + 1] = color;
        c[o + 2] = color;
        c[o + 3] = color;
    }

    /** Spark colours: 0..5 are the light palette, 6.. the logo's own cell colours. */
    private int colorOf(int index) {
        return index < SPARK_COLORS ? sparkColors[index] : slots[half + index - SPARK_COLORS];
    }

    private void buildMeshes(boolean withGlow) {
        faceQ[0] = faceQ[1] = faceQ[2] = 0;
        glowQ = 0;
        float still = 40 * s, tail = 0.028f;
        float halo = cell * 1.6f, size = cell * 0.42f;
        boolean sweeping = phase == HOLD && (holdKind == H_SHIMMER || holdKind == H_SHOW) && lively();
        float sweep = sweeping ? (phaseT % 2.4f) / 1.6f - 0.2f : -9f;
        float yaw = (lively() ? (float) Math.sin(clock * 0.21f) * 0.34f : 0.22f) + spinYaw;
        float pitch = lively() ? 0.2f + (float) Math.sin(clock * 0.15f + 1f) * 0.1f : 0.2f;
        float cw = (float) Math.cos(yaw), sw = (float) Math.sin(yaw), cp = (float) Math.cos(pitch), sp = (float) Math.sin(pitch);
        axes[0] = cw; axes[1] = 0; axes[2] = -sw;
        axes[3] = sp * sw; axes[4] = cp; axes[5] = sp * cw;
        axes[6] = cp * sw; axes[7] = -sp; axes[8] = cp * cw;
        frontLight = Math.max(0.2f, axes[6] * 0.45f + axes[7] * 0.7f + axes[8] * 0.55f);
        // Halos overlap several dense cells: one in three (one in two while moving) at higher strength
        // looks the same for a fraction of the fill.
        int stride = phase == HOLD ? 3 : 2;
        float focal = logo.width() * 2.4f, lcx = art.centerX(), lcy = art.centerY();
        for (int i = 0; i < count; i++) {
            if (pa[i] <= 0.01f) continue;
            int color = slots[bk[i]];
            float white = pw[i];
            float near = 1f - Math.abs(gx[i] - sweep) / 0.09f;
            if (near > 0) white = Math.max(white, near * near * 0.7f);
            if (white > 0) color = Apps.mix(color, 0xFFFFFFFF, Math.min(1f, white));
            float speed2 = vx[i] * vx[i] + vy[i] * vy[i];
            boolean moving = speed2 > still * still;
            float x = px[i], y = py[i];
            int alpha = Math.round(255 * pa[i]);
            voxel(x, y, pz[i], size, color, alpha);
            if (withGlow && i % stride == 0) {
                float k = 1f;
                if (pz[i] != 0f) {
                    k = focal / Math.max(focal * 0.2f, focal + pz[i]);
                    x = lcx + (x - lcx) * k;
                    y = lcy + (y - lcy) * k;
                }
                if (moving) {
                    segment(glowV, glowQ, x - vx[i] * tail, y - vy[i] * tail, x, y, halo * k, halo * 0.6f * k);
                } else {
                    square(glowV, glowQ, x, y, halo * k);
                }
                float strength = (stride == 3 ? 0x80 : 0x58) * pa[i] * (near > 0 ? 1f + near * 1.6f : 1f);
                tint(glowC, glowQ++, Apps.withAlpha(color, Math.min(255, Math.round(strength))));
            }
        }
        for (int g = 0; g < GHOSTS; g++) {
            if (hLife[g] <= 0) continue;
            float k = hLife[g] / hMax[g];
            voxel(hx[g], hy[g], hz[g], size * (0.55f + 0.45f * k), hColor[g], Math.round(255 * hAlpha[g] * k * k));
        }
        float sparkW = Math.max(1.5f, 3.2f * s);
        for (int i = 0; i < SPARKS; i++) {
            if (sLife[i] <= 0) continue;
            float life = sLife[i] / sMax[i];
            float w = sparkW * sWidth[i];
            if ((sFlags[i] & SMOKE) != 0) {
                square(glowV, glowQ, sx[i], sy[i], w * (1.6f - life * 0.6f));
                tint(glowC, glowQ++, Apps.withAlpha(colorOf(sColor[i]), Math.round(0x1C * Math.min(1f, life * 3f))));
                continue;
            }
            float k = Math.min(1f, life * 2f);
            if ((sFlags[i] & RAY) != 0) k *= Math.min(1f, (float) Math.hypot(svx[i], svy[i]) / (RAY_FADE * unit));
            if ((sFlags[i] & TWINKLE) != 0 && life < 0.35f && rnd.nextInt(3) == 0) k *= 0.15f;
            float t = sTail[i] * Math.min(1f, life * 2.5f);
            segment(glowV, glowQ, sx[i] - svx[i] * t, sy[i] - svy[i] * t, sx[i], sy[i], w, w * 1.5f);
            int color = sColor2[i] >= 0 && life < 0.5f ? colorOf(sColor2[i]) : colorOf(sColor[i]);
            tint(glowC, glowQ++, Apps.withAlpha(color, Math.round(255 * k)));
        }
    }

    /**
     * One lit cube seen from a camera in front of the logo's centre: only the faces turned towards the
     * camera, each shaded by a light from the upper left, into the side, top/bottom and front runs.
     */
    private void voxel(float x, float y, float z, float h, int color, int alpha) {
        if (flat) {
            square(coreV[2], faceQ[2], x, y, h);
            tint(coreC[2], faceQ[2]++, Apps.withAlpha(color, alpha));
            return;
        }
        float focal = logo.width() * 2.4f, cx = art.centerX(), cy = art.centerY();
        float ax = axes[0], ay = axes[1], az = axes[2], bx = axes[3], by = axes[4], bz = axes[5];
        float dx = x - cx, dy = y - cy, dz = z + focal;
        for (int k = 0; k < 3; k++) {
            float nx = k == 0 ? ax : k == 1 ? bx : axes[6];
            float ny = k == 0 ? ay : k == 1 ? by : axes[7];
            float nz = k == 0 ? az : k == 1 ? bz : axes[8];
            float facing = nx * dx + ny * dy + nz * dz;
            float sign = facing < -h ? 1f : facing > h ? -1f : 0f;
            if (sign == 0f) continue;
            float ux = k == 0 ? bx : ax, uy = k == 0 ? by : ay, uz = k == 0 ? bz : az;
            float vx2 = k == 2 ? bx : axes[6], vy2 = k == 2 ? by : axes[7], vz2 = k == 2 ? bz : axes[8];
            float fx0 = x + sign * nx * h, fy0 = y + sign * ny * h, fz0 = z + sign * nz * h;
            float[] v = coreV[k];
            int o = faceQ[k] * 8;
            corner(v, o, fx0 - (ux + vx2) * h, fy0 - (uy + vy2) * h, fz0 - (uz + vz2) * h, cx, cy, focal);
            corner(v, o + 2, fx0 + (ux - vx2) * h, fy0 + (uy - vy2) * h, fz0 + (uz - vz2) * h, cx, cy, focal);
            corner(v, o + 4, fx0 + (ux + vx2) * h, fy0 + (uy + vy2) * h, fz0 + (uz + vz2) * h, cx, cy, focal);
            corner(v, o + 6, fx0 - (ux - vx2) * h, fy0 - (uy - vy2) * h, fz0 - (uz - vz2) * h, cx, cy, focal);
            float light = sign * (nx * -0.45f + ny * -0.7f + nz * -0.55f);
            float shade = 0.3f + 0.7f * Math.max(0f, light) / frontLight;
            tint(coreC[k], faceQ[k]++, shaded(color, Math.min(1.3f, shade), alpha));
        }
    }

    private static void corner(float[] v, int o, float x, float y, float z, float cx, float cy, float focal) {
        float k = focal / Math.max(focal * 0.2f, focal + z);
        v[o] = cx + (x - cx) * k;
        v[o + 1] = cy + (y - cy) * k;
    }

    private static int shaded(int color, float k, int alpha) {
        int r = Math.min(255, Math.round(Color.red(color) * k));
        int g = Math.min(255, Math.round(Color.green(color) * k));
        int b = Math.min(255, Math.round(Color.blue(color) * k));
        return Color.argb(alpha, r, g, b);
    }

    // ------------------------------------------------------------------ drawing

    private void drawCore(Canvas c, Paint paint) {
        for (int k = 0; k < 3; k++) {
            if (faceQ[k] == 0) continue;
            c.drawVertices(Canvas.VertexMode.TRIANGLES, faceQ[k] * 8, coreV[k], 0, null, 0, coreC[k], 0, quads, 0, faceQ[k] * 6, paint);
        }
    }

    private void drawRings(Canvas c) {
        for (int i = 0; i < RINGS; i++) {
            if (ringLife[i] <= 0) continue;
            ring.setStrokeWidth(ringWidth[i] * ringLife[i] + 1);
            ring.setColor(Apps.withAlpha(ringColor[i], Math.round(200 * ringLife[i] * ringLife[i])));
            float r = ringR[i];
            c.drawOval(ringX[i] - r, ringY[i] - r * ringSquash[i], ringX[i] + r, ringY[i] + r * ringSquash[i], ring);
        }
    }

    private void drawLight(Canvas c, Shader shader, ColorFilter filter, float x, float y, float rx, float ry, float alpha) {
        if (shader == null || alpha <= 0.01f) return;
        lightMatrix.setScale(rx, ry);
        lightMatrix.postTranslate(x, y);
        shader.setLocalMatrix(lightMatrix);
        light.setShader(shader);
        light.setColorFilter(filter);
        light.setAlpha(Math.round(255 * Math.min(1f, alpha)));
        c.drawOval(x - rx, y - ry, x + rx, y + ry, light);
    }

    @Override
    protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        float dt = Math.min(0.05f, Math.max(0f, (now - last) / 1000f));
        last = now;
        if (running && laidOut && wait > 0) {
            cosmos.setWarp(0f);
            cosmos.update(dt);
            wait -= dt;
            if (wait <= 0 && count > 0) startEnter();
        } else if (running && laidOut && count > 0) {
            update(dt);
            adapt(dt);
        }

        if (laidOut) {
            cosmos.drawBack(c);
        } else {
            c.drawColor(0xFF000000);
        }
        if (laidOut && count > 0 && dim > 0.02f && wait <= 0) {
            c.save();
            if (shake > 0) {
                float m = shake / 0.38f * cell * 1.5f;
                c.translate((rnd.nextFloat() - 0.5f) * m, (rnd.nextFloat() - 0.5f) * m);
            }
            buildMeshes(quality >= 1);
            if (quality >= 2) {
                c.save();
                c.clipRect(0, floorY, getWidth(), clipBottom);
                c.scale(1f, -0.38f, 0, floorY);
                corePaint.setColorFilter(reflection);
                drawCore(c, corePaint);
                corePaint.setColorFilter(null);
                c.restore();
            }
            c.drawRect(logo.left - floorReach, floorY - s, logo.right + floorReach, floorY + 1.5f * s, horizon);

            for (int i = 0; i < LIGHTS; i++) {
                if (lightLife[i] > 0) {
                    drawLight(c, whiteLight, lightTint[i], lightX[i], lightY[i], lightR[i], lightR[i], lightLife[i] * 0.55f);
                }
            }
            if (phase == EXIT && exitKind == X_DETONATE && phaseT < CHARGE_T) {
                float p = phaseT / CHARGE_T, r = logo.width() * (0.12f + 0.5f * p * p);
                drawLight(c, coreShader, null, art.centerX(), art.centerY(), r, r, 0.25f + 0.75f * p);
            } else if (phase == EXIT && exitKind == X_VORTEX && phaseT < VORTEX_T) {
                float p = phaseT / VORTEX_T, r = logo.width() * (0.08f + 0.3f * p);
                drawLight(c, coreShader, null, art.centerX(), art.centerY(), r, r, p);
            }
            if (glowQ > 0) {
                c.drawVertices(Canvas.VertexMode.TRIANGLES, glowQ * 8, glowV, 0, glowT, 0, glowC, 0, quads, 0, glowQ * 6, glowPaint);
            }
            drawCore(c, corePaint);
            if (chroma > 0) {
                float off = Math.min(1f, chroma / 0.22f) * cell * 1.8f;
                for (int t = 0; t < 2; t++) {
                    c.save();
                    c.translate(t == 0 ? -off : off, 0);
                    tintPaint.setColorFilter(chromaTints[t]);
                    drawCore(c, tintPaint);
                    c.restore();
                }
            }
            if (phase == ENTER && enterKind == E_PRINT && phaseT < 1.3f) {
                float y = art.top + phaseT / 1.25f * (art.height() + cell * 3);
                float fade = Math.min(1f, Math.min(phaseT, 1.3f - phaseT) * 8f);
                drawLight(c, whiteLight, beamTint, art.centerX(), y, logo.width() * 0.62f, cell * 5f, fade);
                drawLight(c, whiteLight, null, art.centerX(), y, logo.width() * 0.55f, cell * 1.5f, fade);
            }
            drawRings(c);
            float fr = logo.width() * (0.45f + (1 - flash) * 0.9f);
            if (flash > 0.12f) drawLight(c, flashShader, null, flashX, flashY, fr, fr, flash);
            c.restore();
        }
        if (dim < 0.999f) c.drawColor(Apps.withAlpha(0xFF000000, Math.round(255 * (1 - dim))));

        if (running && motion && (pace != STILL || wait > 0 || cosmos.moving())) postInvalidateOnAnimation();
    }

    // Drop the reflection, then the glow, if the TV cannot keep up; never come back mid-session.
    private void adapt(float dt) {
        slowFrames = dt > 0.024f ? slowFrames + 1 : Math.max(0, slowFrames - 0.5f);
        if (slowFrames > 90 && quality > 0) {
            quality--;
            slowFrames = 0;
            Log.i(TAG, "stage quality " + quality);
        }
    }
}
