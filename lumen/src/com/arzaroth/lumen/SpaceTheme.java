package com.arzaroth.lumen;

/** Lumen's first look: a night sky with its stars, Milky Way, nebulae and meteors. */
final class SpaceTheme implements Theme {
    @Override
    public String id() {
        return "space";
    }

    @Override
    public String name() {
        return "Space";
    }

    @Override
    public Backdrop backdrop(float scale) {
        return new Cosmos(scale);
    }
}
