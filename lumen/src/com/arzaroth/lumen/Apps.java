package com.arzaroth.lumen;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class Apps {
    static final int NEUTRAL = 0xFF8FA6C8;

    /** Apps Home leaves out of the dock and grid: the top bar already opens them. */
    private static final Set<String> HIDDEN = new HashSet<>(Arrays.asList("com.android.tv.settings"));

    private Apps() {}

    static ArrayList<AppEntry> load(Context c, int w, int h) {
        PackageManager pm = c.getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> found = pm.queryIntentActivities(query, 0);
        ArrayList<AppEntry> apps = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        for (ResolveInfo ri : found) {
            ActivityInfo ai = ri.activityInfo;
            if (ai == null || ai.packageName.equals(c.getPackageName()) || HIDDEN.contains(ai.packageName)) continue;
            ComponentName cn = new ComponentName(ai.packageName, ai.name);
            if (!seen.add(cn.flattenToShortString())) continue;
            Drawable banner = bannerOf(pm, cn);
            Bitmap bmp = banner != null ? render(banner, w, h) : renderIcon(ri.loadIcon(pm), w, h);
            int accent = accent(bmp);
            apps.add(new AppEntry(cn, String.valueOf(ri.loadLabel(pm)), bmp, accent));
        }
        return apps;
    }

    /** The app's banner (or icon) drawn at `w` x `h`, for when the tile-sized one is too small. */
    static Bitmap banner(Context c, ComponentName cn, int w, int h) {
        PackageManager pm = c.getPackageManager();
        Drawable banner = bannerOf(pm, cn);
        if (banner != null) return render(banner, w, h);
        try {
            return renderIcon(pm.getActivityIcon(cn), w, h);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    private static Drawable bannerOf(PackageManager pm, ComponentName cn) {
        try {
            return pm.getActivityBanner(cn);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    private static Bitmap render(Drawable d, int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(b);
        canvas.drawColor(0xFF15151B);
        d.setBounds(0, 0, w, h);
        d.draw(canvas);
        return b;
    }

    private static Bitmap renderIcon(Drawable icon, int w, int h) {
        int size = Math.round(h * 0.56f);
        Bitmap ib = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        icon.setBounds(0, 0, size, size);
        icon.draw(new Canvas(ib));
        int tint = accent(ib);

        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(b);
        Paint bg = new Paint();
        bg.setShader(new LinearGradient(0, 0, w, h, mix(tint, 0xFF000000, 0.55f), mix(tint, 0xFF000000, 0.82f), Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, bg);
        canvas.drawBitmap(ib, (w - size) / 2f, (h - size) / 2f, new Paint(Paint.FILTER_BITMAP_FLAG));
        ib.recycle();
        return b;
    }

    static int accent(Bitmap b) {
        Bitmap t = Bitmap.createScaledBitmap(b, 16, 9, true);
        float[] hsv = new float[3];
        double x = 0, y = 0, sat = 0, weight = 0;
        for (int j = 0; j < t.getHeight(); j++) {
            for (int i = 0; i < t.getWidth(); i++) {
                int p = t.getPixel(i, j);
                if (Color.alpha(p) < 128) continue;
                Color.colorToHSV(p, hsv);
                double w = hsv[1] * hsv[2] * hsv[2] + 0.01;
                double rad = Math.toRadians(hsv[0]);
                x += Math.cos(rad) * w;
                y += Math.sin(rad) * w;
                sat += hsv[1] * w;
                weight += w;
            }
        }
        if (t != b) t.recycle();
        if (weight == 0) return NEUTRAL;
        double s = sat / weight;
        if (s < 0.14) return NEUTRAL;
        float hue = (float) ((Math.toDegrees(Math.atan2(y, x)) + 360) % 360);
        float outS = (float) Math.max(0.45, Math.min(0.85, s * 1.15));
        return Color.HSVToColor(new float[] {hue, outS, 0.95f});
    }

    static int mix(int a, int b, float t) {
        float u = 1 - t;
        return Color.argb(
                Math.round(Color.alpha(a) * u + Color.alpha(b) * t),
                Math.round(Color.red(a) * u + Color.red(b) * t),
                Math.round(Color.green(a) * u + Color.green(b) * t),
                Math.round(Color.blue(a) * u + Color.blue(b) * t));
    }

    static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }
}
