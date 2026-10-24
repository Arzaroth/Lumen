package com.arzaroth.lumen;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Random;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class VolcanoTest {
    static final int W = 320, H = 180;
    private final Bitmap screen = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
    private final Canvas canvas = new Canvas(screen);

    static Volcano.Look look(Volcano v, int seed, int hue) {
        int[] sparks = {0xFFFFFFFF, 0xFFFFD27A, hue, 0xFF808080, 0xFF404040, hue};
        return v.look(sparks, hue, new Random(seed));
    }

    static Volcano ready() {
        Volcano v = new Volcano(0.2f);
        v.layout(new RectF(110, 30, 300, 130), W, H);
        v.layout(new RectF(110, 30, 300, 130), W, H);
        for (int i = 0; i < 400 && Robo.get(v, "land") == null; i++) {
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        assertNotNull(Robo.get(v, "land"));
        return v;
    }

    private void run(Volcano v, float seconds) {
        int n = Math.round(seconds * 30);
        for (int i = 0; i < n; i++) {
            v.update(1 / 30f);
            v.drawBack(canvas);
        }
    }

    private int craterRed() {
        int c = screen.getPixel(Math.round(W * 0.22f), Math.round(H * 0.46f) + 3);
        return Color.red(c);
    }

    @Test
    public void lavaTakesTheLogosHueAndGreyLogosGetFire() {
        Volcano v = new Volcano(0.2f);
        float[] hsv = new float[3];
        Color.colorToHSV(look(v, 1, 0xFF2196F3).lava, hsv);
        assertTrue(hsv[0] > 190 && hsv[0] < 225);
        Color.colorToHSV(look(v, 2, 0xFF808080).lava, hsv);
        assertTrue(hsv[0] >= 14 && hsv[0] <= 32);
        assertNotNull(look(v, 3, 0xFFE53935).plume);
    }

    @Test
    public void drawsBlackBeforeTheLandIsBaked() {
        Volcano v = new Volcano(0.2f);
        v.storm(3);
        v.update(0.1f);
        v.drawBack(canvas);
        assertEquals(0xFF000000, screen.getPixel(W / 2, H / 2));
    }

    @Test
    public void theCraterGlowsInTheLavasColour() {
        Volcano v = ready();
        v.setLook(look(v, 1, 0xFFE53935), false);
        run(v, 0.5f);
        int c = screen.getPixel(Math.round(W * 0.22f), Math.round(H * 0.46f) + 3);
        assertTrue(Color.red(c) > 120);
        assertTrue(Color.red(c) > Color.blue(c));
        v.setLook(look(v, 2, 0xFF2196F3), false);
        run(v, 0.5f);
        c = screen.getPixel(Math.round(W * 0.22f), Math.round(H * 0.46f) + 3);
        assertTrue(Color.blue(c) > Color.red(c));
    }

    @Test
    public void lookChangesCrossFadeThePlumeAndLava() {
        Volcano v = ready();
        Volcano.Look red = look(v, 1, 0xFFE53935), blue = look(v, 2, 0xFF2196F3);
        v.setLook(red, true);
        assertTrue(v.moving());
        run(v, 2.5f);
        v.setLook(red, true);
        v.setLook(blue, true);
        assertSame(red, Robo.get(v, "previous"));
        run(v, 1f);
        int mid = Robo.get(v, "lava");
        assertNotEquals(red.lava, mid);
        assertNotEquals(blue.lava, mid);
        run(v, 1.5f);
        assertNull(Robo.get(v, "previous"));
        assertEquals(blue.lava, (int) Robo.get(v, "lava"));
    }

    @Test
    public void embersRiseAndAnEruptionThrowsBombsAndSometimesLightning() {
        Volcano v = ready();
        v.setLook(look(v, 1, 0xFFE53935), false);
        v.setActivity(3f);
        run(v, 2f);
        assertTrue(alive(v) > 10);
        for (int i = 0; i < 6; i++) {
            v.storm(4);
            assertEquals(1f, Robo.<Float>get(v, "flash"), 0f);
            run(v, 0.4f);
        }
        assertTrue(alive(v) > 70);
        Robo.set(v, "nextEruption", 0.01f);
        run(v, 0.2f);
        assertTrue(Robo.<Float>get(v, "nextEruption") > 30f);
    }

    @Test
    public void theIntroFadesTheLandInThenWakesTheCrater() {
        Volcano v = ready();
        v.setLook(look(v, 1, 0xFFE53935), false);
        v.intro();
        v.update(0.01f);
        v.drawBack(canvas);
        assertTrue(craterRed() < 40);
        run(v, 2.4f);
        assertEquals(-1f, Robo.<Float>get(v, "introT"), 0f);
        assertTrue(craterRed() > 120);
    }

    @Test
    public void warpDimsTheLandAndBlowsTheEmbersOutward() {
        Volcano v = ready();
        v.setLook(look(v, 1, 0xFFE53935), false);
        v.setActivity(3f);
        run(v, 1f);
        int before = craterRed();
        v.setWarp(1f);
        run(v, 0.3f);
        assertTrue(craterRed() < before);
        assertTrue(v.moving());
        v.setWarp(0f);
    }

    @Test
    public void stillAndQuietStopsFramesOnceTheEmbersAreGone() {
        Volcano v = ready();
        v.setLook(look(v, 1, 0xFFE53935), false);
        v.setStill(true);
        v.setActivity(0f);
        run(v, 7f);
        assertFalse(v.moving());
        v.setParallax(1f, 1f);
        assertTrue(v.moving());
        run(v, 5f);
        assertFalse(v.moving());
        v.setStill(false);
        assertTrue(v.moving());
    }

    @Test
    public void weatherThickensThePlumeAndRainFallsInFront() {
        Volcano v = ready();
        v.setLook(look(v, 1, 0xFFE53935), false);
        v.setWeather(1f, 1f, 0f);
        assertTrue(v.moving());
        run(v, 5f);
        assertEquals(1f, Robo.<Float>get(v, "clouds"), 0f);
        v.setWeather(0f, 0f, 1f);
        run(v, 5f);
        v.setParallax(-2f, -1f);
        run(v, 2f);
    }

    @Test
    public void anotherScreenSizeBakesAgain() {
        Volcano v = ready();
        Object first = Robo.get(v, "land");
        v.layout(new RectF(0, 0, 10, 10), 160, 90);
        for (int i = 0; i < 400 && Robo.get(v, "land") == first; i++) {
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        assertNotSame(first, Robo.get(v, "land"));
    }

    private static int alive(Volcano v) {
        float[] age = Robo.get(v, "age"), life = Robo.get(v, "life");
        int n = 0;
        for (int i = 0; i < age.length; i++) if (age[i] < life[i]) n++;
        return n;
    }
}
