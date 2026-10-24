# Themes

A theme is the whole look of Lumen: what is drawn behind the logo, how the logo dances, the light
every effect is made of, and the settings that belong to it. Lumen settings › Theme picks one; it is
kept by id (`theme` in the preferences).

## The interfaces

- `Theme`: an id, a name, `backdrop(context, scale)`, and three defaults: `palette()` (Space's),
  `choreography()` (`Stage.COSMOS`, meteors and starbursts, or `Stage.FIREWORKS`, rockets and
  shells, which picks the logo's holds and exits) and `options()` (none).
- `Option`: one of a theme's own settings, a row of choices kept as an int under its own key.
  Lumen settings › Backdrop lists what every theme shares (Animations, Motion, Parallax, Weather),
  then the current theme's options; `applySettings` sends each to the backdrop.
- `Backdrop`: what the stage draws behind the logo. Required: `look` (one app's look from its logo's
  colours, built off the UI thread), `setLook`, `layout`, `update`, `drawBack`, `moving`. Optional,
  with no-op defaults: `intro`, `setParallax`, `setWarp` (the hyperspace exit), `setWeather`,
  `setStill`, `storm`, and `setOption` for the theme's own settings. Nothing in it belongs to one
  theme.
- `Palette`: the light of every effect besides each app's own colours: the first two colours of the
  logo's sparks (`hot`, `warm`), the clock's flying digits (`dust`), the update's dust, glow and
  scrim (`motes`, `glow`, `scrim`), and the screensaver's accent (`calm`). The stage passes it to the
  mosaic builder with the backdrop; `applySettings` hands it to the clock and the update; the
  screensaver reads the theme from the preferences.
- `Themes`: the registry. The built-in themes come first, Space being the default; `discover`
  adds every pack found under `assets/themes/` (called once at start, and by the screensaver).
  `byId` falls back to Space, so a pack that disappears never leaves Home bare.
- `Stage.setTheme` swaps the backdrop and the choreography, drops every cached app look (they belong
  to the old backdrop) and shows the current app again; `LumenActivity.applySettings` then sends
  every setting to it.

## Themes so far

| Theme | Kind | Backdrop | Options | Palette |
|---|---|---|---|---|
| Space | built in | `Cosmos` ([cosmos.md](cosmos.md)) | Meteors, Sky rotation, Nebula, Real sky | `Palette.SPACE` |
| Fireworks | built in | `Cosmos`, with the fireworks choreography | as Space | `Palette.SPACE` |
| Volcano | pack (`assets/themes/volcano`) | `PictureBackdrop` | none | embers |

Space and Fireworks are one class, `SpaceTheme`, in two instances. Space's options keep their old
preference keys; `Cosmos.setOption` maps them, and finds the real sky itself (where home is through
`Place`, the catalogue from `stars.bin`). `Prefs.migrate` turned the old Sky › Backdrop choice into
the Fireworks theme or the Motion setting.

## Packs

A pack is a folder: a picture and a `theme.json`. `Pack` reads it, `PackTheme` wears it, and
`PictureBackdrop` draws any pack: the picture covers the screen with a margin for the parallax, a
point of it pinned to a point of the screen (past its bottom edge its last row carries on), and the
layers bring it to life.

```json
{
  "id": "volcano",
  "name": "Volcano",
  "image": "night.jpg",
  "anchor": {"image": [0.296, 0.693], "screen": [0.30, 0.52]},
  "palette": {"hot": "#FFF1C2", "warm": "#FF9A3C", "dust": ["#FFE6C0", "#FF8A3D"],
              "motes": ["#FFF1C2", "#FF8A3D"], "glow": "#FF7A2A", "scrim": "#0C0504", "calm": "#FFB27A"},
  "layers": [
    {"type": "glow", "period": 2.4, "depth": 0.45},
    {"type": "stars", "above": 0.7, "count": 140},
    {"type": "sparks", "from": [0.296, 0.688], "width": 0.025, "rate": 22, "speed": [0.08, 0.2],
     "angle": [-118, -62], "gravity": 0.1, "life": [1.0, 2.4], "size": [1.0, 2.2],
     "colors": ["#FFF0B0", "#FFA040", "#FF4A10"]}
  ]
}
```

- Positions are fractions of the picture's width and height (`anchor.image`, `from`) or of the
  screen's (`anchor.screen`). Speeds and gravity are fractions of the picture's height per second.
- `glow`: whatever burns in the picture (warm, bright pixels, found automatically) flickers; `depth`
  is how much it brightens, `period` how slowly it wavers. Eruptions flash it.
- `stars`: the picture's stars (small bright grey points above `above`), the `count` brightest,
  twinkle.
- `sparks`: thrown from `from` in a fan between the `angle`s (degrees, -90 straight up), cooling
  through `colors`; `rate` per second, scaled by each app's vigour. Eruptions throw a burst.
- `palette` and every layer are optional; a missing colour falls back to Space's.

Packs ship inside the APK for now; the next step is loading them from files, then the store.

## Where it is going

1. The interfaces, with Space on them unchanged. (Done.)
2. Every other surface themed through `Palette`. (Done.)
3. Each theme its own settings and choreography; nothing theme-specific left in `Backdrop` or in
   Lumen settings. (Done.)
4. Packs: a picture and a JSON file on `PictureBackdrop`. (Done, shipped in the assets.) Next:
   loading them from storage, more layer types (drifting mist, moving clouds).
5. Code plugins: a theme from a separate APK, behind an explicit switch, since its code runs inside
   Home. Then a theme store.

Every theme must fit the GPU budget ([gpu-budget.md](gpu-budget.md)).

A procedural volcano was built on these interfaces in October 2026 (an eruption at dusk after Anak
Krakatau) and dropped: realism out of noise and strokes never got past looking cheap. It is in git
history (`Volcano.java`, removed after `1b66561`). The Volcano pack took the photographic route
instead.

## Sources

- `lumen/src/com/arzaroth/lumen/Theme.java`, `Option.java`, `Backdrop.java`, `Palette.java`, `Themes.java`, `SpaceTheme.java`, `Pack.java`, `PackTheme.java`, `PictureBackdrop.java`, `Precipitation.java`, `Noise.java`, `Prefs.java` (`migrate`), `Stage.java` (`setTheme`, `setOption`), `LumenSettings.java` (`themeChoice`, `backdrop`)
- `lumen/assets/themes/volcano/`
- `lumen/test/com/arzaroth/lumen/ThemesTest.java`, `PackTest.java`, `PictureBackdropTest.java`, `StageTest.java` (`Plain`)
