package com.arzaroth.lumen;

/** One of a theme's own settings: a row of choices in Lumen settings, kept as an int under `key`. */
final class Option {
    final String key, label;
    final String[] values;
    final int fallback;

    Option(String key, String label, int fallback, String... values) {
        this.key = key;
        this.label = label;
        this.fallback = fallback;
        this.values = values;
    }
}
