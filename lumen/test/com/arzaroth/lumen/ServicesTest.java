package com.arzaroth.lumen;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ServicesTest {
    private Application app;

    @Before
    public void setUp() {
        app = (Application) Robo.context();
        app.getSharedPreferences("lumen", Context.MODE_PRIVATE).edit().clear().commit();
    }

    @After
    public void tearDown() {
        Wall.clock = System::currentTimeMillis;
    }

    private static KeyEvent down(int code) {
        return new KeyEvent(KeyEvent.ACTION_DOWN, code);
    }

    private Intent started() {
        return shadowOf(app).getNextStartedActivity();
    }

    @Test
    public void keysFollowHomeAndTheSettings() {
        LumenKeys keys = org.mockito.Mockito.spy(Robolectric.setupService(LumenKeys.class));
        AccessibilityServiceInfo[] info = {new AccessibilityServiceInfo()};
        org.mockito.Mockito.doAnswer(a -> info[0]).when(keys).getServiceInfo();
        org.mockito.Mockito.doAnswer(a -> {
            info[0] = a.getArgument(0);
            return null;
        }).when(keys).setServiceInfo(org.mockito.ArgumentMatchers.any());
        LumenKeys.setHomeInFront(true);
        Robo.call(keys, "onServiceConnected");
        LumenKeys.setHomeInFront(true);
        assertTrue((keys.getServiceInfo().flags & AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS) != 0);
        LumenKeys.setHomeInFront(false);
        assertEquals(0, keys.getServiceInfo().flags & AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS);
        app.getSharedPreferences("lumen", Context.MODE_PRIVATE).edit().putInt(Prefs.KEYS, 1).commit();
        LumenKeys.setHomeInFront(true);
        assertEquals(0, keys.getServiceInfo().flags & AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS);
        keys.onAccessibilityEvent(null);
        keys.onInterrupt();
        keys.onUnbind(new Intent());
        LumenKeys.setHomeInFront(true);
    }

    @Test
    public void keysBringHomeBackRightAfterAnUpdate() {
        android.content.SharedPreferences prefs = app.getSharedPreferences("lumen", android.content.Context.MODE_PRIVATE);
        Wall.clock = () -> 1_000_000_000L;
        LumenKeys keys = Robolectric.setupService(LumenKeys.class);
        Robo.call(keys, "onServiceConnected");
        assertNull(started());
        prefs.edit().putLong(Prefs.RELAUNCH, 1_000_000_000L - 60_000).commit();
        Robo.call(keys, "onServiceConnected");
        assertEquals(LumenActivity.class.getName(), started().getComponent().getClassName());
        assertFalse(prefs.contains(Prefs.RELAUNCH));
        prefs.edit().putLong(Prefs.RELAUNCH, 1_000_000_000L - 3_600_000).commit();
        Robo.call(keys, "onServiceConnected");
        assertNull(started());
        assertFalse(prefs.contains(Prefs.RELAUNCH));
    }

    @Test
    public void keysWithoutServiceInfoDoNothing() {
        LumenKeys keys = Robolectric.setupService(LumenKeys.class);
        Robo.call(keys, "onServiceConnected");
        LumenKeys.setHomeInFront(true);
        assertNull(keys.getServiceInfo());
    }

    @Test
    public void remoteButtonsOpenInputsAndApps() {
        LumenKeys keys = Robolectric.setupService(LumenKeys.class);
        assertTrue(Robo.<Boolean>call(keys, "onKeyEvent", down(KeyEvent.KEYCODE_TV_INPUT)));
        assertEquals(LumenActivity.ACTION_INPUTS, started().getAction());
        assertTrue(Robo.<Boolean>call(keys, "onKeyEvent", down(KeyEvent.KEYCODE_BUTTON_3)));
        assertEquals("market://details?id=com.google.android.youtube.tv", started().getDataString());
        app.getSharedPreferences("lumen", Context.MODE_PRIVATE).edit().putInt(Prefs.YOUTUBE, 1).commit();
        Robo.call(keys, "onKeyEvent", down(KeyEvent.KEYCODE_BUTTON_3));
        assertEquals("market://details?id=" + LumenKeys.SMARTTUBE, started().getDataString());
        Robo.call(keys, "onKeyEvent", down(KeyEvent.KEYCODE_BUTTON_4));
        assertNotNull(started());
        android.content.Intent netflix = new android.content.Intent(android.content.Intent.ACTION_MAIN)
                .addCategory(android.content.Intent.CATEGORY_LEANBACK_LAUNCHER).setPackage("com.netflix.ninja");
        shadowOf(app.getPackageManager()).addResolveInfoForIntent(netflix, AppsTest.leanback("com.netflix.ninja", "Netflix"));
        Robo.call(keys, "onKeyEvent", down(KeyEvent.KEYCODE_BUTTON_4));
        assertNotNull(started());
        assertTrue(Habits.scores(app.getSharedPreferences("lumen", Context.MODE_PRIVATE)).containsKey("com.netflix.ninja"));
        Robo.call(keys, "onKeyEvent", down(KeyEvent.KEYCODE_BUTTON_6));
        assertNotNull(started());
        assertTrue(Robo.<Boolean>call(keys, "onKeyEvent", new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_4)));
        assertNull(started());
        assertFalse(Robo.<Boolean>call(keys, "onKeyEvent", down(KeyEvent.KEYCODE_DPAD_UP)));
        shadowOf(app).checkActivities(true);
        Robo.call(keys, "onKeyEvent", down(KeyEvent.KEYCODE_BUTTON_6));
    }

    @Test
    public void installedAppsOpenDirectly() {
        AppsTest.install("com.netflix.ninja");
        android.content.pm.PackageManager pm = app.getPackageManager();
        Intent leanback = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER).setPackage("com.netflix.ninja");
        shadowOf(pm).addResolveInfoForIntent(leanback, AppsTest.leanback("com.netflix.ninja", "Netflix"));
        LumenKeys keys = Robolectric.setupService(LumenKeys.class);
        Robo.call(keys, "onKeyEvent", down(KeyEvent.KEYCODE_BUTTON_4));
        assertEquals("com.netflix.ninja", started().getComponent().getPackageName());
    }

    @Test
    public void theDreamShowsItsClock() throws Exception {
        LumenDream dream = Robolectric.setupService(LumenDream.class);
        Class<?> phone = Class.forName("com.android.internal.policy.PhoneWindow");
        Window window = (Window) phone.getConstructor(Context.class).newInstance(dream);
        android.view.WindowManager wm = (android.view.WindowManager) dream.getSystemService(Context.WINDOW_SERVICE);
        window.setWindowManager(wm, null, null);
        wm.addView(window.getDecorView(), window.getAttributes());
        Robo.set(dream, "mWindow", window);
        dream.onAttachedToWindow();
        assertNotNull(window.getDecorView());
    }

    @Test
    public void theClockFaceMovesEveryMinute() throws Exception {
        long[] now = {1_793_000_000_000L};
        Wall.clock = () -> now[0];
        Class<?> face = Class.forName("com.arzaroth.lumen.LumenDream$ClockFace");
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        View view = (View) face.getDeclaredConstructor(Context.class).newInstance(activity);
        activity.setContentView(view);
        Robo.idle(2000);
        for (int i = 0; i < 3; i++) {
            now[0] += 61_000;
            Robo.idle(61_000);
            Robo.idle(3000);
        }
        assertFalse(((View) Robo.get(view, "holder")).hasOverlappingRendering());
        ((android.view.ViewGroup) view.getParent()).removeView(view);
        Robo.idle(100);
        assertFalse(view.isAttachedToWindow());
    }
}
