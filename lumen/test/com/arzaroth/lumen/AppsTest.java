package com.arzaroth.lumen;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.robolectric.Shadows.shadowOf;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.ColorDrawable;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class AppsTest {
    static ResolveInfo leanback(String pkg, String label) {
        ResolveInfo ri = new ResolveInfo();
        ri.activityInfo = new ActivityInfo();
        ri.activityInfo.packageName = pkg;
        ri.activityInfo.name = pkg + ".Main";
        ri.activityInfo.applicationInfo = new ApplicationInfo();
        ri.activityInfo.applicationInfo.packageName = pkg;
        ri.nonLocalizedLabel = label;
        ri.icon = 0;
        return ri;
    }

    static void install(String... pkgs) {
        PackageManager pm = Robo.context().getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (String pkg : pkgs) {
            shadowOf(pm).addResolveInfoForIntent(query, leanback(pkg, pkg.substring(pkg.lastIndexOf('.') + 1)));
            shadowOf(pm).addActivityIfNotPresent(new ComponentName(pkg, pkg + ".Main"));
        }
    }

    @Test
    public void loadsLeanbackAppsOnceEachSkippingItselfAndSettings() {
        install("org.example.kodi", "org.example.jellyfin");
        PackageManager pm = Robo.context().getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        shadowOf(pm).addResolveInfoForIntent(query, leanback("org.example.kodi", "kodi again"));
        shadowOf(pm).addResolveInfoForIntent(query, leanback(Robo.context().getPackageName(), "Lumen"));
        ResolveInfo broken = new ResolveInfo();
        shadowOf(pm).addResolveInfoForIntent(query, broken);
        shadowOf(pm).addResolveInfoForIntent(query, leanback("org.example.ghost", "ghost"));
        install("com.android.tv.settings", "com.realtek.tv", "org.doot.android");
        ArrayList<AppEntry> apps = Apps.load(Robo.context(), 160, 90);
        assertEquals(3, apps.size());
        for (AppEntry app : apps) assertNotEquals("com.android.tv.settings", app.component.getPackageName());
        assertEquals(160, apps.get(0).banner.getWidth());
    }

    @Test
    public void bannerRendersTheIconOrBanner() {
        install("org.example.fcast");
        ComponentName cn = new ComponentName("org.example.fcast", "org.example.fcast.Main");
        shadowOf(Robo.context().getPackageManager()).setApplicationIcon("org.example.fcast", new ColorDrawable(0xFF2196F3));
        Bitmap b = Apps.banner(Robo.context(), cn, 320, 180);
        assertNotNull(b);
        assertEquals(320, b.getWidth());
        assertNull(Apps.banner(Robo.context(), new ComponentName("org.missing", "org.missing.Main"), 320, 180));
        ComponentName own = new ComponentName(Robo.context(), LumenActivity.class);
        assertEquals(240, Apps.banner(Robo.context(), own, 240, 135).getWidth());
    }
}
