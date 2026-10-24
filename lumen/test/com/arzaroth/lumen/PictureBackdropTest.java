package com.arzaroth.lumen;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.util.Random;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class PictureBackdropTest {
    static final int W = 320, H = 180;
    private final Bitmap screen = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
    private final Canvas canvas = new Canvas(screen);

    static final String JSON = "{\"id\":\"test\",\"image\":\"p.png\",\"anchor\":{\"image\":[0.5,0.7],\"screen\":[0.3,0.5]},"
            + "\"layers\":[{\"type\":\"glow\",\"period\":1,\"depth\":0.5},{\"type\":\"stars\",\"above\":0.5,\"count\":3},"
            + "{\"type\":\"sparks\",\"from\":[0.5,0.7],\"rate\":30,\"colors\":[\"#FFFF00\",\"#FF8000\",\"#FF0000\"]}]}";

    /** A night: dark blue, four stars up top, and a fire low in the middle. */
    static byte[] picture() {
        Bitmap b = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(0xFF080A18);
        for (int[] star : new int[][] {{30, 20}, {120, 40}, {250, 15}, {200, 70}}) b.setPixel(star[0], star[1], 0xFFE0E0E0);
        Paint p = new Paint();
        p.setColor(0xFFFF6010);
        c.drawCircle(150, 140, 18, p);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.PNG, 100, out);
        return out.toByteArray();
    }

    static PictureBackdrop ready(String json) throws Exception {
        byte[] png = picture();
        Pack pack = Pack.parse(json, name -> {
            if (!name.equals("p.png")) throw new FileNotFoundException(name);
            return new ByteArrayInputStream(png);
        });
        PictureBackdrop b = new PictureBackdrop(pack, 0.2f);
        b.layout(new RectF(110, 30, 300, 130), W, H);
        b.layout(new RectF(110, 30, 300, 130), W, H);
        for (int i = 0; i < 400 && Robo.get(b, "scene") == null && (boolean) Robo.get(b, "baking"); i++) Thread.sleep(25);
        return b;
    }

    private void run(PictureBackdrop b, float seconds) {
        for (int i = 0; i < Math.round(seconds * 30); i++) {
            b.update(1 / 30f);
            b.drawBack(canvas);
        }
    }

    private static int alive(PictureBackdrop b) {
        float[] age = Robo.get(b, "age"), life = Robo.get(b, "life");
        int n = 0;
        for (int i = 0; i < age.length; i++) if (age[i] < life[i]) n++;
        return n;
    }

    @Test
    public void findsTheFireAndTheStarsAndCoversTheScreen() throws Exception {
        PictureBackdrop b = ready(JSON);
        Object scene = Robo.get(b, "scene");
        assertNotNull(Robo.get(scene, "glow"));
        assertEquals(3, ((float[]) Robo.get(scene, "starX")).length);
        b.setLook(b.look(new int[6], 0, new Random(1)), false);
        run(b, 1f);
        assertTrue(alive(b) > 10);
        int fire = screen.getPixel(Math.round(W * 0.3f), Math.round(H * 0.5f));
        assertTrue(Color.red(fire) > Color.blue(fire));
        assertTrue(Color.blue(screen.getPixel(W - 2, 2)) > 0);
        assertTrue(Color.blue(screen.getPixel(W - 2, H - 2)) > 0);
    }

    @Test
    public void anEruptionThrowsABurstAndTheIntroEndsInOne() throws Exception {
        PictureBackdrop b = ready(JSON);
        b.storm(3f);
        assertEquals(1f, Robo.<Float>get(b, "flash"), 0f);
        assertTrue(alive(b) >= 90);
        b.intro();
        b.update(0.01f);
        b.drawBack(canvas);
        assertTrue(Robo.<Float>get(b, "shown") < 0.1f);
        run(b, 1.8f);
        assertEquals(-1f, Robo.<Float>get(b, "introT"), 0f);
    }

    @Test
    public void warpWeatherAndParallaxKeepItMoving() throws Exception {
        PictureBackdrop b = ready(JSON);
        b.setWarp(1f);
        assertTrue(b.moving());
        run(b, 0.3f);
        b.setWarp(0f);
        b.setWeather(1f, 1f, 0f);
        run(b, 5f);
        assertEquals(1f, Robo.<Float>get(b, "clouds"), 0f);
        b.setParallax(-2f, 2f);
        run(b, 3f);
    }

    @Test
    public void stillStopsFramesOnceTheSparksAreOut() throws Exception {
        PictureBackdrop b = ready(JSON);
        b.setStill(true);
        run(b, 4f);
        assertFalse(b.moving());
        b.setStill(false);
        assertTrue(b.moving());
    }

    @Test
    public void aPackWithoutLayersOrPictureStillDraws() throws Exception {
        PictureBackdrop bare = ready("{\"id\":\"bare\",\"image\":\"p.png\"}");
        assertNull(Robo.get(Robo.get(bare, "scene"), "glow"));
        bare.storm(1f);
        run(bare, 0.5f);
        assertEquals(0, alive(bare));
        PictureBackdrop missing = ready("{\"id\":\"gone\",\"image\":\"nowhere.png\"}");
        assertNull(Robo.get(missing, "scene"));
        run(missing, 0.2f);
        assertEquals(0xFF000000, screen.getPixel(W / 2, H / 2));
    }
}
