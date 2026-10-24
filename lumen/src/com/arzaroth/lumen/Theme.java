package com.arzaroth.lumen;

import android.content.Context;

import java.util.Collections;
import java.util.List;

/**
 * A look for Lumen: what is drawn behind the logo, how the logo dances, the light every effect is
 * made of, and the settings that belong to it. Built-in themes are code implementing this; packs are
 * a picture and a JSON file (brain/architecture/themes.md).
 */
interface Theme {
    /** A stable id, kept in the preferences. */
    String id();

    /** The name shown in settings. */
    String name();

    /** A new backdrop for a screen drawn at `scale` (1 at 1920 wide). */
    Backdrop backdrop(Context context, float scale);

    /** The light the clock, the logo's sparks, the update and the screensaver are made of. */
    default Palette palette() {
        return Palette.SPACE;
    }

    /** The logo's choreography: Stage.COSMOS (meteors and starbursts) or Stage.FIREWORKS (rockets and shells). */
    default int choreography() {
        return Stage.COSMOS;
    }

    /** This theme's own settings, after the ones every theme shares; each reaches the backdrop through `Backdrop.setOption`. */
    default List<Option> options() {
        return Collections.emptyList();
    }
}
