package dev.arzaroth.lumen;

/** Keys of Lumen's settings in the "lumen" shared preferences: choice indexes, and the boot the intro last played on. */
final class Prefs {
    static final String BACKDROP = "backdrop", PACE = "pace", METEORS = "meteors", LOGO = "logo";
    static final String GREETING = "greeting", CLOCK = "clock", KEYS = "keys", YOUTUBE = "youtube";
    static final String INTRO = "intro", INTRO_BOOT = "intro_boot";
    static final int METEORS_DEFAULT = 2;

    private Prefs() {
    }
}
