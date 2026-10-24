package com.arzaroth.lumen;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowNetworkCapabilities;

import java.util.ArrayList;
import java.util.Calendar;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w960dp-h540dp-land-mdpi")
public class ActivityTest {
    private Application app;
    private SharedPreferences prefs;

    @Before
    public void setUp() {
        app = (Application) Robo.context();
        prefs = app.getSharedPreferences("lumen", Context.MODE_PRIVATE);
        prefs.edit().clear().putInt(Prefs.INTRO, 1).commit();
    }

    @After
    public void tearDown() {
        Wall.clock = System::currentTimeMillis;
    }

    static void apps(int n) {
        String[] pkgs = new String[n];
        for (int i = 0; i < n; i++) pkgs[i] = i == 0 ? "com.netflix.ninja" : "org.example.app" + i;
        AppsTest.install(pkgs);
    }

    static ActivityController<LumenActivity> launch(Intent intent) {
        ActivityController<LumenActivity> c = intent == null ? Robolectric.buildActivity(LumenActivity.class)
                : Robolectric.buildActivity(LumenActivity.class, intent);
        c.setup();
        waitForApps(c.get());
        return c;
    }

    static void waitForApps(LumenActivity a) {
        for (int i = 0; i < 300; i++) {
            ShadowLooper.idleMainLooper();
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (i > 40 && !(boolean) Robo.get(a, "firstLayout")) break;
        }
        Robo.idle(800);
    }

