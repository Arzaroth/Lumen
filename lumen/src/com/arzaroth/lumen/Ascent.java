package com.arzaroth.lumen;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import java.util.Random;

/**
 * The update, written in the sky. The new version gathers out of stardust as it downloads, each
 * mote leaving its place among the stars and swirling into the digits; when Android takes over to
 * install it, the number lights up whole with a ring of light. Called off or failed, the dust
 * drifts back out into the sky.
 */
final class Ascent extends View {
    enum Phase { OFF, GATHER, RISE, FADE }

    private static final int MAX = 1600, DIGIT = 220, DOT = 40, SPRITE = 32;
    private static final float TRAVEL_T = 1.3f, RISE_MAX = 20f, FADE_T = 1.6f;
    private static final int[] TINTS = {0xFFFFFFFF, 0xFFDDE6FF, 0xFFBFD4FF, 0xFFFFF2D8};

    private final float s;
    private final Random rnd = new Random();
    private final float[] sx = new float[MAX], sy = new float[MAX], tx = new float[MAX], ty = new float[MAX];
    private final float[] called = new float[MAX], swirl = new float[MAX], seed = new float[MAX], dvx = new float[MAX], dvy = new float[MAX];
    private final int[] tint = new int[MAX];
    private final float[] verts = new float[(MAX + 1) * 8], texs = new float[(MAX + 1) * 8];
    private final int[] colors = new int[(MAX + 1) * 4];
    private final short[] indices = new short[(MAX + 1) * 6];
    private final Paint dust = new Paint(Paint.FILTER_BITMAP_FLAG), scrim = new Paint(), ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint number = new Paint(Paint.ANTI_ALIAS_FLAG), sampler = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint header = new Paint(Paint.ANTI_ALIAS_FLAG), detail = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Phase phase = Phase.OFF;
    private boolean failed;
    private String version = "";
    private int count, calledCount;
    private float target, shown, lit, phaseT, ringT = -1f, clock, baseline;
    private long last;

