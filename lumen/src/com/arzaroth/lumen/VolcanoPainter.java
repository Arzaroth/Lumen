package com.arzaroth.lumen;

import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Paints a volcano erupting under the stars, from nothing: a navy sky with its stars and a bank of
 * mist on the horizon, a column of ash rising in lit billows, a stratovolcano scored by ravines with
 * lava running down them, and the dark land in front. Each part is made the way it looks: the ash is
 * a thousand shaded puffs lit from below by the fire, the ravines are a relief lit by the lava, the
 * rivers flow downhill into the ravines and fork. Same seed, same volcano.
 */
final class VolcanoPainter {
    /** Where the vent is, as fractions of the picture. */
    static final float VENT_X = 0.30f, VENT_Y = 0.52f;

    private VolcanoPainter() {
    }

    static Pack.Painting paint(int w, int h) {
        Random r = new Random(1987);
        float u = w / 1920f, sx = w * VENT_X, sy = h * VENT_Y, base = h * 0.80f;
        float halfL = w * 0.26f, halfR = w * 0.36f;
        float[] top = new float[w], land = new float[w];
        for (int x = 0; x < w; x++) {
            top[x] = profile(x, w, h, sx, sy, base, halfL, halfR);
            land[x] = h * 0.805f + h * 0.006f * (Noise.fbm(x / (float) w * 9f, 0.3f, 401, 3) - 0.5f)
                    - h * 0.022f * Noise.smooth(0.55f, 0.75f, x / (float) w) * Noise.fbm(x / (float) w * 4f, 1.3f, 417, 4);
        }
        int[] px = new int[w * h];
        sky(px, w, h, r, land);
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        b.setPixels(px, 0, w, 0, 0, w, h);
        Canvas c = new Canvas(b);
        haze(c, w, h, sx, sy);
        b.getPixels(px, 0, w, 0, 0, w, h);
        ash(px, w, h, top);
        cone(px, w, h, sx, sy, base, halfL, halfR, top, land);
        b.setPixels(px, 0, w, 0, 0, w, h);
        Pack.Painting out = new Pack.Painting(b);
        out.flows = rivers(c, w, h, u, sx, sy, base, halfL, halfR, top, r);
        int x0 = Math.max(0, Math.round(sx - w * 0.3f)), y1 = Math.min(h, Math.round(sy + h * 0.1f));
        Bitmap a = plume(w, h, sx, sy, x0, y1, top, new Random(1987)), b2 = plume(w, h, sx, sy, x0, y1, top, new Random(2024));
        int[] box = opaque(a, opaque(b2, null));
        out.smoke = new Bitmap[] {half(a, box), half(b2, box)};
        out.smokeX = x0 + box[0];
        out.smokeY = box[1];
        out.smokeScale = 2f;
        out.originX = sx;
        out.originY = sy;
        int vx = Math.max(0, Math.round(sx - w * 0.08f)), vy = Math.max(0, Math.round(sy - h * 0.2f));
        out.over = Bitmap.createBitmap(Math.round(w * 0.16f), Math.round(h * 0.23f), Bitmap.Config.ARGB_8888);
        Canvas oc = new Canvas(out.over);
        oc.translate(-vx, -vy);
        vent(oc, w, h, u, sx, sy, r);
        out.overX = vx;
        out.overY = vy;
        return out;
    }

    /** The cone's skyline: a narrow summit, steep high slopes easing out at the foot, a long right flank with a shoulder. */
    private static float profile(int x, int w, int h, float sx, float sy, float base, float halfL, float halfR) {
        float d = x - sx, hgt = base - sy;
        float q = d < 0 ? (-d - w * 0.016f) / halfL : (d - w * 0.016f) / halfR;
        float rim = w * 0.016f;
        if (Math.abs(d) < rim) return sy + h * 0.006f * (1f - (d / rim) * (d / rim)) - h * 0.001f * Noise.fbm(x * 0.2f, 0f, 427, 2);
        q = Math.min(1.2f, q);
        float y = sy + hgt * (1f - (float) Math.pow(Math.max(0f, 1f - q), 1.7f));
        if (d > 0) y -= h * 0.018f * (float) Math.exp(-Math.pow((q - 0.62f) / 0.12f, 2));
        y += h * 0.004f * (Noise.fbm(x / (float) w * 60f, 0.7f, 403, 3) - 0.5f) * (0.3f + q);
        return q >= 1f ? base + (q - 1f) * h * 0.02f : y;
    }

