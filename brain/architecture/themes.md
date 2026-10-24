# Themes

A theme is the whole look of Lumen: what is drawn behind the logo and the light every effect is made
of. Lumen settings › Theme picks one; it is kept by id (`theme` in the preferences).

## The interfaces

- `Theme`: an id, a name, and `backdrop(scale)`. Built-in themes implement it in code; the registry
  is `Themes` (`all`, `byId`, falling back to the first, Space).
- `Backdrop`: what the stage draws behind the logo. Required: `look` (one app's look from its logo's
  colours, built off the UI thread), `setLook`, `layout`, `update`, `drawBack`, `moving`. Optional,
  with no-op defaults: `intro`, `setParallax`, `setWarp` (the hyperspace exit), `setWeather`,
  `setStill`, `setActivity` (meteors in space), `storm`, and the space-only `setTurning`,
  `setNebula`, `setRealSky`.
- `Stage.setTheme` swaps the backdrop, drops every cached app look (they belong to the old backdrop)
  and shows the current app again; `LumenActivity.applySettings` then sends every setting to it.

## Themes so far

| Theme | Backdrop |
|---|---|
| Space | `Cosmos` ([cosmos.md](cosmos.md)) |

## Where it is going

1. The interfaces, with Space on them unchanged. (Done.)
2. A volcano backdrop: a volcanic night landscape, lava and a lit plume, embers instead of meteors,
   its own intro, hyperspace and weather.
3. Every other surface themed: the clock, date and update particles, the logo's sparks, the glow
   colours, the screensaver.
4. Declarative packs: a JSON manifest and images on a generic layered backdrop engine, loaded from
   files, with an author's guide.
5. Code plugins: a theme from a separate APK, behind an explicit switch, since its code runs inside
   Home. Then a theme store.

Every theme must fit the GPU budget ([gpu-budget.md](gpu-budget.md)).

## Sources

- `lumen/src/com/arzaroth/lumen/Theme.java`, `Backdrop.java`, `Themes.java`, `SpaceTheme.java`, `Stage.java` (`setTheme`), `LumenSettings.java` (`themeChoice`)
- `lumen/test/com/arzaroth/lumen/ThemesTest.java`, `StageTest.java` (`Plain`)
