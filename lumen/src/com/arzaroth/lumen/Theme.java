package com.arzaroth.lumen;

/**
 * A look for Lumen: what is drawn behind the logo, and the light every effect is made of. Built-in
 * themes are code implementing this; community themes will come as declarative packs or plugins
 * (brain/architecture/themes.md).
 */
interface Theme {
    /** A stable id, kept in the preferences. */
    String id();

    /** The name shown in settings. */
    String name();

    /** A new backdrop for a screen drawn at `scale` (1 at 1920 wide). */
    Backdrop backdrop(float scale);

    /** The light the clock, the logo's sparks, the update and the screensaver are made of. */
    default Palette palette() {
        return Palette.SPACE;
    }
}
