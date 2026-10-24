# Themes

A theme is the whole look of Lumen: what is drawn behind the logo and the light every effect is made
of. Lumen settings › Theme picks one; it is kept by id (`theme` in the preferences).

## The interfaces

- `Theme`: an id, a name, `backdrop(scale)`, and `palette()` (defaulting to Space's). Built-in themes implement it in code; the registry
  is `Themes` (`all`, `byId`, falling back to the first, Space).
- `Backdrop`: what the stage draws behind the logo. Required: `look` (one app's look from its logo's
  colours, built off the UI thread), `setLook`, `layout`, `update`, `drawBack`, `moving`. Optional,
  with no-op defaults: `intro`, `setParallax`, `setWarp` (the hyperspace exit), `setWeather`,
  `setStill`, `setActivity` (meteors in space), `storm`, and the space-only `setTurning`,
  `setNebula`, `setRealSky`.
- `Palette`: the light of every effect besides each app's own colours: the first two colours of the
  logo's sparks (`hot`, `warm`), the clock's flying digits (`dust`), the update's dust, glow and
  scrim (`motes`, `glow`, `scrim`), and the screensaver's accent (`calm`). The stage passes it to the
  mosaic builder with the backdrop; `applySettings` hands it to the clock and the update; the
  screensaver reads the theme from the preferences.
- `Stage.setTheme` swaps the backdrop, drops every cached app look (they belong to the old backdrop)
  and shows the current app again; `LumenActivity.applySettings` then sends every setting to it.

## Themes so far

| Theme | Backdrop | Palette |
|---|---|---|
| Space | `Cosmos` ([cosmos.md](cosmos.md)) | `Palette.SPACE`: white and gold, cold blue glow |

## Where it is going

1. The interfaces, with Space on them unchanged. (Done.)
2. Every other surface themed: the clock, date and update particles, the logo's sparks, the glow
   colours, the screensaver. (Done, through `Palette`.)
3. Declarative packs: a JSON manifest and images on a generic layered backdrop engine, loaded from
   files, with an author's guide.
4. Code plugins: a theme from a separate APK, behind an explicit switch, since its code runs inside
   Home. Then a theme store.

Every theme must fit the GPU budget ([gpu-budget.md](gpu-budget.md)).

A volcano theme was built on these interfaces in October 2026 (an eruption at dusk after Anak
Krakatau, procedural) and dropped: realism out of noise and strokes never got past looking cheap.
It is in git history (`Volcano.java`, removed after `1b66561`) for anyone who wants to try a
landscape backdrop again; a photographic base is the route it did not take.

## Sources

- `lumen/src/com/arzaroth/lumen/Theme.java`, `Backdrop.java`, `Palette.java`, `Themes.java`, `SpaceTheme.java`, `Precipitation.java`, `Noise.java`, `Stage.java` (`setTheme`), `LumenSettings.java` (`themeChoice`)
- `lumen/test/com/arzaroth/lumen/ThemesTest.java`, `StageTest.java` (`Plain`)
