package dev.arzaroth.lumen;

import android.content.ComponentName;
import android.graphics.Bitmap;

final class AppEntry {
    final ComponentName component;
    final String label;
    final Bitmap banner;
    final int accent;

    AppEntry(ComponentName component, String label, Bitmap banner, int accent) {
        this.component = component;
        this.label = label;
        this.banner = banner;
        this.accent = accent;
    }

    String key() {
        return component.flattenToShortString();
    }
}
