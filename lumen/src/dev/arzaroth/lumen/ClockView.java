package dev.arzaroth.lumen;

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
import android.graphics.Shader;
import android.os.SystemClock;
import android.text.format.DateFormat;
import android.util.SparseArray;
import android.view.View;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Random;

/**
 * The time, alive. Each digit that changes breaks into stardust that streams, swirling, into the
 * next one, which lands heavy and thins out to the clock's hairline weight; on the hour the whole
 * time takes off at once. The colon beats with the seconds, a hairline under the digits fills
 * across the minute and sheds sparks from its head.
 *
 * In between, the digits never sit still: stars twinkle inside their strokes, an iridescent sheen
 * drifts through them, and every few seconds one of six moments plays (never the same twice in a
 * row): a glint, the digits breathing heavier and back, a wave, the colon's dots orbiting each
 * other, dust lifting off and settling back, or a comet crossing that lights each digit it passes.
 */
final class ClockView extends View {
    private static final int SLOTS = 5, POINTS = 200, SPARKS = 48, SPRITE = 48;
    private static final int TWINKLES = 26, EVAP = 36, TRAIL = 18, ORBIT_TRAIL = 8;
    private static final int QUADS = SLOTS * POINTS + SLOTS + SPARKS + 2 + TWINKLES * 3 + SLOTS * EVAP + TRAIL + 2 * ORBIT_TRAIL + SLOTS;
    private static final int GLINT = 0, BREATHE = 1, WAVE = 2, ORBIT = 3, EVAPORATE = 4, COMET = 5, EVENTS = 6;
    private static final float[] EVENT_T = {1.3f, 2.3f, 1.5f, 1.5f, 2.8f, 2.0f};
    private static final int[] DUST = {0xFFFFFFFF, 0, 0xFFFFD8A0, 1};
    private static final float MORPH_T = 1.5f;
    private static final int[] WEIGHTS = {200, 240, 280, 320, 360, 400, 450, 500, 600};
    private static final SparseArray<float[]> GLYPHS = new SparseArray<>();

    private final float size, pad;
    private final boolean seconds;
    private final Paint[] weights = new Paint[WEIGHTS.length];
    private final Paint glint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sprites = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint flashPaint = new Paint();
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RadialGradient flashShader = new RadialGradient(0, 0, 1,
            new int[] {0x66FFFFFF, 0x22FFFFFF, 0x00FFFFFF}, new float[] {0f, 0.4f, 1f}, Shader.TileMode.CLAMP);
    private final Matrix matrix = new Matrix();
    private LinearGradient glintShader, sheenShader;
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float dotTopX, dotTopY, dotBottomX, dotBottomY, dotR;
    private final Random rnd = new Random();
    private final float ascent, digitW, colonW;
    private boolean h24, running;
    private int accent = 0xFF9CB6FF;

    private final char[] now = new char[SLOTS], before = new char[SLOTS];
    private final float[] nowX = new float[SLOTS], beforeX = new float[SLOTS];
    private int nowLen, beforeLen;
    private final float[] morph = new float[SLOTS];
    private final int[] source = new int[SLOTS];
    private boolean hourly;
    private float flash;

    private final float[] fromX = new float[SLOTS * POINTS], fromY = new float[SLOTS * POINTS];
    private final float[] toX = new float[SLOTS * POINTS], toY = new float[SLOTS * POINTS];
    private final float[] delay = new float[SLOTS * POINTS], midX = new float[SLOTS * POINTS], midY = new float[SLOTS * POINTS];
    private final float[] drama = new float[SLOTS];

    private final float[] sx = new float[SPARKS], sy = new float[SPARKS], svx = new float[SPARKS], svy = new float[SPARKS];
    private final float[] sLife = new float[SPARKS];
    private int sparkNext;

    private final float[] verts = new float[QUADS * 8], texs = new float[QUADS * 8];
    private final int[] colors = new int[QUADS * 4];
    private final short[] indices = new short[QUADS * 6];
    private final char[] one = new char[1];

    private final int[] twSlot = new int[TWINKLES], twPoint = new int[TWINKLES];
    private final float[] twLife = new float[TWINKLES], twMax = new float[TWINKLES];

