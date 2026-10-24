package com.arzaroth.lumen;

/** Keys of Lumen's settings in the "lumen" shared preferences: choice indexes, and the boot the intro last played on. */
final class Prefs {
    static final String BACKDROP = "backdrop", PACE = "pace", METEORS = "meteors", LOGO = "logo";
    static final String GREETING = "greeting", CLOCK = "clock", KEYS = "keys", YOUTUBE = "youtube";
    static final String INTRO = "intro", INTRO_BOOT = "intro_boot", TURN = "turn", PARALLAX = "parallax", NEBULA = "nebula", ORDER = "order_by", WEATHER = "weather", REAL_SKY = "real_sky", THEME = "theme";
    /** The backdrop holds still (1) or moves (0). */
    static final String STILL = "still";
    /** When Lumen handed itself to Android to be replaced: the next Lumen comes back to the front. */
    static final String RELAUNCH = "relaunch_at";
    static final int METEORS_DEFAULT = 2;

    private Prefs() {
    }

    /**
     * Carries settings over from older Lumens. The Sky page's Backdrop choice (night sky, fireworks,
     * still sky) became the Fireworks theme and the Motion setting.
     */
    static void migrate(android.content.SharedPreferences prefs) {
        if (!prefs.contains(BACKDROP)) return;
        int backdrop = prefs.getInt(BACKDROP, 0);
        android.content.SharedPreferences.Editor e = prefs.edit().remove(BACKDROP);
        if (backdrop == 1 && !prefs.contains(THEME)) e.putString(THEME, SpaceTheme.FIREWORKS.id());
        if (backdrop == 2) e.putInt(STILL, 1);
        e.apply();
    }
}
