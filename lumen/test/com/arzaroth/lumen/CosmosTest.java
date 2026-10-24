package com.arzaroth.lumen;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Canvas;
import android.graphics.RectF;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Random;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class CosmosTest {
    static final int W = 320, H = 180;
    private final Canvas canvas = Robo.canvas(W, H);

    @After
    public void tearDown() {
        Wall.clock = System::currentTimeMillis;
    }

    static Cosmos.Theme theme(int seed, int hue) {
        int[] sparks = {0xFFFFFFFF, 0xFFFFD27A, hue, 0xFF808080, 0xFF404040, hue};
        return Cosmos.theme(sparks, hue, new Random(seed));
    }

    static Cosmos ready() {
        Cosmos c = new Cosmos(0.2f);
        c.layout(new RectF(110, 30, 300, 130), W, H);
        c.layout(new RectF(110, 30, 300, 130), W, H);
        for (int i = 0; i < 400 && Robo.get(c, "sky") == null; i++) {
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        assertNotNull(Robo.get(c, "sky"));
        return c;
    }

    private void run(Cosmos c, float seconds) {
        int n = Math.round(seconds * 30);
        for (int i = 0; i < n; i++) {
            c.update(1 / 30f);
            c.drawBack(canvas);
        }
    }

    @Test
    public void aFullScreenSkyBakesItsBrightStarsTooWhenTheLiveOnesAreTaken() throws Exception {
        java.lang.reflect.Method sky = Cosmos.class.getDeclaredMethod("sky", int.class, int.class);
        sky.setAccessible(true);
        Object made = sky.invoke(null, 1280, 720);
        float[] live = Robo.get(made, "x");
        assertEquals(800, live.length);
    }

    @Test
    public void themesContrastWithTheLogoAndEachHasANebula() {
        Cosmos.Theme red = theme(1, 0xFFE53935), grey = theme(2, 0xFF808080);
        assertNotNull(red.nebula);
        assertNotNull(grey.nebula);
        assertTrue(red.tint != 0);
    }

    @Test
    public void drawsBeforeTheSkyIsReady() {
        Cosmos c = new Cosmos(0.2f);
        c.update(0.1f);
        c.drawBack(canvas);
        c.setTheme(theme(3, 0xFF2196F3), false);
        c.update(0.1f);
        c.drawBack(canvas);
        assertTrue(c.moving());
    }

    @Test
    public void meteorsSatellitesTrainsAndANova() {
        Cosmos c = ready();
        Cosmos.Theme first = theme(4, 0xFF43A047);
        c.setTheme(first, false);
        c.setTheme(first, true);
        Robo.set(c, "nextSatellite", 0f);
        Robo.set(c, "nextTrain", 0f);
        Robo.set(c, "nextNova", 0f);
        c.storm(3f);
        run(c, 4f);
        assertTrue((float) Robo.get(c, "trainLife") > 0 || (float) Robo.get(c, "satLife") > 0);
        for (int i = 0; i < 3; i++) {
            Robo.set(c, "nextSatellite", 0f);
            Robo.set(c, "nextTrain", 0f);
            run(c, 0.2f);
        }
        run(c, 10f);
        assertEquals(-1, (int) Robo.get(c, "nova"));
        c.setTheme(theme(5, 0xFF8E24AA), true);
        run(c, 1.5f);
    }

    @Test
    public void introStarsComeOutOneByOne() {
        Cosmos c = ready();
        c.setTheme(theme(6, 0xFFFB8C00), false);
        c.intro();
        run(c, 3.2f);
        assertEquals(-1f, (float) Robo.get(c, "introT"), 0f);
    }

    @Test
    public void warpStreaksTheBrightStars() {
        Cosmos c = ready();
        c.setTheme(theme(7, 0xFF00ACC1), false);
        c.setWarp(0.8f);
        run(c, 0.3f);
        c.setWarp(0f);
        run(c, 0.1f);
    }

    @Test
    public void aStillSkyStopsMovingOnceSettled() {
        Cosmos c = ready();
        c.setTheme(theme(8, 0xFF3949AB), false);
        c.setStill(true);
        c.setMeteors(0f);
        run(c, 3f);
        Robo.set(c, "satLife", 0f);
        run(c, 0.2f);
        assertFalse(c.moving());
        c.setStill(false);
        c.setMeteors(1f);
        c.setTheme(theme(9, 0xFFD81B60), true);
        assertTrue(c.moving());
    }

    @Test
    public void theTurningSkyDrawsBothCopiesAcrossItsSeam() {
        Cosmos c = ready();
        c.setTheme(theme(10, 0xFF6D4C41), false);
        c.setTurning(true);
        long tile = 2L * W;
        double rate = 360.0 / 86164.0905 * W / 50.0 / 1000.0;
        long[] at = {(long) (tile * 0.3 / rate)};
        Wall.clock = () -> at[0];
        run(c, 0.2f);
        at[0] = (long) (tile * 1.8 / rate);
        Robo.set(c, "nova", 3);
        Robo.set(c, "novaT", 1f);
        run(c, 0.2f);
        c.setTurning(false);
        run(c, 0.1f);
    }

    @Test
    public void parallaxGlidesTheLayersThenSettles() {
        Cosmos c = ready();
        c.setTheme(theme(11, 0xFF00897B), false);
        c.setStill(true);
        c.setMeteors(0f);
        run(c, 3f);
        Robo.set(c, "satLife", 0f);
        run(c, 0.2f);
        assertFalse(c.moving());
        c.setParallax(4f, 2f);
        assertTrue(c.moving());
        assertEquals(1f, Robo.<Float>get(c, "parTarget"), 0f);
        assertEquals(1f, Robo.<Float>get(c, "parYTarget"), 0f);
        run(c, 0.3f);
        float midway = Robo.get(c, "par");
        assertTrue(midway > 0.3f && midway < 1f);
        run(c, 4f);
        assertFalse(c.moving());
        c.setParallax(-1f, -1f);
        assertEquals(0f, Robo.<Float>get(c, "parYTarget"), 0f);
        c.setTurning(true);
        run(c, 4f);
        assertEquals(-1f, Robo.<Float>get(c, "par"), 0f);
    }

    @Test
    public void theJumpToLightSpeedFliesOnAndNeverPullsBack() {
        Cosmos c = ready();
        c.setTheme(theme(12, 0xFF8E24AA), false);
        run(c, 1f);
        c.setWarp(1f);
        run(c, 0.5f);
        float early = Robo.get(c, "warpTravel");
        run(c, 0.5f);
        assertTrue((float) Robo.get(c, "warpTravel") > early && early > 0f);
        c.setWarp(0.4f);
        run(c, 0.3f);
        c.setWarp(0f);
        run(c, 0.1f);
        assertEquals(0f, Robo.<Float>get(c, "warpTravel"), 0f);
    }

    @Test
    public void withoutTheNebulaOnlyTheAirglowIsLeft() {
        Cosmos.Theme t = theme(13, 0xFF00ACC1);
        assertTrue(t.glow.getPixel(200, 220) != 0xFF000000);
        assertEquals(0xFF000000, t.glow.getPixel(200, 10));
        Cosmos c = ready();
        c.setTheme(t, false);
        c.setNebula(false);
        run(c, 0.3f);
        c.setTheme(theme(14, 0xFFF4511E), true);
        run(c, 0.5f);
        c.setNebula(true);
        run(c, 0.2f);
    }

    @Test
    public void cloudsVeilTheSkyRainAndSnowFallInFront() {
        Cosmos c = ready();
        c.setTheme(theme(15, 0xFF3949AB), false);
        c.setStill(true);
        c.setWeather(1f, 1f, 0f);
        assertTrue(c.moving());
        run(c, 6f);
        assertEquals(1f, Robo.<Float>get(c, "rain"), 0f);
        c.setWeather(0.5f, 0f, 0.6f);
        run(c, 6f);
        assertEquals(0.6f, Robo.<Float>get(c, "snow"), 1e-6f);
        c.setWeather(0f, 0f, 0f);
        run(c, 6f);
        Robo.set(c, "satLife", 0f);
        run(c, 0.2f);
        assertFalse(c.moving());
    }

    private static boolean real(Cosmos c) {
        Object sky = Robo.get(c, "sky");
        return sky != null && (boolean) Robo.call(sky, "real");
    }

    private void until(Cosmos c, java.util.function.BooleanSupplier done) {
        for (int i = 0; i < 1200 && !done.getAsBoolean(); i++) {
            c.update(1 / 30f);
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        assertTrue(done.getAsBoolean());
    }

    @Test
    public void theRealSkyFollowsTheNightAndTheMoon() throws Exception {
        long[] now = {1792872000000L};
        Wall.clock = () -> now[0];
        float[] stars = Heavens.catalog(new java.io.FileInputStream("assets/stars.bin"));
        Cosmos c = ready();
        c.setTheme(theme(16, 0xFF3949AB), false);
        c.setRealSky(new double[] {48.85, 2.35}, stars);
        until(c, () -> real(c));
        run(c, 1.5f);
        assertFalse(Float.isNaN(Robo.<Float>get(c, "moonX")));
        now[0] += 3_600_000L * 24 * 5;
        Robo.set(c, "bakeClock", 119.9f);
        run(c, 0.2f);
        until(c, () -> !(boolean) Robo.get(c, "baking") && (float) Robo.get(c, "fieldFade") < 1f);
        run(c, 3f);
        assertEquals(0f, Robo.<Float>get(c, "moonWaxing"), 0f);
        now[0] += 3_600_000L * 12;
        Robo.set(c, "realClock", 0.99f);
        run(c, 0.2f);
        c.setRealSky(new double[] {-33.9, 151.2}, stars);
        until(c, () -> real(c) && !(boolean) Robo.get(c, "baking"));
        run(c, 1.2f);
        c.setRealSky(null, null);
        until(c, () -> !real(c));
        run(c, 0.2f);
    }
}
