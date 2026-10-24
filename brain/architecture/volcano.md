# Volcano

The Volcanoes theme's backdrop: a volcanic night. A range stands on the horizon under a smoky sky
reddening towards it, with a few dim stars high up; the tallest cone, left of the logo, has lava in
its crater and running down its face, and a plume rising from it, lit from below.

## Per screen, per app, per frame

- **Land** (per screen, baked off the UI thread): one bitmap with margins for the parallax: the sky,
  the far range, a second volcano, the main cone with its crater notch, the near ground, rock
  texture, and a rim of firelight on the edges facing the crater. A second, half-resolution bitmap
  is the lava's white mask: the crater's glow, its pool (clipped to the mountain, so it sits in the
  crater instead of floating) and a tapering flow with a branch, stopping above the dock.
- **Look** (per app, from its logo): the lava colour (the logo's vivid hue, saturated; grey logos
  get fire, 14 to 32 degrees) and a 320x256 premultiplied plume, bent by a per-app wind and lean,
  dark where thick and lit in the lava colour near its root. Looks cross-fade over two seconds.
- **Frame**: one blit of the land, the lava mask drawn ADD through a SRC_IN filter in the lava
  colour (pulsing), the plume over the upper left of the screen, then one mesh of particles:
  embers rising from the crater and flow, cooling from white-hot to dark red, lava bombs after an
  eruption, the crater's flash. Lightning, when there is some, is one path drawn twice.

## Answers to the stage

| Call | What the volcano does |
|---|---|
| `setActivity` | Ember rate, and how often the crater erupts on its own (every 40 to 90 s at 1). |
| `storm` | An eruption now: a flash, 70 bombs, lightning two times in three. |
| `intro` | The land fades in over 1.2 s, the lava wakes until 2.2 s, then the crater erupts. |
| `setWarp` | The land dims by up to 70% and the particles are blown out from the centre. |
| `setWeather` | Clouds dim the land and thicken the plume; rain and snow fall in front (`Precipitation`). |
| `setParallax` | Land and lava at FAR, the plume at MID, particles at NEAR. |
| `setStill` | No new embers; frames stop once the last particle is gone. |

The space-only calls (`setTurning`, `setNebula`, `setRealSky`) keep their no-op defaults.

## Palette

`Volcano.PALETTE`: logo sparks white-hot and orange, the clock's digits flying apart in pale gold
and orange, the update's dust from white-hot to deep orange with an orange glow on a smoky red-black
scrim, and a warm screensaver accent.

## Cost

On the TV, Home with the dock focused measured 12.6 ms of GPU per frame (median) against 11.5 ms
for Space on the same scene, inside the 16.7 ms budget ([gpu-budget.md](gpu-budget.md)).

## Sources

- `lumen/src/com/arzaroth/lumen/Volcano.java`, `VolcanoTheme.java`, `Precipitation.java`, `Noise.java`
- `lumen/test/com/arzaroth/lumen/VolcanoTest.java`
