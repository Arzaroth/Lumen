package dev.arzaroth.lumen;

import android.content.SharedPreferences;

/** The Settings menu: the TV's own settings, or Lumen's, applied as they change. */
final class LumenSettings {
    interface Host {
        void applySettings();

        void resetOrder();

        void openSystemSettings();

        Menu menu();
    }

    private final Host host;
    private final SharedPreferences prefs;

    LumenSettings(Host host, SharedPreferences prefs) {
        this.host = host;
        this.prefs = prefs;
    }

    Menu.Page root() {
        return new Menu.Page("Settings", 400)
                .add(Menu.Row.action("System settings", host::openSystemSettings))
                .add(Menu.Row.sub("Lumen settings", this::lumen));
    }

    private Menu.Page lumen() {
        return new Menu.Page("Lumen settings", 640)
                .add(choice("Backdrop", Prefs.BACKDROP, 0, "Night sky", "Fireworks", "Still sky"))
                .add(choice("Animations", Prefs.PACE, 0, "Lively", "Calm", "Off"))
                .add(choice("Meteors", Prefs.METEORS, Prefs.METEORS_DEFAULT, "Off", "Few", "Shower", "Storm"))
                .add(choice("Logo", Prefs.LOGO, 0, "3D voxels", "Flat"))
                .add(choice("Greeting", Prefs.GREETING, 0, "On", "Off"))
                .add(choice("Clock", Prefs.CLOCK, 0, "System", "24-hour", "12-hour"))
                .add(choice("Remote buttons", Prefs.KEYS, 0, "On", "Off"))
                .add(choice("YouTube button", Prefs.YOUTUBE, 0, "YouTube", "SmartTube"))
                .add(Menu.Row.action("Reset tile order", this::askReset));
    }

    private Menu.Row choice(String label, String key, int fallback, String... values) {
        return Menu.Row.choice(label, values, prefs.getInt(key, fallback), v -> {
            prefs.edit().putInt(key, v).apply();
            host.applySettings();
        });
    }

    private void askReset() {
        Menu menu = host.menu();
        menu.push(Menu.Page.ask("Reset tile order", "Put the apps back in their default order? Your arrangement will be lost.",
                "Cancel", menu::pop, "Reset", () -> {
                    host.resetOrder();
                    menu.pop();
                }, 0));
    }
}
