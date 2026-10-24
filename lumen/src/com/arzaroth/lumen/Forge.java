package com.arzaroth.lumen;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import java.util.Random;

/**
 * The update, as a forge. The new version stands in cold iron and fills with fire from the bottom
 * as it downloads, flames and sparks pouring off it; when Android takes over to install it, the
 * number blazes white, a shockwave rolls out and it throws a pair of burning wings. Called off or
 * failed, the fire dies, the iron cools to ash and the forge fades.
 */
final class Forge extends View {
    enum Phase { OFF, BURN, RISE, COOL }

    /** Particles, plus the halo behind the number and the seam of its molten line. */
    private static final int N = 900, QUADS = N + 2, SPRITE = 32;
    private static final int FLAME = 0, SPARK = 1, ASH = 2, EMBER = 3, TONGUE = 4;
    private static final float RISE_MAX = 20f, COOL_T = 1.8f;
    /** Heat by age, from the white of a fresh flame to the dark red of a dying ember. */
    private static final int[] HEAT = {0xFFFFFFFF, 0xFFFFF0A8, 0xFFFFC23D, 0xFFFF7A1A, 0xFFE0301E, 0xFF6E0A12};

    private final float s;
    private final Random rnd = new Random();
    private final float[] x = new float[N], y = new float[N], vx = new float[N], vy = new float[N];
    private final float[] age = new float[N], life = new float[N], size = new float[N], seed = new float[N];
    private final int[] kind = new int[N], ashOnes = new int[N];
    private final float[] verts = new float[QUADS * 8], texs = new float[QUADS * 8];
    private final int[] colors = new int[QUADS * 4];
    private final short[] indices = new short[QUADS * 6];
    private final Paint fire = new Paint(Paint.FILTER_BITMAP_FLAG), ash = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint veil = new Paint(), floor = new Paint(), ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iron = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hot = new Paint(Paint.ANTI_ALIAS_FLAG), white = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint title = new Paint(Paint.ANTI_ALIAS_FLAG), sub = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final LinearGradient molten;
    private final Matrix moltenMatrix = new Matrix();
    private final Rect bounds = new Rect();
    private Phase phase = Phase.OFF;
    private boolean failed;
    private String version = "";
    private float target, heat, shown, blaze, phaseT, ringT = -1f, clock, emit, bottomEmit;
    private int next;
    private long last;

    Forge(Context c, float s) {
        super(c);
        this.s = s;
        PorterDuffXfermode add = new PorterDuffXfermode(PorterDuff.Mode.ADD);
        BitmapShader dot = new BitmapShader(sprite(), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        fire.setShader(dot);
        fire.setXfermode(add);
        ash.setShader(dot);
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
        veil.setColor(0xFF000000);
        ring.setStyle(Paint.Style.STROKE);
        iron.setTypeface(Fonts.outfit(c, 800));
        iron.setTextSize(250 * s);
        iron.setTextAlign(Paint.Align.CENTER);
        hot.set(iron);
        molten = new LinearGradient(0, 0, 0, 1, new int[] {0xFFFFFFFF, 0xFFFFE27A, 0xFFFFA126, 0xFFF0461C, 0xFF8E0E14},
                new float[] {0f, 0.06f, 0.28f, 0.62f, 1f}, Shader.TileMode.CLAMP);
        hot.setShader(molten);
        white.set(iron);
        white.setColor(0xFFFFFFFF);
        title.setTypeface(Fonts.outfit(c, 600));
        title.setTextSize(24 * s);
        title.setLetterSpacing(0.42f);
        title.setTextAlign(Paint.Align.CENTER);
        sub.set(title);
        sub.setTypeface(Fonts.outfit(c, 500));
        sub.setTextSize(20 * s);
        sub.setLetterSpacing(0.3f);
        setVisibility(GONE);
    }

    private static Bitmap sprite() {
        Bitmap b = Bitmap.createBitmap(SPRITE, SPRITE, Bitmap.Config.ARGB_8888);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(SPRITE / 2f, SPRITE / 2f, SPRITE / 2f,
                new int[] {0xFFFFFFFF, 0x99FFFFFF, 0x26FFFFFF, 0x00FFFFFF}, new float[] {0f, 0.2f, 0.5f, 1f},
                Shader.TileMode.CLAMP));
        new Canvas(b).drawCircle(SPRITE / 2f, SPRITE / 2f, SPRITE / 2f, p);
        return b;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        floor.setShader(new LinearGradient(0, h * 0.55f, 0, h, 0x00B8240E, 0xFFB8240E, Shader.TileMode.CLAMP));
    }

