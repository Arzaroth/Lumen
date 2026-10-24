package com.arzaroth.lumen;

import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.Map;

/**
 * Which apps get opened, as one score per package that gains a point at each launch and halves
 * every week, so what you open lately counts more than what you opened once.
 */
final class Habits {
    static final String KEY = "habits";
    private static final double HALF_LIFE_MS = 7 * 24 * 3600 * 1000.0;

    private Habits() {
    }

    static void record(SharedPreferences prefs, String pkg) {
        Map<String, Double> scores = scores(prefs);
        scores.put(pkg, scores.getOrDefault(pkg, 0.0) + 1);
        StringBuilder sb = new StringBuilder().append(Wall.millis());
        for (Map.Entry<String, Double> e : scores.entrySet()) {
            if (e.getValue() < 0.05) continue;
            sb.append('\n').append(e.getKey()).append(' ').append(e.getValue());
        }
        prefs.edit().putString(KEY, sb.toString()).apply();
    }

    /** Every package's score, decayed to now. */
    static Map<String, Double> scores(SharedPreferences prefs) {
        HashMap<String, Double> scores = new HashMap<>();
        String[] lines = prefs.getString(KEY, "").split("\n");
        if (lines.length < 2) return scores;
        double decay;
        try {
            decay = Math.pow(0.5, Math.max(0, Wall.millis() - Long.parseLong(lines[0])) / HALF_LIFE_MS);
        } catch (NumberFormatException e) {
            return scores;
        }
        for (int i = 1; i < lines.length; i++) {
            int space = lines[i].lastIndexOf(' ');
            if (space <= 0) continue;
            try {
                scores.put(lines[i].substring(0, space), Double.parseDouble(lines[i].substring(space + 1)) * decay);
            } catch (NumberFormatException ignored) {
            }
        }
        return scores;
    }
}
