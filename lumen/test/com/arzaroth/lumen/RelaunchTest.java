package com.arzaroth.lumen;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class RelaunchTest {
    private static final long NOW = 1_000_000_000L;
    private SharedPreferences prefs;
    private Application app;

    @Before
    public void setUp() {
        app = (Application) Robo.context();
        prefs = app.getSharedPreferences("lumen", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        Wall.clock = () -> NOW;
    }

    @After
    public void tearDown() {
        Wall.clock = System::currentTimeMillis;
    }

    private void replaced() {
        new Relaunch().onReceive(app, new Intent(Intent.ACTION_MY_PACKAGE_REPLACED));
    }

    @Test
    public void theNewLumenComesBackAsSoonAsItIsInPlace() {
        replaced();
        assertNull(shadowOf(app).getNextStartedActivity());
        prefs.edit().putLong(Prefs.RELAUNCH, NOW - 5_000).commit();
        new Relaunch().onReceive(app, new Intent(Intent.ACTION_BOOT_COMPLETED));
        assertNull(shadowOf(app).getNextStartedActivity());
        replaced();
        Intent home = shadowOf(app).getNextStartedActivity();
        assertEquals(LumenActivity.class.getName(), home.getComponent().getClassName());
        assertTrue((home.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
        assertTrue(prefs.contains(Prefs.RELAUNCH));
    }

    @Test
    public void anOldMarkIsForgottenWithoutAnything() {
        prefs.edit().putLong(Prefs.RELAUNCH, NOW - Relaunch.WINDOW_MS).commit();
        replaced();
        assertNull(shadowOf(app).getNextStartedActivity());
        assertFalse(prefs.contains(Prefs.RELAUNCH));
    }

    @Test
    public void aRefusedStartIsNoCrash() {
        prefs.edit().putLong(Prefs.RELAUNCH, NOW).commit();
        Context refusing = new android.content.ContextWrapper(app) {
            @Override
            public void startActivity(Intent intent) {
                throw new SecurityException("background activity start");
            }
        };
        Relaunch.home(refusing, true);
        assertFalse(prefs.contains(Prefs.RELAUNCH));
    }
}
