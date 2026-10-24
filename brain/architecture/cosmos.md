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
