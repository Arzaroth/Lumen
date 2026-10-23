package dev.arzaroth.lumen;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

import java.util.Random;

/**
 * Deep space behind the logo: a night sky rendered once at screen resolution (thousands of stars,
 * the Milky Way with its star clouds and dust lanes), bright stars that scintillate, and a meteor
 * shower streaming out of a radiant. Each app gets its own radiant, meteor colour and airglow,
 * chosen to stand apart from the logo.
 */
final class Cosmos {
    private static final int TWINKLERS = 240, SPIKED = 10, METEORS = 48, SPRITE = 64;
    private static final int QUADS = TWINKLERS + 2 * SPIKED + METEORS + 1;
    private static final float TRAVEL_T = 1.2f;
    private static final double SIDEREAL_DEG_PER_S = 360.0 / 86164.0905, VIEW_DEG = 50.0;
    private static final int[] EMISSION = {0xFF7CFFB0, 0xFF8FD8FF, 0xFFFFC27A, 0xFFC9A2FF, 0xFFB6FFF0};

    /** One app's sky: where its meteors come from, their glow, and the airglow on the horizon. */
    static final class Theme {
        final float radiantX, radiantY, rate;
        final int tint, airglow;

        Theme(float radiantX, float radiantY, float rate, int tint, int airglow) {
            this.radiantX = radiantX;
            this.radiantY = radiantY;
            this.rate = rate;
            this.tint = tint;
            this.airglow = airglow;
        }
    }

    private final float s;
    private final Random rnd = new Random();
    private final RectF logo = new RectF();
    private int width, height;
    private volatile Bitmap sky;
    private float skyIn, introT = -1f;
    private final float[] tBirth = new float[TWINKLERS];
    private int burst;

    private Theme theme, previous;
    private Shader glowNow, glowBefore;
    private float travel = 1f, clock, warp, storm, nextMeteor = 1f, nextSatellite = 12f, meteors = 1f;
    private boolean still;

    private final float[] tx = new float[TWINKLERS], ty = new float[TWINKLERS], tz = new float[TWINKLERS];
    private final float[] tPhase = new float[TWINKLERS], tRate = new float[TWINKLERS];
    private final int[] tTint = new int[TWINKLERS];

    private final float[] mx = new float[METEORS], my = new float[METEORS], mdx = new float[METEORS], mdy = new float[METEORS];
    private final float[] mSpeed = new float[METEORS], mLen = new float[METEORS], mLife = new float[METEORS];
    private final float[] mAge = new float[METEORS], mWidth = new float[METEORS], mGain = new float[METEORS];
    private final int[] mTint = new int[METEORS];
    private int meteorNext;

    private float satX, satY, satDx, satDy, satLife;

    private final float[] spriteV = new float[QUADS * 8], spriteT = new float[QUADS * 8];
    private final int[] spriteC = new int[QUADS * 4];
    private final float[] trailV = new float[METEORS * 8];
    private final int[] trailC = new int[METEORS * 4];
    private final short[] quads = new short[QUADS * 6];

    private final Paint spritePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint trailPaint = new Paint();
    private final Paint blit = new Paint(Paint.FILTER_BITMAP_FLAG);
    private boolean turning;
    private final Paint glowPaint = new Paint();

