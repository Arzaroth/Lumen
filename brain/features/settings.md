# Settings

The Settings pill opens a dropdown ([../architecture/menus.md](../architecture/menus.md)):

- **System settings**: the TV's own settings.
- **Lumen settings**, applied at once and remembered:
  - Theme: the whole look of Lumen ([../architecture/themes.md](../architecture/themes.md)).
  - Backdrop: Animations, Motion, Parallax and Weather for every theme, then the current theme's
    own options (Space and Fireworks: Meteors, Sky rotation, Nebula, Real sky).
  - Home screen: Logo, Greeting, Clock, Order (Manual or Most used), Reset tile order.
  - Remote: Remote buttons, YouTube button (YouTube or SmartTube).
  - Boot intro: on/off, Play the intro.
  - Advanced settings: Seasonal events, the update row, the version.

Adding a setting: a key in `Prefs`, a `choice(...)` row in the right group of `LumenSettings`, and
its effect in `LumenActivity.applySettings`.

## Sources

- `lumen/src/com/arzaroth/lumen/LumenSettings.java`, `Prefs.java`, `LumenActivity.java` (`applySettings`)