    Ascent(Context c, float s) {
        super(c);
        this.s = s;
        dust.setShader(new BitmapShader(sprite(), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        dust.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));
        for (int q = 0; q <= MAX; q++) {
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
        scrim.setColor(0xFF05060C);
        ring.setStyle(Paint.Style.STROKE);
        number.setTypeface(Fonts.outfit(c, 200));
        number.setTextSize(200 * s);
        number.setColor(0xFFFFFFFF);
        sampler.setTypeface(Fonts.outfit(c, 400));
        sampler.setTextSize(200 * s);
        header.setTypeface(Fonts.outfit(c, 600));
        header.setTextSize(18 * s);
        header.setLetterSpacing(0.12f);
        header.setTextAlign(Paint.Align.CENTER);
        detail.setTypeface(Fonts.outfit(c, 400));
        detail.setTextSize(22 * s);
        detail.setTextAlign(Paint.Align.CENTER);
        track.setColor(0x33FFFFFF);
        bar.setColor(0xFFFFFFFF);
        setVisibility(GONE);
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

    /** Whether the update is on screen, holding the remote. */
    boolean shown() {
        return phase != Phase.OFF;
    }

    Phase phase() {
        return phase;
    }

    /** Follows the update: the download gathers the dust, the install lights it, anything else lets it go. */
    void follow(Updater.State state, String latest, int progress) {
        if (state == Updater.State.DOWNLOADING) {
            if (phase != Phase.GATHER) start(Phase.GATHER, latest);
            target = progress / 100f;
        } else if (state == Updater.State.INSTALLING) {
            if (phase == Phase.OFF) start(Phase.GATHER, latest);
            if (phase != Phase.RISE) {
                phase = Phase.RISE;
                phaseT = 0f;
                target = 1f;
                ringT = -1f;
            }
        } else if (phase == Phase.GATHER || phase == Phase.RISE) {
            failed = state == Updater.State.FAILED;
            letGo();
        }
    }

    /** Back, once Android has been handed the install: nothing is left to wait for here. */
    void dismiss() {
        if (phase == Phase.RISE) letGo();
    }

    private void start(Phase p, String latest) {
        if (phase == Phase.OFF) {
            last = SystemClock.uptimeMillis();
            setVisibility(VISIBLE);
        }
        phase = p;
        phaseT = 0f;
        version = latest == null ? "" : latest;
        target = 0f;
        lit = 0f;
        calledCount = 0;
        lay();
        invalidate();
    }

    /** Every mote gets a star to leave from and a place on the digits, in an order that fills them evenly. */
    private void lay() {
        int w = Math.max(1, getWidth()), h = Math.max(1, getHeight());
        baseline = h * 0.47f + number.getTextSize() * 0.35f;
        float x = (w - number.measureText(version)) / 2f;
        count = 0;
        for (int k = 0; k < version.length(); k++) {
            char ch = version.charAt(k);
            float adv = number.measureText(version, k, k + 1);
            int n = Character.isDigit(ch) ? DIGIT : DOT;
            float[] pts = Glyphs.sample(sampler, ch, adv, n);
            for (int i = 0; i < n && count < MAX; i++, count++) {
                tx[count] = x + pts[2 * i];
                ty[count] = baseline + pts[2 * i + 1];
            }
            x += adv;
        }
        for (int i = count - 1; i > 0; i--) {
            int o = rnd.nextInt(i + 1);
            float t = tx[i]; tx[i] = tx[o]; tx[o] = t;
            t = ty[i]; ty[i] = ty[o]; ty[o] = t;
        }
        for (int i = 0; i < count; i++) {
            sx[i] = rnd.nextFloat() * w;
            sy[i] = rnd.nextFloat() * h * 0.9f;
            called[i] = -1f;
            swirl[i] = (rnd.nextFloat() - 0.5f) * 260 * s;
            seed[i] = rnd.nextFloat() * 100f;
            tint[i] = TINTS[rnd.nextInt(TINTS.length)];
        }
    }

    private void letGo() {
        phase = Phase.FADE;
        phaseT = 0f;
        for (int i = 0; i < count; i++) {
            float a = rnd.nextFloat() * 6.2832f, v = (30 + rnd.nextFloat() * 90) * s;
            dvx[i] = (float) Math.cos(a) * v;
            dvy[i] = (float) Math.sin(a) * v - 20 * s;
        }
    }

    private static float smooth(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3 - 2 * t);
    }

    private void step(float dt) {
        clock += dt;
        phaseT += dt;
        shown = phase == Phase.FADE ? Math.max(0f, shown - dt / FADE_T) : Math.min(1f, shown + dt * 2.5f);
        int due = Math.min(count, Math.round(target * count));
        for (; calledCount < due; calledCount++) called[calledCount] = clock;
        boolean settled = calledCount == count && (count == 0 || clock - called[count - 1] >= TRAVEL_T);
        if (phase == Phase.RISE && settled && ringT < 0f && lit == 0f) ringT = 0f;
        lit = phase == Phase.RISE && settled ? Math.min(1f, lit + dt * 1.5f) : phase == Phase.FADE ? Math.max(0f, lit - dt * 2f) : lit;
        if (ringT >= 0f && (ringT += dt) > 1.6f) ringT = -1f;
        if (phase == Phase.RISE && phaseT > RISE_MAX) letGo();
        if (phase == Phase.FADE) {
            for (int i = 0; i < count; i++) {
                sx[i] = position(i, true) + dvx[i] * dt;
                sy[i] = position(i, false) + dvy[i] * dt;
                called[i] = -1f;
            }
            if (shown <= 0f) {
                phase = Phase.OFF;
                failed = false;
                ringT = -1f;
                setVisibility(GONE);
            }
        }
    }

    /** Where mote `i` is: in its star until called, then swirling into the digits. */
    private float position(int i, boolean horizontal) {
        float from = horizontal ? sx[i] : sy[i], to = horizontal ? tx[i] : ty[i];
        if (called[i] < 0f) return from;
        float u = smooth((clock - called[i]) / TRAVEL_T);
        float bend = (float) Math.sin(u * Math.PI) * swirl[i];
        float dx = tx[i] - sx[i], dy = ty[i] - sy[i], len = (float) Math.sqrt(dx * dx + dy * dy) + 1f;
        return from + (to - from) * u + (horizontal ? -dy : dx) / len * bend;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        if (phase != Phase.OFF) {
            float due = target;
            lay();
            target = due;
            calledCount = 0;
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        step(Math.min(0.05f, Math.max(0f, (now - last) / 1000f)));
        last = now;
        if (phase == Phase.OFF) return;
        int w = getWidth();
        float cx = w / 2f;

        scrim.setAlpha(Math.round(170 * shown));
        c.drawRect(0, 0, w, getHeight(), scrim);

        int q = 0;
        float bloom = number.measureText(version) * 0.75f;
        quad(q, cx, baseline - number.getTextSize() * 0.36f, bloom);
        tint(q++, Apps.withAlpha(0xFF9FB4FF, Math.round(70 * shown * Math.max(target * 0.5f, lit))));
        for (int i = 0; i < count; i++) {
            float x = position(i, true), y = position(i, false);
            boolean home = called[i] >= 0f && clock - called[i] >= TRAVEL_T;
            float twinkle = 0.55f + 0.45f * (float) Math.sin(clock * (2f + seed[i] % 3f) + seed[i]);
            float a = called[i] < 0f ? 0.35f * twinkle : home ? 0.75f + 0.25f * twinkle : 1f;
            float r = (called[i] < 0f ? 2.2f : 3.2f) * s * (1f - 0.5f * lit);
            quad(q, x, y, r);
            tint(q++, Apps.withAlpha(tint[i], Math.round(255 * a * shown * (1f - 0.6f * lit))));
        }
        c.drawVertices(Canvas.VertexMode.TRIANGLES, q * 8, verts, 0, texs, 0, colors, 0, indices, 0, q * 6, dust);

        if (lit > 0f) {
            number.setAlpha(Math.round(255 * lit * shown));
            c.drawText(version, cx - number.measureText(version) / 2f, baseline, number);
        }
        if (ringT >= 0f) {
            float p = ringT / 1.6f, e = 1f - (1f - p) * (1f - p);
            ring.setStrokeWidth((3f - 2f * p) * s);
            ring.setColor(Apps.withAlpha(0xFFFFFFFF, Math.round(200 * (1f - p) * shown)));
            c.drawCircle(cx, baseline - number.getTextSize() * 0.36f, bloom * (0.5f + 1.4f * e), ring);
        }

        float y = baseline + 90 * s, half = 180 * s, thick = 3f * s;
        String head, line;
        if (phase == Phase.GATHER) {
            head = "UPDATING LUMEN";
            line = Math.round(target * 100) + "%";
        } else if (phase == Phase.RISE) {
            head = "INSTALLING LUMEN " + version;
            line = "Confirm on screen to finish";
        } else {
            head = failed ? "THE UPDATE DIDN'T FINISH" : "UPDATE CANCELLED";
            line = failed ? "Try again from Advanced settings" : "";
        }
        header.setColor(Apps.withAlpha(0xFFFFFFFF, Math.round(0x80 * shown)));
        c.drawText(head, cx, y, header);
        if (phase == Phase.GATHER) {
            float by = y + 26 * s;
            track.setAlpha(Math.round(0x33 * shown));
            c.drawRoundRect(cx - half, by, cx + half, by + thick, thick, thick, track);
            bar.setAlpha(Math.round(255 * shown));
            c.drawRoundRect(cx - half, by, cx - half + 2 * half * target, by + thick, thick, thick, bar);
            y = by + 48 * s;
        } else {
            y += 40 * s;
        }
        detail.setColor(Apps.withAlpha(0xFFFFFFFF, Math.round(0xB0 * shown)));
        if (!line.isEmpty()) c.drawText(line, cx, y, detail);
        postInvalidateOnAnimation();
    }

    private void quad(int q, float x, float y, float r) {
        int o = q * 8;
        verts[o] = x - r;
        verts[o + 1] = y - r;
        verts[o + 2] = x + r;
        verts[o + 3] = y - r;
        verts[o + 4] = x + r;
        verts[o + 5] = y + r;
        verts[o + 6] = x - r;
        verts[o + 7] = y + r;
    }

    private void tint(int q, int color) {
        int o = q * 4;
        colors[o] = color;
        colors[o + 1] = color;
        colors[o + 2] = color;
        colors[o + 3] = color;
    }
}
