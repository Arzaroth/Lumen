package dev.arzaroth.lumen;

import android.content.ComponentName;
import android.graphics.Bitmap;

final class AppEntry {
    final ComponentName component;
    final String label;
    final Bitmap banner;
    final Bitmap glow;
    final int accent;

    AppEntry(ComponentName component, String label, Bitmap banner, Bitmap glow, int accent) {
        this.component = component;
        this.label = label;
        this.banner = banner;
        this.glow = glow;
        this.accent = accent;
    }

    String key() {
        return component.flattenToShortString();
    }
}
