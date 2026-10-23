package com.arzaroth.lumen;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Canvas;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class DateViewTest {
    private final Canvas canvas = Robo.canvas(900, 200);

    private DateView date(boolean upper) {
        DateView v = new DateView(Robo.context(), 30f, 400, 0xB3FFFFFF, upper ? 0.08f : 0f, upper);
        v.setAccent(0xFFE53935);
        return v;
    }

    private void run(DateView v, float seconds) {
        int n = Math.round(seconds * 30);
        for (int i = 0; i < n; i++) {
            Robo.idle(33);
            Robo.wrap(v);
            v.draw(canvas);
        }
    }

    @Test
    public void showsTextAndStaysStillWhenStopped() {
        DateView v = date(false);
        Robo.wrap(v);
        v.draw(canvas);
        v.setText("samedi 24 octobre");
        v.setText("samedi 24 octobre");
        Robo.wrap(v);
        v.draw(canvas);
        assertTrue(v.getMeasuredWidth() > 2 * v.inset());
        assertTrue(!v.hasOverlappingRendering());
    }

    @Test
    public void theTurnOfTheDateDissolvesIntoTheNext() {
        DateView v = date(true);
        v.setText("Saturday 24 October");
        v.setRunning(true);
        v.setRunning(true);
        run(v, 0.3f);
        v.setText("Sunday 25 October with a much longer tail that has to be clipped to fit the view");
        run(v, 2f);
        v.setRunning(false);
        v.setText("Monday 26 October");
        v.draw(canvas);
    }

    @Test
    public void introTypesItIn() {
        DateView v = date(false);
        v.setText("samedi 24 octobre");
        v.setRunning(true);
        v.intro();
        run(v, 2f);
    }

    @Test
    public void everyMomentPlaysAndTheyChooseThemselves() {
        DateView v = date(false);
        v.setText("samedi 24 octobre");
        v.setRunning(true);
        run(v, 0.2f);
        for (int m = 0; m < 3; m++) {
            Robo.set(v, "nextMoment", 0f);
            Robo.set(v, "lastMoment", -1);
            run(v, 0.05f);
            Robo.set(v, "moment", m);
            Robo.set(v, "momentT", 0f);
            if (m == 2) {
                Robo.set(v, "nextMoment", 0f);
                Robo.set(v, "moment", -1);
                Robo.set(v, "lastMoment", -1);
                for (int tries = 0; tries < 50 && (int) Robo.get(v, "moment") != 2; tries++) {
                    Robo.set(v, "moment", -1);
                    Robo.set(v, "lastMoment", tries % 2);
                    Robo.set(v, "nextMoment", 0f);
                    run(v, 0.04f);
                }
            }
            run(v, 3f);
            assertEquals(-1, (int) Robo.get(v, "moment"));
        }
    }
}