    /** Whether the forge is up and holding the screen. */
    boolean shown() {
        return phase != Phase.OFF;
    }

    Phase phase() {
        return phase;
    }

    /** Follows the update: the download burns, the install rises, anything else cools the fire. */
    void follow(Updater.State state, String latest, int progress) {
        if (state == Updater.State.DOWNLOADING) {
            if (phase != Phase.BURN) {
                start(Phase.BURN);
                heat = 0f;
                ignite();
            }
            version = latest;
            target = progress / 100f;
        } else if (state == Updater.State.INSTALLING) {
            if (phase != Phase.RISE) {
                if (phase == Phase.OFF) start(Phase.BURN);
                version = latest;
                rise();
            }
        } else if (phase == Phase.BURN || phase == Phase.RISE) {
            failed = state == Updater.State.FAILED;
            start(Phase.COOL);
        }
    }

    /** Back, once Android has been handed the install: there is nothing left to wait for here. */
    void cool() {
        if (phase == Phase.RISE) start(Phase.COOL);
    }

    private void start(Phase p) {
        if (phase == Phase.OFF) {
            last = SystemClock.uptimeMillis();
            setVisibility(VISIBLE);
        }
        phase = p;
        phaseT = 0f;
        invalidate();
    }

    private void rise() {
        phase = Phase.RISE;
        phaseT = 0f;
        target = 1f;
        ringT = 0f;
        float cx = getWidth() / 2f, cy = numberY() - numberHeight() / 2f;
        float half = numberWidth() / 2f;
        for (int i = 0; i < 480; i++) {
            boolean right = i % 2 == 0;
            float a = (float) Math.toRadians(right ? -12 - rnd.nextFloat() * 48 : -168 + rnd.nextFloat() * 48);
            float v = (500 + rnd.nextFloat() * 1100) * s;
            float from = cx + (right ? 1 : -1) * half * (0.2f + rnd.nextFloat() * 0.8f);
            spawn(i % 4 == 0 ? FLAME : SPARK, from, cy + (rnd.nextFloat() - 0.5f) * numberHeight() * 0.6f,
                    (float) Math.cos(a) * v, (float) Math.sin(a) * v, 1.3f + rnd.nextFloat() * 1.6f, (3 + rnd.nextFloat() * 7) * s);
        }
        invalidate();
    }

    /** Embers drawn in from all around, spiralling into the iron. */
    private void ignite() {
        float cx = getWidth() / 2f, cy = getHeight() * 0.45f, r = getHeight() * 0.62f;
        for (int i = 0; i < 160; i++) {
            float a = rnd.nextFloat() * 6.2832f, d = r * (0.8f + rnd.nextFloat() * 0.4f), t = 0.9f + rnd.nextFloat() * 0.6f;
            float px = cx + (float) Math.cos(a) * d, py = cy + (float) Math.sin(a) * d;
            float tx = -(float) Math.sin(a) * d * 0.9f, ty = (float) Math.cos(a) * d * 0.9f;
            spawn(EMBER, px, py, (cx - px) / t + tx * 0.4f, (cy - py) / t + ty * 0.4f, t, (3 + rnd.nextFloat() * 5) * s);
        }
    }

