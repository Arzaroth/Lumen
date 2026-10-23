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
        down(12);
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
        menu.side(1);
        assertEquals(1, prefs.getInt(Prefs.BACKDROP, 0));
        assertEquals("apply", log.get(1));
        down(10);
        menu.activate();
        assertEquals("intro", log.get(log.size() - 1));
        menu.move(1);
        menu.activate();
        draw();
        menu.activate();
        assertFalse(log.contains("reset"));
        menu.activate();
        menu.side(1);
        menu.activate();
        assertEquals("reset", log.get(log.size() - 1));
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

    @Test
    public void advancedShowsTheVersion() {
        menu.open(settings.root());
        menu.move(1);
        menu.activate();
        down(12);
        menu.activate();
        Menu.Page advanced = Robo.call(menu, "top");
        assertTrue(advanced.rows.get(2).label.startsWith("Lumen "));
    }

    @Test
    public void versionIsBlankWhenThePackageCannotBeRead() {
        Context missing = new android.content.ContextWrapper(Robo.context()) {
            @Override
            public String getPackageName() {
                return "org.missing.lumen";
            }
        };
        assertEquals("", Updater.installed(missing));
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
