package com.arzaroth.lumen;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class PackTest {
    static final String FULL = "{\"id\":\"lava\",\"name\":\"Lava\",\"image\":\"a.jpg\","
            + "\"anchor\":{\"image\":[0.3,0.7],\"screen\":[0.25,0.5]},"
            + "\"palette\":{\"hot\":\"#FFFFFF\",\"warm\":\"#FF8000\",\"dust\":[\"#111111\",\"#222222\"],\"motes\":[\"#333333\"],"
            + "\"glow\":\"#444444\",\"scrim\":\"#555555\",\"calm\":\"not a colour\"},"
            + "\"layers\":[{\"type\":\"glow\",\"depth\":0.5},{\"type\":\"sparks\",\"speed\":[0.1,0.3],\"colors\":[\"#FF0000\",\"bad\"]}]}";

    @Test
    public void readsEverythingAPackSays() throws Exception {
        Pack p = Pack.parse(FULL, name -> null);
        assertEquals("lava", p.id);
        assertEquals("Lava", p.name);
        assertEquals("a.jpg", p.image);
        assertArrayEquals(new float[] {0.3f, 0.7f}, p.from, 1e-6f);
        assertArrayEquals(new float[] {0.25f, 0.5f}, p.to, 1e-6f);
        assertEquals(0xFFFF8000, p.palette.warm);
        assertArrayEquals(new int[] {0xFF111111, 0xFF222222}, p.palette.dust);
        assertEquals(0xFF444444, p.palette.glow);
        assertEquals(Palette.SPACE.calm, p.palette.calm);
        assertEquals(2, p.layers.size());
        Pack.Layer glow = p.layers.get(0), sparks = p.layers.get(1);
        assertEquals(0.5f, glow.num("depth", 0f), 1e-6f);
        assertEquals(7f, glow.num("missing", 7f), 0f);
        assertArrayEquals(new float[] {0.1f, 0.3f}, sparks.pair("speed", 0f, 0f), 1e-6f);
        assertArrayEquals(new float[] {1f, 2f}, sparks.pair("missing", 1f, 2f), 0f);
        assertArrayEquals(new int[] {0xFFFF0000, 0xFF00FF00}, sparks.colors("colors", 0xFF00FF00));
        assertArrayEquals(new int[] {0xFF0000FF}, glow.colors("colors", 0xFF0000FF));
    }

    @Test
    public void aBarePackFallsBackToSpacesLightAndTheMiddle() throws Exception {
        Pack p = Pack.parse("{\"id\":\"bare\",\"image\":\"b.png\",\"palette\":{\"dust\":[\"#000000\"]}}", name -> null);
        assertEquals("bare", p.name);
        assertArrayEquals(new float[] {0.5f, 0.5f}, p.from, 0f);
        assertArrayEquals(Palette.SPACE.dust, p.palette.dust);
        assertTrue(p.layers.isEmpty());
        assertSame(Palette.SPACE, Pack.parse("{\"id\":\"x\",\"image\":\"y\"}", name -> null).palette);
    }

    @Test
    public void readsThemeJsonThroughItsFiles() throws Exception {
        Pack p = Pack.read(name -> {
            if (!name.equals("theme.json")) throw new FileNotFoundException(name);
            return new ByteArrayInputStream(FULL.getBytes(StandardCharsets.UTF_8));
        });
        assertEquals("lava", new PackTheme(p).id());
        assertEquals("Lava", new PackTheme(p).name());
        assertSame(p.palette, new PackTheme(p).palette());
        assertTrue(new PackTheme(p).backdrop(Robo.context(), 0.5f) instanceof PictureBackdrop);
    }
}
