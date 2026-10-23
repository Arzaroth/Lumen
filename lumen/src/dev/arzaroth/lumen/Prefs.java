package dev.arzaroth.lumen;

/** Keys of Lumen's settings in the "lumen" shared preferences; every value is a choice index. */
final class Prefs {
    static final String BACKDROP = "backdrop", PACE = "pace", METEORS = "meteors", LOGO = "logo";
    static final String GREETING = "greeting", CLOCK = "clock", KEYS = "keys", YOUTUBE = "youtube";
    static final int METEORS_DEFAULT = 2;

    private Prefs() {
    }
}
