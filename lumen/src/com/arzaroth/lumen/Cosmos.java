package com.arzaroth.lumen;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

import java.util.Arrays;
import java.util.Random;

/**
 * Deep space behind the logo, alive. A night sky rendered once at screen resolution (faint stars,
 * the Milky Way with its star clouds and dust lanes), on which the brightest stars are drawn live,
 * a few of them scintillating harder near the horizon; a nebula of the app's own, generated for it,
 * drifts very slowly in two layers; meteors stream out of a radiant, satellites and the odd
 * satellite train cross, and now and then a star flares up like a nova. Each app gets its own
 * nebula, radiant, meteor colour and airglow, chosen to stand apart from the logo.
 */
final class Cosmos {
    private static final int LIVE = 800, TWINKLERS = 120, SPIKED = 10, METEORS = 48, TRAIN = 14, SPRITE = 64;
    private static final int QUADS = 2 * TWINKLERS + 2 * SPIKED + METEORS + TRAIN + 3, INDEXED = Math.max(QUADS, LIVE);
    private static final float TRAVEL_T = 1.2f, NOVA_T = 9f;
    private static final double SIDEREAL_DEG_PER_S = 360.0 / 86164.0905, VIEW_DEG = 50.0;
    /** How far each layer slides, as a share of the width, at full parallax: the Milky Way, the nebula, the bright stars and meteors. */
    private static final float FAR = 0.012f, MID = 0.026f, NEAR = 0.042f;
    /** The same depths up and down, as a share of the height; the field is baked this much taller above and below. */
    private static final float FAR_Y = 0.012f, MID_Y = 0.026f, NEAR_Y = 0.042f;
    private static final int[] EMISSION = {0xFF7CFFB0, 0xFF8FD8FF, 0xFFFFC27A, 0xFFC9A2FF, 0xFFB6FFF0};
    private static final int NEB_W = 400, NEB_H = 225;
    /** Rain streaks or snowflakes at full strength. */
    private static final int PRECIP = 240;
    /** How far the nebula is zoomed in at rest, so its edges stay off screen as it drifts and slides. */
    private static final float NEB_ZOOM = 1.12f;

    /** One app's sky: where its meteors come from, their glow, and the airglow on the horizon. */
    static final class Theme {
        final float radiantX, radiantY, rate;
        final int tint, airglow;
        /** The nebula with the airglow baked in, and the airglow alone for a sky without nebula. */
        final Bitmap nebula, glow;

        Theme(float radiantX, float radiantY, float rate, int tint, int airglow, Bitmap nebula, Bitmap glow) {
            this.nebula = nebula;
            this.glow = glow;
            this.radiantX = radiantX;
            this.radiantY = radiantY;
            this.rate = rate;
            this.tint = tint;
            this.airglow = airglow;
        }
    }

    /**
     * The sky, built once: the faint field, and the live stars by brightness. The real sky also keeps
     * each live star's right ascension and declination, to follow it across the night, and how far
     * its field reaches past the screen on each side.
     */
    private static final class Sky {
        final Bitmap field;
        final float[] x, y, b;
        final int[] color;
        final float[] ra, dec;
        final int marginX;

        Sky(Bitmap field, float[] x, float[] y, float[] b, int[] color) {
            this(field, x, y, b, color, null, null, 0);
        }

        Sky(Bitmap field, float[] x, float[] y, float[] b, int[] color, float[] ra, float[] dec, int marginX) {
            this.field = field;
            this.x = x;
            this.y = y;
            this.b = b;
            this.color = color;
            this.ra = ra;
            this.dec = dec;
            this.marginX = marginX;
        }

        boolean real() {
            return ra != null;
        }
    }

    /** How the real sky is framed: facing south (north below the equator), this wide, the horizon this low. */
    private static final float REAL_FOV = 120f, REAL_HORIZON = 0.94f;
    private static final float REAL_REPROJECT_S = 1f, REAL_REBAKE_S = 120f, REAL_FADE_S = 2f;

    private final float s;
    private final Random rnd = new Random();
    private final RectF logo = new RectF();
    private int width, height;
    private volatile Sky sky;
    private volatile double[] place;
    private volatile float[] catalog;
    private volatile Bitmap fadingField;
    private volatile float fieldFade = 1f, moonLit, moonWaxing;
    private volatile boolean baking;
    private float realClock, bakeClock, moonX = Float.NaN, moonY;
    private final Paint moonPaint = new Paint(Paint.ANTI_ALIAS_FLAG), moonShade = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final android.graphics.Path moonPath = new android.graphics.Path();
    private final RectF moonOval = new RectF();
    private float skyIn, introT = -1f;
    private int burst;

    private Theme theme, previous;
    private float travel = 1f, clock, drift, warp, storm, nextMeteor = 1f, nextSatellite = 12f, meteors = 1f;
    private boolean still, turning, nebulaOn = true;
    private float par, parTarget, parY, parYTarget, liveTop, warpTravel;
    private float clouds, cloudsTarget, rain, rainTarget, snow, snowTarget;
    private final float[] dropX = new float[PRECIP], dropY = new float[PRECIP], dropS = new float[PRECIP];
    private final float[] dropV = new float[PRECIP * 8], dropT = new float[PRECIP * 8];
    private final int[] dropC = new int[PRECIP * 4];
    private boolean dropsPlaced;
    private int margin;

    private final float[] liveRate = new float[LIVE], livePhase = new float[LIVE], liveBirth = new float[LIVE], liveAmp = new float[LIVE];
    private int nova = -1;
    private float novaT, nextNova = 20f;

    private final float[] mx = new float[METEORS], my = new float[METEORS], mdx = new float[METEORS], mdy = new float[METEORS];
    private final float[] mSpeed = new float[METEORS], mLen = new float[METEORS], mLife = new float[METEORS];
    private final float[] mAge = new float[METEORS], mWidth = new float[METEORS], mGain = new float[METEORS];
    private final int[] mTint = new int[METEORS];
    private int meteorNext;

    private float satX, satY, satDx, satDy, satLife;
    private float trainX, trainY, trainDx, trainDy, trainLife, nextTrain = 150f;

    private final float[] spriteV = new float[QUADS * 8], spriteT = new float[QUADS * 8];
    private final int[] spriteC = new int[QUADS * 4];
    private final float[] liveV = new float[LIVE * 8];
    private final int[] liveC = new int[LIVE * 4];
    private final float[] trailV = new float[METEORS * 8];
    private final int[] trailC = new int[METEORS * 4];
    private final short[] quads = new short[INDEXED * 6];

    private final Paint spritePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint addPaint = new Paint();
    private final Paint blit = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint nebulaPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final android.graphics.Matrix nebulaMatrix = new android.graphics.Matrix();