    Cosmos(float scale) {
        s = scale;
        PorterDuffXfermode add = new PorterDuffXfermode(PorterDuff.Mode.ADD);
        spritePaint.setShader(new BitmapShader(sprite(), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        spritePaint.setXfermode(add);
        trailPaint.setXfermode(add);
        for (int q = 0; q < QUADS; q++) {
            int v = q * 4, t = q * 8, i = q * 6;
            spriteT[t + 2] = SPRITE;
            spriteT[t + 4] = SPRITE;
            spriteT[t + 5] = SPRITE;
            spriteT[t + 7] = SPRITE;
            quads[i] = (short) v;
            quads[i + 1] = (short) (v + 1);
            quads[i + 2] = (short) (v + 2);
            quads[i + 3] = (short) v;
            quads[i + 4] = (short) (v + 2);
            quads[i + 5] = (short) (v + 3);
        }
        for (int i = 0; i < TWINKLERS; i++) {
            tx[i] = rnd.nextFloat();
            ty[i] = rnd.nextFloat() * 0.92f;
            tz[i] = i < SPIKED ? 0.85f + rnd.nextFloat() * 0.15f : (float) Math.pow(rnd.nextFloat(), 2.2f);
            tPhase[i] = rnd.nextFloat() * 100f;
            tRate[i] = 2f + rnd.nextFloat() * 7f;
            float k = rnd.nextFloat();
            tTint[i] = k < 0.5f ? 0xFFFFFFFF : k < 0.75f ? 0xFFC4D8FF : k < 0.92f ? 0xFFFFE6C0 : 0xFFFFC2A0;
        }
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
        new Thread(() -> sky = sky(w, h), "lumen-sky").start();
        if (theme != null) glowNow = airglow(theme);
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
     * (soft light computed coarse, star clouds and the dark rift on top), then every star as single
     * crisp pixels, denser inside the band.
     */
    private static Bitmap sky(int w, int h) {
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
        int stars = tw * h / 35;
        for (int n = 0; n < stars; n++) {
            int x = r.nextInt(tw), y = r.nextInt(h);
            float d = dens[(y / 4) * gw + x / 4];
            if (r.nextFloat() > 0.35f + 0.65f * d) continue;
            float b = (float) Math.pow(r.nextFloat(), 4.2f) * 1.2f + 0.06f + d * 0.05f;
            float k = r.nextFloat();
            float cr = k < 0.15f ? 0.72f : k < 0.8f ? 0.95f : 1f;
            float cg = k < 0.15f ? 0.83f : k < 0.8f ? 0.96f : 0.86f;
            float cb = k < 0.15f ? 1f : k < 0.8f ? 1f : 0.66f;
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
        Bitmap bmp = Bitmap.createBitmap(tw, h, Bitmap.Config.ARGB_8888);
        bmp.setPixels(px, 0, tw, 0, 0, tw, h);
        return bmp;
    }

    /** Fractal noise at (x, v) that repeats every `tw` pixels across: two copies blended over the period. */
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
        int airglow = Color.HSVToColor(new float[] {(logoHue + 150 + look.nextFloat() * 60) % 360, 0.6f, 1f});
        return new Theme(0.15f + look.nextFloat() * 0.7f, -0.35f + look.nextFloat() * 0.45f,
                0.8f + look.nextFloat() * 0.6f, tint, airglow);
    }

    private Shader airglow(Theme t) {
        if (height == 0) return null;
        return new LinearGradient(0, height * 0.45f, 0, height,
                new int[] {0x00000000, Apps.withAlpha(t.airglow, 0x18), Apps.withAlpha(t.airglow, 0x46)},
                new float[] {0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
    }

    void setTheme(Theme next, boolean animate) {
        if (next == theme) return;
        previous = animate ? theme : null;
        glowBefore = glowNow;
        theme = next;
        glowNow = airglow(next);
        travel = previous == null ? 1f : 0f;
        if (animate && !still && meteors > 0) {
            for (int k = 0; k < 2; k++) meteor(false);
        }
    }

    void setWarp(float w) {
        warp = w;
    }

    void setStill(boolean s) {
        still = s;
    }

    void setMeteors(float rate) {
        meteors = rate;
    }

    /** Whether the sky still needs frames: anything in flight, fading or twinkling. */
    boolean moving() {
        if (introT >= 0f || !still || warp > 0 || travel < 1f || skyIn < 1f || satLife > 0) return true;
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
        for (int i = 0; i < TWINKLERS; i++) tBirth[i] = 0.15f + (float) Math.pow(rnd.nextFloat(), 0.8f) * 1.7f;
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
            float rate = theme.rate * meteors * (storm > 0 ? 9f * Math.min(1f, storm) : 1f);
            nextMeteor = burst > 0 ? 0.02f + rnd.nextFloat() * 0.05f : (float) (-Math.log(1 - rnd.nextFloat() * 0.99f) / rate);
            if (burst > 0) burst--;
        }
        nextSatellite -= dt;
        if (nextSatellite <= 0 && width > 0 && !still) {
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
        if (warp > 0) {
            float ox = logo.centerX() / Math.max(1, width), oy = logo.centerY() / Math.max(1, height);
            for (int i = 0; i < TWINKLERS; i++) {
                float dx = tx[i] - ox, dy = ty[i] - oy, d = (float) Math.sqrt(dx * dx + dy * dy) + 0.001f;
                float v = warp * dt * (0.3f + tz[i]) * 1.6f;
                tx[i] += dx / d * v;
                ty[i] += dy / d * v;
                if (tx[i] < -0.05f || tx[i] > 1.05f || ty[i] < -0.05f || ty[i] > 1.05f) {
                    tx[i] = ox + (rnd.nextFloat() - 0.5f) * 0.15f;
                    ty[i] = oy + (rnd.nextFloat() - 0.5f) * 0.15f;
                }
            }
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

    /** The whole sky, behind the logo. */
    void drawBack(Canvas c) {
        c.drawColor(0xFF000000);
        Bitmap b = sky;
        if (b != null) {
            blit.setAlpha(Math.round(255 * skyIn * (1f - 0.7f * warp)));
            int tw = b.getWidth();
            float left = (float) (turned() % tw);
            c.drawBitmap(b, left - tw, 0, blit);
            c.drawBitmap(b, left, 0, blit);
        }
        if (glowBefore != null && travel < 1f) {
            glowPaint.setShader(glowBefore);
            glowPaint.setAlpha(Math.round(255 * (1 - travel)));
            c.drawRect(0, height * 0.45f, width, height, glowPaint);
        }
        if (glowNow != null) {
            glowPaint.setShader(glowNow);
            glowPaint.setAlpha(Math.round(255 * travel * skyIn * (0.85f + 0.15f * (float) Math.sin(clock * 0.3f))));
            c.drawRect(0, height * 0.45f, width, height, glowPaint);
        }
        drawSprites(c);
        drawTrails(c);
    }

    /** Turns the sky with the Earth, or holds it still. */
    void setTurning(boolean on) {
        turning = on;
    }

    /**
     * How far the sky has turned, in pixels, at the Earth's real rate (one turn per sidereal day)
     * for a view about 50 degrees wide; taken from the wall clock so it carries on across restarts.
     * Drawn at that fractional offset, so it glides rather than stepping a pixel at a time.
     */
    private double turned() {
        return turning ? System.currentTimeMillis() / 1000.0 * SIDEREAL_DEG_PER_S * width / VIEW_DEG : 0.0;
    }

    private void drawSprites(Canvas c) {
        int q = 0;
        float cx = logo.centerX(), cy = logo.centerY();
        float drift = width > 0 ? (float) (turned() % width) : 0f;
        for (int i = 0; i < TWINKLERS; i++) {
            float x = (tx[i] * width + drift) % width, y = ty[i] * height, z = tz[i];
            float scint = still ? 0.85f : 0.7f + 0.3f * (float) Math.sin(clock * tRate[i] + tPhase[i])
                    * (float) Math.sin(clock * tRate[i] * 0.37f + tPhase[i] * 2f);
            float size = (1.4f + 3.4f * z) * s;
            float shown = 1f;
            if (introT >= 0f) {
                float age = introT - tBirth[i];
                if (age <= 0f) continue;
                shown = Math.min(1f, age / 0.15f);
                size *= 1f + 2.2f * (float) Math.exp(-age * 7f);
                scint = Math.max(scint, (float) Math.exp(-age * 5f));
            }
            int color = Apps.withAlpha(tTint[i], Math.round((70 + 185 * z) * scint * shown * (introT >= 0f ? 1f : skyIn)));
            if (warp > 0.05f) {
                float dx = x - cx, dy = y - cy, d = (float) Math.sqrt(dx * dx + dy * dy) + 0.001f;
                float len = warp * (30 + 260 * z) * s;
                quad(spriteV, q, x - dx / d * len, y - dy / d * len, x, y, size * 0.6f, size * 0.6f, size);
            } else {
                quad(spriteV, q, x - size, y, x + size, y, size, size, 0);
            }
            tint(spriteC, q++, color);
            if (i < SPIKED && warp <= 0.05f) {
                float spike = size * (4f + 2f * scint);
                int faint = Apps.withAlpha(tTint[i], Math.round(90 * scint * skyIn));
                quad(spriteV, q, x - spike, y, x + spike, y, size * 0.22f, size * 0.22f, 0);
                tint(spriteC, q++, faint);
                quad(spriteV, q, x, y - spike, x, y + spike, size * 0.22f, size * 0.22f, 0);
                tint(spriteC, q++, faint);
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
        c.drawVertices(Canvas.VertexMode.TRIANGLES, q * 8, spriteV, 0, spriteT, 0, spriteC, 0, quads, 0, q * 6, spritePaint);
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
            c.drawVertices(Canvas.VertexMode.TRIANGLES, q * 8, trailV, 0, null, 0, trailC, 0, quads, 0, q * 6, trailPaint);
        }
    }
}
