package com.arzaroth.lumen;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Random;

/** Points on the strokes of a character, for text that breaks into dust and gathers back. */
final class Glyphs {
    private static final HashMap<String, float[]> CACHE = new HashMap<>();
    private static final char[] ONE = new char[1];

    private Glyphs() {
    }

    /**
     * `points` points on `ch` drawn with `paint` centred in a cell `cellW` wide, as x, y pairs
     * relative to the cell's left edge and the baseline, ordered by angle around the glyph's centre
     * so two glyphs pair up stroke for stroke.
     */
    static synchronized float[] sample(Paint paint, char ch, float cellW, int points) {
        String key = ch + "/" + paint.getTextSize() + "/" + System.identityHashCode(paint.getTypeface()) + "/" + cellW + "/" + points;
        float[] cached = CACHE.get(key);
        if (cached != null) return cached;
        float ascent = -paint.ascent();
        int bw = (int) Math.ceil(cellW) + 4, bh = (int) Math.ceil(ascent + paint.descent()) + 4;
        Bitmap b = Bitmap.createBitmap(Math.max(1, bw), Math.max(1, bh), Bitmap.Config.ARGB_8888);
        ONE[0] = ch;
        new Canvas(b).drawText(ONE, 0, 1, 2 + (cellW - paint.measureText(ONE, 0, 1)) / 2, 2 + ascent, paint);
        int[] px = new int[b.getWidth() * b.getHeight()];
        b.getPixels(px, 0, b.getWidth(), 0, 0, b.getWidth(), b.getHeight());
        bw = b.getWidth();
        b.recycle();
        int n = 0;
        for (int v : px) if (v >>> 24 > 128) n++;
        int[] on = new int[Math.max(1, n)];
        n = 0;
        for (int i = 0; i < px.length; i++) if (px[i] >>> 24 > 128) on[n++] = i;
        Random pick = new Random(ch);
        float[] pts = new float[points * 2];
        float cx = 0, cy = 0;
        for (int k = 0; k < points; k++) {
            int i = n > 0 ? on[pick.nextInt(n)] : 0;
            pts[2 * k] = i % bw - 2 + pick.nextFloat() - 0.5f;
            pts[2 * k + 1] = i / bw - 2 - ascent + pick.nextFloat() - 0.5f;
            cx += pts[2 * k];
            cy += pts[2 * k + 1];
        }
        final float mx = cx / points, my = cy / points;
        Integer[] order = new Integer[points];
        for (int k = 0; k < points; k++) order[k] = k;
        Arrays.sort(order, (a, c) -> Float.compare(
                (float) Math.atan2(pts[2 * a + 1] - my, pts[2 * a] - mx),
                (float) Math.atan2(pts[2 * c + 1] - my, pts[2 * c] - mx)));
        float[] sorted = new float[points * 2];
        for (int k = 0; k < points; k++) {
            sorted[2 * k] = pts[2 * order[k]];
            sorted[2 * k + 1] = pts[2 * order[k] + 1];
        }
        CACHE.put(key, sorted);
        return sorted;
    }
}
