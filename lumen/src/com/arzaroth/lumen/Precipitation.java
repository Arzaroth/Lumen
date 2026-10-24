package com.arzaroth.lumen;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import java.util.Random;

/**
 * Rain as slanted streaks or snow as soft flakes, in front of any backdrop, as many as the weather
 * is strong. It eases into a change over a few seconds.
 */
final class Precipitation {
    private static final int DROPS = 240, SPRITE = 32;

    private final float s;
    private final Random rnd = new Random();
    private final float[] x = new float[DROPS], y = new float[DROPS], seed = new float[DROPS];
    private final float[] verts = new float[DROPS * 8], texs = new float[DROPS * 8];
    private final int[] colors = new int[DROPS * 4];
    private final short[] indices = new short[DROPS * 6];
    private final Paint streak = new Paint(), flake = new Paint(Paint.FILTER_BITMAP_FLAG);
    private float rain, rainTarget, snow, snowTarget, clock;
    private boolean placed;

    Precipitation(float scale) {
        s = scale;
        PorterDuffXfermode add = new PorterDuffXfermode(PorterDuff.Mode.ADD);
        streak.setXfermode(add);
        Bitmap dot = Bitmap.createBitmap(SPRITE, SPRITE, Bitmap.Config.ARGB_8888);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(SPRITE / 2f, SPRITE / 2f, SPRITE / 2f,
                new int[] {0xFFFFFFFF, 0xB0FFFFFF, 0x20FFFFFF, 0x00FFFFFF}, new float[] {0f, 0.12f, 0.4f, 1f}, Shader.TileMode.CLAMP));
        new Canvas(dot).drawCircle(SPRITE / 2f, SPRITE / 2f, SPRITE / 2f, p);
        flake.setShader(new BitmapShader(dot, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        flake.setXfermode(add);
        for (int q = 0; q < DROPS; q++) {
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

    void setTarget(float rain, float snow) {
        rainTarget = rain;
        snowTarget = snow;
    }

    /** Whether anything falls or is about to change. */
    boolean moving() {
        return rain > 0f || snow > 0f || rain != rainTarget || snow != snowTarget;
    }

    void update(float dt, int width, int height) {
        clock += dt;
        rain = toward(rain, rainTarget, dt / 4f);
        snow = toward(snow, snowTarget, dt / 4f);
        if (width == 0 || rain <= 0f && snow <= 0f) return;
        if (!placed) {
            for (int i = 0; i < DROPS; i++) {
                x[i] = rnd.nextFloat() * width;
                y[i] = rnd.nextFloat() * height;
                seed[i] = rnd.nextFloat();
            }
            placed = true;
        }
        boolean raining = rain >= snow;
        for (int i = 0; i < DROPS; i++) {
            float v = raining ? (900 + 500 * seed[i]) * s : (45 + 55 * seed[i]) * s;
            y[i] += v * dt;
            x[i] += raining ? -0.18f * v * dt : (float) Math.sin(clock * 0.8f + seed[i] * 20f) * 22 * s * dt;
            if (y[i] > height + 40 * s) {
                y[i] = -40 * s * rnd.nextFloat();
                x[i] = rnd.nextFloat() * width * 1.15f;
            }
            if (x[i] < -20 * s) x[i] += width + 40 * s;
            else if (x[i] > width + 20 * s) x[i] -= width + 40 * s;
        }
    }

    /** Draws what falls, faded by `alpha`. */
    void draw(Canvas c, float alpha) {
        float strength = Math.max(rain, snow);
        int n = Math.round(strength * DROPS);
        if (n == 0 || !placed) return;
        boolean raining = rain >= snow;
        for (int i = 0; i < n; i++) {
            if (raining) {
                float len = (26 + 34 * seed[i]) * s;
                quad(i, x[i] + 0.18f * len, y[i] - len, x[i], y[i], 0.6f * s, 0.9f * s);
                tint(i, Apps.withAlpha(0xFFBFD3FF, Math.round(95 * strength * alpha)));
            } else {
                float r = (5f + 6f * seed[i]) * s;
                quad(i, x[i] - r, y[i], x[i] + r, y[i], r, r);
                tint(i, Apps.withAlpha(0xFFFFFFFF, Math.round(230 * strength * alpha)));
            }
        }
        if (raining) {
            c.drawVertices(Canvas.VertexMode.TRIANGLES, n * 8, verts, 0, null, 0, colors, 0, indices, 0, n * 6, streak);
        } else {
            c.drawVertices(Canvas.VertexMode.TRIANGLES, n * 8, verts, 0, texs, 0, colors, 0, indices, 0, n * 6, flake);
        }
    }

    private void quad(int q, float x0, float y0, float x1, float y1, float h0, float h1) {
        float dx = x1 - x0, dy = y1 - y0, len = (float) Math.sqrt(dx * dx + dy * dy);
        float ux = len < 0.001f ? 1 : dx / len, uy = len < 0.001f ? 0 : dy / len;
        int o = q * 8;
        verts[o] = x0 - uy * h0;
        verts[o + 1] = y0 + ux * h0;
        verts[o + 2] = x1 - uy * h1;
        verts[o + 3] = y1 + ux * h1;
        verts[o + 4] = x1 + uy * h1;
        verts[o + 5] = y1 - ux * h1;
        verts[o + 6] = x0 + uy * h0;
        verts[o + 7] = y0 - ux * h0;
    }

    private void tint(int q, int color) {
        int o = q * 4;
        colors[o] = color;
        colors[o + 1] = color;
        colors[o + 2] = color;
        colors[o + 3] = color;
    }

    static float toward(float v, float target, float step) {
        return v < target ? Math.min(target, v + step) : Math.max(target, v - step);
    }
}
