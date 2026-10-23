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

import java.util.ArrayList;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class MenuTest {
    private Menu menu;
    private Canvas canvas;
    private final ArrayList<String> log = new ArrayList<>();

    @Before
    public void setUp() {
        menu = new Menu(Robo.context(), 1f);
        canvas = Robo.canvas(900, 900);
    }

    private void draw() {
        Robo.wrap(menu);
        menu.draw(canvas);
    }

    private Menu.Page page() {
        int[] chosen = new int[1];
        return new Menu.Page("Things", 500)
                .add(Menu.Row.note("A note first"))
                .add(Menu.Row.action("Act", () -> log.add("act")))
                .add(Menu.Row.choice("Pick", new String[] {"One", "Two", "Three"}, 7, v -> log.add("pick " + v)))
                .add(Menu.Row.sub("Deeper", () -> new Menu.Page("Deeper", 400).add(Menu.Row.action("Inner", () -> log.add("inner")))))
                .add(Menu.Row.note("A note last"));
    }

    @Test
    public void measuresBeforeAnythingIsOpen() {
        draw();
        assertFalse(menu.isOpen());
        assertEquals(View.GONE, menu.getVisibility());
    }

    @Test
    public void rowsSkipNotesAndCycleChoices() {
        menu.open(page());
        Robo.idle(300);
        draw();
        assertTrue(menu.isOpen());
        menu.move(-1);
        assertEquals(3, ((Menu.Page) Robo.call(menu, "top")).selected);
        menu.move(1);
        assertEquals(1, ((Menu.Page) Robo.call(menu, "top")).selected);
        menu.activate();
        assertEquals("act", log.get(0));
        menu.side(1);
        menu.move(1);
        draw();
        menu.side(-1);
        menu.side(1);
        menu.activate();
        assertEquals("pick 1", log.get(1));
        assertEquals("pick 0", log.get(log.size() - 1));
        menu.move(1);
        menu.activate();
        Robo.idle(100);
        draw();
        Robo.idle(300);
        menu.activate();
        assertEquals("inner", log.get(log.size() - 1));
        assertTrue(menu.pop());
        Robo.idle(300);
        draw();
        assertFalse(menu.pop());
        menu.close();
        Robo.idle(300);
        assertFalse(menu.isOpen());
        assertEquals(View.GONE, menu.getVisibility());
    }

    @Test
    public void questionsMoveBetweenTwoButtons() {
        menu.open(new Menu.Page("Empty", 300));
        draw();
        menu.activate();
        menu.side(1);
        menu.move(1);
        menu.push(Menu.Page.ask("Sure?", "A question long enough to wrap onto a second line of the panel, maybe a third.",
                "No", () -> log.add("no"), "Yes", () -> log.add("yes"), 0));
        Robo.idle(300);
        draw();
        menu.move(1);
        menu.side(-1);
        menu.side(1);
        menu.side(1);
        draw();
        menu.activate();
        assertEquals("yes", log.get(0));
        menu.replace(Menu.Page.ask("Again?", "Short.", "A", () -> log.add("a"), "B", () -> log.add("b"), 1));
        menu.side(-1);
        menu.activate();
        assertEquals("a", log.get(1));
    }

    @Test
    public void rightActsAsOkAndLeftAsBack() {
        menu.open(page());
        menu.right();
        assertEquals("act", log.get(0));
        menu.move(1);
        menu.right();
        menu.left();
        assertEquals("pick 0", log.get(1));
        assertEquals("pick 2", log.get(2));
        menu.move(1);
        menu.right();
        menu.right();
        assertEquals("inner", log.get(3));
        assertTrue(menu.left());
        assertFalse(menu.left());
        menu.push(Menu.Page.ask("Sure?", "Really.", "No", () -> log.add("no"), "Yes", () -> log.add("yes"), 0));
        menu.right();
        assertEquals(1, ((Menu.Page) Robo.call(menu, "top")).selected);
        assertTrue(menu.left());
        assertEquals(0, ((Menu.Page) Robo.call(menu, "top")).selected);
        menu.open(new Menu.Page("Only notes", 300).add(Menu.Row.note("one")).add(Menu.Row.note("two")));
        menu.move(1);
        menu.right();
        assertFalse(menu.left());
    }

    @Test
    public void closingThenReopeningCancelsTheFade() {
        menu.open(page());
        menu.close();
        menu.open(page());
        Robo.idle(400);
        assertEquals(View.VISIBLE, menu.getVisibility());
        menu.open(new Menu.Page("Only notes", 300).add(Menu.Row.note("nothing to pick")));
        menu.activate();
        menu.move(1);
        draw();
    }
}
