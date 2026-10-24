# Volcano

The Volcanoes theme's backdrop: a volcano erupting at dusk, after long exposures of Anak Krakatau.
A broad cone rises from a calm sea under a blue-grey sky with soft clouds, a low wooded ridge on the
right; a column of incandescence stands over the summit and is mirrored in the water, lava bombs
fountain out in glowing arcs, rockfall streaks the slopes and glowing debris lies at their foot.
Each app tints the fire with its logo's colour and has its own ash plume.

## Per screen, per app, per frame

- **Land** (per screen, baked off the UI thread): one bitmap with margins for the parallax: the dusk
  sky with clouds and a few stars, the sea with ripples, the ridge, and the cone, slightly concave
  (`pow(1 - q, 1.35)`), scored by gullies and lit from the left. Its profile is kept (`ground`) so
  rolling bombs follow the slope.
- **Fire** (per screen): a full-resolution white mask over the volcano and the water below it, tinted
  per app at draw time (ADD through a SRC_IN filter, pulsing): the column and its halo, rockfall
  streaks running down the front of the cone, glowing debris at its foot, and the column's
  reflection broken by ripples. Its sides fade out so the halo never shows an edge.
- **Look** (per app, from its logo): the fire colour (the logo's vivid hue, saturated; grey logos
  get fire, 14 to 32 degrees) and a 320x256 premultiplied ash plume of billows, bent by a per-app
  wind, glowing in the fire colour low down and on their undersides. Looks cross-fade over two
  seconds.
- **Frame**: one blit of the land, the plume, the fire mask, then one mesh of particles: a white-hot
  core at the summit, bombs drawn as three-segment ballistic arcs (a long exposure), bombs that
  landed rolling down the slope as streaks, embers, the summit's flash. Lightning, when there is
  some, is one path drawn twice.

## Answers to the stage

| Call | What the volcano does |
|---|---|
| `setActivity` | The fountain's rate (60 bombs a second at 1), embers, and how often it erupts on its own (every 40 to 90 s). |
| `storm` | An eruption now: a flash, 140 bombs thrown harder and wider, lightning two times in three. |
| `intro` | The land fades in over 1.2 s, the fire wakes until 2.2 s, then the summit erupts. |
| `setWarp` | The land dims by up to 70% and the flying particles are blown out from the centre. |
| `setWeather` | Clouds dim the land and thicken the plume; rain and snow fall in front (`Precipitation`). |
| `setParallax` | Land and fire at FAR, the plume at MID, particles at NEAR. |
| `setStill` | No fountain or embers; frames stop once the last particle is gone. |

The space-only calls (`setTurning`, `setNebula`, `setRealSky`) keep their no-op defaults.

## Palette

`Volcano.PALETTE`: logo sparks white-hot and orange, the clock's digits flying apart in pale gold
and orange, the update's dust from white-hot to deep orange with an orange glow on a smoky red-black
scrim, and a warm screensaver accent.

## Cost

The first version of this backdrop measured 12.6 ms of GPU per frame (median) on the TV against
11.5 ms for Space on the same scene, inside the 16.7 ms budget ([gpu-budget.md](gpu-budget.md)).
The dusk scene draws a larger fire mask and up to three quads per bomb; measure it again before
leaning on that number.

## Sources

- `lumen/src/com/arzaroth/lumen/Volcano.java`, `VolcanoTheme.java`, `Precipitation.java`, `Noise.java`
- `lumen/test/com/arzaroth/lumen/VolcanoTest.java`
