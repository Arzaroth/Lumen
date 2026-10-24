package com.arzaroth.lumen;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;

/** The Settings menu: the TV's own settings, or Lumen's, applied as they change. */
final class LumenSettings {
    interface Host {
        void applySettings();

        void resetOrder();

        void openSystemSettings();

        void playIntro();

        /** The update moved on: the screen follows it. */
        void showUpdate(Updater.State state, String latest, int progress);

        Menu menu();
    }

    private static final String DOOT = "org.doot.android", DOOT_ACTION = "org.doot.android.DOOT", DOOTS_OFF = "doots_off";

    private final Context context;
    private final Host host;
    private final SharedPreferences prefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Updater updater;
    private Menu.Page advanced;

    LumenSettings(Context context, Host host, SharedPreferences prefs) {
        this.context = context;
        this.host = host;
        this.prefs = prefs;
        updater = new Updater(context, this::refresh);
    }

    Menu.Page root() {
        return new Menu.Page("Settings", 400)
                .add(Menu.Row.action("System settings", host::openSystemSettings))
                .add(Menu.Row.sub("Lumen settings", this::lumen));
    }

    private Menu.Page lumen() {
        return new Menu.Page("Lumen settings", 520)
                .add(Menu.Row.sub("Sky", this::sky))
                .add(Menu.Row.sub("Home screen", this::home))
                .add(Menu.Row.sub("Remote", this::remote))
                .add(Menu.Row.sub("Boot intro", this::intro))
                .add(Menu.Row.sub("Advanced settings", this::openAdvanced));
    }

    private Menu.Page sky() {
        return new Menu.Page("Sky", 640)
                .add(choice("Backdrop", Prefs.BACKDROP, 0, "Night sky", "Fireworks", "Still sky"))
                .add(choice("Animations", Prefs.PACE, 0, "Lively", "Calm", "Off"))
                .add(choice("Meteors", Prefs.METEORS, Prefs.METEORS_DEFAULT, "Off", "Few", "Shower", "Storm"))
                .add(choice("Sky rotation", Prefs.TURN, 0, "Off", "On"))
                .add(choice("Parallax", Prefs.PARALLAX, 0, "On", "Off"));
    }

    private Menu.Page home() {
        return new Menu.Page("Home screen", 640)
                .add(choice("Logo", Prefs.LOGO, 0, "3D voxels", "Flat"))
                .add(choice("Greeting", Prefs.GREETING, 0, "On", "Off"))
                .add(choice("Clock", Prefs.CLOCK, 0, "System", "24-hour", "12-hour"))
                .add(Menu.Row.action("Reset tile order", this::askReset));
    }

    private Menu.Page remote() {
        return new Menu.Page("Remote", 640)
                .add(choice("Remote buttons", Prefs.KEYS, 0, "On", "Off"))
                .add(choice("YouTube button", Prefs.YOUTUBE, 0, "YouTube", "SmartTube"));
    }

    private Menu.Page intro() {
        return new Menu.Page("Boot intro", 520)
                .add(choice("Boot intro", Prefs.INTRO, 0, "On", "Off"))
                .add(Menu.Row.action("Play the intro", host::playIntro));
    }

    /** Opening Advanced settings asks GitHub again, unless an update is already under way. */
    private Menu.Page openAdvanced() {
        if (!updater.busy()) updater.check();
        return advanced();
    }

    private Menu.Page advanced() {
        advanced = new Menu.Page("Advanced settings", 520)
                .add(Menu.Row.sub("Seasonal events", this::seasonal))
                .add(updateRow())
                .add(Menu.Row.note("Lumen " + Updater.installed(context)));
        return advanced;
    }

    private Menu.Row updateRow() {
        String latest = updater.latest();
        switch (updater.state()) {
            case CURRENT:
                return Menu.Row.action("Lumen is up to date", updater::check);
            case AVAILABLE:
                return Menu.Row.action("Update to " + latest, updater::update);
            case BLOCKED:
                return Menu.Row.action("Run scripts/install.sh to allow updates", updater::update);
            case DOWNLOADING:
                return Menu.Row.action("Downloading " + latest + "\u2026 " + updater.progress() + "%", () -> { });
            case INSTALLING:
                return Menu.Row.action("Installing " + latest + "\u2026", () -> { });
            case FAILED:
                return Menu.Row.action("Couldn't update, try again", updater::check);
            default:
                return Menu.Row.action("Checking for updates\u2026", () -> { });
        }
    }

    private void refresh() {
        host.showUpdate(updater.state(), updater.latest(), updater.progress());
        Menu menu = host.menu();
        if (advanced != null && menu.showing(advanced)) menu.swap(advanced());
    }

    private Menu.Page seasonal() {
        return new Menu.Page("Seasonal events", 480).add(Menu.Row.sub("Spooky doot", this::spooky));
    }

    private Menu.Page spooky() {
        Menu.Page page = new Menu.Page("Spooky doot", 560);
        try {
            context.getPackageManager().getPackageInfo(DOOT, 0);
        } catch (PackageManager.NameNotFoundException e) {
            return page.add(Menu.Row.note("doot is not installed"));
        }
        if (context.checkSelfPermission(Manifest.permission.DUMP) != PackageManager.PERMISSION_GRANTED) {
            return page.add(Menu.Row.note("Run scripts/install.sh to reach doot"));
        }
        if (prefs.getBoolean(DOOTS_OFF, false)) {
            return page.add(Menu.Row.action("Enable doots", this::enableDoots));
        }
        return page.add(Menu.Row.action("Disable doots", this::askDisable));
    }

    /**
     * Two questions before the crypt goes quiet. The second swaps its buttons and opens with the
     * cursor where "Disable" just was, now on "Cancel".
     */
    private void askDisable() {
        Menu menu = host.menu();
        menu.push(Menu.Page.ask("Spooky doot", "Disable doots? The skeleton will stop dooting on this TV.",
                "Cancel", menu::pop, "Disable", () -> menu.replace(Menu.Page.ask("Are you sure?",
                        "ARE YOU SURE? The crypt goes silent. No more doots, not even on Halloween.",
                        "Disable", this::disableDoots, "Cancel", menu::pop, 1)), 0));
    }

    private void disableDoots() {
        doot("--once", "--ignore-season");
        main.postDelayed(() -> doot("--snooze", "36500d"), 8000);
        prefs.edit().putBoolean(DOOTS_OFF, true).apply();
        Menu menu = host.menu();
        menu.pop();
        menu.replace(spooky());
    }

    private void enableDoots() {
        doot("--resume");
        prefs.edit().putBoolean(DOOTS_OFF, false).apply();
        host.menu().replace(spooky());
    }

    private void doot(String... args) {
        context.sendBroadcast(new Intent(DOOT_ACTION).setPackage(DOOT).putExtra("args", args));
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
