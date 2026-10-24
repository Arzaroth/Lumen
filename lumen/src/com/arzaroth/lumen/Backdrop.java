package com.arzaroth.lumen;

import android.graphics.Canvas;
import android.graphics.RectF;

import java.util.Random;

/**
 * What a theme draws behind the logo, and how it answers what happens on Home: an app gets the
 * focus, the logo explodes into hyperspace, the weather turns, the TV has just booted. The stage
 * owns one and drives it every frame; everything it draws must stay within the GPU budget
 * (brain/architecture/gpu-budget.md).
 *
 * Only the first group is required. The rest have no-op defaults, so a backdrop only answers what
 * makes sense for it. Settings only one theme has come through `setOption`.
 */
interface Backdrop {
    /** One app's look in this backdrop, chosen from its logo's colours. Built off the UI thread. */
    interface Look {
    }

    /** Builds an app's look from the logo's spark colours and accent, picking at random from `random` (seeded per app). */
    Look look(int[] sparks, int accent, Random random);

    /** Turns to `look`, cross-fading from the last one when `animate`. */
    void setLook(Look look, boolean animate);

    /** Where the logo is (`area`) on a `w` by `h` screen. */
    void layout(RectF area, int w, int h);

    /** Moves time on by `dt` seconds. */
    void update(float dt);

    /** Draws the whole backdrop, behind the logo. */
    void drawBack(Canvas c);

    /** Whether frames are still needed while the logo holds still. */
    boolean moving();

    /** The first appearance after a boot. */
    default void intro() {
    }

    /** Where the eye looks: -1 (left) to 1, and 0 (level) to 1 (down). */
    default void setParallax(float x, float y) {
    }

    /** The logo's hyperspace exit, from 0 to 1. */
    default void setWarp(float warp) {
    }

    /** The weather outside, each from 0 to 1. */
    default void setWeather(float clouds, float rain, float snow) {
    }

    /** No motion at all (the still backdrop setting). */
    default void setStill(boolean still) {
    }

    /** A burst of events for the next `seconds` (a meteor storm in space). */
    default void storm(float seconds) {
    }

    /** One of its theme's own settings (`Theme.options`) changed to `value`. */
    default void setOption(String key, int value) {
    }
}
