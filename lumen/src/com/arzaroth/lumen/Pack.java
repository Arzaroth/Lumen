package com.arzaroth.lumen;

import android.graphics.Color;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A theme pack: a picture and a `theme.json` saying where it sits on screen, which light it lends
 * the rest of Lumen and which layers bring it to life. No code, so anyone can make one
 * (brain/architecture/themes.md has the format).
 */
final class Pack {
    /** Opens one of the pack's files by name. */
    interface Files {
        InputStream open(String name) throws IOException;
    }

    /** Paints the pack's picture at a size, for a theme drawn in code instead of shipped as an image. */
    interface Painter {
        Painting paint(int width, int height);
    }

    /**
     * What a painter hands over: the picture, and what moves on it. Smoke is two takes of the same
     * cloud, crossfading as each rises from `origin`; `over` is drawn on top of the smoke, lit; each
     * flow is a path lava runs down, its heat first and then its points. Coordinates are picture
     * pixels.
     */
    static final class Painting {
        final android.graphics.Bitmap picture;
        android.graphics.Bitmap[] smoke;
        /** Where the smoke sits, and how many picture pixels one of its pixels covers. */
        float smokeX, smokeY, smokeScale = 1f, originX, originY;
        android.graphics.Bitmap over;
        float overX, overY;
        List<float[]> flows = Collections.emptyList();

        Painting(android.graphics.Bitmap picture) {
            this.picture = picture;
        }
    }

    /** One animated layer over the picture; which fields count depends on `type`. */
    static final class Layer {
        final String type;
        final JSONObject spec;

        Layer(String type, JSONObject spec) {
            this.type = type;
            this.spec = spec;
        }

        float num(String key, float fallback) {
            return (float) spec.optDouble(key, fallback);
        }

        float[] pair(String key, float a, float b) {
            JSONArray v = spec.optJSONArray(key);
            return v != null && v.length() == 2 ? new float[] {(float) v.optDouble(0, a), (float) v.optDouble(1, b)} : new float[] {a, b};
        }

        int[] colors(String key, int fallback) {
            JSONArray v = spec.optJSONArray(key);
            if (v == null || v.length() == 0) return new int[] {fallback};
            int[] out = new int[v.length()];
            for (int i = 0; i < out.length; i++) out[i] = color(v.optString(i), fallback);
            return out;
        }
    }

    final String id, name, image;
    /** The point of the picture (fractions of its width and height) pinned to a point of the screen. */
    final float[] from, to;
    final Palette palette;
    final List<Layer> layers;
    final Files files;
    final Painter painter;

    private Pack(String id, String name, String image, float[] from, float[] to, Palette palette, List<Layer> layers, Files files, Painter painter) {
        this.id = id;
        this.name = name;
        this.image = image;
        this.from = from;
        this.to = to;
        this.palette = palette;
        this.layers = layers;
        this.files = files;
        this.painter = painter;
    }

    /** Reads `theme.json` from `files`. */
    static Pack read(Files files) throws IOException, JSONException {
        try (InputStream in = files.open("theme.json")) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return parse(out.toString("UTF-8"), files);
        }
    }

    static Pack parse(String json, Files files) throws JSONException {
        return parse(json, files, null);
    }

    /** A pack whose picture `painter` paints for each screen; its `image` is never read. */
    static Pack painted(String json, Painter painter) throws JSONException {
        return parse(json, name -> {
            throw new IOException("a painted pack has no files");
        }, painter);
    }

    private static Pack parse(String json, Files files, Painter painter) throws JSONException {
        JSONObject o = new JSONObject(json);
        JSONObject anchor = o.optJSONObject("anchor");
        float[] from = {0.5f, 0.5f}, to = {0.5f, 0.5f};
        if (anchor != null) {
            from = pair(anchor.optJSONArray("image"), from);
            to = pair(anchor.optJSONArray("screen"), to);
        }
        List<Layer> layers = new ArrayList<>();
        JSONArray ls = o.optJSONArray("layers");
        for (int i = 0; ls != null && i < ls.length(); i++) {
            JSONObject l = ls.getJSONObject(i);
            layers.add(new Layer(l.optString("type"), l));
        }
        return new Pack(o.getString("id"), o.optString("name", o.getString("id")), o.getString("image"), from, to,
                palette(o.optJSONObject("palette")), Collections.unmodifiableList(layers), files, painter);
    }

    private static Palette palette(JSONObject p) {
        Palette d = Palette.SPACE;
        if (p == null) return d;
        return new Palette(color(p.optString("hot"), d.hot), color(p.optString("warm"), d.warm), colors(p.optJSONArray("dust"), d.dust, 2),
                colors(p.optJSONArray("motes"), d.motes, 1), color(p.optString("glow"), d.glow), color(p.optString("scrim"), d.scrim),
                color(p.optString("calm"), d.calm));
    }

    private static int[] colors(JSONArray v, int[] fallback, int least) {
        if (v == null || v.length() < least) return fallback;
        int[] out = new int[v.length()];
        for (int i = 0; i < out.length; i++) out[i] = color(v.optString(i), fallback[Math.min(i, fallback.length - 1)]);
        return out;
    }

    static int color(String hex, int fallback) {
        try {
            return hex == null || hex.isEmpty() ? fallback : Color.parseColor(hex);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static float[] pair(JSONArray v, float[] fallback) {
        return v != null && v.length() == 2 ? new float[] {(float) v.optDouble(0, fallback[0]), (float) v.optDouble(1, fallback[1])} : fallback;
    }
}
