package com.arzaroth.lumen;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import java.util.Locale;
import java.util.Random;

/**
 * The date, quietly alive: stars twinkle in its letters and an iridescent sheen drifts through
 * them, and every so often a moment plays (never the same twice in a row): a trace of light runs
 * under the date and lights each letter it passes, the letters lift in a ripple, or a few of them
 * are joined into a constellation. When the date turns, the old letters dissolve upward into dust
 * as the new ones condense, left to right.
 */
final class DateView extends View {
    private static final int MAX = 48, POINTS = 40, TWINKLES = 8, DUST = MAX * 6, SPRITE = 32, STARS = 5;
    private static final int TRACE = 0, LIFT = 1, CONSTELLATION = 2, MOMENTS = 3;
    private static final float[] MOMENT_T = {1.8f, 1.4f, 2.6f};
    private static final float CHANGE_T = 1.4f;
    private static final int QUADS = TWINKLES * 3 + DUST + MAX + STARS + 8;

    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shape = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sprites = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();
    private final Random rnd = new Random();
    private final float size, pad, ascent, lead;
    private final boolean upper;
    private final int ink;
    private LinearGradient sheenShader;
    private int accent = 0xFF9CB6FF;
    private boolean running;

    private String now = "", before = "";
    private final float[] nowX = new float[MAX + 1], beforeX = new float[MAX + 1];
    private float change = -1f, intro = -1f;

    private final float[] dx = new float[DUST], dy = new float[DUST], dvx = new float[DUST], dvy = new float[DUST], dLife = new float[DUST];
    private int dustNext;

    private final int[] twChar = new int[TWINKLES], twPoint = new int[TWINKLES];
    private final float[] twLife = new float[TWINKLES], twMax = new float[TWINKLES];
    private final int[] starChar = new int[STARS], starPoint = new int[STARS];

    private int moment = -1, lastMoment = -1;
    private float momentT, nextMoment = 7f, clock;
    private long lastFrame;

    private final float[] verts = new float[QUADS * 8], texs = new float[QUADS * 8];
    private final int[] colors = new int[QUADS * 4];
    private final short[] indices = new short[QUADS * 6];
    private final char[] one = new char[1];

