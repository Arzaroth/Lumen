package com.arzaroth.lumen;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Random;

/**
 * A pack's picture behind the logo, brought to life by its layers: `glow` makes whatever burns in
 * the picture flicker, `stars` makes its stars twinkle, `sparks` throws sparks from a point. The
 * picture covers the screen with a margin for the parallax, a point of it pinned to a point of the
 * screen; past its bottom edge its last row carries on.
 *
 * Everything is worked out once per screen off the UI thread; per frame it is one picture, the
 * glow over the part that burns, and one mesh of quads.
 */
final class PictureBackdrop implements Backdrop {
    static final class Look implements Backdrop.Look {
        final float vigor;

        Look(float vigor) {
            this.vigor = vigor;
        }
    }

    /** What the pack becomes on one screen. */
    private static final class Scene {
        final Bitmap picture, glow;
        final float k, left, top, glowX, glowY;
        final float[] starX, starY, starB, starPhase;

        Scene(Bitmap picture, Bitmap glow, float k, float left, float top, float glowX, float glowY, float[] starX, float[] starY,
                float[] starB, float[] starPhase) {
            this.picture = picture;
            this.glow = glow;
            this.k = k;
            this.left = left;
            this.top = top;
            this.glowX = glowX;
            this.glowY = glowY;
            this.starX = starX;
            this.starY = starY;
            this.starB = starB;
            this.starPhase = starPhase;
        }
    }

    private static final float FAR = 0.012f, FAR_Y = 0.012f;
    private static final int SPARKS = 400, SPRITE = 32, MAX_STARS = 400, QUADS = SPARKS + MAX_STARS;

