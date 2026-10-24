# Settings

The Settings pill opens a dropdown ([../architecture/menus.md](../architecture/menus.md)):

- **System settings**: the TV's own settings.
- **Lumen settings**, applied at once and remembered:
  - Sky: Backdrop, Animations, Meteors, Sky rotation, Parallax.
  - Home screen: Logo, Greeting, Clock, Reset tile order.
  - Remote: Remote buttons, YouTube button (YouTube or SmartTube).
  - Boot intro: on/off, Play the intro.
  - Advanced settings: Seasonal events, the update row, the version.

Adding a setting: a key in `Prefs`, a `choice(...)` row in the right group of `LumenSettings`, and
its effect in `LumenActivity.applySettings`.

## Sources

- `lumen/src/com/arzaroth/lumen/LumenSettings.java`, `Prefs.java`, `LumenActivity.java` (`applySettings`)
