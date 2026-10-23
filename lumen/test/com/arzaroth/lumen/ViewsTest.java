package com.arzaroth.lumen;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ViewsTest {
    static AppEntry app(String pkg, int color) {
        Bitmap b = Bitmap.createBitmap(160, 90, Bitmap.Config.ARGB_8888);
        b.eraseColor(color);
        return new AppEntry(new ComponentName(pkg, pkg + ".Main"), pkg, b, Apps.accent(b));
    }

    @Test
    public void pillsDrawEveryGlyphFocusedAndNot() {
        Canvas c = Robo.canvas(400, 100);
        for (int kind = Pill.INPUTS; kind <= Pill.SETTINGS; kind++) {
            Pill p = new Pill(Robo.context(), kind, kind == Pill.SETTINGS ? "" : "Label", 1f);
            Robo.wrap(p);
            p.draw(c);
            p.setFocusedState(true, false);
            p.draw(c);
            p.setFocusedState(false, true);
            Robo.idle(400);
            p.draw(c);
        }
    }

    @Test
    public void wifiPillShowsWiredAndRelabels() {
        Pill p = new Pill(Robo.context(), Pill.WIFI, "Wi-Fi", 1f);
        Robo.wrap(p);
        int narrow = p.getMeasuredWidth();
        p.setLabel("Wi-Fi");
        p.setLabel("A much longer network name");
        Robo.wrap(p);
        assertTrue(p.getMeasuredWidth() > narrow);
        p.setWired(true);
        p.setWired(true);
        p.draw(Robo.canvas(600, 100));
        p.setWired(false);
        p.draw(Robo.canvas(600, 100));
    }

    @Test
    public void tileFocusLabelAndWiggle() {
        Tile t = new Tile(Robo.context(), app("org.example.kodi", 0xFF1E88E5), 160, 90, 40, 1f);
        Robo.layout(t, 160, 140);
        Canvas c = Robo.canvas(160, 140);
        t.draw(c);
        t.setFocusedState(true, true);
        t.setFocusedState(true, true);
        Robo.idle(500);
        t.draw(c);
        t.setFocusedState(false, true);
        Robo.idle(500);
        t.draw(c);
        t.setFocusedState(true, false);
        t.draw(c);
        t.setFocusedState(false, false);
        android.graphics.Outline outline = new android.graphics.Outline();
        t.getOutlineProvider().getOutline(t, outline);
        assertEquals(0.85f, outline.getAlpha(), 0.001f);
        t.setWiggling(true);
        Robo.idle(300);
        t.setWiggling(true);
        t.setWiggling(false);
        Robo.idle(300);
        assertEquals(0f, t.getRotation(), 0.01f);
    }

    @Test
    public void highlightSpringsBetweenTargetsAndFades() {
        Highlight h = new Highlight(Robo.context(), 1f);
        Robo.layout(h, 800, 400);
        Canvas c = Robo.canvas(800, 400);
        h.draw(c);
        h.moveTo(new RectF(10, 10, 200, 120), 0xFF00FF00, true);
        Robo.idle(250);
        h.draw(c);
        h.moveTo(new RectF(300, 10, 500, 120), 0xFFFF0000, true);
        Robo.idle(100);
        h.draw(c);
        Robo.idle(500);
        h.moveTo(new RectF(0, 0, 100, 100), 0xFF0000FF, false);
        h.show(true, true);
        h.show(false, true);
        Robo.idle(300);
        h.draw(c);
        h.show(false, false);
        h.draw(c);
    }

    @Test
    public void accentFindsTheHueOrFallsBackToNeutral() {
        Bitmap red = Bitmap.createBitmap(32, 18, Bitmap.Config.ARGB_8888);
        red.eraseColor(0xFFE53935);
        float[] hsv = new float[3];
        Color.colorToHSV(Apps.accent(red), hsv);
        assertTrue(hsv[0] < 20 || hsv[0] > 340);
        Bitmap grey = Bitmap.createBitmap(32, 18, Bitmap.Config.ARGB_8888);
        grey.eraseColor(0xFF808080);
        assertEquals(Apps.NEUTRAL, Apps.accent(grey));
        Bitmap clear = Bitmap.createBitmap(32, 18, Bitmap.Config.ARGB_8888);
        assertEquals(Apps.NEUTRAL, Apps.accent(clear));
        Bitmap exact = Bitmap.createBitmap(16, 9, Bitmap.Config.ARGB_8888);
        exact.eraseColor(0xFF43A047);
        assertNotEquals(Apps.NEUTRAL, Apps.accent(exact));
    }

    @Test
    public void colourHelpers() {
        assertEquals(0xFF808080, Apps.mix(0xFF000000, 0xFFFFFFFF, 0.5f));
        assertEquals(0x40123456, Apps.withAlpha(0xFF123456, 0x40));
        assertEquals("org.example/.Main", new AppEntry(new ComponentName("org.example", "org.example.Main"), "x",
                Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888), 0).key());
    }

    @Test
    public void fontsAreCachedAndFallBackWhenTheAssetCannotLoad() {
        assertEquals(Fonts.outfit(Robo.context(), 300), Fonts.outfit(Robo.context(), 300));
        android.content.ContextWrapper broken = new android.content.ContextWrapper(Robo.context()) {
            @Override
            public android.content.res.AssetManager getAssets() {
                return null;
            }
        };
        assertTrue(Fonts.outfit(broken, 777) != null);
    }
}
