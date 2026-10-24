# Sky

- **Backdrop** (Lumen settings › Sky): Night sky, Fireworks, or Still sky (no motion).
- **Meteors**: Off, Few, Shower, Storm; each app has its own radiant and meteor colour.
- **Sky rotation**: the sky turns at the Earth's real rate, about a pixel every 6 s; off by default.
- **Parallax**: the sky's layers slide as the focus moves along the dock and down the rows; on by
  default.
- Each app also gets its own nebula and airglow tint; they cross-fade on an app switch.

The machinery is in [../architecture/cosmos.md](../architecture/cosmos.md).

## Sources

- `lumen/src/com/arzaroth/lumen/Cosmos.java`, `LumenSettings.java` (`sky`), `LumenActivity.java` (`applySettings`, `parallaxOf`)
