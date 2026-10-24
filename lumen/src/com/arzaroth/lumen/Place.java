package com.arzaroth.lumen;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

/**
 * Roughly where the TV is: Android's last known location when it has one (a TV seldom does), else
 * where its internet connection comes from, which is good to a city and asked at most once a day.
 */
final class Place {
    static String lookup = "https://ipinfo.io/json";
    private static final String KEY = "place";
    private static final long DAY_MS = 24 * 3600 * 1000L;

    private Place() {
    }

    /** Latitude and longitude in degrees, or null when nothing can tell. Blocks: call it off the UI thread. */
    static double[] find(Context c, SharedPreferences prefs) {
        double[] known = lastKnown(c);
        if (known != null) return known;
        String[] cached = prefs.getString(KEY, "").split(",");
        if (cached.length == 3) {
            try {
                if (Wall.millis() - Long.parseLong(cached[2]) < DAY_MS) {
                    return new double[] {Double.parseDouble(cached[0]), Double.parseDouble(cached[1])};
                }
            } catch (NumberFormatException ignored) {
            }
        }
        try {
            String[] loc = new JSONObject(Net.read(lookup)).getString("loc").split(",");
            double[] found = {Double.parseDouble(loc[0]), Double.parseDouble(loc[1])};
            prefs.edit().putString(KEY, found[0] + "," + found[1] + "," + Wall.millis()).apply();
            return found;
        } catch (IOException | JSONException | RuntimeException e) {
            return null;
        }
    }

    private static double[] lastKnown(Context c) {
        if (c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null;
        LocationManager lm = c.getSystemService(LocationManager.class);
        Location best = null;
        for (String provider : lm.getProviders(true)) {
            Location l = lm.getLastKnownLocation(provider);
            if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
        }
        return best == null ? null : new double[] {best.getLatitude(), best.getLongitude()};
    }
}
