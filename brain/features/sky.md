# Sky

The night sky is the Space theme's backdrop, and the Fireworks theme's too (with rockets and shells
instead of starbursts). Its settings are on Lumen settings › Backdrop: the shared ones (Animations,
Motion, Parallax, Weather), then Space's own options.

- **Motion**: Moving, or Still (no motion at all).
- **Meteors**: Off, Few, Shower, Storm; each app has its own radiant and meteor colour.
- **Sky rotation**: the sky turns at the Earth's real rate, about a pixel every 6 s; off by default.
- **Parallax**: the sky's layers slide as the focus moves along the dock and down the rows; on by
  default.
- Each app also gets its own nebula and airglow tint; they cross-fade on an app switch.
- **Nebula**: on by default; off keeps only the airglow on the horizon.
- **Real sky** (off by default): the stars are the real ones above home right now, seen facing south
  (north below the equator) over 120 degrees, with the Milky Way along the true galactic plane, the
  Moon in its phase and Venus, Mars, Jupiter and Saturn. It follows the night: live stars every
  second, the faint field every two minutes with a cross-fade. Sky rotation has no effect on it.
- **Weather** (off by default): the sky follows the weather outside. Clouds veil the stars and thin
  the meteors, rain falls as slanted streaks and snow as soft flakes in front of the sky, and the
  temperature joins the date (Fahrenheit in the US). `Place` finds the TV (Android's last known
  location, else ipinfo.io once a day), `Weather` asks Open-Meteo every half hour while Home is on
  screen.

The machinery is in [../architecture/cosmos.md](../architecture/cosmos.md).

## Sources

- `lumen/src/com/arzaroth/lumen/Cosmos.java` (`setOption`), `SpaceTheme.java`, `LumenSettings.java` (`backdrop`), `LumenActivity.java` (`applySettings`, `parallaxOf`)
