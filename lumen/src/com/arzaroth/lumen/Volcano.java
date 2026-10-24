package com.arzaroth.lumen;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

import java.util.Random;

/**
 * A volcano erupting at dusk, after long exposures of Anak Krakatau: a broad cone rising from the sea
 * under a blue-grey sky, a column of incandescence over its summit mirrored in the water, lava bombs
 * fountaining out in glowing arcs and rolling down the flanks, rockfall streaking the slopes and a
 * field of glowing debris at their foot. Each app tints the fire with its own colour (blue for a blue
 * logo, like the sulphur flames of Kawah Ijen) and has its own ash plume.
 *
 * The landscape is baked once per screen; per frame it is one blit, the plume over the part of the
 * screen it covers, the fire's white mask tinted over the volcano, and one mesh of particles.
 */
final class Volcano implements Backdrop {
    static final class Look implements Backdrop.Look {
        final int lava;
        final Bitmap plume;
        final float wind;

        Look(int lava, Bitmap plume, float wind) {
            this.lava = lava;
            this.plume = plume;
            this.wind = wind;
        }
    }

    /** The landscape, built once per screen: the baked scene, the fire's mask, the summit and the ground's profile. */
    private static final class Land {
        final Bitmap field, fire;
        final RectF fireAt;
        final float craterX, craterY, craterW;
        final float[] ground;

        Land(Bitmap field, Bitmap fire, RectF fireAt, float craterX, float craterY, float craterW, float[] ground) {
            this.field = field;
            this.fire = fire;
            this.fireAt = fireAt;
            this.craterX = craterX;
            this.craterY = craterY;
            this.craterW = craterW;
            this.ground = ground;
        }
    }

    /** Embers and fire: white-hot to deep orange, a smoky red-black behind the update. */
    static final Palette PALETTE = new Palette(0xFFFFF1C2, 0xFFFF9A3C, new int[] {0xFFFFE6C0, 0xFFFF8A3D},
            new int[] {0xFFFFF1C2, 0xFFFFC27A, 0xFFFF8A3D, 0xFFFF5A1F}, 0xFFFF7A2A, 0xFF0C0504, 0xFFFFB27A);

    private static final float FAR = 0.012f, MID = 0.026f, NEAR = 0.042f, FAR_Y = 0.012f, MID_Y = 0.026f, NEAR_Y = 0.042f;
    /** Where the summit sits on screen, as fractions of the width and height; left, out of the logo's way. */
    private static final float CRATER_X = 0.25f, CRATER_Y = 0.43f;
    /** Where the sea meets the land. */
    private static final float SHORE = 0.69f;
    /** The plume covers this much of the screen from its top left corner. */
    private static final float PLUME_W = 0.8f, PLUME_H = 0.6f;
    private static final int PLUME_BW = 320, PLUME_BH = 256;
    private static final int PARTICLES = 900, QUADS = PARTICLES * 3 + 2, SPRITE = 32;
    /** A bomb's trail: this many segments, each this long in seconds, like a long exposure. */
    private static final int TRAIL = 3;
    private static final float TRAIL_T = 0.1f, GRAVITY = 600f;
    private static final int EMBER = 0, BOMB = 1, ROLL = 2;

    private final float s;
    private final Random rnd = new Random();
    private final float[] px = new float[PARTICLES], py = new float[PARTICLES], pvx = new float[PARTICLES], pvy = new float[PARTICLES];
    private final float[] age = new float[PARTICLES], life = new float[PARTICLES], size = new float[PARTICLES];
    private final int[] kind = new int[PARTICLES];
    private final float[] verts = new float[QUADS * 8], texs = new float[QUADS * 8];
    private final int[] colors = new int[QUADS * 4];
    private final short[] indices = new short[QUADS * 6];
    private final Paint blit = new Paint(Paint.FILTER_BITMAP_FLAG), plumePaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Paint firePaint = new Paint(Paint.FILTER_BITMAP_FLAG), glow = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint bolt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix plumeMatrix = new Matrix();
    private final RectF rect = new RectF();
    private final Path lightning = new Path();
    private final Precipitation precipitation;
    private volatile Land land;
    private volatile boolean baking;
    private Look look, previous;
    private int width, height, margin, marginX, next, lava = 0xFFFF6A1A, lavaBefore = 0xFFFF6A1A, tinted;
    private float clock, travel = 1f, introT = -1f, landIn = 1f, fireIn = 1f, activity = 1f, warp, nextBomb, nextEmber, nextEruption = 30f;
    private float flash, boltT = -1f, clouds, cloudsTarget, par, parTarget, parY, parYTarget;
    private boolean still;

