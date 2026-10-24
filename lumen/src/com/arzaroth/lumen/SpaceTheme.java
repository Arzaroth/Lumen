package com.arzaroth.lumen;

import android.content.Context;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** The night sky: stars, the Milky Way, nebulae and meteors, with the logo dancing among them or under fireworks. */
final class SpaceTheme implements Theme {
    private static final List<Option> OPTIONS = Collections.unmodifiableList(Arrays.asList(
            new Option(Prefs.METEORS, "Meteors", Prefs.METEORS_DEFAULT, "Off", "Few", "Shower", "Storm"),
            new Option(Prefs.TURN, "Sky rotation", 0, "Off", "On"),
            new Option(Prefs.NEBULA, "Nebula", 0, "On", "Off"),
            new Option(Prefs.REAL_SKY, "Real sky", 0, "Off", "On")));

    static final SpaceTheme SPACE = new SpaceTheme("space", "Space", Stage.COSMOS);
    static final SpaceTheme FIREWORKS = new SpaceTheme("fireworks", "Fireworks", Stage.FIREWORKS);

    private final String id, name;
    private final int choreography;

    private SpaceTheme(String id, String name, int choreography) {
        this.id = id;
        this.name = name;
        this.choreography = choreography;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Backdrop backdrop(Context context, float scale) {
        return new Cosmos(scale, context);
    }

    @Override
    public int choreography() {
        return choreography;
    }

    @Override
    public List<Option> options() {
        return OPTIONS;
    }
}