    private long lastSecond = -1, lastFrame;
    private float clock, nextEvent = 3f, eventT;
    private int event = -1, lastEvent = -1;

    /** `size` is the digits' text size in pixels; `seconds` draws the minute hairline. */
    ClockView(Context c, float size, boolean seconds) {
        super(c);
        this.size = size;
        this.seconds = seconds;
        pad = size * 0.75f;
        for (int i = 0; i < WEIGHTS.length; i++) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setTypeface(Fonts.outfit(c, WEIGHTS[i]));
            p.setTextSize(size);
            p.setColor(0xFFFFFFFF);
            weights[i] = p;
        }
        Paint base = weights[0];
        ascent = -base.ascent();
        float w = 0;
        for (char d = '0'; d <= '9'; d++) w = Math.max(w, base.measureText(String.valueOf(d)));
        digitW = w * 0.98f;
        colonW = base.measureText(":") * 1.1f;
        glint.set(base);
        glint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));
        sheen.set(base);
        sheen.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));
        dot.setColor(0xFFFFFFFF);
        sprites.setShader(new BitmapShader(sprite(), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        sprites.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));
        line.setStrokeCap(Paint.Cap.ROUND);
        flashPaint.setShader(flashShader);
        flashPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));
        ring.setStyle(Paint.Style.STROKE);
        ring.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));
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
        Arrays.fill(morph, -1f);
        setAccent(accent);
        measureColon();
        h24 = DateFormat.is24HourFormat(c);
        tick(System.currentTimeMillis(), false);
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

    /** How far the digits sit inside the view, on every side, to leave room for the dust. */
    float inset() {
        return pad;
    }

    void setFormat(boolean twentyFour) {
        if (h24 == twentyFour) return;
        h24 = twentyFour;
        lastSecond = -1;
        tick(System.currentTimeMillis(), true);
        invalidate();
    }

    void setAccent(int color) {
        accent = color;
        int tint = Apps.mix(accent, 0xFFFFFFFF, 0.55f);
        glintShader = new LinearGradient(-size * 0.5f, 0, size * 0.5f, size * 0.4f,
                new int[] {0x00000000, Apps.withAlpha(tint, 0x90), 0x00000000}, null, Shader.TileMode.CLAMP);
        float[] hsv = new float[3];
        android.graphics.Color.colorToHSV(accent, hsv);
        hsv[0] = (hsv[0] + 150) % 360;
        hsv[1] = Math.min(hsv[1], 0.6f);
        hsv[2] = 1f;
        int other = android.graphics.Color.HSVToColor(hsv);
        sheenShader = new LinearGradient(0, 0, size * 2.6f, 0,
                new int[] {Apps.withAlpha(accent, 0x46), 0x00000000, Apps.withAlpha(other, 0x3C), 0x14FFFFFF, Apps.withAlpha(accent, 0x46)},
                null, Shader.TileMode.REPEAT);
    }

    /** Where the colon's two dots sit in its cell, and how big they are. */
    private void measureColon() {
        float[] pts = glyph(':');
        float my = 0;
        for (int k = 0; k < POINTS; k++) my += pts[2 * k + 1];
        my /= POINTS;
        float tx = 0, ty = 0, bx = 0, by = 0;
        int nt = 0, nb = 0;
        for (int k = 0; k < POINTS; k++) {
            if (pts[2 * k + 1] < my) {
                tx += pts[2 * k];
                ty += pts[2 * k + 1];
                nt++;
            } else {
                bx += pts[2 * k];
                by += pts[2 * k + 1];
                nb++;
            }
        }
        dotTopX = tx / Math.max(1, nt);
        dotTopY = ty / Math.max(1, nt);
        dotBottomX = bx / Math.max(1, nb);
        dotBottomY = by / Math.max(1, nb);
        float r = 0;
        for (int k = 0; k < POINTS; k++) {
            if (pts[2 * k + 1] < my) r = Math.max(r, (float) Math.hypot(pts[2 * k] - dotTopX, pts[2 * k + 1] - dotTopY));
        }
        dotR = Math.max(1.5f, r * 0.8f);
    }

    void setRunning(boolean run) {
        if (running == run) return;
        running = run;
        lastFrame = SystemClock.uptimeMillis();
        lastSecond = -1;
        tick(System.currentTimeMillis(), false);
        if (run) postInvalidateOnAnimation();
    }

    // ------------------------------------------------------------------ time

    private void tick(long millis, boolean animate) {
        long second = millis / 1000;
        if (second == lastSecond) return;
        lastSecond = second;
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(millis);
        CharSequence text = DateFormat.format(h24 ? "HH:mm" : "h:mm", cal);
        int len = Math.min(SLOTS, text.length());
        boolean same = len == nowLen;
        for (int i = 0; same && i < len; i++) same = text.charAt(i) == now[i];
        if (same) return;

        System.arraycopy(now, 0, before, 0, SLOTS);
        System.arraycopy(nowX, 0, beforeX, 0, SLOTS);
        beforeLen = nowLen;
        nowLen = len;
        float x = pad;
        for (int i = 0; i < len; i++) {
            now[i] = text.charAt(i);
            nowX[i] = x;
            x += now[i] == ':' ? colonW : digitW;
        }
        requestLayout();
        if (!animate || beforeLen == 0) {
            Arrays.fill(morph, -1f);
            return;
        }
        int colon = indexOf(now, nowLen), oldColon = indexOf(before, beforeLen);
        hourly = false;
        boolean tens = false;
        for (int i = 0; i < nowLen; i++) {
            int j = i - colon + oldColon;
            boolean changed = j < 0 || j >= beforeLen || before[j] != now[i] || beforeX[j] != nowX[i];
            if (!changed) continue;
            if (i < colon) hourly = true;
            if (i == colon + 1) tens = true;
        }
        float level = hourly ? 2.4f : tens ? 1.6f : 1f;
        for (int i = 0; i < nowLen; i++) {
            int j = i - colon + oldColon;
            boolean changed = j < 0 || j >= beforeLen || before[j] != now[i] || beforeX[j] != nowX[i];
            if (changed) begin(i, j >= 0 && j < beforeLen ? j : -1, level);
        }
        if (hourly) {
            flash = 1f;
            for (int i = 0; i < nowLen; i++) {
                int j = i - colon + oldColon;
                if (morph[i] < 0 && now[i] != ':') begin(i, j >= 0 && j < beforeLen ? j : -1, level);
            }
        }
    }

    private static int indexOf(char[] chars, int len) {
        for (int i = 0; i < len; i++) if (chars[i] == ':') return i;
        return len;
    }

    /**
     * Starts slot `i` streaming from the old slot `j` (or out of thin air when -1): every grain
     * is flung out on its own arc through a midpoint, wider the bigger the moment (`level`).
     */
    private void begin(int i, int j, float level) {
        morph[i] = 0f;
        source[i] = j;
        drama[i] = level;
        float[] to = glyph(now[i]);
        float[] from = j >= 0 ? glyph(before[j]) : null;
        float baseline = pad + ascent;
        float spread = size * 0.42f * level;
        for (int k = 0; k < POINTS; k++) {
            int p = i * POINTS + k;
            toX[p] = nowX[i] + to[2 * k];
            toY[p] = baseline + to[2 * k + 1];
            if (from != null) {
                fromX[p] = beforeX[j] + from[2 * k];
                fromY[p] = baseline + from[2 * k + 1];
            } else {
                fromX[p] = nowX[i] + digitW / 2 + (rnd.nextFloat() - 0.5f) * size * 0.1f;
                fromY[p] = baseline - ascent * 0.35f + (rnd.nextFloat() - 0.5f) * size * 0.1f;
            }
            delay[p] = (float) k / POINTS * 0.32f + rnd.nextFloat() * 0.1f;
            double a = rnd.nextDouble() * Math.PI * 2;
            float reach = spread * (0.35f + 0.65f * rnd.nextFloat());
            midX[p] = (fromX[p] + toX[p]) / 2 + (float) Math.cos(a) * reach;
            midY[p] = (fromY[p] + toY[p]) / 2 + (float) Math.sin(a) * reach * 0.8f - size * 0.25f * level;
        }
    }

    /**
     * POINTS points on the glyph's strokes, relative to its cell and baseline, ordered by angle
     * around its centre so two glyphs pair up stroke for stroke.
     */
    private float[] glyph(char ch) {
        float[] pts = GLYPHS.get(ch | Math.round(size) << 16);
        if (pts != null) return pts;
        Paint p = weights[0];
        float w = ch == ':' ? colonW : digitW;
        int bw = (int) Math.ceil(w) + 4, bh = (int) Math.ceil(ascent + p.descent()) + 4;
        Bitmap b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint thick = new Paint(p);
        thick.setTypeface(weights[2].getTypeface());
        one[0] = ch;
        c.drawText(one, 0, 1, 2 + (w - thick.measureText(one, 0, 1)) / 2, 2 + ascent, thick);
        int[] px = new int[bw * bh];
        b.getPixels(px, 0, bw, 0, 0, bw, bh);
        b.recycle();
        int n = 0;
        for (int v : px) if (v >>> 24 > 128) n++;
        int[] on = new int[Math.max(1, n)];
        n = 0;
        for (int i = 0; i < px.length; i++) if (px[i] >>> 24 > 128) on[n++] = i;
        Random pick = new Random(ch);
        pts = new float[POINTS * 2];
        float cx = 0, cy = 0;
        for (int k = 0; k < POINTS; k++) {
            int i = n > 0 ? on[pick.nextInt(n)] : 0;
            pts[2 * k] = i % bw - 2 + pick.nextFloat() - 0.5f;
            pts[2 * k + 1] = i / bw - 2 - ascent + pick.nextFloat() - 0.5f;
            cx += pts[2 * k];
            cy += pts[2 * k + 1];
        }
        final float mx = cx / POINTS, my = cy / POINTS;
        Integer[] order = new Integer[POINTS];
        for (int k = 0; k < POINTS; k++) order[k] = k;
        final float[] src = pts;
        Arrays.sort(order, (a, bb) -> Float.compare(
                (float) Math.atan2(src[2 * a + 1] - my, src[2 * a] - mx),
                (float) Math.atan2(src[2 * bb + 1] - my, src[2 * bb] - mx)));
        float[] sorted = new float[POINTS * 2];
        for (int k = 0; k < POINTS; k++) {
            sorted[2 * k] = src[2 * order[k]];
            sorted[2 * k + 1] = src[2 * order[k] + 1];
        }
        GLYPHS.put(ch | Math.round(size) << 16, sorted);
        return sorted;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        float w = 0;
        for (int i = 0; i < nowLen; i++) w += now[i] == ':' ? colonW : digitW;
        float h = ascent + weights[0].descent();
        setMeasuredDimension(Math.round(w + 2 * pad), Math.round(h + 2 * pad));
    }

    private static float smooth(float a, float b, float x) {
        float t = Math.max(0f, Math.min(1f, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
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

    /** A streak from (x0, y0) to (x1, y1), `r` wide, rounded by the sprite at both ends. */
    private void streak(int q, float x0, float y0, float x1, float y1, float r, int color) {
        float dx = x1 - x0, dy = y1 - y0, len = (float) Math.sqrt(dx * dx + dy * dy);
        float ux = len < 0.001f ? 1 : dx / len, uy = len < 0.001f ? 0 : dy / len;
        float ex = ux * r, ey = uy * r, nx = -uy * r, ny = ux * r;
        int o = q * 8;
        verts[o] = x0 - ex + nx;
        verts[o + 1] = y0 - ey + ny;
        verts[o + 2] = x1 + ex + nx;
        verts[o + 3] = y1 + ey + ny;
        verts[o + 4] = x1 + ex - nx;
        verts[o + 5] = y1 + ey - ny;
        verts[o + 6] = x0 - ex - nx;
        verts[o + 7] = y0 - ey - ny;
        int c = q * 4;
        colors[c] = color;
        colors[c + 1] = color;
        colors[c + 2] = color;
        colors[c + 3] = color;
    }

    private float bezier(float a, float m, float b, float e) {
        float u = 1 - e;
        return u * u * a + 2 * u * e * m + e * e * b;
    }

    private void spark(float x, float y) {
        int i = sparkNext;
        sparkNext = (sparkNext + 1) % SPARKS;
        sx[i] = x;
        sy[i] = y;
        svx[i] = -(20 + rnd.nextFloat() * 50) * size / 148f;
        svy[i] = (rnd.nextFloat() - 0.6f) * 40 * size / 148f;
        sLife[i] = 0.6f + rnd.nextFloat() * 0.6f;
    }

    private int digitOrder(int i) {
        int n = 0;
        for (int k = 0; k < i; k++) if (now[k] != ':') n++;
        return n;
    }

    private boolean morphing() {
        for (int i = 0; i < nowLen; i++) if (morph[i] >= 0) return true;
        return false;
    }

    /** 0..1..0 over [start, start + span] of the current event, 0 outside. */
    private float bump(float start, float span) {
        float u = (eventT - start) / span;
        return u <= 0 || u >= 1 ? 0f : (float) Math.sin(Math.PI * u);
    }

    private float cometX() {
        float u = eventT / EVENT_T[COMET];
        return -0.15f * getWidth() + u * 1.3f * getWidth();
    }

    private float cometY(float baseline, float u) {
        return baseline - ascent * (0.45f + 0.25f * (float) Math.sin(u * Math.PI * 1.5f));
    }

    /** A digit at rest, shaped by whatever moment is playing. */
    private int drawIdle(Canvas c, int q, int i, int order, float baseline, int tint) {
        Paint p = weights[0];
        float lift = 0f, glow = 0f, dim = 1f;
        if (event == BREATHE) {
            float b = bump(order * 0.2f, 1.4f);
            p = weights[Math.round(b * 5)];
            glow = b * 0.5f;
        } else if (event == WAVE) {
            float b = bump(order * 0.16f, 0.6f);
            lift = b * size * 0.1f;
            glow = b * 0.8f;
        } else if (event == EVAPORATE) {
            dim = 1f - 0.4f * bump(0f, EVENT_T[EVAPORATE]);
        } else if (event == COMET) {
            float d = (nowX[i] + digitW / 2 - cometX()) / (digitW * 0.7f);
            glow = (float) Math.exp(-d * d) * 1.1f;
        }
        if (p != weights[0]) p.setShadowLayer(size * 0.14f, 0, 0, Apps.withAlpha(accent, 0x50));
        p.setAlpha(Math.round(255 * dim));
        float x = nowX[i] + (digitW - p.measureText(one, 0, 1)) / 2, y = baseline - lift;
        c.drawText(one, 0, 1, x, y, p);
        p.setAlpha(255);
        if (p != weights[0]) p.clearShadowLayer();
        drawSheen(c, p, i, baseline, lift);
        if (glow > 0.02f) {
            glint.setTypeface(p.getTypeface());
            glint.setShader(null);
            glint.setColor(Apps.withAlpha(tint, Math.round(200 * Math.min(1f, glow))));
            c.drawText(one, 0, 1, x, y, glint);
            glint.setColor(0xFFFFFFFF);
            glint.setTypeface(weights[0].getTypeface());
            quad(q++, nowX[i] + digitW / 2, y - ascent * 0.38f, digitW * 0.8f, Apps.withAlpha(tint, Math.round(70 * Math.min(1f, glow))));
        }
        if (event == EVAPORATE) q = drawEvaporate(q, i, baseline, tint);
        return q;
    }

    private void drawSheen(Canvas c, Paint shape, int i, float baseline, float lift) {
        if (!running) return;
        matrix.setRotate(-24f);
        matrix.postTranslate(clock * size * 0.3f, 0);
        sheenShader.setLocalMatrix(matrix);
        sheen.setShader(sheenShader);
        sheen.setTypeface(shape.getTypeface());
        float cell = now[i] == ':' ? colonW : digitW;
        c.drawText(one, 0, 1, nowX[i] + (cell - sheen.measureText(one, 0, 1)) / 2, baseline - lift, sheen);
    }

    /** The colon's dots swing round each other, trailing light. */
    private int drawOrbit(Canvas c, int q, int i, float baseline, float beat, int tint) {
        float u = Math.min(1f, eventT / EVENT_T[ORBIT]);
        float e = u < 0.5f ? 4 * u * u * u : 1 - (float) Math.pow(-2 * u + 2, 3) / 2;
        float mx = nowX[i] + (dotTopX + dotBottomX) / 2, my = baseline + (dotTopY + dotBottomY) / 2;
        float r = (dotBottomY - dotTopY) / 2;
        for (int k = ORBIT_TRAIL; k >= 0; k--) {
            float ek = Math.max(0f, e - k * 0.035f);
            double a = Math.PI * ek;
            float dx = (float) Math.sin(a) * r * 1.15f, dy = -(float) Math.cos(a) * r;
            if (k == 0) {
                dot.setAlpha(Math.round(255 * beat));
                c.drawCircle(mx + dx, my + dy, dotR, dot);
                c.drawCircle(mx - dx, my - dy, dotR, dot);
            } else if (u < 1f) {
                int alpha = Math.round(150 * (1f - (float) k / ORBIT_TRAIL) * (float) Math.sin(Math.PI * u));
                quad(q++, mx + dx, my + dy, dotR * 2.2f, Apps.withAlpha(tint, alpha));
                quad(q++, mx - dx, my - dy, dotR * 2.2f, Apps.withAlpha(tint, alpha));
            }
        }
        return q;
    }

    /** Dust lifting off a digit's strokes, drifting up and settling back. */
    private int drawEvaporate(int q, int i, float baseline, int tint) {
        float[] pts = glyph(now[i]);
        for (int k = 0; k < EVAP; k++) {
            int pk = (k * POINTS / EVAP + i * 7) % POINTS;
            float h = ((k * 37 + i * 11) % 23) / 23f;
            float b = bump(h * 0.9f, 1.6f);
            if (b <= 0.01f) continue;
            float x = nowX[i] + pts[2 * pk] + (float) Math.sin(eventT * 5f + k) * size * 0.05f * b;
            float y = baseline + pts[2 * pk + 1] - size * (0.2f + 0.35f * h) * b;
            quad(q++, x, y, size * (0.02f + 0.015f * (k % 2)), Apps.withAlpha(k % 3 == 0 ? 0xFFFFFFFF : tint, Math.round(220 * b)));
        }
        return q;
    }

    /** Starts, plays and ends the idle moments; the comet's own light is drawn here. */
    private int drawEvent(Canvas c, int q, float baseline, int tint, float dt) {
        if (event < 0) {
            long ms = System.currentTimeMillis() % 60_000;
            if (clock >= nextEvent && !morphing() && ms > 2500 && ms < 55_000) {
                int e;
                do {
                    e = rnd.nextInt(EVENTS);
                } while (e == lastEvent);
                event = lastEvent = e;
                eventT = 0f;
            }
            return q;
        }
        eventT += dt;
        if (eventT >= EVENT_T[event] || morphing()) {
            event = -1;
            nextEvent = clock + 5.5f + rnd.nextFloat() * 4.5f;
            return q;
        }
        if (event == GLINT) {
            float w = getWidth(), cx = -0.3f * w + eventT / EVENT_T[GLINT] * 1.6f * w;
            matrix.setTranslate(cx, 0);
            glintShader.setLocalMatrix(matrix);
            glint.setShader(glintShader);
            for (int i = 0; i < nowLen; i++) {
                one[0] = now[i];
                float cell = now[i] == ':' ? colonW : digitW;
                c.drawText(one, 0, 1, nowX[i] + (cell - glint.measureText(one, 0, 1)) / 2, baseline, glint);
            }
            glint.setShader(null);
        } else if (event == COMET) {
            float total = EVENT_T[COMET];
            for (int k = TRAIL - 1; k >= 0; k--) {
                float u1 = (eventT - k * 0.02f) / total, u0 = (eventT - (k + 1) * 0.02f) / total;
                if (u0 < 0) continue;
                float x1 = -0.15f * getWidth() + u1 * 1.3f * getWidth(), x0 = -0.15f * getWidth() + u0 * 1.3f * getWidth();
                float fade = 1f - (float) k / TRAIL;
                streak(q++, x0, cometY(baseline, u0), x1, cometY(baseline, u1), size * 0.022f * fade,
                        Apps.withAlpha(k < 3 ? 0xFFFFFFFF : tint, Math.round(220 * fade)));
            }
            float u = eventT / total, hx = cometX(), hy = cometY(baseline, u);
            quad(q++, hx, hy, size * 0.09f, 0xFFFFFFFF);
            if (rnd.nextFloat() < dt * 30f) spark(hx, hy);
        }
        return q;
    }

    /** Stars caught in the glass: brief glints on the strokes of the digits at rest. */
    private int drawTwinkles(int q, float baseline, int tint, float dt) {
        for (int n = 0; n < TWINKLES; n++) {
            twLife[n] -= dt;
            int i = twSlot[n];
            boolean valid = i < nowLen && now[i] != ':' && morph[i] < 0;
            if (twLife[n] <= 0 || !valid) {
                if (nowLen == 0) return q;
                twSlot[n] = rnd.nextInt(nowLen);
                twPoint[n] = rnd.nextInt(POINTS);
                twMax[n] = 0.5f + rnd.nextFloat() * 1.1f;
                twLife[n] = twMax[n] * (valid ? 1f : rnd.nextFloat());
                continue;
            }
            float[] pts = glyph(now[i]);
            float x = nowX[i] + pts[2 * twPoint[n]], y = baseline + pts[2 * twPoint[n] + 1];
            float a = (float) Math.sin(Math.PI * twLife[n] / twMax[n]);
            a *= a;
            int color = Apps.withAlpha(n % 3 == 0 ? tint : 0xFFFFFFFF, Math.round(255 * a));
            quad(q++, x, y, size * (0.025f + 0.02f * (n % 3)), color);
            if (n % 4 == 0) {
                float len = size * 0.13f * a, w = size * 0.006f;
                streak(q++, x - len, y, x + len, y, w, color);
                streak(q++, x, y - len, x, y + len, w, color);
            }
        }
        return q;
    }

    @Override
    protected void onDraw(Canvas c) {
        long frame = SystemClock.uptimeMillis();
        float dt = Math.min(0.05f, Math.max(0f, (frame - lastFrame) / 1000f));
        lastFrame = frame;
        long millis = System.currentTimeMillis();
        if (running) {
            clock += dt;
            tick(millis, true);
        }
        float frac = (millis % 1000) / 1000f, minute = (millis % 60_000) / 60_000f;
        float baseline = pad + ascent;
        int tint = Apps.mix(accent, 0xFFFFFFFF, 0.55f);

        if (flash > 0) {
            float r = size * (1.4f + (1 - flash) * 1.6f), cx = getWidth() / 2f, cy = baseline - ascent * 0.4f;
            matrix.setScale(r, r * 0.55f);
            matrix.postTranslate(cx, cy);
            flashShader.setLocalMatrix(matrix);
            flashPaint.setAlpha(Math.round(255 * flash));
            c.drawOval(cx - r, cy - r * 0.55f, cx + r, cy + r * 0.55f, flashPaint);
            flash = Math.max(0f, flash - dt / 1.2f);
        }

        Paint thin = weights[0];
        thin.setShadowLayer(size * 0.14f, 0, 0, Apps.withAlpha(accent, 0x50));
        int q = 0;
        for (int i = 0; i < nowLen; i++) {
            char ch = now[i];
            one[0] = ch;
            if (ch == ':') {
                float beat = running ? 0.35f + 0.65f * (float) Math.exp(-frac * 5f) : 1f;
                if (event == ORBIT) {
                    q = drawOrbit(c, q, i, baseline, beat, tint);
                } else {
                    thin.setAlpha(Math.round(255 * beat));
                    c.drawText(one, 0, 1, nowX[i] + (colonW - thin.measureText(one, 0, 1)) / 2, baseline, thin);
                    thin.setAlpha(255);
                    drawSheen(c, thin, i, baseline, 0);
                }
                continue;
            }
            float t = morph[i];
            if (t < 0) {
                q = drawIdle(c, q, i, digitOrder(i), baseline, tint);
                continue;
            }
            int j = source[i];
            if (j >= 0) {
                float fade = 1f - smooth(0f, 0.3f, t);
                if (fade > 0) {
                    one[0] = before[j];
                    thin.setAlpha(Math.round(255 * fade));
                    c.drawText(one, 0, 1, beforeX[j] + (digitW - thin.measureText(one, 0, 1)) / 2, baseline, thin);
                    one[0] = ch;
                }
            }
            float land = smooth(0.95f, MORPH_T, t);
            if (land > 0) {
                Paint heavy = weights[Math.min(WEIGHTS.length - 1, Math.round((1 - land) * (WEIGHTS.length - 1)))];
                heavy.setAlpha(Math.round(255 * land));
                c.drawText(one, 0, 1, nowX[i] + (digitW - heavy.measureText(one, 0, 1)) / 2, baseline, heavy);
                heavy.setAlpha(255);
            }
            thin.setAlpha(255);
            float cx = nowX[i] + digitW / 2, cy = baseline - ascent * 0.38f;
            float landing = (t - 0.9f) / 0.6f;
            if (landing > 0 && landing < 1) {
                float rr = digitW * (0.3f + 1.1f * landing * drama[i] / 1.6f);
                ring.setStrokeWidth(size * 0.02f * (1 - landing) + 1);
                ring.setColor(Apps.withAlpha(tint, Math.round(170 * (1 - landing) * (1 - landing))));
                c.drawOval(cx - rr, cy - rr * 0.8f, cx + rr, cy + rr * 0.8f, ring);
                quad(q++, cx, cy, digitW * 0.9f, Apps.withAlpha(tint, Math.round(90 * (1 - landing))));
            }
            for (int k = 0; k < POINTS; k++) {
                int p = i * POINTS + k;
                float lt = t - delay[p], x, y, px, py, a;
                if (lt <= 0) {
                    x = px = fromX[p];
                    y = py = fromY[p];
                    a = j >= 0 ? smooth(0f, 0.3f, t) : 0f;
                } else {
                    float u = Math.min(1f, lt / 0.9f), u0 = Math.max(0f, u - 0.09f);
                    float e = u < 0.5f ? 4 * u * u * u : 1 - (float) Math.pow(-2 * u + 2, 3) / 2;
                    float e0 = u0 < 0.5f ? 4 * u0 * u0 * u0 : 1 - (float) Math.pow(-2 * u0 + 2, 3) / 2;
                    x = bezier(fromX[p], midX[p], toX[p], e);
                    y = bezier(fromY[p], midY[p], toY[p], e);
                    px = bezier(fromX[p], midX[p], toX[p], e0);
                    py = bezier(fromY[p], midY[p], toY[p], e0);
                    a = 1f - smooth(1.0f, MORPH_T, t);
                }
                if (a <= 0.01f) continue;
                float r = size * (0.018f + 0.014f * (k % 3));
                int hue = DUST[k % DUST.length];
                int color = hue == 0 ? tint : hue == 1 ? accent : hue;
                streak(q++, px, py, x, y, r, Apps.withAlpha(color, Math.round(235 * a)));
            }
            morph[i] = t + dt;
            if (morph[i] > MORPH_T) morph[i] = -1f;
        }
        thin.clearShadowLayer();

        if (running) {
            q = drawEvent(c, q, baseline, tint, dt);
            q = drawTwinkles(q, baseline, tint, dt);
        }

        if (seconds && nowLen > 0) {
            float x0 = nowX[0] + size * 0.04f, x1 = nowX[nowLen - 1] + digitW - size * 0.04f;
            float y = baseline + size * 0.17f, head = x0 + (x1 - x0) * minute;
            line.setStrokeWidth(Math.max(1f, size * 0.012f));
            line.setColor(0x1FFFFFFF);
            c.drawLine(x0, y, x1, y, line);
            line.setColor(Apps.withAlpha(tint, 0x80));
            c.drawLine(x0, y, head, y, line);
            quad(q++, head, y, size * 0.05f, Apps.withAlpha(tint, 0xE0));
            if (running && rnd.nextFloat() < dt * 14f) spark(head, y);
        }
        for (int i = 0; i < SPARKS; i++) {
            if (sLife[i] <= 0) continue;
            sLife[i] -= dt;
            sx[i] += svx[i] * dt;
            sy[i] += svy[i] * dt;
            quad(q++, sx[i], sy[i], size * 0.016f, Apps.withAlpha(tint, Math.round(200 * Math.min(1f, sLife[i]))));
        }
        if (q > 0) c.drawVertices(Canvas.VertexMode.TRIANGLES, q * 8, verts, 0, texs, 0, colors, 0, indices, 0, q * 6, sprites);

        if (running) postInvalidateOnAnimation();
    }
}
