package com.arzaroth.lumen;

/** A theme read from a pack: its picture brought to life by `PictureBackdrop`. */
final class PackTheme implements Theme {
    private final Pack pack;

    PackTheme(Pack pack) {
        this.pack = pack;
    }

    @Override
    public String id() {
        return pack.id;
    }

    @Override
    public String name() {
        return pack.name;
    }

    @Override
    public Backdrop backdrop(android.content.Context context, float scale) {
        return new PictureBackdrop(pack, scale);
    }

    @Override
    public Palette palette() {
        return pack.palette;
    }
}