    /** Navy night, a little lighter towards the horizon, stars, a bank of mist on the right and the low land under it. */
    private static void sky(int[] px, int w, int h, Random r, float[] land) {
        float horizon = h * 0.8f;
        for (int y = 0; y < h; y++) {
            float t = Math.min(1f, y / horizon);
            float br = 0.018f + 0.035f * t * t, bg = 0.024f + 0.04f * t * t, bb = 0.055f + 0.06f * t * t;
            for (int x = 0; x < w; x++) {
                float fx = x / (float) w, fy = y / (float) h;
                float rr = br, gg = bg, bl = bb;
                if (y < land[x]) {
                    float band = Noise.smooth(0.66f, 0.76f, fy) * (1f - Noise.smooth(0.8f, 0.83f, fy));
                    float mist = band * Noise.smooth(0.42f, 0.62f, fx) * Noise.smooth(0.42f, 0.78f, Noise.fbm(fx * 6f, fy * 26f, 405, 5));
                    float lit = 0.6f + 0.6f * Noise.smooth(0.5f, 0.8f, Noise.fbm(fx * 6f, fy * 26f - 0.4f, 405, 5));
                    rr += mist * 0.13f * lit;
                    gg += mist * 0.14f * lit;
                    bl += mist * 0.16f * lit;
                } else {
                    float grain = 0.8f + 0.4f * Noise.fbm(fx * 300f, fy * 200f, 407, 2);
                    float depth = Math.min(1f, (y - land[x]) / (h * 0.1f));
                    rr = (0.022f - 0.01f * depth) * grain;
                    gg = (0.025f - 0.011f * depth) * grain;
                    bl = (0.034f - 0.014f * depth) * grain;
                }
                px[y * w + x] = Color.rgb(c255(rr), c255(gg), c255(bl));
            }
        }
        for (int k = 0; k < w * h / 700; k++) {
            int x = r.nextInt(w), y = r.nextInt(Math.round(h * 0.78f));
            float b = (float) Math.pow(r.nextFloat(), 4f) * 0.85f + 0.06f;
            int c = px[y * w + x];
            int add = c255(b * (1f - 0.6f * y / (h * 0.78f)));
            px[y * w + x] = Color.rgb(Math.min(255, Color.red(c) + add), Math.min(255, Color.green(c) + add), Math.min(255, Color.blue(c) + add));
            if (b > 0.55f && x + 1 < w && y + 1 < h) {
                int half = add / 3;
                for (int o : new int[] {1, w, -1, -w}) {
                    int i = y * w + x + o;
                    if (i < 0 || i >= px.length) continue;
                    int n = px[i];
                    px[i] = Color.rgb(Math.min(255, Color.red(n) + half), Math.min(255, Color.green(n) + half), Math.min(255, Color.blue(n) + half));
                }
            }
        }
    }

