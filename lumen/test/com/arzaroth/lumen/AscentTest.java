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
public class AscentTest {
    private Ascent ascent;
    private final Canvas canvas = Robo.canvas(960, 540);

    @Before
    public void setUp() {
        ascent = new Ascent(Robo.context(), 0.5f);
        Robo.layout(ascent, 960, 540);
    }

    private void play(float seconds) {
        Robo.frames(ascent, canvas, Math.round(seconds * 30), 33);
    }

    private void untilOff() {
        for (int i = 0; i < 200 && ascent.shown(); i++) play(0.1f);
        assertEquals(Ascent.Phase.OFF, ascent.phase());
        assertEquals(View.GONE, ascent.getVisibility());
    }

    @Test
    public void staysAwayUntilADownloadStarts() {
        ascent.draw(canvas);
        ascent.follow(Updater.State.CURRENT, "1.2.0", 0);
        ascent.follow(Updater.State.FAILED, "1.2.0", 0);
        ascent.dismiss();
        assertFalse(ascent.shown());
    }

    @Test
    public void gathersWithTheDownloadLightsUpToInstallThenLetsGo() {
        ascent.follow(Updater.State.DOWNLOADING, "1.2.0", 0);
        assertEquals(Ascent.Phase.GATHER, ascent.phase());
        assertEquals(View.VISIBLE, ascent.getVisibility());
        play(0.5f);
        assertEquals(0, (int) Robo.<Integer>get(ascent, "calledCount"));
        ascent.follow(Updater.State.DOWNLOADING, "1.2.0", 50);
        play(0.5f);
        int count = Robo.get(ascent, "count");
        assertEquals(count / 2, (int) Robo.<Integer>get(ascent, "calledCount"), 1);
        Robo.layout(ascent, 1280, 720);
        play(0.2f);
        ascent.dismiss();
        assertEquals(Ascent.Phase.GATHER, ascent.phase());
        ascent.follow(Updater.State.INSTALLING, "1.2.0", 100);
        assertEquals(Ascent.Phase.RISE, ascent.phase());
        play(3f);
        assertEquals(1f, Robo.<Float>get(ascent, "lit"), 0f);
        assertFalse(Robo.<Boolean>get(ascent, "confirming"));
        ascent.follow(Updater.State.CONFIRMING, "1.2.0", 100);
        assertTrue(Robo.<Boolean>get(ascent, "confirming"));
        play(0.2f);
        ascent.follow(Updater.State.INSTALLING, "1.2.0", 100);
        ascent.dismiss();
        assertEquals(Ascent.Phase.FADE, ascent.phase());
        untilOff();
    }

    @Test
    public void aFailedDownloadDriftsBackIntoTheSky() {
        ascent.follow(Updater.State.DOWNLOADING, "1.2.0", 30);
        play(1f);
        ascent.follow(Updater.State.FAILED, "1.2.0", 30);
        assertEquals(Ascent.Phase.FADE, ascent.phase());
        assertTrue(Robo.<Boolean>get(ascent, "failed"));
        ascent.follow(Updater.State.FAILED, "1.2.0", 30);
        untilOff();
        assertFalse(Robo.<Boolean>get(ascent, "failed"));
    }

    @Test
    public void anInstallThatNeverAnswersLetsGoOnItsOwn() {
        ascent.follow(Updater.State.INSTALLING, null, 100);
        assertEquals(Ascent.Phase.RISE, ascent.phase());
        play(21f);
        assertEquals(Ascent.Phase.FADE, ascent.phase());
        untilOff();
        ascent.follow(Updater.State.DOWNLOADING, "1.3.0", 0);
        assertEquals(Ascent.Phase.GATHER, ascent.phase());
        ascent.follow(Updater.State.AVAILABLE, "1.3.0", 0);
        untilOff();
    }
}
