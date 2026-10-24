package com.arzaroth.lumen;

/** The light a theme's effects are made of, besides each app's own colours. */
final class Palette {
    static final Palette SPACE = new Palette(0xFFFFFFFF, 0xFFFFD27A, new int[] {0xFFFFFFFF, 0xFFFFD8A0},
            new int[] {0xFFFFFFFF, 0xFFDDE6FF, 0xFFBFD4FF, 0xFFFFF2D8}, 0xFF9FB4FF, 0xFF05060C, 0xFFB8C6E8);

    /** The first two colours of every logo's sparks, before the logo's own. */
    final int hot, warm;
    /** The two colours the clock's digits fly apart in, besides the app's. */
    final int[] dust;
    /** The update's dust. */
    final int[] motes;
    /** The update's number glow. */
    final int glow;
    /** Behind the update. */
    final int scrim;
    /** The screensaver clock's accent. */
    final int calm;

    Palette(int hot, int warm, int[] dust, int[] motes, int glow, int scrim, int calm) {
        this.hot = hot;
        this.warm = warm;
        this.dust = dust;
        this.motes = motes;
        this.glow = glow;
        this.scrim = scrim;
        this.calm = calm;
    }
}
