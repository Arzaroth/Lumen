package com.arzaroth.lumen;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** The themes Lumen can wear, the first being the default. */
final class Themes {
    private static final List<Theme> BUILT_IN = Collections.unmodifiableList(Arrays.asList(new SpaceTheme()));

    private Themes() {
    }

    static List<Theme> all() {
        return BUILT_IN;
    }

    /** The theme with `id`, or the default one when there is none. */
    static Theme byId(String id) {
        for (Theme t : BUILT_IN) if (t.id().equals(id)) return t;
        return BUILT_IN.get(0);
    }
}
