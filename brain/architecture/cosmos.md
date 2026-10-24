# Cosmos

The night sky behind the logo.

## Layers, back to front

1. **Field** (`Sky.field`): faint stars as single pixels and the Milky Way, baked once per screen
   size on a background thread (`sky`), twice the screen wide and seamless so it can turn forever,
   and `margin` taller above and below for the vertical parallax. Blitted on **whole pixels**.
2. **Nebula**: an app's own gas cloud (`nebula`), domain-warped noise in two hues with dust lanes,
   400x225, stretched with a slow drift and zoom (`NEB_ZOOM` 1.12). The **airglow** gradient is
   baked into its lower rows: drawn as its own layer it cost a third of the GPU's frame. On an app
   switch the previous nebula cross-fades out (`travel`).
3. **Live stars**: the brightest stars, kept out of the field, drawn each frame so they scintillate
   (harder near the horizon); one may flare as a nova. Plus sprite glints.
4. **Meteors** from the theme's radiant, satellites and the odd satellite train.

With the nebula turned off (`setNebula`), each theme's `glow` bitmap (the airglow alone, same size)
is drawn in its place, so the horizon keeps its glow at the same cost.

## Real sky

`setRealSky(latLon, stars)` swaps the imagined field for `realSky`: a bake of the same shape (field
plus live stars, sorted by brightness) built from the Yale Bright Star Catalogue
(`lumen/assets/stars.bin`, loaded once by `Stage.setRealSky`) for the place `Place` finds.

- Framing: facing south (north below the equator), `REAL_FOV` 120 degrees wide, the horizon at
  `REAL_HORIZON` (94% down). `look` maps a screen point to altitude and azimuth, `fix` a star to the
  field; the field reaches `marginX` past each side for the parallax.
- The Milky Way keeps the imagined sky's colouring but follows the galactic latitude of each cell,
  its noise sampled in galactic coordinates so its clouds stay put on the sky. Stars fainter than
  the catalogue come from `faintStars`, seeded per one-degree patch of sky so they keep their places.
- `reproject` moves the live stars, the planets and the Moon every second; `rebuild` bakes a new
  field every two minutes and `fieldFade` cross-fades it over the old. `drawMoon` draws the Moon a
  few times its size, lit to its phase.
- The arithmetic (sidereal time, coordinates, Sun, Moon, planets) is in `Heavens`, tested against
  known positions.

## Weather

`setWeather(clouds, rain, snow)` eases the sky toward the weather: clouds scale the alpha of every
sky layer (no new layer) and the meteor rate; rain and snow are one additive mesh of up to
`PRECIP` (240) streaks or soft flakes, drawn after the meteors.

## Hyperspace

`setWarp` (from the stage's hyperspace exit) dims the sky and turns the bright stars into streaks
that fly outward (`warpTravel` builds while the warp is on), faster the nearer and the further out
they are; as the warp eases off they keep flying while they fade and the resting stars fade back in.
Nothing is pulled back.

## Theme

`Cosmos.theme` picks, per app, a radiant, a meteor rate and tint and an airglow hue chosen to stand
apart from the logo's dominant hue, and generates the nebula.

## Motion

- **Sky rotation** (`setTurning`, off by default): `turned()` maps the wall clock to the Earth's
  real rate (one turn per sidereal day for a ~50 degree view), about one pixel every 6 s.
- **Parallax** (`setParallax(x, y)`): eased toward the focus; the field moves least (`FAR`), the
  nebula more (`MID`), live stars and meteors most (`NEAR`), sideways and down.
- `moving()` tells the stage whether the sky still needs frames when the logo is still.

## Sources

- `lumen/src/com/arzaroth/lumen/Cosmos.java`
- `lumen/test/com/arzaroth/lumen/CosmosTest.java`
