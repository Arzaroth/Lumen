package com.arzaroth.lumen;

/** Keys of Lumen's settings in the "lumen" shared preferences: choice indexes, and the boot the intro last played on. */
final class Prefs {
    static final String BACKDROP = "backdrop", PACE = "pace", METEORS = "meteors", LOGO = "logo";
    static final String GREETING = "greeting", CLOCK = "clock", KEYS = "keys", YOUTUBE = "youtube";
    static final String INTRO = "intro", INTRO_BOOT = "intro_boot", TURN = "turn", PARALLAX = "parallax";
    /** When Lumen handed itself to Android to be replaced: the next Lumen comes back to the front. */
    static final String RELAUNCH = "relaunch_at";
    static final int METEORS_DEFAULT = 2;

    private Prefs() {
    }
}