    Cosmos(float scale) {
        s = scale;
        PorterDuffXfermode add = new PorterDuffXfermode(PorterDuff.Mode.ADD);
        spritePaint.setShader(new BitmapShader(sprite(), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        spritePaint.setXfermode(add);
        addPaint.setXfermode(add);

        for (int q = 0; q < PRECIP; q++) {
            dropT[q * 8 + 2] = SPRITE;
            dropT[q * 8 + 4] = SPRITE;
            dropT[q * 8 + 5] = SPRITE;
            dropT[q * 8 + 7] = SPRITE;
        }
        for (int q = 0; q < INDEXED; q++) {
            int v = q * 4, t = q * 8, i = q * 6;
            if (q < QUADS) {
                spriteT[t + 2] = SPRITE;
                spriteT[t + 4] = SPRITE;
                spriteT[t + 5] = SPRITE;
                spriteT[t + 7] = SPRITE;
            }
            quads[i] = (short) v;
            quads[i + 1] = (short) (v + 1);
            quads[i + 2] = (short) (v + 2);
            quads[i + 3] = (short) v;
            quads[i + 4] = (short) (v + 2);
            quads[i + 5] = (short) (v + 3);
        }
        for (int i = 0; i < LIVE; i++) {
            liveRate[i] = 3f + rnd.nextFloat() * 9f;
            livePhase[i] = rnd.nextFloat() * 100f;
            liveBirth[i] = 0.15f + (float) Math.pow(rnd.nextFloat(), 0.8f) * 1.7f;
            liveAmp[i] = rnd.nextFloat() < 0.33f ? 0.35f + rnd.nextFloat() * 0.45f : 0.05f;
        }
        nebulaPaint.setXfermode(add);
    }

    private static Bitmap sprite() {
        Bitmap b = Bitmap.createBitmap(SPRITE, SPRITE, Bitmap.Config.ARGB_8888);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(SPRITE / 2f, SPRITE / 2f, SPRITE / 2f,
                new int[] {0xFFFFFFFF, 0xB0FFFFFF, 0x20FFFFFF, 0x00FFFFFF}, new float[] {0f, 0.12f, 0.4f, 1f},
                Shader.TileMode.CLAMP));
        new Canvas(b).drawCircle(SPRITE / 2f, SPRITE / 2f, SPRITE / 2f, p);
        return b;
    }

    void layout(RectF area, int w, int h) {
        logo.set(area);
        if (w == width && h == height) return;
        width = w;
        height = h;
        rebuild();
    }

    /**
     * Shows the real sky above `latLon` (degrees) from the bright star catalogue `stars` (right
     * ascension, declination, magnitude and colour index per star), or the imagined one when null.
     */
    void setRealSky(double[] latLon, float[] stars) {
        place = latLon;
        catalog = stars;
        bakeClock = 0f;
        if (width > 0) rebuild();
    }

    /** Builds the sky for the screen off the UI thread; a real sky's new field cross-fades over the old. */
    private void rebuild() {
        if (baking) return;
        baking = true;
        final int w = width, h = height, m = (int) Math.ceil(h * FAR_Y) + 1;
        final double[] at = place;
        final float[] stars = catalog;
        new Thread(() -> {
            Sky made = at != null && stars != null ? realSky(w, h + 2 * m, (int) Math.ceil(w * FAR) + 2, m, at, Wall.millis(), stars)
                    : sky(w, h + 2 * m);
            Sky old = sky;
            fadingField = old != null && old.real() && made.real() ? old.field : null;
            fieldFade = fadingField != null ? 0f : 1f;
            if (made.real()) {
                double[] phase = Heavens.moonPhase(Wall.millis());
                moonLit = (float) phase[0];
                moonWaxing = (float) phase[1];
            }
            margin = m;
            sky = made;
            baking = false;
            if (made.real()) reproject(made);
        }, "lumen-sky").start();
    }

    // ------------------------------------------------------------------ the sky, once

    private static float hash(int x, int y, int seed) {
        int h = x * 374761393 + y * 668265263 + seed * 362437;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFFFF) / 16777215f;
    }