    /** `size` in pixels; `upper` sets it in capitals; `ink` is the resting colour. */
    DateView(Context c, float size, int weight, int ink, float letterSpacing, boolean upper) {
        super(c);
        this.size = size;
        this.ink = ink;
        this.upper = upper;
        pad = size * 1.2f;
        text.setTypeface(Fonts.outfit(c, weight));
        text.setTextSize(size);
        text.setLetterSpacing(letterSpacing);
        text.setColor(ink);
        ascent = -text.ascent();
        shape.set(text);
        shape.setLetterSpacing(0f);
        shape.setColor(0xFFFFFFFF);
        lead = letterSpacing * size / 2f;
        glow.set(text);
        glow.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));
        sheen.set(text);
        sheen.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));
        sprites.setShader(new BitmapShader(sprite(), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        sprites.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));
        for (int q = 0; q < QUADS; q++) {
            int v = q * 4, t = q * 8, i = q * 6;
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
        setAccent(accent);
    }

    private static Bitmap sprite() {
        Bitmap b = Bitmap.createBitmap(SPRITE, SPRITE, Bitmap.Config.ARGB_8888);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(SPRITE / 2f, SPRITE / 2f, SPRITE / 2f,
                new int[] {0xFFFFFFFF, 0x99FFFFFF, 0x1AFFFFFF, 0x00FFFFFF}, new float[] {0f, 0.15f, 0.45f, 1f},
                Shader.TileMode.CLAMP));
        new Canvas(b).drawCircle(SPRITE / 2f, SPRITE / 2f, SPRITE / 2f, p);
        return b;
    }

    /** How far the letters sit inside the view, on every side, to leave room for the dust. */
    float inset() {
        return pad;
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    void setAccent(int color) {
        accent = color;
        float[] hsv = new float[3];
        Color.colorToHSV(accent, hsv);
        hsv[0] = (hsv[0] + 150) % 360;
        hsv[1] = Math.min(hsv[1], 0.6f);
        hsv[2] = 1f;
        int other = Color.HSVToColor(hsv);
        sheenShader = new LinearGradient(0, 0, size * 7f, 0,
                new int[] {Apps.withAlpha(accent, 0x30), 0x00000000, Apps.withAlpha(other, 0x2A), 0x10FFFFFF, Apps.withAlpha(accent, 0x30)},
                null, Shader.TileMode.REPEAT);
    }

    void setRunning(boolean run) {
        if (running == run) return;
        running = run;
        lastFrame = SystemClock.uptimeMillis();
        if (run) postInvalidateOnAnimation();
    }

    /** Shows `value`; once something is shown, a new value dissolves the old one into the new. */
    void setText(CharSequence value) {
        String next = upper ? value.toString().toUpperCase(Locale.getDefault()) : value.toString();
        if (next.length() > MAX) next = next.substring(0, MAX);
        if (next.equals(now)) return;
        boolean animate = !now.isEmpty() && running;
        before = now;
        System.arraycopy(nowX, 0, beforeX, 0, nowX.length);
        now = next;
        layoutLetters();
        requestLayout();
        if (!animate) return;
        change = 0f;
        moment = -1;
        float baseline = pad + ascent;
        for (int i = 0; i < before.length(); i++) {
            char ch = before.charAt(i);
            if (ch == ' ') continue;
            float[] pts = points(before, i);
            for (int k = 0; k < 6; k++) {
                int p = (k * 7 + i * 3) % POINTS, d = dustNext;
                dustNext = (dustNext + 1) % DUST;
                dx[d] = beforeX[i] + lead + pts[2 * p];
                dy[d] = baseline + pts[2 * p + 1];
                dvx[d] = (rnd.nextFloat() - 0.5f) * size * 0.8f;
                dvy[d] = -(0.6f + rnd.nextFloat()) * size;
                dLife[d] = 0.6f + rnd.nextFloat() * 0.6f;
            }
        }
    }

    /** The letters type themselves in, one by one, each with a spark. */
    void intro() {
        intro = 0f;
        invalidate();
    }

    private void layoutLetters() {
        for (int i = 0; i <= now.length(); i++) nowX[i] = pad + text.measureText(now, 0, i);
    }

    /** Points on the strokes of letter `i` of `s`, relative to where it is drawn and to the baseline. */
    private float[] points(String s, int i) {
        one[0] = s.charAt(i);
        return Glyphs.sample(shape, s.charAt(i), shape.measureText(one, 0, 1), POINTS);
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        float w = now.isEmpty() ? 0 : nowX[now.length()] - pad;
        setMeasuredDimension(Math.round(w + 2 * pad), Math.round(ascent + text.descent() + 2 * pad));
    }

    private static float smooth(float a, float b, float x) {
        float t = Math.max(0f, Math.min(1f, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    private float bump(float start, float span) {
        float u = (momentT - start) / span;
        return u <= 0 || u >= 1 ? 0f : (float) Math.sin(Math.PI * u);
    }

    private void quad(int q, float x, float y, float r, int color) {
        int o = q * 8;
        verts[o] = x - r;
        verts[o + 1] = y - r;
        verts[o + 2] = x + r;
        verts[o + 3] = y - r;
        verts[o + 4] = x + r;
        verts[o + 5] = y + r;
        verts[o + 6] = x - r;
        verts[o + 7] = y + r;
        int c = q * 4;
        colors[c] = color;
        colors[c + 1] = color;
        colors[c + 2] = color;
        colors[c + 3] = color;
    }

    @Override
    protected void onDraw(Canvas c) {
        long frame = SystemClock.uptimeMillis();
        float dt = Math.min(0.05f, Math.max(0f, (frame - lastFrame) / 1000f));
        lastFrame = frame;
        if (running) clock += dt;
        float baseline = pad + ascent;
        int tint = Apps.mix(accent, 0xFFFFFFFF, 0.55f);
        int n = now.length(), letters = 0;
        for (int i = 0; i < n; i++) if (now.charAt(i) != ' ') letters++;
        int q = 0;

        if (running && moment < 0 && change < 0 && intro < 0 && clock >= nextMoment && letters > 0) {
            int m;
            do {
                m = rnd.nextInt(MOMENTS);
            } while (m == lastMoment);
            moment = lastMoment = m;
            momentT = 0f;
            if (moment == CONSTELLATION) {
                for (int k = 0; k < STARS; k++) {
                    int i;
                    do {
                        i = rnd.nextInt(n);
                    } while (now.charAt(i) == ' ');
                    starChar[k] = i;
                    starPoint[k] = rnd.nextInt(POINTS);
                }
                java.util.Arrays.sort(starChar);
            }
        }
        if (moment >= 0) {
            momentT += dt;
            if (momentT >= MOMENT_T[moment]) {
                moment = -1;
                nextMoment = clock + 9f + rnd.nextFloat() * 6f;
            }
        }
        float traceX = moment == TRACE ? pad - size + (nowX[n] - pad + 2 * size) * smooth(0f, 1f, momentT / MOMENT_T[TRACE]) : -1e9f;

        if (running) {
            matrix.setRotate(-24f);
            matrix.postTranslate(clock * size * 0.6f, 0);
            sheenShader.setLocalMatrix(matrix);
            sheen.setShader(sheenShader);
        }
        if (change >= 0) {
            float fade = 1f - smooth(0f, 0.5f, change);
            text.setAlpha(Math.round(Color.alpha(ink) * fade));
            for (int i = 0; i < before.length(); i++) {
                one[0] = before.charAt(i);
                c.drawText(one, 0, 1, beforeX[i], baseline - change * size * 0.3f, text);
            }
        }
        for (int i = 0; i < n; i++) {
            char ch = now.charAt(i);
            if (ch == ' ') continue;
            float shown = 1f, lift = 0f, light = 0f;
            if (change >= 0) shown = smooth(0.25f + 0.6f * i / Math.max(1, n), 0.55f + 0.6f * i / Math.max(1, n), change);
            if (intro >= 0) shown *= smooth(0.06f * i, 0.06f * i + 0.18f, intro);
            if (shown <= 0.01f) continue;
            float cx = (nowX[i] + nowX[i + 1]) / 2;
            if (moment == LIFT) {
                float b = bump(i * 0.045f, 0.5f);
                lift = b * size * 0.18f;
                light = b * 0.6f;
            } else if (moment == TRACE) {
                float d = (cx - traceX) / (size * 0.9f);
                light = (float) Math.exp(-d * d) * 0.9f;
            }
            one[0] = ch;
            float y = baseline - lift;
            text.setAlpha(Math.round(Color.alpha(ink) * shown));
            c.drawText(one, 0, 1, nowX[i], y, text);
            if (running) c.drawText(one, 0, 1, nowX[i], y, sheen);
            if (light > 0.02f) {
                glow.setColor(Apps.withAlpha(tint, Math.round(150 * light)));
                c.drawText(one, 0, 1, nowX[i], y, glow);
            }
            if (change >= 0 || intro >= 0) {
                float spark = shown * (1f - shown) * 4f;
                if (spark > 0.05f) quad(q++, cx, baseline - ascent * 0.35f, size * 0.5f, Apps.withAlpha(tint, Math.round(160 * spark)));
            }
        }
        text.setAlpha(Color.alpha(ink));

        if (moment == TRACE) {
            float y = baseline + size * 0.32f, a = (float) Math.sin(Math.PI * momentT / MOMENT_T[TRACE]);
            for (int k = 0; k < 6; k++) quad(q++, traceX - k * size * 0.22f, y, size * (0.16f - k * 0.02f), Apps.withAlpha(k == 0 ? 0xFFFFFFFF : tint, Math.round(220 * a * (1 - k / 6f))));
        } else if (moment == CONSTELLATION) {
            float a = bump(0f, MOMENT_T[CONSTELLATION]);
            float px = 0, py = 0;
            line.setStrokeWidth(Math.max(1f, size * 0.03f));
            for (int k = 0; k < STARS; k++) {
                int i = starChar[k];
                float[] pts = points(now, i);
                float x = nowX[i] + lead + pts[2 * starPoint[k]], y = baseline + pts[2 * starPoint[k] + 1];
                float reveal = smooth(k * 0.25f, k * 0.25f + 0.4f, momentT);
                if (k > 0 && reveal > 0) {
                    line.setColor(Apps.withAlpha(tint, Math.round(110 * a * reveal)));
                    c.drawLine(px, py, px + (x - px) * reveal, py + (y - py) * reveal, line);
                }
                quad(q++, x, y, size * 0.14f, Apps.withAlpha(0xFFFFFFFF, Math.round(230 * a * Math.max(reveal, k == 0 ? 1f : 0f))));
                px = x;
                py = y;
            }
        }
        if (running && letters > 0) {
            for (int t = 0; t < TWINKLES; t++) {
                twLife[t] -= dt;
                if (twLife[t] <= 0 || twChar[t] >= n || now.charAt(twChar[t]) == ' ') {
                    int i;
                    do {
                        i = rnd.nextInt(n);
                    } while (now.charAt(i) == ' ');
                    twChar[t] = i;
                    twPoint[t] = rnd.nextInt(POINTS);
                    twMax[t] = 0.5f + rnd.nextFloat() * 1.2f;
                    twLife[t] = twMax[t] * rnd.nextFloat();
                    continue;
                }
                int i = twChar[t];
                float[] pts = points(now, i);
                float a = (float) Math.sin(Math.PI * twLife[t] / twMax[t]);
                a *= a;
                quad(q++, nowX[i] + lead + pts[2 * twPoint[t]], baseline + pts[2 * twPoint[t] + 1], size * 0.09f,
                        Apps.withAlpha(t % 3 == 0 ? tint : 0xFFFFFFFF, Math.round(200 * a)));
            }
        }
        for (int d = 0; d < DUST; d++) {
            if (dLife[d] <= 0) continue;
            dLife[d] -= dt;
            dx[d] += dvx[d] * dt;
            dy[d] += dvy[d] * dt;
            quad(q++, dx[d], dy[d], size * 0.06f, Apps.withAlpha(tint, Math.round(220 * Math.min(1f, dLife[d] * 2f))));
        }
        if (q > 0) c.drawVertices(Canvas.VertexMode.TRIANGLES, q * 8, verts, 0, texs, 0, colors, 0, indices, 0, q * 6, sprites);

        if (change >= 0) {
            change += dt;
            if (change > CHANGE_T) change = -1f;
        }
        if (intro >= 0) {
            intro += dt;
            if (intro > 0.06f * n + 0.4f) intro = -1f;
        }
        if (running) postInvalidateOnAnimation();
    }
}
