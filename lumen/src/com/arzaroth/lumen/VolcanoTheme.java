package com.arzaroth.lumen;

/** A volcanic night: smoking craters, lava in each app's colour, embers and eruptions. */
final class VolcanoTheme implements Theme {
    @Override
    public String id() {
        return "volcano";
    }

    @Override
    public String name() {
        return "Volcanoes";
    }

    @Override
    public Backdrop backdrop(float scale) {
        return new Volcano(scale);
    }

    @Override
    public Palette palette() {
        return Volcano.PALETTE;
    }
}
