package com.arzaroth.lumen;

import android.content.res.AssetManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The themes Lumen can wear: the built-in ones, Space first and the default, then the packs found. */
final class Themes {
    private static final List<Theme> BUILT_IN = Collections.singletonList(new SpaceTheme());
    private static volatile List<Theme> all = BUILT_IN;
    private static boolean discovered;

    private Themes() {
    }

    /** Adds the packs shipped in the app's assets, one folder each under `themes/`; only the first call looks. */
    static synchronized void discover(AssetManager assets) {
        if (discovered) return;
        discovered = true;
        List<Theme> found = new ArrayList<>(BUILT_IN);
        try {
            String[] entries = assets.list("themes");
            java.util.Set<String> folders = new java.util.LinkedHashSet<>();
            for (String e : entries != null ? entries : new String[0]) folders.add(e.contains("/") ? e.substring(0, e.indexOf('/')) : e);
            for (String folder : folders) {
                try {
                    Pack pack = Pack.read(name -> assets.open("themes/" + folder + "/" + name));
                    if (byId(found, pack.id) == null) found.add(new PackTheme(pack));
                } catch (IOException | org.json.JSONException e) {
                    android.util.Log.w("Lumen", "skipping theme pack " + folder + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            android.util.Log.w("Lumen", "no theme packs: " + e.getMessage());
        }
        all = Collections.unmodifiableList(found);
    }

    static List<Theme> all() {
        return all;
    }

    /** The theme with `id`, or the default one when there is none. */
    static Theme byId(String id) {
        Theme t = byId(all, id);
        return t != null ? t : BUILT_IN.get(0);
    }

    private static Theme byId(List<Theme> themes, String id) {
        for (Theme t : themes) if (t.id().equals(id)) return t;
        return null;
    }
}