    private static float noise(float x, float y, int seed) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
        float fx = x - ix, fy = y - iy;
        float ux = fx * fx * (3 - 2 * fx), uy = fy * fy * (3 - 2 * fy);
        float a = hash(ix, iy, seed), b = hash(ix + 1, iy, seed), c = hash(ix, iy + 1, seed), d = hash(ix + 1, iy + 1, seed);
        return a + (b - a) * ux + (c - a) * uy + (a - b - c + d) * ux * uy;
    }

    private static float fbm(float x, float y, int seed, int octaves) {
        float v = 0, amp = 0.5f, norm = 0;
        for (int o = 0; o < octaves; o++) {
            v += amp * noise(x, y, seed + o * 31);
            norm += amp;
            x = x * 2.03f + 17f;
            y = y * 2.03f + 9f;
            amp *= 0.5f;
        }
        return v / norm;
    }

    private static float smooth(float a, float b, float x) {
        float t = Math.max(0f, Math.min(1f, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    /**
     * The sky at screen resolution, twice the screen wide and seamless across its ends so it can
     * turn forever: a dark gradient, the Milky Way as the arc a great circle makes on a panorama
     * (soft light computed coarse, star clouds and the dark rift on top), then every faint star as
     * single crisp pixels, denser inside the band. The brightest stars are kept out of the picture
     * and returned to be drawn live.
     */
    private static Sky sky(int w, int h) {
        int tw = (2 * w + 3) / 4 * 4, gw = tw / 4, gh = h / 4 + 2;
        float[] gr = new float[gw * gh], gg = new float[gw * gh], gb = new float[gw * gh], dens = new float[gw * gh];
        double turn = 2 * Math.PI / tw;
        for (int y = 0; y < gh; y++) {
            for (int x = 0; x < gw; x++) {
                float px = x * 4f, py = y * 4f;
                double phase = px * turn + 2.4;
                float centre = h * (0.48f + 0.36f * (float) Math.sin(phase));
                float slope = h * 0.36f * (float) Math.cos(phase) * (float) turn;
                float across = (py - centre) / h / (float) Math.sqrt(1 + slope * slope);
                float v = py / h;
                float core = (float) Math.exp(-(across / 0.15f) * (across / 0.15f));
                float clouds = seamless(px, v, 3.2f, tw, h, 11, 5);
                float knots = seamless(px, v, 9f, tw, h, 23, 4);
                float lane = smooth(0.48f, 0.66f, seamless(px, v, 5.5f, tw, h, 37, 5));
                float rift = lane * (float) Math.exp(-((across + 0.015f) / 0.055f) * ((across + 0.015f) / 0.055f));
                float light = core * (0.25f + 1.1f * clouds * clouds + 0.5f * knots * clouds) * (1f - 0.85f * rift);
                light *= 0.75f + 0.25f * (float) Math.sin(2 * phase + 1);
                float warm = smooth(0.5f, 0.9f, core * clouds * 1.4f);
                float t = 1f - py / h;
                int i = y * gw + x;
                gr[i] = 0.008f + 0.02f * (1 - t) + light * (0.13f + 0.12f * warm);
                gg[i] = 0.014f + 0.035f * (1 - t) + light * (0.17f + 0.08f * warm);
                gb[i] = 0.04f + 0.08f * (1 - t) + light * (0.27f + 0.02f * warm);
                dens[i] = Math.min(1f, core * (0.4f + clouds * 1.4f) * (1f - 0.9f * rift));
            }
        }
        int[] px = new int[tw * h];
        for (int y = 0; y < h; y++) {
            float fy = y / 4f;
            int y0 = (int) fy;
            float wy = fy - y0;
            for (int x = 0; x < tw; x++) {
                float fx = x / 4f;
                int x0 = (int) fx;
                float wx = fx - x0;
                int a = y0 * gw + x0, b = y0 * gw + (x0 + 1) % gw, c = a + gw, d = b + gw;
                float r = bilerp(gr, a, b, c, d, wx, wy), g = bilerp(gg, a, b, c, d, wx, wy), bl = bilerp(gb, a, b, c, d, wx, wy);
                float dither = (hash(x, y, 3) - 0.5f) / 255f;
                px[y * tw + x] = Color.rgb(c255(r + dither), c255(g + dither), c255(bl + dither));
            }
        }
        Random r = new Random(7);
        int stars = tw * h / 52;
        float[] sx = new float[stars], sy = new float[stars], sb = new float[stars];
        int[] sc = new int[stars];
        int n = 0;
        for (int k = 0; k < stars; k++) {
            int x = r.nextInt(tw), y = r.nextInt(h);
            float d = dens[(y / 4) * gw + x / 4];
            if (r.nextFloat() > 0.35f + 0.65f * d) continue;
            sx[n] = x;
            sy[n] = y;
            sb[n] = (float) Math.pow(r.nextFloat(), 4.2f) * 1.2f + 0.06f + d * 0.05f;
            float t = r.nextFloat();
            sc[n] = t < 0.15f ? 0xFFB8D4FF : t < 0.8f ? 0xFFF2F5FF : 0xFFFFDCA8;
            n++;
        }
        Integer[] order = new Integer[n];
        for (int k = 0; k < n; k++) order[k] = k;
        final float[] bright = sb;
        Arrays.sort(order, (a, b) -> Float.compare(bright[b], bright[a]));
        int live = Math.min(LIVE, n);
        float[] lx = new float[live], ly = new float[live], lb = new float[live];
        int[] lc = new int[live];
        boolean[] isLive = new boolean[n];
        for (int k = 0; k < live; k++) {
            int i = order[k];
            isLive[i] = true;
            lx[k] = sx[i];
            ly[k] = sy[i];
            lb[k] = sb[i];
            lc[k] = sc[i];
        }
        for (int i = 0; i < n; i++) {
            if (isLive[i]) continue;
            int x = (int) sx[i], y = (int) sy[i];
            float b = sb[i], cr = Color.red(sc[i]) / 255f, cg = Color.green(sc[i]) / 255f, cb = Color.blue(sc[i]) / 255f;
            add(px, tw, h, x, y, b * cr, b * cg, b * cb);
            if (b > 0.32f) {
                float e = b * 0.33f, f = b * 0.1f;
                add(px, tw, h, x + 1, y, e * cr, e * cg, e * cb);
                add(px, tw, h, x - 1, y, e * cr, e * cg, e * cb);
                add(px, tw, h, x, y + 1, e * cr, e * cg, e * cb);
                add(px, tw, h, x, y - 1, e * cr, e * cg, e * cb);
                add(px, tw, h, x + 1, y + 1, f * cr, f * cg, f * cb);
                add(px, tw, h, x - 1, y - 1, f * cr, f * cg, f * cb);
                add(px, tw, h, x + 1, y - 1, f * cr, f * cg, f * cb);
                add(px, tw, h, x - 1, y + 1, f * cr, f * cg, f * cb);
            }
        }
        Bitmap field = Bitmap.createBitmap(tw, h, Bitmap.Config.ARGB_8888);
        field.setPixels(px, 0, tw, 0, 0, tw, h);
        return new Sky(field, lx, ly, lb, lc);
    }

    /** Fractal noise at (x, v) that repeats every `tw` pixels across: two copies blended over the period. */
    /**
     * The real sky above `at` (latitude, longitude) at `millis`, framed like the imagined one: the
     * catalogue's stars where they are, faint ones scattered thicker along the Milky Way, and the
     * Milky Way itself along the true galactic plane, brightest towards the galactic centre.
     */
    private static Sky realSky(int w, int fh, int mx, int my, double[] at, long millis, float[] stars) {
        int fw = (w + 2 * mx + 3) / 4 * 4, gw = fw / 4 + 1, gh = fh / 4 + 2;
        double lst = Heavens.siderealTime(millis, at[1]);
        float[] gr = new float[gw * gh], gg = new float[gw * gh], gb = new float[gw * gh], dens = new float[gw * gh];
        boolean[] patches = new boolean[360 * 180];
        float h = fh - 2 * my;
        for (int y = 0; y < gh; y++) {
            for (int x = 0; x < gw; x++) {
                float sx = x * 4f - mx, sy = y * 4f - my, t = 1f - sy / h;
                double[] look = look(sx, sy, w, h, at[0]);
                int i = y * gw + x;
                if (look[0] < -1) {
                    gr[i] = 0.004f;
                    gg[i] = 0.006f;
                    gb[i] = 0.012f;
                    continue;
                }
                double[] eq = Heavens.equatorial(look[0], look[1], at[0], lst);
                patches[Math.min(179, (int) (eq[1] + 90)) * 360 + (int) eq[0] % 360] = true;
                double[] gal = Heavens.galactic(eq[0], eq[1]);
                float lat = (float) gal[0], lon = (float) gal[1];
                float across = lat / 80f;
                float core = (float) Math.exp(-(across / 0.15f) * (across / 0.15f));
                float v = (lat + 90f) / 90f;
                float clouds = seamless(lon, v, 3.2f, 360, 90, 11, 5);
                float knots = seamless(lon, v, 9f, 360, 90, 23, 4);
                float lane = smooth(0.48f, 0.66f, seamless(lon, v, 5.5f, 360, 90, 37, 5));
                float rift = lane * (float) Math.exp(-((across + 0.015f) / 0.055f) * ((across + 0.015f) / 0.055f));
                float light = core * (0.25f + 1.1f * clouds * clouds + 0.5f * knots * clouds) * (1f - 0.85f * rift);
                light *= 0.55f + 0.45f * (float) Math.cos(Math.toRadians(lon));
                float warm = smooth(0.5f, 0.9f, core * clouds * 1.4f);
                gr[i] = 0.008f + 0.02f * (1 - t) + light * (0.13f + 0.12f * warm);
                gg[i] = 0.014f + 0.035f * (1 - t) + light * (0.17f + 0.08f * warm);
                gb[i] = 0.04f + 0.08f * (1 - t) + light * (0.27f + 0.02f * warm);
                dens[i] = Math.min(1f, core * (0.4f + clouds * 1.4f) * (1f - 0.9f * rift));
            }
        }
        int[] px = new int[fw * fh];
        for (int y = 0; y < fh; y++) {
            float fy = y / 4f;
            int y0 = (int) fy;
            float wy = fy - y0;
            for (int x = 0; x < fw; x++) {
                float fx = x / 4f;
                int x0 = (int) fx;
                float wx = fx - x0;
                int a = y0 * gw + x0, b = a + 1, c = a + gw, d = b + gw;
                float r = bilerp(gr, a, b, c, d, wx, wy), g = bilerp(gg, a, b, c, d, wx, wy), bl = bilerp(gb, a, b, c, d, wx, wy);
                float dither = (hash(x, y, 3) - 0.5f) / 255f;
                px[y * fw + x] = Color.rgb(c255(r + dither), c255(g + dither), c255(bl + dither));
            }
        }
        float[] extra = faintStars(patches);
        int total = stars.length / 4 + extra.length / 3 + 4;
        float[] sx = new float[total], sy = new float[total], sb = new float[total], sra = new float[total], sdec = new float[total];
        int[] sc = new int[total];
        int n = 0;
        for (int k = 0; k + 3 < stars.length; k += 4) {
            if (fix(stars[k], stars[k + 1], at[0], lst, w, h, mx, my, fw, fh, sx, sy, n)) {
                sb[n] = brightness(stars[k + 2]);
                sc[n] = starColor(stars[k + 3]);
                sra[n] = stars[k];
                sdec[n++] = stars[k + 1];
            }
        }
        int[] planets = {Heavens.VENUS, Heavens.MARS, Heavens.JUPITER, Heavens.SATURN};
        float[] planetMag = {-4.2f, 0.5f, -2.3f, 0.6f};
        int[] planetColor = {0xFFFFF8E0, 0xFFFFB27A, 0xFFFFF0D8, 0xFFF5E2B0};
        for (int k = 0; k < planets.length; k++) {
            double[] pos = Heavens.body(planets[k], millis);
            if (fix((float) pos[0], (float) pos[1], at[0], lst, w, h, mx, my, fw, fh, sx, sy, n)) {
                sb[n] = brightness(planetMag[k]);
                sc[n] = planetColor[k];
                sra[n] = (float) pos[0];
                sdec[n++] = (float) pos[1];
            }
        }
        int catalogued = n;
        for (int k = 0; k + 2 < extra.length; k += 3) {
            if (fix(extra[k], extra[k + 1], at[0], lst, w, h, mx, my, fw, fh, sx, sy, n)) {
                int fx = (int) sx[n], fy = (int) sy[n];
                float d = dens[Math.min(gh - 1, fy / 4) * gw + Math.min(gw - 1, fx / 4)];
                sb[n] = extra[k + 2] + d * 0.05f;
                float t = (float) Math.abs(Math.sin(extra[k] * 7919f));
                sc[n] = t < 0.15f ? 0xFFB8D4FF : t < 0.8f ? 0xFFF2F5FF : 0xFFFFDCA8;
                n++;
            }
        }
        Integer[] order = new Integer[catalogued];
        for (int k = 0; k < catalogued; k++) order[k] = k;
        final float[] bright = sb;
        Arrays.sort(order, (a, b) -> Float.compare(bright[b], bright[a]));
        int live = Math.min(LIVE, catalogued);
        float[] lx = new float[live], ly = new float[live], lb = new float[live], lra = new float[live], ldec = new float[live];
        int[] lc = new int[live];
        boolean[] isLive = new boolean[n];
        for (int k = 0; k < live; k++) {
            int i = order[k];
            isLive[i] = true;
            lx[k] = sx[i];
            ly[k] = sy[i];
            lb[k] = sb[i];
            lc[k] = sc[i];
            lra[k] = sra[i];
            ldec[k] = sdec[i];
        }
        for (int i = 0; i < n; i++) {
            if (isLive[i]) continue;
            int x = (int) sx[i], y = (int) sy[i];
            float b = sb[i], cr = Color.red(sc[i]) / 255f, cg = Color.green(sc[i]) / 255f, cb = Color.blue(sc[i]) / 255f;
            add(px, fw, fh, x, y, b * cr, b * cg, b * cb);
        }
        Bitmap field = Bitmap.createBitmap(fw, fh, Bitmap.Config.ARGB_8888);
        field.setPixels(px, 0, fw, 0, 0, fw, fh);
        return new Sky(field, lx, ly, lb, lc, lra, ldec, mx);
    }

    /** Altitude and azimuth seen at screen point (`sx`, `sy`) of a `w` by `h` screen. */
    private static double[] look(float sx, float sy, int w, float h, double latitude) {
        float perDeg = w / REAL_FOV;
        double facing = latitude >= 0 ? 180 : 0;
        return new double[] {(h * REAL_HORIZON - sy) / perDeg, Heavens.norm(facing + (sx - w / 2f) / perDeg)};
    }

    /** Puts a star at index `n` of (`xs`, `ys`) in field coordinates; false when it is below the horizon or out of frame. */
    private static boolean fix(float ra, float dec, double latitude, double lst, int w, float h, int mx, int my, int fw, int fh,
                                 float[] xs, float[] ys, int n) {
        double[] hz = Heavens.horizontal(ra, dec, latitude, lst);
        if (hz[0] < 0) return false;
        float perDeg = w / REAL_FOV;
        double facing = latitude >= 0 ? 180 : 0, daz = Heavens.norm(hz[1] - facing + 180) - 180;
        float x = (float) (w / 2f + daz * perDeg) + mx, y = (float) (h * REAL_HORIZON - hz[0] * perDeg) + my;
        if (x < 0 || x >= fw || y < 0 || y >= fh) return false;
        xs[n] = x;
        ys[n] = y;
        return true;
    }

    /** Follows the live stars, the planets and the Moon across the night. */
    private void reproject(Sky sk) {
        double[] at = place;
        if (at == null || width == 0) return;
        long now = Wall.millis();
        double lst = Heavens.siderealTime(now, at[1]);
        float h = sk.field.getHeight() - 2 * margin;
        int fw = sk.field.getWidth(), fh = sk.field.getHeight();
        for (int k = 0; k < sk.x.length; k++) {
            if (!fix(sk.ra[k], sk.dec[k], at[0], lst, width, h, sk.marginX, margin, fw, fh, sk.x, sk.y, k)) sk.y[k] = -1e6f;
        }
        double[] moon = Heavens.body(Heavens.MOON, now);
        float[] mxy = new float[2], dummy = new float[1];
        if (fix((float) moon[0], (float) moon[1], at[0], lst, width, h, sk.marginX, margin, fw, fh, mxy, dummy, 0)) {
            moonX = mxy[0];
            moonY = dummy[0];
        } else {
            moonX = Float.NaN;
        }
    }

    /** The Moon, drawn a few times its true size so it reads on a TV, lit as it is tonight. */
    private void drawMoon(Canvas c, Sky sk, float fade) {
        if (Float.isNaN(moonX)) return;
        float x = moonX - sk.marginX - par * width * NEAR, y = moonY + liveTop, r = width / REAL_FOV * 0.65f;
        quad(spriteV, 0, x - r * 3, y, x + r * 3, y, r * 3, r * 3, 0);
        tint(spriteC, 0, Apps.withAlpha(0xFFE8E4D8, Math.round(70 * fade * (0.3f + 0.7f * moonLit))));
        c.drawVertices(Canvas.VertexMode.TRIANGLES, 8, spriteV, 0, spriteT, 0, spriteC, 0, quads, 0, 6, spritePaint);
        moonShade.setColor(Apps.withAlpha(0xFF8A8F9C, Math.round(40 * fade)));
        c.drawCircle(x, y, r, moonShade);
        moonPath.reset();
        moonOval.set(x - r, y - r, x + r, y + r);
        boolean waxing = moonWaxing > 0.5f;
        moonPath.arcTo(moonOval, -90, waxing ? 180 : -180, true);
        float rx = r * Math.abs(1f - 2f * moonLit);
        moonOval.set(x - rx, y - r, x + rx, y + r);
        moonPath.arcTo(moonOval, 90, (moonLit < 0.5f) == waxing ? -180 : 180, false);
        moonPath.close();
        moonPaint.setColor(Apps.withAlpha(0xFFF1EEE4, Math.round(255 * fade)));
        c.drawPath(moonPath, moonPaint);
    }

    private static float brightness(float mag) {
        return Math.min(1.3f, 0.06f + 1.2f * (float) Math.pow(10, -0.4 * (mag - 1.0)));
    }

    private static int starColor(float bv) {
        return bv < 0f ? 0xFFB8D4FF : bv < 0.3f ? 0xFFDCE6FF : bv < 0.8f ? 0xFFF2F5FF : bv < 1.3f ? 0xFFFFE6C0 : 0xFFFFD0A0;
    }

    /**
     * Stars fainter than the catalogue in the one-degree patches of sky marked in `patches`, as dense
     * as the imagined sky and thicker along the Milky Way. Each patch draws its stars from its own
     * seed, so they keep their places on the sky from one bake to the next. Right ascension,
     * declination and brightness for each.
     */
    private static float[] faintStars(boolean[] patches) {
        float[] out = new float[3 * 64];
        int n = 0;
        for (int p = 0; p < patches.length; p++) {
            if (!patches[p]) continue;
            float ra0 = p % 360, dec0 = p / 360 - 90;
            Random r = new Random(p * 0x9E3779B97F4A7C15L + 7);
            double b = Heavens.galactic(ra0 + 0.5, dec0 + 0.5)[0];
            float area = (float) Math.cos(Math.toRadians(dec0 + 0.5));
            int count = (int) (area * (3f + 9f * (float) Math.exp(-(b / 15) * (b / 15))) + r.nextFloat());
            for (int k = 0; k < count; k++) {
                if (n + 3 > out.length) out = Arrays.copyOf(out, out.length * 2);
                out[n] = ra0 + r.nextFloat();
                out[n + 1] = dec0 + r.nextFloat();
                out[n + 2] = (float) Math.pow(r.nextFloat(), 4.2f) * 1.0f + 0.05f;
                n += 3;
            }
        }
        return Arrays.copyOf(out, n);
    }

    private static float seamless(float x, float v, float scale, int tw, int h, int seed, int octaves) {
        float u = x / h * scale, period = (float) tw / h * scale, t = x / tw;
        return fbm(u, v * scale, seed, octaves) * (1 - t) + fbm(u - period, v * scale, seed, octaves) * t;
    }

    private static float bilerp(float[] v, int a, int b, int c, int d, float wx, float wy) {
        float top = v[a] + (v[b] - v[a]) * wx, bottom = v[c] + (v[d] - v[c]) * wx;
        return top + (bottom - top) * wy;
    }

    private static int c255(float v) {
        return Math.max(0, Math.min(255, Math.round(v * 255)));
    }

    private static void add(int[] px, int w, int h, int x, int y, float r, float g, float b) {
        if (y < 0 || y >= h) return;
        x = (x + w) % w;
        int i = y * w + x, c = px[i];
        px[i] = Color.rgb(Math.min(255, Color.red(c) + c255(r)), Math.min(255, Color.green(c) + c255(g)),
                Math.min(255, Color.blue(c) + c255(b)));
    }

    // ------------------------------------------------------------------ per app

    /** A sky whose meteor colour and airglow stand apart from the logo's dominant hue. */
    static Theme theme(int[] sparks, int accent, Random look) {
        float[] hsv = new float[3];
        Color.colorToHSV(sparks[2], hsv);
        float logoHue = hsv[1] > 0.15f ? hsv[0] : look.nextFloat() * 360f;
        int tint = EMISSION[0];
        float best = -1;
        for (int c : EMISSION) {
            Color.colorToHSV(c, hsv);
            float d = Math.abs(((hsv[0] - logoHue) % 360 + 540) % 360 - 180) + look.nextFloat() * 50;
            if (d > best) {
                best = d;
                tint = c;
            }
        }
        float hue = (logoHue + 150 + look.nextFloat() * 60) % 360;
        int airglow = Color.HSVToColor(new float[] {hue, 0.6f, 1f});
        return new Theme(0.15f + look.nextFloat() * 0.7f, -0.35f + look.nextFloat() * 0.45f,
                0.8f + look.nextFloat() * 0.6f, tint, airglow, nebula(hue, airglow, look), glowOnly(airglow));
    }

    /**
     * An app's own nebula: gas clouds folded by domain warping, a second gas in a neighbouring hue,
     * dark dust lanes through both, gathered into one or two regions placed at random. Faint by
     * design: it tints the sky rather than filling it. The glow of the air near the horizon is baked
     * in too: drawn as its own layer over that much screen, it cost this TV's GPU a third of a frame.
     */
    private static Bitmap nebula(float hue, int airglow, Random look) {
        int seed = look.nextInt(1 << 20);
        float[] a = rgb(Color.HSVToColor(new float[] {hue, 0.75f, 1f}));
        float[] b = rgb(Color.HSVToColor(new float[] {(hue + 35 + look.nextFloat() * 50) % 360, 0.65f, 1f}));
        int regions = 1 + look.nextInt(2);
        float[] rx = new float[regions], ry = new float[regions], rw = new float[regions], rh = new float[regions], ra = new float[regions];
        for (int k = 0; k < regions; k++) {
            rx[k] = 0.15f + look.nextFloat() * 0.7f;
            ry[k] = 0.1f + look.nextFloat() * 0.55f;
            rw[k] = 0.22f + look.nextFloat() * 0.3f;
            rh[k] = 0.15f + look.nextFloat() * 0.2f;
            ra[k] = (look.nextFloat() - 0.5f) * 2.4f;
        }
        float strength = 0.2f + look.nextFloat() * 0.08f;
        int[] px = new int[NEB_W * NEB_H];
        for (int y = 0; y < NEB_H; y++) {
            for (int x = 0; x < NEB_W; x++) {
                float u = x / (float) NEB_W, v = y / (float) NEB_H;
                float shape = 0;
                for (int k = 0; k < regions; k++) {
                    float dx = (u - rx[k]) * 1.78f, dy = v - ry[k];
                    float cs = (float) Math.cos(ra[k]), sn = (float) Math.sin(ra[k]);
                    float p = (dx * cs + dy * sn) / rw[k], q = (-dx * sn + dy * cs) / rh[k];
                    shape = Math.max(shape, (float) Math.exp(-(p * p + q * q)));
                }
                if (shape < 0.01f) continue;
                float wx = fbm(u * 3f, v * 3f, seed, 3) * 2f, wy = fbm(u * 3f + 5f, v * 3f, seed + 9, 3) * 2f;
                float gas = fbm(u * 4f + wx, v * 4f + wy, seed + 17, 5);
                float other = fbm(u * 5f - wy, v * 5f + wx, seed + 29, 4);
                float dust = smooth(0.55f, 0.72f, fbm(u * 7f + wx, v * 7f, seed + 41, 4));
                float g = smooth(0.38f, 0.8f, gas) * shape * (1f - 0.75f * dust) * strength;
                float o = smooth(0.45f, 0.85f, other) * shape * (1f - 0.75f * dust) * strength * 0.7f;
                px[y * NEB_W + x] = Color.rgb(c255(a[0] * g + b[0] * o), c255(a[1] * g + b[1] * o), c255(a[2] * g + b[2] * o));
            }
        }
        float[] air = rgb(airglow);
        for (int y = 0; y < NEB_H; y++) {
            float glow = airglowAt(y);
            if (glow <= 0f) continue;
            for (int x = 0; x < NEB_W; x++) {
                int c = px[y * NEB_W + x];
                px[y * NEB_W + x] = Color.rgb(Math.min(255, Color.red(c) + c255(air[0] * glow)),
                        Math.min(255, Color.green(c) + c255(air[1] * glow)), Math.min(255, Color.blue(c) + c255(air[2] * glow)));
            }
        }
        for (int i = 0; i < px.length; i++) if (px[i] == 0) px[i] = 0xFF000000;
        Bitmap bmp = Bitmap.createBitmap(NEB_W, NEB_H, Bitmap.Config.ARGB_8888);
        bmp.setPixels(px, 0, NEB_W, 0, 0, NEB_W, NEB_H);
        return bmp;
    }

    /** How strong the airglow is on nebula row `y`, placed where that row lands on screen at rest. */
    private static float airglowAt(int y) {
        float screen = (y + 0.5f) / NEB_H * NEB_ZOOM - (NEB_ZOOM - 1f) / 2f;
        float t = (screen - 0.45f) / 0.55f;
        return t <= 0f ? 0f : t < 0.55f ? t / 0.55f * 0x18 / 255f : Math.min(1f, (0x18 + (t - 0.55f) / 0.45f * (0x46 - 0x18)) / 255f);
    }

    private static Bitmap glowOnly(int airglow) {
        float[] air = rgb(airglow);
        int[] px = new int[NEB_W * NEB_H];
        for (int y = 0; y < NEB_H; y++) {
            float glow = airglowAt(y);
            java.util.Arrays.fill(px, y * NEB_W, (y + 1) * NEB_W, Color.rgb(c255(air[0] * glow), c255(air[1] * glow), c255(air[2] * glow)));
        }
        Bitmap bmp = Bitmap.createBitmap(NEB_W, NEB_H, Bitmap.Config.ARGB_8888);
        bmp.setPixels(px, 0, NEB_W, 0, 0, NEB_W, NEB_H);
        return bmp;
    }

    private static float[] rgb(int c) {
        return new float[] {Color.red(c) / 255f, Color.green(c) / 255f, Color.blue(c) / 255f};
    }

    void setTheme(Theme next, boolean animate) {
        if (next == theme) return;
        previous = animate ? theme : null;
        theme = next;
        travel = previous == null ? 1f : 0f;
        if (animate && !still && meteors > 0) {
            for (int k = 0; k < 2; k++) meteor(false);
        }
    }

    /**
     * The jump to light speed, from 0 to 1: the bright stars stretch into streaks and fly out past the
     * edges, faster the nearer and the further out they are; as it eases off they keep flying while
     * they fade, and the resting sky fades back in where it was. Nothing is ever pulled back.
     */
    void setWarp(float w) {
        warp = w;
    }

    void setStill(boolean s) {
        still = s;
    }

    void setMeteors(float rate) {
        meteors = rate;
    }

    /**
     * Where the eye looks across the sky, from -1 (far left) to 1, and down it, from 0 (level) to 1;
     * the layers glide there at their depths.
     */
    void setParallax(float x, float y) {
        parTarget = Math.max(-1f, Math.min(1f, x));
        parYTarget = Math.max(0f, Math.min(1f, y));
    }

    /**
     * The weather outside, each from 0 to 1: clouds veil the stars and thin the meteors, rain falls as
     * streaks and snow as flakes in front of the sky. The sky eases into a change over a few seconds.
     */
    void setWeather(float clouds, float rain, float snow) {
        cloudsTarget = clouds;
        rainTarget = rain;
        snowTarget = snow;
    }

    /** Shows each app's nebula, or only the glow of the air on the horizon. */
    void setNebula(boolean on) {
        nebulaOn = on;
    }

    /** Turns the sky with the Earth, or holds it still. */
    void setTurning(boolean on) {
        turning = on;
    }

    /** Whether the sky still needs frames: anything in flight, fading or twinkling. */
    boolean moving() {
        if (introT >= 0f || !still || par != parTarget || parY != parYTarget || rain > 0f || snow > 0f
                || clouds != cloudsTarget || rain != rainTarget || snow != snowTarget || warp > 0 || travel < 1f || skyIn < 1f || satLife > 0) return true;
        for (int i = 0; i < METEORS; i++) if (mAge[i] < mLife[i]) return true;
        return false;
    }

    /**
     * The first sight of the sky after a boot: the bright stars come out one by one, each with a
     * flicker, the Milky Way rises behind them, then a burst of meteors leaves the radiant.
     */
    void intro() {
        introT = 0f;
        skyIn = 0f;
        burst = 26;
        nextMeteor = 1.5f;
    }

    /** A meteor storm for the next `seconds`. */
    void storm(float seconds) {
        storm = seconds;
    }

    // ------------------------------------------------------------------ motion

    /**
     * A meteor somewhere on screen, flying straight away from the radiant: the further from the
     * radiant, the longer its trail, as perspective draws parallel paths.
     */
    private void meteor(boolean fireball) {
        if (theme == null || width == 0) return;
        int i = meteorNext;
        meteorNext = (meteorNext + 1) % METEORS;
        float rx = theme.radiantX * width, ry = theme.radiantY * height;
        float x = width * (0.05f + rnd.nextFloat() * 0.9f), y = height * (0.02f + rnd.nextFloat() * 0.7f);
        float dx = x - rx, dy = y - ry, d = (float) Math.sqrt(dx * dx + dy * dy) + 1f;
        mx[i] = x;
        my[i] = y;
        mdx[i] = dx / d;
        mdy[i] = dy / d;
        float reach = Math.min(1f, d / (height * 0.9f));
        mSpeed[i] = (700 + rnd.nextFloat() * 900) * s * (0.5f + reach);
        mLen[i] = (60 + rnd.nextFloat() * 260) * s * (0.3f + reach) * (fireball ? 1.8f : 1f);
        mLife[i] = (0.35f + rnd.nextFloat() * 0.5f) * (fireball ? 1.6f : 1f);
        mWidth[i] = (1.1f + rnd.nextFloat() * 1.2f) * s * (fireball ? 2.4f : 1f);
        mGain[i] = fireball ? 1f : 0.45f + (float) Math.pow(rnd.nextFloat(), 2) * 0.55f;
        mTint[i] = fireball || rnd.nextInt(3) == 0 ? theme.tint : 0xFFDDE6FF;
        mAge[i] = 0;
    }

    void update(float dt) {
        clock += dt;
        float glide = 1f - (float) Math.exp(-dt * 3.5f);
        warpTravel = warp > 0f ? warpTravel + dt * warp * warp * width * 1.4f : 0f;
        Sky real = sky;
        if (real != null && real.real()) {
            fieldFade = Math.min(1f, fieldFade + dt / REAL_FADE_S);
            if ((realClock += dt) >= REAL_REPROJECT_S) {
                realClock = 0f;
                reproject(real);
            }
            if ((bakeClock += dt) >= REAL_REBAKE_S) {
                bakeClock = 0f;
                rebuild();
            }
        } else if (real != null && place != null && catalog != null && !baking) {
            rebuild();
        }
        clouds = toward(clouds, cloudsTarget, dt / 4f);
        rain = toward(rain, rainTarget, dt / 4f);
        snow = toward(snow, snowTarget, dt / 4f);
        fall(dt);
        par += (parTarget - par) * glide;
        if (Math.abs(parTarget - par) < 0.0005f) par = parTarget;
        parY += (parYTarget - parY) * glide;
        if (Math.abs(parYTarget - parY) < 0.0005f) parY = parYTarget;
        if (!still) drift += dt;
        if (introT >= 0f) {
            introT += dt;
            float rise = smooth(0.3f, 2.4f, introT);
            skyIn = sky != null ? Math.min(rise, skyIn + dt / 0.8f) : 0f;
            if (introT > 2.4f && skyIn >= rise) introT = -1f;
        } else if (sky != null) {
            skyIn = Math.min(1f, skyIn + dt / 0.8f);
        }
        if (travel < 1f) travel = Math.min(1f, travel + dt / TRAVEL_T);
        storm = Math.max(0f, storm - dt);
        for (int i = 0; i < METEORS; i++) {
            if (mAge[i] < mLife[i]) mAge[i] += dt;
        }
        nextMeteor -= dt;
        if (nextMeteor <= 0 && theme != null && !still && meteors > 0) {
            meteor(rnd.nextInt(30) == 0 || burst % 9 == 4);
            float rate = theme.rate * meteors * (storm > 0 ? 9f * Math.min(1f, storm) : 1f) * (1f - 0.9f * clouds);
            nextMeteor = burst > 0 ? 0.02f + rnd.nextFloat() * 0.05f : (float) (-Math.log(1 - rnd.nextFloat() * 0.99f) / rate);
            if (burst > 0) burst--;
        }
        if (still) return;
        nextSatellite -= dt;
        if (nextSatellite <= 0 && width > 0) {
            boolean ltr = rnd.nextBoolean();
            satX = ltr ? -10 : width + 10;
            satY = height * (0.1f + rnd.nextFloat() * 0.45f);
            float a = (rnd.nextFloat() - 0.5f) * 0.5f, v = (55 + rnd.nextFloat() * 35) * s;
            satDx = (float) Math.cos(a) * v * (ltr ? 1 : -1);
            satDy = (float) Math.sin(a) * v;
            satLife = (width + 20) / Math.abs(satDx);
            nextSatellite = 25f + rnd.nextFloat() * 30f;
        }
        if (satLife > 0) {
            satLife -= dt;
            satX += satDx * dt;
            satY += satDy * dt;
        }
        nextTrain -= dt;
        if (nextTrain <= 0 && width > 0) {
            boolean ltr = rnd.nextBoolean();
            trainX = ltr ? -40 * s : width + 40 * s;
            trainY = height * (0.08f + rnd.nextFloat() * 0.35f);
            float a = (rnd.nextFloat() - 0.5f) * 0.4f, v = (70 + rnd.nextFloat() * 30) * s;
            trainDx = (float) Math.cos(a) * v * (ltr ? 1 : -1);
            trainDy = (float) Math.sin(a) * v;
            trainLife = (width + 40 * s * TRAIN) / Math.abs(trainDx);
            nextTrain = 150f + rnd.nextFloat() * 150f;
        }
        if (trainLife > 0) {
            trainLife -= dt;
            trainX += trainDx * dt;
            trainY += trainDy * dt;
        }
        Sky sk = sky;
        if (nova >= 0) {
            novaT += dt;
            if (novaT > NOVA_T) nova = -1;
        } else if (sk != null && (nextNova -= dt) <= 0) {
            nova = rnd.nextInt(Math.min(sk.x.length, 900));
            novaT = 0f;
            nextNova = 30f + rnd.nextFloat() * 40f;
        }
    }

    // ------------------------------------------------------------------ drawing

    private static void quad(float[] v, int q, float x0, float y0, float x1, float y1, float h0, float h1, float e) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        float ux = len < 0.001f ? 1 : dx / len, uy = len < 0.001f ? 0 : dy / len;
        float ex = ux * e, ey = uy * e;
        int o = q * 8;
        v[o] = x0 - ex - uy * h0;
        v[o + 1] = y0 - ey + ux * h0;
        v[o + 2] = x1 + ex - uy * h1;
        v[o + 3] = y1 + ey + ux * h1;
        v[o + 4] = x1 + ex + uy * h1;
        v[o + 5] = y1 + ey - ux * h1;
        v[o + 6] = x0 - ex + uy * h0;
        v[o + 7] = y0 - ey - ux * h0;
    }

    private static void tint(int[] c, int q, int color) {
        int o = q * 4;
        c[o] = color;
        c[o + 1] = color;
        c[o + 2] = color;
        c[o + 3] = color;
    }

    /**
     * How far the sky has turned, in pixels, at the Earth's real rate (one turn per sidereal day)
     * for a view about 50 degrees wide; taken from the wall clock so it carries on across restarts.
     * Drawn on whole pixels: a filtered half-pixel offset smears every one-pixel star of the field
     * into two at half brightness, and costs a filtered full-screen pass each frame.
     */
    private double turned() {
        return turning ? Wall.millis() / 1000.0 * SIDEREAL_DEG_PER_S * width / VIEW_DEG : 0.0;
    }

    /** The whole sky, behind the logo. */
    void drawBack(Canvas c) {
        c.drawColor(0xFF000000);
        Sky sk = sky;
        float fade = skyIn * (1f - 0.7f * warp) * (1f - 0.6f * clouds);
        float left = 0f, near = 0f;
        if (sk != null) {
            int tw = sk.field.getWidth();
            double turn = sk.real() ? -sk.marginX : turned();
            left = (float) Math.floor(wrap(turn - par * width * FAR, tw));
            near = (float) wrap(turn - par * width * NEAR, tw);
            float top = -margin - (float) Math.floor(parY * height * FAR_Y);
            liveTop = -margin - parY * height * NEAR_Y;
            Bitmap old = sk.real() && fieldFade < 1f ? fadingField : null;
            if (old != null && old.getWidth() == tw) {
                blit.setAlpha(Math.round(255 * fade));
                if (left > 0f) c.drawBitmap(old, left - tw, top, blit);
                if (left < width) c.drawBitmap(old, left, top, blit);
            }
            blit.setAlpha(Math.round(255 * fade * (old != null ? fieldFade : 1f)));
            if (left > 0f) c.drawBitmap(sk.field, left - tw, top, blit);
            if (left < width) c.drawBitmap(sk.field, left, top, blit);
        }
        float mid = -par * width * MID, midY = -parY * height * MID_Y;
        if (previous != null && travel < 1f) drawNebula(c, nebulaOn ? previous.nebula : previous.glow, (1 - travel) * fade, mid, midY);
        if (theme != null) drawNebula(c, nebulaOn ? theme.nebula : theme.glow, (previous != null ? travel : 1f) * fade, mid, midY);
        if (sk != null) drawLive(c, sk, near, fade);
        drawSprites(c, sk, near);
        if (sk != null && sk.real()) drawMoon(c, sk, fade);
        c.save();
        c.translate(-par * width * NEAR, -parY * height * NEAR_Y);
        drawTrails(c);
        c.restore();
        drawPrecipitation(c);
    }

    private static float toward(float v, float target, float step) {
        return v < target ? Math.min(target, v + step) : Math.max(target, v - step);
    }

    private void fall(float dt) {
        if (width == 0 || rain <= 0f && snow <= 0f) return;
        if (!dropsPlaced) {
            for (int i = 0; i < PRECIP; i++) {
                dropX[i] = rnd.nextFloat() * width;
                dropY[i] = rnd.nextFloat() * height;
                dropS[i] = rnd.nextFloat();
            }
            dropsPlaced = true;
        }
        boolean raining = rain >= snow;
        for (int i = 0; i < PRECIP; i++) {
            float v = raining ? (900 + 500 * dropS[i]) * s : (45 + 55 * dropS[i]) * s;
            dropY[i] += v * dt;
            dropX[i] += raining ? -0.18f * v * dt : (float) Math.sin(clock * 0.8f + dropS[i] * 20f) * 22 * s * dt;
            if (dropY[i] > height + 40 * s) {
                dropY[i] = -40 * s * rnd.nextFloat();
                dropX[i] = rnd.nextFloat() * width * 1.15f;
            }
            if (dropX[i] < -20 * s) dropX[i] += width + 40 * s;
            else if (dropX[i] > width + 20 * s) dropX[i] -= width + 40 * s;
        }
    }

    /** Rain as slanted streaks, snow as soft flakes, as many as the weather is strong. */
    private void drawPrecipitation(Canvas c) {
        float strength = Math.max(rain, snow);
        int n = Math.round(strength * PRECIP);
        if (n == 0 || !dropsPlaced) return;
        boolean raining = rain >= snow;
        int q = 0;
        for (int i = 0; i < n; i++) {
            float x = dropX[i], y = dropY[i];
            if (raining) {
                float len = (26 + 34 * dropS[i]) * s;
                quad(dropV, q, x + 0.18f * len, y - len, x, y, 0.6f * s, 0.9f * s, 0);
                tint(dropC, q++, Apps.withAlpha(0xFFBFD3FF, Math.round(95 * strength * skyIn)));
            } else {
                float r = (5f + 6f * dropS[i]) * s;
                quad(dropV, q, x - r, y, x + r, y, r, r, 0);
                tint(dropC, q++, Apps.withAlpha(0xFFFFFFFF, Math.round(230 * strength * skyIn)));
            }
        }
        if (raining) {
            c.drawVertices(Canvas.VertexMode.TRIANGLES, q * 8, dropV, 0, null, 0, dropC, 0, quads, 0, q * 6, addPaint);
        } else {
            c.drawVertices(Canvas.VertexMode.TRIANGLES, q * 8, dropV, 0, dropT, 0, dropC, 0, quads, 0, q * 6, spritePaint);
        }
    }

    private static double wrap(double x, int tw) {
        double m = x % tw;
        return m < 0 ? m + tw : m;
    }

    /** The nebula, drifting and breathing very slowly. */
    private void drawNebula(Canvas c, Bitmap nebula, float alpha, float shift, float shiftY) {
        if (nebula == null || alpha <= 0.01f || width == 0) return;
        float sx = (float) width / NEB_W, sy = (float) height / NEB_H;
        float zoom = NEB_ZOOM + 0.03f * (float) Math.sin(drift * 0.013f);
        float ox = (float) Math.sin(drift * 0.021f) * width * 0.015f, oy = (float) Math.cos(drift * 0.017f) * height * 0.012f;
        nebulaMatrix.setScale(sx * zoom, sy * zoom);
        nebulaMatrix.postTranslate(-(zoom - 1) * width / 2 + ox + shift, -(zoom - 1) * height / 2 + oy + shiftY);
        nebulaPaint.setAlpha(Math.round(255 * alpha));
        c.drawBitmap(nebula, nebulaMatrix, nebulaPaint);
    }

    /** Where a star of the sky (in its own, wider, coordinates) is on screen now; NaN when off screen. */
    private float screenX(Sky sk, int i, float left) {
        float tw = sk.field.getWidth(), x = (sk.x[i] + left) % tw;
        return x < width + 4 ? x : Float.NaN;
    }

    /**
     * The bright stars, live: each scintillates at its own rhythm, harder the lower it sits, as
     * starlight crosses more air near the horizon; one may be flaring as a nova.
     */
    private void drawLive(Canvas c, Sky sk, float left, float fade) {
        int q = 0, n = sk.x.length;
        for (int i = 0; i < n; i++) {
            float x = screenX(sk, i, left);
            if (Float.isNaN(x)) continue;
            float y = sk.y[i] + liveTop, b = sk.b[i];
            float depth = 0.3f + 0.7f * y / height;
            float scint = still ? 1f : 1f + liveAmp[i] * depth * (0.6f * (float) Math.sin(clock * liveRate[i] + livePhase[i])
                    + 0.4f * (float) Math.sin(clock * liveRate[i] * 1.7f + livePhase[i] * 3f));
            float shown = 1f;
            if (introT >= 0f) {
                float age = introT - liveBirth[i];
                if (age <= 0f) continue;
                shown = Math.min(1f, age / 0.15f);
                scint = Math.max(scint, 1f + 2f * (float) Math.exp(-age * 6f));
            }
            if (i == nova) scint *= 1f + 5f * novaGlow();
            float r = 0.55f + 0.75f * Math.min(1.2f, b);
            int alpha = Math.round(255 * Math.min(1f, b * 0.85f * scint) * shown * (introT >= 0f ? 1f : fade));
            if (alpha <= 2) continue;
            quad(liveV, q, x - r, y, x + r, y, r, r, 0);
            tint(liveC, q++, Apps.withAlpha(sk.color[i], alpha));
        }
        if (q > 0) c.drawVertices(Canvas.VertexMode.TRIANGLES, q * 8, liveV, 0, null, 0, liveC, 0, quads, 0, q * 6, addPaint);
    }

    /** A nova's light curve: a fast rise, then a slow fade. */
    private float novaGlow() {
        return Math.min(1f, novaT / 1.5f) * (float) Math.exp(-Math.max(0f, novaT - 1.5f) * 0.55f);
    }

    private void drawSprites(Canvas c, Sky sk, float left) {
        int q = 0;
        float cx = logo.centerX(), cy = logo.centerY();
        int bright = sk == null ? 0 : Math.min(TWINKLERS, sk.x.length);
        for (int i = 0; i < bright; i++) {
            float x = screenX(sk, i, left);
            if (Float.isNaN(x)) continue;
            float y = sk.y[i] + liveTop, z = Math.min(1f, sk.b[i] / 1.2f);
            float depth = 0.3f + 0.7f * y / height;
            float scint = still ? 0.85f : 0.85f + 0.15f * liveAmp[i] * depth * 2f * (float) Math.sin(clock * liveRate[i] + livePhase[i]);
            float size = (1.6f + 3.6f * z) * s;
            float shown = 1f;
            if (introT >= 0f) {
                float age = introT - liveBirth[i];
                if (age <= 0f) continue;
                shown = Math.min(1f, age / 0.15f);
                size *= 1f + 2.2f * (float) Math.exp(-age * 7f);
                scint = Math.max(scint, (float) Math.exp(-age * 5f));
            }
            int alpha = Math.round((40 + 120 * z) * scint * shown * (introT >= 0f ? 1f : skyIn));
            if (warp > 0.05f) {
                float dx = x - cx, dy = y - cy, d = (float) Math.sqrt(dx * dx + dy * dy) + 0.001f;
                float out = warpTravel * (0.4f + 0.8f * z) * (0.3f + d / (width * 0.5f));
                float hx = x + dx / d * out, hy = y + dy / d * out, len = warp * (30 + 260 * z) * s;
                if (hx > -len && hx < width + len && hy > -len && hy < height + len) {
                    quad(spriteV, q, hx - dx / d * len, hy - dy / d * len, hx, hy, size * 0.6f, size * 0.6f, size);
                    tint(spriteC, q++, Apps.withAlpha(sk.color[i], Math.round(alpha * Math.min(1f, warp * 1.5f))));
                }
            }
            if (warp < 1f) {
                quad(spriteV, q, x - size, y, x + size, y, size, size, 0);
                tint(spriteC, q++, Apps.withAlpha(sk.color[i], Math.round(alpha * (1f - warp))));
            }
            if (i < SPIKED && warp <= 0.05f) {
                float spike = size * (4f + 2f * scint);
                int faint = Apps.withAlpha(sk.color[i], Math.round(80 * scint * skyIn));
                quad(spriteV, q, x - spike, y, x + spike, y, size * 0.22f, size * 0.22f, 0);
                tint(spriteC, q++, faint);
                quad(spriteV, q, x, y - spike, x, y + spike, size * 0.22f, size * 0.22f, 0);
                tint(spriteC, q++, faint);
            }
        }
        if (sk != null && nova >= 0) {
            float x = screenX(sk, nova, left), g = novaGlow();
            if (!Float.isNaN(x)) {
                float r = (6f + 22f * g) * s;
                quad(spriteV, q, x - r, sk.y[nova] + liveTop, x + r, sk.y[nova] + liveTop, r, r, 0);
                tint(spriteC, q++, Apps.withAlpha(0xFFFFF2E6, Math.round(200 * g * skyIn)));
            }
        }
        for (int i = 0; i < METEORS; i++) {
            if (mAge[i] >= mLife[i]) continue;
            float u = mAge[i] / mLife[i], k = envelope(u) * mGain[i];
            float x = mx[i] + mdx[i] * mSpeed[i] * mAge[i], y = my[i] + mdy[i] * mSpeed[i] * mAge[i];
            float r = mWidth[i] * (3f + 4f * k);
            quad(spriteV, q, x - r, y, x + r, y, r, r, 0);
            tint(spriteC, q++, Apps.withAlpha(Apps.mix(mTint[i], 0xFFFFFFFF, 0.6f), Math.round(255 * k)));
        }
        if (satLife > 0) {
            float flare = (float) Math.exp(-Math.pow((satLife % 9f) - 4.5f, 2) * 3f);
            float r = (1.6f + 2.5f * flare) * s;
            quad(spriteV, q, satX - r, satY, satX + r, satY, r, r, 0);
            tint(spriteC, q++, Apps.withAlpha(0xFFFFF4E0, Math.round(110 + 145 * flare)));
        }
        if (trainLife > 0) {
            float len = (float) Math.sqrt(trainDx * trainDx + trainDy * trainDy) + 0.001f;
            for (int k = 0; k < TRAIN; k++) {
                float back = k * 34f * s, x = trainX - trainDx / len * back, y = trainY - trainDy / len * back;
                float r = (1.3f + 0.5f * ((k * 7) % 3)) * s;
                quad(spriteV, q, x - r, y, x + r, y, r, r, 0);
                tint(spriteC, q++, Apps.withAlpha(0xFFF4F1E8, 70 + (k * 37) % 50));
            }
        }
        if (q > 0) c.drawVertices(Canvas.VertexMode.TRIANGLES, q * 8, spriteV, 0, spriteT, 0, spriteC, 0, quads, 0, q * 6, spritePaint);
    }

    /** Brightens fast as the meteor hits the air, then burns out. */
    private static float envelope(float u) {
        return Math.min(1f, u * 6f) * (1f - u * u);
    }

    private void drawTrails(Canvas c) {
        int q = 0;
        for (int i = 0; i < METEORS; i++) {
            if (mAge[i] >= mLife[i]) continue;
            float u = mAge[i] / mLife[i], k = envelope(u) * mGain[i];
            float travelled = mSpeed[i] * mAge[i];
            float hx = mx[i] + mdx[i] * travelled, hy = my[i] + mdy[i] * travelled;
            float len = Math.min(mLen[i], travelled) * (1f - 0.4f * u);
            float sx = hx - mdx[i] * len, sy = hy - mdy[i] * len;
            quad(trailV, q, sx, sy, hx, hy, 0f, mWidth[i], 0);
            int o = q * 4;
            int tail = Apps.withAlpha(mTint[i], 0);
            int head = Apps.withAlpha(Apps.mix(mTint[i], 0xFFFFFFFF, 0.45f), Math.round(255 * k));
            trailC[o] = tail;
            trailC[o + 1] = head;
            trailC[o + 2] = head;
            trailC[o + 3] = tail;
            q++;
        }
        if (q > 0) {
            c.drawVertices(Canvas.VertexMode.TRIANGLES, q * 8, trailV, 0, null, 0, trailC, 0, quads, 0, q * 6, addPaint);
        }
    }
}
