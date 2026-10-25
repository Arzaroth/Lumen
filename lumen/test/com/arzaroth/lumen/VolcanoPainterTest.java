package com.arzaroth.lumen;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Color;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class VolcanoPainterTest {
    static final int W = 480, H = 270;

    @Test
    public void paintsANightEruptionWithItsMovingParts() {
        Pack.Painting p = VolcanoPainter.paint(W, H);
        assertEquals(W, p.picture.getWidth());
        int sky = p.picture.getPixel(W - 10, 10);
        assertTrue(Color.blue(sky) > Color.red(sky));
        int land = p.picture.getPixel(W - 10, H - 4);
        assertTrue(Color.red(land) < 20 && Color.green(land) < 20);
        assertEquals(2, p.smoke.length);
        assertEquals(2f, p.smokeScale, 0f);
        assertNotNull(p.over);
        assertTrue(p.flows.size() >= 4);
        float[] flow = p.flows.get(0);
        assertTrue(flow[0] > 0f && flow[2] > p.originY);
        assertEquals(W * VolcanoPainter.VENT_X, p.originX, 0.5f);
        int fire = 0;
        for (int y = 0; y < p.over.getHeight(); y++) {
            for (int x = 0; x < p.over.getWidth(); x++) {
                int c = p.over.getPixel(x, y);
                if (Color.alpha(c) > 100 && Color.red(c) > 200) fire++;
            }
        }
        assertTrue(fire > 10);
    }

    @Test
    public void theSameVolcanoEveryTime() {
        Pack.Painting a = VolcanoPainter.paint(W, H), b = VolcanoPainter.paint(W, H);
        for (int[] at : new int[][] {{W / 3, H / 2}, {W / 4, H / 5}, {W / 2, H * 3 / 4}}) {
            assertEquals(a.picture.getPixel(at[0], at[1]), b.picture.getPixel(at[0], at[1]));
        }
    }

    @Test
    public void theDrawnVolcanoIsATheme() {
        Theme t = DrawnVolcano.THEME;
        assertEquals("volcano-drawn", t.id());
        assertEquals("Volcano (drawn)", t.name());
        assertTrue(t.backdrop(Robo.context(), 0.5f) instanceof PictureBackdrop);
        assertEquals(t, Themes.byId("volcano-drawn"));
    }
}