    /** The fire's light on the air: wide around the column and the left of it, where the ash hangs. */
    private static void haze(Canvas c, int w, int h, float sx, float sy) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));
        glow(c, p, sx - w * 0.08f, sy - h * 0.18f, w * 0.34f, 0x38FF4A10);
        glow(c, p, sx - w * 0.02f, sy - h * 0.06f, w * 0.18f, 0x40FF5A14);
        glow(c, p, sx, sy + h * 0.06f, w * 0.2f, 0x28FF4010);
    }

    private static void glow(Canvas c, Paint p, float x, float y, float radius, int color) {
        p.setShader(new RadialGradient(x, y, radius, new int[] {color, Apps.withAlpha(color, Color.alpha(color) / 3), 0x00000000},
                new float[] {0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, radius, p);
        p.setShader(null);
    }

    /**
     * The ash column as a cloud: puffs along a rising spine add up to a density, noise breaks its
     * surface into billows, and each pixel is lit from the cloud's surface: the fire from below and
     * from the left on the lower edges, the sky faintly on top, darker deep inside. Where the density
     * fades the cloud thins into the sky.
     */
    private static Bitmap plume(int w, int h, float sx, float sy, int x0, int y1, float[] cone, Random r) {
        float rise = sy - h * 0.155f;
        int x1 = Math.min(w, Math.round(sx + w * 0.16f)), y0 = 0;
        int bw = x1 - x0, bh = y1 - y0;
        float[] dens = new float[bw * bh], heat = new float[bw * bh];
        List<float[]> puffs = new ArrayList<>();
        for (int i = 0; i < 150; i++) {
            float t = (float) Math.pow(r.nextFloat(), 0.6f);
            float spineX = sx - w * 0.02f * t - w * 0.008f * (float) Math.sin(t * 4.5f), spineY = sy - rise * t;
            float rad = w * (0.01f + 0.027f * (float) Math.pow(t, 0.8f)) * (0.7f + 0.5f * r.nextFloat());
            float spread = w * (0.004f + 0.11f * (float) Math.pow(t, 2.5f));
            float px = spineX + (float) r.nextGaussian() * spread * 0.5f, py = spineY + (float) r.nextGaussian() * rad * 0.3f;
            float fire = Math.min(1f, 1.3f * (float) Math.exp(-t * 6f));
            puffs.add(new float[] {px, py, rad, fire, spineX});
            bud(puffs, r, px, py, rad, fire, spineX, 2);
        }
        for (float[] f : puffs) splat(dens, heat, bw, bh, f[0] - x0, f[1] - y0, f[2], 1f, f[3]);
        int[] out = new int[bw * bh];
        Bitmap layer = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        for (int y = 1; y < bh - 1; y++) {
            for (int x = 1; x < bw - 1; x++) {
                int i = y * bw + x;
                if (y + y0 >= cone[x + x0]) continue;
                float gx = (x + x0) / (float) w, gy = (y + y0) / (float) h;
                if (dens[i] < 0.02f) continue;
                float bump = Noise.fbm(gx * 38f, gy * 38f, 421, 4), fine = Noise.fbm(gx * 110f, gy * 110f, 423, 2);
                float d = dens[i] * (0.95f + 0.1f * bump);
                float dl = dens[i - 1] * (0.95f + 0.1f * Noise.fbm(gx * 38f - 38f / w, gy * 38f, 421, 4));
                float dr = dens[i + 1] * (0.95f + 0.1f * Noise.fbm(gx * 38f + 38f / w, gy * 38f, 421, 4));
                float du = dens[i - bw] * (0.95f + 0.1f * Noise.fbm(gx * 38f, gy * 38f - 38f / h, 421, 4));
                float dd = dens[i + bw] * (0.95f + 0.1f * Noise.fbm(gx * 38f, gy * 38f + 38f / h, 421, 4));
                float nx = (dl - dr) * 10f, ny = (du - dd) * 10f, nz = 1f;
                float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                nx /= len;
                ny /= len;
                nz /= len;
                float below = Math.max(0f, -0.45f * nx + 0.75f * ny + 0.35f * nz);
                float sky = Math.max(0f, -0.8f * ny + 0.4f * nz);
                float alpha = Noise.smooth(0.42f, 0.6f, d);
                if (alpha < 0.01f) continue;
                float deep = 1f - 0.55f * Noise.smooth(0.8f, 2.2f, d);
                float fire = heat[i] / Math.max(0.001f, dens[i]);
                float left = Math.max(0f, Math.min(1f, (sx - (x + x0)) / (w * 0.12f) + 0.2f));
                float toVent = (float) Math.hypot((x + x0 - sx) / (float) w, (y + y0 - sy) / (float) h * 0.8f);
                float lamp = (float) Math.exp(-toVent / 0.16f) + 0.45f * left * (float) Math.exp(-toVent / 0.28f);
                float grey = (0.04f + 0.025f * sky) * deep * (0.92f + 0.16f * fine);
                float lit = below * lamp * 1.9f * deep + 0.25f * lamp * deep;
                float rr = grey + lit * 0.95f + fire * 0.55f, gg = grey * 0.95f + lit * 0.34f + fire * 0.2f;
                float bb = grey * 0.95f + lit * 0.08f + fire * 0.03f;
                out[i] = Color.argb(c255(alpha), c255(Math.min(1f, rr)), c255(Math.min(1f, gg)), c255(Math.min(1f, bb)));
            }
        }
        layer.setPixels(out, 0, bw, 0, 0, bw, bh);
        return layer;
    }

    /** Lit ash hanging low on the left of the column, still enough to be part of the picture. */
    private static void ash(int[] px, int w, int h, float[] cone) {
        int xa = Math.max(0, Math.round(w * (VENT_X - 0.3f))), xb = Math.round(w * (VENT_X + 0.08f)), yb = Math.round(h * (VENT_Y + 0.1f));
        for (int y = 0; y < yb; y++) {
            for (int x = xa; x < xb; x++) {
                if (y >= cone[x]) continue;
                float gx = x / (float) w, gy = y / (float) h;
                float ex = (gx - (VENT_X - 0.08f)) / 0.13f, ey = (gy - (VENT_Y - 0.12f)) / 0.17f;
                float a = 0.75f * (float) Math.exp(-(ex * ex + ey * ey)) * Noise.smooth(0.35f, 0.75f, Noise.fbm(gx * 9f, gy * 9f, 425, 5));
                if (a < 0.01f) continue;
                int c = px[y * w + x];
                px[y * w + x] = Color.rgb(mixC(Color.red(c), c255(0.62f), a), mixC(Color.green(c), c255(0.2f), a), mixC(Color.blue(c), c255(0.05f), a));
            }
        }
    }

    /** The box (left, top, right, bottom) holding every visible pixel of `b`, grown to hold `box` too. */
    private static int[] opaque(Bitmap b, int[] box) {
        int bw = b.getWidth(), bh = b.getHeight();
        int[] px = new int[bw * bh];
        b.getPixels(px, 0, bw, 0, 0, bw, bh);
        int[] out = box != null ? box.clone() : new int[] {bw, bh, 0, 0};
        for (int y = 0; y < bh; y++) {
            for (int x = 0; x < bw; x++) {
                if ((px[y * bw + x] >>> 24) < 4) continue;
                out[0] = Math.min(out[0], x);
                out[1] = Math.min(out[1], y);
                out[2] = Math.max(out[2], x + 1);
                out[3] = Math.max(out[3], y + 1);
            }
        }
        if (out[2] <= out[0]) return new int[] {0, 0, 2, 2};
        out[2] = Math.min(bw, out[0] + (out[2] - out[0] + 1) / 2 * 2);
        out[3] = Math.min(bh, out[1] + (out[3] - out[1] + 1) / 2 * 2);
        return out;
    }

    /** `b` cut to `box` at half resolution: smoke is soft, and the TV reads a quarter of the pixels every frame. */
    private static Bitmap half(Bitmap b, int[] box) {
        Bitmap cut = Bitmap.createBitmap(b, box[0], box[1], box[2] - box[0], box[3] - box[1]);
        Bitmap small = Bitmap.createScaledBitmap(cut, Math.max(1, cut.getWidth() / 2), Math.max(1, cut.getHeight() / 2), true);
        if (cut != b) cut.recycle();
        b.recycle();
        return small;
    }

    /** Buds smaller puffs on a puff's outer surface, and buds on those, `depth` times: billows on billows. */
    private static void bud(List<float[]> puffs, Random r, float x, float y, float rad, float fire, float spineX, int depth) {
        if (depth == 0 || rad < 2f) return;
        int n = depth == 2 ? 4 + r.nextInt(3) : 2 + r.nextInt(2);
        for (int k = 0; k < n; k++) {
            double a = r.nextDouble() * Math.PI * 2;
            float ox = (float) Math.cos(a), oy = (float) Math.sin(a);
            if (ox * (x - spineX) < 0 && r.nextFloat() < 0.7f) ox = -ox;
            float br = rad * (depth == 2 ? 0.42f + 0.16f * r.nextFloat() : 0.36f + 0.12f * r.nextFloat()), bx = x + ox * rad * 0.8f, by = y + oy * rad * 0.8f;
            puffs.add(new float[] {bx, by, br, fire * 0.95f, spineX});
            bud(puffs, r, bx, by, br, fire, spineX, depth - 1);
        }
    }

    /** Adds one puff to the cloud: a soft ball of `weight`, carrying `fire` of the vent's light. */
    private static void splat(float[] dens, float[] heat, int bw, int bh, float cx, float cy, float rad, float weight, float fire) {
        int xa = Math.max(0, (int) (cx - rad)), xb = Math.min(bw - 1, (int) (cx + rad) + 1);
        int ya = Math.max(0, (int) (cy - rad)), yb = Math.min(bh - 1, (int) (cy + rad) + 1);
        float r2 = rad * rad;
        for (int y = ya; y <= yb; y++) {
            for (int x = xa; x <= xb; x++) {
                float dx = x - cx, dy = y - cy, q = (dx * dx + dy * dy) / r2;
                if (q >= 1f) continue;
                float k = (1f - q) * (1f - q) * weight;
                dens[y * bw + x] += k;
                heat[y * bw + x] += k * fire;
            }
        }
    }

    /**
     * The cone, pixel by pixel: where each point faces around the cone and how far down it is, ravines
     * running down it from a ridged noise, lit by the glow at the summit and the moonless sky, fading
     * into the land at its foot.
     */
    private static void cone(int[] px, int w, int h, float sx, float sy, float base, float halfL, float halfR, float[] top, float[] land) {
        float hgt = base - sy;
        int y0 = Math.max(0, (int) sy - 4);
        for (int y = y0; y < h; y++) {
            float t = Math.max(0f, (y - sy) / hgt);
            float reach = 1f - (float) Math.pow(Math.max(0f, 1f - Math.min(1f, t)), 1f / 1.7f);
            for (int x = 0; x < w; x++) {
                if (y < top[x] || y >= land[x] + h * 0.012f) continue;
                float d = x - sx, half = Math.max(1f, reach * (d < 0 ? halfL : halfR));
                float f = Math.max(-1f, Math.min(1f, d / half));
                float ang = (float) Math.asin(f);
                float ravine = ridged(ang, t), next = ridged(ang + 0.01f, t);
                float facing = (next - ravine) / 0.01f;
                float light = 0.32f + 0.15f * ravine + 0.12f * Math.max(-1f, Math.min(1f, -facing * 0.4f)) - 0.12f * f;
                float summit = (float) Math.exp(-Math.hypot(d, (y - sy) * 1.4f) / (w * 0.07f));
                float grain = (0.8f + 0.4f * Noise.fbm(x / (float) w * 220f, y / (float) h * 140f, 409, 2)) * (0.55f + 0.9f * Noise.fbm(x / (float) w * 24f, y / (float) h * 16f, 419, 4));
                float foot = 1f - 0.6f * Noise.smooth(0.55f, 1.05f, t);
                float rr = (0.15f * light * grain + 0.32f * summit) * foot, gg = (0.125f * light * grain + 0.085f * summit) * foot;
                float bb = (0.115f * light * grain + 0.025f * summit) * foot;
                float edge = Math.min(1f, (y - top[x]) / 2f);
                int old = px[y * w + x];
                float blend = y >= land[x] ? 1f - (y - land[x]) / (h * 0.012f) : 1f;
                blend *= edge;
                px[y * w + x] = Color.rgb(mixC(Color.red(old), c255(rr), blend), mixC(Color.green(old), c255(gg), blend), mixC(Color.blue(old), c255(bb), blend));
            }
        }
    }

    private static float ridged(float ang, float t) {
        float warp = 0.12f * (Noise.fbm(t * 2.2f, ang * 1.5f, 411, 2) - 0.5f);
        return 1f - Math.abs(2f * Noise.fbm((ang + warp) * 4f + 2.3f, t * 1.6f, 413, 3) - 1f);
    }

    /**
     * Lava rivers: each leaves the summit at its own bearing and flows down, drawn into the ravines
     * by the relief and nudged by noise, forking now and then; drawn as a wide red glow, an orange
     * body and a white-hot thread, cooling and thinning as it goes.
     */
    private static List<float[]> rivers(Canvas c, int w, int h, float u, float sx, float sy, float base, float halfL, float halfR, float[] top, Random r) {
        List<float[]> flows = new ArrayList<>();
        float hgt = base - sy;
        List<float[]> sources = new ArrayList<>(Arrays.asList(
                new float[] {-0.35f, 0.3f, 0.6f}, new float[] {0.05f, 0.5f, 0.75f}, new float[] {0.3f, 0.92f, 1f},
                new float[] {0.55f, 0.75f, 0.8f}));
        Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG), body = new Paint(Paint.ANTI_ALIAS_FLAG), core = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (Paint p : new Paint[] {glow, body, core}) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
        }
        glow.setMaskFilter(new BlurMaskFilter(7f * u, BlurMaskFilter.Blur.NORMAL));
        PorterDuffXfermode add = new PorterDuffXfermode(PorterDuff.Mode.ADD);
        glow.setXfermode(add);
        body.setXfermode(add);
        core.setXfermode(add);
        for (int s = 0; s < sources.size(); s++) {
            float[] src = sources.get(s);
            float ang = src[0], end = src[1], strength = src[2], t = src.length > 3 ? src[3] : 0.025f;
            Path path = new Path();
            List<float[]> pts = new ArrayList<>();
            while (t < end) {
                float grad = (ridged(ang + 0.01f, t) - ridged(ang - 0.01f, t)) / 0.02f;
                ang += -grad * 0.004f + (float) r.nextGaussian() * 0.006f;
                ang = Math.max(-1.3f, Math.min(1.3f, ang));
                float reach = 1f - (float) Math.pow(Math.max(0f, 1f - t), 1f / 1.7f);
                float half = reach * (ang < 0 ? halfL : halfR);
                float x = sx + (float) Math.sin(ang) * half, y = sy + t * hgt;
                int col = Math.max(0, Math.min(w - 1, Math.round(x)));
                y = Math.max(y, top[col] + 1f);
                pts.add(new float[] {x, y, t});
                if (r.nextFloat() < 0.02f && t > 0.15f && sources.size() < 12) {
                    sources.add(new float[] {ang + (r.nextBoolean() ? 1 : -1) * (0.05f + 0.06f * r.nextFloat()), Math.min(1f, t + 0.15f + 0.35f * r.nextFloat()), strength * 0.6f, t});
                }
                t += 0.006f;
            }
            float startT = src.length > 3 ? src[3] : 0f;
            float[] flow = new float[1 + pts.size() * 2];
            flow[0] = strength;
            for (int k = 0; k < pts.size(); k++) {
                flow[1 + 2 * k] = pts.get(k)[0];
                flow[2 + 2 * k] = pts.get(k)[1];
            }
            if (pts.size() > 2) flows.add(flow);
            for (int k = 1; k < pts.size(); k++) {
                float[] a = pts.get(k - 1), b = pts.get(k);
                float tt = b[2], heat = strength * (1f - 0.75f * (tt - startT) / Math.max(0.05f, end - startT));
                if (Noise.fbm(tt * 30f, s * 3.1f, 415, 2) > 0.72f) continue;
                float wdt = u * (0.8f + 1.5f * heat);
                glow.setStrokeWidth(wdt * 4f);
                glow.setColor(Apps.withAlpha(0xFFFF3A08, c255(0.32f * heat)));
                c.drawLine(a[0], a[1], b[0], b[1], glow);
                body.setStrokeWidth(wdt * 1.3f);
                body.setColor(Apps.withAlpha(0xFFFF5A10, c255(0.5f * heat)));
                c.drawLine(a[0], a[1], b[0], b[1], body);
                core.setStrokeWidth(Math.max(0.8f * u, wdt * 0.45f));
                core.setColor(Apps.withAlpha(0xFFFFB050, c255(0.4f * heat * heat)));
                c.drawLine(a[0], a[1], b[0], b[1], core);
            }
        }
        return flows;
    }

    /** The vent: a tapering column of incandescence and the spatter flying out of it. */
    private static void vent(Canvas c, int w, int h, float u, float sx, float sy, Random r) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));
        for (int k = 0; k < 14; k++) {
            float t = k / 13f, y = sy - h * 0.12f * t, rad = w * (0.014f + 0.026f * t);
            glow(c, p, sx - w * 0.008f * t, y, rad, Apps.withAlpha(Apps.mix(0xFFFFB040, 0xFFFF4A10, t), c255(0.45f * (1f - t) + 0.06f)));
        }
        glow(c, p, sx, sy - h * 0.012f, w * 0.018f, 0x90FFD890);
        for (int k = 0; k < 260; k++) {
            double a = Math.toRadians(-90 + r.nextGaussian() * 18);
            float dist = h * 0.13f * (float) Math.pow(r.nextFloat(), 1.6f);
            float x = sx + (float) Math.cos(a) * dist, y = sy - h * 0.005f + (float) Math.sin(a) * dist;
            float heat = 1f - dist / (h * 0.13f);
            p.setColor(Apps.withAlpha(Apps.mix(0xFFFF5A14, 0xFFFFE6A0, heat), c255(0.4f + 0.6f * heat * r.nextFloat())));
            c.drawCircle(x, y, u * (0.8f + 1.6f * r.nextFloat()), p);
        }
    }

    private static int rgb(float r, float g, float b) {
        return Color.rgb(c255(r), c255(g), c255(b));
    }

    private static int mixC(int a, int b, float t) {
        return Math.round(a + (b - a) * Math.max(0f, Math.min(1f, t)));
    }

    private static int c255(float v) {
        return Math.max(0, Math.min(255, Math.round(v * 255)));
    }
}
