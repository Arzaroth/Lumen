package com.arzaroth.lumen;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Map;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class HabitsTest {
    private static final long DAY = 24 * 3600 * 1000L;
    private SharedPreferences prefs;
    private long now = 1_000_000_000_000L;

    @Before
    public void setUp() {
        prefs = Robo.context().getSharedPreferences("lumen", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        Wall.clock = () -> now;
    }

    @After
    public void tearDown() {
        Wall.clock = System::currentTimeMillis;
    }

    @Test
    public void eachLaunchCountsAndAWeekHalvesIt() {
        assertTrue(Habits.scores(prefs).isEmpty());
        Habits.record(prefs, "org.example.kodi");
        Habits.record(prefs, "org.example.kodi");
        Habits.record(prefs, "org.example.jellyfin");
        Map<String, Double> scores = Habits.scores(prefs);
        assertEquals(2.0, scores.get("org.example.kodi"), 1e-9);
        assertEquals(1.0, scores.get("org.example.jellyfin"), 1e-9);
        now += 7 * DAY;
        assertEquals(1.0, Habits.scores(prefs).get("org.example.kodi"), 1e-9);
        Habits.record(prefs, "org.example.jellyfin");
        scores = Habits.scores(prefs);
        assertEquals(1.0, scores.get("org.example.kodi"), 1e-9);
        assertEquals(1.5, scores.get("org.example.jellyfin"), 1e-9);
    }

    @Test
    public void longForgottenAppsDropOut() {
        Habits.record(prefs, "org.example.old");
        now += 70 * DAY;
        Habits.record(prefs, "org.example.new");
        assertTrue(!Habits.scores(prefs).containsKey("org.example.old"));
    }

    @Test
    public void aDamagedRecordIsIgnored() {
        prefs.edit().putString(Habits.KEY, "not a time\nx 1").commit();
        assertTrue(Habits.scores(prefs).isEmpty());
        prefs.edit().putString(Habits.KEY, now + "\nbroken\nx y\nok 2").commit();
        assertEquals(1, Habits.scores(prefs).size());
        assertEquals(2.0, Habits.scores(prefs).get("ok"), 1e-9);
    }
}