    private void spawn(int k, float px, float py, float pvx, float pvy, float plife, float psize) {
        int i = next;
        next = (next + 1) % N;
        kind[i] = k;
        x[i] = px;
        y[i] = py;
        vx[i] = pvx;
        vy[i] = pvy;
        age[i] = 0f;
        life[i] = plife;
        size[i] = psize;
        seed[i] = rnd.nextFloat() * 100f;
    }

    private float numberWidth() {
        return iron.measureText(version.isEmpty() ? "0.0.0" : version);
    }

    private float numberHeight() {
        iron.getTextBounds("0", 0, 1, bounds);
        return bounds.height();
    }

    private float numberY() {
        return getHeight() * 0.5f + numberHeight() / 2f;
    }

    private void step(float dt) {
        clock += dt;
        phaseT += dt;
        heat += (target - heat) * (1f - (float) Math.exp(-dt * 4f));
        boolean lit = phase == Phase.BURN || phase == Phase.RISE;
        shown = lit ? Math.min(1f, shown + dt * 3f) : phaseT > COOL_T * 0.6f ? Math.max(0f, shown - dt * 1.6f) : shown;
        blaze = phase == Phase.RISE ? Math.min(1f, blaze + dt * 2.5f) : Math.max(0f, blaze - dt * 1.5f);
        if (ringT >= 0f && (ringT += dt) > 1.4f) ringT = -1f;
        if (phase == Phase.RISE && phaseT > RISE_MAX) start(Phase.COOL);

        float w = numberWidth(), h = numberHeight(), cx = getWidth() / 2f, base = numberY();
        if (lit) {
            float fill = base - h * heat;
            emit += dt * (phase == Phase.RISE ? 520f : 70f + 420f * heat);
            for (; emit >= 1f; emit--) {
                float px = cx + (rnd.nextFloat() - 0.5f) * w;
                float py = phase == Phase.RISE ? base - rnd.nextFloat() * h : fill + (rnd.nextFloat() - 0.3f) * 14 * s;
                if (rnd.nextInt(6) == 0) {
                    spawn(SPARK, px, py, (rnd.nextFloat() - 0.5f) * 160 * s, -(260 + rnd.nextFloat() * 420) * s, 0.6f + rnd.nextFloat() * 0.8f, (1.5f + rnd.nextFloat() * 2f) * s);
                } else {
                    spawn(FLAME, px, py, (rnd.nextFloat() - 0.5f) * 40 * s, -(40 + rnd.nextFloat() * 110) * s, 0.5f + rnd.nextFloat() * 0.7f, (10 + rnd.nextFloat() * 18) * s * (0.6f + heat));
                }
            }
            bottomEmit += dt * (90f + 260f * heat + 260f * blaze);
            for (; bottomEmit >= 1f; bottomEmit--) {
                spawn(TONGUE, rnd.nextFloat() * getWidth(), getHeight() + 10 * s, (rnd.nextFloat() - 0.5f) * 30 * s,
                        -(160 + rnd.nextFloat() * 260) * s * (0.7f + 0.5f * heat), 0.7f + rnd.nextFloat() * 0.9f, (12 + rnd.nextFloat() * 22) * s);
            }
        } else if (phase == Phase.COOL && phaseT < 1f) {
            emit += dt * 60f;
            for (; emit >= 1f; emit--) {
                spawn(ASH, cx + (rnd.nextFloat() - 0.5f) * w, base - rnd.nextFloat() * h, (rnd.nextFloat() - 0.5f) * 30 * s,
                        (10 + rnd.nextFloat() * 40) * s, 2f + rnd.nextFloat() * 1.5f, (2 + rnd.nextFloat() * 3) * s);
            }
        }

        float drag = (float) Math.exp(-dt * 1.2f);
        for (int i = 0; i < N; i++) {
            if (age[i] >= life[i]) continue;
            age[i] += dt;
            switch (kind[i]) {
                case FLAME:
                case TONGUE:
                    vy[i] -= 90 * s * dt;
                    vx[i] += (float) Math.sin(clock * 3f + y[i] * 0.012f + seed[i]) * 120 * s * dt;
                    vx[i] *= drag;
                    break;
                case SPARK:
                    vy[i] += 160 * s * dt;
                    vx[i] *= drag;
                    break;
                case ASH:
                    vx[i] += (float) Math.sin(clock + seed[i]) * 20 * s * dt;
                    break;
                default:
                    break;
            }
            x[i] += vx[i] * dt;
            y[i] += vy[i] * dt;
        }
        if (phase == Phase.COOL && phaseT > COOL_T && shown <= 0f) {
            phase = Phase.OFF;
            failed = false;
            blaze = 0f;
            heat = 0f;
            ringT = -1f;
            for (int i = 0; i < N; i++) age[i] = life[i];
            setVisibility(GONE);
        }
    }