    Volcano(float scale) {
        s = scale;
        precipitation = new Precipitation(scale);
        PorterDuffXfermode add = new PorterDuffXfermode(PorterDuff.Mode.ADD);
        firePaint.setXfermode(add);
        Bitmap dot = Bitmap.createBitmap(SPRITE, SPRITE, Bitmap.Config.ARGB_8888);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(SPRITE / 2f, SPRITE / 2f, SPRITE / 2f,
                new int[] {0xFFFFFFFF, 0xB0FFFFFF, 0x20FFFFFF, 0x00FFFFFF}, new float[] {0f, 0.15f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        new Canvas(dot).drawCircle(SPRITE / 2f, SPRITE / 2f, SPRITE / 2f, p);
        glow.setShader(new BitmapShader(dot, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        glow.setXfermode(add);
        bolt.setStyle(Paint.Style.STROKE);
        bolt.setStrokeCap(Paint.Cap.ROUND);
        bolt.setXfermode(add);
        for (int q = 0; q < QUADS; q++) {
            int t = q * 8, i = q * 6, v = q * 4;
            texs[t + 2] = SPRITE;
            texs[t + 4] = SPRITE;
            texs[t + 5] = SPRITE;
            texs[t + 7] = SPRITE;
            indices[i] = (short) v;
            indices[i + 1] = (short) (v + 1);
            indices[i + 2] = (short) (v + 2);
            indices[i + 3] = (short) v;
            indices[i + 4] = (short) (v + 2);
            indices[i + 5] = (short) (v + 3);
        }
    }

    // ------------------------------------------------------------------ looks

    @Override
    public Look look(int[] sparks, int accent, Random random) {
        float[] hsv = new float[3];
        Color.colorToHSV(sparks[2], hsv);
        float hue = hsv[1] > 0.25f ? hsv[0] : 14f + random.nextFloat() * 18f;
        int lava = Color.HSVToColor(new float[] {hue, 0.9f, 1f});
        return new Look(lava, plume(lava, random), 0.4f + random.nextFloat() * 0.8f);
    }

    /**
     * An app's ash plume: billows rising from the summit, bending downwind and spreading aloft, dark
     * where thick, glowing in the fire's colour low down and on their undersides. Premultiplied, drawn
     * over the sky, so where it is thick it hides it.
     */
    private static Bitmap plume(int lava, Random random) {
        int seed = random.nextInt(1 << 20);
        float lean = 0.35f + random.nextFloat() * 0.35f, girth = 0.05f + random.nextFloat() * 0.03f;
        float lr = Color.red(lava) / 255f, lg = Color.green(lava) / 255f, lb = Color.blue(lava) / 255f;
        float cx = CRATER_X / PLUME_W, cy = CRATER_Y / PLUME_H;
        float[] dense = new float[PLUME_BW * PLUME_BH];
        for (int y = 0; y < PLUME_BH; y++) {
            float v = y / (float) PLUME_BH, rise = Math.max(0f, cy - v), aloft = Math.max(0f, rise - cy * 0.5f);
            if (v > cy) continue;
            float centre = cx + lean * rise * rise * 2.2f + 0.03f * (Noise.fbm(v * 3f, 1f, seed, 3) - 0.5f);
            float width = girth + rise * 0.3f + aloft * 1.4f;
            for (int x = 0; x < PLUME_BW; x++) {
                float u = x / (float) PLUME_BW, d = (u - centre) / width;
                float shape = (float) Math.exp(-d * d * 2f) * Noise.smooth(0.04f, 0.14f, rise);
                if (shape < 0.02f) continue;
                float wx = (Noise.fbm(u * 5f, v * 5f - rise * 2f, seed + 5, 3) - 0.5f) * 1.2f;
                float wy = (Noise.fbm(u * 5f + 7f, v * 5f, seed + 7, 3) - 0.5f) * 1.2f;
                float billow = Noise.fbm((u + wx * 0.15f) * 9f, (v + wy * 0.15f) * 11f + rise * 3f, seed + 11, 5);
                dense[y * PLUME_BW + x] = shape * Noise.smooth(0.32f, 0.62f, billow + 0.18f * shape) * (1f - 0.45f * rise / cy);
            }
        }
        int[] out = new int[PLUME_BW * PLUME_BH];
        for (int y = 0; y < PLUME_BH; y++) {
            float v = y / (float) PLUME_BH, rise = Math.max(0f, cy - v);
            for (int x = 0; x < PLUME_BW; x++) {
                float dn = dense[y * PLUME_BW + x];
                if (dn <= 0.004f) continue;
                float below = y + 3 < PLUME_BH ? dense[(y + 3) * PLUME_BW + x] : 0f;
                float under = Math.max(0f, Math.min(1f, (dn - below) * 3f));
                float a = Math.min(0.88f, dn * 1.8f);
                float fire = (float) Math.exp(-rise * 3f) * (0.5f + 0.5f * under) + 0.3f * under * (float) Math.exp(-rise * 1.2f);
                float r = 0.075f * a + lr * fire * a * 0.8f, g = 0.07f * a + lg * fire * a * 0.6f, b = 0.08f * a + lb * fire * a * 0.55f;
                out[y * PLUME_BW + x] = Color.argb(Math.round(255 * a), c255(Math.min(r, a)), c255(Math.min(g, a)), c255(Math.min(b, a)));
            }
        }
        Bitmap b = Bitmap.createBitmap(PLUME_BW, PLUME_BH, Bitmap.Config.ARGB_8888);
        b.setPremultiplied(true);
        b.setPixels(out, 0, PLUME_BW, 0, 0, PLUME_BW, PLUME_BH);
        return b;
    }

    @Override
    public void setLook(Backdrop.Look incoming, boolean animate) {
        Look next = (Look) incoming;
        if (next == look) return;
        previous = animate ? look : null;
        lavaBefore = look != null && animate ? lava : next.lava;
        look = next;
        travel = previous != null || animate ? 0f : 1f;
        if (look != null && !animate) lava = look.lava;
    }

    // ------------------------------------------------------------------ the landscape

    @Override
    public void layout(RectF area, int w, int h) {
        if (w == width && h == height) return;
        width = w;
        height = h;
        if (baking) return;
        baking = true;
        final int my = (int) Math.ceil(h * FAR_Y) + 1, mx = (int) Math.ceil(w * FAR) + 2;
        new Thread(() -> {
            Land made = land(w, h, mx, my);
            margin = my;
            marginX = mx;
            land = made;
            baking = false;
        }, "lumen-volcano").start();
    }

    /**
     * The scene on a `w` by `h` screen with margins for the parallax: a dusk sky, blue-grey with soft
     * clouds and a few stars, over a calm sea; a low wooded ridge on the right; and the cone, broad
     * and slightly concave, its ash slopes scored by gullies and catching the last light from the
     * left. The fire is not here but in a separate white mask, tinted per app: the column over the
     * summit and its halo, rockfall streaking the slopes, glowing debris at their foot, and the
     * column's reflection on the water.
     */
    private static Land land(int w, int h, int mx, int my) {
        int fw = w + 2 * mx, fh = h + 2 * my;
        float cx = w * CRATER_X + mx, cy = h * CRATER_Y + my, crater = w * 0.018f, shore = h * SHORE + my;
        float rl = w * 0.36f, rr = w * 0.44f, rise = shore - cy;
        float[] ridge = new float[fw], cone = new float[fw];
        for (int x = 0; x < fw; x++) {
            float u = x / (float) fw, d = x - cx;
            float q = Math.min(1f, Math.max(0f, Math.abs(d) - crater) / ((d < 0 ? rl : rr) - crater));
            float notch = Math.abs(d) < crater ? h * 0.006f * (1f - (d / crater) * (d / crater)) : 0f;
            cone[x] = shore - rise * (float) Math.pow(1f - q, 1.35f) + notch
                    + h * 0.004f * (Noise.fbm(u * 50f, 0.9f, 107, 3) - 0.5f) * (0.3f + q);
            float hill = Noise.smooth(0.52f, 0.7f, u) * (1f - 0.4f * Noise.smooth(0.85f, 1f, u));
            ridge[x] = shore - h * hill * (0.055f + 0.025f * Noise.fbm(u * 5f, 0.4f, 103, 3))
                    - h * 0.006f * Noise.fbm(u * 160f, 0.2f, 105, 2) * hill;
        }
        int[] out = new int[fw * fh];
        for (int y = 0; y < fh; y++) {
            float t = Math.max(0f, Math.min(1f, (y - my) / (shore - my))), t2 = t * t;
            float skyR = 0.06f + 0.13f * t2, skyG = 0.065f + 0.12f * t2, skyB = 0.1f + 0.13f * t2;
            float coneT = Math.max(0f, (y - cy) / rise), coneW = 1f - (float) Math.pow(Math.max(0f, 1f - coneT), 1f / 1.35f);
            float sea = Math.max(0f, (y - shore) / (fh - shore));
            for (int x = 0; x < fw; x++) {
                float u = x / (float) fw, v = y / (float) fh;
                float r, g, b;
                if (y >= shore) {
                    float k = 0.55f + 0.5f * Noise.fbm(u * 6f, v * 140f, 151, 3);
                    r = (0.05f + 0.04f * (1f - sea)) * k;
                    g = (0.055f + 0.04f * (1f - sea)) * k;
                    b = (0.075f + 0.05f * (1f - sea)) * k;
                } else if (y >= cone[x]) {
                    float d = x - cx, half = crater + coneW * ((d < 0 ? rl : rr) - crater);
                    float f = Math.max(-1f, Math.min(1f, d / Math.max(1f, half)));
                    float ang = (float) Math.asin(f);
                    float gully = Noise.fbm(ang * 14f + 3f, coneT * 2f, 121, 4);
                    float relief = (Noise.fbm(ang * 14f + 3.04f, coneT * 2f, 121, 4) - Noise.fbm(ang * 14f + 2.96f, coneT * 2f, 121, 4)) * 8f;
                    float lit = Math.max(0f, 0.45f - 0.55f * f) * (float) Math.pow(1f - f * f, 0.2f);
                    float shade = (0.35f + lit) * (0.6f + 0.6f * Math.max(0f, Math.min(1f, 0.5f - relief))) * (0.75f + 0.4f * gully);
                    float grain = 0.85f + 0.3f * Noise.fbm(u * 140f, v * 100f, 123, 2);
                    float foot = 1f - 0.35f * Noise.smooth(0.7f, 1f, coneT);
                    r = 0.07f * shade * grain * foot;
                    g = 0.062f * shade * grain * foot;
                    b = 0.068f * shade * grain * foot;
                } else if (y >= ridge[x]) {
                    float trees = 0.7f + 0.5f * Noise.fbm(u * 200f, v * 120f, 127, 2);
                    r = 0.03f * trees;
                    g = 0.036f * trees;
                    b = 0.042f * trees;
                } else {
                    float cloud = Noise.smooth(0.45f, 0.8f, Noise.fbm(u * 2.2f + 3f, v * 7f, 111, 5));
                    float lift = Noise.fbm(u * 2.2f + 3f, v * 7f - 0.08f, 111, 5) - Noise.fbm(u * 2.2f + 3f, v * 7f + 0.08f, 111, 5);
                    float edge = 0.8f + 1.5f * Math.max(-0.3f, Math.min(0.3f, lift));
                    r = skyR + cloud * (0.05f * edge - 0.025f);
                    g = skyG + cloud * (0.048f * edge - 0.025f);
                    b = skyB + cloud * (0.055f * edge - 0.022f);
                }
                out[y * fw + x] = Color.rgb(c255(r), c255(g), c255(b));
            }
        }
        Random rs = new Random(13);
        for (int k = 0; k < fw * fh / 1400; k++) {
            int x = rs.nextInt(fw), y = rs.nextInt((int) (fh * 0.4f));
            if (y >= cone[x]) continue;
            float b = (float) Math.pow(rs.nextFloat(), 3f) * 0.45f * (1f - y / (fh * 0.4f));
            int c = out[y * fw + x];
            out[y * fw + x] = Color.rgb(Math.min(255, Color.red(c) + c255(b)), Math.min(255, Color.green(c) + c255(b)), Math.min(255, Color.blue(c) + c255(b)));
        }
        Bitmap field = Bitmap.createBitmap(fw, fh, Bitmap.Config.ARGB_8888);
        field.setPixels(out, 0, fw, 0, 0, fw, fh);

        RectF at = new RectF(Math.max(0, cx - w * 0.32f), 0, Math.min(fw, cx + w * 0.45f), fh);
        float fadeX = w * 0.08f;
        int lw = Math.max(1, Math.round(at.width())), lh = Math.max(1, Math.round(at.height()));
        int[] fire = new int[lw * lh];
        float stepX = at.width() / lw, stepY = at.height() / lh;
        for (int j = 0; j < lh; j++) {
            float wy = at.top + (j + 0.5f) * stepY;
            float coneT = Math.max(0f, (wy - cy) / rise), coneW = 1f - (float) Math.pow(Math.max(0f, 1f - coneT), 1f / 1.35f);
            for (int i = 0; i < lw; i++) {
                float wx = at.left + (i + 0.5f) * stepX;
                int col = Math.max(0, Math.min(fw - 1, (int) wx));
                float dx = wx - cx, a;
                if (wy >= shore) {
                    float depth = (wy - shore) / (h * 0.25f), spread = w * (0.03f + 0.06f * depth);
                    float ripple = Noise.smooth(0.35f, 0.75f, Noise.fbm(wx / w * 10f, wy / h * 160f, 153, 3));
                    a = 0.45f * (float) Math.exp(-Math.abs(dx) / spread) * (float) Math.exp(-depth * 1.4f) * ripple;
                } else if (wy < cone[col]) {
                    float up = Math.max(0f, cy - wy), jet = crater * 1.1f + up * 0.28f;
                    float flicker = 0.55f + 0.45f * Noise.fbm(wx / w * 24f, wy / h * 8f, 155, 3);
                    a = (float) Math.exp(-Math.abs(dx) / jet) * (float) Math.exp(-up / (h * 0.24f)) * flicker;
                    float r = (float) Math.sqrt(dx * dx + (wy - cy) * (wy - cy) * 1.6f);
                    a += 0.28f * (float) Math.exp(-r / (w * 0.07f)) + 0.1f * (float) Math.exp(-r / (w * 0.2f));
                } else {
                    float half = crater + coneW * ((dx < 0 ? rl : rr) - crater);
                    float f = Math.max(-1f, Math.min(1f, dx / Math.max(1f, half)));
                    float ang = (float) Math.asin(f);
                    float bend = 0.06f * (Noise.fbm(coneT * 2f, ang * 2f, 161, 2) - 0.5f);
                    float line = 1f - Math.abs(2f * Noise.fbm((ang + bend) * 16f + 0.37f, 0.5f, 157, 2) - 1f);
                    float sector = Noise.smooth(-0.7f, -0.15f, ang) * (1f - Noise.smooth(0.6f, 1.1f, ang));
                    float reachDown = Noise.fbm(ang * 9f, 2.5f, 163, 2);
                    float streak = Noise.smooth(0.84f, 0.97f, line) * sector * (1f - Noise.smooth(0.25f + 0.6f * reachDown, 0.45f + 0.6f * reachDown, coneT));
                    float debris = Noise.smooth(0.6f, 0.95f, coneT) * Noise.smooth(-0.2f, 0.25f, ang) * (1f - Noise.smooth(0.7f, 1.1f, ang));
                    float spots = Noise.smooth(0.7f, 0.78f, Noise.fbm(wx / w * 240f + 0.31f, wy / h * 150f + 0.17f, 159, 2)) * debris;
                    float r = (float) Math.sqrt(dx * dx + (wy - cy) * (wy - cy) * 2f);
                    a = 0.9f * streak + 0.9f * spots + 0.45f * (float) Math.exp(-r / (w * 0.035f))
                            + 0.12f * (float) Math.exp(-r / (w * 0.12f)) + 0.08f * debris;
                }
                if (at.left > 0) a *= Noise.smooth(0f, fadeX, wx - at.left);
                if (at.right < fw) a *= Noise.smooth(0f, fadeX, at.right - wx);
                fire[j * lw + i] = Color.argb(c255(Math.min(1f, a)), 255, 255, 255);
            }
        }
        Bitmap mask = Bitmap.createBitmap(lw, lh, Bitmap.Config.ARGB_8888);
        mask.setPixels(fire, 0, lw, 0, 0, lw, lh);
        return new Land(field, mask, at, cx, cy, crater, cone);
    }

    private static int c255(float v) {
        return Math.max(0, Math.min(255, Math.round(v * 255)));
    }

    // ------------------------------------------------------------------ what happens

    @Override
    public void intro() {
        introT = 0f;
        landIn = 0f;
        fireIn = 0f;
    }

    @Override
    public void setParallax(float x, float y) {
        parTarget = Math.max(-1f, Math.min(1f, x));
        parYTarget = Math.max(0f, Math.min(1f, y));
    }

    @Override
    public void setWarp(float w) {
        warp = w;
    }

    @Override
    public void setWeather(float clouds, float rain, float snow) {
        cloudsTarget = clouds;
        precipitation.setTarget(rain, snow);
    }

    @Override
    public void setStill(boolean s) {
        still = s;
    }

    @Override
    public void setActivity(float rate) {
        activity = rate;
    }

    @Override
    public void storm(float seconds) {
        erupt();
    }

    /** The summit blows: a flash, a tall fan of lava bombs, and sometimes lightning in the plume. */
    private void erupt() {
        Land l = land;
        if (l == null) return;
        flash = 1f;
        for (int k = 0; k < 140; k++) bomb(l, 1.3f, 70f);
        if (rnd.nextInt(3) > 0) boltT = 0.35f + rnd.nextFloat() * 0.4f;
        nextEruption = 40f + rnd.nextFloat() * 50f;
    }

    /** One bomb out of the summit, thrown `force` times as hard as the steady fountain throws, within a fan `spread` degrees wide. */
    private void bomb(Land l, float force, float spread) {
        float a = (float) Math.toRadians(-90 + (rnd.nextFloat() - 0.5f) * spread), v = (260 + rnd.nextFloat() * 380) * s * force;
        spawn(BOMB, l.craterX - marginX + (rnd.nextFloat() - 0.5f) * l.craterW, l.craterY - margin, (float) Math.cos(a) * v,
                (float) Math.sin(a) * v, 5f, (1.6f + rnd.nextFloat() * 1.8f) * s);
    }

    private void spawn(int k, float x, float y, float vx, float vy, float l, float sz) {
        int i = next;
        next = (next + 1) % PARTICLES;
        kind[i] = k;
        px[i] = x;
        py[i] = y;
        pvx[i] = vx;
        pvy[i] = vy;
        age[i] = 0f;
        life[i] = l;
        size[i] = sz;
    }

    /** The ground's height under `x`, in screen coordinates. */
    private float ground(Land l, float x) {
        int col = Math.max(0, Math.min(l.ground.length - 1, Math.round(x + marginX)));
        return l.ground[col] - margin;
    }

    @Override
    public boolean moving() {
        if (introT >= 0f || !still || travel < 1f || warp > 0f || flash > 0f || boltT > 0f || precipitation.moving()
                || clouds != cloudsTarget || par != parTarget || parY != parYTarget) return true;
        for (int i = 0; i < PARTICLES; i++) if (age[i] < life[i]) return true;
        return false;
    }

    @Override
    public void update(float dt) {
        clock += dt;
        float glide = 1f - (float) Math.exp(-dt * 3.5f);
        par += (parTarget - par) * glide;
        if (Math.abs(parTarget - par) < 0.0005f) par = parTarget;
        parY += (parYTarget - parY) * glide;
        if (Math.abs(parYTarget - parY) < 0.0005f) parY = parYTarget;
        clouds = Precipitation.toward(clouds, cloudsTarget, dt / 4f);
        precipitation.update(dt, width, height);
        if (travel < 1f) {
            travel = Math.min(1f, travel + dt / 2f);
            if (look != null) lava = Apps.mix(lavaBefore, look.lava, travel);
            if (travel >= 1f) previous = null;
        }
        if (introT >= 0f) {
            introT += dt;
            landIn = Math.min(1f, introT / 1.2f);
            fireIn = Noise.smooth(1f, 2.2f, introT);
            if (introT >= 2.2f) {
                introT = -1f;
                landIn = 1f;
                fireIn = 1f;
                erupt();
            }
        }
        flash = Math.max(0f, flash - dt * 1.6f);
        if (boltT > 0f) boltT -= dt;
        Land l = land;
        if (l != null && !still && activity > 0f && fireIn >= 1f) {
            nextBomb -= dt;
            while (nextBomb <= 0f) {
                nextBomb += 1f / (60f * activity);
                bomb(l, 1f, 44f);
            }
            nextEmber -= dt;
            while (nextEmber <= 0f) {
                nextEmber += 1f / (8f * activity);
                spawn(EMBER, l.craterX - marginX + (rnd.nextFloat() - 0.5f) * l.craterW * 3f, l.craterY - margin,
                        (rnd.nextFloat() - 0.3f) * 30 * s, -(50 + rnd.nextFloat() * 90) * s, 2.5f + rnd.nextFloat() * 3f,
                        (1f + rnd.nextFloat() * 1.6f) * s);
            }
            if ((nextEruption -= dt * activity) <= 0f) erupt();
        }
        float cx = width / 2f, cy = height / 2f, wind = look != null ? look.wind : 0.6f;
        for (int i = 0; i < PARTICLES; i++) {
            if (age[i] >= life[i]) continue;
            age[i] += dt;
            if (kind[i] == ROLL && l != null) {
                pvx[i] *= 1f - 0.5f * dt;
                pvx[i] += Math.signum(pvx[i]) * 120 * s * dt;
                float ny = ground(l, px[i] + pvx[i] * dt);
                pvy[i] = (ny - py[i]) / Math.max(dt, 0.0001f);
                px[i] += pvx[i] * dt;
                py[i] = ny;
                continue;
            }
            if (kind[i] == EMBER) {
                pvy[i] -= 6 * s * dt;
                pvx[i] += ((float) Math.sin(clock * 1.3f + i) * 14f + wind * 18f) * s * dt;
            } else {
                pvy[i] += GRAVITY * s * dt;
                if (kind[i] == BOMB && l != null && pvy[i] > 0 && py[i] >= ground(l, px[i])) {
                    kind[i] = ROLL;
                    float side = Math.signum(px[i] - (l.craterX - marginX));
                    pvx[i] = side * (60 * s + Math.abs(pvx[i]) * 0.5f);
                    life[i] = Math.min(life[i], age[i] + 1.4f + rnd.nextFloat() * 2f);
                    continue;
                }
            }
            if (warp > 0f) {
                float dx = px[i] - cx, dy = py[i] - cy, d = (float) Math.sqrt(dx * dx + dy * dy) + 1f;
                pvx[i] += dx / d * warp * 1400 * s * dt;
                pvy[i] += dy / d * warp * 1400 * s * dt;
            }
            px[i] += pvx[i] * dt;
            py[i] += pvy[i] * dt;
        }
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void drawBack(Canvas c) {
        c.drawColor(0xFF000000);
        Land l = land;
        if (l == null) return;
        float fade = landIn * (1f - 0.7f * warp) * (1f - 0.35f * clouds);
        float left = -marginX - (float) Math.floor(par * width * FAR);
        float top = -margin - (float) Math.floor(parY * height * FAR_Y);
        blit.setAlpha(Math.round(255 * fade));
        c.drawBitmap(l.field, left, top, blit);
        float midX = -par * width * MID, midY = -parY * height * MID_Y;
        if (previous != null && travel < 1f) drawPlume(c, previous, (1f - travel) * fade, midX, midY);
        if (look != null) drawPlume(c, look, (previous != null ? travel : 1f) * fade, midX, midY);
        float pulse = 0.82f + 0.1f * (float) Math.sin(clock * 1.4f) + 0.08f * Noise.fbm(clock * 0.9f, 3f, 7, 2);
        if (tinted != lava) {
            firePaint.setColorFilter(new PorterDuffColorFilter(lava, PorterDuff.Mode.SRC_IN));
            tinted = lava;
        }
        firePaint.setAlpha(Math.round(255 * Math.min(1f, fade * fireIn * pulse + flash * 0.3f)));
        rect.set(l.fireAt);
        rect.offset(left, top);
        c.drawBitmap(l.fire, null, rect, firePaint);
        drawParticles(c, l, fade);
        if (boltT > 0f) drawLightning(c, l);
        precipitation.draw(c, landIn);
    }

    private void drawPlume(Canvas c, Look k, float alpha, float shiftX, float shiftY) {
        if (alpha <= 0.01f) return;
        float drift = (float) Math.sin(clock * 0.05f) * width * 0.01f, breathe = 1f + 0.02f * (float) Math.sin(clock * 0.11f);
        plumeMatrix.setScale(width * PLUME_W / PLUME_BW * breathe, height * PLUME_H / PLUME_BH * breathe);
        plumeMatrix.postTranslate(shiftX + drift - (breathe - 1f) * width * CRATER_X, shiftY - (breathe - 1f) * height * CRATER_Y);
        plumePaint.setAlpha(Math.round(255 * Math.min(1f, alpha * (0.75f + 0.25f * clouds))));
        c.drawBitmap(k.plume, plumeMatrix, plumePaint);
    }

    /** Bombs as long-exposure arcs, rolling blocks streaking down the flanks, embers, and the summit's flash, in one mesh. */
    private void drawParticles(Canvas c, Land l, float fade) {
        int q = 0;
        float ox = -par * width * NEAR, oy = -parY * height * NEAR_Y;
        int hot = Apps.mix(lava, 0xFFFFF4C8, 0.6f), cool = Apps.mix(lava, 0xFF5A0A00, 0.5f);
        float core = l.craterW * (2f + 0.25f * (float) Math.sin(clock * 7f));
        quad(q, l.craterX - marginX - core + ox, l.craterY - margin + oy - core * 0.4f, l.craterX - marginX + core + ox,
                l.craterY - margin + oy - core * 0.4f, core);
        tint(q++, Apps.withAlpha(hot, Math.round(170 * fireIn * fade)));
        if (flash > 0f) {
            float r = l.craterW * (4f + 6f * (1f - flash));
            quad(q, l.craterX - marginX - r + ox, l.craterY - margin + oy, l.craterX - marginX + r + ox, l.craterY - margin + oy, r);
            tint(q++, Apps.withAlpha(hot, Math.round(220 * flash * fade)));
        }
        for (int i = 0; i < PARTICLES; i++) {
            if (age[i] >= life[i]) continue;
            float u = age[i] / life[i], x = px[i] + ox, y = py[i] + oy, r = size[i];
            int color;
            if (kind[i] == EMBER) {
                float flicker = 0.6f + 0.4f * (float) Math.sin(clock * 9f + i * 2.3f);
                quad(q, x - r, y, x + r, y, r * (1f - 0.4f * u));
                color = Apps.withAlpha(Apps.mix(hot, cool, u), Math.round(200 * (1f - u) * flicker * fade));
            } else if (kind[i] == BOMB) {
                int base = Apps.mix(hot, cool, Math.min(1f, age[i] * 0.4f));
                float g = GRAVITY * s, x0 = x, y0 = y;
                for (int k = 1; k <= TRAIL; k++) {
                    float tau = Math.min(age[i], k * TRAIL_T);
                    float x1 = x - pvx[i] * tau, y1 = y - pvy[i] * tau + 0.5f * g * tau * tau;
                    quad(q, x1, y1, x0, y0, r * 0.5f * (1f - 0.2f * k));
                    tint(q++, Apps.withAlpha(base, Math.round(235 * (1f - (k - 1f) / TRAIL) * fade)));
                    x0 = x1;
                    y0 = y1;
                }
                continue;
            } else {
                quad(q, x - pvx[i] * 0.25f, y - pvy[i] * 0.25f, x, y, r * 0.55f);
                color = Apps.withAlpha(Apps.mix(hot, cool, 0.4f + 0.6f * u), Math.round(235 * (1f - u) * fade));
            }
            tint(q++, color);
        }
        if (q > 0) c.drawVertices(Canvas.VertexMode.TRIANGLES, q * 8, verts, 0, texs, 0, colors, 0, indices, 0, q * 6, glow);
    }

    /** A jagged, branching bolt through the plume, gone in a blink. */
    private void drawLightning(Canvas c, Land l) {
        Random r = new Random((long) (boltT * 10));
        float x = l.craterX - marginX + (r.nextFloat() - 0.2f) * width * 0.12f, y = l.craterY - margin - height * 0.1f;
        lightning.reset();
        lightning.moveTo(x, y);
        for (int k = 0; k < 12; k++) {
            x += (r.nextFloat() - 0.45f) * width * 0.025f;
            y -= height * (0.012f + r.nextFloat() * 0.022f);
            lightning.lineTo(x, y);
            if (k == 4 || k == 8) {
                float bx = x, by = y;
                for (int j = 0; j < 4; j++) {
                    bx += (r.nextFloat() - 0.2f) * width * 0.02f;
                    by -= height * (0.01f + r.nextFloat() * 0.015f);
                    lightning.lineTo(bx, by);
                }
                lightning.moveTo(x, y);
            }
        }
        int alpha = Math.round(255 * Math.min(1f, boltT * 6f));
        bolt.setStrokeWidth(9f * s);
        bolt.setColor(Apps.withAlpha(0xFF8C78FF, alpha / 4));
        c.drawPath(lightning, bolt);
        bolt.setStrokeWidth(2.2f * s);
        bolt.setColor(Apps.withAlpha(0xFFE6E0FF, alpha));
        c.drawPath(lightning, bolt);
    }

    private void quad(int q, float x0, float y0, float x1, float y1, float h) {
        float dx = x1 - x0, dy = y1 - y0, len = (float) Math.sqrt(dx * dx + dy * dy);
        float ux = len < 0.001f ? 1 : dx / len, uy = len < 0.001f ? 0 : dy / len;
        float ex = ux * h, ey = uy * h;
        int o = q * 8;
        verts[o] = x0 - ex - uy * h;
        verts[o + 1] = y0 - ey + ux * h;
        verts[o + 2] = x1 + ex - uy * h;
        verts[o + 3] = y1 + ey + ux * h;
        verts[o + 4] = x1 + ex + uy * h;
        verts[o + 5] = y1 + ey - ux * h;
        verts[o + 6] = x0 - ex + uy * h;
        verts[o + 7] = y0 - ey - ux * h;
    }

    private void tint(int q, int color) {
        int o = q * 4;
        colors[o] = color;
        colors[o + 1] = color;
        colors[o + 2] = color;
        colors[o + 3] = color;
    }
}