    static void key(LumenActivity a, int code) {
        a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, code));
        a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, code));
        Robo.idle(60);
    }

    static void longPress(LumenActivity a) {
        long t = SystemClock.uptimeMillis();
        a.dispatchKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 0));
        a.dispatchKeyEvent(new KeyEvent(t, t + 100, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 1));
        a.dispatchKeyEvent(new KeyEvent(t, t + 700, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 2));
        a.dispatchKeyEvent(new KeyEvent(t, t + 750, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 3));
        a.dispatchKeyEvent(new KeyEvent(t, t + 800, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, 0));
        Robo.idle(60);
    }

    private Intent started() {
        return shadowOf(app).getNextStartedActivity();
    }

    @Test
    public void walksTheDockTheGridAndTheTopBar() {
        apps(13);
        LumenActivity a = launch(null).get();
        assertEquals(13, ((ArrayList<?>) Robo.get(a, "tiles")).size());
        key(a, KeyEvent.KEYCODE_DPAD_LEFT);
        for (int i = 0; i < 5; i++) key(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        for (int i = 0; i < 4; i++) key(a, KeyEvent.KEYCODE_DPAD_DOWN);
        key(a, KeyEvent.KEYCODE_DPAD_UP);
        key(a, KeyEvent.KEYCODE_BACK);
        key(a, KeyEvent.KEYCODE_DPAD_UP);
        assertEquals(0, (int) Robo.get(a, "zone"));
        key(a, KeyEvent.KEYCODE_DPAD_LEFT);
        for (int i = 0; i < 3; i++) key(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        key(a, KeyEvent.KEYCODE_DPAD_UP);
        key(a, KeyEvent.KEYCODE_DPAD_DOWN);
        key(a, KeyEvent.KEYCODE_DPAD_UP);
        key(a, KeyEvent.KEYCODE_BACK);
        key(a, KeyEvent.KEYCODE_BACK);
        a.onWindowFocusChanged(true);
        a.onWindowFocusChanged(false);
        assertFalse(a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A)));
        KeyEvent cancelled = KeyEvent.changeFlags(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK), KeyEvent.FLAG_CANCELED);
        a.dispatchKeyEvent(cancelled);
        a.dispatchKeyEvent(KeyEvent.changeFlags(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER), KeyEvent.FLAG_CANCELED));
    }

    @Test
    public void pillsOpenTheirMenusAndSettings() {
        apps(3);
        LumenActivity a = launch(null).get();
        key(a, KeyEvent.KEYCODE_DPAD_UP);
        key(a, KeyEvent.KEYCODE_DPAD_CENTER);
        Menu menu = Robo.get(a, "menu");
        assertTrue(menu.isOpen());
        key(a, KeyEvent.KEYCODE_DPAD_DOWN);
        key(a, KeyEvent.KEYCODE_DPAD_UP);
        key(a, KeyEvent.KEYCODE_DPAD_LEFT);
        assertFalse(menu.isOpen());
        key(a, KeyEvent.KEYCODE_DPAD_CENTER);
        key(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        assertTrue(menu.isOpen());
        key(a, KeyEvent.KEYCODE_DPAD_LEFT);
        assertFalse(menu.isOpen());
        key(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        key(a, KeyEvent.KEYCODE_DPAD_CENTER);
        assertEquals(Settings.ACTION_WIFI_SETTINGS, started().getAction());
        shadowOf(app).checkActivities(true);
        key(a, KeyEvent.KEYCODE_DPAD_CENTER);
        shadowOf(app).checkActivities(false);
        key(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        key(a, KeyEvent.KEYCODE_DPAD_CENTER);
        assertTrue(menu.isOpen());
        key(a, KeyEvent.KEYCODE_DPAD_CENTER);
        assertEquals(Settings.ACTION_SETTINGS, started().getAction());
        key(a, KeyEvent.KEYCODE_DPAD_CENTER);
        key(a, KeyEvent.KEYCODE_DPAD_DOWN);
        key(a, KeyEvent.KEYCODE_DPAD_CENTER);
        key(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        key(a, KeyEvent.KEYCODE_BACK);
        key(a, KeyEvent.KEYCODE_BACK);
        assertTrue(menu.isOpen());
        key(a, KeyEvent.KEYCODE_BACK);
        assertFalse(menu.isOpen());
        a.resetOrder();
        waitForApps(a);
        a.playIntro();
        Robo.idle(4000);
        a.playIntro();
        a.openSystemSettings();
        shadowOf(app).checkActivities(true);
        a.openSystemSettings();
    }

    @Test
    public void theSkyLooksTowardTheFocusedColumn() {
        apps(8);
        LumenActivity a = launch(null).get();
        Cosmos cosmos = Robo.get(Robo.get(a, "stage"), "cosmos");
        Robo.set(a, "index", 0);
        Robo.call(a, "applyFocus", false);
        assertEquals(-1f, Robo.<Float>get(cosmos, "parTarget"), 0f);
        for (int i = 0; i < 4; i++) key(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        assertEquals(1f, Robo.<Float>get(cosmos, "parTarget"), 0f);
        assertEquals(0f, Robo.<Float>get(cosmos, "parYTarget"), 0f);
        View more = Robo.get(a, "more");
        assertTrue(Robo.<Boolean>get(a, "moreShown"));
        key(a, KeyEvent.KEYCODE_DPAD_DOWN);
        assertEquals(1f, Robo.<Float>get(cosmos, "parYTarget"), 0f);
        assertFalse(Robo.<Boolean>get(a, "moreShown"));
        Robo.idle(600);
        assertEquals(0f, more.getAlpha(), 0f);
        key(a, KeyEvent.KEYCODE_DPAD_UP);
        Robo.idle(1000);
        assertEquals(1f, more.getAlpha(), 0.01f);
        a.getSharedPreferences("lumen", android.content.Context.MODE_PRIVATE).edit().putInt(Prefs.PARALLAX, 1).commit();
        a.applySettings();
        assertEquals(0f, Robo.<Float>get(cosmos, "parTarget"), 0f);
        assertEquals(0f, (float) Robo.<Float>call(a, "parallaxOf", 3), 0f);
    }

    @Test
    public void theUpdateTakesTheScreenWhileItRuns() {
        apps(8);
        LumenActivity a = launch(null).get();
        Menu menu = Robo.get(a, "menu");
        Ascent ascent = Robo.get(a, "ascent");
        key(a, KeyEvent.KEYCODE_DPAD_UP);
        key(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        key(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        key(a, KeyEvent.KEYCODE_DPAD_CENTER);
        assertTrue(menu.isOpen());
        a.showUpdate(Updater.State.CHECKING, null, 0);
        assertTrue(menu.isOpen());
        a.showUpdate(Updater.State.DOWNLOADING, "9.0.0", 10);
        assertFalse(menu.isOpen());
        assertTrue(ascent.shown());
        int focused = Robo.get(a, "index");
        key(a, KeyEvent.KEYCODE_DPAD_DOWN);
        key(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        assertEquals(focused, (int) Robo.<Integer>get(a, "index"));
        a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP));
        key(a, KeyEvent.KEYCODE_BACK);
        assertEquals(Ascent.Phase.GATHER, ascent.phase());
        a.showUpdate(Updater.State.INSTALLING, "9.0.0", 100);
        key(a, KeyEvent.KEYCODE_ESCAPE);
        assertEquals(Ascent.Phase.FADE, ascent.phase());
    }

    @Test
    public void tilesLaunchAndRearrange() {
        apps(8);
        LumenActivity a = launch(null).get();
        key(a, KeyEvent.KEYCODE_DPAD_CENTER);
        assertNotNull(started());
        shadowOf(app).checkActivities(true);
        key(a, KeyEvent.KEYCODE_DPAD_CENTER);
        shadowOf(app).checkActivities(false);
        longPress(a);
        assertTrue(Robo.<Boolean>get(a, "moving"));
        key(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        key(a, KeyEvent.KEYCODE_DPAD_DOWN);
        key(a, KeyEvent.KEYCODE_DPAD_UP);
        key(a, KeyEvent.KEYCODE_DPAD_UP);
        key(a, KeyEvent.KEYCODE_DPAD_CENTER);
        assertFalse(Robo.<Boolean>get(a, "moving"));
        key(a, KeyEvent.KEYCODE_MENU);
        key(a, KeyEvent.KEYCODE_MENU);
        key(a, KeyEvent.KEYCODE_MENU);
        key(a, KeyEvent.KEYCODE_BACK);
        key(a, KeyEvent.KEYCODE_MENU);
        String order = prefs.getString("order", "");
        assertTrue(order.contains("com.netflix.ninja"));
        a.onNewIntent(new Intent(Intent.ACTION_MAIN));
        a.onNewIntent(new Intent(LumenActivity.ACTION_INPUTS));
        key(a, KeyEvent.KEYCODE_DPAD_CENTER);
        a.onNewIntent(new Intent(Intent.ACTION_MAIN));
        key(a, KeyEvent.KEYCODE_MENU);
        a.onNewIntent(new Intent(Intent.ACTION_MAIN));
        key(a, KeyEvent.KEYCODE_MENU);
        app.sendBroadcast(new Intent(Intent.ACTION_TIME_TICK));
        Intent added = new Intent(Intent.ACTION_PACKAGE_ADDED, android.net.Uri.parse("package:org.example.new"));
        app.sendBroadcast(added);
        Robo.idle(600);
        waitForApps(a);
    }

    @Test
    public void noAppsStillNavigates() {
        LumenActivity a = launch(null).get();
        key(a, KeyEvent.KEYCODE_DPAD_DOWN);
        key(a, KeyEvent.KEYCODE_DPAD_UP);
        key(a, KeyEvent.KEYCODE_DPAD_DOWN);
        key(a, KeyEvent.KEYCODE_BACK);
        key(a, KeyEvent.KEYCODE_MENU);
        longPress(a);
        Robo.call(a, "startMoving");
        Robo.set(a, "zone", 1);
        Robo.call(a, "activate");
        key(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        key(a, KeyEvent.KEYCODE_BACK);
    }

    @Test
    public void inputsIntentAndLifecycle() {
        apps(2);
        prefs.edit().putString("focus", "org.example.app2/.Main").commit();
        ActivityController<LumenActivity> c = launch(new Intent(LumenActivity.ACTION_INPUTS));
        LumenActivity a = c.get();
        assertTrue(((Menu) Robo.get(a, "menu")).isOpen());
        c.pause().resume();
        longPress(a);
        key(a, KeyEvent.KEYCODE_DPAD_DOWN);
        longPress(a);
        c.pause().stop().start().resume();
        Robo.call(a, "openInputs");
        c.pause().stop().destroy();
    }

    @Test
    public void settingsChangeTheHomeScreen() {
        apps(2);
        LumenActivity a = launch(null).get();
        for (int backdrop = 0; backdrop < 3; backdrop++) {
            prefs.edit().putInt(Prefs.BACKDROP, backdrop).putInt(Prefs.CLOCK, backdrop).putInt(Prefs.GREETING, backdrop % 2)
                    .putInt(Prefs.LOGO, backdrop % 2).putInt(Prefs.TURN, backdrop % 2).putInt(Prefs.PACE, backdrop).commit();
            a.applySettings();
        }
        Calendar c = Calendar.getInstance();
        for (int hour : new int[] {3, 9, 15, 21}) {
            c.set(Calendar.HOUR_OF_DAY, hour);
            long at = c.getTimeInMillis();
            Wall.clock = () -> at;
            Robo.call(a, "updateClock");
        }
    }

    @Test
    public void theIntroPlaysOncePerBoot() {
        prefs.edit().putInt(Prefs.INTRO, 0).commit();
        Settings.Global.putInt(app.getContentResolver(), Settings.Global.BOOT_COUNT, 7);
        apps(2);
        launch(null);
        assertEquals(7L, prefs.getLong(Prefs.INTRO_BOOT, -1));
        launch(null);
        Settings.Global.putString(app.getContentResolver(), Settings.Global.BOOT_COUNT, null);
        launch(null);
        assertTrue(prefs.getLong(Prefs.INTRO_BOOT, -1) != 7L);
    }

    @Test
    public void theNetworkPillNamesTheConnection() {
        apps(1);
        LumenActivity a = launch(null).get();
        NetworkCapabilities nc = ShadowNetworkCapabilities.newInstance();
        shadowOf(nc).addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET);
        Robo.call(a, "showNetwork", nc);
        Robo.call(a, "showNetwork", (Object) null);
        NetworkCapabilities cell = ShadowNetworkCapabilities.newInstance();
        shadowOf(cell).addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR);
        Robo.call(a, "showNetwork", cell);
        NetworkCapabilities wifi = ShadowNetworkCapabilities.newInstance();
        shadowOf(wifi).addTransportType(NetworkCapabilities.TRANSPORT_WIFI);
        WifiManager wm = app.getSystemService(WifiManager.class);
        for (String ssid : new String[] {"\"Home\"", WifiManager.UNKNOWN_SSID, "<unknown ssid>", "", "A network name far too long to show"}) {
            WifiInfo info = org.robolectric.shadows.ShadowWifiInfo.newInstance();
            shadowOf(info).setSSID(ssid);
            shadowOf(wm).setConnectionInfo(info);
            shadowOf(wifi).setTransportInfo(info);
            Robo.call(a, "showNetwork", wifi);
        }
        shadowOf(wm).setConnectionInfo(null);
        NetworkCapabilities bare = ShadowNetworkCapabilities.newInstance();
        shadowOf(bare).addTransportType(NetworkCapabilities.TRANSPORT_WIFI);
        Robo.call(a, "showNetwork", bare);
        ConnectivityManager cm = app.getSystemService(ConnectivityManager.class);
        for (ConnectivityManager.NetworkCallback cb : shadowOf(cm).getNetworkCallbacks()) {
            cb.onCapabilitiesChanged(null, nc);
            cb.onLost(null);
        }
    }
}
