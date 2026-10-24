package com.arzaroth.lumen;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ThemesTest {
    @Test
    public void spaceIsTheFirstAndTheDefault() {
        assertFalse(Themes.all().isEmpty());
        assertEquals("space", Themes.all().get(0).id());
        assertEquals("Space", Themes.byId("space").name());
        assertEquals("space", Themes.byId("no such theme").id());
        assertEquals("space", Themes.byId(null).id());
        assertTrue(Themes.byId("space").backdrop(0.5f) instanceof Cosmos);
    }
}
