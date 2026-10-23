package com.arzaroth.lumen;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Canvas;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Calendar;
import java.util.TimeZone;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ClockViewTest {
    private Canvas canvas;

    static long now;

    @Before
    public void setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        Wall.clock = () -> now;
        canvas = Robo.canvas(900, 400);
    }

    @After
    public void tearDown() {
        Wall.clock = System::currentTimeMillis;
    }

    static void at(int hour, int minute, int second, int millis) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.clear();
        c.set(2026, Calendar.OCTOBER, 24, hour, minute, second);
        now = c.getTimeInMillis() + millis;
    }

    private ClockView clock(boolean seconds) {
        ClockView v = new ClockView(Robo.context(), 60f, seconds);
        Robo.wrap(v);
        return v;
    }

    private void run(ClockView v, float seconds) {
        int n = Math.round(seconds * 30);
        for (int i = 0; i < n; i++) {
            Robo.idle(33);
            now += 33;
            Robo.wrap(v);
            v.draw(canvas);
        }
    }

    @Test
    public void staysStillWhenNotRunning() {
        at(9, 41, 10, 0);
        ClockView v = clock(true);
        v.draw(canvas);
        v.setRunning(false);
        assertTrue(v.inset() > 0);
        assertTrue(!v.hasOverlappingRendering());
        assertTrue(v.getMeasuredWidth() > 0);
    }

    @Test
    public void minuteTensAndHourChangesMorph() {
        at(12, 58, 59, 500);
        ClockView v = clock(true);
        v.setFormat(true);
        v.setAccent(0xFF2196F3);
        v.setRunning(true);
        v.setRunning(true);
        run(v, 3f);
        at(12, 59, 59, 600);
        run(v, 3f);
        at(13, 9, 59, 700);
        run(v, 3f);
        v.setRunning(false);
        v.draw(canvas);
    }

    @Test
    public void twelveHourWidthChangeAndFormatSwitch() {
        at(9, 59, 59, 700);
        ClockView v = clock(false);
        v.setFormat(false);
        v.setRunning(true);
        run(v, 2.5f);
        v.setFormat(true);
        v.setFormat(true);
        run(v, 2.5f);
        v.setFormat(false);
        run(v, 0.5f);
    }

    @Test
    public void introGathersTheDigitsFromStardust() {
        at(20, 15, 3, 0);
        ClockView v = clock(true);
        v.setRunning(true);
        run(v, 0.2f);
        v.intro();
        run(v, 2.5f);
    }

    @Test
    public void everyIdleMomentPlays() {
        at(20, 15, 10, 0);
        ClockView v = clock(true);
        v.setRunning(true);
        run(v, 0.5f);
        for (int e = 0; e < 6; e++) {
            Robo.set(v, "event", e);
            Robo.set(v, "eventT", 0f);
            at(20, 15, 10, 0);
            run(v, 3f);
            assertEquals(-1, (int) Robo.get(v, "event"));
        }
    }

    @Test
    public void momentsStartOnTheirOwnAndStopForAMorph() {
        at(20, 16, 5, 0);
        ClockView v = clock(true);
        v.setRunning(true);
        Robo.set(v, "nextEvent", 0f);
        run(v, 0.5f);
        assertTrue((int) Robo.get(v, "event") >= 0);
        at(20, 16, 59, 900);
        run(v, 0.5f);
        assertEquals(-1, (int) Robo.get(v, "event"));
        Robo.set(v, "nextEvent", 0f);
        Robo.set(v, "lastEvent", 2);
        at(20, 17, 57, 0);
        run(v, 0.3f);
        assertEquals(-1, (int) Robo.get(v, "event"));
    }
}
