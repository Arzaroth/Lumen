package com.arzaroth.lumen;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Locale;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class WeatherTest {
    private LocalWeb web;
    private SharedPreferences prefs;
    private final ArrayList<Weather.Now> seen = new ArrayList<>();

    static String current(int code, double temp, double clouds, double precipitation, double snowfall) {
        return "{\"current\":{\"temperature_2m\":" + temp + ",\"weather_code\":" + code + ",\"cloud_cover\":" + clouds
                + ",\"precipitation\":" + precipitation + ",\"snowfall\":" + snowfall + "}}";
    }

    @Before
    public void setUp() throws Exception {
        web = new LocalWeb();
        Weather.forecast = web.base + "/forecast";
        Place.lookup = web.base + "/where";
        Weather.every = 30 * 60 * 1000L;
        prefs = Robo.context().getSharedPreferences("lumen", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        shadowOf((Application) Robo.context()).denyPermissions(android.Manifest.permission.ACCESS_COARSE_LOCATION);
    }

    @After
    public void tearDown() throws Exception {
        web.close();
    }

    @Test
    public void readsTheSkyOutOfTheWeatherCodes() throws JSONException {
        Weather.Now clear = Weather.parse(current(0, 12.4, 5, 0, 0));
        assertEquals(0.05f, clear.clouds, 1e-6f);
        assertEquals(0f, clear.rain, 0f);
        assertEquals(0f, clear.snow, 0f);
        assertEquals(12.4f, clear.celsius, 1e-6f);
        assertEquals(1f, Weather.parse(current(63, 9, 100, 6, 0)).rain, 0f);
        assertEquals(0.15f, Weather.parse(current(51, 9, 100, 0.1, 0)).rain, 1e-6f);
        assertEquals(0.5f, Weather.parse(current(73, -2, 90, 0, 1)).snow, 1e-6f);
        assertEquals(0.5f, Weather.parse(current(85, -2, 90, 1, 0)).snow, 1e-6f);
        assertTrue(Weather.parse(current(95, 20, 100, 2, 0)).rain > 0f);
        assertEquals(0f, Weather.parse(current(3, 20, 100, 2, 0)).rain, 0f);
        assertEquals(1f, Weather.parse(current(3, 20, 150, 0, 0)).clouds, 0f);
    }

    @Test
    public void temperaturesReadTheLocalWay() {
        assertEquals("12°", Weather.temperature(12.4f, Locale.FRANCE));
        assertEquals("54°", Weather.temperature(12.4f, Locale.US));
        assertEquals("-3°", Weather.temperature(-2.6f, new Locale("en", "GB")));
    }

    @Test
    public void fetchesWhileWantedAndNoMoreOftenThanEveryHalfHour() {
        web.pages.put("/where", "{\"loc\":\"48.85,2.35\"}");
        web.pages.put("/forecast", current(61, 10, 80, 2, 0));
        Weather w = new Weather(Robo.context(), prefs, seen::add);
        w.setOn(true);
        UpdaterTest.until(() -> !seen.isEmpty());
        assertEquals(0.5f, seen.get(0).rain, 1e-6f);
        int hits = web.hits;
        w.setOn(true);
        w.setOn(false);
        w.setOn(true);
        Robo.idle(1000);
        assertEquals(hits, web.hits);
        w.setOn(false);
    }

    @Test
    public void nothingIsShownWhenThePlaceOrTheWeatherCannotBeFound() {
        Weather w = new Weather(Robo.context(), prefs, seen::add);
        w.setOn(true);
        UpdaterTest.until(() -> web.hits >= 1);
        Robo.idle(500);
        web.pages.put("/where", "{\"loc\":\"48.85,2.35\"}");
        Weather.every = 0;
        w.setOn(false);
        w.setOn(true);
        UpdaterTest.until(() -> web.hits >= 3);
        Robo.idle(500);
        w.setOn(false);
        assertTrue(seen.isEmpty());
    }

    @Test
    public void thePlaceComesFromTheTvTheCacheOrTheConnection() {
        assertNull(Place.find(Robo.context(), prefs));
        web.pages.put("/where", "{\"loc\":\"48.85,2.35\"}");
        double[] here = Place.find(Robo.context(), prefs);
        assertEquals(48.85, here[0], 1e-9);
        web.pages.remove("/where");
        assertEquals(2.35, Place.find(Robo.context(), prefs)[1], 1e-9);
        prefs.edit().putString("place", "a,b,c").commit();
        assertNull(Place.find(Robo.context(), prefs));
        shadowOf((Application) Robo.context()).grantPermissions(android.Manifest.permission.ACCESS_COARSE_LOCATION);
        android.location.LocationManager lm = Robo.context().getSystemService(android.location.LocationManager.class);
        shadowOf(lm).setProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER, true);
        android.location.Location fix = new android.location.Location(android.location.LocationManager.NETWORK_PROVIDER);
        fix.setLatitude(40.7);
        fix.setLongitude(-74.0);
        fix.setTime(5);
        shadowOf(lm).setLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER, fix);
        assertEquals(40.7, Place.find(Robo.context(), prefs)[0], 1e-9);
    }
}