    private static int heatColor(float u) {
        float f = Math.max(0f, Math.min(0.999f, u)) * (HEAT.length - 1);
        int i = (int) f;
        return Apps.mix(HEAT[i], HEAT[i + 1], f - i);
    }

    private void quad(int q, float x0, float y0, float x1, float y1, float r) {
        float dx = x1 - x0, dy = y1 - y0, len = (float) Math.sqrt(dx * dx + dy * dy);
        float ux = len < 0.001f ? 1 : dx / len, uy = len < 0.001f ? 0 : dy / len;
        int o = q * 8;
        verts[o] = x0 - ux * r - uy * r;
        verts[o + 1] = y0 - uy * r + ux * r;
        verts[o + 2] = x1 + ux * r - uy * r;
        verts[o + 3] = y1 + uy * r + ux * r;
        verts[o + 4] = x1 + ux * r + uy * r;
        verts[o + 5] = y1 + uy * r - ux * r;
        verts[o + 6] = x0 - ux * r + uy * r;
        verts[o + 7] = y0 - uy * r - ux * r;
    }

    private void tint(int q, int color) {
        int o = q * 4;
        colors[o] = color;
        colors[o + 1] = color;
        colors[o + 2] = color;
        colors[o + 3] = color;
    }

    @Override
    protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        step(Math.min(0.05f, Math.max(0f, (now - last) / 1000f)));
        last = now;
        if (phase == Phase.OFF) return;
        int w = getWidth(), h = getHeight();
        float cx = w / 2f, base = numberY(), nh = numberHeight(), nw = numberWidth();
        float cooled = phase == Phase.COOL ? Math.min(1f, phaseT / 1.2f) : 0f;
        float glow = shown * (1f - cooled);

        veil.setAlpha(Math.round(235 * shown));
        c.drawRect(0, 0, w, h, veil);
        floor.setAlpha(Math.round(255 * glow * (0.3f + 0.5f * heat + 0.2f * blaze)));
        c.drawRect(0, h * 0.55f, w, h, floor);

        int q = 0;
        float halo = nw * (0.7f + 0.25f * blaze + 0.04f * (float) Math.sin(clock * 5f));
        quad(q, cx, base - nh / 2f, cx, base - nh / 2f, halo);
        tint(q++, Apps.withAlpha(0xFFFF6A1A, Math.round(150 * glow * (0.25f + 0.75f * Math.max(heat, blaze)))));
        float seam = base - nh * heat;
        if (heat > 0.01f && heat < 0.99f) {
            quad(q, cx - nw * 0.42f, seam, cx + nw * 0.42f, seam, 26 * s);
            tint(q++, Apps.withAlpha(0xFFFFC870, Math.round(200 * glow)));
        }
        int a = 0;
        for (int i = 0; i < N; i++) {
            if (age[i] >= life[i]) continue;
            float u = age[i] / life[i];
            if (kind[i] == ASH) {
                ashOnes[a++] = i;
                continue;
            }
            boolean tongue = kind[i] == TONGUE;
            float r = size[i] * (kind[i] == FLAME || tongue ? 1f - 0.6f * u : 1f - 0.3f * u);
            float k = tongue ? 0.32f * (1f - 0.5f * u) : kind[i] == SPARK ? 0.035f : kind[i] == EMBER ? 0.06f : 0.07f;
            quad(q, x[i] - vx[i] * k, y[i] - vy[i] * k, x[i], y[i], r);
            float fade = (float) Math.pow(1f - u, 1.3f) * (kind[i] == EMBER ? Math.min(1f, u * 4f) : tongue ? 0.55f * Math.min(1f, u * 6f) : 1f);
            float tone = kind[i] == SPARK ? u * 0.6f : tongue ? 0.2f + 0.8f * u : u;
            tint(q++, Apps.withAlpha(heatColor(tone), Math.round(255 * fade * Math.max(0.2f, shown))));
        }

