package com.arzaroth.lumen;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.graphics.Canvas;
import android.provider.Settings;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SettingsTest implements LumenSettings.Host {
    private Menu menu;
    private SharedPreferences prefs;
    private LumenSettings settings;
    private final ArrayList<String> log = new ArrayList<>();
    private final ArrayList<Updater.State> forged = new ArrayList<>();
    private Canvas canvas;

    @Override
    public void applySettings() {
        log.add("apply");
    }

    @Override
    public void resetOrder() {
        log.add("reset");
    }

    @Override
    public void openSystemSettings() {
        log.add("system");
    }

    @Override
    public void playIntro() {
        log.add("intro");
    }

    @Override
    public void forge(Updater.State state, String latest, int progress) {
        forged.add(state);
    }

    @Override
    public Menu menu() {
        return menu;
    }

    @Before
    public void setUp() {
        Context c = Robo.context();
        menu = new Menu(c, 1f);
        prefs = c.getSharedPreferences("lumen", Context.MODE_PRIVATE);
        prefs.edit().clear().apply();
        Updater.releases = "http://127.0.0.1:9/latest";
        shadowOf(c.getPackageManager()).setCanRequestPackageInstalls(false);
        settings = new LumenSettings(c, this, prefs);
        canvas = Robo.canvas(900, 1100);
    }

    private void draw() {
        Robo.idle(300);
        Robo.wrap(menu);
        menu.draw(canvas);
    }

    private void down(int n) {
        for (int i = 0; i < n; i++) menu.move(1);
    }

    private void installDoot() {
        PackageInfo info = new PackageInfo();
        info.packageName = "org.doot.android";
        shadowOf(Robo.context().getPackageManager()).installPackage(info);
    }

    private Menu.Page spooky() {
        menu.open(settings.root());
        menu.move(1);
        menu.activate();
        down(4);
        menu.activate();
        menu.activate();
        menu.activate();
        draw();
        return Robo.call(menu, "top");
    }

    @Test
    public void rootOffersSystemAndLumenSettings() {
        menu.open(settings.root());
        draw();
        menu.activate();
        assertEquals("system", log.get(0));
        menu.move(1);
        menu.activate();
        draw();
        assertEquals(5, ((Menu.Page) Robo.call(menu, "top")).rows.size());

        menu.activate();
        assertEquals("Sky", ((Menu.Page) Robo.call(menu, "top")).title);
        menu.side(1);
        assertEquals(1, prefs.getInt(Prefs.BACKDROP, 0));
        assertEquals("apply", log.get(1));
        down(3);
        menu.side(1);
        assertEquals(1, prefs.getInt(Prefs.TURN, 0));
        menu.move(1);
        menu.side(1);
        assertEquals(1, prefs.getInt(Prefs.PARALLAX, 0));
        menu.pop();

        menu.move(1);
        menu.activate();
        assertEquals("Home screen", ((Menu.Page) Robo.call(menu, "top")).title);
        down(3);
        menu.activate();
        draw();
        menu.activate();
        assertFalse(log.contains("reset"));
        menu.activate();
        menu.side(1);
        menu.activate();
        assertEquals("reset", log.get(log.size() - 1));
        menu.pop();

        menu.move(1);
        menu.activate();
        assertEquals("Remote", ((Menu.Page) Robo.call(menu, "top")).title);
        menu.move(1);
        menu.side(1);
        assertEquals(1, prefs.getInt(Prefs.YOUTUBE, 0));
        menu.pop();

        menu.move(1);
        menu.activate();
        assertEquals("Boot intro", ((Menu.Page) Robo.call(menu, "top")).title);
        menu.move(1);
        menu.activate();
        assertEquals("intro", log.get(log.size() - 1));
    }

    @Test
    public void spookyDootWhenDootIsMissing() {
        Menu.Page p = spooky();
        assertEquals("doot is not installed", p.rows.get(0).label);
    }

    @Test
    public void spookyDootWithoutTheDumpPermission() {
        installDoot();
        shadowOf((Application) Robo.context()).denyPermissions(Manifest.permission.DUMP);
        assertEquals("Run scripts/install.sh to reach doot", spooky().rows.get(0).label);
    }

    @Test
    public void disablingDootsTakesTwoQuestionsAndEndsWithADoot() {
        installDoot();
        shadowOf((Application) Robo.context()).grantPermissions(Manifest.permission.DUMP);
        assertEquals("Disable doots", spooky().rows.get(0).label);
        menu.activate();
        draw();
        menu.activate();
        assertEquals("Disable doots", ((Menu.Page) Robo.call(menu, "top")).rows.get(0).label);
        menu.activate();
        menu.side(1);
        menu.activate();
        Menu.Page sure = Robo.call(menu, "top");
        assertEquals("Are you sure?", sure.title);
        assertEquals(1, sure.selected);
        menu.activate();
        assertEquals("Disable doots", ((Menu.Page) Robo.call(menu, "top")).rows.get(0).label);
        menu.activate();
        menu.side(1);
        menu.activate();
        menu.side(-1);
        menu.activate();
        assertTrue(prefs.getBoolean("doots_off", false));
        Robo.idle(9000);
        List<Intent> sent = shadowOf((Application) Robo.context()).getBroadcastIntents();
        assertEquals(2, sent.size());
        assertEquals("--once", sent.get(0).getStringArrayExtra("args")[0]);
        assertEquals("--snooze", sent.get(1).getStringArrayExtra("args")[0]);
        Menu.Page enable = Robo.call(menu, "top");
        assertEquals("Enable doots", enable.rows.get(0).label);
        menu.activate();
        assertFalse(prefs.getBoolean("doots_off", true));
        assertEquals("--resume", shadowOf((Application) Robo.context()).getBroadcastIntents().get(2).getStringArrayExtra("args")[0]);
    }

    private Menu.Page advanced() {
        menu.open(settings.root());
        menu.move(1);
        menu.activate();
        down(4);
        menu.activate();
        return Robo.call(menu, "top");
    }

    @Test
    public void advancedChecksForUpdatesAboveTheVersion() throws Exception {
        try (Releases github = new Releases("v9.0.0")) {
            Menu.Page advanced = advanced();
            assertEquals("Checking for updates\u2026", advanced.rows.get(1).label);
            assertTrue(advanced.rows.get(2).label.startsWith("Lumen "));
            menu.move(1);
            UpdaterTest.until(() -> ((Menu.Page) Robo.call(menu, "top")).rows.get(1).label.equals("Update to 9.0.0"));
            assertEquals(1, ((Menu.Page) Robo.call(menu, "top")).selected);
            draw();
            shadowOf((Application) Robo.context()).checkActivities(true);
            menu.activate();
            assertEquals("Run scripts/install.sh to allow updates", ((Menu.Page) Robo.call(menu, "top")).rows.get(1).label);
            shadowOf((Application) Robo.context()).checkActivities(false);
            menu.activate();
            assertEquals(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, shadowOf((Application) Robo.context()).getNextStartedActivity().getAction());
            shadowOf(Robo.context().getPackageManager()).setCanRequestPackageInstalls(true);
            menu.activate();
            assertTrue(((Menu.Page) Robo.call(menu, "top")).rows.get(1).label.startsWith("Downloading 9.0.0"));
            UpdaterTest.until(() -> ((Menu.Page) Robo.call(menu, "top")).rows.get(1).label.equals("Installing 9.0.0\u2026"));
            github.latestStatus = 500;
            menu.pop();
            menu.activate();
            menu.move(1);
            assertEquals("Installing 9.0.0\u2026", ((Menu.Page) Robo.call(menu, "top")).rows.get(1).label);
            UpdaterTest.report(android.content.pm.PackageInstaller.STATUS_FAILURE, null);
            assertEquals("Couldn't update, try again", ((Menu.Page) Robo.call(menu, "top")).rows.get(1).label);
            menu.activate();
            UpdaterTest.until(() -> ((Menu.Page) Robo.call(menu, "top")).rows.get(1).label.equals("Couldn't update, try again"));
            github.latestStatus = 200;
            github.release("1.0.0", "lumen.apk");
            menu.activate();
            UpdaterTest.until(() -> ((Menu.Page) Robo.call(menu, "top")).rows.get(1).label.equals("Lumen is up to date"));
            menu.pop();
            menu.activate();
            assertEquals("Checking for updates\u2026", ((Menu.Page) Robo.call(menu, "top")).rows.get(1).label);
            UpdaterTest.until(() -> ((Menu.Page) Robo.call(menu, "top")).rows.get(1).label.equals("Lumen is up to date"));
            menu.close();
            Menu.Page shown = Robo.call(menu, "top");
            Robo.call(settings, "refresh");
            assertTrue(shown == Robo.call(menu, "top"));
        }
    }

    @Test
    public void inputsListLiveTvFirst() {
        Menu.Page none = Inputs.page(Robo.context(), i -> log.add("launch"));
        assertEquals("No inputs found", none.rows.get(0).label);
        shadowOf(Robo.context().getPackageManager()).addActivityIfNotPresent(new ComponentName("com.android.tv", "com.android.tv.Live"));
        Intent leanback = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER).setPackage("com.android.tv");
        shadowOf(Robo.context().getPackageManager()).addResolveInfoForIntent(leanback, AppsTest.leanback("com.android.tv", "Live"));
        Menu.Page live = Inputs.page(Robo.context(), i -> log.add("launch " + i.getPackage()));
        assertEquals("Live TV", live.rows.get(0).label);
        live.rows.get(0).action.run();
        assertTrue(log.get(0).startsWith("launch"));
    }
}
