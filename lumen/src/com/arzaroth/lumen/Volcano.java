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
 * A volcanic night. A range of volcanoes stands on the horizon under a smoky sky, the tallest with
 * lava glowing in its crater and running down a flank; a plume rises from it, lit from below; embers
 * float up instead of meteors, and now and then the crater erupts, throwing lava bombs, sometimes
 * with lightning in the plume. Each app gets its own lava colour (blue for a blue logo, like the
 * sulphur flames of Kawah Ijen) and its own plume.
 *
 * The landscape is baked once per screen; per frame it is one blit, the plume over the part of the
 * screen it covers, the lava over the volcano alone, and one mesh of particles.
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

    /** The landscape, built once per screen: the baked scene, the lava's mask and where the crater is. */
    private static final class Land {
        final Bitmap field, lava;
        final RectF lavaAt;
        final float craterX, craterY, craterW;
        final float[] flow;

        Land(Bitmap field, Bitmap lava, RectF lavaAt, float craterX, float craterY, float craterW, float[] flow) {
            this.field = field;
            this.lava = lava;
            this.lavaAt = lavaAt;
            this.craterX = craterX;
            this.craterY = craterY;
            this.craterW = craterW;
            this.flow = flow;
        }
    }

    /** Embers and fire: white-hot to deep orange, a smoky red-black behind the update. */
    static final Palette PALETTE = new Palette(0xFFFFF1C2, 0xFFFF9A3C, new int[] {0xFFFFE6C0, 0xFFFF8A3D},
            new int[] {0xFFFFF1C2, 0xFFFFC27A, 0xFFFF8A3D, 0xFFFF5A1F}, 0xFFFF7A2A, 0xFF0C0504, 0xFFFFB27A);

    private static final float FAR = 0.012f, MID = 0.026f, NEAR = 0.042f, FAR_Y = 0.012f, MID_Y = 0.026f, NEAR_Y = 0.042f;
    /** Where the crater sits on screen, as fractions of the width and height; left, out of the logo's way. */
    private static final float CRATER_X = 0.22f, CRATER_Y = 0.46f;
    /** The plume covers this much of the screen from its top left corner. */
    private static final float PLUME_W = 0.78f, PLUME_H = 0.62f;
    private static final int PLUME_BW = 320, PLUME_BH = 256;
    private static final int PARTICLES = 420, SPRITE = 32;
    private static final int EMBER = 0, BOMB = 1, FLASH = 2;

    private final float s;
    private final Random rnd = new Random();
    private final float[] px = new float[PARTICLES], py = new float[PARTICLES], pvx = new float[PARTICLES], pvy = new float[PARTICLES];
    private final float[] age = new float[PARTICLES], life = new float[PARTICLES], size = new float[PARTICLES];
    private final int[] kind = new int[PARTICLES];
    private final float[] verts = new float[PARTICLES * 8], texs = new float[PARTICLES * 8];
    private final int[] colors = new int[PARTICLES * 4];
    private final short[] indices = new short[PARTICLES * 6];
    private final Paint blit = new Paint(Paint.FILTER_BITMAP_FLAG), plumePaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Paint lavaPaint = new Paint(Paint.FILTER_BITMAP_FLAG), glow = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint bolt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix plumeMatrix = new Matrix();
    private final RectF rect = new RectF();
    private final Path lightning = new Path();
    private final Precipitation precipitation;
    private volatile Land land;
    private volatile boolean baking;
    private Look look, previous;
    private int width, height, margin, marginX, next, lava = 0xFFFF6A1A, lavaBefore = 0xFFFF6A1A;
    private float clock, travel = 1f, introT = -1f, landIn = 1f, lavaIn = 1f, activity = 1f, warp, nextEmber, nextEruption = 30f;
    private float flash, boltT = -1f, clouds, cloudsTarget, par, parTarget, parY, parYTarget;
    private boolean still;

    Volcano(float scale) {
        s = scale;
        precipitation = new Precipitation(scale);
        PorterDuffXfermode add = new PorterDuffXfermode(PorterDuff.Mode.ADD);
        lavaPaint.setXfermode(add);
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
        for (int q = 0; q < PARTICLES; q++) {
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
     * An app's plume: smoke rising from the crater, widening and bending downwind, folded by noise,
     * dark where it is thick and lit in the lava's colour near its root. Premultiplied, drawn over the
     * sky, so where it is thick it hides the stars.
     */
    private static Bitmap plume(int lava, Random random) {
        int seed = random.nextInt(1 << 20);
        float lean = 0.25f + random.nextFloat() * 0.35f, girth = 0.09f + random.nextFloat() * 0.05f;
        float lr = Color.red(lava) / 255f, lg = Color.green(lava) / 255f, lb = Color.blue(lava) / 255f;
        float cx = CRATER_X / PLUME_W, cy = CRATER_Y / PLUME_H;
        int[] px = new int[PLUME_BW * PLUME_BH];
        for (int y = 0; y < PLUME_BH; y++) {
            float v = y / (float) PLUME_BH, rise = Math.max(0f, cy - v);
            for (int x = 0; x < PLUME_BW; x++) {
                float u = x / (float) PLUME_BW;
                if (v > cy + 0.02f) continue;
                float centre = cx + lean * rise * rise * 2.2f + 0.04f * (Noise.fbm(v * 3f, 1f, seed, 3) - 0.5f);
                float width = girth + rise * 0.55f;
                float d = (u - centre) / width;
                float shape = (float) Math.exp(-d * d * 2.2f) * Noise.smooth(0f, 0.08f, rise + 0.02f);
                if (shape < 0.01f) continue;
                float wx = Noise.fbm(u * 4f, v * 4f, seed + 5, 3) * 1.6f;
                float puff = Noise.fbm(u * 5f + wx, v * 6f - wx, seed + 11, 5);
                float density = shape * Noise.smooth(0.25f, 0.65f, puff) * (1f - 0.55f * rise / cy);
                float a = Math.min(0.9f, density * 1.8f);
                float lit = (float) Math.exp(-rise * 3.5f) * shape * (0.35f + 0.65f * Math.min(1f, density * 2f));
                float r = 0.11f * a + lr * lit * 0.6f, g = 0.095f * a + lg * lit * 0.45f, b = 0.1f * a + lb * lit * 0.4f;
                int alpha = Math.round(255 * Math.min(1f, Math.max(a, lit * 0.6f)));
                px[y * PLUME_BW + x] = Color.argb(alpha, c255(r), c255(g), c255(b));
            }
        }
        Bitmap b = Bitmap.createBitmap(PLUME_BW, PLUME_BH, Bitmap.Config.ARGB_8888);
        b.setPremultiplied(true);
        b.setPixels(px, 0, PLUME_BW, 0, 0, PLUME_BW, PLUME_BH);
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
     * The scene on a `w` by `h` screen with margins for the parallax: a smoky sky reddening towards
     * the horizon with a few dim stars high up, a far range in the haze, a second volcano, the main
     * one with its crater, and the near ground; edges facing the crater catch its light. The lava,
     * which glows in each app's colour, is a separate white mask: the crater's mouth and a flow
     * winding down the right flank.
     */
    private static Land land(int w, int h, int mx, int my) {
        int fw = w + 2 * mx, fh = h + 2 * my;
        float cx = w * CRATER_X + mx, cy = h * CRATER_Y + my, crater = w * 0.05f, base = h * 0.80f + my;
        float[] far = new float[fw], second = new float[fw], main = new float[fw], near = new float[fw];
        for (int x = 0; x < fw; x++) {
            float u = x / (float) fw;
            far[x] = h * 0.66f + my - h * 0.09f * Noise.fbm(u * 4f, 0.3f, 101, 5);
            float d2 = Math.abs(x - (w * 0.74f + mx)) / (w * 0.3f);
            second[x] = h * 0.57f + my + h * 0.2f * (float) Math.pow(Math.min(1.4f, d2), 1.3f) + h * 0.02f * Noise.fbm(u * 9f, 0.6f, 103, 4);
            float d = Math.abs(x - cx);
            float cone = cy + (base - cy) * (float) Math.pow(Math.min(1f, d / (w * 0.38f)), 1.25f);
            float notch = d < crater ? h * 0.025f * (1f - (d / crater) * (d / crater)) : 0f;
            main[x] = cone + notch + h * 0.012f * (Noise.fbm(u * 14f, 0.9f, 107, 4) - 0.5f);
            near[x] = h * 0.83f + my - h * 0.05f * Noise.fbm(u * 3f, 1.4f, 109, 4);
        }
        int[] px = new int[fw * fh];
        Random r = new Random(13);
        for (int y = 0; y < fh; y++) {
            float t = Math.max(0f, Math.min(1f, (y - my) / (h * 0.72f)));
            for (int x = 0; x < fw; x++) {
                float haze = Noise.fbm(x / (float) fw * 3f, y / (float) fh * 6f, 111, 4);
                float rr = 0.012f + 0.16f * t * t + 0.02f * haze * t, gg = 0.009f + 0.05f * t * t + 0.008f * haze * t, bb = 0.018f + 0.03f * t * t;
                int layer = y >= near[x] ? 4 : y >= main[x] ? 3 : y >= second[x] ? 2 : y >= far[x] ? 1 : 0;
                if (layer > 0) {
                    float[] tone = {0f, 0.05f, 0.032f, 0.02f, 0.012f};
                    float edge = layer == 1 ? y - far[x] : layer == 2 ? y - second[x] : layer == 3 ? y - main[x] : y - near[x];
                    float rim = (float) Math.exp(-edge / (6f * (w / 1920f))) * (float) Math.exp(-Math.abs(x - cx) / (w * 0.3f));
                    float rock = 0.75f + 0.5f * Noise.fbm(x / (float) fw * 30f, y / (float) fh * 17f, 119, 3);
                    rr = tone[layer] * 1.3f * rock + rim * 0.3f;
                    gg = tone[layer] * 0.75f * rock + rim * 0.09f;
                    bb = tone[layer] * 0.75f * rock + rim * 0.04f;
                }
                px[y * fw + x] = Color.rgb(c255(rr), c255(gg), c255(bb));
            }
        }
        for (int k = 0; k < fw * fh / 900; k++) {
            int x = r.nextInt(fw), y = r.nextInt((int) (fh * 0.5f));
            if (y >= far[x]) continue;
            float b = (float) Math.pow(r.nextFloat(), 3f) * 0.7f * (1f - y / (fh * 0.5f));
            int c = px[y * fw + x];
            px[y * fw + x] = Color.rgb(Math.min(255, Color.red(c) + c255(b)), Math.min(255, Color.green(c) + c255(b * 0.95f)), Math.min(255, Color.blue(c) + c255(b * 0.9f)));
        }
        Bitmap field = Bitmap.createBitmap(fw, fh, Bitmap.Config.ARGB_8888);
        field.setPixels(px, 0, fw, 0, 0, fw, fh);

        float reach = crater * 3.2f;
        RectF at = new RectF(cx - reach, cy - reach, cx + Math.max(reach, w * 0.14f), Math.min(fh, base));
        int lw = Math.max(1, Math.round(at.width() / 2)), lh = Math.max(1, Math.round(at.height() / 2));
        Bitmap mask = Bitmap.createBitmap(lw, lh, Bitmap.Config.ARGB_8888);
        Canvas m = new Canvas(mask);
        m.scale(lw / at.width(), lh / at.height());
        m.translate(-at.left, -at.top);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(cx, cy, reach, new int[] {0x90FFFFFF, 0x30FFFFFF, 0x00FFFFFF}, new float[] {0f, 0.35f, 1f}, Shader.TileMode.CLAMP));
        m.drawCircle(cx, cy, reach, p);
        p.setShader(null);
        Path mountain = new Path();
        mountain.moveTo(at.left, fh);
        for (int x = Math.max(0, (int) at.left); x <= Math.min(fw - 1, (int) at.right); x++) mountain.lineTo(x, main[x] - h * 0.002f);
        mountain.lineTo(at.right, fh);
        mountain.close();
        m.save();
        m.clipPath(mountain);
        p.setColor(0xFFFFFFFF);
        m.drawOval(cx - crater * 0.9f, cy + h * 0.01f, cx + crater * 0.9f, cy + h * 0.034f, p);
        float[] flow = new float[40];
        float[] fxs = new float[20], fys = new float[20];
        for (int k = 0; k < 20; k++) {
            float step = k / 19f;
            fxs[k] = cx + crater * 0.3f + w * 0.05f * step + w * 0.012f * (Noise.fbm(step * 2f, 2f, 113, 2) - 0.5f) * 2f;
            fys[k] = cy + h * 0.025f + h * 0.2f * step;
            flow[2 * k] = fxs[k];
            flow[2 * k + 1] = fys[k];
        }
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        for (int k = 1; k < 20; k++) {
            float taper = 1f - 0.7f * k / 19f;
            p.setColor(0x40FFFFFF);
            p.setStrokeWidth(h * 0.024f * taper);
            m.drawLine(fxs[k - 1], fys[k - 1], fxs[k], fys[k], p);
            p.setColor(Color.argb(Math.round(190 * taper), 255, 255, 255));
            p.setStrokeWidth(h * 0.006f * taper + 1f);
            m.drawLine(fxs[k - 1], fys[k - 1], fxs[k], fys[k], p);
        }
        float bx = fxs[7], by = fys[7];
        for (int k = 1; k < 9; k++) {
            float step = k / 8f;
            float nx = fxs[7] - w * 0.025f * step + w * 0.008f * (Noise.fbm(step * 5f, 4f, 117, 2) - 0.5f) * 2f, ny = fys[7] + h * 0.09f * step;
            p.setColor(Color.argb(Math.round(120 * (1f - 0.7f * step)), 255, 255, 255));
            p.setStrokeWidth(h * 0.004f * (1f - 0.6f * step) + 1f);
            m.drawLine(bx, by, nx, ny, p);
            bx = nx;
            by = ny;
        }
        m.restore();
        return new Land(field, mask, at, cx, cy, crater, flow);
    }

    private static int c255(float v) {
        return Math.max(0, Math.min(255, Math.round(v * 255)));
    }

    // ------------------------------------------------------------------ what happens

    @Override
    public void intro() {
        introT = 0f;
        landIn = 0f;
        lavaIn = 0f;
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

    /** The crater blows: a flash, a fan of lava bombs, and sometimes lightning in the plume. */
    private void erupt() {
        Land l = land;
        if (l == null) return;
        float x = l.craterX - marginX, y = l.craterY - margin;
        flash = 1f;
        for (int k = 0; k < 70; k++) {
            float a = (float) Math.toRadians(-90 + (rnd.nextFloat() - 0.5f) * 70), v = (380 + rnd.nextFloat() * 520) * s;
            spawn(BOMB, x + (rnd.nextFloat() - 0.5f) * l.craterW, y, (float) Math.cos(a) * v, (float) Math.sin(a) * v,
                    1.4f + rnd.nextFloat() * 1.2f, (2.5f + rnd.nextFloat() * 3f) * s);
        }
        if (rnd.nextInt(3) > 0) boltT = 0.35f + rnd.nextFloat() * 0.4f;
        nextEruption = 40f + rnd.nextFloat() * 50f;
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
            lavaIn = Noise.smooth(1f, 2.2f, introT);
            if (introT >= 2.2f) {
                introT = -1f;
                landIn = 1f;
                lavaIn = 1f;
                erupt();
            }
        }
        flash = Math.max(0f, flash - dt * 1.6f);
        if (boltT > 0f) boltT -= dt;
        Land l = land;
        if (l != null && !still && activity > 0f) {
            nextEmber -= dt;
            while (nextEmber <= 0f) {
                nextEmber += 1f / (14f * activity);
                boolean fromFlow = rnd.nextInt(3) == 0;
                int k = rnd.nextInt(l.flow.length / 2);
                float x = fromFlow ? l.flow[2 * k] : l.craterX + (rnd.nextFloat() - 0.5f) * l.craterW * 1.4f;
                float y = fromFlow ? l.flow[2 * k + 1] : l.craterY;
                spawn(EMBER, x - marginX, y - margin, (rnd.nextFloat() - 0.3f) * 30 * s, -(40 + rnd.nextFloat() * 80) * s,
                        2.5f + rnd.nextFloat() * 3f, (1.2f + rnd.nextFloat() * 2.2f) * s);
            }
            if ((nextEruption -= dt * activity) <= 0f) erupt();
        }
        float cx = width / 2f, cy = height / 2f, wind = look != null ? look.wind : 0.6f;
        for (int i = 0; i < PARTICLES; i++) {
            if (age[i] >= life[i]) continue;
            age[i] += dt;
            if (kind[i] == EMBER) {
                pvy[i] -= 6 * s * dt;
                pvx[i] += ((float) Math.sin(clock * 1.3f + i) * 14f + wind * 18f) * s * dt;
            } else {
                pvy[i] += 420 * s * dt;
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
        float pulse = 0.78f + 0.12f * (float) Math.sin(clock * 1.4f) + 0.1f * Noise.fbm(clock * 0.9f, 3f, 7, 2);
        lavaPaint.setColorFilter(new PorterDuffColorFilter(lava, PorterDuff.Mode.SRC_IN));
        lavaPaint.setAlpha(Math.round(255 * Math.min(1f, fade * lavaIn * pulse + flash * 0.4f)));
        rect.set(l.lavaAt);
        rect.offset(left, top);
        c.drawBitmap(l.lava, null, rect, lavaPaint);
        float midX = -par * width * MID, midY = -parY * height * MID_Y;
        if (previous != null && travel < 1f) drawPlume(c, previous, (1f - travel) * fade, midX, midY);
        if (look != null) drawPlume(c, look, (previous != null ? travel : 1f) * fade, midX, midY);
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

    /** Embers cooling as they rise, bombs arcing out of the crater, and the crater's flash, in one mesh. */
    private void drawParticles(Canvas c, Land l, float fade) {
        int q = 0;
        float ox = -par * width * NEAR, oy = -parY * height * NEAR_Y;
        int hot = Apps.mix(lava, 0xFFFFF4C8, 0.6f);
        if (flash > 0f) {
            float r = l.craterW * (2.5f + 3f * (1f - flash));
            quad(q, l.craterX - marginX - r + ox, l.craterY - margin + oy, l.craterX - marginX + r + ox, l.craterY - margin + oy, r);
            tint(q++, Apps.withAlpha(hot, Math.round(220 * flash * fade)));
        }
        for (int i = 0; i < PARTICLES; i++) {
            if (age[i] >= life[i]) continue;
            float u = age[i] / life[i], x = px[i] + ox, y = py[i] + oy;
            float flicker = kind[i] == EMBER ? 0.6f + 0.4f * (float) Math.sin(clock * 9f + i * 2.3f) : 1f;
            float r = size[i] * (kind[i] == BOMB ? 1f : 1f - 0.4f * u);
            if (kind[i] == BOMB) {
                quad(q, x - pvx[i] * 0.03f, y - pvy[i] * 0.03f, x, y, r);
            } else {
                quad(q, x - r, y, x + r, y, r);
            }
            int color = Apps.mix(hot, Apps.mix(lava, 0xFF5A0A00, 0.5f), u);
            tint(q++, Apps.withAlpha(color, Math.round(255 * (1f - u) * flicker * fade)));
        }
        if (q > 0) c.drawVertices(Canvas.VertexMode.TRIANGLES, q * 8, verts, 0, texs, 0, colors, 0, indices, 0, q * 6, glow);
    }

    /** A jagged bolt through the plume, gone in a blink. */
    private void drawLightning(Canvas c, Land l) {
        Random r = new Random((long) (boltT * 10));
        float x = l.craterX - marginX + (r.nextFloat() - 0.2f) * width * 0.12f, y = l.craterY - margin - height * 0.08f;
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
