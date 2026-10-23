package com.arzaroth.lumen;

import java.util.function.LongSupplier;

/** The wall clock, read through one place so tests can set the time of day. */
final class Wall {
    static LongSupplier clock = System::currentTimeMillis;

    private Wall() {
    }

    static long millis() {
        return clock.getAsLong();
    }
}