        float fill = base - nh * heat * (1f - cooled * 0.98f);
        int ember = Apps.mix(0xFF2A1612, 0xFF4A1A10, 0.5f + 0.5f * (float) Math.sin(clock * 2.4f));
        iron.setColor(Apps.withAlpha(Apps.mix(ember, 0xFF4A4542, cooled), Math.round(255 * shown)));
        c.drawText(version, cx, base, iron);
        if (heat > 0.001f && cooled < 1f) {
            c.save();
            c.clipRect(0, fill, w, base + nh);
            moltenMatrix.setScale(1f, Math.max(1f, base - fill));
            moltenMatrix.postTranslate(0, fill);
            molten.setLocalMatrix(moltenMatrix);
            hot.setAlpha(Math.round(255 * shown * (1f - cooled)));
            c.drawText(version, cx, base, hot);
            c.restore();
        }
        if (blaze > 0.01f) {
            white.setAlpha(Math.round(220 * blaze * shown));
            c.drawText(version, cx, base, white);
        }
        if (ringT >= 0f) {
            float p = ringT / 1.4f;
            ring.setStrokeWidth((18 - 14 * p) * s);
            ring.setColor(Apps.withAlpha(heatColor(0.3f + 0.5f * p), Math.round(230 * (1 - p) * (1 - p))));
            c.drawCircle(cx, base - nh / 2f, nw * (0.3f + 1.6f * (float) Math.sqrt(p)), ring);
        }
        if (q > 0) c.drawVertices(Canvas.VertexMode.TRIANGLES, q * 8, verts, 0, texs, 0, colors, 0, indices, 0, q * 6, fire);
        if (a > 0) {
            int aq = 0;
            for (int j = 0; j < a; j++) {
                int i = ashOnes[j];
                float u = age[i] / life[i];
                quad(aq, x[i], y[i], x[i], y[i], size[i]);
                tint(aq++, Apps.withAlpha(Apps.mix(0xFFFF7A1A, 0xFF8A8480, Math.min(1f, u * 3f)), Math.round(200 * (1 - u))));
            }
            c.drawVertices(Canvas.VertexMode.TRIANGLES, aq * 8, verts, 0, texs, 0, colors, 0, indices, 0, aq * 6, ash);
        }

        String line1, line2;
        if (phase == Phase.BURN) {
            line1 = "FORGING LUMEN " + version;
            line2 = Math.round(target * 100) + "%";
        } else if (phase == Phase.RISE) {
            line1 = "LUMEN " + version + " RISES";
            line2 = "CONFIRM ON SCREEN TO INSTALL";
        } else {
            line1 = failed ? "THE FIRE WENT OUT" : "THE FORGE COOLS";
            line2 = failed ? "TRY AGAIN FROM ADVANCED SETTINGS" : "";
        }
        title.setColor(Apps.withAlpha(Apps.mix(0xFFFFD9A0, 0xFFFFFFFF, blaze), Math.round(255 * shown)));
        sub.setColor(Apps.withAlpha(0xFFFF9A50, Math.round(220 * shown)));
        c.drawText(line1, cx, base + 110 * s, title);
        if (!line2.isEmpty()) c.drawText(line2, cx, base + 150 * s, sub);
        postInvalidateOnAnimation();
    }
}
