package com.arzaroth.lumen;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** The weather outside, from Open-Meteo, every half hour while it is wanted. */
final class Weather {
    static String forecast = "https://api.open-meteo.com/v1/forecast";
    static long every = 30 * 60 * 1000L;

    /** What the sky shows: cloud cover, rain and snow from 0 to 1, and the temperature. */
    static final class Now {
        final float clouds, rain, snow, celsius;

        Now(float clouds, float rain, float snow, float celsius) {
            this.clouds = clouds;
            this.rain = rain;
            this.snow = snow;
            this.celsius = celsius;
        }
    }

    private final Context context;
    private final SharedPreferences prefs;
    private final Consumer<Now> listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Executor worker = Executors.newSingleThreadExecutor();
    private final Runnable tick = this::fetch;
    private boolean on;
    private long fetchedAt = Long.MIN_VALUE / 2;

    Weather(Context context, SharedPreferences prefs, Consumer<Now> listener) {
        this.context = context.getApplicationContext();
        this.prefs = prefs;
        this.listener = listener;
    }

    void setOn(boolean want) {
        if (want == on) return;
        on = want;
        main.removeCallbacks(tick);
        if (!on) return;
        long wait = fetchedAt + every - android.os.SystemClock.uptimeMillis();
        if (wait <= 0) fetch();
        else main.postDelayed(tick, wait);
    }

    private void fetch() {
        worker.execute(() -> {
            Now now = null;
            double[] place = Place.find(context, prefs);
            if (place != null) {
                try {
                    now = parse(Net.read(String.format(Locale.ROOT,
                            "%s?latitude=%.3f&longitude=%.3f&current=temperature_2m,weather_code,cloud_cover,precipitation,snowfall",
                            forecast, place[0], place[1])));
                } catch (IOException | JSONException e) {
                    now = null;
                }
            }
            Now found = now;
            main.post(() -> {
                fetchedAt = android.os.SystemClock.uptimeMillis();
                if (!on) return;
                if (found != null) listener.accept(found);
                main.postDelayed(tick, every);
            });
        });
    }

    /** Open-Meteo's current conditions as clouds, rain, snow and temperature (WMO weather codes). */
    static Now parse(String json) throws JSONException {
        JSONObject current = new JSONObject(json).getJSONObject("current");
        int code = current.optInt("weather_code");
        float precipitation = (float) current.optDouble("precipitation", 0);
        float snowfall = (float) current.optDouble("snowfall", 0);
        boolean snowy = code >= 71 && code <= 77 || code == 85 || code == 86;
        boolean rainy = code >= 51 && code <= 67 || code >= 80 && code <= 82 || code >= 95;
        float rain = rainy ? Math.max(0.15f, Math.min(1f, precipitation / 4f)) : 0f;
        float snow = snowy ? Math.max(0.15f, Math.min(1f, Math.max(snowfall, precipitation) / 2f)) : 0f;
        float clouds = Math.max(0f, Math.min(1f, (float) current.optDouble("cloud_cover", 0) / 100f));
        return new Now(clouds, rain, snow, (float) current.getDouble("temperature_2m"));
    }

    /** The temperature the way people around here read it: Fahrenheit in the US and a few others. */
    static String temperature(float celsius, Locale locale) {
        String country = locale.getCountry();
        boolean fahrenheit = country.equals("US") || country.equals("LR") || country.equals("MM") || country.equals("BS");
        return Math.round(fahrenheit ? celsius * 9f / 5f + 32f : celsius) + "°";
    }
}