    private final Pack pack;
    private final float s;
    private final Pack.Layer glowLayer, starLayer, sparkLayer;
    private final Random rnd = new Random();
    private final float[] px = new float[SPARKS], py = new float[SPARKS], vx = new float[SPARKS], vy = new float[SPARKS];
    private final float[] age = new float[SPARKS], life = new float[SPARKS], size = new float[SPARKS];
    private final float[] verts = new float[QUADS * 8], texs = new float[QUADS * 8];
    private final int[] colors = new int[QUADS * 4];
    private final short[] indices = new short[QUADS * 6];
    private final int[] sparkColors;
    private final Paint picture = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG), glowPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint dot = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Matrix matrix = new Matrix();
    private final Precipitation precipitation;
    private volatile Scene scene;
    private Scene shaderFor;
    private volatile boolean baking;
    private Look look = new Look(1f);
    private int width, height, next;
    private float clock, introT = -1f, shown = 1f, warp, nextSpark, flash, clouds, cloudsTarget, par, parTarget, parY, parYTarget;
    private boolean still;

    PictureBackdrop(Pack pack, float scale) {
        this.pack = pack;
        s = scale;
        precipitation = new Precipitation(scale);
        glowLayer = layer("glow");
        starLayer = layer("stars");
        sparkLayer = layer("sparks");
        sparkColors = sparkLayer != null ? sparkLayer.colors("colors", 0xFFFFA040) : new int[] {0xFFFFA040};
        PorterDuffXfermode add = new PorterDuffXfermode(PorterDuff.Mode.ADD);
        glowPaint.setXfermode(add);
        Bitmap sprite = Bitmap.createBitmap(SPRITE, SPRITE, Bitmap.Config.ARGB_8888);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(SPRITE / 2f, SPRITE / 2f, SPRITE / 2f,
                new int[] {0xFFFFFFFF, 0xB0FFFFFF, 0x20FFFFFF, 0x00FFFFFF}, new float[] {0f, 0.15f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        new Canvas(sprite).drawCircle(SPRITE / 2f, SPRITE / 2f, SPRITE / 2f, p);
        dot.setShader(new BitmapShader(sprite, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        dot.setXfermode(add);
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

    private Pack.Layer layer(String type) {
        for (Pack.Layer l : pack.layers) if (l.type.equals(type)) return l;
        return null;
    }

    @Override
    public Look look(int[] sparks, int accent, Random random) {
        return new Look(0.75f + random.nextFloat() * 0.5f);
    }

    @Override
    public void setLook(Backdrop.Look incoming, boolean animate) {
        look = (Look) incoming;
    }

    // ------------------------------------------------------------------ the scene

    @Override
    public void layout(RectF area, int w, int h) {
        if (w == width && h == height) return;
        width = w;
        height = h;
        if (baking) return;
        baking = true;
        new Thread(() -> {
            scene = bake(w, h);
            baking = false;
        }, "lumen-picture").start();
    }

    /** The pack on a `w` by `h` screen: the picture placed, what burns in it, and where its stars are. */
    private Scene bake(int w, int h) {
        Bitmap image;
        try (InputStream in = pack.files.open(pack.image)) {
            image = BitmapFactory.decodeStream(in);
        } catch (IOException e) {
            image = null;
        }
        if (image == null) return null;
        int iw = image.getWidth(), ih = image.getHeight();
        float mx = w * FAR + 2, my = h * FAR_Y + 2;
        float ax = pack.to[0] * w, ay = pack.to[1] * h, fx = pack.from[0], fy = pack.from[1];
        float k = (w + 2 * mx) / iw;
        if (fx > 0) k = Math.max(k, (ax + mx) / (fx * iw));
        if (fx < 1) k = Math.max(k, (w - ax + mx) / ((1 - fx) * iw));
        if (fy > 0) k = Math.max(k, (ay + my) / (fy * ih));
        float left = ax - fx * iw * k, top = ay - fy * ih * k;
        int[] pixels = new int[iw * ih];
        image.getPixels(pixels, 0, iw, 0, 0, iw, ih);

        Bitmap glow = null;
        float glowX = 0, glowY = 0;
        if (glowLayer != null) {
            int x0 = iw, y0 = ih, x1 = -1, y1 = -1;
            float[] weight = new float[iw * ih];
            for (int i = 0; i < pixels.length; i++) {
                int c = pixels[i];
                float r = Color.red(c) / 255f, g = Color.green(c) / 255f, b = Color.blue(c) / 255f;
                float wgt = Noise.smooth(0.18f, 0.5f, r - Math.max(g * 0.85f, b)) * Noise.smooth(0.3f, 0.8f, r);
                if (wgt > 0.02f) {
                    weight[i] = wgt;
                    int x = i % iw, y = i / iw;
                    x0 = Math.min(x0, x);
                    y0 = Math.min(y0, y);
                    x1 = Math.max(x1, x);
                    y1 = Math.max(y1, y);
                }
            }
            if (x1 >= x0) {
                int gw = x1 - x0 + 1, gh = y1 - y0 + 1;
                int[] out = new int[gw * gh];
                for (int y = 0; y < gh; y++) {
                    for (int x = 0; x < gw; x++) {
                        int i = (y + y0) * iw + x + x0;
                        out[y * gw + x] = Apps.withAlpha(pixels[i], Math.round(255 * weight[i]));
                    }
                }
                glow = Bitmap.createBitmap(gw, gh, Bitmap.Config.ARGB_8888);
                glow.setPixels(out, 0, gw, 0, 0, gw, gh);
                glowX = x0;
                glowY = y0;
            }
        }

        float[] sx = new float[0], sy = new float[0], sb = new float[0], phase = new float[0];
        if (starLayer != null) {
            int rows = Math.min(ih - 1, Math.round(starLayer.num("above", 0.6f) * ih));
            int want = Math.min(MAX_STARS, Math.max(0, Math.round(starLayer.num("count", 120))));
            float[] found = new float[iw * 3];
            int n = 0;
            for (int y = 1; y < rows; y++) {
                for (int x = 1; x < iw - 1; x++) {
                    float l = lum(pixels[y * iw + x]);
                    if (l < 0.2f || chroma(pixels[y * iw + x]) > 0.25f) continue;
                    boolean peak = true;
                    for (int dy = -1; dy <= 1 && peak; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            if ((dx != 0 || dy != 0) && lum(pixels[(y + dy) * iw + x + dx]) > l - 0.05f) {
                                peak = false;
                                break;
                            }
                        }
                    }
                    if (!peak) continue;
                    if (n * 3 + 3 > found.length) found = Arrays.copyOf(found, found.length * 2);
                    found[n * 3] = x;
                    found[n * 3 + 1] = y;
                    found[n * 3 + 2] = l;
                    n++;
                }
            }
            Integer[] order = new Integer[n];
            for (int i = 0; i < n; i++) order[i] = i;
            final float[] f = found;
            Arrays.sort(order, (a, b) -> Float.compare(f[b * 3 + 2], f[a * 3 + 2]));
            int m = Math.min(want, n);
            sx = new float[m];
            sy = new float[m];
            sb = new float[m];
            phase = new float[m];
            Random r = new Random(7);
            for (int i = 0; i < m; i++) {
                int o = order[i];
                sx[i] = left + (found[o * 3] + 0.5f) * k;
                sy[i] = top + (found[o * 3 + 1] + 0.5f) * k;
                sb[i] = found[o * 3 + 2];
                phase[i] = r.nextFloat() * 100f;
            }
        }
        return new Scene(image, glow, k, left, top, glowX, glowY, sx, sy, sb, phase);
    }

    private static float lum(int c) {
        return (Color.red(c) + Color.green(c) + Color.blue(c)) / 765f;
    }

    private static float chroma(int c) {
        int hi = Math.max(Color.red(c), Math.max(Color.green(c), Color.blue(c))), lo = Math.min(Color.red(c), Math.min(Color.green(c), Color.blue(c)));
        return (hi - lo) / 255f;
    }

    // ------------------------------------------------------------------ what happens

    @Override
    public void intro() {
        introT = 0f;
        shown = 0f;
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
    public void setStill(boolean still) {
        this.still = still;
    }

    @Override
    public void storm(float seconds) {
        Scene sc = scene;
        if (sc == null || sparkLayer == null) return;
        flash = 1f;
        for (int i = 0; i < 90; i++) spark(sc, 1.6f);
    }

    /** One spark from the layer's source, thrown `force` times as hard as usual. */
    private void spark(Scene sc, float force) {
        float[] from = sparkLayer.pair("from", 0.5f, 0.5f), speed = sparkLayer.pair("speed", 0.08f, 0.2f);
        float[] angle = sparkLayer.pair("angle", -120f, -60f), span = sparkLayer.pair("life", 1f, 2f), sz = sparkLayer.pair("size", 1f, 2f);
        float ih = sc.picture.getHeight() * sc.k, iw = sc.picture.getWidth() * sc.k;
        float a = (float) Math.toRadians(angle[0] + rnd.nextFloat() * (angle[1] - angle[0]));
        float v = (speed[0] + rnd.nextFloat() * (speed[1] - speed[0])) * ih * force;
        int i = next;
        next = (next + 1) % SPARKS;
        px[i] = sc.left + (from[0] + (rnd.nextFloat() - 0.5f) * sparkLayer.num("width", 0.02f)) * iw;
        py[i] = sc.top + from[1] * ih;
        vx[i] = (float) Math.cos(a) * v;
        vy[i] = (float) Math.sin(a) * v;
        age[i] = 0f;
        life[i] = span[0] + rnd.nextFloat() * (span[1] - span[0]);
        size[i] = (sz[0] + rnd.nextFloat() * (sz[1] - sz[0])) * s * 1.5f;
    }

    @Override
    public boolean moving() {
        if (introT >= 0f || !still || warp > 0f || flash > 0f || precipitation.moving() || clouds != cloudsTarget
                || par != parTarget || parY != parYTarget) return true;
        for (int i = 0; i < SPARKS; i++) if (age[i] < life[i]) return true;
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
        if (introT >= 0f) {
            introT += dt;
            shown = Noise.smooth(0f, 1.6f, introT);
            if (introT >= 1.6f) {
                introT = -1f;
                shown = 1f;
                storm(0f);
            }
        }
        flash = Math.max(0f, flash - dt * 1.2f);
        Scene sc = scene;
        if (sc != null && sparkLayer != null && !still && introT < 0f) {
            nextSpark -= dt;
            while (nextSpark <= 0f) {
                nextSpark += 1f / Math.max(0.1f, sparkLayer.num("rate", 20f) * look.vigor);
                spark(sc, 1f);
            }
        }
        float g = sparkLayer != null && sc != null ? sparkLayer.num("gravity", 0.1f) * sc.picture.getHeight() * sc.k : 0f;
        float cx = width / 2f, cy = height / 2f;
        for (int i = 0; i < SPARKS; i++) {
            if (age[i] >= life[i]) continue;
            age[i] += dt;
            vy[i] += g * dt;
            vx[i] += (float) Math.sin(clock * 1.7f + i) * 12f * s * dt;
            if (warp > 0f) {
                float dx = px[i] - cx, dy = py[i] - cy, d = (float) Math.sqrt(dx * dx + dy * dy) + 1f;
                vx[i] += dx / d * warp * 1400 * s * dt;
                vy[i] += dy / d * warp * 1400 * s * dt;
            }
            px[i] += vx[i] * dt;
            py[i] += vy[i] * dt;
        }
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void drawBack(Canvas c) {
        c.drawColor(0xFF000000);
        Scene sc = scene;
        if (sc == null) return;
        float fade = shown * (1f - 0.7f * warp) * (1f - 0.3f * clouds);
        float ox = -(float) Math.floor(par * width * FAR), oy = -(float) Math.floor(parY * height * FAR_Y);
        if (shaderFor != sc) {
            picture.setShader(new BitmapShader(sc.picture, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
            shaderFor = sc;
        }
        matrix.setScale(sc.k, sc.k);
        matrix.postTranslate(sc.left + ox, sc.top + oy);
        picture.getShader().setLocalMatrix(matrix);
        picture.setAlpha(Math.round(255 * fade));
        c.drawRect(0, 0, width, height, picture);
        if (sc.glow != null) {
            float period = Math.max(0.2f, glowLayer.num("period", 2.5f)), depth = glowLayer.num("depth", 0.4f);
            float flicker = 0.35f + 0.65f * Noise.fbm(clock / period, 3.3f, 17, 3);
            matrix.setScale(sc.k, sc.k);
            matrix.postTranslate(sc.left + sc.glowX * sc.k + ox, sc.top + sc.glowY * sc.k + oy);
            glowPaint.setAlpha(Math.round(255 * Math.min(1f, (depth * flicker + 0.6f * flash) * fade)));
            c.drawBitmap(sc.glow, matrix, glowPaint);
        }
        int q = 0;
        if (!still) {
            for (int i = 0; i < sc.starX.length; i++) {
                float tw = (float) Math.sin(clock * (0.6f + (sc.starPhase[i] % 1.3f)) + sc.starPhase[i]);
                if (tw <= 0.2f) continue;
                float a = (tw - 0.2f) / 0.8f;
                a = a * a * a * sc.starB[i] * fade;
                float r = (1.4f + 2.2f * sc.starB[i]) * s * 1.5f;
                quad(q, sc.starX[i] - r + ox, sc.starY[i] + oy, sc.starX[i] + r + ox, sc.starY[i] + oy, r);
                tint(q++, Apps.withAlpha(0xFFE8EEFF, Math.round(230 * a)));
            }
        }
        for (int i = 0; i < SPARKS; i++) {
            if (age[i] >= life[i]) continue;
            float u = age[i] / life[i], x = px[i] + ox, y = py[i] + oy, r = size[i];
            quad(q, x - vx[i] * 0.05f, y - vy[i] * 0.05f, x, y, r * (1f - 0.5f * u));
            tint(q++, Apps.withAlpha(ramp(u), Math.round(255 * (1f - u * u) * fade)));
        }
        if (q > 0) c.drawVertices(Canvas.VertexMode.TRIANGLES, q * 8, verts, 0, texs, 0, colors, 0, indices, 0, q * 6, dot);
        precipitation.draw(c, shown);
    }

    /** The sparks' colour at `u` of their life, through the layer's colours. */
    private int ramp(float u) {
        int n = sparkColors.length;
        if (n == 1) return sparkColors[0];
        float f = Math.max(0f, Math.min(1f, u)) * (n - 1);
        int k = Math.min(n - 2, (int) f);
        return Apps.mix(sparkColors[k], sparkColors[k + 1], f - k);
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
