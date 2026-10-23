package com.arzaroth.lumen;

import android.content.Context;
import android.graphics.Typeface;
import android.util.SparseArray;

final class Fonts {
    private static final SparseArray<Typeface> CACHE = new SparseArray<>();

    private Fonts() {}

    static Typeface outfit(Context c, int weight) {
        Typeface t = CACHE.get(weight);
        if (t != null) return t;
        try {
            t = new Typeface.Builder(c.getAssets(), "outfit.ttf")
                    .setFontVariationSettings("'wght' " + weight)
                    .build();
        } catch (RuntimeException e) {
            t = null;
        }
        if (t == null) t = Typeface.create("sans-serif-light", Typeface.NORMAL);
        CACHE.put(weight, t);
        return t;
    }
}
