package dev.arzaroth.lumen;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Paint;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class GlyphsTest {
    private Paint paint(float size) {
        Context c = RuntimeEnvironment.getApplication();
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(Fonts.outfit(c, 400));
        p.setTextSize(size);
        return p;
    }

    @Test
    public void pointsLieOnTheGlyphInsideItsCell() {
        Paint p = paint(120f);
        float cell = p.measureText("8");
        float[] pts = Glyphs.sample(p, '8', cell, 60);
        assertEquals(120, pts.length);
        for (int k = 0; k < 60; k++) {
            assertTrue("x in cell", pts[2 * k] >= -2 && pts[2 * k] <= cell + 2);
            assertTrue("y above the baseline", pts[2 * k + 1] <= 2 && pts[2 * k + 1] >= p.ascent() - 2);
        }
    }

    @Test
    public void samplesAreCachedPerCharacterAndSize() {
        Paint p = paint(80f);
        float[] a = Glyphs.sample(p, '4', 50f, 30);
        assertSame(a, Glyphs.sample(p, '4', 50f, 30));
        assertTrue(a != Glyphs.sample(p, '5', 50f, 30));
    }

    @Test
    public void pointsRunRoundTheGlyphInAngleOrder() {
        Paint p = paint(120f);
        float[] pts = Glyphs.sample(p, '0', p.measureText("0"), 80);
        float mx = 0, my = 0;
        for (int k = 0; k < 80; k++) {
            mx += pts[2 * k];
            my += pts[2 * k + 1];
        }
        mx /= 80;
        my /= 80;
        double last = -Math.PI - 1e-6;
        for (int k = 0; k < 80; k++) {
            double a = Math.atan2(pts[2 * k + 1] - my, pts[2 * k] - mx);
            assertTrue(a >= last - 1e-4);
            last = a;
        }
    }

    @Test
    public void anEmptyGlyphStillGivesPoints() {
        Paint p = paint(60f);
        assertEquals(20, Glyphs.sample(p, ' ', 20f, 10).length);
    }
}
