package com.arzaroth.lumen;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.graphics.Canvas;
import android.view.View;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ForgeTest {
    private Forge forge;
    private final Canvas canvas = Robo.canvas(960, 540);

    @Before
    public void setUp() {
        forge = new Forge(Robo.context(), 0.5f);
        Robo.layout(forge, 960, 540);
    }

    private void burn(float seconds) {
        Robo.frames(forge, canvas, Math.round(seconds * 30), 33);
    }

    private void untilOff() {
        for (int i = 0; i < 400 && forge.shown(); i++) burn(0.1f);
        assertEquals(Forge.Phase.OFF, forge.phase());
        assertEquals(View.GONE, forge.getVisibility());
    }

    @Test
    public void staysAwayUntilADownloadStarts() {
        forge.draw(canvas);
        forge.follow(Updater.State.CURRENT, "1.2.0", 0);
        forge.follow(Updater.State.FAILED, "1.2.0", 0);
        forge.cool();
        assertFalse(forge.shown());
        assertEquals(View.GONE, forge.getVisibility());
    }

    @Test
    public void burnsWithTheDownloadRisesToInstallThenCools() {
        forge.follow(Updater.State.DOWNLOADING, "1.2.0", 0);
        assertEquals(Forge.Phase.BURN, forge.phase());
        assertEquals(View.VISIBLE, forge.getVisibility());
        burn(1f);
        forge.follow(Updater.State.DOWNLOADING, "1.2.0", 60);
        burn(1.5f);
        assertTrue(Robo.<Float>get(forge, "heat") > 0.5f);
        forge.cool();
        assertEquals(Forge.Phase.BURN, forge.phase());
        forge.follow(Updater.State.INSTALLING, "1.2.0", 100);
        assertEquals(Forge.Phase.RISE, forge.phase());
        burn(2f);
        forge.follow(Updater.State.INSTALLING, "1.2.0", 100);
        assertEquals(Forge.Phase.RISE, forge.phase());
        forge.cool();
        assertEquals(Forge.Phase.COOL, forge.phase());
        untilOff();
    }

    @Test
    public void aFailedDownloadPutsTheFireOut() {
        forge.follow(Updater.State.DOWNLOADING, "1.2.0", 30);
        burn(1f);
        forge.follow(Updater.State.FAILED, "1.2.0", 30);
        assertEquals(Forge.Phase.COOL, forge.phase());
        assertTrue(Robo.<Boolean>get(forge, "failed"));
        burn(0.5f);
        forge.follow(Updater.State.FAILED, "1.2.0", 30);
        untilOff();
        assertFalse(Robo.<Boolean>get(forge, "failed"));
    }

    @Test
    public void anInstallThatNeverAnswersCoolsOnItsOwn() {
        forge.follow(Updater.State.INSTALLING, "1.2.0", 100);
        assertEquals(Forge.Phase.RISE, forge.phase());
        burn(21f);
        assertEquals(Forge.Phase.COOL, forge.phase());
        forge.follow(Updater.State.AVAILABLE, "1.2.0", 100);
        untilOff();
        forge.follow(Updater.State.DOWNLOADING, "1.3.0", 0);
        assertEquals(Forge.Phase.BURN, forge.phase());
    }
}
